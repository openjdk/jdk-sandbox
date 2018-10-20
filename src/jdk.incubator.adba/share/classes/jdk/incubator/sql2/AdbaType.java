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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;

/**
 * <P>Defines the constants that are used to identify generic
 * SQL types, called JDBC types.
 *
 * @see SqlType
 * @since 1.8
 */
public enum AdbaType implements SqlType {
    
        /**
     * Identifies the generic SQL type {@code BIT}.
     */
    BIT(Boolean.class),
    /**
     * Identifies the generic SQL type {@code TINYINT}.
     */
    TINYINT(Byte.class),
    /**
     * Identifies the generic SQL type {@code SMALLINT}.
     */
    SMALLINT(Short.class),
    /**
     * Identifies the generic SQL type {@code INTEGER}.
     */
    INTEGER(Integer.class),
    /**
     * Identifies the generic SQL type {@code BIGINT}.
     */
    BIGINT(Long.class),
    /**
     * Identifies the generic SQL type {@code FLOAT}.
     */
    FLOAT(Double.class),
    /**
     * Identifies the generic SQL type {@code REAL}.
     */
    REAL(Float.class),
    /**
     * Identifies the generic SQL type {@code DOUBLE}.
     */
    DOUBLE(Double.class),
    /**
     * Identifies the generic SQL type {@code NUMERIC}.
     */
    NUMERIC(BigDecimal.class),
    /**
     * Identifies the generic SQL type {@code DECIMAL}.
     */
    DECIMAL(BigDecimal.class),
    /**
     * Identifies the generic SQL type {@code CHAR}.
     */
    CHAR(String.class),
    /**
     * Identifies the generic SQL type {@code VARCHAR}.
     */
    VARCHAR(String.class),
    /**
     * Identifies the generic SQL type {@code LONG VARCHAR}.
     */
    LONG_VARCHAR(String.class),
    /**
     * Identifies the generic SQL type {@code DATE}.
     */
    DATE(LocalDate.class),
    /**
     * Identifies the generic SQL type {@code TIME}.
     */
    TIME(LocalTime.class),
    /**
     * Identifies the generic SQL type {@code TIMESTAMP}.
     */
    TIMESTAMP(LocalDateTime.class),
    /**
     * Identifies the generic SQL type {@code BINARY}.
     */
    BINARY(byte[].class),
    /**
     * Identifies the generic SQL type {@code VARBINARY}.
     */
    VARBINARY(byte[].class),
    /**
     * Identifies the generic SQL type {@code LONG VARBINARY}.
     */
    LONG_VARBINARY(byte[].class),
    /**
     * Identifies the generic SQL value {@code NULL}.
     */
    NULL(Void.class),
    /**
     * Indicates that the SQL type
     * is database-specific and gets mapped to a Java object that can be
     * accessed via the methods getObject and setObject.
     */
    OTHER(Object.class),
    /**
     * Indicates that the SQL type
     * is database-specific and gets mapped to a Java object that can be
     * accessed via the methods getObject and setObject.
     */
    JAVA_OBJECT(Object.class),
    /**
     * Identifies the generic SQL type {@code DISTINCT}.
     */
    DISTINCT(Object.class),
    /**
     * Identifies the generic SQL type {@code STRUCT}.
     */
    STRUCT(SqlStruct.class),
    /**
     * Identifies the generic SQL type {@code ARRAY}.
     */
    ARRAY(Object[].class), //TODO questionable. really want <?>[]
    /**
     * Identifies the generic SQL type {@code BLOB}.
     */
    BLOB(SqlBlob.class),
    /**
     * Identifies the generic SQL type {@code CLOB}.
     */
    CLOB(SqlClob.class),
    /**
     * Identifies the generic SQL type {@code REF}.
     */
    REF(SqlRef.class),
    /**
     * Identifies the generic SQL type {@code DATALINK}.
     */
    DATALINK(Void.class), //TODO
    /**
     * Identifies the generic SQL type {@code BOOLEAN}.
     */
    BOOLEAN(Boolean.class),
    /**
     * Identifies the SQL type {@code ROWID}.
     */
    ROWID(Void.class), //TODO
    /**
     * Identifies the generic SQL type {@code NCHAR}.
     */
    NCHAR(String.class),
    /**
     * Identifies the generic SQL type {@code NVARCHAR}.
     */
    NVARCHAR(String.class),
    /**
     * Identifies the generic SQL type {@code LONG NVARCHAR}.
     */
    LONG_NVARCHAR(String.class),
    /**
     * Identifies the generic SQL type {@code NCLOB}.
     */
    NCLOB(SqlClob.class),
    /**
     * Identifies the generic SQL type {@code SQLXML}.
     */
    SQLXML(Void.class), //TODO

    /**
     * Identifies the generic SQL type {@code REF CURSOR}.
     */
    REF_CURSOR(Void.class), //TODO

    /**
     * Identifies the generic SQL type {@code TIME WITH TIME ZONE}.
     */
    TIME_WITH_TIME_ZONE(OffsetTime.class),

    /**
     * Identifies the generic SQL type {@code TIMESTAMP WITH TIME ZONE}.
     */
    TIMESTAMP_WITH_TIME_ZONE(OffsetDateTime.class);
    
  private static final String STANDARD_VENDOR = "jdk.incubator.sql2";

  protected final Class<?> javaType;
  
  AdbaType(Class<?> type) {
    javaType = type;
  }
  
  /**
   * {@inheritDoc}
   */
  @Override
  public String getName() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getVendor() {
    return STANDARD_VENDOR;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Class<?> getJavaType() {
    return javaType;
  }
  
}
