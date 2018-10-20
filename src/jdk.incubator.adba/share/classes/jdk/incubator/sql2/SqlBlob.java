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

import java.nio.channels.AsynchronousByteChannel;
import java.util.concurrent.CompletionStage;

/**
 * A reference to a BINARY LARGE OBJECT in the attached database.
 *
 */
public interface SqlBlob extends AutoCloseable {

  /**
   * Return an {@link Operation} that will release the temporary resources
   * associated with this {@code SqlBlob}.
   *
   * @return an {@link Operation} that will release the temporary resources
   * associated with this {@code SqlBlob}.
   */
  public Operation<Void> closeOperation();

  /**
   * {@inheritDoc}
   */
  @Override
  public default void close() {
    this.closeOperation().submit();
  }

  /**
   * Return a {@link Operation} that fetches the position of this {@code SqlBlob}.
   * The position is 1-based. Position 0 is immediately before the first byte in
   * the {@code SqlBlob}. Position 1 is the first byte in the {@code SqlBlob}, etc.
   * Position {@link length()} is the last byte in the {@code SqlBlob}.
   *
   * Position is between 0 and length + 1.
   *
   * @return a {@link Operation} that returns the position of this {@code SqlBlob}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlBlob} is closed.
   */
  public Operation<Long> getPositionOperation();

  /**
   * Get the position of this {@code SqlBlob}. The position is 1-based. Position 0
   * is immediately before the first byte in the {@code SqlBlob}. Position 1 is the
   * first byte in the {@code SqlBlob}, etc. Position {@link length()} is the last
   * byte in the {@code SqlBlob}.
   *
   * Position is between 0 and length + 1.
   *
   * ISSUE: Should position be 1-based as SQL seems to do or 0-based as Java
   * does?
   *
   * @return a future which value is the 1-based position of this {@code SqlBlob}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlBlob} is closed.
   */
  public default CompletionStage<Long> getPosition() {
    return getPositionOperation().submit().getCompletionStage();
  }

  /**
   * Return a {@link Operation} that fetches the length of this {@code SqlBlob}.
   *
   * @return a {@link Operation} that returns the length of this {@code SqlBlob}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlBlob} is closed.
   */
  public Operation<Long> lengthOperation();

  /**
   * Get the length of this {@code SqlBlob}.
   *
   * @return a future which value is the number of bytes in this {@code SqlBlob}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlBlob} is closed.
   */
  public default CompletionStage<Long> length() {
    return lengthOperation().submit().getCompletionStage();
  }

  /**
   * Return a {@link Operation} that sets the position of this {@code SqlBlob}. If
   * offset exceeds the length of this {@code SqlBlob} set position to the length +
   * 1 of this {@code SqlBlob}, ie one past the last byte.
   *
   * @param offset a non-negative number
   * @return a {@link Operation} that sets the position of this {@code SqlBlob}
   * @throws IllegalArgumentException if {@code offset} is less than 0
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlBlob} is closed.
   */
  public Operation<Long> setPositionOperation(long offset);

  /**
   * Set the position of this {@code SqlBlob}. If offset exceeds the length of this
   * {@code SqlBlob} set position to the length + 1 of this {@code SqlBlob}, ie one
   * past the last byte.
   *
   * @param offset the 1-based position to set
   * @return this {@code SqlBlob}
   * @throws IllegalArgumentException if offset is less than 0
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlBlob} is closed.
   */
  public default SqlBlob setPosition(long offset) {
    setPositionOperation(offset).submit();
    return this;
  }

  /**
   * Return a {@link Operation} to set the position to the beginning of the next
   * occurrence of the target after the position. If there is no such occurrence
   * set the position to 0.
   *
   * @param target a {@code SqlBlob} created by the same {@link Session}
   * containing the byte sequence to search for
   * @return a {@link Operation} that locates {@code target} in this
   * {@code SqlBlob}
   * @throws IllegalArgumentException if {@code target} was created by some
   * other {@link Session}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlBlob} is closed.
   */
  public Operation<Long> locateOperation(SqlBlob target);

  /**
   * Set the position to the beginning of the next occurrence of the target
   * after the position. If there is no such occurrence set the position to 0.
   *
   * @param target the byte sequence to search for
   * @return this {@code SqlBlob}
   * @throws IllegalArgumentException if {@code target} was created by some
   * other {@link Session}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlBlob} is closed
   */
  public default SqlBlob locate(SqlBlob target) {
    locateOperation(target).submit();
    return this;
  }

  /**
   * Return an {@link Operation} to set the position to the beginning of the
   * next occurrence of the target after the position. If there is no such
   * occurrence set the position to 0.
   *
   * @param target the byte sequence to search for. Not {@code null}. Captured.
   * @return a {@link Operation} that locates {@code target} in this
   * {@code SqlBlob}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlBlob} is closed.
   */
  public Operation<Long> locateOperation(byte[] target);

  /**
   * Set the position to the beginning of the next occurrence of the target
   * after the position. If there is no such occurrence set the position to 0.
   *
   * @param target the byte sequence to search for
   * @return this {@code SqlBlob}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlBlob} is closed.
   */
  public default SqlBlob locate(byte[] target) {
    locateOperation(target).submit();
    return this;
  }

  /**
   * Return a {@link Operation} that truncates this {@code SqlBlob} so that the
   * current position is the end of the {@code SqlBlob}. If the position is N, then
   * after {@link trim()} the length is N - 1. The position is still N. This
   * will fail if position is 0.
   *
   * @return a {@link Operation} that trims the length of this {@code SqlBlob}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlBlob} is closed or position is 0.
   */
  public Operation<Long> trimOperation();

  /**
   * Truncate this {@code SqlBlob} so that the current position is the end of the
   * {@code SqlBlob}. If the position is N, then after {@link trim()} the length is
   * N - 1. The position is still N. This will fail if position is 0.
   *
   * @return this SqlBlob
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlBlob} is closed or position is 0.
   */
  public default SqlBlob trim() {
    trimOperation().submit();
    return this;
  }

  /**
   * Return a {@link java.nio.channels.Channel} that can be used to read bytes from the
   * {@code SqlBlob} beginning at the position. Reading bytes from the returned
   * {@link java.nio.channels.Channel} advances the position.
   *
   * Each call to a read method that fetches bytes from the server creates and
   * submits a virtual {@link Operation} to fetch those bytes. This virtual
   * {@link Operation} is executed in sequence with other {@link Operation}s and
   * may be skipped if an error occurs.
   *
   * @return a read-only byte {@link java.nio.channels.Channel} beginning at the position.
   * @throws IllegalStateException if the {@link Session} that created this
 SqlBlob is closed.
   */
  public AsynchronousByteChannel getReadChannel();

  /**
   * Return a {@link java.nio.channels.Channel} that can be used to write bytes
   * to this {@code SqlBlob} beginning at the position. Bytes written overwrite
   * bytes already in the {@code SqlBlob}. Writing bytes to the returned
   * {@link java.nio.channels.Channel} advances the position.
   *
   * Each call to a write method that flushes bytes to the server creates and
   * submits a virtual {@link Operation} to flush those bytes. This virtual
   * {@link Operation} is executed in sequence with other {@link Operation}s and
   * may be skipped if an error occurs.
   *
   * ISSUE: Can the app read bytes from a write
   * {@link java.nio.channels.Channel}? If so then maybe remove
   * {@link getReadChannel} and add a read-only flag to this method, renamed
   * {@code getChannel}.
   *
   * @return a writable byte {@link java.nio.channels.Channel} beginning at the
   * position.
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlBlob} is closed.
   */
  public AsynchronousByteChannel getWriteChannel();
}
