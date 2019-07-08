/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/metaspace/metaspaceCommon.hpp"

class outputStream;
class Mutex;

namespace metaspace {


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
  // are done from the current chunk.  The list is used for deallocating
  // chunks when the SpaceManager is freed.
  Metachunk* _first_chunk;
  Metachunk* _current_chunk;

  // Prematurely released metablocks.
  BlockFreelist* _block_freelist;


  // Statistics

  // Running counters.
  // Note: capacity = used + free + waste + overhead. We do not keep running counters for
  // free and waste. Their sum can be deduced from the three other values.
  size_t _overhead_words;
  size_t _capacity_words;
  size_t _used_words;
  uint32_t _num_chunks_by_type[NUM_CHUNK_LEVELS];


  Mutex* lock() const                           { return _lock; }
  ChunkManager* chunk_manager() const           { return _chunk_manager; }
  const ChunkAllocSequence* chunk_alloc_sequence() const    { return _chunk_alloc_sequence; }

  Metachunk* first_chunk()                      { return _first_chunk; }
  BlockFreelist* block_freelist(); // created on demand only

  // The current chunk is unable to service a request. The remainder of the chunk is
  // chopped into blocks and fed into the _block_freelists, in the hope of later reuse.
  void retire_current_chunk();

public:

  SpaceManager(ChunkManager* chunk_manager, const ChunkAllocSequence* alloc_sequence, Mutex* lock);

  ~SpaceManager();

  // Allocate memory from Metaspace. Will attempt to allocate from the _block_freelists,
  // failing that, from the current chunk; failing that, attempt to get a new chunk from
  // the associated ChunkManager.
  MetaWord* allocate(size_t word_size);

  // Prematurely returns a metaspace allocation to the _block_freelists because it is not
  // needed anymore.
  void deallocate(MetaWord* p, size_t word_size);

  // Run verifications. slow=true: verify chunk-internal integrity too.
  DEBUG_ONLY(void locked_verify(bool slow) const;)

};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_SPACEMANAGER_HPP
