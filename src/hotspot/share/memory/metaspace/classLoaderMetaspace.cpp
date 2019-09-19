/*
 * Copyright (c) 2018, 2019, SAP SE. All rights reserved.
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "precompiled.hpp"

#include "logging/log.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspaceTracer.hpp"
#include "memory/metaspace/chunkAllocSequence.hpp"
#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/classLoaderMetaspace.hpp"
#include "memory/metaspace/internStat.hpp"
#include "memory/metaspace/metaspaceEnums.hpp"
#include "memory/metaspace/metaspaceStatistics.hpp"
#include "memory/metaspace/runningCounters.hpp"
#include "memory/metaspace/settings.hpp"
#include "memory/metaspace/spaceManager.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"

namespace metaspace {

static bool use_class_space(bool is_class) {
  if (Metaspace::using_class_space()) {
    if (is_class) {
      return true;
    }
  }
  return false;
}

static bool use_class_space(MetadataType mdType) {
  return use_class_space(is_class(mdType));
}

ClassLoaderMetaspace::ClassLoaderMetaspace(Mutex* lock, MetaspaceType space_type)
  : _lock(lock)
  , _space_type(space_type)
  , _non_class_space_manager(NULL)
  , _class_space_manager(NULL)
{
  // Initialize non-class spacemanager
  _non_class_space_manager = new SpaceManager(
      ChunkManager::chunkmanager_nonclass(),
      ChunkAllocSequence::alloc_sequence_by_space_type(space_type, false),
      lock,
      RunningCounters::used_nonclass_counter(),
      "non-class sm");

  // If needed, initialize class spacemanager
  if (Metaspace::using_class_space()) {
    _class_space_manager = new SpaceManager(
        ChunkManager::chunkmanager_class(),
        ChunkAllocSequence::alloc_sequence_by_space_type(space_type, true),
        lock,
        RunningCounters::used_class_counter(),
        "class sm");
  }

#ifdef ASSERT
  InternalStats::inc_num_metaspace_births();
  if (_space_type == metaspace::UnsafeAnonymousMetaspaceType) {
    InternalStats::inc_num_anon_cld_births();
  }
#endif
}

ClassLoaderMetaspace::~ClassLoaderMetaspace() {
  Metaspace::assert_not_frozen();

  delete _non_class_space_manager;
  delete _class_space_manager;

#ifdef ASSERT
  InternalStats::inc_num_metaspace_deaths();
  if (_space_type == metaspace::UnsafeAnonymousMetaspaceType) {
    InternalStats::inc_num_anon_cld_deaths();
  }
#endif

}

// Allocate word_size words from Metaspace.
MetaWord* ClassLoaderMetaspace::allocate(size_t word_size, MetadataType mdType) {
  Metaspace::assert_not_frozen();
  if (use_class_space(mdType)) {
    return class_space_manager()->allocate(word_size);
  } else {
    return non_class_space_manager()->allocate(word_size);
  }
}

// Attempt to expand the GC threshold to be good for at least another word_size words
// and allocate. Returns NULL if failure. Used during Metaspace GC.
MetaWord* ClassLoaderMetaspace::expand_and_allocate(size_t word_size, MetadataType mdType) {
  Metaspace::assert_not_frozen();
  size_t delta_bytes = MetaspaceGC::delta_capacity_until_GC(word_size * BytesPerWord);
  assert(delta_bytes > 0, "Must be");

  size_t before = 0;
  size_t after = 0;
  bool can_retry = true;
  MetaWord* res;
  bool incremented;

  // Each thread increments the HWM at most once. Even if the thread fails to increment
  // the HWM, an allocation is still attempted. This is because another thread must then
  // have incremented the HWM and therefore the allocation might still succeed.
  do {
    incremented = MetaspaceGC::inc_capacity_until_GC(delta_bytes, &after, &before, &can_retry);
    res = allocate(word_size, mdType);
  } while (!incremented && res == NULL && can_retry);

  if (incremented) {
    Metaspace::tracer()->report_gc_threshold(before, after,
                                  MetaspaceGCThresholdUpdater::ExpandAndAllocate);
    log_trace(gc, metaspace)("Increase capacity to GC from " SIZE_FORMAT " to " SIZE_FORMAT, before, after);
  }

  return res;
}

// Prematurely returns a metaspace allocation to the _block_freelists
// because it is not needed anymore.
void ClassLoaderMetaspace::deallocate(MetaWord* ptr, size_t word_size, bool is_class) {

  Metaspace::assert_not_frozen();

  if (use_class_space(is_class)) {
    class_space_manager()->deallocate(ptr, word_size);
  } else {
    non_class_space_manager()->deallocate(ptr, word_size);
  }

  DEBUG_ONLY(InternalStats::inc_num_deallocs();)

}

// Update statistics. This walks all in-use chunks.
void ClassLoaderMetaspace::add_to_statistics(clms_stats_t* out) const {
  if (non_class_space_manager() != NULL) {
    non_class_space_manager()->add_to_statistics(&out->sm_stats_nonclass);
  }
  if (class_space_manager() != NULL) {
    class_space_manager()->add_to_statistics(&out->sm_stats_class);
  }
}

#ifdef ASSERT
void ClassLoaderMetaspace::verify() const {
  check_valid_spacetype(_space_type);
  if (non_class_space_manager() != NULL) {
    non_class_space_manager()->verify();
  }
  if (class_space_manager() != NULL) {
    class_space_manager()->verify();
  }
}
#endif // ASSERT

} // end namespace metaspace




