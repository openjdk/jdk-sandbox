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
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An {@link ParameterizedOperation} for which the result is a set of out parameter 
 * values and/or function results. As the SQL is vendor specific, how parameters
 * are represented in the SQL is itself vendor specific. 
 * 
 * @param <T> the type of the result of this {@link Operation}
 */
public interface OutOperation<T> extends ParameterizedOperation<T> {
  
  /**
   * Register an out parameter identified by the given id.
   * 
   * @param id the parameter identifier
   * @param type the SQL type of the value of the parameter
   * @return this {@link OutOperation}
   * @throws IllegalArgumentException if id is not a parameter marker in the SQL
   * @throws IllegalStateException if this method has been called previously on
   * this {@link Operation} with the same id or this {@link OutOperation} has been submitted
   */
  public OutOperation<T> outParameter(String id, SqlType type);
  
  /**
   * Provide a processor that will handle the result of executing the SQL.
   * 
   * @param processor the {@link Function} that will be called to process the result of
   * this {@link OutOperation}
   * @return this {@link OutOperation}
   * @throws IllegalStateException if this method has been called previously on
   * this {@link Operation} or this {@link Operation} has been submitted.
   */
  public OutOperation<T> apply(Function<Result.OutParameterMap, ? extends T> processor);

  // Covariant overrides
  
  @Override
  public OutOperation<T> onError(Consumer<Throwable> handler);
  
  @Override
  public OutOperation<T> set(String id, Object value);

  @Override
  public OutOperation<T> set(String id, Object value, SqlType type);

  @Override
  public OutOperation<T> set(String id, CompletionStage<?> source);

  @Override
  public OutOperation<T> set(String id, CompletionStage<?> source, SqlType type);

  @Override
  public OutOperation<T> timeout(Duration minTime);

}
