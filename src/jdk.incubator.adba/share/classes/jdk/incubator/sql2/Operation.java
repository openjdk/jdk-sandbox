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
import java.util.function.Consumer;

/**
 * A description of some work to be done by the database and how to process the
 * database output. An {@link Operation} is created by an
 * {@link OperationGroup}, configured and submitted. If not submitted it is not
 * executed. If submitted it is possibly executed according to the attributes of
 * the {@link OperationGroup} that created it.
 * 
 * <p>
 * If execution of the work results in an error, the Operation is completed
 * exceptionally. The {@link Throwable} that completes the Operation is
 * implementation dependent. It is recommended that an implementation use
 * SqlException in the event of database problems. Other {@link Throwable}s such
 * as {@link java.io.IOException}, {@link NullPointerException}, etc can be used
 * as appropriate. An implementation should not wrap a useful exception in a
 * {@link SqlException} unless that provides valuable additional information. An
 * implementation should use whatever {@link Throwable} best facilitates
 * appropriate error handling.</p>
 * 
 * <p>
 * An Operation is not required to be thread safe. In general a single user
 * thread will configure and submit an Operation. Once an Operation is submitted
 * it is immutable. {@link OperationGroup} is an exception and is thread safe.</p>
 *
 * @param <T> the type of the result of the {@link Operation}
 */
public interface Operation<T> extends PrimitiveOperation<T> {
  
  /**
   * Provides an error handler for this {@link Operation}. If execution of this
   * {@link Operation} results in an error, before the Operation is completed,
   * the handler is called with the {@link Throwable} as the argument. The type
   * of the {@link Throwable} is implementation dependent.
   * 
   * @param handler
   * @return this {@link Operation}
   * @throws IllegalStateException if this method is called more than once on
   * this operation
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
   * @throws IllegalArgumentException if minTime &lt;= {@link java.time.Duration#ZERO}
   * @throws IllegalStateException if this method is called more than once on
   * this operation
   */
  public Operation<T> timeout(Duration minTime);


}
