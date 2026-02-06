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

#define __ kit->

static bool satb_can_remove_pre_barrier(GraphKit* kit, PhaseValues* phase, Node* adr,
                                        BasicType bt, uint adr_idx) {
  intptr_t offset = 0;
  Node* base = AddPNode::Ideal_base_and_offset(adr, phase, offset);
  AllocateNode* alloc = AllocateNode::Ideal_allocation(base);

  if (offset == Type::OffsetBot) {
    return false; // cannot unalias unless there are precise offsets
  }

  if (alloc == nullptr) {
    return false; // No allocation found
  }

  intptr_t size_in_bytes = type2aelembytes(bt);

  Node* mem = __ memory(adr_idx); // start searching here...

  for (int cnt = 0; cnt < 50; cnt++) {

    if (mem->is_Store()) {

      Node* st_adr = mem->in(MemNode::Address);
      intptr_t st_offset = 0;
      Node* st_base = AddPNode::Ideal_base_and_offset(st_adr, phase, st_offset);

      if (st_base == nullptr) {
        break; // inscrutable pointer
      }

      // Break we have found a store with same base and offset as ours so break
      if (st_base == base && st_offset == offset) {
        break;
      }

      if (st_offset != offset && st_offset != Type::OffsetBot) {
        const int MAX_STORE = BytesPerLong;
        if (st_offset >= offset + size_in_bytes ||
            st_offset <= offset - MAX_STORE ||
            st_offset <= offset - mem->as_Store()->memory_size()) {
          // Success:  The offsets are provably independent.
          // (You may ask, why not just test st_offset != offset and be done?
          // The answer is that stores of different sizes can co-exist
          // in the same sequence of RawMem effects.  We sometimes initialize
          // a whole 'tile' of array elements with a single jint or jlong.)
          mem = mem->in(MemNode::Memory);
          continue; // advance through independent store memory
        }
      }

      if (st_base != base
          && MemNode::detect_ptr_independence(base, alloc, st_base,
                                              AllocateNode::Ideal_allocation(st_base),
                                              phase)) {
        // Success:  The bases are provably independent.
        mem = mem->in(MemNode::Memory);
        continue; // advance through independent store memory
      }
    } else if (mem->is_Proj() && mem->in(0)->is_Initialize()) {

      InitializeNode* st_init = mem->in(0)->as_Initialize();
      AllocateNode* st_alloc = st_init->allocation();

      // Make sure that we are looking at the same allocation site.
      // The alloc variable is guaranteed to not be null here from earlier check.
      if (alloc == st_alloc) {
        // Check that the initialization is storing null so that no previous store
        // has been moved up and directly write a reference
        Node* captured_store = st_init->find_captured_store(offset,
                                                            type2aelembytes(T_OBJECT),
                                                            phase);
        if (captured_store == nullptr || captured_store == st_init->zero_memory()) {
          return true;
        }
      }
    }

    // Unless there is an explicit 'continue', we must bail out here,
    // because 'mem' is an inscrutable memory state (e.g., a call).
    break;
  }

  return false;
}

static bool shenandoah_can_remove_post_barrier(GraphKit* kit, PhaseValues* phase, Node* store_ctrl, Node* adr) {
  intptr_t      offset = 0;
  Node*         base   = AddPNode::Ideal_base_and_offset(adr, phase, offset);
  AllocateNode* alloc  = AllocateNode::Ideal_allocation(base);

  if (offset == Type::OffsetBot) {
    return false; // Cannot unalias unless there are precise offsets.
  }
  if (alloc == nullptr) {
    return false; // No allocation found.
  }

  Node* mem = store_ctrl;   // Start search from Store node.
  if (mem->is_Proj() && mem->in(0)->is_Initialize()) {
    InitializeNode* st_init = mem->in(0)->as_Initialize();
    AllocateNode*  st_alloc = st_init->allocation();
    // Make sure we are looking at the same allocation
    if (alloc == st_alloc) {
      return true;
    }
  }

  return false;
}

static uint8_t get_store_barrier(C2Access& access) {
  if (!access.is_parse_access()) {
    // Only support for eliding barriers at parse time for now.
    return ShenandoahBarrierSATB | ShenandoahBarrierCardMark;
  }
  GraphKit* kit = (static_cast<C2ParseAccess&>(access)).kit();
  Node* ctl = kit->control();
  Node* adr = access.addr().node();
  uint adr_idx = kit->C->get_alias_index(access.addr().type());
  assert(adr_idx != Compile::AliasIdxTop, "use other store_to_memory factory");

  bool can_remove_pre_barrier = satb_can_remove_pre_barrier(kit, &kit->gvn(), adr, access.type(), adr_idx);

  // We can skip marks on a freshly-allocated object in Eden. Keep this code in
  // sync with CardTableBarrierSet::on_slowpath_allocation_exit. That routine
  // informs GC to take appropriate compensating steps, upon a slow-path
  // allocation, so as to make this card-mark elision safe.
  // The post-barrier can also be removed if null is written. This case is
  // handled by ShenandoahBarrierSetC2::expand_barriers, which runs at the end of C2's
  // platform-independent optimizations to exploit stronger type information.
  bool can_remove_post_barrier = ReduceInitialCardMarks &&
    ((access.base() == kit->just_allocated_object(ctl)) ||
     shenandoah_can_remove_post_barrier(kit, &kit->gvn(), ctl, adr));

  int barriers = 0;
  if (!can_remove_pre_barrier) {
    barriers |= ShenandoahBarrierSATB;
  } else {
    barriers |= ShenandoahBarrierElided;
  }

  if (!can_remove_post_barrier) {
    barriers |= ShenandoahBarrierCardMark;
  } else {
    barriers |= ShenandoahBarrierElided;
  }

  return barriers;
}

Node* ShenandoahBarrierSetC2::store_at_resolved(C2Access& access, C2AccessValue& val) const {
  DecoratorSet decorators = access.decorators();
  bool anonymous = (decorators & ON_UNKNOWN_OOP_REF) != 0;
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool tightly_coupled_alloc = (decorators & C2_TIGHTLY_COUPLED_ALLOC) != 0;
  bool needs_pre_barrier = access.is_oop() && (in_heap || anonymous);
  // Pre-barriers are unnecessary for tightly-coupled initialization stores.
  bool can_be_elided = needs_pre_barrier && tightly_coupled_alloc && ReduceInitialCardMarks;
  bool no_keepalive = (decorators & AS_NO_KEEPALIVE) != 0;
  if (needs_pre_barrier) {
    if (can_be_elided) {
      access.set_barrier_data(access.barrier_data() & ~ShenandoahBarrierSATB);
      access.set_barrier_data(access.barrier_data() | ShenandoahBarrierElided);
    } else {
      access.set_barrier_data(get_store_barrier(access));
    }
  }
  if (no_keepalive) {
    // No keep-alive means no need for the pre-barrier.
    access.set_barrier_data(access.barrier_data() & ~ShenandoahBarrierSATB);
  }
  return BarrierSetC2::store_at_resolved(access, val);
}

static void set_barrier_data(C2Access& access) {
  assert(access.is_oop(), "Precondition");

  if (access.decorators() & C2_TIGHTLY_COUPLED_ALLOC) {
    access.set_barrier_data(ShenandoahBarrierElided);
    return;
  }

  uint8_t barrier_data = 0;

  if (access.decorators() & ON_PHANTOM_OOP_REF) {
    barrier_data |= ShenandoahBarrierPhantom;
  } else if (access.decorators() & ON_WEAK_OOP_REF) {
    barrier_data |= ShenandoahBarrierWeak;
  } else {
    barrier_data |= ShenandoahBarrierStrong;
  }

  if (access.decorators() & IN_NATIVE) {
    barrier_data |= ShenandoahBarrierNative;
  }

  access.set_barrier_data(barrier_data);
}

Node* ShenandoahBarrierSetC2::load_at_resolved(C2Access& access, const Type* val_type) const {
  // 1: non-reference load, no additional barrier is needed
  if (!access.is_oop()) {
    return BarrierSetC2::load_at_resolved(access, val_type);
  }

  // 2. Set barrier data for LRB.
  set_barrier_data(access);

  // 3. If we are reading the value of the referent field of a Reference object, we
  // need to record the referent in an SATB log buffer using the pre-barrier
  // mechanism.
  DecoratorSet decorators = access.decorators();
  bool on_weak = (decorators & ON_WEAK_OOP_REF) != 0;
  bool on_phantom = (decorators & ON_PHANTOM_OOP_REF) != 0;
  bool no_keepalive = (decorators & AS_NO_KEEPALIVE) != 0;
  bool needs_read_barrier = ((on_weak || on_phantom) && !no_keepalive);
  if (needs_read_barrier) {
    uint8_t barriers = access.barrier_data() | ShenandoahBarrierSATB;
    access.set_barrier_data(barriers);
  }

  return BarrierSetC2::load_at_resolved(access, val_type);
}

Node* ShenandoahBarrierSetC2::atomic_cmpxchg_val_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                             Node* new_val, const Type* value_type) const {
  if (access.is_oop()) {
    set_barrier_data(access);
    access.set_barrier_data(access.barrier_data() | ShenandoahBarrierSATB | ShenandoahBarrierCardMark);
  }
  return BarrierSetC2::atomic_cmpxchg_val_at_resolved(access, expected_val, new_val, value_type);
}

Node* ShenandoahBarrierSetC2::atomic_cmpxchg_bool_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                              Node* new_val, const Type* value_type) const {
  if (access.is_oop()) {
    set_barrier_data(access);
    access.set_barrier_data(access.barrier_data() | ShenandoahBarrierSATB | ShenandoahBarrierCardMark);
  }
  return BarrierSetC2::atomic_cmpxchg_bool_at_resolved(access, expected_val, new_val, value_type);
}

Node* ShenandoahBarrierSetC2::atomic_xchg_at_resolved(C2AtomicParseAccess& access, Node* val, const Type* value_type) const {
  if (access.is_oop()) {
    set_barrier_data(access);
    access.set_barrier_data(access.barrier_data() | ShenandoahBarrierSATB | ShenandoahBarrierCardMark);
  }
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
    barrier_data &= ~ShenandoahBarrierNotNull;
    // Simply elide post-barrier if writing null.
    barrier_data &= ~ShenandoahBarrierCardMark;
  } else if (newval_type == TypePtr::NotNull) {
    barrier_data |= ShenandoahBarrierNotNull;
  }
  store->set_barrier_data(barrier_data);
}

void ShenandoahBarrierSetC2::final_refinement(Compile* C) const {
  ResourceMark rm;
  VectorSet visited;
  Node_List worklist;
  worklist.push(C->root());
  while (worklist.size() > 0) {
    Node* n = worklist.pop();
    if (visited.test_set(n->_idx)) {
      continue;
    }

    // Drop elided flag. Matcher does not care about this, and we would like to
    // avoid invoking "barrier_data() != 0" rules when the *only* flag is Elided.
    if (n->is_LoadStore()) {
      LoadStoreNode* load_store = n->as_LoadStore();
      uint8_t barrier_data = load_store->barrier_data();
      if (barrier_data != 0) {
        barrier_data &= ~ShenandoahBarrierElided;
        load_store->set_barrier_data(barrier_data);
      }
    } else if (n->is_Mem()) {
      MemNode* mem = n->as_Mem();
      uint8_t barrier_data = mem->barrier_data();
      if (barrier_data != 0) {
        barrier_data &= ~ShenandoahBarrierElided;
        mem->set_barrier_data(barrier_data);
      }
    }

    for (uint j = 0; j < n->req(); j++) {
      Node* in = n->in(j);
      if (in != nullptr) {
        worklist.push(in);
      }
    }
  }
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

void ShenandoahBarrierSetC2::eliminate_gc_barrier(PhaseMacroExpand* macro, Node* node) const {
  eliminate_gc_barrier_data(node);
}

void* ShenandoahBarrierSetC2::create_barrier_state(Arena* comp_arena) const {
  return new(comp_arena) ShenandoahBarrierSetC2State(comp_arena);
}

ShenandoahBarrierSetC2State* ShenandoahBarrierSetC2::state() const {
  return reinterpret_cast<ShenandoahBarrierSetC2State*>(Compile::current()->barrier_set_state());
}

void ShenandoahBarrierSetC2::print_barrier_data(outputStream* os, uint8_t data) {
  os->print(" Node barriers: ");
  if ((data & ShenandoahBarrierStrong) != 0) {
    data &= ~ShenandoahBarrierStrong;
    os->print("strong ");
  }

  if ((data & ShenandoahBarrierWeak) != 0) {
    data &= ~ShenandoahBarrierWeak;
    os->print("weak ");
  }

  if ((data & ShenandoahBarrierPhantom) != 0) {
    data &= ~ShenandoahBarrierPhantom;
    os->print("phantom ");
  }

  if ((data & ShenandoahBarrierNative) != 0) {
    data &= ~ShenandoahBarrierNative;
    os->print("native ");
  }

  if ((data & ShenandoahBarrierElided) != 0) {
    data &= ~ShenandoahBarrierElided;
    os->print("elided ");
  }

  if ((data & ShenandoahBarrierSATB) != 0) {
    data &= ~ShenandoahBarrierSATB;
    os->print("satb ");
  }

  if ((data & ShenandoahBarrierCardMark) != 0) {
    data &= ~ShenandoahBarrierCardMark;
    os->print("cardmark ");
  }

  if ((data & ShenandoahBarrierNotNull) != 0) {
    data &= ~ShenandoahBarrierNotNull;
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

  // Final refinement might have removed the remaining ShenandoahBarrierElided flag,
  // making some accesses completely blank. TODO: If we get rid of ShenandoahBarrierElided
  // machinery completely, we can drop this filter too.
  bool accept_blank = (phase == BeforeCodeGen);

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
        verify_gc_barrier_assert(accept_blank || bd != 0, "Oop load should have barrier data", bd, n);

        bool is_weak = ((bd & (ShenandoahBarrierWeak | ShenandoahBarrierPhantom)) != 0);
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
          verify_gc_barrier_assert(accept_blank || bd != 0, "Oop store should have barrier data", bd, n);
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
      verify_gc_barrier_assert(accept_blank || bd != 0, "Oop load-store should have barrier data", bd, n);
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
  Compile* const C = Compile::current();
  BufferBlob* const blob = C->output()->scratch_buffer_blob();
  GrowableArray<ShenandoahBarrierStubC2*>* const stubs = barrier_set_state()->stubs();
  int size = 0;

  for (int i = 0; i < stubs->length(); i++) {
    CodeBuffer cb(blob->content_begin(), checked_cast<CodeBuffer::csize_t>((address)C->output()->scratch_locs_memory() - blob->content_begin()));
    MacroAssembler masm(&cb);
    stubs->at(i)->emit_code(masm);
    size += cb.insts_size();
  }

  return size;
}

void ShenandoahBarrierSetC2::emit_stubs(CodeBuffer& cb) const {
  MacroAssembler masm(&cb);
  GrowableArray<ShenandoahBarrierStubC2*>* const stubs = barrier_set_state()->stubs();
  barrier_set_state()->set_stubs_start_offset(masm.offset());

  for (int i = 0; i < stubs->length(); i++) {
    // Make sure there is enough space in the code buffer
    if (cb.insts()->maybe_expand_to_ensure_remaining(PhaseOutput::MAX_inst_size) && cb.blob() == nullptr) {
      ciEnv::current()->record_failure("CodeCache is full");
      return;
    }

    stubs->at(i)->emit_code(masm);
  }

  masm.flush();

}

void ShenandoahBarrierStubC2::register_stub() {
  if (!Compile::current()->output()->in_scratch_emit_size()) {
    barrier_set_state()->stubs()->append(this);
  }
}

ShenandoahStoreBarrierStubC2* ShenandoahStoreBarrierStubC2::create(const MachNode* node, Address dst, bool dst_narrow, Register src, bool src_narrow, Register tmp) {
  auto* stub = new (Compile::current()->comp_arena()) ShenandoahStoreBarrierStubC2(node, dst, dst_narrow, src, src_narrow, tmp);
  stub->register_stub();
  return stub;
}

ShenandoahLoadRefBarrierStubC2* ShenandoahLoadRefBarrierStubC2::create(const MachNode* node, Register obj, Register addr, Register tmp1, Register tmp2, Register tmp3, bool narrow) {
  auto* stub = new (Compile::current()->comp_arena()) ShenandoahLoadRefBarrierStubC2(node, obj, addr, tmp1, tmp2, tmp3, narrow);
  stub->register_stub();
  return stub;
}

ShenandoahSATBBarrierStubC2* ShenandoahSATBBarrierStubC2::create(const MachNode* node, Register addr, Register preval, Register tmp, bool encoded_preval) {
  auto* stub = new (Compile::current()->comp_arena()) ShenandoahSATBBarrierStubC2(node, addr, preval, tmp, encoded_preval);
  stub->register_stub();
  return stub;
}

ShenandoahCASBarrierSlowStubC2* ShenandoahCASBarrierSlowStubC2::create(const MachNode* node, Register addr, Register expected, Register new_val, Register result, Register tmp1, Register tmp2, bool cae, bool acquire, bool release, bool weak) {
  auto* stub = new (Compile::current()->comp_arena()) ShenandoahCASBarrierSlowStubC2(node, addr, Address(), expected, new_val, result, tmp1, tmp2, cae, acquire, release, weak);
  stub->register_stub();
  return stub;
}

ShenandoahCASBarrierSlowStubC2* ShenandoahCASBarrierSlowStubC2::create(const MachNode* node, Address addr, Register expected, Register new_val, Register result, Register tmp1, Register tmp2, bool cae) {
  auto* stub = new (Compile::current()->comp_arena()) ShenandoahCASBarrierSlowStubC2(node, noreg, addr, expected, new_val, result, tmp1, tmp2, cae, false, false, false);
  stub->register_stub();
  return stub;
}

bool ShenandoahBarrierSetC2State::needs_liveness_data(const MachNode* mach) const {
  return ShenandoahSATBBarrierStubC2::needs_barrier(mach) ||
         ShenandoahLoadRefBarrierStubC2::needs_barrier(mach);
}

bool ShenandoahBarrierSetC2State::needs_livein_data() const {
  return true;
}
