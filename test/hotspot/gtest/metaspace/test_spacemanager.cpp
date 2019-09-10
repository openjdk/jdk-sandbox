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

#define LOG_PLEASE

#include "metaspace/metaspaceTestsCommon.hpp"



// See spaceManager.cpp : needed for predicting commit sizes.
namespace metaspace {
  extern size_t get_raw_allocation_word_size(size_t net_word_size);
}



class SpaceManagerTest {

  // One global chunk manager, with an assiociated global virtual space list as backing,
  // and a number of space managers feeding from that manager in parallel.
  CommitLimiter _commit_limiter;

  const size_t _avg_occupancy;

  VirtualSpaceList* _vslist;
  ChunkManager* _cm;
  const ChunkAllocSequence* _alloc_sequence;

  const int _num_spaces;

  size_t _rss_at_start;
  size_t _rss_at_end;
  size_t _rss_after_cleanup;


  // A little test bed holding one SpaceManager and its lock
  //  and keeping track of its allocations.
  struct SpaceManagerTestBed : public CHeapObj<mtInternal> {

    Mutex* _lock;
    SpaceManager* _sm;
    int _index;

    // We keep track of individual allocations. Note that this adds
    // 256K per instance of SpaceManagerTestBed.
    struct allocation_t {
      MetaWord* p;
      size_t word_size;
    };
    static const int _max_allocations = 0x4000;
    int _num_allocations;
    size_t _words_allocated;
    allocation_t _allocations[_max_allocations];

    // Note: used counter contains "used" from the chunk perspective, which is
    // used + freelist + alignment corrections. This does not translate 1:1 to
    // _words_allocated, so this is difficult to test.
    SizeAtomicCounter _used_counter;

  public:

    SpaceManager* sm() { return _sm; }

    SpaceManagerTestBed(int index, ChunkManager* cm, const ChunkAllocSequence* alloc_sequence)
      : _lock(NULL), _sm(NULL), _index(index), _num_allocations(0), _words_allocated(0)
    {
      memset(_allocations, 0, sizeof(_allocations));
      _lock = new Mutex(Monitor::native, "gtest-SpaceManagerTestBed-lock", false, Monitor::_safepoint_check_never);
      {
        // Pull lock during space creation, since this is what happens in the VM too
        // (see ClassLoaderData::metaspace_non_null(), which we mimick here).
        MutexLocker ml(_lock,  Mutex::_no_safepoint_check_flag);
        _sm = new SpaceManager(cm, alloc_sequence, _lock, &_used_counter, "gtest-SpaceManagerTestBed-sm");
      }
    }

    ~SpaceManagerTestBed() {

 //     EXPECT_EQ(_used_counter.get(), _words_allocated);

      // Iterate thru all allocation records and test content first.
      for (int i = 0; i < _num_allocations; i ++) {
        const allocation_t* al = _allocations + i;
        EXPECT_TRUE(al->p == NULL || check_marked_range(al->p, (uintx)this, al->word_size));
      }

      // Delete SpaceManager. That should clean up all metaspace.
      delete _sm;
      delete _lock;

    }


    size_t words_allocated() const        { return _words_allocated; }
    size_t num_allocations() const        { return _num_allocations; }

    int index() const                     { return _index; }

    bool is_full() const                  { return _num_allocations == _max_allocations; }
    bool is_empty() const                 { return _num_allocations == 0; }

    // Allocate from space. Returns NULL if either the bed is full or if the allocation
    // itself failed.
    MetaWord* allocate_and_test(size_t word_size) {
      if (is_full()) {
        return NULL;
      }
      MetaWord* p = _sm->allocate(word_size);
      if (p != NULL) {
        EXPECT_TRUE(is_aligned(p, sizeof(void*)));
        mark_range(p, (uintx)this, word_size);
        // Remember this allocation.
        allocation_t* al = _allocations + _num_allocations;
        al->p = p; al->word_size = word_size;
        _num_allocations ++;
        _words_allocated += word_size;
 //       EXPECT_EQ(_used_counter.get(), _words_allocated);
      }
      return p;
    }

    // Deallocate the last allocation done.
    void deallocate_last() {
      assert(_num_allocations > 0, "Sanity");
      allocation_t* al = &_allocations[_num_allocations - 1];
      _sm->deallocate(al->p, al->word_size);
      al->p = NULL;
      _num_allocations --;
    }

    // Deallocate a random single allocation.
    void deallocate_random() {
      if (_num_allocations > 0) {
        int idx = os::random() % _num_allocations;
        allocation_t* al = _allocations + idx;
        if (al->p == NULL) {
          // already deallocated? Should still have its old word size set
          assert(al->word_size > 0, "Sanity");
        } else {
          _sm->deallocate(al->p, al->word_size);
          al->p = NULL; // but leave word_size, see above
        }
      }
    }

  }; // End: SpaceManagerTestBed

  SpaceManagerTestBed** _testbeds;

  SpaceManagerTestBed* testbed_at(int index) {
    assert(index < _num_spaces, "Sanity");
    // Create on the fly if necessary.
    if (_testbeds[index] == NULL) {
      _testbeds[index] = new SpaceManagerTestBed(index, _cm, _alloc_sequence);
    }
    return _testbeds[index];
  }

  SpaceManagerTestBed* next_testbed(SpaceManagerTestBed* bed) {
    int i = bed->index() + 1;
    if (i == _num_spaces) {
      i = 0;
    }
    return testbed_at(i);
  }

  SpaceManagerTestBed* get_random_testbed() {
    const int index = os::random() % _num_spaces;
    return testbed_at(index);
  }

  SpaceManagerTestBed* get_random_matching_testbed(bool should_be_empty) {
    const int start_index = os::random() % _num_spaces;
    int i = start_index;
    do {
      SpaceManagerTestBed* bed = testbed_at(i);
      if ((should_be_empty && bed->words_allocated() == 0) ||
          (!should_be_empty && bed->words_allocated() > 0)) {
        return bed;
      }
      i ++;
      if (i == _num_spaces) {
        i = 0;
      }
    } while (i != start_index);
    return NULL;
  }

  SpaceManagerTestBed* get_random_nonempty_testbed() {
    return get_random_matching_testbed(false);
  }

  SpaceManagerTestBed* get_random_empty_testbed() {
    return get_random_matching_testbed(true);
  }

  MetaWord* alloc_from_testbed(SpaceManagerTestBed* bed, size_t word_size) {
    MetaWord* p = bed->allocate_and_test(word_size);
    if (p == NULL) {
      // Getting NULL back may mean:
      // - testbed is full.
      // - We hit the commit limit.
      if (!bed->is_full()) {
        EXPECT_LT(_commit_limiter.possible_expansion_words(),
                  metaspace::get_raw_allocation_word_size(word_size));
      }
    }
    return p;
  }

  void delete_testbed_at(int index) {
    delete _testbeds[index];
    _testbeds[index] = NULL;
  }

  void delete_testbed(SpaceManagerTestBed* bed) {
    assert(_testbeds[bed->index()] == bed, "Sanity");
    _testbeds[bed->index()] = NULL;
    delete bed;
  }

  // Allocate multiple times random sizes from a single spacemanager.
  // Will stop allocation prematurely if per-space max is reached or if commit limit is reached.
  bool allocate_multiple_random(SpaceManagerTestBed* bed, int num_allocations, RandSizeGenerator* rgen) {
    for (int n = 0; n < num_allocations; n ++) {
      const size_t alloc_size = rgen->get();
      if (alloc_from_testbed(bed, alloc_size) == NULL) {
        return false;
      }
    }
    return true;
  }

  int get_total_number_of_allocations() const {
    int sum = 0;
    for (int i = 0; i < _num_spaces; i ++) {
      if (_testbeds[i] != NULL) {
        sum += _testbeds[i]->num_allocations();
      }
    }
    return sum;
  }

  size_t get_total_words_allocated() const {
    size_t sum = 0;
    for (int i = 0; i < _num_spaces; i ++) {
      if (_testbeds[i] != NULL) {
        sum += _testbeds[i]->words_allocated();
      }
    }
    return sum;
  }

  // Allocate until you reach avg occupancy, hover there by allocating/freeing.
  void test_hover(int num_cycles, int avg_allocs_per_space_manager, RandSizeGenerator* rgen,
                  bool exercise_reclaim, bool exercise_dealloc) {

    int alloc_cycles = 0;
    int free_cycles = 0;
    for (int cyc = 0; cyc < num_cycles; cyc ++) {
      if (get_total_words_allocated() < _avg_occupancy) {
        SpaceManagerTestBed* bed = get_random_testbed();
        if (allocate_multiple_random(bed, avg_allocs_per_space_manager, rgen)) {
          alloc_cycles ++;
        }
      } else {
        SpaceManagerTestBed* bed = get_random_nonempty_testbed();
        if (bed != NULL) {
          free_cycles ++;
          delete_testbed(bed);
        }
      }
      if (exercise_dealloc) {
        if (os::random() % 100 > 95) {
          SpaceManagerTestBed* bed = get_random_nonempty_testbed();
          if (bed != NULL) {
            bed->deallocate_random();
          }
        }
      }
      if (cyc % 100 == 0) {
        const size_t committed_before = _vslist->committed_words();
        if (exercise_reclaim) {
          _cm->wholesale_reclaim();
        }
        LOG("cyc: %d (a %d f %d) allocated: " SIZE_FORMAT ", committed " SIZE_FORMAT "->" SIZE_FORMAT ".",
            cyc, alloc_cycles, free_cycles, get_total_words_allocated(), committed_before, _vslist->committed_words());
      }
    }

//    _vslist->print_on(tty);
//    _cm->print_on(tty);

  } // end: test_hover

  // Allocate until you reach avg occupancy, then drain completely. Repeat.
  void test_wave(int num_cycles, int avg_allocs_per_space_manager, RandSizeGenerator* rgen,
                 bool exercise_reclaim, bool exercise_dealloc) {

    bool rising = true;
    int num_waves = 0;
    for (int cyc = 0; cyc < num_cycles; cyc ++) {
      if (rising) {
        num_waves ++;
        if (get_total_words_allocated() >= _avg_occupancy) {
          rising = false;
        } else {
          SpaceManagerTestBed* bed = get_random_testbed();
          allocate_multiple_random(bed, avg_allocs_per_space_manager, rgen);
        }
      } else {
        SpaceManagerTestBed* bed = get_random_nonempty_testbed();
        if (bed == NULL) {
          EXPECT_EQ(get_total_words_allocated(), (size_t)0);
          rising = true;
        } else {
          delete_testbed(bed);
        }
      }
      if (exercise_dealloc) {
        if (os::random() % 100 > 95) {
          SpaceManagerTestBed* bed = get_random_nonempty_testbed();
          if (bed != NULL) {
            bed->deallocate_random();
          }
        }
      }
      if (cyc % 100 == 0) {
        LOG("cyc: %d num waves: %d num allocations: %d , words allocated: " SIZE_FORMAT ", committed " SIZE_FORMAT ".",
            cyc, num_waves, get_total_number_of_allocations(), get_total_words_allocated(), _vslist->committed_words());
        const size_t committed_before = _vslist->committed_words();
        if (exercise_reclaim) {
          _cm->wholesale_reclaim();
          LOG(".. reclaim: " SIZE_FORMAT "->" SIZE_FORMAT ".", committed_before, _vslist->committed_words());
        }
      }
    }

//    _vslist->print_on(tty);
    //    _cm->print_on(tty);

  } // end: test_wave


  static void check_sm_stat_is_empty(sm_stats_t& stat) {
    in_use_chunk_stats_t totals = stat.totals();
    EXPECT_EQ(totals.word_size, (size_t)0);
    EXPECT_EQ(totals.committed_words, (size_t)0);
    EXPECT_EQ(totals.used_words, (size_t)0);
    EXPECT_EQ(totals.free_words, (size_t)0);
    EXPECT_EQ(totals.waste_words, (size_t)0);
  }

  static void check_sm_stat_is_consistent(sm_stats_t& stat) {
    in_use_chunk_stats_t totals = stat.totals();
    EXPECT_GE(totals.word_size, totals.committed_words);
    EXPECT_EQ(totals.committed_words, totals.used_words + totals.free_words + totals.waste_words);
    EXPECT_GE(totals.used_words, stat.free_blocks_word_size);
  }

  void test_total_statistics() {
    sm_stats_t totals1;
    check_sm_stat_is_empty(totals1);
    sm_stats_t totals2;
    check_sm_stat_is_empty(totals1);
    for (int i = 0; i < _num_spaces; i ++) {
      if (_testbeds[i] != NULL) {
        sm_stats_t stat;
        _testbeds[i]->_sm->add_to_statistics(&stat);
        check_sm_stat_is_consistent(stat);
        DEBUG_ONLY(stat.verify());
        _testbeds[i]->_sm->add_to_statistics(&totals1);
        check_sm_stat_is_consistent(totals1);
        totals2.add(stat);
        check_sm_stat_is_consistent(totals2);
      }
    }
    EXPECT_EQ(totals1.totals().used_words,
              totals2.totals().used_words);
  }

public:

  void run_test(int num_cycles, int avg_allocs_per_space_manager, RandSizeGenerator* rgen,
            bool exercise_reclaim, bool exercise_dealloc) {
    LOG("hover test");
    test_hover(num_cycles, avg_allocs_per_space_manager, rgen, exercise_reclaim, exercise_dealloc);

    test_total_statistics();

    LOG("wave test");
    test_wave(num_cycles, avg_allocs_per_space_manager, rgen, exercise_reclaim, exercise_dealloc);

    test_total_statistics();

  }

  SpaceManagerTest(int num_spaces, size_t avg_occupancy, size_t max_commit_limit, const ChunkAllocSequence* alloc_sequence)
    : _commit_limiter(max_commit_limit), _avg_occupancy(avg_occupancy), _vslist(NULL), _cm(NULL),
      _alloc_sequence(alloc_sequence), _num_spaces(num_spaces),
      _rss_at_start(0), _rss_at_end(0), _rss_after_cleanup(0),
      _testbeds(NULL)
  {
    _rss_at_start = get_workingset_size();

    // Allocate test bed array
    _testbeds = NEW_C_HEAP_ARRAY(SpaceManagerTestBed*, _num_spaces, mtInternal);
    for (int i = 0; i < _num_spaces; i ++) {
      _testbeds[i] = NULL;
    }

    // Create VirtualSpaceList and ChunkManager as backing memory
    _vslist = new VirtualSpaceList("test_vs", &_commit_limiter);

    _cm = new ChunkManager("test_cm", _vslist);

  }

  ~SpaceManagerTest () {

    _rss_at_end = get_workingset_size();

    // Is the memory footprint abnormal? This is necessarily very fuzzy. The memory footprint of these tests
    // is dominated by all metaspace allocations done and the number of spaces, since the SpaceManagerTestBed
    // - due to the fact that we track individual allocations - is rather big.
    const size_t reasonable_expected_footprint = _avg_occupancy * BytesPerWord +
                                                  sizeof(SpaceManagerTestBed) * _num_spaces +
                                                  sizeof(SpaceManagerTestBed*) * _num_spaces +
                                                  sizeof(ChunkManager) + sizeof(VirtualSpaceList);
    const size_t reasonable_expected_footprint_with_margin =
        (reasonable_expected_footprint * 2) + 1 * M;
    EXPECT_LE(_rss_at_end, _rss_at_start + reasonable_expected_footprint_with_margin);

    for (int i = 0; i < _num_spaces; i ++) {
      delete _testbeds[i];
    }

    FREE_C_HEAP_ARRAY(SpaceManagerTestBed*, _testbeds);

    delete _cm;
    delete _vslist;

    _rss_after_cleanup = get_workingset_size();

    // Check for memory leaks. We should ideally be at the baseline of _rss_at_start. However, this depends
    // on whether this gtest was executed as a first test in the suite, since gtest suite adds overhead of 2-4 MB.
    EXPECT_LE(_rss_after_cleanup, _rss_at_start + 4 * M);

    LOG("rss at start: " INTX_FORMAT ", at end " INTX_FORMAT " (" INTX_FORMAT "), after cleanup: " INTX_FORMAT " (" INTX_FORMAT ").", \
        _rss_at_start, _rss_at_end, _rss_at_end - _rss_at_start, _rss_after_cleanup, _rss_after_cleanup - _rss_at_start); \

  }



  void test_deallocation_in_place() {

    // When deallocating, it is attempted to deallocate in place, i.e.
    // if the allocation is the most recent one, the current usage pointer
    // in the current chunk is just reversed back to its original position
    // before the original allocation.
    //
    // But in-place-deallocation will not reverse allocation of the
    // current chunk itself if its usage pointer reaches 0 due to in-place
    // deallocation!
    //
    // In theory, allocating n times, then deallocation in reverse order should
    // happin in place and at the end the usage counter of the Space Manager should
    // be at the original place.
    // However, this is fragile, since when one of the allocations happens to
    // cause the current chunk to be retired and a new one created, the chain
    // breaks at that point (one cannot deallocate in-place from a non-current chunk).
    //
    // Therefore, to make this test reliable, we work on a new empty testbed - so
    // we have a fresh chunk - and with miniscule allocation sizes, to not
    // cause allocation beyond the smallest possible chunk size. That way we
    // will never cause the initial chunk to be retired, regardless of how small it
    // is.

    delete_testbed_at(0);
    SpaceManagerTestBed* bed = testbed_at(0); // new bed

    const int num_loops = 5;
    const size_t max_alloc_size = metaspace::chklvl::MIN_CHUNK_WORD_SIZE / num_loops * 2;

    // Doing this multiple times should work too as long as we keep the
    // reverse allocation order.
    sm_stats_t stat[10]; // taken before each allocation
    size_t alloc_sizes[10] = {
        max_alloc_size,
        1, 2, 3, // <- small sizes to have difference between raw size and net size
        0, 0, 0, 0, 0, 0 // <- use random values;
    };

    RandSizeGenerator rgen(1, max_alloc_size);
    int i = 0;
    while (i < 10) {
      // take stats before allocating...
      bed->sm()->add_to_statistics(stat + i);
      check_sm_stat_is_consistent(stat[i]);

      // and allocate.
      LOG("alloc round #%d (used: " SIZE_FORMAT ").", i, stat[i].totals().used_words);
      const size_t alloc_size = alloc_sizes[i] > 0 ? alloc_sizes[i] : rgen.get();
      MetaWord* p = bed->allocate_and_test(alloc_size);
      ASSERT_NOT_NULL(p);
      i ++;
    }

    // Now reverse-deallocate and compare used_words while doing so.
    // (Note: used_words should be the same after deallocating as before the
    //  original allocation. All other stats cannot be relied on being the same.)
    i --;
    while (i >= 0) {
      // dealloc, measure statistics after each deallocation and compare with
      // the statistics taken at allocation time.
      LOG("dealloc round #%d", i);
      bed->deallocate_last();
      sm_stats_t stat_now;
      bed->sm()->add_to_statistics(&stat_now);
      check_sm_stat_is_consistent(stat_now);
      ASSERT_EQ(stat_now.totals().used_words,
                stat[i].totals().used_words);
      i --;
    }

  } // end: test_deallocation_in_place

};

// for convenience, here some shorthands for standard alloc sequences.
static const ChunkAllocSequence* const g_standard_allocseq_class =
    ChunkAllocSequence::alloc_sequence_by_space_type(metaspace::StandardMetaspaceType, true);
static const ChunkAllocSequence* const g_standard_allocseq_nonclass =
    ChunkAllocSequence::alloc_sequence_by_space_type(metaspace::StandardMetaspaceType, false);
static const ChunkAllocSequence* const g_boot_allocseq_class =
    ChunkAllocSequence::alloc_sequence_by_space_type(metaspace::BootMetaspaceType, true);
static const ChunkAllocSequence* const g_boot_allocseq_nonclass =
    ChunkAllocSequence::alloc_sequence_by_space_type(metaspace::BootMetaspaceType, false);
static const ChunkAllocSequence* const g_refl_allocseq_class =
    ChunkAllocSequence::alloc_sequence_by_space_type(metaspace::ReflectionMetaspaceType, true);
static const ChunkAllocSequence* const g_refl_allocseq_nonclass =
    ChunkAllocSequence::alloc_sequence_by_space_type(metaspace::ReflectionMetaspaceType, false);

// Some standard random size gens.

// generates sizes between 1 and 128 words.
static RandSizeGenerator rgen_1K_no_outliers(1, 128);

// generates sizes between 1 and 256 words, small chance of large outliers
static RandSizeGenerator rgen_1K_some_huge_outliers(1, 256, 0.05, MAX_CHUNK_WORD_SIZE / 64, MAX_CHUNK_WORD_SIZE / 2);

// generates medium sized sizes
static RandSizeGenerator rgen_32K_no_outliers(128, 0x4000);

// large (and pretty unrealistic) spread
static RandSizeGenerator rgen_large_spread(1, MAX_CHUNK_WORD_SIZE);



#define TEST_WITH_PARAMS(name, num_spaces, avg_occ, commit_limit, alloc_seq, rgen, exercise_reclaim, exercise_dealloc) \
TEST_VM(metaspace, space_manager_test_##name) { \
  SpaceManagerTest stest(num_spaces, avg_occ, commit_limit, alloc_seq); \
  stest.run_test(1000, 50, &rgen, exercise_reclaim, exercise_dealloc); \
}

TEST_WITH_PARAMS(test0, 1, 64 * K, SIZE_MAX, g_standard_allocseq_nonclass, rgen_1K_no_outliers, true, false);

TEST_WITH_PARAMS(test1, 10, 1 * M, SIZE_MAX, g_standard_allocseq_nonclass, rgen_1K_no_outliers, true, false);
TEST_WITH_PARAMS(test2, 10, 1 * M, SIZE_MAX, g_standard_allocseq_nonclass, rgen_1K_no_outliers, false, true);
TEST_WITH_PARAMS(test3, 10, 1 * M, SIZE_MAX, g_standard_allocseq_nonclass, rgen_1K_no_outliers, false, false);

TEST_WITH_PARAMS(test4, 10, 1 * M, SIZE_MAX, g_boot_allocseq_nonclass, rgen_1K_no_outliers, true, false);
TEST_WITH_PARAMS(test5, 10, 1 * M, SIZE_MAX, g_boot_allocseq_nonclass, rgen_1K_no_outliers, false, false);

TEST_WITH_PARAMS(test6, 10, 1 * M, SIZE_MAX, g_standard_allocseq_nonclass, rgen_1K_some_huge_outliers, true, false);
TEST_WITH_PARAMS(test7, 10, 1 * M, SIZE_MAX, g_standard_allocseq_nonclass, rgen_1K_some_huge_outliers, false, false);

TEST_WITH_PARAMS(test8, 10, 1 * M, SIZE_MAX, g_standard_allocseq_nonclass, rgen_32K_no_outliers, true, false);
TEST_WITH_PARAMS(test9, 10, 1 * M, SIZE_MAX, g_standard_allocseq_nonclass, rgen_32K_no_outliers, false, false);

TEST_WITH_PARAMS(test10,  10, 10 * M, 2 * M, g_standard_allocseq_nonclass, rgen_1K_some_huge_outliers, true, false);
TEST_WITH_PARAMS(test11,  10, 10 * M, 2 * M, g_standard_allocseq_nonclass, rgen_1K_some_huge_outliers, false, false);

TEST_WITH_PARAMS(test12,  10, 10 * M, SIZE_MAX, g_standard_allocseq_nonclass, rgen_large_spread, true, false);
TEST_WITH_PARAMS(test13,  10, 10 * M, SIZE_MAX, g_standard_allocseq_nonclass, rgen_large_spread, false, false);

TEST_WITH_PARAMS(test_14, 10, 1 * M, SIZE_MAX, g_standard_allocseq_nonclass, rgen_32K_no_outliers, true, true);
TEST_WITH_PARAMS(test_15, 10, 1 * M, SIZE_MAX, g_standard_allocseq_nonclass, rgen_large_spread, true, false);


TEST_VM(metaspace, space_manager_test_deallocation_in_place) {
  // Test deallocation with no commit limit
  SpaceManagerTest stest(1, 1 * M, 2 * M, g_boot_allocseq_class);
  stest.test_deallocation_in_place();
}



