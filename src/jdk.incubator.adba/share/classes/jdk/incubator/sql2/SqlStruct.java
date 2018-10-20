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
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Identifies a type that represents a STRUCT SQL type.
 */
@Target({TYPE})
@Retention(RUNTIME)
public @interface SqlStruct {
  
  /**
   * The SQL name of the SQL STRUCT type.
   * 
   * @return the SQL identifier
   */
  public String sqlTypeName();
  
  /**
   * The fields of the SQL STRUCT type.
   * 
   * @return the fields
   */
  public Field[] fields();
  
  /**
   * Describes a field of a SQL STRUCT type.
   */
  public @interface Field {
    
    /**
     * The name of the field in the SQL STRUCT.
     * 
     * @return the name of the field
     */
    public String sqlFieldName();
    
    /**
     * The name of the SQL type of the field
     * 
     * @return the SQL type name of the field
     */
    public String sqlTypeName();
    
    /**
     * The Java identifier corresponding to the SQL field. This identifier is
     * used to determine the corresponding getter and setter for getting and
     * setting the value of this field in the annotated Java type.
     *
     * Implementations may choose to directly access a field named with the same
     * identifier or a constructor or static factory method where all of the
     * formal parameters are named by Field annotations in the applied
     * SqlStruct.
     *
     * @return a Java identifier
     */
    public String javaFieldName();
  }
}
