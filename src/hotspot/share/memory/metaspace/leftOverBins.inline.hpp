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
#include "memory/metaspace/leftOverBins.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

namespace metaspace {


// Starting at (including) pos, find the position of the next 1 bit.
// Return -1 if not found.
int BinMap::find_next_set_bit(int pos) const {
  if (get_bit(pos)) {
    return pos;
  }
  mask_type m2 = _mask;
  int pos2 = pos + 1;
  m2 >>= pos2;
  if (m2 > 0) {
    while ((m2 & (mask_type)1) == 0) {
      m2 >>= 1;
      pos2 ++;
    }
    return pos2;
  }
  return -1;
}

///////////////////////////////////////

template <size_t min_word_size, size_t spread, int num_bins>
void Bins<min_word_size, spread, num_bins>::put(MetaWord* p, size_t word_size) {
  assert(word_size >= minimal_word_size() && word_size < maximal_word_size(), "Invalid word size");
  block_t* b = (block_t*)p;
  int bno = bin_for_size(word_size);
  assert(bno >= 0 && bno < num_bins, "Sanity");
  assert(b != _bins[bno], "double add?");
  b->next = _bins[bno];
  b->size = word_size;
  _bins[bno] = b;
  _mask.set_bit(bno);
}

template <size_t min_word_size, size_t spread, int num_bins>
block_t* Bins<min_word_size, spread, num_bins>::get(size_t word_size) {
  // Adjust size for spread (we need the bin number which guarantees word_size).
  word_size += (spread - 1);
  if (word_size >= maximal_word_size()) {
    return NULL;
  }
  int bno = bin_for_size(word_size);
  bno = _mask.find_next_set_bit(bno);
  if (bno != -1) {
    assert(bno >= 0 && bno < num_bins, "Sanity");
    assert(_bins[bno] != NULL, "Sanity");
    block_t* b = _bins[bno];
    _bins[bno] = b->next;
    if (_bins[bno] == NULL) {
      _mask.clr_bit(bno);
    }
    return b;
  }
  return NULL;
}

#ifdef ASSERT
template <size_t min_word_size, size_t spread, int num_bins>
void Bins<min_word_size, spread, num_bins>::verify() const {
  for (int i = 0; i < num_bins; i ++) {
    assert(_mask.get_bit(i) == (_bins[i] != NULL), "Sanity");
    const size_t min_size = minimal_word_size_in_bin(i);
    const size_t max_size = maximal_word_size_in_bin(i);
    for(block_t* b = _bins[i]; b != NULL; b = b->next) {
      assert(b->size >= min_size && b->size < max_size, "Sanity");
    }
  }
}
#endif // ASSERT


template <size_t min_word_size, size_t spread, int num_bins>
void Bins<min_word_size, spread, num_bins>::statistics(block_stats_t* stats) const {
  for (int i = 0; i < num_bins; i ++) {
    for(block_t* b = _bins[i]; b != NULL; b = b->next) {
      stats->num_blocks ++;
      stats->word_size += b->size;
    }
  }
}

template <size_t min_word_size, size_t spread, int num_bins>
void Bins<min_word_size, spread, num_bins>::print(outputStream* st) const {
  bool first = true;
  for (int i = 0; i < num_bins; i ++) {
    int n = 0;
    for(block_t* b = _bins[i]; b != NULL; b = b->next) {
      n ++;
    }
    if (n > 0) {
      if (!first) {
        st->print(", ");
      } else {
        first = false;
      }
      st->print(SIZE_FORMAT "=%d", minimal_word_size_in_bin(i), n);
    }
  }
}



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
