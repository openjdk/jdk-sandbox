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

import java.util.Spliterator;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

/**
 * Snippets used in SpliteratorSnippets.
 */ 

final class SpliteratorSnippets {

  private class TaggedArray<T> {
      private Spliterator<T> spliterator(){
          return null;
      }
    }

// @start region=snippet1 :
 static <T> void parEach(TaggedArray<T> a, Consumer<T> action) {
   Spliterator<T> s = a.spliterator();
   long targetBatchSize = s.estimateSize() / (ForkJoinPool.getCommonPoolParallelism() * 8);
   new ParEach(null, s, action, targetBatchSize).invoke();
 }

 static class ParEach<T> extends CountedCompleter<Void> {
   final Spliterator<T> spliterator;
   final Consumer<T> action;
   final long targetBatchSize;

   ParEach(ParEach<T> parent, Spliterator<T> spliterator,
           Consumer<T> action, long targetBatchSize) {
     super(parent);
     this.spliterator = spliterator; this.action = action;
     this.targetBatchSize = targetBatchSize;
   }

   public void compute() {
     Spliterator<T> sub;
     while (spliterator.estimateSize() > targetBatchSize &&
            (sub = spliterator.trySplit()) != null) {
       addToPendingCount(1);
       new ParEach<>(this, sub, action, targetBatchSize).fork();
     }
     spliterator.forEachRemaining(action);
     propagateCompletion();
   }
 }
// @end snippet1


}
