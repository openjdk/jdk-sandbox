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

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 *
 * A multi-operation is an {@link Operation} that returns one or more results in
 * addition to the result defined by the {@link Operation}. A {@link DynamicMultiOperation} is a
 * multi-operation where the number and types of the results are determined at
 * execution.
 * 
 * NOTE: In general one way to do things is sufficient however the API provides
 * two ways to handle multiple results. This way, the dynamic way, is required
 * because the number and type of results cannot always be known in advance. The
 * static way is also provided because it is much easier to use when the number
 * and type of results is known. The improvement in ease of use outweighs the
 * duplication IMO. If necessary one or the other can be eliminated. Eliminating
 * dynamic reduces functionality. Eliminating static reduces ease of use in what
 * I believe to be a common case.
 *
 * @param <T> type of the result of this DynamicMultiOperation
 */
public interface DynamicMultiOperation<T> extends OutOperation<T> {

  /**
   * Provides a handler for count results. The provided handler is called for
   * each count result. When called the first argument is the number of results
   * that preceeded the current result. The second argument is a CountOperation
   * that will process the current result. This CountOperation has not been
   * configured in any way nor has it been submitted. The handler configures the
   * CountOperation and submits it. The count result is processed when the 
   * CountOperation is submitted.
   * 
   * If this method is not called any count result is ignored.
   *
   * @param handler not null
   * @return this DynamicMultiOperation
   * @throws IllegalStateException if the CountOperation has not been submitted
   * when the call to the handler returns
   */
  public DynamicMultiOperation<T> onCount(BiConsumer<Integer, CountOperation<T>> handler);

  /**
   * Provides a handler for row sequence results. The provided handler is called for
   * each row sequence result. When called the first argument is the number of results
   * that preceeded the current result. The second argument is a RowOperation
   * that will process the current result. This RowOperation has not been
   * configured in any way nor has it been submitted. The handler configures the
   * RowOperation and submits it. The row sequence result is processed when the 
   * RowOperation is submitted.
   * 
   * If this method is not called any row sequence result is ignored.
   *
   * @param handler the error handler for this operation
   * @return This DynamicMultiOperation
   * @throws IllegalStateException if the RowOperation has not been submitted
   * when the call to the handler returns
   */
  public DynamicMultiOperation<T> onRows(BiConsumer<Integer, RowOperation<T>> handler);
  
  /**
   * Provides an error handler for this {@link Operation}. The provided handler 
   * is called for each error that occurs. When called the first argument is the 
   * number of results, including errors, that preceeded the current error. The
   * second argument is a {@link Throwable} corresponding to the error. When the
   * handler returns processing of the DynamicMultiOperation results continues. 
   * Only one onError method may be called.
   * 
   * @param handler a BiConsumer that handles an error
   * @return this DynamicMultiOperation
   * @throws IllegalStateException if any onError method was called previously
   */
  public DynamicMultiOperation<T> onError(BiConsumer<Integer, Throwable> handler);

  // Covariant overrides
  
  /**
   * Provides an error handler for this {@link Operation}. If execution of this
   * {@link Operation} results in an error, before the Operation is completed,
   * the handler is called with the {@link Throwable} as the argument. When the
   * handler returns the {@link Operation} is completed exceptionally with the 
   * {@link Throwable}.
   * 
   * @param handler the error handler for this operation
   * @return this DynamicMultiOperation
   * @throws IllegalStateException if any onError method was called previously
   */
  @Override
  public DynamicMultiOperation<T> onError(Consumer<Throwable> handler);
  
  @Override
  public DynamicMultiOperation<T> outParameter(String id, SqlType type);
  
  @Override
  public DynamicMultiOperation<T> apply(Function<Result.OutParameterMap, ? extends T> processor);

  @Override
  public DynamicMultiOperation<T> set(String id, Object value);

  @Override
  public DynamicMultiOperation<T> set(String id, Object value, SqlType type);

  @Override
  public DynamicMultiOperation<T> set(String id, CompletionStage<?> source);

  @Override
  public DynamicMultiOperation<T> set(String id, CompletionStage<?> source, SqlType type);

  @Override
  public DynamicMultiOperation<T> timeout(Duration minTime);

}
