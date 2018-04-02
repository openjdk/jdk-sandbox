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
 * Interface used to indicate that this object represents a Sharding Key. A
 * {@link ShardingKey} instance is only guaranteed to be compatible with the
 * data source instance that it was derived from. A {@link ShardingKey} is
 * created using {@link Builder}.
 * <p>
 * The following example illustrates the use of {@link Builder} to create a
 * {@link ShardingKey}:
 * <pre>
 * {@code
 *
 *     DataSource ds = new MyDataSource();
 *     ShardingKey shardingKey = ds.createShardingKeyBuilder()
 *                           .subkey("abc", JDBCType.VARCHAR)
 *                           .subkey(94002, JDBCType.INTEGER)
 *                           .build();
 * }
 * </pre>
 * <p>
 *
 * A {@link ShardingKey} is used for specifying a
 * {@link AdbaConnectionProperty#SHARDING_KEY} or a
 * {@link AdbaConnectionProperty#SHARDING_GROUP_KEY}. Databases that support
 * composite Sharding may use a * to specify a additional level of partitioning
 * within to specify a additional level of partitioning within the Shard.
 * <p>
 * The following example illustrates the use of {@link Builder} to create a
 * {@link AdbaConnectionProperty#SHARDING_GROUP_KEY} for an eastern region with
 * a {@link AdbaConnectionProperty#SHARDING_KEY} specified for the Pittsburgh
 * branch office:
 * <pre>
 * {@code
 *
 *     DataSource ds = new MyDataSource();
 *     ShardingKey superShardingKey = ds.shardingKeyBuilder()
 *                           .subkey("EASTERN_REGION", JDBCType.VARCHAR)
 *                           .build();
 *     ShardingKey shardingKey = ds.shardingKeyBuilder()
 *                           .subkey("PITTSBURGH_BRANCH", JDBCType.VARCHAR)
 *                           .build();
 *     Connection con = ds.builder()
 *                           .property(SHARDING_GROUP_KEY, superShardingKey)
 *                           .property(SHARDING_KEY, shardingKey)
 *                           .build();
 * }
 * </pre>
 */
public interface ShardingKey {

  /**
   * A builder created from a {@link DataSource} or object, used to create a
   * {@link ShardingKey} with sub-keys of supported data types. Implementations
   * must support JDBCType.VARCHAR and may also support additional data types.
   * <p>
   * The following example illustrates the use of {@link Builder} to create a
   * {@link ShardingKey}:
   * <pre>
   * {@code
   *
   *     DataSource ds = new MyDataSource();
   *     ShardingKey shardingKey = ds.createShardingKeyBuilder()
   *                           .subkey("abc", JDBCType.VARCHAR)
   *                           .subkey(94002, JDBCType.INTEGER)
   *                           .build();
   * }
   * </pre>
   */
  public interface Builder {

    /**
     * This method will be called to add a subkey into a Sharding Key object
     * being built. The order in which subkey method is called is important as
     * it indicates the order of placement of the subkey within the Sharding
     * Key.
     *
     * @param subkey contains the object that needs to be part of shard sub key
     * @param subkeyType sub-key data type of type java.sql.SQLType
     * @return this builder object
     */
    public Builder subkey(Object subkey, SqlType subkeyType);

    /**
     * Returns an instance of the object defined by this builder.
     *
     * @return The built object
     */
    public ShardingKey build();
  }

}
