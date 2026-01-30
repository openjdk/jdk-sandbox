import java.lang.reflect.Field;
import jdk.internal.misc.Unsafe;

public class UnsafeCAS {

    // Unsafe instance and field offset need to be fully initialized in <clinit>
    public static final Unsafe unsafe;
    public static final long f_obj_off;

    // The field we will CAS on
    public volatile Integer f_obj = Integer.valueOf(0);

    static {
        try {
            // Obtain Unsafe via reflection
            Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            unsafe = (Unsafe) theUnsafeField.get(null);

            // Get the offset of the instance field f_obj
            Field fObjField = UnsafeCAS.class.getDeclaredField("f_obj");
            f_obj_off = unsafe.objectFieldOffset(fObjField);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Unsafe and/or field offset", e);
        }
    }

    public static void main(String[] args) {
        new UnsafeCAS().blah();
        System.out.println("Test finished successfully.");
    }

    public void blah() {
        Integer minusOne = Integer.valueOf(-1);

        for (int i = 0; i < 100_000; i++) {
            this.f_obj = minusOne;
            this.test(minusOne, Integer.valueOf(i));
            if (this.f_obj != i) { // auto-unboxing, compares int values
                throw new RuntimeException("bad result! expected " + i + " but found " + this.f_obj);
            }
        }
    }

    public void test(Object expected, Object update) {
        boolean ok = unsafe.compareAndSetReference(this, f_obj_off, expected, update);
        if (!ok) {
            throw new RuntimeException("CAS failed unexpectedly");
        }
    }
}

