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

import java.io.Reader;
import java.io.Writer;
import java.util.concurrent.CompletionStage;

/**
 * A reference to a CHARACTER LARGE OBJECT in the attached database.
 *
 */
public interface SqlClob extends AutoCloseable {

  /**
   * Return an {@link Operation} that will release the temporary resources
   * associated with this {@code SqlClob}.
   *
   * @return an {@link Operation} that will release the temporary resources
   * associated with this {@code SqlClob}.
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
   * Return a {@link Operation} that fetches the position of this {@code SqlClob}.
   * Position 0 is immediately before the first char in the {@code SqlClob}.
   * Position 1 is the first char in the {@code SqlClob}, etc. Position
   * {@link length()} is the last char in the {@code SqlClob}.
   *
   * Position is between 0 and length + 1.
   *
   * @return an {@link Operation} that returns the position of this {@code SqlClob}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlClob} is closed.;
   */
  public Operation<Long> getPositionOperation();

  /**
   * Get the position of this {@code SqlClob}. Position 0 is immediately before the
   * first char in the {@code SqlClob}. Position 1 is the first char in the
   * {@code SqlClob}, etc. Position {@link length()} is the last char in the SqlClob.

 Position is between 0 and length + 1.

 ISSUE: Should position be 1-based as SQL seems to do or 0-based as Java
 does?
   *
   * @return a future which value is the position of this {@code SqlClob}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlClob} is closed.
   */
  public default CompletionStage<Long> getPosition() {
    return getPositionOperation().submit().getCompletionStage();
  }

  /**
   * Return a {@link Operation} that fetches the length of this {@code SqlClob}.
   *
   * @return a {@link Operation} that returns the length of this {@code SqlClob}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlClob} is closed.
   */
  public Operation<Long> lengthOperation();

  /**
   * Get the length of this {@code SqlClob}.
   *
   * @return a {@link java.util.concurrent.Future} which value is the number of
   * chars in this {@code SqlClob}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlClob} is closed.
   */
  public default CompletionStage<Long> length() {
    return lengthOperation().submit().getCompletionStage();
  }

  /**
   * Return an {@link Operation} that sets the position of this {@code SqlClob}. If
   * {@code offset} exceeds the length of this {@code SqlClob} set position to the
   * length + 1 of this {@code SqlClob}, ie one past the last char.
   *
   * @param offset a non-negative number
   * @return a {@link Operation} that sets the position of this {@code SqlClob}
   * @throws IllegalArgumentException if {@code offset} is less than 0
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlClob} is closed.
   */
  public Operation<Long> setPositionOperation(long offset);

  /**
   * Set the position of this {@code SqlClob}. If {@code offset} exceeds the length
   * of this {@code SqlClob} set position to the length + 1 of this {@code SqlClob},
   * ie one past the last char.
   *
   * @param offset the 1-based position to set
   * @return this {@code SqlClob}
   * @throws IllegalArgumentException if {@code offset} is less than 0
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlClob} is closed.
   */
  public default SqlClob setPosition(long offset) {
    setPositionOperation(offset).submit();
    return this;
  }

  /**
   * Return an {@link Operation} to set the position to the beginning of the
   * next occurrence of the target after the position. If there is no such
   * occurrence set the position to 0.
   *
   * @param target a {@code SqlClob} created by the same {@link Session}
   * containing the char sequence to search for
   * @return an {@link Operation} that locates {@code target} in this
   * {@code SqlClob}
   * @throws IllegalArgumentException if {@code target} was created by some
   * other {@link Session}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlClob} is closed.
   */
  public Operation<Long> locateOperation(SqlClob target);

  /**
   * Set the position to the beginning of the next occurrence of the target
   * after the position. If there is no such occurrence set the position to 0.
   *
   * @param target the char sequence to search for
   * @return this {@code SqlClob}
   * @throws IllegalArgumentException if {@code target} was created by some
   * other {@link Session}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlClob} is closed
   */
  public default SqlClob locate(SqlClob target) {
    locateOperation(target).submit();
    return this;
  }

  /**
   * Return an {@link Operation} to set the position to the beginning of the
   * next occurrence of the target after the position. If there is no such
   * occurrence set the position to 0.
   *
   * @param target the char sequence to search for. Not {@code null}. Captured.
   * @return an {@link Operation} that locates {@code target} in this
   * {@code SqlClob}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlClob} is closed.
   */
  public Operation<Long> locateOperation(CharSequence target);

  /**
   * Set the position to the beginning of the next occurrence of the target
   * after the position. If there is no such occurrence set the position to 0.
   *
   * @param target the char sequence to search for
   * @return this {@code SqlClob}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlClob} is closed.
   */
  public default SqlClob locate(CharSequence target) {
    locateOperation(target).submit();
    return this;
  }

  /**
   * Return an {@link Operation} that truncates this {@code SqlClob} so that the
   * current position is the end of the {@code SqlClob}. If the position is N, then
   * after trim() the length is N - 1. The position is still N. This will fail
   * if position is 0.
   *
   * @return an {@link Operation} that trims the length of this {@code SqlClob}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlClob} is closed or position is 0.
   */
  public Operation<Long> trimOperation();

  /**
   * Truncate this {@code SqlClob} so that the current position is the end of the
   * {@code SqlClob}. If the position is N, then after {@link trim()} the length is
   * N - 1. The position is still N. This will fail if position is 0.
   *
   * @return this {@code SqlClob}
   * @throws IllegalStateException if the {@link Session} that created this
   * {@code SqlClob} is closed or position is 0.
   */
  public default SqlClob trim() {
    trimOperation().submit();
    return this;
  }

  /**
   * Returns a {@link Reader} for the characters in this {@code SqlClob}.
   * Characters are read starting at the current position. Each character read
   * advances the position by one.
   *
   * ISSUE: There is no character analog to
   * {@link java.nio.channels.AsynchronousByteChannel}. It is trivial to
   * construct a {@link java.io.Reader} from an
   * {@link java.nio.channels.AsynchronousByteChannel} however.
   *
   * @return a Reader for the characters in this SqlClob
   */
  public Reader getReader();

  /**
   * Returns a Writer for this {@code SqlClob}. Characters are written starting at
   * the current position. Each character written advances the position by one.
   *
   * ISSUE: There is no character analog to
   * {@link java.nio.channels.AsynchronousByteChannel}. It is trivial to
   * construct a {@link java.io.Writer} from an
   * {@link java.nio.channels.AsynchronousByteChannel} however.
   *
   * @return a {@link java.io.Writer} for the characters of this {@code SqlClob}
   */
  public Writer getWriter();

}
