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
#ifndef PRODUCT
#include "gc/shared/collectedHeap.hpp"
#include "memory/metaspace.hpp"
#include "oops/klass.hpp"
#include "oops/markWord.hpp"
#endif

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

#ifndef PRODUCT
static void log_fwt_probe_miss(HeapWord* const original, size_t region_idx,
                               const char* kind, ShenandoahMarkingContext* ctx) {
 uintptr_t mark_raw = *reinterpret_cast<const uintptr_t*>(original);
 auto mw = cast_to_oop(original)->mark();
 bool bm_marked = ctx->is_marked_ignore_tams(original);
 Klass* k = cast_to_oop(original)->klass_or_null();
 log_trace(gc)("FWT probe-miss (%s) obj " PTR_FORMAT " region=%zu: "
               "mark=" PTR_FORMAT " (forwarded=%s bm_marked=%s) klass=" PTR_FORMAT,
               kind, p2i(original), region_idx,
               mark_raw, BOOL_TO_STR(mw.is_forwarded()), BOOL_TO_STR(bm_marked),
               p2i(k));
 if (mw.is_forwarded()) {
  log_trace(gc)("  -> forwarded to " PTR_FORMAT, p2i(cast_from_oop<HeapWord*>(mw.forwardee())));
 }
 if (k != nullptr && Metaspace::contains(k)) {
  ResourceMark rm;
  size_t obj_size = cast_to_oop(original)->size();
  log_trace(gc)("  class: %s size=%zu range=[" PTR_FORMAT ", " PTR_FORMAT ")",
                k->external_name(), obj_size, p2i(original), p2i(original + obj_size));
 }
}
#endif

inline uint64_t ShenandoahForwardingTable::hash(HeapWord* original, void* table) {
 return FastHash::get_hash64(reinterpret_cast<uint64_t>(original), reinterpret_cast<uint64_t>(table));
}

template<class Entry>
HeapWord* ShenandoahForwardingTable::forwardee(HeapWord* const original) const {
 Entry* table = reinterpret_cast<Entry*>(_table);
 uint64_t hash_val = hash(original, table);
 uint64_t index = hash_val % _num_entries;
 log_develop_trace(gc)("Finding slot, start at index: " UINT64_FORMAT ", for original: " PTR_FORMAT, index, p2i(original));

 assert(table != nullptr, "FWT table race");

 HeapWord* const region_base = _region->bottom();

 ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
 while (table[index].is_used() || table[index].is_marked(ctx)) {
  // Real forwarding entries are always at unmarked slots (enter_forwarding asserts this).
  if (table[index].is_used() && !table[index].is_marked(ctx) && table[index].is_original(region_base, original)) {
   assert(table[index].forwardee() != nullptr, "must have found a forwarding");
   return table[index].forwardee();
  }
  log_develop_trace(gc)("Collision on " UINT64_FORMAT ": " PTR_FORMAT ": is_marked: %s, original: " PTR_FORMAT ", forwardee: " PTR_FORMAT, index, p2i(&table[index]), BOOL_TO_STR(table[index].is_marked(ShenandoahHeap::heap()->marking_context())), p2i(table[index].original(region_base)), p2i(table[index].forwardee()));
  index = (index + 1) % _num_entries;
  if (index == hash_val % _num_entries) {
   NOT_PRODUCT(log_fwt_probe_miss(original, _region->index(), "full-wrap", ctx);)
   return original;
  }
 }

 NOT_PRODUCT(log_fwt_probe_miss(original, _region->index(), "empty-slot", ctx);)
 return original;
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDINGTABLE_INLINE_HPP
