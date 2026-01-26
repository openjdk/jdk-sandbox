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

#ifndef PEFILE_H
#define PEFILE_H

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


#include <sys/types.h>

#include <fileapi.h>
#include <imagehlp.h>
#include <shlwapi.h>
#include <winternl.h>

#include "segment.hpp"


/**
 * Windows PE file.
 */
class PEFile {
  public:
    PEFile(const char* filename);
    bool is_valid() { return fd >= 0; }
    void close();
    ~PEFile();

    uint64_t file_offset_for_reladdr(uint64_t reladdr);

    // Relocate a file to a new absolute load address.
    // Change the actual named file.
    static bool relocate(const char* filename, uint64_t address);

    // Unset DLLCharacteristic IMAGE_DLLCHARACTERISTICS_DYNAMIC_BASE
    // Change the actual named file.
    static bool remove_dynamicbase(const char* filename);

    // Locate data segments in named file.  Populate output parameters.
    static bool find_data_segs(const char *filename, void* address, Segment** _data, Segment** _rdata, Segment** _iat);

  private:
    const char* filename;
    int fd;
    PLOADED_IMAGE image;
};

#endif
