package org.openjdk.bench.vm.gc.barriers.reads;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class SingleVolatile {

    Target src;

    @Setup
    public void setup() {
        src = new Target();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public Object test_ref() {
        return src.f_Object;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int test_int() {
        return src.f_int;
    }

    static class Target {
        volatile int f_int;
        volatile Object f_Object;
    }

}
