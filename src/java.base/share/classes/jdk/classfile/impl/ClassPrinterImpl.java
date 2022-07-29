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
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.reflect.AccessFlag;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

    public record SimpleNodeImpl(ConstantDesc name, ConstantDesc value) implements SimpleNode {

    }

    public record ListNodeImpl(Style style, ConstantDesc name, List<Node> list) implements ListNode {
        public ListNodeImpl(Style style, ConstantDesc name, List<Node> list) {
            this.style = style;
            this.name = name;
            this.list = Collections.unmodifiableList(list);
        }
    }

    public record MapNodeImpl(Style style, ConstantDesc name, Map<ConstantDesc, Node> map) implements MapNode {
        public MapNodeImpl(Style style, ConstantDesc name, Map<ConstantDesc, Node> map) {
            this.style = style;
            this.name = name;
            this.map = Collections.unmodifiableMap(map);
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
        toYaml(0, false, blockList(null, Stream.of(node)), out);
        out.accept(NL);
    }

    private static void toYaml(int indent, boolean skipFirstIndent, Node node, Consumer<String> out) {
        switch (node) {
            case SimpleNode v -> {
                out.accept(quoteAndEscapeYaml(v.value()));
            }
            case ListNodeImpl pl -> {
                switch (pl.style()) {
                    case FLOW -> {
                        out.accept("[");
                        boolean first = true;
                        for (var s : pl.list()) {
                            if (first) first = false;
                            else out.accept(", ");
                            toYaml(0, false, s, out);
                        }
                        out.accept("]");
                    }
                    case BLOCK -> {
                        for (var n : pl.list()) {
                            out.accept(NL + "    ".repeat(indent) + "  - ");
                            toYaml(indent + 1, true, n, out);
                        }
                    }
                }
            }
            case MapNodeImpl pm -> {
                switch (pm.style()) {
                    case FLOW -> {
                        out.accept("{");
                        boolean first = true;
                        for (var n : pm.map().values()) {
                            if (first) first = false;
                            else out.accept(", ");
                            out.accept(quoteAndEscapeYaml(n.name()) + ": ");
                            toYaml(0, false, n, out);
                        }
                        out.accept("}");
                    }
                    case BLOCK -> {
                        for (var n : pm.map().values()) {
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
            switch (esc.charAt(0)) {
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
            case SimpleNode v -> {
                out.accept(quoteAndEscapeJson(v.value()));
            }
            case ListNodeImpl pl -> {
                out.accept("[");
                boolean first = true;
                switch (pl.style()) {
                    case FLOW -> {
                        for (var s : pl.list()) {
                            if (first) first = false;
                            else out.accept(", ");
                            toJson(0, false, s, out);
                        }
                    }
                    case BLOCK -> {
                        for (var n : pl.list()) {
                            if (first) first = false;
                            else out.accept(",");
                            out.accept(NL + "    ".repeat(indent));
                            toJson(indent + 1, true, n, out);
                        }
                    }
                }
                out.accept("]");
            }
            case MapNodeImpl pm -> {
                switch (pm.style()) {
                    case FLOW -> {
                        out.accept("{");
                        boolean first = true;
                        for (var n : pm.map().values()) {
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
                        for (var n : pm.map().values()) {
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
            case SimpleNode v -> {
                out.accept("<" + name + ">");
                out.accept(xmlEscape(v.value()));
            }
            case ListNodeImpl pl -> {
                switch (pl.style()) {
                    case FLOW -> {
                        out.accept("<" + name + ">");
                        for (var n : pl.list()) {
                            toXml(0, false, n, out);
                        }
                    }
                    case BLOCK -> {
                        if (!skipFirstIndent)
                            out.accept(NL + "    ".repeat(indent));
                        out.accept("<" + name + ">");
                        for (var n : pl.list()) {
                            out.accept(NL + "    ".repeat(indent + 1));
                            toXml(indent + 1, true, n, out);
                        }
                    }
                }
            }
            case MapNodeImpl pm -> {
                switch (pm.style()) {
                    case FLOW -> {
                        out.accept("<" + name + ">");
                        for (var me : pm.map().entrySet()) {
                            toXml(0, false, me.getValue(), out);
                        }
                    }
                    case BLOCK -> {
                        if (!skipFirstIndent)
                            out.accept(NL + "    ".repeat(indent));
                        out.accept("<" + name + ">");
                        for (var n : pm.map().values()) {
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

//    private List<String> quoteFlags(Set<? extends Enum<?>> flags) {
//        return flags.stream().map(f -> format.quoteFlagsAndAttrs ? format.quotes + f.name() + format.quotes : f.name()).toList();
//    }
//
//    private String escape(String s) {
//        return format.escapeFunction.apply(s);
//    }
//
//    private String typesToString(Stream<String> strings) {
//        return strings.map(s -> format.quoteTypes ? format.quotes + s + format.quotes : s).collect(Collectors.joining(", ", "[", "]"));
//    }
//
//    private String attributeNames(List<Attribute<?>> attributes) {
//        return attributes.stream().map(a -> format.quoteFlagsAndAttrs ? format.quotes + a.attributeName() + format.quotes : a.attributeName()).collect(Collectors.joining(", ", "[", "]"));
//    }

    private static String elementValueToString(AnnotationValue v) {
        return switch (v) {
            case OfConstant cv -> v.tag() == 'Z' ? String.valueOf((int)cv.constantValue() != 0) : String.valueOf(cv.constantValue());
            case OfClass clv -> clv.className().stringValue();
            case OfEnum ev -> ev.className().stringValue() + "." + ev.constantName().stringValue();
            case OfAnnotation av -> v.tag() + av.annotation().className().stringValue();
            case OfArray av -> av.values().stream().map(ev -> elementValueToString(ev)).collect(Collectors.joining(", ", "[", "]"));
        };
    }

    private static Node elementValuePairsToString(List<AnnotationElement> evps) {
        return list("values", evps.stream().map(evp -> map("pair", "name", evp.name().stringValue(), "value", elementValueToString(evp.value()))));
    }

    private static Map<ConstantDesc, Node> newMap() {
        return new LinkedHashMap<>();
    }

    private static List<Node> newList() {
        return new LinkedList<>();
    }

    private static Node value(ConstantDesc name, ConstantDesc value) {
        return new SimpleNodeImpl(name, value);
    }

    private static Node blockList(ConstantDesc listName, Stream<Node> list) {
        return new ListNodeImpl(BLOCK, listName, list.toList());
    }

    private static Node list(ConstantDesc listName, Stream<Node> list) {
        return new ListNodeImpl(FLOW, listName, list.toList());
    }

    private static Node list(ConstantDesc listName, ConstantDesc itemsName, Stream<?> values) {
        return new ListNodeImpl(FLOW, listName, values.map(v -> {
            return switch (v) {
                case Node p -> p;
                case ConstantDesc cd -> value(itemsName, cd);
                default -> throw new AssertionError("should not reach here");
            };
        }).toList());
    }

    private static Node blockMap(ConstantDesc mapName, Stream<Node> nodes) {
        var map = newMap();
        nodes.forEach(n -> map.put(n.name(), n));
        return new MapNodeImpl(BLOCK, mapName, map);
    }

    private static Node blockMap(ConstantDesc mapName, Collection<Node> nodes) {
        return blockMap(mapName, nodes.stream());
    }

    private static Node map(ConstantDesc mapName, Node... nodes) {
        var map = newMap();
        for (var n : nodes) map.put(n.name(), n);
        return new MapNodeImpl(FLOW, mapName, map);
    }

    private static Node map(ConstantDesc id, ConstantDesc... keysAndValues) {
        var map = newMap();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], value(keysAndValues[i], keysAndValues[i + 1]));
//            switch (keysAndValues[i + 1]) {
//                case Node p -> map.put((ConstantDesc)keysAndValues[i], p);
//                case ConstantDesc cd -> map.put((ConstantDesc)keysAndValues[i], value((ConstantDesc)keysAndValues[i], cd));
//                default -> throw new IllegalArgumentException();
//            }
        }
        return new MapNodeImpl(FLOW, id, map);
    }

    private static String formatDescriptor(String desc) {
        int i = desc.lastIndexOf('[');
        if (i >= 0) desc = desc.substring(i + 1);
        desc = switch (desc) {
            case "I", "B", "Z", "F", "S", "C", "J", "D" -> TypeKind.fromDescriptor(desc).typeName();
            default -> Util.descriptorToClass(desc);
        };
        if (i >= 0) {
            var ret = new StringBuilder(desc.length() + 2*i + 2).append(desc);
            while (i-- >= 0) ret.append('[').append(']');
            return ret.toString();
        }
        return desc;
    }

    private static Stream<String> convertVTIs(List<StackMapTableAttribute.VerificationTypeInfo> vtis) {
        return vtis.stream().mapMulti((vti, ret) -> {
            var s = formatDescriptor(vti.toString());
            ret.accept(s);
            if (vti.type() == StackMapTableAttribute.VerificationType.ITEM_DOUBLE || vti.type() == StackMapTableAttribute.VerificationType.ITEM_LONG)
                ret.accept(s + "2");
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

    public static MapNode toTree(ClassModel clm, Verbosity verbosity) {
        var cllist = newList();
        cllist.add(value("class name", clm.thisClass().asInternalName()));
        cllist.add(value("version", clm.majorVersion() + "." + clm.minorVersion()));
        cllist.add(list("flags", "flag", clm.flags().flags().stream().map(AccessFlag::name)));
        cllist.add(value("superclass", clm.superclass().map(ClassEntry::asInternalName).orElse("")));
        cllist.add(list("interfaces", "interface", clm.interfaces().stream().map(ClassEntry::asInternalName)));
        cllist.add(list("attributes", "attribute", clm.attributes().stream().map(Attribute::attributeName)));
        if (verbosity == Verbosity.TRACE_ALL) {
            var cpEntries = newList();
            for (int i = 1; i < clm.constantPool().entryCount();) {
                var e = clm.constantPool().entryByIndex(i);
                cpEntries.add(toTree(i, e));
                i += e.poolEntries();
            }
            cllist.add(blockMap("constant pool", cpEntries));
        }
        if (verbosity != Verbosity.MEMBERS_ONLY) printAttributes(clm.attributes(), cllist, verbosity);
        cllist.add(blockList("fields", clm.fields().stream().map(f -> {
            var fieldElements = newList();
            fieldElements.add(value("field name", f.fieldName().stringValue()));
            fieldElements.add(list("flags", "flag", f.flags().flags().stream().map(AccessFlag::name)));
            fieldElements.add(value("field type", f.fieldType().stringValue()));
            fieldElements.add(list("attributes", "attribute", f.attributes().stream().map(Attribute::attributeName)));
            if (verbosity != Verbosity.MEMBERS_ONLY) printAttributes(f.attributes(), fieldElements, verbosity);
            return blockMap("field", fieldElements);
        })));
        cllist.add(blockList("methods", clm.methods().stream().map(mm -> (Node)toTree(mm, verbosity))));
        return (MapNode)blockMap("class", cllist);
    }

    private static Node toTree(int i, PoolEntry e) {
        return switch (e) {
            case Utf8Entry ve -> printValueEntry(i, ve);
            case IntegerEntry ve -> printValueEntry(i, ve);
            case FloatEntry ve -> printValueEntry(i, ve);
            case LongEntry ve -> printValueEntry(i, ve);
            case DoubleEntry ve -> printValueEntry(i, ve);
            case ClassEntry ce -> printNamedEntry(i, e, ce.name());
            case StringEntry se -> map(i,
                    "tag", tagName(e.tag()),
                    "value index", se.utf8().index(),
                    "value", se.stringValue());
            case MemberRefEntry mre -> map(i,
                    "tag", tagName(e.tag()),
                    "owner index", mre.owner().index(),
                    "name and type index", mre.nameAndType().index(),
                    "owner", mre.owner().asInternalName(),
                    "name", mre.name().stringValue(),
                    "type", mre.type().stringValue());
            case NameAndTypeEntry nte -> map(i,
                    "tag", tagName(e.tag()),
                    "name index", nte.name().index(),
                    "type index", nte.type().index(),
                    "name", nte.name().stringValue(),
                    "type", nte.type().stringValue());
            case MethodHandleEntry mhe -> map(i,
                    "tag", tagName(e.tag()),
                    "reference kind", DirectMethodHandleDesc.Kind.valueOf(mhe.kind()).name(),
                    "reference index", mhe.reference().index(),
                    "owner", mhe.reference().owner().asInternalName(),
                    "name", mhe.reference().name().stringValue(),
                    "type", mhe.reference().type().stringValue());
            case MethodTypeEntry mte -> map(i,
                    "tag", tagName(e.tag()),
                    "descriptor index", mte.descriptor().index(),
                    "descriptor", mte.descriptor().stringValue());
            case ConstantDynamicEntry cde -> printDynamicEntry(i, cde);
            case InvokeDynamicEntry ide -> printDynamicEntry(i, ide);
            case ModuleEntry me -> printNamedEntry(i, e, me.name());
            case PackageEntry pe -> printNamedEntry(i, e, pe.name());
        };
    }

    private static Node toTree(ConstantDesc name, StackMapFrame f) {
        return map(name, list("locals", "item", convertVTIs(f.effectiveLocals())), list("stack", "item", convertVTIs(f.effectiveStack())));
    }

    public static MapNode toTree(MethodModel m, Verbosity verbosity) {
        var mlist = newList();
        mlist.add(value("method name", m.methodName().stringValue()));
        mlist.add(list("flags", "flag", m.flags().flags().stream().map(AccessFlag::name)));
        mlist.add(value("method type", m.methodType().stringValue()));
        mlist.add(list("attributes", "attribute", m.attributes().stream().map(Attribute::attributeName)));
        if (verbosity != Verbosity.MEMBERS_ONLY) {
            printAttributes(m.attributes(), mlist, verbosity);
            m.code().ifPresent(com -> {
                var clist = newList();
                clist.add(value("max stack", ((CodeAttribute)com).maxStack()));
                clist.add(value("max locals", ((CodeAttribute)com).maxLocals()));
                clist.add(list("attributes", "attribute", com.attributes().stream().map(Attribute::attributeName)));
                var stackMap = newMap();
                var visibleTypeAnnos = new LinkedHashMap<Integer, List<TypeAnnotation>>();
                var invisibleTypeAnnos = new LinkedHashMap<Integer, List<TypeAnnotation>>();
                List<LocalVariableInfo> locals = List.of();
                for (var attr : com.attributes()) {
                    if (attr instanceof StackMapTableAttribute smta) {
                        for (var smf : smta.entries()) {
                            stackMap.put(smf.absoluteOffset(), toTree(smf.absoluteOffset(), smf));
                        }
                        clist.add(blockMap("stack map frames", stackMap.values()));
                    } else if (verbosity == Verbosity.TRACE_ALL) switch (attr) {
                        case LocalVariableTableAttribute lvta -> {
                            locals = lvta.localVariables();
                            clist.add(blockList("local variables", IntStream.range(0, locals.size()).mapToObj(i -> {
                                var lv = lvta.localVariables().get(i);
                                return map(i + 1,
                                    "start", lv.startPc(),
                                    "end", lv.startPc() + lv.length(),
                                    "slot", lv.slot(),
                                    "name", lv.name().stringValue(),
                                    "type", formatDescriptor(lv.type().stringValue()));
                            })));
                        }
                        case LocalVariableTypeTableAttribute lvtta -> {
                            clist.add(blockList("local variable types", IntStream.range(0, lvtta.localVariableTypes().size()).mapToObj(i -> {
                                var lvt = lvtta.localVariableTypes().get(i);
                                return map(i + 1,
                                    "start", lvt.startPc(),
                                    "end", lvt.startPc() + lvt.length(),
                                    "slot", lvt.slot(),
                                    "name", lvt.name().stringValue(),
                                    "signature", formatDescriptor(lvt.signature().stringValue()));
                            })));
                        }
                        case LineNumberTableAttribute lnta -> {
                            clist.add(blockList("line numbers", IntStream.range(0, lnta.lineNumbers().size()).mapToObj(i -> {
                                var ln = lnta.lineNumbers().get(i);
                                return map(i + 1,
                                    "start", ln.startPc(),
                                    "line number", ln.lineNumber());
                            })));
                        }
                        case CharacterRangeTableAttribute crta -> {
                            clist.add(blockList("character ranges", IntStream.range(0, crta.characterRangeTable().size()).mapToObj(i -> {
                                var cr = crta.characterRangeTable().get(i);
                                return map(i + 1,
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
                printAttributes(com.attributes(), clist, verbosity);
                if (!stackMap.containsKey(0)) {
                    clist.add(toTree("stack map frame @0", StackMapDecoder.initFrame(m)));
                }
                var excHandlers = com.exceptionHandlers().stream().map(exc -> new ExceptionHandler(com.labelToBci(exc.tryStart()), com.labelToBci(exc.tryEnd()), com.labelToBci(exc.handler()), exc.catchType().map(ct -> ct.asInternalName()).orElse(null))).toList();
                int bci = 0;
                for (var coe : com) {
                    if (coe instanceof Instruction ins) {
                        var frame = stackMap.get(bci);
                        if (frame != null) {
                            clist.add(new MapNodeImpl(FLOW, "stack map frame @" + bci, ((MapNode)frame).map()));
                        }
                        var annos = invisibleTypeAnnos.get(bci);
                        if (annos != null) {
                            clist.add(blockList("invisible type annotation @" + bci,
                                    annos.stream().map(a -> map("anno",
                                            value("annotation class", a.className().stringValue()),
                                            value("target info", a.targetInfo().targetType().name()),
                                            elementValuePairsToString(a.elements())))));
                        }
                        annos = visibleTypeAnnos.get(bci);
                        if (annos != null) {
                            clist.add(blockList("visible type annotation @" + bci,
                                    annos.stream().map(a -> map("anno",
                                            value("annotation class", a.className().stringValue()),
                                            value("target info", a.targetInfo().targetType().name()),
                                            elementValuePairsToString(a.elements())))));
                        }
//                        for (var exc : excHandlers) {
//                            if (exc.start() == bci) {
//                                out.accept(format.tryStartInline.formatted(exc.start(), exc.end(), exc.handler(), exc.catchType()));
//                            }
//                            if (exc.end() == bci) {
//                                out.accept(format.tryEndInline.formatted(exc.start(), exc.end(), exc.handler(), exc.catchType()));
//                            }
//                            if (exc.handler() == bci) {
//                                out.accept(format.handlerInline.formatted(exc.start(), exc.end(), exc.handler(), exc.catchType()));
//                            }
//                        }
                        switch (coe) {
                            case IncrementInstruction inc -> clist.add(map(bci, appendLocalInfo(locals, inc.slot(), bci,
                                    "opcode", ins.opcode().name(),
                                    "slot", inc.slot(),
                                    "const", inc.constant())));
                            case LoadInstruction lv -> clist.add(map(bci, appendLocalInfo(locals, lv.slot(), bci,
                                    "opcode", ins.opcode().name(),
                                    "slot", lv.slot())));
                            case StoreInstruction lv -> clist.add(map(bci, appendLocalInfo(locals, lv.slot(), bci,
                                    "opcode", ins.opcode().name(),
                                    "slot", lv.slot())));
                            case FieldInstruction fa -> clist.add(map(bci,
                                    "opcode", ins.opcode().name(),
                                    "owner", fa.owner().asInternalName(),
                                    "field name", fa.name().stringValue(),
                                    "field type", fa.type().stringValue()));
                            case InvokeInstruction inv -> clist.add(map(bci,
                                    "opcode", ins.opcode().name(),
                                    "owner", inv.owner().asInternalName(),
                                    "method name", inv.name().stringValue(),
                                    "method type", inv.type().stringValue()));
                            case InvokeDynamicInstruction invd -> {
                                var bm = invd.bootstrapMethod();
                                clist.add(map(bci,
                                    "opcode", ins.opcode().name(),
                                    "name", invd.name().stringValue(),
                                    "descriptor", invd.type().stringValue(),
                                    "kind", bm.kind().name(),
                                    "owner", bm.owner().descriptorString(),
                                    "method name", bm.methodName(),
                                    "invocation type", bm.invocationType().descriptorString()));
                            }
                            case NewObjectInstruction newo -> clist.add(map(bci,
                                    "opcode", ins.opcode().name(),
                                    "type", newo.className().asInternalName()));
                            case NewPrimitiveArrayInstruction newa -> clist.add(map(bci,
                                    "opcode", ins.opcode().name(),
                                    "dimensions", 1,
                                    "descriptor", newa.typeKind().descriptor()));
                            case NewReferenceArrayInstruction newa -> clist.add(map(bci,
                                    "opcode", ins.opcode().name(),
                                    "dimensions", 1,
                                    "descriptor", newa.componentType().asInternalName()));
                            case NewMultiArrayInstruction newa -> clist.add(map(bci,
                                    "opcode", ins.opcode().name(),
                                    "dimensions", newa.dimensions(),
                                    "descriptor", newa.arrayType().asInternalName()));
                            case TypeCheckInstruction tch -> clist.add(map(bci,
                                    "opcode", ins.opcode().name(),
                                    "type", tch.type().asInternalName()));
                            case ConstantInstruction cons -> clist.add(map(bci,
                                    "opcode", ins.opcode().name(),
                                    "constant value", cons.constantValue()));
                            case BranchInstruction br -> clist.add(map(bci,
                                    "opcode", ins.opcode().name(),
                                    "target", com.labelToBci(br.target())));
                            case LookupSwitchInstruction ls -> clist.add(map(bci,
                                    value("opcode", ins.opcode().name()),
                                    list("targets", "target", Stream.concat(Stream.of(ls.defaultTarget()).map(com::labelToBci), ls.cases().stream().map(c -> com.labelToBci(c.target()))))));
                            case TableSwitchInstruction ts -> clist.add(map(bci,
                                    value("opcode", ins.opcode().name()),
                                    list("targets", "target", Stream.concat(Stream.of(ts.defaultTarget()).map(com::labelToBci), ts.cases().stream().map(c -> com.labelToBci(c.target()))))));
                            default -> clist.add(map(bci,
                                    "opcode", ins.opcode().name()));
                        }
                        bci += ins.sizeInBytes();
                    }
                }
                if (excHandlers.size() > 0) {
                    clist.add(blockList("exception handlers", excHandlers.stream().map(exc ->
                                    map("try", "start", exc.start(), "end", exc.end(), "handler", exc.handler(), "type", exc.catchType()))));
                }
                mlist.add(blockMap("code", clist));
            });
        }
        return (MapNode)blockMap("method", mlist);
    }

    private static Node printValueEntry(int i, AnnotationConstantValueEntry e) {
        return map(i,
                "tag", tagName(e.tag()),
                "value", String.valueOf(e.constantValue()));
    }

    private static Node printNamedEntry(int i, PoolEntry e, Utf8Entry name) {
        return map(i,
                "tag", tagName(e.tag()),
                "name index", name.index(),
                "value", name.stringValue());
    }

    private static Node printDynamicEntry(int i, DynamicConstantPoolEntry dcpe) {
        return map(i,
                value("tag", tagName(dcpe.tag())),
                value("bootstrap method handle index", dcpe.bootstrap().bootstrapMethod().index()),
                list("bootstrap method arguments indexes", "index", dcpe.bootstrap().arguments().stream().map(en -> en.index())),
                value("name and type index", dcpe.nameAndType().index()),
                value("name", dcpe.name().stringValue()),
                value("type", dcpe.type().stringValue()));
    }

    private static void printAttributes(List<Attribute<?>> attributes, List<Node> out, Verbosity verbosity) {
        for (var attr : attributes) {
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
                    out.add(blockList("invisible annotations", aa.annotations().stream().map(a -> map("anno",
                                value("annotation class", a.className().stringValue()),
                                elementValuePairsToString(a.elements())))));
                case RuntimeVisibleAnnotationsAttribute aa ->
                    out.add(blockList("visible annotations", aa.annotations().stream().map(a -> map("anno",
                                value("annotation class", a.className().stringValue()),
                                elementValuePairsToString(a.elements())))));
                case RuntimeInvisibleParameterAnnotationsAttribute aa ->
                    out.add(blockMap("invisible parameter annotations", IntStream.range(0, aa.parameterAnnotations().size())
                            .filter(i -> !aa.parameterAnnotations().get(i).isEmpty())
                            .mapToObj(i -> list("parameter " + (i + 1), aa.parameterAnnotations().get(i).stream().map(a -> map("anno",
                                    value("annotation class", a.className().stringValue()),
                                    elementValuePairsToString(a.elements())))))));
                case RuntimeVisibleParameterAnnotationsAttribute aa ->
                    out.add(blockMap("visible parameter annotations", IntStream.range(0, aa.parameterAnnotations().size())
                            .filter(i -> !aa.parameterAnnotations().get(i).isEmpty())
                            .mapToObj(i -> list("parameter " + (i + 1), aa.parameterAnnotations().get(i).stream().map(a -> map("anno",
                                    value("annotation class", a.className().stringValue()),
                                    elementValuePairsToString(a.elements())))))));
                case RuntimeInvisibleTypeAnnotationsAttribute aa ->
                    out.add(blockList("invisible type annotations", aa.annotations().stream().map(a -> map("anno",
                                value("annotation class", a.className().stringValue()),
                                value("target info", a.targetInfo().targetType().name()),
                                elementValuePairsToString(a.elements())))));
                case RuntimeVisibleTypeAnnotationsAttribute aa ->
                    out.add(blockList("visible type annotations", aa.annotations().stream().map(a -> map("anno",
                                value("annotation class", a.className().stringValue()),
                                value("target info", a.targetInfo().targetType().name()),
                                elementValuePairsToString(a.elements())))));
                case SignatureAttribute sa ->
                    out.add(value("signature", sa.signature().stringValue()));
                case SourceFileAttribute sfa ->
                    out.add(value("source file", sfa.sourceFile().stringValue()));
                default -> {}
            }
        }
    }

    private static ConstantDesc[] appendLocalInfo(List<LocalVariableInfo> locals, int slot, int bci, ConstantDesc... info) {
        if (locals != null) {
            for (var l : locals) {
                if (l.slot() == slot && l.startPc() <= bci && l.length() + l.startPc() >= bci) {
                    int il = info.length;
                    info = Arrays.copyOf(info, il + 4);
                    info[il] = "type";
                    info[il + 1] = l.type().stringValue();
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
