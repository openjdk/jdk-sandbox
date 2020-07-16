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


#define CHECK_BT_CONTENT(bt, expected_num, expected_size) { \
  EXPECT_EQ(bt.count(), (unsigned)expected_num); \
  EXPECT_EQ(bt.total_size(), (size_t)expected_size); \
  if (expected_num == 0) { \
    EXPECT_TRUE(bt.is_empty()); \
  } else { \
    EXPECT_FALSE(bt.is_empty()); \
  } \
}

TEST_VM(metaspace, BlockTree_basic) {

  BlockTree bt;
  CHECK_BT_CONTENT(bt, 0, 0);

  size_t real_size = 0;
  MetaWord* p = NULL;
  MetaWord arr[10000];

  const size_t minws = BlockTree::minimal_word_size;
  const size_t maxws = 4096;

  // get_block from empty tree should yield nothing
  p = bt.get_block(minws, &real_size);
  EXPECT_EQ(p, (MetaWord*)NULL);
  EXPECT_EQ(real_size, (size_t)0);
  CHECK_BT_CONTENT(bt, 0, 0);

  // Add some blocks and retrieve them right away.
  size_t sizes[] = {
      minws + 10,
      maxws - 10,
      minws, // smallest possible
      maxws - 1, // largest possible
      0
  };

  for (int i = 0; sizes[i] > 0; i ++) {
    bt.add_block(arr, sizes[i]);
    CHECK_BT_CONTENT(bt, 1, sizes[i]);

    DEBUG_ONLY(bt.verify();)

    MetaWord* p = bt.get_block(sizes[i], &real_size);
    EXPECT_EQ(p, arr);
    EXPECT_EQ(real_size, (size_t)sizes[i]);
    CHECK_BT_CONTENT(bt, 0, 0);
  }

}

TEST_VM(metaspace, BlockTree_closest_fit) {

  // Test the fact that getting blocks should always return the closest fit
  BlockTree bt;
  FeederBuffer fb(10000);

  const size_t minws = BlockTree::minimal_word_size;
  const size_t maxws = 256;

  size_t sizes[] = {
      minws + 9,
      minws + 3,
      minws + 9,
      minws,
      minws + 8,
      maxws - 2,
      minws,
      maxws - 1,
      0
  };

  size_t size_added = 0;
  int num_added = 0;

  for (int i = 0; sizes[i] > 0; i ++) {
    const size_t s = sizes[i];
    MetaWord* p = fb.get(s);
    bt.add_block(p, s);
    num_added ++; size_added += s;
    CHECK_BT_CONTENT(bt, num_added, size_added);
  }

  DEBUG_ONLY(bt.verify();)

  size_t last_size = 0;
  while (bt.is_empty() == false) {
    size_t real_size = 0;
    MetaWord* p = bt.get_block(minws, &real_size);
    EXPECT_TRUE(fb.is_valid_range(p, real_size));

    EXPECT_GE(real_size, last_size);
    last_size = real_size;

    num_added --;
    size_added -= real_size;
    CHECK_BT_CONTENT(bt, num_added, size_added);
  }

  CHECK_BT_CONTENT(bt, 0, 0);

}


TEST_VM(metaspace, BlockTree_basic_siblings)
{
  BlockTree bt;
  CHECK_BT_CONTENT(bt, 0, 0);

  const size_t minws = BlockTree::minimal_word_size;
  const size_t maxws = 256;
  const size_t test_size = minws + 17;
  const int num = 10;

  MetaWord* arr = NEW_C_HEAP_ARRAY(MetaWord, num * test_size, mtInternal);

  for (int i = 0; i < num; i ++) {
    bt.add_block(arr + (i * test_size), test_size);
    CHECK_BT_CONTENT(bt, i + 1, (i + 1) * test_size);
  }

  DEBUG_ONLY(bt.verify();)

  for (int i = num; i > 0; i --) {
    size_t real_size = 4711;
    MetaWord* p = bt.get_block(test_size, &real_size);
    EXPECT_LT(p, arr + num * test_size);
    EXPECT_GE(p, arr);
    EXPECT_EQ(real_size, (size_t)test_size);
    CHECK_BT_CONTENT(bt, i - 1, (i - 1) * test_size);
  }

  FREE_C_HEAP_ARRAY(MetaWord, arr);
}

class BlockTreeTest {

  FeederBuffer _fb;

  BlockTree _bt[2];
  MemRangeCounter _cnt[2];

  RandSizeGenerator _rgen;

#define CHECK_COUNTERS \
		CHECK_BT_CONTENT(_bt[0], _cnt[0].count(), _cnt[0].total_size()) \
    CHECK_BT_CONTENT(_bt[1], _cnt[1].count(), _cnt[1].total_size())

#define CHECK_COUNTERS_ARE_0 \
    CHECK_BT_CONTENT(_bt[0], 0, 0) \
    CHECK_BT_CONTENT(_bt[1], 0, 0)

#ifdef ASSERT
  void verify_trees() {
    _bt[0].verify();
    _bt[1].verify();
  }
#endif

  enum feeding_pattern_t {
    scatter = 1,
    left_right = 2,
    right_left = 3
  };

  void feed_all(feeding_pattern_t feeding_pattern) {

    // Feed the whole feaderbuffer space to the trees.
    MetaWord* p = NULL;
    unsigned added = 0;

    // If we feed in small graining, we cap the number of blocks to limit test duration.
    const unsigned max_blocks = 10000;

    size_t old_feeding_size = feeding_pattern == right_left ? _rgen.max() : _rgen.min();
    do {
      size_t s = 0;
      switch (feeding_pattern) {
      case scatter:
        // fill completely random
        s =_rgen.get();
        break;
      case left_right:
        // fill in ascending order to annoy trees.
        s = MIN2(_rgen.get(), old_feeding_size);
        old_feeding_size = s;
        break;
      case right_left:
        // same, but descending.
        s = MAX2(_rgen.get(), old_feeding_size);
        old_feeding_size = s;
        break;
      }

      p = _fb.get(s);
      if (p != NULL) {
        int which = added % 2;
        added ++;
        _bt[which].add_block(p, s);
        _cnt[which].add(s);
        CHECK_COUNTERS
      }
      DEBUG_ONLY(verify_trees();)
      CHECK_COUNTERS;
    } while (p != NULL && added < max_blocks);

    // Trees should be populated in a balanced way, and not empty
    EXPECT_TRUE( _bt[0].count() == _bt[1].count() ||
                (_bt[0].count() == _bt[1].count() + 1 && _bt[0].count() > 0));

  }

  void ping_pong_loop(int iterations) {

    // We loop and in each iteration randomly retrieve a block from one tree and add it to another.
    for (int i = 0; i < iterations; i ++) {
      int taker = 0;
      int giver = 1;
      if ((os::random() % 10) > 5) {
        giver = 0; taker = 1;
      }
      size_t s =_rgen.get();
      size_t real_size = 0;
      MetaWord* p = _bt[giver].get_block(s, &real_size);
      if (p == NULL) {
        // Todo: check that bt really has no larger block than this.
      } else {
        ASSERT_TRUE(_fb.is_valid_range(p, real_size));
        ASSERT_GE(real_size, s);
        _bt[taker].add_block(p, real_size);
        _cnt[giver].sub(real_size);
        _cnt[taker].add(real_size);
        CHECK_COUNTERS;
      }

#ifdef ASSERT
      if (true) {//i % 1000 == 0) {
        verify_trees();
      }
#endif
    }
  }

  // Drain the trees. While draining, observe the order of the drained items.
  void drain_all() {

    for (int which = 0; which < 2; which ++) {
      BlockTree* bt = _bt + which;
      size_t last_size = 0;
      while(!bt->is_empty()) {

        // We only query for the minimal size. Actually returned size should be
        // monotonously growing since get_block should always return the closest fit.
        size_t real_size = 4711;
        MetaWord* p = bt->get_block(BlockTree::minimal_word_size, &real_size);
        ASSERT_TRUE(_fb.is_valid_range(p, real_size));

        ASSERT_GE(real_size, last_size);
        last_size = real_size;

        _cnt[which].sub(real_size);
        CHECK_COUNTERS;

#ifdef ASSERT
        if (true) {//i % 1000 == 0) {
          bt->verify();
        }
#endif
      }
    }

  }

  void test(feeding_pattern_t feeding_pattern) {

    CHECK_COUNTERS_ARE_0

    feed_all(feeding_pattern);

    LOG("Blocks in circulation: bt1=%d:" SIZE_FORMAT ", bt2=%d:" SIZE_FORMAT ".",
        _bt[0].count(), _bt[0].total_size(),
        _bt[1].count(), _bt[1].total_size());

    ping_pong_loop(3000);

    LOG("After Pingpong: bt1=%d:" SIZE_FORMAT ", bt2=%d:" SIZE_FORMAT ".",
        _bt[0].count(), _bt[0].total_size(),
        _bt[1].count(), _bt[1].total_size());

    drain_all();

    CHECK_COUNTERS_ARE_0
  }


public:

  BlockTreeTest(size_t min_word_size, size_t max_word_size) :
    _fb(2 * M),
    _bt(),
    _rgen(min_word_size, max_word_size)
  {
    CHECK_COUNTERS;
    DEBUG_ONLY(verify_trees();)
  }


  void test_scatter()      { test(scatter); }
  void test_right_left()   { test(right_left); }
  void test_left_right()   { test(left_right); }

};

#define DO_TEST(name, feedingpattern, min, max) \
		TEST_VM(metaspace, BlockTree_##name##_##feedingpattern) { \
      BlockTreeTest btt(min, max); \
      btt.test_##feedingpattern(); \
    }

#define DO_TEST_ALL_PATTERNS(name, min, max) \
  DO_TEST(name, scatter, min, max) \
  DO_TEST(name, right_left, min, max) \
  DO_TEST(name, left_right, min, max)


DO_TEST_ALL_PATTERNS(wide, BlockTree::minimal_word_size, 128 * K);
DO_TEST_ALL_PATTERNS(narrow, BlockTree::minimal_word_size, 16)
DO_TEST_ALL_PATTERNS(129, BlockTree::minimal_word_size, 129)
DO_TEST_ALL_PATTERNS(4096, BlockTree::minimal_word_size, 4*K)
DO_TEST_ALL_PATTERNS(1M, BlockTree::minimal_word_size, 1 * M)



