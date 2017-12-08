/*
 * Copyright (c) 2017, Red Hat, Inc. and/or its affiliates.
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

#include "precompiled.hpp"
#include "gc/epsilon/epsilonHeap.hpp"
#include "gc/epsilon/epsilonMemoryPool.hpp"

jint EpsilonHeap::initialize() {
  CollectedHeap::pre_initialize();

  size_t init_byte_size = _policy->initial_heap_byte_size();
  size_t max_byte_size = _policy->max_heap_byte_size();
  size_t align = _policy->heap_alignment();

  ReservedSpace heap_rs = Universe::reserve_heap(max_byte_size,  align);
  _virtual_space.initialize(heap_rs, init_byte_size);

  MemRegion committed_region((HeapWord*)_virtual_space.low(), (HeapWord*)_virtual_space.high());
  MemRegion reserved_region((HeapWord*)_virtual_space.low_boundary(), (HeapWord*)_virtual_space.high_boundary());

  initialize_reserved_region(reserved_region.start(), reserved_region.end());

  _space = new ContiguousSpace();
  _space->initialize(committed_region, true, true);

  EpsilonBarrierSet* bs = new EpsilonBarrierSet();
  set_barrier_set(bs);

  _max_tlab_size = MIN2(CollectedHeap::max_tlab_size(), EpsilonMaxTLABSize / HeapWordSize);

  _monitoring_support = new EpsilonMonitoringSupport(this);
  _last_counter_update = 0;
  _last_heap_print = 0;

  _step_counter_update = MIN2<size_t>(max_byte_size / 16, EpsilonUpdateCountersStep);
  _step_heap_print = (EpsilonPrintHeapStep == 0) ? SIZE_MAX : (max_byte_size / EpsilonPrintHeapStep);

  if (init_byte_size != max_byte_size) {
    log_info(gc)("Initialized with " SIZE_FORMAT "M heap, resizeable to up to " SIZE_FORMAT "M heap with " SIZE_FORMAT "M steps",
                 init_byte_size / M, max_byte_size / M, EpsilonMinHeapExpand / M);
  } else {
    log_info(gc)("Initialized with " SIZE_FORMAT "M non-resizeable heap", init_byte_size / M);
  }
  if (UseTLAB) {
    log_info(gc)("Using TLAB allocation; min: " SIZE_FORMAT "K, max: " SIZE_FORMAT "K",
                                ThreadLocalAllocBuffer::min_size()*HeapWordSize / K,
                                _max_tlab_size*HeapWordSize / K);
  } else {
    log_info(gc)("Not using TLAB allocation");
  }

  return JNI_OK;
}

void EpsilonHeap::post_initialize() {
  CollectedHeap::post_initialize();
}

void EpsilonHeap::initialize_serviceability() {
  _pool = new EpsilonMemoryPool(this);
  _memory_manager.add_pool(_pool);
}

GrowableArray<GCMemoryManager*> EpsilonHeap::memory_managers() {
  GrowableArray<GCMemoryManager*> memory_managers(1);
  memory_managers.append(&_memory_manager);
  return memory_managers;
}

GrowableArray<MemoryPool*> EpsilonHeap::memory_pools() {
  GrowableArray<MemoryPool*> memory_pools(3);
  memory_pools.append(_pool);
  return memory_pools;
}

size_t EpsilonHeap::unsafe_max_tlab_alloc(Thread *thr) const {
  // This is the only way we can control TLAB sizes without having safepoints.
  // Implement exponential expansion within [MinTLABSize; _max_tlab_size], based
  // on previously "used" TLAB size.

  size_t size = MIN2(_max_tlab_size * HeapWordSize, MAX2(MinTLABSize, thr->tlab().used() * HeapWordSize * 2));

  if (log_is_enabled(Trace, gc)) {
    ResourceMark rm;
    log_trace(gc)(
            "Selecting TLAB size for \"%s\" (Desired: " SIZE_FORMAT "K, Used: " SIZE_FORMAT "K) -> " SIZE_FORMAT "K",
            Thread::current()->name(),
            thr->tlab().desired_size() * HeapWordSize / K,
            thr->tlab().used() * HeapWordSize / K,
            size / K);
  }

  return size;
}

EpsilonHeap* EpsilonHeap::heap() {
  CollectedHeap* heap = Universe::heap();
  assert(heap != NULL, "Uninitialized access to EpsilonHeap::heap()");
  assert(heap->kind() == CollectedHeap::EpsilonHeap, "Not a EpsilonHeap");
  return (EpsilonHeap*)heap;
}

HeapWord* EpsilonHeap::allocate_work(size_t size) {
  HeapWord* res = _space->par_allocate(size);

  while (res == NULL) {
    // Allocation failed, attempt expansion, and retry:
    MutexLockerEx ml(Heap_lock);
    if (!_virtual_space.expand_by(MAX2(size, EpsilonMinHeapExpand))) {
      return NULL;
    }
    _space->set_end((HeapWord *) _virtual_space.high());
    res = _space->par_allocate(size);
  }

  size_t used = _space->used();
  if (used - _last_counter_update >= _step_counter_update) {
    _last_counter_update = used;
    _monitoring_support->update_counters();
  }

  if (used - _last_heap_print >= _step_heap_print) {
    log_info(gc)("Heap: " SIZE_FORMAT "M reserved, " SIZE_FORMAT "M committed, " SIZE_FORMAT "M used",
                 max_capacity() / M, capacity() / M, used / M);
    _last_heap_print = used;
  }

  return res;
}

HeapWord* EpsilonHeap::allocate_new_tlab(size_t size) {
  return allocate_work(size);
}

HeapWord* EpsilonHeap::mem_allocate(size_t size, bool *gc_overhead_limit_was_exceeded) {
  *gc_overhead_limit_was_exceeded = false;
  return allocate_work(size);
}

void EpsilonHeap::collect(GCCause::Cause cause) {
  log_info(gc)("GC was triggered with cause \"%s\". Ignoring.", GCCause::to_string(cause));
  _monitoring_support->update_counters();
}

void EpsilonHeap::do_full_collection(bool clear_all_soft_refs) {
  log_info(gc)("Full GC was triggered with cause \"%s\". Ignoring.", GCCause::to_string(gc_cause()));
  _monitoring_support->update_counters();
}

void EpsilonHeap::safe_object_iterate(ObjectClosure *cl) {
  _space->safe_object_iterate(cl);
}

void EpsilonHeap::print_on(outputStream *st) const {
  st->print_cr("Epsilon Heap");

  // Cast away constness:
  ((VirtualSpace)_virtual_space).print_on(st);

  st->print_cr("Allocation space:");
  _space->print_on(st);
}

void EpsilonHeap::print_tracing_info() const {
  Log(gc) log;
  size_t allocated_kb = used() / K;
  log.info("Total allocated: " SIZE_FORMAT " KB",
           allocated_kb);
  log.info("Average allocation rate: " SIZE_FORMAT " KB/sec",
           (size_t)(allocated_kb * NANOSECS_PER_SEC / os::elapsed_counter()));
}
