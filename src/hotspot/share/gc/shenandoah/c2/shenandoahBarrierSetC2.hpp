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
#include "gc/shared/gc_globals.hpp"
#include "gc/shenandoah/shenandoahBarrierSetAssembler.hpp"
#include "gc/shenandoah/shenandoahRuntime.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "opto/machnode.hpp"
#include "utilities/growableArray.hpp"

static const uint8_t ShenandoahBitStrong    = 1 << 0; // Barrier: LRB, strong
static const uint8_t ShenandoahBitWeak      = 1 << 1; // Barrier: LRB, weak
static const uint8_t ShenandoahBitPhantom   = 1 << 2; // Barrier: LRB, phantom
static const uint8_t ShenandoahBitKeepAlive = 1 << 3; // Barrier: KeepAlive (SATB for stores, KA for loads)
static const uint8_t ShenandoahBitCardMark  = 1 << 4; // Barrier: CM
static const uint8_t ShenandoahBitNotNull   = 1 << 5; // Metadata: src/dst is not null
static const uint8_t ShenandoahBitNative    = 1 << 6; // Metadata: access is in native, not in heap
static const uint8_t ShenandoahBitElided    = 1 << 7; // Metadata: barrier is elided

// Barrier data that implies real barriers, not additional metadata.
static const uint8_t ShenandoahBitsReal = ShenandoahBitStrong | ShenandoahBitWeak | ShenandoahBitPhantom |
                                          ShenandoahBitKeepAlive |
                                          ShenandoahBitCardMark;

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
  }
};

class ShenandoahBarrierSetC2 : public BarrierSetC2 {

  static bool clone_needs_barrier(const TypeOopPtr* src_type, bool& is_oop_array);

  static bool can_remove_load_barrier(Node* node);

  static void refine_load(Node* node);
  static void refine_store(const Node* node);

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
  virtual bool array_copy_requires_gc_barriers(bool tightly_coupled_alloc, BasicType type, bool is_clone,
      bool is_clone_instance, ArrayCopyPhase phase) const;

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
  static void verify_gc_barrier_assert(bool cond, const char* msg, uint8_t bd, Node* n);
#endif

  int estimate_stub_size() const /* override */;
  void emit_stubs(CodeBuffer& cb) const /* override */;
  void late_barrier_analysis() const /* override*/ {
    compute_liveness_at_stubs();
    analyze_dominating_barriers();
  }

  void elide_dominated_barrier(MachNode* mach) const;
  void analyze_dominating_barriers() const;
  void strip_extra_data(const Node* node) const;
  void strip_extra_data(Node_List& accesses) const;

  virtual uint estimated_barrier_size(const Node* node) const;

  static void print_barrier_data(outputStream* os, uint8_t data);
};

class ShenandoahBarrierStubC2 : public BarrierStubC2 {
protected:
  explicit ShenandoahBarrierStubC2(const MachNode* node) : BarrierStubC2(node) {
    assert(!ShenandoahSkipBarriers, "Do not touch stubs when disabled");
  }
  void register_stub();
  static bool is_heap_access(const MachNode* node) {
    return (node->barrier_data() & ShenandoahBitNative) == 0;
  }
  void satb(MacroAssembler* masm, ShenandoahBarrierStubC2* stub, Register scratch1, Register scratch2, Register scratch3);
  void lrb(MacroAssembler* masm, ShenandoahBarrierStubC2* stub, Register obj, Register addr, Label* L_done, bool narrow);
  static Register select_temp_register(Address addr, Register reg1 = noreg, Register reg2 = noreg);

  void keepalive_fast(MacroAssembler* masm, Register obj, Register tmp, Label* L_slow, bool short_slow);
  void keepalive_slow(MacroAssembler* masm, Register obj);
  void lrb_fast(MacroAssembler* masm, Register obj, Register tmp, Label* L_slow, bool short_slow);
  void lrb_slow(MacroAssembler* masm, Register obj, Address addr, bool narrow);
  void gc_state_check(MacroAssembler* masm, const char state, Label* L_not_set);

public:
  static void gc_state_check_c2(MacroAssembler* masm, Register rscratch, const unsigned char test_state,
      BarrierStubC2* slow_stub);
  virtual void emit_code(MacroAssembler& masm) = 0;
};

class ShenandoahLoadBarrierStubC2 : public ShenandoahBarrierStubC2 {
  Register const _dst;
  Register _addr_reg; // Used on x64
  Address  const _src; // Used on aarch64
  const bool _do_load;
  const bool _narrow;
  const bool _maybe_null;
  const bool _needs_load_ref_barrier;
  const bool _needs_keep_alive_barrier;

  ShenandoahLoadBarrierStubC2(const MachNode* node, Register dst, Register addr_reg, Address src, bool narrow, bool do_load) :
    ShenandoahBarrierStubC2(node),
    _dst(dst),
    _addr_reg(addr_reg),
    _src(src),
    _do_load(do_load),
    _narrow(narrow),
    _maybe_null(!src_not_null(node)),
    _needs_load_ref_barrier(needs_load_ref_barrier(node)),
    _needs_keep_alive_barrier(needs_keep_alive_barrier(node)) {
      assert(!_narrow || is_heap_access(node), "Only heap accesses can be narrow");
    }

public:
  static bool needs_barrier(const MachNode* node) {
    return needs_load_ref_barrier(node) || needs_keep_alive_barrier(node);
  }
  static bool needs_keep_alive_barrier(const MachNode* node) {
    return (node->barrier_data() & ShenandoahBitKeepAlive) != 0;
  }
  static bool needs_load_ref_barrier(const MachNode* node) {
    return (node->barrier_data() & (ShenandoahBitStrong | ShenandoahBitWeak | ShenandoahBitPhantom)) != 0;
  }
  static bool needs_load_ref_barrier_weak(const MachNode* node) {
    return (node->barrier_data() & (ShenandoahBitWeak | ShenandoahBitPhantom)) != 0;
  }
  static bool src_not_null(const MachNode* node) {
    return (node->barrier_data() & ShenandoahBitNotNull) != 0;
  }
  static bool is_narrow_result(const MachNode* node) {
    return node->bottom_type()->isa_narrowoop() || node->ideal_Opcode() == Op_DecodeN;
  }
  static ShenandoahLoadBarrierStubC2* create(const MachNode* node, Register dst, Address addr, bool narrow, bool do_load);
  static void check_and_insert(const MachNode* node, MacroAssembler* masm, Register dst, Register addr,
      RegSet regsToPreserve = RegSet(), RegSet regsDontPreserve = RegSet());
  void emit_code(MacroAssembler& masm) override;
};

class ShenandoahStoreBarrierStubC2 : public ShenandoahBarrierStubC2 {
  Register const _addr_reg; // Used on aarch64
  Address const _dst; // Used on x64
  Register const _src;
  Register const _tmp;
  const bool _dst_narrow;
  const bool _src_narrow;

  ShenandoahStoreBarrierStubC2(const MachNode* node, Register addr_reg, Address dst, bool dst_narrow, Register src,
      bool src_narrow, Register tmp) :
    ShenandoahBarrierStubC2(node), _addr_reg(addr_reg), _dst(dst), _src(src), _tmp(tmp), _dst_narrow(dst_narrow),
      _src_narrow(src_narrow) {
      assert(!_dst_narrow || is_heap_access(node), "Only heap accesses can be narrow");
    }

public:
  static bool needs_barrier(const MachNode* node) {
    return needs_card_barrier(node) || needs_keep_alive_barrier(node);
  }
  static bool needs_keep_alive_barrier(const MachNode* node) {
    return (node->barrier_data() & ShenandoahBitKeepAlive) != 0;
  }
  static bool needs_card_barrier(const MachNode* node) {
    return (node->barrier_data() & ShenandoahBitCardMark) != 0;
  }
  static bool src_not_null(const MachNode* node) {
    return (node->barrier_data() & ShenandoahBitNotNull) != 0;
  }
  static ShenandoahStoreBarrierStubC2* create(const MachNode* node, Address addr, bool dst_narrow, Register src,
      bool src_narrow, Register tmp);
  static void check_and_insert(const MachNode* node, MacroAssembler* masm, Register addr, bool dst_narrow, Register src,
      bool src_narrow, Register tmp, RegSet regsToPreserve = RegSet(), RegSet regsDontPreserve = RegSet());
  void emit_code(MacroAssembler& masm) override;
};

class ShenandoahCASBarrierStubC2 : public ShenandoahBarrierStubC2 {
  Register _addr_reg; // Used on aarch64
  Address  _addr; // Used on x64
  Register _expected;
  Register _new_val;
  Register _result;
  Register _tmp1;
  Register _tmp2;
  bool     const _narrow;
  bool     const _cae;
  bool     const _maybe_null;
  bool     const _acquire;
  bool     const _weak;
  bool     const _needs_load_ref_barrier;
  bool     const _needs_keep_alive_barrier;

  explicit ShenandoahCASBarrierStubC2(const MachNode* node, Register addr_reg, Address addr, Register expected,
      Register new_val, Register result, Register tmp1, Register tmp2, bool narrow, bool cae, bool maybe_null,
      bool acquire, bool weak) :
    ShenandoahBarrierStubC2(node), _addr_reg(addr_reg), _addr(addr), _expected(expected), _new_val(new_val),
      _result(result), _tmp1(tmp1), _tmp2(tmp2), _narrow(narrow), _cae(cae), _maybe_null(maybe_null),
      _acquire(acquire),  _weak(weak),
      _needs_load_ref_barrier(needs_load_ref_barrier(node)),
      _needs_keep_alive_barrier(needs_keep_alive_barrier(node)) {
      assert(!_narrow || is_heap_access(node), "Only heap accesses can be narrow");
    }

public:
  static void check_and_insert(const MachNode* node, MacroAssembler* masm,
      Register res, Register addr, Register oldval, Register newval, bool exchange, bool maybe_null, bool narrow,
      bool weak, bool acquire, RegSet regsToPreserve = RegSet(), RegSet regsDontPreserve = RegSet());

  static bool needs_barrier(const MachNode* node) {
    return needs_card_barrier(node) || needs_load_ref_barrier(node) || needs_keep_alive_barrier(node);
  }
  static bool needs_keep_alive_barrier(const MachNode* node) {
    return (node->barrier_data() & ShenandoahBitKeepAlive) != 0;
  }
  static bool needs_card_barrier(const MachNode* node) {
    return (node->barrier_data() & ShenandoahBitCardMark) != 0;
  }
  static bool needs_load_ref_barrier(const MachNode* node) {
    return (node->barrier_data() & ShenandoahBitStrong) != 0;
  }

  static ShenandoahCASBarrierStubC2* create(const MachNode* node, Address addr, Register expected, Register new_val,
      Register result, Register tmp1, Register tmp2, bool narrow, bool cae, bool maybe_null, bool acquire, bool weak);
  void emit_code(MacroAssembler& masm) override;
};
#endif // SHARE_GC_SHENANDOAH_C2_SHENANDOAHBARRIERSETC2_HPP
