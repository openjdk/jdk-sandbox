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
public class MultipleObjects {

    Target s1, s2, s3, s4;

    @Setup
    public void setup() {
        s1 = new Target();
        s2 = new Target();
        s3 = new Target();
        s4 = new Target();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void test(Blackhole bh) {
        bh.consume(s1.x);
        bh.consume(s2.x);
        bh.consume(s3.x);
        bh.consume(s4.x);
    }

    static class Target {
        int x;
    }

}
