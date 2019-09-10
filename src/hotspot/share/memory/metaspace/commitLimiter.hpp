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

#ifndef SHARE_MEMORY_METASPACE_COMMITLIMITER_HPP
#define SHARE_MEMORY_METASPACE_COMMITLIMITER_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace/counter.hpp"

namespace metaspace {

class CommitLimiter : public CHeapObj<mtInternal> {

  // Counts total words committed for metaspace
  SizeCounter _cnt;

  // Purely for testing purposes: cap, in words.
  const size_t _cap;

public:

  // Create a commit limiter. This is only useful for testing, with a cap != 0,
  // since normal code should use the global commit limiter.
  // If cap != 0 (word size), the cap replaces the internal logic of limiting.
  CommitLimiter(size_t cap = 0) : _cnt(), _cap(cap) {}

  // Returns the size, in words, by which we may expand the metaspace committed area without:
  // - _cap == 0: hitting GC threshold or the MaxMetaspaceSize
  // - _cap > 0: hitting cap (this is just for testing purposes)
  size_t possible_expansion_words() const;

  void increase_committed(size_t word_size)   { _cnt.increment_by(word_size); }
  void decrease_committed(size_t word_size)   { _cnt.decrement_by(word_size); }
  size_t committed_words() const              { return _cnt.get(); }

  // Returns the global metaspace commit counter
  static CommitLimiter* globalLimiter();

};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_COMMITLIMITER_HPP
