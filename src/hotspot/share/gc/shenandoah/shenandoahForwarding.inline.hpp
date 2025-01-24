/*
 * Copyright (c) 2015, 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_INLINE_HPP

#include "gc/shenandoah/shenandoahForwarding.hpp"

#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "oops/klass.hpp"
#include "oops/markWord.hpp"
#include "runtime/javaThread.hpp"

inline oop ShenandoahForwarding::get_forwardee_raw(oop obj) {
  shenandoah_assert_in_heap_bounds(nullptr, obj);
  return get_forwardee_raw_unchecked(obj);
}

static HeapWord* to_forwardee(markWord mark) {
  return reinterpret_cast<HeapWord*>(mark.value() & ~(markWord::lock_mask_in_place | markWord::self_fwd_mask_in_place));
}

inline bool ShenandoahForwarding::has_forwardee(markWord m) {
  // Lock bits == marked_value (0b11): the upper bits encode a forwardee
  // pointer. Matches normal-forwarded (0b011) and forward-expanded (0b111);
  // excludes self-forwarded (0b100, 0b101, 0b110).
  return (m.value() & markWord::lock_mask_in_place) == markWord::marked_value;
}

inline oop ShenandoahForwarding::get_forwardee_raw_unchecked(oop obj) {
  // JVMTI and JFR code use mark words for marking objects for their needs.
  // On this path, we can encounter the "marked" object, but with null
  // fwdptr. That object is still not forwarded, and we need to return
  // the object itself.
  markWord mark = obj->mark();
  if (has_forwardee(mark)) {
    HeapWord* fwdptr = to_forwardee(mark);
    if (fwdptr != nullptr) {
      return cast_to_oop(fwdptr);
    }
  }
  // Self-forwarded (evacuation failure): the object stays put; the
  // self-fwd bit is set alongside normal lock bits.
  return obj;
}

inline oop ShenandoahForwarding::get_forwardee_mutator(oop obj) {
  // Same as above, but mutator thread cannot ever see null forwardee.
  shenandoah_assert_correct(nullptr, obj);
  assert(Thread::current()->is_Java_thread(), "Must be a mutator thread");

  markWord mark = obj->mark();
  if (has_forwardee(mark)) {
    HeapWord* fwdptr = to_forwardee(mark);
    assert(fwdptr != nullptr, "Forwarding pointer is never null here");
    return cast_to_oop(fwdptr);
  }
  // Self-forwarded or not forwarded: return the object itself.
  return obj;
}

inline oop ShenandoahForwarding::get_forwardee(oop obj) {
  shenandoah_assert_correct(nullptr, obj);
  return get_forwardee_raw_unchecked(obj);
}

inline bool ShenandoahForwarding::is_forwarded(markWord m) {
  return (m.value() & (markWord::lock_mask_in_place | markWord::self_fwd_mask_in_place)) > markWord::monitor_value;
}

inline bool ShenandoahForwarding::is_forwarded(oop obj) {
  return obj->mark().is_forwarded();
}

inline bool ShenandoahForwarding::is_self_forwarded(oop obj) {
  return obj->mark().is_self_forwarded();
}

inline oop ShenandoahForwarding::try_update_forwardee(oop obj, oop update) {
  markWord old_mark = obj->mark();
  if (has_forwardee(old_mark)) {
    return cast_to_oop(to_forwardee(old_mark));
  }
  if (old_mark.is_self_forwarded()) {
    // Another thread lost the evacuation race; the object stays put.
    return obj;
  }

  markWord new_mark = markWord::encode_pointer_as_mark(update);
  if (UseCompactObjectHeaders && old_mark.is_hashed_not_expanded()) {
    new_mark = markWord(new_mark.value() | FWDED_HASH_TRANSITION);
  }
  markWord prev_mark = obj->cas_set_mark(new_mark, old_mark, memory_order_conservative);
  if (prev_mark == old_mark) {
    return update;
  }
  // Concurrent writers on a cset object's mark can only be other evacuation
  // threads installing forwarding (real or self). Mutators cannot reach the
  // mark of a not-yet-forwarded cset object: LRB + stack watermark barriers
  // redirect all reference uses before a Java-level operation can touch it.
  // So the only possible failure modes are a regular forwardee (marked) or
  // a self-forward (possibly with mutator lock/hash mods layered on top
  // after the self-forward became visible).
  if (has_forwardee(prev_mark)) {
    return cast_to_oop(to_forwardee(prev_mark));
  }
  assert(prev_mark.is_self_forwarded(),
         "concurrent writers on cset objects must install forwarding: prev=" INTPTR_FORMAT,
         prev_mark.value());
  return obj;
}

inline oop ShenandoahForwarding::try_forward_to_self(oop obj, markWord old_mark) {
  assert(!old_mark.is_forwarded(),
         "caller must pass a non-forwarded mark: old=" INTPTR_FORMAT, old_mark.value());
  markWord new_mark = old_mark.set_self_forwarded();
  markWord prev_mark = obj->cas_set_mark(new_mark, old_mark, memory_order_conservative);
  if (prev_mark == old_mark) {
    // We installed the self-forward.
    return nullptr;
  }
  // Same invariant as in try_update_forwardee: the only races on a
  // cset object's mark come from other evac threads installing forwarding.
  if (has_forwardee(prev_mark)) {
    return cast_to_oop(to_forwardee(prev_mark));
  }
  assert(prev_mark.is_self_forwarded(),
         "concurrent writers on cset objects must install forwarding: prev=" INTPTR_FORMAT,
         prev_mark.value());
  return obj;
}

inline Klass* ShenandoahForwarding::klass(oop obj) {
  if (UseCompactObjectHeaders) {
    markWord mark = obj->mark();
    if (has_forwardee(mark)) {
      oop fwd = cast_to_oop(to_forwardee(mark));
      mark = fwd->mark();
    }
    return mark.klass();
  } else {
    return obj->klass();
  }
}

inline size_t ShenandoahForwarding::size(oop obj) {
  markWord mark = obj->mark();
  if (has_forwardee(mark)) {
    oop fwd = cast_to_oop(to_forwardee(mark));
    markWord fwd_mark = fwd->mark();
    Klass* klass = UseCompactObjectHeaders ? fwd_mark.klass() : fwd->klass();
    size_t size = fwd->base_size_given_klass(fwd_mark, klass);
    if (UseCompactObjectHeaders) {
      if ((mark.value() & FWDED_HASH_TRANSITION) != FWDED_HASH_TRANSITION) {
        if (fwd_mark.is_expanded() && klass->expand_for_hash(fwd, fwd_mark)) {
          size = align_object_size(size + 1);
        }
      }
    }
    return size;
  } else {
    Klass* klass = UseCompactObjectHeaders ? mark.klass() : obj->klass();
    return obj->size_given_mark_and_klass(mark, klass);
  }
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_INLINE_HPP
