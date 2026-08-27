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

class FwdTableEntry {
  HeapWord* _original;
  HeapWord* _forwardee;
public:
  // Default-constructed entries read as unused.
  FwdTableEntry() : _original(nullptr), _forwardee(nullptr) {}
  FwdTableEntry(HeapWord* region_base, HeapWord* original, HeapWord* forwardee) : _original(original), _forwardee(forwardee) {}

  HeapWord* original(HeapWord* region_base) const { return _original; }

  // OrderAccess::loadload() is only needed if we prune collisions chains concurrently during the start of update refs.
  HeapWord* forwardee_from_entry_with_barrier() const { OrderAccess::loadload(); return _forwardee; }
  HeapWord* forwardee_from_entry_without_barrier() const { return _forwardee; }

  void overwrite_forwardee(HeapWord* new_value) {
    _forwardee = new_value;
  }

  void overwrite_original(HeapWord* new_value) {
    _original = new_value;
  }

  bool is_marked(ShenandoahMarkingContext* ctx) const;
  // Used on the forwardee lookup path. At construction time the scratch BitMap is used instead.
  bool is_used() const { return _original != nullptr || _forwardee != nullptr; }
  bool is_original(HeapWord* region_base, HeapWord* original);
};

class CompactFwdTableEntry {
  static const uint64_t ENTRY_MARKER = uint64_t(1) << 63;
  static const uint64_t ORIGINAL_BITS = 22; // Enough to encode 32M regions.
  static const uint64_t FORWARDEE_BITS = 63 - ORIGINAL_BITS; // Spare the uppermost bit to identify an entry

  static const int FORWARDEE_SHIFT = 0;
  static const int ORIGINAL_SHIFT = FORWARDEE_SHIFT + FORWARDEE_BITS;

  static const uint64_t ORIGINAL_MASK = right_n_bits(ORIGINAL_BITS) << ORIGINAL_SHIFT;
  static const uint64_t FORWARDEE_MASK = right_n_bits(FORWARDEE_BITS) << FORWARDEE_SHIFT;

  static HeapWord* _heap_base;
  uint64_t const _encoded;

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
  bool is_used() const { return _encoded != 0; }
  bool is_original(HeapWord* region_base, HeapWord* original);
};

class ShenandoahForwardingTable {
  static bool _compact;

  ShenandoahHeapRegion* const _region;
  void* _table;
  size_t _num_entries;
  size_t _num_expected_forwardings;
  size_t _num_actual_forwardings;
  size_t _num_live_words;
  size_t _max_collision_depth;

  template<class Entry>
  bool build(size_t num_forwardings);

  template<class Entry>
  bool initialize(size_t num_forwardings);

  template<class Entry>
  void set_marked_entries_used(BitMap& used);

  template<class Entry>
  void clear_unused_slots(const BitMap& used);

  static uint64_t hash(HeapWord* original, void* table);

  void probe_of(HeapWord* original, size_t& index, size_t& stride) const;

  template<class Entry>
  size_t reserve_forwarding(BitMap& used, size_t index, size_t stride);

  template<class Entry>
  inline void insert_forwarding(size_t index, const Entry& entry);

  template<class Entry>
  void enter_forwarding(BitMap& used, HeapWord* original, HeapWord* forwardee);

  template<class Entry>
  void fill_forwardings(BitMap& used);

  template<class Entry>
  void log_stats() const;

  template<class Entry>
  void verify_forwardings() PRODUCT_RETURN;

#ifdef USE_SENTINELS
  template<class Entry>
  void write_at_originals(uintptr_t word, HeapWord* from, HeapWord* to);
#else
  template<class Entry>
  void add_marks_above_tams();
#endif

public:
  ShenandoahForwardingTable(ShenandoahHeapRegion* region) :
    _region(region), _table(nullptr), _num_entries(0),
    _num_expected_forwardings(0),
    _num_actual_forwardings(0),
    _num_live_words(0),
    _max_collision_depth(0) {}

  void overwrite_max_collision_depth(size_t new_max_depth) {
    // Make sure all overwrites of original/forwardee pairs have been updated before we announce an improved collision depth
    OrderAccess::storestore();
    _max_collision_depth = new_max_depth;
  }

  size_t max_collision_depth() {
    return _max_collision_depth;
  }

  static bool use_compact() { return _compact; }
  static void initialize_globals();

  bool build(size_t num_forwardings);

  void reset() {
    _table = nullptr;
    _num_entries = 0;
  }

  HeapWord* start() const {
    return reinterpret_cast<HeapWord*>(_table);
  }
#ifdef USE_SENTINELS
  void install_sentinels();
#endif

  template<class Entry>
  inline void prune_collision_chain(ShenandoahHeapRegion* region, HeapWord* original,
                             size_t& original_depth, size_t& new_depth);

  template<class Entry>
  HeapWord* forwardee(HeapWord* orginal) const;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDINGTABLE_HPP
