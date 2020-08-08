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

#include "memory/metaspace/blocktree.hpp"
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

struct BlockTree::veri_data_t {
  MemRangeCounter counter;
  int max_edge;
  size_t largest;
};

// Given a node, check that all siblings have the same size and that we have no
// (direct) circularities.
void BlockTree::verify_node_siblings(node_t* n, veri_data_t* vd) const {
  const size_t size = n->size;
  node_t* n2 = n->next;
  node_t* prev_sib = NULL;
  while (n2 != NULL) {
    assrt0(n2->size == size);
    vd->counter.add(n2->size);
    if (prev_sib != NULL) {
      assrt0(prev_sib->next == n2);
      assrt0(prev_sib != n2);
    }
    prev_sib = n2;
    n2 = n2->next;
  }
}

// Given a node and outer bounds applying to it and all children, check it and all children recursively.
void BlockTree::verify_node(node_t* n, size_t left_limit, size_t right_limit,
    veri_data_t* vd, int lvl) const {

  if (lvl > vd->max_edge) {
    vd->max_edge = lvl;
  }

  if (n->size > vd->largest) {
    vd->largest = n->size;
  }

  assrt0((n == _root && n->parent == NULL) || (n != _root && n->parent != NULL));

  // check all siblings
  if (n->next != NULL) {
    verify_node_siblings(n, vd);
  }

  // check order
  assrt(n->size >= minimal_word_size && n->size <= maximal_word_size,
      "bad node size " SIZE_FORMAT, n->size);
  assrt0(n->size < right_limit);
  assrt0(n->size > left_limit);

  vd->counter.add(n->size);

  if (n->left != NULL) {
    assrt0(n != n->left);
    assrt0(n->left->parent == n);
    assrt0(n->left->size < n->size);
    assrt0(n->left->size > left_limit);
    verify_node(n->left, left_limit, n->size, vd, lvl + 1);
  }

  if (n->right != NULL) {
    assrt0(n != n->right);
    assrt0(n->right->parent == n);
    assrt0(n->right->size < right_limit);
    assrt0(n->right->size > n->size);
    verify_node(n->right, n->size, right_limit, vd, lvl + 1);
  }

}

void BlockTree::verify_tree() const {
  int num = 0;
  size_t size = 0;
  veri_data_t vd;
  vd.max_edge = 0;
  vd.largest = 0;
  if (_root != NULL) {
    assrt0(_root->parent == NULL);
    verify_node(_root, 0, maximal_word_size + 1, &vd, 0);
    assrt0(vd.largest == _largest_size_added);
    vd.counter.check(_counter);
    assrt0(vd.counter.count() > 0);
  }
}

void BlockTree::zap_range(MetaWord* p, size_t word_size) {
  memset(p, 0xF3, word_size * sizeof(MetaWord));
}

#undef assrt
#undef assrt0

#endif // ASSERT


void BlockTree::print_node(outputStream* st, node_t* n, int lvl) {
  for (int i = 0; i < lvl; i ++) {
    st->print("---");
  }
  st->print_cr("<" PTR_FORMAT " (size " SIZE_FORMAT ")", p2i(n), n->size);
  if (n->left) {
    print_node(st, n->left, lvl + 1);
  }
  if (n->right) {
    print_node(st, n->right, lvl + 1);
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
