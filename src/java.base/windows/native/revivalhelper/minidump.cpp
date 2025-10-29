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

#include "minidump.hpp"



/**
 * Read a MINIDUMP_STRING, which is:
 * ULONG32 Length, WCHAR buffer (UTF16-LE)
 */
char *readstring_minidump(int fd) {
    wchar_t *wbuf = (wchar_t *) calloc(BUFLEN, 1);
    char *mbuf = (char *) calloc(BUFLEN, 1);
    if (wbuf == nullptr || mbuf == nullptr) {
        warn("Failed to allocate buf for readstring");
        return nullptr;
    }
    ULONG32 length;
    int e = read(fd, &length, sizeof(ULONG32));
    if (e != sizeof(ULONG32)) {
        warn("Failed to read MINIDUMP_STRING length: %d", e);
        return nullptr;
    }
    if (length >= BUFLEN) {
        warn("MINIDUMP_STRING length too long: %d", length);
        return nullptr;
    }
    e = read(fd, wbuf, length);
    if (e != length) {
        warn("Failed to read MINIDUMP_STRING chars: %d", e);
        return nullptr;
    }

    e = (int) wcstombs(mbuf, wbuf, length);
    if (e < (int) (length/2)) {
        warn("MINIDUMP_STRING length %d, short bad results from wcstombs: %d", length, e);
        return nullptr;
    }

    // Ideally put mbuf string into an accurately-sized buffer.
    free(wbuf);
    return mbuf;
}

/**
 * Read a MINDUMP_STRING at a given offset in a file.
 */
char *string_at_offset_minidump(int fd, ULONG32 offset) {
    off_t pos = lseek(fd, 0, SEEK_CUR);
    lseek(fd, offset, SEEK_SET);
    char *s = readstring_minidump(fd);
    lseek(fd, pos, SEEK_SET);
    return s;
}

// Locate the .data section of a PE file.
// Return a Segment using relative addresses.
Segment* data_section(const char *filename) {

    PLOADED_IMAGE image = ImageLoad(filename, nullptr);
    if (image == nullptr) {
    	error("data_section: ImageLoad error : %d", GetLastError());
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
    	warn("data_section: ImageUnload error : %d", GetLastError());
    }
    return seg;
}


MiniDump::MiniDump(const char* filename) {
    open(filename);
    if (is_valid()) {
        read_modules();
        logv("MiniDump: NumberOfStreams = %d StreamDirectoryRva = %d", hdr.NumberOfStreams, hdr.StreamDirectoryRva);
    }
}

/**
 * Open a MINIDUMP.
 * Read header, module list.
 */
void MiniDump::open(const char *filename) {
    fd = ::open(filename, O_RDONLY);
    if (fd < 0) {
        warn("MiniDump::open '%s' failed: %d: %s", core_filename, errno, strerror(errno));
        return;
    }
    // Read MiniDump header
    int e = read(fd, &hdr, sizeof(_MINIDUMP_HEADER));
    if (hdr.Signature != MINIDUMP_SIGNATURE) {
        warn("Minidump header unexpected: %lx", hdr.Signature);
    }
}

void MiniDump::close() {
    ::close(fd);
    fd = -1;
}

MiniDump::~MiniDump() {
    close();
    // Other cleanup...
}

/**
 * Read minidump to locate wanted stream.
 * Seek the dump file descriptor to the stream data.
 */
MINIDUMP_DIRECTORY* MiniDump::find_stream(int stream) {
    if (fd < 0) {
        error("MiniDump not open");
    }
    MINIDUMP_DIRECTORY* md = (MINIDUMP_DIRECTORY*) malloc(sizeof(MINIDUMP_DIRECTORY));
    lseek(fd, hdr.StreamDirectoryRva, SEEK_SET);
    for (unsigned int i = 0; i < hdr.NumberOfStreams; i++) {
        int e = read(fd, md, sizeof(*md));
        if (md->StreamType == stream) {
            lseek(fd, md->Location.Rva, SEEK_SET);
            return md;
        }     
    }
    return nullptr;
}

// Read ModuleListStream.
// Populate the modules list, and specifically record jvm.dll details.
int MiniDump::read_modules() {
    if (fd < 0) {
        error("MiniDump not open");
    }
    MINIDUMP_DIRECTORY *md = find_stream(ModuleListStream);
    if (md == nullptr) {
        warn("Minidump ModuleListStream not found.");
        return -1;
    }

    int count = 0;
    // Use MINIDUMP_LOCATION_DESCRIPTOR
    ULONG32 size = md->Location.DataSize;
    ULONG32 n;
    int e = read(fd, &n, sizeof(n));
    for (unsigned int j = 0; j < n; j++) {
        MINIDUMP_MODULE module;
        e = read(fd, &module, sizeof(module));
//        if (e != sizeof(module)) {
//            error("MiniDump::read_modules: read wants %d got %d", sizeof(module), e);
//        }
        char *name = string_at_offset_minidump(fd, module.ModuleNameRva);
        logv("MODULE name = '%s' 0x%llx", name, module.BaseOfImage);
        // Populate Segment list of modules
        Segment *seg = new Segment((void *) module.BaseOfImage, (size_t) module.SizeOfImage, 0, 0);
        modules.push_back(seg);

        // Is it the JVM?
        if (strstr(name, "\\" JVM_FILENAME) != nullptr) {
            logv("FOUND JVM name = '%s' with base address 0x%llx", name, module.BaseOfImage);
            // Record locally in dump object:
            jvm_filename = strdup(name);
            jvm_address = (void *) module.BaseOfImage;
            // Also lookup .data Section for later copying:
            jvm_seg = seg; // without file offset details at this point
            populate_jvm_data_segs(name);

        }
        free(name);
        count++;
    }
    return count;
}

void MiniDump::populate_jvm_data_segs(const char *filename) {

    PLOADED_IMAGE image = ImageLoad(filename, nullptr);
    if (image == nullptr) {
    	error("ImageLoad error : %d", GetLastError());
    }
    // Create a Segment from .data, and use the next Section to set its end address.
    jvm_rdata_seg = nullptr;
    jvm_data_seg = nullptr;
    for (unsigned int i = 0; i < image->NumberOfSections; i++) {
        logv("data_section image: %s vaddr 0x%llx size 0x%llx", image->Sections[i].Name, image->Sections[i].VirtualAddress,
             image->Sections[i].SizeOfRawData);

        if (jvm_rdata_seg == nullptr && strncmp((char*) image->Sections[i].Name, ".rdata", 8) == 0) {
            jvm_rdata_seg = new Segment((void *) (DWORD_PTR) image->Sections[i].VirtualAddress, (size_t) image->Sections[i].SizeOfRawData, 0, 0); 
            continue;
        }

        if (strncmp((char*) image->Sections[i].Name, ".data", 8) == 0) {
            if (jvm_rdata_seg != nullptr) {
                jvm_rdata_seg->set_length(image->Sections[i].VirtualAddress - jvm_rdata_seg->start());
            }
             jvm_data_seg = new Segment((void *) (DWORD_PTR) image->Sections[i].VirtualAddress, (size_t) image->Sections[i].SizeOfRawData, 0, 0); 
            continue;
        }
        if (jvm_data_seg != nullptr) {
            // Already read and set Seg, use this section as the end of that Seg.
            jvm_data_seg->set_length(image->Sections[i].VirtualAddress - jvm_data_seg->start());
            break;
        }
    }

    int e = ImageUnload(image);
    if (e != TRUE) {
        warn("data_section: ImageUnload error : %d", GetLastError());
    }
    jvm_rdata_seg = new Segment((void*) ((uint64_t) jvm_address + (uint64_t) jvm_rdata_seg->start()), jvm_rdata_seg->length, 0, 0);
    jvm_data_seg = new Segment((void*) ((uint64_t) jvm_address + (uint64_t) jvm_data_seg->start()), jvm_data_seg->length, 0, 0);
    logv("JVM SEG:        0x%llx - 0x%llx ", jvm_seg->start(), jvm_seg->end());
    logv("JVM .rdata SEG: 0x%llx - 0x%llx ", jvm_rdata_seg->start(), jvm_rdata_seg->end());
    logv("JVM .data SEG:  0x%llx - 0x%llx ", jvm_data_seg->start(), jvm_data_seg->end());
}


/**
 * Prepare for caller to read memory ranges.
 *
 * Leaves dump fd positioned to read the array of MINIDUMP_MEMORY_DESCRIPTOR64
 * by calling readSegment().
 */
void MiniDump::prepare_memory_ranges() {
    MINIDUMP_DIRECTORY *md = find_stream(Memory64ListStream);
    if (md == nullptr) {
        error("Minidump Memory64ListStream not found.");
    }
    int e = read(get_fd(), &NumberOfMemoryRanges, sizeof(NumberOfMemoryRanges));
    e = read(get_fd(), &BaseRVA, sizeof(BaseRVA));
    logv("MiniDump: NumberOfMemoryRanges %d, BaseRVA 0x%llx", NumberOfMemoryRanges, BaseRVA);
    rangesRead = 0;
}


/**
 * Read the next Minidump Memory Descriptor.
 * Return a Segment* and update the output parameters.  current RVA will be the dump file offset of the next segment.
 * Return nullptr when no further memory descriptors are found.
 */
Segment* MiniDump::readSegment0(MINIDUMP_MEMORY_DESCRIPTOR64 *d, RVA64* currentRVA) {
    if (rangesRead >= NumberOfMemoryRanges) {
        return nullptr;
    }

    int size = sizeof(MINIDUMP_MEMORY_DESCRIPTOR64);
    do {
        long pos1 = _lseek(fd, 0, SEEK_CUR);
        int e = read(fd, d, size);
        if (e < 0) {
            warn("MiniDump::readSegment0: read failed, returns %d: %s", e, strerror(errno));
            return nullptr;
        } else if (e < size) {
            // Seeing read return e.g. 2, but _lseek shows it has advanced 16 bytes.
            long pos2 = _lseek(fd, 0, SEEK_CUR);
            if (pos2 - pos1 == size) {
                // Well that's fine, why did read return an odd value?...
                warn("MiniDump::readSegment0: read expects %d, got %d, at pos1 %ld pos2 %ld.  But looks OK.", size, e, pos1, pos2);
                break;
            }
            // Retry a short read.
            warn("MiniDump::readSegment0: read expects %d, got %d, at pos1 %ld pos2 %ld.  Retry...", size, e, pos1, pos2);
            _lseek(fd, pos1, SEEK_SET);
            waitHitRet();
            continue;
        } else {
            break; // Done
        }
    } while (true);

    if (max_user_vaddr_pd() > 0 && d->StartOfMemoryRange >= max_user_vaddr_pd()) {
        logv("MiniDump::readSegment0: terminating as address 0x%llx >= 0x%llx", d->StartOfMemoryRange, max_user_vaddr_pd());
        return nullptr; // End of user space mappings.
    }

    Segment *seg = new Segment((void *) d->StartOfMemoryRange, (size_t) d->DataSize, (size_t) *currentRVA, (size_t) d->DataSize);
    if (verbose) {
        char *b = seg->toString();
        warn("readSegment0: minidump range %d new seg = %s", rangesRead, b);
        free(b);
    }
    *currentRVA += d->DataSize;
    rangesRead++;
    return seg;
}

/**
 * Read a Segment from the MiniDump.
 *
 * Handle skipping of clashes with libraries/modules (DLLs), they are the "avoid list".
 *
 * Return a Segment* and update the output parameters.  current RVA will be the dump file offset of the next segment.
 * Return nullptr when no further memory descriptors are found.
 */
Segment* MiniDump::readSegment(MINIDUMP_MEMORY_DESCRIPTOR64 *d, RVA64* currentRVA) {
    Segment *seg = nullptr;
    bool clash;
    static bool pause = false;
    do {
        clash = false;
        seg = readSegment0(d, currentRVA);
        if (seg == nullptr) {
            return nullptr;
        }
        // Simple check for clashes.  Comparing module list from dump, with memory list from dump,
        // so complex overlaps not possible.
        // Module extents (iter) likely to be larger than individual memory descriptors.
        for (std::list<Segment>::iterator iter = modules.begin(); iter != modules.end(); iter++) {
            if (seg->start() >= iter->start() && seg->end() <= iter->end()) {
                // But the JVM .data Section located earlier is needed.
                if (jvm_data_seg != nullptr && (seg->contains(jvm_data_seg) || jvm_data_seg->contains(seg))) {
                    logv("readSegment: Using (JVM .data) seg: 0x%llx - 0x%llx ", seg->start(), seg->end());
                } else {
                    logv("readSegment: Skipping seg 0x%llx - 0x%llx due to hit in module list", seg->start(), seg->end());
                    clash = true;
                }
                break;
            }
        }
    } while (clash);

    return seg;
}

