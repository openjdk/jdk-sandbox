/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHMARKINGCONTEXT_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHMARKINGCONTEXT_INLINE_HPP

#include "gc/shenandoah/shenandoahMarkingContext.hpp"

#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahMarkBitMap.inline.hpp"
#include "logging/log.hpp"

inline bool ShenandoahMarkingContext::mark_strong(oop obj, bool& was_upgraded) {
  return !allocated_after_mark_start(obj) && _mark_bit_map.mark_strong(cast_from_oop<HeapWord*>(obj), was_upgraded);
}

inline bool ShenandoahMarkingContext::mark_strong_ignore_tams(oop obj, bool& was_upgraded) {
  return _mark_bit_map.mark_strong(cast_from_oop<HeapWord*>(obj), was_upgraded);
}

inline bool ShenandoahMarkingContext::mark_weak(oop obj) {
  return !allocated_after_mark_start(obj) && _mark_bit_map.mark_weak(cast_from_oop<HeapWord *>(obj));
}

inline bool ShenandoahMarkingContext::is_marked(oop obj) const {
  return is_marked(cast_from_oop<HeapWord*>(obj));
}

inline bool ShenandoahMarkingContext::is_marked(HeapWord* raw_obj) const {
  return allocated_after_mark_start(raw_obj) || _mark_bit_map.is_marked(raw_obj);
}

inline bool ShenandoahMarkingContext::is_marked_ignore_tams(HeapWord* raw_obj) const {
  return _mark_bit_map.is_marked(raw_obj);
}

inline bool ShenandoahMarkingContext::is_marked_strong(oop obj) const {
  return is_marked_strong(cast_from_oop<HeapWord*>(obj));
}

inline bool ShenandoahMarkingContext::is_marked_strong(HeapWord* raw_obj) const {
  return allocated_after_mark_start(raw_obj) || _mark_bit_map.is_marked_strong(raw_obj);
}

inline bool ShenandoahMarkingContext::is_marked_weak(oop obj) const {
  return allocated_after_mark_start(obj) || _mark_bit_map.is_marked_weak(cast_from_oop<HeapWord *>(obj));
}

inline bool ShenandoahMarkingContext::is_marked_or_old(oop obj) const {
  return is_marked(obj) || ShenandoahHeap::heap()->is_in_old_during_young_collection(obj);
}

inline HeapWord* ShenandoahMarkingContext::get_prev_marked_addr(const HeapWord* limit, const HeapWord* start) const {
  return _mark_bit_map.get_prev_marked_addr(limit, start);
}

inline bool ShenandoahMarkingContext::is_marked_strong_or_old(oop obj) const {
  return is_marked_strong(obj) || ShenandoahHeap::heap()->is_in_old_during_young_collection(obj);
}

inline HeapWord* ShenandoahMarkingContext::get_next_marked_addr(const HeapWord* start, const HeapWord* limit) const {
  return _mark_bit_map.get_next_marked_addr(start, limit);
}

inline HeapWord* ShenandoahMarkingContext::get_next_marked_addr_ignore_tams(const HeapWord* start, const HeapWord* limit) const {
  return _mark_bit_map.get_next_marked_addr_ignore_tams(start, limit);
}

inline HeapWord* ShenandoahMarkingContext::get_last_marked_addr(const HeapWord* start, const HeapWord* limit) const {
  return _mark_bit_map.get_last_marked_addr(start, limit);
}

inline bool ShenandoahMarkingContext::allocated_after_mark_start(oop obj) const {
  const HeapWord* addr = cast_from_oop<HeapWord*>(obj);
  return allocated_after_mark_start(addr);
}

inline bool ShenandoahMarkingContext::allocated_after_mark_start(const HeapWord* addr) const {
  uintx index = ((uintx) addr) >> ShenandoahHeapRegion::region_size_bytes_shift();
  HeapWord* top_at_mark_start = _top_at_mark_starts[index];
  const bool alloc_after_mark_start = addr >= top_at_mark_start;
  return alloc_after_mark_start;
}

inline void ShenandoahMarkingContext::capture_top_at_mark_start(ShenandoahHeapRegion *r) {
  if (!r->is_affiliated()) {
    // Non-affiliated regions do not need their TAMS updated
    return;
  }

  size_t idx = r->index();
  HeapWord* old_tams = _top_at_mark_starts_base[idx];
  HeapWord* new_tams = r->top();

  assert(new_tams >= old_tams,
         "Region %zu, TAMS updates should be monotonic: " PTR_FORMAT " -> " PTR_FORMAT,
         idx, p2i(old_tams), p2i(new_tams));
  assert((new_tams == r->bottom()) || (old_tams == r->bottom()) || (new_tams >= _top_bitmaps[idx]),
         "Region %zu, top_bitmaps updates should be monotonic: " PTR_FORMAT " -> " PTR_FORMAT,
         idx, p2i(_top_bitmaps[idx]), p2i(new_tams));
  /*
  assert(old_tams == r->bottom() || is_bitmap_range_within_region_clear(old_tams, new_tams),
         "Region %zu, bitmap should be clear while adjusting TAMS: " PTR_FORMAT " -> " PTR_FORMAT,
         idx, p2i(old_tams), p2i(new_tams));
*/
  log_debug(gc)("Capturing TAMS for %s Region %zu, was: " PTR_FORMAT ", now: " PTR_FORMAT,
                r->affiliation_name(), idx, p2i(old_tams), p2i(new_tams));

  _top_at_mark_starts_base[idx] = new_tams;
  _top_bitmaps[idx] = new_tams;
}

inline void ShenandoahMarkingContext::reset_top_at_mark_start(ShenandoahHeapRegion* r) {
  _top_at_mark_starts_base[r->index()] = r->bottom();
}

inline HeapWord* ShenandoahMarkingContext::top_at_mark_start(const ShenandoahHeapRegion* r) const {
  return _top_at_mark_starts_base[r->index()];
}

inline void ShenandoahMarkingContext::reset_top_bitmap(ShenandoahHeapRegion* r) {
  assert(is_bitmap_range_within_region_clear(r->bottom(), r->end()),
         "Region %zu should have no marks in bitmap", r->index());
  _top_bitmaps[r->index()] = r->bottom();
}

inline size_t ShenandoahMarkingContext::count_bits(ShenandoahMarkBitMap::bm_word_t word) const {
#if defined(__GNUC__) || defined(__clang__)
  return __builtin_popcountll(word);
#elif defined(_MSC_VER)
  return __popcnt64(word);
#else
  // Fallback for other compilers
  return std::bitset<64>(word).count(); 
#endif
}

template<size_t ENTRY_WORDS>
inline size_t ShenandoahMarkingContext::count_conflicts_in_one_word(ShenandoahMarkBitMap::bm_word_t word) const {
  if (ENTRY_WORDS == 1) {
    // For each marked object, there are two consecutive bits representing a strong and weak mark respectively
    ShenandoahMarkBitMap::bm_word_t shifted_word = word >> 1;
    ShenandoahMarkBitMap::bm_word_t any_mark_word = (shifted_word | word) & 0x5555555555555555;
    return count_bits(any_mark_word);
  } else if (ENTRY_WORDS == 2) {
    // For each marked object, there are two consecutive bits representing a strong and weak mark respectively
    ShenandoahMarkBitMap::bm_word_t shifted_word = word >> 1;
    ShenandoahMarkBitMap::bm_word_t any_mark_word = (shifted_word | word) & 0x5555555555555555;
    size_t total_markwords = count_bits(any_mark_word);
    // Figure out how many of these mark words correspond to a single slot.
    // consecutive_ones is 1 if this word and the next word are both marked.
    size_t consecutive_ones = word & (word >> 2);
    // aligned_conflicts is 1 if this word and the next word are both marked, and this word is the start of an entry.
    size_t aligned_conflicts = consecutive_ones & 0x1111111111111111;
    size_t overcounted_bits = count_bits(aligned_conflicts);
    return total_markwords - overcounted_bits;
  } else {
    assert(false, "count_coflicts_in_one_word() is not implemented for ENTRY_WORDS = %zu", ENTRY_WORDS);
  }
}

template<size_t ENTRY_WORDS>
inline size_t ShenandoahMarkingContext::count_mark_bit_conflicts(const HeapWord* start, const HeapWord* end) const {
  // Assume mark bits are valid for this entire range, even above "TAMS".
  // Figure out how many words we are processing.
  size_t num_bitmap_words = _mark_bit_map.bitmap_words_in_range(start, end);
  if (num_bitmap_words == 0) {
    return 0;
  } else if (num_bitmap_words == 1) {
    ShenandoahMarkBitMap::bm_word_t only_word = _mark_bit_map.first_bitmap_word_in_range(start, end);
    size_t count = count_conflicts_in_one_word<ENTRY_WORDS>(only_word);
    return count;
  } else {
    ShenandoahMarkBitMap::bm_word_t first_word = _mark_bit_map.first_bitmap_word_in_range(start, end);
    size_t conflicts = 0;
    size_t count = count_conflicts_in_one_word<ENTRY_WORDS>(first_word);
    conflicts += count;
    ShenandoahMarkBitMap::bm_word_t last_word = _mark_bit_map.last_bitmap_word_in_range(start, end);
    count = count_conflicts_in_one_word<ENTRY_WORDS>(last_word);
    conflicts += count;
    if (num_bitmap_words == 2) {
      return conflicts;
    } else {
      for (size_t i = 1; i < num_bitmap_words - 1; i++) {
        ShenandoahMarkBitMap::bm_word_t other_word = _mark_bit_map.get_bitmap_word_in_range(start, end, i);
        count = count_conflicts_in_one_word<ENTRY_WORDS>(other_word);
        conflicts += count;
      }
      return conflicts;
    }
  }
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHMARKINGCONTEXT_INLINE_HPP
