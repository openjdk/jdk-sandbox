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
import java.nio.file.Path;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

import jdk.jfr.internal.consumer.EventConsumer;
import jdk.jfr.internal.consumer.RecordingInput;

/**
 * Implementation of an event stream that operates against a recording file.
 *
 */
final class EventFileStream implements EventStream {

    private final static class FileEventConsumer extends EventConsumer {
        private final RecordingInput input;
        private ChunkParser chunkParser;
        private boolean reuse = true;

        public FileEventConsumer(AccessControlContext acc, RecordingInput input) throws IOException {
            super(acc);
            this.input = input;
        }

        @Override
        public void process() throws Exception {
            chunkParser = new ChunkParser(input, reuse);
            chunkParser.setReuse(reuse);
            RecordedEvent event;
            while (true) {
                event = chunkParser.readEvent();
                if (event == null) {
                    event = findNext();
                    if (event == null) {
                        return;
                    }
                }
                dispatch(event);
            }
        }

        private RecordedEvent findNext() throws IOException {
            RecordedEvent event = null;
            while (event == null) {
                if (chunkParser.isLastChunk()) {
                    return null;
                }
                chunkParser = chunkParser.nextChunkParser();
                event = chunkParser.readEvent();
            }
            return event;
        }

        public void setReuse(boolean reuse) {
            if (chunkParser == null) {
                this.reuse = reuse;
            } else {
                chunkParser.setReuse(reuse);
            }
        }
    }

    private final RecordingInput input;
    private final FileEventConsumer eventConsumer;

    public EventFileStream(Path path) throws IOException {
        Objects.requireNonNull(path);
        input = new RecordingInput(path.toFile());
        eventConsumer = new FileEventConsumer(AccessController.getContext(), input);
    }

    @Override
    public void onEvent(Consumer<RecordedEvent> action) {
        Objects.requireNonNull(action);
        eventConsumer.onEvent(action);
    }

    @Override
    public void onEvent(String eventName, Consumer<RecordedEvent> action) {
        Objects.requireNonNull(eventName);
        Objects.requireNonNull(action);
        eventConsumer.onEvent(eventName, action);
    }

    @Override
    public void onFlush(Runnable action) {
        Objects.requireNonNull(action);
        eventConsumer.onFlush(action);
    }

    @Override
    public void onClose(Runnable action) {
        Objects.requireNonNull(action);
        eventConsumer.addCloseAction(action);
    }

    @Override
    public void close() {
        eventConsumer.setClosed(true);
        eventConsumer.runCloseActions();
        try {
            input.close();
        } catch (IOException e) {
            // ignore
        }
    }

    @Override
    public boolean remove(Object action) {
        Objects.requireNonNull(action);
        return eventConsumer.remove(action);
    }

    @Override
    public void start() {
        eventConsumer.start(0);
    }

    public void setReuse(boolean reuse) {
        eventConsumer.setReuse(reuse);
    }

    @Override
    public void startAsync() {
        eventConsumer.startAsync(0);
    }

    @Override
    public void awaitTermination(Duration timeout) {
        Objects.requireNonNull(timeout);
        eventConsumer.awaitTermination(timeout);
    }

    @Override
    public void awaitTermination() {
        eventConsumer.awaitTermination();
    }
}
