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
 * An attribute of a {@link Connection} that can be configured to influence its
 * behavior. Implementors of this interface define the properties of
 * {@link Connection}s. The {@link Connection.Builder#property} method is used to set the values
 * of {@link Connection} properties.
 * 
 */
public interface ConnectionProperty {

  /**
   * Return the name of this {@link ConnectionProperty}.
   * 
   * @return the name of this {@link ConnectionProperty}
   */
  public String name();

  /**
   * Return the type of the value of this {@link ConnectionProperty}. Any value
   * set for this property must be assignable to this type.
   *
   * @return the type of the values of this {@link ConnectionProperty}
   */
  public Class<?> range();

  /**
   * Determine whether a value is valid for this {@link ConnectionProperty}. Returns
   * {@code true} if {@code value} is valid and {@code false} otherwise.
   * 
   * @param value a value for this {@link ConnectionProperty}
   * @return {@code true} if {@code value} is valid for this {@link ConnectionProperty}
   */
  public default boolean validate(Object value) {
    return (value == null && this.range() == Void.class) || this.range().isInstance(value);
  }

  /**
   * Return the value for this property to use if no other value is set. For
   * this to have any meaning for a user defined property the property must be
   * registered with the {@link DataSource} by calling 
   * {@link DataSource.Builder#registerConnectionProperty}. 
   *
   * @return the default value or {@code null} if there is no default value
   */
  public Object defaultValue();

  /**
   * Returns true if this {@link ConnectionProperty} is contains sensitive information
   * such as a password or encryption key.
   *
   * @return true if this is sensitive
   */
  public boolean isSensitive();

  /**
   * Returns an {@link Operation} that will configure the {@link Connection} to have the
   * specified property value.May return {@code null} if no {@link Operation} needed. The
 returned {@link Operation} is a member of group but is not submitted.
   *
   * Called by {@link Connection.Builder#build()} to configure a {@link Connection} as specified
   * in the {@link Connection.Builder#property} method. ConnectionProperties known to the implementation
   * may return {@code null} and rely on the implementation to do the right thing.
   *
   * @param <S> Operation Type
   * @param group an {@link OperationGroup} which will be the container of the returned
   * {@link Operation}, if any
   * @param value the value to which the property is to be set. May be null if
   * {@link range()} is {@link Void}.
   * @return an {@link Operation} or null
   * @throws IllegalStateException if it is not possible to configure the
   * {@link Connection} as specified.
   * @throws IllegalArgumentException if {@code this.validate(value)} returns {@code false}
   */
  public default <S> Operation<? extends S> configureOperation(OperationGroup<S, ?> group, Object value) {
    if (validate(value)) {
      return null;
    }
    else {
      throw new IllegalArgumentException(value.toString() + " is invalid");
    }
  }

}
