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

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayDeque;

import jdk.jfr.Configuration;
import jdk.jfr.EventType;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordingStream;

class UseCasesStream {

    //
    // Use case: Out-of-the-Box Experience
    //
    // - Simple things should be simple
    // - Pique interest, i.e. a one-liner on Stack Overflow
    // - Few lines of code as possible
    // - Should be easier than alternative technologies, like JMX and JVM TI
    //
    // - Non-goals: Corner-cases, advanced configuration, releasing resources
    //
    public static void outOfTheBox() throws InterruptedException {
        try (RecordingStream rs = new RecordingStream()) {
            rs.enable("jdk.ExceptionThrown");
            rs.onEvent(e -> System.out.println(e.getString("message")));
            rs.start();
        }

        // EventStream.start("jdk.JavaMonitorEnter", "threshold", "20 ms",
        // "stackTrace", "false")
        // .addConsumer(System.out::println);
        //
        // EventStream.start("jdk.CPULoad", "period", "1 s")
        // .addConsumer(e -> System.out.println(100 *
        // e.getDouble("totalMachine") + " %"));
        //
        // EventStream.start("jdk.GarbageCollection")
        // .addConsumer(e -> System.out.println("GC: " + e.getStartTime() + "
        // maxPauseTime=" + e.getDuration("maxPauseTime").toMillis() + " ms"));

        Thread.sleep(100_000);
    }

    // Use case: Event Forwarding
    //
    // - Forward arbitrary event to frameworks such as RxJava, JSON/XML and
    // Kafka
    // - Handle flooding
    // - Performant
    // - Graceful shutdown
    // - Non-goals: Filter events
    //
    public static void eventForwarding() throws InterruptedException, IOException, ParseException {
        // KafkaProducer producer = new KafkaProducer<String, String>();
        try (RecordingStream rs = new RecordingStream(Configuration.getConfiguration("default"))) {
            rs.setMaxAge(Duration.ofMinutes(5));
            rs.setMaxSize(1000_000_000L);
            // es.setParallel(true);
            // es.setReuse(true);
            // es.consume(e -> producer.send(new ProducerRecord<String,
            // String>("topic",
            // e.getString("key"), e.getString("value"))));
            rs.start();
        }
        // Write primitive values to XML
        try (RecordingStream rs = new RecordingStream(Configuration.getConfiguration("deafult"))) {
            try (PrintWriter p = new PrintWriter(new FileWriter("recording.xml"))) {
                // es.setParallel(false);
                // es.setReuse(true);
                p.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
                p.println("<events>");
                rs.onEvent(e -> {
                    EventType type = e.getEventType();
                    p.println("  <event type=\"" + type.getName() + "\" start=\"" + e.getStartTime() + "\" end=\"" + e.getEndTime() + "\">");
                    for (ValueDescriptor field : e.getEventType().getFields()) {
                        Object value = e.getValue(field.getName());
                        if (value instanceof Number || field.getTypeName().equals("java.lang.String")) {
                            p.println("    <value field=\"" + field.getName() + "\">" + value + "</value>");
                        }
                    }
                });
                rs.start();
                p.println("</events>");
            }
        }
    }

    // Use case: Repository Access
    //
    // - Read the disk repository from another process, for example a side car
    // in
    // Docker container
    // - Be able to configure flush interval from command line or jcmd.
    // - Graceful shutdown
    //
    public static void repositoryAccess() throws IOException, InterruptedException {
        Path repository = Paths.get("c:\\repository").toAbsolutePath();
        String command = new String();
        command += "java -XX:StartFlightRecording:flush=2s";
        command += "-XX:FlightRecorderOption:repository=" + repository + " Application";
        Process myProcess = Runtime.getRuntime().exec(command);
        try (RecordingStream rs = new RecordingStream()) {
            rs.onEvent(System.out::println);
            rs.startAsync();
            Thread.sleep(10_000);
            myProcess.destroy();
            Thread.sleep(10_000);
        }
    }

    // Use: Tooling
    //
    // - Monitor a stream of data for a very long time
    // - Predictable interval, i.e. once every second
    // - Notification with minimal delay
    // - Events with the same period should arrive together
    // - Consume events in chronological order
    // - Low overhead
    //
    public static void tooling() throws IOException, ParseException {
        ArrayDeque<Double> measurements = new ArrayDeque<>();
        try (RecordingStream rs = new RecordingStream(Configuration.getConfiguration("profile"))) {
            rs.setInterval(Duration.ofSeconds(1));
            rs.setMaxAge(Duration.ofMinutes(1));
            // rs.setOrdered(true);
            // rs.setReuse(false);
            // rs.setParallel(true);
            rs.onEvent("jdk.CPULoad", e -> {
                double d = e.getDouble("totalMachine");
                measurements.addFirst(d);
                if (measurements.size() > 60) {
                    measurements.removeLast();
                }
                // repaint();
            });
            rs.start();
        }
    }

    // Use case: Low Impact
    //
    // - Support event subscriptions in a low latency environment (minimal GC
    // pauses)
    // - Filter out relevant events to minimize disk overhead and allocation
    // pressure
    // - Avoid impact from other recordings
    // - Avoid Heisenberg effects, in particular self-recursion
    //
    // Non-goals: one-liner
    //
    public static void lowImpact() throws InterruptedException, IOException, ParseException {
        try (RecordingStream rs = new RecordingStream()) {
            rs.enable("jdk.JavaMonitorEnter#threshold").withThreshold(Duration.ofMillis(10));
            rs.enable("jdk.ExceptionThrow#enabled");
            // ep.setReuse(true);
            rs.onEvent("jdk.JavaMonitorEnter", System.out::println);
            rs.onEvent("jdk.ExceptionThrow", System.out::println);
            rs.start();
            ;
        }
    }
}
