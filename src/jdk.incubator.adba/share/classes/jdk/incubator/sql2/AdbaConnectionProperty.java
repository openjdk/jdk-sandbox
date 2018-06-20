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

import java.util.function.Function;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * A set of {@link ConnectionProperty}  commonly supported. Implementations are not
 * required to support all of these properties.
 * 
 * ISSUE: Need others such as IDLE_TIMEOUT, TIME_TO_LIVE_TIMEOUT, etc. Can we
 * get away with just ConnectionProperties or do we need DataSourceProperties
 * as well, MAX_CACHED_CONNECTIONS, etc?
 *
 */
public enum AdbaConnectionProperty implements ConnectionProperty {
  
  /**
   *
   */
  CACHING(Caching.class, 
          v -> v instanceof Caching,
          Caching.AS_NEW,
          false),
  
  /**
   *
   */
  EXECUTOR(Executor.class,
           v -> v instanceof Executor,
           ForkJoinPool.commonPool(),
           false),

  /**
   * 
   */
  CONNECT_TIMEOUT(Duration.class,
          v -> v instanceof Duration && ! ((Duration)v).isNegative(),
          Duration.ofSeconds(Long.MAX_VALUE),
          false),
  
  /**
   *
   */
  HOLDABILITY(Holdability.class, 
          v -> v instanceof Holdability,
          Holdability.HOLD_OVER_COMMIT,
          false),

  /**
   *
   */
  NETWORK_TIMEOUT(Duration.class, 
          v -> v instanceof Duration && ! ((Duration)v).isNegative(),
          Duration.ofSeconds(Long.MAX_VALUE),
          false),

  /**
   *
   */
  PASSWORD(String.class,
          v -> v instanceof String,
          null,
          true),

  /**
   *
   */
  READ_ONLY(Boolean.class, 
          v -> v instanceof Boolean,
          false,
          false),

  /**
   *
   */
  SHARDING_KEY(ShardingKey.class,
          v -> v instanceof ShardingKey,
          null,
          false),

  /**
   *
   */
  SHARDING_GROUP_KEY(ShardingKey.class,
          v -> v instanceof ShardingKey,
          null,
          false),

  /**
   *
   */
  TRANSACTION_ISOLATION(TransactionIsolation.class, 
          v -> v instanceof TransactionIsolation,
          TransactionIsolation.READ_COMMITTED,
          false),

  /**
   *
   */
  URL(String.class,
          v -> v instanceof String,
          null,
          false),

  /**
   *
   */
  USER(String.class,
          v -> v instanceof String,
          null,
          false);

  private final Class<?> range;
  private final Function<Object, Boolean> validator;
  private final Object defaultValue;
  private final boolean isSensitive;

  private AdbaConnectionProperty(Class<?> range, 
          Function<Object, Boolean> validator,
          Object value,
          boolean isSensitive) {
    this.range = range;
    this.validator = validator;
    this.defaultValue = value;
    this.isSensitive = isSensitive;
  }

  @Override
  public Class<?> range() {
    return range;
  }

  @Override
  public boolean validate(Object value) {
    return validator.apply(value);
  }

  @Override
  public Object defaultValue() {
    return defaultValue;
  }

  @Override
  public boolean isSensitive() {
    return isSensitive;
  }

  /**
   *
   */
  public enum Caching {
    /**
     * The returned {@link Connection} is required to be completely new and configured
     * exactly as specified by the other properties. Use this with caution and
     * only when absolutely necessary. Use {@link AS_NEW} instead if at all possible.
     * This should be used only to work around some limitation of the database
     * or the implementation.
     */
    NEW,
    /**
     * The returned {@link Connection} has no state other than that of a new Connection
     * modified as specified by the other properties. May not be strictly new
     * but has the same behavior as a new {@link Connection}. The {@link Connection} may be {@link NEW}. The default.
     */
    AS_NEW,
    /**
     * The returned {@link Connection} has the state specified by the other properties
     * but may have additional state that differs from that of a new {@link Connection}.
     * The {@link Connection} may be {@link AS_NEW}.
     */
    CACHED;
  }

  /**
   *
   */
  public enum TransactionIsolation {

    /**
     *
     */
    NONE,

    /**
     *
     */
    READ_COMMITTED,

    /**
     *
     */
    READ_UNCOMMITTED,

    /**
     *
     */
    REPEATABLE_READ,

    /**
     *
     */
    SERIALIZABLE;
  }

  /**
   *
   */
  public enum Holdability {

    /**
     *
     */
    HOLD_OVER_COMMIT,

    /**
     *
     */
    CLOSE_AT_COMMIT;
  }

}
