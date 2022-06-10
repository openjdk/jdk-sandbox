/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.classfile.Attributes;
import jdk.classfile.Classfile;

import jdk.classfile.CodeModel;
import jdk.classfile.Instruction;
import jdk.classfile.MethodModel;
import jdk.classfile.attribute.StackMapTableAttribute;

/**
 * Annotate instructions with stack map.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class StackMapWriter extends InstructionDetailWriter {
    static StackMapWriter instance(Context context) {
        StackMapWriter instance = context.get(StackMapWriter.class);
        if (instance == null)
            instance = new StackMapWriter(context);
        return instance;
    }

    protected StackMapWriter(Context context) {
        super(context);
        context.put(StackMapWriter.class, this);
        classWriter = ClassWriter.instance(context);
    }

    public void reset(CodeModel attr) {
        setStackMap(attr.findAttribute(Attributes.STACK_MAP_TABLE).orElse(null), attr.parent().get());
    }

    void setStackMap(StackMapTableAttribute attr, MethodModel m) {
        if (attr == null) {
            map = null;
            return;
        }
        var args = m.descriptorSymbol().parameterArray();
        boolean isStatic = (m.flags().flagsMask() & Classfile.ACC_STATIC) != 0;

        map = new HashMap<>();
        var initFrame = attr.initFrame();
        firstLocal = isStatic ? null : initFrame.declaredLocals().get(0);
        map.put(-1, initFrame);

        for (var fr : attr.entries())
            map.put(fr.absoluteOffset(), fr);
    }

    public void writeInitialDetails() {
        writeDetails(-1);
    }

    @Override
    public void writeDetails(int pc, Instruction instr) {
        writeDetails(pc);
    }

    private void writeDetails(int pc) {
        if (map == null)
            return;

        var m = map.get(pc);
        if (m != null) {
            print("StackMap locals: ", m.effectiveLocals());
            print("StackMap stack: ", m.effectiveStack());
        }

    }

    void print(String label, List<StackMapTableAttribute.VerificationTypeInfo> entries) {
        print(label);
        for (var e : entries) {
            print(" ");
            print(e);
        }
        println();
    }

    void print(StackMapTableAttribute.VerificationTypeInfo entry) {
        if (entry == null) {
            print("ERROR");
            return;
        }

        switch (entry.type()) {
            case ITEM_TOP ->
                print("top");

            case ITEM_INTEGER ->
                print("int");

            case ITEM_FLOAT ->
                print("float");

            case ITEM_LONG ->
                print("long");

            case ITEM_DOUBLE ->
                print("double");

            case ITEM_NULL ->
                print("null");

            case ITEM_UNINITIALIZED_THIS ->
                print("uninit_this");

            case ITEM_OBJECT ->
                print(entry == firstLocal ? "this" : ((StackMapTableAttribute.ObjectVerificationTypeInfo)entry).className().asInternalName());

            case ITEM_UNINITIALIZED ->
                print(((StackMapTableAttribute.UninitializedVerificationTypeInfo) entry).offset());
        }

    }

    private Map<Integer, StackMapTableAttribute.StackMapFrame> map;
    private ClassWriter classWriter;
    private StackMapTableAttribute.VerificationTypeInfo firstLocal;
}
