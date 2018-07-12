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
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collector;

/**
 * <p>
 * A set of {@link Operation}s that share certain properties, are managed as a
 * unit, and are executed as a unit. The {@link Operation}s created by an
 * {@link OperationGroup} and submitted are the member {@link Operation}s of
 * that {@link OperationGroup}. An {@link OperationGroup} is not a transaction
 * and is not related to a transaction in any way.</p>
 *
 * <p>
 * An {@link OperationGroup} conceptually has a collection of member
 * {@link Operation}s. When an {@link OperationGroup} is submitted it is placed
 * in the collection of the {@link OperationGroup} of which it is a member. The
 * member {@link OperationGroup} is executed according to the attributes of the
 * {@link OperationGroup} of which it is a member. The member {@link Operation}s
 * of an {@link OperationGroup} are executed according to the attributes of that
 * {@link OperationGroup}.</p>
 *
 * <p>
 * How an {@link OperationGroup} is executed depends on its attributes.</p>
 *
 * <p>
 * If an {@link OperationGroup} has a condition and the value of that condition
 * is {@link Boolean#TRUE} then execute the member {@link Operation}s as below.
 * If it is {@link Boolean#FALSE} then the {@link OperationGroup} is completed
 * with the value null. If the condition completed exceptionally then the
 * {@link OperationGroup} is completed exceptionally with a
 * {@link SqlSkippedException} that has that exception as its cause.</p>
 * 
 * <p>
 * If the {@link OperationGroup} is sequential the member {@link Operation}s are
 * executed in the order they were submitted. If it is parallel, they may be
 * executed in any order including simultaneously.</p>
 *
 * <p>
 * If an {@link OperationGroup} is dependent and a member {@link Operation}
 * completes exceptionally the remaining member {@link Operation}s in the
 * collection are completed exceptionally with a {@link SqlSkippedException}
 * that has the initial {@link Exception} as its cause and the {@link OperationGroup}
 * is completed exceptionally with the initial {@link Exception}. A member
 * {@link Operation} in-flight may either complete normally or be completed
 * exceptionally but must complete one way or the other. [NOTE: Too strong?]</p>
 *
 * <p>
 * After a call to {@link OperationGroup#submitHoldingForMoreMembers} the
 * {@link OperationGroup} is submitted and held. After a call to 
 * {@link OperationGroup#releaseProhibitingMoreMembers} the {@link OperationGroup} 
 * is no longer held and is still submitted. Holding permits member {@link Operation}s
 * to be executed at the same time additional member {@link Operation}s are
 * submitted. Collecting the member {@link Operation}s' results does not begin
 * until the {@link OperationGroup} is no longer held.</p>
 * 
 * <p>
 * If an {@link OperationGroup} is held additional member {@link Operation}s may
 * be submitted. If an {@link OperationGroup} is not held, no additional member 
 * {@link Operation}s  may be submitted after the {@link OperationGroup} is 
 * submitted. If an {@link OperationGroup} is held it will be completed only after 
 * it is released or if conditional and the condition is not {@link Boolean#TRUE}. 
 * If a {@link OperationGroup} is dependent, held, one of its member
 * {@link Operation}s completed exceptionally, and its queue is empty then the
 * {@link OperationGroup} is released.</p>
 *
 * <p>
 * The result of this {@link OperationGroup} is the result of collecting the
 * results of its member {@link Operation}s. If the {@link OperationGroup} is 
 * dependent and one of its member {@link Operation}s completes exceptionally,
 * the {@link OperationGroup} is completed exceptionally.</p>
 * 
 * <p>
 * An implementation of this class must be thread safe as result and error
 * handlers running asynchronously may be accessing an {@link OperationGroup} in
 * parallel with each other and with a user thread.</p>

* <p>
 * ISSUE: Currently no way to create a nested {@link OperationGroup}. That is an
 * intentional limitation but may be a simplification we can live with. Or not.</p>
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
   * Note: There is no covariant override of this method in {@link Session}
   * as there is only a small likelihood of needing it.
   *
   * @return this {@link OperationGroup}
   * @throws IllegalStateException if this {@link OperationGroup} has been
   * submitted, any member {@link Operation}s have been created, or this method
   * has been called previously
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
   * Note: There is no covariant override of this method in {@link Session}
   * as there is only a small likelihood of needing it.
   *
   * @return this {@link OperationGroup}
   * @throws IllegalStateException if this {@link OperationGroup} has been 
   * submitted, any member {@link Operation}s have been created, or this method
   * has been called previously
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
   * Note: There is no covariant override of this method in Session as there
   * is only a small likelihood of needing it.
   *
   * ISSUE: Should the member Operations be skipped or otherwise completed
   * exceptionally?
   *
   * @param condition a {@link CompletionStage} the value of which determines
   * whether this {@link OperationGroup} is executed or not
   * @return this OperationGroup
   * @throws IllegalStateException if this {@link OperationGroup} has been
   * submitted, any member {@link Operation}s have been created, or this method
   * has been called previously
   */
  public OperationGroup<S, T> conditional(CompletionStage<Boolean> condition);

  /**
   * Mark this {@link OperationGroup} as submitted and held. It can be executed but cannot be
   * completed. A {@link OperationGroup} that is held remains in the queue even
   * if all of its current member {@link Operation}s have completed. So long as
   * the {@link OperationGroup} is held new member {@link Operation}s can be
   * submitted. A {@link OperationGroup} that is held must be released before it
   * can be completed and removed from the queue.
   * 
   * If the {@link OperationGroup} is dependent and one of its member {@link Operation}s
   * completes exceptionally and its queue is empty the {@link OperationGroup}
   * is completed.
   *
   * Note: There is no covariant override of this method in Session as there
   * is only a small likelihood of needing it.
   *
   * ISSUE: Need a better name.
   *
   * @return a Submission for this OperationGroup
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
   * Note: There is no covariant override of this method in Session as there
   * is only a small likelihood of needing it.
   *
   * ISSUE: Need a better name.
   *
   * @return the same Submission that was returned by {@link OperationGroup#submitHoldingForMoreMembers}
   * @throws IllegalStateException if this {@link OperationGroup} has been
   * completed or is not held.
   */
  public Submission<T> releaseProhibitingMoreMembers();

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
   * Return a new member {@link PrimitiveOperation} that is never skipped.
   * Skipping of member {@link Operation}s stops with a catchOperation and the
   * subsequent {@link Operation} is executed normally. The value of a
   * catchOperation is always null. Since a catchOperation is never completed
   * exceptionally, it has no error handler or timeout.
   *
   * @return an {@link PrimitiveOperation} that is never skipped;
   * @throws IllegalStateException if the {@link OperationGroup} has been
   * submitted and is not held or if the {@link OperationGroup} is parallel or
   * independent.
   */
  public PrimitiveOperation<S> catchOperation();
  
  /**
   * Creates and submits a catch Operation. Convenience method.
   *
   * @return this OperationGroup
   * @throws IllegalStateException if the {@link OperationGroup} has been
   * submitted and is not held or if the {@link OperationGroup} is parallel or
   * independent.
   */
  public default OperationGroup<S, T> catchErrors() {
    catchOperation().submit();
    return this;
  }

  /**
   * Return a new {@link ArrayRowCountOperation}.
   * <p>
   * Usage Note: Frequently use of this method will require a type witness to
   * enable correct type inferencing.
   * <pre><code>
   *   session.<b>&lt;List&lt;Integer&gt;&gt;</b>arrayCountOperation(sql)
   *     .set ...
   *     .collect ...
   *     .submit ...
   * </code></pre>
   *
   * @param <R> the result type of the returned {@link ArrayRowCountOperation}
   * @param sql SQL to be executed. Must return an update count.
   * @return a new {@link ArrayRowCountOperation} that is a member of this
   * {@link OperationGroup}
   * @throws IllegalStateException if the {@link OperationGroup} has been
   * submitted and is not held
   */
  public <R extends S> ArrayRowCountOperation<R> arrayRowCountOperation(String sql);

  /**
   * Return a new {@link ParameterizedRowCountOperation}.
   *
   * @param <R> the result type of the returned {@link RowCountOperation}
   * @param sql SQL to be executed. Must return an update count.
   * @return an new {@link ParameterizedRowCountOperation} that is a member of this
   * {@link OperationGroup}
   * @throws IllegalStateException if the {@link OperationGroup} has been
   * submitted and is not held
   */
  public <R extends S> ParameterizedRowCountOperation<R> rowCountOperation(String sql);

  /**
   * Return a new {@link Operation} for a SQL that doesn't return any result,
   * for example DDL. The result of this Operation is always null.
   * 
   * The result of the returned Operation must be Void but specifying that here
   * causes problems.
   *
   * @param sql SQL for the {@link Operation}.
   * @return a new {@link Operation} that is a member of this
   * {@link OperationGroup}
   * @throws IllegalStateException if the {@link OperationGroup} has been
   * submitted and is not held
   */
  public Operation<S> operation(String sql);

  /**
   * Return a new {@link OutOperation} that is a member {@link Operation} of this 
   * {@link OperationGroup}. The SQL must return a set of zero or more out 
   * parameters or function results.
   *
   * @param <R> the result type of the returned {@link OutOperation}
   * @param sql SQL for the {@link Operation}. Must return zero or more out
   * parameters or function results.
   * @return a new {@link OutOperation} that is a member of this
   * {@link OperationGroup}
   * @throws IllegalStateException if the {@link OperationGroup} has been
   * submitted and is not held
   */
  public <R extends S> OutOperation<R> outOperation(String sql);

  /**
   * Return a new {@link ParameterizedRowOperation} that is a member 
   * {@link Operation} of this {@link OperationGroup}.
   *
   * @param <R> the type of the result of the returned
   * {@link ParameterizedRowOperation}
   * @param sql SQL for the {@link Operation}. Must return a row sequence.
   * @return a new {@link ParameterizedRowOperation} that is a member of this
   * {@link OperationGroup}
   * @throws IllegalStateException if the {@link OperationGroup} has been
   * submitted and is not held
   */
  public <R extends S> ParameterizedRowOperation<R> rowOperation(String sql);

  /**
   * Return a new {@link ParameterizedRowPublisherOperation} that is a member
   * {@link Operation} of this {@link OperationGroup}.
   *
   * @param <R> the type of the result of the returned
   * {@link ParameterizedRowPublisherOperation}
   * @param sql SQL for the {@link Operation}. Must return a row sequence.
   * @return a new {@link ParameterizedRowPublisherOperation} that is a member
   * of this {@link OperationGroup}
   * @throws IllegalStateException if the {@link OperationGroup} has been
   * submitted and is not held
   */
  public <R extends S> ParameterizedRowPublisherOperation<R> rowPublisherOperation(String sql);

  /**
   * Return a new {@link MultiOperation} that is a member 
   * {@link Operation} of this {@link OperationGroup}.
   *
   * @param <R> the type of the result of the returned
   * {@link MultiOperation}
   * @param sql SQL for the {@link Operation}
   * @return a new {@link MultiOperation} that is a member of this
   * {@link OperationGroup}
   * @throws IllegalStateException if the {@link OperationGroup} has been
   * submitted and is not held
   */
  public <R extends S> MultiOperation<R> multiOperation(String sql);

  /**
   * Return a new {@link Operation} that ends the database transaction. This
   * {@link Operation} is a member of the {@link OperationGroup}. The
   * transaction is ended with a commit unless the {@link TransactionCompletion}
   * has been {@link TransactionCompletion#setRollbackOnly} in which case the
   * transaction is ended with a rollback.
   * 
   * <p>
   * An endTransaction Operation may be skipped. To insure that it will not be
   * skipped it should immediately follow a catch Operation. All end transaction
   * convenience methods do so.</p>
   *
   * The type argument {@link S} of the containing {@link OperationGroup} must
   * be a supertype of {@link TransactionOutcome}.
   *
   * @param trans the TransactionCompletion that determines whether the Operation does a 
 database commit or a database rollback.
   * @return an {@link Operation} that will end the database transaction.
   * @throws IllegalStateException if this {@link OperationGroup} has been
   * submitted and is not held or is parallel.
   */
  public Operation<TransactionOutcome> endTransactionOperation(TransactionCompletion trans);

  /**
   * Convenience method that creates and submits a endTransaction
   * {@link Operation} that commits by default but can be set to rollback by
   * calling {@link TransactionCompletion#setRollbackOnly}. The endTransaction Operation
   * is never skipped.
   *
   * @param trans the TransactionCompletion that determines whether the {@link Operation} is a
   * database commit or a database rollback.
   * @return a {@link CompletionStage} that is completed with the outcome of the 
   * transaction
   * @throws IllegalStateException if this {@link OperationGroup} has been
   * submitted and is not held or is parallel.
   */
  public default CompletionStage<TransactionOutcome> commitMaybeRollback(TransactionCompletion trans) {
    catchErrors();
    return this.endTransactionOperation(trans).submit().getCompletionStage();
  }

  /**
   * Return a new {@link LocalOperation} that is a member {@link Operation} of 
   * this {@link OperationGroup}.
   *
   * @param <R> value type of the returned local {@link Operation}
   * @return a LocalOperation
   * @throws IllegalStateException if this {@link OperationGroup} has been submitted and
   * is not held
   */
  public <R extends S> LocalOperation<R> localOperation();

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

  /**
   * Returns a {@code String} enclosed in single quotes. Any occurrence of a
   * single quote within the string will be replaced by two single quotes.
   *
   * <blockquote>
   * <table class="striped">
   * <caption>Examples of the conversion:</caption>
   * <thead>
   * <tr><th scope="col">Value</th><th scope="col">Result</th></tr>
   * </thead>
   * <tbody style="text-align:center">
   * <tr> <th scope="row">Hello</th> <td>'Hello'</td> </tr>
   * <tr> <th scope="row">G'Day</th> <td>'G''Day'</td> </tr>
   * <tr> <th scope="row">'G''Day'</th>
   * <td>'''G''''Day'''</td> </tr>
   * <tr> <th scope="row">I'''M</th> <td>'I''''''M'</td>
   * </tr>
   *
   * </tbody>
   * </table>
   * </blockquote>
   *
   * @implNote JDBC driver implementations may need to provide their own
   * implementation of this method in order to meet the requirements of the
   * underlying datasource.
   * @param val a character string. Not null
   * @return A string enclosed by single quotes with every single quote
   * converted to two single quotes. Not null
   * @throws NullPointerException if {@code val} is {@code null}
   * @throws IllegalArgumentException if {@code val} cannot be enquoted
   */
  default String enquoteLiteral(String val) {
    return "'" + val.replace("'", "''") + "'";
  }

  /**
   * Returns a SQL identifier. If {@code identifier} is a simple SQL identifier:
   * <ul>
   * <li>Return the original value if {@code alwaysQuote} is {@code false}</li>
   * <li>Return a delimited identifier if {@code alwaysQuote} is
   * {@code true}</li>
   * </ul>
   *
   * If {@code identifier} is not a simple SQL identifier, {@code identifier}
   * will be enclosed in double quotes if not already present. If the datasource
   * does not support double quotes for delimited identifiers, the identifier
   * should be enquoted by whatever mechanism the data source supports. If the
   * datasource does not support delimited identifiers, an
   * {@code IllegalArgumentException} should be thrown.
   * <p>
   * A {@code IllegalArgumentException} will be thrown if {@code identifier}
   * contains any characters invalid in a delimited identifier or the identifier
   * length is invalid for the datasource.
   *
   * @implSpec The default implementation uses the following criteria to
   * determine a valid simple SQL identifier:
   * <ul>
   * <li>The string is not enclosed in double quotes</li>
   * <li>The first character is an alphabetic character from a through z, or
   * from A through Z</li>
   * <li>The name only contains alphanumeric characters or the character
   * "_"</li>
   * </ul>
   *
   * The default implementation will throw a {@code SQLException} if:
   * <ul>
   * <li>{@code identifier} contains a {@code null} character or double quote
   * and is not a simple SQL identifier.</li>
   * <li>The length of {@code identifier} is less than 1 or greater than 128
   * characters
   * </ul>
   * <blockquote>
   * <table class="striped" >
   * <caption>Examples of the conversion:</caption>
   * <thead>
   * <tr>
   * <th scope="col">identifier</th>
   * <th scope="col">alwaysQuote</th>
   * <th scope="col">Result</th></tr>
   * </thead>
   * <tbody>
   * <tr>
   * <th scope="row">Hello</th>
   * <td>false</td>
   * <td>Hello</td>
   * </tr>
   * <tr>
   * <th scope="row">Hello</th>
   * <td>true</td>
   * <td>"Hello"</td>
   * </tr>
   * <tr>
   * <th scope="row">G'Day</th>
   * <td>false</td>
   * <td>"G'Day"</td>
   * </tr>
   * <tr>
   * <th scope="row">"Bruce Wayne"</th>
   * <td>false</td>
   * <td>"Bruce Wayne"</td>
   * </tr>
   * <tr>
   * <th scope="row">"Bruce Wayne"</th>
   * <td>true</td>
   * <td>"Bruce Wayne"</td>
   * </tr>
   * <tr>
   * <th scope="row">GoodDay$</th>
   * <td>false</td>
   * <td>"GoodDay$"</td>
   * </tr>
   * <tr>
   * <th scope="row">Hello"World</th>
   * <td>false</td>
   * <td>IllegalArgumentException</td>
   * </tr>
   * <tr>
   * <th scope="row">"Hello"World"</th>
   * <td>false</td>
   * <td>IllegalArgumentException</td>
   * </tr>
   * </tbody>
   * </table>
   * </blockquote>
   * @implNote ADBA driver implementations may need to provide their own
   * implementation of this method in order to meet the requirements of the
   * underlying datasource.
   * @param identifier a SQL identifier. Not null
   * @param alwaysQuote indicates if a simple SQL identifier should be returned
   * as a quoted identifier
   * @return A simple SQL identifier or a delimited identifier. Not null
   * @throws NullPointerException if identifier is {@code null}
   * @throws IllegalArgumentException if {@code identifier} can not be converted 
   * to a valid identifier
   */
  default String enquoteIdentifier(String identifier, boolean alwaysQuote) {
    int len = identifier.length();
    if (len < 1 || len > 128) {
      throw new IllegalArgumentException("Invalid name");
    }
    if (Pattern.compile("[\\p{Alpha}][\\p{Alnum}_]*").matcher(identifier).matches()) {
      return alwaysQuote ? "\"" + identifier + "\"" : identifier;
    }
    if (identifier.matches("^\".+\"$")) {
      identifier = identifier.substring(1, len - 1);
    }
    if (Pattern.compile("[^\u0000\"]+").matcher(identifier).matches()) {
      return "\"" + identifier + "\"";
    }
    else {
      throw new IllegalArgumentException("Invalid name");
    }
  }

  /**
   * Retrieves whether {@code identifier} is a simple SQL identifier.
   *
   * @implSpec The default implementation uses the following criteria to
   * determine a valid simple SQL identifier:
   * <ul>
   * <li>The string is not enclosed in double quotes</li>
   * <li>The first character is an alphabetic character from a through z, or
   * from A through Z</li>
   * <li>The string only contains alphanumeric characters or the character
   * "_"</li>
   * <li>The string is between 1 and 128 characters in length inclusive</li>
   * </ul>
   *
   * <blockquote>
   * <table class="striped" >
   * <caption>Examples of the conversion:</caption>
   * <thead>
   * <tr>
   * <th scope="col">identifier</th>
   * <th scope="col">Simple Identifier</th>
   * </thead>
   *
   * <tbody>
   * <tr>
   * <th scope="row">Hello</th>
   * <td>true</td>
   * </tr>
   * <tr>
   * <th scope="row">G'Day</th>
   * <td>false</td>
   * </tr>
   * <tr>
   * <th scope="row">"Bruce Wayne"</th>
   * <td>false</td>
   * </tr>
   * <tr>
   * <th scope="row">GoodDay$</th>
   * <td>false</td>
   * </tr>
   * <tr>
   * <th scope="row">Hello"World</th>
   * <td>false</td>
   * </tr>
   * <tr>
   * <th scope="row">"Hello"World"</th>
   * <td>false</td>
   * </tr>
   * </tbody>
   * </table>
   * </blockquote>
   * @implNote ADBA driver implementations may need to provide their own
   * implementation of this method in order to meet the requirements of the
   * underlying datasource.
   * @param identifier a SQL identifier. Not null
   * @return true if a simple SQL identifier, false otherwise
   * @throws NullPointerException if identifier is {@code null}
   */
  default boolean isSimpleIdentifier(String identifier) {
    int len = identifier.length();
    return len >= 1 && len <= 128
            && Pattern.compile("[\\p{Alpha}][\\p{Alnum}_]*").matcher(identifier).matches();
  }

  /**
   * Returns a {@code String} representing a National Character Set Literal
   * enclosed in single quotes and prefixed with a upper case letter N. Any
   * occurrence of a single quote within the string will be replaced by two
   * single quotes.
   *
   * <blockquote>
   * <table class="striped">
   * <caption>Examples of the conversion:</caption>
   * <thead>
   * <tr>
   * <th scope="col">Value</th>
   * <th scope="col">Result</th>
   * </tr>
   * </thead>
   * <tbody>
   * <tr> <th scope="row">Hello</th> <td>N'Hello'</td> </tr>
   * <tr> <th scope="row">G'Day</th> <td>N'G''Day'</td> </tr>
   * <tr> <th scope="row">'G''Day'</th>
   * <td>N'''G''''Day'''</td> </tr>
   * <tr> <th scope="row">I'''M</th> <td>N'I''''''M'</td>
   * <tr> <th scope="row">N'Hello'</th> <td>N'N''Hello'''</td> </tr>
   *
   * </tbody>
   * </table>
   * </blockquote>
   *
   * @implNote ADBA driver implementations may need to provide their own
   * implementation of this method in order to meet the requirements of the
   * underlying datasource. An implementation of enquoteNCharLiteral may accept
   * a different set of characters than that accepted by the same drivers
   * implementation of enquoteLiteral.
   * @param val a character string. Not null
   * @return the result of replacing every single quote character in the
   * argument by two single quote characters where this entire result is then
   * prefixed with 'N'. Not null.
   * @throws NullPointerException if {@code val} is {@code null}
   * @throws IllegalArgumentException if {@code val} cannot be enquoted
   */
  default String enquoteNCharLiteral(String val) {
    return "N'" + val.replace("'", "''") + "'";
  }

    // Covariant overrides
  @Override
  public OperationGroup<S, T> timeout(Duration minTime);

  @Override
  public OperationGroup<S, T> onError(Consumer<Throwable> handler);
  
}
