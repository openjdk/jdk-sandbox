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
#include "precompiled.hpp"

#include "memory/metaspace/arenaGrowthPolicy.hpp"
#include "memory/metaspace/chunkLevel.hpp"
#include "utilities/globalDefinitions.hpp"

namespace metaspace {



// A growth policy which is fixed. Last growth step is endlessly repeated
// (so from then on, the growth is linear)

class ConstantArenaGrowthPolicy : public ArenaGrowthPolicy {
private:

  // An array of sizes defining the arena growth per step. The last size
  // is endlessly repeated, so from then on the growth is linear.
  const chunklevel_t* const _steps;
  const int _num;

public:

  ConstantArenaGrowthPolicy(const chunklevel_t* steps, int num)
    : _steps(steps),
      _num(num)
  {
    assert(_num > 0 && _steps != NULL, "Sanity");
  }

  chunklevel_t get_level_at_step(int step) const {
    assert(step >= 0, "Sanity");
    if (step >= _num) {
      return _steps[_num - 1];
    }
    return _steps[step];
  }

};

// hard-coded policies sequences for standard space types

static const chunklevel_t g_sequ_standard_non_class[] = {
    chunklevel::CHUNK_LEVEL_2K,
    chunklevel::CHUNK_LEVEL_4K,
    chunklevel::CHUNK_LEVEL_8K,
    chunklevel::CHUNK_LEVEL_64K
    // .. repeat last
};

static const chunklevel_t g_sequ_standard_class[] = {
    chunklevel::CHUNK_LEVEL_1K,
    chunklevel::CHUNK_LEVEL_2K,
    chunklevel::CHUNK_LEVEL_4K,
    chunklevel::CHUNK_LEVEL_16K
    // .. repeat last
};

// Policies for micro loaders which are expected to load only one class:
static const chunklevel_t g_sequ_micro[] = {
    chunklevel::CHUNK_LEVEL_1K
    // .. repeat last
};

// Boot class loader: we allow it to grow in large steps, and give it
// a large initial step to start. Note that for growth sizes beyond
// commit granule size the costs diminish since the chunks are committed
// on demand only.
static const chunklevel_t g_sequ_boot_non_class[] = {
    chunklevel::CHUNK_LEVEL_4M,
    chunklevel::CHUNK_LEVEL_1M
    // .. repeat last
};

static const chunklevel_t g_sequ_boot_class[] = {
    chunklevel::CHUNK_LEVEL_1M,
    chunklevel::CHUNK_LEVEL_256K
    // .. repeat last
};

#define DEFINE_POLICY(what) \
  static ConstantArenaGrowthPolicy g_policy_##what (g_sequ_##what, sizeof(g_sequ_##what)/sizeof(chunklevel_t));

DEFINE_POLICY(standard_non_class)
DEFINE_POLICY(standard_class)
DEFINE_POLICY(micro)
DEFINE_POLICY(boot_non_class)
DEFINE_POLICY(boot_class)

const ArenaGrowthPolicy* ArenaGrowthPolicy::policy_for_space_type(MetaspaceType space_type, bool is_class) {
  switch(space_type) {
    case StandardMetaspaceType:          return is_class ? &g_policy_standard_class : &g_policy_standard_non_class;
    case BootMetaspaceType:              return is_class ? &g_policy_boot_class : &g_policy_boot_non_class;
    case ReflectionMetaspaceType:
    case ClassMirrorHolderMetaspaceType: return &g_policy_micro;
    default: ShouldNotReachHere();
  }

  return NULL;

}



} // namespace metaspace

