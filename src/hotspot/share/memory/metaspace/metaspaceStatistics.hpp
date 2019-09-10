/*
 * Copyright (c) 2018, 2019 SAP SE. All rights reserved.
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_METASPACESTATISTICS_HPP
#define SHARE_MEMORY_METASPACE_METASPACESTATISTICS_HPP

#include "memory/metaspace.hpp"             // for MetadataType enum
#include "memory/metaspace/chunkLevel.hpp"
#include "utilities/globalDefinitions.hpp"

class outputStream;

namespace metaspace {


// Contains statistics for one or multiple ChunkManager.
struct cm_stats_t {

  // How many chunks per level are checked in.
  int num_chunks[chklvl::NUM_CHUNK_LEVELS];

  // Size, in words, of the sum of all committed areas in this chunk manager, per level.
  size_t committed_word_size[chklvl::NUM_CHUNK_LEVELS];

  cm_stats_t() : num_chunks(), committed_word_size() {}

  void add(const cm_stats_t& other);

  // Returns total word size of all chunks in this manager.
  size_t total_word_size() const;

  // Returns total committed word size of all chunks in this manager.
  size_t total_committed_word_size() const;

  void print_on(outputStream* st, size_t scale) const;

  DEBUG_ONLY(void verify() const;)

}; // ChunkManagerStatistics

// Contains statistics for one or multiple chunks in use.
struct in_use_chunk_stats_t {

  // Number of chunks
  int num;

  // Note:
  // capacity = committed + uncommitted
  //            committed = used + free + waste

  // Capacity (total sum of all chunk sizes) in words.
  // May contain committed and uncommitted space.
  size_t word_size;

  // Total committed area, in words.
  size_t committed_words;

  // Total used area, in words.
  size_t used_words;

  // Total free committed area, in words.
  size_t free_words;

  // Total waste committed area, in words.
  size_t waste_words;

  in_use_chunk_stats_t()
    : num(0), word_size(0), committed_words(0),
      used_words(0), free_words(0), waste_words(0)
  {}

  void add(const in_use_chunk_stats_t& other) {
    num += other.num;
    word_size += other.word_size;
    committed_words += other.committed_words;
    used_words += other.used_words;
    free_words += other.free_words;
    waste_words += other.waste_words;

  }

  void print_on(outputStream* st, size_t scale) const;

  DEBUG_ONLY(void verify() const;)

}; // UsedChunksStatistics

// Class containing statistics for one or more space managers.
struct  sm_stats_t {

  // chunk statistics by chunk level
  in_use_chunk_stats_t stats[chklvl::NUM_CHUNK_LEVELS];
  uintx free_blocks_num;
  size_t free_blocks_word_size;

  sm_stats_t()
    : stats(),
      free_blocks_num(0),
      free_blocks_word_size(0)
  {}

  void add(const sm_stats_t& other);

  void print_on(outputStream* st, size_t scale = K,  bool detailed = true) const;

  in_use_chunk_stats_t totals() const;

  DEBUG_ONLY(void verify() const;)

}; // SpaceManagerStatistics

// Statistics for one or multiple ClassLoaderMetaspace objects
struct clms_stats_t {

  sm_stats_t sm_stats_nonclass;
  sm_stats_t sm_stats_class;

  clms_stats_t() : sm_stats_nonclass(), sm_stats_class() {}

  void add(const clms_stats_t& other) {
    sm_stats_nonclass.add(other.sm_stats_nonclass);
    sm_stats_class.add(other.sm_stats_class);
  }

  void print_on(outputStream* st, size_t scale, bool detailed) const;

  // Returns total space manager statistics for both class and non-class metaspace
  sm_stats_t totals() const;


  DEBUG_ONLY(void verify() const;)

}; // ClassLoaderMetaspaceStatistics

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_METASPACESTATISTICS_HPP

