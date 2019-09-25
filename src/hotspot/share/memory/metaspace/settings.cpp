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


#include <memory/metaspace/settings.hpp>
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
size_t Settings::_committed_words_on_fresh_chunks = 0;

bool Settings::_enlarge_chunks_in_place = false;
size_t Settings::_enlarge_chunks_in_place_max_word_size = 0;

bool Settings::_uncommit_on_return = false;
size_t Settings::_uncommit_on_return_min_word_size = 0;

bool Settings::_delete_nodes_on_purge = false;
bool Settings::_uncommit_on_purge = false;
size_t Settings::_uncommit_on_purge_min_word_size = 0;


bool Settings::_separate_micro_cld_allocations = false;

void Settings::ergo_initialize() {

  if (strcmp(MetaspaceReclaimStrategy, "none") == 0) {

    log_info(metaspace)("Initialized with strategy: no reclaim.");

    _commit_granule_bytes = MAX2((size_t)os::vm_page_size(), 64 * K);
    _commit_granule_words = _commit_granule_bytes / BytesPerWord;

    _newborn_root_chunks_are_fully_committed = true;

    _committed_words_on_fresh_chunks = chklvl::MAX_CHUNK_WORD_SIZE;

    _uncommit_on_return = false;
    _uncommit_on_return_min_word_size = 3; // does not matter; should not be used resp. assert when used.

    _delete_nodes_on_purge = false;
    _uncommit_on_purge = false;
    _uncommit_on_purge_min_word_size = 3; // does not matter; should not be used resp. assert when used.

  } else if (strcmp(MetaspaceReclaimStrategy, "aggressive") == 0) {

    log_info(metaspace)("Initialized with strategy: aggressive reclaim.");

    // Set the granule size rather small; may increase
    // mapping fragmentation but also increase chance to uncommit.
    _commit_granule_bytes = MAX2((size_t)os::vm_page_size(), 16 * K);
    _commit_granule_words = _commit_granule_bytes / BytesPerWord;

    _newborn_root_chunks_are_fully_committed = false;

    // When handing out fresh chunks, only commit the minimum sensible amount (0 would be possible
    // but not make sense since the chunk is immediately used for allocation after being handed out, so the
    // first granule would be committed right away anyway).
    _committed_words_on_fresh_chunks = _commit_granule_words;

    _uncommit_on_return = true;
    _uncommit_on_return_min_word_size = _commit_granule_words;

    _delete_nodes_on_purge = true;
    _uncommit_on_purge = true;
    _uncommit_on_purge_min_word_size = _commit_granule_words; // does not matter; should not be used resp. assert when used.

  } else if (strcmp(MetaspaceReclaimStrategy, "balanced") == 0) {

    log_info(metaspace)("Initialized with strategy: balanced reclaim.");

    _commit_granule_bytes = MAX2((size_t)os::vm_page_size(), 64 * K);
    _commit_granule_words = _commit_granule_bytes / BytesPerWord;

    _newborn_root_chunks_are_fully_committed = false;

    // When handing out fresh chunks, only commit the minimum sensible amount (0 would be possible
    // but not make sense since the chunk is immediately used for allocation after being handed out, so the
    // first granule would be committed right away anyway).
    _committed_words_on_fresh_chunks = _commit_granule_words;

    _uncommit_on_return = true;
    _uncommit_on_return_min_word_size = _commit_granule_words;

    _delete_nodes_on_purge = true;
    _uncommit_on_purge = true;
    _uncommit_on_purge_min_word_size = _commit_granule_words;

  } else {

    vm_exit_during_initialization("Invalid value for MetaspaceReclaimStrategy: \"%s\".", MetaspaceReclaimStrategy);

  }

  // Since this has nothing to do with reclaiming, set it independently from the
  // strategy. This is rather arbitrarily choosen.
  _enlarge_chunks_in_place = true;
  _enlarge_chunks_in_place_max_word_size = 256 * K;

  // Optionally, we can shepherd micro cld metaspace allocs to an own root chunk.
  _separate_micro_cld_allocations = MetaspaceSeparateMicroCLDs;

  // Sanity checks.
  guarantee(commit_granule_words() <= chklvl::MAX_CHUNK_WORD_SIZE, "Too large granule size");
  guarantee(is_power_of_2(commit_granule_words()), "granule size must be a power of 2");

  LogStream ls(Log(metaspace)::info());
  Settings::print_on(&ls);

}

void Settings::print_on(outputStream* st) {

  st->print_cr(" - commit_granule_bytes: " SIZE_FORMAT ".", commit_granule_bytes());
  st->print_cr(" - commit_granule_words: " SIZE_FORMAT ".", commit_granule_words());

  st->print_cr(" - newborn_root_chunks_are_fully_committed: %d.", (int)newborn_root_chunks_are_fully_committed());
  st->print_cr(" - committed_words_on_fresh_chunks: " SIZE_FORMAT ".", committed_words_on_fresh_chunks());

  st->print_cr(" - virtual_space_node_default_size: " SIZE_FORMAT ".", virtual_space_node_default_word_size());
  st->print_cr(" - allocation_from_dictionary_limit: " SIZE_FORMAT ".", allocation_from_dictionary_limit());

  st->print_cr(" - enlarge_chunks_in_place: %d.", (int)enlarge_chunks_in_place());
  st->print_cr(" - enlarge_chunks_in_place_max_word_size: " SIZE_FORMAT ".", enlarge_chunks_in_place_max_word_size());

  st->print_cr(" - uncommit_on_return: %d.", (int)uncommit_on_return());
  st->print_cr(" - uncommit_on_return_min_word_size: " SIZE_FORMAT ".", uncommit_on_return_min_word_size());

  st->print_cr(" - delete_nodes_on_purge: %d.", (int)delete_nodes_on_purge());

  st->print_cr(" - uncommit_on_purge: %d.", (int)uncommit_on_purge());
  st->print_cr(" - uncommit_on_purge_min_word_size: " SIZE_FORMAT ".", uncommit_on_purge_min_word_size());


}

} // namespace metaspace

