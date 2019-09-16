package jdk.jfr.internal.consumer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.internal.LongMap;
import jdk.jfr.internal.consumer.ChunkParser.ParserConfiguration;

final class Dispatcher {

    public final static class EventDispatcher {
        final static EventDispatcher[] NO_DISPATCHERS = new EventDispatcher[0];
        final String eventName;
        public final Consumer<RecordedEvent> action;

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

    final Consumer<Throwable>[] errorActions;
    final Runnable[] flushActions;
    final Runnable[] closeActions;
    final EventDispatcher[] dispatchers;
    final LongMap<EventDispatcher[]> dispatcherLookup = new LongMap<>();
    final ParserConfiguration parserConfiguration;
    final Instant startTime;
    final Instant endTime;
    final long startNanos;
    final long endNanos;

    // Cache
    private EventType cacheEventType;
    private EventDispatcher[] cacheDispatchers;

    @SuppressWarnings({"unchecked","rawtypes"})
    public Dispatcher(StreamConfiguration c) {
        this.flushActions = c.flushActions.toArray(new Runnable[0]);
        this.closeActions = c.closeActions.toArray(new Runnable[0]);
        this.errorActions = c.errorActions.toArray(new Consumer[0]);
        this.dispatchers = c.eventActions.toArray(new EventDispatcher[0]);
        this.parserConfiguration = new ParserConfiguration(0, Long.MAX_VALUE, c.reuse, c.ordered, buildFilter(dispatchers));
        this.startTime = c.startTime;
        this.endTime = c.endTime;
        this.startNanos = c.startNanos;
        this.endNanos = c.endNanos;
    }

    private static ParserFilter buildFilter(EventDispatcher[] dispatchers) {
        ParserFilter ef = new ParserFilter();
        for (EventDispatcher ed : dispatchers) {
            String name = ed.eventName;
            if (name == null) {
                return ParserFilter.ACCEPT_ALL;
            }
            ef.setThreshold(name, 0);
        }
        return ef;
    }

    protected final void dispatch(RecordedEvent event) {
        EventType type = event.getEventType();
        EventDispatcher[] dispatchers = null;
        if (type == cacheEventType) {
            dispatchers = cacheDispatchers;
        } else {
            dispatchers = dispatcherLookup.get(type.getId());
            if (dispatchers == null) {
                List<EventDispatcher> list = new ArrayList<>();
                for (EventDispatcher e : this.dispatchers) {
                    if (e.accepts(type)) {
                        list.add(e);
                    }
                }
                dispatchers = list.isEmpty() ? EventDispatcher.NO_DISPATCHERS : list.toArray(new EventDispatcher[0]);
                dispatcherLookup.put(type.getId(), dispatchers);
            }
            cacheDispatchers = dispatchers;
        }
        // Expected behavior if exception occurs in onEvent:
        //
        // Synchronous:
        //  - User has added onError action:
        //     Catch exception, call onError and continue with next event
        //     Let Errors propagate to caller of EventStream::start
        //  - Default action
        //     Catch exception, e.printStackTrace() and continue with next event
        //     Let Errors propagate to caller of EventStream::start
        //
        // Asynchronous
        //  - User has added onError action
        //     Catch exception, call onError and continue with next event
        //     Let Errors propagate, shutdown thread and stream
        //  - Default action
        //    Catch exception, e.printStackTrace() and continue with next event
        //    Let Errors propagate and shutdown thread and stream
        //
        for (int i = 0; i < dispatchers.length; i++) {
            try {
                dispatchers[i].offer(event);
            } catch (Exception e) {
                handleError(e);
            }
        }
    }

    public void handleError(Throwable e) {
        Consumer<?>[] consumers = this.errorActions;
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

    public void runFlushActions() {
        Runnable[] flushActions = this.flushActions;
        for (int i = 0; i < flushActions.length; i++) {
            try {
                flushActions[i].run();
            } catch (Exception e) {
                handleError(e);
            }
        }
    }

    public void runCloseActions() {
        Runnable[] closeActions = this.closeActions;
        for (int i = 0; i < closeActions.length; i++) {
            try {
                closeActions[i].run();
            } catch (Exception e) {
                handleError(e);
            }
        }
    }

    void defaultErrorHandler(Throwable e) {
        e.printStackTrace();
    }
}
