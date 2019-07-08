/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/metaDebug.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/occupancyMap.hpp"
#include "memory/metaspace/virtualSpaceNode.hpp"
#include "memory/virtualspace.hpp"
#include "runtime/os.hpp"
#include "services/memTracker.hpp"
#include "utilities/copy.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

namespace metaspace {


// Create a new empty node of the given size. Memory will be reserved but
// completely uncommitted.
VirtualSpaceNode::VirtualSpaceNode(size_t wordsize)
  : _next(NULL)
  , _rs()
  , _virtual_space()
  , _top(NULL)
{
}

// Create a new empty node spanning the given reserved space.
VirtualSpaceNode::VirtualSpaceNode(ReservedSpace rs)
  : _next(NULL)
  , _rs(rs)
  , _virtual_space()
  , _top(NULL)
{}









/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// Decide if large pages should be committed when the memory is reserved.
static bool should_commit_large_pages_when_reserving(size_t bytes) {
  if (UseLargePages && UseLargePagesInMetaspace && !os::can_commit_large_page_memory()) {
    size_t words = bytes / BytesPerWord;
    bool is_class = false; // We never reserve large pages for the class space.
    if (MetaspaceGC::can_expand(words, is_class) &&
        MetaspaceGC::allowed_expansion() >= words) {
      return true;
    }
  }

  return false;
}


// byte_size is the size of the associated virtualspace.
VirtualSpaceNode::VirtualSpaceNode(size_t bytes) :
    _next(NULL), _rs(), _top(NULL) {
  assert_is_aligned(bytes, Metaspace::reserve_alignment());
  bool large_pages = should_commit_large_pages_when_reserving(bytes);
  _rs = ReservedSpace(bytes, Metaspace::reserve_alignment(), large_pages);

  if (_rs.is_reserved()) {
    assert(_rs.base() != NULL, "Catch if we get a NULL address");
    assert(_rs.size() != 0, "Catch if we get a 0 size");
    assert_is_aligned(_rs.base(), Metaspace::reserve_alignment());
    assert_is_aligned(_rs.size(), Metaspace::reserve_alignment());

    MemTracker::record_virtual_memory_type((address)_rs.base(), mtClass);
  }
}

VirtualSpaceNode::VirtualSpaceNode(ReservedSpace rs) : _next(NULL), _rs(rs), _top(NULL) {}

// Checks if the node can be purged.
// This iterates through the chunks and checks if all are free.
// This should be quite fast since if all chunks are free they should have been crystallized to 1-2 root chunks
// (a non-class node is only a few MB itself). For class space, it makes no sense to call this since it cannot
// be purged anyway.
bool VirtualSpaceNode::purgable() const {
  Metachunk* chunk = first_chunk();
  Metachunk* invalid_chunk = (Metachunk*) top();
  while (chunk < invalid_chunk ) {
    if (chunk->is_free() == false) {
      return false;
    }
    MetaWord* next = ((MetaWord*)chunk) + chunk->word_size();
    chunk = (Metachunk*) next;
  }
  return true;
}

void VirtualSpaceNode::purge(ChunkManager* chunk_manager) {
  // When a node is purged, lets give it a thorough examination.
  DEBUG_ONLY(verify(true);)
  Metachunk* chunk = first_chunk();
  Metachunk* invalid_chunk = (Metachunk*) top();
  while (chunk < invalid_chunk ) {
    assert(chunk->is_free(), "Should be free");
    MetaWord* next = ((MetaWord*)chunk) + chunk->word_size();
    chunk_manager->remove_chunk(chunk);
    DEBUG_ONLY(chunk->remove_sentinel();)
    assert(chunk->next() == NULL &&
        chunk->prev() == NULL,
        "Was not removed from its list");
    chunk = (Metachunk*) next;
  }
}

void VirtualSpaceNode::print_map(outputStream* st, bool is_class) const {
/*
  if (bottom() == top()) {
    return;
  }

  const size_t spec_chunk_size = is_class ? ClassSpecializedChunk : SpecializedChunk;
  const size_t small_chunk_size = is_class ? ClassSmallChunk : SmallChunk;
  const size_t med_chunk_size = is_class ? ClassMediumChunk : MediumChunk;

  int line_len = 100;
  const size_t section_len = align_up(spec_chunk_size * line_len, med_chunk_size);
  line_len = (int)(section_len / spec_chunk_size);

  static const int NUM_LINES = 4;

  char* lines[NUM_LINES];
  for (int i = 0; i < NUM_LINES; i ++) {
    lines[i] = (char*)os::malloc(line_len, mtInternal);
  }
  int pos = 0;
  const MetaWord* p = bottom();
  const Metachunk* chunk = (const Metachunk*)p;
  const MetaWord* chunk_end = p + chunk->word_size();
  while (p < top()) {
    if (pos == line_len) {
      pos = 0;
      for (int i = 0; i < NUM_LINES; i ++) {
        st->fill_to(22);
        st->print_raw(lines[i], line_len);
        st->cr();
      }
    }
    if (pos == 0) {
      st->print(PTR_FORMAT ":", p2i(p));
    }
    if (p == chunk_end) {
      chunk = (Metachunk*)p;
      chunk_end = p + chunk->word_size();
    }
    // line 1: chunk starting points (a dot if that area is a chunk start).
    lines[0][pos] = p == (const MetaWord*)chunk ? '.' : ' ';

    // Line 2: chunk type (x=spec, s=small, m=medium, h=humongous), uppercase if
    // chunk is in use.
    const bool chunk_is_free = ((Metachunk*)chunk)->is_tagged_free();
    if (chunk->word_size() == spec_chunk_size) {
      lines[1][pos] = chunk_is_free ? 'x' : 'X';
    } else if (chunk->word_size() == small_chunk_size) {
      lines[1][pos] = chunk_is_free ? 's' : 'S';
    } else if (chunk->word_size() == med_chunk_size) {
      lines[1][pos] = chunk_is_free ? 'm' : 'M';
    } else if (chunk->word_size() > med_chunk_size) {
      lines[1][pos] = chunk_is_free ? 'h' : 'H';
    } else {
      ShouldNotReachHere();
    }

    // Line 3: chunk origin
    const ChunkOrigin origin = chunk->get_origin();
    lines[2][pos] = origin == origin_normal ? ' ' : '0' + (int) origin;

    // Line 4: Virgin chunk? Virgin chunks are chunks created as a byproduct of padding or splitting,
    //         but were never used.
    lines[3][pos] = chunk->get_use_count() > 0 ? ' ' : 'v';

    p += spec_chunk_size;
    pos ++;
  }
  if (pos > 0) {
    for (int i = 0; i < NUM_LINES; i ++) {
      st->fill_to(22);
      st->print_raw(lines[i], line_len);
      st->cr();
    }
  }
  for (int i = 0; i < NUM_LINES; i ++) {
    os::free(lines[i]);
  }*/
}


#ifdef ASSERT

// Verify counters, all chunks in this list node and the occupancy map.
void VirtualSpaceNode::verify(bool slow) {
  log_trace(gc, metaspace, freelist)("verifying %s virtual space node (%s).",
    (is_class() ? "class space" : "metaspace"), (slow ? "slow" : "quick"));
  // Fast mode: just verify chunk counters and basic geometry
  // Slow mode: verify chunks and occupancy map
  uintx num_in_use_chunks = 0;
  Metachunk* chunk = first_chunk();
  Metachunk* invalid_chunk = (Metachunk*) top();

  // Iterate the chunks in this node and verify each chunk.
  while (chunk < invalid_chunk ) {
    if (slow) {
      do_verify_chunk(chunk);
    }
    if (!chunk->is_tagged_free()) {
      num_in_use_chunks ++;
    }
    const size_t s = chunk->word_size();
    // Prevent endless loop on invalid chunk size.
    assert(is_valid_chunksize(is_class(), s), "Invalid chunk size: " SIZE_FORMAT ".", s);
    MetaWord* next = ((MetaWord*)chunk) + s;
    chunk = (Metachunk*) next;
  }
  assert(_container_count == num_in_use_chunks, "Container count mismatch (real: " UINTX_FORMAT
      ", counter: " UINTX_FORMAT ".", num_in_use_chunks, _container_count);
  // Also verify the occupancy map.
  if (slow) {
    occupancy_map()->verify(bottom(), top());
  }
}

// Verify that all free chunks in this node are ideally merged
// (there not should be multiple small chunks where a large chunk could exist.)
void VirtualSpaceNode::verify_free_chunks_are_ideally_merged() {
  Metachunk* chunk = first_chunk();
  Metachunk* invalid_chunk = (Metachunk*) top();
  // Shorthands.
  const size_t size_med = (is_class() ? ClassMediumChunk : MediumChunk) * BytesPerWord;
  const size_t size_small = (is_class() ? ClassSmallChunk : SmallChunk) * BytesPerWord;
  int num_free_chunks_since_last_med_boundary = -1;
  int num_free_chunks_since_last_small_boundary = -1;
  bool error = false;
  char err[256];
  while (!error && chunk < invalid_chunk ) {
    // Test for missed chunk merge opportunities: count number of free chunks since last chunk boundary.
    // Reset the counter when encountering a non-free chunk.
    if (chunk->get_chunk_type() != HumongousIndex) {
      if (chunk->is_tagged_free()) {
        // Count successive free, non-humongous chunks.
        if (is_aligned(chunk, size_small)) {
          if (num_free_chunks_since_last_small_boundary > 0) {
            error = true;
            jio_snprintf(err, sizeof(err), "Missed chunk merge opportunity to merge a small chunk preceding " PTR_FORMAT ".", p2i(chunk));
          } else {
            num_free_chunks_since_last_small_boundary = 0;
          }
        } else if (num_free_chunks_since_last_small_boundary != -1) {
          num_free_chunks_since_last_small_boundary ++;
        }
        if (is_aligned(chunk, size_med)) {
          if (num_free_chunks_since_last_med_boundary > 0) {
            error = true;
            jio_snprintf(err, sizeof(err), "Missed chunk merge opportunity to merge a medium chunk preceding " PTR_FORMAT ".", p2i(chunk));
          } else {
            num_free_chunks_since_last_med_boundary = 0;
          }
        } else if (num_free_chunks_since_last_med_boundary != -1) {
          num_free_chunks_since_last_med_boundary ++;
        }
      } else {
        // Encountering a non-free chunk, reset counters.
        num_free_chunks_since_last_med_boundary = -1;
        num_free_chunks_since_last_small_boundary = -1;
      }
    } else {
      // One cannot merge areas with a humongous chunk in the middle. Reset counters.
      num_free_chunks_since_last_med_boundary = -1;
      num_free_chunks_since_last_small_boundary = -1;
    }

    if (error) {
      print_map(tty, is_class());
      fatal("%s", err);
    }

    MetaWord* next = ((MetaWord*)chunk) + chunk->word_size();
    chunk = (Metachunk*) next;
  }
}
#endif // ASSERT

VirtualSpaceNode::~VirtualSpaceNode() {
  _rs.release();
}

// Allocate a root chunk (a chunk of max. size) from the the virtual space and add it to the
// specified chunk manager as free chunk.
void VirtualSpaceNode::allocate_new_chunk(ChunkManager* chunk_manager) {

  assert_is_aligned(top(), MAX_CHUNK_BYTE_SIZE);

  assert_is_aligned(uncommitted_words(), MAX_CHUNK_WORD_SIZE);
  assert_is_aligned(unused_words(), MAX_CHUNK_WORD_SIZE);
  assert_is_aligned(used_words(), MAX_CHUNK_WORD_SIZE);

  // Caller must check, before calling this method, if node needs expansion.
  assert(unused_words() >= MAX_CHUNK_WORD_SIZE, "Needs expansion.");

  // Create new root chunk
  MetaWord* loc = top();
  inc_top(MAX_CHUNK_WORD_SIZE);
  Metachunk* new_chunk = ::new (loc) Metachunk(HIGHEST_CHUNK_LEVEL, this, true);

  // Add it to the chunk manager
  new_chunk->set_in_use();
  chunk_manager->return_chunk(new_chunk);

}

// Expands the committed portion of this node by the size of a root chunk. Will assert
// if expansion is impossible.
bool VirtualSpaceNode::expand() {

  assert_is_aligned(uncommitted_words(), MAX_CHUNK_WORD_SIZE);

  // Caller must check, before calling this method, if node needs expansion.
  assert(uncommitted_words() >= MAX_CHUNK_WORD_SIZE, "Node used up completely.");

  bool result = virtual_space()->expand_by(MAX_CHUNK_BYTE_SIZE, false);

  if (result) {
    log_trace(gc, metaspace, freelist)("Expanded virtual space list node by " SIZE_FORMAT " words.", MAX_CHUNK_BYTE_SIZE);
    DEBUG_ONLY(Atomic::inc(&g_internal_statistics.num_committed_space_expanded));
  } else {
    log_trace(gc, metaspace, freelist)("Failed to expand virtual space list node by " SIZE_FORMAT " words.", MAX_CHUNK_BYTE_SIZE);
  }

  assert(result, "Failed to commit memory");

  assert(unused_words() >= MAX_CHUNK_WORD_SIZE, "sanity");

  return result;
}


bool VirtualSpaceNode::initialize() {

  if (!_rs.is_reserved()) {
    return false;
  }

  // These are necessary restriction to make sure that the virtual space always
  // grows in steps of Metaspace::commit_alignment(). If both base and size are
  // aligned only the middle alignment of the VirtualSpace is used.
  assert_is_aligned(_rs.base(), Metaspace::commit_alignment());
  assert_is_aligned(_rs.size(), Metaspace::commit_alignment());

  // ReservedSpaces marked as special will have the entire memory
  // pre-committed. Setting a committed size will make sure that
  // committed_size and actual_committed_size agrees.
  size_t pre_committed_size = _rs.special() ? _rs.size() : 0;

  bool result = virtual_space()->initialize_with_granularity(_rs, pre_committed_size,
      Metaspace::commit_alignment());
  if (result) {
    assert(virtual_space()->committed_size() == virtual_space()->actual_committed_size(),
        "Checking that the pre-committed memory was registered by the VirtualSpace");

    set_top((MetaWord*)virtual_space()->low());
  }

  return result;
}

void VirtualSpaceNode::print_on(outputStream* st, size_t scale) const {
  size_t used_words = used_words_in_vs();
  size_t commit_words = committed_words();
  size_t res_words = reserved_words();
  VirtualSpace* vs = virtual_space();

  st->print("node @" PTR_FORMAT ": ", p2i(this));
  st->print("reserved=");
  print_scaled_words(st, res_words, scale);
  st->print(", committed=");
  print_scaled_words_and_percentage(st, commit_words, res_words, scale);
  st->print(", used=");
  print_scaled_words_and_percentage(st, used_words, res_words, scale);
  st->cr();
  st->print("   [" PTR_FORMAT ", " PTR_FORMAT ", "
      PTR_FORMAT ", " PTR_FORMAT ")",
      p2i(bottom()), p2i(top()), p2i(end()),
      p2i(vs->high_boundary()));
}

#ifdef ASSERT
void VirtualSpaceNode::mangle() {
  size_t word_size = capacity_words_in_vs();
  Copy::fill_to_words((HeapWord*) low(), word_size, 0xf1f1f1f1);
}
#endif // ASSERT

void VirtualSpaceNode::retire(ChunkManager* chunk_manager) {
  assert(is_class() == chunk_manager->is_class(), "Wrong ChunkManager?");
#ifdef ASSERT
  verify(false);
  EVERY_NTH(VerifyMetaspaceInterval)
    verify(true);
  END_EVERY_NTH
#endif
  for (int i = (int)MediumIndex; i >= (int)ZeroIndex; --i) {
    ChunkIndex index = (ChunkIndex)i;
    size_t chunk_size = chunk_manager->size_by_index(index);

    while (free_words_in_vs() >= chunk_size) {
      Metachunk* chunk = get_chunk_vs(chunk_size);
      // Chunk will be allocated aligned, so allocation may require
      // additional padding chunks. That may cause above allocation to
      // fail. Just ignore the failed allocation and continue with the
      // next smaller chunk size. As the VirtualSpaceNode comitted
      // size should be a multiple of the smallest chunk size, we
      // should always be able to fill the VirtualSpace completely.
      if (chunk == NULL) {
        break;
      }
      chunk_manager->return_single_chunk(chunk);
    }
  }
  assert(free_words_in_vs() == 0, "should be empty now");
}

} // namespace metaspace

