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
 *
 * @param <T>
 */
public interface SqlRef<T> {
  
  /**
   * Return the name of SQL type of the referent of this SQL REF.
   * 
   * ISSUE: Oracle Database JDBC driver may do a round trip for this. Is this
   * that heavy in other databases?
   * 
   * @return
   */
  public String getReferentTypeName();
  
  /**
   * Create and return an Operation that will fetch the value of the REF from
   * the database. The value of the Operation is the value of the REF.
   *
   * @return an Operation that will fetch the referent of this SqlRef
   */
  public Operation<T> fetchOperation();
  
  /**
   * Submit an Operation that will fetch the value of the REF in the database.
   *
   * @return a Future that will complete when the submitted Operation completes.
   * The value of the Future is the value of the REF.
   */
  public default CompletionStage<T> fetch() {
    return fetchOperation().submit().getCompletionStage();
  }
  
  /**
   * Create and return an Operation that will set the value of the REF in the
   * database.
   *
   * @param value
   * @return an Operation that will store the new referent into the REF
   */
  public Operation<Void> storeOperation(T value);
  
  /**
   * Submit an Operation that will store the new value of the referent into
   * the REF in the database.
   *
   * @param value
   * @return a Future that will complete when the submitted Operation completes.
   */
  public default CompletionStage<Void> store(T value) {
    return storeOperation(value).submit().getCompletionStage();
  }
}
