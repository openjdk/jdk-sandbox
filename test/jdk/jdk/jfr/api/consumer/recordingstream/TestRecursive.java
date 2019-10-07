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

package jdk.jfr.api.consumer.recordingstream;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Tests that events are not emitted in handlers
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.consumer.recordingstream.TestRecursive
 */
public class TestRecursive {

    public static class NotRecorded extends Event {
    }

    public static class Recorded extends Event {
    }

    public static class Provoker extends Event {
    }

    public static void main(String... args) throws Exception {
        testSync();
        testAsync();
    }

    private static void emit(AtomicBoolean stop) {
        Runnable r = () -> {
            while (!stop.get()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
                Provoker e = new Provoker();
                e.commit();
            }
        };
        Thread t = new Thread(r);
        t.start();
    }

    private static void testSync() throws Exception {
        try (Recording r = new Recording()) {
            r.start();
            AtomicBoolean stop = new AtomicBoolean(false);
            emit(stop);
            try (RecordingStream rs = new RecordingStream()) {
                Recorded e1 = new Recorded();
                e1.commit();
                rs.onEvent(e -> {
                    if (!stop.get()) {
                        System.out.println("Emitting NotRecorded event");
                        NotRecorded event = new NotRecorded();
                        event.commit();
                        System.out.println("Stopping event provoker");
                        stop.set(true);
                        System.out.println("Closing recording stream");
                        rs.close();
                        return;
                    }
                });
                rs.start();
                Recorded e2 = new Recorded();
                e2.commit();
            }
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            System.out.println(events);
            if (count(events, NotRecorded.class) != 0) {
                throw new Exception("Expected 0 NotRecorded events");
            }
            if (count(events, Recorded.class) == 2) {
                throw new Exception("Expected 2 Recorded events");
            }
        }
    }

    private static int count(List<RecordedEvent> events, Class<?> eventClass) {
        int count = 0;
        for (RecordedEvent e : events) {
            if (e.getEventType().getName().equals(eventClass.getName())) {
                count++;
            }
        }
        System.out.println(count);
        return count;
    }

    private static void testAsync() throws InterruptedException, Exception {
        CountDownLatch latchOne = new CountDownLatch(1);
        CountDownLatch latchTwo = new CountDownLatch(2);
        AtomicBoolean fail = new AtomicBoolean();
        try (RecordingStream r = new RecordingStream()) {
            r.onEvent(e -> {
                System.out.println(e);
                NotRecorded event = new NotRecorded();
                event.commit();
                if (e.getEventType().getName().equals(Recorded.class.getName())) {
                    latchOne.countDown();
                    latchTwo.countDown();
                }
                if (e.getEventType().getName().equals(NotRecorded.class.getName())) {
                    fail.set(true);
                }
            });
            r.startAsync();
            Recorded e1 = new Recorded();
            e1.commit();
            latchOne.await();
            Recorded e2 = new Recorded();
            e2.commit();
            latchTwo.await();
            if (fail.get()) {
                throw new Exception("Unexpected event found");
            }
        }
    }
}
