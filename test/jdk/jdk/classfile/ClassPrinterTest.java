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
 * @summary Testing Classfile ClassPrinter.
 * @run testng ClassPrinterTest
 */
import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import jdk.classfile.ClassModel;
import jdk.classfile.Classfile;
import jdk.classfile.attribute.SourceFileAttribute;
import jdk.classfile.ClassPrinter;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class ClassPrinterTest {

    ClassModel getClassModel() {
        return Classfile.parse(Classfile.build(ClassDesc.of("Foo"), clb ->
            clb.withVersion(61, 0)
                .withFlags(Classfile.ACC_PUBLIC)
                .with(SourceFileAttribute.of("Foo.java"))
                .withSuperclass(ClassDesc.of("Boo"))
                .withInterfaceSymbols(ClassDesc.of("Phee"), ClassDesc.of("Phoo"))
                .withField("f", ConstantDescs.CD_String, Classfile.ACC_PRIVATE)
                .withMethod("m", MethodTypeDesc.of(ConstantDescs.CD_Void, ConstantDescs.CD_boolean, ConstantDescs.CD_Throwable), Classfile.ACC_PROTECTED, mb -> mb.withCode(cob -> {
                    cob.iload(1);
                    cob.ifThen(thb -> thb.aload(2).athrow());
                    cob.return_();
                }))
        ));
    }

    @Test
    public void testPrintYamlTraceAll() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.toYaml(getClassModel(), ClassPrinter.Verbosity.TRACE_ALL, out::append);
        assertOut(out,
                """
                  - class name: Foo
                    version: 61.0
                    flags: [PUBLIC]
                    superclass: Boo
                    interfaces: [Phee, Phoo]
                    attributes: [SourceFile]
                    constant pool:
                        1: {tag: Utf8, value: Foo}
                        2: {tag: Class, class name index: 1, class internal name: Foo}
                        3: {tag: Utf8, value: Boo}
                        4: {tag: Class, class name index: 3, class internal name: Boo}
                        5: {tag: Utf8, value: f}
                        6: {tag: Utf8, value: Ljava/lang/String;}
                        7: {tag: Utf8, value: m}
                        8: {tag: Utf8, value: (ZLjava/lang/Throwable;)Ljava/lang/Void;}
                        9: {tag: Utf8, value: Phee}
                        10: {tag: Class, class name index: 9, class internal name: Phee}
                        11: {tag: Utf8, value: Phoo}
                        12: {tag: Class, class name index: 11, class internal name: Phoo}
                        13: {tag: Utf8, value: Code}
                        14: {tag: Utf8, value: StackMapTable}
                        15: {tag: Utf8, value: SourceFile}
                        16: {tag: Utf8, value: Foo.java}
                    source file: Foo.java
                    fields:
                      - field name: f
                        flags: [PRIVATE]
                        field type: Ljava/lang/String;
                        attributes: []
                    methods:
                      - method name: m
                        flags: [PROTECTED]
                        method type: (ZLjava/lang/Throwable;)Ljava/lang/Void;
                        attributes: [Code]
                        code:
                            max stack: 1
                            max locals: 3
                            attributes: [StackMapTable]
                            stack map frames:
                                6: {locals: [Foo, int, java/lang/Throwable], stack: []}
                            //stack map frame @0: {locals: [Foo, int, java/lang/Throwable], stack: []}
                            0: {opcode: ILOAD_1, slot: 1}
                            1: {opcode: IFEQ, target: 6}
                            4: {opcode: ALOAD_2, slot: 2}
                            5: {opcode: ATHROW}
                            //stack map frame @6: {locals: [Foo, int, java/lang/Throwable], stack: []}
                            6: {opcode: RETURN}
                """);
    }

    @Test
    public void testPrintYamlCriticalAttributes() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.toYaml(getClassModel(), ClassPrinter.Verbosity.CRITICAL_ATTRIBUTES, out::append);
        assertOut(out,
                """
                  - class name: Foo
                    version: 61.0
                    flags: [PUBLIC]
                    superclass: Boo
                    interfaces: [Phee, Phoo]
                    attributes: [SourceFile]
                    fields:
                      - field name: f
                        flags: [PRIVATE]
                        field type: Ljava/lang/String;
                        attributes: []
                    methods:
                      - method name: m
                        flags: [PROTECTED]
                        method type: (ZLjava/lang/Throwable;)Ljava/lang/Void;
                        attributes: [Code]
                        code:
                            max stack: 1
                            max locals: 3
                            attributes: [StackMapTable]
                            stack map frames:
                                6: {locals: [Foo, int, java/lang/Throwable], stack: []}
                            //stack map frame @0: {locals: [Foo, int, java/lang/Throwable], stack: []}
                            0: {opcode: ILOAD_1, slot: 1}
                            1: {opcode: IFEQ, target: 6}
                            4: {opcode: ALOAD_2, slot: 2}
                            5: {opcode: ATHROW}
                            //stack map frame @6: {locals: [Foo, int, java/lang/Throwable], stack: []}
                            6: {opcode: RETURN}
                """);
    }

    @Test
    public void testPrintYamlMembersOnly() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.toYaml(getClassModel(), ClassPrinter.Verbosity.MEMBERS_ONLY, out::append);
        assertOut(out,
                """
                  - class name: Foo
                    version: 61.0
                    flags: [PUBLIC]
                    superclass: Boo
                    interfaces: [Phee, Phoo]
                    attributes: [SourceFile]
                    fields:
                      - field name: f
                        flags: [PRIVATE]
                        field type: Ljava/lang/String;
                        attributes: []
                    methods:
                      - method name: m
                        flags: [PROTECTED]
                        method type: (ZLjava/lang/Throwable;)Ljava/lang/Void;
                        attributes: [Code]
                """);
    }

    @Test
    public void testPrintJsonTraceAll() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.toJson(getClassModel(), ClassPrinter.Verbosity.TRACE_ALL, out::append);
        assertOut(out,
                """
                  { "class name": "Foo",
                    "version": "61.0",
                    "flags": ["PUBLIC"],
                    "superclass": "Boo",
                    "interfaces": ["Phee", "Phoo"],
                    "attributes": ["SourceFile"],
                    "constant pool": {
                        "1": {"tag": "Utf8", "value": "Foo"},
                        "2": {"tag": "Class", "class name index": 1, "class internal name": "Foo"},
                        "3": {"tag": "Utf8", "value": "Boo"},
                        "4": {"tag": "Class", "class name index": 3, "class internal name": "Boo"},
                        "5": {"tag": "Utf8", "value": "f"},
                        "6": {"tag": "Utf8", "value": "Ljava/lang/String;"},
                        "7": {"tag": "Utf8", "value": "m"},
                        "8": {"tag": "Utf8", "value": "(ZLjava/lang/Throwable;)Ljava/lang/Void;"},
                        "9": {"tag": "Utf8", "value": "Phee"},
                        "10": {"tag": "Class", "class name index": 9, "class internal name": "Phee"},
                        "11": {"tag": "Utf8", "value": "Phoo"},
                        "12": {"tag": "Class", "class name index": 11, "class internal name": "Phoo"},
                        "13": {"tag": "Utf8", "value": "Code"},
                        "14": {"tag": "Utf8", "value": "StackMapTable"},
                        "15": {"tag": "Utf8", "value": "SourceFile"},
                        "16": {"tag": "Utf8", "value": "Foo.java"}},
                    "source file": "Foo.java",
                    "fields": [
                          { "field name": "f",
                            "flags": ["PRIVATE"],
                            "field type": "Ljava/lang/String;",
                            "attributes": []}],
                    "methods": [
                          { "method name": "m",
                            "flags": ["PROTECTED"],
                            "method type": "(ZLjava/lang/Throwable;)Ljava/lang/Void;",
                            "attributes": ["Code"],
                            "code": {
                                "max stack": 1,
                                "max locals": 3,
                                "attributes": ["StackMapTable"],
                                "stack map frames": {
                                    "6": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": []}},
                                "//stack map frame @0": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": []},
                                "0": {"opcode": "ILOAD_1", "slot": 1},
                                "1": {"opcode": "IFEQ", "target": 6},
                                "4": {"opcode": "ALOAD_2", "slot": 2},
                                "5": {"opcode": "ATHROW"},
                                "//stack map frame @6": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": []},
                                "6": {"opcode": "RETURN"}}}]}
                """);
    }

    @Test
    public void testPrintJsonCriticalAttributes() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.toJson(getClassModel(), ClassPrinter.Verbosity.CRITICAL_ATTRIBUTES, out::append);
        assertOut(out,
                """
                  { "class name": "Foo",
                    "version": "61.0",
                    "flags": ["PUBLIC"],
                    "superclass": "Boo",
                    "interfaces": ["Phee", "Phoo"],
                    "attributes": ["SourceFile"],
                    "fields": [
                          { "field name": "f",
                            "flags": ["PRIVATE"],
                            "field type": "Ljava/lang/String;",
                            "attributes": []}],
                    "methods": [
                          { "method name": "m",
                            "flags": ["PROTECTED"],
                            "method type": "(ZLjava/lang/Throwable;)Ljava/lang/Void;",
                            "attributes": ["Code"],
                            "code": {
                                "max stack": 1,
                                "max locals": 3,
                                "attributes": ["StackMapTable"],
                                "stack map frames": {
                                    "6": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": []}},
                                "//stack map frame @0": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": []},
                                "0": {"opcode": "ILOAD_1", "slot": 1},
                                "1": {"opcode": "IFEQ", "target": 6},
                                "4": {"opcode": "ALOAD_2", "slot": 2},
                                "5": {"opcode": "ATHROW"},
                                "//stack map frame @6": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": []},
                                "6": {"opcode": "RETURN"}}}]}
                """);
    }

    @Test
    public void testPrintJsonMembersOnly() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.toJson(getClassModel(), ClassPrinter.Verbosity.MEMBERS_ONLY, out::append);
        assertOut(out,
                """
                  { "class name": "Foo",
                    "version": "61.0",
                    "flags": ["PUBLIC"],
                    "superclass": "Boo",
                    "interfaces": ["Phee", "Phoo"],
                    "attributes": ["SourceFile"],
                    "fields": [
                          { "field name": "f",
                            "flags": ["PRIVATE"],
                            "field type": "Ljava/lang/String;",
                            "attributes": []}],
                    "methods": [
                          { "method name": "m",
                            "flags": ["PROTECTED"],
                            "method type": "(ZLjava/lang/Throwable;)Ljava/lang/Void;",
                            "attributes": ["Code"]}]}
                """);
    }

    @Test
    public void testPrintXmlTraceAll() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.toXml(getClassModel(), ClassPrinter.Verbosity.TRACE_ALL, out::append);
        assertOut(out,
                """
                <?xml version = '1.0'?>
                <class>
                    <class_name>Foo</class_name>
                    <version>61.0</version>
                    <flags><flag>PUBLIC</flag></flags>
                    <superclass>Boo</superclass>
                    <interfaces><interface>Phee</interface><interface>Phoo</interface></interfaces>
                    <attributes><attribute>SourceFile</attribute></attributes>
                    <constant_pool>
                        <_1><tag>Utf8</tag><value>Foo</value></_1>
                        <_2><tag>Class</tag><class_name_index>1</class_name_index><class_internal_name>Foo</class_internal_name></_2>
                        <_3><tag>Utf8</tag><value>Boo</value></_3>
                        <_4><tag>Class</tag><class_name_index>3</class_name_index><class_internal_name>Boo</class_internal_name></_4>
                        <_5><tag>Utf8</tag><value>f</value></_5>
                        <_6><tag>Utf8</tag><value>Ljava/lang/String;</value></_6>
                        <_7><tag>Utf8</tag><value>m</value></_7>
                        <_8><tag>Utf8</tag><value>(ZLjava/lang/Throwable;)Ljava/lang/Void;</value></_8>
                        <_9><tag>Utf8</tag><value>Phee</value></_9>
                        <_10><tag>Class</tag><class_name_index>9</class_name_index><class_internal_name>Phee</class_internal_name></_10>
                        <_11><tag>Utf8</tag><value>Phoo</value></_11>
                        <_12><tag>Class</tag><class_name_index>11</class_name_index><class_internal_name>Phoo</class_internal_name></_12>
                        <_13><tag>Utf8</tag><value>Code</value></_13>
                        <_14><tag>Utf8</tag><value>StackMapTable</value></_14>
                        <_15><tag>Utf8</tag><value>SourceFile</value></_15>
                        <_16><tag>Utf8</tag><value>Foo.java</value></_16></constant_pool>
                    <source_file>Foo.java</source_file>
                    <fields>
                        <field>
                            <field_name>f</field_name>
                            <flags><flag>PRIVATE</flag></flags>
                            <field_type>Ljava/lang/String;</field_type>
                            <attributes></attributes></field></fields>
                    <methods>
                        <method>
                            <method_name>m</method_name>
                            <flags><flag>PROTECTED</flag></flags>
                            <method_type>(ZLjava/lang/Throwable;)Ljava/lang/Void;</method_type>
                            <attributes><attribute>Code</attribute></attributes>
                            <code>
                                <max_stack>1</max_stack>
                                <max_locals>3</max_locals>
                                <attributes><attribute>StackMapTable</attribute></attributes>
                                <stack_map_frames>
                                    <_6><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack></stack></_6></stack_map_frames>
                                <__stack_map_frame__0><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack></stack></__stack_map_frame__0>
                                <_0><opcode>ILOAD_1</opcode><slot>1</slot></_0>
                                <_1><opcode>IFEQ</opcode><target>6</target></_1>
                                <_4><opcode>ALOAD_2</opcode><slot>2</slot></_4>
                                <_5><opcode>ATHROW</opcode></_5>
                                <__stack_map_frame__6><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack></stack></__stack_map_frame__6>
                                <_6><opcode>RETURN</opcode></_6></code></method></methods></class>
                """);
    }

    @Test
    public void testPrintXmlCriticalAttributes() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.toXml(getClassModel(), ClassPrinter.Verbosity.CRITICAL_ATTRIBUTES, out::append);
        assertOut(out,
                """
                <?xml version = '1.0'?>
                <class>
                    <class_name>Foo</class_name>
                    <version>61.0</version>
                    <flags><flag>PUBLIC</flag></flags>
                    <superclass>Boo</superclass>
                    <interfaces><interface>Phee</interface><interface>Phoo</interface></interfaces>
                    <attributes><attribute>SourceFile</attribute></attributes>
                    <fields>
                        <field>
                            <field_name>f</field_name>
                            <flags><flag>PRIVATE</flag></flags>
                            <field_type>Ljava/lang/String;</field_type>
                            <attributes></attributes></field></fields>
                    <methods>
                        <method>
                            <method_name>m</method_name>
                            <flags><flag>PROTECTED</flag></flags>
                            <method_type>(ZLjava/lang/Throwable;)Ljava/lang/Void;</method_type>
                            <attributes><attribute>Code</attribute></attributes>
                            <code>
                                <max_stack>1</max_stack>
                                <max_locals>3</max_locals>
                                <attributes><attribute>StackMapTable</attribute></attributes>
                                <stack_map_frames>
                                    <_6><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack></stack></_6></stack_map_frames>
                                <__stack_map_frame__0><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack></stack></__stack_map_frame__0>
                                <_0><opcode>ILOAD_1</opcode><slot>1</slot></_0>
                                <_1><opcode>IFEQ</opcode><target>6</target></_1>
                                <_4><opcode>ALOAD_2</opcode><slot>2</slot></_4>
                                <_5><opcode>ATHROW</opcode></_5>
                                <__stack_map_frame__6><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack></stack></__stack_map_frame__6>
                                <_6><opcode>RETURN</opcode></_6></code></method></methods></class>
                """);
    }

    @Test
    public void testPrintXmlMembersOnly() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.toXml(getClassModel(), ClassPrinter.Verbosity.MEMBERS_ONLY, out::append);
        assertOut(out,
                """
                <?xml version = '1.0'?>
                <class>
                    <class_name>Foo</class_name>
                    <version>61.0</version>
                    <flags><flag>PUBLIC</flag></flags>
                    <superclass>Boo</superclass>
                    <interfaces><interface>Phee</interface><interface>Phoo</interface></interfaces>
                    <attributes><attribute>SourceFile</attribute></attributes>
                    <fields>
                        <field>
                            <field_name>f</field_name>
                            <flags><flag>PRIVATE</flag></flags>
                            <field_type>Ljava/lang/String;</field_type>
                            <attributes></attributes></field></fields>
                    <methods>
                        <method>
                            <method_name>m</method_name>
                            <flags><flag>PROTECTED</flag></flags>
                            <method_type>(ZLjava/lang/Throwable;)Ljava/lang/Void;</method_type>
                            <attributes><attribute>Code</attribute></attributes></method></methods></class>
                """);
    }

    @Test
    public void testWalkTraceAll() throws IOException {
        var node = ClassPrinter.toTree(getClassModel(), ClassPrinter.Verbosity.TRACE_ALL);
        assertEquals(node.walk().count(), 117);
    }

    @Test
    public void testWalkCriticalAttributes() throws IOException {
        var node = ClassPrinter.toTree(getClassModel(), ClassPrinter.Verbosity.CRITICAL_ATTRIBUTES);
        assertEquals(node.walk().count(), 63);
    }

    @Test
    public void testWalkMembersOnly() throws IOException {
        var node = ClassPrinter.toTree(getClassModel(), ClassPrinter.Verbosity.MEMBERS_ONLY);
        assertEquals(node.walk().count(), 26);
    }

    private static void assertOut(StringBuilder out, String expected) {
//        System.out.println("-----------------");
//        System.out.println(out.toString());
//        System.out.println("-----------------");
        assertEquals(out.toString().trim().split(" *\r?\n"), expected.trim().split("\n"));
    }
}
