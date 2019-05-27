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
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * Represents a stream of event that actions can be performed up on.
 */
public interface EventStream extends AutoCloseable {

    /**
     * Creates a stream starting from the next written event in a disk
     * repository.
     *
     * @param directory location of the disk repository, not {@code null}
     * @return an event stream, not {@code null}
     */
    public static EventStream openRepository(Path directory) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
        // AccessControlContext acc = AccessController.getContext();
        // return new EventDirectoryStream(acc);
    }

    /**
     * Creates an event stream starting from the first event in a file.
     *
     * @param file location of the file, not {@code null}
     * @return an event stream, not {@code null}
     *
     * @throws IOException if a stream can't be opened,or an I/O error occurs
     *         during reading
     */
    public static EventStream openFile(Path file) throws IOException {
        return new EventFileStream(file);
    }

    /**
     * Creates an event stream starting start time and end time in a file.
     *
     * @param file location of the file, not {@code null}
     *
     * @param the start start time for the stream, or {@code null} to get data
     *        from the beginning of the
     *
     * @param the end end time for the stream, or {@code null} to get data until
     *        the end.
     *
     * @throws IllegalArgumentException if {@code end} happens before
     *         {@code start}
     *
     * @throws IOException if a stream can't be opened,or an I/O error occurs
     *         during reading
     */
    public static EventStream openFile(Path file, Instant from, Instant to) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
        // return new EventFileStream(file);
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
     * Performs an action when the event stream is closed.
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
     * @return {@code true} if the action was removed, {@code false} otherwise
     *
     * @see #onClose(Runnable)
     * @see #onFlush(Runnable)
     * @see #onEvent(Consumer)
     * @see #onEvent(String, Consumer)
     */
    boolean remove(Object action);

    /**
     * Hint that the the event object in an {@link #onEvent(Consumer)} action
     * may be reused.
     * <p>
     * If reuse is set to
     * {@code true), a callback should not keep a reference to the event object
     * after the callback from {@code onEvent} has returned.
     * <p>
     * By default reuse is {@code true}
     *
     */
    public void setReuse(boolean reuse);

    /**
     * Starts processing events in the stream.
     * <p>
     * All actions will performed on this stream will happen in the current
     * thread.
     *
     * @throws IllegalStateException if the stream is already started or if it
     *         has been closed
     */
    void start();

    /**
     * Starts processing events in the stream asynchronously.
     * <p>
     * All actions on this stream will be performed in a separate thread.
     *
     * @throws IllegalStateException if the stream is already started or if it
     *         has been closed
     */
    void startAsync();

    /**
     * Blocks the current thread until the stream is finished, closed, or it
     * times out.
     *
     * @param timeout the maximum time to wait, not {@code null}
     *
     * @see #start()
     * @see #startAsync()
     */
    void awaitTermination(Duration timeout);

    /**
     * Blocks the current thread until the stream is finished or closed.
     *
     * @see #start()
     * @see #startAsync()
     */
    void awaitTermination();
}