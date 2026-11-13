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
 *
 */

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONALEVACUATIONTASK_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONALEVACUATIONTASK_HPP

#include "gc/shared/workerThread.hpp"

class ShenandoahGeneration;
class ShenandoahGenerationalHeap;
class ShenandoahHeapRegion;
class ShenandoahRegionIterator;

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


// Unlike ShenandoahEvacuationTask, this iterates over all regions rather than just the collection set.
// This is needed in order to promote humongous start regions if age() >= tenure threshold.
class ShenandoahGenerationalEvacuationTask : public WorkerTask {
private:
  ShenandoahGenerationalHeap* const _heap;
  ShenandoahMarkingContext* _ctx;
  ShenandoahCollectionSet* _cset;
  ShenandoahGeneration* const _generation;
  ShenandoahRegionIterator* _regions;
  size_t _num_workers;
  bool _concurrent;
  bool _only_promote_regions;   // if only promote, we do not early recycle
  Atomic<size_t> _early_recycle_preprocessed_regions;

public:
  ShenandoahGenerationalEvacuationTask(ShenandoahGenerationalHeap* sh,
                                       ShenandoahGeneration* generation,
                                       ShenandoahRegionIterator* iterator,
                                       bool concurrent, bool only_promote_regions, size_t num_workers);
  void work(uint worker_id) override;
private:
  void do_work(uint worker_id);
  void promote_regions(uint worker_id);
  void evacuate_regions(uint worker_id);
  void maybe_promote_region(ShenandoahHeapRegion* region);
  void promote_in_place(ShenandoahHeapRegion* region);
  void promote_humongous(ShenandoahHeapRegion* region);
  void yield_or_sleep(uint32_t& yields);
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONALEVACUATIONTASK_HPP
