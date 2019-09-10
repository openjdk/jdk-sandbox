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

#ifndef SHARE_MEMORY_METASPACE_CLASSLOADERMETASPACE_HPP
#define SHARE_MEMORY_METASPACE_CLASSLOADERMETASPACE_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace/metaspaceEnums.hpp"

class Mutex;

namespace metaspace {

class SpaceManager;
struct clms_stats_t;

class ClassLoaderMetaspace : public CHeapObj<mtClass> {

  // The CLD lock.
  Mutex* const _lock;

  const MetaspaceType _space_type;

  SpaceManager* _non_class_space_manager;
  SpaceManager* _class_space_manager;

  Mutex* lock() const                            { return _lock; }
  SpaceManager* non_class_space_manager() const  { return _non_class_space_manager; }
  SpaceManager* class_space_manager() const      { return _class_space_manager; }

  SpaceManager* get_space_manager(bool is_class) {
    return is_class ? class_space_manager() : non_class_space_manager();
  }

public:

  ClassLoaderMetaspace(Mutex* lock, MetaspaceType space_type);

  ~ClassLoaderMetaspace();

  MetaspaceType space_type() const { return _space_type; }

  // Allocate word_size words from Metaspace.
  MetaWord* allocate(size_t word_size, MetadataType mdType);

  // Attempt to expand the GC threshold to be good for at least another word_size words
  // and allocate. Returns NULL if failure. Used during Metaspace GC.
  MetaWord* expand_and_allocate(size_t word_size, MetadataType mdType);

  // Prematurely returns a metaspace allocation to the _block_freelists
  // because it is not needed anymore.
  void deallocate(MetaWord* ptr, size_t word_size, bool is_class);

  // Update statistics. This walks all in-use chunks.
  void add_to_statistics(clms_stats_t* out) const;

  DEBUG_ONLY(void verify(bool slow) const;)

  // TODO
  size_t allocated_blocks_bytes() const { return 0; }
  size_t allocated_chunks_bytes() const { return 0; }

};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_CLASSLOADERMETASPACE_HPP
