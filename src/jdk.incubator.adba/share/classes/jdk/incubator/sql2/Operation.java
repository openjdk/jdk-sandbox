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
import java.util.function.Consumer;

/**
 * A description of some work to be done by the database and how to process the
 * database output. An {@link Operation} is created by an
 * {@link OperationGroup}, configured and submitted. If not submitted it is not
 * executed. If submitted it is possibly executed according to the attributes of
 * the {@link OperationGroup} that created it.
 *
 * Note: A {@link Connection} is an {@link OperationGroup} and so can create
 * {@link Operation}s.
 *
 * @param <T> the type of the result of the {@link Operation}
 */
public interface Operation<T> {
  
  /**
   * Provides an error handler for this {@link Operation}. If execution of this
   * {@link Operation} results in an error, before the Operation is completed,
   * the handler is called with the {@link Throwable} as the argument.
   * 
   * @param handler the error handler for this operation
   * @return this {@link Operation}
   */
  public Operation<T> onError(Consumer<Throwable> handler);
  
  /**
   * The minimum time before this {@link Operation} might be canceled
   * automatically. The default value is forever. The time is
   * counted from the beginning of Operation execution. The Operation will not
   * be canceled before {@code minTime} after the beginning of execution.
   * Some time at least {@code minTime} after the beginning of execution,
   * an attempt will be made to cancel the {@link Operation} if it has not yet
   * completed. Implementations are encouraged to attempt to cancel within a
   * reasonable time, though what is reasonable is implementation dependent.
   *
   * @param minTime minimum time to wait before attempting to cancel
   * @return this Operation
   * @throws IllegalArgumentException if minTime &lt;= 0 seconds
   * @throws IllegalStateException if this method is called more than once on
   * this operation
   */
  public Operation<T> timeout(Duration minTime);

  /**
   * Add this {@link Operation} to the tail of the {@link Operation} collection
   * of the {@link Connection} that created this {@link Operation}. An
   * {@link Operation} can be submitted only once. Once an {@link Operation} is
   * submitted it is immutable. Any attempt to modify a submitted
   * {@link Operation} will throw {@link IllegalStateException}.
   *
   * @return a {@link Submission} for this {@link Operation}
   * @throws IllegalStateException if this method is called more than once on
   * this operation
   */
  public Submission<T> submit();

}
