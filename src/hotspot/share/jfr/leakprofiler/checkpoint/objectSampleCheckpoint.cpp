/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "jfr/jfrEvents.hpp"
#include "jfr/leakprofiler/chains/edgeStore.hpp"
#include "jfr/leakprofiler/chains/objectSampleMarker.hpp"
#include "jfr/leakprofiler/checkpoint/objectSampleCheckpoint.hpp"
#include "jfr/leakprofiler/checkpoint/objectSampleWriter.hpp"
#include "jfr/leakprofiler/leakProfiler.hpp"
#include "jfr/leakprofiler/sampling/objectSample.hpp"
#include "jfr/leakprofiler/sampling/objectSampler.hpp"
#include "jfr/leakprofiler/utilities/rootType.hpp"
#include "jfr/metadata/jfrSerializer.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/stacktrace/jfrStackTraceRepository.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.hpp"
#include "utilities/growableArray.hpp"

static bool predicate(GrowableArray<traceid>* set, traceid id) {
  assert(set != NULL, "invariant");
  bool found = false;
  set->find_sorted<traceid, compare_traceid>(id, found);
  return found;
}

static bool mutable_predicate(GrowableArray<traceid>* set, traceid id) {
  assert(set != NULL, "invariant");
  bool found = false;
  const int location = set->find_sorted<traceid, compare_traceid>(id, found);
  if (!found) {
    set->insert_before(location, id);
  }
  return found;
}

static bool add(GrowableArray<traceid>* set, traceid id) {
  assert(set != NULL, "invariant");
  return mutable_predicate(set, id);
}

const int initial_array_size = 256;

template <typename T>
static GrowableArray<T>* c_heap_allocate_array(int size = initial_array_size) {
  return new (ResourceObj::C_HEAP, mtTracing) GrowableArray<T>(size, true, mtTracing);
}

template <typename T>
static GrowableArray<T>* resource_allocate_array(int size = initial_array_size) {
  return new GrowableArray<T>(size);
}

static void sort_array(GrowableArray<traceid>* ar) {
  assert(ar != NULL, "invariant");
  ar->sort(sort_traceid);
}

static GrowableArray<traceid>* unloaded_thread_id_set = NULL;

class ThreadIdExclusiveAccess : public StackObj {
 private:
  static Semaphore _mutex_semaphore;
 public:
  ThreadIdExclusiveAccess() { _mutex_semaphore.wait(); }
  ~ThreadIdExclusiveAccess() { _mutex_semaphore.signal(); }
};

Semaphore ThreadIdExclusiveAccess::_mutex_semaphore(1);

static void add_to_unloaded_thread_set(traceid tid) {
  ThreadIdExclusiveAccess lock;
  if (unloaded_thread_id_set == NULL) {
    unloaded_thread_id_set = c_heap_allocate_array<traceid>();
  }
  add(unloaded_thread_id_set, tid);
}

static bool has_thread_exited(traceid tid) {
  assert(tid != 0, "invariant");
  return unloaded_thread_id_set != NULL && predicate(unloaded_thread_id_set, tid);
}

static GrowableArray<traceid>* unloaded_klass_set = NULL;

static void sort_unloaded_klass_set() {
  if (unloaded_klass_set != NULL) {
    sort_array(unloaded_klass_set);
  }
}

static void add_to_unloaded_klass_set(traceid klass_id) {
  if (unloaded_klass_set == NULL) {
    unloaded_klass_set = c_heap_allocate_array<traceid>();
  }
  unloaded_klass_set->append(klass_id);
}

void ObjectSampleCheckpoint::on_klass_unload(const Klass* k) {
  assert(k != NULL, "invariant");
  add_to_unloaded_klass_set(TRACE_ID(k));
}

static bool is_klass_unloaded(traceid klass_id) {
  return unloaded_klass_set != NULL && predicate(unloaded_klass_set, klass_id);
}

static GrowableArray<traceid>* id_set = NULL;
static GrowableArray<traceid>* stack_trace_id_set = NULL;

static bool is_processed(traceid id) {
  assert(id != 0, "invariant");
  assert(id_set != NULL, "invariant");
  return mutable_predicate(id_set, id);
}

static bool is_processed_or_unloaded(traceid klass_id) {
  assert(klass_id != 0, "invariant");
  return is_processed(klass_id) || is_klass_unloaded(klass_id);
}

static bool should_process(traceid klass_id) {
  return klass_id != 0 && !is_processed_or_unloaded(klass_id);
}

static bool is_stack_trace_processed(traceid stack_trace_id) {
  assert(stack_trace_id != 0, "invariant");
  assert(stack_trace_id_set != NULL, "invariant");
  return mutable_predicate(stack_trace_id_set, stack_trace_id);
}

template <typename Processor>
static void do_samples(ObjectSample* sample, const ObjectSample* const end, Processor& processor) {
  assert(sample != NULL, "invariant");
  while (sample != end) {
    processor.sample_do(sample);
    sample = sample->next();
  }
}

template <typename Processor>
static void iterate_samples(Processor& processor, bool all = false) {
  ObjectSampler* const sampler = ObjectSampler::sampler();
  assert(sampler != NULL, "invariant");
  ObjectSample* const last = sampler->last();
  assert(last != NULL, "invariant");
  do_samples(last, all ? NULL : sampler->last_resolved(), processor);
}

void ObjectSampleCheckpoint::on_thread_exit(JavaThread* jt) {
  assert(jt != NULL, "invariant");
  if (LeakProfiler::is_running()) {
    add_to_unloaded_thread_set(jt->jfr_thread_local()->thread_id());
  }
}

class CheckpointBlobInstaller {
 private:
  const JfrCheckpointBlobHandle& _cp;
 public:
  CheckpointBlobInstaller(const JfrCheckpointBlobHandle& cp) : _cp(cp) {}
  void sample_do(ObjectSample* sample) {
    if (!sample->is_dead()) {
      sample->set_klass_checkpoint(_cp);
    }
  }
};

static void install_checkpoint_blob(JfrCheckpointWriter& writer) {
  assert(writer.has_data(), "invariant");
  const JfrCheckpointBlobHandle h_cp = writer.copy();
  CheckpointBlobInstaller installer(h_cp);
  iterate_samples(installer, true);
}

void ObjectSampleCheckpoint::on_type_set_unload(JfrCheckpointWriter& writer) {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(LeakProfiler::is_running(), "invariant");
  if (writer.has_data() && ObjectSampler::sampler()->last() != NULL) {
    install_checkpoint_blob(writer);
  }
}

class SampleMarker {
 private:
  ObjectSampleMarker& _marker;
  jlong _last_sweep;
  int _count;
 public:
  SampleMarker(ObjectSampleMarker& marker, jlong last_sweep) : _marker(marker), _last_sweep(last_sweep), _count(0) {}
  void sample_do(ObjectSample* sample) {
    if (sample->is_alive_and_older_than(_last_sweep)) {
      _marker.mark(sample->object());
      ++_count;
    }
  }
  int count() const {
    return _count;
  }
};

int ObjectSampleCheckpoint::save_mark_words(const ObjectSampler* sampler, ObjectSampleMarker& marker, bool emit_all) {
  assert(sampler != NULL, "invariant");
  if (sampler->last() == NULL) {
    return 0;
  }
  SampleMarker sample_marker(marker, emit_all ? max_jlong : sampler->last_sweep().value());
  iterate_samples(sample_marker, true);
  return sample_marker.count();
}

#ifdef ASSERT
static traceid get_klass_id(const Klass* k) {
  assert(k != NULL, "invariant");
  return TRACE_ID(k);
}
#endif

static traceid get_klass_id(traceid method_id) {
  assert(method_id != 0, "invariant");
  return method_id >> TRACE_ID_SHIFT;
}

static int get_method_id_num(traceid method_id) {
  return (int)(method_id & METHOD_ID_NUM_MASK);
}

static Method* lookup_method_in_klasses(Klass* klass, int orig_method_id_num) {
  assert(klass != NULL, "invariant");
  assert(!is_klass_unloaded(get_klass_id(klass)), "invariant");
  while (klass != NULL) {
    if (klass->is_instance_klass()) {
      Method* const m = InstanceKlass::cast(klass)->method_with_orig_idnum(orig_method_id_num);
      if (m != NULL) {
        return m;
      }
    }
    klass = klass->super();
  }
  return NULL;
}

static Method* lookup_method_in_interfaces(Klass* klass, int orig_method_id_num) {
  assert(klass != NULL, "invariant");
  const Array<InstanceKlass*>* const all_ifs = InstanceKlass::cast(klass)->transitive_interfaces();
  const int num_ifs = all_ifs->length();
  for (int i = 0; i < num_ifs; i++) {
    InstanceKlass* const ik = all_ifs->at(i);
    Method* const m = ik->method_with_orig_idnum(orig_method_id_num);
    if (m != NULL) {
      return m;
    }
  }
  return NULL;
}

static Method* lookup_method(Klass* klass, int orig_method_id_num) {
  Method* m = lookup_method_in_klasses(klass, orig_method_id_num);
  if (m == NULL) {
    m = lookup_method_in_interfaces(klass, orig_method_id_num);
  }
  assert(m != NULL, "invariant");
  return m;
}

static void write_stack_trace(traceid id, bool reached_root, u4 nr_of_frames, JfrCheckpointWriter* writer) {
  assert(writer != NULL, "invariant");
  writer->write(id);
  writer->write((u1)!reached_root);
  writer->write(nr_of_frames);
}

static void write_stack_frame(const JfrStackFrame* frame, JfrCheckpointWriter* writer) {
  assert(frame != NULL, "invariant");
  frame->write(*writer);
}

bool ObjectSampleCheckpoint::tag(const JfrStackTrace* trace, JfrCheckpointWriter* writer /* NULL */) {
  assert(trace != NULL, "invariant");
  if (is_stack_trace_processed(trace->id())) {
    return false;
  }
  if (writer != NULL) {
    // JfrStackTrace
    write_stack_trace(trace->id(), trace->_reached_root, trace->_nr_of_frames, writer);
  }
  traceid last_id = 0;
  for (u4 i = 0; i < trace->_nr_of_frames; ++i) {
    if (writer != NULL) {
      // JfrStackFrame(s)
      write_stack_frame(&trace->_frames[i], writer);
    }
    const traceid method_id = trace->_frames[i]._methodid;
    if (last_id == method_id || is_processed(method_id) || is_klass_unloaded(get_klass_id(method_id))) {
      continue;
    }
    last_id = method_id;
    InstanceKlass* const ik = trace->_frames[i]._klass;
    assert(ik != NULL, "invariant");
    JfrTraceId::use(ik, lookup_method(ik, get_method_id_num(method_id)));
  }
  return true;
}

static bool stack_trace_precondition(const ObjectSample* sample) {
  assert(sample != NULL, "invariant");
  return sample->has_stack_trace_id() && !sample->is_dead();
}

class StackTraceTagger {
 private:
  JfrStackTraceRepository& _stack_trace_repo;
 public:
  StackTraceTagger(JfrStackTraceRepository& stack_trace_repo) : _stack_trace_repo(stack_trace_repo) {}
  void sample_do(ObjectSample* sample) {
    if (stack_trace_precondition(sample)) {
      assert(sample->stack_trace_id() == sample->stack_trace()->id(), "invariant");
      ObjectSampleCheckpoint::tag(sample->stack_trace(), NULL);
    }
  }
};

static void tag_old_stack_traces(ObjectSample* last_resolved, JfrStackTraceRepository& stack_trace_repo) {
  assert(last_resolved != NULL, "invariant");
  assert(stack_trace_id_set != NULL, "invariant");
  assert(stack_trace_id_set->is_empty(), "invariant");
  StackTraceTagger tagger(stack_trace_repo);
  do_samples(last_resolved, NULL, tagger);
}

class StackTraceResolver {
 private:
  JfrStackTraceRepository& _stack_trace_repo;
 public:
  StackTraceResolver(JfrStackTraceRepository& stack_trace_repo) : _stack_trace_repo(stack_trace_repo) {}
  void install_to_sample(ObjectSample* sample, const JfrStackTrace* stack_trace);
  void sample_do(ObjectSample* sample) {
    if (stack_trace_precondition(sample)) {
      install_to_sample(sample, _stack_trace_repo.lookup(sample->stack_trace_hash(), sample->stack_trace_id()));
    }
  }
};

#ifdef ASSERT
static void validate_stack_trace(const ObjectSample* sample, const JfrStackTrace* stack_trace) {
  assert(sample != NULL, "invariant");
  assert(!sample->is_dead(), "invariant");
  assert(stack_trace != NULL, "invariant");
  assert(stack_trace->hash() == sample->stack_trace_hash(), "invariant");
  assert(stack_trace->id() == sample->stack_trace_id(), "invariant");
}
#endif

void StackTraceResolver::install_to_sample(ObjectSample* sample, const JfrStackTrace* stack_trace) {
  DEBUG_ONLY(validate_stack_trace(sample, stack_trace));
  JfrStackTrace* const sample_stack_trace = const_cast<JfrStackTrace*>(sample->stack_trace());
  if (sample_stack_trace != NULL) {
    if (sample_stack_trace->id() != stack_trace->id()) {
      *sample_stack_trace = *stack_trace; // copy
    }
  } else {
    sample->set_stack_trace(new JfrStackTrace(stack_trace->id(), *stack_trace, NULL)); // new
  }
  assert(sample->stack_trace() != NULL, "invariant");
}

static void allocate_traceid_working_sets() {
  const int set_size = JfrOptionSet::old_object_queue_size();
  stack_trace_id_set = resource_allocate_array<traceid>(set_size);
  id_set = resource_allocate_array<traceid>(set_size);
  sort_unloaded_klass_set();
}

static void resolve_stack_traces(JfrStackTraceRepository& stack_trace_repo) {
  StackTraceResolver stack_trace_resolver(stack_trace_repo);
  iterate_samples(stack_trace_resolver);
}

// caller needs ResourceMark
void ObjectSampleCheckpoint::on_rotation(ObjectSampler* sampler, JfrStackTraceRepository& stack_trace_repo) {
  assert(sampler != NULL, "invariant");
  assert(LeakProfiler::is_running(), "invariant");
  const ObjectSample* const last = sampler->last();
  if (last == NULL) {
    // nothing to process
    return;
  }
  ObjectSample* const last_resolved = const_cast<ObjectSample*>(sampler->last_resolved());
  if (last_resolved != NULL) {
    allocate_traceid_working_sets();
    tag_old_stack_traces(last_resolved, stack_trace_repo);
  }
  if (last != last_resolved) {
    resolve_stack_traces(stack_trace_repo);
    sampler->set_last_resolved(last);
  }
}

class RootSystemType : public JfrSerializer {
 public:
  void serialize(JfrCheckpointWriter& writer) {
    const u4 nof_root_systems = OldObjectRoot::_number_of_systems;
    writer.write_count(nof_root_systems);
    for (u4 i = 0; i < nof_root_systems; ++i) {
      writer.write_key(i);
      writer.write(OldObjectRoot::system_description((OldObjectRoot::System)i));
    }
  }
};

class RootType : public JfrSerializer {
 public:
  void serialize(JfrCheckpointWriter& writer) {
    const u4 nof_root_types = OldObjectRoot::_number_of_types;
    writer.write_count(nof_root_types);
    for (u4 i = 0; i < nof_root_types; ++i) {
      writer.write_key(i);
      writer.write(OldObjectRoot::type_description((OldObjectRoot::Type)i));
    }
  }
};

static void register_serializers() {
  static bool is_registered = false;
  if (!is_registered) {
    JfrSerializer::register_serializer(TYPE_OLDOBJECTROOTSYSTEM, true, new RootSystemType());
    JfrSerializer::register_serializer(TYPE_OLDOBJECTROOTTYPE, true, new RootType());
    is_registered = true;
  }
}

static void reset_blob_write_state(const ObjectSample* sample) {
  assert(sample != NULL, "invariant");
  if (sample->has_thread_checkpoint()) {
    sample->thread_checkpoint()->reset_write_state();
  }
  if (sample->has_klass_checkpoint()) {
    sample->klass_checkpoint()->reset_write_state();
  }
}

static void write_thread_blob(const ObjectSample* sample, JfrCheckpointWriter& writer) {
  if (sample->has_thread_checkpoint() && has_thread_exited(sample->thread_id())) {
    sample->thread_checkpoint()->exclusive_write(writer);
  }
}

static void write_klass_blob(const ObjectSample* sample, JfrCheckpointWriter& writer) {
  if (sample->has_klass_checkpoint()) {
    sample->klass_checkpoint()->exclusive_write(writer);
  }
}

static void write_blobs(const ObjectSample* sample, JfrCheckpointWriter& writer) {
  assert(sample != NULL, "invariant");
  write_thread_blob(sample, writer);
  write_klass_blob(sample, writer);
}

class CheckpointBlobWriter {
 private:
  const ObjectSampler* _sampler;
  JfrCheckpointWriter& _writer;
  const jlong _last_sweep;
 public:
  CheckpointBlobWriter(const ObjectSampler* sampler, JfrCheckpointWriter& writer, jlong last_sweep) :
    _sampler(sampler), _writer(writer), _last_sweep(last_sweep) {}
  void sample_do(ObjectSample* sample) {
    if (sample->is_alive_and_older_than(_last_sweep)) {
      write_blobs(sample, _writer);
    }
  }
};

class CheckpointBlobStateReset {
 private:
  const ObjectSampler* _sampler;
  const jlong _last_sweep;
 public:
  CheckpointBlobStateReset(const ObjectSampler* sampler, jlong last_sweep) : _sampler(sampler), _last_sweep(last_sweep) {}
  void sample_do(ObjectSample* sample) {
    if (sample->is_alive_and_older_than(_last_sweep)) {
      reset_blob_write_state(sample);
    }
  }
};

static void reset_write_state_for_blobs(const ObjectSampler* sampler, jlong last_sweep) {
  CheckpointBlobStateReset state_reset(sampler, last_sweep);
  iterate_samples(state_reset, true);
}

static void write_sample_blobs(const ObjectSampler* sampler, jlong last_sweep, Thread* thread) {
  JfrCheckpointWriter writer(thread, false);
  CheckpointBlobWriter cbw(sampler, writer, last_sweep);
  iterate_samples(cbw, true);
  reset_write_state_for_blobs(sampler, last_sweep);
}

class StackTraceWriter {
 private:
  JfrStackTraceRepository& _stack_trace_repo;
  JfrCheckpointWriter& _writer;
  const jlong _last_sweep;
  int _count;
 public:
  StackTraceWriter(JfrStackTraceRepository& stack_trace_repo, JfrCheckpointWriter& writer, jlong last_sweep) :
    _stack_trace_repo(stack_trace_repo), _writer(writer), _last_sweep(last_sweep), _count(0) {}
  void sample_do(ObjectSample* sample) {
    if (sample->is_alive_and_older_than(_last_sweep)) {
      if (stack_trace_precondition(sample)) {
        assert(sample->stack_trace_id() == sample->stack_trace()->id(), "invariant");
        if (ObjectSampleCheckpoint::tag(sample->stack_trace(), &_writer)) {
          ++_count;
        }
      }
    }
  }
  int count() const {
    return _count;
  }
};

static void write_and_tag_stack_traces(const ObjectSampler* sampler, JfrStackTraceRepository& repo, jlong last_sweep, Thread* thread) {
  assert(sampler != NULL, "invariant");
  allocate_traceid_working_sets();
  resolve_stack_traces(repo);
  JfrCheckpointWriter writer(thread);
  const JfrCheckpointContext ctx = writer.context();
  writer.write_type(TYPE_STACKTRACE);
  const jlong count_offset = writer.reserve(sizeof(u4));
  StackTraceWriter sw(repo, writer, last_sweep);
  do_samples(sampler->last(), NULL, sw);
  if (sw.count() == 0) {
    writer.set_context(ctx);
    return;
  }
  writer.write_count((u4)sw.count(), count_offset);
}

void ObjectSampleCheckpoint::write(const ObjectSampler* sampler, EdgeStore* edge_store, bool emit_all, Thread* thread) {
  assert(sampler != NULL, "invariant");
  assert(edge_store != NULL, "invariant");
  assert(thread != NULL, "invariant");
  register_serializers();
  // sample set is predicated on time of last sweep
  const jlong last_sweep = emit_all ? max_jlong : sampler->last_sweep().value();
  write_and_tag_stack_traces(sampler, JfrStackTraceRepository::instance(), last_sweep, thread);
  write_sample_blobs(sampler, last_sweep, thread);
  // write reference chains
  if (!edge_store->is_empty()) {
    JfrCheckpointWriter writer(thread);
    ObjectSampleWriter osw(writer, edge_store);
    edge_store->iterate(osw);
  }
}
