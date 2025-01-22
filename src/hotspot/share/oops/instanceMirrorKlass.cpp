/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/cdsConfig.hpp"
#include "cds/serializeClosure.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/oopFactory.hpp"
#include "memory/universe.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/instanceOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/macros.hpp"

int InstanceMirrorKlass::_offset_of_static_fields = 0;

InstanceMirrorKlass::InstanceMirrorKlass() {
  assert(CDSConfig::is_dumping_static_archive() || CDSConfig::is_using_archive(), "only for CDS");
}

size_t InstanceMirrorKlass::instance_size(Klass* k) {
  if (k != nullptr && k->is_instance_klass()) {
    return align_object_size(size_helper() + InstanceKlass::cast(k)->static_field_size());
  }
  return size_helper();
}

instanceOop InstanceMirrorKlass::allocate_instance(Klass* k, bool extend, TRAPS) {
  // Query before forming handle.
  size_t base_size = instance_size(k);
  size_t size = base_size;
  if (extend && UseCompactObjectHeaders) {
    size = align_object_size(size + 1);
  }
  assert(base_size > 0, "base object size must be non-zero: %zu", base_size);

  // Since mirrors can be variable sized because of the static fields, store
  // the size in the mirror itself.
  instanceOop obj = (instanceOop)Universe::heap()->class_allocate(this, size, base_size, THREAD);
  if (extend && UseCompactObjectHeaders) {
    obj->set_mark(obj->mark().set_not_hashed_expanded());
    assert(expand_for_hash(obj), "must not further expand for hash");
  }
  return obj;
}

size_t InstanceMirrorKlass::oop_size(oop obj) const {
  return java_lang_Class::oop_size(obj);
}

int InstanceMirrorKlass::compute_static_oop_field_count(oop obj) {
  Klass* k = java_lang_Class::as_Klass(obj);
  if (k != nullptr && k->is_instance_klass()) {
    return InstanceKlass::cast(k)->static_oop_field_count();
  }
  return 0;
}

int InstanceMirrorKlass::hash_offset_in_bytes(oop obj) const {
  assert(UseCompactObjectHeaders, "only with compact i-hash");
  // TODO: There may be gaps that we could use, e.g. in the fields of Class,
  // between the fields of Class and the static fields or in or at the end of
  // the static fields block.
  // When implementing any change here, make sure that allocate_instance()
  // and corresponding code in InstanceMirrorKlass.java are in sync.
  return checked_cast<int>(obj->base_size_given_klass(this) * BytesPerWord);
}

#if INCLUDE_CDS
void InstanceMirrorKlass::serialize_offsets(SerializeClosure* f) {
  f->do_int(&_offset_of_static_fields);
}
#endif
