import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class CASVisibilityRepro {

    static final class State {
        // Payload we "publish"
        int data0, data1, data2, data3;

        // Gate: null means "empty", non-null means "full"
        volatile Object slot;
    }

    static final Object TOKEN = new Object();

    static final VarHandle VH_SLOT;
    static {
        try {
            VH_SLOT = MethodHandles.lookup().findVarHandle(State.class, "slot", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static volatile boolean stop;

    public static void main(String[] args) throws Exception {
        final long iters = (args.length > 0) ? Long.parseLong(args[0]) : 200_000_000L;

        State s = new State();
        s.slot = null;

        Thread producer = new Thread(() -> {
            for (int i = 1; i <= iters && !stop; i++) {
                // Wait until empty
                while ((Object) VH_SLOT.getVolatile(s) != null) {
                    Thread.onSpinWait();
                }

                // Write payload first
                s.data0 = i;
                s.data1 = i;
                s.data2 = i;
                s.data3 = i;

                // Publish by CAS: null -> TOKEN
                // If your CAS path has the wrong release semantics or barriers,
                // the consumer may see TOKEN but stale payload.
                Object old = VH_SLOT.compareAndExchange(s, null, TOKEN);
                if (old != null) {
                    // Rare under race; just retry the iteration
                    i--;
                }
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            long bad = 0;
            for (int expected = 1; expected <= iters && !stop; expected++) {
                // Wait until full
                while ((Object) VH_SLOT.getVolatile(s) == null) {
                    Thread.onSpinWait();
                }

                // Read payload after seeing TOKEN
                int a = s.data0;
                int b = s.data1;
                int c = s.data2;
                int d = s.data3;

                // Consume by CAS: TOKEN -> null
                Object old = VH_SLOT.compareAndExchange(s, TOKEN, null);
                if (old != TOKEN) {
                    // Lost race; retry
                    expected--;
                    continue;
                }

                // The key check:
                // If we saw TOKEN and successfully consumed it,
                // we should *never* observe a payload not equal to expected.
                if (a != expected || b != expected || c != expected || d != expected) {
                    bad++;
                    stop = true;
                    System.err.printf(
                        "BAD! expected=%d saw=[%d,%d,%d,%d] slotOld=%s%n",
                        expected, a, b, c, d, old
                    );
                    return;
                }

                if ((expected & ((1 << 20) - 1)) == 0) {
                    System.out.printf("ok up to %d%n", expected);
                }
            }
            System.out.println("done; no mismatch");
        }, "consumer");

        producer.start();
        consumer.start();

        producer.join();
        consumer.join();
    }
}

