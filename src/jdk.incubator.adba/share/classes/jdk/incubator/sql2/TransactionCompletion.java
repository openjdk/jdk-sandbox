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

/**
 * A mutable object that controls whether a transactionCompletion
 * {@link Operation} sends a database commit or a database rollback to the
 * server. A transactionCompletion {@link Operation} is created with a
 * {@code TransactionCompletion}. By default a transactionCompletion
 * {@link Operation} requests that the database end the transaction with a
 * commit. If {@link TransactionCompletion#setRollbackOnly} is called on the
 * {@code TransactionCompletion} used to create the Operation prior to the
 * Operation being executed, the Operation will request that the database end
 * the transaction with a rollback.
 *
 * Example:
 *
 * <pre>
 * {@code
 * TransactionCompletion t = session.transactionCompletion();
 * session.countOperation(updateSql)
 * .resultProcessor( count -> { if (count > 1) t.setRollbackOnly(); } )
 * .submit();
 * session.commitMaybeRollback(t);
 * }</pre>
 *
 * A {@code TransactionCompletion} can not be used to create more than one
 * endTransaction {@link Operation}.
 *
 * A {@code TransactionCompletion} is thread safe.
 *
 */
public interface TransactionCompletion {

  /**
   * Causes an endTransaction {@link Operation} created with this
   * {@code TransactionCompletion} that is executed subsequent to this call to
   * perform a rollback. If this method is not called prior to {@link Operation}
   * execution the {@link Operation} will perform a commit.
   *
   * @return {@code true} if the call succeeded. {@code false} if the call did
   * not succeed in setting the TransactionCompletion rollback only because the
   * endTransaction Operation had already been executed.
   */
  public boolean setRollbackOnly();

  /**
   * Returns {@code true} iff the {@link setRollbackOnly} method has been called
   * on this TransactionCompletion
   *
   * @return {@code true} if {@link setRollbackOnly} has been called.
   */
  public boolean isRollbackOnly();

}
