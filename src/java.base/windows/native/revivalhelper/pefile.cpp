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


#include <sys/types.h>

#include <fileapi.h>
#include <imagehlp.h>
#include <shlwapi.h>
#include <winternl.h>

#include "revival.hpp"

#include "pefile.hpp"

PEFile::PEFile(const char* filename) {
    this->filename = filename;
    image = nullptr;
    fd = -1;

/*    image = ImageLoad(filename, nullptr);
    if (image == nullptr) {
        error("PEFile: %s: ImageLoad error: %d", filename, GetLastError());
    } */

/*  fd = ::open(filename, O_RDONLY);
    if (fd < 0) {
        error("MiniDump::open '%s' failed: %d: %s", core_filename, errno, strerror(errno));
        return;
     } */
}


void PEFile::close() {
    if (fd >= 0) {
        ::close(fd);
    }
}

PEFile::~PEFile() {
    if (image != nullptr) {
        int e = ImageUnload(image);
        if (e != TRUE) {
        	warn("PEFile: ImageUnload error: %d", GetLastError());
        }
    }
}


bool PEFile::relocate(const char* filename, uint64_t address) {
    warn("PEFile relocate: %s to 0x%llx", filename, address);

    // Use RebaseImage64: once to find image size, then again to rebase.
    PCSTR SymbolPath = nullptr;
    ULONG OldImageSize;
    ULONG64 OldImageBase;
    ULONG NewImageSize;
    ULONG64 NewImageBase;
    waitHitRet();
    BOOL e = ReBaseImage64(filename, SymbolPath, FALSE /* fReBase */, TRUE /* system file */, FALSE /* rebase downwards */,
                           0 /* max size */, &OldImageSize, &OldImageBase, &NewImageSize, &NewImageBase, 0 /* TimeStamp */);
    logv("ReBaseImage64 1: OldImageSize 0x%llx  OldImageBase 0x%llx  NewImageSize 0x%llx  NewImageBase 0x%llx",
          OldImageSize, OldImageBase, NewImageSize, NewImageBase);
    if (!e) {
        error("ReBaseImage64 1 failed: %d", GetLastError());
    }
    NewImageBase = address + NewImageSize;
    e = ReBaseImage64(filename, SymbolPath, TRUE /* fReBase */, TRUE /* system file */, TRUE /* rebase downwards */,
                           0 /* max size */, &OldImageSize, &OldImageBase, &NewImageSize, &NewImageBase, 0 /* TimeStamp */);

    logv("ReBaseImage64 2: OldImageSize 0x%llx  OldImageBase 0x%llx  NewImageSize 0x%llx  NewImageBase 0x%llx",
         OldImageSize, OldImageBase, NewImageSize, NewImageBase);
    if (!e) {
        error("ReBaseImage64 2 failed: %d", GetLastError());
    }
    if (NewImageBase != address) {
        error("relocate failed, new base 0x%llx != 0x%llx", NewImageBase, address);
    }
    return e;
}


bool PEFile::remove_dynamicbase(const char* filename) {

    // Set DYNAMICBASE:NO
    HANDLE h = CreateFile(filename, GENERIC_READ | GENERIC_WRITE, 0 /* not shared */, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, 0);
    if (h == INVALID_HANDLE_VALUE) { error("CreateFile failure: %d", GetLastError()); }

    HANDLE h2 = CreateFileMapping(h, NULL, PAGE_READWRITE, 0,0, NULL);
    if (h == INVALID_HANDLE_VALUE) { error("CreateFileMapping failure: %d", GetLastError()); }

    LPVOID base = MapViewOfFile(h2, FILE_MAP_READ | FILE_MAP_WRITE, 0,0,0);
    if (base == nullptr) { error("MapViewOfFile failure: %d", GetLastError()); }

    short magic = *(short*) base; // MZ
    if (magic != 0x5a4d) {
        error("%s: DOS magic not recognized: 0x%x", filename, magic);
    }
    warn("base         0x%llx", (uint64_t) base);
    uint64_t peOffsetAddr = (uint64_t) base + 0x3c;
    warn("peOffsetAddr 0x%llx", peOffsetAddr);

    ULONG32 peOffset = *(ULONG32*) peOffsetAddr;
    warn("peOffset 0x%lx", peOffset);

    uint64_t peAddr = (uint64_t) base + peOffset;
    warn("peAddr    0x%llx", peAddr);

    // At peOffset, is IMAGE_NT_HEADERS32 containing:
    //   DWORD                   Signature;
    //   IMAGE_FILE_HEADER       FileHeader;
    //   IMAGE_OPTIONAL_HEADER32 OptionalHeader;
    ULONG32 peMagic = *(ULONG32*) peAddr;
    if (peMagic != 0x4550) { // PE
        error("%s: PE magic not recognized: 0x%x", filename, peMagic);
    }

    PIMAGE_OPTIONAL_HEADER32 optional = (PIMAGE_OPTIONAL_HEADER32) ((uint64_t) peAddr + sizeof(DWORD) + sizeof(IMAGE_FILE_HEADER));
    logv("Optional hdr = 0x%llx", optional);
    logv("DllCharacteristics = 0x%llx", optional->DllCharacteristics);
    logv("Checksum           = 0x%llx", optional->CheckSum);

    WORD dllCharacteristics = optional->DllCharacteristics;
    dllCharacteristics = dllCharacteristics & ~IMAGE_DLLCHARACTERISTICS_DYNAMIC_BASE; // 64
    logv("DllCharacteristics = 0x%llx", dllCharacteristics);

    logv("&o.DllChar =  0x%llx", &(optional->DllCharacteristics));
    *(WORD*)(&(optional->DllCharacteristics)) = dllCharacteristics;

    // Checksum? Update does not appear to be needed.
    // *(WORD*)(&(optional->CheckSum)) = 0;

    if (!UnmapViewOfFile(base)) {
        error("UnmapViewOfFile: %d", GetLastError());
    }
    CloseHandle(h2);
    CloseHandle(h);
    return TRUE;
}


// Locate the .data section of a PE file.
// Return a Segment using relative addresses.
// 
Segment* data_section(const char *filename) {

    PLOADED_IMAGE image = ImageLoad(filename, nullptr);
    if (image == nullptr) {
    	error("data_section: ImageLoad error: %d", GetLastError());
    }
    // Create a Segment from .data, and use the next Section to set its end address.
    Segment *seg = nullptr;
    for (unsigned int i = 0; i < image->NumberOfSections; i++) {
        logv("data_section image: %s vaddr 0x%llx size 0x%llx", image->Sections[i].Name, image->Sections[i].VirtualAddress,
             image->Sections[i].SizeOfRawData);

        if (strncmp((char*) image->Sections[i].Name, ".data", 8) == 0) {
            seg = new Segment((void *) (DWORD_PTR) image->Sections[i].VirtualAddress, (size_t) image->Sections[i].SizeOfRawData, 0, 0); 
            continue;
        }
        if (seg != nullptr) {
            // Already read and set Seg, use this section as the end of that Seg.
            seg->set_length(image->Sections[i].VirtualAddress - seg->start());
            logv("DATA SEG: 0x%llx - 0x%llx ", seg->start(), seg->end());
            break;
        }
    }

    int e = ImageUnload(image);
    if (e != TRUE) {
    	warn("data_section: ImageUnload error: %d", GetLastError());
    }
    return seg;
}

