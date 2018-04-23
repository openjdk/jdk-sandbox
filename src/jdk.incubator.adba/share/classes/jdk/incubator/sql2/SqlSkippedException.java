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
 * A {@link SqlException} that is used to complete an {@link Operation} when that {@link Operation} is
 * skipped. If an {@link Operation} is skipped the {@link Operation} is removed from the head of
 * the queue, no work is sent to the database and the {@link java.util.concurrent.CompletionStage} of that
 * {@link Operation} is completed exceptionally with a {@link SqlSkippedException}. The cause of
 * the {@link SqlSkippedException} is the {@link Throwable} that caused the {@link Operation} to be
 * skipped, if any.
 *
 */
public class SqlSkippedException extends SqlException {
  
  private static final long serialVersionUID = 1L;

  /**
   *
   * @param message
   * @param cause
   * @param sqlState
   * @param vendorCode
   * @param sql
   * @param position
   */
  public SqlSkippedException(String message, Throwable cause, String sqlState, int vendorCode, String sql, int position) {
    super(message, cause, sqlState, vendorCode, sql, position);
  }
  
  /**
   *
   * @param cause
   */
  public SqlSkippedException(SqlException cause) {
    super(cause.getMessage(), cause, cause.getSqlState(), cause.getVendorCode(), cause.getSqlString(), cause.getPosition());
  }
  
  /**
   *
   * @param cause
   */
  public SqlSkippedException(Throwable cause) {
    super(cause.getMessage(), cause, null, -1, null, -1);
  }
}
