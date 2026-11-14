/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2013, 2022, Red Hat, Inc. All rights reserved.
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

#include "cds/aotMappedHeapWriter.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc/shared/classUnloadingContext.hpp"
#include "gc/shared/fullGCForwarding.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/gcArguments.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/locationPrinter.inline.hpp"
#include "gc/shared/memAllocator.hpp"
#include "gc/shared/plab.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "gc/shenandoah/heuristics/shenandoahOldHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahYoungHeuristics.hpp"
#include "gc/shenandoah/mode/shenandoahGenerationalMode.hpp"
#include "gc/shenandoah/mode/shenandoahPassiveMode.hpp"
#include "gc/shenandoah/mode/shenandoahSATBMode.hpp"
#include "gc/shenandoah/shenandoahAllocator.hpp"
#include "gc/shenandoah/shenandoahAllocRate.inline.hpp"
#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahCodeRoots.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahConcurrentMark.hpp"
#include "gc/shenandoah/shenandoahControlThread.hpp"
#include "gc/shenandoah/shenandoahForwarding.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahGenerationalEvacuationTask.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahGlobalGeneration.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegionClosures.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.hpp"
#include "gc/shenandoah/shenandoahInitLogger.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahMemoryPool.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahObjArrayAllocator.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahPadding.hpp"
#include "gc/shenandoah/shenandoahParallelCleaning.inline.hpp"
#include "gc/shenandoah/shenandoahPartitionAllocator.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.inline.hpp"
#include "gc/shenandoah/shenandoahStackChunkGCData.inline.hpp"
#include "gc/shenandoah/shenandoahStackWatermark.hpp"
#include "gc/shenandoah/shenandoahSTWMark.hpp"
#include "gc/shenandoah/shenandoahUncommitThread.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"
#include "gc/shenandoah/shenandoahVMOperations.hpp"
#include "gc/shenandoah/shenandoahWorkerPolicy.hpp"
#include "gc/shenandoah/shenandoahWorkGroup.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "memory/allocation.hpp"
#include "memory/classLoaderMetaspace.hpp"
#include "memory/memoryReserver.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/universe.hpp"
#include "nmt/mallocTracker.hpp"
#include "nmt/memTracker.hpp"
#include "oops/compressedKlass.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "runtime/atomic.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/globals.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/safepointMechanism.hpp"
#include "runtime/stackWatermarkSet.hpp"
#include "runtime/threads.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/events.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/quickSort.hpp"
#if INCLUDE_JFR
#include "gc/shenandoah/shenandoahJfrSupport.hpp"
#endif


size_t ShenandoahConcurrentEvacuateEarlyRecycleRegionObjectClosure::_min_fill_size = 0;


class ShenandoahPretouchHeapTask : public WorkerTask {
private:
  ShenandoahRegionIterator _regions;
  const size_t _page_size;
public:
  ShenandoahPretouchHeapTask(size_t page_size) :
    WorkerTask("Shenandoah Pretouch Heap"),
    _page_size(page_size) {}

  virtual void work(uint worker_id) {
    ShenandoahHeapRegion* r = _regions.next();
    while (r != nullptr) {
      if (r->is_committed()) {
        os::pretouch_memory(r->bottom(), r->end(), _page_size);
      }
      r = _regions.next();
    }
  }
};

class ShenandoahPretouchBitmapTask : public WorkerTask {
private:
  ShenandoahRegionIterator _regions;
  char* _bitmap_base;
  const size_t _bitmap_size;
  const size_t _page_size;
public:
  ShenandoahPretouchBitmapTask(char* bitmap_base, size_t bitmap_size, size_t page_size) :
    WorkerTask("Shenandoah Pretouch Bitmap"),
    _bitmap_base(bitmap_base),
    _bitmap_size(bitmap_size),
    _page_size(page_size) {}

  virtual void work(uint worker_id) {
    ShenandoahHeapRegion* r = _regions.next();
    while (r != nullptr) {
      size_t start = r->index()       * ShenandoahHeapRegion::region_size_bytes() / MarkBitMap::heap_map_factor();
      size_t end   = (r->index() + 1) * ShenandoahHeapRegion::region_size_bytes() / MarkBitMap::heap_map_factor();
      assert (end <= _bitmap_size, "end is sane: %zu < %zu", end, _bitmap_size);

      if (r->is_committed()) {
        os::pretouch_memory(_bitmap_base + start, _bitmap_base + end, _page_size);
      }

      r = _regions.next();
    }
  }
};

static ReservedSpace reserve(size_t size, size_t preferred_page_size) {
  // When a page size is given we don't want to mix large
  // and normal pages. If the size is not a multiple of the
  // page size it will be aligned up to achieve this.
  size_t alignment = os::vm_allocation_granularity();
  if (preferred_page_size != os::vm_page_size()) {
    alignment = MAX2(preferred_page_size, alignment);
    size = align_up(size, alignment);
  }

  const ReservedSpace reserved = MemoryReserver::reserve(size, alignment, preferred_page_size, mtGC);
  if (!reserved.is_reserved()) {
    vm_exit_during_initialization("Could not reserve space");
  }
  return reserved;
}

jint ShenandoahHeap::initialize() {
  //
  // Figure out heap sizing
  //

  size_t init_byte_size = InitialHeapSize;
  size_t min_byte_size  = MinHeapSize;
  size_t max_byte_size  = MaxHeapSize;
  size_t heap_alignment = HeapAlignment;

  size_t reg_size_bytes = ShenandoahHeapRegion::region_size_bytes();

  Universe::check_alignment(max_byte_size,  reg_size_bytes, "Shenandoah heap");
  Universe::check_alignment(init_byte_size, reg_size_bytes, "Shenandoah heap");

  _num_regions = ShenandoahHeapRegion::region_count();
  assert(_num_regions == (max_byte_size / reg_size_bytes),
         "Regions should cover entire heap exactly: %zu != %zu/%zu",
         _num_regions, max_byte_size, reg_size_bytes);

  size_t num_committed_regions = init_byte_size / reg_size_bytes;
  num_committed_regions = MIN2(num_committed_regions, _num_regions);
  assert(num_committed_regions <= _num_regions, "sanity");
  _initial_size = num_committed_regions * reg_size_bytes;

  size_t num_min_regions = min_byte_size / reg_size_bytes;
  num_min_regions = MIN2(num_min_regions, _num_regions);
  assert(num_min_regions <= _num_regions, "sanity");
  _minimum_size = num_min_regions * reg_size_bytes;

  _soft_max_size.store_relaxed(clamp(SoftMaxHeapSize, min_capacity(), max_capacity()));

  _committed.store_relaxed(_initial_size);

  size_t heap_page_size   = UseLargePages ? os::large_page_size() : os::vm_page_size();
  size_t bitmap_page_size = UseLargePages ? os::large_page_size() : os::vm_page_size();
  size_t region_page_size = UseLargePages ? os::large_page_size() : os::vm_page_size();

  //
  // Reserve and commit memory for heap
  //

  ReservedHeapSpace heap_rs = Universe::reserve_heap(max_byte_size, heap_alignment);
  initialize_reserved_region(heap_rs);
  _heap_region = MemRegion((HeapWord*)heap_rs.base(), heap_rs.size() / HeapWordSize);
  _heap_region_special = heap_rs.special();

  assert((((size_t) base()) & ShenandoahHeapRegion::region_size_bytes_mask()) == 0,
         "Misaligned heap: " PTR_FORMAT, p2i(base()));
  os::trace_page_sizes_for_requested_size("Heap",
                                          max_byte_size, heap_alignment,
                                          heap_rs.base(),
                                          heap_rs.size(), heap_rs.page_size());

#if SHENANDOAH_OPTIMIZED_MARKTASK
  // The optimized ShenandoahMarkTask takes some bits away from the full object bits.
  // Fail if we ever attempt to address more than we can.
  if ((uintptr_t)heap_rs.end() >= ShenandoahMarkTask::max_addressable()) {
    FormatBuffer<512> buf("Shenandoah reserved [" PTR_FORMAT ", " PTR_FORMAT") for the heap, \n"
                          "but max object address is " PTR_FORMAT ". Try to reduce heap size, or try other \n"
                          "VM options that allocate heap at lower addresses (HeapBaseMinAddress, AllocateHeapAt, etc).",
                p2i(heap_rs.base()), p2i(heap_rs.end()), ShenandoahMarkTask::max_addressable());
    vm_exit_during_initialization("Fatal Error", buf);
  }
#endif

  ReservedSpace sh_rs = heap_rs.first_part(max_byte_size);
  if (!_heap_region_special) {
    os::commit_memory_or_exit(sh_rs.base(), _initial_size, heap_alignment, false,
                              "Cannot commit heap memory");
  }

  BarrierSet::set_barrier_set(new ShenandoahBarrierSet(this, _heap_region));

  // Now we know the number of regions and heap sizes, initialize the heuristics.
  initialize_heuristics();

  // If ShenandoahCardBarrier is enabled but it's not generational mode
  // it means we're under passive mode and we have to initialize old gen
  // for the purpose of having card table.
  if (ShenandoahCardBarrier && !(mode()->is_generational())) {
    _old_generation = new ShenandoahOldGeneration(max_workers());
  }

  assert(_heap_region.byte_size() == heap_rs.size(), "Need to know reserved size for card table");

  //
  // Worker threads must be initialized after the barrier is configured
  //
  _workers = new ShenandoahWorkerThreads("ShenWorker", _max_workers);
  if (_workers == nullptr) {
    vm_exit_during_initialization("Failed necessary allocation.");
  } else {
    _workers->initialize_workers();
  }

  if (ParallelGCThreads > 1) {
    _safepoint_workers = new ShenandoahWorkerThreads("Safepoint Cleanup Thread", ParallelGCThreads);
    _safepoint_workers->initialize_workers();
  }

  //
  // Reserve and commit memory for bitmap(s)
  //

  size_t bitmap_size_orig = ShenandoahMarkBitMap::compute_size(heap_rs.size());
  _bitmap_size = align_up(bitmap_size_orig, bitmap_page_size);

  size_t bitmap_bytes_per_region = reg_size_bytes / ShenandoahMarkBitMap::heap_map_factor();

  guarantee(bitmap_bytes_per_region != 0,
            "Bitmap bytes per region should not be zero");
  guarantee(is_power_of_2(bitmap_bytes_per_region),
            "Bitmap bytes per region should be power of two: %zu", bitmap_bytes_per_region);

  if (bitmap_page_size > bitmap_bytes_per_region) {
    _bitmap_regions_per_slice = bitmap_page_size / bitmap_bytes_per_region;
    _bitmap_bytes_per_slice = bitmap_page_size;
  } else {
    _bitmap_regions_per_slice = 1;
    _bitmap_bytes_per_slice = bitmap_bytes_per_region;
  }

  guarantee(_bitmap_regions_per_slice >= 1,
            "Should have at least one region per slice: %zu",
            _bitmap_regions_per_slice);

  guarantee(((_bitmap_bytes_per_slice) % bitmap_page_size) == 0,
            "Bitmap slices should be page-granular: bps = %zu, page size = %zu",
            _bitmap_bytes_per_slice, bitmap_page_size);

  ReservedSpace bitmap = reserve(_bitmap_size, bitmap_page_size);
  os::trace_page_sizes_for_requested_size("Mark Bitmap",
                                          bitmap_size_orig, bitmap_page_size,
                                          bitmap.base(),
                                          bitmap.size(), bitmap.page_size());
  MemTracker::record_virtual_memory_tag(bitmap, mtGC);
  _bitmap_region = MemRegion((HeapWord*) bitmap.base(), bitmap.size() / HeapWordSize);
  _bitmap_region_special = bitmap.special();

  size_t bitmap_init_commit = _bitmap_bytes_per_slice *
    align_up(num_committed_regions, _bitmap_regions_per_slice) / _bitmap_regions_per_slice;
  bitmap_init_commit = MIN2(_bitmap_size, bitmap_init_commit);
  if (!_bitmap_region_special) {
    os::commit_memory_or_exit((char *) _bitmap_region.start(), bitmap_init_commit, bitmap_page_size, false,
                              "Cannot commit bitmap memory");
  }

  ShenandoahEarlyRecycleInfo<true>::initialize_globals();

#ifdef KELVIN_DEBUG
  log_info(gc)("Back from ShenandoahEarlyRecycleInfo<true>::initialize_globals()");
#endif

  _marking_context = new ShenandoahMarkingContext(_heap_region, _bitmap_region, _num_regions);

  if (ShenandoahVerify) {
    ReservedSpace verify_bitmap = reserve(_bitmap_size, bitmap_page_size);
    os::trace_page_sizes_for_requested_size("Verify Bitmap",
                                            bitmap_size_orig, bitmap_page_size,
                                            verify_bitmap.base(),
                                            verify_bitmap.size(), verify_bitmap.page_size());
    if (!verify_bitmap.special()) {
      os::commit_memory_or_exit(verify_bitmap.base(), verify_bitmap.size(), bitmap_page_size, false,
                                "Cannot commit verification bitmap memory");
    }
    MemTracker::record_virtual_memory_tag(verify_bitmap, mtGC);
    MemRegion verify_bitmap_region = MemRegion((HeapWord *) verify_bitmap.base(), verify_bitmap.size() / HeapWordSize);
    _verification_bit_map.initialize(_heap_region, verify_bitmap_region);
    _verifier = new ShenandoahVerifier(this, &_verification_bit_map);
  }

  // Reserve aux bitmap for use in object_iterate(). We don't commit it here.
  size_t aux_bitmap_page_size = bitmap_page_size;

  ReservedSpace aux_bitmap = reserve(_bitmap_size, aux_bitmap_page_size);
  os::trace_page_sizes_for_requested_size("Aux Bitmap",
                                          bitmap_size_orig, aux_bitmap_page_size,
                                          aux_bitmap.base(),
                                          aux_bitmap.size(), aux_bitmap.page_size());
  MemTracker::record_virtual_memory_tag(aux_bitmap, mtGC);
  _aux_bitmap_region = MemRegion((HeapWord*) aux_bitmap.base(), aux_bitmap.size() / HeapWordSize);
  _aux_bitmap_region_special = aux_bitmap.special();
  _aux_bit_map.initialize(_heap_region, _aux_bitmap_region);

  //
  // Create regions and region sets
  //
  size_t region_align = align_up(sizeof(ShenandoahHeapRegion), SHENANDOAH_CACHE_LINE_SIZE);
  size_t region_storage_size_orig = region_align * _num_regions;
  size_t region_storage_size = align_up(region_storage_size_orig,
                                        MAX2(region_page_size, os::vm_allocation_granularity()));

  ReservedSpace region_storage = reserve(region_storage_size, region_page_size);
  os::trace_page_sizes_for_requested_size("Region Storage",
                                          region_storage_size_orig, region_page_size,
                                          region_storage.base(),
                                          region_storage.size(), region_storage.page_size());
  MemTracker::record_virtual_memory_tag(region_storage, mtGC);
  if (!region_storage.special()) {
    os::commit_memory_or_exit(region_storage.base(), region_storage_size, region_page_size, false,
                              "Cannot commit region memory");
  }

  // Try to fit the collection set bitmap at lower addresses. This optimizes code generation for cset checks.
  // Go up until a sensible limit (subject to encoding constraints) and try to reserve the space there.
  // If not successful, bite a bullet and allocate at whatever address.
  {
    const size_t cset_align = MAX2<size_t>(os::vm_page_size(), os::vm_allocation_granularity());
    const size_t cset_size = align_up(((size_t) sh_rs.base() + sh_rs.size()) >> ShenandoahHeapRegion::region_size_bytes_shift(), cset_align);
    const size_t cset_page_size = os::vm_page_size();

    uintptr_t min = round_up_power_of_2(cset_align);
    uintptr_t max = (1u << 30u);
    ReservedSpace cset_rs;

    for (uintptr_t addr = min; addr <= max; addr <<= 1u) {
      char* req_addr = (char*)addr;
      assert(is_aligned(req_addr, cset_align), "Should be aligned");
      cset_rs = MemoryReserver::reserve(req_addr, cset_size, cset_align, cset_page_size, mtGC);
      if (cset_rs.is_reserved()) {
        assert(cset_rs.base() == req_addr, "Allocated where requested: " PTR_FORMAT ", " PTR_FORMAT, p2i(cset_rs.base()), addr);
        _collection_set = new ShenandoahCollectionSet(this, cset_rs, sh_rs.base());
        break;
      }
    }

    if (_collection_set == nullptr) {
      cset_rs = MemoryReserver::reserve(cset_size, cset_align, os::vm_page_size(), mtGC);
      if (!cset_rs.is_reserved()) {
        vm_exit_during_initialization("Cannot reserve memory for collection set");
      }

      _collection_set = new ShenandoahCollectionSet(this, cset_rs, sh_rs.base());
    }
    _cset_map = _collection_set->_cset_map;
    ShenandoahBarrierSet::_cset_map = _collection_set->_cset_map;

    os::trace_page_sizes_for_requested_size("Collection Set",
                                            cset_size, cset_page_size,
                                            cset_rs.base(),
                                            cset_rs.size(), cset_rs.page_size());
  }

  _regions = NEW_C_HEAP_ARRAY(ShenandoahHeapRegion*, _num_regions, mtGC);
  _affiliations = NEW_C_HEAP_ARRAY(uint8_t, _num_regions, mtGC);
  _biased_affiliations = _affiliations - (p2u(base()) >> ShenandoahHeapRegion::region_size_bytes_shift());

  {
    ShenandoahHeapLocker locker(lock());
    for (size_t i = 0; i < _num_regions; i++) {
      HeapWord* start = (HeapWord*)sh_rs.base() + ShenandoahHeapRegion::region_size_words() * i;
      bool is_committed = i < num_committed_regions;
      void* loc = region_storage.base() + i * region_align;

      ShenandoahHeapRegion* r = new (loc) ShenandoahHeapRegion(start, i, is_committed);
      assert(is_aligned(r, SHENANDOAH_CACHE_LINE_SIZE), "Sanity");

      _marking_context->initialize_top_at_mark_start(r);
      _regions[i] = r;
      assert(!collection_set()->is_in(i), "New region should not be in collection set");

      _affiliations[i] = ShenandoahAffiliation::FREE;
    }

    if (mode()->is_generational()) {
      size_t young_reserve = (soft_max_capacity() * ShenandoahEvacReserve) / 100;
      young_generation()->set_evacuation_reserve(young_reserve);
      old_generation()->set_evacuation_reserve((size_t) 0);
      old_generation()->set_promoted_reserve((size_t) 0);
    }

    _free_set = new ShenandoahFreeSet(this, _num_regions);
    _collection_set->post_initialize(_free_set);
    _allocator = new ShenandoahAllocator(_free_set);
    initialize_generations();

#ifdef KELVIN_DEBUG
    log_info(gc)("Preparing to rebuild initial freeset");
#endif

    // We are initializing free set.  We ignore cset region tallies.
    size_t young_trashed_regions, old_trashed_regions, first_old, last_old, num_old;
    _free_set->prepare_to_rebuild(young_trashed_regions, old_trashed_regions, first_old, last_old, num_old);
    if (mode()->is_generational()) {
      ShenandoahGenerationalHeap* gen_heap = ShenandoahGenerationalHeap::heap();
      // We cannot call
      //  gen_heap->young_generation()->heuristics()->bytes_of_allocation_runway_before_gc_trigger(young_cset_regions)
      // until after the heap is fully initialized.  So we make up a safe value here.
      size_t allocation_runway = InitialHeapSize / 2;
      // We're initializing the heap.  All regions within young are initially empty.
      size_t max_transfer = allocation_runway;
      gen_heap->compute_old_generation_balance(max_transfer, old_trashed_regions, young_trashed_regions);
    }
    _free_set->finish_rebuild(young_trashed_regions, old_trashed_regions, num_old);
  }

  if (AlwaysPreTouch) {
    // For NUMA, it is important to pre-touch the storage under bitmaps with worker threads,
    // before initialize() below zeroes it with initializing thread. For any given region,
    // we touch the region and the corresponding bitmaps from the same thread.
    ShenandoahPushWorkerScope scope(workers(), _max_workers, false);

    _pretouch_heap_page_size = heap_page_size;
    _pretouch_bitmap_page_size = bitmap_page_size;

    // OS memory managers may want to coalesce back-to-back pages. Make their jobs
    // simpler by pre-touching continuous spaces (heap and bitmap) separately.

    ShenandoahPretouchBitmapTask bcl(bitmap.base(), _bitmap_size, _pretouch_bitmap_page_size);
    _workers->run_task(&bcl);

    ShenandoahPretouchHeapTask hcl(_pretouch_heap_page_size);
    _workers->run_task(&hcl);
  }

  //
  // Initialize the rest of GC subsystems
  //

  _liveness_cache = NEW_C_HEAP_ARRAY(ShenandoahLiveData*, _max_workers, mtGC);
  for (uint worker = 0; worker < _max_workers; worker++) {
    _liveness_cache[worker] = NEW_C_HEAP_ARRAY(ShenandoahLiveData, _num_regions, mtGC);
    Copy::fill_to_bytes(_liveness_cache[worker], _num_regions * sizeof(ShenandoahLiveData));
  }

  // There should probably be Shenandoah-specific options for these,
  // just as there are G1-specific options.
  {
    ShenandoahSATBMarkQueueSet& satbqs = ShenandoahBarrierSet::satb_mark_queue_set();
    satbqs.set_process_completed_buffers_threshold(20); // G1SATBProcessCompletedThreshold
    satbqs.set_buffer_enqueue_threshold_percentage(60); // G1SATBBufferEnqueueingThresholdPercent
  }

  _monitoring_support = new ShenandoahMonitoringSupport(this);
  _phase_timings = new ShenandoahPhaseTimings(max_workers());
  ShenandoahCodeRoots::initialize();

  // Initialization of controller makes use of variables established by initialize_heuristics.
  initialize_controller();

#ifdef KELVIN_DEBUG
  log_info(gc)("Going to post_initialize_heuristics()");
#endif


  // Certain initialization of heuristics must be deferred until after controller is initialized.
  post_initialize_heuristics();
  start_idle_span();
  if (ShenandoahUncommit) {
    _uncommit_thread = new ShenandoahUncommitThread(this);
  }
  print_init_logger();
  FullGCForwarding::initialize(_heap_region);

#ifdef KELVIN_DEBUG
  log_info(gc)("Done with ShenHeap::initialize()");
#endif

  return JNI_OK;
}

void ShenandoahHeap::initialize_controller() {
  _control_thread = new ShenandoahControlThread();
}

void ShenandoahHeap::print_init_logger() const {
  ShenandoahInitLogger::print();
}

void ShenandoahHeap::initialize_mode() {
  if (ShenandoahGCMode != nullptr) {
    if (strcmp(ShenandoahGCMode, "satb") == 0) {
      _gc_mode = new ShenandoahSATBMode();
    } else if (strcmp(ShenandoahGCMode, "passive") == 0) {
      _gc_mode = new ShenandoahPassiveMode();
    } else if (strcmp(ShenandoahGCMode, "generational") == 0) {
      _gc_mode = new ShenandoahGenerationalMode();
    } else {
      vm_exit_during_initialization("Unknown -XX:ShenandoahGCMode option");
    }
  } else {
    vm_exit_during_initialization("Unknown -XX:ShenandoahGCMode option (null)");
  }
  _gc_mode->initialize_flags();
  if (_gc_mode->is_diagnostic() && !UnlockDiagnosticVMOptions) {
    vm_exit_during_initialization(
            err_msg("GC mode \"%s\" is diagnostic, and must be enabled via -XX:+UnlockDiagnosticVMOptions.",
                    _gc_mode->name()));
  }
  if (_gc_mode->is_experimental() && !UnlockExperimentalVMOptions) {
    vm_exit_during_initialization(
            err_msg("GC mode \"%s\" is experimental, and must be enabled via -XX:+UnlockExperimentalVMOptions.",
                    _gc_mode->name()));
  }
}

void ShenandoahHeap::initialize_heuristics() {
  _global_generation = new ShenandoahGlobalGeneration(mode()->is_generational(), max_workers());
  _global_generation->initialize_heuristics(mode());
}

#ifdef _MSC_VER
#pragma warning( push )
#pragma warning( disable:4355 ) // 'this' : used in base member initializer list
#endif

ShenandoahHeap::ShenandoahHeap(ShenandoahCollectorPolicy* policy) :
  CollectedHeap(),
  _active_generation(nullptr),
  _initial_size(0),
  _committed(0),
  _alloc_rate_decay(&_alloc_rate),
  _max_workers(MAX3(ConcGCThreads, ParallelGCThreads, 1U)),
  _workers(nullptr),
  _safepoint_workers(nullptr),
  _heap_region_special(false),
  _num_regions(0),
  _regions(nullptr),
  _affiliations(nullptr),
  _biased_affiliations(nullptr),
  _gc_state_changed(false),
  _gc_no_progress_count(0),
  _cancel_requested_time(0),
  _update_refs_iterator(this),
  _global_generation(nullptr),
  _control_thread(nullptr),
  _uncommit_thread(nullptr),
  _young_generation(nullptr),
  _old_generation(nullptr),
  _shenandoah_policy(policy),
  _gc_mode(nullptr),
  _free_set(nullptr),
  _allocator(nullptr),
  _verifier(nullptr),
  _phase_timings(nullptr),
  _monitoring_support(nullptr),
  _memory_pool(nullptr),
  _stw_memory_manager("Shenandoah Pauses"),
  _cycle_memory_manager("Shenandoah Cycles"),
  _gc_timer(new ConcurrentGCTimer()),
  _log_min_obj_alignment_in_bytes(LogMinObjAlignmentInBytes),
  _marking_context(nullptr),
  _bitmap_size(0),
  _bitmap_regions_per_slice(0),
  _bitmap_bytes_per_slice(0),
  _bitmap_region_special(false),
  _aux_bitmap_region_special(false),
  _liveness_cache(nullptr),
  _collection_set(nullptr),
  _cset_map(),
  _evac_tracker(new ShenandoahEvacuationTracker()),
  _injected_pin_count(0)
{
  // Initialize GC mode early, many subsequent initialization procedures depend on it
  initialize_mode();
  _cancelled_gc.set(GCCause::_no_gc);
}

#ifdef _MSC_VER
#pragma warning( pop )
#endif

void ShenandoahHeap::print_heap_on(outputStream* st) const {
  const bool is_generational = mode()->is_generational();
  const char* front_spacing = "";
  if (is_generational) {
    st->print_cr("Generational Shenandoah Heap");
    st->print_cr(" Young:");
    st->print_cr("  " PROPERFMT " max, " PROPERFMT " used", PROPERFMTARGS(young_generation()->max_capacity()), PROPERFMTARGS(young_generation()->used()));
    st->print_cr(" Old:");
    st->print_cr("  " PROPERFMT " max, " PROPERFMT " used", PROPERFMTARGS(old_generation()->max_capacity()), PROPERFMTARGS(old_generation()->used()));
    st->print_cr(" Entire heap:");
    st->print_cr("  " PROPERFMT " soft max, " PROPERFMT " committed",
                PROPERFMTARGS(soft_max_capacity()), PROPERFMTARGS(committed()));
    front_spacing = " ";
  } else {
    st->print_cr("Shenandoah Heap");
    st->print_cr("  " PROPERFMT " max, " PROPERFMT " soft max, " PROPERFMT " committed, " PROPERFMT " used",
      PROPERFMTARGS(max_capacity()),
      PROPERFMTARGS(soft_max_capacity()),
      PROPERFMTARGS(committed()),
      PROPERFMTARGS(used())
    );
  }
  st->print_cr("%s %zu x " PROPERFMT " regions",
          front_spacing,
          num_regions(),
          PROPERFMTARGS(ShenandoahHeapRegion::region_size_bytes()));

  st->print("Status: ");
  if (has_forwarded_objects())                 st->print("has forwarded objects, ");
  if (!is_generational) {
    if (is_concurrent_mark_in_progress())      st->print("marking,");
  } else {
    if (is_concurrent_old_mark_in_progress())    st->print("old marking, ");
    if (is_concurrent_young_mark_in_progress())  st->print("young marking, ");
  }
  if (is_evacuation_in_progress())             st->print("evacuating, ");
  if (is_update_refs_in_progress())            st->print("updating refs, ");
  if (is_degenerated_gc_in_progress())         st->print("degenerated gc, ");
  if (is_full_gc_in_progress())                st->print("full gc, ");
  if (is_full_gc_move_in_progress())           st->print("full gc move, ");
  if (is_concurrent_weak_root_in_progress())   st->print("concurrent weak roots, ");
  if (is_concurrent_strong_root_in_progress() &&
      !is_concurrent_weak_root_in_progress())  st->print("concurrent strong roots, ");

  if (cancelled_gc()) {
    st->print("cancelled");
  } else {
    st->print("not cancelled");
  }
  st->cr();

  st->print_cr("Reserved region:");
  st->print_cr(" - [" PTR_FORMAT ", " PTR_FORMAT ") ",
               p2i(reserved_region().start()),
               p2i(reserved_region().end()));

  ShenandoahCollectionSet* cset = collection_set();
  st->print_cr("Collection set:");
  if (cset != nullptr) {
    st->print_cr(" - map (vanilla): " PTR_FORMAT, p2i(cset->map_address()));
    st->print_cr(" - map (biased):  " PTR_FORMAT, p2i(cset->biased_map_address()));
  } else {
    st->print_cr(" (null)");
  }

  st->cr();

  if (Verbose) {
    st->cr();
    print_heap_regions_on(st);
  }
}

void ShenandoahHeap::print_gc_on(outputStream* st) const {
  print_heap_regions_on(st);
}

class ShenandoahInitWorkerGCLABClosure : public ThreadClosure {
public:
  void do_thread(Thread* thread) {
    assert(thread != nullptr, "Sanity");
    ShenandoahThreadLocalData::initialize_gclab(thread);
  }
};

void ShenandoahHeap::initialize_generations() {
  _global_generation->post_initialize(this);
}

// We do not call this explicitly  It is called by Hotspot infrastructure.
void ShenandoahHeap::post_initialize() {
  CollectedHeap::post_initialize();

  check_soft_max_changed();

  // Schedule periodic task to report on gc thread CPU utilization
  _mmu_tracker.initialize();

  // Periodically decay allocation rate to compensate for not being updated when allocation rate
  // is low. Heuristics are evaluated unconditionally from a dedicated thread so it will continue
  // to see the last (possibly stale) allocation rate if the allocation rate is low.
  _alloc_rate_decay.enroll();

  MutexLocker ml(Threads_lock);

  ShenandoahInitWorkerGCLABClosure init_gclabs;
  _workers->threads_do(&init_gclabs);

  // gclab can not be initialized early during VM startup, as it can not determinate its max_size.
  // Now, we will let WorkerThreads to initialize gclab when new worker is created.
  _workers->set_initialize_gclab();

  // Note that the safepoint workers may require gclabs if the threads are used to create a heap dump
  // during a concurrent evacuation phase.
  if (_safepoint_workers != nullptr) {
    _safepoint_workers->threads_do(&init_gclabs);
    _safepoint_workers->set_initialize_gclab();
  }

  JFR_ONLY(ShenandoahJFRSupport::register_jfr_type_serializers();)
}

void ShenandoahHeap::post_initialize_heuristics() {
  _global_generation->post_initialize_heuristics();
}

ShenandoahHeuristics* ShenandoahHeap::heuristics() {
  return _global_generation->heuristics();
}

size_t ShenandoahHeap::used() const {
  return global_generation()->used();
}

size_t ShenandoahHeap::committed() const {
  return _committed.load_relaxed();
}

void ShenandoahHeap::increase_committed(size_t bytes) {
  shenandoah_assert_heaplocked_or_safepoint();
  _committed.fetch_then_add(bytes, memory_order_relaxed);
}

void ShenandoahHeap::decrease_committed(size_t bytes) {
  shenandoah_assert_heaplocked_or_safepoint();
  _committed.fetch_then_sub(bytes, memory_order_relaxed);
}

size_t ShenandoahHeap::capacity() const {
  return committed();
}

size_t ShenandoahHeap::max_capacity() const {
  return _num_regions * ShenandoahHeapRegion::region_size_bytes();
}

size_t ShenandoahHeap::soft_max_capacity() const {
  size_t v = _soft_max_size.load_relaxed();
  assert(min_capacity() <= v && v <= max_capacity(),
         "Should be in bounds: %zu <= %zu <= %zu",
         min_capacity(), v, max_capacity());
  return v;
}

void ShenandoahHeap::set_soft_max_capacity(size_t v) {
  assert(min_capacity() <= v && v <= max_capacity(),
         "Should be in bounds: %zu <= %zu <= %zu",
         min_capacity(), v, max_capacity());
  _soft_max_size.store_relaxed(v);
  heuristics()->compute_headroom_adjustment();
}

size_t ShenandoahHeap::min_capacity() const {
  return _minimum_size;
}

size_t ShenandoahHeap::initial_capacity() const {
  return _initial_size;
}

bool ShenandoahHeap::is_in(const void* p) const {
  if (!is_in_reserved(p)) {
    return false;
  }

  if (is_full_gc_move_in_progress()) {
    // Full GC move is running, we do not have a consistent region
    // information yet. But we know the pointer is in heap.
    return true;
  }

  // Now check if we point to a live section in active region.
  ShenandoahHeapRegion* r = heap_region_containing(p);
  if (p >= r->top()) {
    if (!_collection_set->is_reusable(r) && !r->is_cset()) {
      return false;
    }
    return true;
  }

  if (r->is_active()) {
    return true;
  }

  // The region is trash, but won't be recycled until after concurrent weak
  // roots. We also don't allow mutators to allocate from trash regions
  // during weak roots. Concurrent class unloading may access unmarked oops
  // in trash regions.
  return r->is_trash() && is_concurrent_weak_root_in_progress();
}

void ShenandoahHeap::notify_soft_max_changed() {
  if (_uncommit_thread != nullptr) {
    _uncommit_thread->notify_soft_max_changed();
  }
}

void ShenandoahHeap::notify_explicit_gc_requested() {
  if (_uncommit_thread != nullptr) {
    _uncommit_thread->notify_explicit_gc_requested();
  }
}

bool ShenandoahHeap::check_soft_max_changed() {
  size_t new_soft_max = AtomicAccess::load(&SoftMaxHeapSize);
  size_t old_soft_max = soft_max_capacity();
  if (new_soft_max != old_soft_max) {
    new_soft_max = clamp(new_soft_max, min_capacity(), max_capacity());
    if (new_soft_max != old_soft_max) {
      log_info(gc)("Soft Max Heap Size: %zu%s -> %zu%s",
                   byte_size_in_proper_unit(old_soft_max), proper_unit_for_byte_size(old_soft_max),
                   byte_size_in_proper_unit(new_soft_max), proper_unit_for_byte_size(new_soft_max)
      );
      set_soft_max_capacity(new_soft_max);
      return true;
    }
  }
  return false;
}

void ShenandoahHeap::notify_heap_changed() {
  // Update monitoring counters when we took a new region. This amortizes the
  // update costs on slow path.
  monitoring_support()->notify_heap_changed();
  _heap_changed.try_set();
}

void ShenandoahHeap::start_idle_span() {
  heuristics()->start_idle_span();
}

void ShenandoahHeap::set_forced_counters_update(bool value) {
  monitoring_support()->set_forced_counters_update(value);
}

void ShenandoahHeap::handle_force_counters_update() {
  monitoring_support()->handle_force_counters_update();
}

HeapWord* ShenandoahHeap::allocate_from_gclab_slow(Thread* thread, size_t size) {
  // New object should fit the GCLAB size
  size_t min_size = MAX2(size, PLAB::min_size());

  // Figure out size of new GCLAB, looking back at heuristics. Expand aggressively.
  size_t new_size = ShenandoahThreadLocalData::gclab_size(thread) * 2;

  new_size = MIN2(new_size, PLAB::max_size());
  new_size = MAX2(new_size, PLAB::min_size());

  // Record new heuristic value even if we take any shortcut. This captures
  // the case when moderately-sized objects always take a shortcut. At some point,
  // heuristics should catch up with them.
  log_debug(gc, free)("Set new GCLAB size: %zu", new_size);
  ShenandoahThreadLocalData::set_gclab_size(thread, new_size);

  if (new_size < size) {
    // New size still does not fit the object. Fall back to shared allocation.
    // This avoids retiring perfectly good GCLABs, when we encounter a large object.
    log_debug(gc, free)("New gclab size (%zu) is too small for %zu", new_size, size);
    return nullptr;
  }

  // Retire current GCLAB, and allocate a new one.
  PLAB* gclab = ShenandoahThreadLocalData::gclab(thread);
  gclab->retire();

  size_t actual_size = 0;
  HeapWord* gclab_buf = allocate_new_gclab(min_size, new_size, &actual_size);
  if (gclab_buf == nullptr) {
    return nullptr;
  }

  assert (size <= actual_size, "allocation should fit");

  // ...and clear or zap just allocated TLAB, if needed.
  if (ZeroTLAB) {
    Copy::zero_to_words(gclab_buf, actual_size);
  } else if (ZapTLAB) {
    // Skip mangling the space corresponding to the object header to
    // ensure that the returned space is not considered parsable by
    // any concurrent GC thread.
    size_t hdr_size = oopDesc::header_size();
    Copy::fill_to_words(gclab_buf + hdr_size, actual_size - hdr_size, badHeapWordVal);
  }
  gclab->set_buf(gclab_buf, actual_size);
  return gclab->allocate(size);
}

// Called from stubs in JIT code or interpreter
HeapWord* ShenandoahHeap::allocate_new_tlab(size_t min_size,
                                            size_t requested_size,
                                            size_t* actual_size) {
  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_tlab(min_size, requested_size);
  HeapWord* res = allocate_memory(req);
  if (res != nullptr) {
    *actual_size = req.actual_size();
  } else {
    *actual_size = 0;
  }
  return res;
}

HeapWord* ShenandoahHeap::allocate_new_gclab(size_t min_size,
                                             size_t word_size,
                                             size_t* actual_size) {
  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_gclab(min_size, word_size);
  HeapWord* res = allocate_memory(req);
  if (res != nullptr) {
    *actual_size = req.actual_size();
  } else {
    *actual_size = 0;
  }
  return res;
}

HeapWord* ShenandoahHeap::allocate_memory(ShenandoahAllocRequest& req) {
  bool in_new_region = false;
  HeapWord* result = nullptr;

  if (req.is_mutator_alloc()) {

    if (!ShenandoahAllocFailureALot || !should_inject_alloc_failure()) {
      result = allocate_memory_work(req, in_new_region);
    }

    // Check that gc overhead is not exceeded.
    //
    // Shenandoah will grind along for quite a while allocating one
    // object at a time using shared (non-tlab) allocations. This check
    // is testing that the GC overhead limit has not been exceeded.
    // This will notify the collector to start a cycle, but will raise
    // an OOME to the mutator if the last Full GCs have not made progress.
    // gc_no_progress_count is incremented following each degen or full GC that fails to achieve is_good_progress().
    if (result == nullptr && !req.is_lab_alloc() && get_gc_no_progress_count() > ShenandoahNoProgressThreshold) {
      control_thread()->handle_alloc_failure(req, false);
      req.set_actual_size(0);
      return nullptr;
    }

    if (result == nullptr) {
      // Block until control thread reacted, then retry allocation.
      //
      // It might happen that one of the threads requesting allocation would unblock
      // way later after GC happened, only to fail the second allocation, because
      // other threads have already depleted the free storage. In this case, a better
      // strategy is to try again, until at least one full GC has completed.
      //
      // Stop retrying and return nullptr to cause OOMError exception if our allocation failed even after:
      //   a) We experienced a GC that had good progress, or
      //   b) We experienced at least one Full GC (whether or not it had good progress)

      const size_t original_count = shenandoah_policy()->full_gc_count();
      while (result == nullptr && should_retry_allocation(original_count)) {
        control_thread()->handle_alloc_failure(req, true);
        result = allocate_memory_work(req, in_new_region);
      }
      if (result != nullptr) {
        // If our allocation request has been satisfied after it initially failed, we count this as good gc progress
        notify_gc_progress();
      }
      if (log_develop_is_enabled(Debug, gc, alloc)) {
        ResourceMark rm;
        log_debug(gc, alloc)("Thread: %s, Result: " PTR_FORMAT ", Request: %s, Size: %zu"
                             ", Original: %zu, Latest: %zu",
                             Thread::current()->name(), p2i(result), req.type_string(), req.size(),
                             original_count, get_gc_no_progress_count());
      }
    }
  } else {
    assert(req.is_gc_alloc(), "Can only accept GC allocs here");
    result = allocate_memory_work(req, in_new_region);
    // Do not call handle_alloc_failure() here, because we cannot block.
    // The allocation failure would be handled by the LRB slowpath with handle_alloc_failure_evac().
  }

  if (in_new_region) {
    notify_heap_changed();
  }

  if (result == nullptr) {
    req.set_actual_size(0);
  }

  if (result != nullptr) {
    size_t requested = req.size();
    size_t actual = req.actual_size();

    assert (req.is_lab_alloc() || (requested == actual),
            "Only LAB allocations are elastic: %s, requested = %zu, actual = %zu",
            req.type_string(), requested, actual);
  }

  return result;
}

inline bool ShenandoahHeap::should_retry_allocation(size_t original_full_gc_count) const {
  return shenandoah_policy()->full_gc_count() == original_full_gc_count
      && !shenandoah_policy()->is_at_shutdown();
}

HeapWord* ShenandoahHeap::allocate_memory_work(ShenandoahAllocRequest& req, bool& in_new_region) {
  // Reserve the promotion budget up front so it is enforced atomically without the heap lock.
  // If the reserve is exhausted, deny the promotion rather than overshoot it; the reservation
  // is refunded below if the allocation itself fails.
  if (req.is_promotion() && !old_generation()->try_expend_promoted(req.size() << LogHeapWordSize)) {
    return nullptr;
  }

  HeapWord* result = _allocator->allocate(req, in_new_region);

  if (result != nullptr) {
    if (req.is_mutator_alloc()) {
      _alloc_rate.allocated((req.actual_size() + req.waste()) * HeapWordSize);
    }

    if (req.is_old()) {
      if (req.is_lab_alloc()) {
        old_generation()->configure_plab_for_current_thread(req);
      } else if (req.is_promotion()) {
        log_debug(gc, plab)("Expend shared promotion of %zu bytes", req.actual_size() * HeapWordSize);
      }
    }
  } else if (req.is_promotion()) {
    // Allocation failed, so refund the promotion budget reserved above.
    old_generation()->unexpend_promoted(req.size() << LogHeapWordSize);
  }
  return result;
}

HeapWord* ShenandoahHeap::mem_allocate(size_t size) {
  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_shared(size);
  return allocate_memory(req);
}

oop ShenandoahHeap::array_allocate(Klass* klass, size_t size, int length, bool do_zero, TRAPS) {
  ShenandoahObjArrayAllocator allocator(klass, size, length, do_zero, THREAD);
  return allocator.allocate();
}

MetaWord* ShenandoahHeap::satisfy_failed_metadata_allocation(ClassLoaderData* loader_data,
                                                             size_t size,
                                                             Metaspace::MetadataType mdtype) {
  MetaWord* result;

  // Inform metaspace OOM to GC heuristics if class unloading is possible.
  ShenandoahHeuristics* h = global_generation()->heuristics();
  if (h->can_unload_classes()) {
    h->record_metaspace_oom();
  }

  // Expand and retry allocation
  result = loader_data->metaspace_non_null()->expand_and_allocate(size, mdtype);
  if (result != nullptr) {
    return result;
  }

  // Start full GC
  collect(GCCause::_metadata_GC_clear_soft_refs);

  // Retry allocation
  result = loader_data->metaspace_non_null()->allocate(size, mdtype);
  if (result != nullptr) {
    return result;
  }

  // Expand and retry allocation
  result = loader_data->metaspace_non_null()->expand_and_allocate(size, mdtype);
  if (result != nullptr) {
    return result;
  }

  // Out of memory
  return nullptr;
}

class ShenandoahGCStatePropagatorHandshakeClosure : public HandshakeClosure {
public:
  explicit ShenandoahGCStatePropagatorHandshakeClosure(char gc_state) :
    HandshakeClosure("Shenandoah GC State Change"),
    _gc_state(gc_state) {}

  void do_thread(Thread* thread) override {
    ShenandoahThreadLocalData::set_gc_state(thread, _gc_state);
  }
private:
  char _gc_state;
};

class ShenandoahPrepareForUpdateRefsHandshakeClosure : public HandshakeClosure {
public:
  explicit ShenandoahPrepareForUpdateRefsHandshakeClosure(char gc_state) :
    HandshakeClosure("Shenandoah Prepare for Update Refs"),
    _retire(ResizeTLAB), _propagator(gc_state) {}

  void do_thread(Thread* thread) override {
    _propagator.do_thread(thread);
    if (ShenandoahThreadLocalData::gclab(thread) != nullptr) {
      _retire.do_thread(thread);
    }
  }
private:
  ShenandoahRetireGCLABClosure _retire;
  ShenandoahGCStatePropagatorHandshakeClosure _propagator;
};

void ShenandoahHeap::prepare_to_evacuate(bool prepare_for_early_recycle) {
  ShenandoahFreeSet* free_set = this->free_set();
  ShenandoahCollectionSet* cset = collection_set();
  ShenandoahMarkingContext* ctx = marking_context();


  size_t cset_region_count = cset->count();
#ifdef KELVIN_DEBUG
  log_info(gc)("prepare_to_evacuate() is processing %zu cset regions", cset_region_count);
#endif
  free_set->preinitialize_recycled_regions_array(cset_region_count);
  size_t cset_region_no = 0;
  size_t num_regions = this->num_regions();
  for (size_t i = 0; i < num_regions; i++) {
    if (cset->is_in(i)) {
      free_set->set_tlab_region_slot(cset_region_no++, get_region(i));
#ifdef KELVIN_DEPRECATE
      if (prepare_for_early_recycle) {
        size_t marked_object_count = ctx->count_mark_bit_conflicts<1>(r->bottom(), r->top());
        r->set_expected_forwardings(marked_object_count);
      }
#endif
      // else, we don't care about early recycling
    }
  }
  // KELVIN TO DO:
  //  SHOULDN'T THE ENTIRE BODY OF THIS FUNCTION BE CONDITIONED UPON PREPARE_FOR_EARLY_RECYCLE?

  if (prepare_for_early_recycle) {
    free_set->set_fwt_regions_prepared(0);
    free_set->set_fwt_bytes_prepared(0);
    free_set->set_fwt_regions_published(0);
    free_set->set_fwt_bytes_published(0);
    free_set->set_fwt_regions_consumed_by_gc(0);
    free_set->set_fwt_bytes_consumed_by_young_gc(0);
    free_set->set_fwt_bytes_consumed_by_old_gc(0);
    free_set->set_fwt_regions_retired(0);
    free_set->set_fwt_bytes_retired(0);
    free_set->set_young_fwt_bytes_to_mutator(0);
    free_set->set_old_fwt_bytes_to_mutator(0);

    // In the ideal, we would sort regions in order of increasing expected forwardings, but that information is not readily
    // available.  Instead, we sort by live data.
    free_set->sort_early_recycle_for_evac(cset_region_no);
  }
}

void ShenandoahHeap::evacuate_collection_set(ShenandoahGeneration* generation,
                                             bool concurrent, bool require_early_recycle_preprocessing) {
  assert(generation->is_global(), "Only global generation expected here");
  ShenandoahEvacuationTask task(this, _collection_set, concurrent, require_early_recycle_preprocessing, workers()->active_workers());
  prepare_to_evacuate(concurrent);
  workers()->run_task(&task);
  if (cancelled_gc()) {
    // We didn't finish the work.  Do not do final publish and do not
    // switch to reuse forwarding until the degen cycle finishes evacuation.
    return;
  }
#ifdef KELVIN_DEBUG
  log_info(gc)("Back from running ShenEvacTask, concurrent is %d", concurrent);
#endif
  if (concurrent && ShenandoahCSetReuse && (free_set()->fwt_bytes_prepared() > free_set()->fwt_bytes_published())) {
#ifdef KELVIN_DEBUG
    log_info(gc)("Evacuation is done. Running one last handshake because regions prepared was less than regions-published");
#endif
    free_set()->publish_prepared_regions(0, 1 /* only 1 worker now */, true /* is_concurrent */,
                                         true /*   all cset regions are prepared */,
                                         collection_set(), false /* is_generational */);
  }
  _collection_set->switch_to_reuse_forwarding();
}

class ShenandoahCompleteStackWatermarkHandshakeClosure : public HandshakeClosure {
public:
  ShenandoahCompleteStackWatermarkHandshakeClosure() : HandshakeClosure("Shenandoah Complete stacks handshake") {}
  void do_thread(Thread* thread) override {
    if (thread->is_Java_thread()) {
      JavaThread* jt = JavaThread::cast(thread);
      StackWatermarkSet::finish_processing(jt, nullptr, StackWatermarkKind::gc);
    }
  }
};

void ShenandoahHeap::concurrent_prepare_for_update_refs() {
  // The stack watermarks are active since op_final_roots().
  // Make sure the current stack watermark machinery has completed before we drop evac flags.
  // Otherwise the stack processing on stack unwinding may enter evac closure concurrently.
  ShenandoahCompleteStackWatermarkHandshakeClosure cl;
  Handshake::execute(&cl);

  {
    // Java threads take this lock while they are being attached and added to the list of threads.
    // If another thread holds this lock before we update the gc state, it will receive a stale
    // gc state, but they will have been added to the list of java threads and so will be corrected
    // by the following handshake.
    MutexLocker lock(Threads_lock);

    // A cancellation at this point means the degenerated cycle must resume from update-refs.
    set_gc_state_concurrent(EVACUATION, false);
    set_gc_state_concurrent(UPDATE_REFS, true);
  }

  // This will propagate the gc state and retire gclabs and plabs for threads that require it.
  ShenandoahPrepareForUpdateRefsHandshakeClosure prepare_for_update_refs(_gc_state.raw_value());

  // The handshake won't touch worker threads (or control thread, or VM thread), so do those separately.
  Threads::non_java_threads_do(&prepare_for_update_refs);

  // Now retire gclabs and plabs and propagate gc_state for mutator threads
  Handshake::execute(&prepare_for_update_refs);

  _update_refs_iterator.reset();
}

void ShenandoahHeap::op_final_roots() {
#ifdef ASSERT
  // Check if stack watermark machinery is in safe state:
  //  1. With evac-in-progress, we are about to supersede evac processing with new epoch.
  //     op_thread_roots() should have completed the stack watermark processing before
  //     we reach here.
  //  2. Without evac-in-progress, we are in abbreviated cycle, and we are switching
  //     to a new epoch that *also* does not change any oops, which is safe.
  if (is_evacuation_in_progress()) {
    for (JavaThreadIteratorWithHandle jtiwh; JavaThread* jt = jtiwh.next();) {
      StackWatermark* sw = StackWatermarkSet::get(jt, StackWatermarkKind::gc);
      assert(sw == nullptr || sw->processing_completed(),
             "Cannot turn off weak roots before stack watermark processing is complete");
    }
  }
#endif

  set_gc_state_at_safepoint(WEAK_ROOTS, false);

  // Arm the nmethods to change the barriers.
  CodeCache::arm_all_nmethods();

  {
    ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::final_roots_propagate_gc_state);
    propagate_gc_state_to_all_threads();
  }
}

oop ShenandoahHeap::evacuate_object(oop p, Thread* thread) {
  assert(thread == Thread::current(), "Expected thread parameter to be current thread.");

  ShenandoahHeapRegion* r = heap_region_containing(p);
  assert(!r->is_humongous(), "never evacuate humongous objects");

  ShenandoahAffiliation target_gen = r->affiliation();
  return try_evacuate_object(p, thread, r, target_gen);
}

oop ShenandoahHeap::try_evacuate_object(oop p, Thread* thread, ShenandoahHeapRegion* from_region,
                                               ShenandoahAffiliation target_gen) {
  assert(target_gen == YOUNG_GENERATION, "Only expect evacuations to young in this mode");
  assert(from_region->is_young(), "Only expect evacuations from young in this mode");
  bool alloc_from_lab = true;
  HeapWord* copy = nullptr;
  size_t size = ShenandoahForwarding::size(p);

#ifdef ASSERT
  if (ShenandoahOOMDuringEvacALot &&
      (os::random() & 1) == 0) { // Simulate OOM every ~2nd slow-path call
    copy = nullptr;
  } else {
#endif
    if (UseTLAB) {
      copy = allocate_from_gclab(thread, size);
    }
    if (copy == nullptr) {
      // If we failed to allocate in LAB, we'll try a shared allocation.
      ShenandoahAllocRequest req = ShenandoahAllocRequest::for_shared_gc(size, target_gen);
      copy = allocate_memory(req);
      alloc_from_lab = false;
    }
#ifdef ASSERT
  }
#endif

  if (copy == nullptr) {
    control_thread()->handle_alloc_failure_evac(size);

    // Install the self-forwarded bit on p so other evacuators/LRBs see
    // the object as "already handled, do not try to evacuate". The CAS
    // may fail if another thread concurrently installed a real forwardee
    // (they succeeded where we failed) or self-forwarded first.
    markWord old_mark = p->mark();
    if (old_mark.is_forwarded()) {
      return ShenandoahForwarding::get_forwardee(p);
    }
    oop winner = ShenandoahForwarding::try_forward_to_self(p, old_mark);
    if (winner == nullptr) {
      // We own the self-forwarding. Flag the region so the degen/full GC
      // entry drain knows to scan it for self_fwd bits to clear.
      from_region->set_has_self_forwards();
      return p;
    }
    return winner;
  }

  if (ShenandoahEvacTracking) {
    evac_tracker()->begin_evacuation(thread, size * HeapWordSize, from_region->affiliation(), target_gen);
  }

  // Copy the object:
  Copy::aligned_disjoint_words(cast_from_oop<HeapWord*>(p), copy, size);

  oop copy_val = cast_to_oop(copy);

  // Relativize stack chunks before publishing the copy. After the forwarding CAS,
  // mutators can see the copy and thaw it via the fast path if flags == 0. We must
  // relativize derived pointers and set gc_mode before that happens. Skip if the
  // copy's mark word is already a forwarding pointer (another thread won the race
  // and overwrote the original's header before we copied it).
  if (!ShenandoahForwarding::is_forwarded(copy_val)) {
    ContinuationGCSupport::relativize_stack_chunk(copy_val);
  }

  // Try to install the new forwarding pointer.
  oop result = ShenandoahForwarding::try_update_forwardee(p, copy_val);
  if (result == copy_val) {
    // Successfully evacuated. Our copy is now the public one!
    shenandoah_assert_correct(nullptr, copy_val);
    if (ShenandoahEvacTracking) {
      evac_tracker()->end_evacuation(thread, size * HeapWordSize, from_region->affiliation(), target_gen);
    }
    return copy_val;
  }  else {
    // Failed to evacuate. We need to deal with the object that is left behind. Since this
    // new allocation is certainly after TAMS, it will be considered live in the next cycle.
    // But if it happens to contain references to evacuated regions, those references would
    // not get updated for this stale copy during this cycle, and we will crash while scanning
    // it the next cycle.
    if (alloc_from_lab) {
      // For LAB allocations, it is enough to rollback the allocation ptr. Either the next
      // object will overwrite this stale copy, or the filler object on LAB retirement will
      // do this.
      ShenandoahThreadLocalData::gclab(thread)->undo_allocation(copy, size);
    } else {
      // For non-LAB allocations, we have no way to retract the allocation, and
      // have to explicitly overwrite the copy with the filler object. With that overwrite,
      // we have to keep the fwdptr initialized and pointing to our (stale) copy.
      assert(size >= ShenandoahHeap::min_fill_size(), "previously allocated object known to be larger than min_size");
      fill_with_object(copy, size);
      shenandoah_assert_correct(nullptr, copy_val);
      // For non-LAB allocations, the object has already been registered
    }
    shenandoah_assert_correct(nullptr, result);
    return result;
  }
}

// Returns true iff the region is successfully recycled for no-table early recycling.
bool ShenandoahHeap::finish_no_table_region_evacuation(ShenandoahHeapRegion* r, uint32_t num_forwardings) {
  // The region was presumed to have a fwd table.  Now we know it does not.

  if (r->is_pinned() || r->was_promoted_in_place() || r->has_self_forwards()) {
    r->set_early_recycle_status(ShenandoahEarlyRecycleStatus::Abandoned);
    return false;
  }

  ShenandoahMarkingContext* ctx = marking_context();
  HeapWord* bottom = r->bottom();
  size_t pad = 0;
  size_t min_fill = min_fill_size();
  size_t region_size_words = ShenandoahHeapRegion::region_size_words();
#ifdef KELVIN_DEBUG
  HeapWord* next_unmarked = marking_context()->get_next_unmarked_addr_ignore_tams(r->bottom(), r->end());
  log_info(gc)("finish_no_table_region_evacuation(region %zu), expecting to find unmarked at pad: %zu",
               r->index(), next_unmarked - bottom);
  bool first_unmarked_found = false;
#endif
  while (pad < region_size_words) {
#ifdef KELVIN_DEBUG
    if (!first_unmarked_found && !ctx->is_marked_ignore_tams(bottom + pad)) {
      assert(bottom + pad == next_unmarked,
             "get_next_unmarked_addr_ignore_tams() failed me with bottom: " PTR_FORMAT ", pad: %zd, next_unmarked " PTR_FORMAT,
             p2i(bottom), pad + ctx->show_me_is_marked_ignore_tams(bottom + pad), p2i(next_unmarked));
      first_unmarked_found = true;
    }
#endif

    if (((pad == 0) || (pad >= min_fill)) && !ctx->is_marked_ignore_tams(bottom + pad)) {
      break;
    }
    pad++;
  }
#ifdef KELVIN_DEBUG
  log_info(gc)("finish_no_table_region_evacuation() found initial unmarked at pad %zu", pad);
#endif
  if (pad >= region_size_words) {
    r->set_early_recycle_status(ShenandoahEarlyRecycleStatus::Abandoned);
    return false;
  } else {

    // TODO: Dmitry suggests that we use max-allocatable-gap and average-nearest-neighbor metrics to prioritize
    // no-table allocations. Specifically, give preference to allocating from no-fwd-table regions that have largest
    // max-allocatable-gap.

    // Google "average-nearest-neighbor computation of density" tells me:
    //  1. Do (observed mean distance): average of the shortest distance between each point and its nearest neighbor
    //  2. De (expected mean distance): average distance expected if points were randomly distributed across the same area,
    //        calculated as De = 0.5 / square-root(N/A), where N is number of points and A is area.
    // Nearest neighbor index (ANN Ratio) is Do / De
    //  ANN < 1: pattern is clustered (points are closer together than random)
    //  ANN > 1: pattern is dispersed (points are more spread out than random) - not sure we'll ever see this in our
    //           2-dimensional constrained space.
    //  ANN = 0: pattern is completely random

    // I believe we want to prioritize no-FWT allocation from regions that have "large available" memory (end - top) and 
    // have low ANN value (meaning high clusterring).  We prefer clustering of mark words because that results in larger gaps
    // between objects.

    // Can we recompute ANN incrementally?  I think so:
    //  1. At finish_no_table_region_evacution(), calculate:
    //     a. TM: total marked objects, TD: total nearest distances for all marked objects, and Do: TD / TM
    //     b. A: heap_region_size_words, and De: TM / heap_region_size_words
    //     c. ANN = Do / De
    //  2. Each time we allocate from this region, we recalculate:
    //     a. A = region->end() - region->top() (after allocation)
    //     b. TM -= number of marked_words consumed (skipped over) by this allocation, if any
    //     c. TD -= nearest distances for each of the consumed words (maybe we remember previously marked to avoid lookup)
    //     d. Updated value of Do: TD / TM
    //     e. Updated value of De: TM / A
    //     f. Updated ANN: Do / De

    // How do we judge between larger available and lower clustering?
    //  Is it better to allocate from region with 1 MB aailable, and ANN 0.2 or 0.5 MB available and ANN 0.01?

    // Example: Consider A = 100, TM = 10, TD = 10, Do = 1, De = 0.5 / square_root(0.1) = 1.581, ANN = 0.632
    //  There are multiple ways for Do to equal 1:
    //  Case 1: 10 mark words are in first ten consecutive locations of A, leaving allocation gap of 90
    //  Case 2: 10 mark wors are in middle ten consecutive locations of A, leaving 2 allocation gaps of 45
    //    ...
    //  Case N: (This is worst case) We have 5 clusters and 6 gaps, with each gap of size 90/6 = 15.
    //          The marked words are at 15,16, 32,33, 49,50, 66,67, 83,84

    // An optimistic prioiritization would be based on case 1, the general formula of "gap size" being:
    //   optimistic_gap_size (OGS) is: (A - TM * Do)
    // A pessimistic prioritization would be based on case N, the general formula being:
    //   pessimistic_gap_size (PGS) is: (A - TM * Do) / (TM / 2 + 1)

    // Note that a region with a higher pessimistic_gap_size will oten have a higher optimistic_gap_size, but
    // not always. Consider:

    // region 1 with: A = 2M, TM = 1000, Do = 10:
    //                       OGS = 2M - 10000 = 2_087_152
    //               PGS = (2M - 10000) / 501 =     4_166
    // region 2 with A = 2M, TM = 500, Do = 10
    //                        OGS = 2M - 5000 = 2_092_152
    //                PGS = (2M - 5000) / 251 =     8_335
    // region 3 with A = 2M, TM = 15_000, Do = 3
    //                      OGS = 2M - 45_000 = 2_052_152
    //            PGS = (2M - 45_000) / 7_501 =       273
    // region 4 with A = 2M, TM = 6_000, Do = 8
    //                      OGS = 2M - 48_000 = 2_049_152
    //            PGS = (2M - 48_000) / 3_001 =       683

    // Optimistically, we would allocate from region 2, then region 1, then region 3, then region 4
    // Pessimisitcally, we would allocate from region 2, then region 1, then region 4, then region 3

    // I suggest we prioritize in order of decreasing pessimistic_gap_size. This automatically differentiates between
    // regions that have large allocatable vs smaller allocatable.  I don't see a need to compute De or ANN.


    r->set_no_fwt_mark_words((uint32_t) num_forwardings);

#ifdef KELVIN_DENSITY
    log_info(gc)("finish_no_table_evac(region %zu) calculates density: %f from num_forwardings: %zu, region_size_words: %zu",
                 r->index(), ((float) num_forwardings) / ShenandoahHeapRegion::region_size_words(),
                 num_forwardings, ShenandoahHeapRegion::region_size_words());
                 
#endif

    r->set_density_at_most_recent_sift(((float) num_forwardings) / ShenandoahHeapRegion::region_size_words());
    r->set_consumed_mark_words(0);
    r->set_next_no_fwt_conflict(marking_context()->get_next_marked_addr_ignore_tams(r->bottom(), r->end()));
    r->set_alt_top(r->top());
    r->set_top(bottom + pad);
    r->set_early_recycle_status(ShenandoahEarlyRecycleStatus::Prepared_NO_FWT);
    r->set_reserved_body_words(num_forwardings);
    OrderAccess::fence();
    return true;
  }
}

bool ShenandoahHeap::finish_fwt_region_evacuation(ShenandoahHeapRegion* r) {
  // KELVIN TODO: WHY CAN'T WE RECYCLE OLD CSET REGIONS?  WE PROBABLY WILL HAVE TO CLEAR THE REMSET CARDS.
  if (r->is_old()) {
    return false;
  }
  // KELVIN TODO: MOST OF THESE FALSE RETURNS SHOULD BE REPLACED WITH ASSERTS. IF WE CAN'T REPLACE WITH ASSERTS, WE
  // NEED TO RETHINK HOW WE BUDGET FOR FWT REGIONS.

  assert(ShenandoahHeap::heap()->marking_context()->top_at_mark_start(r) == r->top(), "TAMS must be set to top");
  if (!ShenandoahCSetReuse) {
    return false;
  }
#ifdef KELVIN_DEPRECATE
  // STW GC uses mark-word forwarding.
  if (!concurrent) {
    return false;
  }
#endif
  // There should be no live objects.
  if (r->is_pinned() || r->was_promoted_in_place() || r->has_self_forwards()) {
    r->set_early_recycle_status(ShenandoahEarlyRecycleStatus::Abandoned);
    return false;
  } else {
    assert(ShenandoahCSetAllocationForwardingTable, "Should not be here");
    bool can_reuse = r->prepare_reuse_forwarding();
    if (can_reuse) {
      return true;
    } else {
      r->set_early_recycle_status(ShenandoahEarlyRecycleStatus::Abandoned);
      return false;
    }
  }
}

// Clear the self_fwd bit on a live cset object, if set. Runs at a safepoint,
// so a plain store is sufficient — no concurrent writers to the mark word.
class ShenandoahUnSelfForwardObjectClosure : public ObjectClosure {
public:
  void do_object(oop obj) override {
    markWord m = obj->mark();
    if (m.is_self_forwarded()) {
      obj->set_mark(m.unset_self_forwarded());
    }
  }
};

// Parallel task over flagged cset regions. Iterates the live objects via the
// mark bitmap (skipping evacuated and never-marked memory), clears self_fwd
// bits, and resets the region flag once done.
class ShenandoahUnSelfForwardTask : public WorkerTask {
private:
  ShenandoahHeap*          const _heap;
  ShenandoahCollectionSet* const _cs;

public:
  ShenandoahUnSelfForwardTask(ShenandoahHeap* heap, ShenandoahCollectionSet* cs) :
    WorkerTask("Shenandoah Un-Self-Forward"),
    _heap(heap),
    _cs(cs) {}

  void work(uint worker_id) override {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahUnSelfForwardObjectClosure cl;
    ShenandoahHeapRegion* r;
    while ((r = _cs->claim_next()) != nullptr) {
      if (r->has_self_forwards()) {
        _heap->marked_object_iterate(r, &cl);
        r->clear_has_self_forwards();
      }
    }
  }
};

void ShenandoahHeap::un_self_forward_cset_regions() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "must be at safepoint");
  ShenandoahCollectionSet* cs = collection_set();
  if (cs == nullptr || cs->is_empty()) {
    return;
  }
  cs->clear_current_index();
  ShenandoahUnSelfForwardTask task(this, cs);
  workers()->run_task(&task);
  DEBUG_ONLY(assert_no_self_forwards());
}

#ifdef ASSERT
void ShenandoahHeap::assert_no_self_forwards() const {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "must be at safepoint");
  ShenandoahCollectionSet* cs = collection_set();
  if (cs == nullptr) return;
  cs->clear_current_index();
  ShenandoahHeapRegion* r;
  while ((r = cs->next()) != nullptr) {
    assert(!r->has_self_forwards(), "region still flagged after drain");
  }
  cs->clear_current_index();
}
#endif

void ShenandoahHeap::trash_cset_regions() {
  ShenandoahHeapLocker locker(lock());
  ShenandoahCollectionSet* cset = collection_set();
  ShenandoahHeapRegion* r;
  cset->clear_current_index();
  while ((r = cset->next()) != nullptr) {
    if (cset->is_reusable(r)) {
      bool has_live_objects = (r->top() != r->bottom());
      if (has_live_objects) {
        r->make_regular_from_cset();
        marking_context()->clear_bitmap(r);
      } else if (free_set()->membership(r->index()) == ShenandoahFreeSetPartitionId::NotFree) {
        r->make_trash();
      } else {
        r->finish_recycle_early();
      }
    } else {
      r->make_trash();
    }
  }

  free_set()->finish_cset_region_recycling();

  cset->clear();
}

void ShenandoahHeap::print_heap_regions_on(outputStream* st) const {
  st->print_cr("Heap Regions:");
  st->print_cr("Region state: EU=empty-uncommitted, EC=empty-committed, R=regular, H=humongous start, HP=pinned humongous start");
  st->print_cr("              HC=humongous continuation, CS=collection set, TR=trash, P=pinned, CSP=pinned collection set");
  st->print_cr("A=age, BTE=bottom/top/end, TAMS=top-at-mark-start, UWM=update watermark, U=used");
  st->print_cr("T=TLAB allocs, G=GCLAB allocs, S=shared allocs, L=live data");
  st->print_cr("CP=critical pins");

  for (size_t i = 0; i < num_regions(); i++) {
    get_region(i)->print_on(st);
  }
}

void ShenandoahHeap::process_gc_stats() const {
  // Commit worker statistics to cycle data
  phase_timings()->flush_par_workers_to_cycle();

  // Print GC stats for current cycle
  LogTarget(Info, gc, stats) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    phase_timings()->print_cycle_on(&ls);
    if (ShenandoahEvacTracking) {
      ShenandoahCycleStats  evac_stats = evac_tracker()->flush_cycle_to_global();
      evac_tracker()->print_evacuations_on(&ls, &evac_stats.workers,
                                               &evac_stats.mutators);
    }
  }

  // Commit statistics to globals
  phase_timings()->flush_cycle_to_global();
}

size_t ShenandoahHeap::trash_humongous_region_at(ShenandoahHeapRegion* start) const {
  assert(start->is_humongous_start(), "reclaim regions starting with the first one");
  assert(!start->has_live(), "liveness must be zero");

  // Do not try to get the size of this humongous object. STW collections will
  // have already unloaded classes, so an unmarked object may have a bad klass pointer.
  ShenandoahHeapRegion* region = start;
  size_t index = region->index();
  do {
    assert(region->is_humongous(), "Expect correct humongous start or continuation");
    assert(!region->is_cset(), "Humongous region should not be in collection set");
    region->make_trash_immediate();
    region = get_region(++index);
  } while (region != nullptr && region->is_humongous_continuation());

  // Return number of regions trashed
  return index - start->index();
}

class ShenandoahCheckCleanGCLABClosure : public ThreadClosure {
public:
  ShenandoahCheckCleanGCLABClosure() {}
  void do_thread(Thread* thread) {
    PLAB* gclab = ShenandoahThreadLocalData::gclab(thread);
    assert(gclab != nullptr, "GCLAB should be initialized for %s", thread->name());
    assert(gclab->words_remaining() == 0, "GCLAB should not need retirement");

    if (ShenandoahHeap::heap()->mode()->is_generational()) {
      ShenandoahPLAB* shenandoah_plab = ShenandoahThreadLocalData::shenandoah_plab(thread);
      assert(shenandoah_plab != nullptr, "PLAB should be initialized for %s", thread->name());
      assert(shenandoah_plab->plab()->words_remaining() == 0, "PLAB should not need retirement");
    }
  }
};

void ShenandoahHeap::labs_make_parsable() {
  assert(UseTLAB, "Only call with UseTLAB");

  ShenandoahRetireGCLABClosure cl(false);

  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    ThreadLocalAllocBuffer& tlab = t->tlab();
    tlab.make_parsable();
    if (ZeroTLAB) {
      t->retire_tlab();
    }
    cl.do_thread(t);
  }

  workers()->threads_do(&cl);

  if (safepoint_workers() != nullptr) {
    safepoint_workers()->threads_do(&cl);
  }
}

void ShenandoahHeap::tlabs_retire(bool resize) {
  assert(UseTLAB, "Only call with UseTLAB");
  assert(!resize || ResizeTLAB, "Only call for resize when ResizeTLAB is enabled");

  ThreadLocalAllocStats stats;

  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    t->retire_tlab(&stats);
    if (resize) {
      t->tlab().resize();
    }
  }

  stats.publish();

#ifdef ASSERT
  ShenandoahCheckCleanGCLABClosure cl;
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    cl.do_thread(t);
  }
  workers()->threads_do(&cl);
#endif
}

void ShenandoahHeap::gclabs_retire(bool resize) {
  assert(UseTLAB, "Only call with UseTLAB");
  assert(!resize || ResizeTLAB, "Only call for resize when ResizeTLAB is enabled");

  ShenandoahRetireGCLABClosure cl(resize);
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    cl.do_thread(t);
  }

  workers()->threads_do(&cl);

  if (safepoint_workers() != nullptr) {
    safepoint_workers()->threads_do(&cl);
  }
}

// Returns size in bytes
size_t ShenandoahHeap::unsafe_max_tlab_alloc() const {
  // Return the max allowed size, and let the allocation path
  // figure out the safe size for current allocation.
  return ShenandoahHeapRegion::max_tlab_size_bytes();
}

size_t ShenandoahHeap::max_tlab_size() const {
  // Returns size in words
  return ShenandoahHeapRegion::max_tlab_size_words();
}

void ShenandoahHeap::collect_as_vm_thread(GCCause::Cause cause) {
  // These requests are ignored because we can't easily have Shenandoah jump into
  // a synchronous (degenerated or full) cycle while it is in the middle of a concurrent
  // cycle. We _could_ cancel the concurrent cycle and then try to run a cycle directly
  // on the VM thread, but this would confuse the control thread mightily and doesn't
  // seem worth the trouble. Instead, we will have the caller thread run (and wait for) a
  // concurrent cycle in the prologue of the heap inspect/dump operation (see VM_HeapDumper::doit_prologue).
  // This is how other concurrent collectors in the JVM handle this scenario as well.
  assert(Thread::current()->is_VM_thread(), "Should be the VM thread");
  guarantee(cause == GCCause::_heap_dump || cause == GCCause::_heap_inspection, "Invalid cause");
}

void ShenandoahHeap::collect(GCCause::Cause cause) {
  control_thread()->request_gc(cause);
}

void ShenandoahHeap::do_full_collection(bool clear_all_soft_refs) {
  // This method is only called by `CollectedHeap::collect_as_vm_thread`, which we have
  // overridden to do nothing. See the comment there for an explanation of how heap inspections
  // work for Shenandoah.
  ShouldNotReachHere();
}

HeapWord* ShenandoahHeap::block_start(const void* addr) const {
  ShenandoahHeapRegion* r = heap_region_containing(addr);
  if (r != nullptr) {
    return r->block_start(addr);
  }
  return nullptr;
}

bool ShenandoahHeap::block_is_obj(const HeapWord* addr) const {
  ShenandoahHeapRegion* r = heap_region_containing(addr);
  return r->block_is_obj(addr);
}

bool ShenandoahHeap::print_location(outputStream* st, void* addr) const {
  return BlockLocationPrinter<ShenandoahHeap>::print_location(st, addr);
}

void ShenandoahHeap::prepare_for_verify() {
  if (SafepointSynchronize::is_at_safepoint() && UseTLAB) {
    labs_make_parsable();
  }
}

void ShenandoahHeap::gc_threads_do(ThreadClosure* tcl) const {
  if (_shenandoah_policy->is_at_shutdown()) {
    return;
  }

  if (_control_thread != nullptr) {
    tcl->do_thread(_control_thread);
  }

  if (_uncommit_thread != nullptr) {
    tcl->do_thread(_uncommit_thread);
  }

  workers()->threads_do(tcl);
  if (_safepoint_workers != nullptr) {
    _safepoint_workers->threads_do(tcl);
  }
}

void ShenandoahHeap::print_tracing_info() const {
  // Do nothing. The control thread prints out GC statistics once it's finished updating them.
}

void ShenandoahHeap::print_gc_stats_at_exit() const {
  assert(Thread::current() == control_thread(), "Only the control thread prints these stats");
  assert(control_thread()->should_terminate(), "Only during shutdown");
  LogTarget(Info, gc, stats) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);

    if (ShenandoahEvacTracking) {
      evac_tracker()->print_global_on(&ls);
      ls.cr();
      ls.cr();
    }

    phase_timings()->print_global_on(&ls);

    ls.cr();
    ls.cr();

    shenandoah_policy()->print_gc_stats(&ls);

    ls.cr();
    ls.cr();
  }
}

// Active generation may only be set by the VM thread at a safepoint.
void ShenandoahHeap::set_active_generation(ShenandoahGeneration* generation) {
  assert(Thread::current()->is_VM_thread(), "Only the VM Thread");
  assert(SafepointSynchronize::is_at_safepoint(), "Only at a safepoint!");
  _active_generation = generation;
}

void ShenandoahHeap::on_cycle_start(GCCause::Cause cause, ShenandoahGeneration* generation,
                                    bool is_degenerated, bool is_out_of_cycle) {
  shenandoah_policy()->record_collection_cause(cause);

  const GCCause::Cause current = gc_cause();
  assert(current == GCCause::_no_gc, "Over-writing cause: %s, with: %s",
         GCCause::to_string(current), GCCause::to_string(cause));

  set_gc_cause(cause);

  if (is_degenerated) {
    generation->heuristics()->record_degenerated_cycle_start(is_out_of_cycle);
  } else {
    generation->heuristics()->record_cycle_start();
  }
}

void ShenandoahHeap::on_cycle_end(ShenandoahGeneration* generation) {
  assert(gc_cause() != GCCause::_no_gc, "cause wasn't set");

  generation->heuristics()->record_cycle_end();
  if (mode()->is_generational() && generation->is_global()) {
    // If we just completed a GLOBAL GC, claim credit for completion of young-gen and old-gen GC as well
    young_generation()->heuristics()->record_cycle_end();
    old_generation()->heuristics()->record_cycle_end();
  }

  set_gc_cause(GCCause::_no_gc);
}

void ShenandoahHeap::verify(VerifyOption vo) {
  if (ShenandoahSafepoint::is_at_shenandoah_safepoint()) {
    if (ShenandoahVerify) {
      verifier()->verify_generic(active_generation(), vo);
    } else {
      // TODO: Consider allocating verification bitmaps on demand,
      // and turn this on unconditionally.
    }
  }
}
size_t ShenandoahHeap::tlab_capacity() const {
  return _free_set->capacity_not_holding_lock();
}

class ObjectIterateScanRootClosure : public BasicOopIterateClosure {
private:
  MarkBitMap* _bitmap;
  ShenandoahScanObjectStack* _oop_stack;

  template <class T>
  void do_oop_work(T* p) {
    T o = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      obj = ShenandoahBarrierSet::barrier_set()->load_reference_barrier(ON_PHANTOM_OOP_REF, obj, (T*)nullptr);
      if (obj == nullptr) {
        // Dead oop, cannot touch it.
        return;
      }

      assert(oopDesc::is_oop(obj), "must be a valid oop");
      if (!_bitmap->is_marked(obj)) {
        _bitmap->mark(obj);
        _oop_stack->push(obj);
      }
    }
  }
public:
  ObjectIterateScanRootClosure(MarkBitMap* bitmap, ShenandoahScanObjectStack* oop_stack) :
    _bitmap(bitmap), _oop_stack(oop_stack) {}
  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
};

/*
 * This is public API, used in preparation of object_iterate().
 * Since we don't do linear scan of heap in object_iterate() (see comment below), we don't
 * need to make the heap parsable. For Shenandoah-internal linear heap scans that we can
 * control, we call SH::tlabs_retire, SH::gclabs_retire.
 */
void ShenandoahHeap::ensure_parsability(bool retire_tlabs) {
  // No-op.
}

/*
 * Iterates objects in the heap. This is public API, used for, e.g., heap dumping.
 *
 * We cannot safely iterate objects by doing a linear scan at random points in time. Linear
 * scanning needs to deal with dead objects, which may have dead Klass* pointers (e.g.
 * calling oopDesc::size() would crash) or dangling reference fields (crashes) etc. Linear
 * scanning therefore depends on having a valid marking bitmap to support it. However, we only
 * have a valid marking bitmap after successful marking. In particular, we *don't* have a valid
 * marking bitmap during marking, after aborted marking or during/after cleanup (when we just
 * wiped the bitmap in preparation for next marking).
 *
 * For all those reasons, we implement object iteration as a single marking traversal, reporting
 * objects as we mark+traverse through the heap, starting from GC roots. JVMTI IterateThroughHeap
 * is allowed to report dead objects, but is not required to do so.
 */
void ShenandoahHeap::object_iterate(ObjectClosure* cl) {
  // Reset bitmap
  if (!prepare_aux_bitmap_for_iteration())
    return;

  ShenandoahScanObjectStack oop_stack;
  ObjectIterateScanRootClosure oops(&_aux_bit_map, &oop_stack);
  // Seed the stack with root scan
  scan_roots_for_iteration(&oop_stack, &oops);

  // Work through the oop stack to traverse heap
  while (! oop_stack.is_empty()) {
    oop obj = oop_stack.pop();
    assert(oopDesc::is_oop(obj), "must be a valid oop");
    cl->do_object(obj);
    obj->oop_iterate(&oops);
  }

  assert(oop_stack.is_empty(), "should be empty");
  // Reclaim bitmap
  reclaim_aux_bitmap_for_iteration();
}

bool ShenandoahHeap::prepare_aux_bitmap_for_iteration() {
  assert(SafepointSynchronize::is_at_safepoint(), "safe iteration is only available during safepoints");
  if (!_aux_bitmap_region_special) {
    bool success = os::commit_memory((char *) _aux_bitmap_region.start(), _aux_bitmap_region.byte_size(), false);
    if (!success) {
      log_warning(gc)("Auxiliary marking bitmap commit failed: " PTR_FORMAT " (%zu bytes)",
                      p2i(_aux_bitmap_region.start()), _aux_bitmap_region.byte_size());
      return false;
    }
  }
  _aux_bit_map.clear();
  return true;
}

void ShenandoahHeap::scan_roots_for_iteration(ShenandoahScanObjectStack* oop_stack, ObjectIterateScanRootClosure* oops) {
  // Process GC roots according to current GC cycle
  // This populates the work stack with initial objects
  // It is important to relinquish the associated locks before diving
  // into heap dumper
  uint n_workers = safepoint_workers() != nullptr ? safepoint_workers()->active_workers() : 1;
  ShenandoahHeapIterationRootScanner rp(n_workers);
  rp.roots_do(oops);
}

void ShenandoahHeap::reclaim_aux_bitmap_for_iteration() {
  if (!_aux_bitmap_region_special) {
    os::uncommit_memory((char*)_aux_bitmap_region.start(), _aux_bitmap_region.byte_size());
  }
}

// Closure for parallelly iterate objects
class ShenandoahObjectIterateParScanClosure : public BasicOopIterateClosure {
private:
  MarkBitMap* _bitmap;
  ShenandoahObjToScanQueue* _queue;

  template <class T>
  void do_oop_work(T* p) {
    T o = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      obj = ShenandoahBarrierSet::barrier_set()->load_reference_barrier(ON_PHANTOM_OOP_REF, obj, (T*)nullptr);
      if (obj == nullptr) {
        // Dead oop, cannot touch it.
        return;
      }
      assert(oopDesc::is_oop(obj), "Must be a valid oop");
      if (_bitmap->par_mark(obj)) {
        _queue->push(ShenandoahMarkTask(obj));
      }
    }
  }
public:
  ShenandoahObjectIterateParScanClosure(MarkBitMap* bitmap, ShenandoahObjToScanQueue* q) :
    _bitmap(bitmap), _queue(q) {}
  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
};

// Object iterator for parallel heap iteraion.
// The root scanning phase happenes in construction as a preparation of
// parallel marking queues.
// Every worker processes it's own marking queue. work-stealing is used
// to balance workload.
class ShenandoahParallelObjectIterator : public ParallelObjectIteratorImpl {
private:
  uint                         _num_workers;
  bool                         _init_ready;
  MarkBitMap*                  _aux_bit_map;
  ShenandoahHeap*              _heap;
  ShenandoahScanObjectStack    _roots_stack; // global roots stack
  ShenandoahObjToScanQueueSet* _task_queues;
public:
  ShenandoahParallelObjectIterator(uint num_workers, MarkBitMap* bitmap) :
        _num_workers(num_workers),
        _init_ready(false),
        _aux_bit_map(bitmap),
        _heap(ShenandoahHeap::heap()) {
    // Initialize bitmap
    _init_ready = _heap->prepare_aux_bitmap_for_iteration();
    if (!_init_ready) {
      return;
    }

    ObjectIterateScanRootClosure oops(_aux_bit_map, &_roots_stack);
    _heap->scan_roots_for_iteration(&_roots_stack, &oops);

    _init_ready = prepare_worker_queues();
  }

  ~ShenandoahParallelObjectIterator() {
    // Reclaim bitmap
    _heap->reclaim_aux_bitmap_for_iteration();
    // Reclaim queue for workers
    if (_task_queues!= nullptr) {
      for (uint i = 0; i < _num_workers; ++i) {
        ShenandoahObjToScanQueue* q = _task_queues->queue(i);
        if (q != nullptr) {
          delete q;
          _task_queues->register_queue(i, nullptr);
        }
      }
      delete _task_queues;
      _task_queues = nullptr;
    }
  }

  virtual void object_iterate(ObjectClosure* cl, uint worker_id) {
    if (_init_ready) {
      object_iterate_parallel(cl, worker_id, _task_queues);
    }
  }

private:
  // Divide global root_stack into worker queues
  bool prepare_worker_queues() {
    _task_queues = new ShenandoahObjToScanQueueSet((int) _num_workers);
    // Initialize queues for every workers
    for (uint i = 0; i < _num_workers; ++i) {
      ShenandoahObjToScanQueue* task_queue = new ShenandoahObjToScanQueue();
      _task_queues->register_queue(i, task_queue);
    }
    // Divide roots among the workers. Assume that object referencing distribution
    // is related with root kind, use round-robin to make every worker have same chance
    // to process every kind of roots
    size_t roots_num = _roots_stack.size();
    if (roots_num == 0) {
      // No work to do
      return false;
    }

    for (uint j = 0; j < roots_num; j++) {
      uint stack_id = j % _num_workers;
      oop obj = _roots_stack.pop();
      _task_queues->queue(stack_id)->push(ShenandoahMarkTask(obj));
    }
    return true;
  }

  void object_iterate_parallel(ObjectClosure* cl,
                               uint worker_id,
                               ShenandoahObjToScanQueueSet* queue_set) {
    assert(SafepointSynchronize::is_at_safepoint(), "safe iteration is only available during safepoints");
    assert(queue_set != nullptr, "task queue must not be null");

    ShenandoahObjToScanQueue* q = queue_set->queue(worker_id);
    assert(q != nullptr, "object iterate queue must not be null");

    ShenandoahMarkTask t;
    ShenandoahObjectIterateParScanClosure oops(_aux_bit_map, q);

    // Work through the queue to traverse heap.
    // Steal when there is no task in queue.
    while (q->pop(t) || queue_set->steal(worker_id, t)) {
      oop obj = t.obj();
      assert(oopDesc::is_oop(obj), "must be a valid oop");
      cl->do_object(obj);
      obj->oop_iterate(&oops);
    }
    assert(q->is_empty(), "should be empty");
  }
};

ParallelObjectIteratorImpl* ShenandoahHeap::parallel_object_iterator(uint workers) {
  return new ShenandoahParallelObjectIterator(workers, &_aux_bit_map);
}

// Keep alive an object that was loaded with AS_NO_KEEPALIVE.
void ShenandoahHeap::keep_alive(oop obj) {
  ShenandoahBarrierSet::barrier_set()->keepalive_barrier(ON_STRONG_OOP_REF, (oop*)nullptr, obj, ShenandoahBarrierSet::FILTER_MARKED);
}

void ShenandoahHeap::heap_region_iterate(ShenandoahHeapRegionClosure* blk) const {
  for (size_t i = 0; i < num_regions(); i++) {
    ShenandoahHeapRegion* current = get_region(i);
    blk->heap_region_do(current);
  }
}

class ShenandoahHeapRegionIteratorTask : public WorkerTask {
private:
  ShenandoahRegionIterator _regions;
  ShenandoahHeapRegionClosure* _closure;

public:
  ShenandoahHeapRegionIteratorTask(ShenandoahHeapRegionClosure* closure)
    : WorkerTask("Shenandoah Heap Region Iterator")
    , _closure(closure) {}

  void work(uint worker_id) override {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahHeapRegion* region = _regions.next();
    while (region != nullptr) {
      _closure->heap_region_do(region);
      region = _regions.next();
    }
  }
};

class ShenandoahParallelHeapRegionTask : public WorkerTask {
private:
  ShenandoahHeap* const _heap;
  ShenandoahHeapRegionClosure* const _blk;
  size_t const _stride;

  shenandoah_padding(0);
  Atomic<size_t> _index;
  shenandoah_padding(1);

public:
  ShenandoahParallelHeapRegionTask(ShenandoahHeapRegionClosure* blk, size_t stride) :
          WorkerTask("Shenandoah Parallel Region Operation"),
          _heap(ShenandoahHeap::heap()), _blk(blk), _stride(stride), _index(0) {}

  void work(uint worker_id) {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    size_t stride = _stride;

    size_t max = _heap->num_regions();
    while (_index.load_relaxed() < max) {
      size_t cur = _index.fetch_then_add(stride, memory_order_relaxed);
      size_t start = cur;
      size_t end = MIN2(cur + stride, max);
      if (start >= max) break;

      for (size_t i = cur; i < end; i++) {
        ShenandoahHeapRegion* current = _heap->get_region(i);
        _blk->heap_region_do(current);
      }
    }
  }
};

void ShenandoahHeap::parallel_heap_region_iterate(ShenandoahHeapRegionClosure* blk) const {
  assert(blk->is_thread_safe(), "Only thread-safe closures here");
  const uint active_workers = workers()->active_workers();
  const size_t n_regions = num_regions();
  size_t stride = blk->parallel_region_stride();
  if (stride == 0 && active_workers > 1) {
    // Automatically derive the stride to balance the work between threads
    // evenly. Do not try to split work if below the reasonable threshold.
    constexpr size_t threshold = 4096;
    stride = n_regions <= threshold ?
            threshold :
            (n_regions + active_workers - 1) / active_workers;
  }

  if (n_regions > stride && active_workers > 1) {
    ShenandoahParallelHeapRegionTask task(blk, stride);
    workers()->run_task(&task);
  } else {
    heap_region_iterate(blk);
  }
}

void ShenandoahHeap::heap_region_iterator(ShenandoahHeapRegionClosure* closure) const {
  ShenandoahHeapRegionIteratorTask task(closure);
  workers()->run_task(&task);
}

class ShenandoahRendezvousHandshakeClosure : public HandshakeClosure {
public:
  inline ShenandoahRendezvousHandshakeClosure(const char* name) : HandshakeClosure(name) {}
  inline void do_thread(Thread* thread) {}
};

void ShenandoahHeap::rendezvous_threads(const char* name) {
  ShenandoahRendezvousHandshakeClosure cl(name);
  Handshake::execute(&cl);
}

void ShenandoahHeap::recycle_trash() {
  free_set()->recycle_trash();
}

void ShenandoahHeap::do_class_unloading() {
  _unloader.unload();
  if (mode()->is_generational()) {
    old_generation()->set_parsable(false);
  }
}

void ShenandoahHeap::stw_weak_refs(ShenandoahGeneration* generation, bool full_gc) {
  // Weak refs processing
  ShenandoahPhaseTimings::Phase phase = full_gc ? ShenandoahPhaseTimings::full_gc_weakrefs
                                                : ShenandoahPhaseTimings::degen_gc_weakrefs;
  ShenandoahTimingsTracker t(phase);
  ShenandoahGCWorkerPhase worker_phase(phase);
  generation->ref_processor()->process_references(phase, workers(), false /* concurrent */);
}

void ShenandoahHeap::prepare_update_heap_references() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "must be at safepoint");

  // Evacuation is over, no GCLABs are needed anymore. GCLABs are under URWM, so we need to
  // make them parsable for update code to work correctly. Plus, we can compute new sizes
  // for future GCLABs here.
  if (UseTLAB) {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::degen_gc_init_update_refs_manage_gclabs);
    gclabs_retire(ResizeTLAB);
  }

  _update_refs_iterator.reset();
}

void ShenandoahHeap::propagate_gc_state_to_all_threads() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at Shenandoah safepoint");
  if (_gc_state_changed) {
    // If we are only marking old, we do not need to process young pointers
#ifdef ASSERT
    bool actual = ShenandoahBarrierSet::satb_mark_queue_set().get_filter_out_young();
    bool expected = is_concurrent_old_mark_in_progress() && !is_concurrent_young_mark_in_progress();
    assert(actual == expected,
           "SATB young filter consistency, actual=%s, expected=%s",
           BOOL_TO_STR(actual), BOOL_TO_STR(expected));
#endif
    ShenandoahGCStatePropagatorHandshakeClosure propagator(_gc_state.raw_value());
    Threads::threads_do(&propagator);
    _gc_state_changed = false;
  }
}

void ShenandoahHeap::set_gc_state_at_safepoint(uint mask, bool value) {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at Shenandoah safepoint");
  _gc_state.set_cond(mask, value);
  _gc_state_changed = true;
}

void ShenandoahHeap::set_gc_state_concurrent(uint mask, bool value) {
  // Holding the thread lock here assures that any thread created after we change the gc
  // state will have the correct state. It also prevents attaching threads from seeing
  // an inconsistent state. See ShenandoahBarrierSet::on_thread_attach for reference. Established
  // threads will use their thread local copy of the gc state (changed by a handshake, or on a
  // safepoint).
  assert(Threads_lock->is_locked(), "Must hold thread lock for concurrent gc state change");
  _gc_state.set_cond(mask, value);
}

void ShenandoahHeap::set_concurrent_young_mark_in_progress(bool in_progress) {
  uint mask;
  assert(!has_forwarded_objects(), "Young marking is not concurrent with evacuation");
  if (!in_progress && is_concurrent_old_mark_in_progress()) {
    assert(mode()->is_generational(), "Only generational GC has old marking");
    assert(_gc_state.is_set(MARKING), "concurrent_old_marking_in_progress implies MARKING");
    // If old-marking is in progress when we turn off YOUNG_MARKING, leave MARKING (and OLD_MARKING) on
    mask = YOUNG_MARKING;
  } else {
    mask = MARKING | YOUNG_MARKING;
  }
  set_gc_state_at_safepoint(mask, in_progress);
  manage_satb_barrier(in_progress);
}

void ShenandoahHeap::set_concurrent_old_mark_in_progress(bool in_progress) {
#ifdef ASSERT
  // has_forwarded_objects() iff UPDATE_REFS or EVACUATION
  bool has_forwarded = has_forwarded_objects();
  bool updating_or_evacuating = _gc_state.is_set(UPDATE_REFS | EVACUATION);
  bool evacuating = _gc_state.is_set(EVACUATION);
  assert ((has_forwarded == updating_or_evacuating) || (evacuating && !has_forwarded && collection_set()->is_empty()),
          "Updating or evacuating iff has forwarded objects, or if evacuation phase is promoting in place without forwarding");
#endif
  if (!in_progress && is_concurrent_young_mark_in_progress()) {
    // If young-marking is in progress when we turn off OLD_MARKING, leave MARKING (and YOUNG_MARKING) on
    assert(_gc_state.is_set(MARKING), "concurrent_young_marking_in_progress implies MARKING");
    set_gc_state_at_safepoint(OLD_MARKING, in_progress);
  } else {
    set_gc_state_at_safepoint(MARKING | OLD_MARKING, in_progress);
  }
  manage_satb_barrier(in_progress);
}

bool ShenandoahHeap::is_prepare_for_old_mark_in_progress() const {
  return old_generation()->is_preparing_for_mark();
}

void ShenandoahHeap::manage_satb_barrier(bool active) {
  // If we are only marking old, we do not need to process young pointers
  ShenandoahBarrierSet::satb_mark_queue_set().set_filter_out_young(
    is_concurrent_old_mark_in_progress() && !is_concurrent_young_mark_in_progress()
  );

  if (is_concurrent_mark_in_progress()) {
    // Ignore request to deactivate barrier while concurrent mark is in progress.
    // Do not attempt to re-activate the barrier if it is already active.
    if (active && !ShenandoahBarrierSet::satb_mark_queue_set().is_active()) {
      ShenandoahBarrierSet::satb_mark_queue_set().set_active_all_threads(active, !active);
    }
  } else {
    // No concurrent marking is in progress so honor request to deactivate,
    // but only if the barrier is already active.
    if (!active && ShenandoahBarrierSet::satb_mark_queue_set().is_active()) {
      ShenandoahBarrierSet::satb_mark_queue_set().set_active_all_threads(active, !active);
    }
  }
}

void ShenandoahHeap::set_evacuation_in_progress(bool in_progress) {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Only call this at safepoint");
  set_gc_state_at_safepoint(EVACUATION, in_progress);
}

void ShenandoahHeap::set_concurrent_strong_root_in_progress(bool in_progress) {
  if (in_progress) {
    _concurrent_strong_root_in_progress.set();
  } else {
    _concurrent_strong_root_in_progress.unset();
  }
}

void ShenandoahHeap::set_concurrent_weak_root_in_progress(bool cond) {
  set_gc_state_at_safepoint(WEAK_ROOTS, cond);
}

GCTracer* ShenandoahHeap::tracer() {
  return shenandoah_policy()->tracer();
}

size_t ShenandoahHeap::tlab_used() const {
  return _free_set->used_not_holding_lock();
}

bool ShenandoahHeap::try_cancel_gc(GCCause::Cause cause) {
  while (true) {
    const GCCause::Cause prev = _cancelled_gc.get();
    if (prev != GCCause::_no_gc && prev != GCCause::_shenandoah_concurrent_gc && cause != GCCause::_shenandoah_stop_vm) {
      // Only when the gc has not been cancelled, or it has been cancelled to interrupt an old marking cycle
      // do we allow the new cancellation request to happen. We make an exception for stopping the VM.
      return false;
    }

    if (_cancelled_gc.cmpxchg(cause, prev) == prev) {
      return true;
    }
  }
}

void ShenandoahHeap::cancel_concurrent_mark() {
  if (mode()->is_generational()) {
    young_generation()->cancel_marking();
    old_generation()->cancel_marking();
  }

  global_generation()->cancel_marking();

  ShenandoahBarrierSet::satb_mark_queue_set().abandon_partial_marking();
}

bool ShenandoahHeap::cancel_gc(GCCause::Cause cause) {
  if (try_cancel_gc(cause)) {
    FormatBuffer<> msg("Cancelling GC: %s", GCCause::to_string(cause));
    log_info(gc,thread)("%s", msg.buffer());
    Events::log(Thread::current(), "%s", msg.buffer());
    _cancel_requested_time = os::elapsedTime();
    return true;
  }
  return false;
}

uint ShenandoahHeap::max_workers() {
  return _max_workers;
}

void ShenandoahHeap::stop() {
  // The shutdown sequence should be able to terminate when GC is running.

  // Step 0. Notify policy to disable event recording and prevent visiting gc threads during shutdown
  _shenandoah_policy->record_shutdown();

  // Step 1. Stop reporting on gc thread cpu utilization
  mmu_tracker()->stop();

  // Step 2. Stop decaying allocation rate.
  _alloc_rate_decay.disenroll();

  // Step 3. Wait until GC worker exits normally (this will cancel any ongoing GC).
  control_thread()->stop();

  // Step 4. Shutdown uncommit thread.
  if (_uncommit_thread != nullptr) {
    _uncommit_thread->stop();
  }
}

void ShenandoahHeap::stw_unload_classes(bool full_gc) {
  if (!unload_classes()) return;
  ClassUnloadingContext ctx(_workers->active_workers(),
                            true /* unregister_nmethods_during_purge */,
                            false /* lock_nmethod_free_separately */);

  // Unload classes and purge SystemDictionary.
  {
    ShenandoahPhaseTimings::Phase phase = full_gc ?
                                          ShenandoahPhaseTimings::full_gc_purge_class_unload :
                                          ShenandoahPhaseTimings::degen_gc_purge_class_unload;
    ShenandoahIsAliveSelector is_alive;
    {
      CodeCache::UnlinkingScope scope(is_alive.is_alive_closure());
      ShenandoahGCPhase gc_phase(phase);
      ShenandoahGCWorkerPhase worker_phase(phase);
      bool unloading_occurred = SystemDictionary::do_unloading(gc_timer());

      ShenandoahClassUnloadingTask unlink_task(phase, unloading_occurred);
      _workers->run_task(&unlink_task);
    }
    // Release unloaded nmethods's memory.
    ClassUnloadingContext::context()->purge_and_free_nmethods();
  }

  {
    ShenandoahGCPhase phase(full_gc ?
                            ShenandoahPhaseTimings::full_gc_purge_cldg :
                            ShenandoahPhaseTimings::degen_gc_purge_cldg);
    ClassLoaderDataGraph::purge(true /* at_safepoint */);
  }
  // Resize and verify metaspace
  MetaspaceGC::compute_new_size();

  if (mode()->is_generational()) {
    old_generation()->set_parsable(false);
  }

  DEBUG_ONLY(MetaspaceUtils::verify();)
}

// Weak roots are either pre-evacuated (final mark) or updated (final update refs),
// so they should not have forwarded oops.
// However, we do need to "null" dead oops in the roots, if can not be done
// in concurrent cycles.
void ShenandoahHeap::stw_process_weak_roots(bool full_gc) {
  uint num_workers = _workers->active_workers();
  ShenandoahPhaseTimings::Phase timing_phase = full_gc ?
                                               ShenandoahPhaseTimings::full_gc_purge_weak_par :
                                               ShenandoahPhaseTimings::degen_gc_purge_weak_par;
  ShenandoahGCPhase phase(timing_phase);
  ShenandoahGCWorkerPhase worker_phase(timing_phase);
  // Cleanup weak roots
  if (has_forwarded_objects()) {
    ShenandoahForwardedIsAliveClosure is_alive;
    ShenandoahNonConcUpdateRefsClosure keep_alive;
    ShenandoahParallelWeakRootsCleaningTask<ShenandoahForwardedIsAliveClosure, ShenandoahNonConcUpdateRefsClosure>
      cleaning_task(timing_phase, &is_alive, &keep_alive, num_workers);
    _workers->run_task(&cleaning_task);
  } else {
    ShenandoahIsAliveClosure is_alive;
#ifdef ASSERT
    ShenandoahAssertNotForwardedClosure verify_cl;
    ShenandoahParallelWeakRootsCleaningTask<ShenandoahIsAliveClosure, ShenandoahAssertNotForwardedClosure>
      cleaning_task(timing_phase, &is_alive, &verify_cl, num_workers);
#else
    ShenandoahParallelWeakRootsCleaningTask<ShenandoahIsAliveClosure, DoNothingClosure>
      cleaning_task(timing_phase, &is_alive, &do_nothing_cl, num_workers);
#endif
    _workers->run_task(&cleaning_task);
  }
}

void ShenandoahHeap::parallel_cleaning(ShenandoahGeneration* generation, bool full_gc) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  assert(is_stw_gc_in_progress(), "Only for Degenerated and Full GC");
  ShenandoahGCPhase phase(full_gc ?
                          ShenandoahPhaseTimings::full_gc_purge :
                          ShenandoahPhaseTimings::degen_gc_purge);
  stw_weak_refs(generation, full_gc);
  stw_process_weak_roots(full_gc);
  stw_unload_classes(full_gc);
}

void ShenandoahHeap::set_has_forwarded_objects(bool cond) {
  set_gc_state_at_safepoint(HAS_FORWARDED, cond);
}

void ShenandoahHeap::set_unload_classes(bool uc) {
  _unload_classes.set_cond(uc);
}

bool ShenandoahHeap::unload_classes() const {
  return _unload_classes.is_set();
}

address ShenandoahHeap::in_cset_fast_test_addr() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  assert(heap->collection_set() != nullptr, "Sanity");
  return (address) heap->collection_set()->biased_map_address();
}

void ShenandoahHeap::set_degenerated_gc_in_progress(bool in_progress) {
  _degenerated_gc_in_progress.set_cond(in_progress);
}

void ShenandoahHeap::set_full_gc_in_progress(bool in_progress) {
  _full_gc_in_progress.set_cond(in_progress);
}

void ShenandoahHeap::set_full_gc_move_in_progress(bool in_progress) {
  assert (is_full_gc_in_progress(), "should be");
  _full_gc_move_in_progress.set_cond(in_progress);
}

void ShenandoahHeap::set_update_refs_in_progress(bool in_progress) {
  set_gc_state_at_safepoint(UPDATE_REFS, in_progress);
}

void ShenandoahHeap::register_nmethod(nmethod* nm) {
  ShenandoahCodeRoots::register_nmethod(nm);
}

void ShenandoahHeap::unregister_nmethod(nmethod* nm) {
  ShenandoahCodeRoots::unregister_nmethod(nm);
}

void ShenandoahHeap::pin_object(JavaThread* thr, oop o) {
  assert(thr == JavaThread::current(), "Sanity");
  size_t reg_idx_pin = heap_region_index_containing(o);
  size_t reg_idx_cached = ShenandoahThreadLocalData::pin_cache_region(thr);
  size_t count = ShenandoahThreadLocalData::pin_cache_count(thr);
  if (reg_idx_pin == reg_idx_cached) {
    ShenandoahThreadLocalData::pin_cache_set_count(thr, count + 1);
  } else {
    if (count != 0) {
      get_region(reg_idx_cached)->record_pin(count);
    }
    ShenandoahThreadLocalData::pin_cache_set_region(thr, reg_idx_pin);
    ShenandoahThreadLocalData::pin_cache_set_count(thr, 1);
  }
}

void ShenandoahHeap::unpin_object(JavaThread* thr, oop o) {
  assert(thr == JavaThread::current(), "Sanity");
  size_t reg_idx_pin = heap_region_index_containing(o);
  size_t reg_idx_cached = ShenandoahThreadLocalData::pin_cache_region(thr);
  if (reg_idx_pin == reg_idx_cached) {
    size_t count = ShenandoahThreadLocalData::pin_cache_count(thr);
    ShenandoahThreadLocalData::pin_cache_set_count(thr, count - 1);
  } else {
    get_region(reg_idx_pin)->record_unpin();
  }
}

void ShenandoahHeap::flush_region_pin_cache(JavaThread* thr) {
  size_t count = ShenandoahThreadLocalData::pin_cache_count(thr);
  if (count != 0) {
    size_t reg_idx_cached = ShenandoahThreadLocalData::pin_cache_region(thr);
    get_region(reg_idx_cached)->record_pin(count);
    ShenandoahThreadLocalData::pin_cache_set_count(thr, 0);
  }
}

void ShenandoahHeap::flush_region_pin_cache() {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    flush_region_pin_cache(t);
  }
}

void ShenandoahHeap::sync_pinned_region_status() {
  flush_region_pin_cache();
  ShenandoahHeapLocker locker(lock());

  for (size_t i = 0; i < num_regions(); i++) {
    ShenandoahHeapRegion *r = get_region(i);
    if (r->is_active()) {
      if (r->is_pinned()) {
        if (r->pin_count() == 0) {
          r->make_unpinned();
        }
      } else {
        if (r->pin_count() > 0) {
          r->make_pinned();
        }
      }
    }
  }

  assert_pinned_region_status();
}

#ifdef ASSERT
void ShenandoahHeap::assert_pinned_region_status() const {
  assert_pinned_region_status(global_generation());
}

void ShenandoahHeap::assert_pinned_region_status(ShenandoahGeneration* generation) const {
  for (size_t i = 0; i < num_regions(); i++) {
    if (!generation->contains(region_affiliation(i))) {
      // Skip regions outside this generation
      continue;
    }

    ShenandoahHeapRegion* r = get_region(i);
    assert((r->is_pinned() && r->pin_count() > 0) || (!r->is_pinned() && r->pin_count() == 0),
           "Region %zu pinning status is inconsistent", i);
  }
}
#endif

ConcurrentGCTimer* ShenandoahHeap::gc_timer() const {
  return _gc_timer;
}

void ShenandoahHeap::prepare_concurrent_roots() {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  assert(!is_stw_gc_in_progress(), "Only concurrent GC");
  set_concurrent_strong_root_in_progress(!collection_set()->is_empty());
  set_concurrent_weak_root_in_progress(true);
  if (unload_classes()) {
    _unloader.prepare();
  }
}

void ShenandoahHeap::finish_concurrent_roots() {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  assert(!is_stw_gc_in_progress(), "Only concurrent GC");
  if (unload_classes()) {
    _unloader.finish();
  }
}

#ifdef ASSERT
void ShenandoahHeap::assert_gc_workers(uint nworkers) {
  assert(nworkers > 0 && nworkers <= max_workers(), "Sanity");

  if (ShenandoahSafepoint::is_at_shenandoah_safepoint()) {
    // Use ParallelGCThreads inside safepoints
    assert(nworkers == ParallelGCThreads, "Use ParallelGCThreads (%u) within safepoint, not %u",
           ParallelGCThreads, nworkers);
  } else {
    // Use ConcGCThreads outside safepoints
    assert(nworkers == ConcGCThreads, "Use ConcGCThreads (%u) outside safepoints, %u",
           ConcGCThreads, nworkers);
  }
}
#endif

ShenandoahVerifier* ShenandoahHeap::verifier() {
  guarantee(ShenandoahVerify, "Should be enabled");
  assert (_verifier != nullptr, "sanity");
  return _verifier;
}

template<bool CONCURRENT>
class ShenandoahUpdateHeapRefsTask : public WorkerTask {
private:
  ShenandoahHeap* _heap;
  ShenandoahRegionIterator* _regions;
  size_t _num_workers;

public:
  explicit ShenandoahUpdateHeapRefsTask(ShenandoahRegionIterator* regions, size_t num_workers) :
    WorkerTask("Shenandoah Update References"),
    _heap(ShenandoahHeap::heap()),
    _regions(regions),
    _num_workers(num_workers) {
  }

  void work(uint worker_id) {
    if (CONCURRENT) {
      ShenandoahConcurrentWorkerSession worker_session(worker_id);
      SuspendibleThreadSetJoiner stsj;
      do_work<ShenandoahConcUpdateRefsClosure>(worker_id);
    } else {
      ShenandoahWorkerTimingsTracker timer(ShenandoahPhaseTimings::degen_gc_update_refs, ShenandoahPhaseTimings::Work, worker_id, true);
      ShenandoahParallelWorkerSession worker_session(worker_id);
      do_work<ShenandoahNonConcUpdateRefsClosure>(worker_id);
    }
  }

private:
  template<class T>
  void do_work(uint worker_id) {
    if (CONCURRENT && (worker_id == 0)) {
      // We ask the first worker to replenish the Mutator free set by moving regions previously reserved to hold the
      // results of evacuation.  These reserves are no longer necessary because evacuation has completed.
      size_t cset_regions = _heap->collection_set()->count();

      // Now that evacuation is done, we can reassign any regions that had been reserved to hold the results of evacuation
      // to the mutator free set.  At the end of GC, we will have cset_regions newly evacuated fully empty regions from
      // which we will be able to replenish the Collector free set and the OldCollector free set in preparation for the
      // next GC cycle. recycle_collection_set() for fwt regions is called separately.
      _heap->free_set()->move_regions_from_collector_to_mutator(cset_regions);
    }
    // If !CONCURRENT, there's no value in expanding Mutator free set
    _heap->collection_set()->optimize_reused_forwarding(worker_id, _num_workers);
    T cl;
    ShenandoahHeapRegion* r = _regions->next();
    while (r != nullptr) {
      HeapWord* update_watermark = r->get_update_watermark();
      assert (update_watermark >= r->bottom(), "sanity");
      if (r->is_active() && !r->is_cset()) {
        //if (!CONCURRENT) {
        //  log_info(gc)("Updating refs in region: %lu, is_active: %s, is_cset: %s", r->index(), BOOL_TO_STR(r->is_active()), BOOL_TO_STR(r->is_cset()));
        //}
        _heap->marked_object_oop_iterate(r, &cl, update_watermark);
      }
      if (_heap->check_cancelled_gc_and_yield(CONCURRENT)) {
        return;
      }
      r = _regions->next();
    }
  }
};

void ShenandoahHeap::update_heap_references(ShenandoahGeneration* generation, bool concurrent,
                                            size_t num_workers) {
  assert(generation->is_global(), "Should only get global generation here");
  assert(!is_full_gc_in_progress(), "Only for concurrent and degenerated GC");
  if (concurrent) {
    ShenandoahUpdateHeapRefsTask<true> task(&_update_refs_iterator, num_workers);
    workers()->run_task(&task);
  } else {
    ShenandoahUpdateHeapRefsTask<false> task(&_update_refs_iterator, num_workers);
    workers()->run_task(&task);
  }
}

void ShenandoahHeap::update_heap_region_states(bool concurrent) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  assert(!is_full_gc_in_progress(), "Only for concurrent and degenerated GC");

  {
    ShenandoahGCPhase phase(concurrent ?
                            ShenandoahPhaseTimings::final_update_refs_update_region_states :
                            ShenandoahPhaseTimings::degen_gc_final_update_refs_update_region_states);

    final_update_refs_update_region_states();

    assert_pinned_region_status();
  }

  {
    ShenandoahGCPhase phase(concurrent ?
                            ShenandoahPhaseTimings::final_update_refs_trash_cset :
                            ShenandoahPhaseTimings::degen_gc_final_update_refs_trash_cset);
    trash_cset_regions();
  }
}

void ShenandoahHeap::final_update_refs_update_region_states() {
  ShenandoahSynchronizePinnedRegionStates cl;
  parallel_heap_region_iterate(&cl);
}

void ShenandoahHeap::rebuild_free_set_within_phase() {
  ShenandoahHeapLocker locker(lock());
  size_t young_trashed_regions, old_trashed_regions, first_old_region, last_old_region, old_region_count;
  _free_set->prepare_to_rebuild(young_trashed_regions, old_trashed_regions, first_old_region, last_old_region, old_region_count);
  // If there are no old regions, first_old_region will be greater than last_old_region
  assert((first_old_region > last_old_region) ||
         ((last_old_region + 1 - first_old_region >= old_region_count) &&
          get_region(first_old_region)->is_old() && get_region(last_old_region)->is_old()),
         "sanity: old_region_count: %zu, first_old_region: %zu, last_old_region: %zu",
         old_region_count, first_old_region, last_old_region);

  if (mode()->is_generational()) {
#ifdef ASSERT
    if (ShenandoahVerify) {
      verifier()->verify_before_rebuilding_free_set();
    }
#endif

    // The computation of bytes_of_allocation_runway_before_gc_trigger is quite conservative so consider all of this
    // available for transfer to old. Note that transfer of humongous regions does not impact available.
    ShenandoahGenerationalHeap* gen_heap = ShenandoahGenerationalHeap::heap();
    size_t allocation_runway =
      gen_heap->young_generation()->heuristics()->bytes_of_allocation_runway_before_gc_trigger(young_trashed_regions);
    size_t max_transfer = MIN2(allocation_runway,
                               (gen_heap->young_generation()->free_unaffiliated_regions() + young_trashed_regions) *
                               ShenandoahHeapRegion::region_size_bytes());
    gen_heap->compute_old_generation_balance(max_transfer, old_trashed_regions, young_trashed_regions);
  }
  // Rebuild free set based on adjusted generation sizes.
  _free_set->finish_rebuild(young_trashed_regions, old_trashed_regions, old_region_count);

  if (mode()->is_generational()) {
    ShenandoahGenerationalHeap* gen_heap = ShenandoahGenerationalHeap::heap();
    ShenandoahOldGeneration* old_gen = gen_heap->old_generation();
    old_gen->heuristics()->evaluate_triggers(first_old_region, last_old_region, old_region_count, num_regions());
  }
}

void ShenandoahHeap::rebuild_free_set(bool concurrent) {
  ShenandoahGCPhase phase(concurrent ?
                          ShenandoahPhaseTimings::final_update_refs_rebuild_freeset :
                          ShenandoahPhaseTimings::degen_gc_final_update_refs_rebuild_freeset);
  rebuild_free_set_within_phase();
}

bool ShenandoahHeap::is_bitmap_slice_committed(ShenandoahHeapRegion* r, bool skip_self) {
  size_t slice = r->index() / _bitmap_regions_per_slice;

  size_t regions_from = _bitmap_regions_per_slice * slice;
  size_t regions_to   = MIN2(num_regions(), _bitmap_regions_per_slice * (slice + 1));
  for (size_t g = regions_from; g < regions_to; g++) {
    assert (g / _bitmap_regions_per_slice == slice, "same slice");
    if (skip_self && g == r->index()) continue;
    if (get_region(g)->is_committed()) {
      return true;
    }
  }
  return false;
}

void ShenandoahHeap::commit_bitmap_slice(ShenandoahHeapRegion* r) {
  shenandoah_assert_heaplocked();
  assert(!is_bitmap_region_special(), "Not for special memory");

  if (is_bitmap_slice_committed(r, true)) {
    // Some other region from the group is already committed, meaning the bitmap
    // slice is already committed, we exit right away.
    return;
  }

  // Commit the bitmap slice:
  size_t slice = r->index() / _bitmap_regions_per_slice;
  size_t off = _bitmap_bytes_per_slice * slice;
  size_t len = _bitmap_bytes_per_slice;
  char* start = (char*) _bitmap_region.start() + off;

  os::commit_memory_or_exit(start, len, false, "Unable to commit bitmap slice");

  if (AlwaysPreTouch) {
    os::pretouch_memory(start, start + len, _pretouch_bitmap_page_size);
  }
}

void ShenandoahHeap::uncommit_bitmap_slice(ShenandoahHeapRegion *r) {
  shenandoah_assert_heaplocked();
  assert(!is_bitmap_region_special(), "Not for special memory");

  if (is_bitmap_slice_committed(r, true)) {
    // Some other region from the group is still committed, meaning the bitmap
    // slice should stay committed, exit right away.
    return;
  }

  // Uncommit the bitmap slice:
  size_t slice = r->index() / _bitmap_regions_per_slice;
  size_t off = _bitmap_bytes_per_slice * slice;
  size_t len = _bitmap_bytes_per_slice;

  char* addr = (char*) _bitmap_region.start() + off;
  os::uncommit_memory(addr, len);
}

void ShenandoahHeap::forbid_uncommit() {
  if (_uncommit_thread != nullptr) {
    _uncommit_thread->forbid_uncommit();
  }
}

void ShenandoahHeap::allow_uncommit() {
  if (_uncommit_thread != nullptr) {
    _uncommit_thread->allow_uncommit();
  }
}

#ifdef ASSERT
bool ShenandoahHeap::is_uncommit_in_progress() {
  if (_uncommit_thread != nullptr) {
    return _uncommit_thread->is_uncommit_in_progress();
  }
  return false;
}
#endif

void ShenandoahHeap::safepoint_synchronize_begin() {
  StackWatermarkSet::safepoint_synchronize_begin();
  SuspendibleThreadSet::synchronize();
}

void ShenandoahHeap::safepoint_synchronize_end() {
  SuspendibleThreadSet::desynchronize();
}

void ShenandoahHeap::try_inject_alloc_failure() {
  if (ShenandoahAllocFailureALot && !cancelled_gc() && ((os::random() % 1000) > 950)) {
    _inject_alloc_failure.set();
    os::naked_short_sleep(1);
    if (cancelled_gc()) {
      log_info(gc)("Allocation failure was successfully injected");
    }
  }
}

bool ShenandoahHeap::should_inject_alloc_failure() {
  return _inject_alloc_failure.is_set() && _inject_alloc_failure.try_unset();
}

void ShenandoahHeap::try_inject_pin() {
  assert(!ShenandoahSafepoint::is_at_shenandoah_safepoint(), "try_inject_pin() must be called outside a safepoint.");
  assert(active_generation() != nullptr, "Active generation must be set before we inject pins.");
  assert(is_concurrent_mark_in_progress() || active_generation()->is_mark_complete(),
         "try_inject_pin() requires marking is in progress or has completed.");
  if (ShenandoahPinRegionRate && !cancelled_gc() && ((uintx)(os::random() % 1000) < ShenandoahPinRegionRate) &&
      _injected_pin_count < MAX_INJECTED_PINS) {
    const size_t idx = os::random() % num_regions();
    ShenandoahHeapRegion* r = get_region(idx);
    if ((r->is_regular() || r->is_humongous_start()) && r->has_live()) {
      r->record_pin();
      _injected_pin_indices[_injected_pin_count] = idx;
      _injected_pin_count++;
    }
  }
}

void ShenandoahHeap::release_injected_pins() {
  if (_injected_pin_count == 0) {
    return;
  }

  assert(_injected_pin_count <= MAX_INJECTED_PINS,
         "Injected pin count: %u exceeds max: %u.", _injected_pin_count, MAX_INJECTED_PINS);
  for (uint i = 0; i < _injected_pin_count; i++) {
    const size_t idx = _injected_pin_indices[i];
    ShenandoahHeapRegion* r = get_region(idx);
    assert(r->pin_count() > 0, "Region %zu in tracker must contain a pin.", idx);
    r->record_unpin();
  }
  _injected_pin_count = 0;
}

void ShenandoahHeap::initialize_serviceability() {
  _memory_pool = new ShenandoahMemoryPool(this);
  _cycle_memory_manager.add_pool(_memory_pool);
  _stw_memory_manager.add_pool(_memory_pool);
}

GrowableArray<GCMemoryManager*> ShenandoahHeap::memory_managers() {
  GrowableArray<GCMemoryManager*> memory_managers(2);
  memory_managers.append(&_cycle_memory_manager);
  memory_managers.append(&_stw_memory_manager);
  return memory_managers;
}

GrowableArray<MemoryPool*> ShenandoahHeap::memory_pools() {
  GrowableArray<MemoryPool*> memory_pools(1);
  memory_pools.append(_memory_pool);
  return memory_pools;
}

MemoryUsage ShenandoahHeap::memory_usage() {
  return shenandoah_memory_usage(_initial_size, used(), committed(), max_capacity());
}

ShenandoahRegionIterator::ShenandoahRegionIterator() :
  _heap(ShenandoahHeap::heap()),
  _index(0) {}

ShenandoahRegionIterator::ShenandoahRegionIterator(ShenandoahHeap* heap) :
  _heap(heap),
  _index(0) {}

void ShenandoahRegionIterator::reset() {
  _index.store_relaxed(0);
}

bool ShenandoahRegionIterator::has_next() const {
  return _index.load_relaxed() < _heap->num_regions();
}

ShenandoahLiveData* ShenandoahHeap::get_liveness_cache(uint worker_id) {
#ifdef ASSERT
  assert(_liveness_cache != nullptr, "sanity");
  assert(worker_id < _max_workers, "sanity");
  for (uint i = 0; i < num_regions(); i++) {
    assert(_liveness_cache[worker_id][i] == 0, "liveness cache should be empty");
  }
#endif
  return _liveness_cache[worker_id];
}

void ShenandoahHeap::flush_liveness_cache(uint worker_id) {
  assert(worker_id < _max_workers, "sanity");
  assert(_liveness_cache != nullptr, "sanity");
  ShenandoahLiveData* ld = _liveness_cache[worker_id];
  for (uint i = 0; i < num_regions(); i++) {
    ShenandoahLiveData live = ld[i];
    if (live > 0) {
      ShenandoahHeapRegion* r = get_region(i);
      r->increase_live_data_gc_words(live);
      ld[i] = 0;
    }
  }
}

bool ShenandoahHeap::requires_barriers(stackChunkOop obj) const {
  // Can not guarantee obj is deeply good.
  if (has_forwarded_objects()) {
    return true;
  }

  // Objects allocated after marking start are implicitly alive.
  if (is_concurrent_mark_in_progress() &&
      !marking_context()->allocated_after_mark_start(obj)) {
    return true;
  }

  // Nmethods referenced by stack chunk may need the barrier fixups.
  if (ShenandoahStackChunkGCData::is_different_epoch(obj)) {
    return true;
  }

  return false;
}

HeapWord* ShenandoahHeap::allocate_loaded_archive_space(size_t size) {
#if INCLUDE_CDS_JAVA_HEAP
  // CDS wants a raw continuous memory range to load a bunch of objects itself.
  // This is an unusual request, since all requested regions should be regular, not humongous.
  //
  // CDS would guarantee no objects straddle multiple regions, as long as regions are as large
  // as MIN_GC_REGION_ALIGNMENT.
  guarantee(ShenandoahHeapRegion::region_size_bytes() >= AOTMappedHeapWriter::MIN_GC_REGION_ALIGNMENT, "Must be");

  ShenandoahAllocRequest req = ShenandoahAllocRequest::for_cds(size);
  return allocate_memory(req);
#else
  assert(false, "Archive heap loader should not be available, should not be here");
  return nullptr;
#endif // INCLUDE_CDS_JAVA_HEAP
}

void ShenandoahHeap::complete_loaded_archive_space(MemRegion archive_space) {
  // Nothing to do here, except checking that heap looks fine.
#ifdef ASSERT
  HeapWord* start = archive_space.start();
  HeapWord* end = archive_space.end();

  // No unclaimed space between the objects.
  // Objects are properly allocated in correct regions.
  HeapWord* cur = start;
  while (cur < end) {
    oop oop = cast_to_oop(cur);
    shenandoah_assert_in_correct_region(nullptr, oop);
    cur += oop->size();
  }

  // No unclaimed tail at the end of archive space.
  assert(cur == end,
         "Archive space should be fully used: " PTR_FORMAT " " PTR_FORMAT,
         p2i(cur), p2i(end));

  // All regions in contiguous space have good state.
  size_t begin_reg_idx = heap_region_index_containing(start);
  size_t end_reg_idx   = heap_region_index_containing(end);

  for (size_t idx = begin_reg_idx; idx <= end_reg_idx; idx++) {
    ShenandoahHeapRegion* r = get_region(idx);
    assert(r->is_regular(), "Must be regular");
    assert(r->is_young(), "Must be young");
    assert(idx == end_reg_idx || r->top() == r->end(),
           "All regions except the last one should be full: " PTR_FORMAT " " PTR_FORMAT,
           p2i(r->top()), p2i(r->end()));
    assert(idx != begin_reg_idx || r->bottom() == start,
           "Archive space start should be at the bottom of first region: " PTR_FORMAT " " PTR_FORMAT,
           p2i(r->bottom()), p2i(start));
    assert(idx != end_reg_idx || r->top() == end,
           "Archive space end should be at the top of last region: " PTR_FORMAT " " PTR_FORMAT,
           p2i(r->top()), p2i(end));
  }
#endif
}

ShenandoahGeneration* ShenandoahHeap::generation_for(ShenandoahAffiliation affiliation) const {
  if (!mode()->is_generational()) {
    return global_generation();
  } else if (affiliation == YOUNG_GENERATION) {
    return young_generation();
  } else if (affiliation == OLD_GENERATION) {
    return old_generation();
  }

  ShouldNotReachHere();
  return nullptr;
}

void ShenandoahHeap::log_heap_status(const char* msg) const {
  if (mode()->is_generational()) {
    young_generation()->log_status(msg);
    old_generation()->log_status(msg);
  } else {
    global_generation()->log_status(msg);
  }
}

ShenandoahHeapLocker::ShenandoahHeapLocker(ShenandoahHeapLock* lock, bool allow_block_for_safepoint) : _lock(lock) {
#ifdef ASSERT
  ShenandoahFreeSet* free_set = ShenandoahHeap::heap()->free_set();
  // free_set is nullptr only at pre-initialized state
  assert(free_set == nullptr || !free_set->rebuild_lock()->owned_by_self(), "Dead lock, can't acquire heap lock while holding free-set rebuild lock");
  assert(_lock != nullptr, "Must not");
#endif
  _lock->lock(allow_block_for_safepoint);
}

ShenandoahConcurrentEvacuateEarlyRecycleRegionObjectClosure::ShenandoahConcurrentEvacuateEarlyRecycleRegionObjectClosure(
  ShenandoahHeap* heap, ShenandoahHeapRegion* r, ShenandoahHeapRegion* alloc_region) :
    _heap(heap), _region(r), _thread(Thread::current()), _context(heap->marking_context()),
    _tams(_context->top_at_mark_start(_region)), _num_forwardings(0), _heap_evacuated_objects(0) {
  initialize_early_recycle_region(alloc_region);
  if (_min_fill_size == 0) {
    _min_fill_size = ShenandoahHeap::min_fill_size();
  }
}

ShenandoahConcurrentEvacuateEarlyRecycleRegionObjectClosure::~ShenandoahConcurrentEvacuateEarlyRecycleRegionObjectClosure() {
  _context->capture_top_at_mark_start(_region);
  if (_alloc_region_bottom != nullptr) {
    retire_early_recycled_region();
  }
}

void ShenandoahConcurrentEvacuateEarlyRecycleRegionObjectClosure::do_object(oop p) {
  shenandoah_assert_marked(nullptr, p);
  _num_forwardings++;
  if (!p->is_forwarded()) {
    size_t size = ShenandoahForwarding::size(p);
    assert(_conflict_offset > _bump_alloc_offset, "invariant");
    assert(_region->affiliation() == ShenandoahAffiliation::YOUNG_GENERATION, "This code for single-gen Shenandoah only");
    const ShenandoahAffiliation target_gen = ShenandoahAffiliation::YOUNG_GENERATION;
    oop fwd = nullptr;
    bool free_set_evac_attempted = false;
    HeapWord* copy = nullptr;
    if (_alloc_region_bottom != nullptr) {
      // Speculate that early recycle allocation is successful, then confirm below
      copy = _alloc_region_bottom + _bump_alloc_offset;
      // Normally, allocation is satisfied immediately and there's no need to iterate.
      while (_bump_alloc_offset + size > _end_of_allocatable_offset) {
        size_t available_words = _end_of_allocatable_offset - _bump_alloc_offset;
        if (available_words > PLAB::min_size()) {
          // Let's not retire this region. Instead, send only this allocation request to the free-set evacuator.
          fwd = _heap->evacuate_object(p, _thread);
          //log_info(gc)("evacuated object: " PTR_FORMAT ", to: " PTR_FORMAT, p2i(p), p2i(fwd));
          free_set_evac_attempted = true;
          break;
        } else {
          // Retire the region and try to allocate within a replacement region.
          retire_early_recycled_region();
          if (try_establish_new_early_recycle_region()) {
            // We've replaced the early recycle region.  At top of loop, we will confirm that new region has sufficient
            // memory to satisfy our allocation request.
            copy = _alloc_region_bottom + _bump_alloc_offset;
          } else {
            // We were unable to claim an early recycled region. Fall back to free-set evacuator for now.
            assert(_alloc_region_bottom == nullptr, "Early recycle was just retired");
            fwd = _heap->evacuate_object(p, _thread);
            //log_info(gc)("evacuated object: " PTR_FORMAT ", to: " PTR_FORMAT, p2i(p), p2i(fwd));
            free_set_evac_attempted = true;
            break;
          }
        }
      }
      // The loop terminated because the early recycle region has sufficient memory to hold the evacuated object, or
      // because we successfully allocated from free-set partition. If early recycled memory is available, it is identified
      // by copy.
      if (free_set_evac_attempted) {
        // We chose to evacuate with free-set evacuator, so ignore our efforts to allocate a copy in the early recycled region.
        copy = nullptr;
      }
    } else {
      // We're here because we previously depleted our early recycle region and were unable to reclaim a new one. Under
      // normal operating conditions, we expect it to be rare that we're on this control path, because normally we are
      // able to recycle regions much faster than the need to allocate from their memory.
      fwd = _heap->evacuate_object(p, _thread);
      free_set_evac_attempted = true;
      if (_heap_evacuated_objects++ > MaxHeapEvacuatedBeforeRetryEarlyRecycle) {
        try_establish_new_early_recycle_region();
        // if successful, next do_object() call  will find the region is present and use it.
      }
    }

    if (fwd == nullptr && free_set_evac_attempted) {
      // Object has not been evacuated because neither the freeset nor the early-recycled memory was adequate for the need.
    } else if (fwd == nullptr) {
      // The forwarding was not done by the free-set evacuator because we intend to supply the memory from early allocatable.
#ifdef ASSERT
      // Code for randomizing OOMDuringEvacALot is copied from try_evacuate_object().  Instead of returning result,
      // we fall through to code below to execute the code that normally follows return from try_evacuate_object().
      if (ShenandoahOOMDuringEvacALot && (os::random() & 1) == 0) { // Simulate OOM every ~2nd slow-path call.
        // In the calls to _heap->evacuate_objects(), we handle the case that the free-set allocator experiences OOM during
        // evac a lot.  Here, we handle the case that early-recycle evacuator experiences OOM during evac a lot.
        _heap->control_thread()->handle_alloc_failure_evac(size);

        // Install the self-forwarded bit on p so other evacuators/LRBs see the object as "already handled and do not try to
        // evacuate". The CAS may fail if another thread concurrently installed a real forwardee (they succeeded where we
        // failed) or self-forwarded first.
        markWord old_mark = p->mark();
        if (old_mark.is_forwarded()) {
          // Someone else forwarded this object.  Proceed with business as usual.
        } else {
          oop winner = ShenandoahForwarding::try_forward_to_self(p, old_mark);
          if (winner == nullptr) {
            // We own the self-forwarding. Flag the region so the degen/full GC entry drain knows to scan it for self_fwd 
            // bits to clear.
            _region->set_has_self_forwards();
            // I am my own copy.
#ifdef ASSERT
            fwd = p;
#endif
          } else {
            // Some other thread copied the object which I was unable to copy.
#ifdef ASSERT
            fwd = winner;
#endif
          }
        }
        // We intentionally tried to OOMDurinvEvacAlot. It may be that this causes self-forwarding and OOM-during evac
        // recovery. But it may also be that some other successfully evacuated object p, so there is not recovery required.
        // In either case, the heap should be coherent at this point.
      } else {
#endif
        assert(copy != nullptr, "Precondition for this code path");
        if (ShenandoahEvacTracking) {
          _heap->evac_tracker()->begin_evacuation(_thread, size * HeapWordSize, _region->affiliation(), target_gen);
        }

        // Copy the object:
        Copy::aligned_disjoint_words(cast_from_oop<HeapWord*>(p), copy, size);
        oop copy_val = cast_to_oop(copy);

        // Relativize stack chunks before publishing the copy. After the forwarding CAS,
        // mutators can see the copy and thaw it via the fast path if flags == 0. We must
        // relativize derived pointers and set gc_mode before that happens. Skip if the
        // copy's mark word is already a forwarding pointer (another thread won the race
        // and overwrote the original's header before we copied it).
        if (!ShenandoahForwarding::is_forwarded(copy_val)) {
          ContinuationGCSupport::relativize_stack_chunk(copy_val);
        }

        // Try to install the new forwarding pointer.
        oop winner = ShenandoahForwarding::try_update_forwardee(p, copy_val);
#ifdef ASSERT
        fwd = winner;
#endif
        if (winner == copy_val) {
          // Successfully evacuated. Our copy is now the public one!
          shenandoah_assert_correct(nullptr, copy_val);
          if (ShenandoahEvacTracking) {
            _heap->evac_tracker()->end_evacuation(_thread, size * HeapWordSize, _region->affiliation(), target_gen);
          }
          // Successful copy, advance top
          _bump_alloc_offset += size;
          if (!advance_bump_to_unmarked()) {
            // We had a successful allocation, but there is nothing left in this early recycled region. Try to claim another.
            try_establish_new_early_recycle_region();
            // Next invocation of do_object() will find the newly claimed region and use it if try succeeds.
          }
        } else {
          // We failed to install our forwarding pointer because some other thread won the race. We have not yet adjusted
          // the bump allocation pointer. Don't adjust. This allows the speculatively copied object's memory to be reused.
        }
#ifdef ASSERT
      }
#endif
    } else {
      // fwd != nullptr: the object was evacuated by free_set evacuator
    }
#ifdef ASSERT
    assert(fwd != nullptr, "Self forward if not evacuated");
    shenandoah_assert_correct(nullptr, fwd);
#endif
  } // else, p is already forwarded.  I don't have to do anything except make sure p is marked if above TAMS

  if (ShenandoahCSetAllocationForwardingTable) {
    // Regardless of who forwarded this object, explicitly mark it in preparation for construction of region's forwarding table.
    bool upgraded = false;
    if (cast_from_oop<HeapWord*>(p) >= _tams) {
      _context->mark_strong_ignore_tams(p, upgraded);
      assert(!upgraded, "should be first mark");
    }
    assert(_context->is_marked(p), "must be marked");
  }
}

ShenandoahConcurrentEvacuateRegionObjectClosure::ShenandoahConcurrentEvacuateRegionObjectClosure(ShenandoahHeap* heap) :
    _heap(heap), _thread(Thread::current()), _context(heap->marking_context()) {
}

ShenandoahConcurrentEvacuateRegionObjectClosure::~ShenandoahConcurrentEvacuateRegionObjectClosure() {
}

void ShenandoahConcurrentEvacuateRegionObjectClosure::set_region(ShenandoahHeapRegion* region) {
  _tams = _context->top_at_mark_start(region);
  _num_forwardings = 0;
}

void ShenandoahConcurrentEvacuateRegionObjectClosure::finish_region(ShenandoahHeapRegion* region) {
  _context->capture_top_at_mark_start(region);
}

void ShenandoahRetireGCLABClosure::do_thread(Thread* thread) {
  PLAB* gclab = ShenandoahThreadLocalData::gclab(thread);
  assert(gclab != nullptr, "GCLAB should be initialized for %s", thread->name());
  gclab->retire();
  if (_resize && ShenandoahThreadLocalData::gclab_size(thread) > 0) {
    ShenandoahThreadLocalData::set_gclab_size(thread, 0);
  }

  if (ShenandoahHeap::heap()->mode()->is_generational()) {
    ShenandoahPLAB* shenandoah_plab = ShenandoahThreadLocalData::shenandoah_plab(thread);
    assert(shenandoah_plab != nullptr, "PLAB should be initialized for %s", thread->name());

    // There are two reasons to retire all plabs between old-gen evacuation passes.
    //  1. We need to make the plab memory parsable by remembered-set scanning.
    //  2. We need to establish a trustworthy UpdateWaterMark value within each old-gen heap region
    shenandoah_plab->retire();

    // Re-enable promotions for the next evacuation phase.
    shenandoah_plab->enable_promotions();

    // Reset the fill size for next evacuation phase.
    if (_resize && shenandoah_plab->desired_size() > 0) {
      shenandoah_plab->set_desired_size(0);
    }
  }
}


ShenandoahEvacuationTask::ShenandoahEvacuationTask(
  ShenandoahHeap* sh, ShenandoahCollectionSet* cs, bool concurrent, bool require_early_recycle_preprocessing, size_t num_workers) :
    WorkerTask("Shenandoah Evacuation"),
    _sh(sh),
    _cs(cs),
    _ctx(sh->marking_context()),
    _concurrent(concurrent),
    _require_preprocessing(require_early_recycle_preprocessing),
    _num_workers(num_workers),
    _heuristics(ShenandoahHeap::heap()->global_generation()->heuristics()),
    _early_recycle_preprocessed_regions(0)
{
  assert(!ShenandoahHeap::heap()->mode()->is_generational(), "Handle generational evacuation elsewhere");
  if (require_preprocessing) {
    ShenandoahFreeSet* free_set = heap->free_set();
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
  // Otherwise, we are coming back to finish evacuation in a degenerated cycle.  Use the variable values captured at degen time. 
}

void ShenandoahEvacuationTask::work(uint worker_id) {
  if (_concurrent) {
    ShenandoahWorkerTimingsTracker timer(ShenandoahPhaseTimings::conc_evac, ShenandoahPhaseTimings::Work, worker_id, true);
    ShenandoahConcurrentWorkerSession worker_session(worker_id);
    SuspendibleThreadSetJoiner stsj;
    do_work(worker_id);
  } else {
    ShenandoahWorkerTimingsTracker timer(ShenandoahPhaseTimings::degen_gc_evac, ShenandoahPhaseTimings::Work, worker_id, true);
    ShenandoahParallelWorkerSession worker_session(worker_id);
    do_work(worker_id);
  }
#define KELVIN_DEBUG
#ifdef KELVIN_DEBUG
  log_info(gc)("ShenEvacTask::work(%u) is done", worker_id);
#endif
}

void ShenandoahEvacuationTask::yield_or_sleep(uint32_t &yields) {
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

void ShenandoahEvacuationTask::evacuate_region(ShenandoahHeapRegion* r, ShenandoahConcurrentEvacuateRegionObjectClosure& cl) {
  assert(r->has_live(), "Region %zu should have been reclaimed early", r->index());
  assert(!_cs->is_reusable(r), "must not already be recycled");
  log_develop_debug(gc)("GC Thread " PTR_FORMAT " evacuating region: %lu", p2i(Thread::current()), r->index());
  _sh->marked_object_iterate(r, &cl);
}

void ShenandoahEvacuationTask::do_work(unsigned int worker_id) {
  evacuate_regions<false>((uint) worker_id);
}

static void log_region(const ShenandoahHeapRegion* r, LogStream* ls) {
  ls->print_cr("ShenandoahEvacuationTask, looking at region %zu, [%s, %s, %s]",
               r->index(),
               r->is_active()? "active": "inactive",
               r->is_humongous()? (r->is_humongous_start()? "humongous_start": "humongous_continuation"): "regular",
               r->is_cset()? "cset": "not-cset");
}

template <bool GENERATIONAL>
void ShenandoahEvacuationTask::evacuate_regions(uint worker_id) {
  ShenandoahHeapRegion* r;
  size_t fwt_regions = 0;
  size_t region_size_words = ShenandoahHeapRegion::region_size_words();
  size_t region_size_bytes = region_size_words * HeapWordSize;
  size_t entry_size_words = ShenandoahEarlyRecycleInfo<true>::entry_size_words();
  size_t entry_size_bytes = entry_size_words * HeapWordSize;
  size_t num_regions = _sh->num_regions();
  size_t cset_region_count = _cs->count();
  ShenandoahFreeSet* free_set = _sh->free_set();
#define KELVIN_DEBUG
#ifdef KELVIN_DEBUG
  log_info(gc)("ShenGenEvacTask::evac_and_promote_regions(%u)", worker_id);
#endif
#ifdef KELVIN_DEBUG
  log_info(gc)("ShenGenEvacTask::evac_and_promote_regions(%u) is selecting fwt regions", worker_id);
#endif
  // Regions have already been sorted in increasing live data, which is a light-weight approximation of increasing
  // forward table size (aka increasing mark-word-density).

  // Set aside enough regions for early recycle with FWT to satisfy the anticipated memory shortfall.
  // Each worker is responsible for preparing 1/_num_workers of the total shortfall
  if (_require_preprocessing) {
    size_t total_shortfall;
    if (GENERATIONAL) {
      total_shortfall = _sh->young_generation()->heuristics()->mutator_memory_shortfall();
    } else {
      total_shortfall = _heuristics->mutator_memory_shortfall();
    }
    size_t my_shortfall = total_shortfall / _num_workers;
    if (worker_id == 0) {
      my_shortfall = total_shortfall - (_num_workers - 1) * my_shortfall;
    }
    size_t backfill = 0;
    size_t my_preprocessed_regions = 0;
    for (size_t i = worker_id; i < cset_region_count; i += _num_workers) {
      // Yield but do not cancel. Once started, preprocessing must finish.
      if (_concurrent && SuspendibleThreadSet::should_yield()) {
        SuspendibleThreadSet::yield();
      }
      my_preprocessed_regions++;
      r = _cs->cset_region(i);
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
  } else {
    // This cycle degenerated after preprocessing was finished. Redundantly, every worker signals that preprocessing is done.
    _early_recycle_preprocessed_regions.store_relaxed(cset_region_count);
  }

  static const size_t QueueSize = 8;
  uint32_t await_queue_forwardings[QueueSize];
  uint32_t await_queue_cset_indices[QueueSize];
  size_t queued_regions = 0;
  bool cset_is_finished = false;
  size_t num_forwardings;
  uint32_t cset_index;

  // THIS IS ONLY USED FOR GENERATIONAL MODE
  ShenandoahConcurrentEvacuator cl((ShenandoahGenerationalHeap*) _sh);
  // THIS IS ONLY USED FOR SINGLE_GENERATIONAL MODE
  ShenandoahConcurrentEvacuateRegionObjectClosure single_gen_cl(_sh);

  // Concurrent GC workers evacuate regions in order of increasing live data. They start with the regions that have
  // the least amount of live data, so the first few regions evacuated may go very fast.
  LogTarget(Debug, gc) lt;
  while (true) {
    cset_index = _cs->claim_next_index();
    if (cset_index >= cset_region_count) {
      r = nullptr;
      cset_is_finished = true;
    } else {
      r = _cs->cset_region(cset_index);
#ifdef KELVIN_DEBUG
      log_info(gc)("ShenGenEvacTask::evac_and_promote_regions(%u) claimed next region: %zu", worker_id, r->index());
#endif
      if (lt.is_enabled()) {
        LogStream ls(lt);
        log_region(r, &ls);
      }
      if (_cs->is_reusable(r)) {
        // Already evacuated and recycled. Degenerated GC resumes here.
        continue;
      }
      // Evacuate this region.
      // KELVIN TODO: I want to replace the evacuator with evacuate-to-early-recycled when available.
#ifdef KELVIN_DEBUG
      log_info(gc)("ShenGenEvacTask is evacuating region %zu", r->index());
#endif
      if (GENERATIONAL) {
        cl.set_region(r);
        _sh->marked_object_iterate(r, &cl);
        cl.finish_region(r);
        num_forwardings = cl.num_forwardings();
      } else {
        single_gen_cl.set_region(r);
        evacuate_region(r, single_gen_cl);
        single_gen_cl.finish_region(r);
        num_forwardings = single_gen_cl.num_forwardings();
      }
    }
    bool preprocessing_finished = (_early_recycle_preprocessed_regions.load_relaxed() == cset_region_count);
    if (preprocessing_finished) {
      size_t fwt_regions_recycled = 0;
      size_t fwt_bytes_recycled = 0;
      uint32_t first_newly_unpublished = cset_region_count;
      uint32_t last_newly_unpublished = 0;
      if (!cset_is_finished || (queued_regions > 0)) {
        if (cset_is_finished) {
          cset_index = await_queue_cset_indices[--queued_regions];
          num_forwardings = await_queue_forwardings[queued_regions];
          r = _cs->cset_region(cset_index);
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
            _sh->finish_no_table_region_evacuation(r, num_forwardings);
          } else {
            // Recycle with fwt, using num_entries that are stored in the region.
            // finish_fwt_region_evacuation() informs the free set that this region is ready to receive evacuations.
#ifdef KELVIN_DEBUG
            log_info(gc)("worker %u is finishing region %zu (cset index %u) with forward table, forwardings: %zu",
                         worker_id, r->index(), cset_index, num_forwardings);
#endif
            bool status = _sh->finish_fwt_region_evacuation(r);
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
          if (queued_regions > 0) {
            cset_index = await_queue_cset_indices[--queued_regions];
            r = _cs->cset_region(cset_index);
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

#ifdef KELVIN_SUSPENDIBLE
      log_info(gc)("worker %u yields maybe after finishing one region in evacuate_regions(), queued regions: %zu",
                   worker_id, queued_regions);
#endif
      // Cannot cancel evacuation if we have thread-local queued regions. We have to finish those first.
      if ((queued_regions == 0) && _sh->check_cancelled_gc_and_yield(_concurrent)) {
        // TODO: retire any claimed early-recycle-evacuation regions.
        // If we break here, publishing will happen later, when dgen resumes.
        break;
      }

      // Publish if approprriate
      if (cset_is_finished || (fwt_regions_recycled > 0)) {
        // We intend to publish.  If we do publish, we might want to publish more than once. So we loop. While we are
        // publishing, other workers that prepare more regions for publication are prohibited from publishing.
        bool published;
        do {
          // Repeatedly publish as long as there are relevant prepared regions that remain to be published.
          published = free_set->publish_prepared_regions(worker_id, _num_workers, _concurrent, cset_is_finished, _cs, GENERATIONAL);
        } while (published);
      } // else, there were no fwt regions recycled and the cset is not finished, so no need to publish anything.
    } else {
      // Preprocessing is not finished. We don't know whether we should build a forwarding table for this
      // evacuated region. Remember the region in my queue while I work on evacuating more regions.
      if (cset_is_finished) {
        if (queued_regions > 0) {
          // Loop until preprocessing is done
          uint32_t yields = 0;
          while (_early_recycle_preprocessed_regions.load_relaxed() != cset_region_count) {
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
          while (_early_recycle_preprocessed_regions.load_relaxed() != cset_region_count) {
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

#ifdef KELVIN_DEPRECATE_WITH_REPLACEMENT
the_old_do_work() {
  ShenandoahHeapRegion* r;
  size_t fwt_regions = 0;
  size_t region_size_words = ShenandoahHeapRegion::region_size_words();
  size_t region_size_bytes = region_size_words * HeapWordSize;
  size_t entry_size_words = ShenandoahHeapRegion::words_in_forwarding_entry();
  size_t entry_size_bytes = entry_size_words * HeapWordSize;
  size_t num_regions = _sh->num_regions();
  size_t cset_region_count = _cs->count();
  ShenandoahFreeSet* free_set = _sh->free_set();

  // Even a non-concurrent degen can benefit from early recycling of collection set regions.
  if (worker_id == 0) {
    // The first worker is responsible for selecting which regions will be early recycled with forward table.
    // Regions have already been sorted in increasing live data, which is a light-weight approximation of increasing
    // forward table size (aka increasing mark-word-density).

    // Set aside enough regions for early recycle with FWT to satisfy the anticipated memory shortfall.
    size_t short_fall = _heuristics->mutator_memory_shortfall();
    size_t back_fill = 0;
#ifdef KELVIN_DEBUG
    log_info(gc)("Preparing to evacuate with %zu cset regions, short_fall: %zu", cset_region_count, short_fall);
#endif
    for (size_t i = 0; i < cset_region_count; i++) {
      r = _cs->cset_region(i);
#ifdef KELVIN_DEBUG
      log_info(gc)("Examining region %zu, with %zu live words", r->index(), r->get_live_data_words());
#endif
      size_t num_entries;
      if (back_fill < short_fall) {
        // For now, calculate num_forwardings only for regions that we plan to early recycle with forwarding table.
        // As the non-fwt regions are evacuated, we will update their expected forwardings. The order of regions within
        // the early recycle regions array is approximately the order of increasing mark-word density.
        size_t num_forwardings = _ctx->count_mark_bit_conflicts<1>(r->bottom(), r->end());
        size_t num_entries = r->calculate_entries_in_forward_table(num_forwardings);
#ifdef KELVIN_DEBUG
        log_info(gc)("  Region %zu has %zu forwardings, %zu entries", r->index(), num_forwardings, num_entries);
#endif
        if (num_entries > 0) {
          size_t early_available_words = region_size_words - entry_size_words * num_entries;
#ifdef KELVIN_DEBUG
          log_info(gc)("  Early allocatable words: %zu", early_available_words);
#endif
          back_fill += early_available_words;
          r->set_early_recycle_status(ShenandoahEarlyRecycleStatus::Planned_FWT);
          // KELVIN TODO: we don't really need forwardings except for assertion enforcement.  (build-only)
          r->set_expected_forwardings(num_forwardings);
          r->set_fwt_entries(num_entries);
          fwt_regions++;
          continue;
        }
      }
      // This region will not be early recycled with forward table.
#ifdef KELVIN_DEBUG
      log_info(gc)("  Region %zu is rejected for fwt", r->index());
#endif
      r->set_fwt_entries(0);
    }

#ifdef KELVIN_DEBUG
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
  // else, there's no need to early recycle regions because this is not worker 0.

  // Concurrent GC workers evacuate regions in order of increasing live data. They start with the regions that have
  // the least amount of live data, so the first few regions evacuated may go very fast.
  static const size_t QueueSize = 16;
  ShenandoahHeapRegion* await_preprocessing_queue[QueueSize];
  uint32_t await_queue_forwardings[QueueSize];
  size_t queued_regions = 0;
  bool cset_is_finished = false;
  size_t num_forwardings;
  uint32_t cset_index;
  LogTarget(Debug, gc) lt;
  while (true) {
    cset_index = _cs->claim_next_index();
    if (cset_index >= cset_region_count) {
      r = nullptr;
      cset_is_finished = true;
#ifdef KELVIN_DEBUG
      log_info(gc)(" Worker %zu sees that cset_is_finished", worker_id);
#endif
    } else {
      r = _cs->cset_region(cset_index);
      if (_cs->is_reusable(r)) {
        // Already evacuated and recycled. Degenerated GC resumes here.
        continue;
      }
      // Evacuate this region.
      // KELVIN TODO: I want to replace the evacuator with evacuate-to-early-recycled when available.
      ShenandoahConcurrentEvacuateRegionObjectClosure cl(_sh, r);
      SuspendibleThreadSetJoiner stsj(_concurrent);
      evacuate_region(r, cl);
#ifdef KELVIN_DEBUG
      log_info(gc)("  Region %zu has been evacuated by worker %zu", r->index(), worker_id);
#endif
      num_forwardings = cl.num_forwardings();
      if (_sh->check_cancelled_gc_and_yield(_concurrent)) {
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
          r = await_preprocessing_queue[--queued_regions];
          num_forwardings = await_queue_forwardings[queued_regions];
        }
        // Loop here so we can process this region, plus any regions that have been queued up while we waited
        // for preprocessing to finish.
        while (true) {
          size_t num_entries;

#ifdef KELVIN_DEBUG
          log_info(gc)("Worker %zu is postprocessing region %zu with num_forwardings %zu, entries %u",
                       worker_id, r->index(), num_forwardings, r->get_fwt_entries());
#endif

          if ((num_entries = r->get_fwt_entries()) == 0) {
            // We plan that this region will not be early recycled with a forwarding table. We might choose to early recycle
            // this region without a forwarding table. Save the number of forwarded objects now, so that we can use mark-word
            // density to prioritize no-table recycling if necessary.
#ifdef KELVIN_DEBUG
            log_info(gc)("Worker %zu is finishing region %zu for no_table recycle with num_forwardings %zu",
                         worker_id, r->index(), num_forwardings);
#endif
            _sh->finish_no_table_region_evacuation(r, num_forwardings);
#ifdef KELVIN_DEBUG
            log_info(gc)("Worker %zu finished region %zu for no_table recycle with num_forwardings %zu",
                         worker_id, r->index(), num_forwardings);
#endif
          } else {
            // Recycle with fwt, using num_entries that are stored in the region.
            // finish_fwt_region_evacuation() informs the free set that this region is ready to receive evacuations.
#ifdef KELVIN_DEBUG
            log_info(gc)("Worker %zu is finishing region %zu for fwt recycle with num_forwardings %zu",
                         worker_id, r->index(), num_forwardings);
#endif
            _sh->finish_fwt_region_evacuation(r);
            fwt_regions_recycled++;
            fwt_bytes_recycled += (region_size_words - num_entries * entry_size_words) * HeapWordSize;
          }
          if (_sh->check_cancelled_gc_and_yield(_concurrent)) {
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
          //  Prepared > previously published by at least num_workers / 2, or
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
                  size_t upper_bound_on_unpublished = free_set->upper_bound_on_unpublished_fwt();
                  // Note: more regions might get published while I'm iterating through this loop and/or while I'm
                  // performing the handshake.  We won't count those as published until some subsequent publication.
                  for (size_t index = first_unpublished_index; index < upper_bound_on_unpublished; index++) {
                    ShenandoahHeapRegion* r = free_set->get_tlab_region(index);
                    if (r->get_early_recycle_status() == ShenandoahEarlyRecycleStatus::Prepared_FWT) {
                      r->set_early_recycle_status(ShenandoahEarlyRecycleStatus::Publishing_FWT);
                      regions_to_publish++;
                      bytes_to_publish += region_size_bytes - r->get_fwt_entries() * entry_size_bytes;
                    }
                  }

                  // Everything stored above should become visible to other threads after handshake
                  OrderAccess::storeload();
                  if (_concurrent) {
                    assert(!SafepointSynchronize::is_at_safepoint(), "Cannot rendezvous if we're at a safepoint");
                    assert(!Thread::current()->is_suspendible_thread(), "must not hold STS join across rendezvous");
#ifdef KELVIN_DEBUG
                    log_info(gc)("Worker %zu satisfied is_suspendible_thread() test", worker_id);
#endif
                    // Force mutators into handshake to assure that they no longer use mark-word forwarding on any of the
                    // now-prepared regions.
                    _sh->rendezvous_threads("Switch to Forward Table");
#ifdef KELVIN_DEBUG
                    log_info(gc)("worker %zu back from rendezous_threads, first_unpublished: %zu, upper_bound: %zu",
                                 worker_id, first_unpublished_index, upper_bound_on_unpublished);
#endif
                  } else {
#ifdef KELVIN_DEBUG
                    log_info(gc)("worker %zu skips rendezous_threads because we're in degen, first_unpublished: %zu, upper_bound: %zu",
                                 worker_id, first_unpublished_index, upper_bound_on_unpublished);
#endif

                  }

                  size_t new_first_unpublished_index = 0xffffffffffffffff;
                  size_t new_upper_bound = 0;
                  for (size_t index = first_unpublished_index; index < upper_bound_on_unpublished; index++) {
                    ShenandoahHeapRegion* r = free_set->get_tlab_region(index);
                    ShenandoahEarlyRecycleStatus status = r->get_early_recycle_status();
                    switch (status) {
                      case ShenandoahEarlyRecycleStatus::Publishing_FWT:
                        r->set_early_recycle_status(ShenandoahEarlyRecycleStatus::Published_FWT);
                        break;
                      case ShenandoahEarlyRecycleStatus::Prepared_FWT:
                        if (index < new_first_unpublished_index) {
                          new_first_unpublished_index = index;
                        }
                        if (index > new_upper_bound) {
                          new_upper_bound = index;
                        }
                        break;
                      default:
                        assert(false, "Not expecting this status value here: %d", (int) status);
                    }
                  }

                  // If unpublished range has not changed since I read it, update.  Otherwise, leave values alone.
                  // Expected behavior: first_unpublished only advances if it was initially empty.  last_unpublished
                  // grows because other threads finish preparing regions while this thread is publishing.
                  free_set->compare_set_first_unpublished_fwt(first_unpublished_index, new_first_unpublished_index);
                  free_set->compare_set_upper_bound_on_unpublished_fwt(upper_bound_on_unpublished, new_upper_bound);

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
      } // else, there were no fwt regions recycled and the cset is not finished, so no need to publish anything.
    } else {
      // Preprocessing is not yet done: we don't know whether we should build a forwarding table for this
      // evacuated region. Remember the region in my queue while I work on evacuating more regions.
      if (queued_regions < QueueSize) {
        await_queue_forwardings[queued_regions] = num_forwardings;
        await_preprocessing_queue[queued_regions++] = r;
        if (queued_regions == QueueSize) {
          // The queue is full. Do not evacuate another region until preprocessing is done since we have nowhere
          // to store yet another region for preparation.
          int yields = 0;
          while (_early_recycle_preprocessed_regions.load_relaxed() != num_regions) {
            yield_or_sleep(yields);
          }
        }
      }
    }
    if (cset_is_finished) {
      break;
    }
  } // This ends the outer-most while (true) loop, which repeatedly publishes as long as there is more to be published
#ifdef KELVIN_DEBUG
  log_info(gc)("We are done with do_work(%zu)", worker_id);
#endif
}
#endif
