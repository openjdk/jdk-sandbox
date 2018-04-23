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
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * An {@link Operation} that executes a user defined action when executed. Does
 * not perform any database action. The result of a {@link LocalOperation} is
 * the result of calling the {@link Callable}. This type allows user code to
 * execute arbitrary code at particular points in the sequence of
 * {@link Operation}s executed by a {@link Connection} without having to execute
 * a specific database action at the same time.
 *
 * @param <T> the type of the result of this {@link Operation}
 */
public interface LocalOperation<T> extends Operation<T> {

  /**
   * Provides an action for this {@link Operation}. The action is called when this
   * {@link LocalOperation} is executed. The result of this {@link LocalOperation} 
   * is the result of executing the action.
   * 
   * ISSUE: Should this use Supplier rather than Callable?
   *
   * @param action called when this {@link Operation} is executed
   * @return this {@link LocalOperation}
   * @throws IllegalStateException if this method has already been called or
   * this {@link Operation} has been submitted.
   */
  public LocalOperation<T> onExecution(Callable<T> action);

  @Override
  public LocalOperation<T> onError(Consumer<Throwable> handler);

  @Override
  public LocalOperation<T> timeout(Duration minTime);
  
}
