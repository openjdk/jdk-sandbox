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
package java.sql2;

/**
 * This interface supports injecting a {@link DataSourceFactory}. The SPI
 * mechanism will register {@link DataSourceFactory} implementations with the
 * given name.
 *
 */
public interface DataSourceFactory {

  /**
   * Uses SPI to find a {@link DataSourceFactory} with the requested name or
   * {@code null} if one is not found.
   *
   * @param name the name that identifies the factory
   * @return a {@link DataSourceFactory} for {@code name} or {@code null} if one
   * is not found
   */
  public static DataSourceFactory forName(String name) {
    return DataSourceFactoryManager.instance().forName(name);
  }

  /**
   * Registers the given factory with. A newly-loaded factory class should call
   * the method {@code registerDataSourceFactory} to make itself known. If the
   * factory is currently registered, no action is taken.
   *
   * @param factory the new JDBC DataSourceFactory that is to be registered with
   * the {@code DataSourceFactoryManager}
   * @exception NullPointerException if {@code factory} is null
   */
  public static void registerDataSourceFactory(DataSourceFactory factory) {
    registerDataSourceFactory(factory, null);
  }

  /**
   * Registers the given factory with the {@code DataSourceFactoryManager}. A
   * newly-loaded factory class should call the method
   * {@code registerDataSourceFactory} to make itself known. If the factory is
   * currently registered, no action is taken.
   *
   * @param factory the new DataSourceFactory that is to be registered
   * @param da the {@code DataSourceFactoryAction} implementation to be used
   * when {@code DataSourceFactoryManager#deregisterDataSourceFactory} is called
   * @exception NullPointerException if {@code factory} is null
   */
  public static void registerDataSourceFactory(DataSourceFactory factory,
          DataSourceFactoryAction da) {
    DataSourceFactoryManager.instance().registerDataSourceFactory(factory, da);
  }

  /**
   * Removes the specified factory from the list of registered factories.
   * <p>
   * If a {@code null} value is specified for the factory to be removed, then no
   * action is taken.
   * <p>
   * If a security manager exists and its {@code checkPermission} denies
   * permission, then a {@code SecurityException} will be thrown.
   * <p>
   * If the specified factory is not found in the list of registered factories,
   * then no action is taken. If the factory was found, it will be removed from
   * the list of registered factories.
   * <p>
   * If a {@code DataSourceFactoryAction} instance was specified when the JDBC
   * factory was registered, its deregister method will be called prior to the
   * factory being removed from the list of registered factories.
   *
   * @param factory the DataSourceFactory to remove
   * @throws SecurityException if a security manager exists and its
   * {@code checkPermission} method denies permission to deregister a factory.
   *
   * @see SecurityManager#checkPermission
   */
  public static void deregisterDataSourceFactory(DataSourceFactory factory) {
    DataSourceFactoryManager.instance().deregisterDataSourceFactory(factory);
  }

  /**
   * Returns a new {@link DataSource} builder.
   *
   * @return a {@link DataSource} builder. Not {@code null}.
   */
  public java.sql2.DataSource.Builder builder();

  /**
   * Name by which this factory is registered.
   *
   * @return the name of this factory
   */
  public String getName();

}
