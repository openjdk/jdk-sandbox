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

#ifndef SHARE_MEMORY_METASPACE_BLOCKFREELIST_HPP
#define SHARE_MEMORY_METASPACE_BLOCKFREELIST_HPP

#include "memory/allocation.hpp"

namespace metaspace {

class CommitLimit : public AllStatic {

  static size_t _committed;

public:

  // Attempt to increase committed size counters. Caller specifies a mininum expansion size and
  // a preferred one, in bytes.
  //
  // Before increasing the committed counters, function checks two limits:
  //  - the current GC threshold beyond which no expansion may happen without triggering a GC
  //  - MaxMetaspaceSize which limits the total sum of committed space.
  //
  // If increase is possible by either preferred_wordsize or at least min_wordsize, counters are
  // increased by that amount and the increase size is returned.
  //
  // Otherwise, 0 is returned.
  //
  // This function is used from outside the expansion lock. If caller owns expansion lock, use
  // attempt_increase_committed_locked() instead.
  static size_t attempt_increase_committed(size_t min_size, size_t preferred_size);

  // Decrease the commit counter by size bytes.
  static void decrease_committed(size_t size);

};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_BLOCKFREELIST_HPP
