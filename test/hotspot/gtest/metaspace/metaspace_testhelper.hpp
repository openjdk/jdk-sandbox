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

#ifndef GTEST_METASPACE_METASPACETESTHELPER_HPP
#define GTEST_METASPACE_METASPACETESTHELPER_HPP

#include "memory/allocation.hpp"
#include "metaspace/metaspaceTestsCommon.hpp"

using namespace metaspace::chunklevel;

class MetaspaceTestHelper : public StackObj {

  ReservedSpace _rs;
  CommitLimiter _commit_limiter;
  VirtualSpaceList _vs_list;
  ChunkManager _cm;
  int _num_chunks_allocated;

  enum expected_result_t { result_success = 0, result_failure = 1, result_unknown = 2 };
  Metachunk* checked_alloc_chunk_0(chunklevel_t preferred_level, chunklevel_t max_level,
                                   size_t min_committed_size, expected_result_t expected_result);

public:

  // No reserve limit, and a commit limit.
  MetaspaceTestHelper(size_t commit_limit);

  // Reserve limit and commit limit.
  MetaspaceTestHelper(size_t reserve_limit, size_t commit_limit);

  // Default ctor should cause no limits to fire (within reason)
  MetaspaceTestHelper ();

  ~MetaspaceTestHelper();

  const CommitLimiter& commit_limiter() const { return _commit_limiter; }
  const VirtualSpaceList& vslist() const { return _vs_list; }
  ChunkManager& cm() { return _cm; }

  // Returns reserve- and commit limit we run the test with (in the real world,
  // these would be equivalent to CompressedClassSpaceSize resp MaxMetaspaceSize)
  size_t reserve_limit() const { return _rs.is_reserved() ? _rs.size() : max_uintx; }
  size_t commit_limit() const { return _commit_limiter.cap(); }

  /////

  // Allocate a chunk; do not expect success, but if it succeeds, test the chunk.
  Metachunk* alloc_chunk(chunklevel_t preferred_level, chunklevel_t max_level, size_t min_committed_size) {
    return checked_alloc_chunk_0(preferred_level, max_level, min_committed_size, result_unknown);
  }

  // Allocate a chunk; do not expect success, but if it succeeds, test the chunk.
  Metachunk* alloc_chunk(chunklevel_t level) {
    return alloc_chunk(level, level, word_size_for_level(level));
  }

  // Allocate a chunk; it must succeed. Test the chunk.
  Metachunk* alloc_chunk_expect_success(chunklevel_t preferred_level, chunklevel_t max_level, size_t min_committed_size) {
    return checked_alloc_chunk_0(preferred_level, max_level, min_committed_size, result_success);
  }

  // Allocate a chunk; it must succeed. Test the chunk.
  Metachunk* alloc_chunk_expect_success(chunklevel_t level) {
    return alloc_chunk_expect_success(level, level, word_size_for_level(level));
  }

  // Allocate a chunk but expect it to fail.
  void alloc_chunk_expect_failure(chunklevel_t preferred_level, chunklevel_t max_level, size_t min_committed_size) {
    checked_alloc_chunk_0(preferred_level, max_level, min_committed_size, result_failure);
  }

  // Allocate a chunk but expect it to fail.
  void alloc_chunk_expect_failure(chunklevel_t level) {
    return alloc_chunk_expect_failure(level, level, word_size_for_level(level));
  }

  /////

  void return_chunk(Metachunk* c);

  /////

  // Allocates from a chunk; also, fills allocated area with test pattern which will be tested with test_pattern().
  MetaWord* allocate_from_chunk(Metachunk* c, size_t word_size);

  // Test pattern established when allocating from the chunk with allocate_from_chunk_with_tests().
  void test_pattern(Metachunk* c, size_t word_size);
  void test_pattern(Metachunk* c) { test_pattern(c, c->used_words()); }

  void commit_chunk_with_test(Metachunk* c, size_t additional_size);
  void commit_chunk_expect_failure(Metachunk* c, size_t additional_size);

  void uncommit_chunk_with_test(Metachunk* c);

  DEBUG_ONLY(void verify() const;)

};


#endif // GTEST_METASPACE_METASPACETESTHELPER_HPP


