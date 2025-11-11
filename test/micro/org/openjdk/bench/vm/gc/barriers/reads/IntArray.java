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
public class IntArray {

    @Param({"1", "1000", "1000000", "1000000000"})
    private int size;

    int[] src;

    @Setup
    public void setup() {
        src = new int[size];
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void plain(Blackhole bh) {
        for (int t : src) {
            bh.consume(t);
        }
    }

}
