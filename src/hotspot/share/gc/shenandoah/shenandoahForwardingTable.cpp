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

ShenandoahEarlyRecycleSubsetCoordinator _early_recycle_locks[NUM_EARLY_RECYCLE_SUBSETS];


HeapWord* CompactFwdTableEntry::_heap_base = nullptr;

// KELVIN TODO: Do we need this?  Explain the rationale.
template <bool use_forward_table>
uint32_t ShenandoahEarlyRecycleInfo<use_forward_table>::compute_common_max_probes() {
  uint32_t const overrun = ShenandoahForwardingTableProbeOverrun;
  if (overrun == 0) {
    return 0;
  }
  uint32_t const lf = ShenandoahForwardingTableLoadFactorPercent;
  uint32_t const largest_max_keys = (uint32_t)(ShenandoahHeapRegion::region_size_words()
                                               * ShenandoahCSetReuseMaxDensityPercent / 100);
  if (largest_max_keys == 0) {
    return 0;
  }
  assert(0 < lf && lf < 100, "load factor must be in (0, 100)");
  uint32_t const largest_max_slots = largest_max_keys * 100 / lf;
  double const alpha = (double)lf / 100.0;
  double const expected_max = ::log((double)largest_max_keys) / ::log(1.0 / alpha);
  uint32_t const probes = MAX2((size_t)(overrun * expected_max), (size_t)1);
  log_info(gc)("Forwarding table probe limit: %u (largest table %u slots, load factor %u%%, "
               "expected longest chain %.0f, overrun %u)",
               probes, largest_max_slots, lf,
               expected_max, overrun);
  return probes;
}

template <bool use_forward_table>
void ShenandoahEarlyRecycleInfo<use_forward_table>::initialize_globals() {
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

static bool is_prime(uint32_t n) {
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
static uint32_t next_prime(uint32_t n, uint32_t limit) {
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
      static uint32_t small_primes[] = { 23, 47, 71, 97, 113, 131, 151, 173, 193, 211, 233, 251, 271, 293, 313, 331 };
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
      static uint32_t medium_primes[] = { 401, 479, 557, 631, 701, 761, 829, 907, 977, 1021, 1049, 1069 };
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

// Returns 0 if cannot build forwarding table.
template <bool use_forward_table>
template <class Entry, bool b, typename>
uint32_t ShenandoahEarlyRecycleInfo<use_forward_table>::compute_entries(uint32_t num_forwardings) {
  // Find the minimum hashtable that satisfies the target load factor. Live
  // object headers falling within the table's range are unusable slots; the
  // search below grows the table to keep enough usable slots.
  constexpr uint32_t entry_words = sizeof(Entry) / sizeof(HeapWord*);
  // Entry aligned and suitable for count_mark_bit_conflicts().
  uint32_t const entry_obj_align = MAX2(entry_words * HeapWordSize, (uint32_t)MinObjAlignmentInBytes);
  HeapWord* const bottom =  _region->bottom();
  HeapWord* const top =  _region->top();
  HeapWord* const end = _region->end();
  // Usable slots to size for at the target load factor: ceil(num_forwardings * 100 / LF).
#ifdef DMITRY_BUILD
  HeapWord* const lo  = build._lo;
  HeapWord* const hi  = build._hi;
  HeapWord* const top = MIN2(build._carrier->top(), hi);
  // Usable slots to size for at the target load factor: ceil(num_entries * 100 / LF).
#endif

  assert(use_forward_table, "Do not be here");

  // Default LF = 60 -> 1.667x entries per forwarding, chosen to keep the average
  // double-hashing chain near 1.5 for a successful lookup (75 gives ~1.85, 85
  // ~2.2). A lower load factor yields a sparser table
  // with shorter probe chains (cheaper resolve and fill) but a larger tail, so
  // fewer dense regions fit; a higher one packs tighter.
  uint32_t const lf = ShenandoahForwardingTableLoadFactorPercent;
  // num_required_entries is the total number of entries in order to honor requested load factor.  Some entries are unusable.
  uint32_t unusable_entries = 0;
  uint32_t num_required_entries = ((num_forwardings + unusable_entries) * 100 + lf - 1) / lf;
  // Optimistic last possible table start (assuming no unusable entries). We don't need to search beyond that.
  HeapWord* const last_table_start = align_down(end - num_required_entries * entry_words, entry_obj_align);
  if (last_table_start < bottom) {
    log_info(gc)("Forwarding table build failed for region %zu: "
                 "required=%u entries of %u words exceed region_words=%u (num_forwardings=%u)",
                 _region->index(), num_required_entries, entry_words,
                 (uint32_t) pointer_delta(end, bottom), num_forwardings);
    return 0;
  }

  // Find a lower bound that satisfies the target load factor.  Start at the last possible address.  With each
  // iteration, we refine our estimate of num_required_entries as long as the intended growth spans additional
  // unusable entries.  The loop terminates when required growth contains no more unusable entries, or so few
  // that the round-off in the new num_required_entries computation is unaffected.
  ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
  uint32_t num_table_entries;
  if (last_table_start >= top) {
    // If unusable_entries is zero, there is no need for loop iterations below.
    num_table_entries = (end - last_table_start) / entry_words;
    assert(num_table_entries + unusable_entries >= num_required_entries, "Sanity");
  } else {

#define KELVIN_DEBUG
#ifdef KELVIN_DEBUG
    log_info(gc)("[%zu]: compute_entries(num_forwardings: %u), initial num_required: %u, last_table_start: " PTR_FORMAT,
                 _region->index(), num_forwardings, num_required_entries, p2i(last_table_start));
#endif

    HeapWord* table_start = last_table_start;
    uint32_t additional_unusable_entries = ctx->count_mark_bit_conflicts<entry_words>(last_table_start, top);
    unusable_entries += additional_unusable_entries;
    num_required_entries = ((num_forwardings + unusable_entries) * 100 + lf - 1) / lf;
    HeapWord* new_table_start = align_down(end - num_required_entries * entry_words, entry_obj_align);
    num_table_entries = (end - new_table_start) / entry_words;
    // num_table_entries may be larger than num_required_entries in case of "large object alignment".
    assert(num_table_entries >= num_required_entries, "sanity");

#ifdef KELVIN_DEBUG
    log_info(gc)("[%zu] loop prologue, additional unusable: %u, unusable: %u, num_table_entries: %u, lf: %u",
                 _region->index(), additional_unusable_entries, unusable_entries, num_table_entries,
                 (100 * (num_forwardings + unusable_entries)) / num_table_entries);
#endif

    while (true) {
      // Loop invariant:
      //       num_forwadings is the number of forwarding entries that the table must hold
      // Loop body precondition:
      //      new_table_start represents most recent guess for start address of fwt
      //          table_start represents previous guess for start address of fwt
      //     unusable_entries is the number of unusable entries between table_start and end
      // num_required_entries is the size of the table that is required to honor the load factor given
      //                      that num_forwardings plus unusable_entries are being consumed
      //
      // So new_table_start is based on num_required_entries, which is based on unusable_entries for table_start.
      // To decide whether table_start is a good enough guess, we have to check whether there are more unusable_entries
      // between new_table_start and table_start.

#ifdef KELVIN_DEBUG
      if (new_table_start > table_start) {
        log_info(gc)("WTF! new_table_start: " PTR_FORMAT ", table_start: " PTR_FORMAT,
                     p2i(new_table_start), p2i(table_start));
        log_info(gc)(" region is %zu, last_table_start: " PTR_FORMAT, _region->index(), p2i(last_table_start));
        log_info(gc)(" num_table_entries: %u, unusable_entries: %u", num_table_entries, unusable_entries);
        log_info(gc)(" bottom: " PTR_FORMAT ", top: " PTR_FORMAT ", end: " PTR_FORMAT,
                     p2i(_region->bottom()), p2i(_region->top()), p2i(_region->end()));
      }
#endif
      additional_unusable_entries = ctx->count_mark_bit_conflicts<entry_words>(new_table_start, table_start);
      unusable_entries += additional_unusable_entries;
      assert(new_table_start >= bottom, "table start must be in region");

#ifdef KELVIN_DEBUG
      log_info(gc)("[%zu] loop body, additional unusable: %u, unusable: %u, num_table_entries: %u, lf: %u",
                   _region->index(), additional_unusable_entries, unusable_entries, num_table_entries,
                   (100 * (num_forwardings + unusable_entries)) / num_table_entries);
      log_info(gc)("[%zu] loop body, new_table_start: " PTR_FORMAT ", table_start: " PTR_FORMAT,
                   _region->index(), p2i(new_table_start), p2i(table_start));
#endif
      table_start = new_table_start;

      if ((100 * (num_forwardings + unusable_entries)) / num_table_entries <= lf) {
        // We've found our result, as represented by table_start, num_table_entries, unusable_entries.
        break;
      } else {
        // Recompute num_required_entries because unusable_entries has increased since it was last computed.
        num_required_entries = ((num_forwardings + unusable_entries) * 100 + lf - 1) / lf;
        table_start = new_table_start;
        new_table_start = align_down(end - num_required_entries * entry_words, entry_obj_align);
        num_table_entries = (end - new_table_start) / entry_words;
        // num_table_entries may be larger than num_required_entries in case of "large object alignment".
        assert(num_table_entries >= num_required_entries, "sanity");
#ifdef KELVIN_DEBUG
        log_info(gc)("[%zu] bottom of loop body, recomputed num_required_entries: %u, new_table_start: " PTR_FORMAT,
                     _region->index(), num_required_entries, p2i(new_table_start));
#endif
        if (new_table_start <= bottom) {
          table_start = bottom;     // Force loop to abort with failure condition.
          num_table_entries = unusable_entries = 0;
          break;
        }
      }
    }
  }

#ifdef KELVIN_NICE_TO_HAVE
  // live_words is only needed during build so it has been removed from _u._fwt. If we remove it, we don't have
  // an easy way to enforce certain assertions.
  _u._fwt._num_live_words = unusable_entries;
#endif

#ifdef KELVIN_DEBUG
  log_info(gc)("Compute entries region %zu found num_required_entries: %u for forwardings: %u and unusable: %u, load factor: %.3f",
               _region->index(), num_required_entries, num_forwardings, unusable_entries, 
               (num_forwardings + unusable_entries) / ((double) num_required_entries));
#endif

  if (num_table_entries - unusable_entries < num_required_entries) {
    log_info(gc)("Forwarding table build failed for region %zu: "
                 "table_entries=%u unusable=%u required=%u num_forwardings=%u region_words=%u",
                 _region->index(), num_table_entries, unusable_entries, num_required_entries, num_forwardings,
                 (uint32_t) pointer_delta(end, bottom));
    return 0;
#ifdef DMITRY_BUILD
    assert((void*)(reinterpret_cast<Entry*>(desc._table) + desc._num_entries) == (void*)hi,
           "table must be anchored at storage range end");
    log_develop_debug(gc)("Initialized forwarding table: table: " PTR_FORMAT ", num_entries: %u, requested entries: %u",
                        p2i(desc._table), desc._num_entries, num_entries);
    return true;
#endif
  }

  // Prime table size >= 2 (a modulus of 1 would break the double-hashing stride) for
  // a later switch to double hashing.
  uint32_t const region_entries = (end - bottom) / entry_words;
  uint32_t prime_entries;
  if (num_table_entries < _max_prime_32bit) {
    prime_entries = next_prime(MAX2(num_table_entries, (uint32_t)2), region_entries);
  } else {
    prime_entries = 0;  /* overflow: table cannot be constructed */
  }

  if (prime_entries == 0) {
    log_info(gc)("Forwarding table build failed for region %zu: "
                 "no prime table size fits (table_entries=%u region_entries=%u num_forwardings=%u)",
                 _region->index(), num_table_entries, region_entries, num_forwardings);
    return 0;
  }
#ifdef KELVIN_DEBUG
  log_info(gc)("Compute entries chose prime size %u for region %zu, num_forwardings: %u, unusable_entries: %u, load_factor: %.3f",
               prime_entries, _region->index(), num_forwardings, unusable_entries,
               (num_forwardings + unusable_entries) / ((double) prime_entries));
#endif
  return prime_entries;
}

template <bool use_forward_table>
template <class Entry, bool b, typename>
bool ShenandoahEarlyRecycleInfo<use_forward_table>::initialize() {
  HeapWord* const end = _region->end();
  constexpr uint32_t entry_words = sizeof(Entry) / sizeof(HeapWord*);
  HeapWord* table_start = end - _u._fwt._num_entries * entry_words;
  _u._fwt._table = reinterpret_cast<Entry*>(table_start);
#ifdef ASSERT
  HeapWord* const bottom =  _region->bottom();
  assert(is_prime(_u._fwt._num_entries), "num_entries must be a prime number");
  assert(_u._fwt._num_entries <= _max_prime_32bit, "num_entries %u must fit in 32 bits for the multiply-shift probe reduction",
         _u._fwt._num_entries);
  uint32_t const lf = ShenandoahForwardingTableLoadFactorPercent;
  //  _u._fwt._num_expected_forwardings should have been set previously.
#ifdef KELVIN_DEPRECATE
  assert(_u._fwt._num_entries >= (_u._fwt._num_expected_forwardings * 100 + lf - 1) / lf, "Enforce load factor");
#else
  if (_u._fwt._num_entries < (_u._fwt._num_expected_forwardings * 100 + lf - 1) / lf) {
    log_info(gc)("num_entries: %u for expected forwardings %u violates load factor %u",
                 _u._fwt._num_entries, _u._fwt._num_expected_forwardings, lf);
  }
#endif
  HeapWord* table_address = (HeapWord*) _u._fwt._table;
  assert((table_address >= bottom) && (table_address < end) && is_aligned(table_address, entry_words * HeapWordSize),
         "_table must be within range and aligned to entry");
#endif
    assert((void*)(reinterpret_cast<Entry*>(_u._fwt._table) + _u._fwt._num_entries) == (void*)_region->end(),
           "table must be anchored at region end");
    log_develop_debug(gc)("Initialized forwarding table: table: " PTR_FORMAT ", num_entries: %u, forwardings: %u",
                          p2i(_u._fwt._table), _u._fwt._num_entries, _u._fwt._num_expected_forwardings);


  _u._fwt._max_required_probes = 0;
  _status.store_relaxed(ShenandoahEarlyRecycleStatus::Planned_FWT);

  assert((void*)(reinterpret_cast<Entry*>(_u._fwt._table) + _u._fwt._num_entries) == (void*)_region->end(),
         "table must be anchored at region end");
  log_develop_debug(gc)("Initialized forwarding table: table: " PTR_FORMAT ", num_entries: %u, requested entries: %u",
                        p2i(_u._fwt._table), _u._fwt._num_entries, _u._fwt._num_expected_forwardings);
  return true;
}

template <bool use_forward_table>
template <class Entry, bool b, typename>
void ShenandoahEarlyRecycleInfo<use_forward_table>::set_marked_entries_used(BitMap& used) {
#ifdef DMITRY
  const fwt_info& desc = build._desc;
#endif
  assert((void*)(reinterpret_cast<Entry*>(_u._fwt._table) + _u._fwt._num_entries) == (void*)_region->end(),
         "table must be anchored at region end");

  ShenandoahMarkingContext* const ctx = ShenandoahHeap::heap()->marking_context();
#ifdef DMITRY
  HeapWord* const top = MIN2(build._carrier->top(), build._hi);
  assert(ctx->top_at_mark_start(build._carrier) == build._carrier->top(), "TAMS must be at top during table build");
#else
  HeapWord* const top = _region->top();
  assert(ctx->top_at_mark_start(_region) == top, "TAMS must be at top during table build");
#endif

  HeapWord* const table_start = reinterpret_cast<HeapWord*>(_u._fwt._table);
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

// KELVIN TODO:Not sure that we need to do this.  Looks like extra work. I think the problem is if we don't clear the unused slots,
// then we may not be able to detect the end of a search for a newly allocated object that is not in the table.  It will see
// non-zero data in the forwarding table and think this is "meaningful data".  So maybe we really do need to do this.  Can
// we at least add a comment to clarify why this needs to be done.
template <bool use_forward_table>
template <class Entry, bool b, typename>
void ShenandoahEarlyRecycleInfo<use_forward_table>::clear_unused_slots(const BitMap& used) {
  Entry* table = reinterpret_cast<Entry*>(_u._fwt._table);
  BitMap::idx_t current = used.find_first_clear_bit(0);
  while (current < _u._fwt._num_entries) {
    new (&table[current]) Entry();
    current = used.find_first_clear_bit(current + 1);
  }
}

template <bool use_forward_table>
template <class Entry, bool b, typename>
bool ShenandoahEarlyRecycleInfo<use_forward_table>::enter_forwarding(BitMap& used, HeapWord* original, HeapWord* forwardee,
                                                                     uint32_t& num_actual, 
                                                                     Entry& replaced, uint32_t& replaced_index,
                                                                     uint32_t& replaced_stride, uint32_t& replaced_probes) {

  
  assert(get_status() != ShenandoahEarlyRecycleStatus::Abandoned, "Precondition");
  Entry* const table = reinterpret_cast<Entry*>(_u._fwt._table);
  uint32_t const num_entries = _u._fwt._num_entries;
#ifdef DMITRY_BUILD
  if (!build._complete) {
    return;
  }
  fwt_info& desc = build._desc;
  Entry* const table = reinterpret_cast<Entry*>(desc._table);
  uint32_t const num_entries = desc._num_entries;
#endif
  uint32_t index, stride;
  probe_of(table, num_entries, original, index, stride);
  Entry const entry(_region->bottom(), original, forwardee);
  index = reserve_forwarding<Entry>(used, index, stride, num_actual, replaced, replaced_index, replaced_stride, replaced_probes);
  if (index >= _u._fwt._num_entries) {
#ifdef KELVIN_DEBUG
    log_info(gc)("enter_forwarding() craps out in reserve_forwarding, num_actual: %u", num_actual);
#endif
    return false;
  } else {
    assert(index < _u._fwt._num_entries, "Too many forwarded entries: %u vs. expected: %u, entries: %u",
           index, _u._fwt._num_expected_forwardings, _u._fwt._num_entries);
    insert_forwarding<Entry>(table, index, entry);
    return true;
  }
}

template <bool use_forward_table>
template <class Entry, bool b, typename>
bool ShenandoahEarlyRecycleInfo<use_forward_table>::reenter_forwarding(BitMap& used, HeapWord* original, HeapWord* forwardee,
                                                                       uint32_t index, uint32_t stride, uint32_t probes,
                                                                       Entry& replaced, uint32_t& replaced_index,
                                                                       uint32_t& replaced_stride, uint32_t& replaced_probes) {
  assert(get_status() != ShenandoahEarlyRecycleStatus::Abandoned, "Precondition");
  Entry* const table = reinterpret_cast<Entry*>(_u._fwt._table);
  Entry const entry(_region->bottom(), original, forwardee);
  index = reserve_new_forwarding<Entry>(used, index, stride, probes, replaced, replaced_index, replaced_stride, replaced_probes);
  if (index >= _u._fwt._num_entries) {
    return false;
  } else {
    assert(index < _u._fwt._num_entries, "Too many reserves: %u", index);
    insert_forwarding<Entry>(table, index, entry);
    return true;
#ifdef DMITRY_BUILD
    if (!build._complete) {
      return;
    }
    fwt_info& desc = build._desc;
    Entry* const table = reinterpret_cast<Entry*>(desc._table);
    uint32_t const num_entries = desc._num_entries;
    Entry const entry(_region->bottom(), original, forwardee);
    index = reserve_new_forwarding<Entry>(build, used, index, stride, probes, replaced, replaced_index, replaced_stride, replaced_probes);
    if (index == num_entries) {
      assert(!build._complete, "only an abandoned table reserves no slot");
      return;
    }
    insert_forwarding<Entry>(table, index, entry);
#endif
  }
}

#ifdef KELVIN_NICE_TO_HAVE
template <bool use_forward_table>
template <class Entry, bool b, typename> 
void ShenandoahEarlyRecycleInfo<use_forward_table>::log_fwt_stats(const fwt_build& build) const {
#ifndef PRODUCT
  const fwt_info& desc = build._desc;
  log_debug(gc)("Forwarding table load factor: %f",
                (float)(build._num_actual + build._num_live) / (float) (desc._num_entries));
  log_debug(gc)("Forwarding table size: %u entries (== %u bytes)",
                desc._num_entries, (uint32_t)(sizeof(Entry) * desc._num_entries));
  log_debug(gc)("Forwarding table expected: %u, actual: %u, live words: %u",
                build._num_expected, build._num_actual, build._num_live);
#endif
}
#endif

template <bool use_forward_table>
template <class Entry, bool b, typename> 
void ShenandoahEarlyRecycleInfo<use_forward_table>::log_no_tbl_stats() const {
  log_debug(gc)("Early recycle region with no forwarding table, density: %.3f", _u._no_fwt._density_at_most_recent_sift);
}

template <bool use_forward_table>
template <class Entry, bool b, typename>
bool ShenandoahEarlyRecycleInfo<use_forward_table>::fill_forwardings(BitMap& used) {
  class FillForwardingsClosure {
    ShenandoahEarlyRecycleInfo& _fwt;
    HeapWord* _region_base;
    BitMap&         _used;
    HeapWord* const _fwt_start;
    size_t    const _region_idx;
    uint32_t _num_actual;
    // KELVIN TODO: can we abort the object iterator once we decide to abandon forwarding of this region?
    //  Introduce a new iterator, that only continues while do_object() returns true.
    bool _abandoned;

    public:
    FillForwardingsClosure(ShenandoahEarlyRecycleInfo& fwt, BitMap& used, size_t region_idx)
        : _fwt(fwt), _region_base(fwt.region()->bottom()), _used(used),
          _fwt_start(reinterpret_cast<HeapWord*>(_fwt._u._fwt._table)),
          _region_idx(region_idx), _num_actual(0), _abandoned(false) {
#define KELVIN_DEBUG
#ifdef KELVIN_DEBUG
      log_info(gc)("fill_forwardings for region %zu with num_entries: %u, num_expected_forwardings: %u",
                   region_idx, _fwt._u._fwt._num_entries, _fwt._u._fwt._num_expected_forwardings);
#endif
    }

    uint32_t num_actual() {
      return _num_actual;
    }

    void do_object(oop obj) {
      if (!_abandoned) {
        HeapWord* original = cast_from_oop<HeapWord*>(obj);
        HeapWord* forwardee = cast_from_oop<HeapWord*>(ShenandoahForwarding::get_forwardee_raw(obj));
#ifndef PRODUCT
        if (forwardee != original) {
          assert(ShenandoahHeap::heap()->is_in(cast_to_oop(forwardee)),
                 "FWT fill: forwardee " PTR_FORMAT " for original " PTR_FORMAT " region=%zu is outside heap",
                 p2i(forwardee), p2i(original), _region_idx);
        }
#ifdef DMITRY
        // kelvin doesn't understand this code.  why only a warning? shouldn't we assert out here if _fwt_start == nullptr?
        // same questions below in my version of the code.
        else if (_fwt_start != nullptr && (original < _fwt_start || original >= _build._hi)) {
          log_warning(gc)("FWT fill: body object " PTR_FORMAT " region=%zu is self-forwarded (not evacuated)",
                          p2i(original), _region_idx);
        }
#endif
        else if (_fwt_start != nullptr && original < _fwt_start) {
          log_warning(gc)("FWT fill: body object " PTR_FORMAT " region=%zu is self-forwarded (not evacuated)",
                          p2i(original), _region_idx);
        }
#endif  // PRODUCT
        Entry replaced = Entry();       // no-arg constructor builds Entry that !is_entry()
        uint32_t index;
        uint32_t stride;
        uint32_t probe_count;
        if (!_fwt.enter_forwarding<Entry>(_used, original, forwardee, _num_actual, replaced, index, stride, probe_count)) {
#ifdef KELVIN_DEBUG
          log_info(gc)("fill_forwardings() crapped out in enter_forwarding(), num_actual: %u", _num_actual);
#endif
          _abandoned = true;
        } else {
          assert(_num_actual <= _fwt._u._fwt._num_expected_forwardings,
                 "Too many filled entries, _num_actual: %u, expected_forwardings: %u", _num_actual,
                 _fwt._u._fwt._num_expected_forwardings);
          // replaced.is_entry() means I overwrote an existing entry and that existing entry needs to find a new location.
          // Replacement can happen in enter_forwarding() and in reenter_forwarding().
          while (replaced.is_entry()) {
            original = replaced.original(_region_base);
            forwardee = replaced.forwardee_from_entry_without_barrier();
            replaced.reset();
            if (!_fwt.reenter_forwarding<Entry>(_used, original, forwardee,
                                                index, stride, probe_count,
                                                replaced, index, stride, probe_count)) {
#ifdef KELVIN_DEBUG
              log_info(gc)("fill_forwardings craps out in reenter_forwarding(), num_actual: %u", _num_actual);
#endif
              _abandoned = true;

            }
          }
        }
      }
    }
  } cl(*this, used, _region->index());

  ShenandoahHeap::heap()->marked_object_iterate(_region, &cl);
  if (get_status() == ShenandoahEarlyRecycleStatus::Abandoned) {
#ifdef KELVIN_DEBUG
    log_info(gc)("fill_forwardings() returns false because regions status is Abandoned");
#endif
    return false;
  } else {
    assert(cl.num_actual() == _u._fwt._num_expected_forwardings,
           "must enter exact number of forwardings, actual: %u, expected: %u", cl.num_actual(),
           _u._fwt._num_expected_forwardings);
    return true;
  }
#ifdef KELVIN_NICE_TO_HAVE
  // We removed _num_actual_forwardings from _u._fwt because it's only needed during build.  Think about how to
  // make it available for assertion enforcement.
  assert((get_status() == ShenandoahEarlyRecycleStatus::Abandoned)
         || (_u._fwt._num_actual_forwardings == _u._fwt._num_expected_forwardings),
         "must enter exact number of forwardings, actual: %u, expected: %u", _u._fwt._num_actual_forwardings,
         _u._fwt._num_expected_forwardings);
#endif
#ifdef DMITRY_BUILD
  assert(!build._complete || build._num_actual == build._num_expected,
         "must enter exact number of forwardings, actual: %u, expected: %u", build._Num_actual, build._num_expected);,
#endif
#ifdef KELVIN_NICE_TO_HAVE
  log_fwt_stats<Entry>(build);
#endif
}

#ifndef PRODUCT
template <bool use_forward_table>
template <class Entry, bool b, typename>
void ShenandoahEarlyRecycleInfo<use_forward_table>::verify_forwardings() {
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
      guarantee(actual_forwardee == expected_forwardee,
                "Forwardees in mark-word and table must match: original: " PTR_FORMAT
                ", mark-forwardee: " PTR_FORMAT ", found forwardee: " PTR_FORMAT,
                p2i(original), p2i(expected_forwardee), p2i(actual_forwardee));

      if (expected_forwardee != original) {
        guarantee(!ShenandoahHeap::heap()->in_collection_set(cast_to_oop(expected_forwardee)),
                  "forwardee " PTR_FORMAT " for original " PTR_FORMAT " is in CSet (region=%zu)",
                  p2i(expected_forwardee), p2i(original), _region->index());
      }
    }
    start = original + 1;
  }

  // Every used slot is either a preserved mark word or a real entry with marked original.
  Entry* table = reinterpret_cast<Entry*>(_u._fwt._table);
  for (size_t i = 0; i < _u._fwt._num_entries; i++) {
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

template <bool use_forward_table>
template<class Entry, bool b, typename>
bool ShenandoahEarlyRecycleInfo<use_forward_table>::build() {
  bool initialized = initialize<Entry>();
#ifdef DMITRY_BUILD
bool ShenandoahEarlyRecycleInfo<use_forward_table>::build(uint32_t num_entries) {
  fwt_build build = {};
  ShenandoahHeapRegion* const carrier = _region;
  build._num_expected = num_entries;
  build._carrier = carrier;
  build._lo = carrier->bottom();
  build._hi = carrier->end();
  bool const initialized = initialize<Entry>(build);
#endif
  if (initialized) {
    // Track used slots in a scratch bitmap during construction, then zero
    // only the unused slots. This avoids pre-zeroing slots that fill overwrites.
    ResourceMark rm;
    ResourceBitMap used(_u._fwt._num_entries);
    set_marked_entries_used<Entry>(used);
#ifdef KELVIN_DEBUG
    log_info(gc)("build() for region %zu has been innitialized for num_entries %u", _region->index(), _u._fwt._num_entries);
#endif
    if (!fill_forwardings<Entry>(used)) {
      assert(get_status() == ShenandoahEarlyRecycleStatus::Abandoned, "If fill_forwardings fails, status should be Abandoned");
#ifdef KELVIN_NICE_TO_HAVE
      log_debug(gc)("Forwarding table abandoned for region %zu: probe chain reached %u "
                    "(forwardings=%u, slots=%u, live_words=%u)", _region->index(), _common_max_probes,
                    _u._fwt._num_expected_forwardings, _u._fwt._num_entries, _u._fwt._num_live_words);
#endif
      reset();
      return false;
    }
    // Before we publish, it is important to clear unused slots and adjust top.
    clear_unused_slots<Entry>(used);
    _region->set_alt_top(_region->top());
    _region->set_top(_region->bottom());
    OrderAccess::fence();
    bool result = _status.compare_set(ShenandoahEarlyRecycleStatus::Planned_FWT, ShenandoahEarlyRecycleStatus::Prepared_FWT);
    assert(result, "state machine");
#ifdef DMITRY_BUILD
    ResourceBitMap used(build._desc._num_entries);
    set_marked_entries_used<Entry>(build, used);
    fill_forwardings<Entry>(build, used);
    if (!build._complete) {
      log_debug(gc)("Forwarding table abandoned for region %zu: probe chain reached %u "
                    "(forwardings=%u, slots=%u, live_words=%u)",
                    _region->index(), _common_max_probes, num_entries, build._desc._num_entries, build._num_live);
      return false;
    }
    _u._fwt = build._desc;
#endif
    verify_forwardings<Entry>();
  }
  // Run at MaxPercent=100 with -Xlog:gc=debug to see the full distribution.
  if (initialized && log_is_enabled(Debug, gc)) {
    constexpr size_t entry_words = sizeof(Entry) / sizeof(HeapWord*);
    size_t const region_words = pointer_delta(_region->end(), _region->bottom());

    size_t const table_words = _u._fwt._num_entries * entry_words;
    log_debug(gc)("FWT build region %zu: forwardings=%u, slots=%u, table=%u%% of region",  _region->index(),
                  _u._fwt._num_expected_forwardings, _u._fwt._num_entries, (uint32_t) (table_words * 100 / region_words));
#ifdef DMITRY_BUILD
    size_t const table_words = build._desc._num_entries * entry_words;
    log_debug(gc)("FWT build region %zu: forwardings=%u, slots=%u, table=%u%% of region",
                  _region->index(), num_entries, build._desc._num_entries,
                  (uint32_t) (table_words * 100 / region_words));
#endif
  }
  return initialized;
}

template <bool use_forward_table>
template <bool b, typename>
bool ShenandoahEarlyRecycleInfo<use_forward_table>::build() {
  if (_compact) {
    return build<CompactFwdTableEntry>();
  } else {
    return build<FwdTableEntry>();
  }
}

template <bool use_forward_table>
template<class Entry, bool b, typename>
void ShenandoahEarlyRecycleInfo<use_forward_table>::prune_collision_chains() {
  Entry* table = reinterpret_cast<Entry*>(_u._fwt._table);
  HeapWord* const region_base = _region->bottom();
  uint32_t total_pruned_collisions = 0;
  uint32_t max_pruned_collisions = 0;
  for (size_t index = 0; index < _u._fwt._num_entries; index++) {
    if (table[index].is_entry()) {
      HeapWord* const original = table[index].original(region_base);
      HeapWord* const forwardee = table[index].forwardee_from_entry_without_barrier();
      size_t pruned_depth = prune_collision_chain<Entry>(original, forwardee);
      total_pruned_collisions += pruned_depth;
      if (pruned_depth > max_pruned_collisions) {
        max_pruned_collisions = pruned_depth;
      }
    }
  }
  log_debug(gc, fwt)("Pruned region %zu (depth: %u), pruned collisions: %u, max: %u",
                     _region->index(), max_required_probes(),
                     total_pruned_collisions, max_pruned_collisions);
  overwrite_max_required_probes(max_pruned_collisions + 1);
}

template <bool use_forward_table>
template <bool b, typename>
void ShenandoahEarlyRecycleInfo<use_forward_table>::prune_collision_chains() {
  if (_compact) {
    prune_collision_chains<CompactFwdTableEntry>();
  } else {
    prune_collision_chains<FwdTableEntry>();
  }
}

#ifdef USE_SENTINELS
template <bool use_forward_table>
template<class Entry, bool b, typename>
void ShenandoahEarlyRecycleInfo<use_forward_table>::write_at_originals(uintptr_t word, HeapWord* from, HeapWord* to) {
  assert(_u._fwt._table != nullptr, "FWT must be built before writing sentinels");
  Entry* table = reinterpret_cast<Entry*>(_u._fwt._table);
  HeapWord* region_base = _region->bottom();
  // Footprint == min_fill_size so the hole left between reused allocations is always a fillable
  // object; the original's object is >= min_fill_size, so these words never reach the next one.
  const size_t fill_words = ShenandoahHeap::min_fill_size();
  for (size_t i = 0; i < _u._fwt._num_entries; i++) {
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
    for (size_t i = 0; i < _u._fwt._num_entries; i++) {
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

template <bool use_forward_table>
void ShenandoahEarlyRecycleInfo<use_forward_table>::install_sentinels() {
  HeapWord* fwt_start = start();
  HeapWord* bottom    = _region->bottom();
  if (_compact) {
    write_at_originals<CompactFwdTableEntry>(ShenandoahHeap::in_fwt_sentinel, bottom, fwt_start);
  } else {
    write_at_originals<FwdTableEntry>(ShenandoahHeap::in_fwt_sentinel, bottom, fwt_start);
  }
}
#else

#ifdef DMITRY_BUILD
// KELVIN THINKS WE CAN DEPRECATE ADD_MARKS_ABOVE_TAMS().  NOT
// NECESSARY.  REDUNDANT.
template <bool use_forward_table>
template <class Entry, bool b, typename>
void ShenandoahEarlyRecycleInfo<use_forward_table>::add_marks_above_tams() {
  assert(_u._fwt._table != nullptr, "FWT must be built before writing sentinels");
  Entry* table = reinterpret_cast<Entry*>(_u._fwt._table);
  ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
  HeapWord* TAMS = ctx->top_at_mark_start(_region);
  HeapWord* region_base = _region->bottom();
  for (size_t i = 0; i < _u._fwt._num_entries; i++) {
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

template <bool use_forward_table>
size_t ShenandoahEarlyRecycleInfo<use_forward_table>::estimate_reusable_words(size_t num_forwardings, size_t live_words) const {
  size_t const region_words = ShenandoahHeapRegion::region_size_words();
  if (region_words == 0) {
    return 0;
  }
  double const density = (double) live_words / (double) region_words;
  size_t body_span = region_words;
  if constexpr (use_forward_table) {
    if (num_forwardings > 0 && density < 1.0) {
      uint32_t const lf = ShenandoahForwardingTableLoadFactorPercent;
      size_t const entry_words = use_compact() ? 1 : (sizeof(FwdTableEntry) / sizeof(HeapWord*));
      size_t const slots_words = (num_forwardings * 100 / lf) * entry_words;
      size_t const table_words = (size_t) ((double) slots_words / (1.0 - density));
      body_span = region_words > table_words ? region_words - table_words : 0;
    }
  }
  return (size_t) ((double) body_span * (1.0 - density));
}
#endif

// The non-in-lined definitions above need to be explicitly instantiated or they will fail to link.

template class ShenandoahEarlyRecycleInfo<true>;
template class ShenandoahEarlyRecycleInfo<false>;

template void ShenandoahEarlyRecycleInfo<true>::prune_collision_chains<true, void>();

template bool ShenandoahEarlyRecycleInfo<true>::build<FwdTableEntry, true, void>();
template bool ShenandoahEarlyRecycleInfo<true>::build<CompactFwdTableEntry, true, void>();
template void ShenandoahEarlyRecycleInfo<true>::prune_collision_chains<FwdTableEntry, true, void>();
template void ShenandoahEarlyRecycleInfo<true>::prune_collision_chains<CompactFwdTableEntry, true, void>();

template uint32_t ShenandoahEarlyRecycleInfo<true>::compute_entries<CompactFwdTableEntry, true, void>(uint32_t);
template uint32_t ShenandoahEarlyRecycleInfo<true>::compute_entries<FwdTableEntry, true, void>(uint32_t);


#endif

