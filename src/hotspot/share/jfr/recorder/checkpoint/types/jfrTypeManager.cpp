/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/jfr.hpp"
#include "jfr/leakprofiler/leakProfiler.hpp"
#include "jfr/leakprofiler/checkpoint/objectSampleCheckpoint.hpp"
#include "jfr/metadata/jfrSerializer.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/checkpoint/types/jfrType.hpp"
#include "jfr/recorder/checkpoint/types/jfrTypeManager.hpp"
#include "jfr/utilities/jfrDoublyLinkedList.hpp"
#include "jfr/utilities/jfrIterator.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/exceptions.hpp"
#include "runtime/semaphore.hpp"

class JfrSerializerRegistration : public JfrCHeapObj {
 private:
  JfrSerializerRegistration* _next;
  JfrSerializerRegistration* _prev;
  JfrSerializer* _serializer;
  mutable JfrBlobHandle _cache;
  JfrTypeId _id;
  bool _permit_cache;

 public:
  JfrSerializerRegistration(JfrTypeId id, bool permit_cache, JfrSerializer* serializer) :
    _next(NULL), _prev(NULL), _serializer(serializer), _cache(), _id(id), _permit_cache(permit_cache) {}

  ~JfrSerializerRegistration() {
    delete _serializer;
  }

  JfrSerializerRegistration* next() const {
    return _next;
  }

  void set_next(JfrSerializerRegistration* next) {
    _next = next;
  }

  JfrSerializerRegistration* prev() const {
    return _prev;
  }

  void set_prev(JfrSerializerRegistration* prev) {
    _prev = prev;
  }

  JfrTypeId id() const {
    return _id;
  }

  void invoke(JfrCheckpointWriter& writer) const;

  void on_rotation() const;
};

void JfrSerializerRegistration::invoke(JfrCheckpointWriter& writer) const {
  if (_cache.valid()) {
    writer.increment();
    _cache->write(writer);
    return;
  }
  const JfrCheckpointContext ctx = writer.context();
  // serialize the type id before invoking callback
  writer.write_type(_id);
  const intptr_t start = writer.current_offset();
  // invoke the serializer routine
  _serializer->serialize(writer);
  if (start == writer.current_offset() ) {
    // the serializer implementation did nothing, rewind to restore
    writer.set_context(ctx);
    return;
  }
  if (_permit_cache) {
    _cache = writer.copy(&ctx);
  }
}

void JfrSerializerRegistration::on_rotation() const {
  _serializer->on_rotation();
}

class SerializerRegistrationGuard : public StackObj {
 private:
  static Semaphore _mutex_semaphore;
 public:
  SerializerRegistrationGuard() {
    _mutex_semaphore.wait();
  }
  ~SerializerRegistrationGuard() {
    _mutex_semaphore.signal();
  }
};

Semaphore SerializerRegistrationGuard::_mutex_semaphore(1);

typedef JfrDoublyLinkedList<JfrSerializerRegistration> List;
typedef StopOnNullIterator<const List> Iterator;
static List types;

void JfrTypeManager::clear() {
  SerializerRegistrationGuard guard;
  Iterator iter(types);
  JfrSerializerRegistration* registration;
  while (iter.has_next()) {
    registration = types.remove(iter.next());
    assert(registration != NULL, "invariant");
    delete registration;
  }
}

static bool new_registration = false;

void JfrTypeManager::write_types(JfrCheckpointWriter& writer) {
  SerializerRegistrationGuard guard;
  const Iterator iter(types);
  while (iter.has_next()) {
    iter.next()->invoke(writer);
  }
  new_registration = false;
}

static void serialize_threads(JfrCheckpointWriter& writer) {
  JfrThreadConstantSet thread_set;
  writer.write_type(TYPE_THREAD);
  thread_set.serialize(writer);
}

static void serialize_thread_groups(JfrCheckpointWriter& writer) {
  JfrThreadGroupConstant thread_group_set;
  writer.write_type(TYPE_THREADGROUP);
  thread_group_set.serialize(writer);
}

void JfrTypeManager::write_threads(JfrCheckpointWriter& writer) {
  serialize_threads(writer);
  serialize_thread_groups(writer);
}

void JfrTypeManager::notify_types_on_rotation() {
  const Iterator iter(types);
  while (iter.has_next()) {
    iter.next()->on_rotation();
  }
}

void JfrTypeManager::write_type_set() {
  assert(!SafepointSynchronize::is_at_safepoint(), "invariant");
  // can safepoint here because of Module_lock
  MutexLocker cld_lock(SafepointSynchronize::is_at_safepoint() ? NULL : ClassLoaderDataGraph_lock);
  MutexLocker lock(SafepointSynchronize::is_at_safepoint() ? NULL : Module_lock);
  if (!LeakProfiler::is_running()) {
    JfrCheckpointWriter writer;
    TypeSet set;
    set.serialize(writer);
    return;
  }
  JfrCheckpointWriter leakp_writer;
  JfrCheckpointWriter writer;
  TypeSet set(&leakp_writer);
  set.serialize(writer);
  ObjectSampleCheckpoint::on_type_set(leakp_writer);
}

void JfrTypeManager::write_type_set_for_unloaded_classes() {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  JfrCheckpointWriter writer;
  const JfrCheckpointContext ctx = writer.context();
  ClassUnloadTypeSet class_unload_set;
  class_unload_set.serialize(writer);
  if (LeakProfiler::is_running()) {
    ObjectSampleCheckpoint::on_type_set_unload(writer);
  }
  if (!Jfr::is_recording()) {
    // discard anything written
    writer.set_context(ctx);
  }
}

size_t JfrTypeManager::flush_type_set() {
  assert(!SafepointSynchronize::is_at_safepoint(), "invariant");
  JfrCheckpointWriter writer;
  FlushTypeSet flush;
  flush.serialize(writer);
  return flush.elements();
}

void JfrTypeManager::create_thread_blob(Thread* t) {
  assert(t != NULL, "invariant");
  ResourceMark rm(t);
  HandleMark hm(t);
  JfrThreadConstant type_thread(t);
  JfrCheckpointWriter writer(t, true, THREADS);
  writer.write_type(TYPE_THREAD);
  type_thread.serialize(writer);
  // create and install a checkpoint blob
  t->jfr_thread_local()->set_thread_blob(writer.move());
  assert(t->jfr_thread_local()->has_thread_blob(), "invariant");
}

void JfrTypeManager::write_thread_checkpoint(Thread* t) {
  assert(t != NULL, "invariant");
  ResourceMark rm(t);
  HandleMark hm(t);
  JfrThreadConstant type_thread(t);
  JfrCheckpointWriter writer(t, true, THREADS);
  writer.write_type(TYPE_THREAD);
  type_thread.serialize(writer);
}

#ifdef ASSERT
static void assert_not_registered_twice(JfrTypeId id, List& list) {
  const Iterator iter(list);
  while (iter.has_next()) {
    assert(iter.next()->id() != id, "invariant");
  }
}
#endif

static bool register_type(JfrTypeId id, bool permit_cache, JfrSerializer* serializer) {
  assert(serializer != NULL, "invariant");
  JfrSerializerRegistration* const registration = new JfrSerializerRegistration(id, permit_cache, serializer);
  if (registration == NULL) {
    delete serializer;
    return false;
  }

  assert(!types.in_list(registration), "invariant");
  DEBUG_ONLY(assert_not_registered_twice(id, types);)
  if (Jfr::is_recording()) {
    JfrCheckpointWriter writer(STATICS);
    registration->invoke(writer);
    new_registration = true;
  }
  types.prepend(registration);
  return true;
}

bool JfrTypeManager::initialize() {
  SerializerRegistrationGuard guard;
  register_type(TYPE_FLAGVALUEORIGIN, true, new FlagValueOriginConstant());
  register_type(TYPE_INFLATECAUSE, true, new MonitorInflateCauseConstant());
  register_type(TYPE_GCCAUSE, true, new GCCauseConstant());
  register_type(TYPE_GCNAME, true, new GCNameConstant());
  register_type(TYPE_GCWHEN, true, new GCWhenConstant());
  register_type(TYPE_GCTHRESHOLDUPDATER, true, new GCThresholdUpdaterConstant());
  register_type(TYPE_METADATATYPE, true, new MetadataTypeConstant());
  register_type(TYPE_METASPACEOBJECTTYPE, true, new MetaspaceObjectTypeConstant());
  register_type(TYPE_REFERENCETYPE, true, new ReferenceTypeConstant());
  register_type(TYPE_NARROWOOPMODE, true, new NarrowOopModeConstant());
  register_type(TYPE_COMPILERPHASETYPE, true, new CompilerPhaseTypeConstant());
  register_type(TYPE_CODEBLOBTYPE, true, new CodeBlobTypeConstant());
  register_type(TYPE_VMOPERATIONTYPE, true, new VMOperationTypeConstant());
  register_type(TYPE_THREADSTATE, true, new ThreadStateConstant());
  return true;
}

// implementation for the static registration function exposed in the JfrSerializer api
bool JfrSerializer::register_serializer(JfrTypeId id, bool permit_cache, JfrSerializer* serializer) {
  SerializerRegistrationGuard guard;
  return register_type(id, permit_cache, serializer);
}

bool JfrTypeManager::is_new_constant_registered() {
  if (new_registration) {
    SerializerRegistrationGuard guard;
    new_registration = false;
    return true;
  }
  return false;
}
