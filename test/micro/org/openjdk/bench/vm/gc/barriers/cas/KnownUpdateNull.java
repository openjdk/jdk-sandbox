package org.openjdk.bench.vm.gc.barriers.arraycopy;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class KnownUpdateNull {

    AtomicReference<Object> ref;

    Object o1 = new Object();
    Object o2 = o1;

    @Setup
    public void setup() {
        ref = new AtomicReference<>();
        ref.set(o1);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void test() {
        ref.compareAndSet(o1, null);
        ref.set(o1);
    }

}
