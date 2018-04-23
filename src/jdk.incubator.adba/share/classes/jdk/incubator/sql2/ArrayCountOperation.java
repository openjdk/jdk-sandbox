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
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.stream.Collector;

/**
 * A database operation that returns a count that is executed multiple times
 * with multiple sets of parameter values in one database operation. The
 * parameters are submitted to the database in the same order as in the
 * sequences passed to the set methods. The count results are passed to the
 * collector in the same order they are produced by the database. The
 * value of the Operation is the final result produced by the collector.
 *
 * @param <T> the type of the result of collecting the counts
 */
public interface ArrayCountOperation<T> extends Operation<T> {

  /**
   * Set a sequence of parameter values. The value is captured and should not be
   * modified before the {@link Operation} is completed.
   *
   * The Operation is completed exceptionally with ClassCastException if any of
   * the values cannot be converted to the specified SQL type.
   *
   * @param id the identifier of the parameter marker to be set
   * @param values the sequence of values the parameter is to be set to
   * @param type the SQL type of the values to send to the database
   * @return this Operation
   * @throws IllegalArgumentException if the length of values is not the same as
   * the length of the previously set parameter sequences or if the same id was
   * passed in a previous call.
   * @throws IllegalStateException if the {@link Operation} has been submitted
   */
  public ArrayCountOperation<T> set(String id, List<?> values, SqlType type);

  /**
   * Set a sequence of parameter values. Use a default SQL type determined by
   * the type of the value argument. The value is captured and should not be
   * modified before the {@link Operation} is completed.
   *
   * The Operation is completed exceptionally with ClassCastException if any of
   * the values cannot be converted to the specified SQL type.
   *
   * @param id the identifier of the parameter marker to be set
   * @param values the value the parameter is to be set to
   * @return this {@link Operation}
   * @throws IllegalArgumentException if the length of value is not the same as
   * the length of the previously set parameter sequences or if the same id was
   * passed in a previous call.
   * @throws IllegalStateException if the {@link Operation} has been submitted
   */
  public ArrayCountOperation<T> set(String id, List<?> values);

  /**
   * Set a sequence of parameter values. The first parameter is captured and
   * should not be modified before the {@link Operation} is completed.
   *
   * The Operation is completed exceptionally with ClassCastException if any of
   * the values cannot be converted to the specified SQL type.
   *
   * @param <S> the Java type of the individual parameter values
   * @param id the identifier of the parameter marker to be set
   * @param values the value the parameter is to be set to
   * @param type the SQL type of the value to send to the database
   * @return this Operation
   * @throws IllegalArgumentException if the length of value is not the same as
   * the length of the previously set parameter sequences or if the same id was
   * passed in a previous call.
   * @throws IllegalStateException if the {@link Operation} has been submitted
   */
  public <S> ArrayCountOperation<T> set(String id, S[] values, SqlType type);

  /**
   * Set a sequence of parameter values. Use a default SQL type determined by
   * the type of the value argument. The parameter is captured and should not be
   * modified before the {@link Operation} is completed.
   *
   * The Operation is completed exceptionally with ClassCastException if any of
   * the values cannot be converted to the specified SQL type.
   *
   * @param <S> the Java type of the individual parameter values
   * @param id the identifier of the parameter marker to be set
   * @param values the value the parameter is to be set to
   * @return this Operation
   * @throws IllegalArgumentException if the length of value is not the same as
   * the length of the previously set parameter sequences or if the same id was
   * passed in a previous call.
   * @throws IllegalStateException if the {@link Operation} has been submitted
   */
  public <S> ArrayCountOperation<T> set(String id, S[] values);

  /**
   * Provide a source for a sequence of parameter values.
   *
   * This Operation is not executed until source is completed normally. If
   * source completes exceptionally this Operation completes exceptionally with
   * an IllegealArgumentException with the source's exception as the cause.
   *
   * The Operation is completed exceptionally with ClassCastException if any of
   * the values of the source cannot be converted to the specified SQL type.
   *
   * If the length of the value of source is not the same as the length of all
   * other parameter sequences this Operation is completed exceptionally with
   * IllegalArgumentException.
   *
   * @param id the identifier of the parameter marker to be set
   * @param source supplies the values the parameter is to be set to
   * @param type the SQL type of the value to send to the database
   * @return this Operation
   * @throws IllegalArgumentException if the same id was passed in a previous
   * call.
   * @throws IllegalStateException if the {@link Operation} has been submitted
   */
  public ArrayCountOperation<T> set(String id, CompletionStage<?> source, SqlType type);

  /**
   * Provide a source for a sequence of parameter values. Use a default SQL type
   * determined by the element type of the value of the source.
   *
   * This Operation is not executed until source is completed normally. If
   * source completes exceptionally this Operation completes exceptionally with
   * an IllegealArgumentException with the source's exception as the cause.
   *
   * The Operation is completed exceptionally with ClassCastException if any of
   * the values of the source cannot be converted to the specified SQL type.
   *
   * If the length of the value of source is not the same as the length of all
   * other parameter sequences this Operation is completed exceptionally with
   * IllegalArgumentException.
   *
   * @param id the identifier of the parameter marker to be set
   * @param source supplies the values the parameter is to be set to
   * @return this {@link Operation}
   * @throws IllegalArgumentException if the same id was passed in a previous
   * call.
   * @throws IllegalStateException if the {@link Operation} has been submitted
   */
  public ArrayCountOperation<T> set(String id, CompletionStage<?> source);

  /**
   * Provides a {@link Collector} to reduce the sequence of Counts.The result of
   * the {@link Operation} is the result of calling finisher on the final
   * accumulated result. If the {@link Collector} is
   * {@link Collector.Characteristics#UNORDERED} counts may be accumulated out of
   * order. If the {@link Collector} is
   * {@link Collector.Characteristics#CONCURRENT} then the sequence of counts may be
   * split into subsequences that are reduced separately and then combined.
   *
   * @param <A> the type of the accumulator
   * @param <S> the type of the final result
   * @param c the Collector. Not null. 
   * @return This ArrayCountOperation
   * @throws IllegalStateException if this method had been called previously or
   * this Operation has been submitted.
  */
  public <A, S extends T> ArrayCountOperation<T> collect(Collector<? super Result.Count, A, S> c);

  @Override
  public ArrayCountOperation<T> onError(Consumer<Throwable> handler);

  @Override
  public ArrayCountOperation<T> timeout(Duration minTime);

}
