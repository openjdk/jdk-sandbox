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
 */package com.sun.tools.javac.api;

import java.util.function.Consumer;

import com.sun.source.doctree.DocTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TreeBuilder;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree.Tag;

import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;

/**
 * Implementation for TreeBuilder.
 * Note: this is only a crude experiment.
 */
public class TreeBuilderImpl implements TreeBuilder {

    private final TreeMaker make;
    private final Names names;

    public TreeBuilderImpl(TreeMaker make, Names names) {
        this.make = make;
        this.names = names;
    }

    @Override
    public CompilationUnitTree createCompilationUnitTree(Consumer<CompilationUnit> unit) {
        CompilationUnitImpl cui = new CompilationUnitImpl();
        unit.accept(cui);
        return cui.result;
    }
    
    private final class CompilationUnitImpl implements CompilationUnit {
        
        private final JCCompilationUnit result;

        public CompilationUnitImpl() {
            this.result = make.TopLevel(List.nil());
        }

        @Override
        public CompilationUnit _package(String... qname) {
            JCExpression qualIdent = make.Ident(names.fromString(qname[0])); //XXX: should check qname.length > 0!
            for (int i = 1; i < qname.length; i++) {
                qualIdent = make.Select(qualIdent, names.fromString(qname[i]));
            }
            result.defs = result.defs.stream().filter(t -> !t.hasTag(Tag.PACKAGEDEF)).collect(List.collector()) //XXX: what should be the behavior if already filled?
                          .prepend(make.PackageDecl(List.nil(), qualIdent));
            return this;
        }

        @Override
        public CompilationUnit _class(String name, Consumer<Class> clazz) {
            ClassImpl ci = new ClassImpl(name);
            clazz.accept(ci);
            result.defs = result.defs.append(ci.result);
            return this;
        }

    }
    
    private final class ClassImpl implements Class {

        private final JCClassDecl result;

        public ClassImpl(String name) {
            this.result = make.ClassDef(make.Modifiers(0), names.fromString(name), List.nil(), null, List.nil(), List.nil());
        }
        
        @Override
        public Class superclass(Consumer<Expression> sup) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Class superinterface(Consumer<Expression> sup) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Class field(String name, Consumer<Type> type, Consumer<Variable> field) {
            TypeImpl ti = new TypeImpl();
            type.accept(ti);
            if (ti.type == null) {
                throw new IllegalStateException("Type not provided!");
            }
            VariableImpl vi = new VariableImpl(ti.type, name);
            field.accept(vi);
            result.defs = result.defs.append(vi.result);
            return this;
        }

        @Override
        public Class method(String name, Consumer<Type> restype, Consumer<Method> method) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Class _class(String name, Consumer<Class> clazz) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Class modifiers(Consumer<Modifiers> modifiers) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Class javadoc(DocTree doc) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Class javadoc(String doc) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        
    }
    
    private final class TypeImpl implements Type {

        private JCExpression type;

        @Override
        public void _class(String simpleName) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void _int() {
            //XXX: check empty!
            type = make.TypeIdent(TypeTag.INT);
        }

        @Override
        public void _float() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void _void() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

    }
    
    private final class VariableImpl implements Variable {

        private final JCVariableDecl result;
        
        public VariableImpl(JCExpression type, String name) {
            result = make.VarDef(make.Modifiers(0), names.fromString(name), type, null);
        }

        @Override
        public Variable init(Consumer<Expression> init) {
            ExpressionImpl expr = new ExpressionImpl();

            init.accept(expr);

            if (expr.expr == null) {
                throw new IllegalStateException("Expression not provided!");
            }

            result.init = expr.expr;

            return this;
        }

        @Override
        public Variable modifiers(Consumer<Modifiers> modifiers) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Variable javadoc(DocTree doc) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Variable javadoc(String doc) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        
    }

    private final class ExpressionImpl implements Expression {

        private JCExpression expr;

        @Override
        public void minusminus(Consumer<Expression> expr) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void plus(Consumer<Expression> lhs, Consumer<Expression> rhs) {
            ExpressionImpl left = new ExpressionImpl();
            lhs.accept(left);
            ExpressionImpl right = new ExpressionImpl();
            rhs.accept(right);
            expr = make.Binary(Tag.PLUS, left.expr, right.expr); //XXX: check exprs filled!
        }

        @Override
        public void cond(Consumer<Expression> cond, Consumer<Expression> truePart, Consumer<Expression> falsePart) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void ident(String... qnames) {
            expr = make.Ident(names.fromString(qnames[0])); //XXX
        }

        @Override
        public void literal(Object value) {
            expr = make.Literal(value);
        }
    }
}
