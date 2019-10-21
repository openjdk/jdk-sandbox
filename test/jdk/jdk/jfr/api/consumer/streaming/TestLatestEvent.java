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

package jdk.jfr.api.consumer.streaming;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordingStream;

/**
 * @test
 * @summary Verifies that a stream from a repository starts at the latest event
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.consumer.streaming.TestLatestEvent
 */
public class TestLatestEvent {

    @Name("NotLatest")
    static class NotLatestEvent extends Event {
    }

    @Name("Latest")
    static class LatestEvent extends Event {
    }

    @Name("MakeChunks")
    static class MakeChunks extends Event {
    }

    public static void main(String... args) throws Exception {
        try (RecordingStream r = new RecordingStream()) {
            r.startAsync();
            MakeChunks e = new MakeChunks();
            e.commit();
            CountDownLatch beginChunks = new CountDownLatch(1);
            r.onEvent("MakeChunks", event-> {
                beginChunks.countDown();
            });
            System.out.println("Waitning for first chunk");
            beginChunks.await();
            // Create 5 chunks with events in the repository
            for (int i = 0; i < 5; i++) {
                System.out.println("Creating empty chunk");
                try (Recording r1 = new Recording()) {
                    r1.start();
                    NotLatestEvent notLatest = new NotLatestEvent();
                    notLatest.commit();
                    r1.stop();
                }
            }
            System.out.println("All empty chunks created");
            // Create an event in a segment, typically the first.
            NotLatestEvent notLatest = new NotLatestEvent();
            notLatest.commit();
            try (EventStream s = EventStream.openRepository()) {
                System.out.println("EventStream opened");
                awaitFlush(r); // ensure that NotLatest is included
                s.startAsync();
                AtomicBoolean foundLatest = new AtomicBoolean();
                System.out.println("Added onEvent handler");
                s.onEvent(event -> {
                    String name = event.getEventType().getName();
                    System.out.println("Found event " + name);
                    foundLatest.set(name.equals("Latest"));
                    s.close();
                });
                // Emit the latest event
                LatestEvent latest = new LatestEvent();
                latest.commit();
                System.out.println("Latest event emitted");
                System.out.println("Waiting for termination");
                s.awaitTermination();
                if (!foundLatest.get()) {
                    throw new Exception("Didn't find latest event!");
                }
            }
        }
    }

    private static void awaitFlush(RecordingStream r) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        System.out.println("Waiting for flush...");
        r.onFlush(() -> {
            System.out.println("Flush arrived!");
            latch.countDown();
        });
        latch.await();

    }
}
