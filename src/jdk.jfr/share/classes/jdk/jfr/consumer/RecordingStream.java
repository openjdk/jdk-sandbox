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
import java.security.AccessControlContext;
import java.security.AccessController;
import java.time.Duration;
import java.util.function.Consumer;

import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.EventSettings;
import jdk.jfr.EventType;
import jdk.jfr.Recording;
import jdk.jfr.internal.PlatformRecording;
import jdk.jfr.internal.PrivateAccess;
import jdk.jfr.internal.Utils;

/**
 * An event stream produces events from a file, directory or a running JVM (Java
 * Virtual Machine).
 */
public class RecordingStream implements AutoCloseable, EventStream {

    private final Recording recording;
    private final EventDirectoryStream stream;

    /**
     * Creates an event stream for this JVM (Java Virtual Machine).
     * <p>
     * The following example shows how to create a recording stream that prints
     * CPU usage and information about garbage collections.
     *
     * <pre>
     * <code>
     * try (RecordingStream  r = new RecordingStream()) {
     *   r.enable("jdk.GarbageCollection");
     *   r.enable("jdk.CPULoad").withPeriod(Duration.ofSeconds(1));
     *   r.onEvent(System.out::println);
     *   r.start();
     * }
     * </code>
     * </pre>
     *
     * @throws IllegalStateException if Flight Recorder can't be created (for
     *         example, if the Java Virtual Machine (JVM) lacks Flight Recorder
     *         support, or if the file repository can't be created or accessed)
     *
     * @throws SecurityException if a security manager exists and the caller
     *         does not have
     *         {@code FlightRecorderPermission("accessFlightRecorder")}
     */
    public RecordingStream() {
        Utils.checkAccessFlightRecorder();
        AccessControlContext acc = AccessController.getContext();
        this.recording = new Recording();
        this.recording.setFlushInterval(Duration.ofMillis(1000));
        try {
            this.stream = new EventDirectoryStream(acc);
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe.getMessage());
        }
    }

    /**
     * Creates a recording stream using settings from a configuration.
     * <p>
     * The following example shows how to create a recording stream that uses a
     * predefined configuration.
     *
     * <pre>
     * <code>
     * Configuration c = Configuration.getConfiguration("default");
     * try (RecordingStream  r = new RecordingStream(c)) {
     *   r.onEvent(System.out::println);
     *   r.start();
     * }
     * </code>
     * </pre>
     *
     * @param configuration configuration that contains the settings to be use,
     *        not {@code null}
     *
     * @throws IllegalStateException if Flight Recorder can't be created (for
     *         example, if the Java Virtual Machine (JVM) lacks Flight Recorder
     *         support, or if the file repository can't be created or accessed)
     *
     * @throws SecurityException if a security manager is used and
     *         FlightRecorderPermission "accessFlightRecorder" is not set.
     *
     * @see Configuration
     */
    public RecordingStream(Configuration configuration) {
        this();
        recording.setSettings(configuration.getSettings());
    }

    /**
     * Enables the event with the specified name.
     * <p>
     * If multiple events have the same name (for example, the same class is
     * loaded in different class loaders), then all events that match the name
     * are enabled. To enable a specific class, use the {@link #enable(Class)}
     * method or a {@code String} representation of the event type ID.
     *
     * @param name the settings for the event, not {@code null}
     *
     * @return an event setting for further configuration, not {@code null}
     *
     * @see EventType
     */
    public EventSettings enable(String name) {
        return recording.enable(name);
    }

    /**
     * Enables event.
     *
     * @param eventClass the event to enable, not {@code null}
     *
     * @throws IllegalArgumentException if {@code eventClass} is an abstract
     *         class or not a subclass of {@link Event}
     *
     * @return an event setting for further configuration, not {@code null}
     */
    public EventSettings enable(Class<? extends Event> eventClass) {
        return recording.enable(eventClass);
    }

    /**
     * Disables event with the specified name.
     * <p>
     * If multiple events with same name (for example, the same class is loaded
     * in different class loaders), then all events that match the name is
     * disabled. To disable a specific class, use the {@link #disable(Class)}
     * method or a {@code String} representation of the event type ID.
     *
     * @param name the settings for the event, not {@code null}
     *
     * @return an event setting for further configuration, not {@code null}
     *
     */
    public EventSettings disable(String name) {
        return recording.disable(name);
    }

    /**
     * Disables event.
     *
     * @param eventClass the event to enable, not {@code null}
     *
     * @throws IllegalArgumentException if {@code eventClass} is an abstract
     *         class or not a subclass of {@link Event}
     *
     * @return an event setting for further configuration, not {@code null}
     *
     */
    public EventSettings disable(Class<? extends Event> eventClass) {
        return recording.disable(eventClass);
    }
    /**
     * Determines how far back data is kept for the stream if the stream can't
     * keep up.
     * <p>
     * To control the amount of recording data stored on disk, the maximum
     * length of time to retain the data can be specified. Data stored on disk
     * that is older than the specified length of time is removed by the Java
     * Virtual Machine (JVM).
     * <p>
     * If neither maximum limit or the maximum age is set, the size of the
     * recording may grow indefinitely if events are on
     *
     * @param maxAge the length of time that data is kept, or {@code null} if
     *        infinite
     *
     * @throws IllegalArgumentException if <code>maxAge</code> is negative
     *
     * @throws IllegalStateException if the recording is in the {@code CLOSED}
     *         state
     */
    public void setMaxAge(Duration maxAge) {
        recording.setMaxAge(maxAge);
    }

    /**
     * Determines how much data is kept in the disk repository if the stream
     * can't keep up.
     * <p>
     * To control the amount of recording data that is stored on disk, the
     * maximum amount of data to retain can be specified. When the maximum limit
     * is exceeded, the Java Virtual Machine (JVM) removes the oldest chunk to
     * make room for a more recent chunk.
     * <p>
     * If neither maximum limit or the maximum age is set, the size of the
     * recording may grow indefinitely.
     *
     * @param maxSize the amount of data to retain, {@code 0} if infinite
     *
     * @throws IllegalArgumentException if <code>maxSize</code> is negative
     *
     * @throws IllegalStateException if the recording is in {@code CLOSED} state
     */
    public void setMaxSize(long maxSize) {
        recording.setMaxSize(maxSize);
    }

    @Override
    public void onEvent(String eventName, Consumer<RecordedEvent> action) {
        stream.onEvent(eventName, action);
    }

    @Override
    public void onEvent(Consumer<RecordedEvent> action) {
        stream.onEvent(action);
    }

    @Override
    public void onFlush(Runnable action) {
        stream.onFlush(action);
    }

    @Override
    public void onClose(Runnable action) {
        stream.onClose(action);
    }

    @Override
    public void close() {
        recording.close();
        stream.close();
    }

    @Override
    public boolean remove(Object action) {
        return stream.remove(action);
    }

    @Override
    public void start() {
        PlatformRecording pr = PrivateAccess.getInstance().getPlatformRecording(recording);
        long startNanos = pr.start();
        stream.start(startNanos);
    }

    @Override
    public void startAsync() {
        PlatformRecording pr = PrivateAccess.getInstance().getPlatformRecording(recording);
        long startNanos = pr.start();
        stream.startAsync(startNanos);
    }

    @Override
    public void awaitTermination(Duration timeout) {
        stream.awaitTermination(timeout);
    }

    /**
     * Determines how often events are made available for streaming.
     *
     * @param interval the interval at which events are made available to the
     *        stream
     *
     * @throws IllegalArgumentException if <code>interval</code> is negative
     *
     * @throws IllegalStateException if the stream is closed
     */
    public void setInterval(Duration duration) {
        recording.setFlushInterval(duration);
    }

    @Override
    public void awaitTermination() {
        stream.awaitTermination();
    }

    @Override
    public void setReuse(boolean reuse) {
     // hint is ignored
    }
}
