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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.classfile.AnnotationElement;
import jdk.classfile.AnnotationValue;
import jdk.classfile.AnnotationValue.*;
import jdk.classfile.Attribute;
import jdk.classfile.ClassModel;
import jdk.classfile.constantpool.AnnotationConstantValueEntry;
import jdk.classfile.constantpool.ClassEntry;
import jdk.classfile.constantpool.ConstantDynamicEntry;
import jdk.classfile.constantpool.DoubleEntry;
import jdk.classfile.constantpool.DynamicConstantPoolEntry;
import jdk.classfile.constantpool.FloatEntry;
import jdk.classfile.constantpool.IntegerEntry;
import jdk.classfile.constantpool.InvokeDynamicEntry;
import jdk.classfile.constantpool.LongEntry;
import jdk.classfile.constantpool.MemberRefEntry;
import jdk.classfile.constantpool.MethodHandleEntry;
import jdk.classfile.constantpool.MethodTypeEntry;
import jdk.classfile.constantpool.ModuleEntry;
import jdk.classfile.constantpool.NameAndTypeEntry;
import jdk.classfile.constantpool.PackageEntry;
import jdk.classfile.constantpool.PoolEntry;
import jdk.classfile.constantpool.StringEntry;
import jdk.classfile.constantpool.Utf8Entry;
import jdk.classfile.util.ClassPrinter;
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
public final class ClassPrinterImpl implements ClassPrinter {

//    public record Format(char quotes, boolean quoteFlagsAndAttrs, boolean quoteTypes, String mandatoryDelimiter, String inlineDelimiter,
//        Block classForm, Block constantPool, String valueEntry, String stringEntry, String namedEntry, String memberEntry,
//        String nameAndTypeEntry, String methodHandleEntry, String methodTypeEntry, String dynamicEntry,
//        String fieldsHeader, Block field, String methodsHeader, Block method, String simpleAttr, String simpleQuotedAttr,
//        String annotationDefault, Table annotations, Table typeAnnotations, String typeAnnotationInline, Table parameterAnnotations,
//        Table annotationValuePair, Table bootstrapMethods, String enclosingMethod, Table innerClasses, Table methodParameters,
//        Table recordComponents, String recordComponentTail, Block module, Table requires, Table exports, Table opens, Table provides,
//        String modulePackages, String moduleMain, String nestHost, String nestMembers,
//        Block code, String plainInstruction, String localVariableInstruction, String incInstruction, String memberInstruction, String invokeDynamicInstruction,
//        String branchInstruction, String switchInstruction, String newArrayInstruction, String typeInstruction, String constantInstruction,
//        Table exceptionHandlers, String tryStartInline, String tryEndInline, String handlerInline, Table localVariableTable, String localVariableInline,
//        String frameInline, Table stackMapTable, Table lineNumberTable, Table characterRangeTable, Table localVariableTypeTable,
//        Function<String, String> escapeFunction) {}


    public enum Style { BLOCK, FLOW }

    public sealed interface Printable {}

    public record PrintableValue(ConstantDesc value) implements Printable {}

    public record PrintableList(Style style, String itemName, List<? extends Printable> list) implements Printable {}

    public record PrintableMap(Style style, Map<? extends ConstantDesc, ? extends Printable> map) implements Printable {}

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
//            "%n            %d: [%s]",
//            "%n            %d: [%s, {slot: %d%s}]",
//            "%n            %d: [%s, {slot: %d, const: %+d%s}]",
//            "%n            %d: [%s, {owner: '%s', name: '%s', descriptor: '%s'}]",
//            "%n            %d: [%s, {name: '%s', descriptor: '%s', bootstrap method kind: %s, owner: '%s', method name: '%s', invocation type: '%s'}]",
//            "%n            %d: [%s, {target: %d}]",
//            "%n            %d: [%s, {targets: %s}]",
//            "%n            %d: [%s, {dimensions: %d, descriptor: '%s'}]",
//            "%n            %d: [%s, {type: '%s'}]",
//            "%n            %d: [%s, {constant value: '%s'}]",
//            new Table("%n            exception handlers: #[<try start pc>, <try end pc>, <handler pc>, <catch type>]", "", "%n                - [%d, %d, %d, %s]"),
//            "%n            #try block start: {start: %d, end: %d, handler: %d, catch type: %s}",
//            "%n            #try block end: {start: %d, end: %d, handler: %d, catch type: %s}",
//            "%n            #exception handler start: {start: %d, end: %d, handler: %d, catch type: %s}",
//            new Table("%n            local variables: #[<start pc>, <end pc>, '<name>', <type>]", "", "%n                - [%d, %d, %d, '%s', '%s']"),
//            ", type: '%s', variable name: '%s'",
//            "%n            #stack map frame locals: %s, stack: %s",
//            new Table("%n            stack map frames:", "", "%n                %d: {locals: %s, stack: %s}"),
//            new Table("%n            line numbers: #[<start pc>, <line number>]", "", "%n                - [%d, %d]"),
//            new Table("%n            character ranges: #[<start pc>, <end pc>, <range start>, <range end>, <flags>]", "", "%n                - [%d, %d, %d, '%s', '%s']"),
//            new Table("%n            local variable types: #[<start pc>, <end pc>, '<name>', <signature>]", "", "%n                - [%d, %d, %d, '%s', '%s']"),
//            ClassPrinterImpl::escapeYaml);

    public sealed interface Printer {
        public void print(String nodeName, Printable node, Consumer<String> out);
    }

    public static final Printer JSON_PRINTER = new JsonPrinter();
    public static final Printer XML_PRINTER = new XmlPrinter();
    public static final Printer YAML_PRINTER = new YamlPrinter();

    private static String NL = System.lineSeparator();

    public static final class YamlPrinter implements Printer {

        @Override
        public void print(String nodeName, Printable node, Consumer<String> out) {
            print(0, false, node, out);
            out.accept(NL);
        }

        private void print(int indent, boolean skipFirstIndent, Printable node, Consumer<String> out) {
            switch (node) {
                case PrintableValue v -> {
                    out.accept(quoteAndEscape(v.value));
                }
                case PrintableList pl -> {
                    switch (pl.style) {
                        case FLOW -> {
                            out.accept("[");
                            boolean first = true;
                            for (var s : pl.list) {
                                if (first) first = false;
                                else out.accept(", ");
                                print(0, false, s, out);
                            }
                            out.accept("]");
                        }
                        case BLOCK -> {
                            for (var n : pl.list) {
                                out.accept(NL + "    ".repeat(indent) + "  - ");
                                print(indent + 1, true, n, out);
                            }
                        }
                    }
                }
                case PrintableMap pm -> {
                    switch (pm.style) {
                        case FLOW -> {
                            out.accept("{");
                            boolean first = true;
                            for (var me : pm.map.entrySet()) {
                                if (first) first = false;
                                else out.accept(", ");
                                out.accept(quoteAndEscape(me.getKey()) + ": ");
                                print(0, false, me.getValue(), out);
                            }
                            out.accept("}");
                        }
                        case BLOCK -> {
                            for (var me : pm.map.entrySet()) {
                                if (skipFirstIndent) {
                                    skipFirstIndent = false;
                                } else {
                                    out.accept(NL + "    ".repeat(indent));
                                }
                                out.accept(quoteAndEscape(me.getKey()) + ": ");
                                var n = me.getValue();
                                print(n instanceof PrintableList pl && pl.style == BLOCK ? indent : indent + 1, false, n, out);
                            }
                        }
                    }
                }
            }
        }

        private static String quoteAndEscape(ConstantDesc value) {
            String s = value.toString();
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
    }

    public static final class JsonPrinter implements Printer {

        @Override
        public void print(String nodeName, Printable node, Consumer<String> out) {
            print(1, true, node, out);
            out.accept(NL);
        }

        private void print(int indent, boolean skipFirstIndent, Printable node, Consumer<String> out) {
            switch (node) {
                case PrintableValue v -> {
                    out.accept(quoteAndEscape(v.value));
                }
                case PrintableList pl -> {
                    out.accept("[");
                    boolean first = true;
                    switch (pl.style) {
                        case FLOW -> {
                            for (var s : pl.list) {
                                if (first) first = false;
                                else out.accept(", ");
                                print(0, false, s, out);
                            }
                        }
                        case BLOCK -> {
                            for (var n : pl.list) {
                                if (first) first = false;
                                else out.accept(",");
                                out.accept(NL + "    ".repeat(indent));
                                print(indent + 1, true, n, out);
                            }
                        }
                    }
                    out.accept("]");
                }
                case PrintableMap pm -> {
                    switch (pm.style) {
                        case FLOW -> {
                            out.accept("{");
                            boolean first = true;
                            for (var me : pm.map.entrySet()) {
                                if (first) first = false;
                                else out.accept(", ");
                                out.accept(quoteAndEscape(me.getKey().toString()) + ": ");
                                print(0, false, me.getValue(), out);
                            }
                        }
                        case BLOCK -> {
                            if (skipFirstIndent) out.accept("  { ");
                            else out.accept("{");
                            boolean first = true;
                            for (var me : pm.map.entrySet()) {
                                if (first) first = false;
                                else out.accept(",");
                                if (skipFirstIndent) skipFirstIndent = false;
                                else out.accept(NL + "    ".repeat(indent));
                                out.accept(quoteAndEscape(me.getKey().toString()) + ": ");
                                print(indent + 1, false, me.getValue(), out);
                            }
                        }
                    }
                    out.accept("}");
                }
            }
        }

        private static String quoteAndEscape(ConstantDesc value) {
            String s = value.toString();
            if (value instanceof Number) return s;
            var sb = new StringBuilder(s.length() << 1);
            sb.append('"');
            s.chars().forEach(c -> escape(c, sb));
            sb.append('"');
            return sb.toString();
        }
    }

    public static final class XmlPrinter implements Printer {

        @Override
        public void print(String nodeName, Printable node, Consumer<String> out) {
            out.accept("<?xml version = '1.0'?>");
            print(0, new PrintableList(BLOCK, nodeName, List.of(node)), out);
            out.accept(NL);
        }

        private void print(int indent, Printable node, Consumer<String> out) {
            switch (node) {
                case PrintableValue v -> {
                    out.accept(xmlEscape(v.value));
                }
                case PrintableList pl -> {
                    var name = toXmlName(pl.itemName);
                    switch (pl.style) {
                        case FLOW -> {
                            for (var n : pl.list) {
                                out.accept("<" + name + ">");
                                if (n instanceof PrintableValue v) {
                                    print(0, n, out);
                                } else {
                                    print(0, n, out);
                                }
                                out.accept("</" + name + ">");
                            }
                        }
                        case BLOCK -> {
                            for (var n : pl.list) {
                                out.accept(NL + "    ".repeat(indent) + "<" + name + ">");
                                if (n instanceof PrintableValue v) {
                                    print(0, n, out);
                                } else {
                                    print(indent + 1, n, out);
                                }
                                out.accept("</" + name + ">");
                            }
                        }
                    }
                }
                case PrintableMap pm -> {
                    switch (pm.style) {
                        case FLOW -> {
                            for (var me : pm.map.entrySet()) {
                                var name = toXmlName(me.getKey().toString());
                                out.accept("<" + name + ">");
                                print(0, me.getValue(), out);
                                out.accept("</" + name + ">");
                            }
                        }
                        case BLOCK -> {
                            for (var me : pm.map.entrySet()) {
                                out.accept(NL + "    ".repeat(indent));
                                var name = toXmlName(me.getKey().toString());
                                out.accept("<" + name + ">");
                                print(indent + 1, me.getValue(), out);
                                out.accept("</" + name + ">");
                            }
                        }
                    }
                }
            }
        }

        private static String xmlEscape(ConstantDesc value) {
            var s = value.toString();
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
    }

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
//
//    private String elementValueToString(AnnotationValue v) {
//        return switch (v) {
//            case OfConstant cv -> format.escapeFunction().apply(v.tag() == 'Z' ? String.valueOf((int)cv.constantValue() != 0) : String.valueOf(cv.constantValue()));
//            case OfClass clv -> clv.className().stringValue();
//            case OfEnum ev -> ev.className().stringValue() + "." + ev.constantName().stringValue();
//            case OfAnnotation av -> v.tag() + av.annotation().className().stringValue();
//            case OfArray av -> av.values().stream().map(ev -> elementValueToString(ev)).collect(Collectors.joining(", ", "[", "]"));
//        };
//    }
//
//    private String elementValuePairsToString(List<AnnotationElement> evps) {
//        return evps.isEmpty() ? "" : evps.stream().map(evp -> format.annotationValuePair.element.formatted(evp.name().stringValue(), elementValueToString(evp.value())))
//                .collect(Collectors.joining(format.inlineDelimiter, format.annotationValuePair.header, format.annotationValuePair.footer));
//    }
//
//    private static String formatDescriptor(String desc) {
//        int i = desc.lastIndexOf('[');
//        if (i >= 0) desc = desc.substring(i + 1);
//        desc = switch (desc) {
//            case "I", "B", "Z", "F", "S", "C", "J", "D" -> TypeKind.fromDescriptor(desc).typeName();
//            default -> desc = Util.descriptorToClass(desc);
//        };
//        if (i >= 0) {
//            var ret = new StringBuilder(desc.length() + 2*i + 2).append(desc);
//            while (i-- >= 0) ret.append('[').append(']');
//            return ret.toString();
//        }
//        return desc;
//    }
//
//    private static Stream<String> convertVTIs(List<StackMapTableAttribute.VerificationTypeInfo> vtis) {
//        return vtis.stream().mapMulti((vti, ret) -> {
//            var s = formatDescriptor(vti.toString());
//            ret.accept(s);
//            if (vti.type() == StackMapTableAttribute.VerificationType.ITEM_DOUBLE || vti.type() == StackMapTableAttribute.VerificationType.ITEM_LONG)
//                ret.accept(s + "2");
//        });
//    }

    private final Printer printer;
    private final VerbosityLevel verbosity;
    private final Consumer<String> out;

    public ClassPrinterImpl(Printer printer, VerbosityLevel verbosity, Consumer<String> out) {
        this.printer = printer;
        this.verbosity = verbosity;
        this.out = out;
    }

    @Override
    public void printClass(ClassModel clm) {
        printer.print("class", asPrintable(clm), out);
    }

    private static Printable value(ConstantDesc value) {
        return new PrintableValue(value);
    }

    private static PrintableList list(String itemName, Object... values) {
        return list(itemName, Stream.of(values));
    }

    private static PrintableList list(String itemName, Stream<?> values) {
        return new PrintableList(FLOW, itemName, values.map(v -> {
            return switch (v) {
                case Printable p -> p;
                case ConstantDesc cd -> new PrintableValue(cd);
                default -> throw new IllegalArgumentException();
            };
        }).toList());
    }

    private static PrintableMap blockMap(Object... keysAndValues) {
        return _map(BLOCK, keysAndValues);
    }

    private static PrintableMap map(Object... keysAndValues) {
        return _map(FLOW, keysAndValues);
    }

    private static PrintableMap _map(Style style, Object... keysAndValues) {
        var map = new LinkedHashMap<ConstantDesc, Printable>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            switch (keysAndValues[i + 1]) {
                case Printable p -> map.put((ConstantDesc)keysAndValues[i], p);
                case ConstantDesc cd -> map.put((ConstantDesc)keysAndValues[i], new PrintableValue(cd));
                default -> throw new IllegalArgumentException();
            }
        }
        return new PrintableMap(style, map);
    }

    private Printable asPrintable(ClassModel clm) {
        var clmap = new LinkedHashMap<String, Printable>();
        clmap.put("class name", value(clm.thisClass().asInternalName()));
        clmap.put("version", value(clm.majorVersion() + "." + clm.minorVersion()));
        clmap.put("flags", list("flag", clm.flags().flags().stream().map(AccessFlag::name)));
        clmap.put("superclass", value(clm.superclass().map(ClassEntry::asInternalName).orElse("")));
        clmap.put("interfaces", list("interface", clm.interfaces().stream().map(ClassEntry::asInternalName)));
        clmap.put("attributes", list("attribute", clm.attributes().stream().map(Attribute::attributeName)));
        if (verbosity == VerbosityLevel.TRACE_ALL) {
            var cpEntries = new LinkedHashMap<Integer, Printable>();
            for (int i = 1; i < clm.constantPool().entryCount();) {
                var e = clm.constantPool().entryByIndex(i);
                cpEntries.put(i, printCPEntry(e));
                i += e.poolEntries();
            }
            clmap.put("constant pool", new PrintableMap(BLOCK, cpEntries));
        }
        if (verbosity != VerbosityLevel.MEMBERS_ONLY) printAttributes(clm.attributes(), clmap);
        clmap.put("fields", new PrintableList(BLOCK, "field", clm.fields().stream().map(f -> {
            var fieldElements = new LinkedHashMap<String, Printable>();
            fieldElements.put("field name", value(f.fieldName().stringValue()));
            fieldElements.put("flags", list("flag", f.flags().flags().stream().map(AccessFlag::name)));
            fieldElements.put("field type", value(f.fieldType().stringValue()));
            fieldElements.put("attributes", list("attribute", f.attributes().stream().map(Attribute::attributeName)));
            if (verbosity != VerbosityLevel.MEMBERS_ONLY) printAttributes(f.attributes(), fieldElements);
            return new PrintableMap(BLOCK, fieldElements);
        }).toList()));
        clmap.put("methods", new PrintableList(BLOCK, "method", clm.methods().stream().map(this::asPrintable).toList()));
        return new PrintableMap(BLOCK, clmap);
    }


    public static String tagName(byte tag) {
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

    private Printable printCPEntry(PoolEntry e) {
        return switch (e) {
            case Utf8Entry ve -> printValueEntry(ve);
            case IntegerEntry ve -> printValueEntry(ve);
            case FloatEntry ve -> printValueEntry(ve);
            case LongEntry ve -> printValueEntry(ve);
            case DoubleEntry ve -> printValueEntry(ve);
            case ClassEntry ce -> printNamedEntry(e, ce.name());
            case StringEntry se -> map(
                    "tag", tagName(e.tag()),
                    "value index", se.utf8().index(),
                    "value", se.stringValue());
            case MemberRefEntry mre -> map(
                    "tag", tagName(e.tag()),
                    "owner index", mre.owner().index(),
                    "name and type index", mre.nameAndType().index(),
                    "owner", mre.owner().asInternalName(),
                    "name", mre.name().stringValue(),
                    "type", mre.type().stringValue());
            case NameAndTypeEntry nte -> map(
                    "tag", tagName(e.tag()),
                    "name index", nte.name().index(),
                    "type index", nte.type().index(),
                    "name", nte.name().stringValue(),
                    "type", nte.type().stringValue());
            case MethodHandleEntry mhe -> map(
                    "tag", tagName(e.tag()),
                    "reference kind", DirectMethodHandleDesc.Kind.valueOf(mhe.kind()).name(),
                    "reference index", mhe.reference().index(),
                    "owner", mhe.reference().owner().asInternalName(),
                    "name", mhe.reference().name().stringValue(),
                    "type", mhe.reference().type().stringValue());
            case MethodTypeEntry mte -> map(
                    "tag", tagName(e.tag()),
                    "descriptor index", mte.descriptor().index(),
                    "descriptor", mte.descriptor().stringValue());
            case ConstantDynamicEntry cde -> printDynamicEntry(cde);
            case InvokeDynamicEntry ide -> printDynamicEntry(ide);
            case ModuleEntry me -> printNamedEntry(e, me.name());
            case PackageEntry pe -> printNamedEntry(e, pe.name());
        };
    }

    private Printable printValueEntry(AnnotationConstantValueEntry e) {
        return map("tag", tagName(e.tag()),
                   "value", String.valueOf(e.constantValue()));
    }

    private Printable printNamedEntry(PoolEntry e, Utf8Entry name) {
        return map("tag", tagName(e.tag()),
                   "name index", name.index(),
                   "value", name.stringValue());
    }

    private Printable printDynamicEntry(DynamicConstantPoolEntry dcpe) {
        return map("tag", tagName(dcpe.tag()),
                   "bootstrap method handle index", dcpe.bootstrap().bootstrapMethod().index(),
                   "bootstrap method arguments indexes", list("index", dcpe.bootstrap().arguments().stream().map(en -> en.index())),
                   "name and type index", dcpe.nameAndType().index(),
                   "name", dcpe.name().stringValue(),
                   "type", dcpe.type().stringValue());
    }

    private void printAttributes(List<Attribute<?>> attributes, Map<String, Printable> map) {
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
            if (verbosity == VerbosityLevel.TRACE_ALL) switch (attr) {
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
//                case RuntimeInvisibleAnnotationsAttribute riaa ->
//                    printTable(format.annotations, riaa.annotations(), a -> new Object[]{indentSpace, a.className().stringValue(), elementValuePairsToString(a.elements())}, indentSpace, "invisible");
//                case RuntimeVisibleAnnotationsAttribute rvaa ->
//                    printTable(format.annotations, rvaa.annotations(), a -> new Object[]{indentSpace, a.className().stringValue(), elementValuePairsToString(a.elements())}, indentSpace, "visible");
//                case RuntimeInvisibleParameterAnnotationsAttribute ripaa -> {
//                    int i = 0;
//                    for (var pa : ripaa.parameterAnnotations()) {
//                        i++;
//                        if (!pa.isEmpty()) printTable(format.parameterAnnotations, pa, a -> new Object[]{indentSpace, a.className().stringValue(), elementValuePairsToString(a.elements())}, indentSpace, "invisible", i);
//                    }
//                }
//                case RuntimeVisibleParameterAnnotationsAttribute rvpaa -> {
//                    int i = 0;
//                    for (var pa : rvpaa.parameterAnnotations()) {
//                        i++;
//                        if (!pa.isEmpty()) printTable(format.parameterAnnotations, pa, a -> new Object[]{indentSpace, a.className().stringValue(), elementValuePairsToString(a.elements())}, indentSpace, "visible", i);
//                    }
//                }
//                case RuntimeInvisibleTypeAnnotationsAttribute ritaa ->
//                    printTable(format.typeAnnotations, ritaa.annotations(), a -> new Object[]{indentSpace, a.className().stringValue(), a.targetInfo().targetType(), elementValuePairsToString(a.elements())}, indentSpace, "invisible");
//                case RuntimeVisibleTypeAnnotationsAttribute rvtaa ->
//                    printTable(format.typeAnnotations, rvtaa.annotations(), a -> new Object[]{indentSpace, a.className().stringValue(), a.targetInfo().targetType(), elementValuePairsToString(a.elements())}, indentSpace, "visible");
//                case SignatureAttribute sa ->
//                    out.accept(format.simpleQuotedAttr.formatted(indentSpace, "signature", escape(sa.signature().stringValue())));
                case SourceFileAttribute sfa ->
                    map.put("source file", value(sfa.sourceFile().stringValue()));
                default -> {}
            }
        }
    }

//    private String findLocal(List<LocalVariableInfo> locals, int slot, int bci) {
//        if (locals != null) {
//            for (var l : locals) {
//                if (l.slot() == slot && l.startPc() <= bci && l.length() + l.startPc() >= bci) {
//                    return format.localVariableInline.formatted(formatDescriptor(l.type().stringValue()), l.name().stringValue());
//                }
//            }
//        }
//        return "";
//    }
//
//    private void forEachOffset(TypeAnnotation ta, LabelResolver lr, BiConsumer<Integer, TypeAnnotation> consumer) {
//        switch (ta.targetInfo()) {
//            case TypeAnnotation.OffsetTarget ot -> consumer.accept(lr.labelToBci(ot.target()), ta);
//            case TypeAnnotation.TypeArgumentTarget tat -> consumer.accept(lr.labelToBci(tat.target()), ta);
//            case TypeAnnotation.LocalVarTarget lvt -> lvt.table().forEach(lvti -> consumer.accept(lr.labelToBci(lvti.startLabel()), ta));
//            default -> {}
//        }
//    }

    @Override
    public void printMethod(MethodModel m) {
        printer.print("method", asPrintable(m), out);
    }

    private Printable asPrintable(MethodModel m) {
        var mmap = new LinkedHashMap<String, Printable>();
        mmap.put("method name", value(m.methodName().stringValue()));
        mmap.put("flags", list("flag", m.flags().flags().stream().map(AccessFlag::name)));
        mmap.put("method type", value(m.methodType().stringValue()));
        mmap.put("attributes", list("attribute", m.attributes().stream().map(Attribute::attributeName)));
        if (verbosity != VerbosityLevel.MEMBERS_ONLY) {
            printAttributes(m.attributes(), mmap);
//            m.code().ifPresent(com -> {
//                out.accept(format.code.header().formatted(((CodeAttribute)com).maxStack(), ((CodeAttribute)com).maxLocals(), attributeNames(com.attributes())));
//                var stackMap = new LinkedHashMap<Integer, StackMapFrame>();
//                var visibleTypeAnnos = new LinkedHashMap<Integer, TypeAnnotation>();
//                var invisibleTypeAnnos = new LinkedHashMap<Integer, TypeAnnotation>();
//                List<LocalVariableInfo> locals = List.of();
//                int lnc =0, lvc = 0, lvtc = 0;
//                for (var attr : com.attributes()) {
//                    if (attr instanceof StackMapTableAttribute smta) {
//                        for (var smf : smta.entries()) {
//                            stackMap.put(smf.absoluteOffset(), smf);
//                        }
//                        printTable(format.stackMapTable, stackMap.values(), f -> new Object[]{f.absoluteOffset(), typesToString(convertVTIs(f.effectiveLocals())), typesToString(convertVTIs(f.effectiveStack()))});
//                    } else if (verbosity == VerbosityLevel.TRACE_ALL) switch (attr) {
//                        case LocalVariableTableAttribute lvta ->
//                            printTable(format.localVariableTable, locals = lvta.localVariables(), lv -> new Object[]{lv.startPc(), lv.startPc() + lv.length(), lv.slot(), lv.name().stringValue(), formatDescriptor(lv.type().stringValue())}, ++lvc < 2 ? "" : " #" + lvc);
//                        case LineNumberTableAttribute lnta ->
//                            printTable(format.lineNumberTable, lnta.lineNumbers(), lni -> new Object[] {lni.startPc(), lni.lineNumber()}, ++lnc < 2 ? "" : " #"+lnc);
//                        case CharacterRangeTableAttribute crta ->
//                            printTable(format.characterRangeTable, crta.characterRangeTable(), chr -> new Object[] {chr.startPc(), chr.endPc(), chr.characterRangeStart(), chr.characterRangeEnd(), chr.flags()});
//                        case LocalVariableTypeTableAttribute lvtta ->
//                            printTable(format.localVariableTypeTable, lvtta.localVariableTypes(), lvt -> new Object[]{lvt.startPc(), lvt.startPc() + lvt.length(), lvt.slot(), lvt.name().stringValue(), formatDescriptor(lvt.signature().stringValue())}, ++lvtc < 2 ? "" : " #" + lvtc);
//                        case RuntimeVisibleTypeAnnotationsAttribute rvtaa ->
//                            rvtaa.annotations().forEach(a -> forEachOffset(a, com, visibleTypeAnnos::put));
//                        case RuntimeInvisibleTypeAnnotationsAttribute ritaa ->
//                            ritaa.annotations().forEach(a -> forEachOffset(a, com, invisibleTypeAnnos::put));
//                        case Object o -> {}
//                    }
//                }
//                printAttributes("            ", com.attributes());
//                stackMap.putIfAbsent(0, StackMapDecoder.initFrame(m));
//                var excHandlers = com.exceptionHandlers().stream().map(exc -> new ExceptionHandler(com.labelToBci(exc.tryStart()), com.labelToBci(exc.tryEnd()), com.labelToBci(exc.handler()), exc.catchType().map(ct -> ct.asInternalName()).orElse(null))).toList();
//                int bci = 0;
//                for (var coe : com) {
//                    if (coe instanceof Instruction ins) {
//                        var frame = stackMap.get(bci);
//                        if (frame != null) {
//                            out.accept(format.frameInline.formatted(typesToString(convertVTIs(frame.effectiveLocals())), typesToString(convertVTIs(frame.effectiveStack()))));
//                        }
//                        var a = invisibleTypeAnnos.get(bci);
//                        if (a != null) {
//                            out.accept(format.typeAnnotationInline.formatted("invisible", a.className().stringValue(), a.targetInfo().targetType(), elementValuePairsToString(a.elements())));
//                        }
//                        a = visibleTypeAnnos.get(bci);
//                        if (a != null) {
//                            out.accept(format.typeAnnotationInline.formatted("visible", a.className().stringValue(), a.targetInfo().targetType(), elementValuePairsToString(a.elements())));
//                        }
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
//                        out.accept(format.mandatoryDelimiter);
//                        switch (coe) {
//                            case IncrementInstruction inc ->
//                                out.accept(format.incInstruction.formatted(bci, ins.opcode().name(), inc.slot(), inc.constant(), findLocal(locals, inc.slot(), bci)));
//                            case LoadInstruction lv ->
//                                out.accept(format.localVariableInstruction.formatted(bci, ins.opcode().name(), lv.slot(), findLocal(locals, lv.slot(), bci)));
//                            case StoreInstruction lv ->
//                                out.accept(format.localVariableInstruction.formatted(bci, ins.opcode().name(), lv.slot(), findLocal(locals, lv.slot(), bci)));
//                            case FieldInstruction fa ->
//                                out.accept(format.memberInstruction.formatted(bci, ins.opcode().name(), fa.owner().asInternalName(), escape(fa.name().stringValue()), fa.type().stringValue()));
//                            case InvokeInstruction inv ->
//                                out.accept(format.memberInstruction.formatted(bci, ins.opcode().name(), inv.owner().asInternalName(), escape(inv.name().stringValue()), inv.type().stringValue()));
//                            case InvokeDynamicInstruction invd -> {
//                                var bm = invd.bootstrapMethod();
//                                out.accept(format.invokeDynamicInstruction.formatted(bci, ins.opcode().name(), invd.name().stringValue(), invd.type().stringValue(), bm.kind(), bm.owner().descriptorString(), escape(bm.methodName()), bm.invocationType().descriptorString()));
//                            }
//                            case NewObjectInstruction newo ->
//                                out.accept(format.typeInstruction.formatted(bci, ins.opcode().name(), newo.className().asInternalName()));
//                            case NewPrimitiveArrayInstruction newa -> out.accept(format.newArrayInstruction.formatted(bci, ins.opcode().name(), 1, newa.typeKind().descriptor()));
//                            case NewReferenceArrayInstruction newa -> out.accept(format.newArrayInstruction.formatted(bci, ins.opcode().name(), 1, newa.componentType().asInternalName()));
//                            case NewMultiArrayInstruction newa -> out.accept(format.newArrayInstruction.formatted(bci, ins.opcode().name(), newa.dimensions(), newa.arrayType().asInternalName()));
//                            case TypeCheckInstruction tch ->
//                                out.accept(format.typeInstruction.formatted(bci, ins.opcode().name(), tch.type().asInternalName()));
//                            case ConstantInstruction cons ->
//                                out.accept(format.constantInstruction.formatted(bci, ins.opcode().name(), escape(String.valueOf(cons.constantValue()))));
//                            case BranchInstruction br ->
//                                out.accept(format.branchInstruction.formatted(bci, ins.opcode().name(), com.labelToBci(br.target())));
//                            case LookupSwitchInstruction ls ->
//                                out.accept(format.switchInstruction.formatted(bci, ins.opcode().name(), Stream.concat(Stream.of(ls.defaultTarget()).map(com::labelToBci), ls.cases().stream().map(c -> com.labelToBci(c.target()))).toList()));
//                            case TableSwitchInstruction ts ->
//                                out.accept(format.switchInstruction.formatted(bci, ins.opcode().name(), Stream.concat(Stream.of(ts.defaultTarget()).map(com::labelToBci), ts.cases().stream().map(c -> com.labelToBci(c.target()))).toList()));
//                            default ->
//                                out.accept(format.plainInstruction.formatted(bci, ins.opcode().name()));
//                        }
//                        bci += ins.sizeInBytes();
//                    }
//                }
//                out.accept(format.code.footer().formatted());
//                if (excHandlers.size() > 0) {
//                    printTable(format.exceptionHandlers, excHandlers, exc -> new Object[]{exc.start(), exc.end(), exc.handler(), exc.catchType()});
//                }
//            });
        }
        return new PrintableMap(BLOCK, mmap);
    }
}
