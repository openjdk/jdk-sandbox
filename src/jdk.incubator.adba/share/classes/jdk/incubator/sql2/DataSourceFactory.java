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

import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.function.Function;

/**
 * This interface supports injecting a {@link DataSourceFactory}. The SPI
 * mechanism will find {@link DataSourceFactory} implementations with the
 * given class name.
 * 
 * Implementations must be thread safe.
 *
 */
public interface DataSourceFactory {

  /**
   * Uses SPI to find a {@link DataSourceFactory} with the requested name or
   * {@code null} if one is not found.
   *
   * @param name the name of the class that implements the factory
   * @return a {@link DataSourceFactory} for {@code name} or {@code null} if one
   * is not found
   */
  public static DataSourceFactory forName(String name) {
    if (name == null) throw new IllegalArgumentException("DataSourceFactory name is null");
    return ServiceLoader
            .load(DataSourceFactory.class)
            .stream()
            .filter(p -> p.type().getName().equals(name))
            .findFirst()
            .map(Provider::get)
            .orElse(null);
  }

  /**
   * Returns a new {@link DataSource} builder.
   *
   * @return a {@link DataSource} builder. Not {@code null}.
   */
  public DataSource.Builder builder();

}
