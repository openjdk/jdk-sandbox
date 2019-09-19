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
#ifndef SHARE_MEMORY_METASPACE_METACHUNK_HPP
#define SHARE_MEMORY_METASPACE_METACHUNK_HPP


#include "memory/metaspace/counter.hpp"
#include "memory/metaspace/chunkLevel.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"


class outputStream;

namespace metaspace {

class VirtualSpaceNode;

//  Metachunk - Quantum of allocation from a Virtualspace
//    Metachunks are reused (when freed are put on a global freelist) and
//    have no permanent association to a SpaceManager.

//            +--------------+ <- end    ----+         --+
//            |              |               |           |
//            |              |               | free      |
//            |              |               |
//            |              |               |           | size (aka capacity)
//            |              |               |           |
//            | -----------  | <- top     -- +           |
//            |              |               |           |
//            |              |               | used      |
//            +--------------+ <- start   -- +        -- +

// Note: this is a chunk **descriptor**. The real Payload area lives in metaspace,
// this class lives somewhere else.
class Metachunk {

  // start of chunk memory; NULL if dead.
  MetaWord* _base;

  // Used words.
  size_t _used_words;

  // Size of the region, starting from base, which is guaranteed to be committed. In words.
  //  The actual size of committed regions may be larger, but it may be fragmented.
  //
  //  (This is a performance optimization. The underlying VirtualSpaceNode knows
  //  which granules are committed; but we want to avoid asking it unnecessarily
  //  in Metachunk::allocate().)
  size_t _committed_words;

  chklvl_t _level; // aka size.

  // state_free:    free, owned by ChunkManager
  // state_in_use:  in-use, owned by SpaceManager
  // dead:          just a hollow chunk header without associated memory, owned
  //                 by chunk header pool.
  enum state_t {
    state_free = 0,
    state_in_use = 1,
    state_dead = 2
  };
  state_t _state;

  // We need unfortunately a back link to the virtual space node
  // for splitting and merging nodes.
  VirtualSpaceNode* _vsnode;


  // A chunk header is kept in a list:
  // - in the list of used chunks inside a SpaceManager, if it is in use
  // - in the list of free chunks inside a ChunkManager, if it is free
  // - in the freelist of unused headers inside the ChunkHeaderPool,
  //   if it is unused (e.g. result of chunk merging) and has no associated
  //   memory area.
  Metachunk* _prev;
  Metachunk* _next;

  // Furthermore, we keep, per chunk, information about the neighboring chunks.
  // This is needed to split and merge chunks.
  //
  // Note: These members can be modified concurrently while a chunk is alive and in use.
  // This can happen if a neighboring chunk is added or removed.
  // This means only read or modify these members under expand lock protection.
  Metachunk* _prev_in_vs;
  Metachunk* _next_in_vs;

  MetaWord* top() const           { return base() + _used_words; }

  // Commit uncommitted section of the chunk.
  // Fails if we hit a commit limit.
  bool commit_up_to(size_t new_committed_words);

  DEBUG_ONLY(static void assert_have_expand_lock();)

public:

  Metachunk()
    : _base(NULL),
      _used_words(0),
      _committed_words(0),
      _level(chklvl::ROOT_CHUNK_LEVEL),
      _state(state_free),
      _vsnode(NULL),
      _prev(NULL), _next(NULL),
      _prev_in_vs(NULL), _next_in_vs(NULL)
  {}

  size_t word_size() const        { return chklvl::word_size_for_level(_level); }

  MetaWord* base() const          { return _base; }
//  void set_base(MetaWord* p)      { _base = p; }
  MetaWord* end() const           { return base() + word_size(); }

  // Chunk list wiring
  void set_prev(Metachunk* c)     { _prev = c; }
  Metachunk* prev() const         { return _prev; }
  void set_next(Metachunk* c)     { _next = c; }
  Metachunk* next() const         { return _next; }

  DEBUG_ONLY(bool in_list() const { return _prev != NULL || _next != NULL; })

  // Physical neighbors wiring
  void set_prev_in_vs(Metachunk* c) { DEBUG_ONLY(assert_have_expand_lock()); _prev_in_vs = c; }
  Metachunk* prev_in_vs() const     { DEBUG_ONLY(assert_have_expand_lock()); return _prev_in_vs; }
  void set_next_in_vs(Metachunk* c) { DEBUG_ONLY(assert_have_expand_lock()); _next_in_vs = c; }
  Metachunk* next_in_vs() const     { DEBUG_ONLY(assert_have_expand_lock()); return _next_in_vs; }

  bool is_free() const            { return _state == state_free; }
  bool is_in_use() const          { return _state == state_in_use; }
  bool is_dead() const            { return _state == state_dead; }
  void set_free()                 { _state = state_free; }
  void set_in_use()               { _state = state_in_use; }
  void set_dead()                 { _state = state_dead; }

  // Return a single char presentation of the state ('f', 'u', 'd')
  char get_state_char() const;

  void inc_level()                { _level ++; DEBUG_ONLY(chklvl::is_valid_level(_level);) }
  void dec_level()                { _level --; DEBUG_ONLY(chklvl::is_valid_level(_level);) }
//  void set_level(chklvl_t v)      { _level = v; DEBUG_ONLY(chklvl::is_valid_level(_level);) }
  chklvl_t level() const          { return _level; }

  // Convenience functions for extreme levels.
  bool is_root_chunk() const      { return chklvl::ROOT_CHUNK_LEVEL == _level; }
  bool is_leaf_chunk() const      { return chklvl::HIGHEST_CHUNK_LEVEL == _level; }

  VirtualSpaceNode* vsnode() const        { return _vsnode; }
//  void set_vsnode(VirtualSpaceNode* n)    { _vsnode = n; }

  size_t used_words() const                   { return _used_words; }
  size_t free_words() const                   { return word_size() - used_words(); }
  size_t free_below_committed_words() const   { return committed_words() - used_words(); }
  void reset_used_words()                     { _used_words = 0; }

  size_t committed_words() const      { return _committed_words; }
  void set_committed_words(size_t v);
  bool is_fully_committed() const     { return committed_words() == word_size(); }
  bool is_fully_uncommitted() const   { return committed_words() == 0; }

  // Ensure that chunk is committed up to at least new_committed_words words.
  // Fails if we hit a commit limit.
  bool ensure_committed(size_t new_committed_words);
  bool ensure_committed_locked(size_t new_committed_words);

  bool ensure_fully_committed()           { return ensure_committed(word_size()); }
  bool ensure_fully_committed_locked()    { return ensure_committed_locked(word_size()); }

  // Uncommit chunk area. The area must be a common multiple of the
  // commit granule size (in other words, we cannot uncommit chunks smaller than
  // a commit granule size).
  void uncommit();
  void uncommit_locked();

  // Alignment of an allocation.
  static const size_t allocation_alignment_bytes = 8;
  static const size_t allocation_alignment_words = allocation_alignment_bytes / BytesPerWord;

  // Allocation from a chunk

  // Allocate word_size words from this chunk (word_size must be aligned to
  //  allocation_alignment_words).
  //
  // May cause memory to be committed. That may fail if we hit a commit limit. In that case,
  //  NULL is returned and p_did_hit_commit_limit will be set to true.
  // If the remainder portion of the chunk was too small to hold the allocation,
  //  NULL is returned and p_did_hit_commit_limit will be set to false.
  MetaWord* allocate(size_t net_word_size, bool* p_did_hit_commit_limit);

  // Given a memory range which may or may not have been allocated from this chunk, attempt
  // to roll its allocation back. This can work if this is the very last allocation we did
  // from this chunk, in which case we just lower the top pointer again.
  // Returns true if this succeeded, false if it failed.
  bool attempt_rollback_allocation(MetaWord* p, size_t word_size);

  // Initialize structure for reuse.
  void initialize(VirtualSpaceNode* node, MetaWord* base, chklvl_t lvl) {
    _vsnode = node; _base = base; _level = lvl;
    _used_words = _committed_words = 0; _state = state_free;
    _next = _prev = _next_in_vs = _prev_in_vs = NULL;
  }

  // Returns true if this chunk is the leader in its buddy pair, false if not.
  // Must not be called for root chunks.
  bool is_leader() const {
    assert(!is_root_chunk(), "Root chunks have no buddy.");
    // I am sure this can be done smarter...
    return is_aligned(base(), chklvl::word_size_for_level(level() - 1) * BytesPerWord);
  }

  //// Debug stuff ////
#ifdef ASSERT
  void verify(bool slow) const;
  // Verifies linking with neighbors in virtual space. Needs expand lock protection.
  void verify_neighborhood() const;
  void zap_header(uint8_t c = 0x17);
  void fill_with_pattern(MetaWord pattern, size_t word_size);
  void check_pattern(MetaWord pattern, size_t word_size);

  // Returns true if pointer points into the used area of this chunk.
  bool is_valid_pointer(const MetaWord* p) const {
    return base() <= p && p < top();
  }
#endif // ASSERT

  void print_on(outputStream* st) const;

};

// Little print helpers: since we often print out chunks, here some convenience macros
#define METACHUNK_FORMAT                "@" PTR_FORMAT ", %c, base " PTR_FORMAT ", level " CHKLVL_FORMAT
#define METACHUNK_FORMAT_ARGS(chunk)    p2i(chunk), chunk->get_state_char(), p2i(chunk->base()), chunk->level()

#define METACHUNK_FULL_FORMAT                "@" PTR_FORMAT ", %c, base " PTR_FORMAT ", level " CHKLVL_FORMAT " (" SIZE_FORMAT "), used: " SIZE_FORMAT ", committed: " SIZE_FORMAT
#define METACHUNK_FULL_FORMAT_ARGS(chunk)    p2i(chunk), chunk->get_state_char(), p2i(chunk->base()), chunk->level(), chunk->word_size(), chunk->used_words(), chunk->committed_words()

/////////
// A list of Metachunks.
class MetachunkList {

  Metachunk* _first;

  // Number of chunks
  IntCounter _num;

public:

  MetachunkList() : _first(NULL), _num() {}

  Metachunk* first() const { return _first; }
  int size() const { return _num.get(); }

  void add(Metachunk* c) {
    assert(!c->in_list(), "Chunk must not be in a list");
    if (_first) {
      _first->set_prev(c);
    }
    c->set_next(_first);
    c->set_prev(NULL);
    _first = c;
    _num.increment();
  }

  // Remove first node unless empty. Returns node or NULL.
  Metachunk* remove_first() {
    Metachunk* c = _first;
    if (c != NULL) {
      assert(c->prev() == NULL, "Sanity");
      Metachunk* c2 = c->next();
      if (c2 != NULL) {
        c2->set_prev(NULL);
      }
      _first = c2;
      c->set_next(NULL);
      _num.decrement();
    }
    return c;
  }

  // Remove given chunk from list. List must contain that chunk.
  void remove(Metachunk* c) {
    assert(contains(c), "List does not contain this chunk");
    if (_first == c) {
      _first = c->next();
      if (_first != NULL) {
        _first->set_prev(NULL);
      }
    } else {
      if (c->next() != NULL) {
        c->next()->set_prev(c->prev());
      }
      if (c->prev() != NULL) {
        c->prev()->set_next(c->next());
      }
    }
    c->set_prev(NULL);
    c->set_next(NULL);
    _num.decrement();
  }

#ifdef ASSERT
  bool contains(const Metachunk* c) const;
  void verify() const;
#endif

  // Returns size, in words, of committed space of all chunks in this list.
  // Note: walks list.
  size_t committed_word_size() const {
    size_t l = 0;
    for (const Metachunk* c = _first; c != NULL; c = c->next()) {
      l += c->committed_words();
    }
    return l;
  }

  void print_on(outputStream* st) const;

};

//////////////////
// A cluster of Metachunk Lists, one for each chunk level, together with associated counters.
class MetachunkListCluster {

  MetachunkList _lists[chklvl::NUM_CHUNK_LEVELS];
  SizeCounter   _total_word_size;
  IntCounter    _total_num_chunks;

  const MetachunkList* list_for_level(chklvl_t lvl) const         { DEBUG_ONLY(chklvl::check_valid_level(lvl)); return _lists + lvl; }
  MetachunkList* list_for_level(chklvl_t lvl)                     { DEBUG_ONLY(chklvl::check_valid_level(lvl)); return _lists + lvl; }

  const MetachunkList* list_for_chunk(const Metachunk* c) const   { return list_for_level(c->level()); }
  MetachunkList* list_for_chunk(const Metachunk* c)               { return list_for_level(c->level()); }

public:

  const Metachunk* first_at_level(chklvl_t lvl) const   { return list_for_level(lvl)->first(); }
  Metachunk* first_at_level(chklvl_t lvl)               { return list_for_level(lvl)->first(); }

  // Remove given chunk from its list. List must contain that chunk.
  void remove(Metachunk* c) {
    list_for_chunk(c)->remove(c);
    _total_word_size.decrement_by(c->word_size());
    _total_num_chunks.decrement();
  }

  // Remove first node unless empty. Returns node or NULL.
  Metachunk* remove_first(chklvl_t lvl) {
    Metachunk* c = list_for_level(lvl)->remove_first();
    if (c != NULL) {
      _total_word_size.decrement_by(c->word_size());
      _total_num_chunks.decrement();
    }
    return c;
  }

  void add(Metachunk* c) {
    list_for_chunk(c)->add(c);
    _total_word_size.increment_by(c->word_size());
    _total_num_chunks.increment();
  }

  // Returns number of chunks for a given level.
  int num_chunks_at_level(chklvl_t lvl) const {
    return list_for_level(lvl)->size();
  }

  // Returns number of chunks for a given level.
  size_t committed_word_size_at_level(chklvl_t lvl) const {
    return list_for_level(lvl)->committed_word_size();
  }

  // Returs word size, in total, of all chunks in all lists.
  size_t total_word_size() const          { return _total_word_size.get(); }

  // Returns number of chunks in total
  int total_num_chunks() const            { return _total_num_chunks.get(); }

  // Returns size, in words, of committed space of all chunks in all list.
  // Note: walks lists.
  size_t total_committed_word_size() const {
    size_t sum = 0;
    for (chklvl_t l = chklvl::LOWEST_CHUNK_LEVEL; l <= chklvl::HIGHEST_CHUNK_LEVEL; l ++) {
      sum += list_for_level(l)->committed_word_size();
    }
    return sum;
  }

  DEBUG_ONLY(void verify(bool slow) const;)
  DEBUG_ONLY(bool contains(const Metachunk* c) const;)

  void print_on(outputStream* st) const;

};


} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_METACHUNK_HPP
