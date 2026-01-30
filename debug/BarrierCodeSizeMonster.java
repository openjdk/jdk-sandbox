import java.lang.ref.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class BarrierCodeSizeMonster {

    static final int ROOTS           = 1 << 10;  // 1024
    static final int BIG_ARRAY       = 1 << 11;  // 2048
    static final int MATRIX_DIM      = 48;       // 48x48 = 2304
    static final int SMALL_ARR       = 64;
    static final int MEDIUM_ARR      = 256;
    static final int ITERATIONS      = 50_000;

    // Global roots & structures to keep references alive
    static final Node[] roots = new Node[ROOTS];
    static final Object[] shared1 = new Object[BIG_ARRAY];
    static final Object[] shared2 = new Object[BIG_ARRAY];
    static final Object[][] matrix = new Object[MATRIX_DIM][MATRIX_DIM];
    static final AtomicReference<Node> atomicRootA = new AtomicReference<>();
    static final AtomicReference<Node> atomicRootB = new AtomicReference<>();
    static final AtomicReferenceArray<Object> atomicArray =
            new AtomicReferenceArray<>(BIG_ARRAY);

    static final ReferenceQueue<Node> weakQ = new ReferenceQueue<>();
    static final ReferenceQueue<Node> softQ = new ReferenceQueue<>();
    static final ReferenceQueue<Node> phantomQ = new ReferenceQueue<>();

    static final WeakReference<Node>[] weakSlots = new WeakReference[ROOTS];
    static final SoftReference<Node>[] softSlots = new SoftReference[ROOTS];
    @SuppressWarnings("unchecked")
    static final PhantomReference<Node>[] phantomSlots = new PhantomReference[ROOTS];

    // Volatile sinks to defeat DCE
    static volatile int sinkInt;
    static volatile long sinkLong;
    static volatile Object sinkObj;

    static class Node {
        Node left;
        Node right;
        Node next;
        Node prev;
        Object payload;
        Object altPayload;
        Object[] arrayPayload;
        volatile Object v1;
        volatile Object v2;
        int id;
        long stamp;
        Node(int id) {
            this.id = id;
            this.stamp = System.nanoTime();
        }
    }

    public static void main(String[] args) {
        System.out.println("BarrierCodeSizeMonster starting.");
        System.out.println("Example flags:");
        System.out.println("  -XX:+UseG1GC -XX:-TieredCompilation");
        System.out.println("  -XX:CompileCommand=dontinline,BarrierCodeSizeMonster::hugeBarrierMethod");
        System.out.println("  -XX:+UnlockDiagnosticVMOptions -XX:+PrintAssembly");
        init();
        // Warmup & keep hot
        while (true) {
            hugeBarrierMethod(ITERATIONS);
        }
    }

    private static void init() {
        for (int i = 0; i < ROOTS; i++) {
            Node n = new Node(i);
            n.payload = new Object();
            n.altPayload = new Object[SMALL_ARR];
            n.arrayPayload = new Object[MEDIUM_ARR];
            roots[i] = n;
        }
        for (int i = 0; i < BIG_ARRAY; i++) {
            shared1[i] = new Node(10_000 + i);
            shared2[i] = new Node(20_000 + i);
            atomicArray.set(i, new Node(30_000 + i));
        }
        for (int r = 0; r < MATRIX_DIM; r++) {
            for (int c = 0; c < MATRIX_DIM; c++) {
                matrix[r][c] = new Node((r << 16) ^ c);
            }
        }
    }

    // Single huge method, designed to explode barrier code size
    static int hugeBarrierMethod(int iterations) {
        int localSum = 1;
        int localXor = 0;

        for (int i = 0; i < iterations; i++) {
            int idx   = (i * 37 + 11) & (ROOTS - 1);
            int idx2  = (i * 73 + 19) & (ROOTS - 1);
            int idx3  = (i * 91 + 31) & (ROOTS - 1);
            int aIdx1 = (i * 5  + 7)  & (BIG_ARRAY - 1);
            int aIdx2 = (i * 9  + 13) & (BIG_ARRAY - 1);
            int aIdx3 = (i * 17 + 29) & (BIG_ARRAY - 1);

            // --- SECTION A: allocate graph and store refs everywhere ---
            Node nA = new Node(i);
            Node nB = new Node(i + 1);
            Node nC = new Node(i + 2);
            Node nD = new Node(i + 3);

            nA.left  = roots[idx];
            nA.right = nB;
            nA.next  = nC;
            nA.prev  = nD;

            nB.left  = nC;
            nB.right = roots[idx2];
            nB.next  = nD;
            nB.prev  = nA;

            nC.left  = roots[idx3];
            nC.right = nA;
            nC.next  = nB;
            nC.prev  = nD;

            nD.left  = nB;
            nD.right = nC;
            nD.next  = roots[idx];
            nD.prev  = roots[idx2];

            nA.payload      = new byte[SMALL_ARR];
            nA.altPayload   = new Object[SMALL_ARR];
            nA.arrayPayload = new Object[MEDIUM_ARR];
            nB.payload      = new Object();
            nB.altPayload   = new Object[SMALL_ARR];
            nB.arrayPayload = new Object[SMALL_ARR * 2];
            nC.payload      = new Object();
            nC.altPayload   = roots[idx];
            nC.arrayPayload = new Object[SMALL_ARR];
            nD.payload      = new Object[SMALL_ARR];
            nD.altPayload   = roots[idx2];
            nD.arrayPayload = new Object[SMALL_ARR];

            nA.v1 = nB;
            nA.v2 = new Object();
            nB.v1 = nC;
            nB.v2 = new Object();
            nC.v1 = nD;
            nC.v2 = new Object();
            nD.v1 = roots[idx3];
            nD.v2 = new Object();

            roots[idx]  = nA;
            roots[idx2] = nB;
            roots[idx3] = nC;

            atomicRootA.set(nD);
            atomicRootB.set(nA);

            // --- SECTION B: array stores / loads / System.arraycopy / Arrays.fill ---
            shared1[aIdx1] = nA;
            shared1[aIdx2] = nB;
            shared1[aIdx3] = nC;

            shared2[aIdx1] = nD;
            shared2[aIdx2] = roots[idx];
            shared2[aIdx3] = atomicRootA.get();

            Object la1 = shared1[aIdx1];
            Object la2 = shared1[aIdx2];
            Object la3 = shared1[aIdx3];
            Object lb1 = shared2[aIdx1];
            Object lb2 = shared2[aIdx2];
            Object lb3 = shared2[aIdx3];

            atomicArray.set(aIdx1, la1);
            atomicArray.set(aIdx2, la2);
            atomicArray.set(aIdx3, la3);
            atomicArray.set((aIdx1 + 5) & (BIG_ARRAY - 1), lb1);
            atomicArray.set((aIdx2 + 7) & (BIG_ARRAY - 1), lb2);
            atomicArray.set((aIdx3 + 11) & (BIG_ARRAY - 1), lb3);

            Object aa1 = atomicArray.get(aIdx1);
            Object aa2 = atomicArray.get(aIdx2);
            Object aa3 = atomicArray.get(aIdx3);

            if (aa1 instanceof Node) localSum += ((Node) aa1).id;
            if (aa2 instanceof Node) localXor ^= ((Node) aa2).id;
            if (aa3 instanceof Node) localSum += ((Node) aa3).id;

            if ((i & 0x1FF) == 0) {
                int off = (i >>> 2) & (BIG_ARRAY - 64);
                System.arraycopy(shared1, off, shared2, off + 1, 32);
                System.arraycopy(shared2, off + 8, shared1, off + 16, 16);
                Arrays.fill(shared1, off, off + 8, nA);
                Arrays.fill(shared2, off + 4, off + 12, nB);
            }

            // --- SECTION C: manipulate Node.arrayPayload heavily ---
            if (nA.arrayPayload != null) {
                Object[] ap = nA.arrayPayload;
                for (int k = 0; k < ap.length; k += 3) {
                    ap[k] = roots[(idx + k) & (ROOTS - 1)];
                }
                for (int k = 1; k < ap.length; k += 7) {
                    ap[k] = null;
                }
            }
            if (nB.arrayPayload != null) {
                Object[] bp = nB.arrayPayload;
                for (int k = 0; k < bp.length; k += 5) {
                    bp[k] = shared1[(aIdx1 + k) & (BIG_ARRAY - 1)];
                }
                for (int k = 2; k < bp.length; k += 9) {
                    bp[k] = null;
                }
            }
            if (nC.arrayPayload != null) {
                Object[] cp = nC.arrayPayload;
                for (int k = 0; k < cp.length; k += 4) {
                    cp[k] = shared2[(aIdx2 + k) & (BIG_ARRAY - 1)];
                }
            }
            if (nD.arrayPayload != null) {
                Object[] dp = nD.arrayPayload;
                for (int k = 0; k < dp.length; k += 6) {
                    dp[k] = atomicArray.get((aIdx3 + k) & (BIG_ARRAY - 1));
                }
                for (int k = 3; k < dp.length; k += 10) {
                    dp[k] = null;
                }
            }

            // --- SECTION D: matrix-based reference churn ---
            int row1 = (i * 3 + 5) & (MATRIX_DIM - 1);
            int col1 = (i * 7 + 11) & (MATRIX_DIM - 1);
            int row2 = (i * 13 + 17) & (MATRIX_DIM - 1);
            int col2 = (i * 19 + 23) & (MATRIX_DIM - 1);

            Object m1 = matrix[row1][col1];
            Object m2 = matrix[row2][col2];

            if (m1 instanceof Node) localSum += ((Node) m1).id;
            if (m2 instanceof Node) localXor ^= ((Node) m2).id;

            matrix[row1][col1] = nA;
            matrix[row2][col2] = nB;

            if ((i & 0x3FF) == 0) {
                for (int r = 0; r < MATRIX_DIM; r++) {
                    for (int c = 0; c < MATRIX_DIM; c += 3) {
                        Object tmp = matrix[r][c];
                        int c2 = (c + 1) & (MATRIX_DIM - 1);
                        matrix[r][c]  = matrix[r][c2];
                        matrix[r][c2] = tmp;
                        if ((c & 4) == 0) {
                            matrix[r][c] = null;
                        }
                    }
                }
            }

            // --- SECTION E: weak / soft / phantom references ---
            WeakReference<Node>  wr = new WeakReference<>(nA, weakQ);
            SoftReference<Node>  sr = new SoftReference<>(nB, softQ);
            PhantomReference<Node> pr = new PhantomReference<>(nC, phantomQ);

            weakSlots[idx]   = wr;
            softSlots[idx2]  = sr;
            phantomSlots[idx3] = pr;

            Node wrGet = wr.get();
            Node srGet = sr.get();
            if (wrGet != null) localSum += wrGet.id;
            if (srGet != null) localXor ^= srGet.id;

            if ((i & 0xFF) == 0) {
                // inline "drain queues" in this same method
                for (int k = 0; k < 32; k++) {
                    Reference<? extends Node> r = weakQ.poll();
                    if (r == null) break;
                    Node n = r.get();
                    if (n != null) {
                        sinkInt ^= n.id;
                        sinkLong += n.stamp;
                    }
                }
                for (int k = 0; k < 32; k++) {
                    Reference<? extends Node> r = softQ.poll();
                    if (r == null) break;
                    Node n = r.get();
                    if (n != null) {
                        sinkInt += n.id;
                        sinkLong ^= n.stamp;
                    }
                }
                for (int k = 0; k < 32; k++) {
                    Reference<? extends Node> r = phantomQ.poll();
                    if (r == null) break;
                    sinkLong ^= System.identityHashCode(r);
                }
            }

            // --- SECTION F: walks over roots (lots of loads & field reads) ---
            if ((i & 0x1F) == 0) {
                int tmp = 0;
                for (int r = 0; r < ROOTS; r += 7) {
                    Node n = roots[r];
                    if (n != null) {
                        tmp += n.id;
                        if (n.left  != null) tmp ^= n.left.id;
                        if (n.right != null) tmp += n.right.id;
                        if (n.next  != null) tmp ^= n.next.id;
                        if (n.prev  != null) tmp += n.prev.id;
                        if (n.payload != null) sinkObj = n.payload;
                        if (n.altPayload != null) sinkObj = n.altPayload;
                    }
                }
                sinkInt ^= tmp;
            }

            // --- SECTION G: synchronized & volatile traffic ---
            synchronized (BarrierCodeSizeMonster.class) {
                sinkInt ^= localSum;
                sinkLong += nA.stamp ^ nB.stamp ^ nC.stamp ^ nD.stamp;
                sinkObj = nD;
                nA.v1 = nB;
                nB.v1 = nC;
                nC.v1 = nD;
                nD.v1 = atomicRootB.get();
            }

            if ((i & 0x3F) == 0) {
                int rIdx = (idx + i) & (ROOTS - 1);
                Node target = roots[rIdx];
                if (target != null) {
                    synchronized (target) {
                        target.left  = nC;
                        target.right = nD;
                        target.payload = new Object();
                        target.v1 = new Object();
                        target.v2 = nA;
                    }
                }
            }

            // --- SECTION H: simple switch to create extra blocks & control flow ---
            switch (i & 7) {
                case 0:
                    roots[(idx + 5) & (ROOTS - 1)] = nB;
                    nB.v2 = shared1[aIdx1];
                    break;
                case 1:
                    roots[(idx2 + 7) & (ROOTS - 1)] = nC;
                    nC.v2 = shared2[aIdx2];
                    break;
                case 2:
                    roots[(idx3 + 11) & (ROOTS - 1)] = nD;
                    nD.v2 = atomicArray.get(aIdx3);
                    break;
                case 3:
                    nA.left = roots[(idx + idx2) & (ROOTS - 1)];
                    nA.right = roots[(idx2 + idx3) & (ROOTS - 1)];
                    break;
                case 4:
                    nB.left = roots[(idx3 + idx) & (ROOTS - 1)];
                    nB.right = atomicRootA.get();
                    break;
                case 5:
                    nC.left = atomicRootB.get();
                    nC.right = roots[(idx + idx3) & (ROOTS - 1)];
                    break;
                case 6:
                    nD.left = shared1[aIdx1] instanceof Node ? (Node) shared1[aIdx1] : null;
                    nD.right = shared2[aIdx2] instanceof Node ? (Node) shared2[aIdx2] : null;
                    break;
                case 7:
                    nA.next = nB;
                    nB.next = nC;
                    nC.next = nD;
                    nD.next = roots[idx];
                    break;
            }
        }

        sinkInt ^= localSum;
        sinkInt ^= localXor;
        return localSum ^ localXor;
    }
}

