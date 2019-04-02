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

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;

public class CodeBuilder {

    public static String buildCodeToGenerate(Tree t, String builder) {
        StringBuilder result = new StringBuilder();
        new TreeScanner<Void, Void>() {
            private final Set<String> usedNames = new HashSet<>();
            private String currentBuilder = builder;
            @Override
            public Void visitClass(ClassTree node, Void p) {
                result.append(currentBuilder() + "._class(\"" + node.getSimpleName() + "\", ");
                doScan("C", () -> {
//                    R r = scan(node.getModifiers(), p);
//                    r = scanAndReduce(node.getTypeParameters(), p, r);
                    if (node.getExtendsClause() != null) {
                        result.append(currentBuilder() + ".superclass(");
                        handleDeclaredType(node.getExtendsClause());
                        result.append(")");
                    }
                    for (Tree impl : node.getImplementsClause()) {
                        result.append(currentBuilder() + ".superinterface(");
                        handleDeclaredType(impl);
                        result.append(")");
                    }
                    scan(node.getMembers(), p);
                });
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
            public Void visitMethod(MethodTree node, Void p) {
                result.append(currentBuilder() + ".method(\"" + node.getName() + "\", ");
                doScan("T", node.getReturnType());
                result.append(", ");
                doScan("M", () -> {
                    //TODO: other attributes!
                    for (VariableTree param : node.getParameters()) {
                        result.append(currentBuilder() + ".parameter(");
                        doScan("T", param.getType());
                        result.append(", ");
                        doScan("P", () -> {
                            result.append(currentBuilder() + ".name(\"" + param.getName() + "\")");
                        });
                        //TODO: other attributes!
                        result.append(")");
                    }
                    if (node.getBody() != null) {//TODO: test no/null body!
                        result.append(currentBuilder() + ".body(");
                        doScan("B", node.getBody());
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
            public Void visitParameterizedType(ParameterizedTypeTree node, Void p) {
                scan(node.getType(), null);
                result.append(", ");
                doScan("A", () -> {
                    for (Tree ta : node.getTypeArguments()) {
                        result.append(currentBuilder() + ".type(");
                        handleDeclaredType(ta);
                        result.append(")");
                    }
                });
                return null;
            }

            @Override
            public Void visitLiteral(LiteralTree node, Void p) {
                result.append(currentBuilder() + ".literal(" + node.getValue() + ")");
                return null;
            }

            @Override
            public Void visitBinary(BinaryTree node, Void p) {
                String methodName;
                switch (node.getKind()) {
                    case PLUS:
                        methodName = "plus"; break;
                    case EQUAL_TO:
                        methodName = "equal_to"; break;
                    default: throw new IllegalStateException("Not handled: " + node.getKind());
                }
                result.append(currentBuilder() + "." + methodName + "(");
                doScan("E", node.getLeftOperand());
                result.append(", ");
                doScan("E", node.getRightOperand());
                result.append(")");
                return null;
            }

            @Override
            public Void visitMemberSelect(MemberSelectTree node, Void p) {
                //TODO: use ident(String...) for "flat" selects
                result.append(currentBuilder() + ".select(");
                doScan("E", node.getExpression());
                result.append(", \"");
                result.append(node.getIdentifier());
                result.append("\")");
                return null;
            }

            @Override
            public Void visitIdentifier(IdentifierTree node, Void p) {
                result.append(currentBuilder() + ".ident(\"" + node.getName() + "\")");
                return null;
            }

            @Override
            public Void visitIf(IfTree node, Void p) {
                result.append(currentBuilder() + "._if(");
                doScan("E", node.getCondition());
                result.append(", ");
                doScan("S", node.getThenStatement());
                if (node.getElseStatement() != null) {
                    result.append(", ");
                    doScan("S", node.getElseStatement());
                }
                result.append(")");
                return null;
            }

            @Override
            public Void visitReturn(ReturnTree node, Void p) {
                result.append(currentBuilder() + "._return(");
                if (node.getExpression()!= null) {
                    doScan("E", node.getExpression());
                }
                result.append(")");
                return null;
            }

            private void handleDeclaredType(Tree t) {
                doScan("T", () -> {
                    result.append(currentBuilder() + "._class(");
                    doScan("Q", t);
                    result.append(")");
                });
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
