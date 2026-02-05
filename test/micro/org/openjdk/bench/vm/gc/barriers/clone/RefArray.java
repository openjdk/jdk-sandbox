package org.openjdk.bench.vm.gc.barriers.clone;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = {"-Xmx8g", "-Xms8g"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class RefArray {

    Object[] srcNull;
    Object[] srcObj;

    @Param({"1", "4", "16", "64", "256", "1024"})
    int size;

    @Setup
    public void setup() {
        srcNull = new Object[size];
        srcObj = new Object[size];
        for (int c = 0; c < size; c++) {
            srcObj[c] = new Object();
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public Object[] objs() {
        return srcObj.clone();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public Object[] nulls() {
        return srcNull.clone();
    }

}
