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
 * A {@link Session} is an abstraction of a SQL database and
 * a group of {@link Operation}s to be executed by that SQL database. No method
 * on {@link Session} or any of its dependent objects ({@link RowOperation}
 * etc) blocks. Any method that might block must execute any potentially blocking
 * action in a thread other than the calling thread.
 * 
 * <p>
 * A {@link Session} is independent of any particular data source. Any data 
 * source that meets the specifications set by the {@link Session.Builder} can
 * be used to execute the {@link Operation}s submitted to the {@link Session}.
 * An application is expected to create, use, and close {@link Session}s as 
 * needed. An application should hold a {@link Session} only when required by
 * data source semantics. An implementation should cache and reused data source
 * resources as appropriate. {@link Session}s should not be cached.
 *
 * <p>
 * An implementation of this type must be thread safe as result and error
 * handlers running asynchronously may be accessing a {@link Session} in
 * parallel with each other and with a user thread. {@link Session}s are not
 * required to support multiplexed use; a single {@link Session} should be
 * used for only one unit of work at a time. Executing independent units of work
 * on a single {@link Session} in parallel will most likely lead to
 * unpredictable outcomes. As a rule of thumb only one user thread should access
 * a {@link Session} at a time. Such a user thread should execute a complete
 * unit of work before another user thread accesses the {@link Session}. An
 * implementation may support parallel multiplexed use, but it is not required.</p>
 *
 * <p>
 * All methods inherited from OperationGroup throw IllegalStateException if the
 * the {@link Session} is not active.</p>
 */
public interface Session extends AutoCloseable, OperationGroup<Object, Object> {

  /**
   * Identifies the operational state of a {@link Session}.
   */
  public enum Lifecycle {
    /**
     * unattached. When a attach {@link Operation} is completed successfully
     * -&gt; {@link OPEN}. If {@link deactivate} is called -&gt;
     * {@link NEW_INACTIVE}. If {@link abort} is called -&gt; {@link ABORTING}.
     * No {@link Operation}s other than attach and close will be performed. A
 Session in this state is both 'open' and 'active'.
     */
    NEW,
    /**
     * Unattached and inactive. Any queued attach or close {@link Operation}
     * is performed. No work can be submitted. If the {@link activate} method is
     * called -&gt; {@link NEW}. If a attach {@link Operation} completes -&gt;
     * {@link INACTIVE}. If a close {@link Operation} is executed -&gt;
     * {@link CLOSING}. If {@link abort} is called -&gt; {@link ABORTING}. A
 Session in this state is 'open'.
     */
    NEW_INACTIVE,
    /**
     * fully operational. Work is queued and performed. If {@link deactivate} is
     * called -&gt; {@link INACTIVE}. If a close {@link Operation} is executed
     * -&gt; {@link CLOSING}. If {@link abort} is called -&gt; {@link ABORTING}.
 A Session in this state is both 'open' and 'active'.
     */
    OPEN,
    /**
     * Not available for new work. Queued work is performed. No work can be
     * submitted. If the {@link activate} method is called -&gt; {@link OPEN}.
     * If a close {@link Operation} is executed -&gt; {@link CLOSING}. If
     * {@link abort} is called -&gt; {@link ABORTING}. A {@link Session} in
     * this state is 'open'.
     */
    INACTIVE,
    /**
     * Work in progress is completed but no additional work is started or
     * queued. Attempting to queue work throws {@link IllegalStateException}.
     * When the currently executing {@link Operation}s are completed -&gt;
     * {@link CLOSED}. All other queued Operations are completed exceptionally
 with SqlSkippedException. A Session in this state is 'closed'.
     */
    CLOSING,
    /**
     * Work is neither queued nor performed. The currently executing
     * {@link Operation}s, if any, are terminated, exceptionally if necessary.
     * Any queued {@link Operation}s are terminated exceptionally with
     * {@link SqlSkippedException}. Attempting to queue work throws
     * {@link IllegalStateException}. When the queue is empty -&lt;
     * {@link CLOSED}. A Session in this state is 'closed'.
     */
    ABORTING,
    /**
     * Work is neither queued nor performed. Attempting to queue work throws
     * {@link IllegalStateException}. A Session in this state is 'closed'.
     */
    CLOSED;
    
    static {
      NEW.init(true, true, NEW, NEW_INACTIVE, OPEN, ABORTING, CLOSING, CLOSED);
      NEW_INACTIVE.init(true, false, NEW, NEW_INACTIVE, INACTIVE, ABORTING, CLOSING, CLOSED);
      OPEN.init(true, true, OPEN, INACTIVE, OPEN, ABORTING, CLOSING, CLOSED);
      INACTIVE.init(true, false, OPEN, INACTIVE, INACTIVE, ABORTING, INACTIVE, INACTIVE);
      CLOSING.init(false, true, CLOSING, CLOSING, CLOSING, ABORTING, CLOSING, CLOSED);
      ABORTING.init(false, true, ABORTING, ABORTING, ABORTING, ABORTING, ABORTING, CLOSED);
      CLOSED.init(false, true, CLOSED, CLOSED, CLOSED, CLOSED, CLOSED, CLOSED);
    }
    
    private boolean isOpen;
    private boolean isActive;
    private Lifecycle onActivate;
    private Lifecycle onDeactivate;
    private Lifecycle onAttach;
    private Lifecycle onAbort;
    private Lifecycle onClose;
    private Lifecycle onClosed;
    
    private void init(boolean io, boolean ia, Lifecycle ac, Lifecycle da, Lifecycle cn, Lifecycle ab, Lifecycle cl, Lifecycle cd) {
      isOpen = io;
      isActive = ia;
      onActivate = ac;
      onDeactivate = da;
      onAttach = cn;
      onAbort = ab;
      onClose = cl;
      onClosed = cd;
    }
    public boolean isOpen() {
      return isOpen;
    }
    
    public boolean isActive() {
      return isActive;
    }
    
    public Lifecycle activate() {
      return onActivate;
    }
    
    public Lifecycle deactivate() {
      return onDeactivate;
    }
    
    public Lifecycle attach() {
      return onAttach;
    }
    
    public Lifecycle abort() {
      return onAbort;
    }
    
    public Lifecycle close() {
      return onClose;
    }
    
    public Lifecycle closed() {
      return onClosed;
    }
  
  }

  /**
   * Specifiers for how much effort to put into validating a {@link Session}.
   * The amount of effort put into checking should be non-decreasing from NONE
   * (least effort) to COMPLETE (most effort). Exactly what is checked is
   * implementation dependent. For example, a memory resident database driver
   * might implement SOCKET and NETWORK to be the same as LOCAL. SERVER might
   * verify that a database manager thread is running and COMPLETE might trigger
   * the database manager thread to run a deadlock detection algorithm.
   */
  public enum Validation {
    /**
     * isValid fails only if the {@link Session} is closed.
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
     * {@link Session} this method is called whenever that
     * {@link Session}'s lifecycle changes. Note that the lifecycle may have
     * changed again by the time this method is called so the
     * {@link Session}'s current lifecycle may be different from the value of
     * {@code current}.
     *
     * @param session the {@link Session}
     * @param previous the previous value of the lifecycle
     * @param current the new value of the lifecycle
     */
    public void lifecycleEvent(Session session, Lifecycle previous, Lifecycle current);
  }

  /**
   * A {@link Session} builder. A {@link Session} is initially in the
   * {@link Session.Lifecycle#NEW} lifecycle state. It transitions to the
   * {@link Session.Lifecycle#OPEN} lifecycle state when fully initialized or
   * to {@link Session.Lifecycle#CLOSED} if initialization fails.
   *
   */
  public interface Builder {

    /**
     * Specify a property and its value for the built {@link Session}.
     *
     * @param p {@link SessionProperty} to set. Not {@code null}.
     * @param v value for the property
     * @return this {@link Builder}
     * @throws IllegalArgumentException if {@code p.validate(v)} does not return
     * true, if this method has already been called with the property
     * {@code p}, or the implementation does not support the {@link SessionProperty}.
     */
    public Builder property(SessionProperty p, Object v);

    /**
     * Return a {@link Session} with the attributes specified. Note that the
     * {@link Session} may not be attached to a server. Call one of the
     * {@link attach} convenience methods to attach the {@link Session} to
     * a server. The lifecycle of the new {@link Session} is
     * {@link Lifecycle#NEW}.
 
 This method cannot block. If the DataSource is unable to support a new
 Session when this method is called, this method throws SqlException.
 Note that the implementation does not have to allocate scarce resources to
 the new {@link Session} when this method is called so limiting the
     * number of {@link Session}s is not required to limit the use of
     * scarce resources. It may be appropriate to limit the number of 
     * {@link Session}s for other reasons, but that is implementation dependent.
     *
     * @return a {@link Session}
     * @throws IllegalStateException if this method has already been called or
 if the implementation cannot create a Session with the specified
 {@link SessionProperty}s.
     * @throws IllegalStateException if the {@link DataSource} that created this
     * {@link Builder} is closed
     * @throws SqlException if creating a {@link Session} would exceed some
     * limit
     */
    public Session build();
  }

  /**
   * Returns an {@link Operation} that attaches this {@link Session} to a
   * data source. If the Operation completes successfully and the lifecycle is
   * {@link Lifecycle#NEW} -&gt; {@link Lifecycle#OPEN}. If lifecycle is
   * {@link Lifecycle#NEW_INACTIVE} -&gt; {@link Lifecycle#INACTIVE}. If the
   * {@link Operation} completes exceptionally the lifecycle -&gt;
   * {@link Lifecycle#CLOSED}. The lifecycle must be {@link Lifecycle#NEW} or
   * {@link Lifecycle#NEW_INACTIVE} when the {@link Operation} is executed.
   * Otherwise the {@link Operation} will complete exceptionally with
   * {@link SqlException}.
   *
   * Note: It is highly recommended to use the {@link attach()} convenience
   * method or to use {@link DataSource#getSession} which itself calls
   * {@link attach()}. Unless there is a specific need, do not call this method
   * directly.
   *
   * @return an {@link Operation} that attaches this {@link Session} to a
   * server.
   * @throws IllegalStateException if this {@link Session} is in a lifecycle
   * state other than {@link Lifecycle#NEW}.
   */
  public Operation<Void> attachOperation();

  /**
   * Convenience method that supports the fluent style of the builder needed by
   * try with resources.
   *
   * Note: A {@link Session} is an {@link OperationGroup} and so has some
   * advanced features that most users do not need. Management of these features
   * is encapsulated in this method and the corresponding {@link close()}
   * convenience method. The vast majority of users should just use these
   * methods and not worry about the advanced features. The convenience methods
   * do the right thing for the overwhelming majority of use cases. A tiny
   * number of users might want to take advantage of the advanced features that
   * {@link OperationGroup} brings to {@link Session} and so would call
   * {@link attachOperation} directly.
   *
   * @return this Session
   * @throws IllegalStateException if this {@link Session} is in a lifecycle
   * state other than {@link Lifecycle#NEW}.
   */
  public default Session attach() {
    this.submitHoldingForMoreMembers();
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
   * @return this {@link Session}
   * @throws IllegalStateException if this {@link Session} is in a lifecycle
   * state other than {@link Lifecycle#NEW}.
   */
  public default Session attach(Consumer<Throwable> onError) {
    this.submitHoldingForMoreMembers();
    this.attachOperation()
            .submit()
            .getCompletionStage()
            .exceptionally(t -> { onError.accept(t); return null; } );
    return this;
  }

  /**
   * Returns an {@link Operation} that verifies that the resources are available
   * and operational. Successful completion of that {@link Operation} implies
   * that at some point between the beginning and end of the {@link Operation}
 the Session was working properly to the extent specified by {@code depth}.
   * There is no guarantee that the {@link Session} is still working after 
   * completion. If the {@link Session} is not valid the Operation completes
   * exceptionally.
   *
   * @param depth how completely to check that resources are available and
   * operational. Not {@code null}.
   * @return an {@link Operation} that will validate this {@link Session}
   * @throws IllegalStateException if this Session is not active
   */
  public Operation<Void> validationOperation(Validation depth);

  /**
   * Convenience method to validate a {@link Session}.
   *
   * @param depth how completely to check that resources are available and
   * operational. Not {@code null}.
   * @param minTime how long to wait. If 0, wait forever
   * @param onError called if validation fails or times out. May be
   * {@code null}.
   * @return this {@link Session}
   * @throws IllegalArgumentException if {@code milliseconds} &lt; 0 or
   * {@code depth} is {@code null}.
   * @throws IllegalStateException if this Session is not active
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
   * Create an {@link Operation} to close this {@link Session}. When the
   * {@link Operation} is executed, if this {@link Session} is open -&gt;
   * {@link Lifecycle#CLOSING}. If this {@link Session} is closed executing
   * the returned {@link Operation} is a no-op. When the queue is empty and all
   * resources released -&gt; {@link Lifecycle#CLOSED}.
   *
   * A close {@link Operation} is never skipped. Even when the
   * {@link Session} is dependent, the default, and an {@link Operation}
   * completes exceptionally, a close {@link Operation} is still executed. If
   * the {@link Session} is parallel, a close {@link Operation} is not
   * executed so long as there are other {@link Operation}s or the
   * {@link Session} is held for more {@link Operation}s.
   *
   * Note: It is highly recommended to use try with resources or the
   * {@link close()} convenience method. Unless there is a specific need, do not
   * call this method directly.
   *
   * @return an {@link Operation} that will close this {@link Session}.
   * @throws IllegalStateException if the Session is not active
   */
  public Operation<Void> closeOperation();

  /**
   * Create and submit an {@link Operation} to close this {@link Session}.
   * Convenience method.
   *
   * Note: A {@link Session} is an {@link OperationGroup} and so has some
   * advanced features; that most users do not need. Management of these
   * features is encapsulated in this method and the corresponding
   * {@link attach()} convenience method. The vast majority of users should
   * just use these methods and not worry about the advanced features. The
   * convenience methods do the right thing for the overwhelming majority of use
   * cases. A tiny number of user might want to take advantage of the advanced
   * features that {@link OperationGroup} brings to {@link Session} and so
   * would call {@link closeOperation} directly.
   *
   * @throws IllegalStateException if the Session is not active
   */
  @Override
  public default void close() {
    this.closeOperation()
            .submit();
    this.releaseProhibitingMoreMembers();
  }

  /**
   * Create a new {@link OperationGroup} for this {@link Session}.
   *
   * @param <S> the result type of the member {@link Operation}s of the returned
   * {@link OperationGroup}
   * @param <T> the result type of the collected results of the member
   * {@link Operation}s
   * @return a new {@link OperationGroup}.
   * @throws IllegalStateException if this Session is not active
   */
  public <S, T> OperationGroup<S, T> operationGroup();

  /**
   * Returns a new {@link TransactionCompletion} that can be used as an argument to an
   * endTransaction Operation.
   *
   * It is most likely an error to call this within an error handler, or any
   * handler as it is very likely that when the handler is executed the next
   * submitted endTransaction {@link Operation} will have been created with a 
 different TransactionCompletion.
 
 ISSUE: Should this be moved to OperationGroup?
   *
   * @return a new {@link TransactionCompletion}. Not null.
   * @throws IllegalStateException if this Session is not active
   */
  public TransactionCompletion transactionCompletion();
  
  /**
   * Unconditionally perform a transaction rollback. Create an endTransaction 
   * {@link Operation}, set it to rollback only, and submit it. The endTransaction
   * is never skipped. Convenience method. To execute a commit call 
   * {@link OperationGroup#commitMaybeRollback(jdk.incubator.sql2.TransactionCompletion)}.
   *
   * @return this {@link OperationGroup}
   * @see OperationGroup#commitMaybeRollback(jdk.incubator.sql2.TransactionCompletion) 
   */
  public default CompletionStage<TransactionOutcome> rollback() {
    TransactionCompletion t = transactionCompletion();
    t.setRollbackOnly();
    catchErrors();
    return this.endTransactionOperation(t).submit().getCompletionStage();
  }

  /**
   * Register a listener that will be called whenever there is a change in the
   * lifecycle of this {@link Session}.If the listener is already registered
 this is a no-op. ISSUE: Should lifecycleListener be a SessionProperty so that it is 
 always reestablished on Session.activate?
   *
   * @param listener Not {@code null}.
   * @return this Session
   * @throws IllegalStateException if this Session is not active
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
   * @throws IllegalStateException if this Session is not active
   */
  public Session deregisterLifecycleListener(SessionLifecycleListener listener);

  /**
   * Return the current lifecycle of this {@link Session}. 
   *
   * @return the current lifecycle of this {@link Session}.
   */
  public Lifecycle getSessionLifecycle();

  /**
   * Terminate this {@link Session}. If lifecycle is
   * {@link Lifecycle#NEW}, {@link Lifecycle#OPEN}, {@link Lifecycle#INACTIVE}
   * or {@link Lifecycle#CLOSING} -&gt; {@link Lifecycle#ABORTING} If lifecycle
   * is {@link Lifecycle#ABORTING} or {@link Lifecycle#CLOSED} this is a no-op.
   * If an {@link Operation} is currently executing, terminate it immediately.
   * Remove all remaining {@link Operation}s from the queue. {@link Operation}s
   * are not skipped. They are just removed from the queue.
   *
   * @return this {@link Session}
   */
  public Session abort();

  /**
   * Return the set of properties configured on this {@link Session}
   * excepting any sensitive properties. Neither the key nor the value for
   * sensitive properties are included in the result. Properties (other than
   * sensitive properties) that have default values are included even when not
   * explicitly set. Properties that have no default value and are not set
   * explicitly are not included.
   *
   * @return a {@link Map} of property, value. Not modifiable. May be retained.
   * Not {@code null}.
   * @throws IllegalStateException if this Session is not active
   */
  public Map<SessionProperty, Object> getProperties();
  
  /**
   *
   * @return a {@link ShardingKey.Builder} for this {@link Session}
   * @throws IllegalStateException if this Session is not active
   */
  public ShardingKey.Builder shardingKeyBuilder();

  /**
   * Provide a method that this {@link Session} will call to control the rate
   * of {@link Operation} submission. This {@link Session} will call
   * {@code request} with a positive argument when the {@link Session} is
   * able to accept more {@link Operation} submissions. The difference between
   * the sum of all arguments passed to {@code request} and the number of
   * {@link Operation}s submitted after this method is called is the
   * <i>demand</i>. The demand must always be non-negative. If an
   * {@link Operation} is submitted that would make the demand negative the call
   * to {@link Operation#submit} throws {@link IllegalStateException}. Prior to
   * a call to {@code requestHook}, the demand is defined to be infinite.
   * After a call to {@code requestHook}, the demand is defined to be
   * zero and is subsequently computed as described previously.
   * {@link Operation}s submitted prior to the call to {@code requestHook} do
   * not affect the demand.
   *
   * @param request accepts calls to increase the demand. Not null.
   * @return this {@link Session}
   * @throws IllegalStateException if this method has been called previously or
   * this {@link Session} is not active.
   */
  public Session requestHook(LongConsumer request);

  /**
   * Make this {@link Session} ready for use. A newly created
   * {@link Session} is active. Calling this method on a {@link Session}
   * that is active is a no-op. If the lifecycle is {@link Lifecycle#INACTIVE}
   * -&gt; {@link Lifecycle#OPEN}. If the lifecycle is
   * {@link Lifecycle#NEW_INACTIVE} -&gt; {@link Lifecycle#NEW}.
   *
   * @return this {@link Session}
   * @throws IllegalStateException if this {@link Session} is closed.
   */
  public Session activate();

  /**
   * Makes this {@link Session} inactive. After a call to this method
   * previously submitted Operations will be executed normally. If the lifecycle
   * is {@link Lifecycle#NEW} -&gt; {@link Lifecycle#NEW_INACTIVE}. if the
   * lifecycle is {@link Lifecycle#OPEN} -&gt; {@link Lifecycle#INACTIVE}. If
   * the lifecycle is {@link Lifecycle#INACTIVE} or
   * {@link Lifecycle#NEW_INACTIVE} this method is a no-op. After calling this
   * method or calling any method other than {@link deactivate}, {@link activate},
   * {@link abort}, or {@link getSessionLifecycle} will throw
   * {@link IllegalStateException}. Data source state not specified
   * by {@link Session.Builder} may not be preserved.
   * 
   * <p>
   * In general {@link Session}s should not be pooled as the implementation
   * should cache and reuse the data source resources that back {@link Session}s
   * as appropriate, not cache the {@link Session}s themselves. 
   * However, any {@link Session} pool is required by default to
   * call {@code deactivate} when putting a {@link Session} into the pool. The
   * pool is required by default to call {@code activate} when removing a
   * {@link Session} from the pool for use. A pool may have an optional mode where
   * it does not call {@code deactivate}/{@code activate} as required above. The
   * behavior of the pool and {@link Session}s cached in the pool in such a
   * mode is entirely implementation dependent.</p>
   * 
   * @return this {@link Session}
   * @throws IllegalStateException if this {@link Session} is closed
   */
  public Session deactivate();
  
}
