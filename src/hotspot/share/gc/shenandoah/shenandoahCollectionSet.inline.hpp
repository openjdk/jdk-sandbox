/*
 * Copyright (c) 2017, 2020, Red Hat, Inc. All rights reserved.
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
 *
 */

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHCOLLECTIONSET_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHCOLLECTIONSET_INLINE_HPP

#include "gc/shenandoah/shenandoahCollectionSet.hpp"

#include "gc/shenandoah/shenandoahCSetMap.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"

bool ShenandoahCollectionSet::is_in(size_t region_idx) const {
  assert(region_idx < _heap->num_regions(), "Sanity");
  return _cset_map.is_in(region_idx);
}

bool ShenandoahCollectionSet::is_in(ShenandoahHeapRegion* r) const {
  return _cset_map.is_in(r);
}

bool ShenandoahCollectionSet::is_in(oop p) const {
  shenandoah_assert_in_heap_bounds_or_null(nullptr, p);
  return _cset_map.is_in(p);
}

bool ShenandoahCollectionSet::is_in_loc(void* p) const {
  assert(p == nullptr || _heap->is_in_reserved(p), "Must be in the heap");
  return _cset_map.is_in_loc(p);
}

CSetState ShenandoahCollectionSet::cset_state(oop obj) const {
  return _cset_map.cset_state(obj);
}

CSetState ShenandoahCollectionSet::cset_state(ShenandoahHeapRegion* const region) const {
  return _cset_map.cset_state(region);
}

bool ShenandoahCollectionSet::use_forward_table(oop obj) const {
  return _cset_map.use_forward_table(obj);
}

bool ShenandoahCollectionSet::use_forward_table(ShenandoahHeapRegion* r) const {
  return _cset_map.use_forward_table(r);
}

bool ShenandoahCollectionSet::is_midcycle(oop obj) const {
  return _cset_map.is_midcycle(obj);
}

bool ShenandoahCollectionSet::is_reusable(ShenandoahHeapRegion* r) const {
  return _cset_map.is_reusable(r);
}

bool ShenandoahCollectionSet::is_reuse_eligible(ShenandoahHeapRegion* r) const {
  // ShenandoahUpdateThreadRootsAndFlushOldSatbBuffers relies on cset regions
  // staying intact until after update-refs. Refilling a cset region mid-cycle
  // breaks that invariant, so later marks crash. The flag is frozen for the
  // cycle after op_init_mark (covers reuse window).
  if (ShenandoahHeap::heap()->is_concurrent_old_mark_in_progress()) {
    return false;
  }

  return !r->is_old() && !r->is_pinned() && !r->was_promoted_in_place() && !r->has_self_forwards();
}

bool ShenandoahCollectionSet::is_planned_for_reuse(ShenandoahHeapRegion* r) const {
  return _planned_for_reuse.at(r->index());
}

void ShenandoahCollectionSet::set_planned_for_reuse(ShenandoahHeapRegion* r) {
  _planned_for_reuse.set_bit(r->index());
}

size_t ShenandoahCollectionSet::get_live_bytes_in_old_regions() const {
  return _old_bytes_to_evacuate;
}

size_t ShenandoahCollectionSet::get_live_bytes_in_untenurable_regions() const {
  return _young_bytes_to_evacuate - _young_bytes_to_promote;
}

size_t ShenandoahCollectionSet::get_live_bytes_in_tenurable_regions() const {
  return _young_bytes_to_promote;
}

size_t ShenandoahCollectionSet::get_old_garbage() const {
  return _old_garbage;
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHCOLLECTIONSET_INLINE_HPP
