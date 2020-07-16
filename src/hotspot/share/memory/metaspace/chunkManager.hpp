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

#ifndef SHARE_MEMORY_METASPACE_CHUNKMANAGER_HPP
#define SHARE_MEMORY_METASPACE_CHUNKMANAGER_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace/chunkLevel.hpp"
#include "memory/metaspace/counter.hpp"
#include "memory/metaspace/freeChunkList.hpp"
#include "memory/metaspace/metachunk.hpp"

namespace metaspace {

class VirtualSpaceList;
struct cm_stats_t;

// ChunkManager has a central role.

// SpaceManagers request chunks from it. It keeps the freelists for chunks.
// If the freelist is exhausted it allocates new chunks from a connected
// VirtualSpaceList.
//
class ChunkManager : public CHeapObj<mtMetaspace> {

  // A chunk manager is connected to a virtual space list which is used
  // to allocate new root chunks when no free chunks are found.
  VirtualSpaceList* const _vslist;

  // Name
  const char* const _name;

  // Freelists
  FreeChunkListVector _chunks;

  // Returns true if this manager contains the given chunk. Slow (walks free lists) and
  // only needed for verifications.
  DEBUG_ONLY(bool contains_chunk(Metachunk* c) const;)

  // Given a chunk we are about to handout to the caller, make sure it is committed
  // according to constants::committed_words_on_fresh_chunks.
  // May fail if we hit the commit limit.
  static bool commit_chunk_before_handout(Metachunk* c);

  // Take a single chunk from the given freelist and adjust counters. Returns NULL
  // if there is no fitting chunk for this level.
  Metachunk* remove_first_chunk_at_level(chunklevel_t l);

  // Given a chunk, split it into a target chunk of a smaller size (target level)
  //  at least one, possible more splinter chunks. Splinter chunks are added to the
  //  freelist.
  // The original chunk must be outside of the freelist and its state must be free.
  // The resulting target chunk will be located at the same address as the original
  //  chunk, but it will of course be smaller (of a higher level).
  // The committed areas within the original chunk carry over to the resulting
  //  chunks.
  void split_chunk_and_add_splinters(Metachunk* c, chunklevel_t target_level);

  // See get_chunk(s,s,s)
  Metachunk* get_chunk_locked(size_t preferred_word_size, size_t min_word_size, size_t min_committed_words);

  // Uncommit all chunks equal or below the given level.
  void uncommit_free_chunks(chunklevel_t max_level);

public:

  // Creates a chunk manager with a given name (which is for debug purposes only)
  // and an associated space list which will be used to request new chunks from
  // (see get_chunk())
  ChunkManager(const char* name, VirtualSpaceList* space_list);

  // On success, returns a chunk of level of <preferred_level>, but at most <max_level>.
  //  The first first <min_committed_words> of the chunk are guaranteed to be committed.
  // On error, will return NULL.
  //
  // This function may fail for two reasons:
  // - Either we are unable to reserve space for a new chunk (if the underlying VirtualSpaceList
  //   is non-expandable but needs expanding - aka out of compressed class space).
  // - Or, if the necessary space cannot be committed because we hit a commit limit.
  //   This may be either the GC threshold or MaxMetaspaceSize.
  Metachunk* get_chunk(chunklevel_t preferred_level, chunklevel_t max_level, size_t min_committed_words);

  // Convenience function - get a chunk of a given level, uncommitted.
  Metachunk* get_chunk(chunklevel_t lvl) { return get_chunk(lvl, lvl, 0); }

  // Return a single chunk to the ChunkManager and adjust accounting. May merge chunk
  //  with neighbors.
  // Happens after a Classloader was unloaded and releases its metaspace chunks.
  // !! Notes:
  //    1) After this method returns, c may not be valid anymore. ** Do not access c after this function returns **.
  //    2) This function will not remove c from its current chunk list. This has to be done by the caller prior to
  //       calling this method.
  void return_chunk(Metachunk* c);

  // Return a single chunk to the freelist and adjust accounting. No merge is attempted.
  void return_chunk_simple(Metachunk* c);

  // Given a chunk c, which must be "in use" and must not be a root chunk, attempt to
  // enlarge it in place by claiming its trailing buddy.
  //
  // This will only work if c is the leader of the buddy pair and the trailing buddy is free.
  //
  // If successful, the follower chunk will be removed from the freelists, the leader chunk c will
  // double in size (level decreased by one).
  //
  // On success, true is returned, false otherwise.
  bool attempt_enlarge_chunk(Metachunk* c);

  // Attempt to reclaim free areas in metaspace wholesale:
  // - first, attempt to purge nodes of the backing virtual space. This can only be successful
  //   if whole nodes are only containing free chunks, so it highly depends on fragmentation.
  // - then, it will uncommit areas of free chunks according to the rules laid down in
  //   settings (see settings.hpp).
  void wholesale_reclaim();

  // Run verifications. slow=true: verify chunk-internal integrity too.
  DEBUG_ONLY(void verify(bool slow) const;)
  DEBUG_ONLY(void verify_locked(bool slow) const;)

  // Returns the name of this chunk manager.
  const char* name() const                  { return _name; }

  // Returns total number of chunks
  int total_num_chunks() const              { return _chunks.num_chunks(); }

  // Returns number of words in all free chunks (regardless of commit state).
  size_t total_word_size() const            { return _chunks.word_size(); }

  // Returns number of committed words in all free chunks.
  size_t total_committed_word_size() const  { return _chunks.committed_word_size(); }

  // Update statistics.
  void add_to_statistics(cm_stats_t* out) const;

  void print_on(outputStream* st) const;
  void print_on_locked(outputStream* st) const;

private:

  static ChunkManager* _chunkmanager_class;
  static ChunkManager* _chunkmanager_nonclass;

public:

  static ChunkManager* chunkmanager_class() { return _chunkmanager_class; }
  static ChunkManager* chunkmanager_nonclass() { return _chunkmanager_nonclass; }

  static void set_chunkmanager_class(ChunkManager* cm);
  static void set_chunkmanager_nonclass(ChunkManager* cm);


};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_CHUNKMANAGER_HPP
