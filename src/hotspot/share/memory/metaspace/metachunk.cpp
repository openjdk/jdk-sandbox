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
#include "memory/metaspace/chunkLevel.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/metaDebug.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/virtualSpaceNode.hpp"
#include "runtime/mutexLocker.hpp"

#include "utilities/align.hpp"
#include "utilities/copy.hpp"
#include "utilities/debug.hpp"

namespace metaspace {

// Make sure that the Klass alignment also agree.
STATIC_ASSERT(Metachunk::allocation_alignment_bytes == (size_t)KlassAlignmentInBytes);

// Return a single char presentation of the state ('f', 'u', 'd')
char Metachunk::get_state_char() const {
  switch (_state) {
  case state_free:    return 'f';
  case state_in_use:  return 'u';
  case state_dead:    return 'd';
  }
  return '?';
}

#ifdef ASSERT
void Metachunk::assert_have_expand_lock() {
  assert_lock_strong(MetaspaceExpand_lock);
}
#endif

// Commit uncommitted section of the chunk.
// Fails if we hit a commit limit.
bool Metachunk::commit_up_to(size_t new_committed_words) {

  // Please note:
  //
  // VirtualSpaceNode::ensure_range_is_committed(), when called over a range containing both committed and uncommitted parts,
  // will replace the whole range with a new mapping, thus erasing the existing content in the committed parts. Therefore
  // we must make sure never to call VirtualSpaceNode::ensure_range_is_committed() over a range containing live data.
  //
  // Luckily, this cannot happen by design. We have two cases:
  //
  // 1) chunks equal or larger than a commit granule.
  //    In this case, due to chunk geometry, the chunk should cover whole commit granules (in other words, a chunk equal or larger than
  //    a commit granule will never share a granule with a neighbor). That means whatever we commit or uncommit here does not affect
  //    neighboring chunks. We only have to take care not to re-commit used parts of ourself. We do this by moving the committed_words
  //    limit in multiple of commit granules.
  //
  // 2) chunks smaller than a commit granule.
  //    In this case, a chunk shares a single commit granule with its neighbors. But this never can be a problem:
  //    - Either the commit granule is already committed (and maybe the neighbors contain live data). In that case calling
  //      ensure_range_is_committed() will do nothing.
  //    - Or the commit granule is not committed, but in this case, the neighbors are uncommitted too and cannot contain live data.

#ifdef ASSERT
  if (word_size() >= Settings::commit_granule_words()) {
    // case (1)
    assert(is_aligned(base(), Settings::commit_granule_bytes()) &&
           is_aligned(end(), Settings::commit_granule_bytes()),
           "Chunks larger than a commit granule must cover whole granules.");
    assert(is_aligned(_committed_words, Settings::commit_granule_words()),
           "The commit boundary must be aligned to commit granule size");
    assert(_used_words <= _committed_words, "Sanity");
  } else {
    // case (2)
    assert(_committed_words == 0 || _committed_words == word_size(), "Sanity");
  }
#endif

  // We should hold the expand lock at this point.
  assert_lock_strong(MetaspaceExpand_lock);

  const size_t commit_from = _committed_words;
  const size_t commit_to =   MIN2(align_up(new_committed_words, Settings::commit_granule_words()), word_size());

  assert(commit_from >= used_words(), "Sanity");
  assert(commit_to <= word_size(), "Sanity");

  if (commit_to > commit_from) {
    log_debug(metaspace)("Chunk " METACHUNK_FORMAT ": attempting to move commit line to "
                         SIZE_FORMAT " words.", METACHUNK_FORMAT_ARGS(this), commit_to);

    if (!_vsnode->ensure_range_is_committed(base() + commit_from, commit_to - commit_from)) {
      DEBUG_ONLY(verify(true);)
      return false;
    }
  }

  // Remember how far we have committed.
  _committed_words = commit_to;

  DEBUG_ONLY(verify(true);)

  return true;

}


// Ensure that chunk is committed up to at least new_committed_words words.
// Fails if we hit a commit limit.
bool Metachunk::ensure_committed(size_t new_committed_words) {

  bool rc = true;

  if (new_committed_words > committed_words()) {
    MutexLocker cl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
    rc = commit_up_to(new_committed_words);
  }

  return rc;

}

bool Metachunk::ensure_committed_locked(size_t new_committed_words) {

  // the .._locked() variant should be called if we own the lock already.
  assert_lock_strong(MetaspaceExpand_lock);

  bool rc = true;

  if (new_committed_words > committed_words()) {
    rc = commit_up_to(new_committed_words);
  }

  return rc;

}

// Uncommit chunk area. The area must be a common multiple of the
// commit granule size (in other words, we cannot uncommit chunks smaller than
// a commit granule size).
void Metachunk::uncommit() {
  MutexLocker cl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
  uncommit_locked();
}

void Metachunk::uncommit_locked() {
  // Only uncommit chunks which are free, have no used words set (extra precaution) and are equal or larger in size than a single commit granule.
  assert_lock_strong(MetaspaceExpand_lock);
  assert(_state == state_free && _used_words == 0 && word_size() >= Settings::commit_granule_words(),
         "Only free chunks equal or larger than commit granule size can be uncommitted "
         "(chunk " METACHUNK_FULL_FORMAT ").", METACHUNK_FULL_FORMAT_ARGS(this));
  if (word_size() >= Settings::commit_granule_words()) {
    _vsnode->uncommit_range(base(), word_size());
    _committed_words = 0;
  }
}
void Metachunk::set_committed_words(size_t v) {
  // Set committed words. Since we know that we only commit whole commit granules, we can round up v here.
  v = MIN2(align_up(v, Settings::commit_granule_words()), word_size());
 _committed_words = v;
}

// Allocate word_size words from this chunk.
//
// May cause memory to be committed. That may fail if we hit a commit limit. In that case,
//  NULL is returned and p_did_hit_commit_limit will be set to true.
// If the remainder portion of the chunk was too small to hold the allocation,
//  NULL is returned and p_did_hit_commit_limit will be set to false.
MetaWord* Metachunk::allocate(size_t request_word_size, bool* p_did_hit_commit_limit) {

  assert_is_aligned(request_word_size, allocation_alignment_words);

  log_trace(metaspace)("Chunk " METACHUNK_FULL_FORMAT ": allocating " SIZE_FORMAT " words.",
                       METACHUNK_FULL_FORMAT_ARGS(this), request_word_size);

  assert(committed_words() <= word_size(), "Sanity");

  if (free_below_committed_words() < request_word_size) {

    // We may need to expand the comitted area...
    if (free_words() < request_word_size) {
      // ... but cannot do this since we ran out of space.
      *p_did_hit_commit_limit = false;
      log_trace(metaspace)("Chunk " METACHUNK_FULL_FORMAT ": .. does not fit (remaining space: "
                           SIZE_FORMAT " words).", METACHUNK_FULL_FORMAT_ARGS(this), free_words());
      return NULL;
    }

    log_trace(metaspace)("Chunk " METACHUNK_FULL_FORMAT ": .. attempting to increase committed range.",
                         METACHUNK_FULL_FORMAT_ARGS(this));

    if (ensure_committed(used_words() + request_word_size) == false) {

      // Commit failed. We may have hit the commit limit or the gc threshold.
      *p_did_hit_commit_limit = true;
      log_trace(metaspace)("Chunk " METACHUNK_FULL_FORMAT ": .. failed, we hit a limit.",
                           METACHUNK_FULL_FORMAT_ARGS(this));
      return NULL;

    }

  }

  assert(committed_words() >= request_word_size, "Sanity");

  MetaWord* const p = top();

  _used_words += request_word_size;

  SOMETIMES(verify(false);)

  return p;

}

// Given a memory range which may or may not have been allocated from this chunk, attempt
// to roll its allocation back. This can work if this is the very last allocation we did
// from this chunk, in which case we just lower the top pointer again.
// Returns true if this succeeded, false if it failed.
bool Metachunk::attempt_rollback_allocation(MetaWord* p, size_t word_size) {
  assert(p != NULL && word_size > 0, "Sanity");
  assert(is_in_use() && base() != NULL, "Sanity");

  // Is this allocation at the top?
  if (used_words() >= word_size &&
      base() + (used_words() - word_size) == p) {
    log_trace(metaspace)("Chunk " METACHUNK_FULL_FORMAT ": rolling back allocation...",
                         METACHUNK_FULL_FORMAT_ARGS(this));
    _used_words -= word_size;
    log_trace(metaspace)("Chunk " METACHUNK_FULL_FORMAT ": rolled back allocation.",
                         METACHUNK_FULL_FORMAT_ARGS(this));
    DEBUG_ONLY(verify(false);)

    return true;

  }

  return false;
}


#ifdef ASSERT

// Zap this structure.
void Metachunk::zap_header(uint8_t c) {
  memset(this, c, sizeof(Metachunk));
}

void Metachunk::fill_with_pattern(MetaWord pattern, size_t word_size) {
  assert(word_size <= committed_words(), "Sanity");
  for (size_t l = 0; l < word_size; l ++) {
    _base[l] = pattern;
  }
}

void Metachunk::check_pattern(MetaWord pattern, size_t word_size) {
  assert(word_size <= committed_words(), "Sanity");
  for (size_t l = 0; l < word_size; l ++) {
    assert(_base[l] == pattern,
           "chunk " METACHUNK_FULL_FORMAT ": pattern change at " PTR_FORMAT ": expected " UINTX_FORMAT " but got " UINTX_FORMAT ".",
           METACHUNK_FULL_FORMAT_ARGS(this), p2i(_base + l), (uintx)pattern, (uintx)_base[l]);
  }
}


// Verifies linking with neighbors in virtual space.
// Can only be done under expand lock protection.
void Metachunk::verify_neighborhood() const {

  assert_lock_strong(MetaspaceExpand_lock);
  assert(!is_dead(), "Do not call on dead chunks.");

  if (is_root_chunk()) {

    // Root chunks are all alone in the world.
    assert(next_in_vs() == NULL || prev_in_vs() == NULL, "Root chunks should have no neighbors");

  } else {

    // Non-root chunks have neighbors, at least one, possibly two.

    assert(next_in_vs() != NULL || prev_in_vs() != NULL,
           "A non-root chunk should have neighbors (chunk @" PTR_FORMAT
           ", base " PTR_FORMAT ", level " CHKLVL_FORMAT ".",
           p2i(this), p2i(base()), level());

    if (prev_in_vs() != NULL) {
      assert(prev_in_vs()->end() == base(),
             "Chunk " METACHUNK_FULL_FORMAT ": should be adjacent to predecessor: " METACHUNK_FULL_FORMAT ".",
             METACHUNK_FULL_FORMAT_ARGS(this), METACHUNK_FULL_FORMAT_ARGS(prev_in_vs()));
      assert(prev_in_vs()->next_in_vs() == this,
             "Chunk " METACHUNK_FULL_FORMAT ": broken link to left neighbor: " METACHUNK_FULL_FORMAT " (" PTR_FORMAT ").",
             METACHUNK_FULL_FORMAT_ARGS(this), METACHUNK_FULL_FORMAT_ARGS(prev_in_vs()), p2i(prev_in_vs()->next_in_vs()));
    }

    if (next_in_vs() != NULL) {
      assert(end() == next_in_vs()->base(),
             "Chunk " METACHUNK_FULL_FORMAT ": should be adjacent to successor: " METACHUNK_FULL_FORMAT ".",
             METACHUNK_FULL_FORMAT_ARGS(this), METACHUNK_FULL_FORMAT_ARGS(next_in_vs()));
      assert(next_in_vs()->prev_in_vs() == this,
             "Chunk " METACHUNK_FULL_FORMAT ": broken link to right neighbor: " METACHUNK_FULL_FORMAT " (" PTR_FORMAT ").",
             METACHUNK_FULL_FORMAT_ARGS(this), METACHUNK_FULL_FORMAT_ARGS(next_in_vs()), p2i(next_in_vs()->prev_in_vs()));
    }

    // One of the neighbors must be the buddy. It can be whole or splintered.

    // The chunk following us or preceeding us may be our buddy or a splintered part of it.
    Metachunk* buddy = is_leader() ? next_in_vs() : prev_in_vs();

    assert(buddy != NULL, "Missing neighbor.");
    assert(!buddy->is_dead(), "Invalid buddy state.");

    // This neighbor is either or buddy (same level) or a splinter of our buddy - hence
    // the level can never be smaller (aka the chunk size cannot be larger).
    assert(buddy->level() >= level(), "Wrong level.");

    if (buddy->level() == level()) {

      // If the buddy is of the same size as us, it is unsplintered.
      assert(buddy->is_leader() == !is_leader(),
             "Only one chunk can be leader in a pair");

      // When direct buddies are neighbors, one or both should be in use, otherwise they should
      // have been merged.

      // But since we call this verification function from internal functions where we are about to merge or just did split,
      // do not test this. We have RootChunkArea::verify_area_is_ideally_merged() for testing that.

      // assert(buddy->is_in_use() || is_in_use(), "incomplete merging?");

      if (is_leader()) {
        assert(buddy->base() == end(), "Sanity");
        assert(is_aligned(base(), word_size() * 2 * BytesPerWord), "Sanity");
      } else {
        assert(buddy->end() == base(), "Sanity");
        assert(is_aligned(buddy->base(), word_size() * 2 * BytesPerWord), "Sanity");
      }

    } else {

      // Buddy, but splintered, and this is a part of it.
      if (is_leader()) {
        assert(buddy->base() == end(), "Sanity");
      } else {
        assert(buddy->end() > (base() - word_size()), "Sanity");
      }

    }
  }
}

volatile MetaWord dummy = 0;

void Metachunk::verify(bool slow) const {

  // Note. This should be called under CLD lock protection.

  // We can verify everything except the _prev_in_vs/_next_in_vs pair.
  // This is because neighbor chunks may be added concurrently, so we cannot rely
  //  on the content of _next_in_vs/_prev_in_vs unless we have the expand lock.

  assert(!is_dead(), "Do not call on dead chunks.");

  // Note: only call this on a life Metachunk.
  chklvl::check_valid_level(level());

  assert(base() != NULL, "No base ptr");

  assert(committed_words() >= used_words(),
         "mismatch: committed: " SIZE_FORMAT ", used: " SIZE_FORMAT ".",
         committed_words(), used_words());

  assert(word_size() >= committed_words(),
         "mismatch: word_size: " SIZE_FORMAT ", committed: " SIZE_FORMAT ".",
         word_size(), committed_words());

  // Test base pointer
  assert(base() != NULL, "Base pointer NULL");
  assert(vsnode() != NULL, "No space");
  vsnode()->check_pointer(base());

  // Starting address shall be aligned to chunk size.
  const size_t required_alignment = word_size() * sizeof(MetaWord);
  assert_is_aligned(base(), required_alignment);

  // If slow, test the committed area
  if (slow && _committed_words > 0) {
    for (const MetaWord* p = _base; p < _base + _committed_words; p += os::vm_page_size()) {
      dummy = *p;
    }
    dummy = *(_base + _committed_words - 1);
  }

}
#endif // ASSERT

void Metachunk::print_on(outputStream* st) const {

  // Note: must also work with invalid/random data.
  st->print("Chunk @" PTR_FORMAT ", state %c, base " PTR_FORMAT ", "
            "level " CHKLVL_FORMAT " (" SIZE_FORMAT " words), "
            "used " SIZE_FORMAT " words, committed " SIZE_FORMAT " words.",
            p2i(this), get_state_char(), p2i(base()), level(),
            (chklvl::is_valid_level(level()) ? chklvl::word_size_for_level(level()) : 0),
            used_words(), committed_words());

}

///////////////////////////////////7
// MetachunkList

#ifdef ASSERT

bool MetachunkList::contains(const Metachunk* c) const {
  for (Metachunk* c2 = first(); c2 != NULL; c2 = c2->next()) {
    if (c == c2) {
      return true;
    }
  }
  return false;
}

void MetachunkList::verify() const {
  int num = 0;
  const Metachunk* last_c = NULL;
  for (const Metachunk* c = first(); c != NULL; c = c->next()) {
    num ++;
    assert(c->prev() == last_c,
           "Broken link to predecessor. Chunk " METACHUNK_FULL_FORMAT ".",
           METACHUNK_FULL_FORMAT_ARGS(c));
    c->verify(false);
    last_c = c;
  }
  _num.check(num);
}

#endif // ASSERT

void MetachunkList::print_on(outputStream* st) const {

  if (_num.get() > 0) {
    for (const Metachunk* c = first(); c != NULL; c = c->next()) {
      st->print(" - <");
      c->print_on(st);
      st->print(">");
    }
    st->print(" - total : %d chunks.", _num.get());
  } else {
    st->print("empty");
  }

}

///////////////////////////////////7
// MetachunkListCluster

#ifdef ASSERT

bool MetachunkListCluster::contains(const Metachunk* c) const {
  for (chklvl_t l = chklvl::LOWEST_CHUNK_LEVEL; l <= chklvl::HIGHEST_CHUNK_LEVEL; l ++) {
    if (list_for_level(l)->contains(c)) {
      return true;
    }
  }
  return false;
}

void MetachunkListCluster::verify(bool slow) const {

  int num = 0; size_t word_size = 0;

  for (chklvl_t l = chklvl::LOWEST_CHUNK_LEVEL; l <= chklvl::HIGHEST_CHUNK_LEVEL; l ++) {

    // Check, for each chunk in this list, exclusivity.
    for (const Metachunk* c = first_at_level(l); c != NULL; c = c->next()) {
      assert(c->level() == l, "Chunk in wrong list.");
    }

    // Check each list.
    list_for_level(l)->verify();

    num += list_for_level(l)->size();
    word_size += list_for_level(l)->size() * chklvl::word_size_for_level(l);
  }
  _total_num_chunks.check(num);
  _total_word_size.check(word_size);

}
#endif // ASSERT

void MetachunkListCluster::print_on(outputStream* st) const {

  for (chklvl_t l = chklvl::LOWEST_CHUNK_LEVEL; l <= chklvl::HIGHEST_CHUNK_LEVEL; l ++) {
    st->print("-- List[" CHKLVL_FORMAT "]: ", l);
    list_for_level(l)->print_on(st);
    st->cr();
  }
  st->print_cr("total chunks: %d, total word size: " SIZE_FORMAT ".", _total_num_chunks.get(), _total_word_size.get());

}

} // namespace metaspace

