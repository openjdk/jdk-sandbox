package org.openjdk.bench.vm.gc.barriers.writes;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class MultipleKnownNulls {

    Target dst;

    @Setup
    public void setup() {
        dst = new Target();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void test() {
        dst.x1 = null;
        dst.x2 = null;
        dst.x3 = null;
        dst.x4 = null;
    }

    static class Target {
        Object x1;
        Object x2;
        Object x3;
        Object x4;
    }

}
