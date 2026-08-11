/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

package org.openjdk.bench.vm.gc.barriers.reads;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class SameObjectLoaded {

    Target obj1, obj2, obj3, obj4;

    @Setup
    public void setup() {
        obj1 = new Target();
        obj2 = new Target();
        obj3 = new Target();
        obj4 = new Target();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void loadSameTrivial1(Blackhole bh) {
        bh.consume(this.obj1);
        bh.consume(this.obj1);
        bh.consume(this.obj1);
        bh.consume(this.obj1);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void loadFieldTrivial1(Blackhole bh) {
        bh.consume(this.obj1.x + this.obj2.x + this.obj3.x + this.obj4.x);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void loadSameInCountedLoopSmallBody(Blackhole bh) {
      for (int i=0; i<1024; i++) {
        bh.consume(this.obj1);
        bh.consume(this.obj1);
        bh.consume(this.obj1);
        bh.consume(this.obj1);
      }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void loadSameInCountedLoopSmallBody2(Blackhole bh) {
      for (int i=0; i<1024; i++) {
        bh.consume(this.obj1.x);
        bh.consume(this.obj1.x);
        bh.consume(this.obj1.x);
        bh.consume(this.obj1.x);
      }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void loadSameInCountedLoopTinyBody(Blackhole bh) {
      for (int i=0; i<1024; i++) {
        bh.consume(this.obj1);
      }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void loadSameTrivialDominated1(Blackhole bh) {
      bh.consume(this.obj1);

      if (bh != null) {
        bh.consume(this.obj1);
      } else {
        bh.consume(this.obj1);
      }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void loadSameTrivialDominated2(Blackhole bh) {
      bh.consume(this.obj1);

      if (bh != null) {
        bh.consume(123);
      } else {
        bh.consume(456);
      }

      bh.consume(this.obj1);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void loadSameTrivialDominated3(Blackhole bh) {
      if (bh != null) {
        bh.consume(this.obj1);
      } else {
        bh.consume(this.obj1);
      }

      if (bh != null) {
        bh.consume(this.obj1);
      } else {
        bh.consume(this.obj1);
      }

      if (bh != null) {
        bh.consume(this.obj1);
      } else {
        bh.consume(this.obj1);
      }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void loadSameNewAllocation1(Blackhole bh) {
      Target obj1 = new Target();
      bh.consume(obj1);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void loadSameNewAllocation2(Blackhole bh) {
      this.obj1 = new Target();
      bh.consume(this.obj1);
    }

    static class Target {
        int x;
    }
}

