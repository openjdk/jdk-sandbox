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

package jdk.classfile.impl;

import java.lang.constant.ConstantDescs;
import java.util.List;

import jdk.classfile.constantpool.ClassEntry;
import java.lang.reflect.AccessFlag;
import jdk.classfile.attribute.StackMapTableAttribute.*;
import jdk.classfile.ClassReader;

import static jdk.classfile.Classfile.*;
import jdk.classfile.Label;
import jdk.classfile.MethodModel;
import static jdk.classfile.attribute.StackMapTableAttribute.VerificationType.*;

public class StackMapDecoder {
    static final VerificationTypeInfo soleTopVerificationTypeInfo = new SimpleVerificationTypeInfoImpl(ITEM_TOP);
    static final VerificationTypeInfo soleIntegerVerificationTypeInfo = new SimpleVerificationTypeInfoImpl(ITEM_INTEGER);
    static final VerificationTypeInfo soleFloatVerificationTypeInfo = new SimpleVerificationTypeInfoImpl(ITEM_FLOAT);
    static final VerificationTypeInfo soleDoubleVerificationTypeInfo = new SimpleVerificationTypeInfoImpl(ITEM_DOUBLE);
    static final VerificationTypeInfo soleLongVerificationTypeInfo = new SimpleVerificationTypeInfoImpl(ITEM_LONG);
    static final VerificationTypeInfo soleNullVerificationTypeInfo = new SimpleVerificationTypeInfoImpl(ITEM_NULL);
    static final VerificationTypeInfo soleUninitializedThisVerificationTypeInfo = new SimpleVerificationTypeInfoImpl(ITEM_UNINITIALIZED_THIS);

    private static final int
                    SAME_LOCALS_1_STACK_ITEM_EXTENDED = 247,
                    SAME_EXTENDED = 251;

    private final ClassReader classReader;
    private final int pos;
    private final LabelContext ctx;
    private final List<VerificationTypeInfo> initFrameLocals;
    private int p;

    StackMapDecoder(ClassReader classReader, int pos, LabelContext ctx, List<VerificationTypeInfo> initFrameLocals) {
        this.classReader = classReader;
        this.pos = pos;
        this.ctx = ctx;
        this.initFrameLocals = initFrameLocals;
    }

    static List<VerificationTypeInfo> initFrameLocals(MethodModel method) {
        VerificationTypeInfo vtis[];
        var mdesc = method.methodTypeSymbol();
        int i = 0;
        if (!method.flags().has(AccessFlag.STATIC)) {
            vtis = new VerificationTypeInfo[mdesc.parameterCount() + 1];
            var thisClass = method.parent().orElseThrow().thisClass();
            if ("<init>".equals(method.methodName().stringValue()) && !ConstantDescs.CD_Object.equals(thisClass.asSymbol())) {
                vtis[i++] = StackMapDecoder.soleUninitializedThisVerificationTypeInfo;
            } else {
                vtis[i++] = new StackMapDecoder.ObjectVerificationTypeInfoImpl(thisClass);
            }
        } else {
            vtis = new VerificationTypeInfo[mdesc.parameterCount()];
        }
        for(var arg : mdesc.parameterList()) {
            vtis[i++] = switch (arg.descriptorString()) {
                case "I", "S", "C" ,"B", "Z" ->  StackMapDecoder.soleIntegerVerificationTypeInfo;
                case "J" -> StackMapDecoder.soleLongVerificationTypeInfo;
                case "F" -> StackMapDecoder.soleFloatVerificationTypeInfo;
                case "D" -> StackMapDecoder.soleDoubleVerificationTypeInfo;
                case "V" -> throw new IllegalArgumentException("Illegal method argument type: " + arg);
                default -> new StackMapDecoder.ObjectVerificationTypeInfoImpl(TemporaryConstantPool.INSTANCE.classEntry(arg));
            };
        }
        return List.of(vtis);
    }

    List<StackMapFrame> entries() {
        p = pos;
        List<VerificationTypeInfo> locals = initFrameLocals, stack = List.of();
        int bci = -1;
        var entries = new StackMapFrame[u2()];
        for (int ei = 0; ei < entries.length; ei++) {
            int frameType = classReader.readU1(p++);
            if (frameType < 64) {
                bci += frameType + 1;
                stack = List.of();
            } else if (frameType < 128) {
                bci += frameType - 63;
                stack = List.of(readVerificationTypeInfo(bci));
            } else {
                if (frameType < SAME_LOCALS_1_STACK_ITEM_EXTENDED)
                    throw new IllegalArgumentException("Invalid stackmap frame type: " + frameType);
                bci += u2() + 1;
                if (frameType == SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
                    stack = List.of(readVerificationTypeInfo(bci));
                } else if (frameType < SAME_EXTENDED) {
                    locals = locals.subList(0, locals.size() + frameType - SAME_EXTENDED);
                    stack = List.of();
                } else if (frameType == SAME_EXTENDED) {
                    stack = List.of();
                } else if (frameType < SAME_EXTENDED + 4) {
                    int actSize = locals.size();
                    var newLocals = locals.toArray(new VerificationTypeInfo[actSize + frameType - SAME_EXTENDED]);
                    for (int i = actSize; i < newLocals.length; i++)
                        newLocals[i] = readVerificationTypeInfo(bci);
                    locals = List.of(newLocals);
                    stack = List.of();
                } else {
                    var newLocals = new VerificationTypeInfo[u2()];
                    for (int i=0; i<newLocals.length; i++)
                        newLocals[i] = readVerificationTypeInfo(bci);
                    var newStack = new VerificationTypeInfo[u2()];
                    for (int i=0; i<newStack.length; i++)
                        newStack[i] = readVerificationTypeInfo(bci);
                    locals = List.of(newLocals);
                    stack = List.of(newStack);
                }
            }
            entries[ei] = new StackMapFrameImpl(frameType,
                        ctx.getLabel(bci),
                        locals,
                        stack);
        }
        return List.of(entries);
    }

    private VerificationTypeInfo readVerificationTypeInfo(int bci) {
        int tag = classReader.readU1(p++);
        return switch (tag) {
            case VT_TOP -> soleTopVerificationTypeInfo;
            case VT_INTEGER -> soleIntegerVerificationTypeInfo;
            case VT_FLOAT -> soleFloatVerificationTypeInfo;
            case VT_DOUBLE -> soleDoubleVerificationTypeInfo;
            case VT_LONG -> soleLongVerificationTypeInfo;
            case VT_NULL -> soleNullVerificationTypeInfo;
            case VT_UNINITIALIZED_THIS -> soleUninitializedThisVerificationTypeInfo;
            case VT_OBJECT -> new ObjectVerificationTypeInfoImpl((ClassEntry)classReader.entryByIndex(u2()));
            case VT_UNINITIALIZED -> new UninitializedVerificationTypeInfoImpl(ctx.getLabel(bci + u2()));
            default -> throw new IllegalArgumentException("Invalid verification type tag: " + tag);
        };
    }

    public static record SimpleVerificationTypeInfoImpl(VerificationType type) implements SimpleVerificationTypeInfo {

        @Override
        public String toString() {
            return switch (type) {
                case ITEM_DOUBLE -> "D";
                case ITEM_FLOAT -> "F";
                case ITEM_INTEGER -> "I";
                case ITEM_LONG -> "J";
                case ITEM_NULL -> "null";
                case ITEM_TOP -> "?";
                case ITEM_UNINITIALIZED_THIS -> "THIS";
                default -> throw new AssertionError("should never happen");
            };
        }
    }

    public static record ObjectVerificationTypeInfoImpl(
            ClassEntry className) implements ObjectVerificationTypeInfo {

        @Override
        public VerificationType type() { return VerificationType.ITEM_OBJECT; }

        @Override
        public String toString() {
            return className.asInternalName();
        }
    }

    public static record UninitializedVerificationTypeInfoImpl(Label newTarget) implements UninitializedVerificationTypeInfo {

        @Override
        public VerificationType type() { return VerificationType.ITEM_UNINITIALIZED; }

        @Override
        public String toString() {
            return "UNINIT(" + newTarget +")";
        }
    }

    private int u2() {
        int v = classReader.readU2(p);
        p += 2;
        return v;
    }

    public static record StackMapFrameImpl(int frameType,
                                           Label target,
                                           List<VerificationTypeInfo> locals,
                                           List<VerificationTypeInfo> stack)
            implements StackMapFrame {
    }
}
