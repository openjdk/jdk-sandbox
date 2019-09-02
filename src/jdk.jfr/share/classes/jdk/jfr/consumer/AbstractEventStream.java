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
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.LongMap;
import jdk.jfr.internal.SecuritySupport;
import jdk.jfr.internal.Utils;
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
abstract class AbstractEventStream implements EventStream {

    protected static final class StreamConfiguration {
        private static final Runnable[] NO_ACTIONS = new Runnable[0];

        private Consumer<?>[] errorActions = new Consumer<?>[0];
        private Runnable[] flushActions = NO_ACTIONS;
        private Runnable[] closeActions = NO_ACTIONS;
        private EventDispatcher[] dispatchers = EventDispatcher.NO_DISPATCHERS;
        private InternalEventFilter eventFilter = InternalEventFilter.ACCEPT_ALL;
        private LongMap<EventDispatcher[]> dispatcherLookup = new LongMap<>();

        private boolean changedConfiguration = false;
        private boolean closed = false;
        private boolean reuse = true;
        private boolean ordered = true;
        private Instant startTime = null;
        private Instant endTime = null;
        private boolean started = false;
        private long startNanos = 0;
        private long endNanos = Long.MAX_VALUE;

        public StreamConfiguration(StreamConfiguration configuration) {
            this.flushActions = configuration.flushActions;
            this.closeActions = configuration.closeActions;
            this.errorActions = configuration.errorActions;
            this.dispatchers = configuration.dispatchers;
            this.eventFilter = configuration.eventFilter;
            this.closed = configuration.closed;
            this.reuse = configuration.reuse;
            this.ordered = configuration.ordered;
            this.startTime = configuration.startTime;
            this.endTime = configuration.endTime;
            this.started = configuration.started;
            this.startNanos = configuration.startNanos;
            this.endNanos = configuration.endNanos;
            this.dispatcherLookup = configuration.dispatcherLookup;
        }

        public StreamConfiguration() {
        }

        public StreamConfiguration remove(Object action) {
            flushActions = remove(flushActions, action);
            closeActions = remove(closeActions, action);
            dispatchers = removeDispatch(dispatchers, action);
            return this;
        }

        public StreamConfiguration addDispatcher(EventDispatcher e) {
            dispatchers = add(dispatchers, e);
            eventFilter = buildFilter(dispatchers);
            dispatcherLookup = new LongMap<>();
            return this;
        }

        public StreamConfiguration addFlushAction(Runnable action) {
            flushActions = add(flushActions, action);
            return this;
        }

        public StreamConfiguration addCloseAction(Runnable action) {
            closeActions = add(closeActions, action);
            return this;
        }

        public StreamConfiguration addErrorAction(Consumer<Throwable> action) {
            errorActions = add(errorActions, action);
            return this;
        }

        public StreamConfiguration setClosed(boolean closed) {
            this.closed = closed;
            changedConfiguration = true;
            return this;
        }

        public boolean isClosed() {
            return closed;
        }

        public Runnable[] getCloseActions() {
            return closeActions;
        }

        public Runnable[] getFlushActions() {
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
                changedConfiguration = true;
            }
            return result;
        }

        private <T> T[] remove(T[] array, Object action) {
            List<T> list = new ArrayList<>(array.length);
            for (int i = 0; i < array.length; i++) {
                if (array[i] != action) {
                    list.add(array[i]);
                } else {
                    changedConfiguration = true;
                }
            }
            return list.toArray(array);
        }

        private <T> T[] add(T[] array, T object) {
            List<T> list = new ArrayList<>(Arrays.asList(array));
            list.add(object);
            changedConfiguration = true;
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
            return ef;
        }

        public StreamConfiguration setReuse(boolean reuse) {
            this.reuse = reuse;
            changedConfiguration = true;
            return this;
        }

        public StreamConfiguration setOrdered(boolean ordered) {
            this.ordered = ordered;
            changedConfiguration = true;
            return this;
        }

        public StreamConfiguration setEndTime(Instant endTime) {
            this.endTime = endTime;
            this.endNanos = Utils.timeToNanos(endTime);
            changedConfiguration = true;
            return this;
        }

        public StreamConfiguration setStartTime(Instant startTime) {
            this.startTime = startTime;
            this.startNanos = Utils.timeToNanos(startTime);
            changedConfiguration = true;
            return this;
        }

        public Instant getStartTime() {
            return startTime;
        }

        public Object getEndTime() {
            return endTime;
        }

        public boolean isStarted() {
            return started;
        }

        public StreamConfiguration setStartNanos(long startNanos) {
            this.startNanos = startNanos;
            changedConfiguration = true;
            return this;
        }

        public void setStarted(boolean started) {
            this.started = started;
            changedConfiguration = true;
        }

        public boolean hasChanged() {
            return changedConfiguration;
        }

        public boolean getReuse() {
            return reuse;
        }

        public boolean getOrdered() {
            return ordered;
        }

        public InternalEventFilter getFiler() {
            return eventFilter;
        }

        public long getStartNanos() {
            return startNanos;
        }

        public long getEndNanos() {
            return endNanos;
        }

        public InternalEventFilter getFilter() {
            return eventFilter;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Runnable flush : flushActions) {
                sb.append("Flush Action: ").append(flush).append("\n");
            }
            for (Runnable close : closeActions) {
                sb.append("Close Action: " + close + "\n");
            }
            for (Consumer<?> error : errorActions) {
                sb.append("Error Action: " + error + "\n");
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
            sb.append("End Time: ").append(endTime).append("\n");
            sb.append("End Nanos: ").append(endNanos).append("\n");
            return sb.toString();
        }

        private EventDispatcher[] getDispatchers() {
            return dispatchers;
        }
    }

    private final static class EventDispatcher {
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

    final static Comparator<? super RecordedEvent> END_TIME = (e1, e2) -> Long.compare(e1.endTimeTicks, e2.endTimeTicks);

    private final Thread thread;
    private final boolean active;
    private final Runnable flushOperation = () -> runFlushActions();
    private final AccessControlContext accessControllerContext;
    private final Object configurationLock = new Object();

    // Modified by updateConfiguration()
    protected volatile StreamConfiguration configuration = new StreamConfiguration();

    // Cache the last event type and dispatch.
    private EventType lastEventType;
    private EventDispatcher[] lastEventDispatch;

    public AbstractEventStream(AccessControlContext acc, boolean active) throws IOException {
        this.accessControllerContext = Objects.requireNonNull(acc);
        this.active = active;
        this.thread = SecuritySupport.createThreadWitNoPermissions("JFR Event Streaming", () -> run(acc));
    }

    @Override
    abstract public void start();

    @Override
    abstract public void startAsync();

    @Override
    abstract public void close();

    // Purpose of synchronizing the following methods is
    // to serialize changes to the configuration, so only one
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
    public final void awaitTermination() {
        awaitTermination(Duration.ofMillis(0));
    }

    @Override
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

    protected abstract void process() throws Exception;

    protected final void clearLastDispatch() {
        lastEventDispatch = null;
        lastEventType = null;
    }

    protected final void dispatch(RecordedEvent event) {
        EventType type = event.getEventType();
        EventDispatcher[] dispatchers = null;
        if (type == lastEventType) {
            dispatchers = lastEventDispatch;
        } else {
            dispatchers = configuration.dispatcherLookup.get(type.getId());
            if (dispatchers == null) {
                List<EventDispatcher> list = new ArrayList<>();
                for (EventDispatcher e : configuration.getDispatchers()) {
                    if (e.accepts(type)) {
                        list.add(e);
                    }
                }
                dispatchers = list.isEmpty() ? EventDispatcher.NO_DISPATCHERS : list.toArray(new EventDispatcher[0]);
                configuration.dispatcherLookup.put(type.getId(), dispatchers);
            }
            lastEventDispatch = dispatchers;
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
        thread.start();
    }

    protected final void start(long startNanos) {
        startInternal(startNanos);
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
            Consumer<Throwable> c = (Consumer<Throwable>) consumers[i];
            c.accept(e);
        }
    }

    private void defaultErrorHandler(Throwable e) {
        e.printStackTrace();
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

    private void run(AccessControlContext acc) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                execute();
                return null;
            }
        }, acc);
    }
}