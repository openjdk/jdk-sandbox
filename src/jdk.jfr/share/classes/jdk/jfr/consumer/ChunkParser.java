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

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

import jdk.jfr.EventType;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.LongMap;
import jdk.jfr.internal.MetadataDescriptor;
import jdk.jfr.internal.Type;
import jdk.jfr.internal.Utils;
import jdk.jfr.internal.consumer.ChunkHeader;
import jdk.jfr.internal.consumer.InternalEventFilter;
import jdk.jfr.internal.consumer.Parser;
import jdk.jfr.internal.consumer.RecordingInput;

/**
 * Parses a chunk.
 *
 */
final class ChunkParser {
    private static final long CONSTANT_POOL_TYPE_ID = 1;
    private final RecordingInput input;
    private final ChunkHeader chunkHeader;
    private final MetadataDescriptor metadata;
    private final TimeConverter timeConverter;
    private final MetadataDescriptor previousMetadata;
    private final long pollInterval;
    private final LongMap<ConstantLookup> constantLookups;

    private LongMap<Type> typeMap;
    private LongMap<Parser> parsers;
    private boolean chunkFinished;
    private InternalEventFilter eventFilter = InternalEventFilter.ACCEPT_ALL;
    private boolean reuse;
    private boolean ordered;
    private boolean resetEventCache;

    public ChunkParser(RecordingInput input, boolean reuse) throws IOException {
       this(new ChunkHeader(input), null, 500);
       this.reuse = reuse;
    }

    public ChunkParser(ChunkParser previous) throws IOException {
        this(new ChunkHeader(previous.input), null, 500);
     }

    private ChunkParser(ChunkHeader header, ChunkParser previous, long pollInterval) throws IOException {
        this.input = header.getInput();
        this.chunkHeader = header;
        if (previous == null) {
            this.pollInterval = 500;
            this.constantLookups = new LongMap<>();
            this.previousMetadata = null;
        } else {
            this.constantLookups = previous.constantLookups;
            this.previousMetadata = previous.metadata;
            this.pollInterval = previous.pollInterval;
            this.ordered = previous.ordered;
            this.reuse = previous.reuse;
            this.eventFilter = previous.eventFilter;
        }
        this.metadata = header.readMetadata(previousMetadata);
        this.timeConverter = new TimeConverter(chunkHeader, metadata.getGMTOffset());
        if (metadata != previousMetadata) {
            ParserFactory factory = new ParserFactory(metadata, constantLookups, timeConverter);
            parsers = factory.getParsers();
            typeMap = factory.getTypeMap();
            updateEventParsers();
        } else {
            parsers = previous.parsers;
            typeMap = previous.typeMap;
        }
        constantLookups.forEach(c -> c.newPool());
        fillConstantPools(0);
        constantLookups.forEach(c -> c.getLatestPool().setResolving());
        constantLookups.forEach(c -> c.getLatestPool().resolve());
        constantLookups.forEach(c -> c.getLatestPool().setResolved());

        input.position(chunkHeader.getEventStart());
    }

    public void setParserFilter(InternalEventFilter filter) {
        this.eventFilter = filter;
    }

    public InternalEventFilter getEventFilter() {
        return this.eventFilter;
    }


    /**
     * Reads an event and returns null when segment or chunk ends.
     */
    public RecordedEvent readStreamingEvent(boolean awaitNewEvents) throws IOException {
        long absoluteChunkEnd = chunkHeader.getEnd();
        while (true) {
            RecordedEvent event = readEvent();
            if (event != null) {
                return event;
            }
            if (!awaitNewEvents) {
                return null;
            }
            long lastValid = absoluteChunkEnd;
            long metadataPoistion = chunkHeader.getMetataPosition();
            long contantPosition = chunkHeader.getConstantPoolPosition();
            chunkFinished = awaitUpdatedHeader(absoluteChunkEnd);
            if (chunkFinished) {
                Logger.log(LogTag.JFR_SYSTEM_PARSER, LogLevel.INFO, "At chunk end");
                return null;
            }
            absoluteChunkEnd = chunkHeader.getEnd();
            // Read metadata and constant pools for the next segment
            if (chunkHeader.getMetataPosition() != metadataPoistion) {
                Logger.log(LogTag.JFR_SYSTEM_PARSER, LogLevel.INFO, "Found new metadata in chunk. Rebuilding types and parsers");
                MetadataDescriptor metadata = chunkHeader.readMetadata(previousMetadata);
                ParserFactory factory = new ParserFactory(metadata, constantLookups, timeConverter);
                parsers = factory.getParsers();
                typeMap = factory.getTypeMap();
                updateEventParsers();
            }
            if (contantPosition != chunkHeader.getConstantPoolPosition()) {
                Logger.log(LogTag.JFR_SYSTEM_PARSER, LogLevel.INFO, "Found new constant pool data. Filling up pools with new values");
                constantLookups.forEach(c -> c.getLatestPool().setAllResolved(false));
                fillConstantPools(contantPosition + chunkHeader.getAbsoluteChunkStart());
                constantLookups.forEach(c -> c.getLatestPool().setResolving());
                constantLookups.forEach(c -> c.getLatestPool().resolve());
                constantLookups.forEach(c -> c.getLatestPool().setResolved());
            }
            input.position(lastValid);
        }
    }

    /**
     * Reads an event and returns null when the chunk ends
     */
    public RecordedEvent readEvent() throws IOException {
        long absoluteChunkEnd = chunkHeader.getEnd();
        while (input.position() < absoluteChunkEnd) {
            long pos = input.position();
            int size = input.readInt();
            if (size == 0) {
                throw new IOException("Event can't have zero size");
            }
            long typeId = input.readLong();
            // Skip metadata and constant pool events (id = 0, id = 1)
            if (typeId > CONSTANT_POOL_TYPE_ID) {
                Parser p = parsers.get(typeId);
                if (p instanceof EventParser) {
                    EventParser ep = (EventParser) p;
                    RecordedEvent event = ep.parse(input);
                    if (event != null) {
                        input.position(pos + size);
                        return event;
                    }
                }
            }
            input.position(pos + size);
        }
        return null;
    }

    private boolean awaitUpdatedHeader(long absoluteChunkEnd) throws IOException {
        Logger.log(LogTag.JFR_SYSTEM_PARSER, LogLevel.INFO, "Waiting for more data (streaming). Read so far: " + chunkHeader.getChunkSize() + " bytes");
        while (true) {
            chunkHeader.refresh();
            if (absoluteChunkEnd != chunkHeader.getEnd()) {
                return false;
            }
            if (chunkHeader.isFinished()) {
                return true;
            }
            Utils.takeNap(pollInterval);
        }
    }

    private void fillConstantPools(long abortCP) throws IOException {
        long thisCP = chunkHeader.getConstantPoolPosition() + chunkHeader.getAbsoluteChunkStart();
        long lastCP = -1;
        long delta = -1;
        boolean log = Logger.shouldLog(LogTag.JFR_SYSTEM_PARSER, LogLevel.TRACE);
        while (thisCP != abortCP && delta != 0) {
            input.position(thisCP);
            lastCP = thisCP;
            int size = input.readInt(); // size
            long typeId = input.readLong();
            if (typeId != CONSTANT_POOL_TYPE_ID) {
                throw new IOException("Expected check point event (id = 1) at position " + lastCP + ", but found type id = " + typeId);
            }
            input.readLong(); // timestamp
            input.readLong(); // duration
            delta = input.readLong();
            thisCP += delta;
            boolean flush = input.readBoolean();
            int poolCount = input.readInt();
            final long logLastCP = lastCP;
            final long logDelta = delta;
            Logger.log(LogTag.JFR_SYSTEM_PARSER, LogLevel.TRACE, () -> {
                return "New constant pool: startPosition=" + logLastCP + ", size=" + size + ", deltaToNext=" + logDelta + ", flush=" + flush + ", poolCount=" + poolCount;
            });
            for (int i = 0; i < poolCount; i++) {
                long id = input.readLong(); // type id
                ConstantLookup lookup = constantLookups.get(id);
                Type type = typeMap.get(id);
                if (lookup == null) {
                    Logger.log(LogTag.JFR_SYSTEM_PARSER, LogLevel.INFO, "Found constant pool(" + id + ") that is never used");
                    if (type == null) {
                        throw new IOException(
                                "Error parsing constant pool type " + getName(id) + " at position " + input.position() + " at check point between [" + lastCP + ", " + lastCP + size + "]");
                    }
                    ConstantMap pool = new ConstantMap(ObjectFactory.create(type, timeConverter), type.getName());
                    constantLookups.put(type.getId(), new ConstantLookup(pool, type));
                }
                Parser parser = parsers.get(id);
                if (parser == null) {
                    throw new IOException("Could not find constant pool type with id = " + id);
                }
                try {
                    int count = input.readInt();
                    if (count == 0) {
                        throw new InternalError("Pool " + type.getName() + " must contain at least one element ");
                    }
                    if (log) {
                        Logger.log(LogTag.JFR_SYSTEM_PARSER, LogLevel.TRACE, "Constant Pool " + i + ": " + type.getName());
                    }
                    for (int j = 0; j < count; j++) {
                        long key = input.readLong();
//                      Object resolved = lookup.getCurrent(key);
// Disable cache        Object resolved = lookup.getResolved(key);
//                      if (resolved == null) {
                            Object v = parser.parse(input);
                            logConstant(key, v, false);
                            lookup.getLatestPool().put(key, v);
//                        } else {
//                            parser.skip(input);
//                            logConstant(key, resolved, true);
// Disable cache            lookup.getLatestPool().putResolved(key, resolved);
//                        }
                    }
                } catch (Exception e) {
                    throw new IOException("Error parsing constant pool type " + getName(id) + " at position " + input.position() + " at check point between [" + lastCP + ", " + lastCP + size + "]",
                            e);
                }
            }
            if (input.position() != lastCP + size) {
                throw new IOException("Size of check point event doesn't match content");
            }
        }
    }

    private void logConstant(long key, Object v, boolean preresolved) {
        if (!Logger.shouldLog(LogTag.JFR_SYSTEM_PARSER, LogLevel.TRACE)) {
            return;
        }
        String valueText;
        if (v.getClass().isArray()) {
            Object[] array = (Object[]) v;
            StringJoiner sj = new StringJoiner(", ", "{", "}");
            for (int i = 0; i < array.length; i++) {
                sj.add(textify(array[i]));
            }
            valueText = sj.toString();
        } else {
            valueText = textify(v);
        }
        String suffix  = preresolved ? " (presolved)" :"";
        Logger.log(LogTag.JFR_SYSTEM_PARSER, LogLevel.TRACE, "Constant: " + key + " = " + valueText + suffix);
    }

    private String textify(Object o) {
        if (o == null) { // should not happen
            return "null";
        }
        if (o instanceof String) {
            return "\"" + String.valueOf(o) + "\"";
        }
        if (o instanceof RecordedObject) {
            return o.getClass().getName();
        }
        if (o.getClass().isArray()) {
            Object[] array = (Object[]) o;
            if (array.length > 0) {
                return textify(array[0]) + "[]"; // can it be recursive?
            }
        }
        return String.valueOf(o);
    }

    private String getName(long id) {
        Type type = typeMap.get(id);
        return type == null ? ("unknown(" + id + ")") : type.getName();
    }

    public Collection<Type> getTypes() {
        return metadata.getTypes();
    }

    public List<EventType> getEventTypes() {
        return metadata.getEventTypes();
    }

    public boolean isLastChunk() throws IOException {
        return chunkHeader.isLastChunk();
    }

    public ChunkParser newChunkParser() throws IOException {
        return new ChunkParser(this);
    }

    public ChunkParser nextChunkParser() throws IOException {
        return new ChunkParser(chunkHeader.nextHeader(), this, pollInterval);
    }

    public boolean isChunkFinished() {
        return chunkFinished;
    }

    // Need to call updateEventParsers() for
    // change to take effect
    public void setReuse(boolean resue) {
        this.reuse = resue;
    }

    // Need to call updateEventParsers() for
    // change to take effect
    public void setOrdered(boolean ordered) {
        this.ordered = ordered;
    }

    // Need to call updateEventParsers() for
    // change to take effect
    public void resetEventCache() {
        this.resetEventCache = true;
    }

    public void updateEventParsers() {
        parsers.forEach(p -> {
            if (p instanceof EventParser) {
                EventParser ep = (EventParser) p;
                ep.setOrdered(ordered);
                ep.setReuse(reuse);
                if (resetEventCache) {
                    ep.resetCache();
                }
                long threshold = eventFilter.getThreshold(ep.getEventType().getName());
                if (threshold >= 0) {
                    ep.setEnabled(true);
                    ep.setThreshold(timeConverter.convertDurationNanos(threshold));
                } else {
                    ep.setThreshold(-1L);
                }
            }
        });
        resetEventCache = false;
    }
}
