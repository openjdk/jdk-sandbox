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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.StackTrace;
import jdk.jfr.consumer.EventStream;

/**
 * @test
 * @summary Tests EventStream::setStartTime
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.consumer.recordingstream.TestSetStartTime
 */
public final class TestSetStartTime {

    @Name("Mark")
    @StackTrace(false)
    public final static class Mark extends Event {
        public boolean before;
    }

    public static void main(String... args) throws Exception {
        try (Recording r = new Recording()) {
            r.setFlushInterval(Duration.ofSeconds(1));
            r.start();
            Mark event1 = new Mark();
            event1.before = true;
            event1.commit();
            Thread.sleep(2000);
            Instant now = Instant.now();
            System.out.println("Instant.now() = " + now);
            Thread.sleep(2000);
            Mark event2 = new Mark();
            event2.before = false;
            event2.commit();
            AtomicBoolean error = new AtomicBoolean();
            try (EventStream d = EventStream.openRepository()) {
                d.setStartTime(now);
                d.onEvent(e -> {
                    System.out.println(e);
                    boolean early = e.getBoolean("before");
                    if (early) {
                        error.set(true);
                    } else {
                        // OK, as expected
                        d.close();
                    }
                });
                d.start();
                if (error.get()) {
                    throw new Exception("Found unexpected event!");
                }
            }
        }
    }
}
