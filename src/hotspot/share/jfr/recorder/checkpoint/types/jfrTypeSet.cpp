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
#include "classfile/systemDictionary.hpp"
#include "jfr/jfr.hpp"
#include "jfr/jni/jfrGetAllEventClasses.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/checkpoint/types/jfrTypeSet.hpp"
#include "jfr/recorder/checkpoint/types/jfrTypeSetUtils.hpp"
#include "jfr/recorder/checkpoint/types/jfrTypeSetWriter.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/storage/jfrBuffer.hpp"
#include "jfr/utilities/jfrHashtable.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.inline.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/accessFlags.hpp"

// incremented on each checkpoint
static u8 checkpoint_id = 0;

// creates a unique id by combining a checkpoint relative symbol id (2^24)
// with the current checkpoint id (2^40)
#define CREATE_SYMBOL_ID(sym_id) (((u8)((checkpoint_id << 24) | sym_id)))

typedef const Klass* KlassPtr;
typedef const PackageEntry* PkgPtr;
typedef const ModuleEntry* ModPtr;
typedef const ClassLoaderData* CldPtr;
typedef const Method* MethodPtr;
typedef const Symbol* SymbolPtr;
typedef const JfrSymbolId::SymbolEntry* SymbolEntryPtr;
typedef const JfrSymbolId::CStringEntry* CStringEntryPtr;

static traceid module_id(PkgPtr pkg) {
  assert(pkg != NULL, "invariant");
  ModPtr module_entry = pkg->module();
  return module_entry != NULL && module_entry->is_named() ? TRACE_ID(module_entry) : 0;
}

static traceid package_id(KlassPtr klass) {
  assert(klass != NULL, "invariant");
  PkgPtr pkg_entry = klass->package();
  return pkg_entry == NULL ? 0 : TRACE_ID(pkg_entry);
}

static traceid cld_id(CldPtr cld) {
  assert(cld != NULL, "invariant");
  return cld->is_unsafe_anonymous() ? 0 : TRACE_ID(cld);
}

static void tag_leakp_klass_artifacts(KlassPtr k, bool current_epoch) {
  assert(k != NULL, "invariant");
  PkgPtr pkg = k->package();
  if (pkg != NULL) {
    tag_leakp_artifact(pkg, current_epoch);
    ModPtr module = pkg->module();
    if (module != NULL) {
      tag_leakp_artifact(module, current_epoch);
    }
  }
  CldPtr cld = k->class_loader_data();
  assert(cld != NULL, "invariant");
  if (!cld->is_unsafe_anonymous()) {
    tag_leakp_artifact(cld, current_epoch);
  }
}

class TagLeakpKlassArtifact {
  bool _current_epoch;
 public:
  TagLeakpKlassArtifact(bool current_epoch) : _current_epoch(current_epoch) {}
  bool operator()(KlassPtr klass) {
    if (_current_epoch) {
      if (LEAKP_USED_THIS_EPOCH(klass)) {
        tag_leakp_klass_artifacts(klass, _current_epoch);
      }
    } else {
      if (LEAKP_USED_PREV_EPOCH(klass)) {
        tag_leakp_klass_artifacts(klass, _current_epoch);
      }
    }
    return true;
  }
};

/*
 * In C++03, functions used as template parameters must have external linkage;
 * this restriction was removed in C++11. Change back to "static" and
 * rename functions when C++11 becomes available.
 *
 * The weird naming is an effort to decrease the risk of name clashes.
 */

int write__artifact__klass(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, KlassPtr klass) {
  assert(writer != NULL, "invariant");
  assert(artifacts != NULL, "invariant");
  assert(klass != NULL, "invariant");
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
  const traceid symbol_id = artifacts->mark(klass);
  assert(symbol_id > 0, "need to have an address for symbol!");
  writer->write(TRACE_ID(klass));
  writer->write(cld_id(klass->class_loader_data()));
  writer->write((traceid)CREATE_SYMBOL_ID(symbol_id));
  writer->write(pkg_id);
  writer->write((s4)klass->access_flags().get_flags());
  return 1;
}

int write__artifact__klass__leakp(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* k) {
  assert(k != NULL, "invariant");
  KlassPtr klass = (KlassPtr)k;
  return write__artifact__klass(writer, artifacts, klass);
}

int write__artifact__klass__serialize(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* k) {
  assert(k != NULL, "invariant");
  KlassPtr klass = (KlassPtr)k;
  int result = write__artifact__klass(writer, artifacts, klass);
  if (IS_NOT_SERIALIZED(klass)) {
    SET_SERIALIZED(klass);
  }
  assert(IS_SERIALIZED(klass), "invariant");
  return result;
}

typedef LeakPredicate<KlassPtr> LeakKlassPredicate;
typedef SerializePredicate<KlassPtr> KlassPredicate;
typedef JfrPredicatedArtifactWriterImplHost<KlassPtr, LeakKlassPredicate, write__artifact__klass__leakp> LeakKlassWriterImpl;
typedef JfrArtifactWriterHost<LeakKlassWriterImpl, TYPE_CLASS> LeakKlassWriter;
typedef JfrPredicatedArtifactWriterImplHost<KlassPtr, KlassPredicate, write__artifact__klass__serialize> KlassWriterImpl;
typedef JfrArtifactWriterHost<KlassWriterImpl, TYPE_CLASS> KlassWriter;

int write__artifact__method(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, MethodPtr method) {
  assert(writer != NULL, "invariant");
  assert(artifacts != NULL, "invariant");
  const traceid method_name_symbol_id = artifacts->mark(method->name());
  assert(method_name_symbol_id > 0, "invariant");
  const traceid method_sig_symbol_id = artifacts->mark(method->signature());
  assert(method_sig_symbol_id > 0, "invariant");
  KlassPtr klass = method->method_holder();
  assert(klass != NULL, "invariant");
  assert(METHOD_USED_ANY_EPOCH(klass), "invariant");
  writer->write((u8)METHOD_ID(klass, method));
  writer->write((u8)TRACE_ID(klass));
  writer->write((u8)CREATE_SYMBOL_ID(method_name_symbol_id));
  writer->write((u8)CREATE_SYMBOL_ID(method_sig_symbol_id));
  writer->write((u2)method->access_flags().get_flags());
  writer->write(const_cast<Method*>(method)->is_hidden() ? (u1)1 : (u1)0);
  return 1;
}

int write__artifact__method__leakp(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* m) {
  assert(m != NULL, "invariant");
  MethodPtr method = (MethodPtr)m;
  return write__artifact__method(writer, artifacts, method);
}

int write__artifact__method__serialize(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* m) {
  assert(m != NULL, "invariant");
  MethodPtr method = (MethodPtr)m;
  int result = write__artifact__method(writer, artifacts, method);
  if (METHOD_NOT_SERIALIZED(method)) {
    SET_METHOD_SERIALIZED(method);
  }
  assert(IS_METHOD_SERIALIZED(method), "invariant");
  return result;
}

typedef JfrArtifactWriterImplHost<MethodPtr, write__artifact__method__leakp> LeakpMethodWriterImplTarget;
typedef JfrArtifactWriterHost<LeakpMethodWriterImplTarget, TYPE_METHOD> LeakpMethodWriterImpl;
typedef SerializePredicate<MethodPtr> MethodPredicate;
typedef JfrPredicatedArtifactWriterImplHost<MethodPtr, MethodPredicate, write__artifact__method__serialize> MethodWriterImplTarget;
typedef JfrArtifactWriterHost<MethodWriterImplTarget, TYPE_METHOD> MethodWriterImpl;

int write__artifact__package(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, PkgPtr pkg) {
  assert(writer != NULL, "invariant");
  assert(artifacts != NULL, "invariant");
  assert(pkg != NULL, "invariant");
  Symbol* const pkg_name = pkg->name();
  const traceid package_name_symbol_id = pkg_name != NULL ? artifacts->mark(pkg_name) : 0;
  assert(package_name_symbol_id > 0, "invariant");
  writer->write((traceid)TRACE_ID(pkg));
  writer->write((traceid)CREATE_SYMBOL_ID(package_name_symbol_id));
  writer->write(module_id(pkg));
  writer->write((bool)pkg->is_exported());
  return 1;
}

int write__artifact__package__leakp(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* p) {
  assert(p != NULL, "invariant");
  PkgPtr pkg = (PkgPtr)p;
  return write__artifact__package(writer, artifacts, pkg);
}

int write__artifact__package__serialize(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* p) {
  assert(p != NULL, "invariant");
  PkgPtr pkg = (PkgPtr)p;
  int result = write__artifact__package(writer, artifacts, pkg);
  if (IS_NOT_SERIALIZED(pkg)) {
    SET_SERIALIZED(pkg);
  }
  assert(IS_SERIALIZED(pkg), "invariant");
  return result;
}

typedef LeakPredicate<PkgPtr> LeakPackagePredicate;
//int _compare_pkg_ptr_(PkgPtr const& lhs, PkgPtr const& rhs) { return lhs > rhs ? 1 : (lhs < rhs) ? -1 : 0; }
//typedef UniquePredicate<PkgPtr, _compare_pkg_ptr_> PackagePredicate;
typedef SerializePredicate<PkgPtr> PackagePredicate;
typedef JfrPredicatedArtifactWriterImplHost<PkgPtr, LeakPackagePredicate, write__artifact__package__leakp> LeakPackageWriterImpl;
typedef JfrPredicatedArtifactWriterImplHost<PkgPtr, PackagePredicate, write__artifact__package__serialize> PackageWriterImpl;
typedef JfrArtifactWriterHost<LeakPackageWriterImpl, TYPE_PACKAGE> LeakPackageWriter;
typedef JfrArtifactWriterHost<PackageWriterImpl, TYPE_PACKAGE> PackageWriter;

int write__artifact__module(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, ModPtr entry) {
  assert(entry != NULL, "invariant");
  Symbol* const module_name = entry->name();
  const traceid module_name_symbol_id = module_name != NULL ? artifacts->mark(module_name) : 0;
  Symbol* const module_version = entry->version();
  const traceid module_version_symbol_id = module_version != NULL ? artifacts->mark(module_version) : 0;
  Symbol* const module_location = entry->location();
  const traceid module_location_symbol_id = module_location != NULL ? artifacts->mark(module_location) : 0;
  writer->write((traceid)TRACE_ID(entry));
  writer->write(module_name_symbol_id == 0 ? (traceid)0 : (traceid)CREATE_SYMBOL_ID(module_name_symbol_id));
  writer->write(module_version_symbol_id == 0 ? (traceid)0 : (traceid)CREATE_SYMBOL_ID(module_version_symbol_id));
  writer->write(module_location_symbol_id == 0 ? (traceid)0 : (traceid)CREATE_SYMBOL_ID(module_location_symbol_id));
  writer->write(cld_id(entry->loader_data()));
  return 1;
}

int write__artifact__module__leakp(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* m) {
  assert(m != NULL, "invariant");
  ModPtr entry = (ModPtr)m;
  return write__artifact__module(writer, artifacts, entry);
}

int write__artifact__module__serialize(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* m) {
  assert(m != NULL, "invariant");
  ModPtr entry = (ModPtr)m;
  int result = write__artifact__module(writer, artifacts, entry);
  CldPtr cld = entry->loader_data();
  assert(cld != NULL, "invariant");
  if (IS_NOT_SERIALIZED(cld)) {
    if (!cld->is_unsafe_anonymous()) {
      SET_USED_PREV_EPOCH(cld);
    }
  }
  if (IS_NOT_SERIALIZED(entry)) {
    SET_SERIALIZED(entry);
  }
  assert(IS_SERIALIZED(entry), "invariant");
  return result;
}

typedef LeakPredicate<ModPtr> LeakModulePredicate;
//int _compare_mod_ptr_(ModPtr const& lhs, ModPtr const& rhs) { return lhs > rhs ? 1 : (lhs < rhs) ? -1 : 0; }
//typedef UniquePredicate<ModPtr, _compare_mod_ptr_> ModulePredicate;
typedef SerializePredicate<ModPtr> ModulePredicate;
typedef JfrPredicatedArtifactWriterImplHost<ModPtr, LeakModulePredicate, write__artifact__module__leakp> LeakModuleWriterImpl;
typedef JfrPredicatedArtifactWriterImplHost<ModPtr, ModulePredicate, write__artifact__module__serialize> ModuleWriterImpl;
typedef JfrArtifactWriterHost<LeakModuleWriterImpl, TYPE_MODULE> LeakModuleWriter;
typedef JfrArtifactWriterHost<ModuleWriterImpl, TYPE_MODULE> ModuleWriter;

int write__artifact__classloader(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, CldPtr cld) {
  assert(cld != NULL, "invariant");
  assert(!cld->is_unsafe_anonymous(), "invariant");
  const traceid cld_id = TRACE_ID(cld);
  // class loader type
  const Klass* class_loader_klass = cld->class_loader_klass();
  if (class_loader_klass == NULL) {
    // (primordial) boot class loader
    writer->write(cld_id); // class loader instance id
    writer->write((traceid)0);  // class loader type id (absence of)
    writer->write((traceid)CREATE_SYMBOL_ID(1)); // 1 maps to synthetic name -> "bootstrap"
  } else {
    Symbol* symbol_name = cld->name();
    const traceid symbol_name_id = symbol_name != NULL ? artifacts->mark(symbol_name) : 0;
    writer->write(cld_id); // class loader instance id
    writer->write(TRACE_ID(class_loader_klass)); // class loader type id
    writer->write(symbol_name_id == 0 ? (traceid)0 :
      (traceid)CREATE_SYMBOL_ID(symbol_name_id)); // class loader instance name
  }
  return 1;
}

int write__artifact__classloader__leakp(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* c) {
  assert(c != NULL, "invariant");
  CldPtr cld = (CldPtr)c;
  int result = write__artifact__classloader(writer, artifacts, cld);
  if (IS_NOT_LEAKP_SERIALIZED(cld)) {
    SET_LEAKP_SERIALIZED(cld);
  }
  assert(IS_LEAKP_SERIALIZED(cld), "invariant");
  return result;
}

int write__artifact__classloader__serialize(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* c) {
  assert(c != NULL, "invariant");
  CldPtr cld = (CldPtr)c;
  int result = write__artifact__classloader(writer, artifacts, cld);
  if (IS_NOT_SERIALIZED(cld)) {
    SET_SERIALIZED(cld);
  }
  assert(IS_SERIALIZED(cld), "invariant");
  return result;
}

typedef LeakSerializePredicate<CldPtr> LeakCldPredicate;
//int _compare_cld_ptr_(CldPtr const& lhs, CldPtr const& rhs) { return lhs > rhs ? 1 : (lhs < rhs) ? -1 : 0; }
//typedef UniquePredicate<CldPtr, _compare_cld_ptr_> CldPredicate;
typedef SerializePredicate<CldPtr> CldPredicate;
typedef JfrPredicatedArtifactWriterImplHost<CldPtr, LeakCldPredicate, write__artifact__classloader__leakp> LeakCldWriterImpl;
typedef JfrPredicatedArtifactWriterImplHost<CldPtr, CldPredicate, write__artifact__classloader__serialize> CldWriterImpl;
typedef JfrArtifactWriterHost<LeakCldWriterImpl, TYPE_CLASSLOADER> LeakCldWriter;
typedef JfrArtifactWriterHost<CldWriterImpl, TYPE_CLASSLOADER> CldWriter;

typedef const JfrSymbolId::SymbolEntry* SymbolEntryPtr;

static int write__artifact__symbol__entry__(JfrCheckpointWriter* writer, SymbolEntryPtr entry) {
  assert(writer != NULL, "invariant");
  assert(entry != NULL, "invariant");
  ResourceMark rm;
  writer->write(CREATE_SYMBOL_ID(entry->id()));
  writer->write(entry->value()->as_C_string());
  return 1;
}

int write__artifact__symbol__entry(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* e) {
  assert(e != NULL, "invariant");
  return write__artifact__symbol__entry__(writer, (SymbolEntryPtr)e);
}

typedef JfrArtifactWriterImplHost<SymbolEntryPtr, write__artifact__symbol__entry> SymbolEntryWriterImpl;
typedef JfrArtifactWriterHost<SymbolEntryWriterImpl, TYPE_SYMBOL> SymbolEntryWriter;

typedef const JfrSymbolId::CStringEntry* CStringEntryPtr;

static int write__artifact__cstring__entry__(JfrCheckpointWriter* writer, CStringEntryPtr entry) {
  assert(writer != NULL, "invariant");
  assert(entry != NULL, "invariant");
  writer->write(CREATE_SYMBOL_ID(entry->id()));
  writer->write(entry->value());
  return 1;
}

int write__artifact__cstring__entry(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* e) {
  assert(e != NULL, "invariant");
  return write__artifact__cstring__entry__(writer, (CStringEntryPtr)e);
}

typedef JfrArtifactWriterImplHost<CStringEntryPtr, write__artifact__cstring__entry> CStringEntryWriterImpl;
typedef JfrArtifactWriterHost<CStringEntryWriterImpl, TYPE_SYMBOL> CStringEntryWriter;

int write__artifact__klass__symbol(JfrCheckpointWriter* writer, JfrArtifactSet* artifacts, const void* k) {
  assert(writer != NULL, "invariant");
  assert(artifacts != NULL, "invaiant");
  assert(k != NULL, "invariant");
  const InstanceKlass* const ik = (const InstanceKlass*)k;
  if (ik->is_unsafe_anonymous()) {
    CStringEntryPtr entry =
      artifacts->map_cstring(JfrSymbolId::unsafe_anonymous_klass_name_hash_code(ik));
    assert(entry != NULL, "invariant");
    return write__artifact__cstring__entry__(writer, entry);
  }

  SymbolEntryPtr entry = artifacts->map_symbol(JfrSymbolId::regular_klass_name_hash_code(ik));
  return write__artifact__symbol__entry__(writer, entry);
}

int _compare_traceid_(const traceid& lhs, const traceid& rhs) {
  return lhs > rhs ? 1 : (lhs < rhs) ? -1 : 0;
}

template <template <typename> class Predicate>
class KlassSymbolWriterImpl {
 private:
  JfrCheckpointWriter* _writer;
  JfrArtifactSet* _artifacts;
  Predicate<KlassPtr> _predicate;
  MethodUsedPredicate<true> _method_used_predicate;
  MethodFlagPredicate _method_flag_predicate;
  UniquePredicate<traceid, _compare_traceid_> _unique_predicate;

  int klass_symbols(KlassPtr klass);
  int package_symbols(PkgPtr pkg);
  int module_symbols(ModPtr module);
  int class_loader_symbols(CldPtr cld);
  int method_symbols(KlassPtr klass);

 public:
  typedef KlassPtr Type;
  KlassSymbolWriterImpl(JfrCheckpointWriter* writer,
                        JfrArtifactSet* artifacts,
                        bool current_epoch) : _writer(writer),
                                             _artifacts(artifacts),
                                             _predicate(current_epoch),
                                             _method_used_predicate(current_epoch),
                                             _method_flag_predicate(current_epoch),
                                             _unique_predicate(current_epoch) {}

  int operator()(KlassPtr klass) {
    assert(klass != NULL, "invariant");
    int count = 0;
    if (_predicate(klass)) {
      count += klass_symbols(klass);
      PkgPtr pkg = klass->package();
      if (pkg != NULL) {
        count += package_symbols(pkg);
        ModPtr module = pkg->module();
        if (module != NULL && module->is_named()) {
          count += module_symbols(module);
        }
      }
      CldPtr cld = klass->class_loader_data();
      assert(cld != NULL, "invariant");
      if (!cld->is_unsafe_anonymous()) {
        count += class_loader_symbols(cld);
      }
      if (_method_used_predicate(klass)) {
        count += method_symbols(klass);
      }
    }
    return count;
  }
};

template <template <typename> class Predicate>
int KlassSymbolWriterImpl<Predicate>::klass_symbols(KlassPtr klass) {
  assert(klass != NULL, "invariant");
  assert(_predicate(klass), "invariant");
  const InstanceKlass* const ik = (const InstanceKlass*)klass;
  if (ik->is_unsafe_anonymous()) {
    CStringEntryPtr entry =
      this->_artifacts->map_cstring(JfrSymbolId::unsafe_anonymous_klass_name_hash_code(ik));
    assert(entry != NULL, "invariant");
    return _unique_predicate(entry->id()) ? write__artifact__cstring__entry__(this->_writer, entry) : 0;
  }
  SymbolEntryPtr entry = this->_artifacts->map_symbol(ik->name());
  assert(entry != NULL, "invariant");
  return _unique_predicate(entry->id()) ? write__artifact__symbol__entry__(this->_writer, entry) : 0;
}

template <template <typename> class Predicate>
int KlassSymbolWriterImpl<Predicate>::package_symbols(PkgPtr pkg) {
  assert(pkg != NULL, "invariant");
  SymbolPtr pkg_name = pkg->name();
  assert(pkg_name != NULL, "invariant");
  SymbolEntryPtr package_symbol = this->_artifacts->map_symbol(pkg_name);
  assert(package_symbol != NULL, "invariant");
  return _unique_predicate(package_symbol->id()) ? write__artifact__symbol__entry__(this->_writer, package_symbol) : 0;
}

template <template <typename> class Predicate>
int KlassSymbolWriterImpl<Predicate>::module_symbols(ModPtr module) {
  assert(module != NULL, "invariant");
  assert(module->is_named(), "invariant");
  int count = 0;
  SymbolPtr sym = module->name();
  SymbolEntryPtr entry = NULL;
  if (sym != NULL) {
    entry = this->_artifacts->map_symbol(sym);
    assert(entry != NULL, "invariant");
    if (_unique_predicate(entry->id())) {
      count += write__artifact__symbol__entry__(this->_writer, entry);
    }
  }
  sym = module->version();
  if (sym != NULL) {
    entry = this->_artifacts->map_symbol(sym);
    assert(entry != NULL, "invariant");
    if (_unique_predicate(entry->id())) {
      count += write__artifact__symbol__entry__(this->_writer, entry);
    }
  }
  sym = module->location();
  if (sym != NULL) {
    entry = this->_artifacts->map_symbol(sym);
    assert(entry != NULL, "invariant");
    if (_unique_predicate(entry->id())) {
      count += write__artifact__symbol__entry__(this->_writer, entry);
    }
  }
  return count;
}

template <template <typename> class Predicate>
int KlassSymbolWriterImpl<Predicate>::class_loader_symbols(CldPtr cld) {
  assert(cld != NULL, "invariant");
  assert(!cld->is_unsafe_anonymous(), "invariant");
  int count = 0;
  // class loader type
  const Klass* class_loader_klass = cld->class_loader_klass();
  if (class_loader_klass == NULL) {
    // (primordial) boot class loader
    CStringEntryPtr entry = this->_artifacts->map_cstring(0);
    assert(entry != NULL, "invariant");
    assert(strncmp(entry->literal(),
      BOOTSTRAP_LOADER_NAME,
      BOOTSTRAP_LOADER_NAME_LEN) == 0, "invariant");
    if (_unique_predicate(entry->id())) {
      count += write__artifact__cstring__entry__(this->_writer, entry);
    }
  } else {
    const Symbol* class_loader_name = cld->name();
    if (class_loader_name != NULL) {
      SymbolEntryPtr entry = this->_artifacts->map_symbol(class_loader_name);
      assert(entry != NULL, "invariant");
      if (_unique_predicate(entry->id())) {
        count += write__artifact__symbol__entry__(this->_writer, entry);
      }
    }
  }
  return count;
}

template <template <typename> class Predicate>
int KlassSymbolWriterImpl<Predicate>::method_symbols(KlassPtr klass) {
  assert(_predicate(klass), "invariant");
  assert(_method_used_predicate(klass), "invariant");
  assert(METHOD_AND_CLASS_USED_ANY_EPOCH(klass), "invariant");
  int count = 0;
  const InstanceKlass* const ik = InstanceKlass::cast(klass);
  const int len = ik->methods()->length();
  for (int i = 0; i < len; ++i) {
    MethodPtr method = ik->methods()->at(i);
    if (_method_flag_predicate(method)) {
      SymbolEntryPtr entry = this->_artifacts->map_symbol(method->name());
      assert(entry != NULL, "invariant");
      if (_unique_predicate(entry->id())) {
        count += write__artifact__symbol__entry__(this->_writer, entry);
      }
      entry = this->_artifacts->map_symbol(method->signature());
      assert(entry != NULL, "invariant");
      if (_unique_predicate(entry->id())) {
        count += write__artifact__symbol__entry__(this->_writer, entry);
      }
    }
  }
  return count;
}

typedef KlassSymbolWriterImpl<LeakPredicate> LeakKlassSymbolWriterImpl;
typedef JfrArtifactWriterHost<LeakKlassSymbolWriterImpl, TYPE_SYMBOL> LeakKlassSymbolWriter;

class ClearKlassAndMethods {
 private:
  ClearArtifact<KlassPtr> _clear_klass_tag_bits;
  ClearArtifact<MethodPtr> _clear_method_flag;
  MethodUsedPredicate<false> _method_used_predicate;

 public:
  ClearKlassAndMethods(bool current_epoch) : _method_used_predicate(current_epoch) {}
  bool operator()(KlassPtr klass) {
    if (_method_used_predicate(klass)) {
      const InstanceKlass* ik = InstanceKlass::cast(klass);
      const int len = ik->methods()->length();
      for (int i = 0; i < len; ++i) {
        MethodPtr method = ik->methods()->at(i);
        _clear_method_flag(method);
      }
    }
    _clear_klass_tag_bits(klass);
    return true;
  }
};

typedef CompositeFunctor<KlassPtr,
                         TagLeakpKlassArtifact,
                         LeakKlassWriter> LeakpKlassArtifactTagging;

typedef CompositeFunctor<KlassPtr,
                         LeakpKlassArtifactTagging,
                         KlassWriter> CompositeKlassWriter;

typedef CompositeFunctor<KlassPtr,
                         CompositeKlassWriter,
                         KlassArtifactRegistrator> CompositeKlassWriterRegistration;

typedef CompositeFunctor<KlassPtr,
                         KlassWriter,
                         KlassArtifactRegistrator> KlassWriterRegistration;

typedef JfrArtifactCallbackHost<KlassPtr, KlassWriterRegistration> KlassCallback;
typedef JfrArtifactCallbackHost<KlassPtr, CompositeKlassWriterRegistration> CompositeKlassCallback;

/*
 * Composite operation
 *
 * TagLeakpKlassArtifact ->
 *   LeakpPredicate ->
 *     LeakpKlassWriter ->
 *       KlassPredicate ->
 *         KlassWriter ->
 *           KlassWriterRegistration
 */
void JfrTypeSet::write_klass_constants(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer) {
  assert(!_artifacts->has_klass_entries(), "invariant");
  KlassArtifactRegistrator reg(_artifacts);
  KlassWriter kw(writer, _artifacts, current_epoch());
  KlassWriterRegistration kwr(&kw, &reg);
  if (leakp_writer == NULL) {
    KlassCallback callback(&kwr);
    _subsystem_callback = &callback;
    do_klasses();
    _artifacts->tally(kw);
    return;
  }
  TagLeakpKlassArtifact tagging(current_epoch());
  LeakKlassWriter lkw(leakp_writer, _artifacts, current_epoch());
  LeakpKlassArtifactTagging lpkat(&tagging, &lkw);
  CompositeKlassWriter ckw(&lpkat, &kw);
  CompositeKlassWriterRegistration ckwr(&ckw, &reg);
  CompositeKlassCallback callback(&ckwr);
  _subsystem_callback = &callback;
  do_klasses();
}

typedef CompositeFunctor<PkgPtr,
                         PackageWriter,
                         ClearArtifact<PkgPtr> > PackageWriterWithClear;

typedef CompositeFunctor<PkgPtr,
                         PackageWriter,
                         UnTagArtifact<PkgPtr> > PackageWriterWithUnTag;
typedef CompositeFunctor<PkgPtr,
                         LeakPackageWriter,
                         PackageWriter> CompositePackageWriter;

typedef CompositeFunctor<PkgPtr,
                         CompositePackageWriter,
                         ClearArtifact<PkgPtr> > CompositePackageWriterWithClear;
typedef CompositeFunctor<PkgPtr,
                         CompositePackageWriter,
                         UnTagArtifact<PkgPtr> > CompositePackageWriterWithUnTag;

class PackageFieldSelector {
 public:
  typedef PkgPtr TypePtr;
  static TypePtr select(KlassPtr klass) {
    assert(klass != NULL, "invariant");
    return ((InstanceKlass*)klass)->package();
  }
};

typedef KlassToFieldEnvelope<PackageFieldSelector,
                             PackageWriterWithClear> KlassPackageWriterWithClear;

typedef KlassToFieldEnvelope<PackageFieldSelector,
                             PackageWriterWithUnTag> KlassPackageWriterWithUnTag;
typedef KlassToFieldEnvelope<PackageFieldSelector, PackageWriter> KlassPackageWriter;
typedef KlassToFieldEnvelope<PackageFieldSelector, CompositePackageWriter> KlassCompositePackageWriter;
typedef KlassToFieldEnvelope<PackageFieldSelector,
                             CompositePackageWriterWithClear> KlassCompositePackageWriterWithClear;

typedef KlassToFieldEnvelope<PackageFieldSelector,
                             CompositePackageWriterWithUnTag> KlassCompositePackageWriterWithUnTag;
typedef JfrArtifactCallbackHost<PkgPtr, PackageWriterWithClear> PackageCallback;
typedef JfrArtifactCallbackHost<PkgPtr, CompositePackageWriterWithClear> CompositePackageCallback;

static void write_package_constants_current_epoch(JfrArtifactSet* artifacts, JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer) {
  assert(artifacts != NULL, "invariant");
  assert(artifacts->has_klass_entries(), "invariant");
  PackageWriter pw(writer, artifacts, true);
  if (leakp_writer == NULL) {
    KlassPackageWriter kpw(&pw);
    artifacts->iterate_klasses(kpw);
    artifacts->tally(pw);
  } else {
    LeakPackageWriter lpw(leakp_writer, artifacts, true);
    CompositePackageWriter cpw(&lpw, &pw);
    KlassCompositePackageWriter kcpw(&cpw);
    artifacts->iterate_klasses(kcpw);
  }
}

/*
 * Composite operation
 *
 * LeakpPackageWriter ->
 *   PackageWriter ->
 *     ClearArtifact<PackageEntry>
 *
 */
void JfrTypeSet::write_package_constants(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer) {
  assert(_artifacts->has_klass_entries(), "invariant");
  if (current_epoch()) {
    write_package_constants_current_epoch(_artifacts, writer, leakp_writer);
    return;
  }
  assert(is_rotating(), "invariant");
  PackageWriter pw(writer, _artifacts, false);
  ClearArtifact<PkgPtr> clear;
  UnTagArtifact<PkgPtr> untag;
  if (leakp_writer == NULL) {
    PackageWriterWithUnTag kpw(&pw, &untag);
    KlassPackageWriterWithUnTag kpwwut(&kpw);
    _artifacts->iterate_klasses(kpwwut);
    PackageWriterWithClear pwwc(&pw, &clear);
    PackageCallback callback(&pwwc);
    _subsystem_callback = &callback;
    do_packages();
    return;
  }
  LeakPackageWriter lpw(leakp_writer, _artifacts, false);
  CompositePackageWriter cpw(&lpw, &pw);
  CompositePackageWriterWithUnTag cpwwut(&cpw, &untag);
  KlassCompositePackageWriterWithUnTag kcpw(&cpwwut);
  _artifacts->iterate_klasses(kcpw);
  CompositePackageWriterWithClear cpwwc(&cpw, &clear);
  CompositePackageCallback callback(&cpwwc);
  _subsystem_callback = &callback;
  do_packages();
}

typedef CompositeFunctor<ModPtr,
                         ModuleWriter,
                         ClearArtifact<ModPtr> > ModuleWriterWithClear;

typedef CompositeFunctor<ModPtr,
                         ModuleWriter,
                         UnTagArtifact<ModPtr> > ModuleWriterWithUnTag;
typedef CompositeFunctor<ModPtr,
                         LeakModuleWriter,
                         ModuleWriter> CompositeModuleWriter;

typedef CompositeFunctor<ModPtr,
                         CompositeModuleWriter,
                         ClearArtifact<ModPtr> > CompositeModuleWriterWithClear;
typedef CompositeFunctor<ModPtr,
                         CompositeModuleWriter,
                         UnTagArtifact<ModPtr> > CompositeModuleWriterWithUnTag;

typedef JfrArtifactCallbackHost<ModPtr, ModuleWriterWithClear> ModuleCallback;
typedef JfrArtifactCallbackHost<ModPtr, CompositeModuleWriterWithClear> CompositeModuleCallback;

class ModuleFieldSelector {
 public:
  typedef ModPtr TypePtr;
  static TypePtr select(KlassPtr klass) {
    assert(klass != NULL, "invariant");
    PkgPtr pkg = klass->package();
    return pkg != NULL ? pkg->module() : NULL;
  }
};

typedef KlassToFieldEnvelope<ModuleFieldSelector,
                             ModuleWriterWithClear> KlassModuleWriterWithClear;

typedef KlassToFieldEnvelope<ModuleFieldSelector,
                             ModuleWriterWithUnTag> KlassModuleWriterWithUnTag;
typedef KlassToFieldEnvelope<ModuleFieldSelector, ModuleWriter> KlassModuleWriter;
typedef KlassToFieldEnvelope<ModuleFieldSelector,  CompositeModuleWriter> KlassCompositeModuleWriter;
typedef KlassToFieldEnvelope<ModuleFieldSelector,
                             CompositeModuleWriterWithClear> KlassCompositeModuleWriterWithClear;

typedef KlassToFieldEnvelope<ModuleFieldSelector,
                             CompositeModuleWriterWithUnTag> KlassCompositeModuleWriterWithUnTag;

static void write_module_constants_current_epoch(JfrArtifactSet* artifacts, JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer) {
  assert(artifacts != NULL, "invariant");
  assert(artifacts->has_klass_entries(), "invariant");
  ModuleWriter mw(writer, artifacts, true);
  if (leakp_writer == NULL) {
    KlassModuleWriter kmw(&mw);
    artifacts->iterate_klasses(kmw);
    artifacts->tally(mw);
  } else {
    LeakModuleWriter lmw(leakp_writer, artifacts, true);
    CompositeModuleWriter cmw(&lmw, &mw);
    KlassCompositeModuleWriter kcmw(&cmw);
    artifacts->iterate_klasses(kcmw);
  }
}

/*
 * Composite operation
 *
 * LeakpModuleWriter ->
 *   ModuleWriter ->
 *     ClearArtifact<ModuleEntry>
 */
void JfrTypeSet::write_module_constants(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer) {
  assert(_artifacts->has_klass_entries(), "invariant");
  if (current_epoch()) {
    write_module_constants_current_epoch(_artifacts, writer, leakp_writer);
    return;
  }
  assert(is_rotating(), "invariant");
  ClearArtifact<ModPtr> clear;
  UnTagArtifact<ModPtr> untag;
  ModuleWriter mw(writer, _artifacts, false);
  if (leakp_writer == NULL) {
    ModuleWriterWithUnTag kpw(&mw, &untag);
    KlassModuleWriterWithUnTag kmwwut(&kpw);
    _artifacts->iterate_klasses(kmwwut);
    ModuleWriterWithClear mwwc(&mw, &clear);
    ModuleCallback callback(&mwwc);
    _subsystem_callback = &callback;
    do_modules();
    return;
  }
  LeakModuleWriter lmw(leakp_writer, _artifacts, false);
  CompositeModuleWriter cmw(&lmw, &mw);
  CompositeModuleWriterWithUnTag cmwwut(&cmw, &untag);
  KlassCompositeModuleWriterWithUnTag kcmw(&cmwwut);
  _artifacts->iterate_klasses(kcmw);
  CompositeModuleWriterWithClear cmwwc(&cmw, &clear);
  CompositeModuleCallback callback(&cmwwc);
  _subsystem_callback = &callback;
  do_modules();
}

typedef CompositeFunctor<CldPtr, CldWriter, ClearArtifact<CldPtr> > CldWriterWithClear;
typedef CompositeFunctor<CldPtr, CldWriter, UnTagArtifact<CldPtr> > CldWriterWithUnTag;
typedef CompositeFunctor<CldPtr, LeakCldWriter, CldWriter> CompositeCldWriter;
typedef CompositeFunctor<CldPtr, CompositeCldWriter, ClearArtifact<CldPtr> > CompositeCldWriterWithClear;
typedef CompositeFunctor<CldPtr, CompositeCldWriter, UnTagArtifact<CldPtr> > CompositeCldWriterWithUnTag;
typedef JfrArtifactCallbackHost<CldPtr, CldWriterWithClear> CldCallback;
typedef JfrArtifactCallbackHost<CldPtr, CompositeCldWriterWithClear> CompositeCldCallback;

class CldFieldSelector {
 public:
  typedef CldPtr TypePtr;
  static TypePtr select(KlassPtr klass) {
    assert(klass != NULL, "invariant");
    CldPtr cld = klass->class_loader_data();
    return cld->is_unsafe_anonymous() ? NULL : cld;
  }
};

typedef KlassToFieldEnvelope<CldFieldSelector, CldWriter> KlassCldWriter;
typedef KlassToFieldEnvelope<CldFieldSelector, CldWriterWithClear> KlassCldWriterWithClear;
typedef KlassToFieldEnvelope<CldFieldSelector, CldWriterWithUnTag> KlassCldWriterWithUnTag;
typedef KlassToFieldEnvelope<CldFieldSelector, CompositeCldWriter> KlassCompositeCldWriter;
typedef KlassToFieldEnvelope<CldFieldSelector, CompositeCldWriterWithClear> KlassCompositeCldWriterWithClear;
typedef KlassToFieldEnvelope<CldFieldSelector, CompositeCldWriterWithUnTag> KlassCompositeCldWriterWithUnTag;

static void write_class_loader_constants_current_epoch(JfrArtifactSet* artifacts, JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer) {
  assert(artifacts != NULL, "invariant");
  assert(artifacts->has_klass_entries(), "invariant");
  CldWriter cldw(writer, artifacts, true);
  if (leakp_writer == NULL) {
    KlassCldWriter kcw(&cldw);
    artifacts->iterate_klasses(kcw);
    artifacts->tally(cldw);
  } else {
    LeakCldWriter lcldw(leakp_writer, artifacts, true);
    CompositeCldWriter ccldw(&lcldw, &cldw);
    KlassCompositeCldWriter kccldw(&ccldw);
    artifacts->iterate_klasses(kccldw);
  }
}

/*
 * Composite operation
 *
 * LeakpClassLoaderWriter ->
 *   ClassLoaderWriter ->
 *     ClearArtifact<ClassLoaderData>
 */
void JfrTypeSet::write_class_loader_constants(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer) {
  assert(_artifacts->has_klass_entries(), "invariant");
  if (current_epoch()) {
    write_class_loader_constants_current_epoch(_artifacts, writer, leakp_writer);
    return;
  }
  assert(is_rotating(), "invariant");
  ClearArtifact<CldPtr> clear;
  UnTagArtifact<CldPtr> untag;
  CldWriter cldw(writer, _artifacts, false);
  if (leakp_writer == NULL) {
    CldWriterWithUnTag cldwut(&cldw, &untag);
    KlassCldWriterWithUnTag kcldwut(&cldwut);
    _artifacts->iterate_klasses(kcldwut);
    CldWriterWithClear cldwwc(&cldw, &clear);
    CldCallback callback(&cldwwc);
    _subsystem_callback = &callback;
    do_class_loaders();
    return;
  }
  LeakCldWriter lcldw(leakp_writer, _artifacts, false);
  CompositeCldWriter ccldw(&lcldw, &cldw);
  CompositeCldWriterWithUnTag cldwwut(&ccldw, &untag);
  KlassCompositeCldWriterWithUnTag kccldw(&cldwwut);
  _artifacts->iterate_klasses(kccldw);
  CompositeCldWriterWithClear ccldwwc(&ccldw, &clear);
  CompositeCldCallback callback(&ccldwwc);
  _subsystem_callback = &callback;
  do_class_loaders();
}

template <bool predicate_bool, typename MethodFunctor>
class MethodIteratorHost {
 private:
  MethodFunctor _method_functor;
  MethodUsedPredicate<predicate_bool> _method_used_predicate;
  MethodFlagPredicate _method_flag_predicate;

 public:
  MethodIteratorHost(JfrCheckpointWriter* writer,
                     JfrArtifactSet* artifacts,
                     bool current_epoch,
                     bool skip_header = false) :
    _method_functor(writer, artifacts, current_epoch, skip_header),
    _method_used_predicate(current_epoch),
    _method_flag_predicate(current_epoch) {}

  bool operator()(KlassPtr klass) {
    if (_method_used_predicate(klass)) {
      assert(METHOD_AND_CLASS_USED_ANY_EPOCH(klass), "invariant");
      const InstanceKlass* ik = InstanceKlass::cast(klass);
      const int len = ik->methods()->length();
      for (int i = 0; i < len; ++i) {
        MethodPtr method = ik->methods()->at(i);
        if (_method_flag_predicate(method)) {
          _method_functor(method);
        }
      }
    }
    return true;
  }

  int count() const { return _method_functor.count(); }
  void add(int count) { _method_functor.add(count); }
};

typedef MethodIteratorHost<true /*leakp */,  LeakpMethodWriterImpl> LeakMethodWriter;
typedef MethodIteratorHost<false, MethodWriterImpl> MethodWriter;
typedef CompositeFunctor<KlassPtr, LeakMethodWriter, MethodWriter> CompositeMethodWriter;

/*
 * Composite operation
 *
 * LeakpMethodWriter ->
 *   MethodWriter
 */
void JfrTypeSet::write_method_constants(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer) {
  assert(_artifacts->has_klass_entries(), "invariant");
  MethodWriter mw(writer, _artifacts, is_not_rotating());
  if (leakp_writer == NULL) {
    _artifacts->iterate_klasses(mw);
    _artifacts->tally(mw);
    return;
  }
  LeakMethodWriter lpmw(leakp_writer, _artifacts, is_not_rotating());
  CompositeMethodWriter cmw(&lpmw, &mw);
  _artifacts->iterate_klasses(cmw);
}

static void write_symbols_leakp(JfrCheckpointWriter* leakp_writer, JfrArtifactSet* artifacts, bool current_epoch) {
  assert(leakp_writer != NULL, "invariant");
  assert(artifacts != NULL, "invariant");
  LeakKlassSymbolWriter lpksw(leakp_writer, artifacts, current_epoch);
  artifacts->iterate_klasses(lpksw);
}

static void write_symbols(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer, JfrArtifactSet* artifacts, bool current_epoch) {
  assert(writer != NULL, "invariant");
  assert(artifacts != NULL, "invariant");
  if (leakp_writer != NULL) {
    write_symbols_leakp(leakp_writer, artifacts, current_epoch);
  }
  // iterate all registered symbols
  SymbolEntryWriter symbol_writer(writer, artifacts, current_epoch);
  artifacts->iterate_symbols(symbol_writer);
  CStringEntryWriter cstring_writer(writer, artifacts, current_epoch, true); // skip header
  artifacts->iterate_cstrings(cstring_writer);
  symbol_writer.add(cstring_writer.count());
  artifacts->tally(symbol_writer);
}

bool JfrTypeSet::_class_unload = false;
bool JfrTypeSet::_flushpoint = false;
JfrArtifactSet* JfrTypeSet::_artifacts = NULL;
JfrArtifactClosure* JfrTypeSet::_subsystem_callback = NULL;

bool JfrTypeSet::is_rotating() {
  return !(_class_unload || _flushpoint);
}

bool JfrTypeSet::is_not_rotating() {
  return !is_rotating();
}

bool JfrTypeSet::current_epoch() {
  return is_not_rotating();
}

void JfrTypeSet::write_symbol_constants(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer) {
  assert(writer != NULL, "invariant");
  assert(_artifacts->has_klass_entries(), "invariant");
  write_symbols(writer, leakp_writer, _artifacts, _class_unload);
}

void JfrTypeSet::do_unloaded_klass(Klass* klass) {
  assert(klass != NULL, "invariant");
  assert(_subsystem_callback != NULL, "invariant");
  if (IS_JDK_JFR_EVENT_SUBKLASS(klass)) {
    JfrEventClasses::increment_unloaded_event_class();
  }
  if (USED_THIS_EPOCH(klass)) { // includes leakp subset
    _subsystem_callback->do_artifact(klass);
    return;
  }
  if (klass->is_subclass_of(SystemDictionary::ClassLoader_klass()) || klass == SystemDictionary::Object_klass()) {
    SET_LEAKP_USED_THIS_EPOCH(klass); // tag leakp "safe byte" for subset inclusion
    _subsystem_callback->do_artifact(klass);
  }
}

void JfrTypeSet::do_klass(Klass* klass) {
  assert(klass != NULL, "invariant");
  assert(_subsystem_callback != NULL, "invariant");
  if (_flushpoint) {
    if (USED_THIS_EPOCH(klass)) {
      _subsystem_callback->do_artifact(klass);
      return;
    }
  } else {
    if (USED_PREV_EPOCH(klass)) { // includes leakp subset
      _subsystem_callback->do_artifact(klass);
      return;
    }
  }
  if (klass->is_subclass_of(SystemDictionary::ClassLoader_klass()) || klass == SystemDictionary::Object_klass()) {
    if (_flushpoint) {
      SET_LEAKP_USED_THIS_EPOCH(klass);
    } else {
      SET_LEAKP_USED_PREV_EPOCH(klass); // tag leakp "safe byte" for subset inclusion
    }
    _subsystem_callback->do_artifact(klass);
  }
}

void JfrTypeSet::do_klasses() {
  if (_class_unload) {
    ClassLoaderDataGraph::classes_unloading_do(&do_unloaded_klass);
    return;
  }
  ClassLoaderDataGraph::classes_do(&do_klass);
}

template <typename T>
static void do_current_epoch_artifact(JfrArtifactClosure* callback, T* value) {
  assert(callback != NULL, "invariant");
  assert(value != NULL, "invariant");
  if (ANY_USED_THIS_EPOCH(value)) { // includes leakp subset
    callback->do_artifact(value);
  }
}

template <typename T>
static void do_previous_epoch_artifact(JfrArtifactClosure* callback, T* value) {
  assert(callback != NULL, "invariant");
  assert(value != NULL, "invariant");
  if (ANY_USED_PREV_EPOCH(value)) { // includes leakp subset
    callback->do_artifact(value);
    assert(IS_NOT_SERIALIZED(value), "invariant");
    return;
  }
  if (IS_SERIALIZED(value)) {
    UNSERIALIZE(value);
  }
  assert(IS_NOT_SERIALIZED(value), "invariant");
}
void JfrTypeSet::do_unloaded_package(PackageEntry* entry) {
  do_current_epoch_artifact(_subsystem_callback, entry);
}

void JfrTypeSet::do_package(PackageEntry* entry) {
  do_previous_epoch_artifact(_subsystem_callback, entry);
}

void JfrTypeSet::do_packages() {
  if (_class_unload) {
    ClassLoaderDataGraph::packages_unloading_do(&do_unloaded_package);
    return;
  }
  ClassLoaderDataGraph::packages_do(&do_package);
}

void JfrTypeSet::do_unloaded_module(ModuleEntry* entry) {
  do_current_epoch_artifact(_subsystem_callback, entry);
}

void JfrTypeSet::do_module(ModuleEntry* entry) {
  do_previous_epoch_artifact(_subsystem_callback, entry);
}

void JfrTypeSet::do_modules() {
  if (_class_unload) {
    ClassLoaderDataGraph::modules_unloading_do(&do_unloaded_module);
    return;
  }
  ClassLoaderDataGraph::modules_do(&do_module);
}

void JfrTypeSet::do_unloaded_class_loader_data(ClassLoaderData* cld) {
  do_current_epoch_artifact(_subsystem_callback, cld);
}

void JfrTypeSet::do_class_loader_data(ClassLoaderData* cld) {
  do_previous_epoch_artifact(_subsystem_callback, cld);
}

class CLDCallback : public CLDClosure {
 private:
  bool _class_unload;
 public:
  CLDCallback(bool class_unload) : _class_unload(class_unload) {}
  void do_cld(ClassLoaderData* cld) {
    assert(cld != NULL, "invariant");
    if (cld->is_unsafe_anonymous()) {
      return;
    }
    if (_class_unload) {
      JfrTypeSet::do_unloaded_class_loader_data(cld);
      return;
    }
    JfrTypeSet::do_class_loader_data(cld);
  }
};

void JfrTypeSet::do_class_loaders() {
  CLDCallback cld_cb(_class_unload);
  if (_class_unload) {
    ClassLoaderDataGraph::cld_unloading_do(&cld_cb);
    return;
  }
  ClassLoaderDataGraph::loaded_cld_do(&cld_cb);
}

static void clear_artifacts(JfrArtifactSet* artifacts, bool current_epoch) {
  assert(artifacts != NULL, "invariant");
  assert(artifacts->has_klass_entries(), "invariant");

  // untag
  ClearKlassAndMethods clear(current_epoch);
  artifacts->iterate_klasses(clear);
}

/**
 * Write all "tagged" (in-use) constant artifacts and their dependencies.
 */
size_t JfrTypeSet::serialize(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer, bool class_unload, bool flushpoint) {
  assert(writer != NULL, "invariant");
  ResourceMark rm;
  // initialization begin
  _class_unload = class_unload;
  _flushpoint = flushpoint;
  ++checkpoint_id;
  if (_artifacts == NULL) {
    _artifacts = new JfrArtifactSet(current_epoch());
  } else {
    _artifacts->initialize(current_epoch());
  }
  assert(_artifacts != NULL, "invariant");
  assert(!_artifacts->has_klass_entries(), "invariant");
  // initialization complete

  // write order is important because an individual write step
  // might tag an artifact to be written in a subsequent step
  write_klass_constants(writer, leakp_writer);
  if (!_artifacts->has_klass_entries()) {
    return 0;
  }
  write_package_constants(writer, leakp_writer);
  write_module_constants(writer, leakp_writer);
  write_class_loader_constants(writer, leakp_writer);
  write_method_constants(writer, leakp_writer);
  write_symbol_constants(writer, leakp_writer);
  const size_t total_count = _artifacts->total_count();
  if (!flushpoint) {
    clear_artifacts(_artifacts, class_unload);
  }
  return total_count;
}
