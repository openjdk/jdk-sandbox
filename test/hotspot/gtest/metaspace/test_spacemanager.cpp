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
#include "metaspace/metaspace_sparsearray.hpp"
#include "metaspace/metaspace_testhelper.hpp"
#include "utilities/ostream.hpp"

class SpaceManagerTestHelper {

  MetaspaceTestHelper& _helper;

  Mutex* _lock;
  const ArenaGrowthPolicy* _growth_policy;
  SizeAtomicCounter _used_words_counter;
  SpaceManager* _sm;

public:

  SpaceManagerTestHelper(MetaspaceTestHelper& helper, metaspace::MetaspaceType space_type)
    : _helper(helper),
      _lock(NULL),
      _growth_policy(NULL),
      _used_words_counter(),
      _sm(NULL)
  {
    _growth_policy = ArenaGrowthPolicy::policy_for_space_type(space_type, false);
    _lock = new Mutex(Monitor::native, "gtest-SpaceManagerTest-lock", false, Monitor::_safepoint_check_never);
    // Lock during space creation, since this is what happens in the VM too
    //  (see ClassLoaderData::metaspace_non_null(), which we mimick here).
    {
      MutexLocker ml(_lock,  Mutex::_no_safepoint_check_flag);
      _sm = new SpaceManager(&_helper.cm(), _growth_policy, _lock, &_used_words_counter, "gtest-SpaceManagerTest-sm", false);
    }
    DEBUG_ONLY(_sm->verify(true));
  }

  ~SpaceManagerTestHelper() {
    delete_sm_with_tests();
    delete _lock;
  }

  const CommitLimiter& limiter() const { return _helper.commit_limiter(); }
  SpaceManager* sm() const { return _sm; }
  SizeAtomicCounter& used_words_counter() { return _used_words_counter; }

  void delete_sm_with_tests() {
    if (_sm != NULL) {
      size_t used_words_before = _used_words_counter.get();
      size_t committed_words_before = limiter().committed_words();
      DEBUG_ONLY(_sm->verify(true));
      delete _sm;
      _sm = NULL;
      size_t used_words_after = _used_words_counter.get();
      size_t committed_words_after = limiter().committed_words();
      EXPECT_0(used_words_after);
      if (Settings::uncommit_on_return()) {
        EXPECT_LE(committed_words_after, committed_words_before);
      } else {
        EXPECT_EQ(committed_words_after, committed_words_before);
      }
    }
  }

  bool allocate_from_sm_with_tests(size_t word_size) {
    if (_sm != NULL) {

      size_t used_words_before = _used_words_counter.get();
      size_t committed_words_before = limiter().committed_words();
      size_t possible_expansion = limiter().possible_expansion_words();

      MetaWord* p = _sm->allocate(word_size);

      size_t used_words_after = _used_words_counter.get();
      size_t committed_words_after = limiter().committed_words();

      if (p == NULL) {
        EXPECT_LT(possible_expansion, word_size);
        EXPECT_EQ(used_words_after, used_words_before);
        return false;
      } else {
        EXPECT_TRUE(is_aligned(p, sizeof(MetaWord)));
        EXPECT_GE(used_words_after, used_words_before + word_size); // note: overhead in sm
        EXPECT_GE(committed_words_after, committed_words_before);
        return true;
      }
    }
    return false;
  }

};


static void test_basics(size_t commit_limit, bool is_micro) {
  MetaspaceTestHelper msthelper(commit_limit);
  SpaceManagerTestHelper helper(msthelper, is_micro ? metaspace::ReflectionMetaspaceType : metaspace::StandardMetaspaceType);

  helper.sm()->allocate(1);
  helper.sm()->allocate(128);
  helper.sm()->allocate(128 * K);
  helper.sm()->allocate(1);
  helper.sm()->allocate(128);
  helper.sm()->allocate(128 * K);
}

TEST_VM(metaspace, spacemanager_basics_micro_nolimit) {
  test_basics(max_uintx, true);
}

TEST_VM(metaspace, spacemanager_basics_micro_limit) {
  test_basics(256 * K, true);
}

TEST_VM(metaspace, spacemanager_basics_standard_nolimit) {
  test_basics(max_uintx, false);
}

TEST_VM(metaspace, spacemanager_basics_standard_limit) {
  test_basics(256 * K, false);
}

static void test_recover_from_commit_limit_hit() {
  // Test:
  // - Multiple SpaceManager allocate (operating under the same commit limiter).
  // - One, while attempting to commit parts of its current chunk on demand,
  //   triggers the limit and cannot commit its chunk further.
  // - We release the other SpaceManager - its content is put back to the
  //   freelists.
  // - We re-attempt allocation from the first manager. It should now succeed.
  //
  // This means if the first SpaceManager may have to let go of its current chunk and
  // retire it and take a fresh chunk from the freelist.

  const size_t commit_limit = Settings::commit_granule_words() * 10;
  MetaspaceTestHelper msthelper(commit_limit);

  // The first space managers mimick micro loaders. This will fill the free
  //  chunk list with very small chunks. We allocate from them in an interleaved
  //  way to cause fragmentation.
  SpaceManagerTestHelper helper1(msthelper, metaspace::ReflectionMetaspaceType);
  SpaceManagerTestHelper helper2(msthelper, metaspace::ReflectionMetaspaceType);

  // This SpaceManager should hit the limit. We use BootMetaspaceType here since
  // it gets a large initial chunk which is committed
  // on demand and we are likely to hit a commit limit while trying to expand it.
  SpaceManagerTestHelper helper3(msthelper, metaspace::BootMetaspaceType);

  // Allocate space until we have below two but above one granule left
  while (msthelper.commit_limiter().possible_expansion_words() >= Settings::commit_granule_words() * 2) {
    helper1.allocate_from_sm_with_tests(1);
    helper2.allocate_from_sm_with_tests(1);
  }

  // Now, allocating from helper3, creep up on the limit
  size_t allocated_from_3 = 0;
  while (helper3.allocate_from_sm_with_tests(1) &&
         ++allocated_from_3 < Settings::commit_granule_words() * 2);

  EXPECT_LE(allocated_from_3, Settings::commit_granule_words() * 2);

  // We expect the freelist to be empty of committed space...
  EXPECT_0(msthelper.cm().total_committed_word_size());

  //msthelper.cm().print_on(tty);

  // Release the first SpaceManager.
  helper1.delete_sm_with_tests();

  //msthelper.cm().print_on(tty);

  // Should have populated the freelist with committed space
  // We expect the freelist to be empty of committed space...
  EXPECT_GT(msthelper.cm().total_committed_word_size(), (size_t)0);

  // Repeat allocation from helper3, should now work.
  EXPECT_TRUE(helper3.allocate_from_sm_with_tests(1));

}


TEST_VM(metaspace, spacemanager_recover_from_limit_hit) {
  test_recover_from_commit_limit_hit();
}





