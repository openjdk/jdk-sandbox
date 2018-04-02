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
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@link Connection} is an abstraction of a connection to a SQL database and
 * a group of {@link Operation}s to be executed by that SQL database. No method
 * on {@link Connection} or any of its dependent objects ({@link RowOperation}
 * etc) blocks with the exception of those few methods with "Wait" in their
 * name. Any other method that might block must execute any potentially blocking
 * action in a thread other than the calling thread.
 *
 * All methods inherited from OperationGroup throw IllegalStateException if the
 * the connection is not active.
 */
public interface Connection extends AutoCloseable, OperationGroup<Object, Object> {

  /**
   * Identifies the operational state of a {@link Connection}.
   */
  public enum Lifecycle {
    /**
     * unconnected. When a connect {@link Operation} is completed successfully
     * -&gt; {@link OPEN}. If {@link deactivate} is called -&gt;
     * {@link NEW_INACTIVE}. If {@link abort} is called -&gt; {@link ABORTING}.
     * No {@link Operation}s other than connect and close will be performed. A
     * Connection in this state is both 'open' and 'active'.
     */
    NEW,
    /**
     * Unconnected and inactive. Any queued connect or close {@link Operation}
     * is performed. No work can be submitted. If the {@link activate} method is
     * called -&gt; {@link NEW}. If a connect {@link Operation} completes -&gt;
     * {@link INACTIVE}. If a close {@link Operation} is executed -&gt;
     * {@link CLOSING}. If {@link abort} is called -&gt; {@link ABORTING}. A
     * Connection in this state is 'open'.
     */
    NEW_INACTIVE,
    /**
     * fully operational. Work is queued and performed. If {@link deactivate} is
     * called -&gt; {@link INACTIVE}. If a close {@link Operation} is executed
     * -&gt; {@link CLOSING}. If {@link abort} is called -&gt; {@link ABORTING}.
     * A Connection in this state is both 'open' and 'active'.
     */
    OPEN,
    /**
     * Not available for new work. Queued work is performed. No work can be
     * submitted. If the {@link activate} method is called -&gt; {@link OPEN}.
     * If a close {@link Operation} is executed -&gt; {@link CLOSING}. If
     * {@link abort} is called -&gt; {@link ABORTING}. A {@link Connection} in
     * this state is 'open'.
     */
    INACTIVE,
    /**
     * Work in progress is completed but no additional work is started or
     * queued. Attempting to queue work throws {@link IllegalStateException}.
     * When the currently executing {@link Operation}s are completed -&gt;
     * {@link CLOSED}. All other queued Operations are completed exceptionally
     * with SqlSkippedException. A Connection in this state is 'closed'.
     */
    CLOSING,
    /**
     * Work is neither queued nor performed. The currently executing
     * {@link Operation}s, if any, are terminated, exceptionally if necessary.
     * Any queued {@link Operation}s are terminated exceptionally with
     * {@link SqlSkippedException}. Attempting to queue work throws
     * {@link IllegalStateException}. When the queue is empty -&lt;
     * {@link CLOSED}. A Connection in this state is 'closed'.
     */
    ABORTING,
    /**
     * Work is neither queued nor performed. Attempting to queue work throws
     * {@link IllegalStateException}. A Connection in this state is 'closed'.
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
    private Lifecycle onConnect;
    private Lifecycle onAbort;
    private Lifecycle onClose;
    private Lifecycle onClosed;
    
    private void init(boolean io, boolean ia, Lifecycle ac, Lifecycle da, Lifecycle cn, Lifecycle ab, Lifecycle cl, Lifecycle cd) {
      isOpen = io;
      isActive = ia;
      onActivate = ac;
      onDeactivate = da;
      onConnect = cn;
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
    
    public Lifecycle connect() {
      return onConnect;
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
   * Specifiers for how much effort to put into validating a {@link Connection}.
   * The amount of effort put into checking should be non-decreasing from NONE
   * (least effort) to COMPLETE (most effort). Exactly what is checked is
   * implementation dependent. For example, a memory resident database driver
   * might implement SOCKET and NETWORK to be the same as LOCAL. SERVER might
   * verify that a database manager thread is running and COMPLETE might trigger
   * the database manager thread to run a deadlock detection algorithm.
   */
  public enum Validation {
    /**
     * isValid fails only if the {@link Connection} is closed.
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
   * A Listener that is notified of changes in a Connection's lifecycle.
   */
  public interface ConnectionLifecycleListener extends java.util.EventListener {

    /**
     * If this {@link java.util.EventListener} is registered with a
     * {@link Connection} this method is called whenever that
     * {@link Connection}'s lifecycle changes. Note that the lifecycle may have
     * changed again by the time this method is called so the
     * {@link Connection}'s current lifecycle may be different from the value of
     * {@code current}.
     *
     * @param conn the {@link Connection}
     * @param previous the previous value of the lifecycle
     * @param current the new value of the lifecycle
     */
    public void lifecycleEvent(Connection conn, Lifecycle previous, Lifecycle current);
  }

  /**
   * A {@link Connection} builder. A {@link Connection} is initially in the
   * {@link Connection.Lifecycle#NEW} lifecycle state. It transitions to the
   * {@link Connection.Lifecycle#OPEN} lifecycle state when fully initialized or
   * to {@link Connection.Lifecycle#CLOSED} if initialization fails.
   *
   */
  public interface Builder {

    /**
     * Specify a property and its value for the built {@link Connection}.
     *
     * @param p {@link ConnectionProperty} to set. Not {@code null}.
     * @param v value for the property
     * @return this {@link Builder}
     * @throws IllegalArgumentException if {@code p.validate(v)} does not return
     * true or if this method has already been called with the property
     * {@code p}.
     */
    public Builder property(ConnectionProperty p, Object v);

    /**
     * Return a {@link Connection} with the attributes specified. Note that the
     * {@link Connection} may not be connected to a server. Call one of the
     * {@link connect} convenience methods to connect the {@link Connection} to
     * a server. The lifecycle of the new {@link Connection} is {@link Lifecycle#NEW}.
     *
     * @return a {@link Connection}
     * @throws IllegalStateException if this method has already been called.
     */
    public Connection build();
  }

  /**
   * Returns an {@link Operation} that connects this {@link Connection} to a
   * server. If the Operation completes successfully and the lifecycle is
   * {@link Lifecycle#NEW} -&gt; {@link Lifecycle#OPEN}. If lifecycle is
   * {@link Lifecycle#NEW_INACTIVE} -&gt; {@link Lifecycle#INACTIVE}. If the
   * {@link Operation} completes exceptionally the lifecycle -&gt;
   * {@link Lifecycle#CLOSED}. The lifecycle must be {@link Lifecycle#NEW} or
   * {@link Lifecycle#NEW_INACTIVE} when the {@link Operation} is executed.
   * Otherwise the {@link Operation} will complete exceptionally with
   * {@link SqlException}.
   *
   * Note: It is highly recommended to use the {@link connect()} convenience
   * method or to use {@link DataSource#getConnection} which itself calls
   * {@link connect()}. Unless there is a specific need, do not call this method
   * directly.
   *
   * This method exists partially to clearly explain that while creating a
   * {@link Connection} is non-blocking, the act of connecting to the server may
   * block and so is executed asynchronously. We could write a bunch of text
   * saying this but defining this method is more explicit. Given the
   * {@link connect()} convenience methods there's probably not much reason to
   * use this method, but on the other hand, who knows, so here it is.
   *
   * @return an {@link Operation} that connects this {@link Connection} to a
   * server.
   * @throws IllegalStateException if this {@link Connection} is in a lifecycle
   * state other than {@link Lifecycle#NEW}.
   */
  public Operation<Void> connectOperation();

  /**
   * Convenience method that supports the fluent style of the builder needed by
   * try with resources.
   *
   * Note: A {@link Connection} is an {@link OperationGroup} and so has some
   * advanced features that most users do not need. Management of these features
   * is encapsulated in this method and the corresponding {@link close()}
   * convenience method. The vast majority of users should just use these
   * methods and not worry about the advanced features. The convenience methods
   * do the right thing for the overwhelming majority of use cases. A tiny
   * number of users might want to take advantage of the advanced features that
   * {@link OperationGroup} brings to {@link Connection} and so would call
   * {@link connectOperation} directly.
   *
   * @return this Connection
   * @throws IllegalStateException if this {@link Connection} is in a lifecycle
   * state other than {@link Lifecycle#NEW}.
   */
  public default Connection connect() {
    this.submitHoldingForMoreMembers();
    this.connectOperation()
            .submit();
    return this;
  }

  /**
   * Convenience method that supports the fluent style of the builder needed by
   * try with resources.
   *
   * @param onError an Exception handler that is called if the connect
   * {@link Operation} completes exceptionally.
   * @return this {@link Connection}
   * @throws IllegalStateException if this {@link Connection} is in a lifecycle
   * state other than {@link Lifecycle#NEW}.
   */
  public default Connection connect(Consumer<Throwable> onError) {
    this.submitHoldingForMoreMembers();
    this.connectOperation()
            .submit()
            .getCompletionStage()
            .exceptionally(t -> { onError.accept(t); return null; } );
    return this;
  }

  /**
   * Returns an {@link Operation} that verifies that the resources are available
   * and operational. Successful completion of that {@link Operation} implies
   * that at some point between the beginning and end of the {@link Operation}
   * the Connection was working properly to the extent specified by {@code depth}.
   * There is no guarantee that the {@link Connection} is still working after 
   * completion.
   *
   * @param depth how completely to check that resources are available and
   * operational. Not {@code null}.
   * @return an {@link Operation} that will validate this {@link Connection}
   * @throws IllegalStateException if this Connection is not active
   */
  public Operation<Void> validationOperation(Validation depth);

  /**
   * Convenience method to validate a {@link Connection}.
   *
   * @param depth how completely to check that resources are available and
   * operational. Not {@code null}.
   * @param minTime how long to wait. If 0, wait forever
   * @param onError called if validation fails or times out. May be
   * {@code null}.
   * @return this {@link Connection}
   * @throws IllegalArgumentException if {@code milliseconds} &lt; 0 or
   * {@code depth} is {@code null}.
   * @throws IllegalStateException if this Connection is not active
   */
  public default Connection validate(Validation depth,
          Duration minTime,
          Function<Throwable, Void> onError) {
    this.validationOperation(depth)
            .timeout(minTime)
            .submit()
            .getCompletionStage()
            .exceptionally(onError);
    return this;
  }

  /**
   * Create an {@link Operation} to close this {@link Connection}. When the
   * {@link Operation} is executed, if this {@link Connection} is open -&gt;
   * {@link Lifecycle#CLOSING}. If this {@link Connection} is closed executing
   * the returned {@link Operation} is a no-op. When the queue is empty and all
   * resources released -&gt; {@link Lifecycle#CLOSED}.
   *
   * A close {@link Operation} is never skipped. Even when the
   * {@link Connection} is dependent, the default, and an {@link Operation}
   * completes exceptionally, a close {@link Operation} is still executed. If
   * the {@link Connection} is parallel, a close {@link Operation} is not
   * executed so long as there are other {@link Operation}s or the
   * {@link Connection} is held; for more {@link Operation}s.
   *
   * Note: It is highly recommended to use try with resources or the
   * {@link close()} convenience method. Unless there is a specific need, do not
   * call this method directly.
   *
   * @return an {@link Operation} that will close this {@link Connection}.
   * @throws IllegalStateException if the Connection is not active
   */
  public Operation<Void> closeOperation();

  /**
   * Create and submit an {@link Operation} to close this {@link Connection}.
   * Convenience method.
   *
   * Note: A {@link Connection} is an {@link OperationGroup} and so has some
   * advanced features; that most users do not need. Management of these
   * features is encapsulated in this method and the corresponding
   * {@link connect()} convenience method. The vast majority of users should
   * just use these methods and not worry about the advanced features. The
   * convenience methods do the right thing for the overwhelming majority of use
   * cases. A tiny number of user might want to take advantage of the advanced
   * features that {@link OperationGroup} brings to {@link Connection} and so
   * would call {@link closeOperation} directly.
   *
   * @throws IllegalStateException if the Connection is not active
   */
  @Override
  public default void close() {
    this.closeOperation()
            .submit();
    this.releaseProhibitingMoreMembers();
  }

  /**
   * Create a new {@link OperationGroup} for this {@link Connection}.
   *
   * @param <S> the result type of the member {@link Operation}s of the returned
   * {@link OperationGroup}
   * @param <T> the result type of the collected results of the member
   * {@link Operation}s
   * @return a new {@link OperationGroup}.
   * @throws IllegalStateException if this Connection is not active
   */
  public <S, T> OperationGroup<S, T> operationGroup();

  /**
   * Returns a new {@link Transaction} that can be used as an argument to a
   * commit Operation.
   *
   * It is most likely an error to call this within an error handler, or any
   * handler as it is very likely that when the handler is executed the next
   * submitted endTransaction {@link Operation} will have been created with a different
   * Transaction.
   * 
   * ISSUE: Should this be moved to OperationGroup?
   *
   * @return a new {@link Transaction}. Not retained.
   * @throws IllegalStateException if this Connection is not active
   */
  public Transaction transaction();
  
  /**
   * Convenience method that submits an endTransaction {@link Operation}. This
   * {@link Operation} will always attempt to commit the transaction. 
   * When this method is used there is no way to cause the transaction to be ended
   * with a rollback.
   * 
   * @return this {@link Connection}
   */
  public default CompletionStage<TransactionOutcome> commit() {
    return this.commitMaybeRollback(transaction());
  }
  
  /**
   * Create an endTransaction {@link Operation}, set it to rollback only,
   * and submit it. Convenience method.
   *
   * @return this {@link OperationGroup}
   */
  public default CompletionStage<TransactionOutcome> rollback() {
    Transaction t = transaction();
    t.setRollbackOnly();
    return this.endTransactionOperation(t).submit().getCompletionStage();
  }

  /**
   * Register a listener that will be called whenever there is a change in the
   * lifecycle of this {@link Connection}.If the listener is already registered
 this is a no-op. ISSUE: Should lifecycleListener be a ConnectionProperty so that it is 
 always reestablished on Connection.activate?
   *
   * @param listener Not {@code null}.
   * @return this Connection
   * @throws IllegalStateException if this Connection is not active
   */
  public Connection registerLifecycleListener(ConnectionLifecycleListener listener);
  
  /**
   * Removes a listener that was registered by calling
   * registerLifecycleListener.Sometime after this method is called the listener
   * will stop receiving lifecycle events. If the listener is not registered,
   * this is a no-op.
   *
   * @param listener Not {@code null}.
   * @return this Connection
   * @throws IllegalStateException if this Connection is not active
   */
  public Connection deregisterLifecycleListener(ConnectionLifecycleListener listener);

  /**
   * Return the current lifecycle of this {@link Connection}. 
   *
   * @return the current lifecycle of this {@link Connection}.
   */
  public Lifecycle getConnectionLifecycle();

  /**
   * Terminate this {@link Connection}. If lifecycle is
   * {@link Lifecycle#NEW}, {@link Lifecycle#OPEN}, {@link Lifecycle#INACTIVE}
   * or {@link Lifecycle#CLOSING} -&gt; {@link Lifecycle#ABORTING} If lifecycle
   * is {@link Lifecycle#ABORTING} or {@link Lifecycle#CLOSED} this is a no-op.
   * If an {@link Operation} is currently executing, terminate it immediately.
   * Remove all remaining {@link Operation}s from the queue. {@link Operation}s
   * are not skipped. They are just removed from the queue.
   *
   * @return this {@link Connection}
   */
  public Connection abort();

  /**
   * Return the set of properties configured on this {@link Connection}
   * excepting any sensitive properties. Neither the key nor the value for
   * sensitive properties are included in the result. Properties (other than
   * sensitive properties) that have default values are included even when not
   * explicitly set. Properties that have no default value and are not set
   * explicitly are not included.
   *
   * @return a {@link Map} of property, value. Not modifiable. May be retained.
   * Not {@code null}.
   * @throws IllegalStateException if this Connection is not active
   */
  public Map<ConnectionProperty, Object> getProperties();
  
  /**
   *
   * @return a {@link ShardingKey.Builder} for this {@link Connection}
   * @throws IllegalStateException if this Connection is not active
   */
  public ShardingKey.Builder shardingKeyBuilder();

  /**
   * Make this {@link Connection} ready for use. A newly created
   * {@link Connection} is active. Calling this method on a {@link Connection}
   * that is active is a no-op. If the lifecycle is {@link Lifecycle#INACTIVE}
   * -&gt; {@link Lifecycle#OPEN}. If the lifecycle is
   * {@link Lifecycle#NEW_INACTIVE} -&gt; {@link Lifecycle#NEW}.
   *
   * @return this {@link Connection}
   * @throws IllegalStateException if this {@link Connection} is closed.
   */
  public Connection activate();

  /**
   * Makes this {@link Connection} inactive. After a call to this method
   * previously submitted Operations will be executed normally. If the lifecycle
   * is {@link Lifecycle#NEW} -&gt; {@link Lifecycle#NEW_INACTIVE}. if the
   * lifecycle is {@link Lifecycle#OPEN} -&gt; {@link Lifecycle#INACTIVE}. If
   * the lifecycle is {@link Lifecycle#INACTIVE} or
   * {@link Lifecycle#NEW_INACTIVE} this method is a no-op. After calling this
   * method or calling any method other than {@link deactivate}, {@link activate},
   * {@link abort}, or {@link getConnectionLifecycle} will throw
   * {@link IllegalStateException}. Local {@link Connection} state not created
   * by {@link Connection.Builder} may not be preserved.
     * 
   * Any implementation of a {@link Connection} pool is by default required to
   * call {@code deactivate} when putting a {@link Connection} into a pool. The
   * implementation is required to call {@code activate} when removing a
   * {@link Connection} from a pool so the {@link Connection} can be used. An
   * implementation of a {@link Connection} pool may have an optional mode where
   * it does not call {@code deactivate}/{@code activate} as required above. The
   * behavior of the pool and {@link Connection}s cached in the pool in such a
   * mode is entirely implementation dependent.
   *
   * @return this {@link Connection}
   * @throws IllegalStateException if this {@link Connection} is closed
   */
  public Connection deactivate();
  
}
