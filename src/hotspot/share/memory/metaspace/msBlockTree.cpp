/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 SAP SE. All rights reserved.
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

#include "memory/metaspace/msBlockTree.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

namespace metaspace {

#ifdef ASSERT

// Tree verification

// These asserts prints the tree, then asserts
#define assrt(cond, format, ...) \
  if (!(cond)) { \
    print_tree(tty); \
    assert(cond, format, __VA_ARGS__); \
  }

  // This assert prints the tree, then stops (generic message)
#define assrt0(cond) \
	  if (!(cond)) { \
	    print_tree(tty); \
	    assert(cond, "sanity"); \
	  }

struct BlockTree::veridata {
  MemRangeCounter _counter;
  int _max_edge;
  size_t _largest;
};

// Given a node, check that all siblings have the same size and that we have no
// (direct) circularities.
void BlockTree::verify_node_siblings(Node* n, veridata* vd) const {
  const size_t size = n->_word_size;
  Node* n2 = n->_next;
  Node* prev_sib = NULL;
  while (n2 != NULL) {
    assrt0(n2->_word_size == size);
    vd->_counter.add(n2->_word_size);
    if (prev_sib != NULL) {
      assrt0(prev_sib->_next == n2);
      assrt0(prev_sib != n2);
    }
    prev_sib = n2;
    n2 = n2->_next;
  }
}

// Given a node and outer bounds applying to it and all children, check it and all children recursively.
void BlockTree::verify_node(Node* n, size_t left_limit, size_t right_limit,
    veridata* vd, int lvl) const {

  if (lvl > vd->_max_edge) {
    vd->_max_edge = lvl;
  }

  if (n->_word_size > vd->_largest) {
    vd->_largest = n->_word_size;
  }

  assrt0((n == _root && n->_parent == NULL) || (n != _root && n->_parent != NULL));

  // check all siblings
  if (n->_next != NULL) {
    verify_node_siblings(n, vd);
  }

  // check order
  assrt(n->_word_size >= minimal_word_size && n->_word_size <= maximal_word_size,
      "bad node size " SIZE_FORMAT, n->_word_size);
  assrt0(n->_word_size < right_limit);
  assrt0(n->_word_size > left_limit);

  vd->_counter.add(n->_word_size);

  if (n->_left != NULL) {
    assrt0(n != n->_left);
    assrt0(n->_left->_parent == n);
    assrt0(n->_left->_word_size < n->_word_size);
    assrt0(n->_left->_word_size > left_limit);
    verify_node(n->_left, left_limit, n->_word_size, vd, lvl + 1);
  }

  if (n->_right != NULL) {
    assrt0(n != n->_right);
    assrt0(n->_right->_parent == n);
    assrt0(n->_right->_word_size < right_limit);
    assrt0(n->_right->_word_size > n->_word_size);
    verify_node(n->_right, n->_word_size, right_limit, vd, lvl + 1);
  }

}

void BlockTree::verify_tree() const {
  int num = 0;
  size_t size = 0;
  veridata vd;
  vd._max_edge = 0;
  vd._largest = 0;
  if (_root != NULL) {
    assrt0(_root->_parent == NULL);
    verify_node(_root, 0, maximal_word_size + 1, &vd, 0);
    assrt0(vd._largest == _largest_size_added);
    vd._counter.check(_counter);
    assrt0(vd._counter.count() > 0);
  }
}

void BlockTree::zap_range(MetaWord* p, size_t word_size) {
  memset(p, 0xF3, word_size * sizeof(MetaWord));
}

#undef assrt
#undef assrt0

#endif // ASSERT

void BlockTree::print_node(outputStream* st, Node* n, int lvl) {
  for (int i = 0; i < lvl; i++) {
    st->print("---");
  }
  st->print_cr("<" PTR_FORMAT " (size " SIZE_FORMAT ")", p2i(n), n->_word_size);
  if (n->_left) {
    print_node(st, n->_left, lvl + 1);
  }
  if (n->_right) {
    print_node(st, n->_right, lvl + 1);
  }
}

void BlockTree::print_tree(outputStream* st) const {
  if (_root != NULL) {
    print_node(st, _root, 0);
  } else {
    st->print_cr("<no nodes>");
  }
}

} // namespace metaspace
