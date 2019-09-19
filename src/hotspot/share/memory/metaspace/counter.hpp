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

#ifndef SHARE_MEMORY_METASPACE_COUNTER_HPP
#define SHARE_MEMORY_METASPACE_COUNTER_HPP

#include "metaprogramming/isSigned.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"


namespace metaspace {

// A very simple helper class which counts something, offers decrement/increment
// methods and checks for overflow/underflow on increment/decrement.
//
// (since we seem to do that alot....)

template <class T>
class AbstractCounter {

  T _c;

  // Optional name for easier reference
  const char* const _name;

  // Only allow unsigned values for now
  STATIC_ASSERT(IsSigned<T>::value == false);

public:

  AbstractCounter(const char* name) : _c(0), _name(name) {}
  AbstractCounter() : _c(0), _name("") {}

  T get() const           { return _c; }

  void increment() { increment_by(1); }
  void decrement() { decrement_by(1); }

  void increment_by(T v) {
    assert(_c + v >= _c,
        "%s overflow (" UINT64_FORMAT "+" UINT64_FORMAT ")",
        _name, (uint64_t)_c, (uint64_t)v);
    _c += v;
  }

  void decrement_by(T v) {
    assert(_c - v <= _c,
        "%s underflow (" UINT64_FORMAT "-" UINT64_FORMAT ")",
        _name, (uint64_t)_c, (uint64_t)v);
    _c -= v;
  }

  void reset()                { _c = 0; }

#ifdef ASSERT
  void check(T expected) const {
    assert(_c == expected, "Counter mismatch: %d, expected: %d.",
           (int)_c, (int)expected);
    }
#endif

};

typedef AbstractCounter<size_t>   SizeCounter;
typedef AbstractCounter<unsigned> IntCounter;


template <class T>
class AbstractAtomicCounter {

  volatile T _c;

  // Optional name for easier reference
  const char* const _name;

  // Only allow unsigned values for now
  STATIC_ASSERT(IsSigned<T>::value == false);

public:


  AbstractAtomicCounter(const char* name) : _c(0), _name(name) {}
  AbstractAtomicCounter() : _c(0), _name("") {}

  T get() const               { return _c; }

  void increment() {
    assert(_c + 1 != 0,
        "%s overflow (" UINT64_FORMAT "+1)",
        _name, (uint64_t)_c);
    Atomic::inc(&_c);
  }

  void decrement() {
    assert(_c >= 1,
        "%s underflow (" UINT64_FORMAT "-1)",
        _name, (uint64_t)_c);
    Atomic::dec(&_c);
  }

  void increment_by(T v) {
    T v1 = _c;
    T v2 = Atomic::add(v, &_c);
    assert(v2 > v1,
           "%s overflow (" UINT64_FORMAT "+" UINT64_FORMAT ")",
           _name, (uint64_t)v1, (uint64_t)v);
  }

  void decrement_by(T v) {
    assert(_c >= v,
           "%s underflow (" UINT64_FORMAT "-" UINT64_FORMAT ")",
           _name, (uint64_t)_c, (uint64_t)v);
    Atomic::sub(v, &_c);
  }

#ifdef ASSERT
  void check(T expected) const {
    assert(_c == expected, "Counter mismatch: %d, expected: %d.",
           (int)_c, (int)expected);
    }
#endif

};

typedef AbstractAtomicCounter<size_t> SizeAtomicCounter;



} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_WORDSIZECOUNTER_HPP

