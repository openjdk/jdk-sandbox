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

#ifndef SHARE_MEMORY_METASPACE_LEFTOVERBINS_INLINE_HPP
#define SHARE_MEMORY_METASPACE_LEFTOVERBINS_INLINE_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace/blockListArray.inline.hpp"
#include "memory/metaspace/leftOverBins.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

namespace metaspace {


///////////////////////////////////////

// Take the topmost block from the large block reserve list
// and make it current.
inline void LeftOverManager::prime_current() {
  if (_large_block_reserve != NULL) {
    _current = (MetaWord*) _large_block_reserve;
    _current_size = _large_block_reserve->size;
    _large_block_reserve = _large_block_reserve->next;
  } else {
    _current = NULL;
    _current_size = 0;
  }
}

// Allocate from current block. Returns NULL if current block
// is too small.
inline MetaWord* LeftOverManager::alloc_from_current(size_t word_size) {
  if (_current_size >= word_size) {
    assert(_current != NULL, "Must be");
    MetaWord* p = _current;
    size_t remaining = _current_size - word_size;
    if (remaining >= _very_small_bins.minimal_word_size()) {
      _current = p + word_size;
      _current_size = remaining;
    } else {
      // completely used up old large block. Proceed to next.
      prime_current();
    }
    return p;
  }
  return NULL;
}

inline void LeftOverManager::add_block(MetaWord* p, size_t word_size) {
  if (word_size >= minimal_word_size()) {
    if (word_size < _very_small_bins.maximal_word_size()) {
      _very_small_bins.put(p, word_size);
    } else {
      if (_current == NULL) {
        assert(_large_block_reserve == NULL, "Should be primed.");
        _current = p;
        _current_size = word_size;
      } else {
        assert(sizeof(block_t) <= word_size * BytesPerWord, "must be");
        block_t* b = (block_t*)p;
        b->size = word_size;
        b->next = _large_block_reserve;
        _large_block_reserve = b;
      }
    }
    _total_word_size.increment_by(word_size);
  }

  DEBUG_ONLY(verify();)

}

inline MetaWord* LeftOverManager::get_block(size_t requested_word_size) {

  requested_word_size = MAX2(requested_word_size, minimal_word_size());

  // First attempt to take from current large block because that is cheap (pointer bump)
  // and efficient (no spread)
  MetaWord* p = alloc_from_current(requested_word_size);
  if (p == NULL && _current_size > 0) {
    // current large block is too small. If it is moth-eaten enough to be put
    // into the small remains bin, do so.
    if (_current_size < _very_small_bins.maximal_word_size()) {
      _very_small_bins.put(_current, _current_size);
      prime_current(); // proceed to next large block.
      // --- and re-attempt - but only once more. If that fails too, we give up.
      p = alloc_from_current(requested_word_size);
    }
  }

  if (p == NULL) {
    // Did not work. Check the small bins.
    if (requested_word_size < _very_small_bins.maximal_word_size()) {
      block_t* b = _very_small_bins.get(requested_word_size);
      if (b != NULL) {
        p = (MetaWord*)b;
        size_t remaining = b->size - requested_word_size;
        if (remaining >= _very_small_bins.minimal_word_size()) {
          MetaWord* q = p + requested_word_size;
          _very_small_bins.put(q, remaining);
        }
      }
    }
  }

  if (p != NULL) {
    _total_word_size.decrement_by(requested_word_size);
    DEBUG_ONLY(verify();)
  }

  return p;

}


} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_CHUNKMANAGER_HPP
