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
 *
 * Return a malloc'd pointer which the caller should free.
 */
char *readstring_minidump(int fd) {
    int e = 0;
    wchar_t *wbuf = (wchar_t *) calloc(BUFLEN, 1);
    if (wbuf == nullptr) {
        warn("readstring_minidump: Failed to allocate wbuf");
        return nullptr;
    }
    char *mbuf = (char *) calloc(BUFLEN, 1);
    if (mbuf == nullptr) {
        warn("readstring_minidump: Failed to allocate mbuf");
        goto err;
    }

    ULONG32 length;
    e = read(fd, &length, sizeof(ULONG32));
    if (e != sizeof(ULONG32)) {
        warn("Failed to read MINIDUMP_STRING length: %d", e);
        goto err;
    }
    if (length >= BUFLEN) {
        warn("MINIDUMP_STRING length too long: %d", length);
        goto err;
    }
    e = read(fd, wbuf, length);
    if (e != length) {
        warn("Failed to read MINIDUMP_STRING chars: %d", e);
        goto err;
    }

    e = (int) wcstombs(mbuf, wbuf, length);
    if (e < (int) (length/2)) {
        warn("MINIDUMP_STRING length %d short, bad result from wcstombs: %d", length, e);
        goto err;
    }
    free(wbuf);
    return mbuf;

err:
    free(wbuf);
    free(mbuf);
    return nullptr;
}

/**
 * Read a MINDUMP_STRING at a given offset in a file.
 *
 * Return a malloc'd pointer which the caller should free.
 */
char *string_at_offset_minidump(int fd, ULONG32 offset) {
    off_t pos = lseek(fd, 0, SEEK_CUR);
    lseek(fd, offset, SEEK_SET);
    char *s = readstring_minidump(fd);
    lseek(fd, pos, SEEK_SET);
    return s;
}


/**
 * Open a MiniDump, read header.
 */
MiniDump::MiniDump(const char* filename, const char* libdir) {
    this->filename = filename;
    this->libdir = libdir;

    fd = ::open(filename, O_RDONLY | O_BINARY);
    if (fd < 0) {
        warn("MiniDump::open '%s' failed: %d: %s", core_filename, errno, strerror(errno));
        return;
    }
    // Read MiniDump header:
    int e = read(fd, &hdr, sizeof(_MINIDUMP_HEADER));
    if (e != sizeof(_MINIDUMP_HEADER)) {
        warn("MiniDump: header read %d != expected %d", e, sizeof(_MINIDUMP_HEADER));
    }
    if (hdr.Signature != MINIDUMP_SIGNATURE) {
        warn("MiniDump header unexpected: %lx", hdr.Signature);
    }
}

void MiniDump::close() {
    if (fd >= 0) {
        // ::close(fd);
    }
    fd = -1;
}

MiniDump::~MiniDump() {
    close();
}

/**
 * Read minidump to locate wanted stream.
 * Seek the dump file descriptor to the stream data.
 * Return a pointer to a MINIDUMP_DIRECTORY which the caller should free.
 * Return nullptr if not found.
 */
MINIDUMP_DIRECTORY* MiniDump::find_stream(int stream) {
    if (fd < 0) {
        error("MiniDump not open");
    }
    MINIDUMP_DIRECTORY* result = nullptr;

    // Read MiniDump directory:
    lseek(fd, hdr.StreamDirectoryRva, SEEK_SET);
    MINIDUMP_DIRECTORY* md = (MINIDUMP_DIRECTORY*) malloc(sizeof(MINIDUMP_DIRECTORY));

    for (unsigned int i = 0; i < hdr.NumberOfStreams; i++) {
        int e = read(fd, md, sizeof(*md));
        if (md->StreamType == stream) {
            lseek(fd, md->Location.Rva, SEEK_SET);
            result = md;
            break;
        }
    }
    return result;
}


uint64_t MiniDump::resolve_teb() {
    // Find MiniDump ThreadListStream, read _MINIDUMP_THREAD, read TEB
    uint64_t teb = 0;

    MINIDUMP_DIRECTORY *md = this->find_stream(ThreadListStream);
    if (md == nullptr) {
        warn("resolve_teb: MiniDump ThreadListStream not found\n");
        return 0;
    }
    // Read MINIDUMP_THREAD_LIST
    ULONG32 NumberOfThreads;
    int e = read(fd, &NumberOfThreads, sizeof(NumberOfThreads));
    if (e < sizeof(NumberOfThreads)) {
        warn("resolve_teb: read of NumberOfThreads failed");
    } else {
        MINIDUMP_THREAD thread;
        for (unsigned int i = 0; i < NumberOfThreads; i++) {
            memset(&thread, 0, sizeof(thread));
            e = read(fd, &thread, sizeof(thread));
            if (e < sizeof(thread)) {
                warn("resolve_teb: read of MINIDUMP_THREAD %d failed: %d", i, e);
                break;
            }
            logv("resolve_teb: MINIDUMP_THREAD id 0x%lx TEB: 0x%llx", thread.ThreadId, thread.Teb);
            if (thread.Teb != 0) {
                teb = thread.Teb;
                break;
            }
        }
    }
    free(md);
    return teb;
}


/*
 * Read shared library list from ModuleListStream.
 */
void MiniDump::read_sharedlibs() {
    if (fd < 0) {
        error("MiniDump::read_sharedlibs: MiniDump not open");
    }
    if (libs.size() != 0) {
        return;
    }
    MINIDUMP_DIRECTORY *md = find_stream(ModuleListStream);
    if (md == nullptr) {
        error("MiniDump::read_sharedlibs: ModuleListStream not found.");
    }

    int count = 0;
    // Use MINIDUMP_LOCATION_DESCRIPTOR
    ULONG32 size = md->Location.DataSize;
    ULONG32 n;
    int e = read(fd, &n, sizeof(n));
    if (e != sizeof(n)) {
        error("MiniDump::read_shared_libs: failed to read n = %d", e);
    }

    for (unsigned int j = 0; j < n; j++) {
        MINIDUMP_MODULE module;
        e = read(fd, &module, sizeof(module));
        if (e != sizeof(module)) {
            error("MiniDump::read_sharedlibs: read wants %d got %d", sizeof(module), e);
        }
        char *name = string_at_offset_minidump(fd, module.ModuleNameRva);
        if (name == nullptr) {
            warn("MiniDump::read_sharedlibs: module %d: base 0x%llx: null string at ModuleNameRva 0x%llx",
                 j, module.BaseOfImage, module.ModuleNameRva);
            continue;
        }
        logd("MiniDump::read_shared_libs MODULE 0x%llx: '%s'", module.BaseOfImage, name);
        // Populate Segment list of modules/sharedlibs.
        // Adjust name using libdir if needed.
        if (libdir != nullptr) {
            char *alt_name = find_filename_in_libdir(libdir, name);
            if (alt_name != nullptr) {
                logv("Using from libdir: '%s'", alt_name);
                name = alt_name;
            }
        }

        Segment *seg = new Segment(name, (void *) module.BaseOfImage, (size_t) module.SizeOfImage);
        libs.push_back(seg);
        count++;
    }
    logd("MiniDump::read_sharedlibs: NumberOfStreams = %d StreamDirectoryRva = %d", hdr.NumberOfStreams, hdr.StreamDirectoryRva);
    free(md);
}

Segment* MiniDump::get_library_mapping(const char* filename) {
    read_sharedlibs();
    for (std::list<Segment>::iterator iter = libs.begin(); iter != libs.end(); iter++) {
        if ((char*) iter->name == nullptr) {
            continue; // no name
        }
        if (strstr((char*) iter->name, filename)) {
            return new Segment(*iter);
        }
    }
    return nullptr;
}

std::list<Segment> MiniDump::get_library_mappings() {
    return libs;
}

void write_mem_mappings(int mappings_fd, const char* exec_name) {
    // Currently implemented in revival_windows.cpp directly, fetching data from MinDump.
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
    int e = read(fd, &NumberOfMemoryRanges, sizeof(NumberOfMemoryRanges));
    if (e != sizeof(NumberOfMemoryRanges)) {
        error("MiniDump::prepare_memory_ranges: bad read 1 %d", e);
    }
    e = read(fd, &BaseRVA, sizeof(BaseRVA));
    if (e != sizeof(BaseRVA)) {
        error("MiniDump::prepare_memory_ranges: bad read 2 %d", e);
    }
    logv("MiniDump::prepare_memory_ranges: NumberOfMemoryRanges %d, BaseRVA 0x%llx", NumberOfMemoryRanges, BaseRVA);
    rangesRead = 0;
    free(md);
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
        logd("MiniDump::readSegment0: terminating as address 0x%llx >= 0x%llx", d->StartOfMemoryRange, max_user_vaddr_pd());
        return nullptr; // End of user space mappings.
    }

    Segment *seg = new Segment((void *) d->StartOfMemoryRange, (size_t) d->DataSize, (size_t) *currentRVA, (size_t) d->DataSize);
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
    bool retry;
    do {
        retry = false;
        seg = readSegment0(d, currentRVA);
        if (seg == nullptr) {
            return nullptr;
        }
        if (seg->start() >= (uint64_t) 0x7FFE0000 &&  seg->start() <= 0x7FFEF000) {
            logd("readSegment: skip seg: 0x%llx - 0x%llx", seg->start(), seg->end());
            retry = true; // Memory from kernel we can't map.  Ignore, go to next segment.
            continue;
        }

        // Simple check for clashes.  Comparing module list from dump, with memory list from dump,
        // so complex overlaps not possible.
        // Module extents (iter) likely to be larger than individual memory descriptors.
        for (std::list<Segment>::iterator iter = libs.begin(); iter != libs.end(); iter++) {
            if (skipLibraries &&
                (seg->start() >= iter->start() && seg->end() <= iter->end())
              ) {
                // Seg clashes with some module.  Avoid, unless it is the JVM .data Section.
                if (jvm_data_seg != nullptr && (seg->contains(jvm_data_seg) || jvm_data_seg->contains(seg))) {
                    logd("readSegment: Using (JVM .data) seg: 0x%llx - 0x%llx", seg->start(), seg->end());
                } else {
                    logd("readSegment: Skipping seg 0x%llx - 0x%llx due to hit in module list", seg->start(), seg->end());
                    retry = true;
                }
                break; // out of this for loop, possibly retry readSegment0
            }
        }
    } while (retry);

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

