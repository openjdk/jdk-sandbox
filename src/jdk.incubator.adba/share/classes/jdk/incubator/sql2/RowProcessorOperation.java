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
import java.util.concurrent.Flow;
import java.util.function.Consumer;

/**
 * A RowProcessorOperation handles a database action that returns a sequence of 
 * rows. The rows are handled by a java.util.concurrent.Flow.Processor. A 
 * RowProcessorOperation is conceptually a Row Publisher and a result Subscriber.
 * The configured Processor transforms the Row stream into a result stream.
 * Configuring a Processor causes the RowProcessorOperation to subscribe the
 * Processor to the stream of rows and for the RowProcessorOperation itself to 
 * subscribe to the stream of results published by the Processor. The last
 * result produced is the result of the RowProcessorOperation.
 * 
 * The RowProcessorOperation will insure that the demand for results is positive.
 * 
 * Calling Submission.cancel will call cancel on the result Subscription. The
 * Processor should call cancel on the row Subscription when cancel is called on
 * the result Subscription.
 * 
 * @param <T> the type of the result of the {@link Operation}
 */
public interface RowProcessorOperation<T> extends ParameterizedOperation<T> {

  /** DRAFT
   * Accepts a Processor that subscribes to a stream of Rows and publishes
   * a stream of result values. This Operation will subscribe to the stream of
   * results. The last published result value is the result of the
   * Operation.
   * 
   * This Operation will insure result demand is eventually positive until 
   * resultSubscriber.onComplete or resultSubscriber.onError is called. This 
   * Operation will call resultSubscription.cancel if this Operation is canceled.
   * 
   * While there are more rows and row demand is positive and rowSubscription.cancel
   * has not been called, this Operation will eventually call rowToResult.onNext
   * with the next row in the row sequence. The Result.Row argument to onNext is
   * only valid for the duration of that call. When there are no more Rows this 
   * Operation will call rowToResult.onComplete. If there is an error this
   * Operation will call rowToResult.onError with the appropriate Exception whether
   * or not any rows were published.
   *
   * If resultSubscriber.onError is called this Operation completes
   * exceptionally with the passed exception. After all rows are published if
   * neither resultSubscriber.onComplete or resultSubscriber.onError is
   * called this Operation will complete exceptionally after the inactivity
   * timeout expires.
   * 
   * If this Operation is skipped it will be completed exceptionally with
   * SqlSkippedException but no calls will be made to rowToResult.
   * 
   * Calling Row.cancel is the same as calling Subscription.cancel on the Row
   * Subscription.
   *
   * @param rowToResult subscribes to a stream of Result.Rows and publishes a
   * stream of results of type T
   * @return this RowProcessorOperation
   */
  public RowProcessorOperation<T> rowProcessor(Flow.Processor<Result.Row, ? extends T> rowToResult);
  
  /** DRAFT 
   * Subscribe to the stream of Rows returned by this Operation. The result of 
   * this Operation is null. This is a convenience method.
   * 
   * @param rowSubscriber subscribes to a stream of Result.Rows 
   * @return this RowProcessorOperation
   */
  public default RowProcessorOperation<T> subscribe(Flow.Subscriber<Result.Row> rowSubscriber) {

    // create a Row to result Processor that passes the Rows to rowSubscriber
    // and publishes a single null as its only result.
    Flow.Processor<Result.Row, T> rowToResult
            = new Flow.Processor<Result.Row, T>() {

      protected boolean isResultPending = false;
      protected long resultDemand = 0;

      protected Flow.Subscription rowSubscription;

      protected Flow.Subscriber<? super T> resultSubscriber;

      protected Flow.Subscription resultSubscription = new Flow.Subscription() {
        @Override
        public void request(long n) {
          resultDemand += n;
          if (isResultPending && resultDemand > 0) {
            resultSubscriber.onNext(null);
            resultDemand--;
            resultSubscriber.onComplete();
            isResultPending = false;
          }
        }

        @Override
        public void cancel() {
          rowSubscription.cancel();
        }
      };

      @Override
      public void onSubscribe(Flow.Subscription subscription) {
        rowSubscription = subscription;
        rowSubscriber.onSubscribe(rowSubscription);

      }

      @Override
      public void onNext(Result.Row item) {
        rowSubscriber.onNext(item);
      }

      @Override
      public void onError(Throwable throwable) {
        rowSubscriber.onError(throwable);
        resultSubscriber.onError(throwable);
      }

      @Override
      public void onComplete() {
        rowSubscriber.onComplete();
        if (resultDemand > 0) {
          resultSubscriber.onNext(null);
          resultSubscriber.onComplete();
        } else {
          isResultPending = true;
        }
      }

      @Override
      public void subscribe(Flow.Subscriber<? super T> subscriber) {
        resultSubscriber = subscriber;
        resultSubscriber.onSubscribe(resultSubscription);
      }
    };

    return rowProcessor(rowToResult);
  }

  /** DRAFT
   * Sets the minimum time the Operation will wait for Processor activity before
   * terminating. If all of the following hold for some time exceeding minTime,
   * this Operation will be completed exceptionally with 
   * {@link java.util.concurrent.TimeoutException}.
   * <ul>
   * <li>no calls to the onNext, onComplete, or onError methods of the result
   * Subscriber, ie the Subscriber passed to rowToResult.subscribe</li>
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
   * @param minTime minimum time with the Processor making no progress before the
   * Operation is terminated.
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
