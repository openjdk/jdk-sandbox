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

#ifndef SHARE_MEMORY_METASPACE_BLOCKLISTARRAY_INLINE_HPP
#define SHARE_MEMORY_METASPACE_BLOCKLISTARRAY_INLINE_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace/blockListArray.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

namespace metaspace {


// Starting at (including) pos, find the position of the next 1 bit.
// Return -1 if not found.
int BlockListArrayMask::find_next_set_bit(int pos) const {

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
void BlockListArray<min_word_size, spread, num_bins>::put(MetaWord* p, size_t word_size) {
  assert(word_size >= minimal_word_size() && word_size < maximal_word_size(), "Invalid word size");
  block_t* b = (block_t*)p;
  int bno = bin_for_size(word_size);
  assert(bno >= 0 && bno < num_bins, "Sanity");
  assert(b != _bins[bno], "double add?");
  b->next = _bins[bno];
  b->size = word_size;
  _bins[bno] = b;
  _map.set_bit(bno);
}

template <size_t min_word_size, size_t spread, int num_bins>
block_t* BlockListArray<min_word_size, spread, num_bins>::get(size_t word_size) {
  // Adjust size for spread (we need the bin number which guarantees word_size).
  word_size += (spread - 1);
  if (word_size >= maximal_word_size()) {
    return NULL;
  }
  int bno = bin_for_size(word_size);
  bno = _map.find_next_set_bit(bno);
  if (bno != -1) {
    assert(bno >= 0 && bno < num_bins, "Sanity");
    assert(_bins[bno] != NULL, "Sanity");
    block_t* b = _bins[bno];
    _bins[bno] = b->next;
    if (_bins[bno] == NULL) {
      _map.clr_bit(bno);
    }
    return b;
  }
  return NULL;
}

#ifdef ASSERT
template <size_t min_word_size, size_t spread, int num_bins>
void BlockListArray<min_word_size, spread, num_bins>::verify() const {
  for (int i = 0; i < num_bins; i ++) {
    assert(_map.get_bit(i) == (_bins[i] != NULL), "Sanity");
    const size_t min_size = minimal_word_size_in_bin(i);
    const size_t max_size = maximal_word_size_in_bin(i);
    for(block_t* b = _bins[i]; b != NULL; b = b->next) {
      assert(b->size >= min_size && b->size < max_size, "Sanity");
    }
  }
}
#endif // ASSERT


template <size_t min_word_size, size_t spread, int num_bins>
void BlockListArray<min_word_size, spread, num_bins>::statistics(block_stats_t* stats) const {
  for (int i = 0; i < num_bins; i ++) {
    for(block_t* b = _bins[i]; b != NULL; b = b->next) {
      stats->num_blocks ++;
      stats->word_size += b->size;
    }
  }
}

template <size_t min_word_size, size_t spread, int num_bins>
void BlockListArray<min_word_size, spread, num_bins>::print(outputStream* st) const {
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

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_BLOCKLISTARRAY_INLINE_HPP
