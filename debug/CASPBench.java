package org.bellsw;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value=3, jvmArgsAppend = "-Xmx1g")
public class CASPBench {
    public Object testObject1;
    public Object testObject2;
    public AtomicReference<Object> aReference;

    // Target heap usage ratio (0.0 - 1.0)
    private final double TARGET_HEAP_RATIO = 0.75;
    
    // Object size ranges to create varied memory regions
    private final int SMALL_OBJECT_SIZE = 64;
    private final int MEDIUM_OBJECT_SIZE = 1024;
    private final int LARGE_OBJECT_SIZE = 8192;
    private final int HUGE_OBJECT_SIZE = 65536;
    
    @Param({"4"})
    public int neighbours;

    private final int STRIDE = 100;
    
    long maxHeap;
    ExecutorService executor;

    /**
     * The test variables are allocated every iteration so you can assume they are initialized to get similar behaviour
     * across iterations
     */
    @Setup(Level.Iteration)
    public void setupIteration() {
        testObject1 = new Object();
        testObject2 = new Object();
        aReference = new AtomicReference<>(testObject1);
    }

    @TearDown(Level.Iteration)
    public void printStats() {
        System.out.println("Heap ratio: " + getCurrentHeapRatio() * 10000.0/100.0 + "%");
    }

    @Setup(Level.Trial)
    public void startTrashing() {
        System.out.println("Target Heap Ratio: " + TARGET_HEAP_RATIO);        
        // Get max heap size
        maxHeap = Runtime.getRuntime().maxMemory();
        System.out.println("Max Heap: " + (maxHeap / (1024 * 1024)) + " MB");

        // Start memory allocator threads
        executor = Executors.newFixedThreadPool(neighbours);
        
        for (int i = 0; i < neighbours; i++) {
            executor.submit(() -> {
                Queue objs = new ConcurrentLinkedQueue<>();
                while (!Thread.currentThread().isInterrupted()) {
                    allocate(objs);
                }
            });
        }
    }

    @TearDown(Level.Trial)
    public void endTrashing() {
        executor.shutdownNow();
    }

    /** Swap a few references */
    @Benchmark
    @OperationsPerInvocation(2)
    public void testNoisyAtomicReference(Blackhole bh) {
        bh.consume(aReference.compareAndSet(testObject1, testObject2));
        bh.consume(aReference.compareAndSet(testObject2, testObject1));
    }
    
    /** Swap a few references */
    @Benchmark
    @OperationsPerInvocation(2)
    public void testNoisyEmpty(Blackhole bh) {
    }

    private void allocate(Queue<byte[]> objs) {
        // Create lots of short-lived objects - main source of evacuation work
        if (getCurrentHeapRatio() < TARGET_HEAP_RATIO) {
            for (int i = 0; i < STRIDE; i++) {
                byte[] obj = new byte[SMALL_OBJECT_SIZE + 
                    ThreadLocalRandom.current().nextInt(MEDIUM_OBJECT_SIZE)];
                objs.offer(obj);
            }
        } else {
            sleepRandom(1, 10);
            // Let some objects die to create garbage
            for (int i = 0; i < STRIDE && !objs.isEmpty(); i++) {
                objs.poll();
            }
        }
    }

    private double getCurrentHeapRatio() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        return (double) used / maxHeap;
    }
    
    private static void sleepRandom(int minMs, int maxMs) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }    
    
}
