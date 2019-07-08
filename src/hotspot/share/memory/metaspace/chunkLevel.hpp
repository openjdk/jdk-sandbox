/*
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

#ifndef SHARE_MEMORY_METASPACE_CHUNKLEVEL_HPP
#define SHARE_MEMORY_METASPACE_CHUNKLEVEL_HPP

#include "utilities/globalDefinitions.hpp"

// Constants for the chunk levels and some utility functions.

namespace metaspace {

// Metachunk level
typedef int chklvl_t;

// Large enough to hold 99% of InstanceKlass.
static const size_t MIN_CHUNK_BYTE_SIZE    = 1 * K;

// Let MAX_CHUNK_SIZE be large enough to hold the largest possible InstanceKlass.
static const int NUM_CHUNK_LEVELS       = 13;
static const size_t MAX_CHUNK_BYTE_SIZE    = MIN_CHUNK_BYTE_SIZE << NUM_CHUNK_LEVELS;

static const size_t MIN_CHUNK_WORD_SIZE    = MIN_CHUNK_BYTE_SIZE / sizeof(MetaWord);
static const size_t MAX_CHUNK_WORD_SIZE    = MAX_CHUNK_BYTE_SIZE / sizeof(MetaWord);

static const chklvl_t HIGHEST_CHUNK_LEVEL = NUM_CHUNK_LEVELS - 1;
static const chklvl_t LOWEST_CHUNK_LEVEL = 0;

inline bool is_valid_level(chklvl_t level) {
  return level >= LOWEST_CHUNK_LEVEL && level <= HIGHEST_CHUNK_LEVEL;
}

#ifdef ASSERT
inline void check_valid_level(chklvl_t lvl) {
  assert(is_valid_level(lvl), "invalid level (%d)", (int)lvl);
}
#else
inline void check_valid_level(chklvl_t lvl) {}
#endif

// Given a level return the chunk size, in words.
inline size_t word_size_for_level(chklvl_t level) {
  assert(is_valid_level(level), "invalid chunk level (%d)", level);
  return MAX_CHUNK_WORD_SIZE << level;
}

// Given an arbitrary word size smaller than the highest chunk size,
// return the smallest chunk level able to hold this size.
chklvl_t level_fitting_word_size(size_t word_size);

// Shorthands to refer to exact sizes
const chklvl_t CHUNK_LEVEL_1K =    LOWEST_CHUNK_LEVEL;
const chklvl_t CHUNK_LEVEL_2K =    (LOWEST_CHUNK_LEVEL + 1);
const chklvl_t CHUNK_LEVEL_4K =    (LOWEST_CHUNK_LEVEL + 2);
const chklvl_t CHUNK_LEVEL_8K =    (LOWEST_CHUNK_LEVEL + 3);
const chklvl_t CHUNK_LEVEL_16K =   (LOWEST_CHUNK_LEVEL + 4);
const chklvl_t CHUNK_LEVEL_32K =   (LOWEST_CHUNK_LEVEL + 5);
const chklvl_t CHUNK_LEVEL_64K =   (LOWEST_CHUNK_LEVEL + 6);
const chklvl_t CHUNK_LEVEL_128K =  (LOWEST_CHUNK_LEVEL + 7);
const chklvl_t CHUNK_LEVEL_256K =  (LOWEST_CHUNK_LEVEL + 8);
const chklvl_t CHUNK_LEVEL_512K =  (LOWEST_CHUNK_LEVEL + 9);
const chklvl_t CHUNK_LEVEL_1M =    (LOWEST_CHUNK_LEVEL + 10);
const chklvl_t CHUNK_LEVEL_2M =    (LOWEST_CHUNK_LEVEL + 11);
const chklvl_t CHUNK_LEVEL_4M =    (LOWEST_CHUNK_LEVEL + 12);

STATIC_ASSERT(CHUNK_LEVEL_4M == HIGHEST_CHUNK_LEVEL);

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_BLOCKFREELIST_HPP
