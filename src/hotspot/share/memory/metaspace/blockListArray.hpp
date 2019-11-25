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

#ifndef SHARE_MEMORY_METASPACE_BLOCKLISTARRAY_HPP
#define SHARE_MEMORY_METASPACE_BLOCKLISTARRAY_HPP

#include "memory/allocation.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"


class outputStream;

namespace metaspace {

struct block_t {
  block_t* next;
  size_t size;
};

struct block_stats_t {
  size_t word_size;
  int num_blocks;
};

// A bitmap keeping track of which list is occupied. Smallest list corresponds to lowest order bit.
// 1 means list is not empty, 0 means list is empty.
class BlockListArrayMask {

  typedef uint32_t mask_type;
  mask_type _mask;

  static mask_type mask_for_pos(int pos) { return 1 << pos; }

public:

  BlockListArrayMask() : _mask(0) {}

  bool all_zero() const          { return _mask == 0; }

  bool get_bit(int pos) const    { return (_mask & mask_for_pos(pos)) != 0 ? true : false; }
  void set_bit(int pos)          { _mask |= mask_for_pos(pos); }
  void clr_bit(int pos)          { _mask &= ~mask_for_pos(pos); }

  // Starting at (including) pos, find the position of the next 1 bit.
  // Return -1 if not found.
  inline int find_next_set_bit(int pos) const;

  static int size() { return sizeof(mask_type) * 8; }

};

template <
  size_t min_word_size,
  size_t spread,
  int num_bins
>
class BlockListArray {

  STATIC_ASSERT(sizeof(block_t) <= (min_word_size * BytesPerWord));

  block_t* _bins[num_bins];

  BlockListArrayMask _map;

  // e.g. spread = 4
  //
  // sz    bno (put)  bno (get)
  //         (guarant)
  // 0     00         00
  // 1     00         01
  // 2     00         01
  // 3     00         01
  // 4     01         01
  // 5     01         02
  // 6     01         02
  // 7     01         02
  // 8     02         02
  // 9     02         03
  // 10    02         03
  // 11    02         03
  //
  // put -> no = wordsize / spread
  //
  // get -> no = (req_wordsize + spread - 1) / spread

  // The bin number for a given word size.
  static int bin_for_size(size_t word_size) {
    assert(word_size >= min_word_size && word_size < maximal_word_size(),
           "Word size oob (" SIZE_FORMAT ")", word_size);
    return (word_size - min_word_size) / spread;
  }

  // [minimal, maximal) size of blocks which are held in a bin.
  // Note that when taking a block out of the bin, only the minimum block size
  // is guaranteed.
  static size_t minimal_word_size_in_bin(int bno) {
    return min_word_size + (bno * spread);
  }
  static size_t maximal_word_size_in_bin(int bno) {
    return minimal_word_size_in_bin(bno) + spread;
  }

public:

  BlockListArray() : _map() {
    assert(BlockListArrayMask::size() >= num_bins, "Map too small.");
    ::memset(_bins, 0, sizeof(_bins));
  }

  // [min, max) word size
  static size_t minimal_word_size() { return min_word_size; }
  static size_t maximal_word_size() { return min_word_size + (spread * num_bins); }

  inline void put(MetaWord* p, size_t word_size);

  inline block_t* get(size_t word_size);

#ifdef ASSERT
  void verify() const;
#endif

  void statistics(block_stats_t* stats) const;

  void print(outputStream* st) const;

};


} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_BLOCKLISTARRAY_HPP
