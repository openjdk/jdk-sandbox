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
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

import jdk.jfr.internal.SecuritySupport;
import jdk.jfr.internal.Utils;
import jdk.jfr.internal.consumer.FileAccess;

/**
 * Represents a stream of events.
 */
public interface EventStream extends AutoCloseable {

    /**
     * Creates a stream from the disk repository of the current Java Virtual
     * Machine (JVM).
     * <p>
     * By default, the stream starts with the next event flushed by Flight
     * Recorder.
     *
     * @return an event stream, not {@code null}
     *
     * @throws IOException if a stream can't be opened, or an I/O error occurs
     *         when trying to access the repository
     *
     * @throws SecurityException if a security manager exists and the caller
     *         does not have
     *         {@code FlightRecorderPermission("accessFlightRecorder")}
     */
    public static EventStream openRepository() throws IOException {
        Utils.checkAccessFlightRecorder();
        return new EventDirectoryStream(AccessController.getContext(), null, SecuritySupport.PRIVILIGED, false);
    }

    /**
     * Creates an event stream from a disk repository.
     * <p>
     * By default, the stream starts with the next event flushed by Flight
     * Recorder.
     *
     * @param directory location of the disk repository, not {@code null}
     *
     * @return an event stream, not {@code null}
     *
     * @throws IOException if a stream can't be opened, or an I/O error occurs
     *         when trying to access the repository
     *
     * @throws SecurityException if a security manager exists and its
     *         {@code checkRead} method denies read access to the directory, or
     *         files in the directory.
     */
    public static EventStream openRepository(Path directory) throws IOException {
        Objects.nonNull(directory);
        AccessControlContext acc = AccessController.getContext();
        return new EventDirectoryStream(acc, directory, FileAccess.UNPRIVILIGED, false);
    }

    /**
     * Creates an event stream from a file.
     * <p>
     * By default, the stream starts with the first event in the file.
     *
     * @param file location of the file, not {@code null}
     *
     * @return an event stream, not {@code null}
     *
     * @throws IOException if a stream can't be opened, or an I/O error occurs
     *         during reading
     *
     * @throws SecurityException if a security manager exists and its
     *         {@code checkRead} method denies read access to the file
     */
    public static EventStream openFile(Path file) throws IOException {
        return new EventFileStream(AccessController.getContext(), file);
    }

    /**
     * Performs an action on all events in the stream.
     *
     * @param action an action to be performed on each {@code RecordedEvent},
     *        not {@code null}
     */
    void onEvent(Consumer<RecordedEvent> action);

    /**
     * Performs an action on all events in the stream with a specified name.
     *
     * @param eventName the name of the event, not {@code null}
     *
     * @param action an action to be performed on each {@code RecordedEvent}
     *        that matches the event name, not {@code null}
     */
    void onEvent(String eventName, Consumer<RecordedEvent> action);

    /**
     * Performs an action when the event stream has been flushed.
     *
     * @param action an action to be performed after stream has been flushed,
     *        not {@code null}
     */
    void onFlush(Runnable action);

    /**
     * Performs an action if an exception occurs when processing the stream.
     * <p>
     * if an error handler has not been added to the stream, an exception stack
     * trace is printed to standard error.
     * <p>
     * Adding an error handler overrides the default behavior. If multiple error
     * handlers have been added, they will be executed in the order they were
     * added.
     *
     * @param action an action to be performed if an exception occurs, not
     *        {@code null}
     */
    void onError(Consumer<Throwable> action);

    /**
     * Performs an action when the event stream is closed.
     * <p>
     * If the stream is already closed, the action will be executed immediately
     * in the current thread.
     *
     * @param action an action to be performed after the stream has been closed,
     *        not {@code null}
     */
    void onClose(Runnable action);

    /**
     * Releases all resources associated with this event stream.
     */
    void close();

    /**
     * Removes an action from the stream.
     * <p>
     * If the action has been added multiple times, all instance of it will be
     * removed.
     *
     * @param action the action to remove, not {@code null}
     *
     * @return {@code true} if the action was removed, {@code false} otherwise
     *
     * @see #onEvent(Consumer)
     * @see #onEvent(String, Consumer)
     * @see #onFlush(Runnable)
     * @see #onClose(Runnable)
     * @see #onError(Consumer)
     */
    boolean remove(Object action);

    /**
     * Specifies that the event object in an {@link #onEvent(Consumer)} action
     * is to be reused.
     * <p>
     * If reuse is set to {@code true), a callback should not keep a reference
     * to the event object after the callback from {@code onEvent} has returned.
     *
     * @param resuse if event objects can be reused between calls to
     * {@code #onEvent(Consumer)}
     *
     */
    public void setReuse(boolean reuse);

    /**
     * Specifies that events arrives in chronological order, sorted by the time
     * they were committed to the event stream.
     *
     * @param ordered if event objects arrive in chronological order to
     *        {@code #onEvent(Consumer)}
     */
    public void setOrdered(boolean ordered);

    /**
     * Specifies start time of the event stream.
     * <p>
     * The start time must be set before the stream is started.
     *
     * @param startTime the start time, not {@code null}
     *
     * @throws IllegalStateException if the stream has already been started
     */
    public void setStartTime(Instant startTime);

    /**
     * Specifies end time of the event stream.
     * <p>
     * The end time must be set before the stream is started.
     * <p>
     * When the end time is reached the stream is closed.
     *
     * @param endTime the end time, not {@code null}
     *
     * @throws IllegalStateException if the stream has already been started
     */
    public void setEndTime(Instant endTime);

    /**
     * Start processing events in the stream.
     * <p>
     * All actions performed on this stream will happen in the current thread.
     *
     * @throws IllegalStateException if the stream is already started or if it
     *         has been closed
     */
    void start();

    /**
     * Start processing events in the stream asynchronously.
     * <p>
     * All actions on this stream will be performed in a separate thread.
     *
     * @throws IllegalStateException if the stream is already started, or if it
     *         has been closed
     */
    void startAsync();

    /**
     * Blocks the current thread until the stream is finished, closed, or it
     * times out.
     *
     * @param timeout the maximum time to wait, not {@code null}
     *
     * @throws IllegalArgumentException if timeout is negative
     * @throws InterruptedException
     *
     * @see #start()
     * @see #startAsync()
     */
    void awaitTermination(Duration timeout) throws InterruptedException;

    /**
     * Blocks the current thread until the stream is finished or closed.
     *
     * @throws InterruptedException
     *
     * @see #start()
     * @see #startAsync()
     */
    void awaitTermination() throws InterruptedException;
}