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

package com.sun.source.util;

import java.util.function.Consumer;

import javax.lang.model.element.Modifier;

import com.sun.source.doctree.DocTree;
import com.sun.source.tree.CompilationUnitTree;

/**
 *  Note: a crude experiment to explore a builder for javac ASTs.
 */
public interface TreeBuilder {

    CompilationUnitTree createCompilationUnitTree(Consumer<CompilationUnit> unit); //TODO: should be in Trees?

    //mixins:
    interface WithModifiers<T extends WithModifiers<T>> {
        T modifiers(Consumer<Modifiers> modifiers);
    }

    interface WithJavadoc<T extends WithJavadoc<T>> {
        T javadoc(DocTree doc);
        T javadoc(String doc);
    }

    //builders:
    interface CompilationUnit {
        CompilationUnit _package(String... qname);
        CompilationUnit _class(String name, Consumer<Class> clazz);
    }

    interface Class extends WithModifiers<Class>, WithJavadoc<Class> {
        //setters:
        Class superclass(Consumer<Type> sup);
        default Class superclass(String sup) {
            return superclass(t -> t.type(sup));
        }
        Class superinterface(Consumer<Type> sup);
        default Class superinterface(String sup) {
            return superinterface(t -> t.type(sup));
        }
        //type parameters?

        //adders:
        default Class field(String name, Consumer<Type> type) {
            //TODO: has this overload a meaning
            return field(name, type, V -> {});
        }

        Class field(String name, Consumer<Type> type, Consumer<Variable> field);
        Class method(String name, Consumer<Type> restype, Consumer<Method> method);
        Class _class(String name, Consumer<Class> clazz);
        
        //TODO:initializer block
    }

    interface Variable extends WithModifiers<Variable>, WithJavadoc<Variable> {
        //setters:
        Variable init(Consumer<Expression> init);
        default Variable init(String init) {
            return init(E -> E.expression(init));
        }
    }

    interface Parameter extends WithModifiers<Parameter> {
        Parameter modifiers(Consumer<Modifiers> modifiers); //TODO: limit to final only?

        Parameter name(String name);
    }

    interface Method extends WithModifiers<Method>, WithJavadoc<Method> {
        default Method parameter(Consumer<Type> type) {
            return parameter(type, P -> {});
        }

        //TODO: parameter overload type+name?
        Method parameter(Consumer<Type> type, Consumer<Parameter> parameter);

        Method body(Consumer<Block> body);

        /**
         * Must include the '{' '}'.
         * @param body
         * @return 
         */
        Method body(String body);
        //throws, default value
    }

    interface Modifiers {
        Modifiers modifier(Modifier modifier);
        Modifiers annotation(Consumer<Annotation> annotation);
    }

    interface Annotation {
        //TODO...
    }

    //TODO: DeclaredType vs. Type?

    interface Type {
        default void _class(String className) {
            _class(N -> N.qualifiedName(className));
        }
        default void _class(Consumer<QualifiedName> className) {
            _class(className, A -> {});
        }
        void _class(Consumer<QualifiedName> className, Consumer<TypeArguments> typeArguments);
        void _int();
        void _float();
        void _void();
        void type(String type);
    }

    interface TypeArguments { //???
        TypeArguments type(Consumer<Type> t);
    }

    interface QualifiedName {
        void select(Consumer<QualifiedName> selected, String name);
        void ident(String simpleName);
        void ident(String... components);
        default void qualifiedName(String qualifiedName) {
            ident(qualifiedName.split("\\."));
        }
    }

    interface Expression {
        void equal_to(Consumer<Expression> lhs, Consumer<Expression> rhs);
        void minusminus(Consumer<Expression> expr);
        void plus(Consumer<Expression> lhs, Consumer<Expression> rhs);
        void cond(Consumer<Expression> cond, Consumer<Expression> truePart, Consumer<Expression> falsePart);
        void select(Consumer<Expression> selected, String name);
        void ident(String qnames);
        void literal(Object value);
        void expression(String expression);
    }

    interface StatementBase<S> {
        S _if(Consumer<Expression> cond, Consumer<Statement> ifPart);
        S _if(Consumer<Expression> cond, Consumer<Statement> ifPart, Consumer<Statement> elsePart);
        S _return();
        S _return(Consumer<Expression> expr);
        S expr(Consumer<Expression> expr);
        S skip();
        S statement(String statement);
    }

    interface Statement extends StatementBase<Void> {
    }

    interface Block extends StatementBase<Block>{
    }

    static void test(TreeBuilder builder) {
        builder.createCompilationUnitTree(
         U -> U._class("Foo", C -> C.field("x", Type::_int)
                                    .method("foo",
                                            Type::_void,
                                            M -> M.parameter(T -> T._class("Foo"))
                                                  .parameter(T -> T._float(), P -> P.name("whatever"))
                                                  .body(B -> B._if(E -> E.minusminus(V -> V.select(S -> S.ident("foo"), "bar")),
                                                                   Statement::skip,
                                                                   Statement::skip
                                                                  )
                                                       )
                                            )));
    }

    //annotations?
    //positions?
}
