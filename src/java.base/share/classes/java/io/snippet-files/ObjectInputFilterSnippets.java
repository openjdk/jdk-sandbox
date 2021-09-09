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

/**
 * Snippets used in ObjectInputFilterSnippets.
 */

import java.io.ObjectInputFilter;
import java.util.function.BinaryOperator;

final class ObjectInputFilterSnippets {
// @start region=snippet3 :
 public static final class FilterInThread implements BinaryOperator<ObjectInputFilter> {

     private final ThreadLocal<ObjectInputFilter> filterThreadLocal = new ThreadLocal<>();

     // Construct a FilterInThread deserialization filter factory.
     public FilterInThread() {}

     // Returns a composite filter of the static JVM-wide filter, a thread-specific filter,
     // and the stream-specific filter.
     public ObjectInputFilter apply(ObjectInputFilter curr, ObjectInputFilter next) {
         if (curr == null) {
             // Called from the OIS constructor or perhaps OIS.setObjectInputFilter with no current filter
             var filter = filterThreadLocal.get();
             if (filter != null) {
                 // Wrap the filter to reject UNDECIDED results
                 filter = ObjectInputFilter.rejectUndecidedClass(filter);
             }
             if (next != null) {
                 // Merge the next filter with the thread filter, if any
                 // Initially this is the static JVM-wide filter passed from the OIS constructor
                 // Wrap the filter to reject UNDECIDED results
                 filter = ObjectInputFilter.merge(next, filter);
                 filter = ObjectInputFilter.rejectUndecidedClass(filter);
             }
             return filter;
         } else {
             // Called from OIS.setObjectInputFilter with a current filter and a stream-specific filter.
             // The curr filter already incorporates the thread filter and static JVM-wide filter
             // and rejection of undecided classes
             // If there is a stream-specific filter wrap it and a filter to recheck for undecided
             if (next != null) {
                 next = ObjectInputFilter.merge(next, curr);
                 next = ObjectInputFilter.rejectUndecidedClass(next);
                 return next;
             }
             return curr;
         }
     }

     // Applies the filter to the thread and invokes the runnable.
     public void doWithSerialFilter(ObjectInputFilter filter, Runnable runnable) {
         var prevFilter = filterThreadLocal.get();
         try {
             filterThreadLocal.set(filter);
             runnable.run();
         } finally {
             filterThreadLocal.set(prevFilter);
         }
     }
 }
// @end snippet3

}
