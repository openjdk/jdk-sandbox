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
 * A mutable object that controls whether a transactionEnd Operation sends
 * a database commit or a database rollback to the server. A transactionEnd
 * Operation is created with a Transaction. By default a transactionEnd
 * Operation requests that the database end the transaction with a commit.
 * If Transaction#setRollbackOnly is called on the Transaction used to create
 * the Operation prior to the Operation being executed, the Operation will
 * request that the database end the transaction with a rollback.
 * 
 * Example:
 *
 * <pre>
 * {@code
 *   Transaction t = conn.transaction();
 *   conn.countOperation(updateSql)
 *       .resultProcessor( count -> { if (count > 1) t.setRollbackOnly(); } )
 *       .submit();
 *   conn.commit(t);
 * }</pre>
 *
 * A Transaction can not be used to create more than one endTransaction 
 * Operation.
 * 
 * A Transaction is thread safe.
 */
public interface Transaction {

  /**
   * Causes an endTransactionOperation created with this Transaction that is executed
   * subsequent to this call to perform a rollback. If this method is not called
   * prior to Operation execution the Operation will perform a commit.
   *
   * @return true if the call succeeded. False if the call did not succeed in
   * setting the Transaction rollback only because the endTransaction
   * Operation had already been executed.
   */
  public boolean setRollbackOnly();

  /**
   * Returns {@code true} iff the {@link setRollbackOnly} method has been called
   * on this Transaction
   *
   * @return {@code true} if {@link setRollbackOnly} has been called.
   */
  public boolean isRollbackOnly();

}
