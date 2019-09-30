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

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.test.Functional.ThrowingConsumer;
import jdk.jpackage.test.Functional.ThrowingFunction;
import jdk.jpackage.test.Functional.ThrowingRunnable;
import jdk.jpackage.test.Functional.ThrowingSupplier;


class TestInstance implements ThrowingRunnable {

    static class TestDesc {
        private TestDesc() {
        }

        String testFullName() {
            StringBuilder sb = new StringBuilder();
            sb.append(clazz.getSimpleName());
            if (functionName != null) {
                sb.append('.');
                sb.append(functionName);
                if (args != null) {
                    sb.append('(').append(args).append(')');
                }
            }
            return sb.toString();
        }

        private Class clazz;
        private String functionName;
        private String args;

        private static TestDesc create() {
            TestDesc desc = new TestDesc();
            desc.clazz = enclosingMainMethodClass();
            return desc;
        }

        static TestDesc create(Method m, Object... args) {
            TestDesc desc = new TestDesc();
            desc.clazz = m.getDeclaringClass();
            desc.functionName = m.getName();
            if (args.length != 0) {
                desc.args = Stream.of(args).map(Object::toString).collect(
                        Collectors.joining(","));
            }
            return desc;
        }
    }

    TestInstance(ThrowingRunnable testBody) {
        assertCount = 0;
        this.testConstructor = (unused) -> null;
        this.testBody = (unused) -> testBody.run();
        this.beforeActions = Collections.emptyList();
        this.afterActions = Collections.emptyList();
        this.testDesc = TestDesc.create();
    }

    TestInstance(ThrowingFunction testConstructor, MethodCall testBody,
            List<ThrowingConsumer> beforeActions,
            List<ThrowingConsumer> afterActions) {
        assertCount = 0;
        this.testConstructor = testConstructor;
        this.testBody = testBody;
        this.beforeActions = beforeActions;
        this.afterActions = afterActions;
        this.testDesc = testBody.createDescription();
    }

    void notifyAssert() {
        assertCount++;
    }

    boolean passed() {
        return success;
    }

    String functionName() {
        return testDesc.functionName;
    }

    String baseName() {
        return testDesc.clazz.getSimpleName();
    }

    String fullName() {
        return testDesc.testFullName();
    }

    @Override
    public void run() throws Throwable {
        final String fullName = testDesc.testFullName();
        TKit.log(String.format("[ RUN      ] %s", fullName));
        try {
            Object testInstance = testConstructor.apply(testBody);
            beforeActions.forEach((a) -> ThrowingConsumer.toConsumer(a).accept(
                    testInstance));
            Files.createDirectories(TKit.workDir());
            try {
                testBody.accept(testInstance);
            } finally {
                afterActions.forEach(a -> TKit.ignoreExceptions(() -> a.accept(
                        testInstance)));
            }
            success = true;
        } finally {
            TKit.log(String.format("%s %s; checks=%d",
                    success ? "[       OK ]" : "[  FAILED  ]", fullName,
                    assertCount));
        }
    }

    private static Class enclosingMainMethodClass() {
        StackTraceElement st[] = Thread.currentThread().getStackTrace();
        for (StackTraceElement ste : st) {
            if ("main".equals(ste.getMethodName())) {
                return Functional.ThrowingSupplier.toSupplier(() -> Class.forName(
                        ste.getClassName())).get();
            }
        }
        return null;
    }

    private int assertCount;
    private boolean success;
    private final TestDesc testDesc;
    private final ThrowingFunction testConstructor;
    private final ThrowingConsumer testBody;
    private final List<ThrowingConsumer> beforeActions;
    private final List<ThrowingConsumer> afterActions;
}
