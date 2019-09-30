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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.Parameters;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Functional.ThrowingConsumer;
import jdk.jpackage.test.Functional.ThrowingFunction;
import jdk.jpackage.test.Functional.ThrowingSupplier;

final class TestBuilder implements AutoCloseable {

    @Override
    public void close() throws Exception {
        flushTestGroup(null);
    }

    TestBuilder(Consumer<TestInstance> testConsumer) {
        argProcessors = Map.of(CMDLINE_ARG_PREFIX + "after-run",
                arg -> getJavaMethodsFromArg(arg).forEach(
                        (method) -> afterActions.add(wrap(method, dryRun))),
                CMDLINE_ARG_PREFIX + "before-run",
                arg -> getJavaMethodsFromArg(arg).forEach(
                        (method) -> beforeActions.add(wrap(method, dryRun))),
                CMDLINE_ARG_PREFIX + "run",
                arg -> flushTestGroup(getJavaMethodsFromArg(arg).map(
                        TestBuilder::toMethodCalls).flatMap(List::stream).collect(
                        Collectors.toList())),
                CMDLINE_ARG_PREFIX + "dry-run",
                arg -> dryRun = true);
        this.testConsumer = testConsumer;
        clear();
    }

    void processCmdLineArg(String arg) throws Throwable {
        int separatorIdx = arg.indexOf('=');
        final String argName;
        final String argValue;
        if (separatorIdx != -1) {
            argName = arg.substring(0, separatorIdx);
            argValue = arg.substring(separatorIdx + 1);
        } else {
            argName = arg;
            argValue = null;
        }
        try {
            ThrowingConsumer<String> argProcessor = argProcessors.get(argName);
            if (argProcessor == null) {
                throw new ParseException("Unrecognized");
            }
            argProcessor.accept(argValue);
        } catch (ParseException ex) {
            ex.setContext(arg);
            throw ex;
        }
    }

    private void flushTestGroup(List<MethodCall> newTestGroup) {
        if (testGroup != null) {
            testGroup.forEach(testBody -> createTestInstance(testBody));
            clear();
        }
        testGroup = newTestGroup;
    }

    private void createTestInstance(MethodCall testBody) {
        ThrowingFunction<MethodCall, Object> testContructor;
        if (dryRun) {
            testContructor = (unused) -> null;
            testBody = DRY_RUN_TEST_BODY;
        } else {
            testContructor = TestBuilder::constructTest;
        }

        TestInstance test = new TestInstance(testContructor, testBody,
                beforeActions, afterActions);
        trace(String.format("[%s] test constructed", test.fullName()));
        testConsumer.accept(test);
    }

    public static void nop () {
    }

    private final static MethodCall DRY_RUN_TEST_BODY = ThrowingSupplier.toSupplier(() -> {
        return new MethodCall(TestBuilder.class.getMethod("nop"));
    }).get();

    private static Object constructTest(MethodCall testBody) throws
            NoSuchMethodException, InstantiationException,
            IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {
        Constructor ctor = testBody.getRequiredConstructor();
        if (ctor == null) {
            return null;
        }
        return ctor.newInstance();
    }

    private void clear() {
        beforeActions = new ArrayList<>();
        afterActions = new ArrayList<>();
        testGroup = null;
    }

    private static Class probeClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    private static Stream<String> cmdLineArgValueToMethodNames(String v) {
        List<String> result = new ArrayList<>();
        String defaultClassName = null;
        for (String token : v.split(",")) {
            Class testSet = probeClass(token);
            if (testSet != null) {
                // Test set class specified. Pull in all public methods
                // from the class with @Test annotation removing name duplicates.
                // Overloads will be handled at the next phase of processing.
                defaultClassName = token;
                Stream.of(testSet.getMethods()).filter(
                        m -> m.isAnnotationPresent(Test.class)).map(
                                Method::getName).distinct().forEach(
                                name -> result.add(String.join(".", token, name)));
                continue;
            }

            final String qualifiedMethodName;
            final int lastDotIdx = token.lastIndexOf('.');
            if (lastDotIdx != -1) {
                qualifiedMethodName = token;
                defaultClassName = token.substring(0, lastDotIdx);
            } else if (defaultClassName == null) {
                throw new ParseException("Default class name not found in");
            } else {
                qualifiedMethodName = String.join(".", defaultClassName, token);
            }
            result.add(qualifiedMethodName);
        }
        return result.stream();
    }

    private static boolean filterMethod(String expectedMethodName, Method method) {
        if (!method.getName().equals(expectedMethodName)) {
            return false;
        }
        switch (method.getParameterCount()) {
            case 0:
                return !isParametrized(method);
            case 1:
                return isParametrized(method);
        }
        return false;
    }

    private static boolean isParametrized(Method method) {
        return method.isAnnotationPresent(Parameters.class) || method.isAnnotationPresent(
                Parameter.class);
    }

    private static List<Method> getJavaMethodFromString(
            String qualifiedMethodName) {
        int lastDotIdx = qualifiedMethodName.lastIndexOf('.');
        if (lastDotIdx == -1) {
            throw new ParseException("Class name not found in");
        }
        String className = qualifiedMethodName.substring(0, lastDotIdx);
        String methodName = qualifiedMethodName.substring(lastDotIdx + 1);
        Class methodClass;
        try {
            methodClass = Class.forName(className);
        } catch (ClassNotFoundException ex) {
            throw new ParseException(String.format("Class [%s] not found;",
                    className));
        }
        // Get the list of all public methods as need to deal with overloads.
        List<Method> methods = Stream.of(methodClass.getMethods()).filter(
                (m) -> filterMethod(methodName, m)).collect(Collectors.toList());
        if (methods.isEmpty()) {
            new ParseException(String.format(
                    "Method [%s] not found in [%s] class;",
                    methodName, className));
        }
        // Make sure default constructor is accessible if the one is needed.
        // Need to probe all methods as some of them might be static and
        // some class members.
        // Onlu class members require default ctor.
        for (Method method : methods) {
            try {
                MethodCall.getRequiredConstructor(method);
            } catch (NoSuchMethodException ex) {
                throw new ParseException(String.format(
                        "Default constructor not found in [%s] class;",
                        className));
            }
        }

        trace(String.format("%s -> %s", qualifiedMethodName, methods));
        return methods;
    }

    private static Stream<Method> getJavaMethodsFromArg(String argValue) {
        return cmdLineArgValueToMethodNames(argValue).map(
                ThrowingFunction.toFunction(
                        TestBuilder::getJavaMethodFromString)).flatMap(
                        List::stream).sequential();
    }

    private static Parameter[] getParameters(Method method) {
        if (method.isAnnotationPresent(Parameters.class)) {
            return ((Parameters) method.getAnnotation(Parameters.class)).value();
        }

        if (method.isAnnotationPresent(Parameter.class)) {
            return new Parameter[]{(Parameter) method.getAnnotation(
                Parameter.class)};
        }

        // Unexpected
        return null;
    }

    private static List<MethodCall> toMethodCalls(Method method) {
        if (!isParametrized(method)) {
            return List.of(new MethodCall(method));
        }
        Parameter[] annotations = getParameters(method);
        if (annotations.length == 0) {
            return List.of(new MethodCall(method));
        }
        return Stream.of(annotations).map((a) -> {
            String annotationValue = a.value();
            Class paramClass = method.getParameterTypes()[0];
            return new MethodCall(method,
                    fromString(annotationValue, paramClass));
        }).collect(Collectors.toList());
    }

    private static Object fromString(String value, Class toType) {
        Function<String, Object> converter = conv.get(toType);
        if (converter == null) {
            throw new RuntimeException(String.format(
                    "Failed to find a conversion of [%s] string to %s type",
                    value, toType));
        }
        return converter.apply(value);
    }

    // Wraps Method.invike() into ThrowingRunnable.run()
    private static ThrowingConsumer wrap(Method method, boolean dryRun) {
        return (test) -> {
            Class methodClass = method.getDeclaringClass();
            String methodName = String.join(".", methodClass.getName(),
                    method.getName());
            TKit.log(String.format("[ CALL     ] %s()", methodName));
            if (!dryRun) {
                if (methodClass.isInstance(test)) {
                    method.invoke(test);
                } else {
                    method.invoke(null);
                }
            }
        };
    }

    private static class ParseException extends IllegalArgumentException {

        ParseException(String msg) {
            super(msg);
        }

        void setContext(String badCmdLineArg) {
            this.badCmdLineArg = badCmdLineArg;
        }

        @Override
        public String getMessage() {
            String msg = super.getMessage();
            if (badCmdLineArg != null) {
                msg = String.format("%s parameter=[%s]", msg, badCmdLineArg);
            }
            return msg;
        }
        private String badCmdLineArg;
    }

    private static void trace(String msg) {
        if (TKit.VERBOSE_TEST_SETUP) {
            TKit.log(msg);
        }
    }

    private final Map<String, ThrowingConsumer<String>> argProcessors;
    private Consumer<TestInstance> testConsumer;
    private List<MethodCall> testGroup;
    private List<ThrowingConsumer> beforeActions;
    private List<ThrowingConsumer> afterActions;
    private boolean dryRun;

    private final static Map<Class, Function<String, Object>> conv = Map.of(
            boolean.class, Boolean::valueOf,
            Boolean.class, Boolean::valueOf,
            int.class, Integer::valueOf,
            Integer.class, Integer::valueOf,
            long.class, Long::valueOf,
            Long.class, Long::valueOf,
            String.class, String::valueOf);

    final static String CMDLINE_ARG_PREFIX = "--jpt-";
}
