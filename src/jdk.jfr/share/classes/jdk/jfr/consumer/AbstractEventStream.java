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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import jdk.jfr.EventType;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.LongMap;
import jdk.jfr.internal.consumer.InternalEventFilter;

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
abstract class AbstractEventStream implements Runnable {

    public static final class StreamConfiguration {
        private static final Runnable[] NO_ACTIONS = new Runnable[0];

        private Runnable[] flushActions = NO_ACTIONS;
        private Runnable[] closeActions = NO_ACTIONS;
        private EventDispatcher[] dispatchers = NO_DISPATCHERS;
        private InternalEventFilter eventFilter = InternalEventFilter.ACCEPT_ALL;
        private boolean closed = false;
        private boolean reuse = true;
        private boolean ordered = true;
        private Instant startTime = null;
        private boolean started = false;
        private long startNanos = 0;
        private LongMap<EventDispatcher[]> dispatcherLookup = new LongMap<>();

        private boolean changed = false;

        public StreamConfiguration(StreamConfiguration configuration) {
            this.flushActions = configuration.flushActions;
            this.closeActions = configuration.closeActions;
            this.dispatchers = configuration.dispatchers;
            this.eventFilter = configuration.eventFilter;
            this.closed = configuration.closed;
            this.reuse = configuration.reuse;
            this.ordered = configuration.ordered;
            this.startTime = configuration.startTime;
            this.started = configuration.started;
            this.startNanos = configuration.startNanos;
            this.dispatcherLookup = configuration.dispatcherLookup;
        }

        public StreamConfiguration() {
        }

        final public StreamConfiguration remove(Object action) {
            flushActions = remove(flushActions, action);
            closeActions = remove(closeActions, action);
            dispatchers = removeDispatch(dispatchers, action);
            return this;
        }

        final public StreamConfiguration addDispatcher(EventDispatcher e) {
            dispatchers = add(dispatchers, e);
            eventFilter = buildFilter(dispatchers);
            dispatcherLookup = new LongMap<>();
            return this;
        }

        final public StreamConfiguration addFlushAction(Runnable action) {
            flushActions = add(flushActions, action);
            return this;
        }

        final public StreamConfiguration addCloseAction(Runnable action) {
            closeActions = add(closeActions, action);
            return this;
        }

        final public StreamConfiguration setClosed(boolean closed) {
            this.closed = closed;
            changed = true;
            return this;
        }

        final public boolean isClosed() {
            return closed;
        }

        final public Runnable[] getCloseActions() {
            return closeActions;
        }

        final public Runnable[] getFlushActions() {
            return flushActions;
        }

        private EventDispatcher[] removeDispatch(EventDispatcher[] array, Object action) {
            List<EventDispatcher> list = new ArrayList<>(array.length);
            boolean modified = false;
            for (int i = 0; i < array.length; i++) {
                if (array[i].action != action) {
                    list.add(array[i]);
                } else {
                    modified = true;
                }
            }
            EventDispatcher[] result = list.toArray(new EventDispatcher[0]);
            if (modified) {
                eventFilter = buildFilter(result);
                dispatcherLookup = new LongMap<>();
                changed = true;
            }
            return result;
        }

        private <T> T[] remove(T[] array, Object action) {
            List<T> list = new ArrayList<>(array.length);
            for (int i = 0; i < array.length; i++) {
                if (array[i] != action) {
                    list.add(array[i]);
                } else {
                    changed = true;
                }
            }
            return list.toArray(array);
        }

        private <T> T[] add(T[] array, T object) {
            List<T> list = new ArrayList<>(Arrays.asList(array));
            list.add(object);
            changed = true;
            return list.toArray(array);
        }

        private static InternalEventFilter buildFilter(EventDispatcher[] dispatchers) {
            InternalEventFilter ef = new InternalEventFilter();
            for (EventDispatcher ed : dispatchers) {
                String name = ed.eventName;
                if (name == null) {
                    return InternalEventFilter.ACCEPT_ALL;
                }
                ef.setThreshold(name, 0);
            }
            return ef.threadSafe();
        }

        final public StreamConfiguration setReuse(boolean reuse) {
            this.reuse = reuse;
            changed = true;
            return this;
        }

        final public StreamConfiguration setOrdered(boolean ordered) {
            this.ordered = ordered;
            changed = true;
            return this;
        }

        final public StreamConfiguration setStartTime(Instant startTime) {
            this.startTime = startTime;
            changed = true;
            return this;
        }

        final public Instant getStartTime() {
            return startTime;
        }

        final public boolean isStarted() {
            return started;
        }

        final public StreamConfiguration setStartNanos(long startNanos) {
            this.startNanos = startNanos;
            changed = true;
            return this;
        }

        final public void setStarted(boolean started) {
            this.started = started;
            changed = true;
        }

        final public boolean hasChanged() {
            return changed;
        }

        final public boolean getReuse() {
            return reuse;
        }

        final public boolean getOrdered() {
            return ordered;
        }

        final public InternalEventFilter getFiler() {
            return eventFilter;
        }

        final public long getStartNanos() {
            return startNanos;
        }

        final public InternalEventFilter getFilter() {
            return eventFilter;
        }

        final public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Runnable flush : flushActions) {
                sb.append("Flush Action: ").append(flush).append("\n");
            }
            for (Runnable close : closeActions) {
                sb.append("Close Action: " + close + "\n");
            }
            for (EventDispatcher dispatcher : dispatchers) {
                sb.append("Dispatch Action: " + dispatcher.eventName + "(" + dispatcher + ") \n");
            }
            sb.append("Closed: ").append(closed).append("\n");
            sb.append("Reuse: ").append(reuse).append("\n");
            sb.append("Ordered: ").append(ordered).append("\n");
            sb.append("Started: ").append(started).append("\n");
            sb.append("Start Time: ").append(startTime).append("\n");
            sb.append("Start Nanos: ").append(startNanos).append("\n");
            return sb.toString();
        }

        private EventDispatcher[] getDispatchers() {
            return dispatchers;
        }
    }

    final static class EventDispatcher {
        final static EventDispatcher[] NO_DISPATCHERS = new EventDispatcher[0];

        final private String eventName;
        final private Consumer<RecordedEvent> action;

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

    public final static Instant NEXT_EVENT = Instant.now();
    public final static Comparator<? super RecordedEvent> END_TIME = (e1, e2) -> Long.compare(e1.endTimeTicks, e2.endTimeTicks);

    private final static EventDispatcher[] NO_DISPATCHERS = new EventDispatcher[0];
    private final AccessControlContext accessControlContext;
    private final Thread thread;

    // Update bu updateConfiguration()
    protected StreamConfiguration configuration = new StreamConfiguration();

    // Cache the last event type and dispatch.
    private EventType lastEventType;
    private EventDispatcher[] lastEventDispatch;

    public AbstractEventStream(AccessControlContext acc) throws IOException {
        this.accessControlContext = acc;
        // Create thread object in constructor to ensure caller has permission
        // permission before constructing object
        thread = new Thread(this);
        thread.setDaemon(true);
    }

    public final void run() {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                execute();
                return null;
            }
        }, accessControlContext);

    }

    private void execute() {
        // JVM.getJVM().exclude(Thread.currentThread());
        try {
            updateStartNanos();
            process();
        } catch (IOException e) {
            if (!isClosed()) {
                logException(e);
            }
        } catch (Exception e) {
            logException(e);
        } finally {
            Logger.log(LogTag.JFR_SYSTEM_STREAMING, LogLevel.DEBUG, "Execution of stream ended.");
        }
    }

    // User setting overrides default
    private void updateStartNanos() {
        if (configuration.getStartTime() != null) {
            StreamConfiguration c = new StreamConfiguration(configuration);
            try {
                c.setStartNanos(c.getStartTime().toEpochMilli() * 1_000_000L);
            } catch (ArithmeticException ae) {
                c.setStartNanos(Long.MAX_VALUE);
            }
            updateConfiguration(c);
        }
    }

    private void logException(Exception e) {
        // FIXME: e.printStackTrace(); for debugging purposes,
        // remove before before integration
        e.printStackTrace();
        Logger.log(LogTag.JFR_SYSTEM_STREAMING, LogLevel.DEBUG, "Unexpected error processing stream. " + e.getMessage());
    }

    public abstract void process() throws IOException;

    protected final void clearLastDispatch() {
        lastEventDispatch = null;
        lastEventType = null;
    }

    protected final void dispatch(RecordedEvent event) {
        EventType type = event.getEventType();
        EventDispatcher[] ret = null;
        if (type == lastEventType) {
            ret = lastEventDispatch;
        } else {
            ret = configuration.dispatcherLookup.get(type.getId());
            if (ret == null) {
                List<EventDispatcher> list = new ArrayList<>();
                for (EventDispatcher e : configuration.getDispatchers()) {
                    if (e.accepts(type)) {
                        list.add(e);
                    }
                }
                ret = list.isEmpty() ? NO_DISPATCHERS : list.toArray(new EventDispatcher[0]);
                configuration.dispatcherLookup.put(type.getId(), ret);
            }
            lastEventDispatch = ret;
        }
        for (int i = 0; i < ret.length; i++) {
            try {
                ret[i].offer(event);
            } catch (Exception e) {
                logException(e);
            }
        }
    }

    public final void runCloseActions() {
        Runnable[] cas = configuration.getCloseActions();
        for (int i = 0; i < cas.length; i++) {
            try {
                cas[i].run();
            } catch (Exception e) {
                logException(e);
            }
        }
    }

    public final void runFlushActions() {
        Runnable[] fas = configuration.getFlushActions();
        for (int i = 0; i < fas.length; i++) {
            try {
                fas[i].run();
            } catch (Exception e) {
                logException(e);
            }
        }
    }

    // Purpose of synchronizing the following methods is
    // to serialize changes to the configuration, so only one
    // thread at a time can change the configuration.
    //
    // The purpose is not to guard the configuration field. A new
    // configuration is published using updateConfiguration
    //
    public final synchronized boolean remove(Object action) {
        return updateConfiguration(new StreamConfiguration(configuration).remove(action));
    }

    public final synchronized void onEvent(Consumer<RecordedEvent> action) {
        add(new EventDispatcher(action));
    }

    public final synchronized void onEvent(String eventName, Consumer<RecordedEvent> action) {
        add(new EventDispatcher(eventName, action));
    }

    private final synchronized void add(EventDispatcher e) {
        updateConfiguration(new StreamConfiguration(configuration).addDispatcher(e));
    }

    public final synchronized void onFlush(Runnable action) {
        updateConfiguration(new StreamConfiguration(configuration).addFlushAction(action));
    }

    public final synchronized void addCloseAction(Runnable action) {
        updateConfiguration(new StreamConfiguration(configuration).addCloseAction(action));
    }

    public final synchronized void setClosed(boolean closed) {
        updateConfiguration(new StreamConfiguration(configuration).setClosed(closed));
    }

    public final synchronized void setReuse(boolean reuse) {
        updateConfiguration(new StreamConfiguration(configuration).setReuse(reuse));
    }

    public final synchronized void setOrdered(boolean ordered) {
        updateConfiguration(new StreamConfiguration(configuration).setOrdered(ordered));
    }

    public final synchronized void setStartNanos(long startNanos) {
        updateConfiguration(new StreamConfiguration(configuration).setStartNanos(startNanos));
    }

    public final synchronized void setStartTime(Instant startTime) {
        Objects.nonNull(startTime);
        if (configuration.isStarted()) {
            throw new IllegalStateException("Stream is already started");
        }
        if (startTime == null) {
            return;
        }
        if (startTime.isBefore(Instant.EPOCH)) {
            startTime = Instant.EPOCH;
        }
        updateConfiguration(new StreamConfiguration(configuration).setStartTime(startTime));
    }

    private boolean updateConfiguration(StreamConfiguration newConfiguration) {
        // Changes to the configuration must be serialized, so make
        // sure that we have the monitor
        Thread.holdsLock(this);
        if (newConfiguration.hasChanged()) {
            // Publish objects indirectly held by new configuration object
            VarHandle.releaseFence();
            configuration = newConfiguration;
            // Publish the field reference. Making the field volatile
            // would be an alternative, but it is repeatedly read.
            VarHandle.releaseFence();
            return true;
        }
        return false;
    }

    public final boolean isClosed() {
        return configuration.isClosed();
    }

    public final void startAsync(long startNanos) {
        synchronized (this) {
            if (configuration.isStarted()) {
                throw new IllegalStateException("Event stream can only be started once");
            }
            StreamConfiguration c = new StreamConfiguration(configuration);
            c.setStartNanos(startNanos);
            c.setStarted(true);
            updateConfiguration(c);
        }
        thread.start();
    }

    public final void start(long startNanos) {
        synchronized (this) {
            if (configuration.isStarted()) {
                throw new IllegalStateException("Event stream can only be started once");
            }
            StreamConfiguration c = new StreamConfiguration(configuration);
            c.setStartNanos(startNanos);
            c.setStarted(true);
            updateConfiguration(c);
        }
        run();
    }

    public final void awaitTermination(Duration timeout) {
        Objects.requireNonNull(timeout);
        if (thread != Thread.currentThread()) {
            try {
                thread.join(timeout.toMillis());
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    public final void awaitTermination() {
        awaitTermination(Duration.ofMillis(0));
    }

    abstract public void close();
}