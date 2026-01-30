import java.lang.reflect.Field;

public class ReferenceCAS {

    static final int ITERS = Integer.getInteger("iters", 20000);
    static final int WEAK_ATTEMPTS = Integer.getInteger("weakAttempts", 10);

    static final jdk.internal.misc.Unsafe UNSAFE;
    static final long V_OFFSET;

    static {
        try {
            Field f = jdk.internal.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (jdk.internal.misc.Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Unable to get Unsafe instance.", e);
        }

        try {
            Field vField = ReferenceCAS.class.getDeclaredField("v");
            V_OFFSET = UNSAFE.objectFieldOffset(vField);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    Object v;

    private static void assertEquals(boolean a, boolean b, String msg) {
        if (a != b) {
            throw new RuntimeException("a (" + a + ") != b (" + b + "): " + msg);
        }
    }

    private static void assertEquals(Object a, Object b, String msg) {
        if (!a.equals(b)) {
            throw new RuntimeException("a (" + a.toString() + ") != b (" + b.toString() + "): " + msg);
        }
    }

    public static void main(String[] args) {
        ReferenceCAS t = new ReferenceCAS();
        for (int c = 0; c < ITERS; c++) {
            testAccess(t, V_OFFSET);
        }
    }

    static void testAccess(Object base, long offset) {
        String foo = new String("foo");
        String bar = new String("bar");
        String baz = new String("baz");
        UNSAFE.putReference(base, offset, "foo");
        {
            String newval = bar;
            boolean r = UNSAFE.compareAndSetReference(base, offset, "foo", newval);
            assertEquals(r, true, "success compareAndSet Object");
            assertEquals(newval, "bar", "must not destroy newval");
            Object x = UNSAFE.getReference(base, offset);
            assertEquals(x, "bar", "success compareAndSet Object value");
        }

        {
            String newval = baz;
            boolean r = UNSAFE.compareAndSetReference(base, offset, "foo", newval);
            assertEquals(r, false, "failing compareAndSet Object");
            assertEquals(newval, "baz", "must not destroy newval");
            Object x = UNSAFE.getReference(base, offset);
            assertEquals(x, "bar", "failing compareAndSet Object value");
        }

        UNSAFE.putReference(base, offset, "bar");
        {
            String newval = foo;
            Object r = UNSAFE.compareAndExchangeReference(base, offset, "bar", newval);
            assertEquals(r, "bar", "success compareAndExchange Object");
            assertEquals(newval, "foo", "must not destroy newval");
            Object x = UNSAFE.getReference(base, offset);
            assertEquals(x, "foo", "success compareAndExchange Object value");
        }

        {
            String newval = baz;
            Object r = UNSAFE.compareAndExchangeReference(base, offset, "bar", newval);
            assertEquals(r, "foo", "failing compareAndExchange Object");
            assertEquals(newval, "baz", "must not destroy newval");
            Object x = UNSAFE.getReference(base, offset);
            assertEquals(x, "foo", "failing compareAndExchange Object value");
        }

        UNSAFE.putReference(base, offset, "bar");
        {
            String newval = foo;
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = UNSAFE.weakCompareAndSetReference(base, offset, "bar", newval);
                assertEquals(newval, "foo", "must not destroy newval");
            }
            assertEquals(success, true, "weakCompareAndSet Object");
            Object x = UNSAFE.getReference(base, offset);
            assertEquals(x, "foo", "weakCompareAndSet Object");
        }
    }

}
