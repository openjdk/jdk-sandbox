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
#include "precompiled.hpp"


#include "memory/metaspace/chunkAllocSequence.hpp"
#include "memory/metaspace/chunkLevel.hpp"
#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

namespace metaspace {

// Creates a chunk manager with a given name (which is for debug purposes only)
// and an associated space list which will be used to request new chunks from
// (see get_chunk())
ChunkManager::ChunkManager(const char* name, VirtualSpaceList* space_list)
  : _vs_list(space_list)
  , _name(name)
  , _chunks {}
  , _num_chunks {}
  , _total_word_size(0)
  , _total_num_chunks(0)
{
}

void ChunkManager::account_for_added_chunk(const Metachunk* c) {
  assert_lock_strong(MetaspaceExpand_lock);

  const chklvl_t lvl = c->level();
  check_valid_level(lvl);

  _total_num_chunks ++;
  _total_word_size += word_size_for_level(lvl);
  _num_chunks[lvl] ++;
}

void ChunkManager::account_for_removed_chunk(const Metachunk* c) {
  assert_lock_strong(MetaspaceExpand_lock);

  const chklvl_t lvl = c->level();
  check_valid_level(lvl);

  assert(_total_num_chunks > 0, "Sanity.");
  _total_num_chunks --;
  const size_t word_size = word_size_for_level(lvl);
  assert(_total_word_size >= word_size, "Sanity.");
  _total_word_size -= word_size_for_level(lvl);
  assert(_num_chunks[lvl] > 0, "Sanity.");
  _num_chunks[lvl] --;
}


// Remove a chunk of the given level from its freelist, and adjust accounting.
// If no chunk of this given level is free, return NULL.
Metachunk* ChunkManager::get_chunk_simple(chklvl_t level) {

  assert_lock_strong(MetaspaceExpand_lock);
  check_valid_level(level);

  Metachunk* c = _chunks[level];
  if (c != NULL) {
    account_for_removed_chunk(c);
  }
  return c;

}




// Return a chunk to the ChunkManager and adjust accounting. May merge chunk
//  with neighbors.
// Happens after a Classloader was unloaded and releases its metaspace chunks.
// !! Note: this may invalidate the chunk. Do not access the chunk after
//    this function returns !!
void ChunkManager::return_chunk(Metachunk* chunk) {


}


// Remove the given chunk from its free list and adjust accounting.
// (Called during VirtualSpaceNode purging which happens during a Metaspace GC.)
void ChunkManager::remove_chunk(Metachunk* chunk) {
  assert_lock_strong(MetaspaceExpand_lock);
  chunk->remove_from_list();
  account_for_removed_chunk(chunk);
}




} // namespace metaspace



