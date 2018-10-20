/*
 * Copyright (c)  2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@code ParameterizedRowCountOperation} is a {@link ParameterizedOperation} that returns a count.
 *
 * @param <T> the type of the result of this {@link Operation}
 */
public interface ParameterizedRowCountOperation<T> extends ParameterizedOperation<T>, RowCountOperation<T> {
  
  /**
   * Returns a {@link RowOperation} to process the auto-generated keys, if any, returned
   * by this {@link Operation}. If no keys are named the columns of the returned
   * rows are implementation dependent. If keys are specified the columns of the
   * returned rows are the keys. The {@link RowOperation} must be submitted before this 
   * {@link Operation} is submitted. If it has not submitting this {@link Operation} will
   * result throw {@link IllegalStateException}.
   * 
   * ISSUE: Should this be in {@link RowCountOperation}?
   * 
   * @param keys the names of the returned columns or null.
   * @return A RowOperation that will process the auto-generated keys
   * @throws IllegalStateException if this method has already been called on this
   * {@link Operation} or if this {@link Operation} has already been submitted.
   */
  public RowOperation<T> returning(String ... keys);
  
  // Covariant overrides
  
  /**
   * {@inheritDoc}
   * 
   * @return this {@code ParameterizedRowCountOperation}
   */
  @Override
  public ParameterizedRowCountOperation<T> onError(Consumer<Throwable> handler);
  
  /**
   * {@inheritDoc}
   * 
   * @return this {@code ParameterizedRowCountOperation}
   */
  @Override
  ParameterizedRowCountOperation<T> apply(Function<Result.RowCount, ? extends T> processor);
  
  /**
   * {@inheritDoc}
   * 
   * @return this {@code ParameterizedRowCountOperation}
   */
  @Override
  public ParameterizedRowCountOperation<T> set(String id, Object value);

  /**
   * {@inheritDoc}
   * 
   * @return this {@code ParameterizedRowCountOperation}
   */
  @Override
  public ParameterizedRowCountOperation<T> set(String id, Object value, SqlType type);

  /**
   * {@inheritDoc}
   * 
   * @return this {@code ParameterizedRowCountOperation}
   */
  @Override
  public ParameterizedRowCountOperation<T> set(String id, CompletionStage<?> source);

  /**
   * {@inheritDoc}
   * 
   * @return this {@code ParameterizedRowCountOperation}
   */
  @Override
  public ParameterizedRowCountOperation<T> set(String id, CompletionStage<?> source, SqlType type);

  /**
   * {@inheritDoc}
   * 
   * @return this {@code ParameterizedRowCountOperation}
   */
  @Override
  public ParameterizedRowCountOperation<T> timeout(Duration minTime);

}
