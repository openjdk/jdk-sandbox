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

import java.io.Serializable;

/**
 * An attribute of a {@link Session} that can be configured to influence its
 * behavior. implementers of this interface define the properties of
 * {@link Session}s. The {@link Session.Builder#property} method is used to set
 * the values of {@link Session} properties.
 *
 * Implementations must be thread safe.
 *
 */
public interface SessionProperty extends Serializable {

  /**
   * Return the name of this {@code SessionProperty}.
   *
   * @return the name of this {@code SessionProperty}
   */
  public String name();

  /**
   * Return the type of the value of this {@code SessionProperty}. Any value set
   * for this property must be assignable to this type.
   *
   * @return the type of the values of this {@code SessionProperty}
   */
  public Class<?> range();

  /**
   * Determine whether a value is valid for this {@code SessionProperty}.
   * Returns {@code true} if {@code value} is valid and {@code false} otherwise.
   *
   * @param value a value for this {@code SessionProperty}
   * @return {@code true} iff {@code value} is valid for this
   * {@code SessionProperty}
   */
  public default boolean validate(Object value) {
    return (value == null && this.range() == Void.class) || this.range().isInstance(value);
  }

  /**
   * Return the value for this property to use if no other value is set. For
   * this to have any meaning for a user defined property the property must be
   * registered with the {@link DataSource} by calling
   * {@link DataSource.Builder#registerSessionProperty}.
   *
   * @return the default value or {@code null} if there is no default value
   */
  public Object defaultValue();

  /**
   * Returns true if this {@code SessionProperty} contains sensitive information
   * such as a password or encryption key.
   *
   * @return true iff this is sensitive
   */
  public boolean isSensitive();

  /**
   * Creates and submits zero or more {@link Operation}s that will configure the
   * {@link Session} to have the specified property value. Returns {@code true}
   * if any {@link Operation}s were submitted. {@code false} otherwise.
   *
   * Potentially called when an attach {@link Operation} is executed to
   * configure a {@link Session} as specified in the
   * {@link Session.Builder#property} method. SessionProperties known to the
   * implementation may return {@code false} and rely on the implementation to
   * do the right thing.
   *
   * @param group an {@link OperationGroup} which will be the container of the
   * submitted {@link Operation}s, if any
   * @param value the value to which the property is to be set. May be
   * {@code null} if {@link range()} is {@link Void}.
   * @return true if any {@link Operation}s were submitted, false otherwise
   * @throws IllegalStateException if it is not possible to configure the
   * {@link Session} as specified.
   * @throws IllegalArgumentException if {@code this.validate(value)} returns
   * {@code false}
   */
  public default boolean configureOperation(OperationGroup<?, ?> group, Object value) {
    if (validate(value)) {
      return false;
    }
    else {
      throw new IllegalArgumentException(value.toString() + " is invalid");
    }
  }

}
