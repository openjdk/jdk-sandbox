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

import java.lang.constant.ConstantDesc;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.AbstractList;
import java.util.AbstractSequentialList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.classfile.Annotation;

import jdk.classfile.AnnotationElement;
import jdk.classfile.AnnotationValue;
import jdk.classfile.AnnotationValue.*;
import jdk.classfile.Attribute;
import jdk.classfile.ClassModel;
import jdk.classfile.constantpool.*;
import jdk.classfile.ClassPrinter;
import jdk.classfile.ClassPrinter.*;
import jdk.classfile.Instruction;
import jdk.classfile.instruction.*;
import jdk.classfile.MethodModel;
import jdk.classfile.TypeAnnotation;
import jdk.classfile.TypeKind;
import jdk.classfile.attribute.*;
import jdk.classfile.attribute.StackMapTableAttribute.*;

import static jdk.classfile.Classfile.TAG_CLASS;
import static jdk.classfile.Classfile.TAG_CONSTANTDYNAMIC;
import static jdk.classfile.Classfile.TAG_DOUBLE;
import static jdk.classfile.Classfile.TAG_FIELDREF;
import static jdk.classfile.Classfile.TAG_FLOAT;
import static jdk.classfile.Classfile.TAG_INTEGER;
import static jdk.classfile.Classfile.TAG_INTERFACEMETHODREF;
import static jdk.classfile.Classfile.TAG_INVOKEDYNAMIC;
import static jdk.classfile.Classfile.TAG_LONG;
import static jdk.classfile.Classfile.TAG_METHODHANDLE;
import static jdk.classfile.Classfile.TAG_METHODREF;
import static jdk.classfile.Classfile.TAG_METHODTYPE;
import static jdk.classfile.Classfile.TAG_MODULE;
import static jdk.classfile.Classfile.TAG_NAMEANDTYPE;
import static jdk.classfile.Classfile.TAG_PACKAGE;
import static jdk.classfile.Classfile.TAG_STRING;
import static jdk.classfile.Classfile.TAG_UTF8;
import static jdk.classfile.impl.ClassPrinterImpl.Style.*;

/**
 * ClassPrinterImpl
 */
public final class ClassPrinterImpl {

//    public record Format(char quotes, boolean quoteFlagsAndAttrs, boolean quoteTypes, String mandatoryDelimiter, String inlineDelimiter,
//        Block classForm, Block constantPool, String valueEntry, String stringEntry, String namedEntry, String memberEntry,
//        String nameAndTypeEntry, String methodHandleEntry, String methodTypeEntry, String dynamicEntry,
//        String fieldsHeader, Block field, String methodsHeader, Block method, String simpleAttr, String simpleQuotedAttr,
//        String annotationDefault, Table annotations, Table typeAnnotations, String typeAnnotationInline, Table parameterAnnotations,
//        Table annotationValuePair, Table bootstrapMethods, String enclosingMethod, Table innerClasses, Table methodParameters,
//        Table recordComponents, String recordComponentTail, Block module, Table requires, Table exports, Table opens, Table provides,
//        String modulePackages, String moduleMain, String nestHost, String nestMembers,
//        Block code,
//        Table exceptionHandlers, String tryStartInline, String tryEndInline, String handlerInline, Table localVariableTable, String localVariableInline,
//        String frameInline, Table stackMapTable, Table lineNumberTable, Table characterRangeTable, Table localVariableTypeTable,
//        Function<String, String> escapeFunction) {}


    public enum Style { BLOCK, FLOW }

    public record LeafNodeImpl(ConstantDesc name, ConstantDesc value) implements LeafNode {
    }

    private static Node leaf(ConstantDesc name, ConstantDesc value) {
        return new LeafNodeImpl(name, value);
    }

    private static Node[] leafs(ConstantDesc... namesAndValues) {
        if ((namesAndValues.length & 1) > 0)
            throw new AssertionError("Odd number of arguments: " + Arrays.toString(namesAndValues));
        var nodes = new Node[namesAndValues.length >> 1];
        for (int i = 0, j = 0; i < nodes.length; i ++) {
            nodes[i] = leaf(namesAndValues[j++], namesAndValues[j++]);
        }
        return nodes;
    }

    private static Node leafList(ConstantDesc listName, ConstantDesc itemsName, Stream<ConstantDesc> values) {
        return new ListNodeImpl(FLOW, listName, values.map(v -> leaf(itemsName, v)));
    }

    private static Node leafMap(ConstantDesc id, ConstantDesc... keysAndValues) {
        return new MapNodeImpl(FLOW, id).with(leafs(keysAndValues));
    }

    public static final class ListNodeImpl extends AbstractList<Node> implements ListNode {

        private final Style style;
        private final ConstantDesc name;
        private final Node[] nodes;

        public ListNodeImpl(Style style, ConstantDesc name, Stream<Node> nodes) {
            this.style = style;
            this.name = name;
            this.nodes = nodes.toArray(Node[]::new);
        }

        @Override
        public ConstantDesc name() {
            return name;
        }

        public Style style() {
            return style;
        }

        @Override
        public Node get(int index) {
            Objects.checkIndex(index, nodes.length);
            return nodes[index];
        }

        @Override
        public int size() {
            return nodes.length;
        }
    }

    public static final class MapNodeImpl implements MapNode {

        private final Style style;
        private final ConstantDesc name;
        private final Map<ConstantDesc, Node> map;

        public MapNodeImpl(Style style, ConstantDesc name) {
            this.style = style;
            this.name = name;
            this.map = new LinkedHashMap<>();
        }

        @Override
        public ConstantDesc name() {
            return name;
        }

        public Style style() {
            return style;
        }

        @Override
        public int size() {
            return map.size();
        }
        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }
        @Override
        public boolean containsKey(Object key) {
            return map.containsKey(key);
        }
        @Override
        public boolean containsValue(Object value) {
            return map.containsValue(value);
        }

        @Override
        public Node get(Object key) {
            return map.get(key);
        }

        @Override
        public Node put(ConstantDesc key, Node value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Node remove(Object key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void putAll(Map<? extends ConstantDesc, ? extends Node> m) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<ConstantDesc> keySet() {
            return Collections.unmodifiableSet(map.keySet());
        }

        @Override
        public Collection<Node> values() {
            return Collections.unmodifiableCollection(map.values());
        }

        @Override
        public Set<Entry<ConstantDesc, Node>> entrySet() {
            return Collections.unmodifiableSet(map.entrySet());
        }


        MapNodeImpl with(Node... nodes) {
            for (var n : nodes)
                if (map.put(n.name(), n) != null)
                    throw new AssertionError("Double entry of " + n.name() + " into " + name);
            return this;
        }
    }

    private static final String NL = System.lineSeparator();

    private static final char[] DIGITS = "0123456789ABCDEF".toCharArray();

    private static void escape(int c, StringBuilder sb) {
        switch (c) {
            case '\\'  -> sb.append('\\').append('\\');
            case '"' -> sb.append('\\').append('"');
            case '\b' -> sb.append('\\').append('b');
            case '\n' -> sb.append('\\').append('n');
            case '\t' -> sb.append('\\').append('t');
            case '\f' -> sb.append('\\').append('f');
            case '\r' -> sb.append('\\').append('r');
            default -> {
                if (c >= 0x20 && c < 0x7f) {
                    sb.append((char)c);
                } else {
                    sb.append('\\').append('u').append(DIGITS[(c >> 12) & 0xf]).append(DIGITS[(c >> 8) & 0xf]).append(DIGITS[(c >> 4) & 0xf]).append(DIGITS[(c) & 0xf]);
                }
            }
        }
    }

//    public static final Format YAML = new Format('\'', false, true, "", ", ",
//            new Block("  - class name: '%s'%n    version: '%d.%d'%n    flags: %s%n    superclass: '%s'%n    interfaces: %s%n    attributes: %s", "%n"),
//            new Block("%n    constant pool:", ""),
//            "%n        %d: [%s, '%s']",
//            "%n        %d: [%s, {value index: %d, value: '%s'}]",
//            "%n        %d: [%s, {name index: %d, name: '%s'}]",
//            "%n        %d: [%s, {owner index: %d, name and type index: %d, owner: '%s', name: '%s', type: '%s'}]",
//            "%n        %d: [%s, {name index: %d, type index: %d, name: '%s', type: '%s'}]",
//            "%n        %d: [%s, {reference kind: '%s', reference index: %d, owner: '%s', name: '%s', type: '%s'}]",
//            "%n        %d: [%s, {descriptor index: %d, descriptor: '%s'}]",
//            "%n        %d: [%s, {bootstrap method handle index: %d, bootstrap method arguments indexes: %s, name and type index: '%d', name: '%s', type: '%s'}]",
//            "%n    fields:",
//            new Block("%n      - field name: '%s'%n        flags: %s%n        descriptor: '%s'%n        attributes: %s", ""),
//            "%n    methods:",
//            new Block("%n      - method name: '%s'%n        flags: %s%n        descriptor: '%s'%n        attributes: %s", ""),
//            "%n%s%s: %s",
//            "%n%s%s: '%s'",
//            "%n%sannotation default: '%s'",
//            new Table("%n%s%s annotations:", "", "%n%s  - {class: '%s'%s}"),
//            new Table("%n%s%s type annotations:", "", "%n%s  -  {class: '%s', target type: '%s'%s}"),
//            "%n            #%s type annotation: {class: '%s', target type: '%s'%s}",
//            new Table("%n%s%s parameter %d annotations:", "", "%n%s  -  {class: '%s'%s}"),
//            new Table(", values: {", "}", "'%s': '%s'"),
//            new Table("%n    bootstrap methods: #[<method kind>, <owner>, <method name>, <invocation type>, <is interface>, <is methods>]", "", "%n      - ['%s', '%s', '%s', '%s', %b, %b]"),
//            "%n%senclosing method: {class: '%s', method name: '%s', type: 's'}",
//            new Table("%n    inner classes: #[<inner class>, <outer class>, <inner class entry>, <flags>]", "", "%n      - ['%s', '%s', '%s', %s]"),
//            new Table("%n        method parameters: #[<parameter name>, <flags>]", "", "%n          - ['%s', %s]"),
//            new Table("%n    record components:", "", "%n      - name: '%s'%n        type: '%s'%n        attributes: %s"),
//            "",
//            new Block("%n    module:%n        name: '%s'%n        flags: %s%n        version: '%s'%n        uses: %s", ""),
//            new Table("%n        requires:", "", "%n          - { name: '%s', flags: %s, version: '%s' }"),
//            new Table("%n        exports:", "", "%n          - { package: '%s', flags: %s, to: %s }"),
//            new Table("%n        opens:", "", "%n          - { package: '%s', flags: %s, to: %s }"),
//            new Table("%n        provides:", "", "%n          - { class: '%s', with: %s }"),
//            "%n    module packages: %s",
//            "%n    module main class: '%s'",
//            "%n    nest host: '%s'",
//            "%n    nest members: %s",
//            new Block("%n        code:%n            max stack: %d%n            max locals: %d%n            attributes: %s", ""),
//            new Table("%n            exception handlers: #[<try start pc>, <try end pc>, <handler pc>, <catch type>]", "", "%n                - [%d, %d, %d, %s]"),
//            "%n            #try block start: {start: %d, end: %d, handler: %d, catch type: %s}",
//            "%n            #try block end: {start: %d, end: %d, handler: %d, catch type: %s}",
//            "%n            #exception handler start: {start: %d, end: %d, handler: %d, catch type: %s}",
//            new Table("%n            local variables: #[<start pc>, <end pc>, '<name>', <type>]", "", "%n                - [%d, %d, %d, '%s', '%s']"),
//            ", type: '%s', variable name: '%s'",
//            "%n            #stack map frame locals: %s, stack: %s",
//            new Table("%n            stack map frames:", "", "%n                %d: {locals: %s, stack: %s}"),
//            ClassPrinterImpl::escapeYaml);

    public static void toYaml(Node node, Consumer<String> out) {
        toYaml(0, false, new ListNodeImpl(BLOCK, null, Stream.of(node)), out);
        out.accept(NL);
    }

    private static void toYaml(int indent, boolean skipFirstIndent, Node node, Consumer<String> out) {
        switch (node) {
            case LeafNode leaf -> {
                out.accept(quoteAndEscapeYaml(leaf.value()));
            }
            case ListNodeImpl list -> {
                switch (list.style()) {
                    case FLOW -> {
                        out.accept("[");
                        boolean first = true;
                        for (var n : list) {
                            if (first) first = false;
                            else out.accept(", ");
                            toYaml(0, false, n, out);
                        }
                        out.accept("]");
                    }
                    case BLOCK -> {
                        for (var n : list) {
                            out.accept(NL + "    ".repeat(indent) + "  - ");
                            toYaml(indent + 1, true, n, out);
                        }
                    }
                }
            }
            case MapNodeImpl map -> {
                switch (map.style()) {
                    case FLOW -> {
                        out.accept("{");
                        boolean first = true;
                        for (var n : map.values()) {
                            if (first) first = false;
                            else out.accept(", ");
                            out.accept(quoteAndEscapeYaml(n.name()) + ": ");
                            toYaml(0, false, n, out);
                        }
                        out.accept("}");
                    }
                    case BLOCK -> {
                        for (var n : map.values()) {
                            if (skipFirstIndent) {
                                skipFirstIndent = false;
                            } else {
                                out.accept(NL + "    ".repeat(indent));
                            }
                            out.accept(quoteAndEscapeYaml(n.name()) + ": ");
                            toYaml(n instanceof ListNodeImpl pl && pl.style() == BLOCK ? indent : indent + 1, false, n, out);
                        }
                    }
                }
            }
        }
    }

    private static String quoteAndEscapeYaml(ConstantDesc value) {
        String s = String.valueOf(value);
        if (value instanceof Number) return s;
        if (s.length() == 0) return "''";
        var sb = new StringBuilder(s.length() << 1);
        s.chars().forEach(c -> {
            switch (c) {
                case '\''  -> sb.append("''");
                default -> escape(c, sb);
            }});
        String esc = sb.toString();
        if (esc.length() != s.length()) return "'" + esc + "'";
        switch (esc.charAt(0)) {
            case '-', '?', ':', ',', '[', ']', '{', '}', '#', '&', '*', '!', '|', '>', '\'', '"', '%', '@', '`':
                return "'" + esc + "'";
        }
        for (int i = 1; i < esc.length(); i++) {
            switch (esc.charAt(i)) {
                case ',', '[', ']', '{', '}':
                    return "'" + esc + "'";
            }
        }
        return esc;
    }

    public static void toJson(Node node, Consumer<String> out) {
        toJson(1, true, node, out);
        out.accept(NL);
    }

    private static void toJson(int indent, boolean skipFirstIndent, Node node, Consumer<String> out) {
        switch (node) {
            case LeafNode leaf -> {
                out.accept(quoteAndEscapeJson(leaf.value()));
            }
            case ListNodeImpl list -> {
                out.accept("[");
                boolean first = true;
                switch (list.style()) {
                    case FLOW -> {
                        for (var n : list) {
                            if (first) first = false;
                            else out.accept(", ");
                            toJson(0, false, n, out);
                        }
                    }
                    case BLOCK -> {
                        for (var n : list) {
                            if (first) first = false;
                            else out.accept(",");
                            out.accept(NL + "    ".repeat(indent));
                            toJson(indent + 1, true, n, out);
                        }
                    }
                }
                out.accept("]");
            }
            case MapNodeImpl map -> {
                switch (map.style()) {
                    case FLOW -> {
                        out.accept("{");
                        boolean first = true;
                        for (var n : map.values()) {
                            if (first) first = false;
                            else out.accept(", ");
                            out.accept(quoteAndEscapeJson(n.name().toString()) + ": ");
                            toJson(0, false, n, out);
                        }
                    }
                    case BLOCK -> {
                        if (skipFirstIndent) out.accept("  { ");
                        else out.accept("{");
                        boolean first = true;
                        for (var n : map.values()) {
                            if (first) first = false;
                            else out.accept(",");
                            if (skipFirstIndent) skipFirstIndent = false;
                            else out.accept(NL + "    ".repeat(indent));
                            out.accept(quoteAndEscapeJson(n.name().toString()) + ": ");
                            toJson(indent + 1, false, n, out);
                        }
                    }
                }
                out.accept("}");
            }
        }
    }

    private static String quoteAndEscapeJson(ConstantDesc value) {
        String s = String.valueOf(value);
        if (value instanceof Number) return s;
        var sb = new StringBuilder(s.length() << 1);
        sb.append('"');
        s.chars().forEach(c -> escape(c, sb));
        sb.append('"');
        return sb.toString();
    }

    public static void toXml(Node node, Consumer<String> out) {
        out.accept("<?xml version = '1.0'?>");
        toXml(0, false, node, out);
        out.accept(NL);
    }

    private static void toXml(int indent, boolean skipFirstIndent, Node node, Consumer<String> out) {
        var name = toXmlName(node.name().toString());
        switch (node) {
            case LeafNode leaf -> {
                out.accept("<" + name + ">");
                out.accept(xmlEscape(leaf.value()));
            }
            case ListNodeImpl list -> {
                switch (list.style()) {
                    case FLOW -> {
                        out.accept("<" + name + ">");
                        for (var n : list) {
                            toXml(0, false, n, out);
                        }
                    }
                    case BLOCK -> {
                        if (!skipFirstIndent)
                            out.accept(NL + "    ".repeat(indent));
                        out.accept("<" + name + ">");
                        for (var n : list) {
                            out.accept(NL + "    ".repeat(indent + 1));
                            toXml(indent + 1, true, n, out);
                        }
                    }
                }
            }
            case MapNodeImpl map -> {
                switch (map.style()) {
                    case FLOW -> {
                        out.accept("<" + name + ">");
                        for (var n : map.values()) {
                            toXml(0, false, n, out);
                        }
                    }
                    case BLOCK -> {
                        if (!skipFirstIndent)
                            out.accept(NL + "    ".repeat(indent));
                        out.accept("<" + name + ">");
                        for (var n : map.values()) {
                            out.accept(NL + "    ".repeat(indent + 1));
                            toXml(indent + 1, true, n, out);
                        }
                    }
                }
            }
        }
        out.accept("</" + name + ">");
    }

    private static String xmlEscape(ConstantDesc value) {
        var s = String.valueOf(value);
        var sb = new StringBuilder(s.length() << 1);
        s.chars().forEach(c -> {
        switch (c) {
            case '<'  -> sb.append("&lt;");
            case '>'  -> sb.append("&gt;");
            case '"'  -> sb.append("&quot;");
            case '&'  -> sb.append("&amp;");
            case '\''  -> sb.append("&apos;");
            default -> escape(c, sb);
        }});
        return sb.toString();
    }

    private static String toXmlName(String name) {
        if (Character.isDigit(name.charAt(0)))
            name = "_" + name;
        return name.replaceAll("[^A-Za-z_0-9]", "_");
    }

    private static String elementValueToString(AnnotationValue v) {
        return switch (v) {
            case OfConstant cv -> v.tag() == 'Z' ? String.valueOf((int)cv.constantValue() != 0) : String.valueOf(cv.constantValue());
            case OfClass clv -> clv.className().stringValue();
            case OfEnum ev -> ev.className().stringValue() + "." + ev.constantName().stringValue();
            case OfAnnotation av -> v.tag() + av.annotation().className().stringValue();
            case OfArray av -> av.values().stream().map(ev -> elementValueToString(ev)).collect(Collectors.joining(", ", "[", "]"));
        };
    }

    private static Node elementValuePairsToTree(List<AnnotationElement> evps) {
        return new ListNodeImpl(FLOW, "values", evps.stream().map(evp -> leafMap("pair", "name", evp.name().stringValue(), "value", elementValueToString(evp.value()))));
    }

    private static Stream<ConstantDesc> convertVTIs(int bci, List<StackMapTableAttribute.VerificationTypeInfo> vtis) {
        return vtis.stream().mapMulti((vti, ret) -> {
            switch (vti) {
                case SimpleVerificationTypeInfo s -> {
                    switch (s.type()) {
                        case ITEM_DOUBLE -> {
                            ret.accept("double");
                            ret.accept("double2");
                        }
                        case ITEM_FLOAT ->
                            ret.accept("float");
                        case ITEM_INTEGER ->
                            ret.accept("float");
                        case ITEM_LONG ->  {
                            ret.accept("long");
                            ret.accept("long2");
                        }
                        case ITEM_NULL -> ret.accept("null");
                        case ITEM_TOP -> ret.accept("?");
                        case ITEM_UNINITIALIZED_THIS -> ret.accept("THIS");
                    }
                }
                case ObjectVerificationTypeInfo o ->
                    ret.accept(o.className().asSymbol().displayName());
                case UninitializedVerificationTypeInfo u ->
                    ret.accept("UNITIALIZED @" + (bci + u.offset()));
            }
        });
    }

    private static String tagName(byte tag) {
        return switch (tag) {
            case TAG_UTF8 -> "Utf8";
            case TAG_INTEGER -> "Integer";
            case TAG_FLOAT -> "Float";
            case TAG_LONG -> "Long";
            case TAG_DOUBLE -> "Double";
            case TAG_CLASS -> "Class";
            case TAG_STRING -> "String";
            case TAG_FIELDREF -> "Fieldref";
            case TAG_METHODREF -> "Methodref";
            case TAG_INTERFACEMETHODREF -> "InterfaceMethodref";
            case TAG_NAMEANDTYPE -> "NameAndType";
            case TAG_METHODHANDLE -> "MethodHandle";
            case TAG_METHODTYPE -> "MethodType";
            case TAG_CONSTANTDYNAMIC -> "Dynamic";
            case TAG_INVOKEDYNAMIC -> "InvokeDynamic";
            case TAG_MODULE -> "Module";
            case TAG_PACKAGE -> "Package";
            default -> null;
        };
    }

    private record ExceptionHandler(int start, int end, int handler, String catchType) {}

    public static MapNode classToTree(ClassModel clm, Verbosity verbosity) {
        return new MapNodeImpl(BLOCK, "class")
                .with(leaf("class name", clm.thisClass().asInternalName()),
                      leaf("version", clm.majorVersion() + "." + clm.minorVersion()),
                      leafList("flags", "flag", clm.flags().flags().stream().map(AccessFlag::name)),
                      leaf("superclass", clm.superclass().map(ClassEntry::asInternalName).orElse("")),
                      leafList("interfaces", "interface", clm.interfaces().stream().map(ClassEntry::asInternalName)),
                      leafList("attributes", "attribute", clm.attributes().stream().map(Attribute::attributeName)))
                .with(constantPoolToTree(clm.constantPool(), verbosity))
                .with(attributesToTree(clm.attributes(), verbosity))
                .with(new ListNodeImpl(BLOCK, "fields", clm.fields().stream().map(f ->
                    new MapNodeImpl(BLOCK, "field")
                            .with(leaf("field name", f.fieldName().stringValue()),
                                  leafList("flags", "flag", f.flags().flags().stream().map(AccessFlag::name)),
                                  leaf("field type", f.fieldType().stringValue()),
                                  leafList("attributes", "attribute", f.attributes().stream().map(Attribute::attributeName)))
                            .with(attributesToTree(f.attributes(), verbosity)))))
                .with(new ListNodeImpl(BLOCK, "methods", clm.methods().stream().map(mm ->
                    (Node)methodToTree(mm, verbosity))));
    }

    private static Node[] constantPoolToTree(ConstantPool cp, Verbosity verbosity) {
        if (verbosity == Verbosity.TRACE_ALL) {
            var cpNode = new MapNodeImpl(BLOCK, "constant pool");
            for (int i = 1; i < cp.entryCount();) {
                var e = cp.entryByIndex(i);
                cpNode.with(poolEntryToTree(i, e));
                i += e.poolEntries();
            }
            return new Node[]{cpNode};
        } else {
            return new Node[0];
        }
    }

    private static Node poolEntryToTree(int i, PoolEntry e) {
        return switch (e) {
            case Utf8Entry ve -> cpValeEntryToTree(i, ve);
            case IntegerEntry ve -> cpValeEntryToTree(i, ve);
            case FloatEntry ve -> cpValeEntryToTree(i, ve);
            case LongEntry ve -> cpValeEntryToTree(i, ve);
            case DoubleEntry ve -> cpValeEntryToTree(i, ve);
            case ClassEntry ce -> cpNamedEntryToTree(i, e, ce.name());
            case StringEntry se -> leafMap(i,
                    "tag", tagName(e.tag()),
                    "value index", se.utf8().index(),
                    "value", se.stringValue());
            case MemberRefEntry mre -> leafMap(i,
                    "tag", tagName(e.tag()),
                    "owner index", mre.owner().index(),
                    "name and type index", mre.nameAndType().index(),
                    "owner", mre.owner().asSymbol().displayName(),
                    "name", mre.name().stringValue(),
                    "type", mre.type().stringValue());
            case NameAndTypeEntry nte -> leafMap(i,
                    "tag", tagName(e.tag()),
                    "name index", nte.name().index(),
                    "type index", nte.type().index(),
                    "name", nte.name().stringValue(),
                    "type", nte.type().stringValue());
            case MethodHandleEntry mhe -> leafMap(i,
                    "tag", tagName(e.tag()),
                    "reference kind", DirectMethodHandleDesc.Kind.valueOf(mhe.kind()).name(),
                    "reference index", mhe.reference().index(),
                    "owner", mhe.reference().owner().asInternalName(),
                    "name", mhe.reference().name().stringValue(),
                    "type", mhe.reference().type().stringValue());
            case MethodTypeEntry mte -> leafMap(i,
                    "tag", tagName(e.tag()),
                    "descriptor index", mte.descriptor().index(),
                    "descriptor", mte.descriptor().stringValue());
            case ConstantDynamicEntry cde -> cpEntryToTree(i, cde);
            case InvokeDynamicEntry ide -> cpEntryToTree(i, ide);
            case ModuleEntry me -> cpNamedEntryToTree(i, e, me.name());
            case PackageEntry pe -> cpNamedEntryToTree(i, e, pe.name());
        };
    }

    private static Node frameToTree(ConstantDesc name, StackMapFrame f) {
        return new MapNodeImpl(FLOW, name)
                .with(leafList("locals", "item", convertVTIs(f.absoluteOffset(), f.effectiveLocals())))
                .with(leafList("stack", "item", convertVTIs(f.absoluteOffset(), f.effectiveStack())));
    }

    public static MapNode methodToTree(MethodModel m, Verbosity verbosity) {
        var methodNode = new MapNodeImpl(BLOCK, "method");
        methodNode.with(leaf("method name", m.methodName().stringValue()));
        methodNode.with(leafList("flags", "flag", m.flags().flags().stream().map(AccessFlag::name)));
        methodNode.with(leaf("method type", m.methodType().stringValue()));
        methodNode.with(leafList("attributes", "attribute", m.attributes().stream().map(Attribute::attributeName)));
        methodNode.with(attributesToTree(m.attributes(), verbosity));
        if (verbosity != Verbosity.MEMBERS_ONLY) {
            m.code().ifPresent(com -> {
                var codeNode = new MapNodeImpl(BLOCK, "code");
                methodNode.with(codeNode);
                codeNode.with(leaf("max stack", ((CodeAttribute)com).maxStack()));
                codeNode.with(leaf("max locals", ((CodeAttribute)com).maxLocals()));
                codeNode.with(leafList("attributes", "attribute", com.attributes().stream().map(Attribute::attributeName)));
                var stackMap = new MapNodeImpl(BLOCK, "stack map frames");
                var visibleTypeAnnos = new LinkedHashMap<Integer, List<TypeAnnotation>>();
                var invisibleTypeAnnos = new LinkedHashMap<Integer, List<TypeAnnotation>>();
                List<LocalVariableInfo> locals = List.of();
                for (var attr : com.attributes()) {
                    if (attr instanceof StackMapTableAttribute smta) {
                        codeNode.with(stackMap);
                        for (var smf : smta.entries()) {
                            stackMap.with(frameToTree(smf.absoluteOffset(), smf));
                        }
                    } else if (verbosity == Verbosity.TRACE_ALL) switch (attr) {
                        case LocalVariableTableAttribute lvta -> {
                            locals = lvta.localVariables();
                            codeNode.with(new ListNodeImpl(BLOCK, "local variables", IntStream.range(0, locals.size()).mapToObj(i -> {
                                var lv = lvta.localVariables().get(i);
                                return leafMap(i + 1,
                                    "start", lv.startPc(),
                                    "end", lv.startPc() + lv.length(),
                                    "slot", lv.slot(),
                                    "name", lv.name().stringValue(),
                                    "type", lv.typeSymbol().displayName());
                            })));
                        }
                        case LocalVariableTypeTableAttribute lvtta -> {
                            codeNode.with(new ListNodeImpl(BLOCK, "local variable types", IntStream.range(0, lvtta.localVariableTypes().size()).mapToObj(i -> {
                                var lvt = lvtta.localVariableTypes().get(i);
                                return leafMap(i + 1,
                                    "start", lvt.startPc(),
                                    "end", lvt.startPc() + lvt.length(),
                                    "slot", lvt.slot(),
                                    "name", lvt.name().stringValue(),
                                    "signature", lvt.signature().stringValue());
                            })));
                        }
                        case LineNumberTableAttribute lnta -> {
                            codeNode.with(new ListNodeImpl(BLOCK, "line numbers", IntStream.range(0, lnta.lineNumbers().size()).mapToObj(i -> {
                                var ln = lnta.lineNumbers().get(i);
                                return leafMap(i + 1,
                                    "start", ln.startPc(),
                                    "line number", ln.lineNumber());
                            })));
                        }
                        case CharacterRangeTableAttribute crta -> {
                            codeNode.with(new ListNodeImpl(BLOCK, "character ranges", IntStream.range(0, crta.characterRangeTable().size()).mapToObj(i -> {
                                var cr = crta.characterRangeTable().get(i);
                                return leafMap(i + 1,
                                    "start", cr.startPc(),
                                    "end", cr.endPc(),
                                    "range start", cr.characterRangeStart(),
                                    "range end", cr.characterRangeEnd(),
                                    "flags", cr.flags());
                            })));
                        }
                        case RuntimeVisibleTypeAnnotationsAttribute rvtaa ->
                            rvtaa.annotations().forEach(a -> forEachOffset(a, com, (off, an) -> visibleTypeAnnos.computeIfAbsent(off, o -> new LinkedList<>()).add(an)));
                        case RuntimeInvisibleTypeAnnotationsAttribute ritaa ->
                            ritaa.annotations().forEach(a -> forEachOffset(a, com, (off, an) -> invisibleTypeAnnos.computeIfAbsent(off, o -> new LinkedList<>()).add(an)));
                        case Object o -> {}
                    }
                }
                codeNode.with(attributesToTree(com.attributes(), verbosity));
                if (!stackMap.containsKey(0)) {
                    codeNode.with(frameToTree("//stack map frame @0", StackMapDecoder.initFrame(m)));
                }
                var excHandlers = com.exceptionHandlers().stream().map(exc -> new ExceptionHandler(com.labelToBci(exc.tryStart()), com.labelToBci(exc.tryEnd()), com.labelToBci(exc.handler()), exc.catchType().map(ct -> ct.asSymbol().displayName()).orElse(null))).toList();
                int bci = 0;
                for (var coe : com) {
                    if (coe instanceof Instruction ins) {
                        var frame = stackMap.get(bci);
                        if (frame != null) {
                            codeNode.with(new MapNodeImpl(FLOW, "//stack map frame @" + bci).with(frame));
                        }
                        var annos = invisibleTypeAnnos.get(bci);
                        if (annos != null) {
                            codeNode.with(typeAnnotationsToTree(FLOW, "//invisible type annotations @" + bci, annos));
                        }
                        annos = visibleTypeAnnos.get(bci);
                        if (annos != null) {
                            codeNode.with(typeAnnotationsToTree(FLOW, "//visible type annotations @" + bci, annos));
                        }
                        for (int i = 0; i < excHandlers.size(); i++) {
                            var exc = excHandlers.get(i);
                            if (exc.start() == bci) {
                                codeNode.with(leafMap("//try block #" + (i + 1) + " start", "start", exc.start(), "end", exc.end(), "handler", exc.handler(), "catch type", exc.catchType()));
                            }
                            if (exc.end() == bci) {
                                codeNode.with(leafMap("//try block #" + (i + 1) + " end", "start", exc.start(), "end", exc.end(), "handler", exc.handler(), "catch type", exc.catchType()));
                            }
                            if (exc.handler() == bci) {
                                codeNode.with(leafMap("//exception handler #" + (i + 1) + " start", "start", exc.start(), "end", exc.end(), "handler", exc.handler(), "catch type", exc.catchType()));
                            }
                        }
                        codeNode.with(switch (coe) {
                            case IncrementInstruction inc -> leafMap(bci, appendLocalInfo(locals, inc.slot(), bci,
                                    "opcode", ins.opcode().name(),
                                    "slot", inc.slot(),
                                    "const", inc.constant()));
                            case LoadInstruction lv -> leafMap(bci, appendLocalInfo(locals, lv.slot(), bci,
                                    "opcode", ins.opcode().name(),
                                    "slot", lv.slot()));
                            case StoreInstruction lv -> leafMap(bci, appendLocalInfo(locals, lv.slot(), bci,
                                    "opcode", ins.opcode().name(),
                                    "slot", lv.slot()));
                            case FieldInstruction fa -> leafMap(bci,
                                    "opcode", ins.opcode().name(),
                                    "owner", fa.owner().asSymbol().displayName(),
                                    "field name", fa.name().stringValue(),
                                    "field type", fa.typeSymbol().displayName());
                            case InvokeInstruction inv -> leafMap(bci,
                                    "opcode", ins.opcode().name(),
                                    "owner", inv.owner().asSymbol().displayName(),
                                    "method name", inv.name().stringValue(),
                                    "method type", inv.typeSymbol().displayDescriptor());
                            case InvokeDynamicInstruction invd -> leafMap(bci,
                                    "opcode", ins.opcode().name(),
                                    "name", invd.name().stringValue(),
                                    "descriptor", invd.type().stringValue(),
                                    "kind", invd.bootstrapMethod().kind().name(),
                                    "owner", invd.bootstrapMethod().owner().descriptorString(),
                                    "method name", invd.bootstrapMethod().methodName(),
                                    "invocation type", invd.bootstrapMethod().invocationType().displayDescriptor());
                            case NewObjectInstruction newo -> leafMap(bci,
                                    "opcode", ins.opcode().name(),
                                    "type", newo.className().asSymbol().displayName());
                            case NewPrimitiveArrayInstruction newa -> leafMap(bci,
                                    "opcode", ins.opcode().name(),
                                    "dimensions", 1,
                                    "descriptor", newa.typeKind().typeName());
                            case NewReferenceArrayInstruction newa -> leafMap(bci,
                                    "opcode", ins.opcode().name(),
                                    "dimensions", 1,
                                    "descriptor", newa.componentType().asSymbol().displayName());
                            case NewMultiArrayInstruction newa -> leafMap(bci,
                                    "opcode", ins.opcode().name(),
                                    "dimensions", newa.dimensions(),
                                    "descriptor", newa.arrayType().asSymbol().displayName());
                            case TypeCheckInstruction tch -> leafMap(bci,
                                    "opcode", ins.opcode().name(),
                                    "type", tch.type().asSymbol().displayName());
                            case ConstantInstruction cons -> leafMap(bci,
                                    "opcode", ins.opcode().name(),
                                    "constant value", cons.constantValue());
                            case BranchInstruction br -> leafMap(bci,
                                    "opcode", ins.opcode().name(),
                                    "target", com.labelToBci(br.target()));
                            case LookupSwitchInstruction ls -> new MapNodeImpl(FLOW, bci)
                                    .with(leaf("opcode", ins.opcode().name()))
                                    .with(leafList("targets", "target", Stream.concat(Stream.of(ls.defaultTarget()).map(com::labelToBci), ls.cases().stream().map(c -> com.labelToBci(c.target())))));
                            case TableSwitchInstruction ts ->  new MapNodeImpl(FLOW, bci)
                                    .with(leaf("opcode", ins.opcode().name()))
                                    .with(leafList("targets", "target", Stream.concat(Stream.of(ts.defaultTarget()).map(com::labelToBci), ts.cases().stream().map(c -> com.labelToBci(c.target())))));
                            default -> leafMap(bci,
                                    "opcode", ins.opcode().name());
                        });
                        bci += ins.sizeInBytes();
                    }
                }
                if (!excHandlers.isEmpty()) {
                    var handlersNode = new MapNodeImpl(BLOCK, "exception handlers");
                    codeNode.with(handlersNode);
                    for (int i = 0; i < excHandlers.size(); i++) {
                        var exc = excHandlers.get(i);
                        handlersNode.with(leafMap("handler #" + i, "start", exc.start(), "end", exc.end(), "handler", exc.handler(), "type", exc.catchType()));
                    }
                }
            });
        }
        return methodNode;
    }

    private static Node cpValeEntryToTree(int i, AnnotationConstantValueEntry e) {
        return leafMap(i,
                "tag", tagName(e.tag()),
                "value", String.valueOf(e.constantValue()));
    }

    private static Node cpNamedEntryToTree(int i, PoolEntry e, Utf8Entry name) {
        return leafMap(i,
                "tag", tagName(e.tag()),
                "name index", name.index(),
                "value", name.stringValue());
    }

    private static Node cpEntryToTree(int i, DynamicConstantPoolEntry dcpe) {
        return new MapNodeImpl(FLOW, i)
                .with(leaf("tag", tagName(dcpe.tag())))
                .with(leaf("bootstrap method handle index", dcpe.bootstrap().bootstrapMethod().index()))
                .with(leafList("bootstrap method arguments indexes", "index", dcpe.bootstrap().arguments().stream().map(en -> en.index())))
                .with(leaf("name and type index", dcpe.nameAndType().index()))
                .with(leaf("name", dcpe.name().stringValue()))
                .with(leaf("type", dcpe.type().stringValue()));
    }

    private static Node[] attributesToTree(List<Attribute<?>> attributes, Verbosity verbosity) {
        var out = new LinkedList<Node>();
        if (verbosity != Verbosity.MEMBERS_ONLY) for (var attr : attributes) {
//            switch (attr) {
//                case BootstrapMethodsAttribute bma ->
//                    printTable(format.bootstrapMethods, bma.bootstrapMethods(), bm -> {
//                        var mh = bm.bootstrapMethod();
//                        var mref = mh.reference();
//                        return new Object[] {DirectMethodHandleDesc.Kind.valueOf(mh.kind(), mref.isInterface()), mref.owner().asInternalName(), mref.nameAndType().name().stringValue(), mref.nameAndType().type().stringValue(), mref.isInterface(), mref.isMethod()};
//                    });
//                case ConstantValueAttribute cva ->
//                    out.accept(format.simpleQuotedAttr.formatted(indentSpace, "value", escape(cva.constant().constantValue().toString())));
//                case NestHostAttribute nha ->
//                    out.accept(format.nestHost.formatted(nha.nestHost().asInternalName()));
//                case NestMembersAttribute nma ->
//                    out.accept(format.nestMembers.formatted(typesToString(nma.nestMembers().stream().map(mp -> mp.asInternalName()))));
//                case PermittedSubclassesAttribute psa ->
//                    out.accept(format.simpleAttr.formatted(indentSpace, "subclasses", typesToString(psa.permittedSubclasses().stream().map(e -> e.asInternalName()))));
//                default -> {}
//            }
            if (verbosity == Verbosity.TRACE_ALL) switch (attr) {
//                case EnclosingMethodAttribute ema ->
//                    out.accept(format.enclosingMethod.formatted(indentSpace, ema.enclosingClass().asInternalName(), ema.enclosingMethod().map(e -> escape(e.name().stringValue())).orElse(null), ema.enclosingMethod().map(e -> escape(e.type().stringValue())).orElse(null)));
//                case ExceptionsAttribute exa ->
//                    out.accept(format.simpleAttr.formatted(indentSpace, "exceptions", typesToString(exa.exceptions().stream().map(e -> e.asInternalName()))));
//                case InnerClassesAttribute ica ->
//                    printTable(format.innerClasses, ica.classes(), ic -> new Object[] {ic.innerClass().asInternalName(), ic.outerClass().map(e -> escape(e.asInternalName())).orElse(null), ic.innerName().map(e -> escape(e.stringValue())).orElse(null), quoteFlags(ic.flags())});
//                case MethodParametersAttribute mpa ->
//                    printTable(format.methodParameters, mpa.parameters(), mp -> new Object[]{mp.name().map(e -> escape(e.stringValue())).orElse(null), quoteFlags(mp.flags())});
//                case ModuleAttribute ma -> {
//                    out.accept(format.module.header.formatted(ma.moduleName().name().stringValue(), quoteFlags(ma.moduleFlags()), ma.moduleVersion().map(Utf8Entry::stringValue).orElse(""), typesToString(ma.uses().stream().map(ce -> ce.asInternalName()))));
//                    printTable(format.requires, ma.requires(), req -> new Object[] {req.requires().name().stringValue(), quoteFlags(req.requiresFlags()), req.requiresVersion().map(Utf8Entry::stringValue).orElse(null)});
//                    printTable(format.exports, ma.exports(), exp -> new Object[] {exp.exportedPackage().name().stringValue(), quoteFlags(exp.exportsFlags()), typesToString(exp.exportsTo().stream().map(me -> me.name().stringValue()))});
//                    printTable(format.opens, ma.opens(), open -> new Object[] {open.openedPackage().name().stringValue(), quoteFlags(open.opensFlags()), typesToString(open.opensTo().stream().map(me -> me.name().stringValue()))});
//                    printTable(format.provides, ma.provides(), provide -> new Object[] {provide.provides().asInternalName(), typesToString(provide.providesWith().stream().map(me -> me.asInternalName()))});
//                    out.accept(format.module.footer.formatted());
//                }
//                case ModulePackagesAttribute mopa ->
//                    out.accept(format.modulePackages.formatted(typesToString(mopa.packages().stream().map(mp -> mp.name().stringValue()))));
//                case ModuleMainClassAttribute mmca ->
//                    out.accept(format.moduleMain.formatted(mmca.mainClass().asInternalName()));
//                case RecordAttribute ra ->
//                    printTable(format.recordComponents, ra.components(), rc -> new Object[]{rc.name().stringValue(), rc.descriptor().stringValue(), attributeNames(rc.attributes())}, rc -> {
//                        printAttributes("        ", rc.attributes());
//                        out.accept(format.recordComponentTail);
//                    });
//                case AnnotationDefaultAttribute ada ->
//                    out.accept(format.annotationDefault.formatted(indentSpace, elementValueToString(ada.defaultValue())));
                case RuntimeInvisibleAnnotationsAttribute aa ->
                    out.add(annotationsToTree("invisible annotations", aa.annotations()));
                case RuntimeVisibleAnnotationsAttribute aa ->
                    out.add(annotationsToTree("visible annotations", aa.annotations()));
                case RuntimeInvisibleParameterAnnotationsAttribute aa ->
                    out.add(parameterAnnotationsToTree("invisible parameter annotations", aa.parameterAnnotations()));
                case RuntimeVisibleParameterAnnotationsAttribute aa ->
                    out.add(parameterAnnotationsToTree("visible parameter annotations", aa.parameterAnnotations()));
                case RuntimeInvisibleTypeAnnotationsAttribute aa ->
                    out.add(typeAnnotationsToTree(BLOCK, "invisible type annotations", aa.annotations()));
                case RuntimeVisibleTypeAnnotationsAttribute aa ->
                    out.add(typeAnnotationsToTree(BLOCK, "visible type annotations", aa.annotations()));
                case SignatureAttribute sa ->
                    out.add(leaf("signature", sa.signature().stringValue()));
                case SourceFileAttribute sfa ->
                    out.add(leaf("source file", sfa.sourceFile().stringValue()));
                default -> {}
            }
        }
        return out.toArray(Node[]::new);
    }

    private static Node annotationsToTree(String name, List<Annotation> annos) {
        return new ListNodeImpl(BLOCK, name, annos.stream().map(a ->
                new MapNodeImpl(FLOW, "anno")
                        .with(leaf("annotation class", a.classSymbol().displayName()))
                        .with(elementValuePairsToTree(a.elements()))));

    }

    private static Node typeAnnotationsToTree(Style style, String name, List<TypeAnnotation> annos) {
        return new ListNodeImpl(style, name, annos.stream().map(a ->
                new MapNodeImpl(FLOW, "anno")
                        .with(leaf("annotation class", a.classSymbol().displayName()))
                        .with(leaf("target info", a.targetInfo().targetType().name()))
                        .with(elementValuePairsToTree(a.elements()))));

    }

    private static MapNodeImpl parameterAnnotationsToTree(String name, List<List<Annotation>> paramAnnotations) {
        var node = new MapNodeImpl(BLOCK, name);
        for (int i = 0; i < paramAnnotations.size(); i++) {
            var annos = paramAnnotations.get(i);
            if (!annos.isEmpty()) {
                node.with(new ListNodeImpl(FLOW, "parameter " + (i + 1), annos.stream().map(a ->
                                new MapNodeImpl(FLOW, "anno")
                                        .with(leaf("annotation class", a.classSymbol().displayName()))
                                        .with(elementValuePairsToTree(a.elements())))));
            }
        }
        return node;
    }

    private static ConstantDesc[] appendLocalInfo(List<LocalVariableInfo> locals, int slot, int bci, ConstantDesc... info) {
        if (locals != null) {
            for (var l : locals) {
                if (l.slot() == slot && l.startPc() <= bci && l.length() + l.startPc() >= bci) {
                    int il = info.length;
                    info = Arrays.copyOf(info, il + 4);
                    info[il] = "type";
                    info[il + 1] = l.typeSymbol().displayName();
                    info[il + 2] = "variable name";
                    info[il + 3] = l.name().stringValue();
                    return info;
                }
            }
        }
        return info;
    }

    private static void forEachOffset(TypeAnnotation ta, LabelResolver lr, BiConsumer<Integer, TypeAnnotation> consumer) {
        switch (ta.targetInfo()) {
            case TypeAnnotation.OffsetTarget ot -> consumer.accept(lr.labelToBci(ot.target()), ta);
            case TypeAnnotation.TypeArgumentTarget tat -> consumer.accept(lr.labelToBci(tat.target()), ta);
            case TypeAnnotation.LocalVarTarget lvt -> lvt.table().forEach(lvti -> consumer.accept(lr.labelToBci(lvti.startLabel()), ta));
            default -> {}
        }
    }
}
