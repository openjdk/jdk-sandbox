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
  _nmethod_grouper_thread = new C2NMethodGrouperThread();
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

using NMethodSamples = ResourceHashtable<const nmethod*, int, 1024>;

class ThreadSampler : public StackObj {
 private:
  NMethodSamples _samples;
  int _total_samples;
  int _processed_threads;

 public:
  ThreadSampler() : _samples(), _total_samples(0), _processed_threads(0) {}

  void run() {
    MutexLocker ml(Threads_lock);

    for (JavaThreadIteratorWithHandle jtiwh; JavaThread *jt = jtiwh.next(); ) {
      if (is_excluded(jt)) {
        continue;
      }

      _processed_threads++;
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

  void collect_samples() {
    tty->print_cr("Profiling nmethods");

    const int64_t period = sampling_period_ms();
    while (true) {
      const int64_t sampling_start = get_monotonic_ms();
      run();
      if (_total_samples >= min_samples()) {
        break;
      }
      const int64_t next_sample =
          period - (get_monotonic_ms() - sampling_start);
      if (next_sample > 0) {
        os::naked_sleep(next_sample);
      }
    }
  }

  const NMethodSamples& samples() const {
    return _samples;
  }
  int total_samples() const {
    return _total_samples;
  }
  int processed_threads() const {
    return _processed_threads;
  }

  void exclude_unregistered_nmethods(const LinkedListImpl<const nmethod*>& unregistered);
};

void ThreadSampler::exclude_unregistered_nmethods(const LinkedListImpl<const nmethod*>& unregistered) {
  LinkedListIterator<const nmethod*> it(unregistered.head());
  while (!it.is_empty()) {
    const nmethod* nm = *it.next();
    int* count = _samples.get(nm);
    if (count != nullptr) {
      _total_samples -= *count;
      *count = 0;
    }
  }
}

class HotCodeHeapCandidates : public StackObj {
 public:
  void find(const NMethodSamples& samples) {
  }

  void relocate() {}
};

void NMethodGrouper::group_nmethods() {
  ResourceMark rm;

  ThreadSampler sampler;
  sampler.collect_samples();

  {
    MutexLocker ml(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    int total_samples = sampler.total_samples();
    int processed_threads = sampler.processed_threads();
    tty->print_cr("Profiling nmethods done: %d samples, %d nmethods, %d processed threads, %d unregistered nmethods",
       total_samples, sampler.samples().number_of_entries(),
       processed_threads, (int)_unregistered_nmethods.size());

    if (is_code_cache_unstable()) {
      tty->print_cr("CodeCache is unstable, skipping nmethod grouping");
      return;
    }

    sampler.exclude_unregistered_nmethods(_unregistered_nmethods);
    tty->print_cr("Total samples after excluding unregistered nmethods: %d",
       sampler.total_samples());
    _unregistered_nmethods.clear();

    // TODO: We might want to update nmethods GC status to prevent them from getting cold.

    HotCodeHeapCandidates candidates;
    candidates.find(sampler.samples());
    candidates.relocate();
  }
}

void NMethodGrouper::unregister_nmethod(const nmethod* nm) {
  assert_locked_or_safepoint(CodeCache_lock);
  _unregistered_nmethods.add(nm);
  // TODO: add assert to check if add returns nullptr we have exceeded a certain threshold for unregistered nmethods.
  // Instead of a linked list, we can consider using a fixed-size array. When the array is full, it will be a sign
  // of unstable CodeCache. We will stop adding unregistered nmethods.
}