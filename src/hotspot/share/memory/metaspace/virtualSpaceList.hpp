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

#ifndef SHARE_MEMORY_METASPACE_VIRTUALSPACELIST_HPP
#define SHARE_MEMORY_METASPACE_VIRTUALSPACELIST_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace/virtualSpaceNode.hpp"
#include "utilities/globalDefinitions.hpp"


namespace metaspace {

class Metachunk;

class VirtualSpaceList : public public CHeapObj<mtClass> {

  // Name
  const char* const _name;

  // Head of the list.
  VirtualSpaceNode* _nodes;

  // Node currently being used for allocations.
  VirtualSpaceNode* _current_node;

  // Whether this list can expand by allocating new nodes.
  const bool _can_expand;

  // Statistics:

  // Sum of reserved and committed words in all nodes
  size_t _reserved_words;
  size_t _committed_words;

  // Number of virtual spaces
  int _count;

public:

  // Create a new, empty, expandable list.
  VirtualSpaceList(const char* name);

  // Create a new list. The list will contain one node, which uses the given ReservedSpace.
  // It will be not expandable beyond that first node.
  VirtualSpaceList(const char* name, ReservedSpace* rs);

  ~VirtualSpaceList();

  // Allocate a root chunk from the current node in this list.
  // - If the node is full and the list is expandable, a new node may be created.
  // - This may fail if we either hit the GC threshold or the metaspace limits.
  // Returns NULL if it failed.
  Metachunk* allocate_root_chunk();

  // Remove all nodes which only contain empty chunks from the list,
  // remove the chunks from the ChunkManager, and unmap those nodes.
  void purge();

  DEBUG_ONLY(void verify(bool slow);)

};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_VIRTUALSPACELIST_HPP
