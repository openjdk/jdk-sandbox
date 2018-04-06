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

/**
 * All or part of the result of a database operation (lower case).
 *
 * A {@link Result} is valid only for the duration of the call it is passed to. Once
 * that call has returned, the {@link Result} passed to that call is invalid and any
 * calls to it throw {@link IllegalStateException}. {@link Result}s are not required to be
 * thread-safe.
 *
 */
public interface Result {

  /**
   * A {@link Result} that is just a number of rows modified, a {@link Long}.
   *
   * Note: It is certainly true that this is not needed; {@link Long} could be used
   * instead. Seems like there might be a documentational advantage to having
   * this type. If you don't like it, just mentally replace it with {@link Long}
   * everywhere it appears.
   */
  public static interface Count extends Result {

    /**
     *
     * @return The number of rows returned
     */
    public long getCount();
  }

  /**
   * A {@link Result} where the components can be retrieved by name. What 
   * constitutes a name is implementation dependent.
   *
   */
  public static interface ResultMap extends Result {

    /**
     * Return the value indicated by the {@code id}. The {@code id} may be either the id for an
     * OUT parameter marker or for a column. See {@link OutOperation} and
     * {@link RowOperation}.
     *
     * @param <T> the type of the returned value
     * @param id the name of the column or OUT parameter marker
     * @param type the value indicated by {@code id} is converted to this type
     * @return a value of type {@code T}
     * @throws IllegalArgumentException if id is not the identifier of a value
     * in this {@link ResultMap}
     * @throws IllegalStateException if the call that was passed this {@link ResultMap} has
     * ended
     * @throws ClassCastException if the returned value cannot be converted to the 
     * specified type -- ISSUE: Not really a class cast. Maybe a new unchecked exception.
     */
    public <T> T get(String id, Class<T> type);

    /**
     * Returns a {@code {@link String}[]} that contains the identifiers that reference the
     * values of this {@link ResultMap} in the same order these values are returned by the
     * database. A {@code null} value in the array means there is a returned value for
     * which no identifier was defined. There is no way to retrieve such a
     * value.
     *
     * By default the values in the array are the identifier portion of the out
     * parameter markers in the SQL. Alternatively the implementation may assign
     * other identifiers, typically column names or aliases. If there
     * are values that have no associated identifier the corresponding value in
     * the array will be null.
     *
     * @return an array containing the value identifiers. Not {@code null}.
     * @throws IllegalStateException if the call that was passed this {@link ResultMap} has
     * ended
     */
    public String[] getIdentifiers();
  }

  /**
   * Used by {@link OutOperation} to expose the out parameters of a call.
   */
  public static interface OutParameterMap extends ResultMap {
  }

  /**
   * Used by {@link RowOperation} to expose each row of a row sequence.
   */
  public static interface Row extends ResultMap {

    /**
     * The count of {@link Row}s in the {@link Row} sequence preceeding this {@link Row}. For the first
     * row in the Row sequence the {@link rowNumber} is 0.
     *
     * @return the count of {@link Row}s in the {@link Row} sequence preceeding this {@link Row}
     * @throws IllegalStateException if the call that was passed this {@link Result} has
     * ended
     */
    public long rowNumber();

    /**
     * Terminate processing of the rows in this {@link RowOperation}. The result of the
     * call that was passed this {@link Row} will be the result of the {@link Operation}. No
     * further rows in the row sequence will be processed. All subsequent rows,
     * if any, will be ignored. Any rows already fetched will not be processed.
     * Any rows not yet fetched may or may not be fetched. If fetched they will
     * not be processed.
     *
     * @throws IllegalStateException if the call that was passed this {@link Result} has
     * ended
     */
    public void cancel();

  }

}
