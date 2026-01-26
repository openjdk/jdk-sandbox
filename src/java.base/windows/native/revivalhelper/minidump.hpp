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

//#include "revival.hpp" // for Segment


class MiniDump {
  public:
    // Open and basic init of a MiniDump.
    MiniDump(const char* filename, const char* libdir);
    ~MiniDump();

    bool is_valid() { return fd >= 0; }
    void close();

    void read_sharedlibs(); // populate module list, and locate jvm

    MINIDUMP_DIRECTORY* find_stream(int stream);
    Segment* readSegment(MINIDUMP_MEMORY_DESCRIPTOR64 *d, RVA64* currentRVA, boolean skipLibraries);

    Segment* get_library_mapping(const char* filename);

    // Write the list of memory mappings in the core, to be used in the revived process.
    void write_mem_mappings(int mappings_fd, const char* exec_name);

    void prepare_memory_ranges();

    int get_fd() { return fd; }
    RVA64 getBaseRVA() { return BaseRVA; }

    uint64_t file_offset_for_vaddr(uint64_t addr);
    char* readstring_at_address(uint64_t addr);

    void set_jvm_data(Segment* data, Segment* rdata, Segment* iat) {
        this->jvm_data_seg = data;
        this->jvm_rdata_seg = rdata;
        this->jvm_iat_seg = iat;
    }

  private:
    const char* filename;
    const char* libdir;
    int fd;
    _MINIDUMP_HEADER hdr;

    std::list<Segment> sharedlibs;

    Segment* readSegment0(MINIDUMP_MEMORY_DESCRIPTOR64 *d, RVA64* currentRVA);
    ULONG64 NumberOfMemoryRanges;
    RVA64 BaseRVA;
    int rangesRead;

    // jvm_data_segs
    Segment* jvm_data_seg;
    Segment* jvm_rdata_seg;
    Segment* jvm_iat_seg;
};

