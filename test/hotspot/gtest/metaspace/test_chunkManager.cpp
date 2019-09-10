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

class ChunkManagerTest {

  static const int max_num_chunks = 0x100;

  // These counters are updated by the Node.
  CommitLimiter _commit_limiter;
  VirtualSpaceList* _vs_list;

  ChunkManager* _cm;

  Metachunk* _elems[max_num_chunks];
  SizeCounter _word_size_allocated;
  IntCounter _num_allocated;

  // Note: [min_level ... max_level] (inclusive max)
  static chklvl_t get_random_level(chklvl_t min_level, chklvl_t max_level) {
    int range = max_level - min_level + 1; // [ ]
    chklvl_t l = min_level + (os::random() % range);
    return l;
  }

  struct chklvl_range_t { chklvl_t from; chklvl_t to; };

  // Note: [min_level ... max_level] (inclusive max)
  static void get_random_level_range(chklvl_t min_level, chklvl_t max_level, chklvl_range_t* out) {
    chklvl_t l1 = get_random_level(min_level, max_level);
    chklvl_t l2 = get_random_level(min_level, max_level);
    if (l1 > l2) {
      chklvl_t l = l2;
      l1 = l2;
      l2 = l;
    }
    out->from = l1; out->to = l2;
  }

  bool attempt_free_at(size_t index) {

    LOG("attempt_free_at " SIZE_FORMAT ".", index);

    if (_elems[index] == NULL) {
      return false;
    }

    Metachunk* c = _elems[index];
    const size_t chunk_word_size = c->word_size();
    _cm->return_chunk(c);

    _elems[index] = NULL;

    DEBUG_ONLY(_vs_list->verify(true);)
    DEBUG_ONLY(_cm->verify(true);)

    _word_size_allocated.decrement_by(chunk_word_size);
    _num_allocated.decrement();

    return true;
  }

  bool attempt_allocate_at(size_t index, chklvl_t max_level, chklvl_t pref_level, bool fully_commit = false) {

    LOG("attempt_allocate_at " SIZE_FORMAT ". (" CHKLVL_FORMAT "-" CHKLVL_FORMAT ")", index, max_level, pref_level);

    if (_elems[index] != NULL) {
      return false;
    }

    Metachunk* c = _cm->get_chunk(max_level, pref_level);

    EXPECT_NOT_NULL(c);
    EXPECT_TRUE(c->is_in_use());
    EXPECT_LE(c->level(), max_level);
    EXPECT_GE(c->level(), pref_level);

    _elems[index] = c;

    DEBUG_ONLY(c->verify(true);)
    DEBUG_ONLY(_vs_list->verify(true);)
    DEBUG_ONLY(_cm->verify(true);)

    _word_size_allocated.increment_by(c->word_size());
    _num_allocated.increment();

    if (fully_commit) {
      c->ensure_fully_committed();
    }

    return true;
  }

  // Note: [min_level ... max_level] (inclusive max)
  void allocate_n_random_chunks(int n, chklvl_t min_level, chklvl_t max_level) {

    assert(n <= max_num_chunks, "Sanity");

    for (int i = 0; i < n; i ++) {
      chklvl_range_t r;
      get_random_level_range(min_level, max_level, &r);
      attempt_allocate_at(i, r.to, r.from);
    }

  }

  void free_all_chunks() {
    for (int i = 0; i < max_num_chunks; i ++) {
      attempt_free_at(i);
    }
    assert(_num_allocated.get() == 0, "Sanity");
    assert(_word_size_allocated.get() == 0, "Sanity");
  }

  void random_alloc_free(int iterations, chklvl_t min_level, chklvl_t max_level) {
    for (int i = 0; i < iterations; i ++) {
      int index = os::random() % max_num_chunks;
      if ((os::random() % 100) > 50) {
        attempt_allocate_at(index, max_level, min_level);
      } else {
        attempt_free_at(index);
      }
    }
  }

public:

  ChunkManagerTest()
    : _commit_limiter(50 * M), _vs_list(NULL), _cm(NULL), _word_size_allocated()
  {
    _vs_list = new VirtualSpaceList("test_vs_lust", &_commit_limiter);
    _cm = new ChunkManager("test_cm", _vs_list);
    memset(_elems, 0, sizeof(_elems));
  }

  void test(int iterations, chklvl_t min_level, chklvl_t max_level) {
    for (int run = 0; run < iterations; run ++) {
      allocate_n_random_chunks(max_num_chunks, min_level, max_level);
      random_alloc_free(iterations, min_level, max_level);
      free_all_chunks();
    }
  }

  void test_enlarge_chunk() {
    // On an empty state, request a chunk of the smallest possible size from chunk manager; then,
    // attempt to enlarge it in place. Since all splinters should be free, this should work until
    // we are back at root chunk size.
    assert(_cm->total_num_chunks() == 0, "Call this on an empty cm.");
    Metachunk* c = _cm->get_chunk(HIGHEST_CHUNK_LEVEL, HIGHEST_CHUNK_LEVEL);
    ASSERT_NOT_NULL(c);
    ASSERT_EQ(c->level(), HIGHEST_CHUNK_LEVEL);

    int num_splinter_chunks = _cm->total_num_chunks();

    // Getting a chunk of the smallest size there is should have yielded us one splinter for every level beyond 0.
    ASSERT_EQ(num_splinter_chunks, NUM_CHUNK_LEVELS - 1);

    // Now enlarge n-1 times until c is of root chunk level size again.
    for (chklvl_t l = HIGHEST_CHUNK_LEVEL; l > LOWEST_CHUNK_LEVEL; l --) {
      bool rc = _cm->attempt_enlarge_chunk(c);
      ASSERT_TRUE(rc);
      ASSERT_EQ(c->level(), l - 1);
      num_splinter_chunks --;
      ASSERT_EQ(num_splinter_chunks, _cm->total_num_chunks());
    }
  }

  void test_recommit_chunk() {

    // test that if a chunk is committed again, already committed content stays.
    assert(_cm->total_num_chunks() == 0, "Call this on an empty cm.");
    const chklvl_t lvl = metaspace::chklvl::level_fitting_word_size(Settings::commit_granule_words());
    Metachunk* c = _cm->get_chunk(lvl, lvl);
    ASSERT_NOT_NULL(c);
    ASSERT_EQ(c->level(), lvl);

    // clean slate.
    c->set_free();
    c->uncommit();
    c->set_in_use();

    c->ensure_committed(10);

    const size_t committed_words_1 = c->committed_words();
    fill_range_with_pattern(c->base(), (uintx) this, committed_words_1);

    // enlarge chunk, then recommit again, which will make sure we
    bool rc = _cm->attempt_enlarge_chunk(c);
    ASSERT_TRUE(rc);
    rc = _cm->attempt_enlarge_chunk(c);
    ASSERT_TRUE(rc);
    rc = _cm->attempt_enlarge_chunk(c);
    ASSERT_TRUE(rc);
    ASSERT_EQ(c->level(), lvl - 3);

    c->ensure_committed(c->word_size());
    check_range_for_pattern(c->base(), (uintx) this, committed_words_1);

  }

  void test_wholesale_reclaim() {

    // test that if a chunk is committed again, already committed content stays.
    assert(_num_allocated.get() == 0, "Call this on an empty cm.");

    // Get a number of random sized but large chunks, be sure to cover multiple vsnodes.
    // Also, commit those chunks.
    const size_t min_words_to_allocate = 4 * Settings::virtual_space_node_default_word_size(); // about 16 m

    while (_num_allocated.get() < max_num_chunks &&
           _word_size_allocated.get() < min_words_to_allocate) {
      const chklvl_t lvl = LOWEST_CHUNK_LEVEL + os::random() % 4;
      attempt_allocate_at(_num_allocated.get(), lvl, lvl, true); // < fully committed please
    }

//_cm->print_on(tty);
//_vs_list->print_on(tty);
    DEBUG_ONLY(_cm->verify(true);)
    DEBUG_ONLY(_vs_list->verify(true);)

    // Return about three quarter of the chunks.
    for (int i = 0; i < max_num_chunks; i ++) {
      if (os::random() % 100 < 75) {
        attempt_free_at(i);
      }
    }

    // Now do a reclaim.
    _cm->wholesale_reclaim();

//_cm->print_on(tty);
//_vs_list->print_on(tty);
    DEBUG_ONLY(_cm->verify(true);)
    DEBUG_ONLY(_vs_list->verify(true);)

    // Return all chunks
    free_all_chunks();

    // Now do a second reclaim.
    _cm->wholesale_reclaim();

//_cm->print_on(tty);
//_vs_list->print_on(tty);
    DEBUG_ONLY(_cm->verify(true);)
    DEBUG_ONLY(_vs_list->verify(true);)

    // All space should be gone now, if the settings are not preventing reclaim
    if (Settings::delete_nodes_on_purge()) {
      ASSERT_EQ(_vs_list->reserved_words(), (size_t)0);
    }
    if (Settings::uncommit_on_purge() || Settings::delete_nodes_on_purge()) {
      ASSERT_EQ(_vs_list->committed_words(), (size_t)0);
    }

  }

};

// Note: we unfortunately need TEST_VM even though the system tested
// should be pretty independent since we need things like os::vm_page_size()
// which in turn need OS layer initialization.
TEST_VM(metaspace, chunkmanager_test_whole_range) {
  ChunkManagerTest ct;
  ct.test(100, LOWEST_CHUNK_LEVEL, HIGHEST_CHUNK_LEVEL);
}

TEST_VM(metaspace, chunkmanager_test_small_chunks) {
  ChunkManagerTest ct;
  ct.test(100, HIGHEST_CHUNK_LEVEL / 2, HIGHEST_CHUNK_LEVEL);
}

TEST_VM(metaspace, chunkmanager_test_large_chunks) {
  ChunkManagerTest ct;
  ct.test(100, LOWEST_CHUNK_LEVEL, HIGHEST_CHUNK_LEVEL / 2);
}

TEST_VM(metaspace, chunkmanager_test_enlarge_chunk) {
  ChunkManagerTest ct;
  ct.test_enlarge_chunk();
}

TEST_VM(metaspace, chunkmanager_test_recommit_chunk) {
  ChunkManagerTest ct;
  ct.test_recommit_chunk();
}

TEST_VM(metaspace, chunkmanager_test_wholesale_reclaim) {
  ChunkManagerTest ct;
  ct.test_wholesale_reclaim();
}

