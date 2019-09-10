/*
 * Copyright (c) 2019 SAP SE. All rights reserved.
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_CONSTANTS_HPP
#define SHARE_MEMORY_METASPACE_CONSTANTS_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace/chunkLevel.hpp"
#include "utilities/globalDefinitions.hpp"

namespace metaspace {

class Settings : public AllStatic {

  // Granularity, in bytes, metaspace is committed with.
  static size_t _commit_granule_bytes;

  // Granularity, in words, metaspace is committed with.
  static size_t _commit_granule_words;

  // Whether or not commit new-born root chunks thru after creation.
  static bool _newborn_root_chunks_are_fully_committed;

  // When a chunk is handed out by the ChunkManager to a class loader, how much
  // of a chunk should be committed up-front?
  // Size in words. Will be rounded up to the nearest multiple of commit_granule_words.
  // (Note: 0 is possible but inefficient, since it will cause the ClassLoaderMetaspace
  //        to commit the first granule right away anyway, so nothing is saved.
  //        chklvl::MAX_CHUNK_WORD_SIZE pretty much means every chunk is committed thru
  //        from the start.
  static size_t _committed_words_on_fresh_chunks;

  // The default size of a non-class VirtualSpaceNode (unless created differently).
  // Must be a multiple of the root chunk size.
  static const size_t _virtual_space_node_default_word_size = chklvl::MAX_CHUNK_WORD_SIZE * 2; // lets go with 8mb virt size. Seems a good compromise betw. virt and mapping fragmentation.

  static const size_t _allocation_from_dictionary_limit = 4 * K;

  // When allocating from a chunk, if the remaining area in the chunk is too small to hold
  // the requested size, we attempt to double the chunk size in place...
  static bool   _enlarge_chunks_in_place;

  // .. but we do only do this for chunks below a given size to prevent unnecessary memory blowup.
  static size_t _enlarge_chunks_in_place_max_word_size;

  // If true, chunks are uncommitted after gc (when metaspace is purged).
  static bool _uncommit_on_return;

  // If true, vsnodes which only contain free chunks will be deleted (purged) as part of a gc.
  static bool _delete_nodes_on_purge;

  // If _uncommit_on_return is true:
  // Minimum word size a chunk has to have after returning and merging with neighboring free chunks
  // to be candidate for uncommitting. Must be a multiple of and not smaller than commit granularity.
  static size_t _uncommit_on_return_min_word_size;

  // If true, chunks are uncommitted after gc (when metaspace is purged).
  static bool _uncommit_on_purge;

  // If _uncommit_on_purge is true:
  // Minimum word size of an area to be candidate for uncommitting.
  // Must be a multiple of and not smaller than commit granularity.
  static size_t _uncommit_on_purge_min_word_size;

public:

  static size_t commit_granule_bytes()                        { return _commit_granule_bytes; }
  static size_t commit_granule_words()                        { return _commit_granule_words; }
  static bool newborn_root_chunks_are_fully_committed()       { return _newborn_root_chunks_are_fully_committed; }
  static size_t committed_words_on_fresh_chunks()             { return _committed_words_on_fresh_chunks; }
  static size_t virtual_space_node_default_word_size()        { return _virtual_space_node_default_word_size; }
  static size_t allocation_from_dictionary_limit()            { return _allocation_from_dictionary_limit; }
  static bool enlarge_chunks_in_place()                       { return _enlarge_chunks_in_place; }
  static size_t enlarge_chunks_in_place_max_word_size()       { return _enlarge_chunks_in_place_max_word_size; }
  static bool uncommit_on_return()                            { return _uncommit_on_return; }
  static size_t uncommit_on_return_min_word_size()            { return _uncommit_on_return_min_word_size; }
  static bool delete_nodes_on_purge()                         { return _delete_nodes_on_purge; }
  static bool uncommit_on_purge()                             { return _uncommit_on_purge; }
  static size_t uncommit_on_purge_min_word_size()             { return _uncommit_on_purge_min_word_size; }

  // Describes a group of settings
  enum strategy_t {

    // Do not uncommit chunks. New chunks are completely committed thru from the start.
    strategy_no_reclaim,

    // Uncommit very aggressively.
    // - a rather small granule size of 16K
    // - New chunks are committed for one granule size
    // - returned chunks are uncommitted whenever possible
    strategy_aggressive_reclaim,

    // Uncommit, but try to strike a balance with CPU load
    strategy_balanced_reclaim

  };

  static void initialize(strategy_t theme);

  static void print_on(outputStream* st);

};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_BLOCKFREELIST_HPP
