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
package java.sql2;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

/**
 *
 * @param <T> the type of the result of the {@link Operation}
 */
public interface RowProcessorOperation<T> extends ParameterizedOperation<T> {

  /** DRAFT
   * Accepts a Processor that subscribes to a stream of Rows and publishes
   * a stream of result values. The last result value published is the result
   * of the Operation. If no value is published the result of Operation is null.
   *
   * The result of this Operation is the last value passed to the onNext method
   * of the Subscriber passed to rowProcessor.subscribe. If onComplete
   * is called before any value is passed to onNext this Operation is completed
   * with null. If onError is called this Operation completes exceptionally
   * with the passed exception. If neither onComplete or onError is called
   * this Operation will complete exceptionally after the inactivity timeout
   * expires.
   * 
   * Calling Row.cancel is the same as calling Subscription.cancel on the Row
   * Subscription.
   *
   * @param rowToResult subscribes to a stream of Result.Rows and publishes a
   * stream of results of type T
   * @return this RowProcessorOperation
   */
  public RowProcessorOperation<T> rowProcessor(Flow.Processor<Result.Row, T> rowToResult);
  
  /** DRAFT
   * Sets the minimum time the Operation will wait for Processor activity before
   * terminating. If all of the following hold for some time exceeding minTime,
   * this Operation will be completed exceptionally with 
   * {@link java.util.concurrent.TimeoutException}.
   * <ul>
   * <li>no calls to the onNext, onComplete, or
   * onError methods of the Subscriber passed to rowToResult.subscribe</li>
   * <li>the demand for Rows is zero or all rows have been published</li>
   * </ul>
   * If the Operation can publish no more rows either because all rows have
   * been published or because the demand for rows is 0 and rowToResult
   * has neither published a result nor terminated the stream and this state has
   * continued for at least minTime, the Operation is completed exceptionally.
   * 
   * The default value is one minute.
   * 
   * Note: The minTime parameter value must be small to guarantee that the 
   * Connection does not hang for long periods. The default is large enough
   * that it most likely is insignificant for most apps, but small enough to
   * kick loose a hung Connection in semi-reasonable time.
   * 
   * @param minTime
   * @return this RowProcessorOperation
   */
  public RowProcessorOperation<T> inactivityTimeout(Duration minTime);
  
  // Covariant overrides
  
  @Override
  public RowProcessorOperation<T> onError(Consumer<Throwable> handler);
  
  @Override
  public RowProcessorOperation<T> set(String id, Object value, SqlType type);

  @Override
  public RowProcessorOperation<T> set(String id, CompletionStage<?> source, SqlType type);

  @Override
  public RowProcessorOperation<T> set(String id, CompletionStage<?> source);

  @Override
  public RowProcessorOperation<T> set(String id, Object value);

  @Override
  public RowProcessorOperation<T> timeout(Duration minTime);

}
