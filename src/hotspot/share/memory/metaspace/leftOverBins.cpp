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

#include "precompiled.hpp"
#include "memory/metaspace/leftOverBins.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"


namespace metaspace {



#ifdef ASSERT
void LeftOverManager::verify() const {
  _very_small_bins.verify();

  if (_large_block_reserve != NULL) {
    assert(_current != NULL, "Sanity");
  }

  assert( (_current == NULL && _current_size == 0) ||
          (_current != NULL && _current_size > 0), "Sanity");

  for (block_t* b = _large_block_reserve; b != NULL; b = b->next) {
    assert(b->size > 0 && b->size <= 4 * M, "Weird block size");
  }

}
#endif

void LeftOverManager::large_block_statistics(block_stats_t* stats) const {
  for (block_t* b = _large_block_reserve; b != NULL; b = b->next) {
    stats->num_blocks ++;
    stats->word_size += b->size;
  }
}

void LeftOverManager::statistics(block_stats_t* stats) const {
  stats->num_blocks = 0;
  stats->word_size = 0;
  _very_small_bins.statistics(stats);
  if (_current != NULL) {
    stats->num_blocks ++;
    stats->word_size += _current_size;
    large_block_statistics(stats);
  } else {
    assert(_large_block_reserve == NULL, "Sanity");
  }
}

void LeftOverManager::print(outputStream* st, bool detailed) const {

  block_stats_t s;

  if (_current != NULL) {
    st->print("current: " SIZE_FORMAT " words; ", _current_size);
  }

  s.num_blocks = 0; s.word_size = 0;
  large_block_statistics(&s);
  st->print("large blocks: %d blocks, " SIZE_FORMAT " words", s.num_blocks, s.word_size);
  if (detailed) {
    st->print(" (");
    for (block_t* b = _large_block_reserve; b != NULL; b = b->next) {
      st->print(SIZE_FORMAT "%s", b->size, b->next != NULL ? ", " : "");
    }
    st->print(")");
  }
  st->print("; ");

  s.num_blocks = 0; s.word_size = 0;
  _very_small_bins.statistics(&s);
  st->print("small blocks: %d blocks, " SIZE_FORMAT " words", s.num_blocks, s.word_size);
  if (detailed) {
    st->print(" (");
    _very_small_bins.print(st);
    st->print(")");
  }
  st->print("; ");
}

} // namespace metaspace

