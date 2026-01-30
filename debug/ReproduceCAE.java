import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class GetStackTraceSuspendedStressTest {
    static final int MSG_COUNT = 1000;
    static final int VTHREAD_COUNT = 100;
    static final SynchronousQueue<String> QUEUE = new SynchronousQueue<>();

    static void producer(String msg) throws InterruptedException {
        int ii = 1;
        long ll = 2*(long)ii;
        float ff = ll + 1.2f;
        double dd = ff + 1.3D;
        msg += dd;
        QUEUE.put(msg);
    }

    static void producer() {
        try {
            for (int i = 0; i < MSG_COUNT; i++) {
                producer("msg: ");
            }
        } catch (InterruptedException e) { }
    }

    static void consumer() {
        try {
            for (int i = 0; i < MSG_COUNT; i++) {
                String s = QUEUE.take();
            }
        } catch (InterruptedException e) { }
    }

    static String threadName() {
        return Thread.currentThread().getName();
    }

    static final Runnable PRODUCER = () -> {
        String name = threadName();

        System.out.println(name + ": started");
        producer();
        System.out.println(name + ": finished");
    };

    static final Runnable CONSUMER = () -> {
        String name = threadName();

        System.out.println(name + ": started");
        consumer();
        System.out.println(name + ": finished");
    };

    public static void test() throws Exception {
        for (int i = 0; i < VTHREAD_COUNT; i++) {
            Thread t0 = new Thread(PRODUCER, "Prod-" + i);
            Thread t1 = new Thread(CONSUMER, "Cons-" + i);

            t0.start();
            t1.start();

            t0.join();
            t1.join();
        }
    }

    public static void main(String[] args) throws Exception {
      test();
    }
}
