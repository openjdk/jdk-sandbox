dnl Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
dnl DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
dnl
dnl This code is free software; you can redistribute it and/or modify it
dnl under the terms of the GNU General Public License version 2 only, as
dnl published by the Free Software Foundation.
dnl
dnl This code is distributed in the hope that it will be useful, but WITHOUT
dnl ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
dnl FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
dnl version 2 for more details (a copy is included in the LICENSE file that
dnl accompanied this code).
dnl
dnl You should have received a copy of the GNU General Public License version
dnl 2 along with this work; if not, write to the Free Software Foundation,
dnl Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
dnl
dnl Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
dnl or visit www.oracle.com if you need additional information or have any
dnl questions.
dnl
// BEGIN This section of the file is automatically generated from shenandoah_aarch64.m4.


define(`STORE_INSN',
`
// This pattern is generated automatically from shenandoah_aarch64.m4.
// DO NOT EDIT ANYTHING IN THIS SECTION OF THE FILE
instruct store_$1_$2_shenandoah(indirect mem, iReg$1 src, iRegPNoSp tmp, rFlagsReg cr)
%{
  match(Set mem (Store$1 mem src));
  predicate(UseShenandoahGC && ifelse($2,Volatile,'needs_releasing_store(n)`,'!needs_releasing_store(n)`) && n->as_Store()->barrier_data() != 0);
  effect(TEMP tmp, KILL cr);
  ins_cost(ifelse($2,Volatile,VOLATILE_REF_COST,3*INSN_COST));
  format %{ "str  $src, $mem" %}
  ins_encode %{
    ShenandoahBarrierSet::assembler()->store_c2(this, masm,
      $mem$$Register, /* dst_narrow  = */ ifelse($1,N,'true`,'false`),
      $src$$Register, /* src_narrow  = */ ifelse($1,N,'true`,'false`),
      $tmp$$Register, /* pre_val     = */ noreg,
                      /* is_volatile = */ ifelse($2,Volatile,'true`,'false`));
  %}
  ins_pipe(pipe_class_memory);
%}')dnl
STORE_INSN(P,Normal)
STORE_INSN(P,Volatile)
STORE_INSN(N,Normal)
STORE_INSN(N,Volatile)









define(`ENCODEP_AND_STORE_INSN',
`
// This pattern is generated automatically from shenandoah_aarch64.m4.
// DO NOT EDIT ANYTHING IN THIS SECTION OF THE FILE
instruct encodePAndStoreN_$1_shenandoah(indirect mem, iRegP src, iRegPNoSp tmp, rFlagsReg cr)
%{
  match(Set mem (StoreN mem (EncodeP src)));
  predicate(UseShenandoahGC && ifelse($1,Volatile,'needs_releasing_store(n)`,'!needs_releasing_store(n)`) && n->as_Store()->barrier_data() != 0);
  effect(TEMP tmp, KILL cr);
  ins_cost(ifelse($1,Volatile,VOLATILE_REF_COST,4*INSN_COST));
  format %{ "encode_heap_oop $tmp, $src\n\t"
            "str  $tmp, $mem\t# compressed ptr" %}
  ins_encode %{
    ShenandoahBarrierSet::assembler()->store_c2(this, masm,
      $mem$$Register, /* dst_narrow  = */ true,
      $src$$Register, /* src_narrow  = */ false,
      $tmp$$Register, /* pre_val     = */ noreg,
                      /* is_volatile = */ ifelse($1,Volatile,'true`,'false`));
  %}
  ins_pipe(pipe_class_memory);
%}')dnl
ENCODEP_AND_STORE_INSN(Normal)
ENCODEP_AND_STORE_INSN(Volatile)









define(`CMPANDSWP_INSN',
`
// This pattern is generated automatically from shenandoah_aarch64.m4.
// DO NOT EDIT ANYTHING IN THIS SECTION OF THE FILE
instruct compareAndSwap_$1_$2_shenandoah(iRegINoSp res, indirect mem, iReg$1NoSp oldval, iReg$1 newval, iRegPNoSp tmp1, iRegPNoSp tmp2, rFlagsReg cr)
%{
  match(Set res (CompareAndSwap$1 mem (Binary oldval newval)));
  predicate(UseShenandoahGC && ifelse($2,Volatile,'needs_acquiring_load_exclusive(n)`,'!needs_acquiring_load_exclusive(n)`) && n->as_LoadStore()->barrier_data() != 0);
  ins_cost(ifelse($2,Volatile,VOLATILE_REF_COST + 3*INSN_COST,VOLATILE_REF_COST));
  effect(TEMP_DEF res, TEMP tmp1, TEMP tmp2, KILL cr);
  format %{
    "cmpxchg_$1_$2_shenandoah $mem, $oldval, $newval\t# (ptr) if $mem == $oldval then $mem <-- $newval"
  %}
  ins_encode %{
    assert_different_registers($tmp1$$Register, $tmp2$$Register);
    guarantee($mem$$index == -1 && $mem$$disp == 0, "impossible encoding");

    ShenandoahBarrierSet::assembler()->cae_c2(this, masm,
      $res$$Register,
      $mem$$base$$Register,
      $oldval$$Register,
      $newval$$Register,
      $tmp1$$Register,
      $tmp2$$Register,
      /* exchange */ false,
      /* maybe_null */ false,
      /* is_narrow */ ifelse($1,N,'true`,'false`),
      /* acquire */ ifelse($2,Volatile,'true`,'false`),
      /* release */ true,
      /* weak */ false);
  %}

  ins_pipe(pipe_slow);
%}')dnl
CMPANDSWP_INSN(P,Normal)
CMPANDSWP_INSN(N,Normal)
CMPANDSWP_INSN(P,Volatile)
CMPANDSWP_INSN(N,Volatile)





define(`CMPANDXCGH_INSN',
`
// This pattern is generated automatically from shenandoah_aarch64.m4.
// DO NOT EDIT ANYTHING IN THIS SECTION OF THE FILE
instruct compareAndExchange_$1_$2_shenandoah(iReg$1NoSp res, indirect mem, iReg$1 oldval, iReg$1 newval, iRegPNoSp tmp1, iRegPNoSp tmp2, rFlagsReg cr)
%{
  match(Set res (CompareAndExchange$1 mem (Binary oldval newval)));
  predicate(UseShenandoahGC && ifelse($2,Volatile,'needs_acquiring_load_exclusive(n)`,'!needs_acquiring_load_exclusive(n)`) && n->as_LoadStore()->barrier_data() != 0);
  ins_cost(ifelse($2,Volatile,VOLATILE_REF_COST + 3*INSN_COST,2*VOLATILE_REF_COST));
  effect(TEMP_DEF res, TEMP tmp1, TEMP tmp2, KILL cr);
  format %{
    "cae_$1_$2_shenandoah $mem, $oldval, $newval\t# (ptr) if $mem == $oldval then $mem <-- $newval"
  %}
  ins_encode %{
    assert_different_registers($tmp1$$Register, $tmp2$$Register);
    guarantee($mem$$index == -1 && $mem$$disp == 0, "impossible encoding");

    ShenandoahBarrierSet::assembler()->cae_c2(this, masm,
      $res$$Register,
      $mem$$base$$Register,
      $oldval$$Register,
      $newval$$Register,
      $tmp1$$Register,
      $tmp2$$Register,
      /* exchange */ true,
      /* maybe_null */ (this->bottom_type()->make_ptr()->ptr() != TypePtr::NotNull),
      /* is_narrow */ ifelse($1,N,'true`,'false`),
      /* acquire */ ifelse($2,Volatile,'true`,'false`),
      /* release */ true,
      /* weak */ false);
  %}
  ins_pipe(pipe_slow);
%}')dnl
CMPANDXCGH_INSN(N,Normal)
CMPANDXCGH_INSN(P,Normal)
CMPANDXCGH_INSN(N,Volatile)
CMPANDXCGH_INSN(P,Volatile)







define(`WEAKCMPANDSWAP_INSN',
`
// This pattern is generated automatically from shenandoah_aarch64.m4.
// DO NOT EDIT ANYTHING IN THIS SECTION OF THE FILE
instruct weakCompareAndSwap_$1_$2_shenandoah(iRegINoSp res, indirect mem, iReg$1NoSp oldval, iReg$1 newval, iRegPNoSp tmp1, iRegPNoSp tmp2, rFlagsReg cr)
%{
  match(Set res (WeakCompareAndSwap$1 mem (Binary oldval newval)));
  predicate(UseShenandoahGC && ifelse($2,Volatile,'needs_acquiring_load_exclusive(n)`,'!needs_acquiring_load_exclusive(n)`) && n->as_LoadStore()->barrier_data() != 0);
  ins_cost(VOLATILE_REF_COST);
  effect(TEMP_DEF res, TEMP tmp1, TEMP tmp2, KILL cr);
  format %{
    "cae_$1_$2_weak_shenandoah $res = $mem, $oldval, $newval\t# ($1, weak, $2) if $mem == $oldval then $mem <-- $newval\n\t"
    "csetw $res, EQ\t# $res <-- (EQ ? 1 : 0)"
  %}
  ins_encode %{
    assert_different_registers($tmp1$$Register, $tmp2$$Register);
    guarantee($mem$$index == -1 && $mem$$disp == 0, "impossible encoding");

    ShenandoahBarrierSet::assembler()->cae_c2(this, masm,
      $res$$Register,
      $mem$$base$$Register,
      $oldval$$Register,
      $newval$$Register,
      $tmp1$$Register,
      $tmp2$$Register,
      /* exchange */ false,
      /* maybe_null */ false,
      /* is_narrow */ ifelse($1,N,'true`,'false`),
      /* acquire */ ifelse($2,Volatile,'true`,'false`),
      /* release */ true,
      /* weak */ true);
  %}
  ins_pipe(pipe_slow);
%}')dnl
WEAKCMPANDSWAP_INSN(N,Normal)
WEAKCMPANDSWAP_INSN(P,Normal)
WEAKCMPANDSWAP_INSN(N,Volatile)
WEAKCMPANDSWAP_INSN(P,Volatile)


define(`GETANDSET_INSN',
`
// This pattern is generated automatically from shenandoah_aarch64.m4.
// DO NOT EDIT ANYTHING IN THIS SECTION OF THE FILE
instruct getAndSet_$1_$2_shenandoah(indirect mem, iReg$1 newval, iReg$1NoSp preval, rFlagsReg cr)
%{
  match(Set preval (GetAndSet$1 mem newval));
  predicate(UseShenandoahGC && ifelse($2,Volatile,'needs_acquiring_load_exclusive(n)`,'!needs_acquiring_load_exclusive(n)`) && n->as_LoadStore()->barrier_data() != 0);
  effect(TEMP_DEF preval, KILL cr);
  ins_cost(ifelse($2,Volatile,VOLATILE_REF_COST + 3*INSN_COST,2*VOLATILE_REF_COST));
  format %{ "get_and_set_$1_$2 $preval, $newval, [$mem]" %}
  ins_encode %{
    ShenandoahBarrierSet::assembler()->get_and_set_c2(this, masm,
      $preval$$Register,
      $newval$$base$$Register,
      $mem$$Register,
      /* maybe_null */ (this->bottom_type()->make_ptr()->ptr() != TypePtr::NotNull),
      /* is_narrow */ ifelse($1,N,'true`,'false`),
      /* acquire */ ifelse($2,Volatile,'true`,'false`));
  %}
  ins_pipe(pipe_serial);
%}')dnl
GETANDSET_INSN(P,Normal)
GETANDSET_INSN(P,Volatile)
GETANDSET_INSN(N,Normal)
GETANDSET_INSN(N,Volatile)

define(`LOAD_INSN',
`
// This pattern is generated automatically from shenandoah_aarch64.m4.
// DO NOT EDIT ANYTHING IN THIS SECTION OF THE FILE
instruct load_$1_$2_shenandoah(iReg$1NoSp dst, indirect mem, iRegPNoSp tmp, rFlagsReg cr)
%{
  match(Set dst (Load$1 mem));
  predicate(UseShenandoahGC && ifelse($2,Volatile,'needs_acquiring_load(n)`,'!needs_acquiring_load(n)`) && n->as_Load()->barrier_data() != 0);
  effect(TEMP_DEF dst, TEMP tmp, KILL cr);
  ins_cost(ifelse($2,Volatile,VOLATILE_REF_COST,3*INSN_COST));
  format %{ "$3  $dst, $mem\t# ptr" %}
  ins_encode %{
    __ $3($dst$$Register, $mem$$Register);

    Address gcs_addr(rthread, in_bytes(ShenandoahThreadLocalData::gc_state_offset()));
    __ ldrb($tmp$$Register, gcs_addr);

    bool is_narrow = ifelse($1,N,'true`,'false`);
    ShenandoahBarrierSet::assembler()->satb_barrier_c2(this, masm,
                                                        noreg            /* obj */,
                                                        $dst$$Register   /* pre_val, in this case it will be only used in the slowpath as tmp. */,
                                                        $tmp$$Register   /* gc_state */,
                                                        is_narrow        /* encoded_preval */);

    bool maybe_null = (this->bottom_type()->make_ptr()->ptr() != TypePtr::NotNull);
    ShenandoahBarrierSet::assembler()->load_ref_barrier_c2(this, masm,
                                                        $dst$$Register /* obj */,
                                                        $mem$$Register /* addr */,
                                                        is_narrow      /* narrow */,
                                                        maybe_null,
                                                        $tmp$$Register /* gc_state */);
  %}
  ins_pipe(pipe_class_memory);
%}')dnl
LOAD_INSN(P,Normal,ldr)
LOAD_INSN(P,Volatile,ldar)
LOAD_INSN(N,Normal,ldrw)
LOAD_INSN(N,Volatile,ldarw)

// END This section of the file is automatically generated from shenandoah_aarch64.m4.
