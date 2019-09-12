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

#ifndef SHARE_MEMORY_METASPACE_SPACEMANAGER_HPP
#define SHARE_MEMORY_METASPACE_SPACEMANAGER_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspace/blockFreelist.hpp"
#include "memory/metaspace/chunkAllocSequence.hpp"
#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"

class outputStream;
class Mutex;

namespace metaspace {

struct sm_stats_t;

// The SpaceManager:
// - keeps a list of chunks-in-use by the class loader, as well as a current chunk used
//   to allocate from
// - keeps a dictionary of free MetaBlocks. Those can be remnants of a retired chunk or
//   allocations which were not needed anymore for some reason (e.g. releasing half-allocated
//   structures when class loading fails)

class SpaceManager : public CHeapObj<mtClass> {

  // Lock handed down from the associated ClassLoaderData.
  //  Protects allocations from this space.
  Mutex* const _lock;

  // The chunk manager to allocate chunks from.
  ChunkManager* const _chunk_manager;

  // The chunk allocation strategy to use.
  const ChunkAllocSequence* const _chunk_alloc_sequence;

  // List of chunks in use by this SpaceManager.  Allocations
  // are done from the current chunk. The list is used for deallocating
  // chunks when the SpaceManager is freed.
  MetachunkList _chunks;

  Metachunk* current_chunk()              { return _chunks.first(); }
  const Metachunk* current_chunk() const  { return _chunks.first(); }

  // Prematurely released metablocks.
  BlockFreelist* _block_freelist;

  // Points to outside size counter which we are to increase/decrease when we allocate memory
  // on behalf of a user or when we are destroyed.
  SizeAtomicCounter* const _total_used_words_counter;

  const char* const _name;

  Mutex* lock() const                           { return _lock; }
  ChunkManager* chunk_manager() const           { return _chunk_manager; }
  const ChunkAllocSequence* chunk_alloc_sequence() const    { return _chunk_alloc_sequence; }

  BlockFreelist* block_freelist() const         { return _block_freelist; }
  void create_block_freelist();
  void add_allocation_to_block_freelist(MetaWord* p, size_t word_size);

  // The remaining committed free space in the current chunk is chopped up and stored in the block
  // free list for later use. As a result, the current chunk will remain current but completely
  // used up. This is a preparation for calling allocate_new_current_chunk().
  void retire_current_chunk();

  // Given a requested word size, will allocate a chunk large enough to at least fit that
  // size, but may be larger according to internal heuristics.
  //
  // On success, it will replace the current chunk with the newly allocated one, which will
  // become the current chunk. The old current chunk should be retired beforehand.
  //
  // May fail if we could not allocate a new chunk. In that case the current chunk remains
  // unchanged and false is returned.
  bool allocate_new_current_chunk(size_t requested_word_size);

  // Prematurely returns a metaspace allocation to the _block_freelists
  // because it is not needed anymore (requires CLD lock to be active).
  void deallocate_locked(MetaWord* p, size_t word_size);

  // Returns true if the area indicated by pointer and size have actually been allocated
  // from this space manager.
  DEBUG_ONLY(bool is_valid_area(MetaWord* p, size_t word_size) const;)

public:

  SpaceManager(ChunkManager* chunk_manager,
               const ChunkAllocSequence* alloc_sequence,
               Mutex* lock,
               SizeAtomicCounter* total_used_words_counter,
               const char* name);

  ~SpaceManager();

  // Allocate memory from Metaspace.
  // 1) Attempt to allocate from the dictionary of deallocated blocks.
  // 2) Attempt to allocate from the current chunk.
  // 3) Attempt to enlarge the current chunk in place if it is too small.
  // 4) Attempt to get a new chunk and allocate from that chunk.
  // At any point, if we hit a commit limit, we return NULL.
  MetaWord* allocate(size_t word_size);

  // Prematurely returns a metaspace allocation to the _block_freelists because it is not
  // needed anymore.
  void deallocate(MetaWord* p, size_t word_size);

  // Update statistics. This walks all in-use chunks.
  void add_to_statistics(sm_stats_t* out) const;

  // Run verifications. slow=true: verify chunk-internal integrity too.
  DEBUG_ONLY(void verify(bool slow) const;)

};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_SPACEMANAGER_HPP
