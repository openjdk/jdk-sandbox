package org.openjdk.bench.vm.gc.barriers.clone;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = {"-Xmx1g", "-Xms1g"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class Plain {

    PayloadLargeRef largeRef = new PayloadLargeRef();
    PayloadSmallRef smallRef = new PayloadSmallRef();
    PayloadNonRef nonRef = new PayloadNonRef();

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public Object large() {
        return largeRef.clone();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public Object small() {
        return smallRef.clone();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public Object no() {
        return nonRef.clone();
    }

    private static class PayloadSmallRef implements Cloneable {
        Object x1 = new Object();
        Object x2 = new Object();
        Object x3 = new Object();
        Object x4 = new Object();
        Object x5 = new Object();
        Object x6 = new Object();
        Object x7 = new Object();
        Object x8 = new Object();

        @Override
        public PayloadSmallRef clone() {
            try {
                return (PayloadSmallRef) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }
    }

    private static class PayloadLargeRef implements Cloneable {
        Object x1 = new Object();
        Object x2 = new Object();
        Object x3 = new Object();
        Object x4 = new Object();
        Object x5 = new Object();
        Object x6 = new Object();
        Object x7 = new Object();
        Object x8 = new Object();
        Object x9 = new Object();
        Object x10 = new Object();
        Object x11 = new Object();
        Object x12 = new Object();
        Object x13 = new Object();
        Object x14 = new Object();
        Object x15 = new Object();
        Object x16 = new Object();

        @Override
        public PayloadLargeRef clone() {
            try {
                return (PayloadLargeRef) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }
    }

    private static class PayloadNonRef implements Cloneable {
        int x1, x2, x3, x4, x5, x6, x7, x8;
        public PayloadNonRef() {
        }

        @Override
        public PayloadNonRef clone() {
            try {
                return (PayloadNonRef) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }
    }

}
