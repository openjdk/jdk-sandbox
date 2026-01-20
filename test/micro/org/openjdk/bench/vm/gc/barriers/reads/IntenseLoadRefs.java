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
public class IntenseLoadRefs {
    public static class Node {
        volatile Node child;
        volatile byte[] payload;

        Node(int size) {
            payload = new byte[size];
        }
    }

    @Param({"1024", "4096"})
    public int treeDepth;

    @Param({"1024", "4096"})
    public int nodePayloadSize;

    Node[] roots;

    @Setup(Level.Iteration)
    public void setup() {
        roots = new Node[treeDepth];
        for (int i = 0; i < treeDepth; i++) {
            roots[i] = createTree(nodePayloadSize, 3);
        }
    }

    private Node createTree(int payloadSize, int depth) {
        if (depth == 0) return null;
        Node n = new Node(payloadSize);
        n.child = createTree(payloadSize, depth - 1);
        return n;
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
    @Fork(1)
    public int traverseLRBarrier() {
        int sum = 0;
        for (Node root : roots) {
            Node n = root;
            while (n != null) {
                sum += n.payload[0];
                n = n.child;
            }
        }
        return sum;
    }
}
