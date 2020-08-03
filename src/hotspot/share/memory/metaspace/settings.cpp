/*
 * Copyright (c) 2019 SAP SE. All rights reserved.
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
#include "logging/logStream.hpp"

#include "memory/metaspace/chunkLevel.hpp"
#include "memory/metaspace/settings.hpp"

#include "utilities/globalDefinitions.hpp"
#include "utilities/debug.hpp"

namespace metaspace {

size_t Settings::_commit_granule_bytes = 0;
size_t Settings::_commit_granule_words = 0;
bool Settings::_newborn_root_chunks_are_fully_committed = false;
bool Settings::_uncommit_free_chunks = false;

DEBUG_ONLY(bool Settings::_use_allocation_guard = false;)
DEBUG_ONLY(bool Settings::_handle_deallocations = true;)


void Settings::ergo_initialize() {

  if (strcmp(MetaspaceReclaimPolicy, "none") == 0) {

    log_info(metaspace)("Initialized with strategy: no reclaim.");

    _commit_granule_bytes = MAX2((size_t)os::vm_page_size(), 64 * K);
    _commit_granule_words = _commit_granule_bytes / BytesPerWord;

    _newborn_root_chunks_are_fully_committed = true;

    // In "none" reclamation mode, we do not uncommit, but still delete completely empty nodes.
    // This is almost the same behavior as the old Metaspace.

    _uncommit_free_chunks = false;


  } else if (strcmp(MetaspaceReclaimPolicy, "aggressive") == 0) {

    log_info(metaspace)("Initialized with strategy: aggressive reclaim.");

    // Set the granule size rather small; may increase
    // mapping fragmentation but also increase chance to uncommit.
    _commit_granule_bytes = MAX2((size_t)os::vm_page_size(), 16 * K);
    _commit_granule_words = _commit_granule_bytes / BytesPerWord;

    _newborn_root_chunks_are_fully_committed = false;

    _uncommit_free_chunks = true;

  } else if (strcmp(MetaspaceReclaimPolicy, "balanced") == 0) {

    log_info(metaspace)("Initialized with strategy: balanced reclaim.");

    _commit_granule_bytes = MAX2((size_t)os::vm_page_size(), 64 * K);
    _commit_granule_words = _commit_granule_bytes / BytesPerWord;

    _newborn_root_chunks_are_fully_committed = false;

    _uncommit_free_chunks = true;

  } else {

    vm_exit_during_initialization("Invalid value for MetaspaceReclaimPolicy: \"%s\".", MetaspaceReclaimPolicy);

  }

  // Sanity checks.
  guarantee(commit_granule_words() <= chunklevel::MAX_CHUNK_WORD_SIZE, "Too large granule size");
  guarantee(is_power_of_2(commit_granule_words()), "granule size must be a power of 2");

#ifdef ASSERT
  // Off for release builds, and by default for debug builds, but can be switched on manually to aid
  // error analysis.
  _use_allocation_guard = MetaspaceGuardAllocations;

  // Deallocations can be manually switched off to aid error analysis, since this removes one layer of complexity
  //  from allocation.
  _handle_deallocations = MetaspaceHandleDeallocations;

  // We also switch it off automatically if we use allocation guards. This is to keep prefix handling in MetaspaceArena simple.
  if (_use_allocation_guard) {
    _handle_deallocations = false;
  }
#endif

  LogStream ls(Log(metaspace)::info());
  Settings::print_on(&ls);

}

void Settings::print_on(outputStream* st) {

  st->print_cr(" - commit_granule_bytes: " SIZE_FORMAT ".", commit_granule_bytes());
  st->print_cr(" - commit_granule_words: " SIZE_FORMAT ".", commit_granule_words());

  st->print_cr(" - newborn_root_chunks_are_fully_committed: %d.", (int)newborn_root_chunks_are_fully_committed());

  st->print_cr(" - virtual_space_node_default_size: " SIZE_FORMAT ".", virtual_space_node_default_word_size());

  st->print_cr(" - enlarge_chunks_in_place: %d.", (int)enlarge_chunks_in_place());
  st->print_cr(" - enlarge_chunks_in_place_max_word_size: " SIZE_FORMAT ".", enlarge_chunks_in_place_max_word_size());

  st->print_cr(" - uncommit_free_chunks: %d.", (int)uncommit_free_chunks());

  st->print_cr(" - use_allocation_guard: %d.", (int)use_allocation_guard());
  st->print_cr(" - handle_deallocations: %d.", (int)handle_deallocations());

}

} // namespace metaspace

