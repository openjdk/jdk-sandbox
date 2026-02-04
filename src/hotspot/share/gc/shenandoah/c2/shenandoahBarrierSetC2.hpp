/*
 * Copyright (c) 2018, 2021, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_C2_SHENANDOAHBARRIERSETC2_HPP
#define SHARE_GC_SHENANDOAH_C2_SHENANDOAHBARRIERSETC2_HPP

#include "gc/shared/c2/barrierSetC2.hpp"
#include "shenandoahBarrierSetC2.hpp"
#include "utilities/growableArray.hpp"

static const uint8_t ShenandoahBarrierStrong          = 1 << 0;
static const uint8_t ShenandoahBarrierWeak            = 1 << 1;
static const uint8_t ShenandoahBarrierPhantom         = 1 << 2;
static const uint8_t ShenandoahBarrierNative          = 1 << 3;
static const uint8_t ShenandoahBarrierElided          = 1 << 4;
static const uint8_t ShenandoahBarrierSATB            = 1 << 5;
static const uint8_t ShenandoahBarrierCardMark        = 1 << 6;
static const uint8_t ShenandoahBarrierCardMarkNotNull = 1 << 7;

class ShenandoahBarrierStubC2;

class ShenandoahBarrierSetC2State : public BarrierSetC2State {
  GrowableArray<ShenandoahBarrierStubC2*>* _stubs;
  int _stubs_start_offset;

public:
  explicit ShenandoahBarrierSetC2State(Arena* comp_arena);

  bool needs_liveness_data(const MachNode* mach) const override;
  bool needs_livein_data() const override;

  GrowableArray<ShenandoahBarrierStubC2*>* stubs() {
    return _stubs;
  }

  void set_stubs_start_offset(int offset) {
    _stubs_start_offset = offset;
  }

  int stubs_start_offset() {
    return _stubs_start_offset;
  }};

class ShenandoahBarrierSetC2 : public BarrierSetC2 {

  static bool clone_needs_barrier(const TypeOopPtr* src_type, bool& is_oop_array);

protected:
  virtual Node* load_at_resolved(C2Access& access, const Type* val_type) const;
  virtual Node* store_at_resolved(C2Access& access, C2AccessValue& val) const;
  virtual Node* atomic_cmpxchg_val_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                               Node* new_val, const Type* val_type) const;
  virtual Node* atomic_cmpxchg_bool_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                Node* new_val, const Type* value_type) const;
  virtual Node* atomic_xchg_at_resolved(C2AtomicParseAccess& access, Node* new_val, const Type* val_type) const;

public:
  static ShenandoahBarrierSetC2* bsc2();

  ShenandoahBarrierSetC2State* state() const;

  // This is the entry-point for the backend to perform accesses through the Access API.
  virtual void clone(GraphKit* kit, Node* src_base, Node* dst_base, Node* size, bool is_array) const;
  virtual void clone_at_expansion(PhaseMacroExpand* phase, ArrayCopyNode* ac) const;

  // These are general helper methods used by C2
  virtual bool array_copy_requires_gc_barriers(bool tightly_coupled_alloc, BasicType type, bool is_clone, bool is_clone_instance, ArrayCopyPhase phase) const;

  // Support for GC barriers emitted during parsing
  virtual bool expand_barriers(Compile* C, PhaseIterGVN& igvn) const;

  // Support for macro expanded GC barriers
  virtual void eliminate_gc_barrier(PhaseMacroExpand* macro, Node* node) const;
  virtual void eliminate_gc_barrier_data(Node* node) const;

  // Allow barrier sets to have shared state that is preserved across a compilation unit.
  // This could for example comprise macro nodes to be expanded during macro expansion.
  virtual void* create_barrier_state(Arena* comp_arena) const;

#ifdef ASSERT
  virtual void verify_gc_barriers(Compile* compile, CompilePhase phase) const;
  static void report_verify_failure(bool failed, const char* msg, Node* n);
#endif

  int estimate_stub_size() const /* override */;
  void emit_stubs(CodeBuffer& cb) const /* override */;
  void late_barrier_analysis() const /* override*/ {
    compute_liveness_at_stubs();
  }

};

class ShenandoahBarrierStubC2 : public BarrierStubC2 {
protected:
  explicit ShenandoahBarrierStubC2(const MachNode* node) : BarrierStubC2(node) {}
  void register_stub();
public:
  virtual void emit_code(MacroAssembler& masm) = 0;
};

class ShenandoahLoadRefBarrierStubC2 : public ShenandoahBarrierStubC2 {
  Register _obj;
  Register _addr;
  Register _tmp1;
  Register _tmp2;
  Register _tmp3;
  bool _narrow;
  ShenandoahLoadRefBarrierStubC2(const MachNode* node, Register obj, Register addr, Register tmp1, Register tmp2, Register tmp3, bool narrow) :
    ShenandoahBarrierStubC2(node), _obj(obj), _addr(addr), _tmp1(tmp1), _tmp2(tmp2), _tmp3(tmp3), _narrow(narrow) {}
public:
  static bool needs_barrier(const MachNode* node) {
    return (node->barrier_data() & (ShenandoahBarrierStrong | ShenandoahBarrierWeak | ShenandoahBarrierPhantom | ShenandoahBarrierNative)) != 0;
  }
  static ShenandoahLoadRefBarrierStubC2* create(const MachNode* node, Register obj, Register addr, Register tmp1, Register tmp2, Register tmp3, bool narrow);
  void emit_code(MacroAssembler& masm) override;
};

class ShenandoahSATBBarrierStubC2 : public ShenandoahBarrierStubC2 {
  Register _addr;
  Register _preval;
  Register _tmp;
  bool _encoded_preval;
  ShenandoahSATBBarrierStubC2(const MachNode* node, Register addr, Register preval, Register tmp, bool encoded_preval) :
    ShenandoahBarrierStubC2(node), _addr(addr), _preval(preval), _tmp(tmp), _encoded_preval(encoded_preval) {}

public:
  static bool needs_barrier(const MachNode* node) {
    return (node->barrier_data() & ShenandoahBarrierSATB) != 0;
  }
  static ShenandoahSATBBarrierStubC2* create(const MachNode* node, Register addr, Register preval, Register tmp, bool encoded_preval);

  void emit_code(MacroAssembler& masm) override;
};

class ShenandoahCASBarrierSlowStubC2 : public ShenandoahBarrierStubC2 {
  Register const _addr_reg;
  Address  const _addr;
  Register const _expected;
  Register const _new_val;
  Register const _result;
  Register const _tmp1;
  Register const _tmp2;
  bool     const _cae;
  bool     const _acquire;
  bool     const _release;
  bool     const _weak;

  explicit ShenandoahCASBarrierSlowStubC2(const MachNode* node, Register addr_reg, Address addr, Register expected, Register new_val, Register result, Register tmp1, Register tmp2, bool cae, bool acquire, bool release, bool weak) :
    ShenandoahBarrierStubC2(node),
    _addr_reg(addr_reg), _addr(addr), _expected(expected), _new_val(new_val), _result(result), _tmp1(tmp1), _tmp2(tmp2), _cae(cae), _acquire(acquire), _release(release),  _weak(weak) {}

public:
  static ShenandoahCASBarrierSlowStubC2* create(const MachNode* node, Register addr, Register expected, Register new_val, Register result, Register tmp1, Register tmp2, bool cae, bool acquire, bool release, bool weak);
  static ShenandoahCASBarrierSlowStubC2* create(const MachNode* node, Address addr, Register expected, Register new_val, Register result, Register tmp1, Register tmp2, bool cae);
  void emit_code(MacroAssembler& masm) override;
};

#endif // SHARE_GC_SHENANDOAH_C2_SHENANDOAHBARRIERSETC2_HPP
