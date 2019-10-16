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

#include <memory/metaspace/settings.hpp>
#include "precompiled.hpp"

#include "aot/aotLoader.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/filemap.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/metaspaceTracer.hpp"
#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/classLoaderMetaspace.hpp"
#include "memory/metaspace/commitLimiter.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/metaspaceEnums.hpp"
#include "memory/metaspace/metaspaceReport.hpp"
#include "memory/metaspace/metaspaceSizesSnapshot.hpp"
#include "memory/metaspace/runningCounters.hpp"
#include "memory/metaspace/virtualSpaceList.hpp"
#include "memory/universe.hpp"
#include "oops/compressedOops.hpp"
#include "runtime/init.hpp"
#include "runtime/java.hpp"
#include "runtime/orderAccess.hpp"
#include "services/memTracker.hpp"
#include "utilities/copy.hpp"
#include "utilities/debug.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/globalDefinitions.hpp"


using metaspace::ChunkManager;
using metaspace::ClassLoaderMetaspace;
using metaspace::CommitLimiter;
using metaspace::MetaspaceType;
using metaspace::MetadataType;
using metaspace::MetaspaceReporter;
using metaspace::RunningCounters;
using metaspace::VirtualSpaceList;


// Used by MetaspaceCounters
size_t MetaspaceUtils::free_chunks_total_words(MetadataType mdtype) {
  return is_class(mdtype) ? RunningCounters::free_chunks_words_class() : RunningCounters::free_chunks_words_nonclass();
}

size_t MetaspaceUtils::used_words() {
  return RunningCounters::used_words();
}

size_t MetaspaceUtils::used_words(MetadataType mdtype) {
  return is_class(mdtype) ? RunningCounters::used_words_class() : RunningCounters::used_words_nonclass();
}

size_t MetaspaceUtils::reserved_words() {
  return RunningCounters::reserved_words();
}

size_t MetaspaceUtils::reserved_words(MetadataType mdtype) {
  return is_class(mdtype) ? RunningCounters::reserved_words_class() : RunningCounters::reserved_words_nonclass();
}

size_t MetaspaceUtils::committed_words() {
  return RunningCounters::committed_words();
}

size_t MetaspaceUtils::committed_words(MetadataType mdtype) {
  return is_class(mdtype) ? RunningCounters::committed_words_class() : RunningCounters::committed_words_nonclass();
}



void MetaspaceUtils::print_metaspace_change(const metaspace::MetaspaceSizesSnapshot& pre_meta_values) {
  const metaspace::MetaspaceSizesSnapshot meta_values;

  // We print used and committed since these are the most useful at-a-glance vitals for Metaspace:
  // - used tells you how much memory is actually used for metadata
  // - committed tells you how much memory is committed for the purpose of metadata
  // The difference between those two would be waste, which can have various forms (freelists,
  //   unused parts of committed chunks etc)
  //
  // Left out is reserved, since this is not as exciting as the first two values: for class space,
  // it is a constant (to uninformed users, often confusingly large). For non-class space, it would
  // be interesting since free chunks can be uncommitted, but for now it is left out.

  if (Metaspace::using_class_space()) {
    log_info(gc, metaspace)(HEAP_CHANGE_FORMAT" "
                            HEAP_CHANGE_FORMAT" "
                            HEAP_CHANGE_FORMAT,
                            HEAP_CHANGE_FORMAT_ARGS("Metaspace",
                                                    pre_meta_values.used(),
                                                    pre_meta_values.committed(),
                                                    meta_values.used(),
                                                    meta_values.committed()),
                            HEAP_CHANGE_FORMAT_ARGS("NonClass",
                                                    pre_meta_values.non_class_used(),
                                                    pre_meta_values.non_class_committed(),
                                                    meta_values.non_class_used(),
                                                    meta_values.non_class_committed()),
                            HEAP_CHANGE_FORMAT_ARGS("Class",
                                                    pre_meta_values.class_used(),
                                                    pre_meta_values.class_committed(),
                                                    meta_values.class_used(),
                                                    meta_values.class_committed()));
  } else {
    log_info(gc, metaspace)(HEAP_CHANGE_FORMAT,
                            HEAP_CHANGE_FORMAT_ARGS("Metaspace",
                                                    pre_meta_values.used(),
                                                    pre_meta_values.committed(),
                                                    meta_values.used(),
                                                    meta_values.committed()));
  }
}


// Prints an ASCII representation of the given space.
void MetaspaceUtils::print_metaspace_map(outputStream* out, MetadataType mdtype) {
  out->print_cr("-- not yet implemented ---");
}

// This will print out a basic metaspace usage report but
// unlike print_report() is guaranteed not to lock or to walk the CLDG.
void MetaspaceUtils::print_basic_report(outputStream* out, size_t scale) {
  MetaspaceReporter::print_basic_report(out, scale);
}

// Prints a report about the current metaspace state.
// Optional parts can be enabled via flags.
// Function will walk the CLDG and will lock the expand lock; if that is not
// convenient, use print_basic_report() instead.
void MetaspaceUtils::print_full_report(outputStream* out, size_t scale) {
  const int flags =
      MetaspaceReporter::rf_show_loaders |
      MetaspaceReporter::rf_break_down_by_chunktype |
      MetaspaceReporter::rf_show_classes;
  MetaspaceReporter::print_report(out, scale, flags);
}

void MetaspaceUtils::print_on(outputStream* out) {

  // Used from all GCs. It first prints out totals, then, separately, the class space portion.

  out->print_cr(" Metaspace       "
                "used "      SIZE_FORMAT "K, "
                "committed " SIZE_FORMAT "K, "
                "reserved "  SIZE_FORMAT "K",
                used_bytes()/K,
                committed_bytes()/K,
                reserved_bytes()/K);

  if (Metaspace::using_class_space()) {
    const MetadataType ct = metaspace::ClassType;
    out->print_cr("  class space    "
                  "used "      SIZE_FORMAT "K, "
                  "committed " SIZE_FORMAT "K, "
                  "reserved "  SIZE_FORMAT "K",
                  used_bytes(ct)/K,
                  committed_bytes(ct)/K,
                  reserved_bytes(ct)/K);
  }
}

#ifdef ASSERT
void MetaspaceUtils::verify(bool slow) {
  if (Metaspace::initialized()) {

    // Verify non-class chunkmanager...
    ChunkManager* cm = ChunkManager::chunkmanager_nonclass();
    cm->verify(slow);

    // ... and space list.
    VirtualSpaceList* vsl = VirtualSpaceList::vslist_nonclass();
    vsl->verify(slow);

    if (Metaspace::using_class_space()) {
      // If we use compressed class pointers, verify class chunkmanager...
      cm = ChunkManager::chunkmanager_class();
      assert(cm != NULL, "Sanity");
      cm->verify(slow);

      // ... and class spacelist.
      VirtualSpaceList* vsl = VirtualSpaceList::vslist_nonclass();
      assert(vsl != NULL, "Sanity");
      vsl->verify(slow);
    }

  }
}
#endif

////////////////////////////////7
// MetaspaceGC methods

volatile size_t MetaspaceGC::_capacity_until_GC = 0;
uint MetaspaceGC::_shrink_factor = 0;
bool MetaspaceGC::_should_concurrent_collect = false;

// VM_CollectForMetadataAllocation is the vm operation used to GC.
// Within the VM operation after the GC the attempt to allocate the metadata
// should succeed.  If the GC did not free enough space for the metaspace
// allocation, the HWM is increased so that another virtualspace will be
// allocated for the metadata.  With perm gen the increase in the perm
// gen had bounds, MinMetaspaceExpansion and MaxMetaspaceExpansion.  The
// metaspace policy uses those as the small and large steps for the HWM.
//
// After the GC the compute_new_size() for MetaspaceGC is called to
// resize the capacity of the metaspaces.  The current implementation
// is based on the flags MinMetaspaceFreeRatio and MaxMetaspaceFreeRatio used
// to resize the Java heap by some GC's.  New flags can be implemented
// if really needed.  MinMetaspaceFreeRatio is used to calculate how much
// free space is desirable in the metaspace capacity to decide how much
// to increase the HWM.  MaxMetaspaceFreeRatio is used to decide how much
// free space is desirable in the metaspace capacity before decreasing
// the HWM.

// Calculate the amount to increase the high water mark (HWM).
// Increase by a minimum amount (MinMetaspaceExpansion) so that
// another expansion is not requested too soon.  If that is not
// enough to satisfy the allocation, increase by MaxMetaspaceExpansion.
// If that is still not enough, expand by the size of the allocation
// plus some.
size_t MetaspaceGC::delta_capacity_until_GC(size_t bytes) {
  size_t min_delta = MinMetaspaceExpansion;
  size_t max_delta = MaxMetaspaceExpansion;
  size_t delta = align_up(bytes, Metaspace::commit_alignment());

  if (delta <= min_delta) {
    delta = min_delta;
  } else if (delta <= max_delta) {
    // Don't want to hit the high water mark on the next
    // allocation so make the delta greater than just enough
    // for this allocation.
    delta = max_delta;
  } else {
    // This allocation is large but the next ones are probably not
    // so increase by the minimum.
    delta = delta + min_delta;
  }

  assert_is_aligned(delta, Metaspace::commit_alignment());

  return delta;
}

size_t MetaspaceGC::capacity_until_GC() {
  size_t value = OrderAccess::load_acquire(&_capacity_until_GC);
  assert(value >= MetaspaceSize, "Not initialized properly?");
  return value;
}

// Try to increase the _capacity_until_GC limit counter by v bytes.
// Returns true if it succeeded. It may fail if either another thread
// concurrently increased the limit or the new limit would be larger
// than MaxMetaspaceSize.
// On success, optionally returns new and old metaspace capacity in
// new_cap_until_GC and old_cap_until_GC respectively.
// On error, optionally sets can_retry to indicate whether if there is
// actually enough space remaining to satisfy the request.
bool MetaspaceGC::inc_capacity_until_GC(size_t v, size_t* new_cap_until_GC, size_t* old_cap_until_GC, bool* can_retry) {
  assert_is_aligned(v, Metaspace::commit_alignment());

  size_t old_capacity_until_GC = _capacity_until_GC;
  size_t new_value = old_capacity_until_GC + v;

  if (new_value < old_capacity_until_GC) {
    // The addition wrapped around, set new_value to aligned max value.
    new_value = align_down(max_uintx, Metaspace::commit_alignment());
  }

  if (new_value > MaxMetaspaceSize) {
    if (can_retry != NULL) {
      *can_retry = false;
    }
    return false;
  }

  if (can_retry != NULL) {
    *can_retry = true;
  }
  size_t prev_value = Atomic::cmpxchg(new_value, &_capacity_until_GC, old_capacity_until_GC);

  if (old_capacity_until_GC != prev_value) {
    return false;
  }

  if (new_cap_until_GC != NULL) {
    *new_cap_until_GC = new_value;
  }
  if (old_cap_until_GC != NULL) {
    *old_cap_until_GC = old_capacity_until_GC;
  }
  return true;
}

size_t MetaspaceGC::dec_capacity_until_GC(size_t v) {
  assert_is_aligned(v, Metaspace::commit_alignment());

  return Atomic::sub(v, &_capacity_until_GC);
}

void MetaspaceGC::initialize() {
  // Set the high-water mark to MaxMetapaceSize during VM initializaton since
  // we can't do a GC during initialization.
  _capacity_until_GC = MaxMetaspaceSize;
}

void MetaspaceGC::post_initialize() {
  // Reset the high-water mark once the VM initialization is done.
  _capacity_until_GC = MAX2(MetaspaceUtils::committed_bytes(), MetaspaceSize);
}

bool MetaspaceGC::can_expand(size_t word_size, bool is_class) {
  // Check if the compressed class space is full.
  if (is_class && Metaspace::using_class_space()) {
    size_t class_committed = MetaspaceUtils::committed_bytes(metaspace::ClassType);
    if (class_committed + word_size * BytesPerWord > CompressedClassSpaceSize) {
      log_trace(gc, metaspace, freelist)("Cannot expand %s metaspace by " SIZE_FORMAT " words (CompressedClassSpaceSize = " SIZE_FORMAT " words)",
                (is_class ? "class" : "non-class"), word_size, CompressedClassSpaceSize / sizeof(MetaWord));
      return false;
    }
  }

  // Check if the user has imposed a limit on the metaspace memory.
  size_t committed_bytes = MetaspaceUtils::committed_bytes();
  if (committed_bytes + word_size * BytesPerWord > MaxMetaspaceSize) {
    log_trace(gc, metaspace, freelist)("Cannot expand %s metaspace by " SIZE_FORMAT " words (MaxMetaspaceSize = " SIZE_FORMAT " words)",
              (is_class ? "class" : "non-class"), word_size, MaxMetaspaceSize / sizeof(MetaWord));
    return false;
  }

  return true;
}

size_t MetaspaceGC::allowed_expansion() {
  size_t committed_bytes = MetaspaceUtils::committed_bytes();
  size_t capacity_until_gc = capacity_until_GC();

  assert(capacity_until_gc >= committed_bytes,
         "capacity_until_gc: " SIZE_FORMAT " < committed_bytes: " SIZE_FORMAT,
         capacity_until_gc, committed_bytes);

  size_t left_until_max  = MaxMetaspaceSize - committed_bytes;
  size_t left_until_GC = capacity_until_gc - committed_bytes;
  size_t left_to_commit = MIN2(left_until_GC, left_until_max);
  log_trace(gc, metaspace, freelist)("allowed expansion words: " SIZE_FORMAT
            " (left_until_max: " SIZE_FORMAT ", left_until_GC: " SIZE_FORMAT ".",
            left_to_commit / BytesPerWord, left_until_max / BytesPerWord, left_until_GC / BytesPerWord);

  return left_to_commit / BytesPerWord;
}

void MetaspaceGC::compute_new_size() {
  assert(_shrink_factor <= 100, "invalid shrink factor");
  uint current_shrink_factor = _shrink_factor;
  _shrink_factor = 0;

  // Using committed_bytes() for used_after_gc is an overestimation, since the
  // chunk free lists are included in committed_bytes() and the memory in an
  // un-fragmented chunk free list is available for future allocations.
  // However, if the chunk free lists becomes fragmented, then the memory may
  // not be available for future allocations and the memory is therefore "in use".
  // Including the chunk free lists in the definition of "in use" is therefore
  // necessary. Not including the chunk free lists can cause capacity_until_GC to
  // shrink below committed_bytes() and this has caused serious bugs in the past.
  const size_t used_after_gc = MetaspaceUtils::committed_bytes();
  const size_t capacity_until_GC = MetaspaceGC::capacity_until_GC();

  const double minimum_free_percentage = MinMetaspaceFreeRatio / 100.0;
  const double maximum_used_percentage = 1.0 - minimum_free_percentage;

  const double min_tmp = used_after_gc / maximum_used_percentage;
  size_t minimum_desired_capacity =
    (size_t)MIN2(min_tmp, double(MaxMetaspaceSize));
  // Don't shrink less than the initial generation size
  minimum_desired_capacity = MAX2(minimum_desired_capacity,
                                  MetaspaceSize);

  log_trace(gc, metaspace)("MetaspaceGC::compute_new_size: ");
  log_trace(gc, metaspace)("    minimum_free_percentage: %6.2f  maximum_used_percentage: %6.2f",
                           minimum_free_percentage, maximum_used_percentage);
  log_trace(gc, metaspace)("     used_after_gc       : %6.1fKB", used_after_gc / (double) K);


  size_t shrink_bytes = 0;
  if (capacity_until_GC < minimum_desired_capacity) {
    // If we have less capacity below the metaspace HWM, then
    // increment the HWM.
    size_t expand_bytes = minimum_desired_capacity - capacity_until_GC;
    expand_bytes = align_up(expand_bytes, Metaspace::commit_alignment());
    // Don't expand unless it's significant
    if (expand_bytes >= MinMetaspaceExpansion) {
      size_t new_capacity_until_GC = 0;
      bool succeeded = MetaspaceGC::inc_capacity_until_GC(expand_bytes, &new_capacity_until_GC);
      assert(succeeded, "Should always succesfully increment HWM when at safepoint");

      Metaspace::tracer()->report_gc_threshold(capacity_until_GC,
                                               new_capacity_until_GC,
                                               MetaspaceGCThresholdUpdater::ComputeNewSize);
      log_trace(gc, metaspace)("    expanding:  minimum_desired_capacity: %6.1fKB  expand_bytes: %6.1fKB  MinMetaspaceExpansion: %6.1fKB  new metaspace HWM:  %6.1fKB",
                               minimum_desired_capacity / (double) K,
                               expand_bytes / (double) K,
                               MinMetaspaceExpansion / (double) K,
                               new_capacity_until_GC / (double) K);
    }
    return;
  }

  // No expansion, now see if we want to shrink
  // We would never want to shrink more than this
  assert(capacity_until_GC >= minimum_desired_capacity,
         SIZE_FORMAT " >= " SIZE_FORMAT,
         capacity_until_GC, minimum_desired_capacity);
  size_t max_shrink_bytes = capacity_until_GC - minimum_desired_capacity;

  // Should shrinking be considered?
  if (MaxMetaspaceFreeRatio < 100) {
    const double maximum_free_percentage = MaxMetaspaceFreeRatio / 100.0;
    const double minimum_used_percentage = 1.0 - maximum_free_percentage;
    const double max_tmp = used_after_gc / minimum_used_percentage;
    size_t maximum_desired_capacity = (size_t)MIN2(max_tmp, double(MaxMetaspaceSize));
    maximum_desired_capacity = MAX2(maximum_desired_capacity,
                                    MetaspaceSize);
    log_trace(gc, metaspace)("    maximum_free_percentage: %6.2f  minimum_used_percentage: %6.2f",
                             maximum_free_percentage, minimum_used_percentage);
    log_trace(gc, metaspace)("    minimum_desired_capacity: %6.1fKB  maximum_desired_capacity: %6.1fKB",
                             minimum_desired_capacity / (double) K, maximum_desired_capacity / (double) K);

    assert(minimum_desired_capacity <= maximum_desired_capacity,
           "sanity check");

    if (capacity_until_GC > maximum_desired_capacity) {
      // Capacity too large, compute shrinking size
      shrink_bytes = capacity_until_GC - maximum_desired_capacity;
      // We don't want shrink all the way back to initSize if people call
      // System.gc(), because some programs do that between "phases" and then
      // we'd just have to grow the heap up again for the next phase.  So we
      // damp the shrinking: 0% on the first call, 10% on the second call, 40%
      // on the third call, and 100% by the fourth call.  But if we recompute
      // size without shrinking, it goes back to 0%.
      shrink_bytes = shrink_bytes / 100 * current_shrink_factor;

      shrink_bytes = align_down(shrink_bytes, Metaspace::commit_alignment());

      assert(shrink_bytes <= max_shrink_bytes,
             "invalid shrink size " SIZE_FORMAT " not <= " SIZE_FORMAT,
             shrink_bytes, max_shrink_bytes);
      if (current_shrink_factor == 0) {
        _shrink_factor = 10;
      } else {
        _shrink_factor = MIN2(current_shrink_factor * 4, (uint) 100);
      }
      log_trace(gc, metaspace)("    shrinking:  initThreshold: %.1fK  maximum_desired_capacity: %.1fK",
                               MetaspaceSize / (double) K, maximum_desired_capacity / (double) K);
      log_trace(gc, metaspace)("    shrink_bytes: %.1fK  current_shrink_factor: %d  new shrink factor: %d  MinMetaspaceExpansion: %.1fK",
                               shrink_bytes / (double) K, current_shrink_factor, _shrink_factor, MinMetaspaceExpansion / (double) K);
    }
  }

  // Don't shrink unless it's significant
  if (shrink_bytes >= MinMetaspaceExpansion &&
      ((capacity_until_GC - shrink_bytes) >= MetaspaceSize)) {
    size_t new_capacity_until_GC = MetaspaceGC::dec_capacity_until_GC(shrink_bytes);
    Metaspace::tracer()->report_gc_threshold(capacity_until_GC,
                                             new_capacity_until_GC,
                                             MetaspaceGCThresholdUpdater::ComputeNewSize);
  }
}



//////  Metaspace methods /////



MetaWord* Metaspace::_compressed_class_space_base = NULL;
size_t Metaspace::_compressed_class_space_size = 0;
const MetaspaceTracer* Metaspace::_tracer = NULL;
bool Metaspace::_initialized = false;
size_t Metaspace::_commit_alignment = 0;
size_t Metaspace::_reserve_alignment = 0;

DEBUG_ONLY(bool Metaspace::_frozen = false;)


#ifdef _LP64
static const uint64_t UnscaledClassSpaceMax = (uint64_t(max_juint) + 1);

void Metaspace::set_narrow_klass_base_and_shift(address metaspace_base, address cds_base) {
  assert(!DumpSharedSpaces, "narrow_klass is set by MetaspaceShared class.");
  // Figure out the narrow_klass_base and the narrow_klass_shift.  The
  // narrow_klass_base is the lower of the metaspace base and the cds base
  // (if cds is enabled).  The narrow_klass_shift depends on the distance
  // between the lower base and higher address.
  address lower_base;
  address higher_address;
#if INCLUDE_CDS
  if (UseSharedSpaces) {
    higher_address = MAX2((address)(cds_base + MetaspaceShared::core_spaces_size()),
                          (address)(metaspace_base + compressed_class_space_size()));
    lower_base = MIN2(metaspace_base, cds_base);
  } else
#endif
  {
    higher_address = metaspace_base + compressed_class_space_size();
    lower_base = metaspace_base;

    uint64_t klass_encoding_max = UnscaledClassSpaceMax << LogKlassAlignmentInBytes;
    // If compressed class space fits in lower 32G, we don't need a base.
    if (higher_address <= (address)klass_encoding_max) {
      lower_base = 0; // Effectively lower base is zero.
    }
  }

  // We must prevent any metaspace object from being allocated directly at
  // CompressedKlassPointers::base() - that would translate to a narrow Klass
  // pointer of 0, which has a special meaning (invalid) (Note: that was
  // never a problem in old metaspace, since every chunk was prefixed by its
  // header, so allocation at position 0 in a chunk was never possible).
  if (lower_base == metaspace_base) {
    lower_base -= os::vm_page_size();
  }

  CompressedKlassPointers::set_base(lower_base);

  // CDS uses LogKlassAlignmentInBytes for narrow_klass_shift. See
  // MetaspaceShared::initialize_dumptime_shared_and_meta_spaces() for
  // how dump time narrow_klass_shift is set. Although, CDS can work
  // with zero-shift mode also, to be consistent with AOT it uses
  // LogKlassAlignmentInBytes for klass shift so archived java heap objects
  // can be used at same time as AOT code.
  if (!UseSharedSpaces
      && (uint64_t)(higher_address - lower_base) <= UnscaledClassSpaceMax) {
    CompressedKlassPointers::set_shift(0);
  } else {
    CompressedKlassPointers::set_shift(LogKlassAlignmentInBytes);
  }
  AOTLoader::set_narrow_klass_shift();
}

#if INCLUDE_CDS
// Return TRUE if the specified metaspace_base and cds_base are close enough
// to work with compressed klass pointers.
bool Metaspace::can_use_cds_with_metaspace_addr(char* metaspace_base, address cds_base) {
  assert(cds_base != 0 && UseSharedSpaces, "Only use with CDS");
  assert(UseCompressedClassPointers, "Only use with CompressedKlassPtrs");
  address lower_base = MIN2((address)metaspace_base, cds_base);
  address higher_address = MAX2((address)(cds_base + MetaspaceShared::core_spaces_size()),
                                (address)(metaspace_base + compressed_class_space_size()));
  return ((uint64_t)(higher_address - lower_base) <= UnscaledClassSpaceMax);
}
#endif

// Try to allocate the metaspace at the requested addr.
void Metaspace::allocate_metaspace_compressed_klass_ptrs(char* requested_addr, address cds_base) {
  assert(!DumpSharedSpaces, "compress klass space is allocated by MetaspaceShared class.");
  assert(using_class_space(), "called improperly");
  assert(UseCompressedClassPointers, "Only use with CompressedKlassPtrs");
  assert(compressed_class_space_size() < KlassEncodingMetaspaceMax,
         "Metaspace size is too big");
  assert_is_aligned(requested_addr, _reserve_alignment);
  assert_is_aligned(cds_base, _reserve_alignment);
  assert_is_aligned(compressed_class_space_size(), _reserve_alignment);

  // Don't use large pages for the class space.
  bool large_pages = false;

#if !(defined(AARCH64) || defined(AIX))
  ReservedSpace rs = ReservedSpace(compressed_class_space_size(),
                                             _reserve_alignment,
                                             large_pages,
                                             requested_addr);
#else // AARCH64
  ReservedSpace rs;

  // Our compressed klass pointers may fit nicely into the lower 32
  // bits.
  if ((uint64_t)requested_addr + compressed_class_space_size() < 4*G) {
    rs = ReservedSpace(compressed_class_space_size(),
                                 _reserve_alignment,
                                 large_pages,
                                 requested_addr);
  }

  if (! rs.is_reserved()) {
    // Aarch64: Try to align metaspace so that we can decode a compressed
    // klass with a single MOVK instruction.  We can do this iff the
    // compressed class base is a multiple of 4G.
    // Aix: Search for a place where we can find memory. If we need to load
    // the base, 4G alignment is helpful, too.
    size_t increment = AARCH64_ONLY(4*)G;
    for (char *a = align_up(requested_addr, increment);
         a < (char*)(1024*G);
         a += increment) {
      if (a == (char *)(32*G)) {
        // Go faster from here on. Zero-based is no longer possible.
        increment = 4*G;
      }

#if INCLUDE_CDS
      if (UseSharedSpaces
          && ! can_use_cds_with_metaspace_addr(a, cds_base)) {
        // We failed to find an aligned base that will reach.  Fall
        // back to using our requested addr.
        rs = ReservedSpace(compressed_class_space_size(),
                                     _reserve_alignment,
                                     large_pages,
                                     requested_addr);
        break;
      }
#endif

      rs = ReservedSpace(compressed_class_space_size(),
                                   _reserve_alignment,
                                   large_pages,
                                   a);
      if (rs.is_reserved())
        break;
    }
  }

#endif // AARCH64

  if (!rs.is_reserved()) {
#if INCLUDE_CDS
    if (UseSharedSpaces) {
      size_t increment = align_up(1*G, _reserve_alignment);

      // Keep trying to allocate the metaspace, increasing the requested_addr
      // by 1GB each time, until we reach an address that will no longer allow
      // use of CDS with compressed klass pointers.
      char *addr = requested_addr;
      while (!rs.is_reserved() && (addr + increment > addr) &&
             can_use_cds_with_metaspace_addr(addr + increment, cds_base)) {
        addr = addr + increment;
        rs = ReservedSpace(compressed_class_space_size(),
                                     _reserve_alignment, large_pages, addr);
      }
    }
#endif
    // If no successful allocation then try to allocate the space anywhere.  If
    // that fails then OOM doom.  At this point we cannot try allocating the
    // metaspace as if UseCompressedClassPointers is off because too much
    // initialization has happened that depends on UseCompressedClassPointers.
    // So, UseCompressedClassPointers cannot be turned off at this point.
    if (!rs.is_reserved()) {
      rs = ReservedSpace(compressed_class_space_size(),
                                   _reserve_alignment, large_pages);
      if (!rs.is_reserved()) {
        vm_exit_during_initialization(err_msg("Could not allocate metaspace: " SIZE_FORMAT " bytes",
                                              compressed_class_space_size()));
      }
    }
  }

  // If we got here then the metaspace got allocated.
  MemTracker::record_virtual_memory_type((address)rs.base(), mtClass);

  _compressed_class_space_base = (MetaWord*)rs.base();

#if INCLUDE_CDS
  // Verify that we can use shared spaces.  Otherwise, turn off CDS.
  if (UseSharedSpaces && !can_use_cds_with_metaspace_addr(rs.base(), cds_base)) {
    FileMapInfo::stop_sharing_and_unmap(
        "Could not allocate metaspace at a compatible address");
  }
#endif
  set_narrow_klass_base_and_shift((address)rs.base(),
                                  UseSharedSpaces ? (address)cds_base : 0);

  initialize_class_space(rs);

  LogTarget(Trace, gc, metaspace) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    print_compressed_class_space(&ls, requested_addr);
  }
}

// For UseCompressedClassPointers the class space is reserved above the top of
// the Java heap.  The argument passed in is at the base of the compressed space.
void Metaspace::initialize_class_space(ReservedSpace rs) {

  // The reserved space size may be bigger because of alignment, esp with UseLargePages
  assert(rs.size() >= CompressedClassSpaceSize,
         SIZE_FORMAT " != " SIZE_FORMAT, rs.size(), CompressedClassSpaceSize);
  assert(using_class_space(), "Must be using class space");

  VirtualSpaceList* vsl = new VirtualSpaceList("class space list", rs, CommitLimiter::globalLimiter());
  VirtualSpaceList::set_vslist_class(vsl);
  ChunkManager* cm = new ChunkManager("class space chunk manager", vsl);
  ChunkManager::set_chunkmanager_class(cm);

}


void Metaspace::print_compressed_class_space(outputStream* st, const char* requested_addr) {
  st->print_cr("Narrow klass base: " PTR_FORMAT ", Narrow klass shift: %d",
               p2i(CompressedKlassPointers::base()), CompressedKlassPointers::shift());
  if (Metaspace::using_class_space()) {
    st->print("Compressed class space size: " SIZE_FORMAT " Address: " PTR_FORMAT,
                 compressed_class_space_size(), p2i(compressed_class_space_base()));
    if (requested_addr != 0) {
      st->print(" Req Addr: " PTR_FORMAT, p2i(requested_addr));
    }
    st->cr();
  }
}

#endif

void Metaspace::ergo_initialize() {

  // Must happen before using any setting from Settings::---
  metaspace::Settings::ergo_initialize();

  if (DumpSharedSpaces) {
    // Using large pages when dumping the shared archive is currently not implemented.
    FLAG_SET_ERGO(UseLargePagesInMetaspace, false);
  }

  size_t page_size = os::vm_page_size();
  if (UseLargePages && UseLargePagesInMetaspace) {
    page_size = os::large_page_size();
  }

  // Commit alignment: (I would rather hide this since this is an implementation detail but we need it
  // when calculating the gc threshold).
  _commit_alignment  = metaspace::Settings::commit_granule_bytes();

  // Reserve alignment: all Metaspace memory mappings are to be aligned to the size of a root chunk.
  _reserve_alignment = MAX2(page_size, (size_t)metaspace::chklvl::MAX_CHUNK_BYTE_SIZE);

  assert(is_aligned(_reserve_alignment, os::vm_allocation_granularity()),
         "root chunk size must be a multiple of alloc granularity");

  // Do not use FLAG_SET_ERGO to update MaxMetaspaceSize, since this will
  // override if MaxMetaspaceSize was set on the command line or not.
  // This information is needed later to conform to the specification of the
  // java.lang.management.MemoryUsage API.
  //
  // Ideally, we would be able to set the default value of MaxMetaspaceSize in
  // globals.hpp to the aligned value, but this is not possible, since the
  // alignment depends on other flags being parsed.
  MaxMetaspaceSize = align_down_bounded(MaxMetaspaceSize, _reserve_alignment);

  if (MetaspaceSize > MaxMetaspaceSize) {
    MetaspaceSize = MaxMetaspaceSize;
  }

  MetaspaceSize = align_down_bounded(MetaspaceSize, _commit_alignment);

  assert(MetaspaceSize <= MaxMetaspaceSize, "MetaspaceSize should be limited by MaxMetaspaceSize");

  MinMetaspaceExpansion = align_down_bounded(MinMetaspaceExpansion, _commit_alignment);
  MaxMetaspaceExpansion = align_down_bounded(MaxMetaspaceExpansion, _commit_alignment);

  CompressedClassSpaceSize = align_down_bounded(CompressedClassSpaceSize, _reserve_alignment);

  // Note: InitialBootClassLoaderMetaspaceSize is an old parameter which is used to determine the chunk size
  // of the first non-class chunk handed to the boot class loader. See metaspace/chunkAllocSequence.hpp.
  size_t min_metaspace_sz = align_up(InitialBootClassLoaderMetaspaceSize, _reserve_alignment);
  if (UseCompressedClassPointers) {
    if (min_metaspace_sz >= MaxMetaspaceSize) {
      vm_exit_during_initialization("MaxMetaspaceSize is too small.");
    } else if ((min_metaspace_sz + CompressedClassSpaceSize) >  MaxMetaspaceSize) {
      FLAG_SET_ERGO(CompressedClassSpaceSize, MaxMetaspaceSize - min_metaspace_sz);
    }
  } else if (min_metaspace_sz >= MaxMetaspaceSize) {
    FLAG_SET_ERGO(InitialBootClassLoaderMetaspaceSize,
                  min_metaspace_sz);
  }

  _compressed_class_space_size = CompressedClassSpaceSize;

}

void Metaspace::global_initialize() {
  MetaspaceGC::initialize(); // <- since we do not prealloc init chunks anymore is this still needed?

#if INCLUDE_CDS
  if (DumpSharedSpaces) {
    MetaspaceShared::initialize_dumptime_shared_and_meta_spaces();
  } else if (UseSharedSpaces) {
    // If any of the archived space fails to map, UseSharedSpaces
    // is reset to false. Fall through to the
    // (!DumpSharedSpaces && !UseSharedSpaces) case to set up class
    // metaspace.
    MetaspaceShared::initialize_runtime_shared_and_meta_spaces();
  }

  if (DynamicDumpSharedSpaces && !UseSharedSpaces) {
    vm_exit_during_initialization("DynamicDumpSharedSpaces is unsupported when base CDS archive is not loaded", NULL);
  }
#endif // INCLUDE_CDS

  // Initialize class space:
  if (CDS_ONLY(!DumpSharedSpaces && !UseSharedSpaces) NOT_CDS(true)) {
#ifdef _LP64
    if (using_class_space()) {
      char* base = (char*)align_up(CompressedOops::end(), _reserve_alignment);
      allocate_metaspace_compressed_klass_ptrs(base, 0);
    }
#endif // _LP64
  }

  // Initialize non-class virtual space list, and its chunk manager:
  VirtualSpaceList* vsl = new VirtualSpaceList("non-class virtualspacelist", CommitLimiter::globalLimiter());
  VirtualSpaceList::set_vslist_nonclass(vsl);
  ChunkManager* cm = new ChunkManager("non-class chunkmanager", vsl);
  ChunkManager::set_chunkmanager_nonclass(cm);

  _tracer = new MetaspaceTracer();

  _initialized = true;

}

void Metaspace::post_initialize() {
  MetaspaceGC::post_initialize();
}

MetaWord* Metaspace::allocate(ClassLoaderData* loader_data, size_t word_size,
                              MetaspaceObj::Type type, TRAPS) {
  assert(!_frozen, "sanity");
  assert(!(DumpSharedSpaces && THREAD->is_VM_thread()), "sanity");

  if (HAS_PENDING_EXCEPTION) {
    assert(false, "Should not allocate with exception pending");
    return NULL;  // caller does a CHECK_NULL too
  }

  assert(loader_data != NULL, "Should never pass around a NULL loader_data. "
        "ClassLoaderData::the_null_class_loader_data() should have been used.");

  MetadataType mdtype = (type == MetaspaceObj::ClassType) ? metaspace::ClassType : metaspace::NonClassType;

  // Try to allocate metadata.
  MetaWord* result = loader_data->metaspace_non_null()->allocate(word_size, mdtype);

  if (result == NULL) {
    tracer()->report_metaspace_allocation_failure(loader_data, word_size, type, mdtype);

    // Allocation failed.
    if (is_init_completed()) {
      // Only start a GC if the bootstrapping has completed.
      // Try to clean out some heap memory and retry. This can prevent premature
      // expansion of the metaspace.
      result = Universe::heap()->satisfy_failed_metadata_allocation(loader_data, word_size, mdtype);
    }
  }

  if (result == NULL) {
    if (DumpSharedSpaces) {
      // CDS dumping keeps loading classes, so if we hit an OOM we probably will keep hitting OOM.
      // We should abort to avoid generating a potentially bad archive.
      vm_exit_during_cds_dumping(err_msg("Failed allocating metaspace object type %s of size " SIZE_FORMAT ". CDS dump aborted.",
          MetaspaceObj::type_name(type), word_size * BytesPerWord),
        err_msg("Please increase MaxMetaspaceSize (currently " SIZE_FORMAT " bytes).", MaxMetaspaceSize));
    }
    report_metadata_oome(loader_data, word_size, type, mdtype, THREAD);
    assert(HAS_PENDING_EXCEPTION, "sanity");
    return NULL;
  }

  // Zero initialize.
  Copy::fill_to_words((HeapWord*)result, word_size, 0);

  log_trace(metaspace)("Metaspace::allocate: type %d return " PTR_FORMAT ".", (int)type, p2i(result));

  return result;
}

void Metaspace::report_metadata_oome(ClassLoaderData* loader_data, size_t word_size, MetaspaceObj::Type type, MetadataType mdtype, TRAPS) {
  tracer()->report_metadata_oom(loader_data, word_size, type, mdtype);

  // If result is still null, we are out of memory.
  Log(gc, metaspace, freelist, oom) log;
  if (log.is_info()) {
    log.info("Metaspace (%s) allocation failed for size " SIZE_FORMAT,
             is_class(mdtype) ? "class" : "data", word_size);
    ResourceMark rm;
    if (log.is_debug()) {
      if (loader_data->metaspace_or_null() != NULL) {
        LogStream ls(log.debug());
        loader_data->print_value_on(&ls);
      }
    }
    LogStream ls(log.info());
    // In case of an OOM, log out a short but still useful report.
    MetaspaceUtils::print_basic_report(&ls, 0);
  }

  // Which limit did we hit? CompressedClassSpaceSize or MaxMetaspaceSize?
  bool out_of_compressed_class_space = false;
  if (is_class(mdtype)) {
    ClassLoaderMetaspace* metaspace = loader_data->metaspace_non_null();
    out_of_compressed_class_space =
      MetaspaceUtils::committed_bytes(metaspace::ClassType) +
      // TODO: Okay this is just cheesy.
      // Of course this may fail and return incorrect results.
      // Think this over - we need some clean way to remember which limit
      // exactly we hit during an allocation. Some sort of allocation context structure?
      align_up(word_size * BytesPerWord, 4 * M) >
      CompressedClassSpaceSize;
  }

  // -XX:+HeapDumpOnOutOfMemoryError and -XX:OnOutOfMemoryError support
  const char* space_string = out_of_compressed_class_space ?
    "Compressed class space" : "Metaspace";

  report_java_out_of_memory(space_string);

  if (JvmtiExport::should_post_resource_exhausted()) {
    JvmtiExport::post_resource_exhausted(
        JVMTI_RESOURCE_EXHAUSTED_OOM_ERROR,
        space_string);
  }

  if (!is_init_completed()) {
    vm_exit_during_initialization("OutOfMemoryError", space_string);
  }

  if (out_of_compressed_class_space) {
    THROW_OOP(Universe::out_of_memory_error_class_metaspace());
  } else {
    THROW_OOP(Universe::out_of_memory_error_metaspace());
  }
}

void Metaspace::purge() {
  ChunkManager* cm = ChunkManager::chunkmanager_nonclass();
  if (cm != NULL) {
    cm->wholesale_reclaim();
  }
  if (using_class_space()) {
    cm = ChunkManager::chunkmanager_class();
    if (cm != NULL) {
      cm->wholesale_reclaim();
    }
  }
}

bool Metaspace::contains(const void* ptr) {
  if (MetaspaceShared::is_in_shared_metaspace(ptr)) {
    return true;
  }
  return contains_non_shared(ptr);
}

bool Metaspace::contains_non_shared(const void* ptr) {
  if (using_class_space() && VirtualSpaceList::vslist_class()->contains((MetaWord*)ptr)) {
     return true;
  }

  return VirtualSpaceList::vslist_nonclass()->contains((MetaWord*)ptr);
}
