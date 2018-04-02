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
import java.util.function.Function;

/**
 * An {@link Operation} that returns a count.
 *
 * @param <T> the type of the result of the {@link Operation}
 * @see ParameterizedCountOperation
 */
public interface CountOperation<T> extends Operation<T> {

  /**
   * Sets the result processor for this {@link Operation}.
   * 
   * @param function processes the count produced by executing this
   * {@link Operation} and returns the result
   * @return this {@link CountOperation}
   * @throws IllegalStateException if this method has been called previously
   */
  public CountOperation<T> apply(Function<Result.Count, ? extends T> function);

  @Override
  public CountOperation<T> onError(Consumer<Throwable> handler);

  @Override
  public CountOperation<T> timeout(Duration minTime);
  
}
