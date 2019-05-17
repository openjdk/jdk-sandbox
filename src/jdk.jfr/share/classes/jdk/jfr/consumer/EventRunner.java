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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.jfr.consumer.EventDirectoryStream.EventConsumer;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;

class EventRunner implements Runnable {
    private final static VarHandle closedHandle;
    private final static VarHandle consumersHandle;
    private final static VarHandle dispatcherHandle;
    private final static VarHandle flushActionsHandle;
    private final static VarHandle closeActionsHandle;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            closedHandle = l.findVarHandle(EventRunner.class, "closed", boolean.class);
            consumersHandle = l.findVarHandle(EventRunner.class, "consumers", EventConsumer[].class);
            dispatcherHandle = l.findVarHandle(EventRunner.class, "dispatcher", LongMap.class);
            flushActionsHandle = l.findVarHandle(EventRunner.class, "flushActions", Runnable[].class);
            closeActionsHandle = l.findVarHandle(EventRunner.class, "closeActions", Runnable[].class);
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }
    // set by VarHandle
    private boolean closed;
    // set by VarHandle
    private EventConsumer[] consumers = new EventConsumer[0];
    // set by VarHandle
    private LongMap<EventConsumer[]> dispatcher = new LongMap<>();
    // set by VarHandle
    private Runnable[] flushActions = new Runnable[0];
    // set by VarHandle
    private Runnable[] closeActions = new Runnable[0];

    private final static JVM jvm = JVM.getJVM();
    private final static EventConsumer[] NO_CONSUMERS = new EventConsumer[0];
    private final AccessControlContext accessControlContext;
    private EventSetLocation location;
    private EventSet eventSet;
    private InternalEventFilter eventFilter = InternalEventFilter.ACCEPT_ALL;
    private int eventSetIndex;
    private int eventArrayIndex;
    private RecordedEvent[] currentEventArray = new RecordedEvent[0];
    private volatile long startNanos;

    public EventRunner(AccessControlContext acc) throws IOException {
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

    private void process() throws Exception, IOException {
        this.location = EventSetLocation.current();
        this.eventSet = location.acquire(startNanos, null); // use timestamp from
        if (eventSet == null) {
            return;
        }
        while (!closed) {
            processSegment();
            Runnable[] fas = this.flushActions;
            for (int i = 0; i < fas.length; i++) {
                fas[i].run();
            }
            do {
                if (closed) {
                    return;
                }
                currentEventArray = eventSet.readEvents(eventSetIndex);
                if (currentEventArray == EventSet.END_OF_SET) {
                    eventSet = eventSet.next(eventFilter);
                    if (eventSet == null || closed) {
                        return;
                    }
                    eventSetIndex = 0;
                    continue;
                }
                if (currentEventArray == null) {
                    return; // no more events
                }
                eventSetIndex++;
            } while (currentEventArray.length == 0);
            eventArrayIndex = 0;
        }
    }

    private void processSegment() {
        while (eventArrayIndex < currentEventArray.length) {
            RecordedEvent e = currentEventArray[eventArrayIndex++];
            if (e == null) {
               return;
            }
            EventConsumer[] consumerDispatch = dispatcher.get(e.getEventType().getId());
            if (consumerDispatch == null) {
                consumerDispatch = NO_CONSUMERS;
                for (EventConsumer ec : consumers.clone()) {
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
    }

    static EventConsumer[] merge(EventConsumer[] current, EventConsumer add) {
        EventConsumer[] array = new EventConsumer[current.length + 1];
        System.arraycopy(current, 0, array, 0, current.length);
        array[current.length] = add;
        return array;
    }

    public void add(EventConsumer e) {
        consumersHandle.setVolatile(this, merge(consumers, e));
        dispatcherHandle.setVolatile(this, new LongMap<>()); // will reset
    }

    private static Runnable[] removeAction(Runnable[] array, Object action)  {
        if (array.length == 0) {
            return null;
        }
        boolean remove = false;
        List<Runnable> list = new ArrayList<>();
        for (int i = 0; i < array.length; i++)  {
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

    private static Runnable[] addAction(Runnable[] array, Runnable action)   {
        ArrayList<Runnable> a = new ArrayList<>();
        a.addAll(Arrays.asList(array));
        a.add(action);
        return a.toArray(new Runnable[0]);
    }

    public boolean remove(Object action) {
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
        List<EventConsumer> list = new ArrayList<>();
        for (int i = 0; i < consumers.length; i++) {
            if (consumers[i].action != action) {
                list.add(consumers[i]);
            } else {
                removeConsumer = true;
                remove = true;
            }
        }
        if (removeConsumer) {
            EventConsumer[] array = list.toArray(new EventConsumer[list.size()]);
            consumersHandle.setVolatile(this, array);
            dispatcherHandle.setVolatile(this, new LongMap<>()); // will reset dispatch
        }
        return remove;
    }

    public void addFlush(Runnable action) {
        flushActionsHandle.setVolatile(this, addAction(flushActions, action));
    }

    public void close() {
        closedHandle.setVolatile(this, true);
        // TODO: Data races here, must fix
        if (eventSet != null) {
            eventSet.release(null);
        }
        if (location != null) {
            location.release();
        }

        Runnable[] cas = this.closeActions;
        for (int i = 0; i < cas.length; i++) {
            cas[i].run();
        }
    }

    public void addCloseAction(Runnable action) {
        closeActionsHandle.setVolatile(this, addAction(closeActions, action));
    }

    public void setStartNanos(long startNanos) {
        this.startNanos = startNanos;
    }
}