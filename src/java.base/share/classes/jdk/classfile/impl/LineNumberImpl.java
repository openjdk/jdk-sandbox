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

import java.util.Optional;

import jdk.classfile.Label;
import jdk.classfile.Opcode;
import jdk.classfile.instruction.LineNumber;

/**
 * LineNumberImpl
 */
public final class LineNumberImpl
        extends AbstractElement
        implements LineNumber {
    private static final int INTERN_LIMIT = 1000;
    private static final LineNumber[] internCache = new LineNumber[INTERN_LIMIT];
    static {
        for (int i=0; i<INTERN_LIMIT; i++)
            internCache[i] = new LineNumberImpl(i, null);
    }

    private final int line;
    private final Label label;

    private LineNumberImpl(int line, Label label) {
        this.line = line;
        this.label = label;
    }

    public static LineNumber of(int line) {
        return (line < INTERN_LIMIT)
               ? internCache[line]
               : new LineNumberImpl(line, null);
    }

    public static LineNumber of(int line, Label label) {
        return new LineNumberImpl(line, label);
    }

    @Override
    public int line() {
        return line;
    }

    public Optional<Label> label() {
        return Optional.ofNullable(label);
    }

    @Override
    public Kind codeKind() {
        return Kind.LINE_NUMBER;
    }

    @Override
    public Opcode opcode() {
        return Opcode.LOCAL_VARIABLE_TYPE;
    }

    @Override
    public int sizeInBytes() {
        return 0;
    }

    @Override
    public void writeTo(DirectCodeBuilder writer) {
        writer.setLineNumber(line, label);
    }

    @Override
    public String toString() {
        return String.format("LineNumber[line=%d,label=%s]", line, label);
    }
}

