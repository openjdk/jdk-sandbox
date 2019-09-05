/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.jfr.consumer;

import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import jdk.jfr.EventType;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.SecuritySupport;

/*
 * Purpose of this class is to simplify the implementation of
 * an event stream. In particular, it handles:
 *
 * - configuration storage
 * - atomic updates to a configuration
 * - dispatch mechanism
 * - error handling
 * - security
 *
 */
abstract class AbstractEventStream implements EventStream {

    final static class EventDispatcher {
        final static EventDispatcher[] NO_DISPATCHERS = new EventDispatcher[0];
        final String eventName;
        final Consumer<RecordedEvent> action;

        public EventDispatcher(Consumer<RecordedEvent> action) {
            this(null, action);
        }

        public EventDispatcher(String eventName, Consumer<RecordedEvent> action) {
            this.eventName = eventName;
            this.action = action;
        }

        public void offer(RecordedEvent event) {
            action.accept(event);
        }

        public boolean accepts(EventType eventType) {
            return (eventName == null || eventType.getName().equals(eventName));
        }
    }

    final static Comparator<? super RecordedEvent> END_TIME = (e1, e2) -> Long.compare(e1.endTimeTicks, e2.endTimeTicks);
    private final static AtomicLong counter = new AtomicLong(1);
    private volatile Thread thread;
    private final Object terminated = new Object();
    private final boolean active;
    private final Runnable flushOperation = () -> runFlushActions();
    private final AccessControlContext accessControllerContext;
    private final Object configurationLock = new Object();

    // Modified by updateConfiguration()
    protected volatile StreamConfiguration configuration = new StreamConfiguration();

    public AbstractEventStream(AccessControlContext acc, boolean active) throws IOException {
        this.accessControllerContext = Objects.requireNonNull(acc);
        this.active = active;
    }

    @Override
    abstract public void start();

    @Override
    abstract public void startAsync();

    @Override
    abstract public void close();

    // Purpose of synchronizing the following methods is
    // to serialize changes to the configuration so only one
    // thread at a time can change the configuration.
    //
    // The purpose is not to guard the configuration field. A new
    // configuration is published using updateConfiguration
    //
    @Override
    public final void setOrdered(boolean ordered) {
        synchronized (configurationLock) {
            updateConfiguration(new StreamConfiguration(configuration).setOrdered(ordered));
        }
    }

    @Override
    public final void setReuse(boolean reuse) {
        synchronized (configurationLock) {
            updateConfiguration(new StreamConfiguration(configuration).setReuse(reuse));
        }
    }

    @Override
    public final void setStartTime(Instant startTime) {
        Objects.nonNull(startTime);
        synchronized (configurationLock) {
            if (configuration.isStarted()) {
                throw new IllegalStateException("Stream is already started");
            }
            if (startTime.isBefore(Instant.EPOCH)) {
                startTime = Instant.EPOCH;
            }
            updateConfiguration(new StreamConfiguration(configuration).setStartTime(startTime));
        }
    }

    @Override
    public final void setEndTime(Instant endTime) {
        Objects.requireNonNull(endTime);
        synchronized (configurationLock) {
            if (configuration.isStarted()) {
                throw new IllegalStateException("Stream is already started");
            }
            updateConfiguration(new StreamConfiguration(configuration).setEndTime(endTime));
        }
    }

    @Override
    public final void onEvent(Consumer<RecordedEvent> action) {
        Objects.requireNonNull(action);
        synchronized (configurationLock) {
            add(new EventDispatcher(action));
        }
    }

    @Override
    public final void onEvent(String eventName, Consumer<RecordedEvent> action) {
        Objects.requireNonNull(eventName);
        Objects.requireNonNull(action);
        synchronized (configurationLock) {
            add(new EventDispatcher(eventName, action));
        }
    }

    @Override
    public final void onFlush(Runnable action) {
        Objects.requireNonNull(action);
        synchronized (configurationLock) {
            updateConfiguration(new StreamConfiguration(configuration).addFlushAction(action));
        }
    }

    @Override
    public final void onClose(Runnable action) {
        Objects.requireNonNull(action);
        synchronized (configurationLock) {
            updateConfiguration(new StreamConfiguration(configuration).addCloseAction(action));
        }
    }

    @Override
    public final void onError(Consumer<Throwable> action) {
        Objects.requireNonNull(action);
        synchronized (configurationLock) {
            updateConfiguration(new StreamConfiguration(configuration).addErrorAction(action));
        }
    }

    @Override
    public final boolean remove(Object action) {
        Objects.requireNonNull(action);
        synchronized (configurationLock) {
            return updateConfiguration(new StreamConfiguration(configuration).remove(action));
        }
    }

    @Override
    public final void awaitTermination() throws InterruptedException {
        awaitTermination(Duration.ofMillis(0));
    }

    @Override
    public final void awaitTermination(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout);
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        long base = System.currentTimeMillis();
        long now = 0;

        long millis;
        try {
            millis = Math.multiplyExact(timeout.getSeconds(), 1000);
        } catch (ArithmeticException a) {
            millis = Long.MAX_VALUE;
        }
        int nanos = timeout.toNanosPart();
        if (nanos == 0 && millis == 0) {
            synchronized (terminated) {
                while (!isClosed()) {
                    terminated.wait(0);
                }
            }
        } else {
            while (!isClosed()) {
                long delay = millis - now;
                if (delay <= 0) {
                    break;
                }
                synchronized (terminated) {
                    terminated.wait(delay, nanos);
                }
                now = System.currentTimeMillis() - base;
            }
        }
    }

    protected abstract void process() throws Exception;

    protected final void dispatch(StreamConfiguration c, RecordedEvent event) {
        EventType type = event.getEventType();
        EventDispatcher[] dispatchers = null;
        if (type == c.cacheEventType) {
            dispatchers = c.cacheDispatchers;
        } else {
            dispatchers = c.dispatcherLookup.get(type.getId());
            if (dispatchers == null) {
                List<EventDispatcher> list = new ArrayList<>();
                for (EventDispatcher e : c.getDispatchers()) {
                    if (e.accepts(type)) {
                        list.add(e);
                    }
                }
                dispatchers = list.isEmpty() ? EventDispatcher.NO_DISPATCHERS : list.toArray(new EventDispatcher[0]);
                c.dispatcherLookup.put(type.getId(), dispatchers);
            }
            c.cacheDispatchers = dispatchers;
        }
        for (int i = 0; i < dispatchers.length; i++) {
            try {
                dispatchers[i].offer(event);
            } catch (Exception e) {
                handleError(e);
            }
        }
    }

    protected final void runCloseActions() {
        Runnable[] closeActions = configuration.getCloseActions();
        for (int i = 0; i < closeActions.length; i++) {
            try {
                closeActions[i].run();
            } catch (Exception e) {
                handleError(e);
            }
        }
    }

    protected final void setClosed(boolean closed) {
        synchronized (configurationLock) {
            updateConfiguration(new StreamConfiguration(configuration).setClosed(closed));
        }
    }

    protected final boolean isClosed() {
        return configuration.isClosed();
    }

    protected final void startAsync(long startNanos) {
        startInternal(startNanos);
        Runnable r = () -> run(accessControllerContext);
        thread = SecuritySupport.createThreadWitNoPermissions(nextThreadName(), r);
        thread.start();
    }

    protected final void start(long startNanos) {
        startInternal(startNanos);
        thread = Thread.currentThread();
        run(accessControllerContext);
    }

    protected final Runnable getFlushOperation() {
        return flushOperation;
    }

    private void add(EventDispatcher e) {
        updateConfiguration(new StreamConfiguration(configuration).addDispatcher(e));
    }

    private boolean updateConfiguration(StreamConfiguration newConfiguration) {
        if (!Thread.holdsLock(configurationLock)) {
            throw new InternalError("Modification of configuration without proper lock");
        }
        if (newConfiguration.hasChanged()) {
            // Publish objects held by configuration object
            VarHandle.releaseFence();
            configuration = newConfiguration;
            return true;
        }
        return false;
    }

    private void startInternal(long startNanos) {
        synchronized (configurationLock) {
            if (configuration.isStarted()) {
                throw new IllegalStateException("Event stream can only be started once");
            }
            StreamConfiguration c = new StreamConfiguration(configuration);
            if (active) {
                c.setStartNanos(startNanos);
            }
            c.setStarted(true);
            updateConfiguration(c);
        }
    }

    private void execute() {
        JVM.getJVM().exclude(Thread.currentThread());
        try {
            process();
        } catch (Exception e) {
            defaultErrorHandler(e);
        } finally {
            Logger.log(LogTag.JFR_SYSTEM_STREAMING, LogLevel.DEBUG, "Execution of stream ended.");
            try {
                close();
            } finally {
                synchronized (terminated) {
                    terminated.notifyAll();
                }
            }
        }
    }

    private void handleError(Throwable e) {
        Consumer<?>[] consumers = configuration.errorActions;
        if (consumers.length == 0) {
            defaultErrorHandler(e);
            return;
        }
        for (int i = 0; i < consumers.length; i++) {
            @SuppressWarnings("unchecked")
            Consumer<Throwable> conusmer = (Consumer<Throwable>) consumers[i];
            conusmer.accept(e);
        }
    }

    private void runFlushActions() {
        Runnable[] flushActions = configuration.getFlushActions();
        for (int i = 0; i < flushActions.length; i++) {
            try {
                flushActions[i].run();
            } catch (Exception e) {
                handleError(e);
            }
        }
    }

    private void run(AccessControlContext accessControlContext) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                execute();
                return null;
            }
        }, accessControlContext);
    }

    private String nextThreadName() {
        counter.incrementAndGet();
        return "JFR Event Stream " + counter;
    }

    private void defaultErrorHandler(Throwable e) {
        e.printStackTrace();
    }
}