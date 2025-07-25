/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, Red Hat, Inc. and/or its affiliates.
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

#include "gc/parallel/parallelArguments.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/shared/adaptiveSizePolicy.hpp"
#include "gc/shared/fullGCForwarding.hpp"
#include "gc/shared/gcArguments.hpp"
#include "gc/shared/genArguments.hpp"
#include "gc/shared/workerPolicy.hpp"
#include "logging/log.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/powerOfTwo.hpp"

size_t ParallelArguments::conservative_max_heap_alignment() {
  return compute_heap_alignment();
}

void ParallelArguments::initialize() {
  GCArguments::initialize();
  assert(UseParallelGC, "Error");

  // If no heap maximum was requested explicitly, use some reasonable fraction
  // of the physical memory, up to a maximum of 1GB.
  FLAG_SET_DEFAULT(ParallelGCThreads,
                   WorkerPolicy::parallel_worker_threads());
  if (ParallelGCThreads == 0) {
    jio_fprintf(defaultStream::error_stream(),
        "The Parallel GC can not be combined with -XX:ParallelGCThreads=0\n");
    vm_exit(1);
  }

  if (UseAdaptiveSizePolicy) {
    // We don't want to limit adaptive heap sizing's freedom to adjust the heap
    // unless the user actually sets these flags.
    if (FLAG_IS_DEFAULT(MinHeapFreeRatio)) {
      FLAG_SET_DEFAULT(MinHeapFreeRatio, 0);
    }
    if (FLAG_IS_DEFAULT(MaxHeapFreeRatio)) {
      FLAG_SET_DEFAULT(MaxHeapFreeRatio, 100);
    }
  }

  // True in product build, since tests using debug build often stress GC
  if (FLAG_IS_DEFAULT(UseGCOverheadLimit)) {
    FLAG_SET_DEFAULT(UseGCOverheadLimit, trueInProduct);
  }

  if (InitialSurvivorRatio < MinSurvivorRatio) {
    if (FLAG_IS_CMDLINE(InitialSurvivorRatio)) {
      if (FLAG_IS_CMDLINE(MinSurvivorRatio)) {
        jio_fprintf(defaultStream::error_stream(),
          "Inconsistent MinSurvivorRatio vs InitialSurvivorRatio: %zu vs %zu\n", MinSurvivorRatio, InitialSurvivorRatio);
      }
      FLAG_SET_DEFAULT(MinSurvivorRatio, InitialSurvivorRatio);
    } else {
      FLAG_SET_DEFAULT(InitialSurvivorRatio, MinSurvivorRatio);
    }
  }

  // If InitialSurvivorRatio or MinSurvivorRatio were not specified, but the
  // SurvivorRatio has been set, reset their default values to SurvivorRatio +
  // 2.  By doing this we make SurvivorRatio also work for Parallel Scavenger.
  // See CR 6362902 for details.
  if (!FLAG_IS_DEFAULT(SurvivorRatio)) {
    if (FLAG_IS_DEFAULT(InitialSurvivorRatio)) {
       FLAG_SET_DEFAULT(InitialSurvivorRatio, SurvivorRatio + 2);
    }
    if (FLAG_IS_DEFAULT(MinSurvivorRatio)) {
      FLAG_SET_DEFAULT(MinSurvivorRatio, SurvivorRatio + 2);
    }
  }

  if (FLAG_IS_DEFAULT(ParallelRefProcEnabled) && ParallelGCThreads > 1) {
    FLAG_SET_DEFAULT(ParallelRefProcEnabled, true);
  }

  FullGCForwarding::initialize_flags(heap_reserved_size_bytes());
}

// The alignment used for spaces in young gen and old gen
static size_t default_space_alignment() {
  return 64 * K * HeapWordSize;
}

void ParallelArguments::initialize_alignments() {
  // Initialize card size before initializing alignments
  CardTable::initialize_card_size();
  SpaceAlignment = default_space_alignment();
  HeapAlignment = compute_heap_alignment();
}

void ParallelArguments::initialize_heap_flags_and_sizes_one_pass() {
  // Do basic sizing work
  GenArguments::initialize_heap_flags_and_sizes();
}

void ParallelArguments::initialize_heap_flags_and_sizes() {
  initialize_heap_flags_and_sizes_one_pass();

  const size_t min_pages = 4; // 1 for eden + 1 for each survivor + 1 for old
  const size_t page_sz = os::page_size_for_region_aligned(MinHeapSize, min_pages);

  // Can a page size be something else than a power of two?
  assert(is_power_of_2((intptr_t)page_sz), "must be a power of 2");
  size_t new_alignment = align_up(page_sz, SpaceAlignment);
  if (new_alignment != SpaceAlignment) {
    SpaceAlignment = new_alignment;
    // Redo everything from the start
    initialize_heap_flags_and_sizes_one_pass();
  }
}

size_t ParallelArguments::heap_reserved_size_bytes() {
  return MaxHeapSize;
}

CollectedHeap* ParallelArguments::create_heap() {
  return new ParallelScavengeHeap();
}
