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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A multi-operation is an {@link Operation} that returns one or more results in
 * addition to the out result defined by the {@link Operation}. Each result is
 * processed by an Operation. The Operations can be created by calling
 * rowOperation, rowProcessorOperation, or countOperation if the kind of results
 * is known. These results are processed in the order the Operations are
 * submitted. Any results not processed by an explicit Operation is processed by
 * calling the appropriate handler specified by onRows or onCount. If any result
 * is an error that error is processed by calling the handler specified by
 * onError. If the appropriate handler is not specified that result is ignored,
 * including errors.
 *
 * ISSUE: Should this have a collector?
 *
 * @param <T> The type of the result of this {@link Operation}
 */
public interface MultiOperation<T> extends OutOperation<T> {

  /**
   * Returns a {@link RowOperation} to process a row sequence result. The
   * {@link Operation}s are executed in the order they are submitted. If a
   * result is of the wrong type for the next submitted {@link Operation} the
   * {@link MultiOperation} is completed with {@link IllegalStateException}.
   *
   * @return a {@link RowOperation} that is part of this {@link MultiOperation}
   */
  public RowOperation<T> rowOperation();

  /**
   * Returns a {@link RowPublisherOperation} to process a row sequence result.
   * The {@link Operation}s are executed in the order they are submitted. If a
   * result is of the wrong type for the next submitted {@link Operation} the
   * {@link MultiOperation} is completed with {@link IllegalStateException}.
   *
   * @return a {@link RowPublisherOperation} that is part of this
   * {@link MultiOperation}
   */
  public RowPublisherOperation<T> rowProcessorOperation();

  /**
   * Returns a {@link RowCountOperation} to process a count result. The
   * {@link Operation}s are executed in the order they are submitted. If a
   * result is of the wrong type for the next submitted Operation the
   * {@link MultiOperation} is completed with {@link IllegalStateException}.
   *
   * @return a {@link RowCountOperation} that is part of this
   * {@link MultiOperation}
   */
  public RowCountOperation<T> rowCountOperation();

  /**
   * Provides a handler for trailing count results. The provided handler is
   * called for each count result not processed by RowCountOperation. When
   * called the first argument is the number of results that preceeded the
   * current result. The second argument is a {@link RowCountOperation} that
   * will process the current result. This {@link RowCountOperation} has not
   * been configured in any way nor has it been submitted. The handler
   * configures the {@link RowCountOperation} and submits it. The count result
   * is processed when the {@link RowCountOperation} is submitted. If the
   * {@link RowCountOperation} is not submitted when the handler returns the
   * count result is ignored.
   *
   * If this method is not called any trailing count results are ignored.
   *
   * @param handler not null
   * @return this MultiOperation
   * @throws IllegalStateException if this method was called previously
   */
  public MultiOperation<T> onCount(BiConsumer<Integer, RowCountOperation<T>> handler);

  /**
   * Provides a handler for trailing row sequence results. The provided handler
   * is called for each row sequence result not processed by a RowOperation.
   * When called the first argument is the number of results that preceeded the
   * current result. The second argument is a {@link RowOperation} that will
   * process the current result. This {@link RowOperation} has not been
   * configured in any way nor has it been submitted. The handler configures the
   * {@link RowOperation} and submits it. The row sequence result is processed
   * when the {@link RowOperation} is submitted. If the {@link RowOperation} is
   * not submitted when the handler returns, the row sequence result is ignored.
   *
   * If this method is not called any trailing row sequence results are ignored.
   *
   * ISSUE: Should there be a version of this method that provides
   * RowProcessorOperations? If so only one of that method or this one can be
   * called.
   *
   * @param handler
   * @return This MultiOperation
   * @throws IllegalStateException if this method was called previously
   */
  public MultiOperation<T> onRows(BiConsumer<Integer, RowOperation<T>> handler);

  /**
   * Provides an error handler for this {@link Operation}. The provided handler
   * is called for each error that occurs. When called the first argument is the
   * number of results, including errors, that preceeded the current error. The
   * second argument is a {@link Throwable} corresponding to the error. When the
   * handler returns processing of the MultiOperation results continues. Only
   * one onError method may be called.
   *
   * @param handler a BiConsumer that handles an error
   * @return this MultiOperation
   * @throws IllegalStateException if this method or 
   * {@link MultiOperation#onError(java.util.function.Consumer)} was called 
   * previously
   */
  public MultiOperation<T> onError(BiConsumer<Integer, Throwable> handler);
  // Covariant overrides

  /**
   * This handler is called if the execution fails completely. If the execution
   * returns any individual results, even if any or all of those results are
   * errors, this handler is not called.
   *
   * @param handler
   * @return
   */
  @Override
  public MultiOperation<T> onError(Consumer<Throwable> handler);

  @Override
  public MultiOperation<T> apply(Function<Result.OutColumn, ? extends T> processor);

  @Override
  public MultiOperation<T> outParameter(String id, SqlType type);

  @Override
  public MultiOperation<T> set(String id, Object value, SqlType type);

  @Override
  public MultiOperation<T> set(String id, Object value);

  @Override
  public MultiOperation<T> set(String id, CompletionStage<?> source, SqlType type);

  @Override
  public MultiOperation<T> set(String id, CompletionStage<?> source);

  @Override
  public MultiOperation<T> timeout(Duration minTime);

}
