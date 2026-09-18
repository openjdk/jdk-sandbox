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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDINGTABLE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDINGTABLE_HPP

#include "gc/shared/fullGCForwarding.hpp"
#include "utilities/globalDefinitions.hpp"

class BitMap;
class ShenandoahHeapRegion;
class ShenandoahMarkingContext;

enum ShenandoahEarlyRecycleSubsets {
  TLAB_ALLOCATABLES,
  SHARED_ALLOCATABLES,
  NO_TBL_ALLOCATABLES,
  NUM_EARLY_RECYCLE_SUBSETS
};

class FwdTableEntry {
  static const uint64_t ENTRY_MARKER = uint64_t(1) << 63;
  HeapWord* _original;
  HeapWord* _forwardee;
public:
  // Default-constructed entries read as unused.
  FwdTableEntry() : _original(nullptr), _forwardee(nullptr) {}
  FwdTableEntry(HeapWord* region_base, HeapWord* original, HeapWord* forwardee) :
      _original((HeapWord*) (((uint64_t) original) | ENTRY_MARKER)), _forwardee(forwardee) {}

  HeapWord* original(HeapWord* region_base) const { return (HeapWord*) (((uint64_t) _original) & ~ENTRY_MARKER); }

  // OrderAccess::loadload() is only needed if we prune collisions chains concurrently during the start of update refs.
  HeapWord* forwardee_from_entry_with_barrier() const { OrderAccess::loadload(); return _forwardee; }
  HeapWord* forwardee_from_entry_without_barrier() const { return _forwardee; }

  void overwrite_forwardee(HeapWord* new_value) {
    _forwardee = new_value;
  }

  void overwrite_original(HeapWord* new_value) {
    _original = (HeapWord*) (((uint64_t) new_value) | ENTRY_MARKER);
  }

  bool is_marked(ShenandoahMarkingContext* ctx) const;
  bool is_entry() const { return ((uint64_t) _original) & ENTRY_MARKER; }
  // Used on the forwardee lookup path. At construction time the scratch BitMap is used instead.
  bool is_used() const { return _original != nullptr || _forwardee != nullptr; }
  bool is_original(HeapWord* region_base, HeapWord* original);
  void reset() { _forwardee = nullptr; _original = nullptr; };
};

// Use CompactFwdTableEntry if the region size is <= 32M and the total heap size is less than 2^(41+3) = 16 GB
class CompactFwdTableEntry {
  static const uint64_t ENTRY_MARKER = uint64_t(1) << 63;
  static const uint64_t ORIGINAL_BITS = 22; // Enough to encode 32M regions.
  static const uint64_t FORWARDEE_BITS = 63 - ORIGINAL_BITS; // Spare the uppermost bit to identify an entry

  static const int FORWARDEE_SHIFT = 0;
  static const int ORIGINAL_SHIFT = FORWARDEE_SHIFT + FORWARDEE_BITS;

  static const uint64_t ORIGINAL_MASK = right_n_bits(ORIGINAL_BITS) << ORIGINAL_SHIFT;
  static const uint64_t FORWARDEE_MASK = right_n_bits(FORWARDEE_BITS) << FORWARDEE_SHIFT;

  static HeapWord* _heap_base;
  uint64_t _encoded;

  static uint64_t encode(HeapWord* region_base, HeapWord* original, HeapWord* forwardee);

  static HeapWord* decode_original(HeapWord* region_base, uint64_t encoded);
  static HeapWord* decode_forwardee(uint64_t encoded);

public:
  // Default-constructed entries read as unused.
  CompactFwdTableEntry() : _encoded(0) {}
  CompactFwdTableEntry(HeapWord* region_base, HeapWord* original, HeapWord* forwardee) :
      _encoded(encode(region_base, original, forwardee)) {}

  static constexpr size_t max_region_size_words() {
    return size_t(1) << ORIGINAL_BITS;
  }

  static constexpr size_t max_heap_size_words() {
    // We can't encode the last word, because of the way we setup heap-base. See below.
    return (size_t(1) << FORWARDEE_BITS) - 1;
  }

  static void set_heap_base(HeapWord* heap_base) {
    // Intentionally assume heap-base is one word lower. This way we
    // can never get a valid encoding of 0. We want to use 0 as 'unused'.
    _heap_base = heap_base - 1;
  }

  HeapWord* original(HeapWord* region_base) const { return decode_original(region_base, _encoded); }
  // CompactFwdTableEntry::forwardee never uses a fence

  // No barrier required for CompactFwdTableEntry
  HeapWord* forwardee_from_entry_with_barrier() const { return decode_forwardee(_encoded); }
  HeapWord* forwardee_from_entry_without_barrier() const { return decode_forwardee(_encoded); }

  bool is_marked(ShenandoahMarkingContext* ctx) const;
  // Used on the forwardee lookup path. At construction time the scratch BitMap is used instead.
  bool is_entry() const { return _encoded & ENTRY_MARKER; }
  bool is_used() const { return _encoded != 0; }
  bool is_original(HeapWord* region_base, HeapWord* original);
  void reset() { _encoded = 0; }
};

#define CACHE_LINE_SIZE_IN_BYTES 64

// Align each subset coordinator on a different cache line
class alignas(CACHE_LINE_SIZE_IN_BYTES) ShenandoahEarlyRecycleSubsetCoordinator {
  // High-order 32 bits represent the number of requests made since last prioritization of allocation regions.
  // Low-order 32 bits represent the number of requests made since last prioritization of allocation regions have been completed.
  volatile uint64_t _requests_and_completions;
  uint32_t _size;

public:

  static const uint64_t LOCK_SUBSET_SENTINEL = 0xffffffffffffffffUL;
  static const uint32_t NO_REGION_FOUND = 0xffffffffUL;

  ShenandoahEarlyRecycleSubsetCoordinator(): 
    _requests_and_completions(0),
    _size(0) {
  }

  // Busy wait for all region locks to be individually released; then locks the entire subset.
  // the 
  void lock_entire_subset() {
    // first raise _requests to _size to prevent new mutator requests,
    // then wait for in-process requests to finish
    // Then set L

  }

  // This unlocks the entire subset lock, setting size to the value of num_regions argument.
  void reset_lock(uint32_t num_regions) {
    _size = num_regions;
  }

  // Returns the index of the locked region within its respective subset, or NO_REGION_FOUND if we cannot lock a region.
  uint32_t lock_one_region() {

    return NO_REGION_FOUND;
  }

  // Assumes we hold the lock.  
  void unlock_one_region() {

  }
  
};

extern ShenandoahEarlyRecycleSubsetCoordinator _early_recycle_locks[];

template <bool use_forward_table>
class alignas(CACHE_LINE_SIZE_IN_BYTES) ShenandoahEarlyRecycleInfo {

  static inline uint32_t _common_max_probes = 0;

  // Depends on region size and heap size only. All forwarding tables in JVM use same encodin
  static inline bool _compact = false;

  // A Graviton-2 cache line is 64 bytes, representing 8 words.  All of the following instance fields should fit in a single
  // cache line.  The first 5 fields are accessed on the hot path through forwardee(original). If !use_forward_table, the
  // count resets each time completions equals requests.
  ShenandoahHeapRegion* const _region;
  ShenandoahMarkingContext* _ctx;


  // Two instance fields above consume 16 bytes
  // The _no_fwt_info union member consumes 8 + 4*4 = 24 bytes
  // the _fwt_info union member consumes 8 + 5*4 + 2 = 30 bytes

  union {
    struct no_fwt_info {
      // No-fwt allocatable regions are sorted by mark-word density. We prefer to allocate in regions that have low mark-word
      // density, as allocations are easier with fewer conflicts.  As allocations are made, mark words may be "consumed" (or
      // skipped over) by the allocations, changing the density of available memory above top.
      // Occasionally, we resift the allocatable no-fwt regions to reprioritize allocations from the no-fwt allocatable
      // regions.

      // What is the next marked word above current _region->top()?
      HeapWord* _next_marked_cursor;

      // _density_at_most_recent_sift initially holds the density computed at final mark. Its value is updated
      // each time we re-sort the no-fwt regions into allocation priority order.  _density is computed as 
      // (evacuated_objects - _consumed_mark_words) / (_region->end() - _region->top())
      float _density_at_most_recent_sift;
      // _evacuated_objects is initialized at mark, based on how many total objects were marked. By the time we early recycle
      // this region, all of these objects will have been evacuaetd.
      uint32_t _evacuated_objects;
      // This is initialized to zero at final mark, is updated following each allocation to represent the number of markword
      // spanned by the waste associated with the allocation.
      uint32_t _consumed_mark_words;

    } _no_fwt;
    struct fwt_info {
      void* _table;
      // uint32_t _num_entries in forwarding table has max value 2^32 == 4M.  Since each entry consumes at least 8 bytes,
      // this is sufficient to consume an entire region of size 32M. This matches the Shenandoah definition of MAX_REGION_SIZE.
      // Note that G1 GC has a larger maximum region size, 512 MB. Even that can be supported with a uint32_t forward table size.
      // In that configuration, each entry in the forward table consumes 16 bytes, so the maximum forward table would by 64M,
      // representing 12.5% of the region size. Generally, we would not want to try to forward more than approximately 10% of
      // a heap region's content, especially for such large heap regions.
      uint32_t _num_entries;
      uint32_t _max_required_probes;
      uint32_t _num_expected_forwardings;
      uint32_t _num_actual_forwardings;  // kelvin says we should not need to keep this info around.  We can tally
                                         // up the number of actual forwardings when we build the forward table and can
                                         // validate at that time that it equals _num_expected_forewardings.
      uint32_t _num_live_words;     // Number of mark words spanned by the fwt.  We also do not need to keep this around
      bool _abandoned;
      bool _fullgc_fixup;
    } _fwt;
  } _u;

  static uint32_t compute_common_max_probes();

  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  bool initialize(uint32_t num_forwardings);

  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  void set_marked_entries_used(BitMap& used);

  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  void clear_unused_slots(const BitMap& used);

  template <bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  static uint64_t hash(HeapWord* original, void* table);

  template <bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  inline void probe_of(HeapWord* original, uint32_t& index, uint32_t& stride) const;

  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  inline uint32_t reserve_forwarding(BitMap& used, uint32_t index, uint32_t stride, Entry& replaced,
                                   uint32_t& replaced_index, uint32_t& replaced_stride, uint32_t& replaced_probes);

  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  inline uint32_t reserve_new_forwarding(BitMap& used, uint32_t index, uint32_t stride, uint32_t probes,
                                       Entry& replaced, uint32_t& replaced_index, uint32_t& replaced_stride,
                                       uint32_t& replaced_probes);

  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  inline void insert_forwarding(uint32_t index, const Entry& entry);

  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  void enter_forwarding(BitMap& used, HeapWord* original, HeapWord* forwardee,
                        Entry& replaced, uint32_t& replaced_index, uint32_t& replaced_stride, uint32_t& replaced_probes);

  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  void reenter_forwarding(BitMap& used, HeapWord* original, HeapWord* forwardee,
                          uint32_t index, uint32_t stride, uint32_t probed_count,
                          Entry& replaced, uint32_t& replaced_index, uint32_t& replaced_stride, uint32_t& replaced_probes);

  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  void fill_forwardings(BitMap& used);

  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  void log_fwt_stats() const;

  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == false>>
  void log_no_tbl_stats() const;

  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  void verify_forwardings() PRODUCT_RETURN;

#ifdef USE_SENTINELS
  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  void write_at_originals(uintptr_t word, HeapWord* from, HeapWord* to);
#else
  // KELVIN THINKS WE DO NOT NEED THIS AND CAN DEPRECATE.
  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  void add_marks_above_tams();
#endif

public:
  ShenandoahEarlyRecycleInfo(ShenandoahHeapRegion* region) :
    _region(region),
    _ctx(ShenandoahHeap::heap()->marking_context()) {
    if (use_forward_table) {
      _u._fwt._table = nullptr;
      _u._fwt._num_entries = 0;
      _u._fwt._max_required_probes = 0;
      _u._fwt._num_expected_forwardings = 0;
      _u._fwt._num_actual_forwardings = 0;
      _u._fwt._num_live_words = 0;
      _u._fwt._abandoned = false;
      _u._fwt._fullgc_fixup = false;
    } else {
      _u._no_fwt._next_marked_cursor = nullptr;
      _u._no_fwt._density_at_most_recent_sift = 0.0;
      _u._no_fwt._evacuated_objects = 0;
      _u._no_fwt._consumed_mark_words = 0;
    }
  }

  template <bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  void overwrite_max_required_probes(uint32_t new_max_probes) {
    // Make sure all overwrites of original/forwardee pairs have been updated before we announce an improved collision depth
    OrderAccess::storestore();
    _u._fwt._max_required_probes = new_max_probes;
  }

  template <bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  uint32_t max_required_probes() {
    return _u._fwt._max_required_probes;
  }

  template <bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  void start_fullgc() {
    _u._fwt._fullgc_fixup = true;
  }

  template <bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  static bool use_compact() { return _compact; }

  static void initialize_globals();

  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  bool build(uint32_t num_forwardings);

  template <bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  bool build(uint32_t num_forwardings);    

  template <bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  void reset() {
    _u._fwt._table = nullptr;
    _u._fwt._num_entries = 0;
    _u._fwt._abandoned = false;
    _u._fwt._fullgc_fixup = false;
  }

  template <bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  HeapWord* start() const {
    return reinterpret_cast<HeapWord*>(_u._fwt._table);
  }

  ShenandoahHeapRegion* region() const { return _region; }

  uint32_t num_live_words() const { return _num_live_words; }

#ifdef USE_SENTINELS
  template <bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  void install_sentinels();
#endif

  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  inline uint32_t prune_collision_chain(HeapWord* original, HeapWord* forwardee);

  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  void prune_collision_chains();

  template <bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  void prune_collision_chains();

  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  HeapWord* forwardee(HeapWord* orginal) const;

  template <class Entry, bool b = use_forward_table, typename = std::enable_if_t<b == true>>
  inline uint32_t probes(HeapWord* original, uint32_t& stride) const;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDINGTABLE_HPP
