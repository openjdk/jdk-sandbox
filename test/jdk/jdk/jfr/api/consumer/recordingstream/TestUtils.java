package jdk.jfr.api.consumer.recordingstream;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestUtils {

    public static final class TestError extends Error {
        private static final long serialVersionUID = 1L;
    }

    public static final class TestException extends Exception {
        private static final long serialVersionUID = 1L;
        private volatile boolean printed;

        @Override
        public void printStackTrace() {
            super.printStackTrace();
            printed = true;
        }

        public boolean isPrinted() {
            return printed;
        }
    }

    // Can throw checked exception as unchecked.
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> void throwUnchecked(Throwable e) throws T {
        throw (T) e;
    }

    public static void installUncaughtException(CountDownLatch receivedError, Throwable expected) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
            if (throwable == expected) {
                System.out.println("Received uncaught exception " + expected.getClass());
                receivedError.countDown();
            }
        });
    }
}
