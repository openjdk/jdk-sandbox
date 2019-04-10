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

#include "precompiled.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc/shared/gcLocker.hpp"
#include "interpreter/bytecodeUtils.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/signature.hpp"
#include "utilities/events.hpp"

/*
 * Prints the name of the method that is described at constant pool
 * index cp_index in the constant pool of method 'method'.
 */
static void print_method_name(outputStream *os, Method* method, int cp_index) {
  ConstantPool* cp = method->constants();
  int class_index = cp->klass_ref_index_at(cp_index);
  Symbol* klass = cp->klass_at_noresolve(class_index);

  int name_and_type_index = cp->name_and_type_ref_index_at(cp_index);
  int name_index = cp->name_ref_index_at(name_and_type_index);
  int type_index = cp->signature_ref_index_at(name_and_type_index);
  Symbol* name = cp->symbol_at(name_index);
  Symbol* signature = cp->symbol_at(type_index);

  signature->print_as_signature_external_return_type(os);
  os->print(" %s.%s(", klass->as_klass_external_name(), name->as_C_string());
  signature->print_as_signature_external_parameters(os);
  os->print(")");
}

/*
 * Prints the name of the field that is described at constant pool
 * index cp_index in the constant pool of method 'method'.
 */
static void print_field_and_class(outputStream *os, Method* method, int cp_index) {
  ConstantPool* cp = method->constants();
  int class_index = cp->klass_ref_index_at(cp_index);
  Symbol* klass = cp->klass_at_noresolve(class_index);

  int name_and_type_index = cp->name_and_type_ref_index_at(cp_index);
  int name_index = cp->name_ref_index_at(name_and_type_index);
  Symbol* name = cp->symbol_at(name_index);

  os->print("%s.%s", klass->as_klass_external_name(), name->as_C_string());
}

/*
 * Returns the name of the field that is described at constant pool
 * index cp_index in the constant pool of method 'method'.
 */
static char const* get_field_name(Method* method, int cp_index) {
  ConstantPool* cp = method->constants();
  int name_and_type_index = cp->name_and_type_ref_index_at(cp_index);
  int name_index = cp->name_ref_index_at(name_and_type_index);
  Symbol* name = cp->symbol_at(name_index);
  return name->as_C_string();
}

static void print_local_var(outputStream *os, int bci, Method* method, int slot) {
  if (method->has_localvariable_table()) {
    for (int i = 0; i < method->localvariable_table_length(); i++) {
      LocalVariableTableElement* elem = method->localvariable_table_start() + i;
      int start = elem->start_bci;
      int end = start + elem->length;

      if ((bci >= start) && (bci < end) && (elem->slot == slot)) {
        ConstantPool* cp = method->constants();
        char *var =  cp->symbol_at(elem->name_cp_index)->as_C_string();
        os->print("%s", var);

        return;
      }
    }
  }

  // Handle at least some cases we know.
  if (!method->is_static() && (slot == 0)) {
    os->print("this");
  } else {
    int curr = method->is_static() ? 0 : 1;
    SignatureStream ss(method->signature());
    int param_index = 0;
    bool found = false;

    for (SignatureStream ss(method->signature()); !ss.is_done(); ss.next()) {
      if (ss.at_return_type()) {
        continue;
      }
      int size = type2size[ss.type()];
      if ((slot >= curr) && (slot < curr + size)) {
        found = true;
        break;
      }
      param_index += 1;
      curr += size;
    }

    if (found) {
      os->print("<parameter%d>", 1 + param_index);
    } else {
      // This is the best we can do.
      os->print("<local%d>", slot);
    }
  }
}

TrackingStackEntry::TrackingStackEntry(BasicType type) : _entry(INVALID + type * SCALE) { }

TrackingStackEntry::TrackingStackEntry(int bci, BasicType type) : _entry(bci + type * SCALE) {
  assert(bci >= 0, "BCI must be >= 0");
  assert(bci < 65536, "BCI must be < 65536");
}

int TrackingStackEntry::get_bci() {
  return _entry % SCALE;
}

BasicType TrackingStackEntry::get_type() {
  return BasicType(_entry / SCALE);
}

TrackingStackEntry TrackingStackEntry::merge(TrackingStackEntry other) {
  if (get_type() != other.get_type()) {
    if (((get_type() == T_OBJECT) || (get_type() == T_ARRAY)) &&
        ((other.get_type() == T_OBJECT) || (other.get_type() == T_ARRAY))) {
      if (get_bci() == other.get_bci()) {
        return TrackingStackEntry(get_bci(), T_OBJECT);
      } else {
        return TrackingStackEntry(T_OBJECT);
      }
    } else {
      return TrackingStackEntry(T_CONFLICT);
    }
  }

  if (get_bci() == other.get_bci()) {
    return *this;
  } else {
    return TrackingStackEntry(get_type());
  }
}


TrackingStack::TrackingStack(const TrackingStack &copy) {
  for (int i = 0; i < copy.get_size(); i++) {
    push_raw(copy._stack.at(i));
  }
}

void TrackingStack::push_raw(TrackingStackEntry entry) {
  if (entry.get_type() == T_VOID) {
    return;
  }

  _stack.push(entry);
}

void TrackingStack::push(TrackingStackEntry entry) {
  if (type2size[entry.get_type()] == 2) {
    push_raw(entry);
    push_raw(entry);
  } else {
    push_raw(entry);
  }
}

void TrackingStack::push(int bci, BasicType type) {
  push(TrackingStackEntry(bci, type));
}

void TrackingStack::pop(int slots) {
  for (int i = 0; i < slots; ++i) {
    _stack.pop();
  }

  assert(get_size() >= 0, "Popped too many slots");
}

void TrackingStack::merge(TrackingStack const& other) {
  assert(get_size() == other.get_size(), "Stacks not of same size");

  for (int i = get_size() - 1; i >= 0; --i) {
    _stack.at_put(i, _stack.at(i).merge(other._stack.at(i)));
  }
}

int TrackingStack::get_size() const {
  return _stack.length();
}

TrackingStackEntry TrackingStack::get_entry(int slot) {
  assert(slot >= 0, "Slot < 0");
  assert(slot < get_size(), "Slot >= size");

  return _stack.at(get_size() - slot - 1);
}

TrackingStackCreator::TrackingStackCreator(Method* method, int bci) : _method(method) {
  ConstMethod* const_method = method->constMethod();

  int len = const_method->code_size();
  _nr_of_entries = 0;
  _max_entries = 1000000;
  _stacks = new GrowableArray<TrackingStack*> (len+1);

  for (int i = 0; i <= len; ++i) {
    _stacks->push(NULL);
  }

  // Initialize stack a bci 0.
  _stacks->at_put(0, new TrackingStack());

  // And initialize the start of all exception handlers.
  if (const_method->has_exception_handler()) {
    ExceptionTableElement *et = const_method->exception_table_start();
    for (int i = 0; i < const_method->exception_table_length(); ++i) {
      u2 index = et[i].handler_pc;

      if (_stacks->at(index) == NULL) {
        _stacks->at_put(index, new TrackingStack());
        _stacks->at(index)->push(index, T_OBJECT);
      }
    }
  }

  _all_processed = false;
  _added_one = true;

  // Do this until each bytecode hash a stack or we haven't
  // added a new stack in one iteration.
  while (!_all_processed && _added_one) {
    _all_processed = true;
    _added_one = false;

    for (int i = 0; i < len; ) {
      // Analyse bytecode i. Step by size of the analyzed bytecode to next bytecode.
      i += do_instruction(i);

      // If we want the data only for a certain bci, we can possibly end early.
      if ((bci == i) && (_stacks->at(i) != NULL)) {
        _all_processed = true;
        break;
      }

      if (_nr_of_entries > _max_entries) {
        return;
      }
    }
  }
}

TrackingStackCreator::~TrackingStackCreator() {
  for (int i = 0; i < _stacks->length(); ++i) {
    delete _stacks->at(i);
  }
}

void TrackingStackCreator::merge(int bci, TrackingStack* stack) {
  assert(stack != _stacks->at(bci), "Cannot merge itself");

  if (_stacks->at(bci) != NULL) {
    stack->merge(*_stacks->at(bci));
  } else {
    // Got a new stack, so count the entries.
    _nr_of_entries += stack->get_size();
  }

  delete _stacks->at(bci);
  _stacks->at_put(bci, new TrackingStack(*stack));
}

int TrackingStackCreator::do_instruction(int bci) {
  ConstMethod* const_method = _method->constMethod();
  address code_base = _method->constMethod()->code_base();

  // We use the java code, since we don't want to cope with all the fast variants.
  int len = Bytecodes::java_length_at(_method, code_base + bci);

  // If we have no stack for this bci, we cannot process the bytecode now.
  if (_stacks->at(bci) == NULL) {
    _all_processed = false;
    return len;
  }

  TrackingStack* stack = new TrackingStack(*_stacks->at(bci));

  // dest_bci is != -1 if we branch.
  int dest_bci = -1;

  // This is for table and lookup switch.
  static const int initial_length = 2;
  GrowableArray<int> dests(initial_length);

  bool flow_ended = false;

  // Get the bytecode.
  bool is_wide = false;
  Bytecodes::Code raw_code = Bytecodes::code_at(_method, code_base + bci);
  Bytecodes::Code code = Bytecodes::java_code_at(_method, code_base + bci);
  int pos = bci + 1;

  if (code == Bytecodes::_wide) {
    is_wide = true;
    code = Bytecodes::java_code_at(_method, code_base + bci + 1);
    pos += 1;
  }

  // Now simulate the action of each bytecode.
  switch (code) {
    case Bytecodes::_nop:
    case Bytecodes::_aconst_null:
    case Bytecodes::_iconst_m1:
    case Bytecodes::_iconst_0:
    case Bytecodes::_iconst_1:
    case Bytecodes::_iconst_2:
    case Bytecodes::_iconst_3:
    case Bytecodes::_iconst_4:
    case Bytecodes::_iconst_5:
    case Bytecodes::_lconst_0:
    case Bytecodes::_lconst_1:
    case Bytecodes::_fconst_0:
    case Bytecodes::_fconst_1:
    case Bytecodes::_fconst_2:
    case Bytecodes::_dconst_0:
    case Bytecodes::_dconst_1:
    case Bytecodes::_bipush:
    case Bytecodes::_sipush:
    case Bytecodes::_iload:
    case Bytecodes::_lload:
    case Bytecodes::_fload:
    case Bytecodes::_dload:
    case Bytecodes::_aload:
    case Bytecodes::_iload_0:
    case Bytecodes::_iload_1:
    case Bytecodes::_iload_2:
    case Bytecodes::_iload_3:
    case Bytecodes::_lload_0:
    case Bytecodes::_lload_1:
    case Bytecodes::_lload_2:
    case Bytecodes::_lload_3:
    case Bytecodes::_fload_0:
    case Bytecodes::_fload_1:
    case Bytecodes::_fload_2:
    case Bytecodes::_fload_3:
    case Bytecodes::_dload_0:
    case Bytecodes::_dload_1:
    case Bytecodes::_dload_2:
    case Bytecodes::_dload_3:
    case Bytecodes::_aload_0:
    case Bytecodes::_aload_1:
    case Bytecodes::_aload_2:
    case Bytecodes::_aload_3:
    case Bytecodes::_iinc:
    case Bytecodes::_new:
      stack->push(bci, Bytecodes::result_type(code));
      break;

    case Bytecodes::_ldc:
    case Bytecodes::_ldc_w:
    case Bytecodes::_ldc2_w: {
      int cp_index;
      ConstantPool* cp = _method->constants();

      if (code == Bytecodes::_ldc) {
        cp_index = *(uint8_t*) (code_base + pos);

        if (raw_code == Bytecodes::_fast_aldc) {
          cp_index = cp->object_to_cp_index(cp_index);
        }
      } else {
        if (raw_code == Bytecodes::_fast_aldc_w) {
          cp_index = Bytes::get_native_u2(code_base + pos);
          cp_index = cp->object_to_cp_index(cp_index);
        }
        else {
          cp_index = Bytes::get_Java_u2(code_base + pos);
        }
      }

      constantTag tag = cp->tag_at(cp_index);
      if (tag.is_klass()  || tag.is_unresolved_klass() ||
          tag.is_method() || tag.is_interface_method() ||
          tag.is_field()  || tag.is_string()) {
        stack->push(bci, T_OBJECT);
      } else if (tag.is_int()) {
        stack->push(bci, T_INT);
      } else if (tag.is_long()) {
        stack->push(bci, T_LONG);
      } else if (tag.is_float()) {
        stack->push(bci, T_FLOAT);
      } else if (tag.is_double()) {
        stack->push(bci, T_DOUBLE);
      } else {
        assert(false, "Unexpected tag");
      }
      break;
    }

    case Bytecodes::_iaload:
    case Bytecodes::_faload:
    case Bytecodes::_aaload:
    case Bytecodes::_baload:
    case Bytecodes::_caload:
    case Bytecodes::_saload:
    case Bytecodes::_laload:
    case Bytecodes::_daload:
      stack->pop(2);
      stack->push(bci, Bytecodes::result_type(code));
      break;

    case Bytecodes::_istore:
    case Bytecodes::_lstore:
    case Bytecodes::_fstore:
    case Bytecodes::_dstore:
    case Bytecodes::_astore:
    case Bytecodes::_istore_0:
    case Bytecodes::_istore_1:
    case Bytecodes::_istore_2:
    case Bytecodes::_istore_3:
    case Bytecodes::_lstore_0:
    case Bytecodes::_lstore_1:
    case Bytecodes::_lstore_2:
    case Bytecodes::_lstore_3:
    case Bytecodes::_fstore_0:
    case Bytecodes::_fstore_1:
    case Bytecodes::_fstore_2:
    case Bytecodes::_fstore_3:
    case Bytecodes::_dstore_0:
    case Bytecodes::_dstore_1:
    case Bytecodes::_dstore_2:
    case Bytecodes::_dstore_3:
    case Bytecodes::_astore_0:
    case Bytecodes::_astore_1:
    case Bytecodes::_astore_2:
    case Bytecodes::_astore_3:
    case Bytecodes::_iastore:
    case Bytecodes::_lastore:
    case Bytecodes::_fastore:
    case Bytecodes::_dastore:
    case Bytecodes::_aastore:
    case Bytecodes::_bastore:
    case Bytecodes::_castore:
    case Bytecodes::_sastore:
    case Bytecodes::_pop:
    case Bytecodes::_pop2:
    case Bytecodes::_monitorenter:
    case Bytecodes::_monitorexit:
    case Bytecodes::_breakpoint:
      stack->pop(-Bytecodes::depth(code));
      break;

    case Bytecodes::_dup:
      stack->push_raw(stack->get_entry(0));
      break;

    case Bytecodes::_dup_x1: {
      TrackingStackEntry top1 = stack->get_entry(0);
      TrackingStackEntry top2 = stack->get_entry(1);
      stack->pop(2);
      stack->push_raw(top1);
      stack->push_raw(top2);
      stack->push_raw(top1);
      break;
    }

    case Bytecodes::_dup_x2: {
      TrackingStackEntry top1 = stack->get_entry(0);
      TrackingStackEntry top2 = stack->get_entry(1);
      TrackingStackEntry top3 = stack->get_entry(2);
      stack->pop(3);
      stack->push_raw(top1);
      stack->push_raw(top3);
      stack->push_raw(top2);
      stack->push_raw(top1);
      break;
    }

    case Bytecodes::_dup2:
      stack->push_raw(stack->get_entry(1));
      stack->push_raw(stack->get_entry(1));
      break;

    case Bytecodes::_dup2_x1: {
      TrackingStackEntry top1 = stack->get_entry(0);
      TrackingStackEntry top2 = stack->get_entry(1);
      TrackingStackEntry top3 = stack->get_entry(2);
      stack->pop(3);
      stack->push_raw(top2);
      stack->push_raw(top1);
      stack->push_raw(top3);
      stack->push_raw(top2);
      stack->push_raw(top1);
      break;
    }

    case Bytecodes::_dup2_x2: {
      TrackingStackEntry top1 = stack->get_entry(0);
      TrackingStackEntry top2 = stack->get_entry(1);
      TrackingStackEntry top3 = stack->get_entry(2);
      TrackingStackEntry top4 = stack->get_entry(3);
      stack->pop(4);
      stack->push_raw(top2);
      stack->push_raw(top1);
      stack->push_raw(top4);
      stack->push_raw(top3);
      stack->push_raw(top2);
      stack->push_raw(top1);
      break;
    }

    case Bytecodes::_swap: {
      TrackingStackEntry top1 = stack->get_entry(0);
      TrackingStackEntry top2 = stack->get_entry(1);
      stack->pop(2);
      stack->push(top1);
      stack->push(top2);
      break;
    }

    case Bytecodes::_iadd:
    case Bytecodes::_ladd:
    case Bytecodes::_fadd:
    case Bytecodes::_dadd:
    case Bytecodes::_isub:
    case Bytecodes::_lsub:
    case Bytecodes::_fsub:
    case Bytecodes::_dsub:
    case Bytecodes::_imul:
    case Bytecodes::_lmul:
    case Bytecodes::_fmul:
    case Bytecodes::_dmul:
    case Bytecodes::_idiv:
    case Bytecodes::_ldiv:
    case Bytecodes::_fdiv:
    case Bytecodes::_ddiv:
    case Bytecodes::_irem:
    case Bytecodes::_lrem:
    case Bytecodes::_frem:
    case Bytecodes::_drem:
    case Bytecodes::_iand:
    case Bytecodes::_land:
    case Bytecodes::_ior:
    case Bytecodes::_lor:
    case Bytecodes::_ixor:
    case Bytecodes::_lxor:
      stack->pop(2 * type2size[Bytecodes::result_type(code)]);
      stack->push(bci, Bytecodes::result_type(code));
      break;

    case Bytecodes::_ineg:
    case Bytecodes::_lneg:
    case Bytecodes::_fneg:
    case Bytecodes::_dneg:
      stack->pop(type2size[Bytecodes::result_type(code)]);
      stack->push(bci, Bytecodes::result_type(code));
      break;

    case Bytecodes::_ishl:
    case Bytecodes::_lshl:
    case Bytecodes::_ishr:
    case Bytecodes::_lshr:
    case Bytecodes::_iushr:
    case Bytecodes::_lushr:
      stack->pop(1 + type2size[Bytecodes::result_type(code)]);
      stack->push(bci, Bytecodes::result_type(code));
      break;

    case Bytecodes::_i2l:
    case Bytecodes::_i2f:
    case Bytecodes::_i2d:
    case Bytecodes::_f2i:
    case Bytecodes::_f2l:
    case Bytecodes::_f2d:
    case Bytecodes::_i2b:
    case Bytecodes::_i2c:
    case Bytecodes::_i2s:
      stack->pop(1);
      stack->push(bci, Bytecodes::result_type(code));
      break;

    case Bytecodes::_l2i:
    case Bytecodes::_l2f:
    case Bytecodes::_l2d:
    case Bytecodes::_d2i:
    case Bytecodes::_d2l:
    case Bytecodes::_d2f:
      stack->pop(2);
      stack->push(bci, Bytecodes::result_type(code));
      break;

    case Bytecodes::_lcmp:
    case Bytecodes::_fcmpl:
    case Bytecodes::_fcmpg:
    case Bytecodes::_dcmpl:
    case Bytecodes::_dcmpg:
      stack->pop(1 - Bytecodes::depth(code));
      stack->push(bci, T_INT);
      break;

    case Bytecodes::_ifeq:
    case Bytecodes::_ifne:
    case Bytecodes::_iflt:
    case Bytecodes::_ifge:
    case Bytecodes::_ifgt:
    case Bytecodes::_ifle:
    case Bytecodes::_if_icmpeq:
    case Bytecodes::_if_icmpne:
    case Bytecodes::_if_icmplt:
    case Bytecodes::_if_icmpge:
    case Bytecodes::_if_icmpgt:
    case Bytecodes::_if_icmple:
    case Bytecodes::_if_acmpeq:
    case Bytecodes::_if_acmpne:
    case Bytecodes::_ifnull:
    case Bytecodes::_ifnonnull:
      stack->pop(-Bytecodes::depth(code));
      dest_bci = bci + (int16_t) Bytes::get_Java_u2(code_base + pos);
      break;

    case Bytecodes::_jsr:
      // NOTE: Bytecodes has wrong depth for jsr.
      stack->push(bci, T_ADDRESS);
      dest_bci = bci + (int16_t) Bytes::get_Java_u2(code_base + pos);
      flow_ended = true;
      break;

    case Bytecodes::_jsr_w: {
      // NOTE: Bytecodes has wrong depth for jsr.
      stack->push(bci, T_ADDRESS);
      dest_bci = bci + (int32_t) Bytes::get_Java_u4(code_base + pos);
      flow_ended = true;
      break;
    }

    case Bytecodes::_ret:
      // We don't track local variables, so we cannot know were we
      // return. This makes the stacks imprecise, but we have to
      // live with that.
      flow_ended = true;
      break;

    case Bytecodes::_tableswitch: {
      stack->pop(1);
      pos = (pos + 3) & ~3;
      dest_bci = bci + (int32_t) Bytes::get_Java_u4(code_base + pos);
      int low = (int32_t) Bytes::get_Java_u4(code_base + pos + 4);
      int high = (int32_t) Bytes::get_Java_u4(code_base + pos + 8);

      for (int64_t i = low; i <= high; ++i) {
        dests.push(bci + (int32_t) Bytes::get_Java_u4(code_base + pos + 12 + 4 * (i - low)));
      }

      break;
    }

    case Bytecodes::_lookupswitch: {
      stack->pop(1);
      pos = (pos + 3) & ~3;
      dest_bci = bci + (int32_t) Bytes::get_Java_u4(code_base + pos);
      int nr_of_dests = (int32_t) Bytes::get_Java_u4(code_base + pos + 4);

      for (int i = 0; i < nr_of_dests; ++i) {
        dests.push(bci + (int32_t) Bytes::get_Java_u4(code_base + pos + 12 + 8 * i));
      }

      break;
    }

    case Bytecodes::_ireturn:
    case Bytecodes::_lreturn:
    case Bytecodes::_freturn:
    case Bytecodes::_dreturn:
    case Bytecodes::_areturn:
    case Bytecodes::_return:
    case Bytecodes::_athrow:
      stack->pop(-Bytecodes::depth(code));
      flow_ended = true;
      break;

    case Bytecodes::_getstatic:
    case Bytecodes::_getfield: {
      // Find out the type of the field accessed.
      int cp_index = Bytes::get_native_u2(code_base + pos) DEBUG_ONLY(+ ConstantPool::CPCACHE_INDEX_TAG);
      ConstantPool* cp = _method->constants();
      int name_and_type_index = cp->name_and_type_ref_index_at(cp_index);
      int type_index = cp->signature_ref_index_at(name_and_type_index);
      Symbol* signature = cp->symbol_at(type_index);
      // Simulate the bytecode: pop the address, push the 'value' loaded
      // from the field.
      stack->pop(1 - Bytecodes::depth(code));
      stack->push(bci, char2type((char) signature->char_at(0)));
      break;
    }

    case Bytecodes::_putstatic:
    case Bytecodes::_putfield: {
      int cp_index = Bytes::get_native_u2(code_base + pos) DEBUG_ONLY(+ ConstantPool::CPCACHE_INDEX_TAG);
      ConstantPool* cp = _method->constants();
      int name_and_type_index = cp->name_and_type_ref_index_at(cp_index);
      int type_index = cp->signature_ref_index_at(name_and_type_index);
      Symbol* signature = cp->symbol_at(type_index);
      ResultTypeFinder result_type(signature);
      stack->pop(type2size[char2type((char) signature->char_at(0))] - Bytecodes::depth(code) - 1);
      break;
    }

    case Bytecodes::_invokevirtual:
    case Bytecodes::_invokespecial:
    case Bytecodes::_invokestatic:
    case Bytecodes::_invokeinterface:
    case Bytecodes::_invokedynamic: {
      ConstantPool* cp = _method->constants();
      int cp_index;

      if (code == Bytecodes::_invokedynamic) {
        cp_index = ((int) Bytes::get_native_u4(code_base + pos));
      } else {
        cp_index = Bytes::get_native_u2(code_base + pos) DEBUG_ONLY(+ ConstantPool::CPCACHE_INDEX_TAG);
      }

      int name_and_type_index = cp->name_and_type_ref_index_at(cp_index);
      int type_index = cp->signature_ref_index_at(name_and_type_index);
      Symbol* signature = cp->symbol_at(type_index);

      if ((code != Bytecodes::_invokestatic) && (code != Bytecodes::_invokedynamic)) {
        // Pop class.
        stack->pop(1);
      }

      stack->pop(ArgumentSizeComputer(signature).size());
      ResultTypeFinder result_type(signature);
      stack->push(bci, result_type.type());
      break;
    }

    case Bytecodes::_newarray:
    case Bytecodes::_anewarray:
    case Bytecodes::_instanceof:
      stack->pop(1);
      stack->push(bci, Bytecodes::result_type(code));
      break;

    case Bytecodes::_arraylength:
      // The return type of arraylength is wrong in the bytecodes table (T_VOID).
      stack->pop(1);
      stack->push(bci, T_INT);
      break;

    case Bytecodes::_checkcast:
      break;

    case Bytecodes::_multianewarray:
      stack->pop(*(uint8_t*) (code_base + pos + 2));
      stack->push(bci, T_OBJECT);
      break;

   case Bytecodes::_goto:
      stack->pop(-Bytecodes::depth(code));
      dest_bci = bci + (int16_t) Bytes::get_Java_u2(code_base + pos);
      flow_ended = true;
      break;


   case Bytecodes::_goto_w:
      stack->pop(-Bytecodes::depth(code));
      dest_bci = bci + (int32_t) Bytes::get_Java_u4(code_base + pos);
      flow_ended = true;
      break;

    default:
      // Allow at least the bcis which have stack info to work.
      _all_processed = false;
      _added_one = false;
      delete stack;

      return len;
  }

  // Put new stack to the next instruction, if we might reach if from
  // this bci.
  if (!flow_ended) {
    if (_stacks->at(bci + len) == NULL) {
      _added_one = true;
    }

    merge(bci + len, stack);
  }

  // Put the stack to the branch target too.
  if (dest_bci != -1) {
    if (_stacks->at(dest_bci) == NULL) {
      _added_one = true;
    }

    merge(dest_bci, stack);
  }

  // If we have more than one branch target, process these too.
  for (int64_t i = 0; i < dests.length(); ++i) {
    if (_stacks->at(dests.at(i)) == NULL) {
      _added_one = true;
    }

    merge(dests.at(i), stack);
  }

  delete stack;

  return len;
}

int TrackingStackCreator::get_NPE_null_slot(int bci) {
  // If this NPE was created via reflection, we have no real NPE.
  if (_method->method_holder() == SystemDictionary::reflect_NativeConstructorAccessorImpl_klass()) {
    return -2;
  }
  // Get the bytecode.
  address code_base = _method->constMethod()->code_base();
  Bytecodes::Code code = Bytecodes::java_code_at(_method, code_base + bci);
  int pos = bci + 1;
  if (code == Bytecodes::_wide) {
    code = Bytecodes::java_code_at(_method, code_base + bci + 1);
    pos += 1;
  }

  switch (code) {
    case Bytecodes::_getfield:
    case Bytecodes::_arraylength:
    case Bytecodes::_athrow:
    case Bytecodes::_monitorenter:
    case Bytecodes::_monitorexit:
      return 0;
    case Bytecodes::_iaload:
    case Bytecodes::_faload:
    case Bytecodes::_aaload:
    case Bytecodes::_baload:
    case Bytecodes::_caload:
    case Bytecodes::_saload:
    case Bytecodes::_laload:
    case Bytecodes::_daload:
      return 1;
    case Bytecodes::_iastore:
    case Bytecodes::_fastore:
    case Bytecodes::_aastore:
    case Bytecodes::_bastore:
    case Bytecodes::_castore:
    case Bytecodes::_sastore:
      return 2;
    case Bytecodes::_lastore:
    case Bytecodes::_dastore:
      return 3;
    case Bytecodes::_putfield: {
        int cp_index = Bytes::get_native_u2(code_base + pos) DEBUG_ONLY(+ ConstantPool::CPCACHE_INDEX_TAG);
        ConstantPool* cp = _method->constants();
        int name_and_type_index = cp->name_and_type_ref_index_at(cp_index);
        int type_index = cp->signature_ref_index_at(name_and_type_index);
        Symbol* signature = cp->symbol_at(type_index);
        return type2size[char2type((char) signature->char_at(0))];
      }
    case Bytecodes::_invokevirtual:
    case Bytecodes::_invokespecial:
    case Bytecodes::_invokeinterface:
      {
        int cp_index = Bytes::get_native_u2(code_base+ pos) DEBUG_ONLY(+ ConstantPool::CPCACHE_INDEX_TAG);
        ConstantPool* cp = _method->constants();
        int name_and_type_index = cp->name_and_type_ref_index_at(cp_index);
        int name_index = cp->name_ref_index_at(name_and_type_index);
        int type_index = cp->signature_ref_index_at(name_and_type_index);
        Symbol* name = cp->symbol_at(name_index);
        Symbol* signature = cp->symbol_at(type_index);

        // Assume the the call of a constructor can never cause a NullPointerException
        // (which is true in Java). This is mainly used to avoid generating wrong
        // messages for NullPointerExceptions created explicitly by new in Java code.
        if (name != vmSymbols::object_initializer_name()) {
          return ArgumentSizeComputer(signature).size();
        } else {
          return -2;
        }
      }

    default:
      break;
  }

  return -1;
}

const int TrackingStackCreator::_max_cause_detail = 5;

void TrackingStackCreator::TrackingStackCreator::print_NPE_cause(outputStream *os, int bci, int slot) {
  if (print_NPE_cause0(os, bci, slot, _max_cause_detail, "'")) {
    os->print("' is null. ");
  }
}

/* Recursively print what was null.
 *
 * Go the the bytecode that pushed slot 'slot' on the operant stack
 * at bytecode 'bci'. Compute a message for that bytecode. If
 * necessary (array, field), recur further.
 * At most do max_detail recursions.
 *
 * Returns true if something was printed.
 */
bool TrackingStackCreator::TrackingStackCreator::print_NPE_cause0(outputStream *os, int bci, int slot,
                                                                  int max_detail, const char *prefix) {
  assert(bci >= 0, "BCI too low");
  assert(bci < get_size(), "BCI to large");

  if (max_detail <= 0) {
    return false;
  }

  if (_stacks->at(bci) == NULL) {
    return false;
  }

  TrackingStack* stack = _stacks->at(bci);
  assert(slot >= 0, "Slot nr. too low");
  assert(slot < stack->get_size(), "Slot nr. too large");

  TrackingStackEntry entry = stack->get_entry(slot);

  if (!entry.has_bci()) {
    return false;
  }

  // Get the bytecode.
  int source_bci = entry.get_bci();
  address code_base = _method->constMethod()->code_base();
  Bytecodes::Code code = Bytecodes::java_code_at(_method, code_base + source_bci);
  bool is_wide = false;
  int pos = source_bci + 1;

  if (code == Bytecodes::_wide) {
    is_wide = true;
    code = Bytecodes::java_code_at(_method, code_base + source_bci + 1);
    pos += 1;
  }

  if (max_detail == _max_cause_detail &&
      prefix != NULL &&
      code != Bytecodes::_invokevirtual &&
      code != Bytecodes::_invokespecial &&
      code != Bytecodes::_invokestatic &&
      code != Bytecodes::_invokeinterface) {
    os->print("%s", prefix);
  }

  switch (code) {
    case Bytecodes::_iload_0:
    //case Bytecodes::_lload_0: // ?
    //case Bytecodes::_fload_0: // ?
    //case Bytecodes::_dload_0: // ?
    case Bytecodes::_aload_0:
      print_local_var(os, source_bci, _method, 0);
      return true;

    case Bytecodes::_iload_1:
    //case Bytecodes::_lload_1: // ?
    //case Bytecodes::_fload_1: // ?
    //case Bytecodes::_dload_1: // ?
    case Bytecodes::_aload_1:
      print_local_var(os, source_bci, _method, 1);
      return true;

    case Bytecodes::_iload_2:
    //case Bytecodes::_lload_2: // ?
    //case Bytecodes::_fload_2: // ?
    //case Bytecodes::_dload_2: // ?
    case Bytecodes::_aload_2:
      print_local_var(os, source_bci, _method, 2);
      return true;

    case Bytecodes::_iload_3:
    //case Bytecodes::_lload_3: // ?
    //case Bytecodes::_fload_3: // ?
    //case Bytecodes::_dload_3: // ?
    case Bytecodes::_aload_3:
      print_local_var(os, source_bci, _method, 3);
      return true;

    case Bytecodes::_iload:
    //case Bytecodes::_lload: // ?
    //case Bytecodes::_fload: // ?
    //case Bytecodes::_dload: // ?
    case Bytecodes::_aload: {
      int index;

      if (is_wide) {
        index = Bytes::get_Java_u2(code_base + source_bci + 2);
      } else {
        index = *(uint8_t*) (code_base + source_bci + 1);
      }

      print_local_var(os, source_bci, _method, index);
      return true;
    }

    case Bytecodes::_aconst_null:
      os->print("null");
      return true;
    case Bytecodes::_iconst_m1:
      os->print("-1");
      return true;
    case Bytecodes::_iconst_0:
      os->print("0");
      return true;
    case Bytecodes::_iconst_1:
      os->print("1");
      return true;
    case Bytecodes::_iconst_2:
      os->print("2");
      return true;
    case Bytecodes::_iconst_3:
      os->print("3");
      return true;
    case Bytecodes::_iconst_4:
      os->print("4");
      return true;
    case Bytecodes::_iconst_5:
      os->print("5");
      return true;
      /*
    case Bytecodes::_lconst_0: // ?
      os->print("0L");
      return true;
    case Bytecodes::_lconst_1: // ?
      os->print("1L");
      return true;
    case Bytecodes::_fconst_0: // ?
      os->print("0.0f");
      return true;
    case Bytecodes::_fconst_1: // ?
      os->print("1.0f");
      return true;
    case Bytecodes::_fconst_2: // ?
      os->print("2.0f");
      return true;
    case Bytecodes::_dconst_0: // ?
      os->print("0.0");
      return true;
    case Bytecodes::_dconst_1: // ?
      os->print("1.0");
      return true;
      */
    case Bytecodes::_bipush: {
      jbyte con = *(jbyte*) (code_base + source_bci + 1);
      os->print("%d", con);
      return true;
    }
    case Bytecodes::_sipush: {
      u2 con = Bytes::get_Java_u2(code_base + source_bci + 1);
      os->print("%d", con);
      return true;
    }
   case Bytecodes::_iaload:
      //case Bytecodes::_faload: // ?
  case Bytecodes::_aaload: {
    //case Bytecodes::_baload: // ?
    //case Bytecodes::_caload: // ?
    //case Bytecodes::_saload: // ?
    //case Bytecodes::_laload: // ?
    //case Bytecodes::_daload: { // ?

      // Print the 'name' of the array. Go back to the bytecode that
      // pushed the array reference on the operand stack.
      if (!print_NPE_cause0(os, source_bci, 1, max_detail-1)) {
        //  Returned false. Max recursion depth was reached. Print dummy.
        os->print("<array>");
      }
      os->print("[");
      // Print the index expression. Go back to the bytecode that
      // pushed the index on the operand stack.
      // Don't decrement maxdetail so we get a value here and only 
      // cancel out on the dereference.
      if (!print_NPE_cause0(os, source_bci, 0, max_detail)) {
        // Returned false. We don't print complex array index expressions. Print placeholder.
        os->print("...");
      }
      os->print("]");
      return true;
    }

    case Bytecodes::_getstatic: {
      int cp_index = Bytes::get_native_u2(code_base + pos) + ConstantPool::CPCACHE_INDEX_TAG;
      os->print("static ");
      print_field_and_class(os, _method, cp_index);
      return true;
    }

    case Bytecodes::_getfield: {
      // Print the sender. Go back to the bytecode that
      // pushed the sender on the operand stack.
      if (print_NPE_cause0(os, source_bci, 0, max_detail - 1)) {
        os->print(".");
      }
      int cp_index = Bytes::get_native_u2(code_base + pos) + ConstantPool::CPCACHE_INDEX_TAG;
      os->print("%s", get_field_name(_method, cp_index));
      return true;
    }

    case Bytecodes::_invokevirtual:
    case Bytecodes::_invokespecial:
    case Bytecodes::_invokestatic:
    case Bytecodes::_invokeinterface: {
      int cp_index = Bytes::get_native_u2(code_base + pos) DEBUG_ONLY(+ ConstantPool::CPCACHE_INDEX_TAG);
      if (max_detail == _max_cause_detail) {
        os->print("The return value of '");
      }
      print_method_name(os, _method, cp_index);
      return true;
    }

    default: break;
  }
  return false;
}

void TrackingStackCreator::print_NPE_failedAction(outputStream *os, int bci) {
  // If this NPE was created via reflection, we have no real NPE.
  assert(_method->method_holder() != SystemDictionary::reflect_NativeConstructorAccessorImpl_klass(),
         "We should have checked for reflection in get_NPE_null_slot().");

  // Get the bytecode.
  address code_base = _method->constMethod()->code_base();
  Bytecodes::Code code = Bytecodes::java_code_at(_method, code_base + bci);
  int pos = bci + 1;
  if (code == Bytecodes::_wide) {
    code = Bytecodes::java_code_at(_method, code_base + bci + 1);
    pos += 1;
  }

  switch (code) {
    case Bytecodes::_iaload:
      os->print("Can not load from null int array."); break;
    case Bytecodes::_faload:
      os->print("Can not load from null float array."); break;
    case Bytecodes::_aaload:
      os->print("Can not load from null object array."); break;
    case Bytecodes::_baload:
      os->print("Can not load from null byte/boolean array."); break;
    case Bytecodes::_caload:
      os->print("Can not load from null char array."); break;
    case Bytecodes::_saload:
      os->print("Can not load from null short array."); break;
    case Bytecodes::_laload:
      os->print("Can not load from null long array."); break;
    case Bytecodes::_daload:
      os->print("Can not load from null double array."); break;

    case Bytecodes::_iastore:
      os->print("Can not store to null int array."); break;
    case Bytecodes::_fastore:
      os->print("Can not store to null float array."); break;
    case Bytecodes::_aastore:
      os->print("Can not store to null object array."); break;
    case Bytecodes::_bastore:
      os->print("Can not store to null byte/boolean array."); break;
    case Bytecodes::_castore:
      os->print("Can not store to null char array."); break;
    case Bytecodes::_sastore:
      os->print("Can not store to null short array."); break;
    case Bytecodes::_lastore:
      os->print("Can not store to null long array."); break;
    case Bytecodes::_dastore:
      os->print("Can not store to null double array."); break;

    case Bytecodes::_arraylength:
      os->print("Can not read the array length."); break;
    case Bytecodes::_athrow:
      os->print("Can not throw a null exception object."); break;
    case Bytecodes::_monitorenter:
      os->print("Can not enter a null monitor."); break;
    case Bytecodes::_monitorexit:
      os->print("Can not exit a null monitor."); break;
    case Bytecodes::_getfield: {
        int cp_index = Bytes::get_native_u2(code_base + pos) DEBUG_ONLY(+ ConstantPool::CPCACHE_INDEX_TAG);
        ConstantPool* cp = _method->constants();
        int name_and_type_index = cp->name_and_type_ref_index_at(cp_index);
        int name_index = cp->name_ref_index_at(name_and_type_index);
        Symbol* name = cp->symbol_at(name_index);
        os->print("Can not read field '%s'.", name->as_C_string());
      } break;
    case Bytecodes::_putfield: {
        int cp_index = Bytes::get_native_u2(code_base + pos) DEBUG_ONLY(+ ConstantPool::CPCACHE_INDEX_TAG);
        os->print("Can not write field '%s'.", get_field_name(_method, cp_index));
      } break;
    case Bytecodes::_invokevirtual:
    case Bytecodes::_invokespecial:
    case Bytecodes::_invokeinterface: {
        int cp_index = Bytes::get_native_u2(code_base+ pos) DEBUG_ONLY(+ ConstantPool::CPCACHE_INDEX_TAG);
        os->print("Can not invoke method '");
        print_method_name(os, _method, cp_index);
        os->print("'.");
      } break;

    default:
      assert(0, "We should have checked this bytecode in get_NPE_null_slot().");
      break;
  }
}

