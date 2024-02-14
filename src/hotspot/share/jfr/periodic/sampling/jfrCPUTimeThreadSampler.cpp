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
#include "runtime/javaThread.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/semaphore.hpp"
#include "runtime/stackWatermark.hpp"
#include "runtime/suspendedThreadTask.hpp"
#include "runtime/threadCrashProtection.hpp"
#include "runtime/threadSMR.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/systemMemoryBarrier.hpp"
#include <algorithm>
#include <bits/types/timer_t.h>
#include <ctime>
#include <signal.h>
#include <time.h>

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
  const size_t _min_size; // for enqueue buffer monitoring
  int _cur_index;
  volatile bool _disenrolled;
  timer_t _timerid;

  const JfrBuffer* get_enqueue_buffer();
  const JfrBuffer* renew_if_full(const JfrBuffer* enqueue_buffer);

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
    int64_t get_java_period() const { return Atomic::load(&_java_period_millis); };
    int64_t get_native_period() const { return Atomic::load(&_native_period_millis); };

    void handle_timer_signal(void* context);
    void init_timer();
    void stop_timer();
    void set_sampling_period(int64_t period_millis);
};

static bool is_excluded(JavaThread* thread) {
  assert(thread != nullptr, "invariant");
  return thread->is_hidden_from_external_view() || thread->in_deopt_handler() || thread->jfr_thread_local()->is_excluded();
}


JfrCPUTimeThreadSampler::JfrCPUTimeThreadSampler(int64_t java_period_millis, int64_t native_period_millis, u4 max_traces, u4 max_frames_per_trace) :
  _sample(),
  _sampler_thread(nullptr),
  _queues(max_traces, max_frames_per_trace),
  _java_period_millis(java_period_millis),
  _native_period_millis(native_period_millis),
  _min_size(max_frames_per_trace * max_traces * 2 * wordSize), // each frame tags at most 2 words, min size is a full stacktrace
  _cur_index(-1),
  _disenrolled(true) {
  assert(_java_period_millis >= 0, "invariant");
  assert(_native_period_millis >= 0, "invariant");
}

void JfrCPUTimeThreadSampler::set_java_period(int64_t period_millis) {
  assert(period_millis >= 0, "invariant");
  Atomic::store(&_java_period_millis, period_millis);
  set_sampling_period(std::min(get_java_period(), get_native_period()));
}

void JfrCPUTimeThreadSampler::set_native_period(int64_t period_millis) {
  assert(period_millis >= 0, "invariant");
  Atomic::store(&_native_period_millis, period_millis);
  set_sampling_period(std::min(get_java_period(), get_native_period()));
}

void JfrCPUTimeThreadSampler::on_javathread_suspend(JavaThread* thread) {
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
    init_timer();
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
    if (trace == nullptr) {
      continue;
    }
    if (trace->error() != 0) {
      continue;
    }
    if (is_excluded(trace->sampled_thread())) {
      continue;
    }

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
    enqueue_buffer = renew_if_full(get_enqueue_buffer());
    trace->reset();
    _queues.fresh().enqueue(trace);
  }
}



void JfrCPUTimeThreadSampler::post_run() {
  this->NonJavaThread::post_run();
  delete this;
}

const JfrBuffer* JfrCPUTimeThreadSampler::get_enqueue_buffer() {
  const JfrBuffer* buffer = JfrTraceIdLoadBarrier::get_sampler_enqueue_buffer(this);
  return buffer != nullptr ? renew_if_full(buffer) : JfrTraceIdLoadBarrier::renew_sampler_enqueue_buffer(this);
}

const JfrBuffer* JfrCPUTimeThreadSampler::renew_if_full(const JfrBuffer* enqueue_buffer) {
  assert(enqueue_buffer != nullptr, "invariant");
  return enqueue_buffer->free_size() < _min_size ? JfrTraceIdLoadBarrier::renew_sampler_enqueue_buffer(this) : enqueue_buffer;
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

void handle_timer_signal(int signo, siginfo_t* info, void* context) {
  assert(_instance != nullptr, "invariant");
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

void JfrCPUTimeThreadSampler::init_timer() {
  // adopted from timer_create(2)
  _timerid = nullptr;
  sigset_t mask;
  struct sigaction sa;

  sa.sa_flags = SA_SIGINFO;
  sa.sa_sigaction = ::handle_timer_signal;
  sigemptyset(&sa.sa_mask);
  if (sigaction(SIG, &sa, NULL) == -1) {
    warning("Failed to install signal handler for thread sampling");
    return;
  }

  sigemptyset(&mask);
  sigaddset(&mask, SIG);
  if (sigprocmask(SIG_SETMASK, &mask, NULL) == -1) {
    warning("Failed to block signal for thread sampling");
    return;
  }

  timer_t t;

  struct sigevent sev;

  sev.sigev_notify = SIGEV_SIGNAL;
  sev.sigev_signo = SIG;
  sev.sigev_value.sival_ptr = &_timerid;
  sev.sigev_notify_attributes = nullptr;
  sev.sigev_notify_function = nullptr;

  if (timer_create(CLOCK_THREAD_CPUTIME_ID, &sev, &_timerid) == -1) {
    warning("Failed to create timer for thread sampling");
    return;
  }

  if (sigprocmask(SIG_UNBLOCK, &mask, NULL) == -1) {
    warning("Failed to unblock signal for thread sampling");
    return;
  }
}

void JfrCPUTimeThreadSampler::stop_timer() {
  if (_timerid != nullptr) {
    timer_delete(_timerid);
    _timerid = nullptr;
  }
}

void JfrCPUTimeThreadSampler::set_sampling_period(int64_t period_millis) {
      printf("Setting timer for thread sampling ms=%d\n", (int)period_millis);

  if (_timerid != nullptr) {
    printf("Setting timer for thread sampling inner ms=%d\n", (int)period_millis);
    struct itimerspec its;
    its.it_value.tv_sec = period_millis / 1000;
    its.it_value.tv_nsec = (period_millis % 1000) * 1000000;
    its.it_interval.tv_sec = its.it_value.tv_sec;
    its.it_interval.tv_nsec = its.it_value.tv_nsec;
    if (timer_settime(_timerid, 0, &its, NULL) == -1) {
      warning("Failed to set timer for thread sampling");
    }
  }
}