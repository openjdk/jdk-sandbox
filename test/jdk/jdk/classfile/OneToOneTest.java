/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

/*
 * @test
 * @summary Testing Classfile class writing and reading.
 * @run junit OneToOneTest
 */
import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.*;
import java.lang.constant.MethodTypeDesc;
import java.util.List;

import jdk.internal.classfile.AccessFlags;
import java.lang.reflect.AccessFlag;
import jdk.internal.classfile.ClassModel;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.Instruction;
import jdk.internal.classfile.Label;
import jdk.internal.classfile.MethodModel;
import jdk.internal.classfile.TypeKind;
import jdk.internal.classfile.attribute.SourceFileAttribute;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import jdk.internal.classfile.instruction.ConstantInstruction;
import jdk.internal.classfile.instruction.StoreInstruction;
import jdk.internal.classfile.instruction.BranchInstruction;
import jdk.internal.classfile.instruction.LoadInstruction;
import jdk.internal.classfile.instruction.OperatorInstruction;
import jdk.internal.classfile.instruction.FieldInstruction;
import jdk.internal.classfile.instruction.InvokeInstruction;

import static helpers.TestConstants.CD_PrintStream;
import static helpers.TestConstants.CD_System;
import static helpers.TestConstants.MTD_INT_VOID;
import static helpers.TestConstants.MTD_VOID;
import static jdk.internal.classfile.Opcode.*;

class OneToOneTest {

    @Test
    void testClassWriteRead() {

        byte[] bytes = Classfile.build(ClassDesc.of("MyClass"), cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.withVersion(52, 0);
            cb.with(SourceFileAttribute.of(cb.constantPool().utf8Entry(("MyClass.java"))))

              .withMethod("<init>", MethodTypeDesc.of(CD_void), 0, mb -> mb
                                  .withCode(codeb -> codeb.loadInstruction(TypeKind.ReferenceType, 0)
                                                          .invokeInstruction(INVOKESPECIAL, CD_Object, "<init>", MTD_VOID, false)
                                                          .returnInstruction(TypeKind.VoidType)
                                  )
              )
              .withMethod("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()),
                          AccessFlags.ofMethod(AccessFlag.STATIC, AccessFlag.PUBLIC).flagsMask(),
                          mb -> mb.withCode(c0 -> {
                                                Label loopTop = c0.newLabel();
                                                Label loopEnd = c0.newLabel();
                                                int fac = 1;
                                                int i = 2;
                                                c0.constantInstruction(ICONST_1, 1)         // 0
                                                  .storeInstruction(TypeKind.IntType, fac)        // 1
                                                  .constantInstruction(ICONST_1, 1)         // 2
                                                  .storeInstruction(TypeKind.IntType, i)          // 3
                                                  .labelBinding(loopTop)
                                                  .loadInstruction(TypeKind.IntType, i)           // 4
                                                  .constantInstruction(BIPUSH, 10)         // 5
                                                  .branchInstruction(IF_ICMPGE, loopEnd) // 6
                                                  .loadInstruction(TypeKind.IntType, fac)         // 7
                                                  .loadInstruction(TypeKind.IntType, i)           // 8
                                                  .operatorInstruction(IMUL)             // 9
                                                  .storeInstruction(TypeKind.IntType, fac)        // 10
                                                  .incrementInstruction(i, 1)    // 11
                                                  .branchInstruction(GOTO, loopTop)     // 12
                                                  .labelBinding(loopEnd)
                                                  .fieldInstruction(GETSTATIC, CD_System, "out", CD_PrintStream)   // 13
                                                  .loadInstruction(TypeKind.IntType, fac)
                                                  .invokeInstruction(INVOKEVIRTUAL, CD_PrintStream, "println", MTD_INT_VOID, false)  // 15
                                                  .returnInstruction(TypeKind.VoidType);
                                            }
                          )
              );
                                       }
        );

        ClassModel cm = Classfile.parse(bytes);
        List<MethodModel> ms = cm.methods();
        assertEquals(2, ms.size());
        boolean found = false;
        for (MethodModel mm : ms) {
            if (mm.methodName().stringValue().equals("main") && mm.code().isPresent()) {
                found = true;
                var code = mm.code().get();
                var instructions = code.elementList().stream()
                                       .filter(e -> e instanceof Instruction)
                                       .map(e -> (Instruction)e)
                                       .toList();
                assertEquals(17, instructions.size());

                assertEquals(ICONST_1, instructions.get(0).opcode());

                var i1 = (StoreInstruction) instructions.get(1);
                assertEquals(ISTORE_1, i1.opcode());
                int lv1 = i1.slot();
                assertEquals(1, lv1);

                ConstantInstruction i5 = (ConstantInstruction) instructions.get(5);
                assertEquals(BIPUSH, i5.opcode());
                assertEquals(10, i5.constantValue());

                BranchInstruction i6 = (BranchInstruction) instructions.get(6);
                assertEquals(IF_ICMPGE, i6.opcode());
                // assertEquals(14, code.instructionOffset(i6.target()));  //FIXME: CodeModel gives BCI, should give instruction offset

                LoadInstruction i7 = (LoadInstruction) instructions.get(7);
                assertEquals(ILOAD_1, i7.opcode());

                OperatorInstruction i9 = (OperatorInstruction) instructions.get(9);
                assertEquals(IMUL, i9.opcode());

                FieldInstruction i13 = (FieldInstruction) instructions.get(13);
                assertEquals(GETSTATIC, i13.opcode());
                assertEquals("java/lang/System", i13.owner().asInternalName());
                assertEquals("out", i13.name().stringValue());
                assertEquals("Ljava/io/PrintStream;", i13.type().stringValue());

                InvokeInstruction i15 = (InvokeInstruction) instructions.get(15);
                assertEquals(INVOKEVIRTUAL, i15.opcode());
                assertEquals("java/io/PrintStream", i15.owner().asInternalName());
                assertEquals("println", i15.name().stringValue());
                assertEquals("(I)V", i15.type().stringValue());
            }
        }
        assertTrue(found);
    }
}
