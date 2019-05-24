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

package jdk.jfr.internal.consumer;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class InternalEventFilter {
    public static final InternalEventFilter ACCEPT_ALL = new InternalEventFilter();
    private final Map<String, Long> thresholds = new HashMap<>();
    private boolean acceptAll;

    public static InternalEventFilter merge(Collection<InternalEventFilter> filters) {
        for (InternalEventFilter ef : filters) {
            if (ef.getAcceptAll()) {
                return ACCEPT_ALL;
            }
        }
        if (filters.size() == 1) {
            return filters.iterator().next();
        }

        Set<String> eventNames = new HashSet<>();
        for (InternalEventFilter ef : filters) {
            eventNames.addAll(ef.thresholds.keySet());
        }
        InternalEventFilter result = new InternalEventFilter();
        for (String eventName : eventNames) {
            for (InternalEventFilter ef : filters) {
                Long l = ef.thresholds.get(eventName);
                if (l != null) {
                    result.setThreshold(eventName, l.longValue());
                }
            }
        }
        return result;
    }

    private boolean getAcceptAll() {
        return acceptAll;
    }

    public void setAcceptAll() {
        acceptAll = true;
    }

    public void setThreshold(String eventName, long nanos) {
        Long l = thresholds.get(eventName);
        if (l != null) {
            l = Math.min(l, nanos);
        } else {
            l = nanos;
        }
        thresholds.put(eventName, l);
    }

    public long getThreshold(String eventName) {
        if (acceptAll) {
            return 0;
        }
        Long l = thresholds.get(eventName);
        if (l != null) {
            return l;
        }
        return -1;
    }
    public String toString() {
        if (acceptAll) {
            return "ACCEPT ALL";
        }
        StringBuilder sb = new StringBuilder();
        for (String key : thresholds.keySet().toArray(new String[0])) {
            Long value = thresholds.get(key);
            sb.append(key);
            sb.append(" = ");
            sb.append(value.longValue() / 1_000_000);
            sb.append(" ms");
        }
        return sb.toString();
    }
}