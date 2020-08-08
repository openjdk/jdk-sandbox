/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, 2020 SAP SE. All rights reserved.
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

//#define LOG_PLEASE

#include "classfile/classLoaderData.hpp"
#include "metaspaceTestsCommon.hpp"


TEST_VM(metaspace, misc_sizes)   {

  // Test test common sizes (seems primitive but breaks surprisingly often during development
  //  because of word vs byte confusion)
  // Adjust this test if numbers change.
  ASSERT_TRUE(Settings::commit_granule_bytes() == 16 * K ||
              Settings::commit_granule_bytes() == 64 * K);
  ASSERT_EQ(Settings::commit_granule_bytes(), Metaspace::commit_alignment());
  ASSERT_TRUE(is_aligned(Settings::virtual_space_node_default_word_size(),
              metaspace::chunklevel::MAX_CHUNK_WORD_SIZE));
  ASSERT_EQ(Settings::virtual_space_node_default_word_size(),
            metaspace::chunklevel::MAX_CHUNK_WORD_SIZE * 2);
  ASSERT_EQ(Settings::virtual_space_node_reserve_alignment_words(),
            Metaspace::reserve_alignment_words());

}


TEST_VM(metaspace, misc_max_alloc_size)   {

  // Make sure we can allocate what we promise to allocate
  const size_t sz = Metaspace::max_allocation_word_size();
  ClassLoaderData* cld = ClassLoaderData::the_null_class_loader_data();
  MetaWord* p = cld->metaspace_non_null()->allocate(sz, Metaspace::NonClassType);
  ASSERT_NOT_NULL(p);
  cld->metaspace_non_null()->deallocate(p, sz, false);

}

