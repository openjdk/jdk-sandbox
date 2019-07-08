/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jfr.internal.EventInstrumentation.FIELD_DURATION;

import java.io.IOException;
import java.util.List;

import jdk.jfr.EventType;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.internal.consumer.Parser;
import jdk.jfr.internal.consumer.RecordingInput;

/**
 * Parses an event and returns a {@link RecordedEvent}.
 *
 */
final class EventParser extends Parser {
    private final Parser[] parsers;
    private final EventType eventType;
    private final TimeConverter timeConverter;
    private final boolean hasDuration;
    private final List<ValueDescriptor> valueDescriptors;
    private final int startIndex;
    private final int length;
    private final RecordedEvent unorderedEvent;
    private boolean enabled = true;
    private RecordedEvent[] eventCache;
    private int index;
    private boolean ordered;
    private long firstNanos;
    private long thresholdNanos = -1;
    private ObjectContext objectContext;

    EventParser(TimeConverter timeConverter, EventType type, Parser[] parsers) {
        this.timeConverter = timeConverter;
        this.parsers = parsers;
        this.eventType = type;
        this.hasDuration = type.getField(FIELD_DURATION) != null;
        this.startIndex = hasDuration ? 2 : 1;
        this.length = parsers.length - startIndex;
        this.valueDescriptors = type.getFields();
        this.objectContext = new ObjectContext(type, valueDescriptors, timeConverter);
        this.unorderedEvent = new RecordedEvent(objectContext, new Object[length], 0L, 0L);
    }

    private RecordedEvent cachedEvent() {
        if (ordered) {
            if (index == eventCache.length) {
                RecordedEvent[] cache = eventCache;
                eventCache = new RecordedEvent[eventCache.length * 2];
                System.arraycopy(cache, 0, eventCache, 0, cache.length);
            }
            RecordedEvent event = eventCache[index];
            if (event == null) {
                event = new RecordedEvent(objectContext, new Object[length], 0L, 0L);
                eventCache[index] = event;
            }
            index++;
            return event;
        } else {
            return unorderedEvent;
        }
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setThresholdNanos(long thresholdNanos) {
        this.thresholdNanos = thresholdNanos;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public RecordedEvent parse(RecordingInput input) throws IOException {
        if (enabled) {
            long startTicks = input.readLong();
            long durationTicks = 0;
            if (hasDuration) {
                durationTicks = input.readLong();
                if (thresholdNanos > 0L) {
                    if (timeConverter.convertTimespan(durationTicks) < thresholdNanos) {
                        return null;
                    }
                }
            }
            long endTicks = startTicks + durationTicks;
            if (firstNanos > 0L) {
                if (timeConverter.convertTimestamp(endTicks) < firstNanos) {
                    return null;
                }
            }

            if (eventCache != null) {
                RecordedEvent event = cachedEvent();
                event.startTimeTicks = startTicks;
                event.endTimeTicks = endTicks;
                Object[] values = event.objects;
                for (int i = 0; i < length; i++) {
                    values[i] = parsers[startIndex + i].parse(input);
                }
                return event;
            } else {
                Object[] values = new Object[length];
                for (int i = 0; i < length; i++) {
                    values[i] = parsers[startIndex + i].parse(input);
                }
                return new RecordedEvent(objectContext, values, startTicks, endTicks);
            }
        }
        return null;
    }

    @Override
    public void skip(RecordingInput input) throws IOException {
        throw new InternalError("Should not call this method. More efficent to read event size and skip ahead");
    }

    public void resetCache() {
        index = 0;
    }

    public boolean hasReuse() {
        return eventCache != null;
    }

    public void setReuse(boolean reuse) {
        if (reuse == hasReuse()) {
            return;
        }
        if (reuse) {
            eventCache = new RecordedEvent[2];
            index = 0;
        } else {
            eventCache = null;
        }
    }

    public void setFirstNanos(long firstNanos) {
        this.firstNanos = firstNanos;
    }

    public void setOrdered(boolean ordered) {
        if (this.ordered == ordered) {
            return;
        }
        this.ordered = ordered;
        this.index = 0;
    }
}
