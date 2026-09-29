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
  // This code was borrowed from ShenandohLock, where it was tested with different values of N, and chose 3 for best performance.
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
    ShenandoahConcurrentWorkerSession worker_session(worker_id);
    // Join the suspendible thread set only for the promote-only path
    // to avoid double-joining.
    SuspendibleThreadSetJoiner stsj(_only_promote_regions);
    do_work(worker_id);
  } else {
    ShenandoahWorkerTimingsTracker timer(ShenandoahPhaseTimings::degen_gc_evac, ShenandoahPhaseTimings::Work, worker_id, true);
    ShenandoahParallelWorkerSession worker_session(worker_id);
    do_work(worker_id);
  }
}

void ShenandoahGenerationalEvacuationTask::do_work(uint worker_id) {
  if (_only_promote_regions) {
    assert(_cset->is_empty(), "Should not have a collection set here");
    promote_regions();
  } else {
    assert(!_cset->is_empty(), "Should have a collection set here");
    evacuate_and_promote_regions(worker_id);
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

void ShenandoahGenerationalEvacuationTask::evacuate_and_promote_regions(uint worker_id) {
  ShenandoahHeapRegion* r;
  size_t fwt_regions = 0;
  size_t region_size_words = ShenandoahHeapRegion::region_size_words();
  size_t region_size_bytes = region_size_words * HeapWordSize;
  size_t entry_size_words = ShenandoahEarlyRecycleInfo<true>::entry_size_words();
  size_t entry_size_bytes = entry_size_words * HeapWordSize;
  size_t num_regions = _heap->num_regions();
  ShenandoahFreeSet* free_set = _heap->free_set();
  if (_concurrent && (worker_id == 0)) {
    // The first worker is responsible for selecting which regions will be early recycled with forward table.
    // Regions have already been sorted in increasing live data, which is a light-weight approximation of increasing
    // forward table size (aka increasing mark-word-density).

    // Set aside enough regions for early recycle with FWT to satisfy the anticipated memory shortfall.
    size_t short_fall = _heap->young_generation()->heuristics()->mutator_memory_shortfall();
    size_t back_fill = 0;
    size_t cset_region_count = _cset->count();
    for (size_t i = 0; i < cset_region_count; i++) {
      r = free_set->get_tlab_region(i);
      size_t num_entries;
      if (back_fill < short_fall) {
        // For now, calculate num_forwardings only for regions that we plan to early recycle with forwarding table.
        // As the non-fwt regions are evacuated, we will update their expected forwardings. The order of regions within
        // the early recycle regions array is approximately the order of increasing mark-word density.
        size_t num_forwardings = _ctx->count_mark_bit_conflicts<1>(r->bottom(), r->end());
        size_t num_entries = r->calculate_entries_in_forward_table();
        if (num_entries > 0) {
          size_t early_available_words = region_size_words - entry_size_words * num_entries;
          back_fill += early_available_words;
          r->set_expected_forwardings(num_forwardings);
          r->set_fwt_entries(num_entries);
          assert(r->early_recycle_status() == ShenandoahEarlyRecycleStatus::Planned_FWT, "Precondition");
          fwt_regions++;
          continue;
        }
      }
      // This region will not be early recycled with forward table.
      r->set_fwt_entries(0);
    }

#ifdef KELVIN_TRACE
    log_info(gc)("After evac pre-init, cset region count: %zu, fwt regions: %zu, no-table regions: %zu",
                 cset_region_count, fwt_regions, cset_region_count - fwt_regions);
    log_info(gc)("  shortfall: %zu, back_fill: %zu", short_fall, back_fill);
#endif

    // Once we identify that preprocessing is finished, other threads may consult the fields we've initialized above.
    OrderAccess::storeload();

    // Signal to all other workers that the early-release preprocessing is done.
    bool result = _early_recycle_preprocessed_regions.compare_set(0, num_regions, memory_order_relaxed);
    assert(result, "_early_recycle_preprocessed_regions should equal 0 during preprocessing");
  }
  // else, there's no need to early recycle regions because we're in STW mode. TODO: Even STW GC may benefit from early
  // recycling in order to "complete" the evacuation reserves.  But there would be no need to optimize LRB behavior in
  // STW mode. Let's not bother with this because of planned degenectomy.

  ShenandoahConcurrentEvacuator cl(_heap);
  // Concurrent GC workers evacuate regions in order of increasing live data. They start with the regions that have
  // the least amount of live data, so the first few regions evacuated may go very fast.
  static const size_t QueueSize = 8;
  ShenandoahHeapRegion* await_preprocessing_queue[QueueSize];
  uint32_t await_queue_forwardings[QueueSize];
  size_t queued_regions = 0;
  bool cset_is_finished = false;
  LogTarget(Debug, gc) lt;
  while (true) {
    r = _cset->claim_next();
    if (lt.is_enabled()) {
      LogStream ls(lt);
      log_region(r, &ls);
    }
    size_t num_forwardings = 0;
    if (r == nullptr) {
      cset_is_finished = true;
    } else {
      if (_cset->is_reusable(r)) {
        // Already evacuated and recycled. Degenerated GC resumes here.
        continue;
      } else {
        SuspendibleThreadSetJoiner stsj(_concurrent);
        cl.set_region(r);
        _heap->marked_object_iterate(r, &cl);
        cl.finish_region(r);
        num_forwardings = cl.num_forwardings();
        if (_heap->check_cancelled_gc_and_yield(_concurrent)) {
          break;
        }
      }
      if (_concurrent) {
        bool preprocessing_finished = (_early_recycle_preprocessed_regions.load_relaxed() == num_regions);
        if (preprocessing_finished) {
          size_t fwt_regions_recycled = 0;
          size_t fwt_bytes_recycled = 0;
          if (!cset_is_finished || (queued_regions > 0)) {
            if (cset_is_finished) {
              r = await_preprocessing_queue[--queued_regions];
              num_forwardings = await_queue_forwardings[queued_regions];
            }
            // Loop here so we can process this region, plus any regions that have been queued up while we waited
            // for preprocessing to finish.
            while (true) {
              size_t num_entries;
              if ((num_entries = r->get_fwt_entries()) == 0) {
                // We plan that this region will not be early recycled with a forwarding table. We might choose to early recycle
                // this region without a forwarding table. Save the number of forwarded objects now, so that we can use mark-word
                // density to prioritize no-table recycling if necessary.
                _heap->finish_no_table_region_evacuation(r, num_forwardings);
              } else {
                // Recycle with fwt, using num_entries that are stored in the region.
                // finish_fwt_region_evacuation() informs the free set that this region is ready to receive evacuations.
                _heap->finish_fwt_region_evacuation(r);
                fwt_regions_recycled++;
                fwt_bytes_recycled += region_size_bytes - num_entries * entry_size_bytes;
              }
              if (_heap->check_cancelled_gc_and_yield(_concurrent)) {
                break;
              }
              if (queued_regions > 0) {
                r = await_preprocessing_queue[--queued_regions];
                num_forwardings = await_queue_forwardings[queued_regions];
              } else {
                break;
              }
            }
          }

          // Update the prepared tallies if we added any fwt regions to the prepared total.
          size_t now_prepared;
          size_t now_prepared_bytes;
          if (fwt_regions_recycled > 0) {
            size_t original_prepared;
            do {
              original_prepared = free_set->fwt_regions_prepared();
              now_prepared = original_prepared + fwt_regions_recycled;
            } while (!free_set->compare_set_fwt_regions_prepared(original_prepared, now_prepared));

            // Note: it is possible that a "publisher" sees regions prepared without the accompanying bytes prepared.
            // We expect this to be very rare.  If it happens, it might cause a delay in transfer of reserves
            // from Collector or OldCollector to the mutator partition. Current thought is to allow that to happen
            // rarely rather than invest in a more expensive synchronization mechanism.
            size_t original_prepared_bytes;
            do {
              original_prepared_bytes = free_set->fwt_bytes_prepared();
              now_prepared_bytes = original_prepared_bytes + fwt_bytes_recycled;;
            } while (!free_set->compare_set_fwt_bytes_prepared(original_prepared_bytes, now_prepared_bytes));
          } else {
            now_prepared = free_set->fwt_regions_prepared();
            now_prepared_bytes = free_set->fwt_bytes_prepared();
          }

          // Publish if approprirate
          if (cset_is_finished || (fwt_regions_recycled > 0)) {
            while (true) {
              // We'll break out of this loop if we decide not to publish.  But if we publish once, we may be the
              // only opportunity to publish again, because all the workers that have been preparing during out publication
              // were forbidden from publishing while we held the publication lock.
              size_t original_published = free_set->fwt_regions_published();

              // Publish if:
              //  Nothing has been published yet and we have at least one (probably more) prepared regions, or
              //  Prepared > previously published by at least _num_workers / 2, or
              //  Prepared > previously published and there are no more regions to prepare
              // unless we decide otherwise below
              if (((original_published == 0) && (now_prepared > 0)) ||
                  ((now_prepared > original_published) &&
                   ((now_prepared - original_published >= _num_workers / 2) || cset_is_finished))) {
              
                // Because of races, original_consumed may be > original_published. That only happens if other threads publish
                // and consume after I read published, in which case I should not need to publish now, because some other thread
                // is publishing.

                // We'll publish if the available supply to GC workers is less than 1 region per worker, or if there are
                // no more cset regions to prepare and the prepared regions have not already been published.
                size_t original_consumed = free_set->fwt_regions_consumed_by_gc();
                if (((original_published > original_consumed) && (original_published - original_consumed < _num_workers)) ||
                    (cset_is_finished && (original_published > now_prepared))) {
                  uint32_t started, finished;

                  free_set->fwt_handshakes(started, finished);
                  if (started == finished) {
                    if (free_set->fwt_start_handshake(started, finished)) {
                      // I hold the publication lock.
                      // Update my counts of prepared and published since values may have changed since I sampled above

                      size_t regions_to_publish = 0;
                      size_t bytes_to_publish = 0;
                      size_t first_unpublished_index = free_set->first_unpublished_fwt();
                      size_t last_unpublished_index = free_set->last_unpublished_fwt();
                      // Note: more regions might get published while I'm iterating through this loop and/or while I'm
                      // performing the handshake.  We won't count those as published until some subsequent publication.
                      for (size_t index = first_unpublished_index; index <= last_unpublished_index; index++) {
                        ShenandoahHeapRegion* r = free_set->get_tlab_region(index);
                        if (r->early_recycle_status() == ShenandoahEarlyRecycleStatus::Prepared_FWT) {
                          r->set_early_recycle_status(ShenandoahEarlyRecycleStatus::Publishing_FWT);
                          regions_to_publish++;
                          bytes_to_publish += region_size_bytes - r->get_fwt_entries() * entry_size_bytes;
                        }
                      }
                      
                      // Everything stored above should become visible to other threads after handshake
                      OrderAccess::storeload();

                      // Force mutators into handshake to assure that they no longer use mark-word forwarding on any of the
                      // now-prepared regions.
                      assert(!Thread::current()->is_suspendible_thread(), "must not hold STS join across rendezvous");
                      _heap->rendezvous_threads("Switch to Forward Table");

                      for (size_t index = first_unpublished_index; index <= last_unpublished_index; index++) {
                        ShenandoahHeapRegion* r = free_set->get_tlab_region(index);
                        if (r->early_recycle_status() == ShenandoahEarlyRecycleStatus::Publishing_FWT) {
                          r->set_early_recycle_status(ShenandoahEarlyRecycleStatus::Published_FWT);
                        }
                      }

                      // Published is at least as much as was prepared before our handshake.  It's likely more, but we
                      // don't know that for sure, so we act conservatively.
                      free_set->add_to_fwt_regions_published(regions_to_publish);
                      size_t total_available_to_xfer = free_set->add_to_fwt_bytes_published(bytes_to_publish);
                      
                      // Still holding the handshake lock, transfer as much memory as possible from Reserves to Mutator.
                      // now_prepared_bytes is aka as fwt_bytes_published
                      size_t young_transfers = free_set->young_fwt_bytes_to_mutator();
                      size_t old_transfers = free_set->old_fwt_bytes_to_mutator();
                      size_t transferable = total_available_to_xfer - (young_transfers + old_transfers);
                      size_t newly_transferred = free_set->endow_mutator_from_collector(transferable);
                      free_set->set_young_fwt_bytes_to_mutator(young_transfers + newly_transferred);
                      transferable -= newly_transferred;
                      newly_transferred = free_set->endow_mutator_from_old_collector(transferable);
                      free_set->set_old_fwt_bytes_to_mutator(old_transfers + newly_transferred);

                      // Release the publication lock
                      bool result = free_set->fwt_finish_handshake(started+1, finished);
                      assert(result, "Nobody else should modify starts and finishes while I hold the lock");
                    } else {
                      // if started != finished, that means some other thread holds the publication lock and is
                      // in the process of of publishing. If that other thread started its handshake before certain
                      // regions were prepared, it will recheck conditions and perform another handshake if appropriate.
                      // I will not be responsible for publishing my newly prepared regions.
                      break;
                    }
                  } else {
                    // otherwise, some other thread was already working on a handshake, so we'll just move on
                    break;
                  }
                } else {
                  // There's plenty of runway for the number of workers, so no need to publish right now.
                  break;
                }
              } else {
                // There are not enough newly prepared regions to make publication worthwhile, so let's wait for more preparation
                break;
              }
              // Each worker, upon detecting cset_is_finished, will have at least one chance to do one final publication of
              // prepared regions. If I prepare a region but do not publish because some other worker was already publishing,
              // that other worker will consult prepared regions after it finishes its current handshake and will perform
              // a second handshake, either because that other worker already saw that cset_is_finished, or because it will see
              // that cset_is_finished on its next iteration through the cset iterator.
              if (cset_is_finished) {
                break;
              }
            }  // This ends the while(true) loop. Either we published, or we decided not to publish.
          }
          // else, there were no fwt regions recycled and the cset is not finished, so no need to publish anything.
        } else {
          // Since pre-processing is not yet done, we don't know whether we should build a forwarding table for this
          // evacuated region. Remember the region in my queue while I work on evacuating more regions.
          if (queued_regions < QueueSize) {
            await_queue_forwardings[queued_regions] = num_forwardings;
            await_preprocessing_queue[queued_regions++] = r;
            if (queued_regions == QueueSize) {
              // The queue is full. Do not evacuate another region until preprocessing is done since we have nowhere
              // to store yet another region for preparation.
              uint32_t yields = 0;
              while (_early_recycle_preprocessed_regions.load_relaxed() != num_regions) {
                yield_or_sleep(yields);
              }
            }
          }
        }
      }
    }
  }
}

