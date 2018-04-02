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
import java.util.stream.Collector;

/**
 * An Operation that accepts parameters and processes a sequence of rows.
 * 
 * @param <T> the type of the result of this {@link Operation}
 */
public interface ParameterizedRowOperation<T> extends ParameterizedOperation<T>, RowOperation<T> {

  // Covariant overrides
  
  @Override
  public ParameterizedRowOperation<T> onError(Consumer<Throwable> handler);
  
  @Override
  public ParameterizedRowOperation<T> fetchSize(long rows) throws IllegalArgumentException;
  
  @Override
  public <A, S extends T> ParameterizedRowOperation<T> collect(Collector<? super Result.Row, A, S> c);

  @Override
  public ParameterizedRowOperation<T> set(String id, Object value, SqlType type);

  @Override
  public ParameterizedRowOperation<T> set(String id, CompletionStage<?> source, SqlType type);

  @Override
  public ParameterizedRowOperation<T> set(String id, CompletionStage<?> source);

  @Override
  public ParameterizedRowOperation<T> set(String id, Object value);

  @Override
  public ParameterizedRowOperation<T> timeout(Duration minTime);

}
