/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package jdk.incubator.http.internal.hpack;

import jdk.incubator.http.internal.hpack.HPACK.Logger.Level;
import jdk.internal.vm.annotation.Stable;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static jdk.incubator.http.internal.hpack.HPACK.Logger.Level.EXTRA;
import static jdk.incubator.http.internal.hpack.HPACK.Logger.Level.NONE;
import static jdk.incubator.http.internal.hpack.HPACK.Logger.Level.NORMAL;

/**
 * Internal utilities and stuff.
 */
public final class HPACK {

    private static final RootLogger LOGGER;
    private static final Map<String, Level> logLevels =
            Map.of("NORMAL", NORMAL, "EXTRA", EXTRA);

    static {
        String PROPERTY = "jdk.internal.httpclient.hpack.log.level";

        String value = AccessController.doPrivileged(
                (PrivilegedAction<String>) () -> System.getProperty(PROPERTY));

        if (value == null) {
            LOGGER = new RootLogger(NONE);
        } else {
            String upperCasedValue = value.toUpperCase();
            Level l = logLevels.get(upperCasedValue);
            if (l == null) {
                LOGGER = new RootLogger(NONE);
                LOGGER.log(() -> format("%s value '%s' not recognized (use %s); logging disabled",
                                        PROPERTY, value, logLevels.keySet().stream().collect(joining(", "))));
            } else {
                LOGGER = new RootLogger(l);
                LOGGER.log(() -> format("logging level %s", l));
            }
        }
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    private HPACK() { }

    /**
     * The purpose of this logger is to provide means of diagnosing issues _in
     * the HPACK implementation_. It's not a general purpose logger.
     */
    public static class Logger {

        /**
         * Log detail level.
         */
        public enum Level {

            NONE(0),
            NORMAL(1),
            EXTRA(2);

            private final int level;

            Level(int i) {
                level = i;
            }

            public final boolean implies(Level other) {
                return this.level >= other.level;
            }
        }

        private final String name;
        @Stable
        private final Logger[] path; /* A path to parent: [root, ..., parent, this] */

        private Logger(Logger[] path, String name) {
            Logger[] p = Arrays.copyOfRange(path, 0, path.length + 1);
            p[path.length] = this;
            this.path = p;
            this.name = name;
        }

        protected final String getName() {
            return name;
        }
        /*
         * Usual performance trick for logging, reducing performance overhead in
         * the case where logging with the specified level is a NOP.
         */

        public boolean isLoggable(Level level) {
            return isLoggable(path, level);
        }

        public void log(Level level, Supplier<? extends CharSequence> s) {
            log(path, level, s);
        }

        public Logger subLogger(String name) {
            return new Logger(path, name);
        }

        protected boolean isLoggable(Logger[] path, Level level) {
            return parent().isLoggable(path, level);
        }

        protected void log(Logger[] path,
                           Level level,
                           Supplier<? extends CharSequence> s) {
            parent().log(path, level, s);
        }

        protected final Logger parent() {
            return path[path.length - 2];
        }
    }

    private static final class RootLogger extends Logger {

        private final Level level;
        @Stable
        private final Logger[] path = { this };

        protected RootLogger(Level level) {
            super(new Logger[]{ }, "hpack");
            this.level = level;
        }

        @Override
        protected boolean isLoggable(Logger[] path, Level level) {
            return this.level.implies(level);
        }

        @Override
        public void log(Level level, Supplier<? extends CharSequence> s) {
            log(path, level, s);
        }

        @Override
        protected void log(Logger[] path,
                           Level level,
                           Supplier<? extends CharSequence> s) {
            if (this.level.implies(level)) {
                log(path, s);
            }
        }

        public void log(Supplier<? extends CharSequence> s) {
            log(path, s);
        }

        private void log(Logger[] path, Supplier<? extends CharSequence> s) {
            StringBuilder b = new StringBuilder();
            for (Logger p : path) {
                b.append('/').append(p.getName());
            }
            System.out.println(b.toString() + ' ' + s.get());
        }
    }
}
