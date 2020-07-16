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
 */


#include "precompiled.hpp"

//#define LOG_PLEASE

#include "metaspaceTestsCommon.hpp"

TEST_VM(metaspace, arena_growth_policy) {

  // These tests may have to be adapted if we change policies

  const ArenaGrowthPolicy* a =
      ArenaGrowthPolicy::policy_for_space_type(metaspace::ReflectionMetaspaceType, false);

  EXPECT_EQ(a->get_level_at_step(0), CHUNK_LEVEL_1K);
  EXPECT_EQ(a->get_level_at_step(2), CHUNK_LEVEL_1K);
  EXPECT_EQ(a->get_level_at_step(10), CHUNK_LEVEL_1K);

  a = ArenaGrowthPolicy::policy_for_space_type(metaspace::ClassMirrorHolderMetaspaceType, false);

  EXPECT_EQ(a->get_level_at_step(0), CHUNK_LEVEL_1K);
  EXPECT_EQ(a->get_level_at_step(2), CHUNK_LEVEL_1K);
  EXPECT_EQ(a->get_level_at_step(10), CHUNK_LEVEL_1K);

  a = ArenaGrowthPolicy::policy_for_space_type(metaspace::StandardMetaspaceType, false);

  EXPECT_EQ(a->get_level_at_step(0), CHUNK_LEVEL_2K);
  EXPECT_EQ(a->get_level_at_step(2), CHUNK_LEVEL_8K);
  EXPECT_EQ(a->get_level_at_step(10), CHUNK_LEVEL_64K);

  a = ArenaGrowthPolicy::policy_for_space_type(metaspace::BootMetaspaceType, false);

  EXPECT_EQ(a->get_level_at_step(0), CHUNK_LEVEL_4M);
  EXPECT_EQ(a->get_level_at_step(2), CHUNK_LEVEL_1M);
  EXPECT_EQ(a->get_level_at_step(10), CHUNK_LEVEL_1M);

}
