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
#include "gc/shenandoah/shenandoahPrefetch.inline.hpp"

#include "utilities/align.hpp"
#include "utilities/fastHash.hpp"

inline bool FwdTableEntry::is_marked(ShenandoahMarkingContext* ctx) const {
 HeapWord* const orig_addr = cast_from_oop<HeapWord*>(cast_to_oop(&_original));
 HeapWord* const fwd_addr  = cast_from_oop<HeapWord*>(cast_to_oop(&_forwardee));
 return (is_object_aligned(orig_addr) && ctx->is_marked_ignore_tams(orig_addr)) ||
        (is_object_aligned(fwd_addr)  && ctx->is_marked_ignore_tams(fwd_addr));
}

inline bool FwdTableEntry::is_original(HeapWord* region_base, HeapWord* original) {
  return _original == (HeapWord*) (((uint64_t) original) | ENTRY_MARKER);
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
 HeapWord* const addr = reinterpret_cast<HeapWord*>(const_cast<uint64_t*>(&_encoded));
 return is_object_aligned(addr) && ctx->is_marked_ignore_tams(addr);
}

inline uint64_t ShenandoahForwardingTable::hash(HeapWord* original, void* table) {
 return FastHash::get_hash64(reinterpret_cast<uint64_t>(original), reinterpret_cast<uint64_t>(table));
}

inline void ShenandoahForwardingTable::probe_of(HeapWord* original, size_t& index, size_t& stride) const {
  uint64_t const h = hash(original, _table);
  assert(_num_entries <= (size_t) 0xffffffff, "Precondition");
  // Generalize this code for 32-bit deployments if necessary.
  assert(sizeof(size_t) == 8, "This code does not work on 32-bit hardware");
  uint64_t const truncated_h = h & 0xffffffff;
  // [0, N-1] from low bits of h
  // Assume that the low-order bits of h hold a "randomly distributed" pattern of bits derived from original and _table.
  // When we multiply _num_entries by truncated_h and divide by 0x100000000, we obtain a number that is greater than or
  // equal to 0 and less than or equal to _num_entries - 1.  If _truncated_bits are well distributed, the initial index
  // probe will also be well distributed.  We use multiply rather than divide because 64-bit multiply is up to nine times
  // faster than a 32-bit divide on typical "modern hardware", and multiply instructions are more effectively pipelined.
  // Since both factors are 32-bit, the 64-bit product will not overflow.
  index  = static_cast<size_t>((truncated_h * _num_entries) >> 32);
  assert(_num_entries > 16, "invariant");
  uint64_t basis = _num_entries / 16;
  uint64_t high_bits = h >> 32;
  uint64_t one_to_eight = (((high_bits >> 29) & 0x7) & 0x7) + 1;
  // Stride is between (1/16 of entries and 8/16 of entries) + some_portion of 1/16 of entries.
  stride = static_cast<size_t> (basis * one_to_eight + (basis & high_bits));
}

template<class Entry>
HeapWord* ShenandoahForwardingTable::forwardee(HeapWord* const original) const {
  Entry* table = reinterpret_cast<Entry*>(_table);
  size_t start_index, stride;
  probe_of(original, start_index, stride);
  size_t index = start_index;
  uint probes = 0;

  // In the case that we are searching a forwardee at the same time the collision chains are being pruned, we need
  // to make sure that we use the value of _max_required_probes that was valid at the moment we started our traversal.
  size_t max_required_probes = _max_required_probes;
  // If we see an update value of max_collision_depth, we also need to see the updated values of all original/forwardee pairs.
  OrderAccess::loadload();

  HeapWord* const region_base = _region->bottom();
  while (table[index].is_used()) {
    probes++;
    // A mark word is considered used because it has non-zero value.  A mark word will not match original because:
    //   1. With CompactFwdTableEntry, the mark word does not have ENTRY_MARKER bit set and the is_original() test
    //      requires this bit to be set.
    //   2. With non-compact FwdTableTnry, the mark word has "mark bits" set (0x07) and the original does not.
    if (table[index].is_original(region_base, original)) {
      HeapWord* result;
      Entry &entry = table[index];
      if (ShenandoahPruneFWTCollisionChains) {
        result = entry.forwardee_from_entry_with_barrier();
      } else {
        result = entry.forwardee_from_entry_without_barrier();
      }
      // We cannot enforce this assertion because pruning places forward table entries into marked locations.
#ifdef ASSERT
      ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
      assert(ShenandoahPruneFWTCollisionChains || (!is_object_aligned((HeapWord*) &table[index]) ||
                                                   !ctx->is_marked_ignore_tams((HeapWord*) &table[index])),
             "Do not expect forward entry is at forwarded markword location: " PTR_FORMAT
             " while forwarding " PTR_FORMAT " at index %zu with computed forwardee: " PTR_FORMAT,
             p2i((HeapWord*) &table[index]), p2i(original), index, p2i(result));
      assert(result == original || ShenandoahHeap::heap()->is_in(cast_to_oop(result)),
             "FWT forwardee " PTR_FORMAT " for original " PTR_FORMAT " region=%zu is outside heap",
             p2i(result), p2i(original), _region->index());
#endif
      return result;
    }

    if (probes >= max_required_probes) {
      break;
    }
    index += stride;
    if (index >= _num_entries) {
      index -= _num_entries;
    }
    if (index == start_index) {
      break;
    }
  }

  // Full GC resets the marking bitmap but still consults forwardees left over from an abandoned concurrent GC effort
  // before it begins its sliding compaction GC.
  assert(ShenandoahHeap::heap()->is_full_gc_in_progress() || !table[index].is_used() || (probes >= max_required_probes),
         "FWT probe miss for marked obj " PTR_FORMAT " region=%zu, probes: %u, max_required: %zu, is_used? %s",
         p2i(original), _region->index(), probes, max_required_probes, table[index].is_used()? "true": "false");
  return original;
}

template<class Entry>
inline void ShenandoahForwardingTable::insert_forwarding(size_t index, const Entry& entry) {
#undef KELVIN_DEBUG
#ifdef KELVIN_DEBUG
  log_info(gc)("insert_forwarding for region %zu, index: %zu (" PTR_FORMAT ", " PTR_FORMAT ")",
               _region->index(), index, p2i(entry.original(_region->bottom())), p2i(entry.forwardee_from_entry_without_barrier()));
#endif
  new (reinterpret_cast<Entry*>(_table) + index) Entry(entry);
}

template<class Entry>
inline void ShenandoahForwardingTable::prune_collision_chain(ShenandoahHeapRegion* region, HeapWord* original,
                                                      size_t& original_depth, size_t& new_depth) {
  size_t start_index, stride;
  Entry* table = reinterpret_cast<Entry*>(region->forwarding_table_start());
  HeapWord* const region_base = region->bottom();
  probe_of(original, start_index, stride);
  const size_t entry_words = sizeof(Entry) / sizeof(HeapWord*);
  HeapWord* const table_start = start();
  bool found_mark_word_collision = false;
  size_t index_of_mark_word_collision = 0;
  size_t depth_of_mark_word_collision = 0;
  size_t collision_chain_depth = 0;
  size_t index = start_index;
  ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
  while (table[index].is_used()) {
    size_t next_index = index + stride;
    if (next_index >= _num_entries) {
      next_index -= _num_entries;
    }
    // Issue the prefetch even before we know if we'll need this value; leave enough time to get the memory.
    ShenandoahPrefetch::prefetch(cast_to_oop(&table[next_index]));
    if (table[index].is_original(region_base, original)) {
      HeapWord* forwardee = table[index].forwardee_from_entry_without_barrier();
      original_depth = collision_chain_depth;
      if (found_mark_word_collision) {
        new_depth = depth_of_mark_word_collision;

        if constexpr (std::is_same_v<Entry, CompactFwdTableEntry>) {
          Entry const entry(region_base, original, forwardee);
          insert_forwarding<Entry>(index_of_mark_word_collision, entry);
        } else {
          table[index_of_mark_word_collision].overwrite_forwardee(forwardee);
          // Make sure that _forwardee is set before anybody has the opportunity to match the new value of _original.
          // The previous value of _original will not match, as it is a mark word.
          OrderAccess::storestore();
          // Note: there is no race on publication of original.  If some other thread sees the old value of original while
          // it is searching to resolve a forwarded address, it will eventually resolve.  It will just not see the "short cut".
          table[index_of_mark_word_collision].overwrite_original(original);
        }
      } else {
        // no change to depth of this collision chain
        new_depth = collision_chain_depth;
      }
      return;
    }

    if (!found_mark_word_collision && !table[index].is_entry()) {
      found_mark_word_collision = true;
      index_of_mark_word_collision = index;
      depth_of_mark_word_collision = collision_chain_depth;
    }
    collision_chain_depth++;
    index = next_index;
    assert(index != start_index, "Existing forward table should never wrap around to initial probe");
  }
  assert(false, "table[index].is_used() should be true until we find the forwardee");
}

// How many probes on the chain required to resolve original?
template<class Entry>
size_t ShenandoahForwardingTable::probes(HeapWord* original, size_t& stride) const {
  // This service is typically called during construction of forward table. We expect typical depth to be no more than 5
  // and expect forward table to be mostly in cache.  Thus, we iterate to end of chanin rather than trying to figure out
  // complicated wrap-around divide calculation.
  Entry* table = reinterpret_cast<Entry*>(_table);
  size_t start_index;
  probe_of(original, start_index, stride);
  size_t index = start_index;
  uint probes = 1;
  HeapWord* const region_base = _region->bottom();
  while (table[index].is_used()) {
    // A mark word is considered used because it has non-zero value.  A mark word will not match original because:
    //   1. With CompactFwdTableEntry and FwdTableEntry, the mark word does not have ENTRY_MARKER bit set and the
    //      is_original() test requires this bit to be set.
    //   2. With non-compact FwdTableTnry, the mark word has "mark bits" set (0x07) and the original does not.
    if (table[index].is_original(region_base, original)) {
      HeapWord* result;
      Entry &entry = table[index];
      if (ShenandoahPruneFWTCollisionChains) {
        result = entry.forwardee_from_entry_with_barrier();
      } else {
        result = entry.forwardee_from_entry_without_barrier();
      }
      assert(result == original || ShenandoahHeap::heap()->is_in(cast_to_oop(result)),
             "FWT forwardee " PTR_FORMAT " for original " PTR_FORMAT " region=%zu is outside heap",
             p2i(result), p2i(original), _region->index());
      return probes;
    }
    probes++;
    index += stride;
    if (index >= _num_entries) {
      index -= _num_entries;
    }
  }
  assert(false, "probes() must find forwardee");
  return probes;
}

template<class Entry>
size_t ShenandoahForwardingTable::reserve_forwarding(BitMap& used, size_t index, size_t stride, Entry& replaced,
                                                     size_t& replaced_index, size_t& replaced_stride, size_t& replaced_probes) {
#ifdef ASSERT
  size_t const first_index = index;
#endif
  size_t const max_probes = _common_max_probes;
  size_t depth = 1;
  HeapWord* const region_base = _region->bottom();
  Entry* table = reinterpret_cast<Entry*>(_table);
  while (used.at(index)) {
    if (max_probes != 0 && depth >= max_probes) {
      _abandoned = true; // pathological chain
      return _num_entries;
    }
    Entry& entry = table[index];
    if (entry.is_entry()) {
      replaced_probes = ShenandoahForwardingTable::probes<Entry>(entry.original(region_base), replaced_stride);
      if (replaced_probes < depth) {
        _num_actual_forwardings++;
        replaced_index = index;
        replaced = Entry(region_base, entry.original(region_base), entry.forwardee_from_entry_without_barrier());
#undef KELVIN_DEBUG
#ifdef KELVIN_DEBUG
        log_info(gc)("reserve_forwarding(region: %zu) replacing at depth %zu (" PTR_FORMAT ", " PTR_FORMAT ") at depth %zu with index %zu",
                     _region->index(), depth, p2i(entry.original(region_base)), p2i(entry.forwardee_from_entry_without_barrier()),
                     replaced_probes, index);
        if (depth >= 2) {
          log_info(gc)(" initial index: %zu, stride: %zu, num_entries: %zu", first_index, stride, _num_entries);
        }
#endif
        if (depth > _max_required_probes) {
          _max_required_probes = depth;
        }
        return index;
      }
#ifdef KELVIN_DEBUG
      else {
        log_info(gc)("Not replacing index %zu because its probe count is %zu and mine is %zu", index, replaced_probes, depth);
      }
#endif
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
#undef KELVIN_DEBUG
#ifdef KELVIN_DEBUG
  log_info(gc)("reserve_forwarding(region: %zu) at depth %zu with index %zu", _region->index(), depth, index);
  if (depth >= 2) {
    log_info(gc)(" initial index: %zu, stride: %zu, num_entries: %zu", first_index, stride, _num_entries);
  }
#endif
  return index;
}

template<class Entry>
size_t ShenandoahForwardingTable::reserve_new_forwarding(BitMap& used, size_t index, size_t stride, size_t depth, Entry& replaced,
                                                         size_t& replaced_index, size_t& replaced_stride, size_t& replaced_probes)
{
#ifdef ASSERT
  size_t const first_index = index;
#endif
  size_t const max_probes = _common_max_probes;
  HeapWord* const region_base = _region->bottom();
  Entry* table = reinterpret_cast<Entry*>(_table);
  while (used.at(index)) {
    if (max_probes != 0 && depth >= max_probes) {
      _abandoned = true; // pathological chain
      return _num_entries;
    }
    Entry& entry = table[index];
    if (entry.is_entry()) {
      replaced_probes = ShenandoahForwardingTable::probes<Entry>(entry.original(region_base), replaced_stride);
      if (replaced_probes < depth) {
        replaced_index = index;
        replaced = Entry(region_base, entry.original(region_base), entry.forwardee_from_entry_without_barrier());
#ifdef KELVIN_DEBUG
        log_info(gc)("reserve_new_forwarding(region: %zu) replacing at depth %zu (" PTR_FORMAT ", " PTR_FORMAT ") at depth %zu with index %zu",
                     _region->index(), depth, p2i(entry.original(region_base)), p2i(entry.forwardee_from_entry_without_barrier()),
                     replaced_probes, index);
        if (depth >= 2) {
          log_info(gc)(" initial index: %zu, stride: %zu, num_entries: %zu", first_index, stride, _num_entries);
        }
#endif
        if (depth > _max_required_probes) {
          _max_required_probes = depth;
        }
        return index;
      }
    }
#ifdef KELVIN_DEBUG
      else {
        log_info(gc)("Not replacing new-forwarded index %zu because its probe count is %zu and mine is %zu",
                     index, replaced_probes, depth);
      }
#endif
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
  assert(_num_actual_forwardings <= _num_expected_forwardings, "must not exceed number of forwardings");
#ifdef KELVIN_DEBUG
  log_info(gc)("reserve_new_forwarding(region: %zu) at depth %zu with index %zu",
               _region->index(), depth, index);
  if (depth >= 2) {
    log_info(gc)(" initial index: %zu, stride: %zu, num_entries: %zu", first_index, stride, _num_entries);
  }
#endif
  return index;
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDINGTABLE_INLINE_HPP
