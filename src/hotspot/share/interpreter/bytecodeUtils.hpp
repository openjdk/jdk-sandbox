/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019 SAP SE. All rights reserved.
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

#ifndef SHARE_INTERPRETER_BYTECODEUTILS_HPP
#define SHARE_INTERPRETER_BYTECODEUTILS_HPP

#include "memory/allocation.hpp"
#include "oops/method.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/globalDefinitions.hpp"

class TrackingStack;
class TrackingStackCreator;

// The entry of TrackingStack.
class TrackingStackEntry {
 private:

  friend class TrackingStack;
  friend class TrackingStackCreator;

  // The raw entry composed of the type and the bci.
  int _entry;

  enum {
    SCALE = 1024 * 1024
  };

  // Merges this entry with the given one and returns the result. If
  // the bcis of the entry are different, the bci of the result will be
  // undefined. If the types are different, the result type is T_CONFLICT.
  // (an exception is if one type is an array and the other is object, then
  // the result type will be T_OBJECT).
  TrackingStackEntry merge(TrackingStackEntry other);
public:
  // Creates a new entry with an invalid bci and the given type.
  TrackingStackEntry(BasicType type = T_CONFLICT);

  // Creates a new entry with the given bci and type.
  TrackingStackEntry(int bci, BasicType type);

 public:

  enum {
    INVALID = SCALE - 1
  };

  // Returns the bci. If the bci is invalid, INVALID is returned.
  int get_bci();

  // Returns true, if the bci is not invalid.
  bool has_bci() { return get_bci() != INVALID; }

  // Returns the type of the entry.
  BasicType get_type();
};

// A stack consisting of TrackingStackEntries.
class TrackingStack: CHeapObj<mtInternal> {

 private:

  friend class TrackingStackCreator;
  friend class TrackingStackEntry;

  // The stack.
  GrowableArray<TrackingStackEntry> _stack;

  TrackingStack() { };
  TrackingStack(const TrackingStack &copy);

  // Pushes the given entry.
  void push_raw(TrackingStackEntry entry);

  // Like push_raw, but if the entry is long or double, we push two.
  void push(TrackingStackEntry entry);

  // Like push(entry), but using bci/type instead of entry.
  void push(int bci, BasicType type);

  // Pops the given number of entries.
  void pop(int slots);

  // Merges this with the given stack by merging all entries. The
  // size of the stacks must be the same.
  void merge(TrackingStack const& other);

 public:

  // Returns the size of the stack.
  int get_size() const;

  // Returns the entry with the given index. Top of stack is at index 0.
  TrackingStackEntry get_entry(int slot);
};

// Analyses the bytecodes of a method and tries to create a tracking
// stack for each bci. The tracking stack holds the bci and type of
// the object on the stack. The bci (if valid) holds the bci of the
// instruction, which put the entry on the stack.
class TrackingStackCreator {

  // The stacks.
  GrowableArray<TrackingStack*>* _stacks;

  // The method.
  Method* _method;

  // The maximum number of entries we want to use. This is used to
  // limit the amount of memory we waste for insane methods (as they
  // appear in JCK tests).
  int _max_entries;

  // The number of entries used (the sum of all entries of all stacks).
  int _nr_of_entries;

  // If true, we have added at least one new stack.
  bool _added_one;

  // If true, we have processed all bytecodes.
  bool _all_processed;

  static const int _max_cause_detail;

  // Merges the stack the the given bci with the given stack. If there
  // is no stack at the bci, we just put the given stack there. This
  // method doesn't takes ownership of the stack.
  void merge(int bci, TrackingStack* stack);

  // Processes the instruction at the given bci in the method. Returns
  // the size of the instruction.
  int do_instruction(int bci);

  bool print_NPE_cause0(outputStream *os, int bci, int slot, int max_detail, const char *prefix = NULL);

 public:

  // Creates tracking stacks for the given method (the method must be
  // rewritten already). Note that you're not allowed to use this object
  // when crossing a safepoint! If the bci is != -1, we only create the
  // stacks as far as needed to get a stack for the bci.
  TrackingStackCreator(Method* method, int bci = -1);

  // Releases the resources.
  ~TrackingStackCreator();

  // Returns the number of stacks (this is the size of the method).
  int get_size() { return _stacks->length() - 1; }

  // Assuming that a NullPointerException was thrown at the given bci,
  // we return the nr of the slot holding the null reference. If this
  // NPE is created by hand, we return -2 as the slot. If there
  // cannot be a NullPointerException at the bci, -1 is returned.
  int get_NPE_null_slot(int bci);

  // Prints a java-like expression for the bytecode that pushed
  // the value to the given slot being live at the given bci.
  // It constructs the expression recuring backwards over the
  // bytecode.
  // The TOS has the slot number 0, that below 1 and so on.
  void print_NPE_cause(outputStream *os, int bci, int slot);

  // Prints a string describing the failed action.
  void print_NPE_failedAction(outputStream *os, int bci);
};

#endif // SHARE_INTERPRETER_BYTECODEUTILS_HPP
