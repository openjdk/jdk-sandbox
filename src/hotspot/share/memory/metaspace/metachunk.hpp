/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_MEMORY_METASPACE_METACHUNK_HPP
#define SHARE_MEMORY_METASPACE_METACHUNK_HPP

#include "memory/metaspace/metabase.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/chunkLevel.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class MetachunkTest;

namespace metaspace {

class VirtualSpaceNode;

//  Metachunk - Quantum of allocation from a Virtualspace
//    Metachunks are reused (when freed are put on a global freelist) and
//    have no permanent association to a SpaceManager.

//            +--------------+ <- end    ----+         --+
//            |              |               |           |
//            |              |               | free      |
//            | -----------  | <- commit_top |           |
//            |              |               |           | size (aka capacity)
//            |              |               |           |
//            | -----------  | <- top     -- +           |
//            |              |               |           |
//            |              |               | used      |
//            +--------------+ <- start   -- +           |
//            |   header     |               | overhead  |
//            +--------------+ <- base   ----+         --+


class Metachunk {

  friend class ::MetachunkTest;

  // A metachunk is kept in a list
  Metachunk* _prev;
  Metachunk* _next;

  chklvl_t _level; // aka size.

  bool _is_free;

  // Committed words, including header.
  size_t _committed_words;

  // Used words, including header.
  size_t _used_words;

  // "abandoned" committed words
  // Number of words committed beyond the commit_top(). This
  // can be the result of merging two half-committed chunks.
  // We need to track the number, but not the exact location
  // (see merging/splitting)
  size_t _abandoned_committed_words;

#ifdef ASSERT
  // A 32bit sentinel for debugging purposes.
  static const uint32_t CHUNK_SENTINEL = 0x4d4554EF;  // "MET"
  static const uint32_t CHUNK_SENTINEL_INVALID = 0xFEEEEEEF;
  uint32_t _sentinel;
#endif

  MetaWord* base() const        { return (MetaWord*)this; }
  MetaWord* start() const       { return (MetaWord*)this + overhead(); }
  MetaWord* top() const         { return base() + _used_words; }
  MetaWord* commit_top() const  { return base() + _committed_words; }
  MetaWord* end() const         { return (MetaWord*)this + word_size(); }

  // A "root chunk" is a chunk of the highest level. It cannot coalesce further
  // and has no buddy.
  bool is_root_chunk() const    { return _level == HIGHEST_CHUNK_LEVEL; }

  // expand the committed range of this chunk to hold at least word_size additional words;
  // Returns false if failed, which may be e.g. due to hitting a limit.
  bool expand_committed(size_t requested_word_size, bool& did_hit_commit_limit);

  // Returns the location of the buddy, or NULL if this is a root chunk which
  // has no buddy.
  Metachunk* get_buddy_address() const;

public:

  // Create a chunk with a given level and a given commit size.
  Metachunk(chklvl_t level, size_t committed_words);

  // Alignment of each allocation in the chunks.
  static size_t object_alignment();

  // Size of the Metachunk header, in words, including alignment.
  static size_t overhead();

  chklvl_t level() const              { return _level; }

  // Returns size, in bytes, of this chunk including the header.
  size_t size() const {
    return MIN_CHUNK_BYTE_SIZE << _level;
  }


  // Reset top to bottom so chunk can be reused.
  void reset()    { _used_words = 0; }


  // Returns the total word size of this chunk, including header.
  size_t word_size() const                { return size() / sizeof(MetaWord); }

  // Returns the used words in this chunk, including header.
  size_t used_word_size() const           { return _used_words; }

  // Returns the number of free words below the commit top
  // (how much can be allocated from this chunk without commit).
  size_t free_word_size_no_commit() const { return _committed_words - _used_words; }

  // Returns the number of free words in total, including uncommitted area.
  size_t free_word_size_total() const     { return word_size() - _used_words; }

  bool is_empty() const             { return _used_words == 0; }
  bool is_fully_committed() const   { return _committed_words == word_size(); }

  /////////////
  // Allocation, commit, uncommit

  // Allocate from chunk.
  // This may fail and return NULL due to the following reasons:
  // - the chunk may be too small to hold the allocation (did_hit_commit_limit will be false).
  // - chunk needed to expand his commit top to hold the allocation, but that failed because we hit a
  //   limit (GC threshold or metaspace limit)  (did_hit_commit_limit will be true).
  // Returns pointer to allocation, or NULL.
  MetaWord* allocate(size_t requested_word_size, bool& did_hit_commit_limit);

  /////////////
  // Splitting

  // Given a chunk c, split it two chunks.
  // - chunk must be free
  // - chunk must be committed far enough to include the header of the new
  //   buddy chunk
  // - if chunk is of the smallest size already and cannot split anymore,
  //   false is ret
  // Returns true if success.
  // This operation succeeds unless the chunk is of the smallest level already
  //  in which case NULL is returned.
  static bool split(Metachunk* c, Metachunk** p_leader, Metachunk** p_follower);

  /////////////
  // Merging

  // Chunk merging means a chunk is merged with its buddy. The resulting
  //  chunk occupies the area of both former chunks. Its level will be increased by 1.
  //  (x marks the header):
  //
  // before:  |x      |x      |
  //
  // after:   |x              |
  //
  // The result chunk will be committed according to following rules:
  //  1) if the first chunk was completely committed, result chunk will be as far committed
  //     as the second chunk was committed (dashes mark the committed area):
  //
  // before:  |-------|----   |
  //
  // after:   |------------   |
  //
  //    Obviously, this means that if the second chunk was completely committed, the
  //    result chunk will be completely committed too:
  //
  // before:  |-------|-------|
  //
  // after:   |---------------|
  //
  //  2) if the first chunk was not completely committed, result chunk will be as far committed
  //     as the first chunk, and will carry over the size of the committed area of the second chunk
  //     in the "_abandoned_committed_words" member:
  //
  // before:  |---    |------ |  _abandoned_committed_words = 0
  //
  // after:   |---            |  _abandoned_committed_words = sizeof(------)
  //
  // _abandoned_committed_words accumulates with each subsequent merge. After a sequence of merges,
  // it carries over the sum of all committed "abandoned" regions in the follower chunks.
  //
  //

  // Attempt to merge this chunk with its buddy.
  // This succeeds if:
  // - the chunk is not of the highest level (root chunks have no buddies).
  // - the buddy is free
  // If successful, a pointer to the new chunk is returned.
  //   !! In that case, the original "this" pointer will be invalid; do not access it anymore !!
  // If failed, will return NULL.
  Metachunk* try_merge();

  /////////////
  // Free/in use

  bool is_free() const  { return _is_free; }
  void set_free()       { _is_free = true; }
  void set_in_use()     { _is_free = false; }


  /////////////
  // List stuff

  void set_prev(Metachunk* c)   { _prev = c; }
  void set_next(Metachunk* c)   { _next = c; }
  Metachunk* prev() const       { return _prev; }
  Metachunk* next() const       { return _next; }
  // Remove chunk from whatever list it lives in by wiring next with previous.
  // In debug case, zeros out _next, _prev.
  void remove_from_list();

  //////////////
  // Debugging aids

  bool contains(const void* ptr) const {
    return base() <= ptr && ptr < top();
  }

  void print_on(outputStream* st) const;

#ifdef ASSERT
  bool is_valid_sentinel() const        { return _sentinel == CHUNK_SENTINEL; }
  void remove_sentinel()                { _sentinel = CHUNK_SENTINEL_INVALID; }
#endif

  DEBUG_ONLY(void mangle(juint word_value);)
  DEBUG_ONLY(void verify() const;)

};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_METACHUNK_HPP
