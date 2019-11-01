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

class LeftOverBinsTest {

  // A simple preallocated buffer used to "feed" the allocator.
  // Mimicks chunk retirement leftover blocks.
  class FeederBuffer {

    static const size_t buf_word_size = 512 * K;
    MetaWord* _buf;
    size_t _used;

  public:

    FeederBuffer() : _used(0) {
      _buf = NEW_C_HEAP_ARRAY(MetaWord, buf_word_size, mtInternal);
    }

    ~FeederBuffer() {
      FREE_C_HEAP_ARRAY(MetaWord, _buf);
    }

    MetaWord* get(size_t word_size) {
      if (_used > (buf_word_size - word_size)) {
        return NULL;
      }
      MetaWord* p = _buf + _used;
      _used += word_size;
      return p;
    }

  };

  FeederBuffer _fb;
  LeftOverManager _lom;

  // random generator for block feeding
  RandSizeGenerator _rgen_feeding;

  // random generator for allocations (and, hence, deallocations)
  RandSizeGenerator _rgen_allocations;

  SizeCounter _allocated_words;

  struct allocation_t {
    allocation_t* next;
    size_t word_size;
    MetaWord* p;
  };

  // Array of the same size as the pool max capacity; holds the allocated elements.
  allocation_t* _allocations;


  int _num_allocs;
  int _num_deallocs;
  int _num_feeds;

  bool feed_some() {
    size_t word_size = _rgen_feeding.get();
    MetaWord* p = _fb.get(word_size);
    if (p != NULL) {
      _lom.add_block(p, word_size);
      return true;
    }
    return false;
  }

  void deallocate_top() {

    allocation_t* a = _allocations;
    if (a != NULL) {
      _allocations = a->next;
      check_marked_range(a->p, a->word_size);
      _lom.add_block(a->p, a->word_size);
      delete a;
      DEBUG_ONLY(_lom.verify();)
    }
  }

  bool allocate() {

    size_t word_size = MAX2(_rgen_allocations.get(), _lom.minimal_word_size());
    MetaWord* p = _lom.get_block(word_size);
    if (p != NULL) {
      _allocated_words.increment_by(word_size);
      allocation_t* a = new allocation_t;
      a->p = p; a->word_size = word_size;
      a->next = _allocations;
      _allocations = a;
      DEBUG_ONLY(_lom.verify();)
      mark_range(p, word_size);
      return true;
    }
    return false;
  }

  void test_all_marked_ranges() {
    for (allocation_t* a = _allocations; a != NULL; a = a->next) {
      check_marked_range(a->p, a->word_size);
    }
  }

  void test_loop() {
    // We loop and in each iteration execute one of three operations:
    // - allocation from lom
    // - deallocation to lom of a previously allocated block
    // - feeding a new larger block into the lom (mimicks chunk retiring)
    // When we have fed all large blocks into the lom (feedbuffer empty), we
    //  switch to draining the lom completely (only allocs)
    bool forcefeed = false;
    bool draining = false;
    bool stop = false;
    int iter = 100000; // safety stop
    while (!stop && iter > 0) {
      iter --;
      int surprise = (int)os::random() % 10;
      if (!draining && (surprise >= 7 || forcefeed)) {
        forcefeed = false;
        if (feed_some()) {
          _num_feeds ++;
        } else {
          // We fed all input memory into the LOM. Now lets proceed until the lom is drained.
          draining = true;
        }
      } else if (!draining && surprise < 1) {
        deallocate_top();
        _num_deallocs ++;
      } else {
        if (allocate()) {
          _num_allocs ++;
        } else {
          if (draining) {
            stop = _lom.total_word_size() < 512;
          } else {
            forcefeed = true;
          }
        }
      }
      if ((iter % 1000) == 0) {
        DEBUG_ONLY(_lom.verify();)
        test_all_marked_ranges();
        LOG("a %d (" SIZE_FORMAT "), d %d, f %d", _num_allocs, _allocated_words.get(), _num_deallocs, _num_feeds);
#ifdef LOG_PLEASE
        _lom.print(tty, true);
        tty->cr();
#endif
      }
    }

    // Drain


  }



public:

  LeftOverBinsTest(size_t avg_alloc_size) :
    _fb(), _lom(),
    _rgen_feeding(128, 4096),
    _rgen_allocations(avg_alloc_size / 4, avg_alloc_size * 2, 0.01f, avg_alloc_size / 3, avg_alloc_size * 30),
    _allocations(NULL),
    _num_allocs(0), _num_deallocs(0), _num_feeds(0)
  {
    // some initial feeding
    _lom.add_block(_fb.get(1024), 1024);
  }


  static void test_small_allocations() {
    LeftOverBinsTest test(10);
    test.test_loop();
  }

  static void test_medium_allocations() {
    LeftOverBinsTest test(30);
    test.test_loop();
  }

  static void test_large_allocations() {
    LeftOverBinsTest test(150);
    test.test_loop();
  }


};

TEST_VM(metaspace, leftoverbins_mask_basic) {
  // Basic tests
  metaspace::BinMap map;
  EXPECT_TRUE(map.all_zero());
  for (int i = 0; i < map.size(); i ++) {
    map.set_bit(i);
    EXPECT_TRUE(map.get_bit(i));
    map.clr_bit(i);
    EXPECT_FALSE(map.get_bit(i));
    EXPECT_TRUE(map.all_zero());
  }
}

TEST_VM(metaspace, leftoverbins_mask_find_next_set_bit) {
  metaspace::BinMap map;
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

TEST_VM(metaspace, leftoverbins_basics) {

  LeftOverManager lom;
  MetaWord tmp[1024];
  metaspace::block_stats_t stats;

  lom.add_block(tmp, 1024);
  DEBUG_ONLY(lom.verify();)

  lom.statistics(&stats);
  EXPECT_EQ(stats.num_blocks, 1);
  EXPECT_EQ(stats.word_size, (size_t)1024);

  MetaWord* p = lom.get_block(1024);
  EXPECT_EQ(p, tmp);
  DEBUG_ONLY(lom.verify();)

  lom.statistics(&stats);
  EXPECT_EQ(stats.num_blocks, 0);
  EXPECT_EQ(stats.word_size, (size_t)0);
}

TEST_VM(metaspace, leftoverbins_small) {
  LeftOverBinsTest::test_small_allocations();
}

TEST_VM(metaspace, leftoverbins_medium) {
  LeftOverBinsTest::test_medium_allocations();
}

TEST_VM(metaspace, leftoverbins_large) {
  LeftOverBinsTest::test_large_allocations();
}

