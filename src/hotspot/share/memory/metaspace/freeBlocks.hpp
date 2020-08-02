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

// Class FreeBlocks manages deallocated blocks in Metaspace.
//
// In Metaspace, deallocations are an uncommon occurrence of
// allocated memory blocks which are deallocated prematurely. Normally,
// memory blocks stay allocated as long as the ClassLoaderMetaspace itself
// exists - i.e. typically, until the class loader is unloaded.
//
// However, there are cases when Metaspace memory blocks are deallocated
// prematurely. E.g. when class loading errors happen and half-loaded
// metadata are left over; or when a class is redefined, leaving the
// old bytecode. For details, see Metaspace::deallocate().
//
// All these blocks can be reused, so they are collected. Since these
// blocks are embedded into chunks which are still in use by a very alive
// class loader, we cannot give these blocks to other class loaders; we can
// however collect these blocks at the class loader level and reuse them
// for future allocations from the same class loader.
//
// This is what FreeBlocks does: it manages small memory blocks.
//
// FreeBlocks is optimized toward the typical size and number of deallocated
//  blocks. The vast majority of them (about 90%) are below 16 words in size,
//  but there is a significant portion of memory blocks much larger than that -
//  leftover space from used uu chunks, see MetaspaceArena::retire_current_chunk().
//

class FreeBlocks : public CHeapObj<mtMetaspace> {

  typedef BinList32 SmallBlocksType;

  // _small_blocks takes care of small to very small blocks.
  SmallBlocksType _small_blocks;

  // A BST for larger blocks.
  BlockTree _tree;

  static const size_t splinter_threshold = 0;// 0x100;

public:

  const static size_t minimal_word_size = SmallBlocksType::minimal_word_size;

  // Add a block to the deallocation management.
  void add_block(MetaWord* p, size_t word_size);

  // Retrieve a block of at least requested_word_size.
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
