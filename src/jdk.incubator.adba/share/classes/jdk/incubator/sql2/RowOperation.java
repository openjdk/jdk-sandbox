/*
 * Copyright (c)  2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.sql2;

import java.util.function.Consumer;
import java.util.stream.Collector;

/**
 * A {@link RowOperation} is a database operation that returns a row sequence.
 * 
 * @param <T> the type of the result of this {@link Operation}
 */
public interface RowOperation<T> extends Operation<T> {

  /**
   * A hint to the implementation of how many rows to fetch in each database
   * access. Implementations are free to ignore it.
   * 
   * @param rows suggested number of rows to fetch per access
   * @return this {@link RowOperation}
   * @throws IllegalArgumentException if row &lt; 1
   * @throws IllegalStateException if this method had been called previously or
   * this Operation has been submitted.
   */
  public RowOperation<T> fetchSize(long rows) throws IllegalArgumentException;
  
  /**
   * Provides a {@link Collector} to reduce the sequence of rows.The result of
   * the {@link Operation} is the result of calling finisher on the final
   * accumulated result. If the {@link Collector} is
   * {@link Collector.Characteristics#UNORDERED} rows may be accumulated out of
   * order. If the {@link Collector} is
   * {@link Collector.Characteristics#CONCURRENT} then the sequence of rows may be
   * split into subsequences that are reduced separately and then combined.
   *
   * @param <A> the type of the accumulator
   * @param <S> the type of the final result
   * @param c the Collector. Not null. 
   * @return This RowOperation
   * @throws IllegalStateException if this method had been called previously or
   * this Operation has been submitted.
  */
  public <A, S extends T> RowOperation<T> collect(Collector<? super Result.Row, A, S> c);

  @Override
  public RowOperation<T> onError(Consumer<Throwable> handler);
  
}
