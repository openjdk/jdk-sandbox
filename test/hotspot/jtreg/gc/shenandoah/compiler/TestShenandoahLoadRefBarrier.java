/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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

/**
 * @test id=passive
 * @summary Test Shenandoah Load Reference Barrier
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:-IgnoreUnrecognizedVMOptions
 *                  -XX:-BackgroundCompilation
 *                  -XX:-UseOnStackReplacement
 *                  -XX:-TieredCompilation
 *                  -Xms512m -Xmx512m
 *                  -XX:+UseShenandoahGC
 *                  -XX:ShenandoahGCMode=passive
 *                  -XX:+ShenandoahLoadRefBarrier
 *                  -XX:CompileCommand=dontinline,TestShenandoahLoadRefBarrier::notInlined
 *                  -XX:CompileCommand=dontinline,TestShenandoahLoadRefBarrier::lrb*
 *                  -XX:CompileCommand=print,TestShenandoahLoadRefBarrier::lrb*
 *                  -Xlog:gc+ergo,gc+barrier=debug
 *                  TestShenandoahLoadRefBarrier
 */

public class TestShenandoahLoadRefBarrier {
    private static final int LOOP_COUNT = 20_000;
    private static A obj_a = new A();

    public static void main(String[] args) {
        for (int i = 0; i < LOOP_COUNT; i++) {
            lrb_1();
        }
    }

    private static boolean lrb_1() {
        return TestShenandoahLoadRefBarrier.obj_a != null;
    }

    private static class A {
        public float float_field;
    }
}
