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

#ifndef SHARE_VM_SERVICES_EPSILON_COLLECTORPOLICY_HPP
#define SHARE_VM_SERVICES_EPSILON_COLLECTORPOLICY_HPP

#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc/epsilon/epsilonCollectedHeap.hpp"
#include "services/memoryPool.hpp"
#include "services/memoryUsage.hpp"
#endif // INCLUDE_ALL_GCS

class EpsilonDummyMemoryPool : public CollectedMemoryPool {
public:
  EpsilonDummyMemoryPool();
  MemoryUsage get_memory_usage()      { return MemoryUsage(0, 0, 0, 0); }
  size_t used_in_bytes()              { return 0; }
  size_t max_size() const             { return 0; }
};

class EpsilonMemoryPool : public CollectedMemoryPool {
private:
  const static size_t _undefined_max = (size_t) -1;
  EpsilonCollectedHeap* _heap;

public:
  EpsilonMemoryPool(EpsilonCollectedHeap* heap);

  size_t committed_in_bytes() {
    return _heap->capacity();
  }
  size_t used_in_bytes() {
    return _heap->used();
  }
  size_t max_size() const {
    return _heap->max_capacity();
  }
  MemoryUsage get_memory_usage();
};

#endif // SHARE_VM_SERVICES_EPSILON_COLLECTORPOLICY_HPP
