/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * @bug 9999999
 * @summary XXX
 * @modules java.compiler
 *          jdk.compiler
 */

import java.net.URI;
import java.util.Arrays;
import java.util.function.Consumer;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeBuilder;
import com.sun.source.util.TreeBuilder.*;
import com.sun.source.util.Trees;
import com.sun.source.util.TreeScanner;

public class ASTBuilder {
    
    public static void main(String[] args) throws Exception {
        runTest("class Test {" +
                "    int x;" +
                "}",
                U -> U._class("Test", C -> C.field("x", Type::_int)));
    }

    private static void runTest(String expectedCode, Consumer<CompilationUnit> actualBuilder) throws Exception {
        final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        assert tool != null;

        JavacTask expecteTask = (JavacTask) tool.getTask(null, null, null,
            null, null, Arrays.asList(new MyFileObject(expectedCode)));
        String expectedDump = dumpTree(expecteTask.parse().iterator().next());

        JavacTask ct = (JavacTask) tool.getTask(null, null, null,
            null, null, Arrays.asList(new MyFileObject("")));
        ct.parse(); //init javac
        Trees t = Trees.instance(ct);
        
        TreeBuilder builder = t.getTreeBuilder();

        CompilationUnitTree cut = builder.createCompilationUnitTree(actualBuilder);
        
        String actualDump = dumpTree(cut);
        
        if (!actualDump.equals(expectedDump)) {
            throw new AssertionError("Expected and actual dump differ. Expected: " + expectedDump + "; actual: " + actualDump);
        }
    }

    static String dumpTree(Tree t) {
        StringBuilder result = new StringBuilder();
        new TreeScanner<Void, Void>() {
            String sep = "";
            @Override
            public Void scan(Tree tree, Void p) {
                result.append(sep);
                sep = " ";
                if (tree == null) {
                    result.append("null");
                    return null;
                }
                result.append("(");
                result.append(tree.getKind().name());
                super.scan(tree, p);
                result.append(")");
                return null;
            }
            @Override
            public Void scan(Iterable<? extends Tree> nodes, Void p) {
                result.append(sep);
                sep = " ";
                if (nodes == null) {
                    result.append("null");
                    return null;
                }
                result.append("(");
                super.scan(nodes, p);
                result.append(")");
                return null;
            }
        }.scan(t, null);
        return result.toString();
    }

    static class MyFileObject extends SimpleJavaFileObject {
        private String text;

        public MyFileObject(String text) {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            this.text = text;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }
}