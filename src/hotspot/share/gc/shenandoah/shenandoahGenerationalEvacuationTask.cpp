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
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahGenerationalEvacuationTask.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahInPlacePromoter.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"

class ShenandoahConcurrentEvacuator : public ObjectClosure {
private:
  ShenandoahGenerationalHeap* const _heap;
  Thread* const _thread;
  ShenandoahMarkingContext* const _context;
  HeapWord* _tams;
  size_t _num_forwardings;
public:
  explicit ShenandoahConcurrentEvacuator(ShenandoahGenerationalHeap* heap) :
          _heap(heap), _thread(Thread::current()),
          _context(heap->marking_context()), _tams(nullptr), _num_forwardings(0) {}

  void set_region(ShenandoahHeapRegion* r) {
    _tams = _context->top_at_mark_start(r);
    _num_forwardings = 0;
  }

  void finish_region(ShenandoahHeapRegion* r) {
    // Advance TAMS to top so that get_last_marked_addr in build_forwarding_table
    // can search the full [bottom, top) range of the evacuated region.
    _context->capture_top_at_mark_start(r);
  }

  void do_object(oop p) override {
    shenandoah_assert_marked(nullptr, p);
    _num_forwardings++;
    if (!p->is_forwarded()) {
      _heap->evacuate_object(p, _thread);
    }
    // Mark objects beyond TAMS so that their headers are findable when
    // building the forwarding table (mirrors ShenandoahConcurrentEvacuateRegionObjectClosure).
    if (cast_from_oop<HeapWord*>(p) >= _tams) {
      bool upgraded = false;
      _context->mark_strong_ignore_tams(p, upgraded);
      assert(!upgraded, "should be first mark");
    }
    assert(_context->is_marked(p), "must be marked");
  }

  size_t num_forwardings() const { return _num_forwardings; }
};

ShenandoahGenerationalEvacuationTask::ShenandoahGenerationalEvacuationTask(ShenandoahGenerationalHeap* heap,
                                                                           ShenandoahGeneration* generation,
                                                                           ShenandoahRegionIterator* iterator,
                                                                           bool concurrent, bool only_promote_regions) :
  WorkerTask("Shenandoah Evacuation"),
  _heap(heap),
  _generation(generation),
  _regions(iterator),
  _concurrent(concurrent),
  _only_promote_regions(only_promote_regions)
{
  shenandoah_assert_generational();
}

void ShenandoahGenerationalEvacuationTask::work(uint worker_id) {
  if (_concurrent) {
    ShenandoahConcurrentWorkerSession worker_session(worker_id);
    // Join the suspendible thread set only for the promote-only path
    // to avoid double-joining.
    ShenandoahSuspendibleThreadSetJoiner stsj(_only_promote_regions);
    do_work();
  } else {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    do_work();
  }
}

void ShenandoahGenerationalEvacuationTask::do_work() {
  if (_only_promote_regions) {
    // No allocations will be made, do not enter oom-during-evac protocol.
    assert(_heap->collection_set()->is_empty(), "Should not have a collection set here");
    promote_regions();
  } else {
    assert(!_heap->collection_set()->is_empty(), "Should have a collection set here");
    evacuate_and_promote_regions();
  }
}

void log_region(const ShenandoahHeapRegion* r, LogStream* ls) {
  ls->print_cr("GenerationalEvacuationTask, looking at %s region %zu, (age: %d) [%s, %s, %s]",
              r->is_old()? "old": r->is_young()? "young": "free", r->index(), r->age(),
              r->is_active()? "active": "inactive",
              r->is_humongous()? (r->is_humongous_start()? "humongous_start": "humongous_continuation"): "regular",
              r->is_cset()? "cset": "not-cset");
}

void ShenandoahGenerationalEvacuationTask::promote_regions() {
  LogTarget(Debug, gc) lt;
  ShenandoahInPlacePromoter promoter(_heap);
  ShenandoahHeapRegion* r;
  while ((r = _regions->next()) != nullptr) {
    if (lt.is_enabled()) {
      LogStream ls(lt);
      log_region(r, &ls);
    }

    promoter.maybe_promote_region(r);

    if (_heap->check_cancelled_gc_and_yield(_concurrent)) {
      break;
    }
  }
}

void ShenandoahGenerationalEvacuationTask::evacuate_and_promote_regions() {
  LogTarget(Debug, gc) lt;
  ShenandoahConcurrentEvacuator cl(_heap);
  ShenandoahInPlacePromoter promoter(_heap);
  ShenandoahHeapRegion* r;

  while ((r = _regions->next()) != nullptr) {
    if (lt.is_enabled()) {
      LogStream ls(lt);
      log_region(r, &ls);
    }

    if (r->is_cset()) {
      assert(r->has_live(), "Region %zu should have been reclaimed early", r->index());
      if (_heap->collection_set()->use_forward_table(r)) {
        // Already evacuated (degenerated GC resumes here).
        continue;
      }
      size_t num_forwardings;
      {
        ShenandoahSuspendibleThreadSetJoiner stsj(_concurrent);
        ShenandoahEvacOOMScope oom_evac_scope;
        cl.set_region(r);
        _heap->marked_object_iterate(r, &cl);
        if (_heap->check_cancelled_gc_and_yield(_concurrent)) {
          break;
        }
        cl.finish_region(r);
        num_forwardings = cl.num_forwardings();
      }
      // Build the forwarding table outside the stsj+oom scope, after the
      // region's objects have been evacuated.
      _heap->finish_region_evacuation(r, num_forwardings, _concurrent);
    } else {
      ShenandoahSuspendibleThreadSetJoiner stsj(_concurrent);
      promoter.maybe_promote_region(r);
      if (_heap->check_cancelled_gc_and_yield(_concurrent)) {
        break;
      }
    }
  }
}
