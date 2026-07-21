/*
* Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 */

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDINGTABLE_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDINGTABLE_INLINE_HPP

#include "gc/shenandoah/shenandoahForwardingTable.hpp"

#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "utilities/fastHash.hpp"

inline bool FwdTableEntry::is_marked(ShenandoahMarkingContext* ctx) const {
 return ctx->is_marked_ignore_tams(cast_from_oop<HeapWord*>(cast_to_oop(&_original))) ||
        ctx->is_marked_ignore_tams(cast_from_oop<HeapWord*>(cast_to_oop(&_forwardee)));
}

inline bool FwdTableEntry::is_original(HeapWord* region_base, HeapWord* original) {
 return _original == original;
}

inline uint64_t CompactFwdTableEntry::encode(HeapWord* region_base, HeapWord* original, HeapWord* forwardee) {
 assert(original >= region_base, "original must be in region");
 assert(original - region_base <= right_n_bits(ORIGINAL_BITS), "original must be encodable");
 assert(forwardee > _heap_base, "forwardee must be in heap");
 assert(forwardee - _heap_base <= right_n_bits(FORWARDEE_BITS), "forwardee must be encodable");

 uint64_t orig_encoded = (original - region_base) << ORIGINAL_SHIFT;
 uint64_t fwd_encoded = (forwardee - _heap_base) << FORWARDEE_SHIFT;
 return orig_encoded | fwd_encoded | ENTRY_MARKER;
}

inline HeapWord* CompactFwdTableEntry::decode_original(HeapWord* region_base, uint64_t encoded) {
 return region_base + ((encoded & ORIGINAL_MASK) >> ORIGINAL_SHIFT);
}

inline HeapWord* CompactFwdTableEntry::decode_forwardee(uint64_t encoded) {
 return _heap_base + (encoded & FORWARDEE_MASK);
}

inline bool CompactFwdTableEntry::is_original(HeapWord* region_base, HeapWord* original) {
  // Instead of decoding the entry and matching the original, we do the
  // other way around and encode the requested address, and compare this
  // to the corresponding bits in the entry. This allows us to forgo the
  // marking bitmap check in the forwardee path.
  assert(original >= region_base, "original must be in region");
  assert(original - region_base <= right_n_bits(ORIGINAL_BITS), "original must be encodable");
  uint64_t orig_encoded = (original - region_base) << ORIGINAL_SHIFT | ENTRY_MARKER;
  return orig_encoded == (_encoded & (ORIGINAL_MASK | ENTRY_MARKER));
}

inline bool CompactFwdTableEntry::is_marked(ShenandoahMarkingContext* ctx) const {
 return ctx->is_marked_ignore_tams(reinterpret_cast<HeapWord*>(const_cast<uint64_t*>(&_encoded)));
}

inline uint64_t ShenandoahForwardingTable::hash(HeapWord* original, void* table) {
 return FastHash::get_hash64(reinterpret_cast<uint64_t>(original), reinterpret_cast<uint64_t>(table));
}

inline size_t ShenandoahForwardingTable::index_of(HeapWord* original) const {
 return static_cast<size_t>(hash(original, _table) % _num_entries);
}

template<class Entry>
HeapWord* ShenandoahForwardingTable::forwardee(HeapWord* const original) const {
 Entry* table = reinterpret_cast<Entry*>(_table);
 size_t const start_index = index_of(original);
 size_t index = start_index;

 HeapWord* const region_base = _region->bottom();

 while (table[index].is_used()) {
  if (table[index].is_original(region_base, original)) {
   HeapWord* result = table[index].forwardee();
   assert(result == original || ShenandoahHeap::heap()->is_in(cast_to_oop(result)),
          "FWT forwardee " PTR_FORMAT " for original " PTR_FORMAT " region=%zu is outside heap",
          p2i(result), p2i(original), _region->index());
   return result;
  }
  if (++index == _num_entries) {
   index = 0;
  }
  if (index == start_index) {
   break;
  }
 }

 // Full GC resets the marking bitmap but still consults forwardees left over from an abandoned concurrent GC effort
 // before it begins its sliding compaction GC.
 assert(ShenandoahHeap::heap()->is_full_gc_in_progress()
        || !ShenandoahHeap::heap()->marking_context()->is_marked_ignore_tams(original),
        "FWT probe miss for marked obj " PTR_FORMAT " region=%zu", p2i(original), _region->index());
 return original;
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDINGTABLE_INLINE_HPP
