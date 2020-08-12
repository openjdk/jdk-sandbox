/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 SAP SE. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_ALLOCATION_GUARD_HPP
#define SHARE_MEMORY_METASPACE_ALLOCATION_GUARD_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace/chunkLevel.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

// In Debug builds, Metadata in Metaspace can be optionally guarded - enclosed in canaries -
// to detect memory overwriters.
//
// These canaries are periodically checked, e.g. when the Metaspace is purged in a context
// of a GC.

// The canaries precede any allocated block...
//
// +---------------+
// |  'METAMETA'   |
// +---------------+
// |  block size   |
// +---------------+
// |  block...     |
// .               .
// .               .
// .               .
// |               |
// +---------------+
// . <padding>     .
// +---------------+
// |  'METAMETA'   |
// +---------------+
// |  block size   |
// +---------------+
// |  block...     |

// ... and since the blocks are allocated via pointer bump and closely follow each other,
// one block's prefix is its predecessor's suffix, so apart from the last block all
// blocks have an overwriter canary on both ends.
//

// Note: this feature is only available in debug, and is activated using
//  -XX:+MetaspaceGuardAllocations. When active, it disables deallocation handling - since
//  freeblock handling in the freeblock lists would get too complex - so one may run leaks
//  in deallocation-heavvy scenarios (e.g. lots of class redefinitions).
//


namespace metaspace {

#ifdef ASSERT

struct prefix_t {
  uintx mark;
  size_t word_size;       // raw word size including prefix
  // MetaWord payload [0];   // varsized (but unfortunately not all our compilers understand that)
};

// The prefix structure must be aligned to MetaWord size.
STATIC_ASSERT((sizeof(prefix_t) & WordAlignmentMask) == 0);

inline prefix_t* prefix_from_payload(MetaWord* p) {
  return (prefix_t*)((address)p - sizeof(prefix_t));
}

inline MetaWord* payload_from_prefix(prefix_t* pp) {
  // return pp->payload;
  return (MetaWord*)((address)pp + sizeof(prefix_t));
}

inline size_t prefix_size() {
  return sizeof(prefix_t);
}

#define EYECATCHER NOT_LP64(0x77698465) LP64_ONLY(0x7769846577698465ULL) // "META" resp "METAMETA"

// Given a pointer to a memory area, establish the prefix at the start of that area and
// return the starting pointer to the payload.
inline MetaWord* establish_prefix(MetaWord* p_raw, size_t raw_word_size) {
  prefix_t* pp = (prefix_t*)p_raw;
  pp->mark = EYECATCHER;
  pp->word_size = raw_word_size;
  return payload_from_prefix(pp);
}

inline void check_prefix(const prefix_t* pp) {
  assert(pp->mark == EYECATCHER, "corrupt block at " PTR_FORMAT ".", p2i(pp));
  assert(pp->word_size > 0 && pp->word_size < chunklevel::MAX_CHUNK_WORD_SIZE,
         "Invalid size " SIZE_FORMAT " in block at " PTR_FORMAT ".", pp->word_size, p2i(pp));
}

#endif

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_ALLOCATION_GUARD_HPP
