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
    BIT,
    /**
     * Identifies the generic SQL type {@code TINYINT}.
     */
    TINYINT,
    /**
     * Identifies the generic SQL type {@code SMALLINT}.
     */
    SMALLINT,
    /**
     * Identifies the generic SQL type {@code INTEGER}.
     */
    INTEGER,
    /**
     * Identifies the generic SQL type {@code BIGINT}.
     */
    BIGINT,
    /**
     * Identifies the generic SQL type {@code FLOAT}.
     */
    FLOAT,
    /**
     * Identifies the generic SQL type {@code REAL}.
     */
    REAL,
    /**
     * Identifies the generic SQL type {@code DOUBLE}.
     */
    DOUBLE,
    /**
     * Identifies the generic SQL type {@code NUMERIC}.
     */
    NUMERIC,
    /**
     * Identifies the generic SQL type {@code DECIMAL}.
     */
    DECIMAL,
    /**
     * Identifies the generic SQL type {@code CHAR}.
     */
    CHAR,
    /**
     * Identifies the generic SQL type {@code VARCHAR}.
     */
    VARCHAR,
    /**
     * Identifies the generic SQL type {@code LONGVARCHAR}.
     */
    LONGVARCHAR,
    /**
     * Identifies the generic SQL type {@code DATE}.
     */
    DATE,
    /**
     * Identifies the generic SQL type {@code TIME}.
     */
    TIME,
    /**
     * Identifies the generic SQL type {@code TIMESTAMP}.
     */
    TIMESTAMP,
    /**
     * Identifies the generic SQL type {@code BINARY}.
     */
    BINARY,
    /**
     * Identifies the generic SQL type {@code VARBINARY}.
     */
    VARBINARY,
    /**
     * Identifies the generic SQL type {@code LONGVARBINARY}.
     */
    LONGVARBINARY,
    /**
     * Identifies the generic SQL value {@code NULL}.
     */
    NULL,
    /**
     * Indicates that the SQL type
     * is database-specific and gets mapped to a Java object that can be
     * accessed via the methods getObject and setObject.
     */
    OTHER,
    /**
     * Indicates that the SQL type
     * is database-specific and gets mapped to a Java object that can be
     * accessed via the methods getObject and setObject.
     */
    JAVA_OBJECT,
    /**
     * Identifies the generic SQL type {@code DISTINCT}.
     */
    DISTINCT,
    /**
     * Identifies the generic SQL type {@code STRUCT}.
     */
    STRUCT,
    /**
     * Identifies the generic SQL type {@code ARRAY}.
     */
    ARRAY,
    /**
     * Identifies the generic SQL type {@code BLOB}.
     */
    BLOB,
    /**
     * Identifies the generic SQL type {@code CLOB}.
     */
    CLOB,
    /**
     * Identifies the generic SQL type {@code REF}.
     */
    REF,
    /**
     * Identifies the generic SQL type {@code DATALINK}.
     */
    DATALINK,
    /**
     * Identifies the generic SQL type {@code BOOLEAN}.
     */
    BOOLEAN,

    /**
     * Identifies the SQL type {@code ROWID}.
     */
    ROWID,
    /**
     * Identifies the generic SQL type {@code NCHAR}.
     */
    NCHAR,
    /**
     * Identifies the generic SQL type {@code NVARCHAR}.
     */
    NVARCHAR,
    /**
     * Identifies the generic SQL type {@code LONGNVARCHAR}.
     */
    LONGNVARCHAR,
    /**
     * Identifies the generic SQL type {@code NCLOB}.
     */
    NCLOB,
    /**
     * Identifies the generic SQL type {@code SQLXML}.
     */
    SQLXML,

    /**
     * Identifies the generic SQL type {@code REF CURSOR}.
     */
    REF_CURSOR,

    /**
     * Identifies the generic SQL type {@code TIME WITH TIME ZONE}.
     */
    TIME_WITH_TIME_ZONE,

    /**
     * Identifies the generic SQL type {@code TIMESTAMP WITH TIME ZONE}.
     */
    TIMESTAMP_WITH_TIME_ZONE;

  
    /**
     *{@inheritDoc }
     * @return The name of this {@code SQLType}.
     */
  @Override
  public String getName() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

    /**
     * Returns the name of the vendor that supports this data type.
     * @return  The name of the vendor for this data type which is
     * {@literal java.sql} for ABDAType.
     */
  @Override
  public String getVendor() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

    /**
     * Returns the vendor specific type number for the data type.
     * @return  An Integer representing the data type. For {@code ABDAType},
     * the value will be the same value as in {@code Types} for the data type.
     */
  @Override
  public Integer getVendorTypeNumber() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }
  
}
