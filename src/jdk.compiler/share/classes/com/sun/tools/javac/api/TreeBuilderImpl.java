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
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree.Tag;

import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
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
        public Class superclass(Consumer<Type> sup) {
            result.extending = visitType(sup); //TODO: check extending not filled!
            return this;
        }

        @Override
        public Class superinterface(Consumer<Type> sup) {
            result.implementing = result.implementing.append(visitType(sup));
            return this;
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
            TypeImpl ti = new TypeImpl();
            restype.accept(ti);
            if (ti.type == null) {
                throw new IllegalStateException("Type not provided!");
            }
            MethodImpl vi = new MethodImpl(ti.type, name);
            method.accept(vi);
            result.defs = result.defs.append(vi.result);
            return this;
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
        public void _class(Consumer<QualifiedName> className, Consumer<TypeArguments> typeArguments) {
            JCExpression clazz = visitQualifiedName(className);
            TypeArgumentsImpl ta = new TypeArgumentsImpl();
            typeArguments.accept(ta);
            if (ta.types.isEmpty()) {
                type = clazz;
            } else {
                type = make.TypeApply(clazz, ta.types);
            }
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

    private final class TypeArgumentsImpl implements TypeArguments {
        private List<JCExpression> types = List.nil();
        @Override
        public TypeArguments type(Consumer<Type> t) {
            TypeImpl type = new TypeImpl();
            t.accept(type);
            types = types.append(type.type);
            return this;
        }
    }

    private final class VariableImpl implements Variable {

        private final JCVariableDecl result;
        
        public VariableImpl(JCExpression type, String name) {
            result = make.VarDef(make.Modifiers(0), names.fromString(name), type, null);
        }

        @Override
        public Variable init(Consumer<Expression> init) {
            result.init = visitExpression(init);
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

    private final class MethodImpl implements Method {

        private final JCMethodDecl result;

        public MethodImpl(JCExpression restype, String name) {
            result = make.MethodDef(make.Modifiers(0), names.fromString(name), restype, List.nil(), List.nil(), List.nil(), null, null);
        }

        @Override
        public Method parameter(Consumer<Type> type, Consumer<Parameter> parameter) {
            ParameterImpl paramImpl = new ParameterImpl(visitType(type));
            parameter.accept(paramImpl);
            result.params = result.params.append(paramImpl.result);
            return this;
        }

        @Override
        public Method body(Consumer<Block> statements) {
            BlockImpl block = new BlockImpl();
            statements.accept(block);
            result.body = make.Block(0, block.statements);
            return this;
        }

        @Override
        public Method modifiers(Consumer<Modifiers> modifiers) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Method javadoc(DocTree doc) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Method javadoc(String doc) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        
    }

    private final class ParameterImpl implements Parameter {

        private final JCVariableDecl result;

        public ParameterImpl(JCExpression type) {
            //TODO: infer name
            result = make.VarDef(make.Modifiers(0), null, type, null);
        }

        @Override
        public Parameter modifiers(Consumer<Modifiers> modifiers) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Parameter name(String name) {
            result.name = names.fromString(name); //XXX: check not set yet.
            return this;
        }
        
    }

    private final class BlockImpl extends StatementBaseImpl<Block> implements Block {

        private List<JCStatement> statements = List.nil();

        @Override
        protected Block addStatement(JCStatement stat) {
            statements = statements.append(stat);
            return this;
        }
        
    }

    private final class StatementImpl extends StatementBaseImpl<Void> implements Statement {
        private JCStatement result;

        @Override
        protected Void addStatement(JCStatement stat) {
            if (result != null) {
                throw new IllegalStateException();
            }
            result = stat;
            return null;
        }
    }

    private abstract class StatementBaseImpl<S> implements StatementBase<S> {

        @Override
        public S _if(Consumer<Expression> cond, Consumer<Statement> ifPart) {
            JCExpression expr = visitExpression(cond);
            //TODO: should this automatic wrapping with parenthesized be here?
            expr = make.Parens(expr);
            StatementImpl ifStatement = new StatementImpl();
            ifPart.accept(ifStatement);
            //TODO: check ifPart filled!
            return addStatement(make.If(expr, ifStatement.result, null));
        }

        @Override
        public S _if(Consumer<Expression> cond, Consumer<Statement> ifPart, Consumer<Statement> elsePart) {
            JCExpression expr = visitExpression(cond);
            //TODO: should this automatic wrapping with parenthesized be here?
            expr = make.Parens(expr);
            StatementImpl ifStatement = new StatementImpl();
            ifPart.accept(ifStatement);
            //TODO: check ifPart filled!
            StatementImpl elseStatement = new StatementImpl();
            elsePart.accept(elseStatement);
            return addStatement(make.If(expr, ifStatement.result, elseStatement.result));
        }

        @Override
        public S _return() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public S _return(Consumer<Expression> expr) {
            return addStatement(make.Return(visitExpression(expr)));
        }

        @Override
        public S expr(Consumer<Expression> expr) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public S skip() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        protected abstract S addStatement(JCStatement stat);
    }
    
    private final class ExpressionImpl implements Expression {

        private JCExpression expr;

        @Override
        public void equal_to(Consumer<Expression> lhs, Consumer<Expression> rhs) {
            expr = make.Binary(Tag.EQ,
                               visitExpression(lhs),
                               visitExpression(rhs));
        }

        @Override
        public void minusminus(Consumer<Expression> expr) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void plus(Consumer<Expression> lhs, Consumer<Expression> rhs) {
            expr = make.Binary(Tag.PLUS,
                               visitExpression(lhs),
                               visitExpression(rhs));
        }

        @Override
        public void cond(Consumer<Expression> cond, Consumer<Expression> truePart, Consumer<Expression> falsePart) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void select(Consumer<Expression> selected, String name) {
            expr = make.Select(visitExpression(selected), names.fromString(name));
        }

        @Override
        public void ident(String ident) {
            expr = make.Ident(names.fromString(ident)); //XXX
        }

        @Override
        public void literal(Object value) {
            expr = make.Literal(value);
        }
    }

    private final class QualifiedNameImpl implements QualifiedName {

        private JCExpression expr;

        @Override
        public void select(Consumer<QualifiedName> selected, String name) {
            expr = make.Select(visitQualifiedName(selected), names.fromString(name));
        }

        @Override
        public void ident(String ident) {
            expr = make.Ident(names.fromString(ident));
        }

        @Override
        public void ident(String... qnames) {
            expr = make.Ident(names.fromString(qnames[0]));
            for (int i = 1; i < qnames.length; i++) {
                expr = make.Select(expr, names.fromString(qnames[i]));
            }
        }

    }

    private JCExpression visitExpression(Consumer<Expression> c) {
        ExpressionImpl expr = new ExpressionImpl();

        c.accept(expr);

        if (expr.expr == null) {
            throw new IllegalStateException("Expression not provided!");
        }

        return expr.expr;
    }

    private JCExpression visitQualifiedName(Consumer<QualifiedName> c) {
        QualifiedNameImpl expr = new QualifiedNameImpl();

        c.accept(expr);

        if (expr.expr == null) {
            throw new IllegalStateException("Name not provided!");
        }

        return expr.expr;
    }

    private JCExpression visitType(Consumer<Type> c) {
        TypeImpl type = new TypeImpl();

        c.accept(type);

        if (type.type == null) {
            throw new IllegalStateException("Expression not provided!");
        }

        return type.type;
    }
}
