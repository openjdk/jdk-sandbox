package jdk.jfr.consumer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import jdk.jfr.EventType;
import jdk.jfr.consumer.AbstractEventStream.EventDispatcher;
import jdk.jfr.internal.LongMap;
import jdk.jfr.internal.Utils;
import jdk.jfr.internal.consumer.InternalEventFilter;

final class StreamConfiguration {
    private static final Runnable[] NO_ACTIONS = new Runnable[0];

    Consumer<?>[] errorActions = new Consumer<?>[0];
    private Runnable[] flushActions = NO_ACTIONS;
    private Runnable[] closeActions = NO_ACTIONS;
    private EventDispatcher[] dispatchers = EventDispatcher.NO_DISPATCHERS;
    private InternalEventFilter eventFilter = InternalEventFilter.ACCEPT_ALL;
    LongMap<EventDispatcher[]> dispatcherLookup = new LongMap<>();

    private boolean changedConfiguration = false;
    private boolean closed = false;
    private boolean reuse = true;
    private boolean ordered = true;
    private Instant startTime = null;
    private Instant endTime = null;
    private boolean started = false;
    private long startNanos = 0;
    private long endNanos = Long.MAX_VALUE;

    // Cache the last event type and dispatch.
    EventType cacheEventType;
    EventDispatcher[] cacheDispatchers;


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
        errorActions = remove(errorActions, action);
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

    public void clearDispatchCache() {
        cacheDispatchers = null;
        cacheEventType = null;
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

    EventDispatcher[] getDispatchers() {
        return dispatchers;
    }
}