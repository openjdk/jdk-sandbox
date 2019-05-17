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

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Implementation of an event stream that operates against a recording file.
 *
 */
final class EventFileStream implements EventStream {

    public EventFileStream(Path path) {
        Objects.requireNonNull(path);
    }

    @Override
    public void onEvent(Consumer<RecordedEvent> action) {
        Objects.requireNonNull(action);
        notImplemented();
    }

    public void onEvent(EventFilter filter, Consumer<RecordedEvent> action) {
        Objects.requireNonNull(filter);
        Objects.requireNonNull(action);
        notImplemented();
    }

    @Override
    public void onEvent(String eventName, Consumer<RecordedEvent> action) {
        Objects.requireNonNull(eventName);
        Objects.requireNonNull(action);
        notImplemented();
    }

    @Override
    public void onFlush(Runnable action) {
        Objects.requireNonNull(action);
        notImplemented();
    }

    @Override
    public void onClose(Runnable action) {
        Objects.requireNonNull(action);
        notImplemented();
    }

    @Override
    public void close() {
        notImplemented();
    }

    @Override
    public boolean remove(Object action) {
        Objects.requireNonNull(action);
        notImplemented();
        return false;
    }

    @Override
    public void start() {
        notImplemented();
    }

    @Override
    public void startAsync() {
        notImplemented();
    }

    @Override
    public void awaitTermination(Duration timeout) {
        Objects.requireNonNull(timeout);
    }

    @Override
    public void awaitTermination() {
        notImplemented();
    }

    private static void notImplemented() {
        throw new UnsupportedOperationException("Streaming for files not yet implemenetd");
    }
}
