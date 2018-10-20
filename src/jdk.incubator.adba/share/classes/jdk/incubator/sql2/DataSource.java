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

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Uses the builder pattern to get a {@link Session}. A {@link DataSource#getSession}
 * method is provided as a convenience.
 * 
 * Implementations must be thread safe.
 */
public interface DataSource
        extends AutoCloseable {

  /**
   * Instances of this type are used to build {@link DataSource}s. This type is 
   * immutable once configured. No property can be set more than once. No 
   * property can be set after {@link build} is called. 
   * 
   * ISSUE: Probably need property(DataSourceProperty prop, Object value).
   */
  public interface Builder {

    /**
     * Specify a property and its value for the built {@link DataSource}.
     *
     * @param p {@link DataSourceProperty} to set. Not {@code null}.
     * @param v value for the property. If v is {@link Cloneable} it is cloned, 
     * otherwise it is retained.
     * @return this {@link Builder}
     * @throws IllegalArgumentException if {@code p.validate(v)} does not return
     * true, if this method has already been called with the property
     * {@code p}, or the implementation does not support the 
     * {@link DataSourceProperty}.
     */
    public Builder property(DataSourceProperty p, Object v);

    /**
     * A convenience method for setting the {@link AdbaSessionProperty#URL}.
     *
     * @param url the value to be set for {@link AdbaSessionProperty#URL}
     * @return this {@link Builder}
     * @see sessionProperty
     */
    public default Builder url(String url) {
      return sessionProperty(AdbaSessionProperty.URL, url);
    }

    /**
     * A convenience method for setting the {@link AdbaSessionProperty#USER}.
     *
     * @param name the value to be set for {@link AdbaSessionProperty#USER}
     * @return this {@link Builder}
     * @see sessionProperty
     */
    public default Builder username(String name) {
      return sessionProperty(AdbaSessionProperty.USER, name);
    }

    /**
     * A convenience method for setting the {@link AdbaSessionProperty#PASSWORD}.
     *
     * @param password the value to be set for {@link AdbaSessionProperty#PASSWORD}
     * @return this {@link Builder}
     * @see sessionProperty
     */
    public default Builder password(String password) {
      return sessionProperty(AdbaSessionProperty.PASSWORD, password);
    }
    
    /**
     * Specify the value of a {@link Session} property that will be set by default on
     * all {@link Session}s produced by this {@link DataSource}. A different value can be set
     * for a particular {@link Session} via {@link Session.Builder#property}.
     *
     * @param property the {@link SessionProperty} to be set. May not be {@code null}.
     * @param value the value to be set for {@code property}. If value is 
     * {@link Cloneable} it is cloned otherwise it is retained
     * @return this {@link Builder}
     * @throws IllegalArgumentException if {@code property.validate(value)} does not
     * return {@code true}. If it throws an {@link Exception} that {@link Exception} is the cause. Or if
     * this property has been specified previously to this method or
     * {@link sessionProperty} or {@link registerSessionProperty}.
     * @throws IllegalStateException if {@link build} has previously been called.
     */
    public Builder defaultSessionProperty(SessionProperty property, Object value);

    /**
     * Specify the value of a {@link Session} property that will be set on
     * all {@link Session}s produced by the built {@link DataSource}.
     * Attempting to set a different value via
     * {@link Session.Builder#property} will throw
     * {@link IllegalArgumentException}.
     *
     * @param property the {@link SessionProperty} to set. May not be
     * {@code null}.
     * @param value the value to set as the default for {@code property}. If 
     * value is {@link Cloneable} it is cloned otherwise it is retained
     * @return this {@link Builder}
     * @throws IllegalArgumentException if {@code property.validate(value)} does
     * not return {@code true}. If it throws an {@link Exception} that
     * {@link Exception} is the cause. Or if this property has been specified
     * previously to this method or {@link defaultSessionProperty} or
     * {@link registerSessionProperty}.
     * @throws IllegalStateException if {@link build} has previously been
     * called.
     */
    public Builder sessionProperty(SessionProperty property, Object value);

    /**
     * Make a user defined property known to the implementation. One reason to
     * do this is so the default value of the property will be used. If the
     * {@link DataSource} doesn't know about the property then it cannot know to
     * set the default value. Convenience method.
     *
     * @param property the {@link SessionProperty} to make known. May not be
     * {@code null}.
     * @return this Builder
     * @throws IllegalArgumentException if this property has been specified
     * previously to this method or {@link sessionProperty} or
     * {@link defaultSessionProperty}.
     * @throws IllegalStateException if {@link build} has previously been
     * called.
     */
    public default Builder registerSessionProperty(SessionProperty property) {
      return defaultSessionProperty(property, property.defaultValue());
    }

    /**
     * Provide a method that the built {@link DataSource} will call to control the
     * rate of {@link Session} creations. The built
     * {@link DataSource} will call {@code request} with a positive argument
     * when the {@link DataSource} is able to accept more calls to
     * {@link DataSource#builder}. The difference between
     * the sum of all arguments passed to {@code request} and the number of
     * calls to {@link DataSource#builder} is the
     * <i>demand</i>. The demand must always be non-negative. If a call is made to
     * {@link DataSource#builder} that would make the demand negative, that call 
     * throws {@link IllegalStateException}. If {@code requestHook} is not called,
     * the demand is defined to be infinite.
     * 
     * <p>
     * Since the user thread is never blocked, a user thread could in theory 
     * create, attach, use, and close {@link Session}s faster than the underlying
     * implementation can process the submitted work. At some point work would
     * start timing out or Java would run out of memory to store the queued
     * {@link Operation}s. This is a poor way address the issue. This method 
     * allows user code to get feedback from the {@link DataSource} as to whether
     * the {@link DataSource} can accept more work.
     * </p>
     *
     * @param request accepts calls to increase the demand. Not null.
     * @return this {@link Builder}
     * @throws IllegalStateException if this method has been called previously
     */
    public Builder requestHook(LongConsumer request);

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
   * Returns a {@link Session} builder. By default that builder will return
   * {@link Session}s with the {@code SessionProperty}s specified when creating this
   * DataSource. Default and unspecified {@link SessionProperty}s can be set with
   * the returned builder.
   *
   * @return a new {@link Session} builder. Not {@code null}.
   * @throws IllegalStateException if this {@link DataSource} is closed
   */
  public Session.Builder builder();

  /**
   * Returns a {@link Session} that has a submitted attach {@link Operation}. Convenience
   * method for use with try with resources.
   *
   * @return a {@link Session}
   * @throws IllegalStateException if this {@link DataSource} is closed
   */
  public default Session getSession() {
    return builder().build().attach();
  }

  /**
   * Returns a {@link Session} that has a submitted attach {@link Operation} with an error
   * handler. Convenience method for use with try with resources. The error
   * handle handles errors in the attach {@link Operation}.
   *
   * @param handler for errors in the attach {@link Operation}
   * @return a {@link Session}
   * @throws IllegalStateException if this {@link DataSource} is closed
   */
  public default Session getSession(Consumer<Throwable> handler) {
    return builder().build().attach(handler);
  }
  
  /**
   * Translates a SQL string from the format specified by the format argument
   * to a format that can be used to create {@link Operation}s for the {@link Session}s
   * provided by this {@link DataSource}. 
   * 
   * ISSUE: Just an idea
   * 
   * @param format not {@code null}
   * @param source SQL in the format specified by {@code format}. Not {@code null}.
   * @return SQL in the format supported by this {@link DataSource}. Not {@code null}.
   * @throws IllegalArgumentException if the {@code format} is not supported or
   * if the {@link DataSource} cannot translate the SQL
   * @throws IllegalStateException if this {@link DataSource} is closed
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
   * @throws IllegalStateException if this {@link DataSource} is closed
   */
  public default List<String> supportedTranslateSqlFormats() {
    return new LinkedList<>();
  }
  
  /**
   * {@inheritDoc}
   */
  @Override
  public void close();

}
