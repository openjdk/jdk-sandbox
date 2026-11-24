/*
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

#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahForwardingTable.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahGenerationalEvacuationTask.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahInPlacePromoter.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"

ShenandoahGenerationalEvacuationTask::ShenandoahGenerationalEvacuationTask(ShenandoahGenerationalHeap* heap,
                                                                           ShenandoahCollectionSet* cset,
                                                                           ShenandoahRegionIterator* iterator,
                                                                           bool concurrent, bool only_promote_regions,
                                                                           bool require_preprocessing, size_t num_workers) :
    ShenandoahEvacuationTask(heap, cset, concurrent, require_preprocessing, num_workers),
    _regions(iterator),
    _only_promote_regions(only_promote_regions)
{
  shenandoah_assert_generational();
}

size_t ShenandoahGenerationalEvacuationTask::shortfall() const {
  return _heap->young_generation()->heuristics()->mutator_memory_shortfall();
}

void ShenandoahGenerationalEvacuationTask::work(uint worker_id) {
  if (_concurrent) {
    ShenandoahWorkerTimingsTracker timer(ShenandoahPhaseTimings::conc_evac, ShenandoahPhaseTimings::Work, worker_id, true);
    SuspendibleThreadSetJoiner stsj;
#undef KELVIN_SUSPENDIBLE
#ifdef KELVIN_SUSPENDIBLE
    log_info(gc)("worker %u becomes suspendible in work()", worker_id);
#endif
    ShenandoahConcurrentWorkerSession worker_session(worker_id);
    do_work(worker_id);
#ifdef KELVIN_SUSPENDIBLE
    log_info(gc)("worker %u detaches suspendible in work()", worker_id);
#endif
  } else {
    ShenandoahWorkerTimingsTracker timer(ShenandoahPhaseTimings::degen_gc_evac, ShenandoahPhaseTimings::Work, worker_id, true);
    ShenandoahParallelWorkerSession worker_session(worker_id);
    do_work(worker_id);
  }
}

void ShenandoahGenerationalEvacuationTask::do_work(uint worker_id) {
  if (_only_promote_regions) {
    assert(_cset->is_empty(), "Should not have a collection set here");
    promote_regions(worker_id);
  } else {
    assert(!_cset->is_empty(), "Should have a collection set here");
    evacuate_regions(worker_id);
    promote_regions(worker_id);
  }
}

static void log_region(const ShenandoahHeapRegion* r, LogStream* ls) {
  ls->print_cr("GenerationalEvacuationTask, looking at %s region %zu, (age: %d) [%s, %s, %s]",
               r->is_old()? "old": r->is_young()? "young": "free", r->index(), r->age(),
               r->is_active()? "active": "inactive",
               r->is_humongous()? (r->is_humongous_start()? "humongous_start": "humongous_continuation"): "regular",
               r->is_cset()? "cset": "not-cset");
}

void ShenandoahGenerationalEvacuationTask::promote_regions(uint worker_id) {
#ifdef KELVIN_DEBUG
  log_info(gc)("worker %u is done with promote_regions()", worker_id);
#endif
  LogTarget(Debug, gc) lt;
  ShenandoahInPlacePromoter promoter((ShenandoahGenerationalHeap*) _heap);
  ShenandoahHeapRegion* r;
  while ((r = _regions->next()) != nullptr) {
    if (lt.is_enabled()) {
      LogStream ls(lt);
      log_region(r, &ls);
    }
    promoter.maybe_promote_region(r);
#ifdef KELVIN_SUSPENDIBLE
    log_info(gc)("Suspendible worker %u yields in promote_regions()", worker_id);
#endif
    if (_heap->check_cancelled_gc_and_yield(_concurrent)) {
      break;
    }
  }
}

