/*
 * Copyright (c) 2018, 2023, Red Hat, Inc. All rights reserved.
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

#include "classfile/javaClasses.inline.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shenandoah/c2/shenandoahBarrierSetC2.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahForwarding.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahRuntime.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "opto/arraycopynode.hpp"
#include "opto/escape.hpp"
#include "opto/graphKit.hpp"
#include "opto/idealKit.hpp"
#include "opto/macro.hpp"
#include "opto/narrowptrnode.hpp"
#include "opto/output.hpp"
#include "opto/rootnode.hpp"
#include "opto/runtime.hpp"

ShenandoahBarrierSetC2* ShenandoahBarrierSetC2::bsc2() {
  return reinterpret_cast<ShenandoahBarrierSetC2*>(BarrierSet::barrier_set()->barrier_set_c2());
}

ShenandoahBarrierSetC2State::ShenandoahBarrierSetC2State(Arena* comp_arena) :
    BarrierSetC2State(comp_arena),
    _stubs(new (comp_arena) GrowableArray<ShenandoahBarrierStubC2*>(comp_arena, 8,  0, nullptr)),
    _stubs_start_offset(0) {
}

static void set_barrier_data(C2Access& access, bool load, bool store) {
  if (!access.is_oop()) {
    return;
  }

  DecoratorSet decorators = access.decorators();
  bool tightly_coupled = (decorators & C2_TIGHTLY_COUPLED_ALLOC) != 0;
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool on_weak = (decorators & ON_WEAK_OOP_REF) != 0;
  bool on_phantom = (decorators & ON_PHANTOM_OOP_REF) != 0;

  if (tightly_coupled) {
    access.set_barrier_data(ShenandoahBitElided);
    return;
  }

  uint8_t barrier_data = 0;

  if (load) {
    if (ShenandoahLoadRefBarrier) {
      if (on_phantom) {
        barrier_data |= ShenandoahBitPhantom;
      } else if (on_weak) {
        barrier_data |= ShenandoahBitWeak;
      } else {
        barrier_data |= ShenandoahBitStrong;
      }
    }
  }

  if (store) {
    if (ShenandoahSATBBarrier) {
      barrier_data |= ShenandoahBitKeepAlive;
    }
    if (ShenandoahCardBarrier && in_heap) {
      barrier_data |= ShenandoahBitCardMark;
    }
  }

  if (!in_heap) {
    barrier_data |= ShenandoahBitNative;
  }

  access.set_barrier_data(barrier_data);
}

Node* ShenandoahBarrierSetC2::load_at_resolved(C2Access& access, const Type* val_type) const {
  // 1: Non-reference load, no additional barrier is needed
  if (!access.is_oop()) {
    return BarrierSetC2::load_at_resolved(access, val_type);
  }

  // 2. Set barrier data for load
  set_barrier_data(access, /* load = */ true, /* store = */ false);

  // 3. Correction: If we are reading the value of the referent field of
  // a Reference object, we need to record the referent resurrection.
  DecoratorSet decorators = access.decorators();
  bool on_weak = (decorators & ON_WEAK_OOP_REF) != 0;
  bool on_phantom = (decorators & ON_PHANTOM_OOP_REF) != 0;
  bool no_keepalive = (decorators & AS_NO_KEEPALIVE) != 0;
  bool needs_keepalive = ((on_weak || on_phantom) && !no_keepalive);
  if (needs_keepalive) {
    uint8_t barriers = access.barrier_data() | (ShenandoahSATBBarrier ? ShenandoahBitKeepAlive : 0);
    access.set_barrier_data(barriers);
  }

  return BarrierSetC2::load_at_resolved(access, val_type);
}

Node* ShenandoahBarrierSetC2::store_at_resolved(C2Access& access, C2AccessValue& val) const {
  // 1: Non-reference store, no additional barrier is needed
  if (!access.is_oop()) {
    return BarrierSetC2::store_at_resolved(access, val);
  }

  // 2. Set barrier data for store
  set_barrier_data(access, /* load = */ false, /* store = */ true);

  // 3. Correction: avoid keep-alive barriers that should not do keep-alive.
  DecoratorSet decorators = access.decorators();
  bool no_keepalive = (decorators & AS_NO_KEEPALIVE) != 0;
  if (no_keepalive) {
    access.set_barrier_data(access.barrier_data() & ~ShenandoahBitKeepAlive);
  }

  return BarrierSetC2::store_at_resolved(access, val);
}

Node* ShenandoahBarrierSetC2::atomic_cmpxchg_val_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                             Node* new_val, const Type* value_type) const {
  set_barrier_data(access, /* load = */ true, /* store = */ true);
  return BarrierSetC2::atomic_cmpxchg_val_at_resolved(access, expected_val, new_val, value_type);
}

Node* ShenandoahBarrierSetC2::atomic_cmpxchg_bool_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                              Node* new_val, const Type* value_type) const {
  set_barrier_data(access, /* load = */ true, /* store = */ true);
  return BarrierSetC2::atomic_cmpxchg_bool_at_resolved(access, expected_val, new_val, value_type);
}

Node* ShenandoahBarrierSetC2::atomic_xchg_at_resolved(C2AtomicParseAccess& access, Node* val, const Type* value_type) const {
  set_barrier_data(access, /* load = */ true, /* store = */ true);
  return BarrierSetC2::atomic_xchg_at_resolved(access, val, value_type);
}

void ShenandoahBarrierSetC2::refine_store(const Node* n) {
  MemNode* store = n->as_Store();
  const Node* newval = n->in(MemNode::ValueIn);
  assert(newval != nullptr, "");
  const Type* newval_bottom = newval->bottom_type();
  TypePtr::PTR newval_type = newval_bottom->make_ptr()->ptr();
  uint8_t barrier_data = store->barrier_data();
  if (!newval_bottom->isa_oopptr() &&
      !newval_bottom->isa_narrowoop() &&
      newval_type != TypePtr::Null) {
    // newval is neither an OOP nor null, so there is no barrier to refine.
    assert(barrier_data == 0, "non-OOP stores should have no barrier data");
    return;
  }
  if (barrier_data == 0) {
    // No barrier to refine.
    return;
  }
  if (newval_type == TypePtr::Null) {
    barrier_data &= ~ShenandoahBitNotNull;
    // Simply elide post-barrier if writing null.
    barrier_data &= ~ShenandoahBitCardMark;
  } else if (newval_type == TypePtr::NotNull) {
    barrier_data |= ShenandoahBitNotNull;
  }
  store->set_barrier_data(barrier_data);
}

bool ShenandoahBarrierSetC2::can_remove_load_barrier(Node* n) {
  // Check if all outs feed into nodes that do not expose the oops to the rest
  // of the runtime system. In this case, we can elide the LRB barrier. We bail
  // out with false at the first sight of trouble.

  ResourceMark rm;
  VectorSet visited;
  Node_List worklist;
  worklist.push(n);

  while (worklist.size() > 0) {
    Node* n = worklist.pop();
    if (visited.test_set(n->_idx)) {
      continue;
    }

    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* out = n->fast_out(i);
      switch (out->Opcode()) {
        case Op_CmpN: {
          if (out->in(1) == n &&
              out->in(2)->Opcode() == Op_ConN &&
              out->in(2)->get_narrowcon() == 0) {
            // Null check, no oop is exposed.
            break;
          } else {
            return false;
          }
        }
        case Op_CmpP: {
          if (out->in(1) == n &&
              out->in(2)->Opcode() == Op_ConP &&
              out->in(2)->get_ptr() == 0) {
            // Null check, no oop is exposed.
            break;
          } else {
            return false;
          }
        }
        case Op_DecodeN:
        case Op_CastPP: {
          // Check if any other outs are escaping.
          worklist.push(out);
          break;
        }
        case Op_CallStaticJava: {
          if (out->as_CallStaticJava()->is_uncommon_trap()) {
            // Local feeds into uncommon trap. Deopt machinery handles barriers itself.
            break;
          } else {
            return false;
          }
        }

        default: {
          // Paranoidly distrust any other nodes.
          // TODO: Check if there are other patterns that benefit from this elision.
          return false;
        }
      }
    }
  }

  // Nothing troublesome found.
  return true;
}

void ShenandoahBarrierSetC2::refine_load(Node* n) {
  MemNode* load = n->as_Load();

  uint8_t barrier_data = load->barrier_data();

  // Do not touch weak LRBs at all: they are responsible for shielding from
  // Reference.referent resurrection.
  if ((barrier_data & (ShenandoahBitWeak | ShenandoahBitPhantom)) != 0) {
    return;
  }

  if (can_remove_load_barrier(n)) {
    barrier_data &= ~ShenandoahBitStrong;
    barrier_data |= ShenandoahBitElided;
  }

  load->set_barrier_data(barrier_data);
}

bool ShenandoahBarrierSetC2::expand_barriers(Compile* C, PhaseIterGVN& igvn) const {
  ResourceMark rm;
  VectorSet visited;
  Node_List worklist;
  worklist.push(C->root());
  while (worklist.size() > 0) {
    Node* n = worklist.pop();
    if (visited.test_set(n->_idx)) {
      continue;
    }
    switch(n->Opcode()) {
      case Op_StoreP:
      case Op_StoreN: {
        refine_store(n);
        break;
      }
      case Op_LoadN:
      case Op_LoadP: {
        refine_load(n);
        break;
      }
    }

    for (uint j = 0; j < n->req(); j++) {
      Node* in = n->in(j);
      if (in != nullptr) {
        worklist.push(in);
      }
    }
  }
  return false;
}

// Support for macro expanded GC barriers
void ShenandoahBarrierSetC2::eliminate_gc_barrier_data(Node* node) const {
  if (node->is_LoadStore()) {
    LoadStoreNode* loadstore = node->as_LoadStore();
    loadstore->set_barrier_data(0);
  } else if (node->is_Mem()) {
    MemNode* mem = node->as_Mem();
    mem->set_barrier_data(0);
  }
}

// If there are no real barrier flags on the node, strip away additional fluff.
// Matcher does not care about this, and we would like to avoid invoking "barrier_data() != 0"
// rules when the only flags are the irrelevant fluff.
void ShenandoahBarrierSetC2::strip_extra_data(const Node* n) const {
  if (n->is_LoadStore()) {
    LoadStoreNode* load_store = n->as_LoadStore();
    uint8_t barrier_data = load_store->barrier_data();
    if ((barrier_data & ShenandoahBitsReal) == 0) {
      load_store->set_barrier_data(0);
    }
  } else if (n->is_Mem()) {
    MemNode* mem = n->as_Mem();
    uint8_t barrier_data = mem->barrier_data();
    if ((barrier_data & ShenandoahBitsReal) == 0) {
      mem->set_barrier_data(0);
    }
  }
}

void ShenandoahBarrierSetC2::strip_extra_data(Node_List& accesses) const {
  for (uint c = 0; c < accesses.size(); c++) {
    strip_extra_data(accesses.at(c));
  }
}

void ShenandoahBarrierSetC2::eliminate_gc_barrier(PhaseMacroExpand* macro, Node* node) const {
  eliminate_gc_barrier_data(node);
}

void ShenandoahBarrierSetC2::elide_dominated_barrier(MachNode* mach) const {
  mach->set_barrier_data(0);
}

void ShenandoahBarrierSetC2::analyze_dominating_barriers() const {
  ResourceMark rm;
  Compile* const C = Compile::current();
  PhaseCFG* const cfg = C->cfg();

  Node_List all_loads, loads, stores, atomics;
  Node_List load_dominators, store_dominators, atomic_dominators;

  for (uint i = 0; i < cfg->number_of_blocks(); ++i) {
    const Block* const block = cfg->get_block(i);
    for (uint j = 0; j < block->number_of_nodes(); ++j) {
      Node* const node = block->get_node(j);

      // Everything that happens in allocations does not need barriers.
      if (node->is_Phi() && is_allocation(node)) {
        load_dominators.push(node);
        store_dominators.push(node);
        atomic_dominators.push(node);
        continue;
      }

      if (!node->is_Mach()) {
        continue;
      }

      MachNode* const mach = node->as_Mach();
      switch (mach->ideal_Opcode()) {

        // Dominating loads have already passed through LRB and their load
        // locations got fixed. Subsequent barriers are no longer required.
        // The only exception are weak loads that have to go through LRB
        // to deal with dying referents.
        case Op_LoadP:
        case Op_LoadN: {
          if (mach->barrier_data() != 0) {
            all_loads.push(mach);
          }
          if ((mach->barrier_data() & ShenandoahBitStrong) != 0) {
            loads.push(mach);
            load_dominators.push(mach);
          }
          break;
        }

        // Dominating stores have recorded the old value in SATB, and made the
        // card table update for a location. Subsequent barriers are no longer
        // required.
        case Op_StoreP:
        case Op_StoreN: {
          if (mach->barrier_data() != 0) {
            stores.push(mach);
            load_dominators.push(mach);
            store_dominators.push(mach);
            atomic_dominators.push(mach);
          }
          break;
        }

        // Dominating atomics have dealt with false positives, and made the card
        // table updates for a location. Even though CAS barriers are conditional,
        // they perform all needed barriers when memory access is successful.
        // Therefore, subsequent barriers are no longer required.
        case Op_CompareAndExchangeN:
        case Op_CompareAndExchangeP:
        case Op_CompareAndSwapN:
        case Op_CompareAndSwapP:
        case Op_GetAndSetP:
        case Op_GetAndSetN: {
          if (mach->barrier_data() != 0) {
            atomics.push(mach);
            load_dominators.push(mach);
            store_dominators.push(mach);
            atomic_dominators.push(mach);
          }
          break;
        }

      default:
        break;
      }
    }
  }

  elide_dominated_barriers(loads, load_dominators);
  elide_dominated_barriers(stores, store_dominators);
  elide_dominated_barriers(atomics, atomic_dominators);

  // Also clean up extra metadata on these nodes. Dominance analysis likely left
  // many non-elided barriers with extra metadata, which can be stripped away.
  strip_extra_data(all_loads);
  strip_extra_data(stores);
  strip_extra_data(atomics);
}

uint ShenandoahBarrierSetC2::estimated_barrier_size(const Node* node) const {
  // Barrier impact on fast-path is driven by GC state checks emitted very late.
  // These checks are tight load-test-branch sequences, with no impact on C2 graph
  // size. Limiting unrolling in presence of GC barriers might turn some loops
  // tighter than with default unrolling, which may benefit performance due to denser
  // code. Testing shows it is still counter-productive.
  // Therefore, we report zero barrier size to let C2 do its normal thing.
  return 0;
}

bool ShenandoahBarrierSetC2::array_copy_requires_gc_barriers(bool tightly_coupled_alloc, BasicType type, bool is_clone, bool is_clone_instance, ArrayCopyPhase phase) const {
  bool is_oop = is_reference_type(type);
  if (!is_oop) {
    return false;
  }
  if (ShenandoahSATBBarrier && tightly_coupled_alloc) {
    if (phase == Optimization) {
      return false;
    }
    return !is_clone;
  }
  return true;
}

bool ShenandoahBarrierSetC2::clone_needs_barrier(const TypeOopPtr* src_type, bool& is_oop_array) {
  if (!ShenandoahCloneBarrier) {
    return false;
  }

  if (src_type->isa_instptr() != nullptr) {
    // Instance: need barrier only if there is a possibility of having an oop anywhere in it.
    ciInstanceKlass* ik = src_type->is_instptr()->instance_klass();
    if ((src_type->klass_is_exact() || !ik->has_subklass()) &&
        !ik->has_injected_fields() && !ik->has_object_fields()) {
      if (!src_type->klass_is_exact()) {
        // Class is *currently* the leaf in the hierarchy.
        // Record the dependency so that we deopt if this does not hold in future.
        Compile::current()->dependencies()->assert_leaf_type(ik);
      }
      return false;
    }
  } else if (src_type->isa_aryptr() != nullptr) {
    // Array: need barrier only if array is oop-bearing.
    BasicType src_elem = src_type->isa_aryptr()->elem()->array_element_basic_type();
    if (is_reference_type(src_elem, true)) {
      is_oop_array = true;
    } else {
      return false;
    }
  }

  // Assume the worst.
  return true;
}

void ShenandoahBarrierSetC2::clone(GraphKit* kit, Node* src_base, Node* dst_base, Node* size, bool is_array) const {
  const TypeOopPtr* src_type = kit->gvn().type(src_base)->is_oopptr();

  bool is_oop_array = false;
  if (!clone_needs_barrier(src_type, is_oop_array)) {
    // No barrier is needed? Just do what common BarrierSetC2 wants with it.
    BarrierSetC2::clone(kit, src_base, dst_base, size, is_array);
    return;
  }

  if (ShenandoahCloneRuntime || !is_array || !is_oop_array) {
    // Looks like an instance? Prepare the instance clone. This would either
    // be exploded into individual accesses or be left as runtime call.
    // Common BarrierSetC2 prepares everything for both cases.
    BarrierSetC2::clone(kit, src_base, dst_base, size, is_array);
    return;
  }

  // We are cloning the oop array. Prepare to call the normal arraycopy stub
  // after the expansion. Normal stub takes the number of actual type-sized
  // elements to copy after the base, compute the count here.
  Node* offset = kit->MakeConX(arrayOopDesc::base_offset_in_bytes(UseCompressedOops ? T_NARROWOOP : T_OBJECT));
  size = kit->gvn().transform(new SubXNode(size, offset));
  size = kit->gvn().transform(new URShiftXNode(size, kit->intcon(LogBytesPerHeapOop)));
  ArrayCopyNode* ac = ArrayCopyNode::make(kit, false, src_base, offset, dst_base, offset, size, true, false);
  ac->set_clone_array();
  Node* n = kit->gvn().transform(ac);
  if (n == ac) {
    ac->set_adr_type(TypeRawPtr::BOTTOM);
    kit->set_predefined_output_for_runtime_call(ac, ac->in(TypeFunc::Memory), TypeRawPtr::BOTTOM);
  } else {
    kit->set_all_memory(n);
  }
}

void ShenandoahBarrierSetC2::clone_at_expansion(PhaseMacroExpand* phase, ArrayCopyNode* ac) const {
  Node* const ctrl        = ac->in(TypeFunc::Control);
  Node* const mem         = ac->in(TypeFunc::Memory);
  Node* const src         = ac->in(ArrayCopyNode::Src);
  Node* const src_offset  = ac->in(ArrayCopyNode::SrcPos);
  Node* const dest        = ac->in(ArrayCopyNode::Dest);
  Node* const dest_offset = ac->in(ArrayCopyNode::DestPos);
  Node* length            = ac->in(ArrayCopyNode::Length);

  const TypeOopPtr* src_type = phase->igvn().type(src)->is_oopptr();

  bool is_oop_array = false;
  if (!clone_needs_barrier(src_type, is_oop_array)) {
    // No barrier is needed? Expand to normal HeapWord-sized arraycopy.
    BarrierSetC2::clone_at_expansion(phase, ac);
    return;
  }

  if (ShenandoahCloneRuntime || !ac->is_clone_array() || !is_oop_array) {
    // Still looks like an instance? Likely a large instance or reflective
    // clone with unknown length. Go to runtime and handle it there.
    clone_in_runtime(phase, ac, CAST_FROM_FN_PTR(address, ShenandoahRuntime::clone_addr()), "ShenandoahRuntime::clone");
    return;
  }

  // We are cloning the oop array. Call into normal oop array copy stubs.
  // Those stubs would call BarrierSetAssembler to handle GC barriers.

  // This is the full clone, so offsets should equal each other and be at array base.
  assert(src_offset == dest_offset, "should be equal");
  const jlong offset = src_offset->get_long();
  const TypeAryPtr* const ary_ptr = src->get_ptr_type()->isa_aryptr();
  BasicType bt = ary_ptr->elem()->array_element_basic_type();
  assert(offset == arrayOopDesc::base_offset_in_bytes(bt), "should match");

  const char*   copyfunc_name = "arraycopy";
  const address copyfunc_addr = phase->basictype2arraycopy(T_OBJECT, nullptr, nullptr, true, copyfunc_name, true);

  Node* const call = phase->make_leaf_call(ctrl, mem,
      OptoRuntime::fast_arraycopy_Type(),
      copyfunc_addr, copyfunc_name,
      TypeRawPtr::BOTTOM,
      phase->basic_plus_adr(src, src_offset),
      phase->basic_plus_adr(dest, dest_offset),
      length,
      phase->top()
  );
  phase->transform_later(call);

  phase->igvn().replace_node(ac, call);
}

void* ShenandoahBarrierSetC2::create_barrier_state(Arena* comp_arena) const {
  return new(comp_arena) ShenandoahBarrierSetC2State(comp_arena);
}

ShenandoahBarrierSetC2State* ShenandoahBarrierSetC2::state() const {
  return reinterpret_cast<ShenandoahBarrierSetC2State*>(Compile::current()->barrier_set_state());
}

void ShenandoahBarrierSetC2::print_barrier_data(outputStream* os, uint8_t data) {
  os->print(" Node barriers: ");
  if ((data & ShenandoahBitStrong) != 0) {
    data &= ~ShenandoahBitStrong;
    os->print("strong ");
  }

  if ((data & ShenandoahBitWeak) != 0) {
    data &= ~ShenandoahBitWeak;
    os->print("weak ");
  }

  if ((data & ShenandoahBitPhantom) != 0) {
    data &= ~ShenandoahBitPhantom;
    os->print("phantom ");
  }

  if ((data & ShenandoahBitElided) != 0) {
    data &= ~ShenandoahBitElided;
    os->print("elided ");
  }

  if ((data & ShenandoahBitKeepAlive) != 0) {
    data &= ~ShenandoahBitKeepAlive;
    os->print("keepalive ");
  }

  if ((data & ShenandoahBitCardMark) != 0) {
    data &= ~ShenandoahBitCardMark;
    os->print("cardmark ");
  }

  if ((data & ShenandoahBitNotNull) != 0) {
    data &= ~ShenandoahBitNotNull;
    os->print("not-null ");
  }
  os->cr();

  if (data > 0) {
    fatal("Unknown bit!");
  }

  os->print_cr(" GC configuration: %sLRB %sSATB %sCAS %sClone %sCard",
    (ShenandoahLoadRefBarrier ? "+" : "-"),
    (ShenandoahSATBBarrier    ? "+" : "-"),
    (ShenandoahCASBarrier     ? "+" : "-"),
    (ShenandoahCloneBarrier   ? "+" : "-"),
    (ShenandoahCardBarrier    ? "+" : "-")
  );
}

#ifdef ASSERT
void ShenandoahBarrierSetC2::verify_gc_barrier_assert(bool cond, const char* msg, uint8_t bd, Node* n) {
  if (!cond) {
    stringStream ss;
    ss.print_cr("%s", msg);
    ss.print_cr("-----------------");
    print_barrier_data(&ss, bd);
    ss.print_cr("-----------------");
    n->dump_bfs(1, nullptr, "", &ss);
    report_vm_error(__FILE__, __LINE__, ss.as_string());
  }
}

void ShenandoahBarrierSetC2::verify_gc_barriers(Compile* compile, CompilePhase phase) const {
  if (!ShenandoahVerifyOptoBarriers) {
    return;
  }

  // Optimizations might have removed the remaining auxiliary flags, making some accesses completely blank.
  bool accept_blank = (phase == BeforeCodeGen);
  bool expect_load_barriers       = !accept_blank && ShenandoahLoadRefBarrier;
  bool expect_store_barriers      = !accept_blank && (ShenandoahSATBBarrier || ShenandoahCardBarrier);
  bool expect_load_store_barriers = !accept_blank && ShenandoahCASBarrier;

  Unique_Node_List wq;
  Node_Stack phis(0);
  VectorSet visited;

  wq.push(compile->root());
  for (uint next = 0; next < wq.size(); next++) {
    Node *n = wq.at(next);
    int opc = n->Opcode();

    if (opc == Op_LoadP || opc == Op_LoadN) {
      uint8_t bd = n->as_Load()->barrier_data();

      const TypePtr* adr_type = n->as_Load()->adr_type();
      if (adr_type->isa_oopptr() || adr_type->isa_narrowoop()) {
        verify_gc_barrier_assert(!expect_load_barriers || (bd != 0), "Oop load should have barrier data", bd, n);

        bool is_weak = ((bd & (ShenandoahBitWeak | ShenandoahBitPhantom)) != 0);
        bool is_referent = adr_type->isa_instptr() &&
            adr_type->is_instptr()->instance_klass()->is_subtype_of(Compile::current()->env()->Reference_klass()) &&
            adr_type->is_instptr()->offset() == java_lang_ref_Reference::referent_offset();

        verify_gc_barrier_assert(!is_weak || is_referent, "Weak load only for Reference.referent", bd, n);
      } else if (adr_type->isa_rawptr() || adr_type->isa_klassptr()) {
        // Some LoadP-s are used for T_ADDRESS loads from raw pointers. These are not oops.
        // Some LoadP-s are used to load class data.
        // TODO: Verify their barrier data.
      } else {
        verify_gc_barrier_assert(false, "Unclassified access type", bd, n);
      }
    } else if (opc == Op_StoreP || opc == Op_StoreN) {
      uint8_t bd = n->as_Store()->barrier_data();
      const TypePtr* adr_type = n->as_Store()->adr_type();
      if (adr_type->isa_oopptr() || adr_type->isa_narrowoop()) {
        // Reference.clear stores null
        bool is_referent = adr_type->isa_instptr() &&
             adr_type->is_instptr()->instance_klass()->is_subtype_of(Compile::current()->env()->Reference_klass()) &&
             adr_type->is_instptr()->offset() == java_lang_ref_Reference::referent_offset();

        const TypePtr* val_type = n->as_Store()->in(MemNode::Memory)->adr_type();
        if (!is_referent && (val_type->isa_oopptr() || val_type->isa_narrowoop())) {
          verify_gc_barrier_assert(!expect_store_barriers || (bd != 0), "Oop store should have barrier data", bd, n);
        }
      } else if (adr_type->isa_rawptr() || adr_type->isa_klassptr()) {
        // Similar to LoadP-s, some of these accesses are raw, and some are handling oops.
        // TODO: Verify their barrier data.
      } else {
        verify_gc_barrier_assert(false, "Unclassified access type", bd, n);
      }
    } else if (opc == Op_WeakCompareAndSwapP || opc == Op_WeakCompareAndSwapN ||
               opc == Op_CompareAndExchangeP || opc == Op_CompareAndExchangeN ||
               opc == Op_CompareAndSwapP     || opc == Op_CompareAndSwapN ||
               opc == Op_GetAndSetP          || opc == Op_GetAndSetN) {
      uint8_t bd = n->as_LoadStore()->barrier_data();
      verify_gc_barrier_assert(!expect_load_store_barriers || (bd != 0), "Oop load-store should have barrier data", bd, n);
    } else if (n->is_Mem()) {
      uint8_t bd = MemNode::barrier_data(n); // FIXME: LOL HotSpot, why not n->as_Mem()? LoadStore is both is_Mem() and not as_Mem().
      verify_gc_barrier_assert(bd == 0, "Other mem nodes should have no barrier data", bd, n);
    }

    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* m = n->fast_out(i);
      wq.push(m);
    }
  }
}
#endif

static ShenandoahBarrierSetC2State* barrier_set_state() {
  return reinterpret_cast<ShenandoahBarrierSetC2State*>(Compile::current()->barrier_set_state());
}

int ShenandoahBarrierSetC2::estimate_stub_size() const {
  GrowableArray<ShenandoahBarrierStubC2*>* const stubs = barrier_set_state()->stubs();
  assert(stubs->is_empty(), "Lifecycle: no stubs were yet created");
  return 0;
}

void ShenandoahBarrierSetC2::emit_stubs(CodeBuffer& cb) const {
  MacroAssembler masm(&cb);
  GrowableArray<ShenandoahBarrierStubC2*>* const stubs = barrier_set_state()->stubs();
  barrier_set_state()->set_stubs_start_offset(masm.offset());

  // Stub generation uses nested skipped counters that can double-count.
  // Calculate the actual skipped amount by the real PC before/after stub generation.
  // FIXME: This should be handled upstream.
  int offset_before = masm.offset();
  int skipped_before = masm.get_skipped();

  for (int i = 0; i < stubs->length(); i++) {
    // Make sure there is enough space in the code buffer
    if (cb.insts()->maybe_expand_to_ensure_remaining(PhaseOutput::MAX_inst_size) && cb.blob() == nullptr) {
      ciEnv::current()->record_failure("CodeCache is full");
      return;
    }

    stubs->at(i)->emit_code(masm);
  }

  int offset_after = masm.offset();

  // The real stubs section is coming up after this, so we have to account for alignment
  // padding there. See CodeSection::alignment().
  offset_after = align_up(offset_after, HeapWordSize);

  masm.set_skipped(skipped_before + (offset_after - offset_before));

  masm.flush();
}

void ShenandoahBarrierStubC2::register_stub() {
  if (!Compile::current()->output()->in_scratch_emit_size()) {
    barrier_set_state()->stubs()->append(this);
  }
}

ShenandoahLoadBarrierStubC2* ShenandoahLoadBarrierStubC2::create(const MachNode* node, Register dst, Address addr, bool narrow, bool self_load) {
  auto* stub = new (Compile::current()->comp_arena()) ShenandoahLoadBarrierStubC2(node, dst, addr, narrow, self_load);
  stub->register_stub();
  return stub;
}

bool ShenandoahBarrierSetC2State::needs_liveness_data(const MachNode* mach) const {
  // Nodes that require slow-path stubs need liveness data.
  return ShenandoahBarrierStubC2::needs_keep_alive_barrier(mach) ||
         ShenandoahBarrierStubC2::needs_load_ref_barrier(mach);
}

bool ShenandoahBarrierSetC2State::needs_livein_data() const {
  return true;
}
