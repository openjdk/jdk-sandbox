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
 */

#ifndef GTEST_METASPACE_METASPACETESTCOMMON_HPP

#include "memory/allocation.hpp"

#include "memory/metaspace/chunkHeaderPool.hpp"
#include "memory/metaspace/chunkLevel.hpp"
#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/counter.hpp"
#include "memory/metaspace/commitLimiter.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/metaspaceStatistics.hpp"
#include "memory/metaspace/virtualSpaceList.hpp"
#include "memory/metaspace/spaceManager.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"

#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

#include "unittest.hpp"

#include <stdio.h>


//////////////////////////////////////////////////////////
// handy aliases

using metaspace::ChunkAllocSequence;
using metaspace::ChunkHeaderPool;
using metaspace::ChunkManager;
using metaspace::CommitLimiter;
using metaspace::CommitMask;
using metaspace::SizeCounter;
using metaspace::SizeAtomicCounter;
using metaspace::IntCounter;
using metaspace::Metachunk;
using metaspace::MetachunkList;
using metaspace::MetachunkListCluster;
using metaspace::Settings;
using metaspace::sm_stats_t;
using metaspace::in_use_chunk_stats_t;
using metaspace::cm_stats_t;
using metaspace::SizeCounter;
using metaspace::SpaceManager;
using metaspace::VirtualSpaceList;
using metaspace::VirtualSpaceNode;

using metaspace::chklvl_t;
using metaspace::chklvl::HIGHEST_CHUNK_LEVEL;
using metaspace::chklvl::MAX_CHUNK_WORD_SIZE;
using metaspace::chklvl::MAX_CHUNK_BYTE_SIZE;
using metaspace::chklvl::LOWEST_CHUNK_LEVEL;
using metaspace::chklvl::NUM_CHUNK_LEVELS;


/////////////////////////////////////////////////////////////////////
// A little mockup to mimick and test the CommitMask in various tests

class TestMap {
  const size_t _len;
  char* _arr;
public:
  TestMap(size_t len) : _len(len), _arr(NULL) {
    _arr = NEW_C_HEAP_ARRAY(char, len, mtInternal);
    memset(_arr, 0, _len);
  }
  ~TestMap() { FREE_C_HEAP_ARRAY(char, _arr); }

  int get_num_set(size_t from, size_t to) const {
    int result = 0;
    for(size_t i = from; i < to; i ++) {
      if (_arr[i] > 0) {
        result ++;
      }
    }
    return result;
  }

  size_t get_num_set() const { return get_num_set(0, _len); }

  void set_range(size_t from, size_t to) {
    memset(_arr + from, 1, to - from);
  }

  void clear_range(size_t from, size_t to) {
    memset(_arr + from, 0, to - from);
  }

};



///////////////////////////////////////////////////////////////
// Functions to calculate random ranges in outer ranges

// Note: [ from..to )
struct range_t {
  size_t from; size_t to;
};

void calc_random_range(size_t outer_range_len, range_t* out, size_t alignment);

// Note:  [ p..p+word_size )
struct address_range_t {
  MetaWord* p; size_t word_size;
};

void calc_random_address_range(const address_range_t* outer_range, address_range_t* out, size_t alignment);


///////////////////////////////////////////////////////////
// Helper class for generating random allocation sizes
class RandSizeGenerator {
  const size_t _min; // [
  const size_t _max; // )
  const float _outlier_chance; // 0.0 -- 1.0
  const size_t _outlier_min; // [
  const size_t _outlier_max; // )
public:
  RandSizeGenerator(size_t min, size_t max)
    : _min(min), _max(max), _outlier_chance(0.0), _outlier_min(min), _outlier_max(max)
  {}

  RandSizeGenerator(size_t min, size_t max, float outlier_chance, size_t outlier_min, size_t outlier_max)
    : _min(min), _max(max), _outlier_chance(outlier_chance), _outlier_min(outlier_min), _outlier_max(outlier_max)
  {}

  size_t get() const {
    size_t l1 = _min;
    size_t l2 = _max;
    int r = os::random() % 1000;
    if ((float)r < _outlier_chance * 1000.0) {
      l1 = _outlier_min;
      l2 = _outlier_max;
    }
    const size_t d = l2 - l1;
    return l1 + (os::random() % d);
  }

}; // end RandSizeGenerator


///////////////////////////////////////////////////////////
// Function to test-access a memory range

void zap_range(MetaWord* p, size_t word_size);

// "fill_range_with_pattern" fills a range of heap words with pointers to itself.
//
// The idea is to fill a memory range with a pattern which is both marked clearly to the caller
// and cannot be moved without becoming invalid.
//
// The filled range can be checked with check_range_for_pattern. One also can only check
// a sub range of the original range.
void fill_range_with_pattern(MetaWord* p, uintx pattern, size_t word_size);
bool check_range_for_pattern(const MetaWord* p, uintx pattern, size_t word_size);

// Writes a uniqe pattern to p
void mark_address(MetaWord* p, uintx pattern);
// checks pattern at address
bool check_marked_address(const MetaWord* p, uintx pattern);

// Similar to fill_range_with_pattern, but only marks start and end. This is optimized for cases
// where fill_range_with_pattern just is too slow.
// Use check_marked_range to check the range. In contrast to check_range_for_pattern, only the original
// range can be checked.
void mark_range(MetaWord* p, uintx pattern, size_t word_size);
bool check_marked_range(const MetaWord* p, uintx pattern, size_t word_size);

//////////////////////////////////////////////////////////
// Some helpers to avoid typing out those annoying casts for NULL

#define ASSERT_NOT_NULL(ptr)      ASSERT_NE((void*)NULL, (void*)ptr)
#define ASSERT_NULL(ptr)          ASSERT_EQ((void*)NULL, (void*)ptr)
#define EXPECT_NOT_NULL(ptr)      EXPECT_NE((void*)NULL, (void*)ptr)
#define EXPECT_NULL(ptr)          EXPECT_EQ((void*)NULL, (void*)ptr)


//////////////////////////////////////////////////////////
// logging

// Define "LOG_PLEASE" to switch on logging for a particular test before inclusion of this header.
#ifdef LOG_PLEASE
  #define LOG(...) { printf(__VA_ARGS__); printf("\n"); }
#else
  #define LOG(...)
#endif

//////////////////////////////////////////////////////////
// Helper

size_t get_workingset_size();


#endif // GTEST_METASPACE_METASPACETESTCOMMON_HPP
