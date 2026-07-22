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

#include "gc/shared/gc_globals.hpp"
#include "gc/shenandoah/shenandoahForwardingTable.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/bitMap.inline.hpp"

HeapWord* CompactFwdTableEntry::_heap_base = nullptr;
bool ShenandoahForwardingTable::_compact = false;

void ShenandoahForwardingTable::initialize_globals() {
  if (!ShenandoahCompactFWTEntries) {
    _compact = false;
    return;
  }
  MemRegion heap = ShenandoahHeap::heap()->reserved_region();
  size_t heap_size_words = heap.word_size();
  if (ShenandoahHeapRegion::region_size_words() > CompactFwdTableEntry::max_region_size_words() ||
      heap.word_size() > CompactFwdTableEntry::max_heap_size_words()) {
    _compact = false;
  } else {
    _compact = true;
    CompactFwdTableEntry::set_heap_base(heap.start());
  }
}

static bool different_entries(HeapWord* a, HeapWord* b, size_t entry_size_in_words) {
  uintptr_t aint = reinterpret_cast<uintptr_t>(a) / HeapWordSize;
  uintptr_t bint = reinterpret_cast<uintptr_t>(b) / HeapWordSize;
  return aint / entry_size_in_words != bint / entry_size_in_words;
}

template<class Entry>
bool ShenandoahForwardingTable::initialize(size_t num_entries) {
  // Try to find the minimum hashtable that satisfies a load-factor of 0.75.
  // We know that we have num_entries live words that we can not use and we
  // need num_entries * 1.5 usable entries.
  constexpr size_t entry_words = sizeof(Entry) / sizeof(HeapWord*);
  HeapWord* const bottom =  _region->bottom();
  HeapWord* const top =  _region->top();
  HeapWord* const end = _region->end();
  // We want 1.5x entries than expected forwardings, to maintain the 0.75 load-factor.
  size_t const num_required_entries = num_entries + num_entries / 2;
  // Optimistic last possible table start. We don't need to search beyond that.
  HeapWord* const last_table_start = end - num_required_entries * entry_words;
  if (last_table_start < bottom) {
    log_info(gc)("Forwarding table build failed for region %zu: required=%zu entries of %zu words exceed region_words=%zu (num_forwardings=%zu)",
                 _region->index(), num_required_entries, entry_words,
                 pointer_delta(end, bottom), num_entries);
    return false;
  }
  // Count number of live words in the tail [last_table_start, top).
  ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
  size_t unusable_entries = 0;
  HeapWord* limit = top;
  while (last_table_start < limit) {
    HeapWord* live = ctx->get_last_marked_addr(last_table_start, limit);
    if (live >= limit) break;  // No more live objects in range
    if (different_entries(live, limit, entry_words)) {
      unusable_entries++;
    }
    limit = live;
  }
  // Now try to find a lower bound that satisfies the 0.75 load-factor.
  // Start at the last possible address.
  HeapWord* table_start = last_table_start;
  assert(table_start >= bottom, "table start must be in region");
  size_t num_table_entries = (end - table_start) / entry_words;
  while (table_start > bottom && num_table_entries - unusable_entries < num_required_entries) {
    HeapWord* prev_live = ctx->get_last_marked_addr(bottom, table_start);
    if (prev_live >= table_start) {
      // No more live objects found. Use bottom as table_start.
      table_start = bottom;
      assert(table_start >= bottom, "table start must be in region");
    } else {
      if (different_entries(prev_live, table_start, entry_words)) {
        unusable_entries++;
      }
      table_start = prev_live;
      assert(table_start >= bottom, "table start must be in region");
    }
    num_table_entries = (end - table_start) / entry_words;
  }

  assert(table_start >= bottom, "table start must be in region");

  // We may have overshot a little, adjust for optimum lower boundary.
  if (num_table_entries > (unusable_entries + num_required_entries)) {
    size_t adjust = num_table_entries - unusable_entries - num_required_entries;
    HeapWord* old_start = table_start;
    table_start += adjust * entry_words;
    num_table_entries -= adjust;
    assert(table_start >= bottom, "table start must be in region: adjust: %lu, old table start: " PTR_FORMAT ", new table start: " PTR_FORMAT ", bottom: " PTR_FORMAT, adjust, p2i(old_start), p2i(table_start), p2i(bottom));
  }

  if (num_table_entries - unusable_entries < num_required_entries) {
    log_info(gc)("Forwarding table build failed for region %zu: table_entries=%zu unusable=%zu required=%zu num_forwardings=%zu region_words=%zu",
                 _region->index(), num_table_entries, unusable_entries, num_required_entries, num_entries,
                 pointer_delta(end, bottom));
    return false;
  }
  table_start = align_down(table_start, entry_words * HeapWordSize);
  _table = reinterpret_cast<Entry*>(table_start);
  _num_entries = (end - table_start) / entry_words;
  _num_expected_forwardings = num_entries;
  _num_actual_forwardings = 0;
  _num_live_words = unusable_entries;

  assert((void*)(reinterpret_cast<Entry*>(_table) + _num_entries) == (void*)_region->end(), "table must be anchored at region end");
  log_develop_debug(gc)("Initialized forwarding table: table: " PTR_FORMAT ", num_entries: %lu, requested entries: %lu", p2i(_table), _num_entries, num_entries);
  return true;
}

template<class Entry>
void ShenandoahForwardingTable::set_marked_entries_used(BitMap& used) {
  assert((void*)(reinterpret_cast<Entry*>(_table) + _num_entries) == (void*)_region->end(), "table must be anchored at region end");

  ShenandoahMarkingContext* const ctx = ShenandoahHeap::heap()->marking_context();
  HeapWord* const top = _region->top();
  assert(ctx->top_at_mark_start(_region) == top, "TAMS must be at top during table build");

  HeapWord* const table_start = start();
  constexpr size_t entry_words = sizeof(Entry) / sizeof(HeapWord*);
  HeapWord* cb = (table_start < top) ? ctx->get_next_marked_addr(table_start, top) : top;
  while (cb < top) {
    assert(*reinterpret_cast<uintptr_t*>(cb) != 0, "preserved mark word must be non-zero at " PTR_FORMAT, p2i(cb));
    size_t slot = (cb - table_start) / entry_words;
    used.set_bit(slot);
    HeapWord* const slot_base = table_start + slot * entry_words;
    for (size_t w = 0; w < entry_words; w++) {
      if (!ctx->is_marked_ignore_tams(slot_base + w)) {
        *reinterpret_cast<uintptr_t*>(slot_base + w) = 0;
      }
    }
    HeapWord* next = cb + 1;
    cb = (next < top) ? ctx->get_next_marked_addr(next, top) : top;
  }
}

template<class Entry>
void ShenandoahForwardingTable::clear_unused_slots(const BitMap& used) {
  Entry* table = reinterpret_cast<Entry*>(_table);
  BitMap::idx_t current = used.find_first_clear_bit(0);
  while (current < _num_entries) {
    new (&table[current]) Entry();
    current = used.find_first_clear_bit(current + 1);
  }
}

template<class Entry>
void ShenandoahForwardingTable::enter_forwarding(BitMap& used, HeapWord* original, HeapWord* forwardee) {
  Entry* table = reinterpret_cast<Entry*>(_table);
  size_t index = index_of(original);
  DEBUG_ONLY(size_t const first_index = index;)
  HeapWord* region_base = _region->bottom();
  while (used.at(index)) {
    assert(!table[index].is_original(region_base, original), "occupied slot must not match the original being entered");
    if (++index == _num_entries) {
      index = 0;
    }
    assert(index != first_index, "must find a usable slot, _num_entries: %lu, actual forwardings: %lu, live_words: %lu", _num_entries, _num_actual_forwardings, _num_live_words);
  }
  new (&table[index]) Entry(region_base, original, forwardee);
  used.set_bit(index);
  _num_actual_forwardings++;
  assert(_num_actual_forwardings <= _num_expected_forwardings, "must not exceed number of forwardings");
}

template<class Entry>
void ShenandoahForwardingTable::log_stats() const {
#ifndef PRODUCT
  log_debug(gc)("Forwarding table load factor: %f", (float)(_num_actual_forwardings + _num_live_words) / (float) (_num_entries));
  log_debug(gc)("Forwarding table size: %lu (== %lu bytes)", _num_entries, sizeof(Entry) * _num_entries);
  log_debug(gc)("Forwarding table expected: %lu, actual: %lu, live words: %lu", _num_expected_forwardings, _num_actual_forwardings, _num_live_words);
#endif
}

template<class Entry>
void ShenandoahForwardingTable::fill_forwardings(BitMap& used) {
  class FillForwardingsClosure {
    ShenandoahForwardingTable& _table;
    BitMap&   _used;
    HeapWord* const _fwt_start;
    size_t    const _region_idx;
  public:
    FillForwardingsClosure(ShenandoahForwardingTable& t, BitMap& used, HeapWord* fwt_start, size_t region_idx)
      : _table(t), _used(used), _fwt_start(fwt_start), _region_idx(region_idx) {}
    void do_object(oop obj) {
      HeapWord* original = cast_from_oop<HeapWord*>(obj);
      HeapWord* forwardee = cast_from_oop<HeapWord*>(ShenandoahForwarding::get_forwardee_raw(obj));
#ifndef PRODUCT
      if (forwardee != original) {
        assert(ShenandoahHeap::heap()->is_in(cast_to_oop(forwardee)),
               "FWT fill: forwardee " PTR_FORMAT " for original " PTR_FORMAT " region=%zu is outside heap",
               p2i(forwardee), p2i(original), _region_idx);
      } else if (_fwt_start != nullptr && original < _fwt_start) {
        log_warning(gc)("FWT fill: body object " PTR_FORMAT " region=%zu is self-forwarded (not evacuated)",
                        p2i(original), _region_idx);
      }
#endif
      _table.enter_forwarding<Entry>(_used, original, forwardee);
    }
  } cl(*this, used, start(), _region->index());

  ShenandoahHeap::heap()->marked_object_iterate(_region, &cl);
  assert(_num_actual_forwardings == _num_expected_forwardings, "must enter exact number of forwardings, actual: %lu, expected: %lu", _num_actual_forwardings, _num_expected_forwardings);
  log_stats<Entry>();
}

#ifndef PRODUCT

template<class Entry>
void ShenandoahForwardingTable::verify_forwardings() {
  if (!ShenandoahVerify) {
    return;
  }
  ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
  HeapWord* const region_base = _region->bottom();
  HeapWord* end = _region->top();

  // Every marked object is resolved.
  HeapWord* start = region_base;
  while (start < end) {
    HeapWord* original = ctx->get_next_marked_addr(start, end);
    if (original < end) {
      HeapWord* expected_forwardee = cast_from_oop<HeapWord*>(ShenandoahForwarding::get_forwardee_raw(cast_to_oop(original)));
      HeapWord* actual_forwardee = forwardee<Entry>(original);
      guarantee(actual_forwardee == expected_forwardee, "Forwardees in mark-word and table must match: original: " PTR_FORMAT ", mark-forwardee: " PTR_FORMAT ", found forwardee: " PTR_FORMAT, p2i(original), p2i(expected_forwardee), p2i(actual_forwardee));

      if (expected_forwardee != original) {
        guarantee(!ShenandoahHeap::heap()->in_collection_set(cast_to_oop(expected_forwardee)),
                  "forwardee " PTR_FORMAT " for original " PTR_FORMAT " is in CSet (region=%zu)",
                  p2i(expected_forwardee), p2i(original), _region->index());
      }
    }
    start = original + 1;
  }

  // Every used slot is either a preserved mark word or a real entry with marked original.
  Entry* table = reinterpret_cast<Entry*>(_table);
  for (size_t i = 0; i < _num_entries; i++) {
    if (!table[i].is_used() || table[i].is_marked(ctx)) {
      continue;
    }
    HeapWord* orig = table[i].original(region_base);
    guarantee(ctx->is_marked_ignore_tams(orig),
              "FWT entry %zu in region %zu has original " PTR_FORMAT " that is not a marked object",
              i, _region->index(), p2i(orig));
  }
}
#endif

template<class Entry>
bool ShenandoahForwardingTable::build(size_t num_entries) {
  bool initialized = initialize<Entry>(num_entries);
  if (initialized) {
    // Track used slots in a scratch bitmap during construction, then zero
    // only the unused slots. This avoids pre-zeroing slots that fill overwrites.
    ResourceMark rm;
    ResourceBitMap used(_num_entries);
    set_marked_entries_used<Entry>(used);
    fill_forwardings<Entry>(used);
    clear_unused_slots<Entry>(used);
    verify_forwardings<Entry>();
  }
  return initialized;
}

bool ShenandoahForwardingTable::build(size_t num_entries) {
  if (_compact) {
    return build<CompactFwdTableEntry>(num_entries);
  } else {
    return build<FwdTableEntry>(num_entries);
  }
}

#ifdef USE_SENTINELS
template<class Entry>
void ShenandoahForwardingTable::write_at_originals(uintptr_t word, HeapWord* from, HeapWord* to) {
  assert(_table != nullptr, "FWT must be built before writing sentinels");
  Entry* table = reinterpret_cast<Entry*>(_table);
  HeapWord* region_base = _region->bottom();
  // Footprint == min_fill_size so the hole left between reused allocations is always a fillable
  // object; the original's object is >= min_fill_size, so these words never reach the next one.
  const size_t fill_words = ShenandoahHeap::min_fill_size();
  for (size_t i = 0; i < _num_entries; i++) {
    if (table[i].is_used()) {
      HeapWord* original = table[i].original(region_base);
      if (original >= from && original < to) {
        for (size_t w = 0; w < fill_words && original + w < to; w++) {
          *reinterpret_cast<uintptr_t*>(original + w) = word;
        }
      }
    }
  }
#ifndef PRODUCT
  if (ShenandoahVerify) {
    for (size_t i = 0; i < _num_entries; i++) {
      if (table[i].is_used()) {
        HeapWord* original = table[i].original(region_base);
        if (original >= from && original < to) {
          uintptr_t got = *reinterpret_cast<uintptr_t*>(original);
          guarantee(got == word,
                    "readback mismatch at " PTR_FORMAT " region=%zu slot=%zu: expected " PTR_FORMAT ", got " PTR_FORMAT,
                    p2i(original), _region->index(), i, word, got);
        }
      }
    }
  }
#endif
}

void ShenandoahForwardingTable::install_sentinels() {
  HeapWord* fwt_start = start();
  HeapWord* bottom    = _region->bottom();
  if (_compact) {
    write_at_originals<CompactFwdTableEntry>(ShenandoahHeap::in_fwt_sentinel, bottom, fwt_start);
  } else {
    write_at_originals<FwdTableEntry>(ShenandoahHeap::in_fwt_sentinel, bottom, fwt_start);
  }
}
#else
template<class Entry>
void ShenandoahForwardingTable::add_marks_above_tams() {
  assert(_table != nullptr, "FWT must be built before writing sentinels");
  Entry* table = reinterpret_cast<Entry*>(_table);
  ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
  HeapWord* TAMS = ctx->top_at_mark_start(_region);
  HeapWord* region_base = _region->bottom();
#undef KELVIN_MARK_BITMAP
#ifdef KELVIN_MARK_BITMAP
  log_info(gc)("KELVIN extending bitmap for region %zu, [" PTR_FORMAT ", " PTR_FORMAT "], TAMS: " PTR_FORMAT,
               _region->index(), p2i(_region->bottom()), p2i(_region->end()), p2i(TAMS));
  size_t newly_marked = 0;
  size_t already_marked = 0;
#endif
  for (size_t i = 0; i < _num_entries; i++) {
    if (table[i].is_used()) {
      HeapWord* original = table[i].original(region_base);
      if (original >= TAMS) {
        bool was_upgraded;
        oop obj = cast_to_oop(original);
        ctx->mark_strong_ignore_tams(obj, was_upgraded);
#ifdef KELVIN_MARK_BITMAP
        if (was_upgraded) {
          newly_marked++;
        } else {
          already_marked++;
        }
#endif
      }
    }
  }
#ifdef KELVIN_MARK_BITMAP
  log_info(gc)(" added %zu mark bits, supplementing %zu originally marked_bits", newly_marked, already_marked);
#endif
}

void ShenandoahForwardingTable::extend_mark_bitmaps() {
  if (_compact) {
    add_marks_above_tams<CompactFwdTableEntry>();
  } else {
    add_marks_above_tams<FwdTableEntry>();
  }
}
#endif

