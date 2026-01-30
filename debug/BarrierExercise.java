import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class BarrierExercise {

    static final int ROOT_COUNT       = 1 << 10;
    static final int ARRAY_SIZE       = 256;
    static final int BIG_ARRAY_SIZE   = 1024;
    static final int ITERATIONS       = 10_000;
    static final int THREADS          = 4;

    // Shared roots to cause a lot of heap loads / stores
    static final Node[] roots = new Node[ROOT_COUNT];

    // Arrays with references, for array load/store barriers
    static final Object[] sharedArray = new Object[BIG_ARRAY_SIZE];
    static final AtomicReferenceArray<Object> atomicArray =
            new AtomicReferenceArray<>(BIG_ARRAY_SIZE);

    // Queues & references to exercise weak/soft ref paths
    static final ReferenceQueue<Node> weakQueue  = new ReferenceQueue<>();
    static final ReferenceQueue<Node> softQueue  = new ReferenceQueue<>();
    static final ConcurrentLinkedQueue<WeakReference<Node>> weakRefs =
            new ConcurrentLinkedQueue<>();
    static final ConcurrentLinkedQueue<SoftReference<Node>> softRefs =
            new ConcurrentLinkedQueue<>();

    // Some atomics to keep work from being dead-code-eliminated
    static final AtomicReference<Node> atomicRoot = new AtomicReference<>();
    static volatile int sinkInt;
    static volatile Object sinkObj;

    static final Random rnd = new Random(42);

    static class Node {
        Node left;
        Node right;
        Object payload;
        Object[] arrayPayload;
        volatile Object volatileField;
        int id;

        Node(int id) {
            this.id = id;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Starting G1 barrier exercise...");
        System.out.println("Run with e.g.: -XX:+UseG1GC -XX:+UnlockDiagnosticVMOptions -XX:+PrintAssembly");
        System.out.println("Press Ctrl+C to stop.");

        // Preinitialize some roots to have non-null references early
        for (int i = 0; i < ROOT_COUNT; i++) {
            roots[i] = new Node(i);
        }

        Thread[] ts = new Thread[THREADS];
        for (int t = 0; t < THREADS; t++) {
            final int tid = t;
            ts[t] = new Thread(() -> {
                while (true) {
                    exerciseBarriers(tid, ITERATIONS);
                }
            }, "G1BarrierThread-" + t);
            ts[t].setDaemon(false);
            ts[t].start();
        }

        for (Thread t : ts) {
            t.join();
        }
    }

    /**
     * Huge method that tries to generate a variety of reference loads/stores:
     *  - object field loads/stores
     *  - array loads/stores
     *  - volatile field loads/stores
     *  - weak/soft references
     *  - AtomicReference / AtomicReferenceArray
     *  - a bit of traversal to keep SATB interesting
     */
    static int exerciseBarriers(int threadId, int iterations) {
        int localSum = 0;

        for (int i = 0; i < iterations; i++) {
            int idx = (i + threadId * 31) & (ROOT_COUNT - 1);

            // --- Allocate a bunch of nodes (allocation + post-barriers on fields) ---
            Node n = new Node(i ^ threadId);
            n.payload = new byte[ARRAY_SIZE];              // new array, store into field
            n.arrayPayload = new Object[ARRAY_SIZE];       // new ref array
            n.volatileField = new Object();                // volatile store

            // Build a short chain to get some pointer chasing
            Node n2 = new Node(i + 1);
            Node n3 = new Node(i + 2);
            n.left = n2;                                   // field store barrier
            n.right = n3;                                  // field store barrier
            n2.left = roots[idx];                          // store from roots
            n3.right = atomicRoot.get();                   // load from atomic, then store

            // Put into global roots (card marking)
            roots[idx] = n;                                // store into roots array
            atomicRoot.set(n);                             // atomic store

            // --- Array stores / loads (post + pre barriers) ---
            int arrIdx = (i * 7) & (BIG_ARRAY_SIZE - 1);

            sharedArray[arrIdx] = n;                       // object array store
            Object loaded = sharedArray[arrIdx];           // array load

            atomicArray.set(arrIdx, loaded);               // atomic array store
            Object loaded2 = atomicArray.get(arrIdx);      // atomic array load

            // Use the loads so they are not DCE'd
            if (loaded2 instanceof Node) {
                localSum += ((Node) loaded2).id;
            }

            // --- Manipulate Node fields repeatedly ---
            Node root = roots[(idx + 13) & (ROOT_COUNT - 1)];
            if (root != null) {
                // a small walk
                Node p = root;
                for (int k = 0; k < 4; k++) {
                    localSum ^= p.id;
                    if (p.left != null) {
                        p = p.left;
                    } else if (p.right != null) {
                        p = p.right;
                    } else {
                        break;
                    }
                }

                // mutate fields to force more post-barriers
                root.left = n2;
                root.right = n3;
                root.payload = new Object();              // overwrite payload
                root.volatileField = loaded;              // volatile write
            }

            // --- Fill array payload with references, then clear them ---
            if (n.arrayPayload != null) {
                for (int k = 0; k < n.arrayPayload.length; k += 8) {
                    n.arrayPayload[k] = roots[(idx + k) & (ROOT_COUNT - 1)];
                }
                for (int k = 0; k < n.arrayPayload.length; k += 16) {
                    n.arrayPayload[k] = null;            // nulling refs (write barriers)
                }
            }

            // --- Weak / soft references (weak root barriers, SATB) ---
            WeakReference<Node> wr = new WeakReference<>(n, weakQueue);
            SoftReference<Node> sr = new SoftReference<>(n2, softQueue);
            weakRefs.add(wr);
            softRefs.add(sr);

            Node wrGet = wr.get();
            if (wrGet != null) {
                localSum += wrGet.id;
            }
            Node srGet = sr.get();
            if (srGet != null) {
                localSum += srGet.id;
            }

            // Drain queues occasionally
            if ((i & 0xFF) == 0) {
                drainReferenceQueues();
            }

            // --- Some synchronized and volatile interactions (just to keep things alive) ---
            synchronized (BarrierExercise.class) {
                sinkInt ^= localSum;
                sinkObj = n.payload;
            }

            // Use roots in bulk sometimes to encourage scanning
            if ((i & 0x3FF) == 0) {
                int tmp = 0;
                for (int j = 0; j < ROOT_COUNT; j += 8) {
                    Node r = roots[j];
                    if (r != null) {
                        tmp += r.id;
                        // touch fields
                        if (r.left != null) tmp ^= r.left.id;
                        if (r.right != null) tmp ^= r.right.id;
                    }
                }
                sinkInt ^= tmp;
            }

            // Periodically mess with sharedArray by copying chunks (arraycopy-like)
            if ((i & 0x7FF) == 0) {
                int off = (i >>> 3) & (BIG_ARRAY_SIZE - 64);
                System.arraycopy(sharedArray, off, sharedArray, off + 1, 32);
                Arrays.fill(sharedArray, off, off + 4, n3);
            }
        }

        sinkInt ^= localSum;
        return localSum;
    }

    private static void drainReferenceQueues() {
        // Weak queue
        for (int k = 0; k < 16; k++) {
            WeakReference<? extends Node> wr = (WeakReference<? extends Node>) weakQueue.poll();
            if (wr == null) break;
            // Just touch it to keep barrier traffic
            Node n = wr.get();
            if (n != null) {
                sinkInt ^= n.id;
            }
        }

        // Soft queue
        for (int k = 0; k < 16; k++) {
            SoftReference<? extends Node> sr = (SoftReference<? extends Node>) softQueue.poll();
            if (sr == null) break;
            Node n = sr.get();
            if (n != null) {
                sinkInt ^= n.id;
            }
        }
    }
}

