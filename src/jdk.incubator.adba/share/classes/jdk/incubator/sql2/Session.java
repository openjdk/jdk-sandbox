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
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * A {@code Session} is an abstraction of a SQL database and a group of
 * {@link Operation}s to be executed by that SQL database. No method on
 * {@code Session} or any of its dependent objects ({@link RowOperation} etc)
 * blocks. Any method that might block must execute any potentially blocking
 * action in a thread other than the calling thread.
 *
 * <p>
 * A {@code Session} is independent of any particular data source. Any data
 * source that meets the specifications set by the {@link Session.Builder} can
 * be used to execute the {@link Operation}s submitted to the {@code Session}.
 * An application is expected to create, use, and close {@code Session}s as
 * needed. An application should hold a {@code Session} only when required by
 * data source semantics. An implementation should cache and reused data source
 * resources as appropriate. {@code Session}s should not be cached.
 *
 * <p>
 * An implementation of this type must be thread safe as result and error
 * handlers running asynchronously may be accessing a {@code Session} in
 * parallel with each other and with a user thread. {@code Session}s are not
 * required to support multiplexed use; a single {@code Session} should be used
 * for only one unit of work at a time. Executing independent units of work on a
 * single {@code Session} in parallel will most likely lead to unpredictable
 * outcomes. As a rule of thumb only one user thread should access a
 * {@code Session} at a time. Such a user thread should execute a complete unit
 * of work before another user thread accesses the {@code Session}. An
 * implementation may support parallel multiplexed use, but it is not
 * required.</p>
 *
 * <p>
 * All methods inherited from {@link OperationGroup} throw
 * {@link IllegalStateException} if the the {@code Session} is closed.</p>
 */
public interface Session extends AutoCloseable, OperationGroup<Object, Object> {

  /**
   * Identifies the operational state of a {@code Session}.
   */
 public enum Lifecycle {
   /**
    * A newly created Session is in this state. When a attach {@link Operation}
    * is completed successfully -&gt; {@link ATTACHED}. If {@link Session#abort}
    * is called -&gt; {@link ABORTING}. No {@link Operation}s other than attach
    * and close will be performed. A {@code Session} in this state is 'open'.
    */
   NEW,
   /**
    * fully operational. Work is queued and performed. If a close
    * {@link Operation} is submitted -&gt; {@link CLOSING}. If
    * {@link Session#abort} is called -&gt; {@link ABORTING}. A {@code Session}
    * in this state is 'open' and 'working'.
    */
   ATTACHED,
   /**
    * Work in progress is completed but no additional work can be submitted. 
    * Attempting to queue work throws {@link IllegalStateException}.
    * When the close {@link Operation} is completed -&gt;
    * {@link CLOSED}. A {@code Session} in this state is 'closed' and 'working'.
    */
   CLOSING,
   /**
    * Work is neither queued nor performed. The currently executing
    * {@link Operation}s, if any, are terminated immediately. Any queued
    * {@link Operation}s are removed from the queue. Attempting to queue work
    * throws {@link IllegalStateException}. When the queue is empty -&lt;
    * {@link CLOSED}. A {@code Session} in this state is 'closed'.
    */
   ABORTING,
   /**
    * Work is neither queued nor performed. Attempting to queue work throws
    * {@link IllegalStateException}. A Session in this state is 'closed'.
    */
   CLOSED;

   static {
     NEW.init(false, false, ATTACHED, CLOSING, ABORTING, CLOSED);
     ATTACHED.init(false, true, ATTACHED, CLOSING, ABORTING, CLOSED);
     CLOSING.init(true, true, CLOSING, CLOSING, ABORTING, CLOSED);
     ABORTING.init(true, false, ABORTING, ABORTING, ABORTING, CLOSED);
     CLOSED.init(true, false, CLOSED, CLOSED, CLOSED, CLOSED);
   }

   private boolean isClosed;
   private boolean isWorking;
   private Lifecycle onAttach;
   private Lifecycle onClose;
   private Lifecycle onAbort;
   private Lifecycle onClosed;

   private void init(boolean isClosed,
                     boolean isWorking,
                     Lifecycle onAttach, 
                     Lifecycle onClose, 
                     Lifecycle onAbort, 
                     Lifecycle onClosed) {
     this.isClosed = isClosed;
     this.isWorking = isWorking;
     this.onAttach = onAttach;
     this.onClose = onClose;
     this.onAbort = onAbort;
     this.onClosed = onClosed;
   }

   public boolean isClosed() {
     return isClosed;
   }

   public boolean isWorking() {
     return isWorking;
   }

   public Lifecycle attach() {
     return onAttach;
   }

   public Lifecycle close() {
     return onClose;
   }

   public Lifecycle abort() {
     return onAbort;
   }

   public Lifecycle closed() {
     return onClosed;
   }

 }

  /**
   * Specifiers for how much effort to put into validating a {@code Session}.
   * The amount of effort put into checking should be non-decreasing from NONE
   * (least effort) to COMPLETE (most effort). Exactly what is checked is
   * implementation dependent. For example, a memory resident database driver
   * might implement SOCKET and NETWORK to be the same as LOCAL. SERVER might
   * verify that a database manager thread is running and COMPLETE might trigger
   * the database manager thread to run a deadlock detection algorithm.
   */
  public enum Validation {
    /**
     * isValid fails only if the {@code Session} is closed.
     */
    NONE,
    /**
     * {@link NONE} plus check local resources
     */
    LOCAL,
    /**
     * {@link LOCAL} plus the server isn't obviously unreachable (dead socket)
     */
    SOCKET,
    /**
     * {@link SOCKET} plus the network is intact (network PING)
     */
    NETWORK,
    /**
     * {@link NETWORK} plus significant server processes are running
     */
    SERVER,
    /**
     * everything that can be checked is working. At least {@link SERVER}.
     */
    COMPLETE;
  }

  /**
   * A Listener that is notified of changes in a Session's lifecycle.
   */
  public interface SessionLifecycleListener extends java.util.EventListener {

    /**
     * If this {@link java.util.EventListener} is registered with a
     * {@code Session} this method is called whenever that {@code Session}'s
     * lifecycle changes. Note that the lifecycle may have changed again by the
     * time this method is called so the {@code Session}'s current lifecycle may
     * be different from the value of {@code current}.
     *
     * @param session the {@code Session}
     * @param previous the previous value of the lifecycle
     * @param current the new value of the lifecycle
     */
    public void lifecycleEvent(Session session, Lifecycle previous, Lifecycle current);
  }

  /**
   * A {@code Session} builder. A {@code Session} is initially in the
   * {@link Session.Lifecycle#NEW} lifecycle state. It transitions to 
   * {@link Session.Lifecycle#CLOSED} if initialization fails.
   *
   */
  public interface Builder {

    /**
     * Specify a property and its value for the built {@code Session}.
     *
     * @param p {@link SessionProperty} to set. Not {@code null}.
     * @param v value for the property. If v is {@link Cloneable} it is cloned
     * otherwise it is retained.
     * @return this {@link Builder}
     * @throws IllegalArgumentException if {@code p.validate(v)} does not return
     * true, if this method has already been called with the property {@code p},
     * or the implementation does not support the {@link SessionProperty}.
     */
    public Builder property(SessionProperty p, Object v);

    /**
     * Return a {@code Session} with the attributes specified. Note that the
     * {@code Session} may not be attached to a server. Call one of the
     * {@link attach} convenience methods to attach the {@code Session} to a
     * server. The lifecycle of the new {@code Session} is
     * {@link Lifecycle#NEW}.
     *
     * This method cannot block. If the DataSource is unable to support a new
     * Session when this method is called, this method throws SqlException. Note
     * that the implementation does not have to allocate scarce resources to the
     * new {@code Session} when this method is called so limiting the number of
     * {@code Session}s is not required to limit the use of scarce resources. It
     * may be appropriate to limit the number of {@code Session}s for other
     * reasons, but that is implementation dependent.
     *
     * @return a {@code Session}
     * @throws IllegalStateException if this method has already been called or
     * if the implementation cannot create a Session with the specified
     * {@link SessionProperty}s.
     * @throws IllegalStateException if the {@link DataSource} that created this
     * {@link Builder} is closed
     * @throws SqlException if creating a {@code Session} would exceed some
     * limit
     */
    public Session build();
  }

  /**
   * Returns an {@link Operation} that attaches this {@code Session} to a data
   * source. If the Operation completes successfully and the lifecycle is
   * {@link Lifecycle#NEW} -&gt; {@link Lifecycle#ATTACHED}. If the
   * {@link Operation} completes exceptionally the lifecycle -&gt;
   * {@link Lifecycle#CLOSED}. The lifecycle must be {@link Lifecycle#NEW}  when the {@link Operation} is executed.
   * Otherwise the {@link Operation} will complete exceptionally with
   * {@link SqlException}.
   *
   * Note: It is highly recommended to use the {@link attach()} convenience
   * method or to use {@link DataSource#getSession} which itself calls
   * {@link attach()}. Unless there is a specific need, do not call this method
   * directly.
   *
   * @return an {@link Operation} that attaches this {@code Session} to a
   * server.
   * @throws IllegalStateException if this {@code Session} is in a lifecycle
   * state other than {@link Lifecycle#NEW}.
   */
  public Operation<Void> attachOperation();

  /**
   * Convenience method that supports the fluent style of the builder needed by
   * try with resources.
   *
   * Note: A {@code Session} is an {@link OperationGroup} and so has some
   * advanced features that most users do not need. Management of these features
   * is encapsulated in this method and the corresponding {@link close()}
   * convenience method. The vast majority of users should just use these
   * methods and not worry about the advanced features. The convenience methods
   * do the right thing for the overwhelming majority of use cases. A tiny
   * number of users might want to take advantage of the advanced features that
   * {@link OperationGroup} brings to {@code Session} and so would call
   * {@link attachOperation} directly.
   *
   * @return this Session
   * @throws IllegalStateException if this {@code Session} is in a lifecycle
   * state other than {@link Lifecycle#NEW}.
   */
  public default Session attach() {
    this.submit();
    this.attachOperation()
            .submit();
    return this;
  }

  /**
   * Convenience method that supports the fluent style of the builder needed by
   * try with resources.
   *
   * @param onError an Exception handler that is called if the attach
   * {@link Operation} completes exceptionally.
   * @return this {@code Session}
   * @throws IllegalStateException if this {@code Session} is in a lifecycle
   * state other than {@link Lifecycle#NEW}.
   */
  public default Session attach(Consumer<Throwable> onError) {
    this.submit();
    this.attachOperation()
            .submit()
            .getCompletionStage()
            .exceptionally(t -> {
              onError.accept(t);
              return null;
            });
    return this;
  }

  /**
   * Returns an {@link Operation} that verifies that the resources are available
   * and operational. Successful completion of that {@link Operation} implies
   * that at some point between the beginning and end of the {@link Operation}
   * the Session was working properly to the extent specified by {@code depth}.
   * There is no guarantee that the {@code Session} is still working after
   * completion. If the {@code Session} is not valid the Operation completes
   * exceptionally.
   *
   * @param depth how completely to check that resources are available and
   * operational. Not {@code null}.
   * @return an {@link Operation} that will validate this {@code Session}
   * @throws IllegalStateException if this Session is closed.
   */
  public Operation<Void> validationOperation(Validation depth);

  /**
   * Convenience method to validate a {@code Session}.
   *
   * @param depth how completely to check that resources are available and
   * operational. Not {@code null}.
   * @param minTime how long to wait. If 0, wait forever
   * @param onError called if validation fails or times out. May be
   * {@code null}.
   * @return this {@code Session}
   * @throws IllegalArgumentException if {@code milliseconds} &lt; 0 or
   * {@code depth} is {@code null}.
   * @throws IllegalStateException if this Session is closed.
   */
  public default Session validate(Validation depth,
                                  Duration minTime,
                                  Consumer<Throwable> onError) {
    this.validationOperation(depth)
            .timeout(minTime)
            .onError(onError)
            .submit();
    return this;
  }

  /**
   * Create an {@link Operation} to close this {@code Session}. When the
   * {@link Operation} is submitted, if this {@code Session} is not closed -&gt;
   * {@link Lifecycle#CLOSING}. If this {@code Session} is closed executing the
   * returned {@link Operation} is a no-op. When the queue is empty and all
   * resources released -&gt; {@link Lifecycle#CLOSED}.
   *
   * A close {@link Operation} is never skipped. Even when the {@code Session}
   * is dependent, the default, and an {@link Operation} completes
   * exceptionally, a close {@link Operation} is still executed. If the
   * {@code Session} is parallel, a close {@link Operation} is not executed so
   * long as there are other {@link Operation}s or the {@code Session} is held
   * for more {@link Operation}s.
   *
   * Note: It is highly recommended to use try with resources or the
   * {@link close()} convenience method. Unless there is a specific need, do not
   * call this method directly.
   *
   * @return an {@link Operation} that will close this {@code Session}.
   * @throws IllegalStateException if the Session is closed.
   */
  public Operation<Void> closeOperation();

  /**
   * Create and submit an {@link Operation} to close this {@code Session}.
   * Convenience method.
   *
   * Note: A {@code Session} is an {@link OperationGroup} and so has some
   * advanced features; that most users do not need. Management of these
   * features is encapsulated in this method and the corresponding
   * {@link attach()} convenience method. The vast majority of users should just
   * use these methods and not worry about the advanced features. The
   * convenience methods do the right thing for the overwhelming majority of use
   * cases. A tiny number of user might want to take advantage of the advanced
   * features that {@link OperationGroup} brings to {@code Session} and so would
   * call {@link closeOperation} directly.
   *
   * @throws IllegalStateException if the Session is closed.
   */
  /**
   * {@inheritDoc}
   */
  @Override
  public default void close() {
    this.closeOperation()
            .submit();
    // submitting a close Operation must satisfy the requirements of
    // OperationGroup.close()
  }

  /**
   * Create a new {@link OperationGroup} for this {@code Session}.
   *
   * @param <S> the result type of the member {@link Operation}s of the returned
   * {@link OperationGroup}
   * @param <T> the result type of the collected results of the member
   * {@link Operation}s
   * @return a new {@link OperationGroup}.
   * @throws IllegalStateException if this Session is closed.
   */
  public <S, T> OperationGroup<S, T> operationGroup();

  /**
   * Returns a new {@link TransactionCompletion} that can be used as an argument
   * to an endTransaction Operation.
   *
   * It is most likely an error to call this within an error handler, or any
   * handler as it is very likely that when the handler is executed the next
   * submitted endTransaction {@link Operation} will have been created with a
   * different TransactionCompletion.
   *
   * ISSUE: Should this be moved to OperationGroup?
   *
   * @return a new {@link TransactionCompletion}. Not null.
   * @throws IllegalStateException if this Session is closed.
   */
  public TransactionCompletion transactionCompletion();

  /**
   * Unconditionally perform a transaction rollback. Create an endTransaction
   * {@link Operation}, set it to rollback only, and submit it. The
   * endTransaction is never skipped. Convenience method. To execute a commit
   * call
   * {@link OperationGroup#commitMaybeRollback(jdk.incubator.sql2.TransactionCompletion)}.
   *
   * @return this {@link OperationGroup}
   * @see
   * OperationGroup#commitMaybeRollback(jdk.incubator.sql2.TransactionCompletion)
   */
  public default CompletionStage<TransactionOutcome> rollback() {
    TransactionCompletion t = transactionCompletion();
    t.setRollbackOnly();
    catchErrors();
    return this.endTransactionOperation(t).submit().getCompletionStage();
  }

  /**
   * Register a listener that will be called whenever there is a change in the
   * lifecycle of this {@code Session}.If the listener is already registered
   * this is a no-op. ISSUE: Should lifecycleListener be a SessionProperty so
   * that it is always reestablished on Session.activate?
   *
   * @param listener Not {@code null}.
   * @return this Session
   * @throws IllegalStateException if this Session is not open
   */
  public Session registerLifecycleListener(SessionLifecycleListener listener);

  /**
   * Removes a listener that was registered by calling
   * registerLifecycleListener.Sometime after this method is called the listener
   * will stop receiving lifecycle events. If the listener is not registered,
   * this is a no-op.
   *
   * @param listener Not {@code null}.
   * @return this Session
   */
  public Session deregisterLifecycleListener(SessionLifecycleListener listener);

  /**
   * Return the current lifecycle of this {@code Session}.
   *
   * @return the current lifecycle of this {@code Session}.
   */
  public Lifecycle getSessionLifecycle();

  /**
   * Terminate this {@code Session}. If lifecycle is
   * {@link Lifecycle#NEW}, {@link Lifecycle#ATTACHED}
   * or {@link Lifecycle#CLOSING} -&gt; {@link Lifecycle#ABORTING} If lifecycle
   * is {@link Lifecycle#ABORTING} or {@link Lifecycle#CLOSED} this is a no-op.
   * If an {@link Operation} is currently executing, terminate it immediately.
   * Remove all remaining {@link Operation}s from the queue. {@link Operation}s
   * are not skipped. They are just removed from the queue.
   *
   * @return this {@code Session}
   */
  public Session abort();

  /**
   * Return the set of properties configured on this {@code Session} excepting
   * any sensitive properties. Neither the key nor the value for sensitive
   * properties are included in the result. Properties (other than sensitive
   * properties) that have default values are included even when not explicitly
   * set. Properties that have no default value and are not set explicitly
   * either by the {@link DataSource} or the {@link Session.Builder} are not
   * included.
   *
   * @return a {@link Map} of property, value. Not modifiable. May be retained.
   * Not {@code null}.
   * @throws IllegalStateException if this Session is not open
   */
  public Map<SessionProperty, Object> getProperties();

  /**
   *
   * @return a {@link ShardingKey.Builder} for this {@code Session}
   * @throws IllegalStateException if this Session is not open
   */
  public ShardingKey.Builder shardingKeyBuilder();

  /**
   * Provide a method that this {@code Session} will call to control the rate of
   * {@link Operation} submission. This {@code Session} will call
   * {@code request} with a positive argument when the {@code Session} is able
   * to accept more {@link Operation} submissions. The difference between the
   * sum of all arguments passed to {@code request} and the number of
   * {@link Operation}s submitted after this method is called is the
   * <i>demand</i>. The demand must always be non-negative. If an
   * {@link Operation} is submitted that would make the demand negative the call
   * to {@link Operation#submit} throws {@link IllegalStateException}. Prior to
   * a call to {@code requestHook}, the demand is defined to be infinite. After
   * a call to {@code requestHook}, the demand is defined to be zero and is
   * subsequently computed as described previously. {@link Operation}s submitted
   * prior to the call to {@code requestHook} do not affect the demand.
   *
   * @param request accepts calls to increase the demand. Not null.
   * @return this {@code Session}
   * @throws IllegalStateException if this method has been called previously or
   * this {@code Session} is not open.
   */
  public Session requestHook(LongConsumer request);

}
