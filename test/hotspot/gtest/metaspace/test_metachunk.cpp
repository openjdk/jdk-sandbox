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
#include "metaspace/metaspaceTestsCommon.hpp"
#include "runtime/mutexLocker.hpp"


class MetachunkTest {

  CommitLimiter _commit_limiter;
  VirtualSpaceList _vs_list;
  ChunkManager _cm;

  Metachunk* alloc_chunk(chklvl_t lvl) {

    Metachunk* c = _cm.get_chunk(lvl, lvl);
    EXPECT_NOT_NULL(c);
    EXPECT_EQ(c->level(), lvl);
    check_chunk(c);

    DEBUG_ONLY(c->verify(true);)

    return c;
  }

  void check_chunk(const Metachunk* c) const {
    EXPECT_LE(c->used_words(), c->committed_words());
    EXPECT_LE(c->committed_words(), c->word_size());
    EXPECT_NOT_NULL(c->base());
    EXPECT_TRUE(_vs_list.contains(c->base()));
    EXPECT_TRUE(is_aligned(c->base(), MAX_CHUNK_BYTE_SIZE));
    EXPECT_TRUE(is_aligned(c->word_size(), MAX_CHUNK_WORD_SIZE));
    EXPECT_TRUE(metaspace::chklvl::is_valid_level(c->level()));

    if (c->next() != NULL) EXPECT_EQ(c->next()->prev(), c);
    if (c->prev() != NULL) EXPECT_EQ(c->prev()->next(), c);

    {
      MutexLocker fcl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
      if (c->next_in_vs() != NULL) EXPECT_EQ(c->next_in_vs()->prev_in_vs(), c);
      if (c->prev_in_vs() != NULL) EXPECT_EQ(c->prev_in_vs()->next_in_vs(), c);
    }

    DEBUG_ONLY(c->verify(true);)
  }

public:

  MetachunkTest(size_t commit_limit)
    : _commit_limiter(commit_limit),
      _vs_list("test_vs_list", &_commit_limiter),
      _cm("test_cm", &_vs_list)
  {
  }

  void test_random_allocs() {

    // Randomly alloc from a chunk until it is full.
    Metachunk* c = alloc_chunk(LOWEST_CHUNK_LEVEL);

    check_chunk(c);

    EXPECT_TRUE(c->is_in_use());
    EXPECT_EQ(c->used_words(), (size_t)0);

    // uncommit to start off with uncommitted chunk; then start allocating.
    c->set_free();
    c->uncommit();
    c->set_in_use();

    EXPECT_EQ(c->committed_words(), (size_t)0);

    RandSizeGenerator rgen(1, 256, 0.1f, 1024, 4096); // note: word sizes
    SizeCounter words_allocated;

    MetaWord* p = NULL;
    bool did_hit_commit_limit = false;
    do {

      const size_t alloc_size = align_up(rgen.get(), Metachunk::allocation_alignment_words);

      // Note: about net and raw sizes: these concepts only exist at the SpaceManager level.
      // At the chunk level (where we test here), we allocate exactly what we ask, in number of words.

      const bool may_hit_commit_limit =
          _commit_limiter.possible_expansion_words() <= align_up(alloc_size, Settings::commit_granule_words());

      p = c->allocate(alloc_size, &did_hit_commit_limit);
      LOG("Allocated " SIZE_FORMAT " words, chunk: " METACHUNK_FULL_FORMAT, alloc_size, METACHUNK_FULL_FORMAT_ARGS(c));

      check_chunk(c);

      if (p != NULL) {
        // From time to time deallocate to test deallocation. Since we do this on the very last allocation,
        // this should always work.
        if (os::random() % 100 > 95) {
          LOG("Test dealloc in place");
          EXPECT_TRUE(c->attempt_rollback_allocation(p, alloc_size));
        } else {
          fill_range_with_pattern(p, (uintx) this, alloc_size); // test that we can access this.
          words_allocated.increment_by(alloc_size);
          EXPECT_EQ(c->used_words(), words_allocated.get());
        }
      } else {
        // Allocating from a chunk can only fail for one of two reasons: Either the chunk is full, or
        // we attempted to increase the chunk's commit region and hit the commit limit.
        if (did_hit_commit_limit) {
          EXPECT_TRUE(may_hit_commit_limit);
        } else {
          // Chunk is full.
          EXPECT_LT(c->free_words(), alloc_size);
        }
      }

    } while(p != NULL);

    check_range_for_pattern(c->base(), (uintx) this, c->used_words());

    // At the end of the test return the chunk to the chunk manager to
    // avoid asserts on destruction time.
    _cm.return_chunk(c);

  }



};



TEST_VM(metaspace, metachunk_test_random_allocs_no_commit_limit) {

  // The test only allocates one root chunk and plays with it, so anything
  // above the size of a root chunk should not hit commit limit.
  MetachunkTest test(2 * MAX_CHUNK_WORD_SIZE);
  test.test_random_allocs();

}

TEST_VM(metaspace, metachunk_test_random_allocs_with_commit_limit) {

  // The test allocates one root chunk and plays with it, so a limit smaller
  // than root chunk size will be hit.
  MetachunkTest test(MAX_CHUNK_WORD_SIZE / 2);
  test.test_random_allocs();

}
