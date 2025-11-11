package org.openjdk.bench.vm.gc.barriers.arraycopy;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class RefArray {

    Object[] srcNull;
    Object[] srcObj;

    @Param({"1", "2", "4", "8", "16", "32", "64"})
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
    public Object objs() {
        Object[] dst = new Object[size];
        System.arraycopy(srcObj, 0, dst, 0, size);
        return dst;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public Object nulls() {
        Object[] dst = new Object[size];
        System.arraycopy(srcNull, 0, dst, 0, size);
        return dst;
    }

}
