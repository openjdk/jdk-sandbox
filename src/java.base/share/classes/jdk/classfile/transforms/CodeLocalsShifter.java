/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.classfile.transforms;

import java.lang.constant.MethodTypeDesc;
import java.util.Arrays;

import java.lang.reflect.AccessFlag;
import jdk.classfile.AccessFlags;
import jdk.classfile.CodeBuilder;
import jdk.classfile.CodeElement;
import jdk.classfile.CodeTransform;
import jdk.classfile.Signature;
import jdk.classfile.instruction.IncrementInstruction;
import jdk.classfile.instruction.LoadInstruction;
import jdk.classfile.instruction.LocalVariable;
import jdk.classfile.instruction.StoreInstruction;
import jdk.classfile.TypeKind;
import jdk.classfile.instruction.LocalVariableType;

/**
 * CodeLocalsShifter is a {@link jdk.classfile.CodeTransform} shifting locals to
 * new index possitions to avoid conflicts during code injection.
 * Locals with indexes pointing to the 'this' or method arguments are never shifted.
 * All locals with indexes pointing beyond the method arguments are re-indexed
 * in order of appearance.
 * <p>
 * A unique index for additional local variable can be manually allocated using
 * {@link jdk.classfile.transforms.CodeLocalsShifter#addLocal(jdk.classfile.TypeKind)}
 * at any time before, during, or after the transformation.
 * <p>
 * A fork of the actual CodeLocalsShifter can be obtained from
 * {@link jdk.classfile.transforms.CodeLocalsShifter#fork()}.
 * Forked CodeLocalsShifter starts with clean locals mapping, however it respects original
 * method argument locals and avoids conflicts with previously allocated or shifted locals.
 * Forked CodeLocalsShifter does not dynamically sync with its parent, nor vice versa.
 * <p>
 * Sample use in complex transformation:
 * <p>
 * {@snippet lang=java :
 *     var localsShifter = new CodeLocalsShifter(methodModel.flags(), methodModel.methodTypeSymbol());
 *     methodBuilder.transformCode(codeModel,
 *          localsShifter
 *          .andThen((codeBuilder, codeElement) -> {
 *              ...
 *              codeBuilder.transform(injectedCodeModel,
 *                                    localsShifter.fork()
 *                                    .andThen(otherInjectedCodeTransforms));
 *              ...
 *          }));
 * }
 */
public sealed interface CodeLocalsShifter extends CodeTransform {

    /**
     * Creates a new instance of CodeLocalsShifter
     * with fixed local slots calculated from provided method information
     * @param methodFlags flags of the method to construct CodeLocalsShifter for
     * @param methodDescriptor descriptor of the method to construct CodeLocalsShifter for
     * @return new instance of CodeLocalsShifter
     */
    static CodeLocalsShifter of(AccessFlags methodFlags, MethodTypeDesc methodDescriptor) {
        int next = methodFlags.has(AccessFlag.STATIC) ? 0 : 1;
        for (var param : methodDescriptor.parameterList())
            next += TypeKind.fromDescriptor(param.descriptorString()).slotSize();
        return new CodeLocalsShifterImpl(next, next);
    }

    /**
     * Reserves additional local variable for exclusive use
     * @param tk type of local variable to reserve
     * @return index of the reserved local variable
     */
    int addLocal(TypeKind tk);

    /**
     * Creates a new instance of CodeLocalsShifter forked from the actual state.
     * Forked CodeLocalsShifter starts with clean locals mapping, however it respects original
     * method argument locals and avoids conflicts with previously allocated or shifted locals.
     * Forked CodeLocalsShifter does not dynamically sync with its parent, nor vice versa.
     * @return new instance of CodeLocalsShifter forked from the actual state
     */
    CodeLocalsShifter fork();

    final static class CodeLocalsShifterImpl implements CodeLocalsShifter {

        private int[] locals = new int[0];
        private final int fixed;
        private int next;

        private CodeLocalsShifterImpl(int fixed, int next) {
            this.fixed = fixed;
            this.next = next;
        }

        @Override
        public CodeLocalsShifter fork() {
            return new CodeLocalsShifterImpl(fixed, next);
        }

        @Override
        public int addLocal(TypeKind tk) {
            int local = next;
            next += tk.slotSize();
            return local;
        }

        @Override
        public void accept(CodeBuilder cob, CodeElement coe) {
            switch (coe) {
                case LoadInstruction li ->
                    cob.loadInstruction(
                            li.typeKind(),
                            shift(li.slot(), li.typeKind()));
                case StoreInstruction si ->
                    cob.storeInstruction(
                            si.typeKind(),
                            shift(si.slot(), si.typeKind()));
                case IncrementInstruction ii ->
                    cob.incrementInstruction(
                            shift(ii.slot(), TypeKind.IntType),
                            ii.constant());
                case LocalVariable lv ->
                    cob.localVariable(
                            shift(lv.slot(), TypeKind.fromDescriptor(lv.type().stringValue())),
                            lv.name(),
                            lv.type(),
                            lv.startScope(),
                            lv.endScope());
                case LocalVariableType lvt ->
                    cob.localVariableType(
                            shift(lvt.slot(),
                                    (lvt.signatureSymbol() instanceof Signature.BaseTypeSig bsig)
                                            ? TypeKind.fromDescriptor(bsig.signatureString())
                                            : TypeKind.ReferenceType),
                            lvt.name(),
                            lvt.signature(),
                            lvt.startScope(),
                            lvt.endScope());
                default -> cob.with(coe);
            }
        }

        private int shift(int slot, TypeKind tk) {
            if (tk == TypeKind.VoidType)  throw new IllegalArgumentException("Illegal local void type");
            if (slot >= fixed) {
                int key = 2*slot - fixed + tk.slotSize() - 1;
                if (key >= locals.length) locals = Arrays.copyOf(locals, key + 20);
                slot = locals[key] - 1;
                if (slot < 0) {
                    slot = addLocal(tk);
                    locals[key] = slot + 1;
                    if (tk.slotSize() == 2) locals[key - 1] = slot + 1;
                }
            }
            return slot;
        }
    }
}
