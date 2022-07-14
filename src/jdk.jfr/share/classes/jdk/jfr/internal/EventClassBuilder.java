/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal;

import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.*;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import jdk.classfile.*;
import jdk.classfile.constantpool.*;
import jdk.classfile.jdktypes.*;
import jdk.classfile.attribute.RuntimeVisibleAnnotationsAttribute;

import jdk.jfr.AnnotationElement;
import jdk.jfr.Event;
import jdk.jfr.ValueDescriptor;


// Helper class for building dynamic events
public final class EventClassBuilder {

    private static final String TYPE_EVENT = "jdk/jfr/Event";
    private static final String TYPE_IOBE = "java/lang/IndexOutOfBoundsException";
    private static final AtomicLong idCounter = new AtomicLong();
    private final String fullClassName, internalClassName;
    private final List<ValueDescriptor> fields;
    private final List<AnnotationElement> annotationElements;

    public EventClassBuilder(List<AnnotationElement> annotationElements, List<ValueDescriptor> fields) {
        this.fullClassName = "jdk.jfr.DynamicEvent" + idCounter.incrementAndGet();
        this.internalClassName = fullClassName.replace('.', '/');
        this.fields = fields;
        this.annotationElements = annotationElements;
    }

    public Class<? extends Event> build() {
        String internalSuperName = ASMToolkit.getInternalName(Event.class.getName());
        byte[] bytes = Classfile.build(ClassDesc.ofInternalName(internalClassName), clb -> {
            clb.withFlags(Classfile.ACC_PUBLIC + Classfile.ACC_FINAL + Classfile.ACC_SUPER);
            clb.withSuperclass(ClassDesc.ofInternalName(internalSuperName));
            buildAnnotations(clb);
            buildConstructor(clb);
            buildFields(clb);
            buildSetMethod(clb);
        });
        ASMToolkit.logASM(fullClassName, bytes);
        return SecuritySupport.defineClass(Event.class, bytes).asSubclass(Event.class);
    }

    private void buildSetMethod(ClassBuilder clb) {
        clb.withMethod("set", MethodTypeDesc.ofDescriptor("(ILjava/lang/Object;)V"), Classfile.ACC_PUBLIC, mb -> mb.withFlags(Classfile.ACC_PUBLIC).withCode(cob -> {
            int index = 0;
            for (ValueDescriptor v : fields) {
                cob.loadInstruction(TypeKind.IntType, 1);
                cob.constantInstruction(index);
                var notEqual = cob.newLabel();
                cob.branchInstruction(Opcode.IF_ICMPNE, notEqual);
                cob.loadInstruction(TypeKind.ReferenceType, 0);
                cob.loadInstruction(TypeKind.ReferenceType, 2);
                var fieldType = ASMToolkit.getDescriptor(v.getTypeName());
                unbox(cob, fieldType);
                cob.fieldInstruction(Opcode.PUTFIELD, ClassDesc.ofDescriptor(internalClassName), v.getName(), ClassDesc.ofDescriptor(fieldType));
                cob.returnInstruction(TypeKind.VoidType);
                cob.labelBinding(notEqual);
                index++;
            }
            cob.newObjectInstruction(ClassDesc.ofInternalName(TYPE_IOBE));
            cob.stackInstruction(Opcode.DUP);
            cob.constantInstruction("Index must between 0 and " + fields.size());
            cob.invokeInstruction(Opcode.INVOKESPECIAL, ClassDesc.ofDescriptor(TYPE_IOBE), "<init>", MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V"), false);
            cob.throwInstruction();
        }));
    }

    public static void unbox(CodeBuilder cob, final String type) {
        var boxedType = "Ljava/lang/Number;";
        String unboxMethodName = null, unboxMethodSig = null;
        switch (type) {
            case "V":
                return;
            case "C":
                boxedType = "Ljava/lang/Character;";
                unboxMethodName = "charValue";
                unboxMethodSig = "()C";
                break;
            case "Z":
                boxedType = "Ljava/lang/Boolean;";
                unboxMethodName = "booleanValue";
                unboxMethodSig = "()Z";
                break;
            case "D":
                unboxMethodName = "doubleValue";
                unboxMethodSig = "()D";
                break;
            case "F":
                unboxMethodName = "floatValue";
                unboxMethodSig = "()F";
                break;
            case "J":
                unboxMethodName = "longValue";
                unboxMethodSig = "()J";
                break;
            case "I":
            case "S":
            case "B":
                unboxMethodName = "intValue";
                unboxMethodSig = "()I";
                break;
        }
        if (unboxMethodName == null) {
            cob.typeCheckInstruction(Opcode.CHECKCAST, ClassDesc.ofDescriptor(type));
        } else {
            cob.typeCheckInstruction(Opcode.CHECKCAST, ClassDesc.ofDescriptor(boxedType));
            cob.invokeInstruction(Opcode.INVOKEVIRTUAL, ClassDesc.ofDescriptor(boxedType), unboxMethodName, MethodTypeDesc.ofDescriptor(unboxMethodSig), false);
        }
    }

    private void buildConstructor(ClassBuilder clb) {
        clb.withMethod("<init>", MethodTypeDesc.of(CD_void), Classfile.ACC_PUBLIC, mb -> mb.withFlags(Classfile.ACC_PUBLIC).withCode(cob -> {
            cob.loadInstruction(TypeKind.ReferenceType, 0);
            cob.invokeInstruction(Opcode.INVOKESPECIAL, ClassDesc.ofDescriptor(TYPE_EVENT), "<init>", MethodTypeDesc.of(CD_void), false);
            cob.returnInstruction(TypeKind.VoidType);
        }));
    }

    private void buildAnnotations(ClassBuilder clb) {
        if (annotationElements.isEmpty())
            return;
        List<Annotation> result = new ArrayList<>(annotationElements.size());
        ConstantPoolBuilder constantPoolBuilder = clb.constantPool();
        for (AnnotationElement a : annotationElements) {
            result.add(Annotation.of(
                    ClassDesc.ofDescriptor(ASMToolkit.getDescriptor(a.getTypeName())),
                    a.getValueDescriptors().stream().map(v -> jdk.classfile.AnnotationElement.of(
                            v.getName(),
                            AnnotationValue.of(a.getValue(v.getName())))).toList()));
        }
        clb.with(RuntimeVisibleAnnotationsAttribute.of(result));
    }

    private void buildFields(ClassBuilder clb) {
        for (ValueDescriptor v : fields) {
            String internal = ASMToolkit.getDescriptor(v.getTypeName());
            clb.withField(v.getName(), ClassDesc.ofDescriptor(internal), Classfile.ACC_PRIVATE);
            // No need to store annotations on field since they will be replaced anyway.
        }
    }
}
