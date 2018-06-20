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

import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * Identifies a constructor or static factory method that can be used to construct
 * an instance of the containing type when the type is passed to {@link Result.ResultMap#get}.
 * The method or constructor must be public.
 * 
 * An instance of this type will be constructed by calling the factory method or
 * constructor. Each element in the value of this annotation is used as a column
 * identifier. The value of that column is passed to the corresponding parameter
 * of the annotated method or constructor. The id argument to {@link Result.ResultMap#get} is 
 * prefixed to the column identifiers.
 * 
 * The following pseudo-code describes how an instance is constructed.
 * 
 * {@code
 * <pre>    int i = 0;
 *   String[] columns = methodOrConstructor.getAnnotation(SqlColumns.class).value();
 *   Object[] args = new Object[columns.length];
 *   for (String columnName : columns)
 *     args[i] = resultMap.get(prefix + columnName, parameterTypes[i++];
 *   instance = methodOrConstructor.invoke(null, args);</pre>}
 * 
 */
@Retention(RUNTIME)
@Target({CONSTRUCTOR, METHOD})
public @interface SqlColumns {
  
  /**
   * The column names corresponding to the parameters of the factory method or
   * constructor to construct an instance of this type. There must be exactly one 
   * column name for each parameter of the annotated method or constructor.
   * 
   * @return the column names in the order returned by the database
   */
  public String[] value();
}
