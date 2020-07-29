/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, 2019 SAP SE. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_METASPACE_TEST_HPP
#define SHARE_MEMORY_METASPACE_METASPACE_TEST_HPP


#include "memory/allocation.hpp"
#include "memory/metaspace/commitLimiter.hpp"
#include "memory/metaspace/counter.hpp"
#include "memory/metaspace/metaspaceEnums.hpp"
#include "memory/virtualspace.hpp"
#include "utilities/globalDefinitions.hpp"

// Convenience classes for metaspace tests (jtreg, via whitebox, and gtests)

class ReservedSpace;
class Mutex;
class outputStream;

namespace metaspace {

class VirtualSpaceList;
class ChunkManager;
class MetaspaceArena;


// Wraps a MetaspaceTestArena with its own lock for testing purposes.
class MetaspaceTestArena : public CHeapObj<mtInternal> {

  Mutex* const _lock;
  MetaspaceArena* const _arena;

public:

  const MetaspaceArena* arena() const {
    return _arena;
  }

  MetaspaceTestArena(Mutex* lock, MetaspaceArena* arena);
  ~MetaspaceTestArena();

  MetaWord* allocate(size_t word_size);
  void deallocate(MetaWord* p, size_t word_size);

};


// Wraps one instance of the global objects - VirtualSpaceList with underlying ReservedSpace,
// its limiter, and the associated ChunkManager.
class MetaspaceTestContext : public CHeapObj<mtInternal> {

  const char* _name;
  CommitLimiter _commit_limiter;
  ReservedSpace _rs;
  VirtualSpaceList* _vslist;
  ChunkManager* _cm;
  SizeAtomicCounter _used_words_counter;

public:

  // Note: limit == 0 means unlimited
  // Reserve limit > 0 simulates a non-expandable VirtualSpaceList (like CompressedClassSpace)
  // Commit limit > 0 simulates a limit to max commitable space (like MaxMetaspaceSize)
  MetaspaceTestContext(const char* name, size_t commit_limit = 0, size_t reserve_limit = 0);
  ~MetaspaceTestContext();

  // Create an arena, feeding off this area.
  MetaspaceTestArena* create_arena(MetaspaceType type);

  void purge_area();

  // Accessors
  const CommitLimiter& commit_limiter() const { return _commit_limiter; }
  const VirtualSpaceList& vslist() const      { return *_vslist; }
  ChunkManager& cm()                          { return *_cm; }

  // Returns reserve- and commit limit we run the test with (in the real world,
  // these would be equivalent to CompressedClassSpaceSize resp MaxMetaspaceSize)
  size_t reserve_limit() const { return _rs.is_reserved() ? _rs.size() : max_uintx; }
  size_t commit_limit() const { return _commit_limiter.cap(); }

  // Convenience function to retrieve total committed/used words
  size_t used_words() const       { return _used_words_counter.get(); }
  size_t committed_words() const  { return _commit_limiter.committed_words(); }

  DEBUG_ONLY(void verify(bool slow = false) const;)

  void print_on(outputStream* st) const;

};


} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_METASPACE_TEST_HPP

