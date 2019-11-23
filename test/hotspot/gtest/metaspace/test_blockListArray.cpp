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

TEST_VM(metaspace, BlockListFreeMap_mask_basic) {
  // Basic tests
  metaspace::BlockListFreeMap map;
  EXPECT_TRUE(map.all_zero());
  for (int i = 0; i < map.size(); i ++) {
    map.set_bit(i);
    EXPECT_TRUE(map.get_bit(i));
    map.clr_bit(i);
    EXPECT_FALSE(map.get_bit(i));
    EXPECT_TRUE(map.all_zero());
  }
}

TEST_VM(metaspace, BlockListFreeMap_mask_find_next_set_bit) {
  metaspace::BlockListFreeMap map;
  EXPECT_TRUE(map.all_zero());
  for (int i = 0; i < map.size(); i ++) {
    map.set_bit(i);
    for (int j = 0; j < i; j ++) {
      int n = map.find_next_set_bit(j);
      if (j <= i) {
        EXPECT_EQ(n, i);
      } else {
        EXPECT_EQ(n, -1);
      }
    }
    map.clr_bit(i);
  }
}

