/*
 * Copyright (c) 2024, SAP SE. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "jfr/periodic/sampling/jfrCPUTimeThreadSampler.hpp"

#if defined(LINUX)

#include "jfr/recorder/service/jfrEvent.hpp"
#include "jfr/recorder/stacktrace/jfrStackTrace.hpp"
#include "jfr/recorder/stacktrace/jfrAsyncStackTrace.hpp"
#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "memory/allocation.hpp"
#include "precompiled.hpp"
#include "classfile/javaThreadStatus.hpp"
#include "jfr/jfrEvents.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/periodic/sampling/jfrCallTrace.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdLoadBarrier.inline.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/stacktrace/jfrStackTraceRepository.hpp"
#include "jfr/recorder/storage/jfrBuffer.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfrfiles/jfrEventClasses.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/osThread.hpp"
#include "runtime/semaphore.hpp"
#include "runtime/stackWatermark.hpp"
#include "runtime/suspendedThreadTask.hpp"
#include "runtime/thread.hpp"
#include "runtime/threadSMR.hpp"
#include "signals_posix.hpp"
#include "runtime/threadCrashProtection.hpp"
#include "utilities/concurrentHashTable.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/systemMemoryBarrier.hpp"
#include <algorithm>
#include <bits/types/clockid_t.h>
#include <bits/types/timer_t.h>
#include <cerrno>
#include <climits>
#include <cstdio>
#include <ctime>
#include <pthread.h>
#include <signal.h>
#include <time.h>

enum JfrSampleType {
  // no sample, because thread not in walkable state
  NO_SAMPLE = 0,
  // sample from thread while in Java
  JAVA_SAMPLE = 1,
  // sample from thread while in native
  NATIVE_SAMPLE = 2
};

static bool thread_state_in_java(JavaThread* thread) {
  assert(thread != nullptr, "invariant");
  switch(thread->thread_state()) {
    case _thread_new:
    case _thread_uninitialized:
    case _thread_new_trans:
    case _thread_in_vm_trans:
    case _thread_blocked_trans:
    case _thread_in_native_trans:
    case _thread_blocked:
    case _thread_in_vm:
    case _thread_in_native:
      break;
    case _thread_in_Java_trans:
    case _thread_in_Java:
      return true;
    default:
      ShouldNotReachHere();
      break;
  }
  return false;
}

static bool thread_state_in_native(JavaThread* thread) {
  assert(thread != nullptr, "invariant");
  switch(thread->thread_state()) {
    case _thread_new:
    case _thread_uninitialized:
    case _thread_new_trans:
    case _thread_in_Java_trans:
    case _thread_in_Java:
          break;
    case _thread_blocked_trans:
    case _thread_in_vm_trans:
    case _thread_in_native_trans:
    case _thread_blocked:
    case _thread_in_native:
    case _thread_in_vm: // walking in vm causes weird bugs (assertions in G1 fail), so don't
      return true;
    default:
      ShouldNotReachHere();
      break;
  }
  return false;
}

static bool is_excluded(JavaThread* thread) {
  return thread->is_hidden_from_external_view() ||
    (os::is_readable_pointer(thread->jfr_thread_local()) && thread->jfr_thread_local()->is_excluded());
}

static JavaThread* get_java_thread_if_valid() {
  Thread* raw_thread = Thread::current_or_null_safe();
  JavaThread* jt;

  if (raw_thread == nullptr || !raw_thread->is_Java_thread() ||
      (jt = JavaThread::cast(raw_thread))->is_exiting()) {
    return nullptr;
  }

  if (is_excluded(jt)) {
    return nullptr;
  }
  return jt;
}

// A trace of stack frames, contains all information
// collected in the signal handler, required to create
// a JFR event with a stack trace
class JfrCPUTimeTrace {
  template <bool multiple_dequeuers, bool multipler_enqueuers>
  friend class JfrTraceQueue;
  u4 _index;
  JfrAsyncStackFrame* _frames;
  JfrAsyncStackTrace _stacktrace;
  u4 _max_frames;
  // error code for the trace, 0 if no error
  u4 _error;

  JfrSampleType _type;
  JfrTicks _start_time;
  JfrTicks _end_time;
  JavaThread* _sampled_thread;

public:
  JfrCPUTimeTrace(u4 index, JfrAsyncStackFrame* frames, u4 max_frames):
    _index(index), _frames(frames), _stacktrace(_frames, max_frames),
    _max_frames(max_frames) {

    }

  JfrAsyncStackFrame* frames() { return _frames; }
  u4 max_frames() const { return _max_frames; }

  u4 error() const { return _error; }

  JfrSampleType type() const { return _type; }

  void set_start_time(JfrTicks start_time) { _start_time = start_time; }
  JfrTicks start_time() const { return _start_time; }
  void set_end_time(JfrTicks end_time) { _end_time = end_time; }
  JfrTicks end_time() const { return _end_time; }
  void set_sampled_thread(JavaThread* thread) { Atomic::store(&_sampled_thread, thread); }
  JavaThread* sampled_thread() const { return _sampled_thread; }

  JfrAsyncStackTrace& stacktrace() { return _stacktrace; }

  // Record a trace of the current thread
  void record_trace(JavaThread* jt, void* ucontext) {
    _stacktrace = JfrAsyncStackTrace(_frames, _max_frames);
    set_sampled_thread(jt);
    _type = NO_SAMPLE;
    _error = 1;
    _start_time = JfrTicks::now();
    _end_time = JfrTicks::now();
    if (!jt->in_deopt_handler() && !Universe::heap()->is_stw_gc_active())  {
      ThreadInAsgct tia(jt);
      if (thread_state_in_java(jt)) {
        record_java_trace(jt, ucontext);
      } else if (thread_state_in_native(jt)) {
        record_native_trace(jt, ucontext);
      }
    }
    _end_time = JfrTicks::now();
  }

private:

  void record_java_trace(JavaThread* jt, void* ucontext) {
    _type = JAVA_SAMPLE;
    JfrGetCallTrace trace(true, jt);
    frame topframe;
    if (trace.get_topframe(ucontext, topframe)) {
      _error = _stacktrace.record_async(jt, topframe) ? 0 : 11;
    } else {
      _error = 2;
      return;
     }
  }

  void record_native_trace(JavaThread* jt, void* ucontext) {
    // When a thread is only attach it will be native without a last java frame
   _type = NATIVE_SAMPLE;
    _error = 1;
    if (!jt->has_last_Java_frame()) {
      _error = 3;
      return;
    }
    frame topframe;
    if (!jt->pd_get_top_frame_for_signal_handler(&topframe, ucontext, false)) {
      _error = 6;
      return;
    }
    frame first_java_frame;
    Method* method = nullptr;
    JfrGetCallTrace gct(false, jt);
    if (!gct.find_top_frame(topframe, &method, first_java_frame)) {
      _error = 4;
      return;
    }
    if (method == nullptr) {
      _error = 5;
      return;
    }
    topframe = first_java_frame;
    _error = _stacktrace.record_async(jt, topframe) ? 0 : 10;
  }
};

// An atomic circular buffer of JfrTraces with a fixed size
// Does not own any frames
template <bool multiple_dequeuers, bool multipler_enqueuers>
class JfrTraceQueue {
  JfrCPUTimeTrace** _traces;
  u4 _size;
  volatile u4 _head;
  volatile u4 _tail;

public:
  JfrTraceQueue(u4 size): _traces(JfrCHeapObj::new_array<JfrCPUTimeTrace*>(size)), _size(size), _head(0), _tail(0) {}

  ~JfrTraceQueue() {
    JfrCHeapObj::free(_traces, sizeof(JfrCPUTimeTrace) * _size);
  }

  bool is_empty() const { return _head == _tail; }
  bool is_full() const { return (_head + 1) % _size == _tail; }

  JfrCPUTimeTrace* dequeue() {
    while (true) {
      u4 current_tail = _tail;
      u4 next_tail = (current_tail + 1) % _size;
      if (current_tail == _tail) {
        if (current_tail == _head) {
          return nullptr; // queue is empty
        }
        if (Atomic::cmpxchg(&_tail, current_tail, next_tail, atomic_memory_order::memory_order_acquire) == current_tail) {
          JfrCPUTimeTrace* trace = _traces[current_tail];
          return trace;
        }
      }
    }
  }

  bool enqueue(JfrCPUTimeTrace* trace) {
    while (true) {
      u4 current_head = _head;
      u4 next_head = (current_head + 1) % _size;
      if (current_head == _head) {
        if (is_full()) {
          return false;
        }
        if (Atomic::cmpxchg(&_head,
          current_head, next_head, atomic_memory_order::memory_order_release) == current_head) {
          _traces[current_head] = trace;
          return true;
        }
      }
    }
  }

  u4 count() const { return (_head - _tail) % _size; }
};


typedef JfrTraceQueue<true, false> JfrFreshTraceQueue;
typedef JfrTraceQueue<false, true> JfrFilledTraceQueue;


// Two queues for sampling, fresh and filled
// at the start, all traces are in the fresh queue
class JfrTraceQueues {
  JfrAsyncStackFrame* _frames;
  JfrCPUTimeTrace* _traces;
  JfrFreshTraceQueue _fresh;
  JfrFilledTraceQueue  _filled;
  u4 _max_traces;
  u4 _max_frames_per_trace;

public:
  JfrTraceQueues(u4 max_traces, u4 max_frames_per_trace):
    _frames(JfrCHeapObj::new_array<JfrAsyncStackFrame>(max_traces * max_frames_per_trace)),
    _traces(JfrCHeapObj::new_array<JfrCPUTimeTrace>(max_traces)), _fresh(max_traces), _filled(max_traces),
    _max_traces(max_traces), _max_frames_per_trace(max_frames_per_trace) {
    // create traces
    for (u4 i = 0; i < max_traces; i++) {
      _traces[i] = JfrCPUTimeTrace(i, &_frames[i * max_frames_per_trace], max_frames_per_trace);
    }
    // initialize fresh queue
    for (u4 i = 0; i < max_traces; i++) {
      _fresh.enqueue(&_traces[i]);
    }
  }

  ~JfrTraceQueues() {
    JfrCHeapObj::free(_frames, sizeof(JfrAsyncStackFrame) * _max_traces * _max_frames_per_trace);
    JfrCHeapObj::free(_traces, sizeof(JfrCPUTimeTrace) * _max_traces);
  }

  JfrFreshTraceQueue& fresh() { return _fresh; }
  JfrFilledTraceQueue& filled() { return _filled; }

  u4 max_traces() const { return _max_traces; }
};

class JfrCPUTimeFillCallback;

class JfrCPUTimeThreadSampler : public NonJavaThread {
  friend class JfrCPUTimeThreadSampling;
  friend class JfrCPUTimeFillCallback;
 private:
  Semaphore _sample;
  Thread* _sampler_thread;
  JfrTraceQueues _queues;
  int64_t _period_millis;
  const size_t _max_frames_per_trace; // for enqueue buffer monitoring
  volatile bool _disenrolled;
  volatile bool _stop_signals = false;
  volatile int _active_signal_handlers;
  JfrStackFrame *_jfrFrames;
  const size_t _min_jfr_buffer_size;
  volatile int _ignore_because_queue_full = 0;
  volatile int _ignore_because_queue_full_sum = 0;

  const JfrBuffer* get_enqueue_buffer();
  const JfrBuffer* renew_if_full(const JfrBuffer* enqueue_buffer);

  void task_stacktrace(JfrSampleType type, JavaThread** last_thread);
  JfrCPUTimeThreadSampler(int64_t period_millis, u4 max_traces, u4 max_frames_per_trace);
  ~JfrCPUTimeThreadSampler();

  void start_thread();

  void enroll();
  void disenroll();
  void set_sampling_period(int64_t period_millis);

  void process_trace_queue();
 protected:
    virtual void post_run();
   public:
    virtual const char* name() const { return "JFR CPU Time Thread Sampler"; }
    virtual const char* type_name() const { return "JfrCPUTimeThreadSampler"; }
    bool is_JfrSampler_thread() const { return true; }
    void run();
    void on_javathread_create(JavaThread* thread);
    bool create_timer_for_thread(JavaThread* thread, timer_t &timerid);
    void set_timer_time(timer_t timerid);
    void on_javathread_terminate(JavaThread* thread);
    int64_t get_sampling_period() const { return Atomic::load(&_period_millis); };

    void handle_timer_signal(void* context);
    void init_timers();
    void stop_timer();
};


JfrCPUTimeThreadSampler::JfrCPUTimeThreadSampler(int64_t period_millis, u4 max_traces, u4 max_frames_per_trace) :
  _sample(),
  _sampler_thread(nullptr),
  _queues(max_traces, max_frames_per_trace),
  _period_millis(period_millis),
  _max_frames_per_trace(max_frames_per_trace),
  _disenrolled(true),
  _jfrFrames(JfrCHeapObj::new_array<JfrStackFrame>(_max_frames_per_trace)),
  _min_jfr_buffer_size(_max_frames_per_trace * 2 * wordSize * (_queues.fresh().count() + 1)) {
  assert(_period_millis >= 0, "invariant");
}

JfrCPUTimeThreadSampler::~JfrCPUTimeThreadSampler() {
  JfrCHeapObj::free(_jfrFrames, sizeof(JfrStackFrame) * _max_frames_per_trace);
}

void JfrCPUTimeThreadSampler::on_javathread_create(JavaThread* thread) {
  if (thread->is_Compiler_thread()) {
    return;
  }
  if (thread->jfr_thread_local() != nullptr) {
    timer_t timerid;
    if (create_timer_for_thread(thread, timerid) && thread->jfr_thread_local() != nullptr) {
      thread->jfr_thread_local()->set_timerid(timerid);
    }
  }
}

void JfrCPUTimeThreadSampler::on_javathread_terminate(JavaThread* thread) {
  if (thread->jfr_thread_local() != nullptr && thread->jfr_thread_local()->has_timerid()) {
    timer_delete(thread->jfr_thread_local()->timerid());
    thread->jfr_thread_local()->unset_timerid();
  }
}

void JfrCPUTimeThreadSampler::start_thread() {
  if (os::create_thread(this, os::os_thread)) {
    os::start_thread(this);
  } else {
    log_error(jfr)("Failed to create thread for thread sampling");
  }
}

void JfrCPUTimeThreadSampler::enroll() {
  if (_disenrolled) {
    log_trace(jfr)("Enrolling thread sampler");
    _sample.signal();
    _disenrolled = false;
    printf("Enrolled\n");
    init_timers();
    set_sampling_period(get_sampling_period());
  }
}

void JfrCPUTimeThreadSampler::disenroll() {
  if (!_disenrolled) {
    printf("Disenrolled\n");
    stop_timer();
    Atomic::store(&_stop_signals, true);
    while (_active_signal_handlers > 0) {
      // wait for all signal handlers to finish
      os::naked_short_nanosleep(1000);
    }
    _sample.wait();
    Atomic::store(&_disenrolled, true);
    log_trace(jfr)("Disenrolling thread sampler");
  }
}

void JfrCPUTimeThreadSampler::run() {
  assert(_sampler_thread == nullptr, "invariant");
  // initialize the buffer
  JfrTraceIdLoadBarrier::renew_sampler_enqueue_buffer(this, _min_jfr_buffer_size * 5);
  _sampler_thread = this;
  while (true) {
    if (!_sample.trywait()) {
      // disenrolled
      _sample.wait();
    }
    _sample.signal();

    int64_t period_millis = get_sampling_period();
    period_millis = period_millis == 0 ? max_jlong : MAX2<int64_t>(period_millis, 1);

    // If both periods are max_jlong, it implies the sampler is in the process of
    // disenrolling. Loop back for graceful disenroll by means of the semaphore.
    if (period_millis == max_jlong) {
      continue;
    }

    // process all filled traces
    if (!_queues.filled().is_empty()) {
      if (_ignore_because_queue_full != 0) {
        printf("ignore because queue full %d sum %d\n", _ignore_because_queue_full, _ignore_because_queue_full_sum);
        if (EventCPUTimeExecutionSamplerQueueFull::is_enabled()) {
          EventCPUTimeExecutionSamplerQueueFull event;
          event.set_starttime(JfrTicks::now());
          event.set_droppedSamples(_ignore_because_queue_full);
          event.commit();
        }
        Atomic::store(&_ignore_because_queue_full, 0);
      }
      process_trace_queue();
    }
    if (_disenrolled) {
      break;
    }

    // assumption: every sample_interval / max_traces should come a new sample
    int64_t sleep_to_next = period_millis * NANOSECS_PER_MILLISEC / os::processor_count();
    if (sleep_to_next > 20000) {
      os::naked_short_nanosleep(sleep_to_next);
    }
  }
}

static u4 max_queue_size = 0;

// crash protection for JfrThreadLocal::thread_id(trace->sampled_thread())
// because the thread could be deallocated between the time of recording
// and the time of processing
class JFRRecordSampledThreadCallback : public CrashProtectionCallback {
  friend class JfrCPUTimeThreadSampler;
 public:
  JFRRecordSampledThreadCallback(JavaThread* thread) :
    _thread(thread) {
  }
  virtual void call() {
    _thread_id = JfrThreadLocal::thread_id(_thread);
  }
 private:
  JavaThread* _thread;
  traceid _thread_id;
};


static size_t count = 0;

void JfrCPUTimeThreadSampler::process_trace_queue() {
  if (max_queue_size < _queues.filled().count()) {
    max_queue_size = _queues.filled().count();
    printf("max queue size %d\n", max_queue_size);
  }
  while (!_queues.filled().is_empty()) {
    JfrCPUTimeTrace* trace = _queues.filled().dequeue();
    if (!os::is_readable_pointer(trace)) {
      continue;
    }
    // create event, convert frames (resolve method ids)
    // we can't do the conversion in the signal handler,
    // as this causes segmentation faults related to the
    // enqueue buffers
    EventCPUTimeExecutionSample event;
    const JfrBuffer* enqueue_buffer = get_enqueue_buffer();
    if (trace->error() == 0 && trace->stacktrace().nr_of_frames() > 0) {
      JfrStackTrace jfrTrace(_jfrFrames, _max_frames_per_trace);
      const JfrBuffer* enqueue_buffer = get_enqueue_buffer();
      if (trace->stacktrace().store(&jfrTrace, enqueue_buffer) && jfrTrace.nr_of_frames() > 0) {
        traceid id = JfrStackTraceRepository::add(jfrTrace);
        event.set_stackTrace(id);
      } else {
        event.set_stackTrace(0);
      }
    } else {
      event.set_stackTrace(0);
    }
    event.set_starttime(trace->start_time());
    event.set_endtime(trace->end_time());

    JFRRecordSampledThreadCallback cb(trace->sampled_thread());
    ThreadCrashProtection crash_protection;
    if (crash_protection.call(cb)) {
      event.set_sampledThread(cb._thread_id);
      event.set_state(static_cast<u8>(JavaThreadStatus::RUNNABLE));
      assert(EventCPUTimeExecutionSample::is_enabled(), "invariant");
      event.commit();
      count++;
      if (count % 10000 == 0) {
        printf("count %lu\n", count);
      }
    }
    _queues.fresh().enqueue(trace);
  }
}



void JfrCPUTimeThreadSampler::post_run() {
  this->NonJavaThread::post_run();
  delete this;
}

const JfrBuffer* JfrCPUTimeThreadSampler::get_enqueue_buffer() {
  const JfrBuffer* buffer = JfrTraceIdLoadBarrier::get_sampler_enqueue_buffer(this);
  if (buffer == nullptr) {
    JfrBuffer* buffer = JfrTraceIdLoadBarrier::renew_sampler_enqueue_buffer(this, _min_jfr_buffer_size * 2);
    return buffer;
  }
  return renew_if_full(buffer);
}

const JfrBuffer* JfrCPUTimeThreadSampler::renew_if_full(const JfrBuffer* enqueue_buffer) {
  assert(enqueue_buffer != nullptr, "invariant");
  if (enqueue_buffer->free_size() < _min_jfr_buffer_size) {
    JfrBuffer* buffer = JfrTraceIdLoadBarrier::renew_sampler_enqueue_buffer(this, _min_jfr_buffer_size * 2);
    return buffer;
  }
  return enqueue_buffer;
}

static JfrCPUTimeThreadSampling* _instance = nullptr;

JfrCPUTimeThreadSampling& JfrCPUTimeThreadSampling::instance() {
  return *_instance;
}

JfrCPUTimeThreadSampling* JfrCPUTimeThreadSampling::create() {
  assert(_instance == nullptr, "invariant");
  _instance = new JfrCPUTimeThreadSampling();
  return _instance;
}

void JfrCPUTimeThreadSampling::destroy() {
  if (_instance != nullptr) {
    delete _instance;
    _instance = nullptr;
  }
}

JfrCPUTimeThreadSampling::JfrCPUTimeThreadSampling() : _sampler(nullptr) {}

JfrCPUTimeThreadSampling::~JfrCPUTimeThreadSampling() {
  if (_sampler != nullptr) {
    _sampler->disenroll();
  }
}

#ifdef ASSERT
void assert_periods(const JfrCPUTimeThreadSampler* sampler, int64_t period_millis) {
  assert(sampler != nullptr, "invariant");
  assert(sampler->get_sampling_period() == period_millis, "invariant");
}
#endif

static void log(int64_t period_millis) {
  log_trace(jfr)("Updated thread sampler for java: " INT64_FORMAT "  ms", period_millis);
}

void JfrCPUTimeThreadSampling::create_sampler(int64_t period_millis) {
  assert(_sampler == nullptr, "invariant");
  log_trace(jfr)("Creating thread sampler for java:" INT64_FORMAT " ms", period_millis);
  int factor = 20;
  if (const char* env_p = std::getenv("FACTOR")) {
    factor = std::atoi(env_p);
  }
  int queue_size = factor * os::processor_count();
  printf("queue size %d\n", queue_size);
  _sampler = new JfrCPUTimeThreadSampler(period_millis, queue_size, JfrOptionSet::stackdepth());
  _sampler->start_thread();
  _sampler->enroll();
}

void JfrCPUTimeThreadSampling::update_run_state(int64_t period_millis) {
  if (period_millis > 0) {
    if (_sampler == nullptr) {
      create_sampler(period_millis);
    } else {
      _sampler->set_sampling_period(period_millis);
      _sampler->enroll();
    }
    DEBUG_ONLY(assert_periods(_sampler, period_millis);)
    log(period_millis);
    return;
  }
  if (_sampler != nullptr) {
    _sampler->set_sampling_period(period_millis);
    DEBUG_ONLY(assert_periods(_sampler, period_millis);)
    _sampler->disenroll();
  }
}

void JfrCPUTimeThreadSampling::set_sampling_period(int64_t period_millis) {
  if (_sampler != nullptr) {
    _sampler->set_sampling_period(period_millis);
  }
  update_run_state(period_millis);
}

void JfrCPUTimeThreadSampling::set_sample_period(int64_t period_millis) {
  assert(period_millis >= 0, "invariant");
  if (_instance == nullptr && 0 == period_millis) {
    return;
  }
  instance().set_sampling_period(period_millis);
}

void JfrCPUTimeThreadSampling::on_javathread_create(JavaThread *thread) {
  if (_instance != nullptr && _instance->_sampler != nullptr) {
    _instance->_sampler->on_javathread_create(thread);
  }
}

void JfrCPUTimeThreadSampling::on_javathread_terminate(JavaThread *thread) {
  if (_instance != nullptr && _instance->_sampler != nullptr) {
    _instance->_sampler->on_javathread_terminate(thread);
  }
}

void handle_timer_signal(int signo, siginfo_t* info, void* context) {
  assert(_instance != nullptr, "invariant");
  _instance->handle_timer_signal(context);
}


void JfrCPUTimeThreadSampling::handle_timer_signal(void* context) {
  assert(_sampler != nullptr, "invariant");
  if (_sampler->_stop_signals) {
    return;
  }
  Atomic::inc(&_sampler->_active_signal_handlers);
  _sampler->handle_timer_signal(context);
  Atomic::dec(&_sampler->_active_signal_handlers);
}

void JfrCPUTimeThreadSampler::handle_timer_signal(void* context) {
  JavaThread* jt = get_java_thread_if_valid();
  if (jt == nullptr) {
    return;
  }
  JfrCPUTimeTrace* trace = this->_queues.fresh().dequeue();
  if (trace != nullptr) {
    trace->record_trace(jt, context);
    this->_queues.filled().enqueue(trace);
  } else {
    Atomic::inc(&_ignore_because_queue_full);
    Atomic::inc(&_ignore_because_queue_full_sum);
  }
}

const int SIG = SIGPROF;

void JfrCPUTimeThreadSampler::set_timer_time(timer_t timerid) {
  struct itimerspec its;
  int64_t period_millis = get_sampling_period();
  its.it_interval.tv_sec = period_millis / 1000;
  its.it_interval.tv_nsec = (period_millis % 1000) * 1000000;
  its.it_value = its.it_interval;
  if (timer_settime(timerid, 0, &its, NULL) == -1) {
    warning("Failed to set timer for thread sampling");
  }
}

bool JfrCPUTimeThreadSampler::create_timer_for_thread(JavaThread* thread, timer_t& timerid) {
  if (thread->osthread() == nullptr || thread->osthread()->thread_id() == 0){
    return false;
  }
  timer_t t;
  OSThread::thread_id_t tid = thread->osthread()->thread_id();
  struct sigevent sev;
  sev.sigev_notify = SIGEV_THREAD_ID;
  sev.sigev_signo = SIG;
  sev.sigev_value.sival_ptr = &t;
  ((int*)&sev.sigev_notify)[1] = tid;
  clockid_t clock;
  int err = pthread_getcpuclockid(thread->osthread()->pthread_id(), &clock);
  if (err != 0) {
    errno = err;
    perror("pthread_getcpuclockid");
    return false;
  }
  if (timer_create(clock, &sev, &t) < 0) {
    return false;
  }
  set_timer_time(t);
  timerid = t;
  return true;
}

void JfrCPUTimeThreadSampler::init_timers() {
  // install sig handler for sig
  PosixSignals::install_generic_signal_handler(SIG, (void*)::handle_timer_signal);

  // create timers for all existing threads
  MutexLocker tlock(Threads_lock);
  ThreadsListHandle tlh;
  for (size_t i = 0; i < tlh.length(); i++) {
    on_javathread_create(tlh.thread_at(i));
  }
}

void JfrCPUTimeThreadSampler::stop_timer() {
  MutexLocker tlock(Threads_lock);
  ThreadsListHandle tlh;
  for (size_t i = 0; i < tlh.length(); i++) {
    JavaThread* thread = tlh.thread_at(i);
    JfrThreadLocal* jfr_thread_local = thread->jfr_thread_local();
    if (jfr_thread_local != nullptr && jfr_thread_local->has_timerid()) {
      timer_delete(jfr_thread_local->timerid());
      thread->jfr_thread_local()->unset_timerid();
    }
  }
}

void JfrCPUTimeThreadSampler::set_sampling_period(int64_t period_millis) {
  Atomic::store(&_period_millis, period_millis);
  MutexLocker tlock(Threads_lock);
  ThreadsListHandle tlh;
  for (size_t i = 0; i < tlh.length(); i++) {
    JavaThread* thread = tlh.thread_at(i);
    JfrThreadLocal* jfr_thread_local = thread->jfr_thread_local();
    if (jfr_thread_local != nullptr && jfr_thread_local->has_timerid()) {
      set_timer_time(jfr_thread_local->timerid());
    }
  }
}

#else

static bool _showed_warning = false;

void warn() {
  if (!_showed_warning) {
    warning("CPU time method sampling not supported in JFR on your platform");
    _showed_warning = true;
  }
}

static JfrCPUTimeThreadSampling* _instance = nullptr;

JfrCPUTimeThreadSampling& JfrCPUTimeThreadSampling::instance() {
  return *_instance;
}

JfrCPUTimeThreadSampling* JfrCPUTimeThreadSampling::create() {
  _instance = new JfrCPUTimeThreadSampling();
  return _instance;
}

void JfrCPUTimeThreadSampling::destroy() {
  delete _instance;
  _instance = nullptr;
}

void JfrCPUTimeThreadSampling::set_sample_period(int64_t period_millis) {
  if (period_millis != 0) {
    warn();
  }
}

void JfrCPUTimeThreadSampling::on_javathread_create(JavaThread* thread) {
}

void JfrCPUTimeThreadSampling::on_javathread_terminate(JavaThread* thread) {
}


#endif // defined(LINUX)