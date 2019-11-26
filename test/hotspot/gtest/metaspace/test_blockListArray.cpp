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
  metaspace::BlockListArrayMask map;
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
  metaspace::BlockListArrayMask map;
  EXPECT_TRUE(map.all_zero());
  for (int i = 0; i < map.size(); i ++) {
    map.set_bit(i);
    for (int j = 0; j < i; j ++) {
      int n = map.find_next_set_bit(j);
      if (j < i) {
        EXPECT_EQ(n, i);
      } else {
        EXPECT_EQ(n, -1);
      }
    }
    map.clr_bit(i);
  }
}

#define CHECK_BLA_CONTENT(BLA, NUM_EXPECTED, SIZE_EXPECTED) \
{ \
  metaspace::block_stats_t stat; \
  memset(&stat, 0xFF, sizeof(stat)); \
  BLA.statistics(&stat); \
  ASSERT_EQ(stat.num_blocks, (int)NUM_EXPECTED); \
  ASSERT_EQ(stat.word_size, (size_t)SIZE_EXPECTED); \
  if (NUM_EXPECTED == 0) { \
	  ASSERT_TRUE(BLA.is_empty()); \
  } else { \
	  ASSERT_FALSE(BLA.is_empty()); \
  } \
}



TEST_VM(metaspace, BlockListArray_basic) {

  metaspace::BlockListArray<100, 5, 20> bla;
  ASSERT_EQ(bla.maximal_word_size(), (size_t)200);
  ASSERT_EQ(bla.minimal_word_size(), (size_t)100);

  CHECK_BLA_CONTENT(bla, 0, 0);

  // Put something into the bla and check bla.
  // Take something out of the bla; any allocation smaller
  // than the one block in it shall succeed.
  MetaWord tmp[1024];

  for (size_t feeding_size = 100; feeding_size < 200; feeding_size ++) {
    for (size_t l = 100; l < 200; l ++) {
      LOG(SIZE_FORMAT "-" SIZE_FORMAT, feeding_size, l);

      bla.put(tmp, feeding_size);
      CHECK_BLA_CONTENT(bla, 1, feeding_size);

      metaspace::block_t* b = bla.get(l);

      if (l <= feeding_size) {
        // We expect the get() to work and return the block we just put in
        // if the size we ask for is smaller than the size we put in.
        ASSERT_NOT_NULL(b);
        ASSERT_EQ((MetaWord*) b, tmp);
        ASSERT_EQ(b->size, feeding_size);
        CHECK_BLA_CONTENT(bla, 0, 0);
        memset(b, 0xDE, b->size * sizeof(MetaWord));
      } else {
        // Otherwise we expect the bla to be unchanged.
        assert(b == NULL, "s");
        ASSERT_NULL(b);
        CHECK_BLA_CONTENT(bla, 1, feeding_size);
      }
      DEBUG_ONLY(bla.verify();)

      // Regardless of bla's state, empty it out for the next iteration.
      bla.get(feeding_size);
      CHECK_BLA_CONTENT(bla, 0, 0);
    }
  }
}

TEST_VM(metaspace, BlockListArray_fill_and_drain) {

  metaspace::BlockListArray<100, 5, 20> bla;
  ASSERT_EQ(bla.maximal_word_size(), (size_t)200);
  ASSERT_EQ(bla.minimal_word_size(), (size_t)100);

  CHECK_BLA_CONTENT(bla, 0, 0);

  // Now feed it some memory:
  FeederBuffer fb(16 * K);
  RandSizeGenerator rgen(100, 200);
  int num_fed = 0;
  size_t size_fed = 0;
  MetaWord* p = NULL;
  do {
    const size_t s = rgen.get();
    p = fb.get(s);
    if (p != NULL) {
      num_fed ++;
      size_fed += s;
      bla.put(p, s);
      CHECK_BLA_CONTENT(bla, num_fed, size_fed);
    }
  } while (p != NULL);

  DEBUG_ONLY(bla.verify();)

  // Now remove memory until empty:
  int num_retrieved = 0;
  size_t size_retrieved = 0;
  metaspace::block_t* b = NULL;
  do {
    const size_t s = rgen.get();
    metaspace::block_t* b = bla.get(s);
    if (p != NULL) {
      ASSERT_GE(b->size, s);
      num_retrieved ++;
      size_retrieved += b->size;
      memset(b, 0xDE, b->size * BytesPerWord);
      CHECK_BLA_CONTENT(bla, num_fed - num_retrieved,
                             size_fed - size_retrieved);
      ASSERT_LE(num_retrieved, num_fed);
      ASSERT_LE(size_retrieved, size_fed);
    }

  } while (p != NULL);

  DEBUG_ONLY(bla.verify();)

}

