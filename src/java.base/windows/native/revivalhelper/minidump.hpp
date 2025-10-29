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

#include <direct.h>
#include <intrin.h>
#include <io.h>
#include <memoryapi.h>
#include <processthreadsapi.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <sysinfoapi.h>
#include <windows.h>

#include <minidumpapiset.h>

#include <sys/types.h>

#include <fileapi.h>
#include <imagehlp.h>
#include <shlwapi.h>
#include <winternl.h>

#include "revival.hpp"



class MiniDump {
  public:
    MiniDump(const char* filename);
    bool is_valid() { return fd >= 0; }
    void close();
    ~MiniDump();
    MINIDUMP_DIRECTORY* find_stream(int stream);
    Segment* readSegment(MINIDUMP_MEMORY_DESCRIPTOR64 *d, RVA64* currentRVA);

    char* get_jvm_filename() { return jvm_filename; }
    void* get_jvm_address() { return jvm_address; }

    void prepare_memory_ranges();

    Segment* get_jvm_seg() { return jvm_seg; }
    Segment* get_jvm_rdata_seg() { return jvm_rdata_seg; }
    Segment* get_jvm_data_seg() { return jvm_data_seg; }

    int get_fd() { return fd; }
    RVA64 getBaseRVA() { return BaseRVA; }

  private:
    void open(const char* filename);
    int read_modules(); // populate module list, and locate jvm
    Segment* readSegment0(MINIDUMP_MEMORY_DESCRIPTOR64 *d, RVA64* currentRVA);

    int fd;
    _MINIDUMP_HEADER hdr;
    std::list<Segment> modules;

    char* jvm_filename;
    void* jvm_address;
    Segment* jvm_seg;
    Segment* jvm_rdata_seg;
    Segment* jvm_data_seg;
    void populate_jvm_data_segs(const char *filename);

    ULONG64 NumberOfMemoryRanges;
    RVA64 BaseRVA;
    int rangesRead;
};

