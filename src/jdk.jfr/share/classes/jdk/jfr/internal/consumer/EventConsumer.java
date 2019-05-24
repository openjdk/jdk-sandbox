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

package jdk.jfr.internal.consumer;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import jdk.jfr.EventType;
import jdk.jfr.consumer.LongMap;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;

abstract public class EventConsumer implements Runnable {

    private final static class EventDispatcher {
        public final static EventDispatcher[] NO_DISPATCHERS = new EventDispatcher[0];

        final private String eventName;
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

    private final static JVM jvm = JVM.getJVM();
    private final static VarHandle closedHandle;
    private final static VarHandle consumersHandle;
    private final static VarHandle dispatcherHandle;
    private final static VarHandle flushActionsHandle;
    private final static VarHandle closeActionsHandle;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            closedHandle = l.findVarHandle(EventConsumer.class, "closed", boolean.class);
            consumersHandle = l.findVarHandle(EventConsumer.class, "consumers", EventDispatcher[].class);
            dispatcherHandle = l.findVarHandle(EventConsumer.class, "dispatcher", LongMap.class);
            flushActionsHandle = l.findVarHandle(EventConsumer.class, "flushActions", Runnable[].class);
            closeActionsHandle = l.findVarHandle(EventConsumer.class, "closeActions", Runnable[].class);
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }
    // set by VarHandle
    private boolean closed;
    // set by VarHandle
    private EventDispatcher[] consumers = new EventDispatcher[0];
    // set by VarHandle
    private LongMap<EventDispatcher[]> dispatcher = new LongMap<>();
    // set by VarHandle
    private Runnable[] flushActions = new Runnable[0];
    // set by VarHandle
    private Runnable[] closeActions = new Runnable[0];

    protected InternalEventFilter eventFilter = InternalEventFilter.ACCEPT_ALL;

    private final AccessControlContext accessControlContext;
    private boolean started;
    private Thread thread;

    protected long startNanos;

    public EventConsumer(AccessControlContext acc) throws IOException {
        this.accessControlContext = acc;
    }

    public void run() {
        doPriviliged(() -> execute());
    }

    void doPriviliged(Runnable r) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                r.run();
                return null;
            }
        }, accessControlContext);
    }

    private void execute() {
        jvm.exclude(Thread.currentThread());
        try {
            process();
        } catch (Throwable e) {
            e.printStackTrace();
            Logger.log(LogTag.JFR_SYSTEM_STREAMING, LogLevel.DEBUG, "Unexpectedexception iterating consumer.");
        } finally {
            Logger.log(LogTag.JFR_SYSTEM_STREAMING, LogLevel.DEBUG, "Execution of stream ended.");
        }
    }

    public abstract void process() throws Exception;

    public synchronized boolean remove(Object action) {
        boolean remove = false;
        Runnable[] updatedFlushActions = removeAction(flushActions, action);
        if (updatedFlushActions != null) {
            flushActionsHandle.setVolatile(this, updatedFlushActions);
            remove = true;
        }
        Runnable[] updatedCloseActions = removeAction(closeActions, action);
        if (updatedCloseActions != null) {
            closeActionsHandle.setVolatile(this, updatedCloseActions);
            remove = true;
        }

        boolean removeConsumer = false;
        List<EventDispatcher> list = new ArrayList<>();
        for (int i = 0; i < consumers.length; i++) {
            if (consumers[i].action != action) {
                list.add(consumers[i]);
            } else {
                removeConsumer = true;
                remove = true;
            }
        }
        if (removeConsumer) {
            EventDispatcher[] array = list.toArray(new EventDispatcher[list.size()]);
            consumersHandle.setVolatile(this, array);
            dispatcherHandle.setVolatile(this, new LongMap<>()); // will reset
                                                                 // dispatch
        }
        return remove;
    }

    public void dispatch(RecordedEvent e) {
        EventDispatcher[] consumerDispatch = dispatcher.get(e.getEventType().getId());
        if (consumerDispatch == null) {
            consumerDispatch = EventDispatcher.NO_DISPATCHERS;
            for (EventDispatcher ec : consumers.clone()) {
                if (ec.accepts(e.getEventType())) {
                    consumerDispatch = merge(consumerDispatch, ec);
                }
            }
            dispatcher.put(e.getEventType().getId(), consumerDispatch);
        }
        for (int i = 0; i < consumerDispatch.length; i++) {
            consumerDispatch[i].offer(e);
        }

    }

    public void onEvent(Consumer<RecordedEvent> action) {
        add(new EventDispatcher(action));
    }

    public void onEvent(String eventName, Consumer<RecordedEvent> action) {
        add(new EventDispatcher(eventName, action));
    }

    private synchronized void add(EventDispatcher e) {
        consumersHandle.setVolatile(this, merge(consumers, e));
        dispatcherHandle.setVolatile(this, new LongMap<>()); // will reset
    }

    public synchronized void onFlush(Runnable action) {
        flushActionsHandle.setVolatile(this, addAction(flushActions, action));
    }

    public synchronized void addCloseAction(Runnable action) {
        closeActionsHandle.setVolatile(this, addAction(closeActions, action));
    }

    public void setClosed(boolean closed) {
        closedHandle.setVolatile(this, closed);
    }

    final public boolean isClosed() {
        return closed;
    }

    public void runCloseActions() {

        Runnable[] cas = this.closeActions;
        for (int i = 0; i < cas.length; i++) {
            cas[i].run();
        }
    }

    public void runFlushActions() {
        Runnable[] fas = this.flushActions;
        for (int i = 0; i < fas.length; i++) {
            fas[i].run();
        }
    }

    public synchronized void startAsync(long startNanos) {
        if (started) {
            throw new IllegalStateException("Event stream can only be started once");
        }
        started = true;
        setStartNanos(startNanos);
        thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }

    public void start(long startNanos) {
        synchronized (this) {
            if (started) {
                throw new IllegalStateException("Event stream can only be started once");
            }
            started = true;
            setStartNanos(startNanos);
        }
        run();
    }

    public void awaitTermination(Duration timeout) {
        Objects.requireNonNull(timeout);
        Thread t = null;
        synchronized (this) {
            t = thread;
        }
        if (t != null && t != Thread.currentThread()) {
            try {
                t.join(timeout.toMillis());
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    public void awaitTermination() {
        awaitTermination(Duration.ofMillis(0));
    }

    private void setStartNanos(long startNanos) {
        this.startNanos = startNanos;
    }

    protected static EventDispatcher[] merge(EventDispatcher[] current, EventDispatcher add) {
        EventDispatcher[] array = new EventDispatcher[current.length + 1];
        System.arraycopy(current, 0, array, 0, current.length);
        array[current.length] = add;
        return array;
    }

    private static Runnable[] removeAction(Runnable[] array, Object action) {
        if (array.length == 0) {
            return null;
        }
        boolean remove = false;
        List<Runnable> list = new ArrayList<>();
        for (int i = 0; i < array.length; i++) {
            if (array[i] != action) {
                list.add(array[i]);
            } else {
                remove = true;
            }
        }
        if (remove) {
            return list.toArray(new Runnable[list.size()]);
        }
        return null;
    }

    private static Runnable[] addAction(Runnable[] array, Runnable action) {
        ArrayList<Runnable> a = new ArrayList<>();
        a.addAll(Arrays.asList(array));
        a.add(action);
        return a.toArray(new Runnable[0]);
    }

}