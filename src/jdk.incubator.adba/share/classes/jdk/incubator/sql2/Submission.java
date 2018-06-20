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

import java.util.concurrent.CompletionStage;

/**
 * The result of submitting an {@link Operation}. The {@link cancel} method of a
 * {@link CompletionStage} does not cancel the {@link Operation}. This is part
 * of the contract of {@link CompletionStage}. This type provides a method to
 * cancel the {@link Operation}. Canceling an {@link Operation} only makes sense
 * after the {@link Operation} is submitted so this type is the result of
 * submitting an {@link Operation}.
 * 
 * ISSUE: Should Operation.submit return a CompletionStage with the requirement
 * that cancel attempts to cancel the database action? Conceptually this is fine.
 * The concern is that it requires the implementor to implement their own
 * CompletionStage or at the least subclass CompletableFuture to override
 * cancel. Neither of these is trivial.
 *
 * @param <T> The type of the result of the {@link Operation} that created this
 * {@link Submission}
 */
public interface Submission<T> {

  /**
   * Request that the {@link Operation} not be executed or that its execution be
   * aborted if already begun. This is a best effort action and may not succeed
   * in preventing or aborting the execution. This method does not block.
   * 
   * If execution is prevented the Operation is completed exceptionally with
   * SkippedSqlException. If the Operation is aborted it is completed
   * exceptionally with SqlException.
   *
   * @return a {@link java.util.concurrent.CompletionStage} that has the value
   * true if the {@link Operation} is canceled.
   */
  public CompletionStage<Boolean> cancel();

  /**
   * Returns a {@link CompletionStage} which value is the result of the
   * {@link Operation}. Any actions on the returned {@link CompletionStage},
   * eg {@code completeExceptionally} or {@code cancel}, have no impact on this
   * {@link Operation}. If this {@link Operation} is already completed the
   * returned {@link CompletionStage} will be completed. 
   * 
   * The returned {@link CompletionStage} is completed after the Operation
   * is completed. It may be completed by the same thread that completed the
   * Operation or a different one. The Operation following the one that created
   * this Submission begins execution when the Operation that created this 
   * Submission is completed. It is not required to wait for the returned
   * CompletionStage to complete. [Note: this is necessary because the app can
   * call this method after the Operation completes.]
   *
   * Each call of this method for a given {@link Operation} returns the same
   * {@link CompletionStage}.
   *
   * @return the {@link java.util.concurrent.CompletionStage} for the result of this
   * {@link Operation}. Retained.
   */
  public CompletionStage<T> getCompletionStage();

}
