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
public class NewObject {

    static Object SRC = new Object();

    Target dst;

    @Setup
    public void setup() {
        dst = new Target();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public Object test() {
        Target t = new Target();
        t.x1 = SRC;
        return t;
    }

    static class Target {
        Object x1;
    }

}
