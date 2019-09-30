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
package jdk.jpackage.test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import jdk.jpackage.test.Functional.ThrowingConsumer;
import jdk.jpackage.test.Functional.ThrowingSupplier;
import jdk.jpackage.test.TestInstance.TestDesc;

class MethodCall implements ThrowingConsumer {

    MethodCall(Method method, Object... args) {
        this.method = method;
        this.args = args;
    }

    TestDesc createDescription() {
        return TestDesc.create(method, args);
    }

    Constructor getRequiredConstructor() throws NoSuchMethodException {
        return MethodCall.getRequiredConstructor(method);
    }

    static Constructor getRequiredConstructor(Method method) throws
            NoSuchMethodException {
        if ((method.getModifiers() & Modifier.STATIC) == 0) {
            return method.getDeclaringClass().getConstructor();
        }
        return null;
    }

    @Override
    public void accept(Object thiz) throws Throwable {
        method.invoke(thiz, args);
    }

    private final Object[] args;
    private final Method method;
}
