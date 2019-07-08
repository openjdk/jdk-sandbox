/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.hpp"
#include "memory/metaspace/chunkLevel.hpp"
#include "memory/metaspace/commitLimit.hpp"
#include "memory/metaspace/constants.hpp"
#include "memory/metaspace/metachunk.hpp"

#include "utilities/align.hpp"
#include "utilities/copy.hpp"
#include "utilities/debug.hpp"

namespace metaspace {

size_t Metachunk::object_alignment() {
  // Must align pointers and sizes to 8,
  // so that 64 bit types get correctly aligned.
  const size_t alignment = 8;

  // Make sure that the Klass alignment also agree.
  STATIC_ASSERT(alignment == (size_t)KlassAlignmentInBytes);

  return alignment;
}

size_t Metachunk::overhead() {
  return align_up(sizeof(Metachunk), object_alignment()) / BytesPerWord;
}

// Metachunk methods

void Metachunk::remove_from_list() {
  if (_prev != NULL) {
    _prev->set_next(_next);
  }
  if (_next != NULL) {
    _next->set_prev(_prev);
  }
  _prev = _next = NULL;
}


// Create a chunk with a given level and a given commit size.
Metachunk::Metachunk(chklvl_t level, size_t committed_words)
  : _prev(NULL), _next(NULL)
  , _level(level)
  , _is_free(true)
  , _committed_words(committed_words)
  , _used_words(overhead())
  , _abandoned_committed_words(0)
{
#ifdef ASSERT
  mangle(uninitMetaWordVal);
  verify();
#endif
}

// expand the committed range of this chunk to hold at least word_size additional words;
// Returns false if failed, which may be e.g. due to hitting a limit.
bool Metachunk::expand_committed(size_t requested_word_size, bool& did_hit_commit_limit) {

  assert(free_word_size_no_commit() >= requested_word_size, "why did you call me");

  const size_t needed = (requested_word_size - free_word_size_no_commit()) * BytesPerWord;

  const size_t alloc_granularity = (size_t)os::vm_allocation_granularity();

  size_t min_expansion = align_up(needed * BytesPerWord, alloc_granularity);
  size_t preferred_expansion = align_up(metachunk_commit_granularity, alloc_granularity);

  const size_t bytes_left_in_chunk = free_word_size_total() * BytesPerWord;
  if (preferred_expansion > bytes_left_in_chunk) {
    // Do not commit beyond chunk limits
    preferred_expansion = bytes_left_in_chunk;
  }

  if (min_expansion > preferred_expansion) {
    preferred_expansion = min_expansion;

  }

  // We are not locked under the metaspace expand lock.
  {
    MutexLocker cl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
    const size_t word_size_possible = CommitLimit::attempt_increase_committed(min_expansion, preferred_expansion);
    if (word_size_possible < min_expansion) {
      // No such luck.
      did_hit_commit_limit = false;
      return NULL;
    }

//blabla
  }
  //blabla


}


// Allocate from chunk.
// This may fail and return NULL due to the following reasons:
// - the chunk may be too small to hold the allocation (did_hit_commit_limit will be false).
// - chunk needed to expand his commit top to hold the allocation, but that failed because we hit a
//   limit (GC threshold or metaspace limit)  (did_hit_commit_limit will be true).
// Returns pointer to allocation, or NULL.
MetaWord* Metachunk::allocate(size_t requested_word_size, bool& did_hit_commit_limit) {

  MetaWord* result = NULL;

  // Can we fit this allocation into the chunk at all?
  if (requested_word_size > free_word_size_total()) {
    did_hit_commit_limit = false;
    return NULL;
  }

  // If yes, do we need to commit more pages?
  if (requested_word_size > free_word_size_no_commit()) {
    if (expand_committed(requested_word_size, did_hit_commit_limit) == false) {
      return NULL;
    }
  }

  assert(free_word_size_no_commit() >= requested_word_size, "Sanity");

  result = base() + _used_words;
  _used_words += requested_word_size;

  assert(_used_words <= _committed_words, "Sanity");

  return result;

}


/////////////
// Merging

// Chunk merging means a chunk is merged with its buddy. The resulting
//  chunk occupies the area of both former chunks (x marks the header):
//
// before:  |x      |x      |
//
// after:   |x              |
//
// The result chunk size is obviously double the size the merged chunks, and
//  its level is one increased.
//
// The result chunk will be committed according to following rules:
//  1) if the first chunk was completely committed, result chunk will be as far committed
//     as the second chunk was committed
//     (dash marks the committed area):
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
//     as the first chunk, and will carry over the size of the committed area in
//     "_abandoned_committed_words"
//
// before:  |---    |------ |
//
// after:   |---            |  with _abandoned_committed_words += sizeof(------)
//
// _abandoned_committed_words accumulates with each subsequent merge. After a sequence of merges,
// it carries over the sum of all committed "abandoned" regions in the follower chunks.
//
//

// Attempt to merge this chunk with its buddy.
// This succeeds if:
// - the chunk is not of the highest level (root chunks have no buddies).
// - the buddy is free
// If successful, a pointer to the new chunk is returned. !! In that case, the original this
//   will be invalid; do not access it anymore!!
// If failed, will return NULL.
Metachunk* Metachunk::try_merge() {

  DEBUG_ONLY(verify();)
  assert(is_free(), "Can only merge free chunks.");

  Metachunk* buddy = get_buddy_address();

  if (buddy) {

    DEBUG_ONLY(buddy->verify();)
    assert(buddy->level() <= level(), "Weird geometry");

    // Can only merge with buddy if it is not splintered and free.
    if (buddy->level() == level() && buddy->is_free()) {

      assert(buddy->word_size() == word_size(), "Sanity");

      // find out who is the leader.
      Metachunk* leader = this;
      Metachunk* follower = buddy;
      if (buddy < this) {
        Metachunk* tmp = leader;
        leader = follower;
        follower = tmp;
      }

      assert(leader->base() + leader->word_size() == follower->base(),
             "weird buddy address");

      // Calc committed region of the merged chunk. See lengthy comment in header.
      size_t merged_committed_words = 0;
      size_t merged_abandoned_committed_words = 0;
      if (leader->is_fully_committed()) {
        merged_committed_words = leader->_committed_words + follower->_committed_words;
        merged_abandoned_committed_words = follower->_abandoned_committed_words;
      } else {
        merged_committed_words = leader->_committed_words;
        merged_abandoned_committed_words = follower->_committed_words + follower->_abandoned_committed_words;
      }

      Metachunk* const merged = leader;
      merged->_level ++;
      merged->_committed_words = merged_committed_words;
      merged->_abandoned_committed_words = merged_abandoned_committed_words;

      // Mark follower as invalid.
      follower->remove_sentinel();

      DEBUG_ONLY(merged->verify());

      return merged;

    }

  }

  return NULL;

}


#ifdef ASSERT
void Metachunk::mangle(juint word_value) {
  // Overwrite the payload of the chunk and not the links that
  // maintain list of chunks.
  assert(_words_committed >= overhead, "sanity");
  size_t mangle_size = _words_committed - overhead();
  Copy::fill_to_words((HeapWord*)start(), size, word_value);
}

void Metachunk::verify() const {
  assert(is_valid_sentinel(), "Chunk " PTR_FORMAT ": sentinel invalid", p2i(this));
  assert(is_valid_level(_level), "Invalid level (%d)", _level);

  // Starting address shall be aligned to chunk size.
  const size_t required_alignment = word_size() * sizeof(MetaWord);
  assert(is_aligned((address)this, required_alignment),
         "Chunk " PTR_FORMAT ": (size " SIZE_FORMAT ") not aligned correctly to " SIZE_FORMAT ".",
         p2i(this), word_size() * sizeof(MetaWord), required_alignment);

  assert(base() == (MetaWord*) this, "sanity");
  assert(end() == base() + word_size(), "sanity");
  assert(top() >= start() && top() <= end(), "sanity");
  assert(commit_top() >= top() && commit_top() <= end(), "sanity");

  assert(_container != NULL, "sanity");

}
#endif // ASSERT

} // namespace metaspace

