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

#ifndef SHARE_MEMORY_METASPACE_CHUNKALLOCSEQUENCE_HPP
#define SHARE_MEMORY_METASPACE_CHUNKALLOCSEQUENCE_HPP

#include "memory/metaspace.hpp" // For Metaspace::MetaspaceType
#include "memory/metaspace/chunkLevel.hpp"
#include "memory/metaspace/metaspaceEnums.hpp"

namespace metaspace {


// ArenaGrowthPolicy encodes the growth policy of an arena (a SpaceManager).
//
// These arenas grow in steps (by allocating new chunks). The coarseness of growth
// (chunk size, level) depends on what the arena is used for. Used for a class loader
// which is expected to load only one or very few classes should grow in tiny steps.
// For normal classloaders, it can grow in coarser steps, and arenas used by
// the boot loader will grow in even larger steps since we expect it to load a lot of
// classes.
// Note that when growing in large steps (in steps larger than a commit granule,
// by default 64K), costs diminish somewhat since we do not commit the whole space
// immediately.

class ArenaGrowthPolicy {
public:

  // Return the level of chunk the arena should preferably allocate at step X.
  virtual chunklevel_t get_level_at_step(int step) const = 0;

  // Given a space type, return the correct policy to use.
  // The returned object is static and read only.
  static const ArenaGrowthPolicy* policy_for_space_type(MetaspaceType space_type, bool is_class);

};


} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_CHUNKALLOCSEQUENCE_HPP
