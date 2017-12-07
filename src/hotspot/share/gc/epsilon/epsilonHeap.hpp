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

#ifndef SHARE_VM_GC_EPSILON_COLLECTEDHEAP_HPP
#define SHARE_VM_GC_EPSILON_COLLECTEDHEAP_HPP

#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/space.hpp"
#include "gc/epsilon/epsilonCollectorPolicy.hpp"
#include "gc/epsilon/epsilonMonitoringSupport.hpp"
#include "gc/epsilon/epsilonBarrierSet.hpp"
#include "gc/epsilon/epsilon_globals.hpp"

class EpsilonHeap : public CollectedHeap {
private:
  EpsilonCollectorPolicy* _policy;
  EpsilonMonitoringSupport* _monitoring_support;
  ContiguousSpace* _space;
  VirtualSpace _virtual_space;
  size_t _max_tlab_size;
  size_t _last_counter_update;
public:
  EpsilonHeap(EpsilonCollectorPolicy* p) : _policy(p) {};

  virtual Name kind() const {
    return CollectedHeap::EpsilonHeap;
  }

  virtual const char *name() const {
    return "Epsilon GC";
  }

  virtual jint initialize();

  virtual void post_initialize();

  virtual void initialize_serviceability();
  virtual GrowableArray<GCMemoryManager*> memory_managers();
  virtual GrowableArray<MemoryPool*> memory_pools();

  static EpsilonHeap* heap();

  virtual size_t capacity()     const { return _virtual_space.committed_size(); }
  virtual size_t used()         const { return _space->used(); }
  virtual size_t max_capacity() const { return _virtual_space.reserved_size(); }

  virtual bool is_maximal_no_gc() const {
    // No GC is going to happen, unless we are at capacity.
    // At which point we will fail anyway.
    return used() == capacity();
  }

  virtual bool is_in(const void *p) const { return _space->is_in(p); }

  virtual bool is_scavengable(oop obj) {
    // Epsilon does not move objects, no objects are scavengable.
    return false;
  }

  HeapWord* allocate_work(size_t size);
  virtual HeapWord* mem_allocate(size_t size, bool *gc_overhead_limit_was_exceeded);
  virtual HeapWord* allocate_new_tlab(size_t size);

  // TLAB allocations
  virtual bool supports_tlab_allocation()           const { return UseTLAB; }
  virtual size_t tlab_capacity(Thread *thr)         const { return capacity(); }
  virtual size_t tlab_used(Thread *thr)             const { return used(); }
  virtual size_t max_tlab_size()                    const { return _max_tlab_size; }
  virtual size_t unsafe_max_tlab_alloc(Thread *thr) const;

  virtual bool can_elide_tlab_store_barriers() const {
    // No store barriers for Epsilon, allow elision
    return true;
  }

  virtual bool can_elide_initializing_store_barrier(oop new_obj) {
    // No card marks for Epsilon, can elide them all.
    return true;
  }

  virtual bool card_mark_must_follow_store() const {
    // No card marks for Epsilon.
    return false;
  }

  virtual void collect(GCCause::Cause cause);
  virtual void do_full_collection(bool clear_all_soft_refs);

  virtual AdaptiveSizePolicy *size_policy() {
    // No such thing for Epsilon
    return NULL;
  }

  virtual CollectorPolicy *collector_policy() const {
    return _policy;
  }

  virtual void object_iterate(ObjectClosure *cl) {
    safe_object_iterate(cl);
  }

  virtual void safe_object_iterate(ObjectClosure *cl);

  virtual HeapWord* block_start(const void *addr) const {
    // Epsilon does not support block parsing.
    return NULL;
  }

  virtual size_t block_size(const HeapWord *addr) const {
    // Epsilon does not support block parsing.
    return 0;
  }

  virtual bool block_is_obj(const HeapWord *addr) const {
    // Epsilon does not support block parsing.
    return false;
  }

  virtual jlong millis_since_last_gc() {
    // Report time since the VM start
    return os::elapsed_counter() / NANOSECS_PER_MILLISEC;
  }

  virtual void prepare_for_verify() {
    // No heap verification.
  }

  virtual void print_gc_threads_on(outputStream *st) const {
    // No GC threads.
  }

  virtual void gc_threads_do(ThreadClosure *tc) const {
    // No GC threads.
  }

  virtual void print_on(outputStream *st) const;
  virtual void print_tracing_info() const;

  virtual void verify(VerifyOption option) {
    // No heap verification for Epsilon.
  }

};

#endif // SHARE_VM_GC_EPSILON_COLLECTEDHEAP_HPP
