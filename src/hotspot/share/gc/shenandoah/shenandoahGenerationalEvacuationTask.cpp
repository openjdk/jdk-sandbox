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
                                                                           bool concurrent, bool only_promote_regions,
                                                                           size_t num_workers) :
  WorkerTask("Shenandoah Evacuation"),
  _heap(heap),
  _ctx(heap->marking_context()),
  _cset(heap->collection_set()),
  _generation(generation),
  _regions(iterator),
  _num_workers(num_workers),
  _concurrent(concurrent),
  _only_promote_regions(only_promote_regions),
  _early_recycle_preprocessed_regions(0)
{
  shenandoah_assert_generational();
}

void ShenandoahGenerationalEvacuationTask::yield_or_sleep(uint32_t& yields) {
  // This code was borrowed from ShenandoahLock, where it was tested with different values of N, and chose 3 for best performance.
  // Simple yield-sleep policy: do one 100us sleep after every N yields.
  if (yields < 3) {
    os::naked_yield();
    yields++;
  } else {
    os::naked_short_nanosleep(100000);
    yields = 0;
  }
}

void ShenandoahGenerationalEvacuationTask::work(uint worker_id) {
  if (_concurrent) {
    ShenandoahWorkerTimingsTracker timer(ShenandoahPhaseTimings::conc_evac, ShenandoahPhaseTimings::Work, worker_id, true);
    SuspendibleThreadSetJoiner stsj;
#define KELVIN_SUSPENDIBLE
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

void log_region(const ShenandoahHeapRegion* r, LogStream* ls) {
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
  ShenandoahInPlacePromoter promoter(_heap);
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

void ShenandoahGenerationalEvacuationTask::evacuate_regions(uint worker_id) {
  ShenandoahHeapRegion* r;
  size_t fwt_regions = 0;
  size_t region_size_words = ShenandoahHeapRegion::region_size_words();
  size_t region_size_bytes = region_size_words * HeapWordSize;
  size_t entry_size_words = ShenandoahEarlyRecycleInfo<true>::entry_size_words();
  size_t entry_size_bytes = entry_size_words * HeapWordSize;
  size_t num_regions = _heap->num_regions();
  size_t cset_region_count = _cset->count();
  ShenandoahFreeSet* free_set = _heap->free_set();
#define KELVIN_DEBUG
#ifdef KELVIN_DEBUG
  log_info(gc)("ShenGenEvacTask::evac_and_promote_regions(%u)", worker_id);
#endif
  // Even a non-concurrent degen can benefit from early recycling of collection set regions.
  if (worker_id == 0) {
    // The first worker is responsible for initializing early-recycle state variables.
    free_set->set_first_unpublished_fwt(0);
    free_set->set_upper_bound_on_unpublished_fwt(0);
    free_set->set_first_unclaimed_young_fwt(0);
    free_set->set_upper_bound_on_unclaimed_young_fwt(0);
    free_set->set_first_unclaimed_old_fwt(0);
    free_set->set_upper_bound_on_unclaimed_old_fwt(0);
    free_set->set_fwt_regions_prepared(0);
    free_set->set_fwt_bytes_prepared(0);
    free_set->set_fwt_regions_published(0);
    free_set->set_fwt_bytes_published(0);
    free_set->set_fwt_regions_consumed_by_gc(0);
    free_set->set_fwt_bytes_consumed_by_young_gc(0);
    free_set->set_fwt_bytes_consumed_by_old_gc(0);
    free_set->set_old_fwt_bytes_to_mutator(0);
    free_set->set_young_fwt_bytes_to_mutator(0);
    free_set->set_fwt_regions_retired(0);
    free_set->set_fwt_bytes_retired(0);
  }
#ifdef KELVIN_DEBUG
  log_info(gc)("ShenGenEvacTask::evac_and_promote_regions(%u) is selecting fwt regions", worker_id);
#endif
  // Regions have already been sorted in increasing live data, which is a light-weight approximation of increasing
  // forward table size (aka increasing mark-word-density).

  // Set aside enough regions for early recycle with FWT to satisfy the anticipated memory shortfall.
  // Each worker is responsible for preparing 1/_num_workers of the total shortfall

  size_t total_shortfall = _heap->young_generation()->heuristics()->mutator_memory_shortfall();
  size_t my_shortfall = total_shortfall / _num_workers;
  if (worker_id == 0) {
    my_shortfall = total_shortfall - (_num_workers - 1) * my_shortfall;
  }
  size_t backfill = 0;
  size_t my_preprocessed_regions = 0;
  for (size_t i = worker_id; i < cset_region_count; i += worker_id) {
    my_preprocessed_regions++;
    r = _cset->cset_region(i);
    size_t num_entries;
    if (backfill < my_shortfall) {
      // For now, calculate num_forwardings only for regions that we plan to early recycle with forwarding table.
      // As the non-fwt regions are evacuated, we will update their expected forwardings. The order of regions within
      // the early recycle regions array is approximately the order of increasing mark-word density.
      size_t num_forwardings = _ctx->count_mark_bit_conflicts<1>(r->bottom(), r->end());
      // Objects allocated during concurrent mark are not yet recognized as "marked", so count them separately.
      HeapWord* top = r->top();
      for (HeapWord* p = _ctx->top_at_mark_start(r); p < top; ) {
        oop obj = cast_to_oop(p);
        num_forwardings++;
        p += ShenandoahForwarding::size(obj);
      }
#ifdef KELVIN_SUSPENDIBLE
      log_info(gc)("worker %u checks for yield in preprocessing if _concurrent: %s",
                   worker_id, _concurrent? "true": "false");
#endif
      if (_concurrent) {
        if (SuspendibleThreadSet::should_yield()) {
          SuspendibleThreadSet::yield();
        }
      }
      size_t num_entries = r->calculate_entries_in_forward_table(num_forwardings);
      if (num_entries > 0) {
        size_t early_available_words = region_size_words - entry_size_words * num_entries;
        backfill += early_available_words;
#ifdef KELVIN_DEBUG
        log_info(gc)("Recycled FWT region %zu: live: %zu, %zu entries (of size %zu), available: %zu, backfill: %zu",
                     r->index(), r->get_live_data_words(), num_entries, entry_size_words, early_available_words, backfill);
#endif
        r->set_early_recycle_status(ShenandoahEarlyRecycleStatus::Planned_FWT);
        // KELVIN TODO: we don't really need forwardings except for assertion enforcement.  (build-only)
        // We should remove from ShenandoahHeapRegion EarlyRecycleInfo.
        r->set_expected_forwardings(num_forwardings);
        r->set_fwt_entries(num_entries);
        fwt_regions++;
#ifdef KELVIN_DEBUG
        log_info(gc)("Region %zu is Planned_FWT with %zu forwardings, %zu entries", r->index(), num_forwardings, num_entries);
#endif
        continue;
      }
    }
    // This region will not be early recycled with forward table.
    r->set_fwt_entries(0);
  }

#ifdef KELVIN_TRACE
  log_info(gc)("Worker %u is done preprocessing my regions: %zu, fwt regions: %zu",
               worker_id, my_preprocessed_regions, fwt_regions);
  log_info(gc)("  my shortfall: %zu, backfill: %zu", my_shortfall, backfill);
#endif

  // Once we identify that preprocessing is finished, other threads may consult the fields we've initialized above.
  OrderAccess::storeload();

  // Signal to all other workers that my early-release preprocessing is done.
  size_t other_preprocessed_regions;
  do {
    other_preprocessed_regions = _early_recycle_preprocessed_regions.load_relaxed();
  } while (!_early_recycle_preprocessed_regions.compare_set(other_preprocessed_regions,
                                                            other_preprocessed_regions + my_preprocessed_regions,
                                                            memory_order_relaxed));

  ShenandoahConcurrentEvacuator cl(_heap);
  // Concurrent GC workers evacuate regions in order of increasing live data. They start with the regions that have
  // the least amount of live data, so the first few regions evacuated may go very fast.
  static const size_t QueueSize = 8;
  uint32_t await_queue_forwardings[QueueSize];
  uint32_t await_queue_cset_indices[QueueSize];
  size_t queued_regions = 0;
  bool cset_is_finished = false;
  size_t num_forwardings;
  uint32_t cset_index;
  LogTarget(Debug, gc) lt;
  while (true) {
    cset_index = _cset->claim_next_index();
    if (cset_index >= cset_region_count) {
      r = nullptr;
      cset_is_finished = true;
    } else {
      r = _cset->cset_region(cset_index);
#ifdef KELVIN_DEBUG
      log_info(gc)("ShenGenEvacTask::evac_and_promote_regions(%u) claimed next region: %zu", worker_id, r->index());
#endif
      if (lt.is_enabled()) {
        LogStream ls(lt);
        log_region(r, &ls);
      }
      if (_cset->is_reusable(r)) {
        // Already evacuated and recycled. Degenerated GC resumes here.
        continue;
      }
      // Evacuate this region.
      // KELVIN TODO: I want to replace the evacuator with evacuate-to-early-recycled when available.
#ifdef KELVIN_DEBUG
      log_info(gc)("ShenGenEvacTask is evacuating region %zu", r->index());
#endif
      cl.set_region(r);
      _heap->marked_object_iterate(r, &cl);
      cl.finish_region(r);
      num_forwardings = cl.num_forwardings();
#ifdef KELVIN_SUSPENDIBLE
      log_info(gc)("suspendible worker %u yields after evacuating one region in evacuate_regions()", worker_id);
#endif
      if (_heap->check_cancelled_gc_and_yield(_concurrent)) {
        break;
      }
    }
    bool preprocessing_finished = (_early_recycle_preprocessed_regions.load_relaxed() == num_regions);
    if (preprocessing_finished) {
      size_t fwt_regions_recycled = 0;
      size_t fwt_bytes_recycled = 0;
      uint32_t first_newly_unpublished = cset_region_count;
      uint32_t last_newly_unpublished = 0;
      if (!cset_is_finished || (queued_regions > 0)) {
        if (cset_is_finished) {
          cset_index = await_queue_cset_indices[--queued_regions];
          num_forwardings = await_queue_forwardings[queued_regions];
          r = _cset->cset_region(cset_index);
#ifdef KELVIN_DEBUG
          log_info(gc)("Preprocessing is finished and cset is finished, worker %u is processing region %zu (cset index: %u) with %zu forwards",
                       worker_id, r->index(), cset_index, num_forwardings);
#endif
        }
#ifdef KELVIN_DEBUG
        else {
          log_info(gc)("Preprocessing is finished, cset not finished, worker %u is processing region %zu (cset index: %u) with %zu forwards",
                       worker_id, r->index(), cset_index, num_forwardings);
        }
#endif
        // Loop here so we can process this region, plus any regions that have been queued up while we waited
        // for preprocessing to finish.
        while (true) {
          size_t num_entries;
          if ((num_entries = r->get_fwt_entries()) == 0) {
            // We plan that this region will not be early recycled with a forwarding table. We might choose to early recycle
            // this region without a forwarding table. Save the number of forwarded objects now, so that we can use mark-word
            // density to prioritize no-table recycling if necessary.
#ifdef KELVIN_DEBUG
            log_info(gc)("worker %u is finishing region %zu (cset index %u) with no table, forwardings: %zu",
                         worker_id, r->index(), cset_index, num_forwardings);
#endif
            _heap->finish_no_table_region_evacuation(r, num_forwardings);
          } else {
            // Recycle with fwt, using num_entries that are stored in the region.
            // finish_fwt_region_evacuation() informs the free set that this region is ready to receive evacuations.
#ifdef KELVIN_DEBUG
            log_info(gc)("worker %u is finishing region %zu (cset index %u) with forward table, forwardings: %zu",
                         worker_id, r->index(), cset_index, num_forwardings);
#endif
            bool status = _heap->finish_fwt_region_evacuation(r);
            if (status) {
              fwt_regions_recycled++;
              fwt_bytes_recycled += region_size_bytes - num_entries * entry_size_bytes;
              if (cset_index < first_newly_unpublished) {
                first_newly_unpublished = cset_index;
              }
              if (cset_index > last_newly_unpublished) {
                last_newly_unpublished = cset_index;
              }
            }
          }
#ifdef KELVIN_SUSPENDIBLE
          log_info(gc)("worker %u yields after finishing one region in evacuate_regions()", worker_id);
#endif
          if (_heap->check_cancelled_gc_and_yield(_concurrent)) {
            break;
          }
          if (queued_regions > 0) {
            cset_index = await_queue_cset_indices[--queued_regions];
            r = _cset->cset_region(cset_index);
            num_forwardings = await_queue_forwardings[queued_regions];
#ifdef KELVIN_DEBUG
            log_info(gc)("worker %u is preparing to process last queued region %zu (of %zu queued regions) (cset index: %u) with forwardings: %zu",
                         worker_id, r->index(), queued_regions + 1, cset_index, num_forwardings);
#endif
          } else {
            break;
          }
        } // This ends the recycling loop
      } // This ends preparation of evacuated regions

      // Update the prepared tallies if we added any fwt regions to the prepared total.
      size_t now_prepared;
      size_t now_prepared_bytes;
      if (fwt_regions_recycled > 0) {
        size_t original_prepared;
        do {
          original_prepared = free_set->fwt_regions_prepared();
          now_prepared = original_prepared + fwt_regions_recycled;
        } while (!free_set->compare_set_fwt_regions_prepared(original_prepared, now_prepared));
#ifdef KELVIN_DEBUG
        log_info(gc)("worker %u incremented globally prepared by %zu to %zu", worker_id,
                     fwt_regions_recycled, original_prepared + fwt_regions_recycled);
#endif
        // Note: it is possible that a "publisher" sees regions prepared without the accompanying bytes prepared.
        // We expect this to be very rare.  If it happens, it might cause a delay in transfer of reserves
        // from Collector or OldCollector to the mutator partition. Current thought is to allow that to happen
        // rarely rather than invest in a more expensive synchronization mechanism.
        size_t original_prepared_bytes;
        do {
          original_prepared_bytes = free_set->fwt_bytes_prepared();
          now_prepared_bytes = original_prepared_bytes + fwt_bytes_recycled;;
        } while (!free_set->compare_set_fwt_bytes_prepared(original_prepared_bytes, now_prepared_bytes));
#ifdef KELVIN_DEBUG
        log_info(gc)("worker %u incremented globally prepared bytes by %zu to %zu",
                     worker_id, fwt_bytes_recycled, original_prepared_bytes + fwt_bytes_recycled);
        log_info(gc)("worker %u prepared regions from first_unpublished: %u to last_unpublished %u",
                     worker_id, first_newly_unpublished, last_newly_unpublished);
        log_info(gc)(" current range of unpublished is %zu to upper bound %zu",
                     free_set->first_unpublished_fwt(), free_set->upper_bound_on_unpublished_fwt());
#endif
        size_t original_first_unpublished;
        do {
          original_first_unpublished = free_set->first_unpublished_fwt();
          if (original_first_unpublished < first_newly_unpublished) {
            break;
          }
        } while (!free_set->compare_set_first_unpublished_fwt(original_first_unpublished, (size_t) first_newly_unpublished));
#ifdef KELVIN_DEBUG
        if (original_first_unpublished >= first_newly_unpublished) {
          log_info(gc)("worker %u adjusted first_unpublished: %zu, newly: %u",
                       worker_id, original_first_unpublished, first_newly_unpublished);
        }
#endif

        size_t original_bound_on_unpublished;
        do {
          original_bound_on_unpublished = free_set->upper_bound_on_unpublished_fwt();
          if (original_bound_on_unpublished >= last_newly_unpublished + 1) {
            break;
          }
        } while (!free_set->compare_set_upper_bound_on_unpublished_fwt(original_bound_on_unpublished,
                                                                       (size_t) (last_newly_unpublished + 1)));
#ifdef KELVIN_DEBUG
        if (original_bound_on_unpublished < last_newly_unpublished + 1) {
          log_info(gc)("worker %u adjusted upper_bound_on_unpublished: %zu, newly: %u",
                       worker_id, original_bound_on_unpublished, last_newly_unpublished + 1);
        }
#endif
      }

      // Publish if approprriate
      if (cset_is_finished || (fwt_regions_recycled > 0)) {
        // We intend to publish.  If we do publish, we might want to publish more than once. So we loop. While we are
        // publishing, other workers that prepare more regions for publication are prohibited from publishing.
        bool published_anything;
        do {
          // Repeatedly publish as long as there are relevant prepared regions that remain to be published.
          published_anything = free_set->publish_prepared_regions(worker_id, _num_workers, _concurrent, cset_is_finished, _cset);
        } while (published_anything);
      } // else, there were no fwt regions recycled and the cset is not finished, so no need to publish anything.
    } else {
      // Preprocessing is not finished. We don't know whether we should build a forwarding table for this
      // evacuated region. Remember the region in my queue while I work on evacuating more regions.
      if (cset_is_finished) {
        if (queued_regions > 0) {
          // Loop until preprocessing is done
          uint32_t yields = 0;
          while (_early_recycle_preprocessed_regions.load_relaxed() != num_regions) {
            yield_or_sleep(yields);
            if (_concurrent && SuspendibleThreadSet::should_yield()) {
              SuspendibleThreadSet::yield();
            }
          }
          // Resume the loop so queued entries can be finished now that preprocessing is done.
          continue;
        }
        // else, we'll fall through to below and break out of loop because cset_is_finished
      } else if (queued_regions < QueueSize) {
        await_queue_forwardings[queued_regions] = num_forwardings;
        await_queue_cset_indices[queued_regions++] = cset_index;
#ifdef KELVIN_DEBUG
        log_info(gc)("worker %u is queueing region %zu (%zu of %zu), forwardings %zu because preprocessing is not finished",
                     worker_id, r->index(), queued_regions, QueueSize, num_forwardings);
#endif
        if (queued_regions == QueueSize) {
#ifdef KELVIN_DEBUG
          log_info(gc)("worker %u is going to spin some cycles while we wait for preprocessing", worker_id);
#endif
          // The queue is full. Do not evacuate another region until preprocessing is done since we have nowhere
          // to store yet another region for preparation.
          uint32_t yields = 0;
          while (_early_recycle_preprocessed_regions.load_relaxed() != num_regions) {
            yield_or_sleep(yields);
#ifdef KELVIN_SUSPENDIBLE
            log_info(gc)("worker %u checks for yield in full queues if _concurrent: %s",
                         worker_id, _concurrent? "true": "false");
#endif
            if (_concurrent) {
              if (SuspendibleThreadSet::should_yield()) {
                SuspendibleThreadSet::yield();
              }
            }
          }
        }
      }
    } // This finishes the else branch for (!preprocessing_finished)
    if (cset_is_finished) {
      break;
    }
  } // This ends the "while (true)" loop.

#ifdef KELVIN_DEBUG
  log_info(gc)("worker %u is done evacuating, now we're going to promote_regions()", worker_id);
#endif
}

