/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, 2020, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2020, 2021, Huawei Technologies Co., Ltd. All rights reserved.
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

#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/mode/shenandoahMode.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahBarrierSetAssembler.hpp"
#include "gc/shenandoah/shenandoahForwarding.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahRuntime.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "interpreter/interp_masm.hpp"
#include "interpreter/interpreter.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/sharedRuntime.hpp"
#ifdef COMPILER1
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "gc/shenandoah/c1/shenandoahBarrierSetC1.hpp"
#endif
#ifdef COMPILER2
#include "gc/shenandoah/c2/shenandoahBarrierSetC2.hpp"
#include "opto/output.hpp"
#include "utilities/population_count.hpp"
#include "utilities/powerOfTwo.hpp"
#endif

#define __ masm->

void ShenandoahBarrierSetAssembler::arraycopy_prologue(MacroAssembler* masm, DecoratorSet decorators, bool is_oop,
                                                       Register src, Register dst, Register count, RegSet saved_regs) {
  if (is_oop) {
    bool dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;
    if ((ShenandoahSATBBarrier && !dest_uninitialized) || ShenandoahLoadRefBarrier) {

      Label done;

      // Avoid calling runtime if count == 0
      __ beqz(count, done);

      // Is GC active?
      Address gc_state(xthread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
      assert_different_registers(src, dst, count, t0);

      __ lbu(t0, gc_state);
      if (ShenandoahSATBBarrier && dest_uninitialized) {
        __ test_bit(t0, t0, ShenandoahHeap::HAS_FORWARDED_BITPOS);
        __ beqz(t0, done);
      } else {
        __ andi(t0, t0, ShenandoahHeap::HAS_FORWARDED | ShenandoahHeap::MARKING);
        __ beqz(t0, done);
      }

      __ push_reg(saved_regs, sp);
      if (UseCompressedOops) {
        __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::arraycopy_barrier_narrow_oop),
                        src, dst, count);
      } else {
        __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::arraycopy_barrier_oop), src, dst, count);
      }
      __ pop_reg(saved_regs, sp);
      __ bind(done);
    }
  }
}

void ShenandoahBarrierSetAssembler::arraycopy_epilogue(MacroAssembler* masm, DecoratorSet decorators, bool is_oop,
                                                       Register start, Register count, Register tmp) {
  if (ShenandoahCardBarrier && is_oop) {
    gen_write_ref_array_post_barrier(masm, decorators, start, count, tmp);
  }
}

void ShenandoahBarrierSetAssembler::satb_barrier(MacroAssembler* masm,
                                                 Register obj,
                                                 Register pre_val,
                                                 Register thread,
                                                 Register tmp1,
                                                 Register tmp2,
                                                 bool tosca_live,
                                                 bool expand_call) {
  assert(ShenandoahSATBBarrier, "Should be checked by caller");

  // If expand_call is true then we expand the call_VM_leaf macro
  // directly to skip generating the check by
  // InterpreterMacroAssembler::call_VM_leaf_base that checks _last_sp.
  assert(thread == xthread, "must be");

  Label done;
  Label runtime;

  assert_different_registers(obj, pre_val, tmp1, tmp2);
  assert(pre_val != noreg && tmp1 != noreg && tmp2 != noreg, "expecting a register");

  Address index(thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset()));
  Address buffer(thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset()));

  // Is marking active?
  Address gc_state(xthread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
  __ lbu(t1, gc_state);
  __ test_bit(t1, t1, ShenandoahHeap::MARKING_BITPOS);
  __ beqz(t1, done);

  // Do we need to load the previous value?
  if (obj != noreg) {
    __ load_heap_oop(pre_val, Address(obj, 0), noreg, noreg, AS_RAW);
  }

  // Is the previous value null?
  __ beqz(pre_val, done);

  // Can we store original value in the thread's buffer?
  // Is index == 0?
  // (The index field is typed as size_t.)
  __ ld(tmp1, index);                  // tmp := *index_adr
  __ beqz(tmp1, runtime);              // tmp == 0? If yes, goto runtime

  __ subi(tmp1, tmp1, wordSize);       // tmp := tmp - wordSize
  __ sd(tmp1, index);                  // *index_adr := tmp
  __ ld(tmp2, buffer);
  __ add(tmp1, tmp1, tmp2);            // tmp := tmp + *buffer_adr

  // Record the previous value
  __ sd(pre_val, Address(tmp1, 0));
  __ j(done);

  __ bind(runtime);
  // save the live input values
  RegSet saved = RegSet::of(pre_val);
  if (tosca_live) saved += RegSet::of(x10);
  if (obj != noreg) saved += RegSet::of(obj);

  __ push_reg(saved, sp);

  // Calling the runtime using the regular call_VM_leaf mechanism generates
  // code (generated by InterpreterMacroAssember::call_VM_leaf_base)
  // that checks that the *(rfp+frame::interpreter_frame_last_sp) is null.
  //
  // If we care generating the pre-barrier without a frame (e.g. in the
  // intrinsified Reference.get() routine) then ebp might be pointing to
  // the caller frame and so this check will most likely fail at runtime.
  //
  // Expanding the call directly bypasses the generation of the check.
  // So when we do not have have a full interpreter frame on the stack
  // expand_call should be passed true.
  if (expand_call) {
    assert(pre_val != c_rarg1, "smashed arg");
    __ super_call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_barrier_pre), pre_val);
  } else {
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_barrier_pre), pre_val);
  }

  __ pop_reg(saved, sp);

  __ bind(done);
}

void ShenandoahBarrierSetAssembler::resolve_forward_pointer(MacroAssembler* masm, Register dst, Register tmp) {
  assert(ShenandoahLoadRefBarrier || ShenandoahCASBarrier, "Should be enabled");

  Label is_null;
  __ beqz(dst, is_null);
  resolve_forward_pointer_not_null(masm, dst, tmp);
  __ bind(is_null);
}

// IMPORTANT: This must preserve all registers, even t0 and t1, except those explicitly
// passed in.
void ShenandoahBarrierSetAssembler::resolve_forward_pointer_not_null(MacroAssembler* masm, Register dst, Register tmp) {
  assert(ShenandoahLoadRefBarrier || ShenandoahCASBarrier, "Should be enabled");
  // The below loads the mark word, checks if the lowest two bits are
  // set, and if so, clear the lowest two bits and copy the result
  // to dst. Otherwise it leaves dst alone.
  // Implementing this is surprisingly awkward. I do it here by:
  // - Inverting the mark word
  // - Test lowest two bits == 0
  // - If so, set the lowest two bits
  // - Invert the result back, and copy to dst
  RegSet saved_regs = RegSet::of(t2);
  bool borrow_reg = (tmp == noreg);
  if (borrow_reg) {
    // No free registers available. Make one useful.
    tmp = t0;
    if (tmp == dst) {
      tmp = t1;
    }
    saved_regs += RegSet::of(tmp);
  }

  assert_different_registers(tmp, dst, t2);
  __ push_reg(saved_regs, sp);

  Label done;
  __ ld(tmp, Address(dst, oopDesc::mark_offset_in_bytes()));
  __ xori(tmp, tmp, -1); // eon with 0 is equivalent to XOR with -1
  __ andi(t2, tmp, markWord::lock_mask_in_place);
  __ bnez(t2, done);
  __ ori(tmp, tmp, markWord::marked_value);
  __ xori(dst, tmp, -1); // eon with 0 is equivalent to XOR with -1
  __ bind(done);

  __ pop_reg(saved_regs, sp);
}

void ShenandoahBarrierSetAssembler::load_reference_barrier(MacroAssembler* masm,
                                                           Register dst,
                                                           Address load_addr,
                                                           DecoratorSet decorators) {
  assert(ShenandoahLoadRefBarrier, "Should be enabled");
  assert(dst != t1 && load_addr.base() != t1, "need t1");
  assert_different_registers(load_addr.base(), t0, t1);

  bool is_strong  = ShenandoahBarrierSet::is_strong_access(decorators);
  bool is_weak    = ShenandoahBarrierSet::is_weak_access(decorators);
  bool is_phantom = ShenandoahBarrierSet::is_phantom_access(decorators);
  bool is_native  = ShenandoahBarrierSet::is_native_access(decorators);
  bool is_narrow  = UseCompressedOops && !is_native;

  Label heap_stable, not_cset;
  __ enter();
  Address gc_state(xthread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
  __ lbu(t1, gc_state);

  // Check for heap stability
  if (is_strong) {
    __ test_bit(t1, t1, ShenandoahHeap::HAS_FORWARDED_BITPOS);
    __ beqz(t1, heap_stable);
  } else {
    Label lrb;
    __ test_bit(t0, t1, ShenandoahHeap::WEAK_ROOTS_BITPOS);
    __ bnez(t0, lrb);
    __ test_bit(t0, t1, ShenandoahHeap::HAS_FORWARDED_BITPOS);
    __ beqz(t0, heap_stable);
    __ bind(lrb);
  }

  // use x11 for load address
  Register result_dst = dst;
  if (dst == x11) {
    __ mv(t1, dst);
    dst = t1;
  }

  // Save x10 and x11, unless it is an output register
  RegSet saved_regs = RegSet::of(x10, x11) - result_dst;
  __ push_reg(saved_regs, sp);
  __ la(x11, load_addr);
  __ mv(x10, dst);

  // Test for in-cset
  if (is_strong) {
    __ mv(t1, ShenandoahHeap::in_cset_fast_test_addr());
    __ srli(t0, x10, ShenandoahHeapRegion::region_size_bytes_shift_jint());
    __ add(t1, t1, t0);
    __ lbu(t1, Address(t1));
    __ test_bit(t0, t1, 0);
    __ beqz(t0, not_cset);
  }

  __ push_call_clobbered_registers();
  address target = nullptr;
  if (is_strong) {
    if (is_narrow) {
      target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong_narrow);
    } else {
      target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong);
    }
  } else if (is_weak) {
    if (is_narrow) {
      target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak_narrow);
    } else {
      target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak);
    }
  } else {
    assert(is_phantom, "only remaining strength");
    assert(!is_narrow, "phantom access cannot be narrow");
    target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak);
  }
  __ rt_call(target);
  __ mv(t0, x10);
  __ pop_call_clobbered_registers();
  __ mv(x10, t0);
  __ bind(not_cset);
  __ mv(result_dst, x10);
  __ pop_reg(saved_regs, sp);

  __ bind(heap_stable);
  __ leave();
}

//
// Arguments:
//
// Inputs:
//   src:        oop location to load from, might be clobbered
//
// Output:
//   dst:        oop loaded from src location
//
// Kill:
//   x30 (tmp reg)
//
// Alias:
//   dst: x30 (might use x30 as temporary output register to avoid clobbering src)
//
void ShenandoahBarrierSetAssembler::load_at(MacroAssembler* masm,
                                            DecoratorSet decorators,
                                            BasicType type,
                                            Register dst,
                                            Address src,
                                            Register tmp1,
                                            Register tmp2) {
  // 1: non-reference load, no additional barrier is needed
  if (!is_reference_type(type)) {
    BarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1, tmp2);
    return;
  }

  // 2: load a reference from src location and apply LRB if needed
  if (ShenandoahBarrierSet::need_load_reference_barrier(decorators, type)) {
    Register result_dst = dst;

    // Preserve src location for LRB
    RegSet saved_regs;
    if (dst == src.base()) {
      dst = (src.base() == x28) ? x29 : x28;
      saved_regs = RegSet::of(dst);
      __ push_reg(saved_regs, sp);
    }
    assert_different_registers(dst, src.base());

    BarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1, tmp2);

    load_reference_barrier(masm, dst, src, decorators);

    if (dst != result_dst) {
      __ mv(result_dst, dst);
      dst = result_dst;
    }

    if (saved_regs.bits() != 0) {
      __ pop_reg(saved_regs, sp);
    }
  } else {
    BarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1, tmp2);
  }

  // 3: apply keep-alive barrier if needed
  if (ShenandoahBarrierSet::need_keep_alive_barrier(decorators, type)) {
    __ enter();
    __ push_call_clobbered_registers();
    satb_barrier(masm /* masm */,
                 noreg /* obj */,
                 dst /* pre_val */,
                 xthread /* thread */,
                 tmp1 /* tmp1 */,
                 tmp2 /* tmp2 */,
                 true /* tosca_live */,
                 true /* expand_call */);
    __ pop_call_clobbered_registers();
    __ leave();
  }
}

void ShenandoahBarrierSetAssembler::card_barrier(MacroAssembler* masm, Register obj) {
  assert(ShenandoahCardBarrier, "Should have been checked by caller");

  __ srli(obj, obj, CardTable::card_shift());

  assert(CardTable::dirty_card_val() == 0, "must be");

  Address curr_ct_holder_addr(xthread, in_bytes(ShenandoahThreadLocalData::card_table_offset()));
  __ ld(t1, curr_ct_holder_addr);
  __ add(t1, obj, t1);

  if (UseCondCardMark) {
    Label L_already_dirty;
    __ lbu(t0, Address(t1));
    __ beqz(t0, L_already_dirty);
    __ sb(zr, Address(t1));
    __ bind(L_already_dirty);
  } else {
    __ sb(zr, Address(t1));
  }
}

void ShenandoahBarrierSetAssembler::store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                             Address dst, Register val, Register tmp1, Register tmp2, Register tmp3) {
  // 1: non-reference types require no barriers
  if (!is_reference_type(type)) {
    BarrierSetAssembler::store_at(masm, decorators, type, dst, val, tmp1, tmp2, tmp3);
    return;
  }

  // Flatten object address right away for simplicity: likely needed by barriers
  if (dst.offset() == 0) {
    if (dst.base() != tmp3) {
      __ mv(tmp3, dst.base());
    }
  } else {
    __ la(tmp3, dst);
  }

  bool storing_non_null = (val != noreg);

  // 2: pre-barrier: SATB needs the previous value
  if (ShenandoahBarrierSet::need_satb_barrier(decorators, type)) {
    satb_barrier(masm,
                 tmp3 /* obj */,
                 tmp2 /* pre_val */,
                 xthread /* thread */,
                 tmp1 /* tmp */,
                 t0 /* tmp2 */,
                 storing_non_null /* tosca_live */,
                 false /* expand_call */);
  }

  // Store!
  BarrierSetAssembler::store_at(masm, decorators, type, Address(tmp3, 0), val, noreg, noreg, noreg);

  // 3: post-barrier: card barrier needs store address
  if (ShenandoahBarrierSet::need_card_barrier(decorators, type) && storing_non_null) {
    card_barrier(masm, tmp3);
  }
}

void ShenandoahBarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm, Register jni_env,
                                                                  Register obj, Register tmp, Label& slowpath) {
  Label done;
  // Resolve jobject
  BarrierSetAssembler::try_resolve_jobject_in_native(masm, jni_env, obj, tmp, slowpath);

  // Check for null.
  __ beqz(obj, done);

  assert(obj != t1, "need t1");
  Address gc_state(jni_env, ShenandoahThreadLocalData::gc_state_offset() - JavaThread::jni_environment_offset());
  __ lbu(t1, gc_state);

  // Check for heap in evacuation phase
  __ test_bit(t0, t1, ShenandoahHeap::EVACUATION_BITPOS);
  __ bnez(t0, slowpath);

  __ bind(done);
}

#ifdef COMPILER2
void ShenandoahBarrierSetAssembler::try_resolve_weak_handle_in_c2(MacroAssembler *masm, Register obj,
                                                                  Register tmp, Label& slow_path) {
  assert_different_registers(obj, tmp);

  Label done;

  // Resolve weak handle using the standard implementation.
  BarrierSetAssembler::try_resolve_weak_handle_in_c2(masm, obj, tmp, slow_path);

  // Check if the reference is null, and if it is, take the fast path.
  __ beqz(obj, done);

  Address gc_state(xthread, ShenandoahThreadLocalData::gc_state_offset());
  __ lbu(tmp, gc_state);

  // Check if the heap is under weak-reference/roots processing, in
  // which case we need to take the slow path.
  __ test_bit(tmp, tmp, ShenandoahHeap::WEAK_ROOTS_BITPOS);
  __ bnez(tmp, slow_path);
  __ bind(done);
}
#endif

// Special Shenandoah CAS implementation that handles false negatives due
// to concurrent evacuation.  The service is more complex than a
// traditional CAS operation because the CAS operation is intended to
// succeed if the reference at addr exactly matches expected or if the
// reference at addr holds a pointer to a from-space object that has
// been relocated to the location named by expected.  There are two
// races that must be addressed:
//  a) A parallel thread may mutate the contents of addr so that it points
//     to a different object.  In this case, the CAS operation should fail.
//  b) A parallel thread may heal the contents of addr, replacing a
//     from-space pointer held in addr with the to-space pointer
//     representing the new location of the object.
// Upon entry to cmpxchg_oop, it is assured that new_val equals null
// or it refers to an object that is not being evacuated out of
// from-space, or it refers to the to-space version of an object that
// is being evacuated out of from-space.
//
// By default the value held in the result register following execution
// of the generated code sequence is 0 to indicate failure of CAS,
// non-zero to indicate success. If is_cae, the result is the value most
// recently fetched from addr rather than a boolean success indicator.
//
// Clobbers t0, t1
void ShenandoahBarrierSetAssembler::cmpxchg_oop(MacroAssembler* masm,
                                                Register addr,
                                                Register expected,
                                                Register new_val,
                                                Assembler::Aqrl acquire,
                                                Assembler::Aqrl release,
                                                bool is_cae,
                                                Register result) {
  bool is_narrow = UseCompressedOops;
  Assembler::operand_size size = is_narrow ? Assembler::uint32 : Assembler::int64;

  assert_different_registers(addr, expected, t0, t1);
  assert_different_registers(addr, new_val, t0, t1);

  Label retry, success, fail, done;

  __ bind(retry);

  // Step1: Try to CAS.
  __ cmpxchg(addr, expected, new_val, size, acquire, release, /* result */ t1);

  // If success, then we are done.
  __ beq(expected, t1, success);

  // Step2: CAS failed, check the forwarded pointer.
  __ mv(t0, t1);

  if (is_narrow) {
    __ decode_heap_oop(t0, t0);
  }
  resolve_forward_pointer(masm, t0);

  __ encode_heap_oop(t0, t0);

  // Report failure when the forwarded oop was not expected.
  __ bne(t0, expected, fail);

  // Step 3: CAS again using the forwarded oop.
  __ cmpxchg(addr, t1, new_val, size, acquire, release, /* result */ t0);

  // Retry when failed.
  __ bne(t0, t1, retry);

  __ bind(success);
  if (is_cae) {
    __ mv(result, expected);
  } else {
    __ mv(result, 1);
  }
  __ j(done);

  __ bind(fail);
  if (is_cae) {
    __ mv(result, t0);
  } else {
    __ mv(result, zr);
  }

  __ bind(done);
}

void ShenandoahBarrierSetAssembler::gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                                                     Register start, Register count, Register tmp) {
  assert(ShenandoahCardBarrier, "Did you mean to enable ShenandoahCardBarrier?");

  Label L_loop, L_done;
  const Register end = count;

  // Zero count? Nothing to do.
  __ beqz(count, L_done);

  // end = start + count << LogBytesPerHeapOop
  // last element address to make inclusive
  __ shadd(end, count, start, tmp, LogBytesPerHeapOop);
  __ subi(end, end, BytesPerHeapOop);
  __ srli(start, start, CardTable::card_shift());
  __ srli(end, end, CardTable::card_shift());

  // number of bytes to copy
  __ sub(count, end, start);

  Address curr_ct_holder_addr(xthread, in_bytes(ShenandoahThreadLocalData::card_table_offset()));
  __ ld(tmp, curr_ct_holder_addr);
  __ add(start, start, tmp);

  __ bind(L_loop);
  __ add(tmp, start, count);
  __ sb(zr, Address(tmp));
  __ subi(count, count, 1);
  __ bgez(count, L_loop);
  __ bind(L_done);
}

#undef __

#ifdef COMPILER1

#define __ ce->masm()->

void ShenandoahBarrierSetAssembler::gen_pre_barrier_stub(LIR_Assembler* ce, ShenandoahPreBarrierStub* stub) {
  ShenandoahBarrierSetC1* bs = (ShenandoahBarrierSetC1*)BarrierSet::barrier_set()->barrier_set_c1();
  // At this point we know that marking is in progress.
  // If do_load() is true then we have to emit the
  // load of the previous value; otherwise it has already
  // been loaded into _pre_val.
  __ bind(*stub->entry());

  assert(stub->pre_val()->is_register(), "Precondition.");

  Register pre_val_reg = stub->pre_val()->as_register();

  if (stub->do_load()) {
    ce->mem2reg(stub->addr(), stub->pre_val(), T_OBJECT, stub->patch_code(), stub->info(), false /* wide */);
  }
  __ beqz(pre_val_reg, *stub->continuation(), /* is_far */ true);
  ce->store_parameter(stub->pre_val()->as_register(), 0);
  __ far_call(RuntimeAddress(bs->pre_barrier_c1_runtime_code_blob()->code_begin()));
  __ j(*stub->continuation());
}

void ShenandoahBarrierSetAssembler::gen_load_reference_barrier_stub(LIR_Assembler* ce,
                                                                    ShenandoahLoadReferenceBarrierStub* stub) {
  ShenandoahBarrierSetC1* bs = (ShenandoahBarrierSetC1*)BarrierSet::barrier_set()->barrier_set_c1();
  __ bind(*stub->entry());

  DecoratorSet decorators = stub->decorators();
  bool is_strong  = ShenandoahBarrierSet::is_strong_access(decorators);
  bool is_weak    = ShenandoahBarrierSet::is_weak_access(decorators);
  bool is_phantom = ShenandoahBarrierSet::is_phantom_access(decorators);
  bool is_native  = ShenandoahBarrierSet::is_native_access(decorators);

  Register obj = stub->obj()->as_register();
  Register res = stub->result()->as_register();
  Register addr = stub->addr()->as_pointer_register();
  Register tmp1 = stub->tmp1()->as_register();
  Register tmp2 = stub->tmp2()->as_register();

  assert(res == x10, "result must arrive in x10");
  assert_different_registers(tmp1, tmp2, t0);

  if (res != obj) {
    __ mv(res, obj);
  }

  if (is_strong) {
    // Check for object in cset.
    __ mv(tmp2, ShenandoahHeap::in_cset_fast_test_addr());
    __ srli(tmp1, res, ShenandoahHeapRegion::region_size_bytes_shift_jint());
    __ add(tmp2, tmp2, tmp1);
    __ lbu(tmp2, Address(tmp2));
    __ beqz(tmp2, *stub->continuation(), true /* is_far */);
  }

  ce->store_parameter(res, 0);
  ce->store_parameter(addr, 1);

  if (is_strong) {
    if (is_native) {
      __ far_call(RuntimeAddress(bs->load_reference_barrier_strong_native_rt_code_blob()->code_begin()));
    } else {
      __ far_call(RuntimeAddress(bs->load_reference_barrier_strong_rt_code_blob()->code_begin()));
    }
  } else if (is_weak) {
    __ far_call(RuntimeAddress(bs->load_reference_barrier_weak_rt_code_blob()->code_begin()));
  } else {
    assert(is_phantom, "only remaining strength");
    __ far_call(RuntimeAddress(bs->load_reference_barrier_phantom_rt_code_blob()->code_begin()));
  }

  __ j(*stub->continuation());
}

#undef __

#define __ sasm->

void ShenandoahBarrierSetAssembler::generate_c1_pre_barrier_runtime_stub(StubAssembler* sasm) {
  __ prologue("shenandoah_pre_barrier", false);

  // arg0 : previous value of memory

  BarrierSet* bs = BarrierSet::barrier_set();

  const Register pre_val = x10;
  const Register thread = xthread;
  const Register tmp = t0;

  Address queue_index(thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset()));
  Address buffer(thread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset()));

  Label done;
  Label runtime;

  // Is marking still active?
  Address gc_state(thread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
  __ lb(tmp, gc_state);
  __ test_bit(tmp, tmp, ShenandoahHeap::MARKING_BITPOS);
  __ beqz(tmp, done);

  // Can we store original value in the thread's buffer?
  __ ld(tmp, queue_index);
  __ beqz(tmp, runtime);

  __ subi(tmp, tmp, wordSize);
  __ sd(tmp, queue_index);
  __ ld(t1, buffer);
  __ add(tmp, tmp, t1);
  __ load_parameter(0, t1);
  __ sd(t1, Address(tmp, 0));
  __ j(done);

  __ bind(runtime);
  __ push_call_clobbered_registers();
  __ load_parameter(0, pre_val);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_barrier_pre), pre_val);
  __ pop_call_clobbered_registers();
  __ bind(done);

  __ epilogue();
}

void ShenandoahBarrierSetAssembler::generate_c1_load_reference_barrier_runtime_stub(StubAssembler* sasm,
                                                                                    DecoratorSet decorators) {
  __ prologue("shenandoah_load_reference_barrier", false);
  // arg0 : object to be resolved

  __ push_call_clobbered_registers();
  __ load_parameter(0, x10);
  __ load_parameter(1, x11);

  bool is_strong  = ShenandoahBarrierSet::is_strong_access(decorators);
  bool is_weak    = ShenandoahBarrierSet::is_weak_access(decorators);
  bool is_phantom = ShenandoahBarrierSet::is_phantom_access(decorators);
  bool is_native  = ShenandoahBarrierSet::is_native_access(decorators);
  address target  = nullptr;
  if (is_strong) {
    if (is_native) {
      target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong);
    } else {
      if (UseCompressedOops) {
        target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong_narrow);
      } else {
        target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_strong);
      }
    }
  } else if (is_weak) {
    assert(!is_native, "weak must not be called off-heap");
    if (UseCompressedOops) {
      target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak_narrow);
    } else {
      target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_weak);
    }
  } else {
    assert(is_phantom, "only remaining strength");
    assert(is_native, "phantom must only be called off-heap");
    target = CAST_FROM_FN_PTR(address, ShenandoahRuntime::load_reference_barrier_phantom);
  }
  __ rt_call(target);
  __ mv(t0, x10);
  __ pop_call_clobbered_registers();
  __ mv(x10, t0);

  __ epilogue();
}

#undef __

#endif // COMPILER1

#ifdef COMPILER2

#undef __
#define __ masm.

bool ShenandoahBarrierStubC2::push_save_register_if_live(MacroAssembler& masm, Register reg) {
  if (is_live(reg)) {
    push_save_register(masm, reg);
    return true;
  } else {
    return false;
  }
}

void ShenandoahBarrierStubC2::push_save_register(MacroAssembler& masm, Register reg) {
  __ sw(reg, Address(sp, push_save_slot()));
}

void ShenandoahBarrierStubC2::pop_save_register(MacroAssembler& masm, Register reg) {
  __ ld(reg, Address(sp, pop_save_slot()));
}

Register ShenandoahBarrierStubC2::select_temp_register(bool& selected_live, Address addr, Register reg1) {
  Register tmp = noreg;
  Register fallback_live = noreg;

  // Try to select non-live first:
  for (int i = 0; i < Register::number_of_registers; i++) {
    Register r = as_Register(i);
    if (r != fp && r != sp &&
        r != xheapbase && r != xthread &&
        r != t0 && r != t1 && r != zr &&
        r != reg1 && r != addr.base() && r != addr.index()) {
      if (!is_live(r)) {
        tmp = r;
        break;
      } else if (fallback_live == noreg) {
        fallback_live = r;
      }
    }
  }

  // If we could not find a non-live register, select the live fallback:
  if (tmp == noreg) {
    tmp = fallback_live;
    selected_live = true;
  } else {
    selected_live = false;
  }

  assert(tmp != noreg, "successfully selected");
  assert_different_registers(tmp, reg1);
  assert_different_registers(tmp, addr.base());
  assert_different_registers(tmp, addr.index());
  return tmp;
}

void ShenandoahBarrierStubC2::enter_if_gc_state(MacroAssembler& masm, const char test_state) {
  // Emit the unconditional branch in the first version of the method.
  // Let the rest of runtime figure out how to manage it.
  __ relocate(barrier_Relocation::spec());
  __ j(*entry());

#ifdef ASSERT
  Address gc_state_fast(xthread, in_bytes(ShenandoahThreadLocalData::gc_state_fast_offset()));
  __ ld(t0, gc_state_fast);
  __ beqz(t0, *continuation());
  __ illegal_instruction(Assembler::csr::time); // Correctness bug: barrier is NOP-ed, but heap is NOT IDLE
#endif
  __ bind(*continuation());
}

address ShenandoahBarrierSetAssembler::parse_stub_address(address pc) {
  Unimplemented();
  return nullptr;
}

void ShenandoahBarrierSetAssembler::patch_branch_to_nop(address pc) {
  Unimplemented();
}

void ShenandoahBarrierSetAssembler::patch_nop_to_branch(address pc, address stub_addr) {
  Unimplemented();
}

#undef __
#define __ masm->

void ShenandoahBarrierSetAssembler::compare_and_set_c2(const MachNode* node, MacroAssembler* masm, Register res, Register addr,
    Register oldval, Register newval, Register tmp, bool exchange, bool maybe_null, bool narrow, bool weak, bool is_acquire) {
  const Assembler::Aqrl acquire = is_acquire ? Assembler::aq : Assembler::relaxed;
  const Assembler::Aqrl release = Assembler::rl;

  // Pre-barrier covers several things:
  //  a. Avoids false positives from CAS encountering to-space memory values.
  //  b. Satisfies the need for LRB for the CAE result.
  //  c. Records old value for the sake of SATB.
  //
  // (a) and (b) are covered because load barrier does memory location fixup.
  // (c) is covered by KA on the current memory value.
  if (ShenandoahBarrierStubC2::needs_slow_barrier(node)) {
    ShenandoahBarrierStubC2* const stub = ShenandoahBarrierStubC2::create(node, tmp, Address(addr, 0), narrow, /* do_load: */ true);
    char check = 0;
    check |= ShenandoahBarrierStubC2::needs_keep_alive_barrier(node) ? ShenandoahHeap::MARKING : 0;
    check |= ShenandoahBarrierStubC2::needs_load_ref_barrier(node)   ? ShenandoahHeap::HAS_FORWARDED : 0;
    assert(!ShenandoahBarrierStubC2::needs_load_ref_barrier_weak(node), "Not supported for CAS/CAE");
    stub->enter_if_gc_state(*masm, check);
  }

  // Existing RISCV cmpxchg_oop already handles Shenandoah forwarded-value retry logic.
  // It returns:
  //   - boolean 0/1 for CAS (!exchange)
  //   - loaded/current value for CAE (exchange)
  ShenandoahBarrierSet::assembler()->cmpxchg_oop(masm, addr, oldval, newval, acquire, release, exchange /* is_cae */, res);

  // Post-barrier deals with card updates.
  card_barrier_c2(node, masm, Address(addr, 0));
}

void ShenandoahBarrierSetAssembler::get_and_set_c2(const MachNode* node, MacroAssembler* masm, Register preval,
    Register newval, Register addr, Register tmp, bool is_acquire) {
  const bool narrow = node->bottom_type()->isa_narrowoop();

  // Pre-barrier covers several things:
  //  a. Satisfies the need for LRB for the GAS result.
  //  b. Records old value for the sake of SATB.
  //
  // (a) is covered because load barrier does memory location fixup.
  // (b) is covered by KA on the current memory value.
  if (ShenandoahBarrierStubC2::needs_slow_barrier(node)) {
    ShenandoahBarrierStubC2* const stub = ShenandoahBarrierStubC2::create(node, tmp, Address(addr, 0), narrow, /* do_load: */ true);
    char check = 0;
    check |= ShenandoahBarrierStubC2::needs_keep_alive_barrier(node) ? ShenandoahHeap::MARKING : 0;
    check |= ShenandoahBarrierStubC2::needs_load_ref_barrier(node)   ? ShenandoahHeap::HAS_FORWARDED : 0;
    assert(!ShenandoahBarrierStubC2::needs_load_ref_barrier_weak(node), "Not supported for GAS");
    stub->enter_if_gc_state(*masm, check);
  }

  if (narrow) {
    if (is_acquire) {
      __ atomic_xchgalwu(preval, newval, addr);
    } else {
      __ atomic_xchgwu(preval, newval, addr);
    }
  } else {
    if (is_acquire) {
      __ atomic_xchgal(preval, newval, addr);
    } else {
      __ atomic_xchg(preval, newval, addr);
    }
  }

  // Post-barrier deals with card updates.
  card_barrier_c2(node, masm, Address(addr, 0));
}

void ShenandoahBarrierSetAssembler::store_c2(const MachNode* node, MacroAssembler* masm, Address dst, bool dst_narrow,
    Register src, bool src_narrow, Register tmp, bool is_volatile) {

  // Pre-barrier: SATB / keep-alive on current value in memory.
  if (ShenandoahBarrierStubC2::needs_slow_barrier(node)) {
    assert(!ShenandoahBarrierStubC2::needs_load_ref_barrier(node), "Should not be required for stores");
    ShenandoahBarrierStubC2* const stub = ShenandoahBarrierStubC2::create(node, tmp, dst, dst_narrow, /* do_load: */ true);
    stub->enter_if_gc_state(*masm, ShenandoahHeap::MARKING);
  }

  // Do the actual store
  if (dst_narrow) {
    Register actual_src = src;
    if (!src_narrow) {
      assert(tmp != noreg, "need temp register");
      __ mv(tmp, src);
      if (ShenandoahBarrierStubC2::maybe_null(node)) {
        __ encode_heap_oop(tmp, tmp);
      } else {
        __ encode_heap_oop_not_null(tmp, tmp);
      }
      actual_src = tmp;
    }

    if (is_volatile) {
      __ membar(MacroAssembler::StoreStore | MacroAssembler::LoadStore);
      __ sw(actual_src, dst);
    } else {
      __ sw(actual_src, dst);
    }
  } else {
    if (is_volatile) {
      __ membar(MacroAssembler::StoreStore | MacroAssembler::LoadStore);
      __ sd(src, dst);
    } else {
      __ sd(src, dst);
    }
  }

  // Post-barrier: card updates.
  card_barrier_c2(node, masm, dst);
}

void ShenandoahBarrierSetAssembler::load_c2(const MachNode* node, MacroAssembler* masm, Register dst, Address src, bool is_acquire) {
  const bool narrow = node->bottom_type()->isa_narrowoop();

  // Do the actual load. This load is the candidate for implicit null check, and MUST come first.
  if (narrow) {
    __ lwu(dst, src);
    if (is_acquire) {
      __ membar(MacroAssembler::LoadLoad | MacroAssembler::LoadStore);
    }
  } else {
    __ ld(dst, src);
    if (is_acquire) {
      __ membar(MacroAssembler::LoadLoad | MacroAssembler::LoadStore);
    }
  }

  // Post-barrier: LRB / KA / weak-root processing.
  if (ShenandoahBarrierStubC2::needs_slow_barrier(node)) {
    ShenandoahBarrierStubC2* const stub = ShenandoahBarrierStubC2::create(node, dst, src, narrow, /* do_load: */ false);
    char check = 0;
    check |= ShenandoahBarrierStubC2::needs_keep_alive_barrier(node)    ? ShenandoahHeap::MARKING : 0;
    check |= ShenandoahBarrierStubC2::needs_load_ref_barrier(node)      ? ShenandoahHeap::HAS_FORWARDED : 0;
    check |= ShenandoahBarrierStubC2::needs_load_ref_barrier_weak(node) ? ShenandoahHeap::WEAK_ROOTS : 0;
    stub->enter_if_gc_state(*masm, check);
  }
}

void ShenandoahBarrierSetAssembler::card_barrier_c2(const MachNode* node, MacroAssembler* masm, Address address) {
  if (!ShenandoahBarrierStubC2::needs_card_barrier(node)) {
    return;
  }

  assert(CardTable::dirty_card_val() == 0, "must be");
  Assembler::InlineSkippedInstructionsCounter skip_counter(masm);

  // t1 = effective address
  __ la(t1, address);

  // t1 = card index
  __ srli(t1, t1, CardTable::card_shift());

  // t0 = card table base
  Address curr_ct_holder_addr(xthread, in_bytes(ShenandoahThreadLocalData::card_table_offset()));
  __ ld(t0, curr_ct_holder_addr);

  // t1 = &card_table[card_index]
  __ add(t1, t1, t0);

  if (UseCondCardMark) {
    Label L_already_dirty;
    __ lbu(t0, Address(t1));
    __ beqz(t0, L_already_dirty);
    __ sb(zr, Address(t1));
    __ bind(L_already_dirty);
  } else {
    __ sb(zr, Address(t1));
  }
}

#undef __
#define __ masm.

void ShenandoahBarrierStubC2::emit_code(MacroAssembler& masm) {
  Assembler::InlineSkippedInstructionsCounter skip_counter(&masm);

  assert(_needs_keep_alive_barrier || _needs_load_ref_barrier, "Why are you here?");

  Label L_done;

  // Stub entry
  __ bind(*BarrierStubC2::entry());

  // If needed, perform the load here so stub logic sees the current oop value.
  if (_do_load) {
    __ load_heap_oop(_obj, _addr, noreg, noreg, AS_RAW);
  } else if (_narrow) {
    // Decode narrow oop before barrier processing.
    if (_maybe_null) {
      __ decode_heap_oop(_obj, _obj);
    } else {
      __ decode_heap_oop_not_null(_obj, _obj);
    }
  }

  if (_do_load || _maybe_null) {
    __ beqz(_obj, L_done);
  }

  keepalive(masm, _obj, t0);

  lrb(masm, _obj, _addr, noreg);

  // If object is narrow, we need to encode it before exiting.
  // For encoding, dst can only turn null if we are dealing with weak loads.
  // Otherwise, we have already null-checked. We can skip all this if we performed
  // the load ourselves, which means the value is not used by caller.
  if (_narrow && !_do_load) {
    if (_needs_load_ref_weak_barrier) {
      __ encode_heap_oop(_obj, _obj);
    } else {
      __ encode_heap_oop_not_null(_obj, _obj);
    }
  }

  // Go back to fast path
  __ bind(L_done);
  __ j(*continuation());
}

void ShenandoahBarrierStubC2::keepalive(MacroAssembler& masm, Register obj, Register tmp1, Label* L_done_unused) {
  Address index(xthread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_index_offset()));
  Address buffer(xthread, in_bytes(ShenandoahThreadLocalData::satb_mark_queue_buffer_offset()));
  Label L_runtime;
  Label L_done;

  // The node doesn't even need keepalive barrier, just don't check anything else
  if (!_needs_keep_alive_barrier) {
    return;
  }

  // If both LRB and KeepAlive barriers are required (rare), do a runtime check
  // for enabled barrier.
  if (_needs_load_ref_barrier) {
    Address gcs_addr(xthread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
    __ lbu(t0, gcs_addr);
    __ test_bit(t0, t0, ShenandoahHeap::MARKING_BITPOS);
    __ beqz(t0, L_done);
  }

  // If the queue is full, go to runtime.
  __ ld(tmp1, index);
  __ beqz(tmp1, L_runtime);

  bool selected_live = false;
  Register tmp2 = select_temp_register(selected_live, _addr, obj);
  if (selected_live) {
    push_save_register(masm, tmp2);
  }

  // Push into SATB queue.
  __ subi(tmp1, tmp1, wordSize);
  __ sd(tmp1, index);
  __ ld(tmp2, buffer);
  __ add(tmp1, tmp1, tmp2);
  __ sd(obj, Address(tmp1, 0));
  __ j(L_done);

  // Runtime call
  __ bind(L_runtime);

  preserve(obj);
  {
    SaveLiveRegisters save_registers(&masm, this);
    __ mv(c_rarg0, obj);
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, ShenandoahRuntime::write_barrier_pre), c_rarg0);
  }

  __ bind(L_done);

  if (selected_live) {
    pop_save_register(masm, tmp2);
  }
}

void ShenandoahBarrierStubC2::lrb(MacroAssembler& masm, Register obj, Address addr, Register tmp, Label* L_done_unused) {
  Label L_done;

  // The node doesn't even need LRB barrier, just don't check anything else
  if (!_needs_load_ref_barrier) {
    return;
  }

  if ((_node->barrier_data() & ShenandoahBitStrong) != 0) {
    // If both LRB and KeepAlive barriers are required (rare), do a runtime
    // check for enabled barrier.
    if (_needs_keep_alive_barrier) {
      Address gcs_addr(xthread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
      __ lbu(t0, gcs_addr);

      if (_needs_load_ref_weak_barrier) {
        __ srli(t1, t0, ShenandoahHeap::WEAK_ROOTS_BITPOS);
        __ orr(t0, t0, t1);
      }

      __ test_bit(t0, t0, ShenandoahHeap::HAS_FORWARDED_BITPOS);
      __ beqz(t0, L_done);
    }

    // Weak/phantom loads always need to go to runtime. For strong refs we
    // check if the object in cset, if they are not, then we are done with LRB.
    __ mv(t1, ShenandoahHeap::in_cset_fast_test_addr());
    __ srli(t0, obj, ShenandoahHeapRegion::region_size_bytes_shift_jint());
    __ add(t1, t1, t0);
    __ lbu(t1, Address(t1));
    __ beqz(t1, L_done);
  }

  dont_preserve(obj);
  {
    SaveLiveRegisters save_registers(&masm, this);

    // Runtime call wants:
    //   c_rarg0 <- obj
    //   c_rarg1 <- lea(addr)
    if (c_rarg0 == obj) {
      __ la(c_rarg1, addr);
    } else if (c_rarg1 == obj) {
      // Set up arguments in reverse, and then flip them
      __ la(c_rarg0, addr);
      // flip them
      __ mv(t0, c_rarg0);
      __ mv(c_rarg0, c_rarg1);
      __ mv(c_rarg1, t0);
    } else {
      assert_different_registers(c_rarg1, obj);
      __ la(c_rarg1, addr);
      __ mv(c_rarg0, obj);
    }

    // Get address of runtime LRB entry and call it
    __ rt_call(lrb_runtime_entry_addr());

    // If we loaded the object in the stub it means we don't need to return it
    // to fastpath, so no need to make this mov.
    if (!_do_load) {
      __ mv(obj, x10);
    }
  }

  __ bind(L_done);
}

void ShenandoahBarrierStubC2::post_init(int offset) {
  // Do nothing.
}
#endif // COMPILER2
