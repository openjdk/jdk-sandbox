/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.micro.jdk.java.lang.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@Fork(jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-Xmn768m", "-XX:+UseParallelGC"}, value = 5)
public class GetMethods {

    public static class InternalClass {

        public InternalClass() {
        }

        @Override
        public String toString() {
            return InternalClass.class.getName();
        }
    }

    /**
     * Get the constructor through reflection on a class in the same classloader
     *
     * @return the constructor
     * @throws NoSuchMethodException
     */
    @Benchmark
    public Constructor<InternalClass> getConstructor() throws NoSuchMethodException {
        return InternalClass.class.getConstructor();
    }

    /**
     * Get the constructor through reflection on a class in a different classloader
     *
     * @return the constructor
     * @throws NoSuchMethodException
     */
    @Benchmark
    public Constructor<String> getConstructorDifferentClassLoader() throws NoSuchMethodException {
        return String.class.getConstructor();
    }

    /**
     * Get the toString method through reflection on a class in the same classloader
     *
     * @return the toString method
     * @throws java.lang.NoSuchMethodException
     */
    @Benchmark
    public Method getMethod() throws NoSuchMethodException {
        return InternalClass.class.getMethod("toString");
    }

    /**
     * Get the toString method through reflection on a class in a different classloader
     *
     * @return the toString method
     * @throws NoSuchMethodException
     */
    @Benchmark
    public Method getMethodDifferentClassLoader() throws NoSuchMethodException {
        return String.class.getMethod("toString");
    }
}
