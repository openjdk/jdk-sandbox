/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;


public class CodeBuilder {

    public static String buildCodeToGenerate(Tree t, String builder) {
        StringBuilder result = new StringBuilder();
        new TreeScanner<Void, Void>() {
            private final Set<String> usedNames = new HashSet<>();
            private String currentBuilder = builder;
            @Override
            public Void visitClass(ClassTree node, Void p) {
                result.append(currentBuilder() + "._class(\"" + node.getSimpleName() + "\", ");
                doScan("C", () -> super.visitClass(node, p));
                result.append(")");
                return null;
            }
            @Override
            public Void visitCompilationUnit(CompilationUnitTree node, Void p) {
                result.append(currentBuilder() + ".createCompilationUnitTree(");
                doScan("U", () -> super.visitCompilationUnit(node, p));
                result.append(")");
                return null;
            }
            @Override
            public Void visitVariable(VariableTree node, Void p) {
                result.append(currentBuilder() + ".field(\"" + node.getName() + "\", "); //XXX: field/vs local variable!
                doScan("T", node.getType());
                result.append(", ");
                doScan("F", () -> {
                    //TODO: modifiers....
                    if (node.getInitializer() != null) {
                        result.append(currentBuilder() + ".init(");
                        doScan("E", node.getInitializer());
                        result.append(")");
                    }
                });
                result.append(")");
                return null;
            }

            @Override
            public Void visitPrimitiveType(PrimitiveTypeTree node, Void p) {
                result.append(currentBuilder() + "._" + node.getPrimitiveTypeKind().name().toLowerCase(Locale.ROOT) + "()");
                return null;
            }

            @Override
            public Void visitLiteral(LiteralTree node, Void p) {
                result.append(currentBuilder() + ".literal(" + node.getValue() + ")");
                return null;
            }

            @Override
            public Void visitBinary(BinaryTree node, Void p) {
                switch (node.getKind()) {
                    case PLUS:
                        result.append(currentBuilder() + ".plus(");
                        doScan("E", node.getLeftOperand());
                        result.append(", ");
                        doScan("E", node.getRightOperand());
                        result.append(")");
                        break;
                    default: throw new IllegalStateException("Not handled: " + node.getKind());
                }
                return null;
            }

            @Override
            public Void visitIdentifier(IdentifierTree node, Void p) {
                result.append(currentBuilder() + ".ident(\"" + node.getName() + "\")");
                return null;
            }

            private String currentBuilder() {
                if (currentBuilder != null) {
                    String res = currentBuilder;
                    currentBuilder = null;
                    return res;
                }
                return "";
            }
            private void doScan(String preferredBuilderName, Tree... subTreesToScan) {
                doScan(preferredBuilderName, () -> {
                    for (Tree tree : subTreesToScan) {
                        scan(tree, null);
                    }
                });
            }

            private void doScan(String preferredBuilderName, Runnable scan) {
                String prevBuilder = currentBuilder;
                String builder = inferBuilderName(preferredBuilderName);
                try {
                    result.append(builder + " -> ");
                    int len = result.length();
                    currentBuilder = builder;
                    scan.run();
                    if (result.length() == len) {
                        result.append("{}");
                    }
                } finally {
                    currentBuilder = prevBuilder;
                    usedNames.remove(builder);
                }
            }
            private String inferBuilderName(String preferredBuilderName) {
                Integer idx = null;
                while (true) {
                    String thisBuilder = preferredBuilderName + (idx != null ? idx : "");
                    if (usedNames.add(thisBuilder)) {
                        return thisBuilder;
                    }
                    idx = idx != null ? idx + 1 : 1;
                }
            }
        }.scan(t, null);

        return result.toString();
    }

}
