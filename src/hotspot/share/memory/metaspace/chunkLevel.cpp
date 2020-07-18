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
#include <memory/metaspace/settings.hpp>
#include "precompiled.hpp"
#include "memory/metaspace/chunkLevel.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

namespace metaspace {

chunklevel_t chunklevel::level_fitting_word_size(size_t word_size) {

  assert(chunklevel::MAX_CHUNK_WORD_SIZE >= word_size,
         SIZE_FORMAT " - too large allocation size.", word_size * BytesPerWord);

  // TODO: This can be done much better.
  chunklevel_t l = chunklevel::HIGHEST_CHUNK_LEVEL;
  while (l >= chunklevel::LOWEST_CHUNK_LEVEL &&
         word_size > chunklevel::word_size_for_level(l)) {
    l --;
  }

  return l;
}

void chunklevel::print_chunk_size(outputStream* st, chunklevel_t lvl) {
  if (chunklevel::is_valid_level(lvl)) {
    const size_t s = chunklevel::word_size_for_level(lvl) * BytesPerWord;
    if (s < 1 * M) {
      st->print("%3uk", (unsigned)(s / K));
    } else {
      st->print("%3um", (unsigned)(s / M));
    }
  } else {
    st->print("?-?");
  }

}

} // namespace metaspace


