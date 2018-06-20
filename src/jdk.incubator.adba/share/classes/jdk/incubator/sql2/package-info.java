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


/**
 * <p>
 * An API for accessing and processing data stored in a data source (usually a
 * relational database) using the Java&trade; programming language. This API
 * includes a framework whereby different drivers can be installed dynamically
 * to access different data sources. This API is specifically geared for passing
 * SQL statements to a database though it may be used for reading and writing
 * data from/to any data source that has a tabular format.</p>
 *
 * <p>
 * This API differs from the API in {@code java.sql} in several ways.</p>
 * <ul>
 * <li>Is asynchronous
 * <li>Is geared toward high-throughput programs
 * <li>Does not attempt to support every database feature
 * <li>Does not attempt to abstract the database
 * <li>Uses the builder pattern
 * <li>Supports the fluent programming style
 * </ul>
 *
 * <p>
 * It is worth emphasizing that this API is an alternate to the {@code java.sql}
 * API, not a replacement. There are many programs that can much more readily be
 * written using the {@code java.sql} API as it has many features that are not
 * available in this API. For example this API provides almost no mechanism for
 * getting metadata.</p>
 *
 * <p>
 * This API is not an extension to the {@code java.sql} API. It an independent
 * API and is used on its own without reference to java.sql. </p>
 *
 *
 * <h3>Overview</h3>
 *
 * <p>
 * The core feature of this API is that it is asynchronous. No method call will 
 * wait for a network operation. </p>
 *
 * <p>
 * Possibly blocking actions are represented as {@link Operation}s. An
 * application using the API creates and submits one or more {@link Operation}s.
 * The implementation executes these {@link Operation}s asynchronously, reporting 
 * their results via {@link java.util.concurrent.CompletionStage}s. An application
 * can respond to the results via the
 * {@link java.util.concurrent.CompletionStage}s or via callbacks that can be
 * configured on many of the {@link Operation}s or both. Creating and submitting
 * {@link Operation}s is strictly non-blocking. Handling the results of possibly
 * blocking {@link Operation}s is done asynchronously. No application thread
 * will ever block on a call to a method in this API.</p>
 *
 * <p>
 * All {@link Operation}s provide a
 * {@link java.util.concurrent.CompletionStage}. The value of that
 * {@link java.util.concurrent.CompletionStage} is the value of the
 * {@link Operation}, set when the {@link Operation} completes. Some
 * {@link Operation}s provide callbacks for processing the result of the
 * {@link Operation} independent of the
 * {@link java.util.concurrent.CompletionStage}. Those {@link Operation}s can
 * be used for executing SQL that returns results of a specific type. For
 * example SQL that returns a row sequence would be executed with a
 * {@link RowOperation}. A {@link RowOperation} provides callbacks for
 * processing each row and for collecting the results of processing the rows.
 * Other {@link Operation}s are specialized for SQL that returns a count or that
 * returns out parameters. The choice of {@link Operation} is dependent on the
 * result to be processed and is independent of the particular kind of SQL
 * statement.</p>
 *
 * <p>
 * An {@link OperationGroup} encapsulates a group of {@link Operation}s and
 * executes them using common attributes. An {@link OperationGroup} can be
 * unconditional or conditional, sequential or parallel, dependent or
 * independent, or any combination of these. Dependent/independent controls
 * error handling. If one member of a dependent {@link OperationGroup} fails the
 * remaining not-yet-executed members are completed exceptionally. If the
 * {@link OperationGroup} is independent, the member {@link Operation}s are
 * executed regardless of whether one or more fails.</p>
 *
 * <p>
 * A {@link Connection} is itself an {@link OperationGroup} and so can be
 * conditional, parallel, or independent, but by default is unconditional,
 * sequential, dependent. While a {@link Connection} may be created with values
 * other than the defaults, using the defaults is by far the most common case.
 * The API provides convenience methods that support this case. Using these
 * convenience methods is recommended in all but the most unusual circumstances.
 * In particular making the {@link Connection} parallel introduces some
 * challenges that would require a full understanding of the details of the API.
 * It would almost certainly be better to create a parallel
 * {@link OperationGroup} within the {@link Connection}.</p>
 *
 * <p>
 * <i>
 * ISSUE: Should we disallow {@code Connection.parallel()}?</i></p>
 *
 * <p>
 * The {@code java.sql} API frequently provides many ways to do the same thing.
 * This API makes no attempt to do this. For those capabilities this API
 * supports, it frequently defines exactly one way to do something. Doing things
 * another way, for example calling methods in a non-standard order, frequently
 * results in an IllegalStateException. This approach is intended to make things
 * simpler for both the user and the implementor. Rather than having to
 * understand complicated interactions of many different components and methods
 * executed in any order, the intent is that there is only one way to do things
 * so only one path must be understood or implemented. Anything off that path is
 * an error. While this requires a programmer to write code in one specific way
 * it makes things easier on future maintainers of the code as the code will
 * conform to the standard pattern. Similarly the implementation is simplified
 * as only the standard use pattern is supported.</p>
 *
 * <p>
 * One way this API simplifies things in to define types as single use. Many
 * types are created, configured, used once, and are then no longer usable. Most
 * configuration methods can be called only once on a given instance. Once an
 * instance is configured it cannot be reconfigured. Once an instance is used it
 * cannot be reused. This simplifies things by eliminating the need to
 * understand and implement arbitrary sequences of method calls that reconfigure
 * and reuse instances. Since objects are single use there is no expectation
 * that an application cache or share {@link Operation}s.</p>
 *
 * <p>
 * While the user visible types are single use, it is expected that an
 * implementation will cache and reuse data and {@link Object}s that are worth
 * the effort. Rather than attempt to guess what an implementation should reuse
 * and capture that in the API, this API leaves it entirely up to the
 * implementation. Since the API specifies very little reuse, an implementation
 * is free to reuse whatever is appropriate. Since the pattern of use is
 * strictly enforced figuring out how to reuse objects is greatly
 * simplified.</p>
 *
 * <p>
 * The {@code java.sql} API provides many tools for abstracting the database,
 * for enabling the user to write database independent code. This API does not.
 * It is not a goal of this API to enable users to write database independent
 * code. That is not to say it is not possible, just that this API does not
 * provide tools to support such. Abstraction features typically impose
 * performance penalties on some implementations. As this API is geared for
 * high-throughput programs it avoids such abstractions rather than reduce
 * performance.</p>
 *
 * <p>
 * One such abstraction feature is the JDBC escape sequences. Implementing these
 * features requires parsing the SQL so as to identify the escape sequences and
 * then generating a new String with the vendor specific SQL corresponding to
 * the escape sequence. This is an expensive operation. Further each SQL must be
 * parsed whether it contains an escape sequence or not imposing the cost on all
 * JDBC users, not just the ones who use escape sequences. The same is true of
 * JDBC parameter markers. The SQL accepted by this API is entirely vendor
 * specific, including parameter markers. There is no need for pre-processing
 * prior to SQL execution substantially reducing the amount of work the
 * implementation must do.</p>
 *
 * <p>
 * Note: It would be a reasonable future project to develop a SQL builder API
 * that creates vendor specific SQL from some more abstract representation.</p>
 * 
 * <p>
 * This API is targeted at high-throughput apps. If a particular feature of this
 * API would have a surprising performance impact for a particular implementation
 * it is recommended that the implementation not implement that feature. It is
 * better that a feature be unsupported as opposed to users investing substantial
 * effort in an app using that feature only to discover in production that the
 * performance is unacceptable. For example, if an implementation can only support
 * {@link Operation#timeout} through active polling it would be better for that
 * implementation to throw  {@link UnsupportedOperationException} if 
 * {@link Operation#timeout} is called.</p>
 *
 * <h3>Execution Model</h3>
 *
 * <p>
 * <i>This section describes the function of a conforming implementation. It is
 * not necessary for an implementation to be implemented as described only that
 * the behavior be the same.</i></p>
 *
 * <p>
 * An {@link Operation} has an action and a
 * {@link java.util.concurrent.CompletionStage}. Some {@link Operation}s have
 * some form of result processor.</p>
 *
 * <p>
 * An {@link Operation} is executed by causing the action to be performed,
 * processing the result of the action if there is a result processor, and
 * completing the {@link java.util.concurrent.CompletionStage} with the result
 * of the result processor if there is one or with the result of the action if
 * there is no result processor. If the action or the result processing causes
 * an unhandled error the {@link java.util.concurrent.CompletionStage} is
 * completed exceptionally. The {@link java.util.concurrent.CompletionStage} is
 * completed asynchronously, as though it were created by calling an
 * <i>async</i> method on {@link java.util.concurrent.CompletionStage}.
 * </p>
 *
 * <p>
 * Performing the action may require one or more interactions with the database.
 * These interactions may be carried out in parallel with processing the result.
 * If the database result is ordered, that result is processed in that order.
 *
 * <p>
 * An {@link OperationGroup} has a collection of {@link Operation}s and
 * optionally a condition. For a sequential {@link OperationGroup}
 * {@link Operation}s are selected from the collection in the order they were
 * submitted. For a parallel {@link OperationGroup} {@link Operation}s are
 * selected from the collection in any order.</p>
 *
 * <p>
 * The action of an {@link OperationGroup} is performed as follows:
 * <ul>
 * <li>
 * If the {@link OperationGroup} has a condition, the value of the condition is
 * retrieved. If the value is {@link Boolean#FALSE} the action is complete and
 * the {@link java.util.concurrent.CompletionStage} is completed with null. If
 * the condition value completes exceptionally the action is complete and the
 * {@link java.util.concurrent.CompletionStage} is completed exceptionally
 * with the same exception. If the condition value is {@link Boolean#TRUE} or
 * there is no condition the {@link Operation}s in the collection are executed
 * and their results processed. The action is complete when the
 * {@link OperationGroup} is not held and all the {@link Operation}s have been
 * executed.</li>
 * <li>
 * If the {@link OperationGroup} is parallel more than one member
 * {@link Operation} may be executed at a time.</li>
 * <li>
 * If the {@link OperationGroup} is dependent and a member {@link Operation} completes
 * exceptionally all member {@link Operation}s that are yet to begin
 * execution are completed exceptionally with a {@link SqlSkippedException}. The
 * cause of that exception is the {@link Throwable} that caused the
 * {@link Operation} to be completed exceptionally. If an {@link Operation} is
 * in flight when another {@link Operation} completes exceptionally the in
 * flight {@link Operation} may either be allowed to complete uninterrupted or
 * it may be completed exceptionally. The {@link OperationGroup} is dependent it
 * is completed exceptionally with the {@link Throwable} that caused the 
 * {@link Operation} to complete exceptionally. 
 * 
 * <p>
 * Note: the {@link Operation}s returned by {@link Connection#closeOperation}
 * and {@link OperationGroup#catchOperation} are never skipped, i.e. never 
 * completed exceptionally with {@link SqlSkippedException}. The {@link Operation}
 * returned by {@link OperationGroup#catchOperation} never completes 
 * exceptionally so the following {@link Operation} is always executed normally. 
 * No {@link Operation} can be submitted after the {@link Operation} returned by 
 * {@link Connection#closeOperation} has been submitted.</p> </li>
 * <li>
 * If the {@link OperationGroup} is independent and an {@link Operation}
 * completes exceptionally all other {@link Operation}s are executed regardless.
 * There is no result to be processed for an {@link Operation} that completed
 * exceptionally. The {@link OperationGroup} is not completed exceptionally as
 * the result of one or more {@link Operation}s completing exceptionally.</li>
 * </ul>
 *
 * <p>
 * A {@link Connection} is a distinguished {@link OperationGroup}. A
 * {@link Connection} is executed upon being submitted.</p>
 *
 * <h3>Transactions</h3>
 *
 * <p>
 * <i>This section describes the function of a conforming implementation. It is
 * not necessary for an implementation to be implemented as described only that
 * the behavior be the same.</i></p>
 *
 * <p>
 * An implementation has only limited control over transactions. SQL statements
 * can start, commit, and rollback transactions without the implementation
 * having any influence or even being aware. This specification only describes
 * the behavior of those transaction actions that are visible to and controlled
 * by the implementation, i.e. the endTransaction {@link Operation}.
 * Transaction actions caused by SQL may interact with actions controlled by the
 * implementation in unexpected ways.</p>
 *
 * <p>
 * The creation of Operations and the subsequent execution of those Operations
 * are separated in time. It is quite reasonable to determine that a transaction
 * should commit after the Operation that ends the transaction is submitted.
 * But if the execution of the transaction does not result in the expected results
 * it might be necessary to rollback the transaction rather than commit it. This
 * determination depends on the execution of the Operations long after the
 * endTransaction Operation is created. To address this mismatch, the endTransaction Operation
 * specified by this API is conditioned by a {@link Transaction}. By default, a 
 * {@link Transaction} will cause an endTransaciton {@link Operation} to commit 
 * the transaction. At any time before the endTransaction {@link Operation} that 
 * references it is executed a {@link Transaction} can be set
 * to rollback the transaction .</p>
 *
 * <p>
 * An endTransaction {@link Operation}, like all {@link Operation}s, is immutable once submitted.
 * But an endTransaction {@link Operation} is created with a {@link Transaction} and that
 * {@link Transaction} can be set to commit or rollback. A {@link Transaction} controls the
 * endTransaction {@link Operation} created with it. Using this mechanism an
 * error handler, result handler or other code can cause a subsequent endTransaction
 * {@link Operation} to rollback instead of the default which is to commit.</p>
 *
 * <pre>
 * {@code
 *   Transaction t = conn.getTransaction();
 *   conn.countOperation(updateSql)
 *       .resultProcessor( count -> { 
 *           if (count > 1) t.setRollbackOnly(); 
 *           return null; 
 *       } )
 *       .submit();
 *   conn.catchErrors();
 *   conn.commitMaybeRollback(t);
 * }
 * </pre>
 *
 * <p>
 * In this example if the update SQL modifies more than one row the result
 * processor will set the Transaction to rollback only. When the endTransaction
 * Operation submitted by commitMaybeRollback is executed it will cause
 * the transaction to rollback.</p>
 * 
 *
 * <h3>Implementation Note</h3>
 * 
 * <p>
 * If an implementation exposes any implementation specific types and methods, the
 * implementation is expected to provide covariant overrides for all methods that
 * return the standard super-type of the implementation specific type.</p>
 * 
 * <p>
 * Consider an implementation that adds a method foo() to RowCountOperation. To do
 * that it would have to expose a type FooRowCountOperation extends RowCountOperation.
 * So that an application can transparently access foo, the implementation would
 * also have to expose FooDataSource, FooOperationGroup and FooConnection. Further
 * each of these types would have to declare covariant overrides for every method
 * that returns a direct super-type of one of these types.</p>
 * <ul>
 * <li>FooDataSourceFactory must override builder to return FooDataSource.Builder</li>
 * <li>FooDataSource.Builder must override url, password, etc to return a
 * FooDataSource.Builder. build must return a FooDataSource.</li>
 * <li>FooDataSource must override builder to return FooConnection.Builder</li>
 * <li>FooConnection.Builder must override url, password, etc to return a 
 * FooConnection.Builder. build must return a FooConnection</li>
 * <li>FooDataSource must override getConnection to return FooConnection</li>
 * <li>FooConnection must extend FooOperationGroup</li>
 * <li>FooOperationGroup> must override rowCountOperation to return FooRowCountOperation</li>
 * <li>FooRowCountOperation must override apply and onError to return FooRowCountOperation</li>
 * </ul>
 * <p>
 * The intent is to transparently expose the vendor extension without use of casts.
 * Example: </p>
 * 
 * <pre>
 * {@code
 *   FooDataSourceFactory factory = DataSourceFatory.newFactory("com.foo.FooDataSourceFatory");
 *   FooDataSource dataSource = factory.builder()
 *       .url("scott/tiger@host:port")
 *       .build();
 *   FooConnection conn = dataSource.getConnection();
 *   CompletionStage<Long> count = conn.rowOperation(sql)
 *       .set("param", value, AdbaType.VARCHAR)
 *       .foo()
 *       .submit()
 *       .getCompletionStage();
 * }
 * </pre>
 * 
 * <p>
 * Notice that there are no casts, yet both standard methods an the vendor extension
 * method foo can be referenced. This is possible only if the implementation exposes
 * all the necessary types and provides covariant overrides for every method that
 * returns one of those types. Implementations are expected (required?) to do this.
 * </p>
 * 
 * <p>
 * If an implementation does not expose any implementation specific methods or 
 * types, that implementation is not required to provide covariant overrides that
 * return implementation specific types.</p>
 * 
 * 
 */
 package jdk.incubator.sql2;
 
