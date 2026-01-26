/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include <segment.hpp>


bool Segment::contains(Segment* seg) {
  return seg->start() >= this->start() && seg->end() <= this->end();
}

bool Segment::contains(uint64_t addr) {
  return addr >= this->start() && addr <= this->end();
}

/**
 * Is this Segment not trivially ignorable, e.g. zero-length.
 */
bool Segment::is_relevant() {
  return length > 0 && file_length > 0;
}


/**
 * Adjust by moving (adding to) the start vaddr, shortening the
 * segment.
 */
void Segment::move_start(long dist) {
    vaddr = (void*) ((long long) vaddr + dist);
    length -= dist;
    file_offset += dist;
    file_length -= dist;
}

/**
 * Write this Segment, formatted as a core.mappings line, to the given fd.
 */
int Segment::write_mapping(int fd) {
    return write_mapping(fd, "M");
}

int Segment::write_mapping(int fd, const char *type) {
    // type vaddr endaddress fileoffset filesize memsize perms
    // e.g.
    // M 2d05a12e000 2d05a12f000 19615fd4 1000 1000 RW-
    char buf[BUFLEN];
    snprintf(buf, BUFLEN, "%s %llx %llx %llx %llx %llx %s\n",
             type,
             (unsigned long long) vaddr,
             (unsigned long long) end(), // vaddr + length,
             (unsigned long long) file_offset,
             (unsigned long long) file_length,
             (unsigned long long) length,
             "RWX" // temp
            );
    write0(fd, buf); // includes warning on error
    return 0;
}

char *Segment::toString() {
    char* buf = (char *) malloc(BUFLEN);
    snprintf(buf, BUFLEN, "Segment: %llx - %llx '%s' off: %llx len:%llx",
             (unsigned long long) vaddr,
             (unsigned long long) end(), // vaddr + length,
             name != nullptr ? name : "",
             (unsigned long long) file_offset,
             (unsigned long long) file_length
            );
    return buf;
}

