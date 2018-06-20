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
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * Identifies a method the result of which will be bound to a parameter in a SQL
 * statement when an instance of the containing type is passed to 
 * {@link ParameterizedOperation#set}.
 * 
 * The following pseudo-code describes how an instance is used to set parameter 
 * values:
 * 
 * {@code
 * <pre>    for (Method getter : annotatedMethods) {
 *       Annotation parameter = getter.getAnnotation(SqlParameter.class);
 *       op.set(prefix + parameter.marker(), method.invoke(instance), parameter.sqlType());
 *   }</pre>}
 *
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface SqlParameter {
  
  /**
   * The marker for SQL parameter that the result of this method will be bound to.
   *
   * @return the name that identifies the parameter in the SQL
   */
  public String marker();

  /**
   * The SQL type of the value bound to the parameter.
   * Must be either the name of an enum in {@link SqlType} or the fully
   * qualified name of a constant {@link SqlType},
   * for example an enum that implements that interface.
   * 
   * @return the name of the SQL type of the value bound to the parameter
   */
  public String sqlType() default "<default>";
}
