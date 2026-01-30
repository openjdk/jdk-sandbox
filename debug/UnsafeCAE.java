import java.lang.reflect.Field;
import jdk.internal.misc.Unsafe;

public class UnsafeCAE {

    public static final Unsafe unsafe;
    public static final long f_obj_off;

    public volatile Integer f_obj = Integer.valueOf(0);

    static {
        try {
            Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            unsafe = (Unsafe) theUnsafeField.get(null);

            Field fObjField = UnsafeCAE.class.getDeclaredField("f_obj");
            f_obj_off = unsafe.objectFieldOffset(fObjField);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Unsafe and/or field offset", e);
        }
    }

    public static void main(String[] args) {
        new UnsafeCAE().blah();
        System.out.println("Test finished successfully.");
    }

    public void blah() {
        Integer minusOne = Integer.valueOf(-1);

        for (int i = 0; i < 100_000; i++) {
            this.f_obj = minusOne;
            this.test(minusOne, Integer.valueOf(i));
            if (this.f_obj != i) {
                throw new RuntimeException("bad result! expected " + i +
                                           " but found " + this.f_obj);
            }
        }
    }

    public void test(Object expected, Object update) {
        Object witness = unsafe.compareAndExchangeReference(this, f_obj_off, expected, update);
        if (witness != expected) {
            throw new RuntimeException("CAE failed unexpectedly, saw " + witness);
        }
    }
}

