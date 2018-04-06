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
import java.util.logging.Logger;
import java.util.stream.Collector;

/**
 * A set of {@link Operation}s that share certain properties, are managed as a
 * unit, and are executed as a unit. The {@link Operation}s created by an
 * {@link OperationGroup} and submitted are the member {@link Operation}s of
 * that {@link OperationGroup}. An {@link OperationGroup} is not a transaction
 * and is not related to a transaction in any way.
 *
 * An {@link OperationGroup} conceptually has a collection of member
 * {@link Operation}s. When an {@link OperationGroup} is submitted it is placed
 * in the collection of the {@link OperationGroup} of which it is a member. The
 * member {@link OperationGroup} is executed according to the attributes of the
 * {@link OperationGroup} of which it is a member. The member {@link Operation}s
 * of an {@link OperationGroup} are executed according to the attributes of that
 * {@link OperationGroup}.
 *
 * How an {@link OperationGroup} is executed depends on its attributes.
 *
 * If an {@link OperationGroup} has a condition and the value of that condition
 * is {@link Boolean#TRUE} then execute the member {@link Operation}s as below.
 * If it is {@link Boolean#FALSE} then the {@link OperationGroup} is completed
 * with the value null. If the condition completed exceptionally then the
 * {@link OperationGroup} is completed exceptionally with a
 * {@link SqlSkippedException} that has that exception as its cause.
 *
 * If the {@link OperationGroup} is sequential the member {@link Operation}s are
 * executed in the order they were submitted. If it is parallel, they may be
 * executed in any order including simultaneously.
 *
 * If an {@link OperationGroup} is dependent and a member {@link Operation}
 * completes exceptionally the remaining member {@link Operation}s in the
 * collection are completed exceptionally with a {@link SqlSkippedException}
 * that has the initial {@link Exception} as its cause and the {@link OperationGroup}
 * is completed exceptionally with the initial {@link Exception}. A member
 * {@link Operation} in-flight may either complete normally or be completed
 * exceptionally but must complete one way or the other. [NOTE: Too strong?]
 *
 * If an {@link OperationGroup} is held additional member {@link Operation}s may
 * be submitted after the {@link OperationGroup} is submitted. If an
 * {@link OperationGroup} is not held, no additional member {@link Operation}s
 * may be submitted after the {@link OperationGroup} is submitted. If an
 * {@link OperationGroup} is held it will be completed only after it is released
 * or if conditional and the condition is not {@link Boolean#TRUE}. If a
 * {@link OperationGroup} is dependent, held, one of its member
 * {@link Operation}s completed exceptionally, and its queue is empty then the
 * {@link OperationGroup} is released.
 *
 * The result of this {@link OperationGroup} is the result of collecting the
 * results of its member {@link Operation}s. If the {@link OperationGroup} is 
 * dependent and one of its member {@link Operation}s completes exceptionally,
 * the {@link OperationGroup} is completed exceptionally.
 *
 * ISSUE: Currently no way to create a nested {@link OperationGroup}. That is a
 * intentional limitation but may be a simplification we can live with. Or not.
 *
 * @param <S> The type of the result of the member {@link Operation}s
 * @param <T> The type of the collected results the member {@link Operation}s
 */
public interface OperationGroup<S, T> extends Operation<T> {

  /**
   * Mark this {@link OperationGroup} as parallel. If this method is not called
   * the {@link OperationGroup} is sequential. If an {@link OperationGroup} is
   * parallel, member {@link Operation}s may be executed in any order including
   * in parallel. If an {@link OperationGroup} is sequential, the default,
   * member {@link Operation}s are executed strictly in the order they are
   * submitted.
   *
   * Note: There is no covariant override of this method in {@link Connection}
   * as there is only a small likelihood of needing it.
   *
   * @return this {@link OperationGroup}
   * @throws IllegalStateException if this {@link OperationGroup} has been
   * submitted or any member {@link Operation}s have been created.
   */
  public OperationGroup<S, T> parallel();

  /**
   * Mark this {@link OperationGroup} as independent. If this method is not
   * called the {@link OperationGroup} is dependent, the default. If an
   * {@link OperationGroup} is independent then failure of one member
   * {@link Operation} does not affect the execution of other member
   * {@link Operation}s. If an {@link OperationGroup} is dependent then failure
   * of one member {@link Operation} will cause all member {@link Operation}s
   * remaining in the queue to be completed exceptionally with a
   * {@link SqlSkippedException} with the cause set to the original exception.
   *
   * The result of this {@link OperationGroup}'s execution is the result of collecting the
   * results of the member {@link Operation}s that complete normally. 
   *
   * Note: There is no covariant override of this method in {@link Connection}
   * as there is only a small likelihood of needing it.
   *
   * @return this {@link OperationGroup}
   * @throws IllegalStateException f this {@link OperationGroup} blocks errors
   * or if this {@link OperationGroup} has been submitted or any member
   * {@link Operation}s have been created
   */
  public OperationGroup<S, T> independent();

  /**
   * Define a condition that determines whether the member {@link Operation}s of
   * this {@link OperationGroup} are executed or not. If and when this
   * {@link OperationGroup} is executed then if the condition argument is
   * completed with {@link Boolean#TRUE} the member {@link Operation}s are
   * executed. If {@link Boolean#FALSE} or if it is completed exceptionally the
   * member {@link Operation}s are not executed but are removed from the queue.
   * After all member {@link Operation}s have been removed from the queue this
   * {@link OperationGroup} is completed with {@code null}.
   *
   * Note: There is no covariant override of this method in Connection as there
   * is only a small likelihood of needing it.
   *
   * ISSUE: Should the member Operations be skipped or otherwise completed
   * exceptionally?
   *
   * @param condition a {@link CompletionStage} the value of which determines
   * whether this {@link OperationGroup} is executed or not
   * @return this OperationGroup
   * @throws IllegalStateException iif this {@link OperationGroup} has been
   * submitted or any member {@link Operation}s have been created
   */
  public OperationGroup<S, T> conditional(CompletionStage<Boolean> condition);

  /**
   * Mark this {@link OperationGroup} as held. It can be executed but cannot be
   * completed. A {@link OperationGroup} that is held remains in the queue even
   * if all of its current member {@link Operation}s have completed. So long as
   * the {@link OperationGroup} is held new member {@link Operation}s can be
   * submitted. A {@link OperationGroup} that is held must be released before it
   * can be completed and removed from the queue.
   *
   * Note: There is no covariant override of this method in Connection as there
   * is only a small likelihood of needing it.
   *
   * ISSUE: Need a better name.
   *
   * @return a Submission
   * @throws IllegalStateException if this {@link OperationGroup} has been
   * submitted
   */
  public Submission<T> submitHoldingForMoreMembers();

  /**
   * Allow this {@link OperationGroup} to be completed and removed from the
   * queue once all of its member {@link Operation}s have been completed. After
   * this method is called no additional member {@link Operation}s can be
   * submitted. Once all member {@link Operation}s have been removed from the
   * queue this {@link OperationGroup} will be completed and removed from the
   * queue.
   *
   * Calling this method when this {@link OperationGroup} is not held is a
   * no-op.
   *
   * Note: There is no covariant override of this method in Connection as there
   * is only a small likelihood of needing it.
   *
   * ISSUE: Need a better name.
   *
   * @return this OperationGroup
   * @throws IllegalStateException if this {@link OperationGroup} has been
   * completed
   */
  public OperationGroup<S, T> releaseProhibitingMoreMembers();

  /**
   * Provides a {@link Collector} to reduce the results of the member
   * {@link Operation}s. The result of this {@link OperationGroup} is the result
   * of calling finisher on the final accumulated result.If the
   * {@link Collector} is {@link Collector.Characteristics#UNORDERED} the member
   * {@link Operation} results may be accumulated out of order.If the
   * {@link Collector} is {@link Collector.Characteristics#CONCURRENT} then the
   * member {@link Operation} results may be split into subsets that are reduced
   * separately and then combined. If this {@link OperationGroup} is sequential,
   * the characteristics of the {@link Collector} only affect how the results of
   * the member {@link Operation}s are collected; the member {@link Operation}s
   * are executed sequentially regardless. If this {@link OperationGroup} is
   * parallel the characteristics of the {@link Collector} may influence the
   * execution order of the member {@link Operation}s.
   *
   * The default value is
   * {@code Collector.of(()->null, (a,t)->{}, (l,r)->null, a->null)}.
   *
   * @param c the Collector. Not null.
   * @return This OperationGroup
   * @throws IllegalStateException if called more than once or if this
   * {@link OperationGroup} has been submitted
   */
  public OperationGroup<S, T> collect(Collector<S, ?, T> c);
  
  /**
   * Returns an Operation that is never skipped. Skipping stops with a catchOperation
   * and the subsequent Operation is executed normally. The value of a 
   * catchOperation is always null.
   * 
   * @return an unskippable Operation;
   */
  public Operation<S> catchOperation();
  
  /**
   * Creates and submits a catch Operation. Convenience method.
   *
   * @return this OperationGroup
   */
  public default OperationGroup<S, T> catchErrors() {
    catchOperation().submit();
    return this;
  }

  /**
   * Return a new {@link ArrayCountOperation}.
   * <p>
   * Usage Note: Frequently use of this method will require a type witness to
   * enable correct type inferencing.
   * <pre><code>
   *   conn.<b>&lt;List&lt;Integer&gt;&gt;</b>arrayCountOperation(sql)
   *     .set ...
   *     .collect ...
   *     .submit ...
   * </code></pre>
   *
   * @param <R> the result type of the returned {@link ArrayCountOperation}
   * @param sql SQL to be executed. Must return an update count.
   * @return a new {@link ArrayCountOperation} that is a member of this
   * {@link OperationGroup}
   */
  public <R extends S> ArrayCountOperation<R> arrayCountOperation(String sql);

  /**
   * Return a new {@link CountOperation}.
   *
   * @param <R> the result type of the returned {@link CountOperation}
   * @param sql SQL to be executed. Must return an update count.
   * @return an new {@link CountOperation} that is a member of this
   * {@link OperationGroup}
   *
   */
  public <R extends S> ParameterizedCountOperation<R> countOperation(String sql);

  /**
   * Return a new {@link Operation} for a SQL that doesn't return any result,
   * for example DDL. The result of this Operation is always null.
   *
   * @param sql SQL for the {@link Operation}.
   * @return a new {@link Operation} that is a member of this
   * {@link OperationGroup}
   */
  public Operation<S> operation(String sql);

  /**
   * Return a new {@link OutOperation}. The SQL must return a set of zero or
   * more out parameters or function results.
   *
   * @param <R> the result type of the returned {@link OutOperation}
   * @param sql SQL for the {@link Operation}. Must return zero or more out
   * parameters or function results.
   * @return a new {@link OutOperation} that is a member of this
   * {@link OperationGroup}
   */
  public <R extends S> OutOperation<R> outOperation(String sql);

  /**
   * Return a {@link ParameterizedRowOperation}.
   *
   * @param <R> the type of the result of the returned
   * {@link ParameterizedRowOperation}
   * @param sql SQL for the {@link Operation}. Must return a row sequence.
   * @return a new {@link ParameterizedRowOperation} that is a member of this
   * {@link OperationGroup}
   */
  public <R extends S> ParameterizedRowOperation<R> rowOperation(String sql);

  public <R extends S> RowProcessorOperation<R> rowProcessorOperation(String sql);

  /**
   * Return a {@link StaticMultiOperation}.
   *
   * @param <R> the type of the result of the returned
   * {@link StaticMultiOperation}
   * @param sql SQL for the {@link Operation}
   * @return a new {@link StaticMultiOperation} that is a member of this
   * {@link OperationGroup}
   */
  public <R extends S> StaticMultiOperation<R> staticMultiOperation(String sql);

  /**
   * Return a {@link DynamicMultiOperation}. Use this when the number and type
   * of the results is not knowable.
   *
   * @param <R> the type of the result of the returned
   * {@link DynamicMultiOperation}
   * @param sql SQL for the {@link Operation}
   * @return a new {@link DynamicMultiOperation} that is a member of this
   * {@link OperationGroup}
   */
  public <R extends S> DynamicMultiOperation<R> dynamicMultiOperation(String sql);

  /**
   * Return an {@link Operation} that ends the database transaction. The
   * transaction is ended with a commit unless the {@link Transaction} has been
   * {@link Transaction#setRollbackOnly} in which case the transaction is ended
   * with a rollback.
   *
   * The type argument {@link S} of the containing {@link OperationGroup} must
   * be a supertype of {@link TransactionOutcome}.
   *
   * @param trans the Transaction that determines whether the Operation does a
   * database commit or a database rollback.
   * @return an {@link Operation} that will end the database transaction.
   * @throws IllegalStateException if this {@link OperationGroup} has been
   * submitted and is not held or is parallel.
   */
  public Operation<TransactionOutcome> endTransactionOperation(Transaction trans);

  /**
   * Convenience method that creates and submits a endTransaction
   * {@link Operation} that commits by default but can be set to rollback by
   * calling {@link Transaction#setRollbackOnly}.
   *
   * @param trans the Transaction that determines whether the Operation is a
   * database commit or a database rollback.
   * @return this {@link OperationGroup}
   * @throws IllegalStateException if this {@link OperationGroup} has been
   * submitted and is not held or is parallel.
   */
  public default CompletionStage<TransactionOutcome> commitMaybeRollback(Transaction trans) {
    return this.endTransactionOperation(trans).submit().getCompletionStage();
  }

  /**
   * Return a {@link LocalOperation}.
   *
   * @param <R> value type of the returned local Operation
   * @return a LocalOperation
   * @throws IllegalStateException if this OperationGroup has been submitted and
   * is not held
   */
  public <R extends S> LocalOperation<R> localOperation();

  /**
   * Returns a Flow.Processor that subscribes to a sequence of Operations and
   * produces a sequence of corresponding Submissions.The Operations must be
   * members of this OperationGroup. Calling Subscription.onNext with any
   * Operation that is not a member of this OperationGroup, that is was not
   * created by calling one of the Operation factory methods on this
   * OperationGroup, will cause the Subscription to be canceled and call
   * Subscriber.onError with IllegalArgumentException. The method
   * Subscription.onNext will call submit on each Operation it is passed and
   * publish the resulting Submission. Since an Operation can only be submitted
   * once, submitting an Operation and calling onNext with that submitted
   * Operation will cause the Subscription to be canceled and Subscriber.onError
   * to be called with IllegalStateException. The Processor does not retain
   * Submissions to produce to a subsequently attached Subscriber.
   *
   * If there is no Subscriber to the Processor, the Processor will request
   * Operations as appropriate. If there is a Subscriber to the Processor, the
   * Processor will request Operations no faster than the Subscriber requests
   * Submissions.
   *
   * Each call to this method returns a new Flow.processor. The Submissions
   * published to each Processor are exactly those generated by calling submit
   * on the Operations passed as arguments to onNext on the same Processor.
   * Calling this method while there is an active Processor will throw
   * IllegalStateException.
   *
   * Note: If any Operation is submitted directly, that is by calling submit
   * rather than passing it to onNext, the Submission returned by the submit
   * call will not be published.
   *
   * @param <R> the type of the result of the returned
   * {@link Flow.Processor}
   * @return a Flow.Processor that accepts Operations and generates Submissions
   * @throws IllegalStateException if there is an active Processor
   */
  public <R extends S> Flow.Processor<Operation<R>, Submission<R>> operationProcessor();

  /**
   * Supply a {@link Logger} for the implementation of this
   * {@link OperationGroup} to use to log significant events. Exactly what
   * events are logged, at what Level the events are logged and with what
   * parameters is implementation dependent. All member {@link Operation}s of
   * this {@link OperationGroup} will use the same {@link Logger} except a
   * member {@link OperationGroup} that is supplied with a different
   * {@link Logger} uses that {@link Logger}.
   *
   * Supplying a {@link Logger} configured with a
   * {@link java.util.logging.MemoryHandler} with the
   * {@link java.util.logging.MemoryHandler#pushLevel} set to
   * {@link java.util.logging.Level#WARNING} will result in no log output in
   * normal operation. In the event of an error the actions leading up to the
   * error will be logged.
   *
   * Implementation Note: Implementations are encouraged to log the creation of
   * this {@link OperationGroup} set to {@link java.util.logging.Level#INFO},
   * the creation of member {@link Operation}s at the
   * {@link java.util.logging.Level#CONFIG} level, and execution of member
   * {@link Operation}s at the {@link java.util.logging.Level#FINE} level.
   * Detailed information about the execution of member {@link Operation}s may
   * be logged at the {@link java.util.logging.Level#FINER} and
   * {@link java.util.logging.Level#FINEST} levels. Errors in the execution of
   * user code should be logged at the {@link java.util.logging.Level#WARNING}
   * Level. Errors in the implementation code should be logged at the
   * {@link java.util.logging.Level#SEVERE} Level.
   *
   * @param logger used by the implementation to log significant events
   * @return this {@link OperationGroup}
   */
  public OperationGroup<S, T> logger(Logger logger);

  // Covariant overrides
  @Override
  public OperationGroup<S, T> timeout(Duration minTime);

  @Override
  public OperationGroup<S, T> onError(Consumer<Throwable> handler);
}
