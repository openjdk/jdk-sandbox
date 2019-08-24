/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_CHECKPOINT_TYPES_JFRTYPESETUTILS_HPP
#define SHARE_JFR_RECORDER_CHECKPOINT_TYPES_JFRTYPESETUTILS_HPP

#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrHashtable.hpp"
#include "oops/klass.hpp"
#include "oops/method.hpp"
#include "utilities/growableArray.hpp"

// Composite callback/functor building block
template <typename T, typename Func1, typename Func2>
class CompositeFunctor {
 private:
  Func1* _f;
  Func2* _g;
 public:
  CompositeFunctor(Func1* f, Func2* g) : _f(f), _g(g) {
    assert(f != NULL, "invariant");
    assert(g != NULL, "invariant");
  }
  bool operator()(T const& value) {
    return (*_f)(value) && (*_g)(value);
  }
};

class JfrArtifactClosure {
 public:
  virtual void do_artifact(const void* artifact) = 0;
};

template <typename T, typename Callback>
class JfrArtifactCallbackHost : public JfrArtifactClosure {
 private:
  Callback* _callback;
 public:
  JfrArtifactCallbackHost(Callback* callback) : _callback(callback) {}
  void do_artifact(const void* artifact) {
    (*_callback)(reinterpret_cast<T const&>(artifact));
  }
};

template <typename FieldSelector, typename Letter>
class KlassToFieldEnvelope {
  Letter* _letter;
 public:
  KlassToFieldEnvelope(Letter* letter) : _letter(letter) {}
  bool operator()(const Klass* klass) {
    typename FieldSelector::TypePtr t = FieldSelector::select(klass);
    return t != NULL ? (*_letter)(t) : true;
  }
};

template <typename T>
class ClearArtifact {
 public:
  bool operator()(T const& value) {
    CLEAR_METHOD_AND_CLASS_PREV_EPOCH(value);
    CLEAR_SERIALIZED(value);
    assert(IS_NOT_SERIALIZED(value), "invariant");
    return true;
  }
};

template <>
class ClearArtifact<const Method*> {
 public:
  bool operator()(const Method* method) {
    assert(METHOD_FLAG_USED_PREV_EPOCH(method), "invariant");
    CLEAR_METHOD_FLAG_USED_PREV_EPOCH(method);
    CLEAR_METHOD_SERIALIZED(method);
    assert(METHOD_NOT_SERIALIZED(method), "invariant");
    return true;
  }
};

template <typename T>
class Stub {
 public:
  bool operator()(T const& value) { return true; }
};

template <typename T>
class SerializePredicate {
  bool _class_unload;
 public:
  SerializePredicate(bool class_unload) : _class_unload(class_unload) {}
  bool operator()(T const& value) {
    assert(value != NULL, "invariant");
    return _class_unload ? true : IS_NOT_SERIALIZED(value);
  }
};

template <>
class SerializePredicate<const Method*> {
  bool _class_unload;
 public:
  SerializePredicate(bool class_unload) : _class_unload(class_unload) {}
  bool operator()(const Method* method) {
    assert(method != NULL, "invariant");
    return _class_unload ? true : METHOD_NOT_SERIALIZED(method);
  }
};

template <typename T>
class SymbolPredicate {
  bool _class_unload;
 public:
  SymbolPredicate(bool class_unload) : _class_unload(class_unload) {}
  bool operator()(T const& value) {
    assert(value != NULL, "invariant");
    return _class_unload ? value->is_unloading() : !value->is_serialized();
  }
};

class MethodUsedPredicate {
  bool _current_epoch;
public:
  MethodUsedPredicate(bool current_epoch) : _current_epoch(current_epoch) {}
  bool operator()(const Klass* klass) {
    return _current_epoch ? METHOD_USED_THIS_EPOCH(klass) : METHOD_USED_PREV_EPOCH(klass);
  }
};

class MethodFlagPredicate {
  bool _current_epoch;
 public:
  MethodFlagPredicate(bool current_epoch) : _current_epoch(current_epoch) {}
  bool operator()(const Method* method) {
    return _current_epoch ? METHOD_FLAG_USED_THIS_EPOCH(method) : METHOD_FLAG_USED_PREV_EPOCH(method);
  }
};

class JfrSymbolId : public JfrCHeapObj {
  template <typename, typename, template<typename, typename> class, typename, size_t>
  friend class HashTableHost;
  typedef HashTableHost<const Symbol*, traceid, ListEntry, JfrSymbolId> SymbolTable;
  typedef HashTableHost<const char*, traceid, ListEntry, JfrSymbolId> CStringTable;
 public:
  typedef SymbolTable::HashEntry SymbolEntry;
  typedef CStringTable::HashEntry CStringEntry;
 private:
  SymbolTable* _sym_table;
  CStringTable* _cstring_table;
  const SymbolEntry* _sym_list;
  const CStringEntry* _cstring_list;
  traceid _symbol_id_counter;
  bool _class_unload;

  // hashtable(s) callbacks
  void assign_id(const SymbolEntry* entry);
  bool equals(const Symbol* query, uintptr_t hash, const SymbolEntry* entry);
  void unlink(const SymbolEntry* entry);
  void assign_id(const CStringEntry* entry);
  bool equals(const char* query, uintptr_t hash, const CStringEntry* entry);
  void unlink(const CStringEntry* entry);

  template <typename Functor, typename T>
  void iterate(Functor& functor, const T* list) {
    const T* symbol = list;
    while (symbol != NULL) {
      const T* next = symbol->list_next();
      functor(symbol);
      symbol = next;
    }
  }

 public:
  static bool is_unsafe_anonymous_klass(const Klass* k);
  static const char* create_unsafe_anonymous_klass_symbol(const InstanceKlass* ik, uintptr_t& hashcode);
  static uintptr_t unsafe_anonymous_klass_name_hash_code(const InstanceKlass* ik);
  static uintptr_t regular_klass_name_hash_code(const Klass* k);

  JfrSymbolId();
  ~JfrSymbolId();

  void clear();
  void set_class_unload(bool class_unload);

  traceid mark_unsafe_anonymous_klass_name(const Klass* k);
  traceid mark(const Symbol* sym, uintptr_t hash);
  traceid mark(const Klass* k);
  traceid mark(const Symbol* symbol);
  traceid mark(const char* str, uintptr_t hash);

  const SymbolEntry* map_symbol(const Symbol* symbol) const;
  const SymbolEntry* map_symbol(uintptr_t hash) const;
  const CStringEntry* map_cstring(uintptr_t hash) const;

  template <typename Functor>
  void symbol(Functor& functor, const Klass* k) {
    if (is_unsafe_anonymous_klass(k)) {
      return;
    }
    functor(map_symbol(regular_klass_name_hash_code(k)));
  }

  template <typename Functor>
  void symbol(Functor& functor, const Method* method) {
    assert(method != NULL, "invariant");
    functor(map_symbol((uintptr_t)method->name()->identity_hash()));
    functor(map_symbol((uintptr_t)method->signature()->identity_hash()));
  }

  template <typename Functor>
  void cstring(Functor& functor, const Klass* k) {
    if (!is_unsafe_anonymous_klass(k)) {
      return;
    }
    functor(map_cstring(unsafe_anonymous_klass_name_hash_code((const InstanceKlass*)k)));
  }

  template <typename Functor>
  void iterate_symbols(Functor& functor) {
    iterate(functor, _sym_list);
  }

  template <typename Functor>
  void iterate_cstrings(Functor& functor) {
    iterate(functor, _cstring_list);
  }

  bool has_entries() const { return has_symbol_entries() || has_cstring_entries(); }
  bool has_symbol_entries() const { return _sym_list != NULL; }
  bool has_cstring_entries() const { return _cstring_list != NULL; }
};

/**
 * When processing a set of artifacts, there will be a need
 * to track transitive dependencies originating with each artifact.
 * These might or might not be explicitly "tagged" at that point.
 * With the introduction of "epochs" to allow for concurrent tagging,
 * we attempt to avoid "tagging" an artifact to indicate its use in a
 * previous epoch. This is mainly to reduce the risk for data races.
 * Instead, JfrArtifactSet is used to track transitive dependencies
 * during the write process itself.
 *
 * It can also provide opportunities for caching, as the ideal should
 * be to reduce the amount of iterations neccessary for locating artifacts
 * in the respective VM subsystems.
 */
class JfrArtifactSet : public JfrCHeapObj {
 private:
  JfrSymbolId* _symbol_id;
  GrowableArray<const Klass*>* _klass_list;
  size_t _total_count;

 public:
  JfrArtifactSet(bool class_unload);
  ~JfrArtifactSet();

  // caller needs ResourceMark
  void initialize(bool class_unload);
  void clear();


  traceid mark(const Symbol* sym, uintptr_t hash);
  traceid mark(const Klass* klass);
  traceid mark(const Symbol* symbol);
  traceid mark(const char* const str, uintptr_t hash);
  traceid mark_unsafe_anonymous_klass_name(const Klass* klass);

  const JfrSymbolId::SymbolEntry* map_symbol(const Symbol* symbol) const;
  const JfrSymbolId::SymbolEntry* map_symbol(uintptr_t hash) const;
  const JfrSymbolId::CStringEntry* map_cstring(uintptr_t hash) const;

  bool has_klass_entries() const;
  int entries() const;
  size_t total_count() const;
  void register_klass(const Klass* k);

  template <typename Functor>
  void iterate_klasses(Functor& functor) const {
    for (int i = 0; i < _klass_list->length(); ++i) {
      if (!functor(_klass_list->at(i))) {
        break;
      }
    }
  }

  template <typename T>
  void iterate_symbols(T& functor) {
    _symbol_id->iterate_symbols(functor);
  }

  template <typename T>
  void iterate_cstrings(T& functor) {
    _symbol_id->iterate_cstrings(functor);
  }

  template <typename Writer>
  void tally(Writer& writer) {
    _total_count += writer.count();
  }

};

class KlassArtifactRegistrator {
 private:
  JfrArtifactSet* _artifacts;
 public:
  KlassArtifactRegistrator(JfrArtifactSet* artifacts) :
    _artifacts(artifacts) {
    assert(_artifacts != NULL, "invariant");
  }

  bool operator()(const Klass* klass) {
    assert(klass != NULL, "invariant");
    _artifacts->register_klass(klass);
    return true;
  }
};

#endif // SHARE_JFR_RECORDER_CHECKPOINT_TYPES_JFRTYPESETUTILS_HPP
