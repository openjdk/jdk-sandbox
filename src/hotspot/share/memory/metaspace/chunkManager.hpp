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

#ifndef SHARE_MEMORY_METASPACE_CHUNKMANAGER_HPP
#define SHARE_MEMORY_METASPACE_CHUNKMANAGER_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace/chunkLevel.hpp"

namespace metaspace {

class VirtualSpaceList;
class Metachunk;


// class ChunkManager
//
// The ChunkManager has a central role. Callers request chunks from it.
// It keeps the freelists for chunks. If the freelist is exhausted it
// allocates new chunks from a connected VirtualSpaceList.
//
class ChunkManager : public CHeapObj<mtInternal> {

  // A chunk manager is connected to a virtual space list which is used
  // to allocate new root chunks when no free chunks are found.
  VirtualSpaceList* const _vs_list;

  // Name
  const char* const _name;

  // Freelist
  Metachunk* _chunks [NUM_CHUNK_LEVELS];

  // Statistics.
  // Number of counters per level.
  int        _num_chunks [NUM_CHUNK_LEVELS];

  // Total size in all chunks, and total number of chunks.
  size_t     _total_word_size;
  int        _total_num_chunks;

  // Remove a chunk of the given level from its freelist, and adjust accounting.
  // If no chunk of this given level is free, return NULL.
  Metachunk* get_chunk_simple(chklvl_t level);

  // Update counters after a chunk has been added or removed.
  void account_for_added_chunk(const Metachunk* c);
  void account_for_removed_chunk(const Metachunk* c);

  // Returns true if this manager contains the given chunk. Slow (walks free list) and
  // only needed for verifications.
  DEBUG_ONLY(bool contains_chunk(Metachunk* metachunk) const;)

public:

  // Creates a chunk manager with a given name (which is for debug purposes only)
  // and an associated space list which will be used to request new chunks from
  // (see get_chunk())
  ChunkManager(const char* name, VirtualSpaceList* space_list);

  // Get a chunk and be smart about it.
  // - Attempt to find a free chunk of exactly the pref_level level
  // - Failing that, attempt to find a chunk smaller or equal the minimal level.
  // - Failing that, attempt to find a free chunk of larger size and split it.
  // - Failing that, attempt to allocate a new chunk from the connected virtual space.
  //    This may fail if we hit GC threshold or metaspace limit.
  // - Failing that, give up and return NULL.
  Metachunk* get_chunk(chklvl_t min_level, chklvl_t pref_level);

  // Remove the given chunk from its free list and adjust accounting.
  // (Called during VirtualSpaceNode purging which happens during a Metaspace GC.)
  void remove_chunk(Metachunk* chunk);

  // Return a chunk to the ChunkManager and adjust accounting. May merge chunk
  //  with neighbors.
  // Happens after a Classloader was unloaded and releases its metaspace chunks.
  // !! Note: this may invalidate the chunk. Do not access the chunk after
  //    this function returns !!
  void return_chunk(Metachunk* chunk);

  // Uncommit payload area of free chunks. Will be called during Metaspace GC.
  void uncommit_free_chunks();

  // Run verifications. slow=true: verify chunk-internal integrity too.
  DEBUG_ONLY(void verify(bool slow) const;)
  DEBUG_ONLY(void locked_verify(bool slow) const;)

  // Returns the name of this chunk manager.
  const char* name() const { return _name; }

};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_CHUNKMANAGER_HPP
