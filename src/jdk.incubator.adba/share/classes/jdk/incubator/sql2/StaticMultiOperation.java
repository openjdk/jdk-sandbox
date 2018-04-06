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
 * A multi-operation is an {@link Operation} that returns one or more results in
 * addition to the result defined by the {@link Operation}. A
 * {@link StaticMultiOperation} is a multi-operation where the number and types
 * of the results are known in advance. Operations are executed in the order
 * submitted. If an {@link Operation} is created but not submitted prior to the
 * {@link StaticMultiOperation} being submitted, submitting the
 * {@link StaticMultiOperation} throws {@link IllegalStateException}.
 *
 * @param <T> The type of the result of this {@link Operation}
 * @see DynamicMultiOperation
 */
public interface StaticMultiOperation<T> extends OutOperation<T> {

  /**
   * Returns a {@link RowOperation} to process a row sequence result. The
   * {@link Operation}s are executed in the order they are submitted. If a
   * result is of the wrong type for the next submitted {@link Operation} the
   * {@link StaticMultiOperation} is completed with
   * {@link IllegalStateException}.
   *
   * @return a {@link RowOperation} that is part of this {@link StaticMultiOperation}
   */
  public RowOperation<T> rowOperation();

  /**
   * Returns a {@link CountOperation} to process a count result. The {@link Operation}s
   * are executed in the order they are submitted. If a result is of the wrong
   * type for the next submitted Operation the {@link StaticMultiOperation} is completed
   * with {@link IllegalStateException}.
   *
   * @return a {@link CountOperation} that is part of this {@link StaticMultiOperation}
   */
  public CountOperation<T> countOperation();

  // Covariant overrides

  @Override
  public StaticMultiOperation<T> onError(Consumer<Throwable> handler);
  
  @Override
  public StaticMultiOperation<T> apply(Function<Result.OutParameterMap, ? extends T> processor);

  @Override
  public StaticMultiOperation<T> outParameter(String id, SqlType type);

  @Override
  public StaticMultiOperation<T> set(String id, Object value, SqlType type);

  @Override
  public StaticMultiOperation<T> set(String id, Object value);

  @Override
  public StaticMultiOperation<T> set(String id, CompletionStage<?> source, SqlType type);

  @Override
  public StaticMultiOperation<T> set(String id, CompletionStage<?> source);

  @Override
  public StaticMultiOperation<T> timeout(Duration minTime);

}
