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

#include "revival.hpp"

uint64_t valign; // set from dwAllocationGranularity
uint64_t pagesize;

// With hints from ZGC:
typedef PVOID (*VirtualAlloc2Fn)(HANDLE, PVOID, SIZE_T, ULONG, ULONG, MEM_EXTENDED_PARAMETER*, ULONG);
typedef PVOID (*MapViewOfFile3Fn)(HANDLE, HANDLE, PVOID, ULONG64, SIZE_T, ULONG, ULONG, MEM_EXTENDED_PARAMETER*, ULONG);

static VirtualAlloc2Fn      pVirtualAlloc2;
static MapViewOfFile3Fn     pMapViewOfFile3;

static void* lookup_kernelbase_library() {
    const char* const name = "KernelBase";
    void* const handle = LoadLibrary(name);
    if (handle == nullptr) {
        fprintf(stderr, "LoadLibrary failed");
        exit(1);
    }
    return handle;
}

static void* lookup_kernelbase_symbol(const char* name) {
    static void* const handle = lookup_kernelbase_library();
    if (handle == nullptr) {
        return nullptr;
    }
    void* ret = ::GetProcAddress((HMODULE) handle, name);
    if (ret == nullptr) {
        fprintf(stderr, "failed to lookup kernelbase symbol: %s", name);
        exit(1);
    }
    return ret;
}

template <typename Fn>
static void install_kernelbase_symbol(Fn*& fn, const char* name) {
    fn = reinterpret_cast<Fn*>(lookup_kernelbase_symbol(name));
}

template <typename Fn>
static void install_kernelbase_1803_symbol_or_exit(Fn*& fn, const char* name) {
    install_kernelbase_symbol(fn, name);
    if (fn == nullptr) {
        fprintf(stderr, "Failed to find 1803 symbol: %s", name);
        exit(1);
    }
}


void init_pd() {
    verbose = true; // Temp default to verbose on Windows
    _SYSTEM_INFO systemInfo;
    GetSystemInfo(&systemInfo);
    valign = systemInfo.dwAllocationGranularity - 1;
    pagesize = systemInfo.dwPageSize;
    if (verbose) {
        fprintf(stderr, "dwPageSize = %d\n", systemInfo.dwPageSize);
        fprintf(stderr, "dwAllocationGranularity = %d\n", systemInfo.dwAllocationGranularity);
    }
    if (valign != 0xffff) {
        // expected: dwAllocationGranularity = 65536
        fprintf(stderr, "Note: dwAllocationGranularity not 64k, valign = %lld\n", valign);
    }

    // Function lookups
    install_kernelbase_1803_symbol_or_exit(pVirtualAlloc2,      "VirtualAlloc2");
    install_kernelbase_1803_symbol_or_exit(pMapViewOfFile3,     "MapViewOfFile3");
}

uint64_t vaddr_alignment_pd() {
    return valign;
}

uint64_t offset_alignment_pd() {
    return valign;
}

uint64_t length_alignment_pd() {
    return valign;
}

/*
 * https://en.wikipedia.org/wiki/Win32_Thread_Information_Block#Accessing_the_TIB
 */
void *getTIB() {
#ifdef _M_AMD64
    return (void *)__readgsqword(0x30);
#elif _M_IX86
    return (void *)__readfsdword(0x18); // not x64, not needed if 32-bit not implemented
#else
#error unsupported architecture
#endif
}

void *getTLS() {
#ifdef _M_AMD64
    return (void*) ( (unsigned long long) getTIB() + 0x58);
#else
#error Unsupported architecture for getTLS implementation
#endif
}

void printTLS() {
    fprintf(stderr, "TIB = 0x%p\n", getTIB());
    fprintf(stderr, "TLS = 0x%p contains 0x%p\n", getTLS(), (void *) *(long long *)getTLS());
}

bool revival_direxists_pd(const char *dirname) {
    DWORD attr = GetFileAttributes(dirname);
    return attr != INVALID_FILE_ATTRIBUTES && (attr & FILE_ATTRIBUTE_DIRECTORY);
}

int revival_checks_pd(const char *dirname) {
    return 0;
}

void tls_fixup_pd(void *tlsPtr) {
    if (verbose) {
        fprintf(stderr, "tls_fixup restore to 0x%p\n", tlsPtr);
        printTLS();
    }
    uint64_t *this_tls = (uint64_t*) getTLS();
    *this_tls = (int64_t) tlsPtr;
    if (verbose) {
        printTLS();
    }
}


void printMemBasicInfo(MEMORY_BASIC_INFORMATION meminfo) {

    uint64_t end = (uint64_t) meminfo.BaseAddress + meminfo.RegionSize;

    fprintf(stderr, "AllocBase: 0x%016llx   Base: 0x%016llx - 0x%016llx len 0x%08llx  AllocProt: 0x%08lx Prot: 0x%08lx\n",
            (uint64_t) meminfo.AllocationBase,
            (uint64_t) meminfo.BaseAddress, end, (uint64_t) meminfo.RegionSize, meminfo.AllocationProtect, meminfo.Protect);

}
void pmap_pd() {

    // Is QueryWorkingSet more useful?
    MEMORY_BASIC_INFORMATION meminfo;
    uint64_t p = (uint64_t) &pmap_pd;
    fprintf(stderr, "Memory Map: >>>\n");
    HANDLE hProc = GetCurrentProcess();

    size_t q = VirtualQueryEx(hProc, (PVOID) p, &meminfo, sizeof(meminfo));

    while (q == sizeof(meminfo)) {
        printMemBasicInfo(meminfo);
        uint64_t end = (uint64_t) meminfo.BaseAddress + meminfo.RegionSize;
        uint64_t next_p = end;
        q = VirtualQueryEx(hProc, (PVOID) next_p, &meminfo, sizeof(meminfo));
        if (next_p == p) {
            break;
        }
        p = next_p;
    }
    fprintf(stderr, "<<<\n");
    waitHitRet();
}


void *symbol_dynamiclookup_pd(void *h, const char*str) {
    FARPROC s = GetProcAddress((HMODULE) h, str);
    if (verbose) {
        fprintf(stderr, "symbol_dynamiclookup: %s = %p \n", str, s);
    }
    if (s == 0) {
        if (verbose) {
            fprintf(stderr, "GetProcAddress failed: 0x%x\n", GetLastError());
        }
        return (void *) -1;
    }
    return (void*) s;
}


void *load_sharedobject_pd(const char *name, void *vaddr) {
    int tries = 0;
    int max_tries = 20;
    while (true) {
        HMODULE h = LoadLibraryA(name);
        if ((void*) h == vaddr) {
            return (void*) h; // success
        }
        fprintf(stderr, "load_sharedobject_pd: %s: will unload as 0x%p != requested 0x%p. error=0x%lx\n", name, h, vaddr, GetLastError());
        int unloaded = FreeLibrary(h);
        if (unloaded == 0) {
            fprintf(stderr, "load_sharedobject_pd: unload failed.\n");
            break;
        }
        tries++;
        if (tries > max_tries) {
            break;
        }
    }
    return (void *) -1;
}

int unload_sharedobject_pd(void *h) {
    if (!FreeLibrary((HMODULE) h)) { // Non-zero on success, zero on failure.
        return GetLastError();
    } else {
        return 0;
    }
}


bool mem_canwrite_pd(void *vaddr, size_t length) {

    MEMORY_BASIC_INFORMATION meminfo;
    HANDLE hProc = GetCurrentProcess();
    size_t q = VirtualQueryEx(hProc, vaddr, &meminfo, sizeof(meminfo));

    if (q == sizeof(meminfo)) {
        if (meminfo.Protect == PAGE_EXECUTE_READWRITE || meminfo.Protect == PAGE_EXECUTE_WRITECOPY|| meminfo.Protect == PAGE_READWRITE
                || meminfo.Protect == PAGE_WRITECOPY) {
            return true;
        } else {
            if (verbose) {
                fprintf(stderr, "    mem_canwrite_pd: %p protect: %lx: NO\n", vaddr, meminfo.Protect);
            }
            return false;
        }
    } else {
        fprintf(stderr, "    mem_canwrite_pd: %p VirtualQueryEx failed: NO\n", vaddr);
    }
    return false;
}



/**
 * do_mmap_pd
 *
 * Mappings are not always simple on Windows, so this is likely to fail
 * file offset is not usually aligned as required,
 * and changing file offset to be aligned means mapping to a different vaddr, which will then not be aligned.
 *
 */
void *do_mmap_pd(void *addr, size_t length, char *filename, int fd, off_t offset) {
    // TODO fail quickly if unaligned?
    //
    // TODO test FILE_MAP_COPY (COW)
    LPVOID p = nullptr;
    HANDLE h;
    HANDLE h2;
    DWORD createFileDesiredAccess = GENERIC_READ | GENERIC_EXECUTE;
    DWORD mappingProt = PAGE_EXECUTE_READ;
    DWORD mapViewAccess = FILE_MAP_READ | FILE_MAP_EXECUTE;
    /*  if (mapWrite) {
        createFileDesiredAccess |= GENERIC_WRITE;
        mappingProt |= PAGE_READWRITE;
        mapViewProt |= 
        } */
    h = CreateFile(filename, createFileDesiredAccess, FILE_SHARE_READ, nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (h == nullptr) {
        printf("    do_mmap_pd: CreateFile failed: %s: 0x%lx\n", filename, GetLastError());
    } else {
        h2 = CreateFileMapping(h, nullptr, mappingProt, 0, 0, nullptr);
        if (h2 == nullptr) {
            printf("    do_mmap_pd: CreateFileMapping failed: %s: 0x%lx\n", filename, GetLastError());
            return (void *) -1;
        }
    }
    // Align virtual address:
    address addr_aligned = align_down((uint64_t) addr, vaddr_alignment_pd());
    // If vaddr changed, update offset:
    if (addr_aligned != (address) addr) {
        offset -= (off_t) ((address) addr - addr_aligned);
        // But offset must be multiple of allocation granularity
        if (offset != align_down(offset, vaddr_alignment_pd())) {
            if (verbose) {
                printf("    do_mmap_pd: file offset becomes unalinged.\n");
            }
        }
    }
    uint64_t offsetAligned = align_down(offset, vaddr_alignment_pd());
    if (verbose) {
        printf("  do_mmap_pd: will map: addr 0x%p length 0x%lx file offset 0x%lx -> offset aligned 0x%lx\n",
                addr, (unsigned long) length, (unsigned long) offset, (unsigned long) offsetAligned);
    }

    HANDLE hProc = GetCurrentProcess();
    DWORD prot = PAGE_EXECUTE_READ; // PAGE_EXECUTE_READWRITE;
    p = pMapViewOfFile3(h2, hProc, (PVOID) addr, offset, length, MEM_REPLACE_PLACEHOLDER, prot, nullptr, 0);
    if ((address) p != (address) addr) {
        if (verbose) {
            printf("    do_mmap_pd: MapViewOfFile3 0x%p failed, ret=0x%p error=0x%lx\n", addr, p, GetLastError());
        }
        p = (void *) -1;
        waitHitRet();
    }
    CloseHandle(h2);
    return (void *) p;
}


void *do_mmap_pd(void *addr, size_t length, off_t offset) {
    return do_mmap_pd(addr, length, core_filename, -1, offset);
}


int do_munmap_pd(void *addr, size_t length) {
    int e = UnmapViewOfFile(addr); // Returns non-zero on success.  Zero on failure.
    if (e == 0) {
        fprintf(stderr, "UnmapViewOfFile 0x%p: failed: returns 0x%d: 0x%lx\n", addr, e, GetLastError());
    }
    return e;
}


void *do_map_allocate_pd_MapViewOfFile(void *vaddr, size_t length) {
    DWORD mappingProt = PAGE_EXECUTE_READWRITE;
    DWORD mapViewAccess = FILE_MAP_READ | FILE_MAP_WRITE | FILE_MAP_EXECUTE;

    address addr_aligned = align_down((uint64_t) vaddr, vaddr_alignment_pd());
    uint64_t diff = (uint64_t) vaddr -  (uint64_t) addr_aligned;
    size_t length_aligned = length + diff;

    HANDLE h = CreateFileMapping(INVALID_HANDLE_VALUE, nullptr, mappingProt, 0, (DWORD) length_aligned, nullptr);
    if (h == nullptr) {
        printf("    do_map_allocate_pd_MapViewOfFile: CreateFileMapping returns = 0x%p : error = 0x%lx\n", h, GetLastError());
        return (void *) -1;
    }

    LPVOID p = MapViewOfFileEx(h, mapViewAccess, 0, 0, length, (void *) vaddr);

    if ((void*) p == vaddr || (void*) p == (void*) addr_aligned) {
        if (verbose) {
            printf("    do_map_allocate_pd_MapViewOfFile: OK.\n");
        }
        return vaddr;
    }

    p = MapViewOfFileEx(h, mapViewAccess, 0, 0, length_aligned, (void *) addr_aligned);
    if ((void*) p == (void*) addr_aligned) {
        if (verbose) {
            printf("    do_map_allocate_pd_MapViewOfFile: OK aligned.\n");
        }
        return vaddr;
    }
    if (verbose) {
        printf("    do_map_allocate_pd_MapViewOfFile: 0x%p fail, gets 0x%p.\n", vaddr, p);
    }

    /*
       if ((void*) p != (void*) vaddrAligned) {
    // Retry if access denied:
    if (GetLastError() == ERROR_ACCESS_DENIED) { // 0x5
    mapViewAccess ^= FILE_MAP_WRITE;
    mapViewAccess ^= FILE_MAP_EXECUTE;
    printf("    Access denied, retry\n");
    p = MapViewOfFileEx(h, mapViewAccess, 0, 0, length, (void *) vaddrAligned);
    }
    if ((void*) p != (void*) vaddrAligned) { 

    printf("    do_map_allocate_pd_MapViewOfFile: first alloc attempt 0x%p len 0x%zx : returns = 0x%p, error = 0x%lx\n",
    (void *) vaddrAligned, length, p, GetLastError());

    if (GetLastError() == ERROR_INVALID_ADDRESS) {// 0x1e7
    // Already mapped, or conflict.

    p = MapViewOfFileEx(h, mapViewAccess, 0, 0, length, (void *) vaddrAligned);
    if ((void*) p != (void*) vaddrAligned) {
    if (GetLastError() == ERROR_INVALID_ADDRESS) { // 0x1e7
    printf("    do_map_allocate_pd_MapViewOfFile: MapViewOfFileEx 0x%p len 0x%zx : returns = 0x%p, error = 0x%lx\n",
    (void *) vaddrAligned, length, p, GetLastError());
    return (void*) -1;
    }
    }
    }
    }
    }
    if ((void*) p == (void*) vaddrAligned) {
    if (verbose) {
    printf("    do_map_allocate_pd_MapViewOfFile: OK at requested 0x%p len 0x%zx (called with 0x%p, length 0x%zx), returns 0x%p : error = 0x%lx\n",
    (void *) vaddrAligned, length, vaddrAligned, length, p, GetLastError());
    }
    }
    if ((void*) p != (void *) vaddrAligned) {
    waitHitRet();
    return (void*) -1;
    }  */
    return p;
}


void *do_map_allocate_pd_VirtualAlloc2(void *addr, size_t length) {
    void *p;
    // TODO: figure out the flags to get VirtualAlloc2 to give finer-than-64k address alignment.

    // (1)
    address addr_aligned = align_down((uint64_t) addr, vaddr_alignment_pd());
    uint64_t diff = (uint64_t) addr -  (uint64_t) addr_aligned;
    size_t length_aligned = length + diff;

    printf("    do_map_allocate_pd_VirtualAlloc2: vaddr 0x%p aligns -> 0x%p  len 0x%llx aligns -> 0x%llx\n",
            (void*) addr, (void*) addr_aligned, length, length_aligned);

    HANDLE hProc = GetCurrentProcess();
    p = pVirtualAlloc2(hProc, (PVOID) addr_aligned, length_aligned, MEM_RESERVE | MEM_COMMIT, PAGE_EXECUTE_READWRITE, nullptr, 0);

    if (verbose) {

        printf("    do_map_allocate_pd_VirtualAlloc2: first alloc attempt 0x%p len 0x%zx : returns = 0x%p, error = 0x%lx\n",
                (void *) addr_aligned, length_aligned, p, GetLastError());
    }

    if ((void*) p != (void*) addr_aligned) {

        if (GetLastError() == 0) {
            //  e.g. first alloc attempt 0x000000E69850B000 len 0x5000 : returns = 0x000000E698500000, error = 0x0
            // No error, but requested address was re-aligned.
            // If length was not increased, will be a problem. length is pagesize aligned, 4k = 0x1000

            // Query aligned address, expand?
            MEMORY_BASIC_INFORMATION meminfo;
            size_t q = VirtualQueryEx(hProc, addr, &meminfo, sizeof(meminfo));
            if (q == sizeof(meminfo)) {
                uint64_t new_ptr = (uint64_t) meminfo.BaseAddress + (uint64_t) meminfo.RegionSize;
                length_aligned = length_aligned - ((uint64_t)addr - new_ptr);
                printf("    do_map_allocate_pd_VirtualAlloc2: retry new base 0x%llx len 0x%llx\n", new_ptr, length_aligned);
                return do_map_allocate_pd_VirtualAlloc2((void*) new_ptr, length_aligned);
            } else {
                printf("VirtualQueryEx failed");
                return (void*) -1;
            }

        }

        if (GetLastError() == ERROR_INVALID_ADDRESS /* 0x1e7 */) {
            // Already mapped, conflict.
            // If we aligned down, either manually above (1), or because VirtualAlloc2 did it for us, we can overlap with the previous region.

            // Query to find the gaps that need mapping, return success to proceed with copy.
            printf("    invalid address: already mapped?\n");
            MEMORY_BASIC_INFORMATION meminfo;
            size_t q = VirtualQueryEx(hProc, (PVOID) addr_aligned, &meminfo, sizeof(meminfo));
            if (q != sizeof(meminfo)) {
                printf("    do_map_allocate_pd_VirtualAlloc2: VirtualQueryEX: returns %lld (fail)\n",  q);
                return (void *) -1;
            } else {

                printf("    VirtualQueryEx: 1 base 0x%llx len 0x%llx allocationprotect: 0x%lx protect: 0x%lx\n",
                        (uint64_t) meminfo.BaseAddress, (uint64_t) meminfo.RegionSize, meminfo.AllocationProtect, meminfo.Protect);
                printMemBasicInfo(meminfo);

                if ((uint64_t) meminfo.BaseAddress == addr_aligned) {
                    // matches existing mapping
                    printf("    invalid address: already mapped region matches base\n");
                    if (meminfo.Protect == 0 || meminfo.Protect == PAGE_READONLY || meminfo.Protect == PAGE_EXECUTE_READ) {
                        DWORD lpfOldProtect;
                        // DWORD prot = PAGE_EXECUTE_READ;
                        if (!VirtualProtect((PVOID) meminfo.BaseAddress, length_aligned, PAGE_EXECUTE_READWRITE, &lpfOldProtect)) {
                            printf("    do_map_allocate_pd: existing, failed setting rw: 0x%x.\n", GetLastError());
                            return (void *) -1;
                        }
                    }

                    // Is more allocation needed?
                    // Compare region with originally requested address.
                    uint64_t existing_end = (uint64_t) meminfo.BaseAddress + (uint64_t) meminfo.RegionSize;
                    uint64_t wanted_end = (uint64_t) addr + (uint64_t) length;
                    if (existing_end >= wanted_end) {
                        printf("    do_map_allocate_pd: mapping covered by existing_end at 0x%llx.\n", existing_end);
                        return addr;
                    }
                    size_t remaining = (uint64_t) addr  - existing_end;
                    printf("    do_map_allocate_pd: existing. remaining = 0x%llx.\n", remaining);
                    if (remaining <= 0) {
                        printf("    do_map_allocate_pd: done.\n");
                        return addr;
                    } else {
                        printf("    do_map_allocate_pd: recurse for next part 0x%p len 0x%zx.\n", (void *) existing_end, remaining);
                        void *p2 = do_map_allocate_pd((void *) existing_end, remaining);
                        if (p2 != (void *) existing_end) {
                            printf("    do_map_allocate_pd: recursion failed, got 0x%p\n", p2);
                            p = (void *) -1;
                        }
                    }
                }
            }
        }
    }

    if ((void*) p == (void*) addr_aligned) {
        if (verbose) {
            printf("    do_map_allocate_pd_VirtualAlloc2: OK at requested 0x%p len 0x%zx (called with 0x%p, length 0x%zx), returns 0x%p : error = 0x%lx\n",
                    (void *) addr_aligned, length_aligned, addr, length, p, GetLastError());
            return addr; // original requested address
        }
    }

    waitHitRet();
    return (void*) -1;
}


/**
 * Create a memory mapping at a given address, length.
 *
 * Allocate, ready for copying data in, as direct mapping from file has failed.
 */
void *do_map_allocate_pd(void *addr, size_t length) {
    return do_map_allocate_pd_MapViewOfFile(addr, length);
    //return do_map_allocate_pd_VirtualAlloc2(addr, length);
}


/**
 * Read a MINIDUMP_STRING, which is:
 * ULONG32 Length, WCHAR buffer (UTF16-LE)
 */
char *readstring_minidump(int fd) {
    wchar_t *wbuf = (wchar_t *) calloc(BUFLEN, 1);
    char *mbuf = (char *) calloc(BUFLEN, 1);
    if (wbuf == nullptr || mbuf == nullptr) {
        fprintf(stderr, "Failed to allocate buf for readstring\n");
        return nullptr;
    }
    ULONG32 length;
    int e = read(fd, &length, sizeof(ULONG32));
    if (e != sizeof(ULONG32)) {
        fprintf(stderr, "Failed to read MINIDUMP_STRING length: %d\n", e);
        return nullptr;
    }
    if (length >= BUFLEN) {
        fprintf(stderr, "MINIDUMP_STRING length too long: %d\n", length);
        return nullptr;
    }
    e = read(fd, wbuf, length);
    if (e != length) {
        fprintf(stderr, "Failed to read MINIDUMP_STRING chars: %d\n", e);
        return nullptr;
    }

    e = (int) wcstombs(mbuf, wbuf, length);
    if (e < (int) (length/2)) {
        fprintf(stderr, "MINIDUMP_STRING length %d, short bad results from wcstombs: %d\n", length, e);
        return nullptr;
    }
    //    fprintf(stderr, "readsstring_minidump: size %d '%s'\n", length, (const char *) mbuf);

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


struct minidump {
    int fd;
    _MINIDUMP_HEADER hdr;
};

/**
 * Open a MINIDUMP file. Read header.
 */
struct minidump *open_minidump(const char *filename) {

    int fd = open(filename, O_RDONLY);
    if (fd < 0) {
        fprintf(stderr, "open '%s' failed: %d: %s\n", core_filename, errno, strerror(errno));
        return nullptr;
    }
    struct minidump *dump = (struct minidump *) malloc(sizeof(struct minidump));
    dump->fd = fd;

    // Read MiniDump header
    // _MINIDUMP_HEADER hdr;
    int e = read(fd, &(dump->hdr), sizeof(_MINIDUMP_HEADER));
    if (dump->hdr.Signature != MINIDUMP_SIGNATURE) {
        fprintf(stderr, "Minidump header unexpected: %lx\n", dump->hdr.Signature);
        return nullptr;
    }
    if (verbose) {
        fprintf(stderr, "NumberOfStreams = %d\n", dump->hdr.NumberOfStreams);
        fprintf(stderr, "StreamDirectoryRva = %d\n", dump->hdr.StreamDirectoryRva);
    }

    return dump;
}

/**
 * Read minidump to locate wanted stream.
 * Seek the dump file descriptor to the stream data.
 */
MINIDUMP_DIRECTORY *minidump_find_stream(struct minidump *dump, int stream) {
    // Stream directory
    MINIDUMP_DIRECTORY* md = (MINIDUMP_DIRECTORY*) malloc(sizeof(MINIDUMP_DIRECTORY));
    lseek(dump->fd, dump->hdr.StreamDirectoryRva, SEEK_SET);
    for (unsigned int i=0; i < dump->hdr.NumberOfStreams; i++) {
        int e = read(dump->fd, md, sizeof(*md));
        if (verbose) {
            fprintf(stderr, "StreamType = %d\n", md->StreamType);
        }
        if (md->StreamType == stream) {
            lseek(dump->fd, md->Location.Rva, SEEK_SET);
            return md;
        }     
    }
    return nullptr;
}

void close_minidump(struct minidump *dump) {
    close(dump->fd);
    free(dump);
}

void *resolve_jvm_info_pd(const char *filename) {
    // Open dump, find JVM, find JVM load address.

    struct minidump *dump = open_minidump(filename);
    if (dump == nullptr) {
        return nullptr;
    }

    MINIDUMP_DIRECTORY *md = minidump_find_stream(dump, ModuleListStream);
    if (md == nullptr) {
        fprintf(stderr, "Minidump ModuleListStream not found\n");
        return (void *) -1;
    }

    // Use MINIDUMP_LOCATION_DESCRIPTOR
    ULONG32 size = md->Location.DataSize;
    ULONG32 n;
    int e = read(dump->fd, &n, sizeof(n));
    // fprintf(stderr, "number of modules = %d\n", n);
    for (unsigned int j = 0; j < n; j++) {
        MINIDUMP_MODULE module;
        e = read(dump->fd, &module, sizeof(module));
        // Read name
        //fprintf(stderr, "name RVA = 0x%lx\n", (unsigned long) module.ModuleNameRva);
        char *name = string_at_offset_minidump(dump->fd, module.ModuleNameRva);
        if (verbose) {
            fprintf(stderr, "MODULE name = '%s' 0x%llx\n", name, module.BaseOfImage);
        }
        // Is it the JVM?
        if (strstr(name, "\\jvm.dll") != nullptr) {
            if (verbose) {
                fprintf(stderr, "FOUND JVM name = '%s' %llx\n", name, module.BaseOfImage);
            }
            jvm_filename = name;
            jvm_address = (void *) module.BaseOfImage;
            break;
        }
        free(name);
    }
    close_minidump(dump);
    return jvm_filename;
}


char *get_jvm_filename_pd(const char *filename) {
    if (jvm_filename == nullptr) {
        resolve_jvm_info_pd(filename);
    }
    return jvm_filename;
}

void *get_jvm_load_adddress_pd(const char*corename) {
    return jvm_address;
}

int copy_file_pd(const char *srcfile, const char *destfile) {
    char command[BUFLEN];
    memset(command, 0, BUFLEN);

    strncat(command, "COPY ", BUFLEN - 1);
    strncat(command, srcfile, BUFLEN - 1);
    strncat(command, " ", BUFLEN - 1);
    strncat(command, destfile, BUFLEN - 1);

    fprintf(stderr, "COPY: %s\n", command);
    return system(command);
}


int relocate_sharedlib_pd(const char *filename, const void *addr) {
    // Call editbin.exe
    char *editbin = getenv("EDITBIN");
    if (editbin  == nullptr) {
        fprintf(stderr, "EDITBIN not set\n");
        return -1;
    }
    // EDITBIN.EXE /DYNAMICBASE:NO /REBASE:BASE=0xaddress jvm.dll
    char command[BUFLEN];
    memset(command, 0, BUFLEN);

    strncat(command, editbin, BUFLEN - 1);
    strncat(command, " /DYNAMICBASE:NO /REBASE:BASE=0x", BUFLEN - 1);
    char address[32];
    sprintf(address, "%llx", (unsigned long long) addr);
    strncat(command, address, BUFLEN - 1);
    strncat(command, " ", BUFLEN - 1);
    strncat(command, filename, BUFLEN - 1);

    fprintf(stderr, "relocate_sharedlib_pd: '%s'\n", command);
    return system(command);
}


int create_mappings_pd(int fd, const char *corename, const char *jvm_copy, const char *javahome, void *addr) {
    // Read minidump memory list.
    struct minidump *dump = open_minidump(corename);
    if (dump == nullptr) {
        return -1;
    }

    MINIDUMP_DIRECTORY *md = minidump_find_stream(dump, Memory64ListStream); // or MemoryListStream
    if (md == nullptr) {
        fprintf(stderr, "Minidump MemoryListStream not found\n");
        return -1;
    }

    // Read MINIDUMP_MEMORY64_LIST 
    ULONG64 NumberOfMemoryRanges;
    RVA64 BaseRVA;
    int e = read(dump->fd, &NumberOfMemoryRanges, sizeof(NumberOfMemoryRanges));
    e = read(dump->fd, &BaseRVA, sizeof(BaseRVA));
    fprintf(stderr, "create_mappings_pd: %lld memory ranges at base RVA=0x%llx\n", NumberOfMemoryRanges, BaseRVA);
    RVA64 currentRVA = BaseRVA;
    MINIDUMP_MEMORY_DESCRIPTOR64 d;
    ULONG64 prevAddr = 0;
    for (ULONG64 i = 0; i < NumberOfMemoryRanges; i++) {
        e = read(dump->fd, &d, sizeof(d));
        if (d.StartOfMemoryRange == prevAddr) {
            break;
        }
        fprintf(stderr, "create_mappings_pd: %8lld 0x%16llx 0x%16llx   dump file offset 0x%16llx \n",
                i, d.StartOfMemoryRange, d.DataSize, currentRVA);
        currentRVA += d.DataSize;
        prevAddr = d.StartOfMemoryRange;

        // PSR_TODO
        // Write to mappings file.
        // use fd, create a Segment, call int mappings_file_write(int fd, Segment seg) 

    }
    close_minidump(dump);
    return -1;
}

int create_revivalbits_native_pd(const char *corename, const char *javahome, const char *revival_dirname, const char *libdir) {
    char jvm_copy[BUFLEN];

    char *jvm = get_jvm_filename_pd(corename);
    if (jvm == nullptr) {
        return -1;
    }
    fprintf(stderr, "JVM = '%s'\n", jvm);
    void *addr = get_jvm_load_adddress_pd(corename);
    fprintf(stderr, "JVM addr = %p\n", addr);

    // make core.revival dir
    int e = _mkdir(revival_dirname);
    if (e < 0) {
        fprintf(stderr, "Cannot create directory: %s: %s\n", revival_dirname, strerror(errno));
        return e;
    }

    // use libdir if specified
    if (libdir != nullptr) {
        // find jvm.dll in libdir, use that instead of dump's jvm filename
    }

    // copy libjvm into core.revival dir
    memset(jvm_copy, 0, BUFLEN);
    strncpy(jvm_copy, revival_dirname, BUFLEN - 1);
    strncat(jvm_copy, "\\jvm.dll", BUFLEN - 1);
    fprintf(stderr, "copying JVM to: %s\n", jvm_copy);
    e = copy_file_pd(jvm, jvm_copy);
    if (e != 0) {
        fprintf(stderr, "Cannot copy JVM: %s to  %s\n", jvm_copy, revival_dirname);
        return -1;
    }

    // relocate copy of libjvm:
    e = relocate_sharedlib_pd(jvm_copy, addr);
    if (e != 0) {
        fprintf(stderr, "failed to relocate JVM: %d\n", e);
        //    return -1; // temp ignore to read mappings...
    }

    // Create core.mappings file:
    unsigned long long coretime = 0;
    int fd = mappings_file_create(revival_dirname, corename, "0", coretime);
    if (fd < 0) {
        fprintf(stderr, "failed to create mappings file\n");
        return -1;
    }

    e = create_mappings_pd(fd, corename, jvm_copy, javahome, addr);

    fsync(fd);
    close(fd);
    if (e != 0) {
        fprintf(stderr, "failed to create memory mappings: %d\n", e);
        return -1;
    }
    return 0;
}

