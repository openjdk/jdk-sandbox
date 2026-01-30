import java.lang.ref.*;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class HugeBarrierExercise {

    static final int ROOT_COUNT         = 1 << 11; // 2048
    static final int SMALL_ARRAY_SIZE   = 128;
    static final int MEDIUM_ARRAY_SIZE  = 512;
    static final int BIG_ARRAY_SIZE     = 2048;
    static final int ITERATIONS         = 50_000;
    static final int THREADS            = 4;

    static final Node[]      roots        = new Node[ROOT_COUNT];
    static final Object[]    sharedArray1 = new Object[BIG_ARRAY_SIZE];
    static final Object[]    sharedArray2 = new Object[BIG_ARRAY_SIZE];
    static final Object[][]  matrix       = new Object[64][64];
    static final AtomicReferenceArray<Object> atomicArray =
            new AtomicReferenceArray<>(BIG_ARRAY_SIZE);

    static final ReferenceQueue<Node> weakQ    = new ReferenceQueue<>();
    static final ReferenceQueue<Node> softQ    = new ReferenceQueue<>();
    static final ReferenceQueue<Node> phantomQ = new ReferenceQueue<>();

    static final ConcurrentLinkedQueue<WeakReference<Node>> weakRefs =
            new ConcurrentLinkedQueue<>();
    static final ConcurrentLinkedQueue<SoftReference<Node>> softRefs =
            new ConcurrentLinkedQueue<>();
    static final ConcurrentLinkedQueue<PhantomReference<Node>> phantomRefs =
            new ConcurrentLinkedQueue<>();

    static final AtomicReference<Node> atomicRoot1 = new AtomicReference<>();
    static final AtomicReference<Node> atomicRoot2 = new AtomicReference<>();

    static volatile int sinkInt;
    static volatile long sinkLong;
    static volatile Object sinkObj;

    static final Random rnd = new Random(12345);

    static class Node {
        Node left;
        Node right;
        Node next;
        Node prev;
        Object payload;
        Object altPayload;
        Object[] arrayPayload;
        volatile Object volatileField;
        volatile Object volatileField2;
        int id;
        long stamp;

        Node(int id) {
            this.id = id;
            this.stamp = System.nanoTime();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting HUGE G1 barrier exercise.");
        System.out.println("Suggested VM options:");
        System.out.println("  -XX:+UseG1GC -XX:-TieredCompilation");
        System.out.println("  -XX:CompileCommand=dontinline,HugeBarrierExercise::hotLoop");
        System.out.println("  -XX:+UnlockDiagnosticVMOptions -XX:+PrintAssembly (optional)");
        System.out.println("Press Ctrl+C to stop.");

        // Preinitialize roots and arrays with non-null values
        for (int i = 0; i < ROOT_COUNT; i++) {
            Node n = new Node(i);
            n.payload = new Object();
            n.altPayload = new Object[SMALL_ARRAY_SIZE];
            n.arrayPayload = new Object[MEDIUM_ARRAY_SIZE];
            roots[i] = n;
        }
        for (int i = 0; i < BIG_ARRAY_SIZE; i++) {
            sharedArray1[i] = new Node(i + 10_000);
            sharedArray2[i] = new Node(i + 20_000);
            atomicArray.set(i, new Node(i + 30_000));
        }
        for (int r = 0; r < matrix.length; r++) {
            for (int c = 0; c < matrix[r].length; c++) {
                matrix[r][c] = new Node((r << 16) ^ c);
            }
        }

        Thread[] ts = new Thread[THREADS];
        for (int t = 0; t < THREADS; t++) {
            final int tid = t;
            ts[t] = new Thread(() -> {
                while (true) {
                    hotLoop(tid, ITERATIONS);
                }
            }, "G1HugeBarrierThread-" + t);
            ts[t].setDaemon(false);
            ts[t].start();
        }

        for (Thread t : ts) {
            t.join();
        }
    }

    /**
     * One enormous hot loop that exercises tons of reference patterns.
     * Intentionally huge and ugly.
     */
    static int hotLoop(int threadId, int iterations) {
        int localSum = threadId;

        for (int i = 0; i < iterations; i++) {
            int idx = (i * 37 + threadId * 101) & (ROOT_COUNT - 1);
            int idx2 = (i * 113 + threadId * 17) & (ROOT_COUNT - 1);

            // --- Section 1: allocate new nodes and link them in a small graph ---
            Node a = new Node((threadId << 24) ^ i);
            Node b = new Node((threadId << 20) ^ (i + 1));
            Node c = new Node((threadId << 16) ^ (i + 2));
            Node d = new Node((threadId << 12) ^ (i + 3));

            a.left  = roots[idx];      // store old root into new node
            a.right = b;
            b.left  = c;
            b.right = d;
            c.next  = a;
            d.prev  = b;

            a.payload      = new byte[SMALL_ARRAY_SIZE];        // store array
            a.altPayload   = new Object[SMALL_ARRAY_SIZE / 2];  // store array
            a.arrayPayload = new Object[MEDIUM_ARRAY_SIZE];     // ref-array payload

            b.payload      = new Object();
            b.altPayload   = c;
            b.arrayPayload = new Object[SMALL_ARRAY_SIZE];

            c.payload      = roots[idx2];
            c.altPayload   = new Object[SMALL_ARRAY_SIZE];
            c.arrayPayload = new Object[SMALL_ARRAY_SIZE * 2];

            d.payload      = new Object();
            d.altPayload   = new Object();
            d.arrayPayload = new Object[SMALL_ARRAY_SIZE];

            // Volatile fields
            a.volatileField  = b;
            a.volatileField2 = new Object();
            b.volatileField  = c;
            b.volatileField2 = new Object();
            c.volatileField  = d;
            c.volatileField2 = new Object();

            // --- Section 2: write through roots & atomics (more card marking) ---
            roots[idx] = a;
            roots[idx2] = b;

            atomicRoot1.set(c);
            atomicRoot2.set(d);

            // Swap roots sometimes
            if ((i & 7) == 0) {
                Node tmp = roots[idx];
                roots[idx] = roots[idx2];
                roots[idx2] = tmp;
            }

            // --- Section 3: array stores/loads, arraycopy, fill ---
            int arrIdx = (i * 5 + threadId * 19) & (BIG_ARRAY_SIZE - 1);
            int arrIdx2 = (i * 9 + threadId * 23) & (BIG_ARRAY_SIZE - 1);

            sharedArray1[arrIdx]  = a;
            sharedArray1[arrIdx2] = b;
            sharedArray2[arrIdx]  = c;
            sharedArray2[arrIdx2] = d;

            Object la1 = sharedArray1[arrIdx];
            Object la2 = sharedArray2[arrIdx2];

            atomicArray.set(arrIdx, la1);
            atomicArray.set(arrIdx2, la2);
            Object aa1 = atomicArray.get(arrIdx);
            Object aa2 = atomicArray.get(arrIdx2);

            if (aa1 instanceof Node) localSum += ((Node) aa1).id;
            if (aa2 instanceof Node) localSum ^= ((Node) aa2).id;

            if ((i & 0x1FF) == 0) {
                int off = (i >>> 3) & (BIG_ARRAY_SIZE - 64);
                System.arraycopy(sharedArray1, off, sharedArray2, off + 2, 32);
                System.arraycopy(sharedArray2, off, sharedArray1, off + 4, 16);
                Arrays.fill(sharedArray1, off, off + 4, c);
                Arrays.fill(sharedArray2, off + 8, off + 12, d);
            }

            // --- Section 4: manipulate arrayPayload inside nodes ---
            if (a.arrayPayload != null) {
                for (int k = 0; k < a.arrayPayload.length; k += 4) {
                    a.arrayPayload[k] = roots[(idx + k) & (ROOT_COUNT - 1)];
                }
                for (int k = 1; k < a.arrayPayload.length; k += 8) {
                    a.arrayPayload[k] = null;
                }
            }
            if (b.arrayPayload != null) {
                for (int k = 0; k < b.arrayPayload.length; k += 3) {
                    b.arrayPayload[k] = sharedArray1[(arrIdx + k) & (BIG_ARRAY_SIZE - 1)];
                }
            }
            if (c.arrayPayload != null) {
                for (int k = 0; k < c.arrayPayload.length; k += 5) {
                    c.arrayPayload[k] = sharedArray2[(arrIdx2 + k) & (BIG_ARRAY_SIZE - 1)];
                }
                for (int k = 2; k < c.arrayPayload.length; k += 7) {
                    c.arrayPayload[k] = null;
                }
            }

            // --- Section 5: random walks over roots (SATB-friendly) ---
            Node walk = roots[(idx + 13) & (ROOT_COUNT - 1)];
            for (int steps = 0; steps < 6 && walk != null; steps++) {
                localSum ^= walk.id;
                if ((steps & 1) == 0) walk = walk.left;
                else walk = walk.right;
                if (walk == null) break;
                if (walk.next != null) {
                    walk = walk.next;
                }
            }

            // matrix-based references
            int row = (i + threadId) & (matrix.length - 1);
            int col = (i * 3 + threadId * 7) & (matrix[row].length - 1);
            Object m = matrix[row][col];
            if (m instanceof Node) {
                localSum += ((Node) m).id;
            }
            matrix[row][col] = a;

            // --- Section 6: Weak/Soft/Phantom references ---
            WeakReference<Node> wr   = new WeakReference<>(a, weakQ);
            SoftReference<Node> sr   = new SoftReference<>(b, softQ);
            PhantomReference<Node> pr = new PhantomReference<>(c, phantomQ);

            weakRefs.add(wr);
            softRefs.add(sr);
            phantomRefs.add(pr);

            Node wrGet = wr.get();
            Node srGet = sr.get();

            if (wrGet != null) localSum += wrGet.id;
            if (srGet != null) localSum ^= srGet.id;

            if ((i & 0xFF) == 0) {
                drainQueues();
            }

            // --- Section 7: synchronized blocks + volatile interactions ---
            synchronized (HugeBarrierExercise.class) {
                sinkInt ^= localSum;
                sinkLong += a.stamp ^ b.stamp ^ c.stamp ^ d.stamp;
                sinkObj = a.payload;
            }

            // mutate some root fields under lock
            if ((i & 0x3F) == 0) {
                int rIdx = (idx + i) & (ROOT_COUNT - 1);
                synchronized (roots) {
                    Node rr = roots[rIdx];
                    if (rr != null) {
                        rr.left = c;
                        rr.right = d;
                        rr.payload = new Object();
                        rr.volatileField = new Object();
                    }
                }
            }

            // --- Section 8: a bunch more loads/stores over roots to keep traffic high ---
            if ((i & 0x3FF) == 0) {
                int tmp = 0;
                for (int r = 0; r < ROOT_COUNT; r += 5) {
                    Node n = roots[r];
                    if (n != null) {
                        tmp += n.id;
                        if (n.left != null) tmp ^= n.left.id;
                        if (n.right != null) tmp ^= n.right.id;
                        if (n.next != null) tmp += n.next.id;
                        if (n.prev != null) tmp ^= n.prev.id;
                        Object p1 = n.payload;
                        Object p2 = n.altPayload;
                        if (p1 != null) sinkObj = p1;
                        if (p2 != null) sinkObj = p2;
                    }
                }
                sinkInt ^= tmp;
            }

            // --- Section 9: stress matrix more with swaps & nulling ---
            if ((i & 0x7FF) == 0) {
                for (int r = 0; r < matrix.length; r++) {
                    for (int c2 = 0; c2 < matrix[r].length; c2 += 4) {
                        Object v = matrix[r][c2];
                        matrix[r][c2] = matrix[r][(c2 + 1) & (matrix[r].length - 1)];
                        matrix[r][(c2 + 1) & (matrix[r].length - 1)] = v;
                        if ((c2 & 0x8) == 0) {
                            matrix[r][c2] = null;
                        }
                    }
                }
            }
        }

        sinkInt ^= localSum;
        return localSum;
    }

    private static void drainQueues() {
        for (int i = 0; i < 32; i++) {
            Reference<? extends Node> r = weakQ.poll();
            if (r == null) break;
            Node n = r.get();
            if (n != null) sinkInt ^= n.id;
        }
        for (int i = 0; i < 32; i++) {
            Reference<? extends Node> r = softQ.poll();
            if (r == null) break;
            Node n = r.get();
            if (n != null) sinkInt += n.id;
        }
        for (int i = 0; i < 32; i++) {
            Reference<? extends Node> r = phantomQ.poll();
            if (r == null) break;
            // get() on Phantom is always null, but access 'r' anyway
            sinkLong ^= System.identityHashCode(r);
        }
    }
}

