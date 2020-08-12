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

#ifndef SHARE_MEMORY_METASPACE_BLOCKTREE_HPP
#define SHARE_MEMORY_METASPACE_BLOCKTREE_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace/counter.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

namespace metaspace {


// BlockTree is a rather simple binary search tree. It is used to
//  manage small to medium free memory blocks (see class FreeBlocks).
//
// There is no separation between payload (managed blocks) and nodes: the
//  memory blocks themselves are the nodes, with the block size being the key.
//
// We store node pointer information in these blocks when storing them. That
//  imposes a minimum size to the managed memory blocks.
//  See MetaspaceArene::get_raw_allocation_word_size().
//
// We want to manage many memory blocks of the same size, but we want
//  to prevent the tree from blowing up and degenerating into a list. Therefore
//  there is only one node for each unique block size; subsequent blocks of the
//  same size are stacked below that first node:
//
//                   +-----+
//                   | 100 |
//                   +-----+
//                  /       \
//           +-----+
//           | 80  |
//           +-----+
//          /   |   \
//         / +-----+ \
//  +-----+  | 80  |  +-----+
//  | 70  |  +-----+  | 85  |
//  +-----+     |     +-----+
//           +-----+
//           | 80  |
//           +-----+
//
//
// Todo: This tree is unbalanced. It would be a good fit for a red-black tree.
//  In order to make this a red-black tree, we need an algorithm which can deal
//  with nodes which are their own payload (most red-black tree implementations
//  swap payloads of their nodes at some point, see e.g. j.u.TreeSet).
// A good example is the Linux kernel rbtree, which is a clean, easy-to-read
//  implementation.

class BlockTree: public CHeapObj<mtMetaspace> {


  struct node_t {

    // Normal tree node stuff...
    node_t* parent;
    node_t* left;
    node_t* right;

    // blocks with the same size are put in a list with this node as head.
    node_t* next;

    // word size of node. Note that size cannot be larger than max metaspace size,
    // so this could be very well a 32bit value (in case we ever make this a balancing
    // tree and need additional space for weighting information).
    size_t size;

  };

public:

  // Largest node size, (bit arbitrarily) capped at 4M since we know this to
  // be the max possible metaspace allocation size. TODO. Do this better.
  const static size_t maximal_word_size = 4 * M;

  // We need nodes to be at least large enough to hold a node_t
  const static size_t minimal_word_size =
      (sizeof(node_t) + sizeof(MetaWord) - 1) / sizeof(MetaWord);

private:

  node_t* _root;

  // As a performance optimization, we keep the size of the largest node.
  size_t _largest_size_added;

  MemRangeCounter _counter;

  // given a node n, add it to the list starting at head
  static void add_to_list(node_t* n, node_t* head) {
    assert(head->size == n->size, "sanity");
    n->next = head->next;
    head->next = n;
    DEBUG_ONLY(n->left = n->right = n->parent = NULL;)
  }

  // given a node list starting at head, remove one node from it and return it.
  // List must contain at least one other node.
  static node_t* remove_from_list(node_t* head) {
    assert(head->next != NULL, "sanity");
    node_t* n = head->next;
    if (n != NULL) {
      head->next = n->next;
    }
    return n;
  }

  // Given a node n and a node p, wire up n as left child of p.
  static void set_left_child(node_t* p, node_t* c) {
    p->left = c;
    if (c != NULL) {
      assert(c->size < p->size, "sanity");
      c->parent = p;
    }
  }

  // Given a node n and a node p, wire up n as right child of p.
  static void set_right_child(node_t* p, node_t* c) {
    p->right = c;
    if (c != NULL) {
      assert(c->size > p->size, "sanity");
      c->parent = p;
    }
  }

  // Given a node n, return its predecessor in the tree
  // (node with the next-smaller size).
  static node_t* predecessor(node_t* n) {
    node_t* pred = NULL;
    if (n->left != NULL) {
      pred = n->left;
      while (pred->right != NULL) {
        pred = pred->right;
      }
    } else {
      pred = n->parent;
      node_t* n2 = n;
      while (pred != NULL && n2 == pred->left) {
        n2 = pred;
        pred = pred->parent;
      }
    }
    return pred;
  }

  // Given a node n, return its predecessor in the tree
  // (node with the next-smaller size).
  static node_t* successor(node_t* n) {
    node_t* succ = NULL;
    if (n->right != NULL) {
      // If there is a right child, search the left-most
      // child of that child.
      succ = n->right;
      while (succ->left != NULL) {
        succ = succ->left;
      }
    } else {
      succ = n->parent;
      node_t* n2 = n;
      // As long as I am the right child of my parent, search upward
      while (succ != NULL && n2 == succ->right) {
        n2 = succ;
        succ = succ->parent;
      }
    }
    return succ;
  }

  // Given a node, replace it with a replacement node as a child for its parent.
  // If the node is root and has no parent, sets it as root.
  void replace_node_in_parent(node_t* child, node_t* replace) {
    node_t* parent = child->parent;
    if (parent != NULL) {
      if (parent->left == child) { // I am a left child
        set_left_child(parent, replace);
      } else {
        set_right_child(parent, replace);
      }
    } else {
      assert(child == _root, "must be root");
      _root = replace;
      if (replace != NULL) {
        replace->parent = NULL;
      }
    }
    return;
  }

  // Given a node n and a node forebear, insert n under forebear
  void insert(node_t* forebear, node_t* n) {
    if (n->size == forebear->size) {
      add_to_list(n, forebear); // parent stays NULL in this case.
    } else {
      if (n->size < forebear->size) {
        if (forebear->left == NULL) {
          set_left_child(forebear, n);
        } else {
          insert(forebear->left, n);
        }
      } else {
        assert(n->size > forebear->size, "sanity");
        if (forebear->right == NULL) {
          set_right_child(forebear, n);
          if (_largest_size_added < n->size) {
            _largest_size_added = n->size;
          }
        } else {
          insert(forebear->right, n);
        }
      }
    }
  }

  // Given a node and a wish size, search this node and all children for
  // the node closest (equal or larger sized) to the size s.
  static node_t* find_closest_fit(node_t* n, size_t s) {

    if (n->size == s) {
      // Perfect fit.
      return n;

    } else if (n->size < s) {
      // too small, dive down right side
      if (n->right != NULL) {
        return find_closest_fit(n->right, s);
      } else {
        return NULL;
      }
    } else {
      // n is a possible fit
      assert(n->size > s, "Sanity");
      if (n->left != NULL && n->left->size >= s) {
        // but not the best - dive down left side.
        return find_closest_fit(n->left, s);
      } else {
        // n is the best fit.
        return n;
      }
    }

  }

  // Given a wish size, search the whole tree for a
  // node closest (equal or larger sized) to the size s.
  node_t* find_closest_fit(size_t s) {
    if (_root != NULL) {
      return find_closest_fit(_root, s);
    }
    return NULL;
  }

  // Given a node n, remove it from the tree and repair tree.
  void remove_node_from_tree(node_t* n) {

    assert(n->next == NULL, "do not delete a node which has a non-empty list");

    // Maintain largest size node to speed up lookup
    if (n->size == _largest_size_added) {
      node_t* pred = predecessor(n);
      if (pred != NULL) {
        _largest_size_added = pred->size;
      } else {
        _largest_size_added = 0;
      }
    }

    if (n->left == NULL && n->right == NULL) {
      replace_node_in_parent(n, NULL);

    } else if (n->left == NULL && n->right != NULL) {
      replace_node_in_parent(n, n->right);

    } else if (n->left != NULL && n->right == NULL) {
      replace_node_in_parent(n, n->left);

    } else {

      // Node has two children.

      // 1) Find direct successor (the next larger node).
      node_t* succ = successor(n);

      // There has to be a successor since n->right was != NULL...
      assert(succ != NULL, "must be");

      // ... and it should not have a left child since successor
      //     is supposed to be the next larger node, so it must be the mostleft node
      //     in the sub tree rooted at n->right
      assert(succ->left == NULL, "must be");

      assert(succ->size > n->size, "sanity");

      node_t* successor_parent = succ->parent;
      node_t* successor_right_child = succ->right;

      // Remove successor from its parent.
      if (successor_parent == n) {

        // special case: successor is a direct child of n. Has to be the right child then.
        assert(n->right == succ, "sanity");

        // Just replace n with this successor.
        replace_node_in_parent(n, succ);

        // Take over n's old left child, too.
        // We keep the successor's right child.
        set_left_child(succ, n->left);

      } else {

        // If the successors parent is not n, we are deeper in the tree,
        // the successor has to be the left child of its parent.
        assert(successor_parent->left == succ, "sanity");

        // The right child of the successor (if there was one) replaces the successor at its parent's left child.
        set_left_child(successor_parent, succ->right);

        // and the successor replaces n at its parent
        replace_node_in_parent(n, succ);

        // and takes over n's old children
        set_left_child(succ, n->left);
        set_right_child(succ, n->right);

      }
    }
  }

#ifdef ASSERT

  struct veri_data_t;
  void verify_node_siblings(node_t* n, veri_data_t* vd) const;
  void verify_node(node_t* n, size_t left_limit, size_t right_limit, veri_data_t* vd, int lvl) const;
  void verify_tree() const;

  void zap_range(MetaWord* p, size_t word_size);

#endif // ASSERT


  static void print_node(outputStream* st, node_t* n, int lvl);

public:

  BlockTree() : _root(NULL), _largest_size_added(0) {}

  // Add a memory block to the tree. Memory block will be used to store
  // node information.
  void add_block(MetaWord* p, size_t word_size) {
    DEBUG_ONLY(zap_range(p, word_size));
    assert(word_size >= minimal_word_size && word_size < maximal_word_size,
           "invalid block size " SIZE_FORMAT, word_size);
    node_t* n = (node_t*)p;
    n->size = word_size;
    n->next = n->left = n->right = n->parent = NULL;
    if (_root == NULL) {
      _root = n;
    } else {
      insert(_root, n);
    }
    _counter.add(word_size);

    // Maintain largest node to speed up lookup
    if (_largest_size_added < n->size) {
      _largest_size_added = n->size;
    }

  }

  // Given a word_size, searches and returns a block of at least that size.
  // Block may be larger. Real block size is returned in *p_real_word_size.
  MetaWord* get_block(size_t word_size, size_t* p_real_word_size) {
    assert(word_size >= minimal_word_size && word_size < maximal_word_size,
           "invalid block size " SIZE_FORMAT, word_size);

    if (_largest_size_added < word_size) {
      return NULL;
    }

    node_t* n = find_closest_fit(word_size);

    if (n != NULL) {
      assert(n->size >= word_size, "sanity");

      // If the node has siblings, remove one of them,
      // otherwise remove this node from the tree.
      if (n->next != NULL) {
        n = remove_from_list(n);
      } else {
        remove_node_from_tree(n);
      }

      MetaWord* p = (MetaWord*)n;
      *p_real_word_size = n->size;

      _counter.sub(n->size);

      DEBUG_ONLY(zap_range(p, n->size));

      return p;
    }
    return NULL;
  }


  // Returns number of blocks in this structure
  unsigned count() const { return _counter.count(); }

  // Returns total size, in words, of all elements.
  size_t total_size() const { return _counter.total_size(); }

  bool is_empty() const { return _root == NULL; }

  void print_tree(outputStream* st) const;

  DEBUG_ONLY(void verify() const { verify_tree(); })

};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_BLOCKTREE_HPP
