/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch.iouring;

import java.lang.foreign.MemorySegment;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

/**
 *  Submission Q entry (user view). The setter and getter methods of this
 *  class have the same name as the respective field in the io_uring_sqe
 *  structure. Support for all fields is not provided yet.
 *  The exception is the xxx_flags field. This sets/gets any/all of
 *  the 32 bit values in the unnamed union in io_uring_sqe with 
 *  the large number of 32bit flag variants.
 */

public class Sqe {
    int opcode;
    int flags;
    int xxx_flags;
    OptionalInt buf_index;
    int fd;
    Optional<MemorySegment> addr;
    Optional<MemorySegment> addr2;
    OptionalInt len;
    OptionalLong off;

    public String toString() {
        return "[opcode: " + Util.sqe_opcode(opcode) + " flags: " + flags +
                " addr: " + addr + " addr2: " + addr2 + " xxx_flags: " +
                xxx_flags + " buf_index: " + buf_index + " fd: " + fd + " len: " + len + "]";
    }

    public Sqe() {
        addr = Optional.empty();
        addr2 = Optional.empty();
        len = OptionalInt.empty();
        off = OptionalLong.empty();
        buf_index = OptionalInt.empty();
        fd = -1;
    }
    public OptionalLong off() {
        return off;
    }

    public Sqe off(long off) {
        if (addr2.isPresent())
            throw new IllegalArgumentException("off cannot be set with addr2");
        this.off = OptionalLong.of(off);
        return this;
    }

    public Sqe buf_index(int index) {
        this.buf_index = OptionalInt.of(index);
        return this;
    }

    public Sqe opcode(int opcode) {
        this.opcode = opcode;
        return this;
    }
    Sqe flags(int flags) {
        this.flags = flags;
        return this;
    }
    public Sqe xxx_flags(int xxx_flags) {
        this.xxx_flags = xxx_flags;
        return this;
    }
    public Sqe fd(int fd) {
        this.fd = fd;
        return this;
    }
    public Sqe addr(MemorySegment buffer) {
        this.addr = Optional.of(buffer);
        return this;
    }
    Sqe addr2(MemorySegment buffer) {
        if (off.isPresent())
            throw new IllegalArgumentException("addr2 cannot be set with off");
        this.addr2 = Optional.of(buffer);
        return this;
    }
    public Sqe len(int len) {
        this.len = OptionalInt.of(len);
        return this;
    }

    // Getters

    public int opcode() {
        return opcode;
    }

    public int flags() {
        return flags;
    }

    public int xxx_flags() {
        return xxx_flags;
    }

    public int fd() {
        return fd;
    }

    public Optional<MemorySegment> addr() {
        return addr;
    }

    public Optional<MemorySegment> addr2() {
        return addr2;
    }
    public OptionalInt len() {
        return len;
    }
    public OptionalInt buf_index() { return buf_index;}
}
