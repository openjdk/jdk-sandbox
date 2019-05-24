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

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

final class EventFilter {
    private final String[] eventNames;
    private final Duration threshold;
    private final String[] fields;

    private EventFilter(String[] eventNames, Duration threshold, String[] fields) {
        this.eventNames = eventNames;
        this.threshold = threshold;
        this.fields = fields;
    }

    public static EventFilter eventTypes(String... eventNames) {
        return new EventFilter(eventNames.clone(), null, new String[0]);
    }

    public EventFilter aboveThreshold(Duration threshold) {
        return new EventFilter(eventNames, threshold, fields);
    }

    public EventFilter mustHaveFields(String... fieldNames) {
        return new EventFilter(eventNames, threshold, fieldNames);
    }



    public EventFilter onlyThreads(Thread... t) {
        return this;
    }

    public EventFilter onlyThreadIds(long... threadId) {
        return this;
    }

    public EventFilter onlyThreadNames(String... threadName) {
        return this;
    }

    public EventFilter threadFilters(String... filter) {
        return this;
    }

    List<String> getFields() {
        return Arrays.asList(fields);
    }

    List<String> getEventNames() {
        return Arrays.asList(fields);
    }

    Duration getThreshold() {
        return threshold;
    }

}
