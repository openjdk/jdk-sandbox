/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_MEMORY_METASPACE_HPP
#define SHARE_MEMORY_METASPACE_HPP

#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "memory/metaspace/metaspaceEnums.hpp"
#include "memory/metaspaceChunkFreeListSummary.hpp"
#include "memory/virtualspace.hpp"
#include "runtime/globals.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"

// Metaspace
//
// Metaspaces are Arenas for the VM's metadata.
// They are allocated one per class loader object, and one for the null
// bootstrap class loader
//
//    block X ---+       +-------------------+
//               |       |  Virtualspace     |
//               |       |                   |
//               |       |                   |
//               |       |-------------------|
//               |       || Chunk            |
//               |       ||                  |
//               |       ||----------        |
//               +------>||| block 0 |       |
//                       ||----------        |
//                       ||| block 1 |       |
//                       ||----------        |
//                       ||                  |
//                       |-------------------|
//                       |                   |
//                       |                   |
//                       +-------------------+
//

class ClassLoaderData;
class MetaspaceShared;
class MetaspaceTracer;
class outputStream;


namespace metaspace {
class MetaspaceSizesSnapshot;
}

////////////////// Metaspace ///////////////////////

// Metaspaces each have a  SpaceManager and allocations
// are done by the SpaceManager.  Allocations are done
// out of the current Metachunk.  When the current Metachunk
// is exhausted, the SpaceManager gets a new one from
// the current VirtualSpace.  When the VirtualSpace is exhausted
// the SpaceManager gets a new one.  The SpaceManager
// also manages freelists of available Chunks.
//
// Currently the space manager maintains the list of
// virtual spaces and the list of chunks in use.  Its
// allocate() method returns a block for use as a
// quantum of metadata.

// Namespace for important central static functions
// (auxiliary stuff goes into MetaspaceUtils)
class Metaspace : public AllStatic {

  friend class MetaspaceShared;

  // Base and size of the compressed class space.
  static MetaWord* _compressed_class_space_base;
  static size_t _compressed_class_space_size;

  static size_t _commit_alignment;
  static size_t _reserve_alignment;
  DEBUG_ONLY(static bool   _frozen;)

  static const MetaspaceTracer* _tracer;

  static bool _initialized;

  static MetaWord* compressed_class_space_base()              { return _compressed_class_space_base; }
  static size_t compressed_class_space_size()                 { return _compressed_class_space_size; }

public:

  static const MetaspaceTracer* tracer() { return _tracer; }
  static void freeze() {
    assert(DumpSharedSpaces, "sanity");
    DEBUG_ONLY(_frozen = true;)
  }
  static void assert_not_frozen() {
    assert(!_frozen, "sanity");
  }
#ifdef _LP64
  static void allocate_metaspace_compressed_klass_ptrs(char* requested_addr, address cds_base);
#endif

 private:

#ifdef _LP64
  static void set_narrow_klass_base_and_shift(address metaspace_base, address cds_base);

  // Returns true if can use CDS with metaspace allocated as specified address.
  static bool can_use_cds_with_metaspace_addr(char* metaspace_base, address cds_base);

  static void initialize_class_space(ReservedSpace rs);
#endif

 public:

  static void ergo_initialize();
  static void global_initialize();
  static void post_initialize();

  // The alignment at which Metaspace mappings are reserved.
  static size_t reserve_alignment()       { return _reserve_alignment; }
  static size_t reserve_alignment_words() { return _reserve_alignment / BytesPerWord; }

  // The granularity at which Metaspace is committed and uncommitted.
  static size_t commit_alignment()        { return _commit_alignment; }
  static size_t commit_words()            { return _commit_alignment / BytesPerWord; }

  static MetaWord* allocate(ClassLoaderData* loader_data, size_t word_size,
                            MetaspaceObj::Type type, TRAPS);

  static bool contains(const void* ptr);
  static bool contains_non_shared(const void* ptr);

  // Free empty virtualspaces
  static void purge();

  static void report_metadata_oome(ClassLoaderData* loader_data, size_t word_size,
                                   MetaspaceObj::Type type, metaspace::MetadataType mdtype, TRAPS);

  static void print_compressed_class_space(outputStream* st, const char* requested_addr = 0) NOT_LP64({});

  // Return TRUE only if UseCompressedClassPointers is True.
  static bool using_class_space() {
    return NOT_LP64(false) LP64_ONLY(UseCompressedClassPointers);
  }

  static bool initialized() { return _initialized; }

};

////////////////// MetaspaceGC ///////////////////////

// Metaspace are deallocated when their class loader are GC'ed.
// This class implements a policy for inducing GC's to recover
// Metaspaces.

class MetaspaceGCThresholdUpdater : public AllStatic {
 public:
  enum Type {
    ComputeNewSize,
    ExpandAndAllocate,
    Last
  };

  static const char* to_string(MetaspaceGCThresholdUpdater::Type updater) {
    switch (updater) {
      case ComputeNewSize:
        return "compute_new_size";
      case ExpandAndAllocate:
        return "expand_and_allocate";
      default:
        assert(false, "Got bad updater: %d", (int) updater);
        return NULL;
    };
  }
};

class MetaspaceGC : public AllStatic {

  // The current high-water-mark for inducing a GC.
  // When committed memory of all metaspaces reaches this value,
  // a GC is induced and the value is increased. Size is in bytes.
  static volatile size_t _capacity_until_GC;

  // For a CMS collection, signal that a concurrent collection should
  // be started.
  static bool _should_concurrent_collect;

  static uint _shrink_factor;

  static size_t shrink_factor() { return _shrink_factor; }
  void set_shrink_factor(uint v) { _shrink_factor = v; }

 public:

  static void initialize();
  static void post_initialize();

  static size_t capacity_until_GC();
  static bool inc_capacity_until_GC(size_t v,
                                    size_t* new_cap_until_GC = NULL,
                                    size_t* old_cap_until_GC = NULL,
                                    bool* can_retry = NULL);
  static size_t dec_capacity_until_GC(size_t v);

  static bool should_concurrent_collect() { return _should_concurrent_collect; }
  static void set_should_concurrent_collect(bool v) {
    _should_concurrent_collect = v;
  }

  // The amount to increase the high-water-mark (_capacity_until_GC)
  static size_t delta_capacity_until_GC(size_t bytes);

  // Tells if we have can expand metaspace without hitting set limits.
  static bool can_expand(size_t words, bool is_class);

  // Returns amount that we can expand without hitting a GC,
  // measured in words.
  static size_t allowed_expansion();

  // Calculate the new high-water mark at which to induce
  // a GC.
  static void compute_new_size();
};




class MetaspaceUtils : AllStatic {
public:

  // Committed space actually in use by Metadata
  static size_t used_words();
  static size_t used_words(metaspace::MetadataType mdtype);

  // Space committed for Metaspace
  static size_t committed_words();
  static size_t committed_words(metaspace::MetadataType mdtype);

  // Space reserved for Metaspace
  static size_t reserved_words();
  static size_t reserved_words(metaspace::MetadataType mdtype);

  // _bytes() variants for convenience...
  static size_t used_bytes()                                    { return used_words() * BytesPerWord; }
  static size_t used_bytes(metaspace::MetadataType mdtype)      { return used_words(mdtype) * BytesPerWord; }
  static size_t committed_bytes()                               { return committed_words() * BytesPerWord; }
  static size_t committed_bytes(metaspace::MetadataType mdtype) { return committed_words(mdtype) * BytesPerWord; }
  static size_t reserved_bytes()                                { return reserved_words() * BytesPerWord; }
  static size_t reserved_bytes(metaspace::MetadataType mdtype)  { return reserved_words(mdtype) * BytesPerWord; }

  // TODO. Do we need this really? This number is kind of uninformative.
  static size_t capacity_bytes()                                { return 0; }
  static size_t capacity_bytes(metaspace::MetadataType mdtype)  { return 0; }

  // Todo. Consolidate.
  // Committed space in freelists
  static size_t free_chunks_total_words(metaspace::MetadataType mdtype);

  // Todo. Implement or Consolidate.
  static MetaspaceChunkFreeListSummary chunk_free_list_summary(metaspace::MetadataType mdtype) {
    return MetaspaceChunkFreeListSummary(0,0,0,0,0,0,0,0);
  }

  // Log change in used metadata.
  static void print_metaspace_change(const metaspace::MetaspaceSizesSnapshot& pre_meta_values);

  // Prints an ASCII representation of the given space.
  static void print_metaspace_map(outputStream* out, metaspace::MetadataType mdtype);

  // This will print out a basic metaspace usage report but
  // unlike print_report() is guaranteed not to lock or to walk the CLDG.
  static void print_basic_report(outputStream* st, size_t scale = 0);

  // Prints a report about the current metaspace state.
  // Function will walk the CLDG and will lock the expand lock; if that is not
  // convenient, use print_basic_report() instead.
  static void print_full_report(outputStream* out, size_t scale = 0);

  static void print_on(outputStream * out);

  DEBUG_ONLY(static void verify(bool slow);)

};

#endif // SHARE_MEMORY_METASPACE_HPP
