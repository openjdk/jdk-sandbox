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


#include "precompiled.hpp"
#include "logging/log.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/counter.hpp"
#include "memory/metaspace/commitLimiter.hpp"
#include "memory/metaspace/counter.hpp"
#include "memory/metaspace/virtualSpaceList.hpp"
#include "memory/metaspace/virtualSpaceNode.hpp"
#include "runtime/mutexLocker.hpp"


namespace metaspace {

static int next_node_id = 0;

// Create a new, empty, expandable list.
VirtualSpaceList::VirtualSpaceList(const char* name, CommitLimiter* commit_limiter)
  : _name(name),
    _first_node(NULL),
    _can_expand(true),
    _can_purge(true),
    _commit_limiter(commit_limiter),
    _reserved_words_counter(),
    _committed_words_counter()
{
}

// Create a new list. The list will contain one node only, which uses the given ReservedSpace.
// It will be not expandable beyond that first node.
VirtualSpaceList::VirtualSpaceList(const char* name, ReservedSpace rs, CommitLimiter* commit_limiter)
: _name(name),
  _first_node(NULL),
  _can_expand(false),
  _can_purge(false),
  _commit_limiter(commit_limiter),
  _reserved_words_counter(),
  _committed_words_counter()
{
  // Create the first node spanning the existing ReservedSpace. This will be the only node created
  // for this list since we cannot expand.
  VirtualSpaceNode* vsn = VirtualSpaceNode::create_node(next_node_id++,
                                                        rs, _commit_limiter,
                                                        &_reserved_words_counter, &_committed_words_counter);
  assert(vsn != NULL, "node creation failed");
  _first_node = vsn;
  _first_node->set_next(NULL);
  _nodes_counter.increment();
}

VirtualSpaceList::~VirtualSpaceList() {
  // Note: normally, there is no reason ever to delete a vslist since they are
  // global objects, but for gtests it makes sense to allow this.
  VirtualSpaceNode* vsn = _first_node;
  VirtualSpaceNode* vsn2 = vsn;
  while (vsn != NULL) {
    vsn2 = vsn->next();
    delete vsn;
    vsn = vsn2;
  }
}

// Create a new node and append it to the list. After
// this function, _current_node shall point to a new empty node.
// List must be expandable for this to work.
void VirtualSpaceList::create_new_node() {
  assert(_can_expand, "List is not expandable");
  assert_lock_strong(MetaspaceExpand_lock);

  VirtualSpaceNode* vsn = VirtualSpaceNode::create_node(next_node_id ++,
                                                        Settings::virtual_space_node_default_word_size(),
                                                        _commit_limiter,
                                                        &_reserved_words_counter, &_committed_words_counter);
  assert(vsn != NULL, "node creation failed");
  vsn->set_next(_first_node);
  _first_node = vsn;
  _nodes_counter.increment();
}

// Allocate a root chunk from this list.
// Note: this just returns a chunk whose memory is reserved; no memory is committed yet.
// Hence, before using this chunk, it must be committed.
// Also, no limits are checked, since no committing takes place.
Metachunk*  VirtualSpaceList::allocate_root_chunk() {
  assert_lock_strong(MetaspaceExpand_lock);

  log_debug(metaspace)("VirtualSpaceList %s: allocate root chunk.", _name);

  if (_first_node == NULL ||
      _first_node->free_words() == 0) {

    // The current node is fully used up.
    log_debug(metaspace)("VirtualSpaceList %s: need new node.", _name);

    // Since all allocations from a VirtualSpaceNode happen in
    // root-chunk-size units, and the node size must be root-chunk-size aligned,
    // we should never have left-over space.
    assert(_first_node == NULL ||
           _first_node->free_words() == 0, "Sanity");

    if (_can_expand) {
      create_new_node();
    } else {
      return NULL; // We cannot expand this list.
    }
  }

  Metachunk* c = _first_node->allocate_root_chunk();

  assert(c != NULL, "This should have worked");

  return c;

}

// Attempts to purge nodes. This will remove and delete nodes which only contain free chunks.
// The free chunks are removed from the freelists before the nodes are deleted.
// Return number of purged nodes.
int VirtualSpaceList::purge(MetachunkListCluster* freelists) {

  // Note: I am not sure all that purging business is even necessary anymore
  // since we have a good reclaim mechanism in place. Need to measure.

  assert_lock_strong(MetaspaceExpand_lock);

  if (_can_purge == false) {
    log_debug(metaspace)("VirtualSpaceList %s: cannot purge this list.", _name);
    return 0;
  }

  log_debug(metaspace)("VirtualSpaceList %s: purging...", _name);

  VirtualSpaceNode* vsn = _first_node;
  VirtualSpaceNode* prev_vsn = NULL;
  int num = 0, num_purged = 0;
  while (vsn != NULL) {
    VirtualSpaceNode* next_vsn = vsn->next();
    bool purged = vsn->attempt_purge(freelists);
    if (purged) {
      // Note: from now on do not dereference vsn!
      log_debug(metaspace)("VirtualSpaceList %s: purged node @" PTR_FORMAT, _name, p2i(vsn));
      if (_first_node == vsn) {
        _first_node = next_vsn;
      }
      DEBUG_ONLY(vsn = (VirtualSpaceNode*)((uintptr_t)(0xdeadbeef));)
      if (prev_vsn != NULL) {
        prev_vsn->set_next(next_vsn);
      }
      num_purged ++;
      _nodes_counter.decrement();
    } else {
      prev_vsn = vsn;
    }
    vsn = next_vsn;
    num ++;
  }

  log_debug(metaspace)("VirtualSpaceList %s: purged %d/%d nodes.", _name, num_purged, num);

  return num_purged;

}

// Print all nodes in this space list.
void VirtualSpaceList::print_on(outputStream* st) const {
  MutexLocker fcl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);

  st->print_cr("vsl %s:", _name);
  const VirtualSpaceNode* vsn = _first_node;
  int n = 0;
  while (vsn != NULL) {
    st->print("- node #%d: ", n);
    vsn->print_on(st);
    vsn = vsn->next();
    n ++;
  }
  st->print_cr("- total %d nodes, " SIZE_FORMAT " reserved words, " SIZE_FORMAT " committed words.",
               n, reserved_words(), committed_words());
}

#ifdef ASSERT
void VirtualSpaceList::verify_locked(bool slow) const {

  assert_lock_strong(MetaspaceExpand_lock);

  assert(_name != NULL, "Sanity");

  int n = 0;

  if (_first_node != NULL) {

    size_t total_reserved_words = 0;
    size_t total_committed_words = 0;
    const VirtualSpaceNode* vsn = _first_node;
    while (vsn != NULL) {
      n ++;
      vsn->verify(slow);
      total_reserved_words += vsn->word_size();
      total_committed_words += vsn->committed_words();
      vsn = vsn->next();
    }

    _nodes_counter.check(n);
    _reserved_words_counter.check(total_reserved_words);
    _committed_words_counter.check(total_committed_words);

  } else {

    _reserved_words_counter.check(0);
    _committed_words_counter.check(0);

  }
}

void VirtualSpaceList::verify(bool slow) const {
  MutexLocker fcl(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
  verify_locked(slow);
}
#endif

// Returns true if this pointer is contained in one of our nodes.
bool VirtualSpaceList::contains(const MetaWord* p) const {
  const VirtualSpaceNode* vsn = _first_node;
  while (vsn != NULL) {
    if (vsn->contains(p)) {
      return true;
    }
    vsn = vsn->next();
  }
  return false;
}

VirtualSpaceList* VirtualSpaceList::_vslist_class = NULL;
VirtualSpaceList* VirtualSpaceList::_vslist_nonclass = NULL;

void VirtualSpaceList::set_vslist_class(VirtualSpaceList* vsl) {
  assert(_vslist_class == NULL, "Sanity");
  _vslist_class = vsl;
}

void VirtualSpaceList::set_vslist_nonclass(VirtualSpaceList* vsl) {
  assert(_vslist_nonclass == NULL, "Sanity");
  _vslist_nonclass = vsl;
}

} // namespace metaspace

