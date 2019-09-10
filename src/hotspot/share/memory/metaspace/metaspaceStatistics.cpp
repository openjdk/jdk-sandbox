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
#include "precompiled.hpp"


#include "memory/metaspace/chunkLevel.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/metaspaceStatistics.hpp"

#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

namespace metaspace {

// ChunkManagerStatistics methods

// Returns total word size of all chunks in this manager.
void cm_stats_t::add(const cm_stats_t& other) {
  for (chklvl_t l = chklvl::LOWEST_CHUNK_LEVEL; l <= chklvl::HIGHEST_CHUNK_LEVEL; l ++) {
    num_chunks[l] += other.num_chunks[l];
    committed_word_size[l] += other.committed_word_size[l];
  }
}

// Returns total word size of all chunks in this manager.
size_t cm_stats_t::total_word_size() const {
  size_t s = 0;
  for (chklvl_t l = chklvl::LOWEST_CHUNK_LEVEL; l <= chklvl::HIGHEST_CHUNK_LEVEL; l ++) {
    s += num_chunks[l] * chklvl::word_size_for_level(l);
  }
  return s;
}

// Returns total committed word size of all chunks in this manager.
size_t cm_stats_t::total_committed_word_size() const {
  size_t s = 0;
  for (chklvl_t l = chklvl::LOWEST_CHUNK_LEVEL; l <= chklvl::HIGHEST_CHUNK_LEVEL; l ++) {
    s += committed_word_size[l];
  }
  return s;
}


void cm_stats_t::print_on(outputStream* st, size_t scale) const {
  // Note: used as part of MetaspaceReport so formatting matters.
  size_t total_size = 0;
  size_t total_committed_size = 0;
  for (chklvl_t l = chklvl::LOWEST_CHUNK_LEVEL; l <= chklvl::HIGHEST_CHUNK_LEVEL; l ++) {
    st->cr();
    chklvl::print_chunk_size(st, l);
    st->print(": ");
    if (num_chunks[l] > 0) {
      const size_t word_size = num_chunks[l] * chklvl::word_size_for_level(l);

      st->print("%4d, capacity=", num_chunks[l]);
      print_scaled_words(st, word_size, scale);

      st->print(", committed=");
      print_scaled_words_and_percentage(st, committed_word_size[l], word_size, scale);

      total_size += word_size;
      total_committed_size += committed_word_size[l];
    } else {
      st->print("(none)");
    }
  }
  st->cr();
  st->print("Total word size: ");
  print_scaled_words(st, total_size, scale);
  st->print(", committed: ");
  print_scaled_words_and_percentage(st, total_committed_size, total_size, scale);
  st->cr();
}

#ifdef ASSERT
void cm_stats_t::verify() const {
  assert(total_committed_word_size() <= total_word_size(),
         "Sanity");
}
#endif

// UsedChunksStatistics methods

void in_use_chunk_stats_t::print_on(outputStream* st, size_t scale) const {
  int col = st->position();
  st->print("%4d chunk%s, ", num, num != 1 ? "s" : "");
  if (num > 0) {
    col += 14; st->fill_to(col);

    print_scaled_words(st, word_size, scale, 5);
    st->print(" capacity,");

    col += 20; st->fill_to(col);
    print_scaled_words_and_percentage(st, committed_words, word_size, scale, 5);
    st->print(" committed, ");

    col += 18; st->fill_to(col);
    print_scaled_words_and_percentage(st, used_words, word_size, scale, 5);
    st->print(" used, ");

    col += 20; st->fill_to(col);
    print_scaled_words_and_percentage(st, free_words, word_size, scale, 5);
    st->print(" free, ");

    col += 20; st->fill_to(col);
    print_scaled_words_and_percentage(st, waste_words, word_size, scale, 5);
    st->print(" waste ");

  }
}

#ifdef ASSERT
void in_use_chunk_stats_t::verify() const {
  assert(word_size >= committed_words &&
      committed_words == used_words + free_words + waste_words,
         "Sanity: cap " SIZE_FORMAT ", committed " SIZE_FORMAT ", used " SIZE_FORMAT ", free " SIZE_FORMAT ", waste " SIZE_FORMAT ".",
         word_size, committed_words, used_words, free_words, waste_words);
}
#endif

// SpaceManagerStatistics methods

void sm_stats_t::add(const sm_stats_t& other) {
  for (chklvl_t l = chklvl::LOWEST_CHUNK_LEVEL; l <= chklvl::HIGHEST_CHUNK_LEVEL; l ++) {
    stats[l].add(other.stats[l]);
  }
  free_blocks_num += other.free_blocks_num;
  free_blocks_word_size += other.free_blocks_word_size;
}


// Returns total chunk statistics over all chunk types.
in_use_chunk_stats_t sm_stats_t::totals() const {
  in_use_chunk_stats_t out;
  for (chklvl_t l = chklvl::LOWEST_CHUNK_LEVEL; l <= chklvl::HIGHEST_CHUNK_LEVEL; l ++) {
    out.add(stats[l]);
  }
  return out;
}

void sm_stats_t::print_on(outputStream* st, size_t scale,  bool detailed) const {
  streamIndentor sti(st);
  if (detailed) {
    st->cr_indent();
    st->print("Usage by chunk level:");
    {
      streamIndentor sti2(st);
      for (chklvl_t l = chklvl::LOWEST_CHUNK_LEVEL; l <= chklvl::HIGHEST_CHUNK_LEVEL; l ++) {
        st->cr_indent();
        chklvl::print_chunk_size(st, l);
        st->print(" chunks: ");
        if (stats[l].num == 0) {
          st->print(" (none)");
        } else {
          stats[l].print_on(st, scale);
        }
      }

      st->cr_indent();
      st->print("%15s: ", "-total-");
      totals().print_on(st, scale);
    }
    if (free_blocks_num > 0) {
      st->cr_indent();
      st->print("deallocated: " UINTX_FORMAT " blocks with ", free_blocks_num);
      print_scaled_words(st, free_blocks_word_size, scale);
    }
  } else {
    totals().print_on(st, scale);
    st->print(", ");
    st->print("deallocated: " UINTX_FORMAT " blocks with ", free_blocks_num);
    print_scaled_words(st, free_blocks_word_size, scale);
  }
}

#ifdef ASSERT

void sm_stats_t::verify() const {
  size_t total_used = 0;
  for (chklvl_t l = chklvl::LOWEST_CHUNK_LEVEL; l <= chklvl::HIGHEST_CHUNK_LEVEL; l ++) {
    stats[l].verify();
    total_used += stats[l].used_words;
  }
  // Deallocated allocations still count as used
  assert(total_used >= free_blocks_word_size,
         "Sanity");
}
#endif

// ClassLoaderMetaspaceStatistics methods

// Returns total space manager statistics for both class and non-class metaspace
sm_stats_t clms_stats_t::totals() const {
  sm_stats_t out;
  out.add(sm_stats_nonclass);
  out.add(sm_stats_class);
  return out;
}

void clms_stats_t::print_on(outputStream* st, size_t scale, bool detailed) const {
  streamIndentor sti(st);
  st->cr_indent();
  if (Metaspace::using_class_space()) {
    st->print("Non-Class: ");
  }
  sm_stats_nonclass.print_on(st, scale, detailed);
  if (detailed) {
    st->cr();
  }
  if (Metaspace::using_class_space()) {
    st->cr_indent();
    st->print("    Class: ");
    sm_stats_class.print_on(st, scale, detailed);
    if (detailed) {
      st->cr();
    }
    st->cr_indent();
    st->print("     Both: ");
    totals().print_on(st, scale, detailed);
    if (detailed) {
      st->cr();
    }
  }
  st->cr();
}


#ifdef ASSERT
void clms_stats_t::verify() const {
  sm_stats_nonclass.verify();
  sm_stats_class.verify();
}
#endif

} // end namespace metaspace



