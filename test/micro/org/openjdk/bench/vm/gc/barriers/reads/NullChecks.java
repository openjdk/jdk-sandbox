package org.openjdk.bench.vm.gc.barriers.reads;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class NullChecks {

    Target src;

    @Setup
    public void setup() {
        src = new Target();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public boolean test_src() {
        return (src != null);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public boolean test_src_ref() {
        return (src != null) && (src.f_Object != null);
    }

    static class Target {
        Object f_Object;
    }

}
