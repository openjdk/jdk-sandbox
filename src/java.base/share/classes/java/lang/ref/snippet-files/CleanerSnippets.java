/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.Cleaner;

/**
 * Snippets used in CleanerSnippets.
 */ 

final class CleanerSnippets {
private static void snippet1() {
// @start region=snippet1 :
  class CleaningExample implements AutoCloseable { //@replace regex="class" replacement="public class"
        // A cleaner, preferably one shared within a library
        private static final Cleaner cleaner = null; //@replace regex="null" replacement="<cleaner>"

        static class State implements Runnable {

            State(int i) { //@replace regex="int i" replacement="..."
                // initialize State needed for cleaning action
            }

            public void run() {
                // cleanup action accessing State, executed at most once
            }
        }

        private State state; //@replace regex="private" replacement="private final"
        private Cleaner.Cleanable cleanable; //@replace regex="private" replacement="private final"

        public CleaningExample() {
            this.state = new State(1); //@replace regex="1" replacement="..."
            this.cleanable = cleaner.register(this, state);
        }

        public void close() {
            cleanable.clean();
        }
    }
// @end snippet1
}

}
