/*
 * Copyright (c) 2019, SAP SE. All rights reserved.
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_MEMORY_METASPACEENUMS_HPP
#define SHARE_MEMORY_METASPACEENUMS_HPP

#include "memory/metaspace.hpp"
#include "utilities/debug.hpp"

// MetadataType and MetaspaceType, as well as some convenience functions surrounding them.
namespace metaspace {

///////////////////////

/* Moved to metaspace.hpp to keep diff in non-metaspace coding
 * small. Would like to have this here and in metaspace:: namespace,
 * eventually.
 *
 * enum MetadataType {
 *  ClassType,
 *  NonClassType,
 *  MetadataTypeCount
 * };
 */

inline bool is_class(Metaspace::MetadataType md) { return md == Metaspace::ClassType; }

inline Metaspace::MetadataType mdtype_from_bool(bool is_class) { return is_class ? Metaspace::ClassType : Metaspace::NonClassType; }

const char* describe_mdtype(Metaspace::MetadataType md);

#ifdef ASSERT
inline bool is_valid_mdtype(Metaspace::MetadataType md) {
  return (int)md >= 0 && (int)md < Metaspace::MetadataTypeCount;
}
inline void check_valid_mdtype(Metaspace::MetadataType md) {
  assert(is_valid_mdtype(md), "Wrong value for MetadataType: %d", (int) md);
}
#endif // ASSERT

///////////////////////

enum MetaspaceType {
  ZeroMetaspaceType = 0,
  StandardMetaspaceType = ZeroMetaspaceType,
  BootMetaspaceType = StandardMetaspaceType + 1,
  ClassMirrorHolderMetaspaceType = BootMetaspaceType + 1,
  ReflectionMetaspaceType = ClassMirrorHolderMetaspaceType + 1,
  MetaspaceTypeCount
};

const char* describe_spacetype(MetaspaceType st);

#ifdef ASSERT
inline bool is_valid_spacetype(MetaspaceType st) {
  return (int)st >= 0 && (int)st < MetaspaceTypeCount;
}
inline void check_valid_spacetype(MetaspaceType st) {
  assert(is_valid_spacetype(st), "Wrong value for MetaspaceType: %d", (int) st);
}
#endif

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACEENUMS_HPP
