/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_VIRTUALSPACENODE_HPP
#define SHARE_MEMORY_METASPACE_VIRTUALSPACENODE_HPP

#include "memory/virtualspace.hpp"
#include "memory/memRegion.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class outputStream;

namespace metaspace {

class Metachunk;



// A node in the VirtualSpaceList.
//
// VirtualSpaceNodes manage a single address range and a commit high water mark.
//
// The address range start and end are aligned to the highest Metachunk size (root chunk size)
// and memory is only ever requested in units of one root chunk size.
//
class VirtualSpaceNode : public CHeapObj<mtClass> {

  /////////////////////////////////
  //
  //  |---------------------     end()
  //  |
  //  |  (uncommitted)                  = words_uncommitted()
  //  |
  //  |---------------------     commit_top()
  //  |
  //  |  (committed unused)             = words_committed_unused()
  //  |
  //  |---------------------     top()
  //  |
  //  |  (committed used)               = words_used()
  //  |
  //  |
  //  |
  //  |--------------------      start()
  //

  // Link to next VirtualSpaceNode
  VirtualSpaceNode* _next;

  ReservedSpace _rs;
  VirtualSpace _virtual_space;

  // Current allocated words
  size_t _used_words;

  // Current committed words
  size_t _committed_words;

  VirtualSpace* virtual_space() const { return _virtual_space; }

  MetaWord* start() const {
    assert(virtual_space()->low() == virtual_space()->low_boundary(), "sanity");
    return (MetaWord*) virtual_space()->low_boundary();
  }

  // End of the used area.
  MetaWord* top() const { return start() + _used_words; }

  // End of the committed area.
  MetaWord* commit_top() const {
    assert(virtual_space()->high() == start() + _committed_words, "sanity");
    return start() + _committed_words;
  }

  // End of the reserved space; highest address in this node.
  MetaWord* end() const { return virtual_space()->high_boundary(); }

public:

  // Create a new empty node of the given size. Memory will be reserved but
  // completely uncommitted.
  VirtualSpaceNode(size_t wordsize);

  // Create a new empty node spanning the given reserved space.
  VirtualSpaceNode(ReservedSpace rs);

  // Releases the node memory.
  ~VirtualSpaceNode();

  // Allocate a root chunk from this node. Will fail and return NULL
  // if the node is full.
  Metachunk* allocate_root_chunk();

  // Returns true if this node can be purged (all chunks are free).
  bool can_be_purged() const;

  // Purge this node: remove all the chunks in the node from the given chunk manager.
  // Assumption: all chunks are free (see can_be_purged()).
  void purge(ChunkManager* chunk_manager);

  // Verify counters and basic structure. Slow mode: verify all chunks in depth
  DEBUG_ONLY(void verify(bool slow);)

};


} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_VIRTUALSPACENODE_HPP
