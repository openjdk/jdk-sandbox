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

    @Name("Chunk")
    static class ChunkEvent extends Event {
        boolean end;
    }

    public static void main(String... args) throws Exception {
        try (RecordingStream r = new RecordingStream()) {
            r.startAsync();
            // Create chunks with events in the repository
            for (int i = 0; i < 5; i++) {
                try (Recording r1 = new Recording()) {
                    r1.start();
                    ChunkEvent e = new ChunkEvent();
                    e.end = false;
                    e.commit();
                    r1.stop();
                }
            }
            CountDownLatch endEventRecevied = new CountDownLatch(1);
            CountDownLatch emitEvent = new CountDownLatch(1);
            try (EventStream s = EventStream.openRepository()) {
                s.onEvent("Chunk", e -> {
                    if (e.getBoolean("end")) {
                        endEventRecevied.countDown();
                        return;
                    }
                    System.out.println("Stream should start at latest event:");
                    System.out.println(e);
                });

                ChunkEvent e1 = new ChunkEvent();
                e1.end = false;
                e1.commit();
                s.startAsync();
                s.onFlush(() -> {
                    emitEvent.countDown();
                });
                emitEvent.await();
                ChunkEvent e2 = new ChunkEvent();
                e2.end = true;
                e2.commit();

                endEventRecevied.await();
            }
        }
    }
}
