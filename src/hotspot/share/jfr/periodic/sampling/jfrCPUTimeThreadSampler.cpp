/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/recorder/service/jfrEvent.hpp"
#include "jfr/recorder/stacktrace/jfrStackTrace.hpp"
#include "jfr/utilities/jfrAllocation.hpp"
#include "memory/allocation.hpp"
#include "precompiled.hpp"
#include "classfile/javaThreadStatus.hpp"
#include "jfr/jfrEvents.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/periodic/sampling/jfrCallTrace.hpp"
#include "jfr/periodic/sampling/jfrCPUTimeThreadSampler.hpp"
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
#include "runtime/threadCrashProtection.hpp"
#include "runtime/threadSMR.hpp"
#include "signals_posix.hpp"
#include "utilities/concurrentHashTable.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/systemMemoryBarrier.hpp"
#include <algorithm>
#include <bits/types/clockid_t.h>
#include <bits/types/timer_t.h>
#include <climits>
#include <ctime>
#include <signal.h>
#include <time.h>
#include <sys/syscall.h>

enum JfrSampleType {
  NO_SAMPLE = 0,
  JAVA_SAMPLE = 1,
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
    case _thread_in_Java_trans:
      break;
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
    case _thread_blocked_trans:
    case _thread_blocked:
    case _thread_in_vm:
    case _thread_in_vm_trans:
    case _thread_in_Java_trans:
    case _thread_in_Java:
    case _thread_in_native_trans:
      break;
    case _thread_in_native:
      return true;
    default:
      ShouldNotReachHere();
      break;
  }
  return false;
}

/*static char* thread_state_string(JavaThread* thread) {
  assert(thread != nullptr, "invariant");
  // switch case return all state names
  switch(thread->thread_state()) {
    case _thread_new:
      return (char*)"new";
    case _thread_uninitialized:
      return (char*)"uninitialized";
    case _thread_new_trans:
      return (char*)"new_trans";
    case _thread_blocked_trans:
      return (char*)"blocked_trans";
    case _thread_blocked:
      return (char*)"blocked";
    case _thread_in_vm:
      return (char*)"in_vm";
    case _thread_in_vm_trans:
      return (char*)"in_vm_trans";
    case _thread_in_Java_trans:
      return (char*)"in_Java_trans";
    case _thread_in_Java:
      return (char*)"in_Java";
    case _thread_in_native_trans:
      return (char*)"in_native_trans";
    case _thread_in_native:
      return (char*)"in_native";
    default:
      ShouldNotReachHere();
      break;
  }
  return (char*)"unknown state";
}*/

// A trace of stack frames, contains all information
// collected in the signal handler, required to create
// a JFR event with a stack trace
class JfrCPUTimeTrace {
  friend class JfrTraceQueue;
  u4 _index;
  JfrStackFrame* _frames;
  JfrStackTrace _stacktrace;
  u4 _max_frames;
  // error code for the trace, 0 if no error
  u4 _error;

  JfrSampleType _type;
  JfrTicks _start_time;
  JfrTicks _end_time;
  JavaThread* _sampled_thread;


public:
  JfrCPUTimeTrace(u4 index, JfrStackFrame* frames, u4 max_frames):
    _index(index), _frames(frames), _stacktrace(_frames, max_frames),
    _max_frames(max_frames) {

    }

  JfrStackFrame* frames() { return _frames; }
  u4 max_frames() const { return _max_frames; }

  u4 error() const { return _error; }

  JfrSampleType type() const { return _type; }

  void set_start_time(JfrTicks start_time) { _start_time = start_time; }
  JfrTicks start_time() const { return _start_time; }
  void set_end_time(JfrTicks end_time) { _end_time = end_time; }
  JfrTicks end_time() const { return _end_time; }
  void set_sampled_thread(JavaThread* thread) { _sampled_thread = thread; }
  JavaThread* sampled_thread() const { return _sampled_thread; }

  void reset() {
    _error = 0;
    _stacktrace = JfrStackTrace(_frames, _max_frames);
    _type = NO_SAMPLE;
    _start_time = JfrTicks::now();
    _end_time = JfrTicks::now();
    _sampled_thread = nullptr;
  }

  JfrStackTrace& stacktrace() { return _stacktrace; }

  // Record a trace of the current thread
  void record_trace(void* ucontext) {

    _type = NO_SAMPLE;
    _error = 1;
    _start_time = JfrTicks::now();
    _end_time = JfrTicks::now();
    Thread* raw_thread = Thread::current_or_null_safe();
    JavaThread* jt;

    if (raw_thread == nullptr || !raw_thread->is_Java_thread() ||
        (jt = JavaThread::cast(raw_thread))->is_exiting()) {
      return;
    }
    ThreadInAsgct tia(jt);
    _sampled_thread = jt;
    if (thread_state_in_java(jt)) {
      record_java_trace(jt, ucontext);
    } else if (thread_state_in_native(jt)) {
      record_native_trace(jt);
    }
    _end_time = JfrTicks::now();
  }

private:

  void record_java_trace(JavaThread* jt, void* ucontext) {
    _type = JAVA_SAMPLE;
    if (!thread_state_in_java(jt)) {
      return;
    }
    JfrGetCallTrace trace(true, jt);
    frame topframe;
    if (trace.get_topframe(ucontext, topframe)) {
      if (_stacktrace.record_async(jt, topframe)) {
        _error = 0;
      }
    }
  }

  void record_native_trace(JavaThread* jt) {
    // When a thread is only attach it will be native without a last java frame
    _type = NATIVE_SAMPLE;
    _error = 1;
    if (!jt->has_last_Java_frame()) {
      return;
    }
    frame topframe = jt->last_frame();
    frame first_java_frame;
    Method* method = nullptr;
    JfrGetCallTrace gct(false, jt);
    if (!gct.find_top_frame(topframe, &method, first_java_frame)) {
      return;
    }
    if (method == nullptr) {
      return;
    }
    topframe = first_java_frame;
    _error = _stacktrace.record_async(jt, topframe) ? 0 : 1;
  }
};

// An atomic circular buffer of JfrTraces with a fixed size
// Does not own any frames
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
    if (is_empty()) {
      return nullptr;
    }
    JfrCPUTimeTrace* trace = _traces[_tail];
    _tail = (_tail + 1) % _size;
    return trace;
  }

  bool enqueue(JfrCPUTimeTrace* trace) {
    if (is_full()) {
      return false;
    }
    _traces[_head] = trace;
    _head = (_head + 1) % _size;
    return true;
  }

  u4 count() const { return (_head - _tail) % _size; }
};



// Two queues for sampling, fresh and filled
// at the start, all traces are in the fresh queue
class JfrTraceQueues {
  JfrStackFrame* _frames;
  JfrCPUTimeTrace* _traces;
  JfrTraceQueue _fresh;
  JfrTraceQueue  _filled;
  u4 _max_traces;
  u4 _max_frames_per_trace;

public:
  JfrTraceQueues(u4 max_traces, u4 max_frames_per_trace): _frames(JfrCHeapObj::new_array<JfrStackFrame>(max_traces * max_frames_per_trace)),
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
    JfrCHeapObj::free(_frames, sizeof(JfrStackFrame) * _max_traces * _max_frames_per_trace);
    JfrCHeapObj::free(_traces, sizeof(JfrCPUTimeTrace) * _max_traces);
  }

  JfrTraceQueue& fresh() { return _fresh; }
  JfrTraceQueue& filled() { return _filled; }

  u4 max_traces() const { return _max_traces; }
};


class JfrCPUTimeThreadSampler : public NonJavaThread {
  friend class JfrCPUTimeThreadSampling;
 private:
  Semaphore _sample;
  Thread* _sampler_thread;
  JfrTraceQueues _queues;
  int64_t _java_period_millis;
  int64_t _native_period_millis;
  const size_t _max_frames_per_trace; // for enqueue buffer monitoring
  int _cur_index;
  volatile bool _disenrolled;
  GrowableArray<JavaThread*> _threads;

  const JfrBuffer* get_enqueue_buffer();
  const JfrBuffer* renew_if_needed(size_t min_elements);

  JavaThread* next_thread(ThreadsList* t_list, JavaThread* first_sampled, JavaThread* current);
  void task_stacktrace(JfrSampleType type, JavaThread** last_thread);
  JfrCPUTimeThreadSampler(int64_t java_period_millis, int64_t native_period_millis, u4 max_traces, u4 max_frames_per_trace);

  void start_thread();

  void enroll();
  void disenroll();
  void set_java_period(int64_t period_millis);
  void set_native_period(int64_t period_millis);

  void process_trace_queue();
 protected:
    virtual void post_run();
   public:
    virtual const char* name() const { return "JFR CPU Time Thread Sampler"; }
    virtual const char* type_name() const { return "JfrCPUTimeThreadSampler"; }
    bool is_JfrSampler_thread() const { return true; }
    void run();
    static Monitor* transition_block() { return JfrCPUTimeThreadSampler_lock; }
    static void on_javathread_suspend(JavaThread* thread);
    void on_javathread_create(JavaThread* thread);
    timer_t create_timer_for_thread(JavaThread* thread);
    void set_timer_time(timer_t timerid);
    void on_javathread_terminate(JavaThread* thread);
    int64_t get_java_period() const { return Atomic::load(&_java_period_millis); };
    int64_t get_native_period() const { return Atomic::load(&_native_period_millis); };

    int64_t get_sampling_period() const {
      // return period in millis ignore 0s
      int64_t java_period_millis = get_java_period();
      int64_t native_period_millis = get_native_period();
      return java_period_millis == 0 || native_period_millis < java_period_millis ? native_period_millis : java_period_millis;
     }

    void handle_timer_signal(void* context);
    void init_timers();
    void stop_timer();
    void set_sampling_period(int64_t period_millis);
};

static bool is_excluded(JavaThread* thread) {
  return thread == nullptr || thread->is_hidden_from_external_view() || thread->in_deopt_handler() || thread->jfr_thread_local()->is_excluded();
}


JfrCPUTimeThreadSampler::JfrCPUTimeThreadSampler(int64_t java_period_millis, int64_t native_period_millis, u4 max_traces, u4 max_frames_per_trace) :
  _sample(),
  _sampler_thread(nullptr),
  _queues(max_traces, max_frames_per_trace),
  _java_period_millis(java_period_millis),
  _native_period_millis(native_period_millis),
  _max_frames_per_trace(max_frames_per_trace),
  _cur_index(-1),
  _disenrolled(true),
  _threads(100) {
  assert(_java_period_millis >= 0, "invariant");
  assert(_native_period_millis >= 0, "invariant");
}

void JfrCPUTimeThreadSampler::set_java_period(int64_t period_millis) {
  assert(period_millis >= 0, "invariant");
  Atomic::store(&_java_period_millis, period_millis);
  set_sampling_period(get_sampling_period());
}

void JfrCPUTimeThreadSampler::set_native_period(int64_t period_millis) {
  assert(period_millis >= 0, "invariant");
  Atomic::store(&_native_period_millis, period_millis);
  set_sampling_period(get_sampling_period());
}

void JfrCPUTimeThreadSampler::on_javathread_suspend(JavaThread* thread) {
}

void JfrCPUTimeThreadSampler::on_javathread_create(JavaThread* thread) {
  printf("JfrCPUTimeThreadSampler::on_javathread_create\n");
  if (thread->jfr_thread_local() != nullptr) {
    timer_t timerid = create_timer_for_thread(thread);
    printf("timerid %p\n", timerid);
    if (timerid != 0 && thread->jfr_thread_local() != nullptr) {
      printf("set timerid %p\n", timerid);
      thread->jfr_thread_local()->set_timerid(timerid);
      //MonitorLocker ml(JfrCPUTimeThreadSamplerThreadSet_lock, Mutex::_no_safepoint_check_flag);
      //_threads.append(thread);
    }
  }
}

void JfrCPUTimeThreadSampler::on_javathread_terminate(JavaThread* thread) {
  if (thread->jfr_thread_local() != nullptr && thread->jfr_thread_local()->timerid() != nullptr) {
    //timer_delete(thread->jfr_thread_local()->timerid());
    MonitorLocker ml(JfrCPUTimeThreadSamplerThreadSet_lock, Mutex::_no_safepoint_check_flag);
    for (int i = 0; i < _threads.length(); i++) {
      if (_threads.at(i) == thread) {
        _threads.at_put(i, _threads.at(_threads.length() - 1));
      }
    }
    _threads.trunc_to(_threads.length() - 1);
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
    renew_if_needed(_queues.fresh().count() + 1);
    init_timers();
    set_sampling_period(std::min(get_java_period(), get_native_period()));
  }
}

void JfrCPUTimeThreadSampler::disenroll() {
  if (!_disenrolled) {
    stop_timer();
    _sample.wait();
    _disenrolled = true;
    log_trace(jfr)("Disenrolling thread sampler");
  }
}

void JfrCPUTimeThreadSampler::run() {
  assert(_sampler_thread == nullptr, "invariant");

  _sampler_thread = this;
  while (true) {
    if (!_sample.trywait()) {
      // disenrolled
      _sample.wait();
    }
    _sample.signal();

    int64_t java_period_millis = get_java_period();
    java_period_millis = java_period_millis == 0 ? max_jlong : MAX2<int64_t>(java_period_millis, 1);
    int64_t native_period_millis = get_native_period();
    native_period_millis = native_period_millis == 0 ? max_jlong : MAX2<int64_t>(native_period_millis, 1);

    // If both periods are max_jlong, it implies the sampler is in the process of
    // disenrolling. Loop back for graceful disenroll by means of the semaphore.
    if (java_period_millis == max_jlong && native_period_millis == max_jlong) {
      continue;
    }

    // process all filled traces
    if (!_queues.filled().is_empty()) {
      printf("process_trace_queue\n");
      process_trace_queue();
    }


    // assumption: every sample_interval / max_traces should come a new sample
    int64_t sleep_to_next = MIN2<int64_t>(java_period_millis, native_period_millis) * NANOSECS_PER_MILLISEC / _queues.max_traces();
    os::naked_short_nanosleep(sleep_to_next);
  }
}

void JfrCPUTimeThreadSampler::process_trace_queue() {

  ResourceMark rm;

  const JfrBuffer* enqueue_buffer = get_enqueue_buffer();
  assert(enqueue_buffer != nullptr, "invariant");

  while (!_queues.filled().is_empty()) {
    JfrCPUTimeTrace* trace = _queues.filled().dequeue();
    printf("process_trace_queue dequeued trace %p thread %p error %d\n", trace, trace->sampled_thread(), trace->error());
    if (trace != nullptr && trace->error() == 0 && !is_excluded(trace->sampled_thread())) {
      printf("process_trace_queue trace start_time %lu end_time %lu\n", trace->start_time().milliseconds(), trace->end_time().milliseconds());
      // create event
      traceid id = JfrStackTraceRepository::add(trace->stacktrace());
      if (trace->type() == JAVA_SAMPLE) {
        EventCPUTimeExecutionSample event;
        event.set_starttime(trace->start_time());
        event.set_endtime(trace->end_time());
        event.set_sampledThread(JfrThreadLocal::thread_id(trace->sampled_thread()));
        event.set_state(static_cast<u8>(JavaThreadStatus::RUNNABLE));
        event.set_stackTrace(id);
        if (EventCPUTimeExecutionSample::is_enabled()) {
          event.commit();
        }
      } else if (trace->type() == NATIVE_SAMPLE) {
        EventCPUTimeNativeMethodSample event;
        event.set_starttime(trace->start_time());
        event.set_endtime(trace->end_time());
        event.set_sampledThread(JfrThreadLocal::thread_id(trace->sampled_thread()));
        event.set_state(static_cast<u8>(JavaThreadStatus::RUNNABLE));
        event.set_stackTrace(id);
        if (EventCPUTimeNativeMethodSample::is_enabled()) {
          event.commit();
        }
      }
    }
    enqueue_buffer = get_enqueue_buffer();
    trace->reset();
    renew_if_needed(_queues.fresh().count() + 1);
    _queues.fresh().enqueue(trace);
  }
  printf("fresh queue size %d\n", _queues.fresh().count());
}



void JfrCPUTimeThreadSampler::post_run() {
  this->NonJavaThread::post_run();
  delete this;
}

const JfrBuffer* JfrCPUTimeThreadSampler::get_enqueue_buffer() {
  return JfrTraceIdLoadBarrier::get_sampler_enqueue_buffer(this);
}

const JfrBuffer* JfrCPUTimeThreadSampler::renew_if_needed(size_t min_elements) {
  const JfrBuffer* enqueue_buffer = get_enqueue_buffer();
  size_t min_size = _max_frames_per_trace * min_elements * 2 * wordSize; // each frame tags at most 2 words, min size is a full stacktrace
  return enqueue_buffer->free_size() < min_size ? JfrTraceIdLoadBarrier::renew_sampler_enqueue_buffer(this, min_size * 2) : enqueue_buffer;
}

static JfrCPUTimeThreadSampling* _instance = nullptr;

JfrCPUTimeThreadSampling& JfrCPUTimeThreadSampling::instance() {
  return *_instance;
}

JfrCPUTimeThreadSampling* JfrCPUTimeThreadSampling::create() {
  assert(_instance == nullptr, "invariant");
  printf("create\n");
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
void assert_periods(const JfrCPUTimeThreadSampler* sampler, int64_t java_period_millis, int64_t native_period_millis) {
  assert(sampler != nullptr, "invariant");
  assert(sampler->get_java_period() == java_period_millis, "invariant");
  assert(sampler->get_native_period() == native_period_millis, "invariant");
}
#endif

static void log(int64_t java_period_millis, int64_t native_period_millis) {
  log_trace(jfr)("Updated thread sampler for java: " INT64_FORMAT "  ms, native " INT64_FORMAT " ms", java_period_millis, native_period_millis);
}

void JfrCPUTimeThreadSampling::create_sampler(int64_t java_period_millis, int64_t native_period_millis) {
  assert(_sampler == nullptr, "invariant");
  log_trace(jfr)("Creating thread sampler for java:" INT64_FORMAT " ms, native " INT64_FORMAT " ms", java_period_millis, native_period_millis);
  _sampler = new JfrCPUTimeThreadSampler(java_period_millis, native_period_millis, os::processor_count(), JfrOptionSet::stackdepth());
  _sampler->start_thread();
  _sampler->enroll();
}

void JfrCPUTimeThreadSampling::update_run_state(int64_t java_period_millis, int64_t native_period_millis) {
  if (java_period_millis > 0 || native_period_millis > 0) {
    if (_sampler == nullptr) {
      create_sampler(java_period_millis, native_period_millis);
    } else {
      _sampler->enroll();
    }
    DEBUG_ONLY(assert_periods(_sampler, java_period_millis, native_period_millis);)
    log(java_period_millis, native_period_millis);
    return;
  }
  if (_sampler != nullptr) {
    DEBUG_ONLY(assert_periods(_sampler, java_period_millis, native_period_millis);)
    _sampler->disenroll();
  }
}

void JfrCPUTimeThreadSampling::set_sampling_period(bool is_java_period, int64_t period_millis) {
  int64_t java_period_millis = 0;
  int64_t native_period_millis = 0;
  if (is_java_period) {
    java_period_millis = period_millis;
    if (_sampler != nullptr) {
      _sampler->set_java_period(java_period_millis);
      native_period_millis = _sampler->get_native_period();
    }
  } else {
    native_period_millis = period_millis;
    if (_sampler != nullptr) {
      _sampler->set_native_period(native_period_millis);
      java_period_millis = _sampler->get_java_period();
    }
  }
  update_run_state(java_period_millis, native_period_millis);
}

void JfrCPUTimeThreadSampling::set_java_sample_period(int64_t period_millis) {
  assert(period_millis >= 0, "invariant");
  if (_instance == nullptr && 0 == period_millis) {
    return;
  }
  instance().set_sampling_period(true, period_millis);
}

void JfrCPUTimeThreadSampling::set_native_sample_period(int64_t period_millis) {
  assert(period_millis >= 0, "invariant");
  if (_instance == nullptr && 0 == period_millis) {
    return;
  }
  instance().set_sampling_period(false, period_millis);
}

void JfrCPUTimeThreadSampling::on_javathread_suspend(JavaThread* thread) {
  JfrCPUTimeThreadSampler::on_javathread_suspend(thread);
}

void JfrCPUTimeThreadSampling::on_javathread_create(JavaThread *thread) {
    if (_instance != nullptr) {
    printf("        create + sampler %p\n", _instance->_sampler);
  }
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
  printf("handle_timer_signal\n");
  _instance->handle_timer_signal(context);
}


void JfrCPUTimeThreadSampling::handle_timer_signal(void* context) {
  assert(_sampler != nullptr, "invariant");
  _sampler->handle_timer_signal(context);
}

void JfrCPUTimeThreadSampler::handle_timer_signal(void* context) {
  JfrCPUTimeTrace* trace = this->_queues.fresh().dequeue();
  if (trace != nullptr) {
    trace->record_trace(context);
    this->_queues.filled().enqueue(trace);
  }
}

const int SIG = SIGPROF;

// libc doesn't allow to set thread specific timers, so we need to create a timer for each thread

clockid_t make_process_cpu_clock(unsigned int pid, clockid_t clock) {
  return (~pid << 3) | clock;
}

clockid_t make_thread_cpu_clock(unsigned int tid) {
  return make_process_cpu_clock(tid,  2 /*CPUCLOCK_SCHED*/ | 4/*CPUCLOCK_PERTHREAD_MASK*/);
}

void JfrCPUTimeThreadSampler::set_timer_time(timer_t timerid) {
  struct itimerspec its;
  printf("Set timer for thread %p java_period %lu native_persiod %lu\n", timerid, get_java_period(), get_native_period());
  int64_t period_millis = std::min(get_java_period(), get_native_period());
  its.it_interval.tv_sec = period_millis / 1000;
  its.it_interval.tv_nsec = (period_millis % 1000) * 1000000;
  its.it_value = its.it_interval;
  if (timer_settime(timerid, 0, &its, NULL) == -1) {
    warning("Failed to set timer for thread sampling");
  }
  printf("Set timer for thread %p to %lums\n", timerid, period_millis);
}

timer_t JfrCPUTimeThreadSampler::create_timer_for_thread(JavaThread* thread) {
  printf("Create timer for thread %p osthread %p\n", thread, thread->osthread());
  if (thread->osthread() == nullptr || thread->osthread()->thread_id() == 0){
    return 0;
  }
  timer_t t;
  OSThread::thread_id_t tid = thread->osthread()->thread_id();
  struct sigevent sev;
  sev.sigev_notify = SIGEV_THREAD_ID;
  sev.sigev_signo = SIG;
  sev.sigev_value.sival_ptr = &t;
  ((int*)&sev.sigev_notify)[1] = tid;
  clockid_t clock = make_thread_cpu_clock(tid);
  if (syscall(__NR_timer_create, clock, &sev, &t) < 0) {
    return 0;
  }
  set_timer_time(t);
  printf("Created timer for thread %d\n", tid);
  return t;
}

void JfrCPUTimeThreadSampler::init_timers() {
  // install sig handler for sig
  PosixSignals::install_generic_signal_handler(SIG, (void*)::handle_timer_signal);
}

void JfrCPUTimeThreadSampler::stop_timer() {
  MonitorLocker ml(JfrCPUTimeThreadSamplerThreadSet_lock, Mutex::_no_safepoint_check_flag);
  for (int i = 0; i < _threads.length(); i++) {
    timer_t timerid = _threads.at(i)->jfr_thread_local()->timerid();
    if (timerid != 0) {
      timer_delete(timerid);
      _threads.at(i)->jfr_thread_local()->set_timerid(0);
    }
  }
}

void JfrCPUTimeThreadSampler::set_sampling_period(int64_t period_millis) {
  MonitorLocker ml(JfrCPUTimeThreadSamplerThreadSet_lock, Mutex::_no_safepoint_check_flag);
  for (int i = 0; i < _threads.length(); i++) {
    timer_t timerid = _threads.at(i)->jfr_thread_local()->timerid();
    if (timerid != 0) {
      set_timer_time(timerid);
    }
  }
}