import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class VHCompareAndExchangeHammer {

    static final class Cell {
        // Make it volatile to match the typical VarHandle+CAS use pattern.
        volatile Object value;
        Cell(Object v) { this.value = v; }
    }

    // Two distinct objects we toggle between (avoid Integer caching etc.)
    static final Object A = new Object();
    static final Object B = new Object();
    static final Object WRONG = new Object(); // used to force CAS failures

    static final VarHandle VH_VALUE;

    static {
        try {
            VH_VALUE = MethodHandles.lookup().findVarHandle(Cell.class, "value", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void main(String[] args) {
      for (int i=0; i<100; i++) {
        System.out.println("Iteration " + i);
        test();
      }
    }

    public static void test() {
        final int iters = 50_000_000;
        final int allocEvery = 10000;

        Cell c = new Cell(A);

        long ok = 0, fail = 0;
        long hash = 0;

        for (int i = 1; i <= iters; i++) {
            // Read current (volatile read via VarHandle)
            Object cur = (Object) VH_VALUE.getVolatile(c);

            // Toggle expected/new so we alternate between A <-> B.
            Object expected = cur;
            Object newVal = (cur == A) ? B : A;

            // 1) Success path: expected matches
            Object old1 = VH_VALUE.compareAndExchange(c, expected, newVal);
            if (old1 == expected) ok++;
            else fail++; // should be rare in single-thread, but keep accounting

            // 2) Forced failure path: expected is wrong, should not update
            Object old2 = VH_VALUE.compareAndExchange(c, WRONG, expected);
            if (old2 == WRONG) ok++;     // should never happen
            else fail++;                  // should always happen

            // Consume results so JIT can’t trivially delete everything.
            hash ^= System.identityHashCode(old1);
            hash ^= System.identityHashCode(old2);

            // Optional garbage to provoke GC / barriers being “meaningful”
            if (allocEvery > 0 && (i % allocEvery) == 0) {
                // A bit of garbage + a fresh object we store sometimes
                byte[] junk = new byte[1024];
                hash ^= junk[0];
                // occasionally store a fresh object to make the reference change “real”
                if ((i & 1023) == 0) {
                    Object fresh = new Object();
                    Object old3 = VH_VALUE.compareAndExchange(c, (Object) VH_VALUE.getVolatile(c), fresh);
                    hash ^= System.identityHashCode(old3);
                }
            }
        }

        // Final read so the loop has an externally visible effect.
        Object finalVal = (Object) VH_VALUE.getVolatile(c);
        System.out.printf("iters=%d ok=%d fail=%d final=%s hash=%d%n",
                iters, ok, fail,
                (finalVal == A ? "A" : finalVal == B ? "B" : "OTHER"),
                hash);
    }
}

