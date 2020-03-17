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

#include "memory/metaspace/counter.hpp"
#include "memory/metaspace/binlist.hpp"
#include "memory/metaspace/blocktree.hpp"

#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"


class outputStream;

namespace metaspace {

// class FreeBlocks is responsible for managing small leftover-
// and deallocated blocks.
// They come from two sources:
// a) the leftover space left in a chunk when a chunk gets retired
//    because it cannot serve a requested allocation. These blocks
//    can be largeish (100s - 1000s of words).
// b) when a metaspace allocation is deallocated prematurely - e.g.
//    due to interrupted class loading. These blocks are small or
//    very small.

class FreeBlocks : public CHeapObj<mtInternal> {

  typedef BinList32 SmallBlocksType;

  SmallBlocksType _small_blocks;
  BlockTree _tree;

  static const size_t splinter_threshold = 0x100;

public:

  const static size_t minimal_word_size = SmallBlocksType::minimal_word_size;

  void add_block(MetaWord* p, size_t word_size);
  MetaWord* get_block(size_t requested_word_size);

#ifdef ASSERT
  void verify() const {
    _tree.verify();
    _small_blocks.verify();
  };
#endif

  // Returns number of blocks.
  int count() const {
    return _small_blocks.count() + _tree.count();
  }

  // Returns total size, in words, of all elements.
  size_t total_size() const {
    return _small_blocks.total_size() + _tree.total_size();
  }

  // Returns true if empty.
  bool is_empty() const {
    return _small_blocks.is_empty() && _tree.is_empty();
  }

};




} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_CHUNKMANAGER_HPP
