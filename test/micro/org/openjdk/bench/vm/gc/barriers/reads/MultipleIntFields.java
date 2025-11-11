package org.openjdk.bench.vm.gc.barriers.reads;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class MultipleIntFields {

    Target src;

    @Setup
    public void setup() {
        src = new Target();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int test_local() {
        Target s = src;
        return s.x1 + s.x2 + s.x3 + s.x4;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int test_field() {
        return src.x1 + src.x2 + src.x3 + src.x4;
    }

    static class Target {
        int x1;
        int x2;
        int x3;
        int x4;
    }


}
