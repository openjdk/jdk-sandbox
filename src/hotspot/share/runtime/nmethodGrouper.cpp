#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/nmethodGrouper.hpp"
#include "runtime/suspendedThreadTask.hpp"
#include "runtime/threads.hpp"
#include "utilities/resourceHash.hpp"

class C2NMethodGrouperThread : public NonJavaThread {
 public:
  void run() override {
    NMethodGrouper::group_nmethods_loop();
  }
  const char* name()      const override { return "C2 nmethod Grouper Thread"; }
  const char* type_name() const override { return "C2NMethodGrouperThread"; }
};

NonJavaThread *NMethodGrouper::_nmethod_grouper_thread = nullptr;
LinkedListImpl<const nmethod*> NMethodGrouper::_unregistered_nmethods;

void NMethodGrouper::initialize() {
  if (os::create_thread(_nmethod_grouper_thread, os::os_thread)) {
    os::start_thread(_nmethod_grouper_thread);
  } else {
    vm_exit_during_initialization("Failed to create C2 nmethod grouper thread");
  }
}

void NMethodGrouper::group_nmethods_loop() {
  while (true) {
    // TODO: implement logic to group nmethods if they are sparse.
    os::naked_sleep(60 * 1000);
    group_nmethods();
  }
}

static inline bool is_excluded(JavaThread* thread) {
  return (thread->is_hidden_from_external_view() ||
          thread->thread_state() != _thread_in_Java ||
          thread->in_deopt_handler());
}

class GetC2NMethodTask : public SuspendedThreadTask {
 public:
  nmethod *_nmethod;
  GetC2NMethodTask(JavaThread* thread) : SuspendedThreadTask(thread), _nmethod(nullptr) {}

  void do_task(const SuspendedThreadTaskContext& context) {
    JavaThread* jt = JavaThread::cast(context.thread());
    if (jt->thread_state() != _thread_in_Java) {
      return;
    }

    intptr_t *last_sp = jt->last_Java_sp();
    address pc = nullptr;
    if (last_sp == nullptr) {
      pc = os::fetch_frame_from_context(context.ucontext(), nullptr, &last_sp);
    } else {
      pc = jt->last_Java_pc();
      if (pc == nullptr) {
        pc = frame::return_address(last_sp);
      }
    }

    if (pc != nullptr && !Interpreter::contains(pc) && CodeCache::contains(pc)) {
      const CodeBlob* const cb = CodeCache::find_blob_fast(pc);
      if (cb != nullptr && cb->is_nmethod()) {
        nmethod* nm = cb->as_nmethod();
        if (nm->is_compiled_by_c2() &&
            !nm->is_osr_method()  &&
            nm->is_in_use() &&
            !nm->is_marked_for_deoptimization() &&
            !nm->is_unloading()) {
          _nmethod = nm;
        }
      }
    }
  }
};

using NMethodSamples = ResourceHashtable<nmethod*, int, 1024>;

class ThreadSampler : public StackObj {
 private:
  NMethodSamples _samples;
  int _total_samples;

 public:
  ThreadSampler() : _samples(), _total_samples(0) {}

  void run() {
    MutexLocker ml(Threads_lock);

    for (JavaThreadIteratorWithHandle jtiwh; JavaThread *jt = jtiwh.next(); ) {
      if (is_excluded(jt)) {
        continue;
      }

      GetC2NMethodTask task(jt);
      task.run();
      if (task._nmethod != nullptr) {
        bool created = false;
        int *count = _samples.put_if_absent(task._nmethod, 0, &created);
        (*count)++;
        _total_samples++;
      }
    }
  }

  const NMethodSamples& samples() const {
    return _samples;
  }
  int total_samples() const {
    return _total_samples;
  }
};

static inline int64_t get_monotonic_ms() {
  return os::javaTimeNanos() / 1000000;
}

static inline int64_t sampling_period_ms() {
  // This is the interval in milliseconds between samples.
  return 20;
}

static inline int64_t duration_ms() {
  // This is the total duration in milliseconds for which the sampling will run.
  return 60 * 1000; // 60 seconds
}

static inline int min_samples() {
  return 3000; // Minimum number of samples to collect
}

static void find_nmethods_to_group() {
  ResourceMark rm;
  // TODO: add logging support to codecache.
  tty->print_cr("Profiling nmethods");

  const int64_t period = sampling_period_ms();
  ThreadSampler sampler;
  while (sampler.total_samples() < min_samples()) {
    const int64_t sampling_start = get_monotonic_ms();
    sampler.run();
    const int64_t next_sample = period - (get_monotonic_ms() - sampling_start);
    if (next_sample > 0) {
      os::naked_sleep(next_sample);
    }
  }
  int total_samples = sampler.total_samples();
  tty->print_cr("Profiling nmethods done, %d samples", total_samples);
}

void NMethodGrouper::group_nmethods() {
  find_nmethods_to_group();
}

void NMethodGrouper::unregister_nmethod(const nmethod* nm) {
  assert_locked_or_safepoint(CodeCache_lock);
  _unregistered_nmethods.add(nm);
  // TODO: add assert to check if add returns nullptr we have exceeded a certain threshold for unregistered nmethods.
  // Instead of a linked list, we can consider using a fixed-size array. When the array is full, it will be a sign
  // of unstable CodeCache. We will stop adding unregistered nmethods.
}