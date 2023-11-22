package io.bellsoft.hotcode.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleThreadFactory implements ThreadFactory {
    private final String baseName;
    private final boolean daemon;
    private final AtomicInteger threadNum = new AtomicInteger();
    public SimpleThreadFactory(String baseName, boolean daemon) {
        if (baseName == null) {
            throw new IllegalArgumentException();
        }
        this.baseName = baseName;
        this.daemon = daemon;
    }
    public Thread newThread(Runnable r) {
        var t = new Thread(r, baseName + "-" + threadNum.incrementAndGet());
        t.setDaemon(daemon);
        return t;
    }
}
