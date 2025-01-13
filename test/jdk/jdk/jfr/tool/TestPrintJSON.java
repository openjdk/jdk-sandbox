/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jdk.jfr.tool;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import java.util.json.Json;
import java.util.json.JsonArray;
import java.util.json.JsonObject;
import java.util.json.JsonString;
import java.util.json.JsonValue;
import jdk.jfr.Timespan;
import jdk.jfr.Timestamp;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;

/**
 * @test
 * @key jfr
 * @summary Tests print --json
 * @requires vm.hasJFR
 *
 * @library /test/lib /test/jdk
 * @modules jdk.jfr
 * @enablePreview
 * @run main/othervm jdk.jfr.tool.TestPrintJSON
 */
public class TestPrintJSON {

    public static void main(String... args) throws Throwable {

        Path recordingFile = ExecuteHelper.createProfilingRecording().toAbsolutePath();

        OutputAnalyzer output = ExecuteHelper.jfr("print", "--json", "--stack-depth", "999", recordingFile.toString());
        String json = output.getStdout();

        JsonValue out = Json.parse(json);

        List<RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);
        Collections.sort(events, new EndTicksComparator());
        Iterator<RecordedEvent> it = events.iterator();

        // Verify events are equal
        if (out instanceof JsonObject jsonObj && jsonObj.keys().get("recording") instanceof JsonObject recordings
                && recordings.keys().get("events") instanceof JsonArray jsonEvents) {
            for (JsonValue jsonEvent : jsonEvents.values()) {
                RecordedEvent recordedEvent = it.next();
                String typeName = recordedEvent.getEventType().getName();
                if (jsonEvent instanceof JsonObject joEvent
                        && joEvent.keys().get("type") instanceof JsonString type) {
                    Asserts.assertEquals(typeName, type.value());
                    assertEquals(jsonEvent, recordedEvent);
                } else {
                    jsonFail(JsonString.class);
                }
            }
            Asserts.assertFalse(events.size() != jsonEvents.values().size(), "Incorrect number of events");
        } else {
            jsonFail(JsonArray.class);
        }
    }

    private static void assertEquals(Object jsonObject, Object jfrObject) throws Exception {
        // Check object
        if (jfrObject instanceof RecordedObject) {
            RecordedObject recObject = (RecordedObject) jfrObject;
            if (jsonObject instanceof JsonObject jo && jo.keys().get("values") instanceof JsonObject values) {
                Asserts.assertEquals(values.keys().size(), recObject.getFields().size());
                for (ValueDescriptor v : recObject.getFields()) {
                    String name = v.getName();
                    Object jsonValue = values.keys().get(name);
                    Object expectedValue = recObject.getValue(name);
                    if (v.getAnnotation(Timestamp.class) != null) {
                        // Make instant of OffsetDateTime
                        String text = ((JsonString) jsonValue).value();
                        jsonValue = OffsetDateTime.parse(text).toInstant().toString();
                        expectedValue = recObject.getInstant(name);
                    }
                    if (v.getAnnotation(Timespan.class) != null) {
                        expectedValue = recObject.getDuration(name);
                    }
                    assertEquals(jsonValue, expectedValue);
                    return;
                }
            }
        }

        // Check array
        if (jfrObject != null && jfrObject.getClass().isArray()) {
            Object[] jfrArray = (Object[]) jfrObject;
            if (jsonObject instanceof JsonArray jsArray) {
                for (int i = 0; i < jfrArray.length; i++) {
                    assertEquals(jsArray.values().get(i), jfrArray[i]);
                }
            }
            return;
        }

        String jsonText = String.valueOf(jsonObject);
        // Double.NaN / Double.Inifinity is not supported by JSON format,
        // use null
        if (jfrObject instanceof Double) {
            double expected = ((Double) jfrObject);
            if (Double.isInfinite(expected) || Double.isNaN(expected)) {
                Asserts.assertEquals("null", jsonText);
                return;
            }
            double value = Double.parseDouble(jsonText);
            Asserts.assertEquals(expected, value);
            return;
        }
        // Float.NaN / Float.Inifinity is not supported by JSON format,
        // use null
        if (jfrObject instanceof Float) {
            float expected = ((Float) jfrObject);
            if (Float.isInfinite(expected) || Float.isNaN(expected)) {
                Asserts.assertEquals("null", jsonText);
                return;
            }
            float value = Float.parseFloat(jsonText);
            Asserts.assertEquals(expected, value);
            return;
        }
        if (jfrObject instanceof Integer) {
            Integer expected = ((Integer) jfrObject);
            double value = Double.parseDouble(jsonText);
            Asserts.assertEquals(expected.doubleValue(), value);
            return;
        }
        if (jfrObject instanceof Long) {
            Long expected = ((Long) jfrObject);
            double value = Double.parseDouble(jsonText);
            Asserts.assertEquals(expected.doubleValue(), value);
            return;
        }

        String jfrText = String.valueOf(jfrObject);
        Asserts.assertEquals(jfrText, jsonText, "Primitive values don't match. JSON = " + jsonText);
    }

    private static void jsonFail(Class<?> expected) {
        throw new RuntimeException(String.format(
                "Json in unexpected format. Final pattern match did not get: %s",expected));
    }
}
