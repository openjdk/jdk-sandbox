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
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/packageEntry.hpp"
#include "classfile/symbolTable.hpp"
#include "jfr/jfr.hpp"
#include "jfr/jni/jfrGetAllEventClasses.hpp"
#include "jfr/leakprofiler/checkpoint/objectSampleCheckpoint.hpp"
#include "jfr/recorder/checkpoint/types/jfrTypeSet.hpp"
#include "jfr/recorder/checkpoint/types/jfrTypeSetUtils.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/utilities/jfrHashtable.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "jfr/writers/jfrTypeWriterHost.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/accessFlags.hpp"

typedef const Klass* KlassPtr;
typedef const PackageEntry* PkgPtr;
typedef const ModuleEntry* ModPtr;
typedef const ClassLoaderData* CldPtr;
typedef const Method* MethodPtr;
typedef const Symbol* SymbolPtr;
typedef const JfrSymbolId::SymbolEntry* SymbolEntryPtr;
typedef const JfrSymbolId::CStringEntry* CStringEntryPtr;

// incremented on each rotation
static u8 checkpoint_id = 1;

// creates a unique id by combining a checkpoint relative symbol id (2^24)
// with the current checkpoint id (2^40)
#define CREATE_SYMBOL_ID(sym_id) (((u8)((checkpoint_id << 24) | sym_id)))

static traceid create_symbol_id(traceid artifact_id) {
  return artifact_id != 0 ? CREATE_SYMBOL_ID(artifact_id) : 0;
}

static JfrCheckpointWriter* _writer = NULL;
static bool _class_unload = false;
static bool _flushpoint = false;
static JfrArtifactSet* _artifacts = NULL;
static JfrArtifactClosure* _subsystem_callback = NULL;

static bool current_epoch() {
  return _class_unload || _flushpoint;
}

static bool previous_epoch() {
  return !current_epoch();
}

static bool is_complete() {
  return !_artifacts->has_klass_entries() && current_epoch();
}

static traceid mark_symbol(KlassPtr klass) {
  return klass != NULL ? create_symbol_id(_artifacts->mark(klass)) : 0;
}

static traceid mark_symbol(Symbol* symbol) {
  return symbol != NULL ? create_symbol_id(_artifacts->mark(symbol)) : 0;
}

template <typename T>
static traceid artifact_id(const T* ptr) {
  assert(ptr != NULL, "invariant");
  return TRACE_ID(ptr);
}

static traceid package_id(KlassPtr klass) {
  assert(klass != NULL, "invariant");
  PkgPtr pkg_entry = klass->package();
  return pkg_entry != NULL ? artifact_id(pkg_entry) : 0;
}

static traceid module_id(PkgPtr pkg) {
  assert(pkg != NULL, "invariant");
  ModPtr module_entry = pkg->module();
  if (module_entry != NULL && module_entry->is_named()) {
    SET_TRANSIENT(module_entry);
    return artifact_id(module_entry);
  }
  return 0;
}

static traceid method_id(KlassPtr klass, MethodPtr method) {
  assert(klass != NULL, "invariant");
  assert(method != NULL, "invariant");
  return METHOD_ID(klass, method);
}

static traceid cld_id(CldPtr cld) {
  assert(cld != NULL, "invariant");
  if (cld->is_unsafe_anonymous()) {
    return 0;
  }
  SET_TRANSIENT(cld);
  return artifact_id(cld);
}

template <typename T>
static s4 get_flags(const T* ptr) {
  assert(ptr != NULL, "invariant");
  return ptr->access_flags().get_flags();
}

template <typename T>
static void set_serialized(const T* ptr) {
  assert(ptr != NULL, "invariant");
  SET_SERIALIZED(ptr);
  assert(IS_SERIALIZED(ptr), "invariant");
}

/*
 * In C++03, functions used as template parameters must have external linkage;
 * this restriction was removed in C++11. Change back to "static" and
 * rename functions when C++11 becomes available.
 *
 * The weird naming is an effort to decrease the risk of name clashes.
 */

int write__klass(JfrCheckpointWriter* writer, const void* k) {
  assert(writer != NULL, "invariant");
  assert(_artifacts != NULL, "invariant");
  assert(k != NULL, "invariant");
  KlassPtr klass = (KlassPtr)k;
  traceid pkg_id = 0;
  KlassPtr theklass = klass;
  if (theklass->is_objArray_klass()) {
    const ObjArrayKlass* obj_arr_klass = ObjArrayKlass::cast(klass);
    theklass = obj_arr_klass->bottom_klass();
  }
  if (theklass->is_instance_klass()) {
    pkg_id = package_id(theklass);
  } else {
    assert(theklass->is_typeArray_klass(), "invariant");
  }
  writer->write(artifact_id(klass));
  writer->write(cld_id(klass->class_loader_data()));
  writer->write(mark_symbol(klass));
  writer->write(pkg_id);
  writer->write(get_flags(klass));
  set_serialized(klass);
  return 1;
}

static void do_implied(Klass* klass) {
  assert(klass != NULL, "invariant");
  if (klass->is_subclass_of(SystemDictionary::ClassLoader_klass()) || klass == SystemDictionary::Object_klass()) {
    _subsystem_callback->do_artifact(klass);
  }
}

static void do_unloaded_klass(Klass* klass) {
  assert(klass != NULL, "invariant");
  assert(_subsystem_callback != NULL, "invariant");
  if (IS_JDK_JFR_EVENT_SUBKLASS(klass)) {
    JfrEventClasses::increment_unloaded_event_class();
  }
  if (USED_THIS_EPOCH(klass)) {
    ObjectSampleCheckpoint::on_klass_unload(klass);
    _subsystem_callback->do_artifact(klass);
    return;
  }
  do_implied(klass);
}

static void do_klass(Klass* klass) {
  assert(klass != NULL, "invariant");
  assert(_subsystem_callback != NULL, "invariant");
  if (_flushpoint) {
    if (USED_THIS_EPOCH(klass)) {
      _subsystem_callback->do_artifact(klass);
      return;
    }
  } else {
    if (USED_PREV_EPOCH(klass)) {
      _subsystem_callback->do_artifact(klass);
      return;
    }
  }
  do_implied(klass);
}

static void do_klasses() {
  if (_class_unload) {
    ClassLoaderDataGraph::classes_unloading_do(&do_unloaded_klass);
    return;
  }
  ClassLoaderDataGraph::classes_do(&do_klass);
}

typedef SerializePredicate<KlassPtr> KlassPredicate;
typedef JfrPredicatedTypeWriterImplHost<KlassPtr, KlassPredicate, write__klass> KlassWriterImpl;
typedef JfrTypeWriterHost<KlassWriterImpl, TYPE_CLASS> KlassWriter;
typedef CompositeFunctor<KlassPtr, KlassWriter, KlassArtifactRegistrator> KlassWriterRegistration;
typedef JfrArtifactCallbackHost<KlassPtr, KlassWriterRegistration> KlassCallback;

static bool write_klasses() {
  assert(!_artifacts->has_klass_entries(), "invariant");
  assert(_writer != NULL, "invariant");
  KlassArtifactRegistrator reg(_artifacts);
  KlassWriter kw(_writer, _class_unload);
  KlassWriterRegistration kwr(&kw, &reg);
  KlassCallback callback(&kwr);
  _subsystem_callback = &callback;
  do_klasses();
  if (is_complete()) {
    return false;
  }
  _artifacts->tally(kw);
  return true;
}

template <typename T>
static void do_previous_epoch_artifact(JfrArtifactClosure* callback, T* value) {
  assert(callback != NULL, "invariant");
  assert(value != NULL, "invariant");
  if (USED_PREV_EPOCH(value)) {
    callback->do_artifact(value);
    assert(IS_NOT_SERIALIZED(value), "invariant");
    return;
  }
  if (IS_SERIALIZED(value)) {
    CLEAR_SERIALIZED(value);
  }
  assert(IS_NOT_SERIALIZED(value), "invariant");
}

int write__package(JfrCheckpointWriter* writer, const void* p) {
  assert(writer != NULL, "invariant");
  assert(_artifacts != NULL, "invariant");
  assert(p != NULL, "invariant");
  PkgPtr pkg = (PkgPtr)p;
  writer->write(artifact_id(pkg));
  writer->write(mark_symbol(pkg->name()));
  writer->write(module_id(pkg));
  writer->write((bool)pkg->is_exported());
  set_serialized(pkg);
  return 1;
}

static void do_package(PackageEntry* entry) {
  do_previous_epoch_artifact(_subsystem_callback, entry);
}

static void do_packages() {
  ClassLoaderDataGraph::packages_do(&do_package);
}

class PackageFieldSelector {
 public:
  typedef PkgPtr TypePtr;
  static TypePtr select(KlassPtr klass) {
    assert(klass != NULL, "invariant");
    return ((InstanceKlass*)klass)->package();
  }
};

typedef SerializePredicate<PkgPtr> PackagePredicate;
typedef JfrPredicatedTypeWriterImplHost<PkgPtr, PackagePredicate, write__package> PackageWriterImpl;
typedef JfrTypeWriterHost<PackageWriterImpl, TYPE_PACKAGE> PackageWriter;
typedef CompositeFunctor<PkgPtr, PackageWriter, ClearArtifact<PkgPtr> > PackageWriterWithClear;
typedef KlassToFieldEnvelope<PackageFieldSelector, PackageWriter> KlassPackageWriter;
typedef JfrArtifactCallbackHost<PkgPtr, PackageWriterWithClear> PackageCallback;

static void write_packages() {
  assert(_writer != NULL, "invariant");
  PackageWriter pw(_writer, _class_unload);
  KlassPackageWriter kpw(&pw);
  _artifacts->iterate_klasses(kpw);
  if (previous_epoch()) {
    ClearArtifact<PkgPtr> clear;
    PackageWriterWithClear pwwc(&pw, &clear);
    PackageCallback callback(&pwwc);
    _subsystem_callback = &callback;
    do_packages();
  }
  _artifacts->tally(pw);
}

int write__module(JfrCheckpointWriter* writer, const void* m) {
  assert(m != NULL, "invariant");
  assert(_artifacts != NULL, "invariant");
  ModPtr mod = (ModPtr)m;
  writer->write(artifact_id(mod));
  writer->write(mark_symbol(mod->name()));
  writer->write(mark_symbol(mod->version()));
  writer->write(mark_symbol(mod->location()));
  writer->write(cld_id(mod->loader_data()));
  set_serialized(mod);
  return 1;
}

static void do_module(ModuleEntry* entry) {
  do_previous_epoch_artifact(_subsystem_callback, entry);
}

static void do_modules() {
  ClassLoaderDataGraph::modules_do(&do_module);
}

class ModuleFieldSelector {
 public:
  typedef ModPtr TypePtr;
  static TypePtr select(KlassPtr klass) {
    assert(klass != NULL, "invariant");
    PkgPtr pkg = klass->package();
    return pkg != NULL ? pkg->module() : NULL;
  }
};

typedef SerializePredicate<ModPtr> ModulePredicate;
typedef JfrPredicatedTypeWriterImplHost<ModPtr, ModulePredicate, write__module> ModuleWriterImpl;
typedef JfrTypeWriterHost<ModuleWriterImpl, TYPE_MODULE> ModuleWriter;
typedef CompositeFunctor<ModPtr, ModuleWriter, ClearArtifact<ModPtr> > ModuleWriterWithClear;
typedef JfrArtifactCallbackHost<ModPtr, ModuleWriterWithClear> ModuleCallback;
typedef KlassToFieldEnvelope<ModuleFieldSelector, ModuleWriter> KlassModuleWriter;

static void write_modules() {
  assert(_writer != NULL, "invariant");
  ModuleWriter mw(_writer, _class_unload);
  KlassModuleWriter kmw(&mw);
  _artifacts->iterate_klasses(kmw);
  if (previous_epoch()) {
    ClearArtifact<ModPtr> clear;
    ModuleWriterWithClear mwwc(&mw, &clear);
    ModuleCallback callback(&mwwc);
    _subsystem_callback = &callback;
    do_modules();
  }
  _artifacts->tally(mw);
}

int write__classloader(JfrCheckpointWriter* writer, const void* c) {
  assert(c != NULL, "invariant");
  CldPtr cld = (CldPtr)c;
  assert(!cld->is_unsafe_anonymous(), "invariant");
  // class loader type
  const Klass* class_loader_klass = cld->class_loader_klass();
  if (class_loader_klass == NULL) {
    // (primordial) boot class loader
    writer->write(artifact_id(cld)); // class loader instance id
    writer->write((traceid)0);  // class loader type id (absence of)
    writer->write(create_symbol_id(1)); // 1 maps to synthetic name -> "bootstrap"
  } else {
    writer->write(artifact_id(cld)); // class loader instance id
    writer->write(artifact_id(class_loader_klass)); // class loader type id
    writer->write(mark_symbol(cld->name())); // class loader instance name
  }
  set_serialized(cld);
  return 1;
}

static void do_class_loader_data(ClassLoaderData* cld) {
  do_previous_epoch_artifact(_subsystem_callback, cld);
}

class CldFieldSelector {
 public:
  typedef CldPtr TypePtr;
  static TypePtr select(KlassPtr klass) {
    assert(klass != NULL, "invariant");
    CldPtr cld = klass->class_loader_data();
    return cld->is_unsafe_anonymous() ? NULL : cld;
  }
};

class CLDCallback : public CLDClosure {
 public:
  CLDCallback() {}
  void do_cld(ClassLoaderData* cld) {
    assert(cld != NULL, "invariant");
    if (cld->is_unsafe_anonymous()) {
      return;
    }
    do_class_loader_data(cld);
  }
};

static void do_class_loaders() {
  CLDCallback cld_cb;
  ClassLoaderDataGraph::loaded_cld_do(&cld_cb);
}

typedef SerializePredicate<CldPtr> CldPredicate;
typedef JfrPredicatedTypeWriterImplHost<CldPtr, CldPredicate, write__classloader> CldWriterImpl;
typedef JfrTypeWriterHost<CldWriterImpl, TYPE_CLASSLOADER> CldWriter;
typedef CompositeFunctor<CldPtr, CldWriter, ClearArtifact<CldPtr> > CldWriterWithClear;
typedef JfrArtifactCallbackHost<CldPtr, CldWriterWithClear> CldCallback;
typedef KlassToFieldEnvelope<CldFieldSelector, CldWriter> KlassCldWriter;

static void write_classloaders() {
  assert(_writer != NULL, "invariant");
  CldWriter cldw(_writer, _class_unload);
  KlassCldWriter kcw(&cldw);
  _artifacts->iterate_klasses(kcw);
  if (previous_epoch()) {
    ClearArtifact<CldPtr> clear;
    CldWriterWithClear cldwwc(&cldw, &clear);
    CldCallback callback(&cldwwc);
    _subsystem_callback = &callback;
    do_class_loaders();
  }
  _artifacts->tally(cldw);
}

static u1 get_visibility(MethodPtr method) {
  assert(method != NULL, "invariant");
  return const_cast<Method*>(method)->is_hidden() ? (u1)1 : (u1)0;
}

template <>
void set_serialized<Method>(MethodPtr method) {
  assert(method != NULL, "invariant");
  SET_METHOD_SERIALIZED(method);
  assert(IS_METHOD_SERIALIZED(method), "invariant");
}

int write__method(JfrCheckpointWriter* writer, const void* m) {
  assert(writer != NULL, "invariant");
  assert(_artifacts != NULL, "invariant");
  assert(m != NULL, "invariant");
  MethodPtr method = (MethodPtr)m;
  KlassPtr klass = method->method_holder();
  assert(klass != NULL, "invariant");
  assert(METHOD_USED_ANY_EPOCH(klass), "invariant");
  writer->write(method_id(klass, method));
  writer->write(artifact_id(klass));
  writer->write(mark_symbol(method->name()));
  writer->write(mark_symbol(method->signature()));
  writer->write((u2)get_flags(method));
  writer->write(get_visibility(method));
  set_serialized(method);
  return 1;
}

template <typename MethodCallback, typename KlassCallback>
class MethodIteratorHost {
 private:
  MethodCallback _method_cb;
  KlassCallback _klass_cb;
  MethodUsedPredicate _method_used_predicate;
  MethodFlagPredicate _method_flag_predicate;
 public:
  MethodIteratorHost(JfrCheckpointWriter* writer,
                     bool current_epoch = false,
                     bool class_unload = false,
                     bool skip_header = false) :
    _method_cb(writer, class_unload, skip_header),
    _klass_cb(writer, class_unload, skip_header),
    _method_used_predicate(current_epoch),
    _method_flag_predicate(current_epoch) {}

  bool operator()(KlassPtr klass) {
    if (_method_used_predicate(klass)) {
      assert(METHOD_AND_CLASS_USED_ANY_EPOCH(klass), "invariant");
      const InstanceKlass* const ik = InstanceKlass::cast(klass);
      const int len = ik->methods()->length();
      for (int i = 0; i < len; ++i) {
        MethodPtr method = ik->methods()->at(i);
        if (_method_flag_predicate(method)) {
          _method_cb(method);
        }
      }
    }
    return _klass_cb(klass);
  }

  int count() const { return _method_cb.count(); }
  void add(int count) { _method_cb.add(count); }
};

template <typename T, template <typename> class Impl>
class Wrapper {
  Impl<T> _t;
 public:
  Wrapper(JfrCheckpointWriter*, bool, bool) : _t() {}
  bool operator()(T const& value) {
    return _t(value);
  }
};

typedef SerializePredicate<MethodPtr> MethodPredicate;
typedef JfrPredicatedTypeWriterImplHost<MethodPtr, MethodPredicate, write__method> MethodWriterImplTarget;
typedef JfrTypeWriterHost<MethodWriterImplTarget, TYPE_METHOD> MethodWriterImpl;
typedef Wrapper<KlassPtr, Stub> KlassCallbackStub;
typedef MethodIteratorHost<MethodWriterImpl, KlassCallbackStub> MethodWriter;

static void write_methods() {
  assert(_writer != NULL, "invariant");
  MethodWriter mw(_writer, current_epoch(), _class_unload);
  _artifacts->iterate_klasses(mw);
  _artifacts->tally(mw);
}

template <>
void set_serialized<JfrSymbolId::SymbolEntry>(SymbolEntryPtr ptr) {
  assert(ptr != NULL, "invariant");
  ptr->set_serialized();
  assert(ptr->is_serialized(), "invariant");
}

template <>
void set_serialized<JfrSymbolId::CStringEntry>(CStringEntryPtr ptr) {
  assert(ptr != NULL, "invariant");
  ptr->set_serialized();
  assert(ptr->is_serialized(), "invariant");
}

int write__symbol(JfrCheckpointWriter* writer, const void* e) {
  assert(writer != NULL, "invariant");
  assert(e != NULL, "invariant");
  ResourceMark rm;
  SymbolEntryPtr entry = (SymbolEntryPtr)e;
  writer->write(create_symbol_id(entry->id()));
  writer->write(entry->value()->as_C_string());
  set_serialized(entry);
  return 1;
}

int write__cstring(JfrCheckpointWriter* writer, const void* e) {
  assert(writer != NULL, "invariant");
  assert(e != NULL, "invariant");
  CStringEntryPtr entry = (CStringEntryPtr)e;
  writer->write(create_symbol_id(entry->id()));
  writer->write(entry->value());
  set_serialized(entry);
  return 1;
}

typedef SymbolPredicate<SymbolEntryPtr> SymPredicate;
typedef JfrPredicatedTypeWriterImplHost<SymbolEntryPtr, SymPredicate, write__symbol> SymbolEntryWriterImpl;
typedef JfrTypeWriterHost<SymbolEntryWriterImpl, TYPE_SYMBOL> SymbolEntryWriter;
typedef SymbolPredicate<CStringEntryPtr> CStringPredicate;
typedef JfrPredicatedTypeWriterImplHost<CStringEntryPtr, CStringPredicate, write__cstring> CStringEntryWriterImpl;
typedef JfrTypeWriterHost<CStringEntryWriterImpl, TYPE_SYMBOL> CStringEntryWriter;

static void write_symbols() {
  assert(_writer != NULL, "invariant");
  SymbolEntryWriter symbol_writer(_writer, _class_unload);
  _artifacts->iterate_symbols(symbol_writer);
  CStringEntryWriter cstring_writer(_writer, _class_unload, true); // skip header
  _artifacts->iterate_cstrings(cstring_writer);
  symbol_writer.add(cstring_writer.count());
  _artifacts->tally(symbol_writer);
}

typedef Wrapper<KlassPtr, ClearArtifact> ClearKlassBits;
typedef Wrapper<MethodPtr, ClearArtifact> ClearMethodFlag;
typedef MethodIteratorHost<ClearMethodFlag, ClearKlassBits> ClearKlassAndMethods;

static size_t teardown() {
  assert(_artifacts != NULL, "invariant");
  const size_t total_count = _artifacts->total_count();
  if (previous_epoch()) {
    assert(_writer != NULL, "invariant");
    ClearKlassAndMethods clear(_writer);
    _artifacts->iterate_klasses(clear);
    _artifacts->clear();
    ++checkpoint_id;
  }
  return total_count;
}

static void setup(JfrCheckpointWriter* writer, bool class_unload, bool flushpoint) {
  _writer = writer;
  _class_unload = class_unload;
  _flushpoint = flushpoint;
  if (_artifacts == NULL) {
    _artifacts = new JfrArtifactSet(class_unload);
  } else {
    _artifacts->initialize(class_unload);
  }
  assert(_artifacts != NULL, "invariant");
  assert(!_artifacts->has_klass_entries(), "invariant");
}

/**
 * Write all "tagged" (in-use) constant artifacts and their dependencies.
 */
size_t JfrTypeSet::serialize(JfrCheckpointWriter* writer, bool class_unload, bool flushpoint) {
  assert(writer != NULL, "invariant");
  ResourceMark rm;
  setup(writer, class_unload, flushpoint);
  // write order is important because an individual write step
  // might tag an artifact to be written in a subsequent step
  if (!write_klasses()) {
    return 0;
  }
  write_packages();
  write_modules();
  write_classloaders();
  write_methods();
  write_symbols();
  return teardown();
}
