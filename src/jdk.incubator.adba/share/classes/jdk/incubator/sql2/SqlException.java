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
 * <P>
 * An exception that provides information on a database access error or other
 * errors.
 *
 * <P>
 * Each <code>SqlException</code> provides several kinds of information:
 * <UL>
 * <LI> a string describing the error. This is used as the Java Exception
 * message, available via the method <code>getMesasge</code>.
 * <LI> a "SQLstate" string, which follows either the XOPEN SQLstate conventions
 * or the SQL:2003 conventions. The values of the SQLState string are described
 * in the appropriate spec. The <code>DatabaseMetaData</code> method
 * <code>getSQLStateType</code> can be used to discover whether the driver
 * returns the XOPEN type or the SQL:2003 type.
 * <LI> an integer error code that is specific to each vendor. Normally this
 * will be the actual error code returned by the underlying database.
 * <LI> the causal relationship, if any for this <code>SqlException</code>.
 * <LI> the SQL string that was executing when the error occurred.
 * <LI> the position in the SQL string where the error was detected.
 * </UL>
 */
public class SqlException extends RuntimeException {
  
  private static final long serialVersionUID = 1L;

  // Fields

  /**
   */
  private String sqlState = null;

  /**
   */
  private int vendorCode = -1;

  /**
   * The SQL string that was sent to the database.
   */
  private String sqlString = null;

  /**
   * The index of the first character in SQL where an error is detected. Zero
   * based.
   */
  private int position = -1;
  
  // Constructors

  private SqlException() {
    super();
  }

  /**
   *
   * @param message a description of the exception
   * @param cause the underlying reason for this SqlException
   * (which is saved for later retrieval by the getCause() method);
   * may be null indicating the cause is non-existent or unknown.
   * @param sqlState an XOPEN or SQL:2003 code identifying the exception
   * @param vendorCode a database vendor-specific exception code
   * @param sql the SQL string that was sent to the database
   * @param position the index of the first character in SQL where an error is detected. Zero
   * based
   */
  public SqlException(String message, Throwable cause, String sqlState, int vendorCode, String sql, int position) {
    super(message, cause);
    this.sqlState = sqlState;
    this.vendorCode = vendorCode;
    this.sqlString = sql;
    this.position = position;
  }
  
  // Methods
  
  /**
   * Retrieves the SqlState for this <code>SqlException</code> object.
   *
   * @return the SQLState value
   */
  public String getSqlState() {
    return (sqlState);
  }

  /**
   * Retrieves the vendor-specific exception code for this
   * <code>SqlException</code> object.
   *
   * @return the vendor's error code
   */
  public int getVendorCode() {
    return (vendorCode);
  }

  /**
   * Get the position.
   *
   * @return the index of the first character in sql where an error is detected.
   * Zero based.
   */
  public int getPosition() {
    return position;
  }

  /**
   * Get the sql.
   *
   * @return the SQL string sent to the database
   */
  public String getSqlString() {
    return sqlString;
  }
}
