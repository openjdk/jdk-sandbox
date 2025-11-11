package org.openjdk.bench.vm.gc.barriers.writes;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(1)
@State(Scope.Thread)
public class MultipleZeroes {

    Target dst;

    @Setup
    public void setup() {
        dst = new Target();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void test() {
        dst.x1 = 0;
        dst.x2 = 0;
        dst.x3 = 0;
        dst.x4 = 0;
    }

    static class Target {
        int x1;
        int x2;
        int x3;
        int x4;
    }

}
