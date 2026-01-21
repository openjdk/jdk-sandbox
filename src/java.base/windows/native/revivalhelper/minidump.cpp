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
#include "pefile.hpp"



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


MiniDump::MiniDump(const char* filename, const char* libdir) {
    this->filename = filename;
    open(filename);
    this->libdir = libdir;

    // read_modules() must be called if needed.
}

/**
 * Open a MINIDUMP.
 * Read header, module list.
 */
void MiniDump::open(const char *filename) {
    fd = ::open(filename, O_RDONLY | O_BINARY);
    if (fd < 0) {
        warn("MiniDump::open '%s' failed: %d: %s", core_filename, errno, strerror(errno));
        return;
    }
    // Read MiniDump header
    int e = read(fd, &hdr, sizeof(_MINIDUMP_HEADER));
    if (hdr.Signature != MINIDUMP_SIGNATURE) {
        warn("MiniDump header unexpected: %lx", hdr.Signature);
    }
}

void MiniDump::close() {
    if (fd >= 0) {
        ::close(fd);
    }
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
        error("MiniDump::read_modules: MiniDump not open");
    }
    if (!is_valid()) {
        error("MiniDump::read_modules: MiniDump not valid");
    }
    MINIDUMP_DIRECTORY *md = find_stream(ModuleListStream);
    if (md == nullptr) {
        error("MiniDump::read_modules: ModuleListStream not found.");
    }

    int count = 0;
    // Use MINIDUMP_LOCATION_DESCRIPTOR
    ULONG32 size = md->Location.DataSize;
    ULONG32 n;
    int e = read(fd, &n, sizeof(n));
    for (unsigned int j = 0; j < n; j++) {
        MINIDUMP_MODULE module;
        e = read(fd, &module, sizeof(module));
        if (e != sizeof(module)) {
            warn("MiniDump::read_modules: read wants %d got %d", sizeof(module), e);
        }
        char *name = string_at_offset_minidump(fd, module.ModuleNameRva);
        if (name == nullptr) {
            warn("MiniDump::read_modules: %d: base 0x%llx: null string at offset 0x%llx", j, module.BaseOfImage, module.ModuleNameRva);
            continue;
        }
        logv("MODULE name = '%s' 0x%llx", name, module.BaseOfImage);
        // Populate Segment list of modules
        Segment *seg = new Segment((void *) module.BaseOfImage, (size_t) module.SizeOfImage, 0, 0);
        modules.push_back(seg);

        // Is it the JVM?
        if (strstr(name, FILE_SEPARATOR JVM_FILENAME) != nullptr) {
            logv("FOUND JVM name = '%s' with base address 0x%llx", name, module.BaseOfImage);

            // Record locally in dump object:
            if (libdir != nullptr) {
                // Use libdir if set:
                jvm_filename = find_filename_in_libdir(libdir, name);
                if (jvm_filename == nullptr) {
                   error("No JVM found in libdir");
                }
                logv("Using from libdir: '%s'", jvm_filename);
            } else {
                // Otherwise use file from dump.  Check JVM exists:
                if (!file_exists_pd(name)) {
                    warn("JVM library required in core not found at: %s", jvm_filename);
                    error("For minidumps from other systems, or if JDK at path in core has changed, use -L to specify JVM location.");
                }
                jvm_filename = strdup(name);
            }
            if (jvm_filename == nullptr) {
               error("No JVM found in libdir");
            }
            jvm_address = (void *) module.BaseOfImage;
            // Also lookup .data Section for later copying:
            jvm_seg = seg; // without file offset details at this point
            logv("JVM SEG:        0x%llx - 0x%llx ", jvm_seg->start(), jvm_seg->end());

            // Find .data
            // (using jvm_filename which may be in libdir);
            if (!PEFile::find_data_segs(jvm_filename, jvm_address, &jvm_data_seg, &jvm_rdata_seg, &jvm_iat_seg)) {
                error("Failed to find jvm data segments.");
            }
            logv("minidump: jvm rdata SEG: 0x%llx - 0x%llx",  jvm_rdata_seg->start(), jvm_rdata_seg->end());
            logv("minidump: jvm .data SEG: 0x%llx - 0x%llx", jvm_data_seg->start(),  jvm_data_seg->end());
        }
        free(name);
        count++;
    }
    logv("MiniDump::read_modules: NumberOfStreams = %d StreamDirectoryRva = %d", hdr.NumberOfStreams, hdr.StreamDirectoryRva);
    return count;
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
        error("MiniDump Memory64ListStream not found.");
    }
    int e = read(get_fd(), &NumberOfMemoryRanges, sizeof(NumberOfMemoryRanges));
    e = read(get_fd(), &BaseRVA, sizeof(BaseRVA));
    logv("MiniDump::prepare_memory_ranges: NumberOfMemoryRanges %d, BaseRVA 0x%llx", NumberOfMemoryRanges, BaseRVA);
    rangesRead = 0;
}


/**
 * Read the next MiniDump Memory Descriptor.
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
            long pos2 = _lseek(fd, 0, SEEK_CUR);
            if (pos2 - pos1 == size) {
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
    /* if (verbose) {
        char *b = seg->toString();
        warn("readSegment0: minidump range %d new seg = %s", rangesRead, b);
        free(b);
    } */
    *currentRVA += d->DataSize;
    rangesRead++;
    return seg;
}

/**
 * Read a Segment from the MiniDump, for the purpose of building a list of regions for process revival.
 *
 * Handle skipping of clashes with libraries/modules (DLLs), they are the "avoid list".
 *
 * Return a Segment* and update the output parameters.  current RVA will be the dump file offset of the next segment.
 * Return nullptr when no further memory descriptors are found.
 */
Segment* MiniDump::readSegment(MINIDUMP_MEMORY_DESCRIPTOR64 *d, RVA64* currentRVA, boolean skipLibraries) {
    Segment *seg = nullptr;
    bool clash;
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
            if (skipLibraries &&
                (seg->start() >= iter->start() && seg->end() <= iter->end())
              ) {
                // Seg clashes with some module.
                //
                // But the JVM .data Section located earlier is needed.
                if (jvm_data_seg != nullptr && (seg->contains(jvm_data_seg) || jvm_data_seg->contains(seg))) {
                    logv("readSegment: Using (JVM .data) seg: 0x%llx - 0x%llx ", seg->start(), seg->end());
                    //waitHitRet();
                } else if (jvm_rdata_seg != nullptr && (seg->contains(jvm_rdata_seg) || jvm_rdata_seg->contains(seg))) {
                    // .rdata start with IAT so don't change that.
                    // Need to copy only, not map, as mapping will get aligned and overwrite.
                    logv("readSegment: Using (JVM .rdata) seg: 0x%llx - 0x%llx ", seg->start(), seg->end());
                    seg->move_start(0xa30);
                   // seg->set_copyonly();
                    log("should also NOT map: 0x%llx", seg->start());
                    waitHitRet();
                } else {
                    logv("readSegment: Skipping seg 0x%llx - 0x%llx due to hit in module list", seg->start(), seg->end());
                    clash = true; // Loop and call readSegment0 again
                }
                break;
            }
        }
    } while (clash);

    return seg;
}


uint64_t MiniDump::file_offset_for_vaddr(uint64_t addr) {
    // Find data segment for address.
    this->prepare_memory_ranges();
    RVA64 currentRVA = this->getBaseRVA();
    MINIDUMP_MEMORY_DESCRIPTOR64 d;

    Segment* seg = this->readSegment(&d, &currentRVA, false);
    while (seg != nullptr) {
        if (seg->contains(addr)) {
            // Find offset into segment:
            uint64_t relAddr = addr - seg->start();
            uint64_t offset = seg->file_offset + relAddr;
            return offset;
        }
        seg = this->readSegment(&d, &currentRVA, false);
    }
    return 0;
}


char* MiniDump::readstring_at_address(uint64_t addr) {
    uint64_t offset = file_offset_for_vaddr(addr);
    if (offset == 0) {
       return nullptr;
    } else {
        return readstring_at_offset_pd(this->filename, offset);
    }
}

