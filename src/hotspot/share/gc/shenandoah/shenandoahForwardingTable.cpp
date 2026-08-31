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
#include "oops/markWord.hpp"
#include "runtime/prefetch.inline.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/powerOfTwo.hpp"

#include <math.h>

HeapWord* CompactFwdTableEntry::_heap_base = nullptr;
bool ShenandoahForwardingTable::_compact = false;
size_t ShenandoahForwardingTable::_common_max_probes = 0;

size_t ShenandoahForwardingTable::compute_common_max_probes() {
  size_t const overrun = ShenandoahForwardingTableProbeOverrun;
  if (overrun == 0) {
    return 0;
  }
  size_t const lf = ShenandoahForwardingTableLoadFactorPercent;
  constexpr size_t largest_entry_words = sizeof(CompactFwdTableEntry) / sizeof(HeapWord*);
  size_t const largest_max_slots = (ShenandoahHeapRegion::region_size_words() / largest_entry_words)
                                   * ShenandoahForwardingTableMaxPercent / 100;
  size_t const largest_max_keys = largest_max_slots * lf / 100;
  if (largest_max_keys == 0) {
    return 0;
  }
  assert(0 < lf && lf < 100, "load factor must be in (0, 100)");
  double const alpha = (double)lf / 100.0;
  double const expected_max = ::log((double)largest_max_keys) / ::log(1.0 / alpha);
  size_t const probes = MAX2((size_t)(overrun * expected_max), (size_t)1);
  log_info(gc)("Forwarding table probe limit: %zu (largest table %zu slots, load factor %zu%%, "
               "expected longest chain %.0f, overrun %zu)",
               probes, largest_max_slots, lf,
               expected_max, overrun);
  return probes;
}

void ShenandoahForwardingTable::initialize_globals() {
  _common_max_probes = compute_common_max_probes();
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

// Return true if the two addresses a and b correspond to different entries within the associated forwarding table.
template<class Entry>
static inline bool different_entries(HeapWord* a, HeapWord* b) {
  if constexpr (std::is_same_v<Entry, FwdTableEntry>) {
    constexpr size_t entry_words = sizeof(FwdTableEntry) / sizeof(HeapWord*);
    uintptr_t aint = reinterpret_cast<uintptr_t>(a) / HeapWordSize;
    uintptr_t bint = reinterpret_cast<uintptr_t>(b) / HeapWordSize;
    return aint / entry_words != bint / entry_words;
  } else {
    assert((std::is_same_v<Entry, CompactFwdTableEntry>) && (sizeof(CompactFwdTableEntry) == HeapWordSize), "sanity");
    return a != b;
  }
}

static bool is_prime(size_t n) {
  if (n < 2)      return false;
  if (n % 2 == 0) return n == 2;
  if (n % 3 == 0) return n == 3;
  //   6k+0 - divisible by 2 and 3
  //   6k+1 - candidate (tested as d+2)
  //   6k+2 - divisible by 2
  //   6k+3 - divisible by 3
  //   6k+4 - divisible by 2
  //   6k+5 - candidate (tested as d)
  for (size_t d = 5; d <= n / d; d += 6) {
    if (n % d == 0 || n % (d + 2) == 0) return false;
  }
  return true;
}

// n is the (planned forwardings plus the expected mark-word collisions)  / Specified LoadFactor
static size_t next_prime(size_t n, size_t limit) {
  if (n < 1024) {               // Give special handling to smaller tables
    if (n < 256) {
      size_t selector = n / 16;
      /*  selector      max      _num_entries     percent
       *            forwardings                     pad
       *      0         15             23           53%
       *      1         31             47           52%
       *      2         47             71           51%
       *      3         63             97           54%
       *      4         79            113           43%
       *      5         95            131           38%
       *      6        111            151           36%
       *      7        127            173           36%
       *      8        143            193           35%
       *      9        160            211           32%
       *     10        175            233           32%
       *     11        191            251           31%
       *     12        207            271           31%
       *     13        223            293           31%
       *     14        239            313           31%
       *     15        255            331           30%
       */
      assert(selector < 16, "sanity");
      static size_t small_primes[] = { 23, 47, 71, 97, 113, 131, 151, 173, 193, 211, 233, 251, 271, 293, 313, 331 };
      return small_primes[selector];
    } else {
      size_t selector = n / 64;
      /*  selector      max      _num_entries     percent
       *            forwardings                     pad
       *      0         63         handled above
       *      1        127         handled above
       *      2        191         handled above
       *      3        255         handled above
       *      4        319            401           26%
       *      5        383            479           25%
       *      6        447            557           25%
       *      7        511            631           23%
       *      8        575            701           22%
       *      9        639            761           19%
       *     10        704            829           18%
       *     11        767            907           18%
       *     12        831            977           18%
       *     13        895           1021           14%
       *     14        959           1049            9%
       *     15       1023           1069            4%
       */
      static size_t medium_primes[] = { 401, 479, 557, 631, 701, 761, 829, 907, 977, 1021, 1049, 1069 };
      assert(selector < 16, "sanity");
      assert(selector >= 4, "sanity");
      return medium_primes[selector - 4];
    }
  } else {
    if (n <= 2) return (limit >= 2) ? 2 : 0;
    // if n is even, don't bother testing it.
    if (n % 2 == 0) n++;
    // Test every odd integer >= the original value of n.
    for (; n <= limit; n += 2) {
      // Return the first integer that satisfies the is_prime test.
      if (is_prime(n)) return n;
    }
    // Return 0 if we could not find a prime lower or equal to limit
    return 0;
  }
}

template<class Entry>
bool ShenandoahForwardingTable::initialize(size_t num_entries) {
  // Find the minimum hashtable that satisfies the target load factor. Live
  // object headers falling within the table's range are unusable slots; the
  // search below grows the table to keep enough usable slots.
  constexpr size_t entry_words = sizeof(Entry) / sizeof(HeapWord*);
  // Entry aligned and suitable for count_mark_bit_conflicts().
  size_t const entry_obj_align = MAX2(entry_words * HeapWordSize, (size_t)MinObjAlignmentInBytes);
  HeapWord* const bottom =  _region->bottom();
  HeapWord* const top =  _region->top();
  HeapWord* const end = _region->end();
  // Usable slots to size for at the target load factor: ceil(num_entries * 100 / LF).
  // Default LF = 60 -> 1.667x entries per forwarding, chosen to keep the average
  // double-hashing chain near 1.5 for a successful lookup (75 gives ~1.85, 85
  // ~2.2). A lower load factor yields a sparser table
  // with shorter probe chains (cheaper resolve and fill) but a larger tail, so
  // fewer dense regions fit; a higher one packs tighter.
  size_t const lf = ShenandoahForwardingTableLoadFactorPercent;
  // num_required_entries is the number of usable entries in order to honor requested load factor
  size_t const num_required_entries = (num_entries * 100 + lf - 1) / lf;
  // Optimistic last possible table start (assuming no unusable entries). We don't need to search beyond that.
  HeapWord* const last_table_start = align_down(end - num_required_entries * entry_words, entry_obj_align);
  if (last_table_start < bottom) {
    log_info(gc)("Forwarding table build failed for region %zu: "
                 "required=%zu entries of %zu words exceed region_words=%zu (num_forwardings=%zu)",
                 _region->index(), num_required_entries, entry_words,
                 pointer_delta(end, bottom), num_entries);
    return false;
  }
  // Diagnostic knob: only switch to a forwarding table when its tail would
  // occupy at most ShenandoahForwardingTableMaxPercent of the region. Denser
  // regions keep mark-word forwarding: nothing to build here, and no sentinel
  // run to skip during TLAB carving, at the cost of not early-reclaiming the
  // body. 100 disables this check (physical fit only, enforced above); the
  // default 93 keeps ~7% of the region as reusable body.
  if (ShenandoahForwardingTableMaxPercent < 100) {
    size_t const region_words = pointer_delta(end, bottom);
    size_t const table_words = num_required_entries * entry_words;
    if (table_words * 100 > region_words * ShenandoahForwardingTableMaxPercent) {
      log_debug(gc)("Forwarding table skipped for region %zu: table %zu%% of region exceeds max %zu%% (num_forwardings=%zu)",
                    _region->index(), table_words * 100 / region_words,
                    (size_t)ShenandoahForwardingTableMaxPercent, num_entries);
      return false;
    }
  }
  // Count number of live words in the tail [last_table_start, top).
  ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
  size_t unusable_entries;
  if (last_table_start >= top) {
    unusable_entries = 0;
  } else {
    unusable_entries = ctx->count_mark_bit_conflicts<entry_words>(last_table_start, top);
  }
  // Now try to find a lower bound that satisfies the target load factor.  Start at the last possible address.
  HeapWord* table_start = last_table_start;
  assert(table_start >= bottom, "table start must be in region");
  size_t num_table_entries = (end - table_start) / entry_words;

  while (table_start > bottom && num_table_entries - unusable_entries < num_required_entries) {
    size_t growth = num_required_entries + unusable_entries - num_table_entries;
    HeapWord* new_table_start = align_down(table_start - growth * entry_words, entry_obj_align);
    if (new_table_start < bottom) {
      table_start = bottom;     // Force loop to abort with failure condition.
      break;
    } else {
      unusable_entries += ctx->count_mark_bit_conflicts<entry_words>(new_table_start, table_start);
      table_start = new_table_start;
      num_table_entries = (end - table_start) / entry_words;
    }
  }

  if (num_table_entries - unusable_entries < num_required_entries) {
    log_info(gc)("Forwarding table build failed for region %zu: "
                 "table_entries=%zu unusable=%zu required=%zu num_forwardings=%zu region_words=%zu",
                 _region->index(), num_table_entries, unusable_entries, num_required_entries, num_entries,
                 pointer_delta(end, bottom));
    return false;
  }
  table_start = align_down(table_start, entry_words * HeapWordSize);

  // Prime table size >= 2 (a modulus of 1 would break the double-hashing stride) for
  // a later switch to double hashing.
  size_t const region_entries = (end - bottom) / entry_words;
  size_t const prime_entries = next_prime(MAX2(num_table_entries, (size_t)2), region_entries);

  if (prime_entries == 0) {
    log_info(gc)("Forwarding table build failed for region %zu: "
                 "no prime table size fits (table_entries=%zu region_entries=%zu num_forwardings=%zu)",
                 _region->index(), num_table_entries, region_entries, num_entries);
    return false;
  }
  table_start = end - prime_entries * entry_words;
  _table = reinterpret_cast<Entry*>(table_start);
#ifdef ASSERT
  HeapWord* table_address = (HeapWord*) _table;
  assert((table_address >= bottom) && (table_address < end) && is_object_aligned(table_address),
         "_table must be within range and aligned");
#endif
  _num_entries = prime_entries;
  assert(_num_entries <= max_juint, "num_entries %zu must fit in 32 bits for the multiply-shift probe reduction", _num_entries);
  _num_expected_forwardings = num_entries;
  _num_actual_forwardings = 0;
  _num_live_words = unusable_entries;
  _max_required_probes = 0;
  _abandoned = false;

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
    // These slots overlay live object headers, which the forwardee path tells from real
    // entries by value alone (see forwardee()). That needs a forwarding pointer here, not
    // a plain header carrying klass bits, which under +UseCompactObjectHeaders would alias
    // ENTRY_MARKER. Assert it, so relaxing that cannot break it quietly.
    DEBUG_ONLY(markWord const mark = markWord(*reinterpret_cast<uintptr_t*>(cb));)
    DEBUG_ONLY(void* const fwdptr = mark.clear_lock_bits().to_pointer();)
    assert(mark.is_marked(), "preserved header at " PTR_FORMAT " in region %zu must be a forwarding "
           "pointer, got " PTR_FORMAT, p2i(cb), _region->index(), p2i(mark.to_pointer()));
    // A null forwardee reads back as "not forwarded" and carries no klass bits either,
    // so accept it; a non-null one must be a real heap address.
    assert(fwdptr == nullptr || ShenandoahHeap::heap()->is_in(fwdptr),
           "preserved header at " PTR_FORMAT " in region %zu must forward into the heap, got " PTR_FORMAT,
           p2i(cb), _region->index(), p2i(fwdptr));
    size_t slot = (cb - table_start) / entry_words;
    used.set_bit(slot);
    HeapWord* const slot_base = table_start + slot * entry_words;
    // Fill the non-marked words of this entry with 0.  This will not match any forwardee lookup requests.
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
size_t ShenandoahForwardingTable::reserve_forwarding(BitMap& used, size_t index, size_t stride) {
  size_t const first_index = index;
  size_t const max_probes = _common_max_probes;
  size_t depth = 1;
  while (used.at(index)) {
    if (max_probes != 0 && depth >= max_probes) {
      _abandoned = true; // pathological chain
      return _num_entries;
    }
    index += stride;
    if (index >= _num_entries) {
      index -= _num_entries;
    }
    guarantee(index != first_index, "must find a usable slot, _num_entries: %zu, actual forwardings: %zu, live_words: %zu"
              ", first_index: %zu, index: %zu, stride: %zu, depth: %zu",
              _num_entries, _num_actual_forwardings, _num_live_words, first_index, index, stride, depth);
    depth++;
  }
  used.set_bit(index);
  if (depth > _max_required_probes) {
    _max_required_probes = depth;
  }
  _num_actual_forwardings++;
  assert(_num_actual_forwardings <= _num_expected_forwardings, "must not exceed number of forwardings");
  return index;
}

template<class Entry>
void ShenandoahForwardingTable::enter_forwarding(BitMap& used, HeapWord* original, HeapWord* forwardee) {
  if (_abandoned) {
    return;
  }
  size_t index, stride;
  probe_of(original, index, stride);
  Entry const entry(_region->bottom(), original, forwardee);
  index = reserve_forwarding<Entry>(used, index, stride);
  if (index == _num_entries) {
    assert(_abandoned, "only an abandoned table reserves no slot");
    return;
  }
  insert_forwarding<Entry>(index, entry);
}

template<class Entry>
void ShenandoahForwardingTable::log_stats() const {
#ifndef PRODUCT
  log_debug(gc)("Forwarding table load factor: %f", (float)(_num_actual_forwardings + _num_live_words) / (float) (_num_entries));
  log_debug(gc)("Forwarding table size: %lu (== %lu bytes)", _num_entries, sizeof(Entry) * _num_entries);
  log_debug(gc)("Forwarding table expected: %lu, actual: %lu, live words: %lu", _num_expected_forwardings, _num_actual_forwardings, _num_live_words);
#endif
#undef KELVIN_VERBOSE
#ifdef KELVIN_VERBOSE
  log_info(gc)("Forwarding table load factor: %f", (float)(_num_actual_forwardings + _num_live_words) / (float) (_num_entries));
  log_info(gc)("Forwarding table size: %lu (== %lu bytes)", _num_entries, sizeof(Entry) * _num_entries);
  log_info(gc)("Forwarding table expected: %lu, actual: %lu, live words: %lu", _num_expected_forwardings, _num_actual_forwardings, _num_live_words);
#endif
}

template<class Entry>
void ShenandoahForwardingTable::fill_forwardings(BitMap& used) {
  class FillForwardingsClosure {
    ShenandoahForwardingTable& _fwt;
    BitMap&         _used;
    HeapWord* const _fwt_start;
    size_t    const _region_idx;

  public:
    FillForwardingsClosure(ShenandoahForwardingTable& fwt, BitMap& used,
                           HeapWord* fwt_start, size_t region_idx)
      : _fwt(fwt), _used(used), _fwt_start(fwt_start), _region_idx(region_idx) {}

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
      _fwt.enter_forwarding<Entry>(_used, original, forwardee);
    }
  } cl(*this, used, start(), _region->index());

  ShenandoahHeap::heap()->marked_object_iterate(_region, &cl);
  assert(_abandoned || _num_actual_forwardings == _num_expected_forwardings, "must enter exact number of forwardings, actual: %lu, expected: %lu", _num_actual_forwardings, _num_expected_forwardings);
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
    if (_abandoned) {
      log_debug(gc)("Forwarding table abandoned for region %zu: probe chain reached %zu "
                    "(forwardings=%zu, slots=%zu, live_words=%zu)",
                    _region->index(), _common_max_probes, num_entries, _num_entries, _num_live_words);
      reset();
      return false;
    }
    clear_unused_slots<Entry>(used);
    verify_forwardings<Entry>();
  }
  // Run at MaxPercent=100 with -Xlog:gc=debug to see the full distribution.
  if (initialized && log_is_enabled(Debug, gc)) {
    constexpr size_t entry_words = sizeof(Entry) / sizeof(HeapWord*);
    size_t const region_words = pointer_delta(_region->end(), _region->bottom());
    size_t const table_words = _num_entries * entry_words;
    log_debug(gc)("FWT build region %zu: forwardings=%zu, slots=%zu, table=%zu%% of region",
                  _region->index(), num_entries, _num_entries,
                  table_words * 100 / region_words);
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
  for (size_t i = 0; i < _num_entries; i++) {
    if (table[i].is_used()) {
      HeapWord* original = table[i].original(region_base);
      if (original >= TAMS) {
        bool was_upgraded;
        oop obj = cast_to_oop(original);
        ctx->mark_strong_ignore_tams(obj, was_upgraded);
      }
    }
  }
}

#endif

