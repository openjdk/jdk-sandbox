/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, 2021, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef CPU_AARCH64_GC_SHENANDOAH_SHENANDOAHBARRIERSETASSEMBLER_AARCH64_HPP
#define CPU_AARCH64_GC_SHENANDOAH_SHENANDOAHBARRIERSETASSEMBLER_AARCH64_HPP

#include "asm/macroAssembler.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#ifdef COMPILER1
class LIR_Assembler;
class ShenandoahPreBarrierStub;
class ShenandoahLoadReferenceBarrierStub;
class StubAssembler;
#endif
#ifdef COMPILER2
class MachNode;
#endif // COMPILER2
class StubCodeGenerator;

// Barriers on aarch64 are implemented with a test-and-branch immediate instruction.
// This immediate has a max delta of 32K. Because of this the branch is implemented with
// a small jump, as follows:
//      __ tbz(gcs, bits_to_check, L_short_branch);
//      __ b(*stub->entry());
//      __ bind(L_short_branch);
//
// If we can guarantee that the *stub->entry() label is within 32K we can replace the above
// code with:
//      __ tbnz(gcs, bits_to_check, *stub->entry());
//
// From the branch shortening part of PhaseOutput we get a pessimistic code size that the code
// will not grow beyond.
//
// The stubs objects are created and registered when the barriers are emitted. The decision
// between emitting the long branch or the test and branch is done at this point and uses the
// pessimistic code size from branch shortening.
//
// After the code has been emitted the barrier set will emit all the stubs. When the stubs are
// emitted we know the real code size. Because of this the trampoline jump can be skipped in
// favour of emitting the stub directly if it does not interfere with the next trampoline stub.
// (With respect to test and branch distance)
//
// The algorithm for emitting the load barrier branches and stubs now have three versions
// depending on the distance between the barrier and the stub.
// Version 1: Not Reachable with a test-and-branch immediate
// Version 2: Reachable with a test-and-branch immediate via trampoline
// Version 3: Reachable with a test-and-branch immediate without trampoline
//
//     +--------------------- Code ----------------------+
//     |                      ***                        |
//     | tbz(gcs, bits_to_check, L_short_branch);        |
//     | b(stub1)                                        | (Version 1)
//     | bind(L_short_branch);                           |
//     |                      ***                        |
//     | tbnz(gcs, bits_to_check, tramp)                 | (Version 2)
//     |                      ***                        |
//     | tbnz(gcs, bits_to_check, stub3)                 | (Version 3)
//     |                      ***                        |
//     +--------------------- Stub ----------------------+
//     | tramp: b(stub2)                                 | (Trampoline slot)
//     | stub3:                                          |
//     |                  * Stub Code*                   |
//     | stub1:                                          |
//     |                  * Stub Code*                   |
//     | stub2:                                          |
//     |                  * Stub Code*                   |
//     +-------------------------------------------------+
//
//  Version 1: Is emitted if the pessimistic distance between the branch instruction and the current
//             trampoline slot cannot fit in a test and branch immediate.
//
//  Version 2: Is emitted if the distance between the branch instruction and the current trampoline
//             slot can fit in a test and branch immediate. But emitting the stub directly would
//             interfere with the next trampoline.
//
//  Version 3: Same as version two but emitting the stub directly (skipping the trampoline) does not
//             interfere with the next trampoline.
//
class ShenandoahBarrierSetAssembler: public BarrierSetAssembler {
  friend class ShenandoahCASBarrierSlowStub;
private:

  void satb_barrier(MacroAssembler* masm,
                    Register obj,
                    Register pre_val,
                    Register thread,
                    Register tmp1,
                    Register tmp2,
                    bool tosca_live,
                    bool expand_call);

  void card_barrier(MacroAssembler* masm, Register obj);

  void resolve_forward_pointer(MacroAssembler* masm, Register dst, Register tmp = noreg);
  void resolve_forward_pointer_not_null(MacroAssembler* masm, Register dst, Register tmp = noreg);
  void load_reference_barrier(MacroAssembler* masm, Register dst, Address load_addr, DecoratorSet decorators);

  void gen_write_ref_array_post_barrier(MacroAssembler* masm, DecoratorSet decorators,
                                        Register start, Register count,
                                        Register scratch);

public:
  virtual NMethodPatchingType nmethod_patching_type() { return NMethodPatchingType::conc_instruction_and_data_patch; }

  void cmpxchg_oop(MacroAssembler* masm, Register addr, Register expected, Register new_val,
                   bool acquire, bool release, bool is_cae, Register result);
  virtual void arraycopy_prologue(MacroAssembler* masm, DecoratorSet decorators, bool is_oop,
                                  Register src, Register dst, Register count, RegSet saved_regs);
  virtual void arraycopy_epilogue(MacroAssembler* masm, DecoratorSet decorators, bool is_oop,
                                  Register start, Register count, Register tmp);
  virtual void load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                       Register dst, Address src, Register tmp1, Register tmp2);
  virtual void store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                        Address dst, Register val, Register tmp1, Register tmp2, Register tmp3);
  virtual void try_resolve_jobject_in_native(MacroAssembler* masm, Register jni_env,
                                             Register obj, Register tmp, Label& slowpath);

#ifdef COMPILER1
  void gen_pre_barrier_stub(LIR_Assembler* ce, ShenandoahPreBarrierStub* stub);
  void gen_load_reference_barrier_stub(LIR_Assembler* ce, ShenandoahLoadReferenceBarrierStub* stub);
  void generate_c1_pre_barrier_runtime_stub(StubAssembler* sasm);
  void generate_c1_load_reference_barrier_runtime_stub(StubAssembler* sasm, DecoratorSet decorators);
#endif

#ifdef COMPILER2
  // Barrier hotpatching
  static address parse_stub_address(address pc);
  static void patch_branch_to_nop(address pc);
  static void patch_nop_to_branch(address pc, address stub_addr);

  // Entry points from Matcher
  void load_c2(const MachNode* node, MacroAssembler* masm, Register dst, Address addr, bool is_narrow, bool is_acquire);
  void store_c2(const MachNode* node, MacroAssembler* masm, Address dst, bool dst_narrow, Register src, bool src_narrow, bool is_volatile);
  void compare_and_set_c2(const MachNode* node, MacroAssembler* masm, Register res, Register addr, Register oldval,
      Register newval, bool exchange, bool narrow, bool weak, bool acquire);
  void get_and_set_c2(const MachNode* node, MacroAssembler* masm, Register preval, Register newval, Register addr, bool acquire);
  void card_barrier_c2(const MachNode* node, MacroAssembler* masm, Address addr);
  virtual void try_resolve_weak_handle_in_c2(MacroAssembler* masm, Register obj, Register tmp, Label& slow_path);
#endif
};

#endif // CPU_AARCH64_GC_SHENANDOAH_SHENANDOAHBARRIERSETASSEMBLER_AARCH64_HPP
