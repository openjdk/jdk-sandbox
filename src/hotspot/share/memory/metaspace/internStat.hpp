/*
 * Copyright (c) 2019, SAP SE. All rights reserved.
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_MEMORY_METASPACE_INTERNSTAT_HPP
#define SHARE_MEMORY_METASPACE_INTERNSTAT_HPP

#ifdef ASSERT


#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "utilities/globalDefinitions.hpp"

class outputStream;

namespace metaspace {

class InternalStats : public AllStatic {

  // Note: all counters which are modified on the classloader local allocation path
  //   (not under ExpandLock protection) have to be atomic.

#define ALL_MY_COUNTERS(x, x_atomic)                \
                                                    \
  /* Number of allocations. */                      \
  x_atomic(num_allocs)                              \
                                                    \
  /* Number of deallocations */                     \
  x_atomic(num_deallocs)                            \
  /* Number of times an allocation was satisfied */ \
  /*  from deallocated blocks. */                   \
  x_atomic(num_allocs_from_deallocated_blocks)      \
                                                    \
  /* Number of times an allocation failed */        \
  /*  because the chunk was too small. */           \
	x_atomic(num_allocs_failed_chunk_too_small)       \
                                                    \
  /* Number of times an allocation failed */        \
  /*  because we hit a limit. */                    \
  x_atomic(num_allocs_failed_limit)                 \
                                                    \
  /* Number of times a ClassLoaderMetaspace was */  \
  /*  born... */                                    \
  x(num_metaspace_births)                           \
  /* ... and died. */                               \
  x(num_metaspace_deaths)                           \
                                                    \
  /* Number of times VirtualSpaceNode were */       \
  /*  created...  */                                \
  x(num_vsnodes_created)                            \
  /* ... and purged. */                             \
  x(num_vsnodes_destroyed)                          \
                                                    \
  /* Number of times we committed space. */         \
  x(num_space_committed)                            \
  /* Number of times we uncommitted space. */       \
  x(num_space_uncommitted)                          \
                                                    \
  /* Number of times a chunk was returned to the */ \
  /*  freelist (external only). */                  \
  x(num_chunks_returned_to_freelist)                \
  /* Number of times a chunk was taken from */      \
  /*  freelist (external only) */                   \
  x(num_chunks_taken_from_freelist)                 \
                                                    \
  /* Number of successful chunk merges */           \
  x(num_chunk_merges)                               \
  /* Number of chunks removed from freelist as */   \
  /* result of a merge operation */                 \
  x(num_chunks_removed_from_freelist_due_to_merge)  \
                                                    \
  /* Number of chunk splits */                      \
  x(num_chunk_splits)                               \
  /* Number of chunks added to freelist as */       \
  /* result of a split operation */                 \
  x(num_chunks_added_to_freelist_due_to_split)      \
  /* Number of chunk in place enlargements */       \
  x(num_chunk_enlarged)                             \
  /* Number of chunks retired */                    \
  x(num_chunks_retired)                             \
                                                    \
  /* Number of times we did a purge */              \
  x(num_purges)                                     \
  /* Number of times we did a wholesale uncommit */ \
  x(num_wholesale_uncommits)                        \



#define DEFINE_COUNTER(name)          static uintx _##name;
#define DEFINE_ATOMIC_COUNTER(name)   static volatile uintx _##name;

  ALL_MY_COUNTERS(DEFINE_COUNTER, DEFINE_ATOMIC_COUNTER)

#undef DEFINE_COUNTER
#undef DEFINE_ATOMIC_COUNTER

public:

#define INCREMENTOR(name)           static void inc_##name() { _##name ++; }
#define INCREMENTOR_ATOMIC(name)    static void inc_##name() { Atomic::inc(&_##name); }

  ALL_MY_COUNTERS(INCREMENTOR, INCREMENTOR_ATOMIC)

  static void print_on(outputStream* st);

#undef INCREMENTOR
#undef INCREMENTOR_ATOMIC

};

} // namespace metaspace

#endif // ASSERT

#endif // SHARE_MEMORY_METASPACE_INTERNSTAT_HPP
