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
#include "precompiled.hpp"
#include "memory/metaspace/chunkHeaderPool.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

namespace metaspace {


// Returns reference to the one global chunk header pool.
ChunkHeaderPool ChunkHeaderPool::_chunkHeaderPool(false);


ChunkHeaderPool::ChunkHeaderPool(bool delete_on_destruction)
  : _num_slabs(), _first_slab(NULL), _current_slab(NULL), _delete_on_destruction(delete_on_destruction)
{
}

ChunkHeaderPool::~ChunkHeaderPool() {
  if (_delete_on_destruction) {
    // This is only done when tests are running, but not for the global chunk pool,
    // since that is supposed to live until the process ends.
    slab_t* s = _first_slab;
    while (s != NULL) {
      slab_t* next_slab = s->next;
      os::free(s);
      s = next_slab;
    }
  }
}

void ChunkHeaderPool::allocate_new_slab() {
  slab_t* slab = (slab_t*)os::malloc(sizeof(slab_t), mtInternal);
  memset(slab, 0, sizeof(slab_t));
  if (_current_slab != NULL) {
    _current_slab->next = slab;
  }
  _current_slab = slab;
  if (_first_slab == NULL) {
    _first_slab = slab;
  }
  _num_slabs.increment();
}

// Returns size of memory used.
size_t ChunkHeaderPool::memory_footprint_words() const {
  return (_num_slabs.get() * sizeof(slab_t)) / BytesPerWord;
}

#ifdef ASSERT
void ChunkHeaderPool::verify(bool slow) const {
  const slab_t* s = _first_slab;
  int num = 0;
  while (s != NULL) {
    assert(s->top >= 0 && s->top <= slab_capacity,
           "invalid slab at " PTR_FORMAT ", top: %d, slab cap: %d",
           p2i(s), s->top, slab_capacity );
    s = s->next;
    num ++;
  }
  _num_slabs.check(num);
}
#endif

} // namespace metaspace


