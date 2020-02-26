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

#ifndef SHARE_MEMORY_METASPACE_LEFTOVERBINS_HPP
#define SHARE_MEMORY_METASPACE_LEFTOVERBINS_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace/blockListArray.hpp"
#include "memory/metaspace/counter.hpp"
#include "utilities/bitMap.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"


class outputStream;

namespace metaspace {

// The LeftOverManager is responsible for managing small leftover-
// and deallocated blocks.
// They come from two sources:
// a) the leftover space left in a chunk when a chunk gets retired
//    because it cannot serve a requested allocation. These blocks
//    can be largeish (100s - 1000s of words).
// b) when a metaspace allocation is deallocated prematurely - e.g.
//    due to interrupted class loading. These blocks are small or
//    very small.

class LeftOverManager : public CHeapObj<mtInternal> {

  typedef BlockListArray<2, 2, 16> VerySmallBinsType;
  VerySmallBinsType _very_small_bins;

  block_t* _large_block_reserve;

  // The current large block we gnaw on
  MetaWord* _current;
  size_t _current_size;

  SizeCounter _total_word_size;

  // Take the topmost block from the large block reserve list
  // and make it current.
  inline void prime_current();

  // Allocate from current block. Returns NULL if current block
  // is too small.
  inline MetaWord* alloc_from_current(size_t word_size);

  void large_block_statistics(block_stats_t* stats) const;

public:

  // Returns smallest size, in words, a block has to have
  // to be managed by the LeftOverManager
  static size_t minimal_word_size() {
    return VerySmallBinsType::minimal_word_size();
  }

  LeftOverManager() :
    _very_small_bins(),
    _large_block_reserve(NULL),
    _current(NULL),
    _current_size(0)
  {}

  inline void add_block(MetaWord* p, size_t word_size);

  inline MetaWord* get_block(size_t requested_word_size);

#ifdef ASSERT
  void verify() const;
#endif

  void statistics(block_stats_t* stats) const;

  void print(outputStream* st, bool detailed = false) const;

  bool is_empty() const {
    return _very_small_bins.is_empty() && _current == NULL;
  }

  size_t total_word_size() const { return _total_word_size.get(); }

};




} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_CHUNKMANAGER_HPP
