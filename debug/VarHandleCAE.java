import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class VarHandleCAE {

    // VarHandle to the instance field f_obj
    private static final VarHandle F_OBJ_HANDLE;

    // The field we will CAE on
    public volatile Integer f_obj = Integer.valueOf(0);

    static {
        try {
            F_OBJ_HANDLE = MethodHandles.lookup()
                    .findVarHandle(VarHandleCAE.class, "f_obj", Integer.class);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to initialize VarHandle", e);
        }
    }

    public static void main(String[] args) {
        new VarHandleCAE().blah();
        System.out.println("VarHandle compareAndExchange test finished successfully.");
    }

    public void blah() {
        Integer minusOne = Integer.valueOf(-1);

        for (int i = 0; i < 100_000; i++) {
            // Set baseline value
            this.f_obj = minusOne;

            // Try to CAS from minusOne -> i
            this.test(minusOne, Integer.valueOf(i));

            // Validate that field now holds the expected int value
            if (this.f_obj != i) { // auto-unboxing, compares int values
                throw new RuntimeException(
                        "bad result! expected " + i + " but found " + this.f_obj);
            }
        }
    }

    public void test(Integer expected, Integer update) {
        // VarHandle.compareAndExchange has full volatile semantics
        Integer witness = (Integer) F_OBJ_HANDLE.compareAndExchange(this, expected, update);

        // Success if we saw exactly the expected reference
        if (witness != expected) {
            throw new RuntimeException(
                    "compareAndExchange failed unexpectedly, saw " + witness + " instead of " + expected);
        }
    }
}

