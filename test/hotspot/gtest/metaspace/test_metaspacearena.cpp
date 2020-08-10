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

#include "metaspace/metaspaceTestsCommon.hpp"
#include "metaspace/metaspaceTestContexts.hpp"
#include "metaspace/metaspace_sparsearray.hpp"
#include "utilities/ostream.hpp"


// TODO: this class is very similar to MetaspaceArenaTestBed in test_metaspacearena_stress.cpp.
// should be unified.
class MetaspaceArenaTestHelper {

  MetaspaceTestContext& _helper;

  Mutex* _lock;
  const ArenaGrowthPolicy* _growth_policy;
  SizeAtomicCounter _used_words_counter;
  MetaspaceArena* _arena;

public:

  MetaspaceArenaTestHelper(MetaspaceTestContext& helper, Metaspace::MetaspaceType space_type, bool is_class,
                         const char* name = "gtest-MetaspaceArena")
    : _helper(helper),
      _lock(NULL),
      _growth_policy(NULL),
      _used_words_counter(),
      _arena(NULL)
  {
    _growth_policy = ArenaGrowthPolicy::policy_for_space_type(space_type, is_class);
    _lock = new Mutex(Monitor::native, "gtest-MetaspaceArenaTest-lock", false, Monitor::_safepoint_check_never);
    // Lock during space creation, since this is what happens in the VM too
    //  (see ClassLoaderData::metaspace_non_null(), which we mimick here).
    {
      MutexLocker ml(_lock,  Mutex::_no_safepoint_check_flag);
      _arena = new MetaspaceArena(&_helper.cm(), _growth_policy, _lock, &_used_words_counter, name);
    }
    DEBUG_ONLY(_arena->verify(true));
  }

  ~MetaspaceArenaTestHelper() {
    delete_arena_with_tests();
    delete _lock;
  }

  const CommitLimiter& limiter() const { return _helper.commit_limiter(); }
  MetaspaceArena* arena() const { return _arena; }
  SizeAtomicCounter& used_words_counter() { return _used_words_counter; }

  // Note: all test functions return void due to gtests limitation that we cannot use ASSERT
  // in non-void returning tests.

  void delete_arena_with_tests() {
    if (_arena != NULL) {
      size_t used_words_before = _used_words_counter.get();
      size_t committed_words_before = limiter().committed_words();
      DEBUG_ONLY(_arena->verify(true));
      delete _arena;
      _arena = NULL;
      size_t used_words_after = _used_words_counter.get();
      size_t committed_words_after = limiter().committed_words();
      ASSERT_0(used_words_after);
      if (Settings::uncommit_free_chunks()) {
        ASSERT_LE(committed_words_after, committed_words_before);
      } else {
        ASSERT_EQ(committed_words_after, committed_words_before);
      }
    }
  }

  void usage_numbers_with_test(size_t* p_used, size_t* p_committed, size_t* p_capacity) const {
    _arena->usage_numbers(p_used, p_committed, p_capacity);
    if (p_used != NULL) {
      if (p_committed != NULL) {
        ASSERT_GE(*p_committed, *p_used);
      }
      // Since we own the used words counter, it should reflect our usage number 1:1
      ASSERT_EQ(_used_words_counter.get(), *p_used);
    }
    if (p_committed != NULL && p_capacity != NULL) {
      ASSERT_GE(*p_capacity, *p_committed);
    }
  }

  // Allocate; caller expects success; return pointer in *p_return_value
  void allocate_from_arena_with_tests_expect_success(MetaWord** p_return_value, size_t word_size) {
    allocate_from_arena_with_tests(p_return_value, word_size);
    ASSERT_NOT_NULL(*p_return_value);
  }

  // Allocate; caller expects success but is not interested in return value
  void allocate_from_arena_with_tests_expect_success(size_t word_size) {
    MetaWord* dummy = NULL;
    allocate_from_arena_with_tests_expect_success(&dummy, word_size);
  }

  // Allocate; caller expects failure
  void allocate_from_arena_with_tests_expect_failure(size_t word_size) {
    MetaWord* dummy = NULL;
    allocate_from_arena_with_tests(&dummy, word_size);
    ASSERT_NULL(dummy);
  }

  // Allocate; it may or may not work; return value in *p_return_value
  void allocate_from_arena_with_tests(MetaWord** p_return_value, size_t word_size) {

    // Note: usage_numbers walks all chunks in use and counts.
    size_t used = 0, committed = 0, capacity = 0;
    usage_numbers_with_test(&used, &committed, &capacity);

    size_t possible_expansion = limiter().possible_expansion_words();

    MetaWord* p = _arena->allocate(word_size);

    SOMETIMES(DEBUG_ONLY(_arena->verify(true);))

    size_t used2 = 0, committed2 = 0, capacity2 = 0;
    usage_numbers_with_test(&used2, &committed2, &capacity2);

    if (p == NULL) {
      // Allocation failed.
      if (Settings::new_chunks_are_fully_committed()) {
        ASSERT_LT(possible_expansion, MAX_CHUNK_WORD_SIZE);
      } else {
        ASSERT_LT(possible_expansion, word_size);
      }

      ASSERT_EQ(used, used2);
      ASSERT_EQ(committed, committed2);
      ASSERT_EQ(capacity, capacity2);
    } else {
      // Allocation succeeded. Should be correctly aligned.
      ASSERT_TRUE(is_aligned(p, sizeof(MetaWord)));
      // used: may go up or may not (since our request may have been satisfied from the freeblocklist
      //   whose content already counts as used).
      // committed: may go up, may not
      // capacity: ditto
      ASSERT_GE(used2, used);
      ASSERT_GE(committed2, committed);
      ASSERT_GE(capacity2, capacity);
    }

    *p_return_value = p;
  }

  // Allocate; it may or may not work; but caller does not care for the result value
  void allocate_from_arena_with_tests(size_t word_size) {
    MetaWord* dummy = NULL;
    allocate_from_arena_with_tests(&dummy, word_size);
  }


  void deallocate_with_tests(MetaWord* p, size_t word_size) {
    size_t used = 0, committed = 0, capacity = 0;
    usage_numbers_with_test(&used, &committed, &capacity);

    _arena->deallocate(p, word_size);

    SOMETIMES(DEBUG_ONLY(_arena->verify(true);))

    size_t used2 = 0, committed2 = 0, capacity2 = 0;
    usage_numbers_with_test(&used2, &committed2, &capacity2);

    // Nothing should have changed. Deallocated blocks are added to the free block list
    // which still counts as used.
    ASSERT_EQ(used2, used);
    ASSERT_EQ(committed2, committed);
    ASSERT_EQ(capacity2, capacity);
  }


};


static void test_basics(size_t commit_limit, bool is_micro) {
  MetaspaceTestContext msthelper(commit_limit);
  MetaspaceArenaTestHelper helper(msthelper, is_micro ? Metaspace::ReflectionMetaspaceType : Metaspace::StandardMetaspaceType, false);

  helper.allocate_from_arena_with_tests(1);
  helper.allocate_from_arena_with_tests(128);
  helper.allocate_from_arena_with_tests(128 * K);
  helper.allocate_from_arena_with_tests(1);
  helper.allocate_from_arena_with_tests(128);
  helper.allocate_from_arena_with_tests(128 * K);
}

TEST_VM(metaspace, MetaspaceArena_basics_micro_nolimit) {
  test_basics(max_uintx, true);
}

TEST_VM(metaspace, MetaspaceArena_basics_micro_limit) {
  test_basics(256 * K, true);
}

TEST_VM(metaspace, MetaspaceArena_basics_standard_nolimit) {
  test_basics(max_uintx, false);
}

TEST_VM(metaspace, MetaspaceArena_basics_standard_limit) {
  test_basics(256 * K, false);
}


// Test: in a single undisturbed MetaspaceArena (so, we should have chunks enlarged in place)
// we allocate a small amount, then the full amount possible. The sum of first and second
// allocation bring us above root chunk size. This should work - chunk enlargement should
// fail and a new root chunk should be allocated instead.
TEST_VM(metaspace, MetaspaceArena_test_enlarge_in_place) {

  if (Settings::use_allocation_guard()) {
    return;
  }

  MetaspaceTestContext msthelper;
  MetaspaceArenaTestHelper helper(msthelper, Metaspace::StandardMetaspaceType, false);
  helper.allocate_from_arena_with_tests_expect_success(1);
  helper.allocate_from_arena_with_tests_expect_success(MAX_CHUNK_WORD_SIZE);
  helper.allocate_from_arena_with_tests_expect_success(MAX_CHUNK_WORD_SIZE / 2);
  helper.allocate_from_arena_with_tests_expect_success(MAX_CHUNK_WORD_SIZE);
}

// Test allocating from smallest to largest chunk size, and one step beyond.
// The first n allocations should happen in place, the ladder should open a new chunk.
TEST_VM(metaspace, MetaspaceArena_test_enlarge_in_place_ladder_1) {

  if (Settings::use_allocation_guard()) {
    return;
  }

  MetaspaceTestContext msthelper;
  MetaspaceArenaTestHelper helper(msthelper, Metaspace::StandardMetaspaceType, false);
  size_t size = MIN_CHUNK_WORD_SIZE;
  while (size <= MAX_CHUNK_WORD_SIZE) {
    helper.allocate_from_arena_with_tests_expect_success(size);
    size *= 2;
  }
  helper.allocate_from_arena_with_tests_expect_success(MAX_CHUNK_WORD_SIZE);
}

// Same as MetaspaceArena_test_enlarge_in_place_ladder_1, but increase in *4 step size;
// this way chunk-in-place-enlargement does not work and we should have new chunks at each allocation.
TEST_VM(metaspace, MetaspaceArena_test_enlarge_in_place_ladder_2) {

  if (Settings::use_allocation_guard()) {
    return;
  }

  MetaspaceTestContext msthelper;
  MetaspaceArenaTestHelper helper(msthelper, Metaspace::StandardMetaspaceType, false);
  size_t size = MIN_CHUNK_WORD_SIZE;
  while (size <= MAX_CHUNK_WORD_SIZE) {
    helper.allocate_from_arena_with_tests_expect_success(size);
    size *= 4;
  }
  helper.allocate_from_arena_with_tests_expect_success(MAX_CHUNK_WORD_SIZE);
}

// Test the MetaspaceArenas' free block list:
// Allocate, deallocate, then allocate the same block again. The second allocate should
// reuse the deallocated block.
TEST_VM(metaspace, MetaspaceArena_deallocate) {
  if (Settings::use_allocation_guard()) {
    return;
  }
  for (size_t s = 2; s <= MAX_CHUNK_WORD_SIZE; s *= 2) {
    MetaspaceTestContext msthelper;
    MetaspaceArenaTestHelper helper(msthelper, Metaspace::StandardMetaspaceType, false);

    MetaWord* p1 = NULL;
    helper.allocate_from_arena_with_tests_expect_success(&p1, s);

    size_t used1 = 0, capacity1 = 0;
    helper.usage_numbers_with_test(&used1, NULL, &capacity1);
    ASSERT_EQ(used1, s);

    helper.deallocate_with_tests(p1, s);

    size_t used2 = 0, capacity2 = 0;
    helper.usage_numbers_with_test(&used2, NULL, &capacity2);
    ASSERT_EQ(used1, used2);
    ASSERT_EQ(capacity2, capacity2);

    MetaWord* p2 = NULL;
    helper.allocate_from_arena_with_tests_expect_success(&p2, s);

    size_t used3 = 0, capacity3 = 0;
    helper.usage_numbers_with_test(&used3, NULL, &capacity3);
    ASSERT_EQ(used3, used2);
    ASSERT_EQ(capacity3, capacity2);

    // Actually, we should get the very same allocation back
    ASSERT_EQ(p1, p2);
  }
}

static void test_recover_from_commit_limit_hit() {

  if (Settings::new_chunks_are_fully_committed()) {
    return; // This would throw off the commit counting in this test.
  }

  // Test:
  // - Multiple MetaspaceArena allocate (operating under the same commit limiter).
  // - One, while attempting to commit parts of its current chunk on demand,
  //   triggers the limit and cannot commit its chunk further.
  // - We release the other MetaspaceArena - its content is put back to the
  //   freelists.
  // - We re-attempt allocation from the first manager. It should now succeed.
  //
  // This means if the first MetaspaceArena may have to let go of its current chunk and
  // retire it and take a fresh chunk from the freelist.

  const size_t commit_limit = Settings::commit_granule_words() * 10;
  MetaspaceTestContext msthelper(commit_limit);

  // The first MetaspaceArena mimicks a micro loader. This will fill the free
  //  chunk list with very small chunks. We allocate from them in an interleaved
  //  way to cause fragmentation.
  MetaspaceArenaTestHelper helper1(msthelper, Metaspace::ReflectionMetaspaceType, false);
  MetaspaceArenaTestHelper helper2(msthelper, Metaspace::ReflectionMetaspaceType, false);

  // This MetaspaceArena should hit the limit. We use BootMetaspaceType here since
  // it gets a large initial chunk which is committed
  // on demand and we are likely to hit a commit limit while trying to expand it.
  MetaspaceArenaTestHelper helper3(msthelper, Metaspace::BootMetaspaceType, false);

  // Allocate space until we have below two but above one granule left
  size_t allocated_from_1_and_2 = 0;
  while (msthelper.commit_limiter().possible_expansion_words() >= Settings::commit_granule_words() * 2 &&
      allocated_from_1_and_2 < commit_limit) {
    helper1.allocate_from_arena_with_tests_expect_success(1);
    helper2.allocate_from_arena_with_tests_expect_success(1);
    allocated_from_1_and_2 += 2;
  }

  // Now, allocating from helper3, creep up on the limit
  size_t allocated_from_3 = 0;
  MetaWord* p = NULL;
  while ( (helper3.allocate_from_arena_with_tests(&p, 1), p != NULL) &&
         ++allocated_from_3 < Settings::commit_granule_words() * 2);

  EXPECT_LE(allocated_from_3, Settings::commit_granule_words() * 2);

  // We expect the freelist to be empty of committed space...
  EXPECT_0(msthelper.cm().total_committed_word_size());

  //msthelper.cm().print_on(tty);

  // Release the first MetaspaceArena.
  helper1.delete_arena_with_tests();

  //msthelper.cm().print_on(tty);

  // Should have populated the freelist with committed space
  // We expect the freelist to be empty of committed space...
  EXPECT_GT(msthelper.cm().total_committed_word_size(), (size_t)0);

  // Repeat allocation from helper3, should now work.
  helper3.allocate_from_arena_with_tests_expect_success(1);

}


TEST_VM(metaspace, MetaspaceArena_recover_from_limit_hit) {
  test_recover_from_commit_limit_hit();
}

static void test_controlled_growth(Metaspace::MetaspaceType type, bool is_class,
                                   size_t expected_starting_capacity,
                                   bool test_in_place_enlargement)
{

  if (Settings::use_allocation_guard()) {
    return;
  }

  // From a MetaspaceArena in a clean room allocate tiny amounts;
  // watch it grow. Used/committed/capacity should not grow in
  // large jumps. Also, different types of MetaspaceArena should
  // have different initial capacities.

  MetaspaceTestContext msthelper;
  MetaspaceArenaTestHelper smhelper(msthelper, type, is_class, "Grower");

  MetaspaceArenaTestHelper smhelper_harrasser(msthelper, Metaspace::ReflectionMetaspaceType, true, "Harasser");

  size_t used = 0, committed = 0, capacity = 0;
  const size_t alloc_words = 16;

  smhelper.arena()->usage_numbers(&used, &committed, &capacity);
  ASSERT_0(used);
  ASSERT_0(committed);
  ASSERT_0(capacity);

  ///// First allocation //

  smhelper.allocate_from_arena_with_tests_expect_success(alloc_words);

  smhelper.arena()->usage_numbers(&used, &committed, &capacity);

  ASSERT_EQ(used, alloc_words);
  ASSERT_GE(committed, used);
  ASSERT_GE(capacity, committed);

  ASSERT_EQ(capacity, expected_starting_capacity);

  if (!(Settings::new_chunks_are_fully_committed() && type == Metaspace::BootMetaspaceType)) {
    // Initial commit charge for the whole context should be one granule
    ASSERT_EQ(msthelper.committed_words(), Settings::commit_granule_words());
    // Initial commit number for the arena should be less since - apart from boot loader - no
    //  space type has large initial chunks.
    ASSERT_LE(committed, Settings::commit_granule_words());
  }

  ///// subsequent allocations //

  DEBUG_ONLY(const uintx num_chunk_enlarged = metaspace::InternalStats::num_chunks_enlarged();)

  size_t words_allocated = 0;
  int num_allocated = 0;
  const size_t safety = MAX_CHUNK_WORD_SIZE * 1.2;
  size_t highest_capacity_jump = capacity;
  int num_capacity_jumps = 0;

  while (words_allocated < safety && num_capacity_jumps < 15) {

    // if we want to test growth with in-place chunk enlargement, leave MetaspaceArena
    // undisturbed; it will have all the place to grow. Otherwise allocate from a little
    // side arena to increase fragmentation.
    // (Note that this does not completely prevent in-place chunk enlargement but makes it
    //  rather improbable)
    if (!test_in_place_enlargement) {
      smhelper_harrasser.allocate_from_arena_with_tests_expect_success(alloc_words * 2);
    }

    smhelper.allocate_from_arena_with_tests_expect_success(alloc_words);
    words_allocated += alloc_words;
    num_allocated ++;

    size_t used2 = 0, committed2 = 0, capacity2 = 0;

    smhelper.arena()->usage_numbers(&used2, &committed2, &capacity2);

    // used should not grow larger than what we allocated, plus possible overhead.
    ASSERT_GE(used2, used);
    ASSERT_LE(used2, used + alloc_words * 2);
    ASSERT_LE(used2, words_allocated + 100);
    used = used2;

    // A jump in committed words should not be larger than commit granule size.
    // It can be smaller, since the current chunk of the MetaspaceArena may be
    // smaller than a commit granule.
    // (Note: unless root chunks are born fully committed)
    ASSERT_GE(committed2, used2);
    ASSERT_GE(committed2, committed);
    const size_t committed_jump = committed2 - committed;
    if (committed_jump > 0 && !Settings::new_chunks_are_fully_committed()) {
      ASSERT_LE(committed_jump, Settings::commit_granule_words());
    }
    committed = committed2;

    // Capacity jumps: Test that arenas capacity does not grow too fast.
    ASSERT_GE(capacity2, committed2);
    ASSERT_GE(capacity2, capacity);
    const size_t capacity_jump = capacity2 - capacity;
    if (capacity_jump > 0) {
      LOG(">" SIZE_FORMAT "->" SIZE_FORMAT "(+" SIZE_FORMAT ")", capacity, capacity2, capacity_jump)
      if (capacity_jump > highest_capacity_jump) {
        /* Disabled for now since this is rather shaky. The way it is tested makes it too dependent
         * on allocation history. Need to rethink this.
        ASSERT_LE(capacity_jump, highest_capacity_jump * 2);
        ASSERT_GE(capacity_jump, MIN_CHUNK_WORD_SIZE);
        ASSERT_LE(capacity_jump, MAX_CHUNK_WORD_SIZE);
        */
        highest_capacity_jump = capacity_jump;
      }
      num_capacity_jumps ++;
    }

    capacity = capacity2;

  }

  // After all this work, we should see an increase in number of chunk-in-place-enlargements
  //  (this especially is vulnerable to regression: the decisions of when to do in-place-enlargements are somewhat
  //   complicated, see MetaspaceArena::attempt_enlarge_current_chunk())
#ifdef ASSERT
  if (test_in_place_enlargement) {
    const uintx num_chunk_enlarged_2 = metaspace::InternalStats::num_chunks_enlarged();
    ASSERT_GT(num_chunk_enlarged_2, num_chunk_enlarged);
  }
#endif
}

// these numbers have to be in sync with arena policy numbers (see memory/metaspace/arenaGrowthPolicy.cpp)
TEST_VM(metaspace, MetaspaceArena_growth_refl_c_inplace) {
  test_controlled_growth(Metaspace::ReflectionMetaspaceType, true,
                         word_size_for_level(CHUNK_LEVEL_1K), true);
}

TEST_VM(metaspace, MetaspaceArena_growth_refl_c_not_inplace) {
  test_controlled_growth(Metaspace::ReflectionMetaspaceType, true,
                         word_size_for_level(CHUNK_LEVEL_1K), false);
}

TEST_VM(metaspace, MetaspaceArena_growth_anon_c_inplace) {
  test_controlled_growth(Metaspace::ClassMirrorHolderMetaspaceType, true,
                         word_size_for_level(CHUNK_LEVEL_1K), true);
}

TEST_VM(metaspace, MetaspaceArena_growth_anon_c_not_inplace) {
  test_controlled_growth(Metaspace::ClassMirrorHolderMetaspaceType, true,
                         word_size_for_level(CHUNK_LEVEL_1K), false);
}

TEST_VM(metaspace, MetaspaceArena_growth_standard_c_inplace) {
  test_controlled_growth(Metaspace::StandardMetaspaceType, true,
                         word_size_for_level(CHUNK_LEVEL_2K), true);
}

TEST_VM(metaspace, MetaspaceArena_growth_standard_c_not_inplace) {
  test_controlled_growth(Metaspace::StandardMetaspaceType, true,
                         word_size_for_level(CHUNK_LEVEL_2K), false);
}

/* Disabled growth tests for BootMetaspaceType: there, the growth steps are too rare,
 * and too large, to make any reliable guess as toward chunks get enlarged in place.
TEST_VM(metaspace, MetaspaceArena_growth_boot_c_inplace) {
  test_controlled_growth(Metaspace::BootMetaspaceType, true,
                         word_size_for_level(CHUNK_LEVEL_1M), true);
}

TEST_VM(metaspace, MetaspaceArena_growth_boot_c_not_inplace) {
  test_controlled_growth(Metaspace::BootMetaspaceType, true,
                         word_size_for_level(CHUNK_LEVEL_1M), false);
}
*/

TEST_VM(metaspace, MetaspaceArena_growth_refl_nc_inplace) {
  test_controlled_growth(Metaspace::ReflectionMetaspaceType, false,
                         word_size_for_level(CHUNK_LEVEL_2K), true);
}

TEST_VM(metaspace, MetaspaceArena_growth_refl_nc_not_inplace) {
  test_controlled_growth(Metaspace::ReflectionMetaspaceType, false,
                         word_size_for_level(CHUNK_LEVEL_2K), false);
}

TEST_VM(metaspace, MetaspaceArena_growth_anon_nc_inplace) {
  test_controlled_growth(Metaspace::ClassMirrorHolderMetaspaceType, false,
                         word_size_for_level(CHUNK_LEVEL_1K), true);
}

TEST_VM(metaspace, MetaspaceArena_growth_anon_nc_not_inplace) {
  test_controlled_growth(Metaspace::ClassMirrorHolderMetaspaceType, false,
                         word_size_for_level(CHUNK_LEVEL_1K), false);
}

TEST_VM(metaspace, MetaspaceArena_growth_standard_nc_inplace) {
  test_controlled_growth(Metaspace::StandardMetaspaceType, false,
                         word_size_for_level(CHUNK_LEVEL_4K), true);
}

TEST_VM(metaspace, MetaspaceArena_growth_standard_nc_not_inplace) {
  test_controlled_growth(Metaspace::StandardMetaspaceType, false,
                         word_size_for_level(CHUNK_LEVEL_4K), false);
}

/* Disabled growth tests for BootMetaspaceType: there, the growth steps are too rare,
 * and too large, to make any reliable guess as toward chunks get enlarged in place.
TEST_VM(metaspace, MetaspaceArena_growth_boot_nc_inplace) {
  test_controlled_growth(Metaspace::BootMetaspaceType, false,
                         word_size_for_level(CHUNK_LEVEL_4M), true);
}

TEST_VM(metaspace, MetaspaceArena_growth_boot_nc_not_inplace) {
  test_controlled_growth(Metaspace::BootMetaspaceType, false,
                         word_size_for_level(CHUNK_LEVEL_4M), false);
}
*/
