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
 * A PrimitiveOperation can be submitted, nothing more. Used only by
 * {@link OperationGroup#catchOperation} and as a supertype to {@link Operation}.
 * 
 * References in JavaDoc to the "collection of Operations" and "member
 * Operations" should be understood to include PrimitiveOperations. The
 * distinction between {@link Operation} and {@code PrimitiveOperation} in the
 * API is strictly followed as it enables the compiler to catch a significant
 * class of errors. The two types are not distinguished in the JavaDoc as making 
 * such a distinction would not add clarity.
 * 
 * @see Operation
 * @see OperationGroup#catchOperation
 */
public interface PrimitiveOperation<T> {

  /**
   * Add this {@code PrimitiveOperation} to the tail of the {@link Operation}
   * collection of the {@link Session} that created this
   * {@code PrimitiveOperation}. A {@code PrimitiveOperation} can be submitted
   * only once. Once a {@code PrimitiveOperation} is submitted it is immutable.
   * Any attempt to modify a submitted {@code PrimitiveOperation} will throw
   * {@link IllegalStateException}.
   *
   * @return a {@link Submission} for this {@code PrimitiveOperation}
   * @throws IllegalStateException if this method is called more than once on
   * this {@code PrimitiveOperation}
   */
  Submission<T> submit();

}
