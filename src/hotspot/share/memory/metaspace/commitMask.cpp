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

#include "memory/metaspace/commitMask.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/settings.hpp"
#include "runtime/stubRoutines.hpp"

#include "utilities/align.hpp"
#include "utilities/debug.hpp"

namespace metaspace {

CommitMask::CommitMask(const MetaWord* start, size_t word_size)
  : CHeapBitMap(mask_size(word_size, Settings::commit_granule_words()))
  , _base(start)
  , _word_size(word_size)
  , _words_per_bit(Settings::commit_granule_words())
{
  assert(_word_size > 0 && _words_per_bit > 0 &&
         is_aligned(_word_size, _words_per_bit), "Sanity");
}

#ifdef ASSERT

// This is very expensive
static const bool TEST_UNCOMMITTED_REGION = false;

volatile u1 x;

static void check_range_is_accessible(const MetaWord* p, size_t word_size) {
  const MetaWord* const p_end = p + word_size;
  u1 x2 = 0;
  for (const MetaWord* q = p; q < p_end; q += os::vm_page_size() / BytesPerWord) {
    x2 += *(u1*)q;
  }
  x = x2;
}

void CommitMask::verify(bool slow) const {

  // Walk the whole commit mask.
  // For each 1 bit, check if the associated granule is accessible.
  // For each 0 bit, check if the associated granule is not accessible. Slow mode only.

  assert(_base != NULL && _word_size > 0 && _words_per_bit > 0, "Sanity");
  assert_is_aligned(_base, _words_per_bit * BytesPerWord);
  assert_is_aligned(_word_size, _words_per_bit);

  if (slow) {
    for (idx_t i = 0; i < size(); i ++) {
      const MetaWord* const p = _base + (i * _words_per_bit);
      if (at(i)) {
        // Should be accessible. Just touch it.
        check_range_is_accessible(p, _words_per_bit);
      } else {
        // Note: results may differ between platforms. On Linux, this should be true since
        // we uncommit memory by setting protection to PROT_NONE. We may have to look if
        // this works as expected on other platforms.
        if (TEST_UNCOMMITTED_REGION && CanUseSafeFetch32()) {
          assert(os::is_readable_pointer(p) == false,
                 "index %u, pointer " PTR_FORMAT ", should not be accessible.",
                 (unsigned)i, p2i(p));
        }
      }
    }
  }

}

#endif // ASSERT

void CommitMask::print_on(outputStream* st) const {

  st->print("commit mask, base " PTR_FORMAT ":", p2i(base()));

  for (idx_t i = 0; i < size(); i ++) {
    st->print("%c", at(i) ? 'X' : '-');
  }

  st->cr();

}

} // namespace metaspace

