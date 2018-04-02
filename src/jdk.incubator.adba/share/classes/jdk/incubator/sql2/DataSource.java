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

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Uses the builder pattern to get a {@link Connection}. A {@link getConnection}
 * method is provided as a convenience.
 */
public interface DataSource
        extends AutoCloseable {

  /**
   * Instances of this type are used to build {@link DataSource}s. This type is 
   * immutable once configured. No property can be set more than once. No 
   * property can be set after {@link build} is called. 
   */
  public interface Builder {

    /**
     * A convenience method for setting the {@link AdbaConnectionProperty#URL}.
     *
     * @param url the value to be set for {@link AdbaConnectionProperty#URL}
     * @return this {@link Builder}
     * @see connectionProperty
     */
    public default Builder url(String url) {
      return connectionProperty(AdbaConnectionProperty.URL, url);
    }

    /**
     * A convenience method for setting the {@link AdbaConnectionProperty#USER}.
     *
     * @param name the value to be set for {@link AdbaConnectionProperty#USER}
     * @return this {@link Builder}
     * @see connectionProperty
     */
    public default Builder username(String name) {
      return connectionProperty(AdbaConnectionProperty.USER, name);
    }

    /**
     * A convenience method for setting the {@link AdbaConnectionProperty#PASSWORD}.
     *
     * @param password the value to be set for {@link AdbaConnectionProperty#PASSWORD}
     * @return this {@link Builder}
     * @see connectionProperty
     */
    public default Builder password(String password) {
      return connectionProperty(AdbaConnectionProperty.PASSWORD, password);
    }
    
    /**
     * Specify the value of a {@link Connection} property that will be set by default on
     * all {@link Connection}s produced by this {@link DataSource}. A different value can be set
     * for a particular {@link Connection} via {@link Connection.Builder#property}.
     *
     * @param property the {@link ConnectionProperty} to be set. May not be {@code null}.
     * @param value the value to be set for {@code property}
     * @return this {@link Builder}
     * @throws IllegalArgumentException if {@code property.validate(value)} does not
     * return {@code true}. If it throws an {@link Exception} that {@link Exception} is the cause. Or if
     * this property has been specified previously to this method or
     * {@link connectionProperty} or {@link registerConnectionProperty}.
     * @throws IllegalStateException if {@link build} has previously been called.
     */
    public Builder defaultConnectionProperty(ConnectionProperty property, Object value);

    /**
     * Specify the value of a {@link Connection} property that will be set on
     * all {@link Connection}s produced by the built {@link DataSource}.
     * Attempting to set a different value via
     * {@link Connection.Builder#property} will throw
     * {@link IllegalArgumentException}.
     *
     * @param property the {@link ConnectionProperty} to set. May not be
     * {@code null}.
     * @param value the value to set as the default for {@code property}
     * @return this {@link Builder}
     * @throws IllegalArgumentException if {@code property.validate(value)} does
     * not return {@code true}. If it throws an {@link Exception} that
     * {@link Exception} is the cause. Or if this property has been specified
     * previously to this method or {@link defaultConnectionProperty} or
     * {@link registerConnectionProperty}.
     * @throws IllegalStateException if {@link build} has previously been
     * called.
     */
    public Builder connectionProperty(ConnectionProperty property, Object value);

    /**
     * Make a user defined property known to the implementation. One reason to
     * do this is so the default value of the property will be used. If the
     * {@link DataSource} doesn't know about the property then it cannot know to
     * set the default value. Convenience method.
     *
     * @param property the {@link ConnectionProperty} to make known. May not be
     * {@code null}.
     * @return this Builder
     * @throws IllegalArgumentException if this property has been specified
     * previously to this method or {@link connectionProperty} or
     * {@link defaultConnectionProperty}.
     * @throws IllegalStateException if {@link build} has previously been
     * called.
     */
    public default Builder registerConnectionProperty(ConnectionProperty property) {
      return defaultConnectionProperty(property, property.defaultValue());
    }

    /**
     * Return a DataSource configured as specified. 
     *
     * @return a configured {@link DataSource}. Not {@code null}.
     * @throws IllegalArgumentException if unable to return a {@link DataSource} due to
     * problems with the configuration such is missing or conflicting properties.
     */
    public DataSource build();
  }

  /**
   * Returns a {@link Connection} builder. By default that builder will return
   * {@link Connection}s with the {@code ConnectionProperty}s specified when creating this
   * DataSource. Default and unspecified {@link ConnectionProperty}s can be set with
   * the returned builder.
   *
   * @return a new {@link Connection} builder. Not {@code null}.
   */
  public Connection.Builder builder();

  /**
   * Returns a {@link Connection} that has a submitted connect {@link Operation}. Convenience
   * method for use with try with resources.
   *
   * @return a {@link Connection}
   */
  public default Connection getConnection() {
    return builder().build().connect();
  }

  /**
   * Returns a {@link Connection} that has a submitted connect {@link Operation} with an error
   * handler. Convenience method for use with try with resources. The error
   * handle handles errors in the connect {@link Operation}.
   *
   * @param handler for errors in the connect {@link Operation}
   * @return a {@link Connection}
   */
  public default Connection getConnection(Consumer<Throwable> handler) {
    return builder().build().connect(handler);
  }
  
  /**
   * Translates a SQL string from the format specified by the format argument
   * to a format that can be used to create {@link Operation}s for the {@link Connection}s
   * provided by this {@link DataSource}. 
   * 
   * ISSUE: Just an idea
   * 
   * @param format not {@code null}
   * @param source SQL in the format specified by {@code format}. Not {@code null}.
   * @return SQL in the format supported by this {@link DataSource}. Not {@code null}.
   * @throws IllegalArgumentException if the {@code format} is not supported or
   * if the {@link DataSource} cannot translate the SQL
   */
  public default String translateSql(String format, String source) throws SqlException {
    throw new IllegalArgumentException("Unsupported format: \"" + format + "\"");
  }
  
  /**
   * Return a list of the source formats accepted by the {@link translateSql} method.
   * 
   * ISSUE: Just an idea
   * 
   * @return an array of Strings each of which identifies a supported format
   */
  public default List<String> supportedTranslateSqlFormats() {
    return new LinkedList<>();
  }
  
  @Override
  public void close();

}
