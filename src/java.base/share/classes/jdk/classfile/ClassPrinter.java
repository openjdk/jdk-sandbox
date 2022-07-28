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
package jdk.classfile;

import java.lang.constant.ConstantDesc;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import jdk.classfile.ClassModel;
import jdk.classfile.MethodModel;
import jdk.classfile.impl.ClassPrinterImpl;

/**
 *
 */
public final class ClassPrinter {

    public enum Verbosity { MEMBERS_ONLY, CRITICAL_ATTRIBUTES, TRACE_ALL }

    public enum Style { BLOCK, FLOW }

    public sealed interface Node {}

    public sealed interface SimpleNode extends Node
            permits ClassPrinterImpl.SimpleNodeImpl {

        public ConstantDesc value();
    }

    public sealed interface ListNode extends Node
            permits ClassPrinterImpl.ListNodeImpl {

        public Style style();

        public String itemName();

        public List<Node> list();
    }

    public sealed interface MapNode extends Node
            permits ClassPrinterImpl.MapNodeImpl {

        public Style style();

        public Map<ConstantDesc, Node> map();
    }

    public static Node toTree(ClassModel classModel, Verbosity verbosity) {
        return ClassPrinterImpl.toTree(classModel, verbosity);
    }

    public static Node toTree(MethodModel methodModel, Verbosity verbosity) {
        return ClassPrinterImpl.toTree(methodModel, verbosity);
    }

    public static void toJson(ClassModel classModel, Verbosity verbosity, Consumer<String> out) {
        ClassPrinterImpl.JSON_PRINTER.printClass(classModel, verbosity, out);
    }

    public static void toJson(MethodModel methodModel, Verbosity verbosity, Consumer<String> out) {
        ClassPrinterImpl.JSON_PRINTER.printMethod(methodModel, verbosity, out);
    }

    public static void toXml(ClassModel classModel, Verbosity verbosity, Consumer<String> out) {
        ClassPrinterImpl.XML_PRINTER.printClass(classModel, verbosity, out);
    }

    public static void toXml(MethodModel methodModel, Verbosity verbosity, Consumer<String> out) {
        ClassPrinterImpl.XML_PRINTER.printMethod(methodModel, verbosity, out);
    }

    public static void toYaml(ClassModel classModel, Verbosity verbosity, Consumer<String> out) {
        ClassPrinterImpl.YAML_PRINTER.printClass(classModel, verbosity, out);
    }

    public static void toYaml(MethodModel methodModel, Verbosity verbosity, Consumer<String> out) {
        ClassPrinterImpl.YAML_PRINTER.printMethod(methodModel, verbosity, out);
    }
}
