/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, 2019 SAP SE. All rights reserved.
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

#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/internStat.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/metaDebug.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/metaspaceStatistics.hpp"
#include "memory/metaspace/smallBlocks.hpp"
#include "memory/metaspace/spaceManager.hpp"
#include "memory/metaspace/virtualSpaceList.hpp"
#include "runtime/atomic.hpp"
#include "runtime/init.hpp"
#include "services/memoryService.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

namespace metaspace {

// Given a net allocation word size, return the raw word size
// we need to actually allocate in order to:
// 1) be able to deallocate the allocation - deallocated blocks are stored either in SmallBlocks
//    (an array of short lists) or, beyond a certain size, in a dictionary tree.
//    For that to work the allocated block must be at least three words.
// 2) be aligned to sizeof(void*)

// Note: externally visible for gtests.
//static
size_t get_raw_allocation_word_size(size_t net_word_size) {

  size_t byte_size = net_word_size * BytesPerWord;
  byte_size = MAX2(byte_size, (size_t)SmallBlocks::small_block_min_byte_size());
  byte_size = align_up(byte_size, Metachunk::allocation_alignment_bytes);

  size_t word_size = byte_size / BytesPerWord;
  assert(word_size * BytesPerWord == byte_size, "Sanity");

  return word_size;

}

static const size_t highest_possible_delta_between_raw_and_net_size = get_raw_allocation_word_size(1) - 1;

// The inverse function to get_raw_allocation_word_size: Given a raw size, return the max net word size
// fitting into it.
static size_t get_net_allocation_word_size(size_t raw_word_size) {

  size_t byte_size = raw_word_size * BytesPerWord;
  byte_size = align_down(byte_size, Metachunk::allocation_alignment_bytes);
  if (byte_size < SmallBlocks::small_block_min_byte_size()) {
    return 0;
  }
  return byte_size / BytesPerWord;

}

// Given a requested word size, will allocate a chunk large enough to at least fit that
// size, but may be larger according to internal heuristics.
//
// On success, it will replace the current chunk with the newly allocated one, which will
// become the current chunk. The old current chunk should be retired beforehand.
//
// May fail if we could not allocate a new chunk. In that case the current chunk remains
// unchanged and false is returned.
bool SpaceManager::allocate_new_current_chunk(size_t requested_word_size) {

  assert_lock_strong(lock());

  guarantee(requested_word_size <= chklvl::MAX_CHUNK_WORD_SIZE,
            "Requested size too large (" SIZE_FORMAT ") - max allowed size per allocation is " SIZE_FORMAT ".",
            requested_word_size, chklvl::MAX_CHUNK_WORD_SIZE);

  // If we have a current chunk, it should have been retired (almost empty) beforehand.
  // See: retire_current_chunk().
  assert(current_chunk() == NULL || current_chunk()->free_below_committed_words() <= 10, "Must retire chunk beforehand");

  const chklvl_t min_level = chklvl::level_fitting_word_size(requested_word_size);
  chklvl_t pref_level = _chunk_alloc_sequence->get_next_chunk_level(_chunks.size());

  if (pref_level > min_level) {
    pref_level = min_level;
  }

  log_trace(metaspace)("SpaceManager %s: requested word size_ " SIZE_FORMAT ", num chunks so far: %d, preferred level: "
                       CHKLVL_FORMAT ", min level: " CHKLVL_FORMAT ".",
                       _name, requested_word_size, _chunks.size(), pref_level, min_level);

  Metachunk* c = _chunk_manager->get_chunk(min_level, pref_level);
  if (c == NULL) {
    log_debug(metaspace)("SpaceManager %s: failed to allocate new chunk for requested word size " SIZE_FORMAT ".",
                         _name, requested_word_size);
    return false;
  }

  assert(c->is_in_use(), "Wrong chunk state.");
  assert(c->level() <= min_level && c->level() >= pref_level, "Sanity");

  _chunks.add(c);

  log_debug(metaspace)("SpaceManager %s: allocated new chunk " METACHUNK_FORMAT " for requested word size " SIZE_FORMAT ".",
                       _name, METACHUNK_FORMAT_ARGS(c), requested_word_size);

  // Workaround for JDK-8233019: never return space allocated at a 32bit aligned address
  if (Settings::do_not_return_32bit_aligned_addresses() &&
      (((intptr_t)c->base()) & 0xFFFFFFFF) == 0)
  {
    bool ignored;
    c->allocate(1, &ignored);
  }

  return c;

}

void SpaceManager::create_block_freelist() {
  assert(_block_freelist == NULL, "Only call once");
  _block_freelist = new BlockFreelist();
}

void SpaceManager::add_allocation_to_block_freelist(MetaWord* p, size_t word_size) {
  if (_block_freelist == NULL) {
    _block_freelist = new BlockFreelist(); // Create only on demand
  }
  _block_freelist->return_block(p, word_size);
}

SpaceManager::SpaceManager(ChunkManager* chunk_manager,
             const ChunkAllocSequence* alloc_sequence,
             Mutex* lock,
             SizeAtomicCounter* total_used_words_counter,
             const char* name)
: _lock(lock),
  _chunk_manager(chunk_manager),
  _chunk_alloc_sequence(alloc_sequence),
  _chunks(),
  _block_freelist(NULL),
  _total_used_words_counter(total_used_words_counter),
  _name(name)
{
}

SpaceManager::~SpaceManager() {

  MutexLocker fcl(lock(), Mutex::_no_safepoint_check_flag);
  Metachunk* c = _chunks.first();
  Metachunk* c2 = NULL;
  while(c) {
    // c may become invalid. Take care while iterating.
    c2 = c->next();
    _total_used_words_counter->decrement_by(c->used_words());
    _chunks.remove(c);
    _chunk_manager->return_chunk(c);
    c = c2;
  }

  DEBUG_ONLY(chunk_manager()->verify(true);)

  delete _block_freelist;

}

// The remaining committed free space in the current chunk is chopped up and stored in the block
// free list for later use. As a result, the current chunk will remain current but completely
// used up. This is a preparation for calling allocate_new_current_chunk().
void SpaceManager::retire_current_chunk() {
  assert_lock_strong(lock());

  Metachunk* c = current_chunk();
  assert(c != NULL, "Sanity");

  log_debug(metaspace)("SpaceManager %s: retiring chunk " METACHUNK_FULL_FORMAT ".",
                       _name, METACHUNK_FULL_FORMAT_ARGS(c));

  // Side note:
  // In theory it could happen that we are asked to retire a completely empty chunk. This may be the
  // result of rolled back allocations (see deallocate in place) and a lot of luck.
  // But since these cases should be exceedingly rare, we do not handle them special in order to keep
  // the code simple.

  size_t raw_remaining_words = c->free_below_committed_words();
  size_t net_remaining_words = get_net_allocation_word_size(raw_remaining_words);
  if (net_remaining_words > 0) {
    bool did_hit_limit = false;
    MetaWord* ptr = c->allocate(net_remaining_words, &did_hit_limit);
    assert(ptr != NULL && did_hit_limit == false, "Should have worked");
    add_allocation_to_block_freelist(ptr, net_remaining_words);
    _total_used_words_counter->increment_by(net_remaining_words);
  }

  // After this operation: the current chunk should have (almost) no free committed space left.
  assert(current_chunk()->free_below_committed_words() <= highest_possible_delta_between_raw_and_net_size,
         "Chunk retiring did not work (current chunk " METACHUNK_FULL_FORMAT ").",
         METACHUNK_FULL_FORMAT_ARGS(current_chunk()));

  DEBUG_ONLY(verify_locked();)

}

// Allocate memory from Metaspace.
// 1) Attempt to allocate from the dictionary of deallocated blocks.
// 2) Attempt to allocate from the current chunk.
// 3) Attempt to enlarge the current chunk in place if it is too small.
// 4) Attempt to get a new chunk and allocate from that chunk.
// At any point, if we hit a commit limit, we return NULL.
MetaWord* SpaceManager::allocate(size_t requested_word_size) {

  MutexLocker cl(lock(), Mutex::_no_safepoint_check_flag);

  const size_t raw_word_size = get_raw_allocation_word_size(requested_word_size);

  log_trace(metaspace)("SpaceManager %s: requested " SIZE_FORMAT " words, "
                       "raw word size: " SIZE_FORMAT ".",
                       _name, requested_word_size, raw_word_size);

  MetaWord* p = NULL;

  bool did_hit_limit = false;

  // Allocate first chunk if needed.
  if (current_chunk() == NULL) {
    if (allocate_new_current_chunk(raw_word_size) == false) {
      did_hit_limit = true;
    } else {
      assert(current_chunk() != NULL && current_chunk()->free_words() >= raw_word_size, "Sanity");
    }
  }

  // 1) Attempt to allocate from the dictionary of deallocated blocks.

  // Allocation from the dictionary is expensive in the sense that
  // the dictionary has to be searched for a size.  Don't allocate
  // from the dictionary until it starts to get fat.  Is this
  // a reasonable policy?  Maybe an skinny dictionary is fast enough
  // for allocations.  Do some profiling.  JJJ
  if (_block_freelist != NULL && _block_freelist->total_size() > Settings::allocation_from_dictionary_limit()) {
    p = _block_freelist->get_block(raw_word_size);

    if (p != NULL) {
      DEBUG_ONLY(InternalStats::inc_num_allocs_from_deallocated_blocks();)
      log_trace(metaspace)("SpaceManager %s: .. taken from freelist.", _name);
      // Note: space in the freeblock dictionary counts as used (see retire_current_chunk()) -
      // that means that we must not increase the used counter again when allocating from the dictionary.
      // Therefore we return here.
      return p;
    }

  }

  // 2) Failing that, attempt to allocate from the current chunk. If we hit commit limit, return NULL.
  if (p == NULL && !did_hit_limit) {
    p = current_chunk()->allocate(raw_word_size, &did_hit_limit);
    log_trace(metaspace)("SpaceManager %s: .. taken from current chunk.", _name);
  }

  // 3) Failing that because the remaining chunk space is too small for the requested size
  //     (and not because commit limit), attempt to enlarge the chunk in place.
  if (p == NULL && !did_hit_limit) {

    // Since we did not hit the commit limit, the current chunk must have been too small.
    assert(current_chunk()->free_words() < raw_word_size, "Sanity");

    DEBUG_ONLY(InternalStats::inc_num_allocs_failed_chunk_too_small();)

    // Under certain conditions we can just attempt to enlarge the chunk - fusing it with its follower
    // chunk to produce a chunk double the size (level decreased by 1).
    // 0) only if it is not switched off
    // 1) obviously, this only works for non-root chunks
    // 2) ... which are leader of their buddy pair.
    // 3) only if the requested allocation would fit into a thus enlarged chunk
    // 4) do not grow memory faster than what the chunk allocation strategy would allow
    // 5) as a safety feature, only below a certain limit
    if (Settings::enlarge_chunks_in_place() &&              // 0
        current_chunk()->is_root_chunk() == false &&        // 1
        current_chunk()->is_leader() &&                     // 2
        current_chunk()->word_size() + current_chunk()->free_words() >= requested_word_size &&      // 3
        _chunk_alloc_sequence->get_next_chunk_level(_chunks.size()) <= current_chunk()->level() &&  // 4
        current_chunk()->word_size() <= Settings::enlarge_chunks_in_place_max_word_size())          // 5
    {

      if (_chunk_manager->attempt_enlarge_chunk(current_chunk())) {

        // Re-attempt allocation.
        p = current_chunk()->allocate(raw_word_size, &did_hit_limit);

        if (p != NULL) {
          DEBUG_ONLY(InternalStats::inc_num_chunk_enlarged();)
          log_trace(metaspace)("SpaceManager %s: .. taken from current chunk (enlarged chunk).", _name);
        }
      }
    }
  }

  // 4) Failing that, attempt to get a new chunk and allocate from that chunk. Again, we may hit a commit
  //    limit, in which case we return NULL.
  if (p == NULL && !did_hit_limit) {

    // Since we did not hit the commit limit, the current chunk must have been too small.
    assert(current_chunk()->free_words() < raw_word_size, "Sanity");

    // Before we allocate a new chunk we need to retire the old chunk, which is too small to serve our request
    // but may still have free committed words.
    retire_current_chunk();

    DEBUG_ONLY(InternalStats::inc_num_chunks_retired();)

    // Allocate a new chunk.
    if (allocate_new_current_chunk(raw_word_size) == false) {
      did_hit_limit = true;
    } else {
      assert(current_chunk() != NULL && current_chunk()->free_words() >= raw_word_size, "Sanity");
      p = current_chunk()->allocate(raw_word_size, &did_hit_limit);
      log_trace(metaspace)("SpaceManager %s: .. allocated new chunk " CHKLVL_FORMAT " and taken from that.",
                           _name, current_chunk()->level());
    }

  }

  assert(p != NULL || (p == NULL && did_hit_limit), "Sanity");

  SOMETIMES(verify_locked();)

  if (p == NULL) {
    DEBUG_ONLY(InternalStats::inc_num_allocs_failed_limit();)
  } else {
    DEBUG_ONLY(InternalStats::inc_num_allocs();)
    _total_used_words_counter->increment_by(raw_word_size);
  }

  log_trace(metaspace)("SpaceManager %s: returned " PTR_FORMAT ".",
                       _name, p2i(p));

  return p;

}

// Prematurely returns a metaspace allocation to the _block_freelists
// because it is not needed anymore (requires CLD lock to be active).
void SpaceManager::deallocate_locked(MetaWord* p, size_t word_size) {
  assert_lock_strong(lock());

  // Allocations and deallocations are in raw_word_size
  size_t raw_word_size = get_raw_allocation_word_size(word_size);

  log_debug(metaspace)("SpaceManager %s: deallocating " PTR_FORMAT
                       ", word size: " SIZE_FORMAT ", raw size: " SIZE_FORMAT ".",
                       _name, p2i(p), word_size, raw_word_size);

  assert(current_chunk() != NULL, "SpaceManager is empty.");

  assert(is_valid_area(p, word_size),
         "Pointer range not part of this SpaceManager and cannot be deallocated: (" PTR_FORMAT ".." PTR_FORMAT ").",
         p2i(p), p2i(p + word_size));

  // If this allocation has just been allocated from the current chunk, it may still be on the top of the
  // current chunk. In that case, just roll back the allocation.
  if (current_chunk()->attempt_rollback_allocation(p, raw_word_size)) {
    log_trace(metaspace)("SpaceManager %s: ... rollback succeeded.", _name);
    return;
  }

  add_allocation_to_block_freelist(p, raw_word_size);

  DEBUG_ONLY(verify_locked();)

}

// Prematurely returns a metaspace allocation to the _block_freelists because it is not
// needed anymore.
void SpaceManager::deallocate(MetaWord* p, size_t word_size) {
  MutexLocker cl(lock(), Mutex::_no_safepoint_check_flag);
  deallocate_locked(p, word_size);
}

// Update statistics. This walks all in-use chunks.
void SpaceManager::add_to_statistics(sm_stats_t* out) const {

  MutexLocker cl(lock(), Mutex::_no_safepoint_check_flag);

  for (const Metachunk* c = _chunks.first(); c != NULL; c = c->next()) {
    in_use_chunk_stats_t& ucs = out->stats[c->level()];
    ucs.num ++;
    ucs.word_size += c->word_size();
    ucs.committed_words += c->committed_words();
    ucs.used_words += c->used_words();
    // Note: for free and waste, we only count what's committed.
    if (c == current_chunk()) {
      ucs.free_words += c->free_below_committed_words();
    } else {
      ucs.waste_words += c->free_below_committed_words();
    }
  }

  if (block_freelist() != NULL) {
    out->free_blocks_num += block_freelist()->num_blocks();
    out->free_blocks_word_size += block_freelist()->total_size();
  }

  SOMETIMES(out->verify();)

}

#ifdef ASSERT

void SpaceManager::verify_locked() const {

  assert_lock_strong(lock());

  assert(_chunk_alloc_sequence != NULL && _chunk_manager != NULL, "Sanity");

  _chunks.verify();

}

void SpaceManager::verify() const {

  MutexLocker cl(lock(), Mutex::_no_safepoint_check_flag);
  verify_locked();

}

// Returns true if the area indicated by pointer and size have actually been allocated
// from this space manager.
bool SpaceManager::is_valid_area(MetaWord* p, size_t word_size) const {
  assert(p != NULL && word_size > 0, "Sanity");
  for (const Metachunk* c = _chunks.first(); c != NULL; c = c->next()) {
    if (c->is_valid_pointer(p)) {
      assert(c->is_valid_pointer(p + word_size - 1), "Range partly oob");
      return true;
    }
  }
  return false;
}

#endif // ASSERT


} // namespace metaspace

