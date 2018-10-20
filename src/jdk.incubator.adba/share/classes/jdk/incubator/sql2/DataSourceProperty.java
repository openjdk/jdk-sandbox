/*
 * Copyright (c)  2018, Oracle and/or its affiliates. All rights reserved.
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
 * An attribute of a {@link DataSource} that can be configured to influence its
 * {@link DataSource}s. The {@link DataSource.Builder#property} method is used
 * to set the values of {@link DataSource} properties.
 *
 * Implementations must be thread safe.
 *
 */
public interface DataSourceProperty extends Serializable {

  /**
   * Return the name of this {@link DataSourceProperty}.
   *
   * @return the name of this {@link DataSourceProperty}
   */
  public String name();

  /**
   * Return the type of the value of this {@link DataSourceProperty}. Any value
   * set for this property must be assignable to this type.
   *
   * @return the type of the values of this {@link DataSourceProperty}
   */
  public Class<?> range();

  /**
   * Determine whether a value is valid for this {@link DataSourceProperty}.
   * Returns {@code true} if {@code value} is valid and {@code false} otherwise.
   *
   * @param value a value for this {@link DataSourceProperty}
   * @return {@code true} iff {@code value} is valid for this
   * {@link DataSourceProperty}
   */
  public default boolean validate(Object value) {
    return (value == null && this.range() == Void.class) || this.range().isInstance(value);
  }

  /**
   * Return the value for this property to use if no other value is set. This
   * has no meaning for user defined properties as the implementation is not
   * aware of the the existence of the property. Default values are used for
   * standard and implementation defined properties.
   *
   * @return the default value or {@code null} if there is no default value
   */
  public Object defaultValue();

  /**
   * Returns true if this {@link DataSourceProperty} is contains sensitive
   * information such as a password or encryption key.
   *
   * @return true iff this is sensitive
   */
  public boolean isSensitive();

  /**
   * Configure the {@link DataSource} as appropriate for the given {@code value} 
   * of this {@link DataSourceProperty}. This is primarily for the use of user 
   * defined properties.
   *
   * @param ds the {@link DataSource} to configure
   * @param value the value of this property
   */
  public default void configure(DataSource ds, Object value) {
    // nothing
  }
  
}
