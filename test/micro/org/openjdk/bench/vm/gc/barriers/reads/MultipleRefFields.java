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
public class MultipleRefFields {

    Target src;

    @Setup
    public void setup() {
        src = new Target();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void test_local(Blackhole bh) {
        Target s = src;
        bh.consume(s.x1);
        bh.consume(s.x2);
        bh.consume(s.x3);
        bh.consume(s.x4);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void test_field(Blackhole bh) {
        bh.consume(src.x1);
        bh.consume(src.x2);
        bh.consume(src.x3);
        bh.consume(src.x4);
    }

    static class Target {
        Object x1;
        Object x2;
        Object x3;
        Object x4;
    }


}
