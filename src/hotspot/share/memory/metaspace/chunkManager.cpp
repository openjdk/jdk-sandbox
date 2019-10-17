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
 *
 */
#include <memory/metaspace/settings.hpp>
#include "precompiled.hpp"


#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/metaspace/chunkAllocSequence.hpp"
#include "memory/metaspace/chunkLevel.hpp"
#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/internStat.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/metaDebug.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/metaspaceStatistics.hpp"
#include "memory/metaspace/virtualSpaceNode.hpp"
#include "memory/metaspace/virtualSpaceList.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

namespace metaspace {


// Return a single chunk to the freelist and adjust accounting. No merge is attempted.
void ChunkManager::return_chunk_simple(Metachunk* c) {

  assert_lock_strong(MetaspaceExpand_lock);

  DEBUG_ONLY(c->verify(false));

  const chklvl_t lvl = c->level();
  _chunks.add(c);
  c->reset_used_words();

  // Tracing
  log_debug(metaspace)("ChunkManager %s: returned chunk " METACHUNK_FORMAT ".",
                       _name, METACHUNK_FORMAT_ARGS(c));

}

// Take a single chunk from the given freelist and adjust counters. Returns NULL
// if there is no fitting chunk for this level.
Metachunk* ChunkManager::remove_first_chunk_at_level(chklvl_t l) {

  assert_lock_strong(MetaspaceExpand_lock);
  DEBUG_ONLY(chklvl::check_valid_level(l);)

  Metachunk* c = _chunks.remove_first(l);

  // Tracing
  if (c != NULL) {
    log_debug(metaspace)("ChunkManager %s: removed chunk " METACHUNK_FORMAT ".",
                         _name, METACHUNK_FORMAT_ARGS(c));
  } else {
    log_trace(metaspace)("ChunkManager %s: no chunk found for level " CHKLVL_FORMAT,
                         _name, l);
  }

  return c;
}

// Creates a chunk manager with a given name (which is for debug purposes only)
// and an associated space list which will be used to request new chunks from
// (see get_chunk())
ChunkManager::ChunkManager(const char* name, VirtualSpaceList* space_list)
  : _vslist(space_list),
    _name(name),
    _chunks()
{
}

// Given a chunk we are about to handout to the caller, make sure it is committed
// according to constants::committed_words_on_fresh_chunks
bool ChunkManager::commit_chunk_before_handout(Metachunk* c) {
  assert_lock_strong(MetaspaceExpand_lock);
  const size_t must_be_committed = MIN2(c->word_size(), Settings::committed_words_on_fresh_chunks());
  return c->ensure_committed_locked(must_be_committed);
}

// Given a chunk which must be outside of a freelist and must be free, split it to
// meet a target level and return it. Splinters are added to the freelist.
Metachunk* ChunkManager::split_chunk_and_add_splinters(Metachunk* c, chklvl_t target_level) {

  assert_lock_strong(MetaspaceExpand_lock);

  assert(c->is_free() && c->level() < target_level, "Invalid chunk for splitting");
  DEBUG_ONLY(chklvl::check_valid_level(target_level);)

  DEBUG_ONLY(c->verify(true);)

  // Chunk must be outside of our freelists
  assert(_chunks.contains(c) == false, "Chunk is in freelist.");

  log_debug(metaspace)("ChunkManager %s: will split chunk " METACHUNK_FORMAT " to " CHKLVL_FORMAT ".",
                       _name, METACHUNK_FORMAT_ARGS(c), target_level);

  const chklvl_t orig_level = c->level();
  c = c->vsnode()->split(target_level, c, &_chunks);

  // Splitting should never fail.
  assert(c != NULL, "Split failed");
  assert(c->level() == target_level, "Sanity");

  DEBUG_ONLY(c->verify(false));

  DEBUG_ONLY(verify_locked(true);)

  SOMETIMES(c->vsnode()->verify(true);)

  return c;
}

// Get a chunk and be smart about it.
// - 1) Attempt to find a free chunk of exactly the pref_level level
// - 2) Failing that, attempt to find a chunk smaller or equal the max level.
// - 3) Failing that, attempt to find a free chunk of larger size and split it.
// - 4) Failing that, attempt to allocate a new chunk from the connected virtual space.
// - Failing that, give up and return NULL.
// Note: this is not guaranteed to return a *committed* chunk. The chunk manager will
//   attempt to commit the returned chunk according to constants::committed_words_on_fresh_chunks;
//   but this may fail if we hit a commit limit. In that case, a partly uncommit chunk
//   will be returned, and the commit is attempted again when we allocate from the chunk's
//   uncommitted area. See also Metachunk::allocate.
Metachunk* ChunkManager::get_chunk(chklvl_t max_level, chklvl_t pref_level) {

  MutexLocker fcl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);

  DEBUG_ONLY(verify_locked(false);)

  DEBUG_ONLY(chklvl::check_valid_level(max_level);)
  DEBUG_ONLY(chklvl::check_valid_level(pref_level);)
  assert(max_level >= pref_level, "invalid level.");

  Metachunk* c = NULL;

  // Tracing
  log_debug(metaspace)("ChunkManager %s: get chunk: max " CHKLVL_FORMAT " (" SIZE_FORMAT "),"
                       "preferred " CHKLVL_FORMAT " (" SIZE_FORMAT ").",
                       _name, max_level, chklvl::word_size_for_level(max_level),
                       pref_level, chklvl::word_size_for_level(pref_level));

  // 1) Attempt to find a free chunk of exactly the pref_level level
  c = remove_first_chunk_at_level(pref_level);

  // Todo:
  //
  // We need to meditate about steps (2) and (3) a bit more.
  // By simply preferring to reuse small chunks vs splitting larger chunks we may emphasize
  // fragmentation, strangely enough, if callers wanting medium sized chunks take small chunks
  // instead, and taking them away from the many users which prefer small chunks.
  // Alternatives:
  // - alternating between (2) (3) and (3) (2)
  // - mixing (2) and (3) into one loop with a growing delta, and maybe a "hesitance" barrier function
  // - keeping track of chunk demand and adding this into the equation: if e.g. 4K chunks were heavily
  //   preferred in the past, maybe for callers wanting larger chunks leave those alone.
  // - Take into account which chunks are committed? This would require some extra bookkeeping...

  // 2) Failing that, attempt to find a chunk smaller or equal the minimal level.
  if (c == NULL) {
    for (chklvl_t lvl = pref_level + 1; lvl <= max_level; lvl ++) {
      c = remove_first_chunk_at_level(lvl);
      if (c != NULL) {
        break;
      }
    }
  }

  // 3) Failing that, attempt to find a free chunk of larger size and split it.
  if (c == NULL) {
    for (chklvl_t lvl = pref_level - 1; lvl >= chklvl::ROOT_CHUNK_LEVEL; lvl --) {
      c = remove_first_chunk_at_level(lvl);
      if (c != NULL) {
        // Split chunk; add splinters to freelist
        c = split_chunk_and_add_splinters(c, pref_level);
        break;
      }
    }
  }

  // 4) Failing that, attempt to allocate a new chunk from the connected virtual space.
  if (c == NULL) {

    // Tracing
    log_debug(metaspace)("ChunkManager %s: need new root chunk.", _name);

    c = _vslist->allocate_root_chunk();

    // This may have failed if the virtual space list is exhausted but it cannot be expanded
    // by a new node (class space).
    if (c == NULL) {
      return NULL;
    }

    assert(c->level() == chklvl::LOWEST_CHUNK_LEVEL, "Not a root chunk?");

    // Split this root chunk to the desired chunk size.
    if (pref_level > c->level()) {
      c = split_chunk_and_add_splinters(c, pref_level);
    }
  }

  // Note that we should at this point have a chunk; should always work. If we hit
  // a commit limit in the meantime, the chunk may still be uncommitted, but the chunk
  // itself should exist now.
  assert(c != NULL, "Unexpected");

  // Before returning the chunk, attempt to commit it according to the handout rules.
  // If that fails, we ignore the error and return the uncommitted chunk.
  if (commit_chunk_before_handout(c) == false) {
    log_info(gc, metaspace)("Failed to commit chunk prior to handout.");
  }

  // Any chunk returned from ChunkManager shall be marked as in use.
  c->set_in_use();

  DEBUG_ONLY(verify_locked(false);)
  SOMETIMES(c->vsnode()->verify(true);)

  log_debug(metaspace)("ChunkManager %s: handing out chunk " METACHUNK_FORMAT ".",
                       _name, METACHUNK_FORMAT_ARGS(c));


  DEBUG_ONLY(InternalStats::inc_num_chunks_taken_from_freelist();)

  return c;

} // ChunkManager::get_chunk


// Return a single chunk to the ChunkManager and adjust accounting. May merge chunk
//  with neighbors.
// As a side effect this removes the chunk from whatever list it has been in previously.
// Happens after a Classloader was unloaded and releases its metaspace chunks.
// !! Note: this may invalidate the chunk. Do not access the chunk after
//    this function returns !!
void ChunkManager::return_chunk(Metachunk* c) {

  MutexLocker fcl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);

  log_debug(metaspace)("ChunkManager %s: returning chunk " METACHUNK_FORMAT ".",
                       _name, METACHUNK_FORMAT_ARGS(c));

  DEBUG_ONLY(c->verify(true);)

  assert(!_chunks.contains(c), "A chunk to be added to the freelist must not be in the freelist already.");

  assert(c->is_in_use(), "Unexpected chunk state");
  assert(!c->in_list(), "Remove from list first");
  c->set_free();
  c->reset_used_words();

  const chklvl_t orig_lvl = c->level();

  Metachunk* merged = NULL;
  if (!c->is_root_chunk()) {
    // Only attempt merging if we are not of the lowest level already.
    merged = c->vsnode()->merge(c, &_chunks);
  }

  if (merged != NULL) {

    DEBUG_ONLY(merged->verify(false));

    // We did merge our chunk into a different chunk.

    // We did merge chunks and now have a bigger chunk.
    assert(merged->level() < orig_lvl, "Sanity");

    log_trace(metaspace)("ChunkManager %s: merged into chunk " METACHUNK_FORMAT ".",
                         _name, METACHUNK_FORMAT_ARGS(merged));

    c = merged;

  }

  if (Settings::uncommit_on_return() &&
      Settings::uncommit_on_return_min_word_size() <= c->word_size())
  {
    log_trace(metaspace)("ChunkManager %s: uncommitting free chunk " METACHUNK_FORMAT ".",
                         _name, METACHUNK_FORMAT_ARGS(c));
    c->uncommit_locked();
  }

  return_chunk_simple(c);

  DEBUG_ONLY(verify_locked(false);)
  SOMETIMES(c->vsnode()->verify(true);)

  DEBUG_ONLY(InternalStats::inc_num_chunks_returned_to_freelist();)

}

// Given a chunk c, which must be "in use" and must not be a root chunk, attempt to
// enlarge it in place by claiming its trailing buddy.
//
// This will only work if c is the leader of the buddy pair and the trailing buddy is free.
//
// If successful, the follower chunk will be removed from the freelists, the leader chunk c will
// double in size (level decreased by one).
//
// On success, true is returned, false otherwise.
bool ChunkManager::attempt_enlarge_chunk(Metachunk* c) {
  MutexLocker fcl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
  return c->vsnode()->attempt_enlarge_chunk(c, &_chunks);
}

static void print_word_size_delta(outputStream* st, size_t word_size_1, size_t word_size_2) {
  if (word_size_1 == word_size_2) {
    print_scaled_words(st, word_size_1);
    st->print (" (no change)");
  } else {
    print_scaled_words(st, word_size_1);
    st->print("->");
    print_scaled_words(st, word_size_2);
    st->print(" (");
    if (word_size_2 <= word_size_1) {
      st->print("-");
      print_scaled_words(st, word_size_1 - word_size_2);
    } else {
      st->print("+");
      print_scaled_words(st, word_size_2 - word_size_1);
    }
    st->print(")");
  }
}

// Attempt to reclaim free areas in metaspace wholesale:
// - first, attempt to purge nodes of the backing virtual space. This can only be successful
//   if whole nodes are only containing free chunks, so it highly depends on fragmentation.
// - then, it will uncommit areas of free chunks according to the rules laid down in
//   settings (see settings.hpp).
void ChunkManager::wholesale_reclaim() {

  MutexLocker fcl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);

  log_info(metaspace)("ChunkManager \"%s\": reclaiming memory...", _name);

  const size_t reserved_before = _vslist->reserved_words();
  const size_t committed_before = _vslist->committed_words();
  int num_nodes_purged = 0;

  if (Settings::delete_nodes_on_purge()) {
    num_nodes_purged = _vslist->purge(&_chunks);
    DEBUG_ONLY(InternalStats::inc_num_purges();)
  }

  if (Settings::uncommit_on_purge()) {
    const chklvl_t max_level =
        chklvl::level_fitting_word_size(Settings::uncommit_on_purge_min_word_size());
    for (chklvl_t l = chklvl::LOWEST_CHUNK_LEVEL;
         l <= max_level;
         l ++)
    {
      for (Metachunk* c = _chunks.first_at_level(l); c != NULL; c = c->next()) {
        c->uncommit_locked();
      }
    }
    DEBUG_ONLY(InternalStats::inc_num_wholesale_uncommits();)
  }

  const size_t reserved_after = _vslist->reserved_words();
  const size_t committed_after = _vslist->committed_words();

  // Print a nice report.
  if (reserved_after == reserved_before && committed_after == committed_before) {
    log_info(metaspace)("ChunkManager %s: ... nothing reclaimed.", _name);
  } else {
    LogTarget(Info, metaspace) lt;
    if (lt.is_enabled()) {
      LogStream ls(lt);
      ls.print_cr("ChunkManager %s: finished reclaiming memory: ", _name);

      ls.print("reserved: ");
      print_word_size_delta(&ls, reserved_before, reserved_after);
      ls.cr();

      ls.print("committed: ");
      print_word_size_delta(&ls, committed_before, committed_after);
      ls.cr();

      ls.print_cr("full nodes purged: %d", num_nodes_purged);
    }
  }

  DEBUG_ONLY(_vslist->verify_locked(true));
  DEBUG_ONLY(verify_locked(true));

}


ChunkManager* ChunkManager::_chunkmanager_class = NULL;
ChunkManager* ChunkManager::_chunkmanager_nonclass = NULL;

void ChunkManager::set_chunkmanager_class(ChunkManager* cm) {
  assert(_chunkmanager_class == NULL, "Sanity");
  _chunkmanager_class = cm;
}

void ChunkManager::set_chunkmanager_nonclass(ChunkManager* cm) {
  assert(_chunkmanager_nonclass == NULL, "Sanity");
  _chunkmanager_nonclass = cm;
}



// Update statistics.
void ChunkManager::add_to_statistics(cm_stats_t* out) const {

  MutexLocker fcl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);

  for (chklvl_t l = chklvl::ROOT_CHUNK_LEVEL; l <= chklvl::HIGHEST_CHUNK_LEVEL; l ++) {
    out->num_chunks[l] += _chunks.num_chunks_at_level(l);
    out->committed_word_size[l] += _chunks.committed_word_size_at_level(l);
  }

  DEBUG_ONLY(out->verify();)

}

#ifdef ASSERT

void ChunkManager::verify(bool slow) const {
  MutexLocker fcl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
  verify_locked(slow);
}

void ChunkManager::verify_locked(bool slow) const {

  assert_lock_strong(MetaspaceExpand_lock);

  assert(_vslist != NULL, "No vslist");

  // This checks that the lists are wired up correctly, that the counters are valid and
  // that each chunk is (only) in its correct list.
  _chunks.verify(true);

  // Need to check that each chunk is free..._size = 0;
  for (chklvl_t l = chklvl::LOWEST_CHUNK_LEVEL; l <= chklvl::HIGHEST_CHUNK_LEVEL; l ++) {
    for (const Metachunk* c = _chunks.first_at_level(l); c != NULL; c = c->next()) {
      assert(c->is_free(), "Chunk is not free.");
      assert(c->used_words() == 0, "Chunk should have not used words.");
    }
  }

}

#endif // ASSERT

void ChunkManager::print_on(outputStream* st) const {
  MutexLocker fcl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
  print_on_locked(st);
}

void ChunkManager::print_on_locked(outputStream* st) const {
  assert_lock_strong(MetaspaceExpand_lock);
  st->print_cr("cm %s: %d chunks, total word size: " SIZE_FORMAT ", committed word size: " SIZE_FORMAT, _name,
               total_num_chunks(), total_word_size(), _chunks.total_committed_word_size());
  _chunks.print_on(st);
}

} // namespace metaspace



