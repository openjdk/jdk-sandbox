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

#include <imagehlp.h>
// #include <dbghelp.h>
#include <winternl.h>

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
        error("LoadLibrary failed");
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
        error("failed to lookup kernelbase symbol: %s", name);
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
        error("Failed to find 1803 symbol: %s", name);
    }
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

unsigned long long max_user_vaddr_pd() {
    return 0x7FFFFFFFFFFF;
}

void init_pd() {
    _SYSTEM_INFO systemInfo;
    GetSystemInfo(&systemInfo);
    valign = systemInfo.dwAllocationGranularity - 1;
    pagesize = systemInfo.dwPageSize;
    if (verbose) {
        log("dwPageSize = %d", systemInfo.dwPageSize);
        log("dwAllocationGranularity = %d", systemInfo.dwAllocationGranularity);
    }
    if (valign != 0xffff) {
        // expected: dwAllocationGranularity = 65536
        warn("Note: dwAllocationGranularity not 64k, valign = %lld", valign);
    }

    // Function lookups
    install_kernelbase_1803_symbol_or_exit(pVirtualAlloc2,      "VirtualAlloc2");
    install_kernelbase_1803_symbol_or_exit(pMapViewOfFile3,     "MapViewOfFile3");
}


bool revival_direxists_pd(const char *dirname) {
    DWORD attr = GetFileAttributes(dirname);
    return attr != INVALID_FILE_ATTRIBUTES && (attr & FILE_ATTRIBUTE_DIRECTORY);
}

int revival_checks_pd(const char *dirname) {
    return 0;
}


void printMemBasicInfo(MEMORY_BASIC_INFORMATION meminfo) {

    uint64_t end = (uint64_t) meminfo.BaseAddress + meminfo.RegionSize;

    fprintf(stderr, "AllocBase: 0x%016llx   Base: 0x%016llx - 0x%016llx len 0x%08llx  AllocProt: 0x%08lx Prot: 0x%08lx\n",
            (uint64_t) meminfo.AllocationBase,
            (uint64_t) meminfo.BaseAddress, end, (uint64_t) meminfo.RegionSize, meminfo.AllocationProtect, meminfo.Protect);

}

void pmap_pd() {

/*    // Is QueryWorkingSet more useful?
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
    waitHitRet(); */
}


void *symbol_dynamiclookup_pd(void *h, const char*str) {
    FARPROC s = GetProcAddress((HMODULE) h, str);
    logv("symbol_dynamiclookup: %s = %p", str, s);
    if (s == 0) {
        logv("GetProcAddress failed: 0x%x", GetLastError());
        return (void *) -1;
    }
    return (void*) s;
}


void *load_sharedobject_pd(const char *name, void *vaddr) {
    int tries = 0;
    int max_tries = 2;

    while (tries++ < max_tries) {
        HMODULE h = LoadLibraryA(name);
        if ((void*) h == vaddr) {
            return (void*) h; // success
        }
        warn("load_sharedobject_pd: %s: will unload as 0x%p != requested 0x%p. error=0x%lx\n", name, h, vaddr, GetLastError());
        int unloaded = FreeLibrary(h);
        // If we want to try re-trying, should map some memory at the address we just got.
        if (unloaded == 0) {
            warn("load_sharedobject_pd: unload failed.\n");
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
        if (meminfo.Protect == PAGE_EXECUTE_READWRITE
            || meminfo.Protect == PAGE_EXECUTE_WRITECOPY
            //|| meminfo.Protect == PAGE_READWRITE
            || meminfo.Protect == PAGE_WRITECOPY) {
            //logv("    mem_canwrite_pd: %p protect: 0x%lx: YES", vaddr, meminfo.Protect);
            return true;
        } else {
            logv("    mem_canwrite_pd: %p protect: 0x%lx: NO", vaddr, meminfo.Protect);
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
    // TODO test FILE_MAP_COPY (COW)

    // Fail quickly if unaligned:
    uint64_t offsetAligned = align_down(offset, vaddr_alignment_pd());
    if (offsetAligned != offset) {
        logv("do_mmap_pd: file offset 0x%lx not aligned, not mapping directly.", offset);
        return (void *) -1;
    }


    LPVOID p = nullptr;
    HANDLE h;
    HANDLE h2;
    DWORD createFileDesiredAccess = GENERIC_READ | GENERIC_EXECUTE;
    DWORD mappingProt = PAGE_EXECUTE_READ;
    DWORD mapViewAccess = FILE_MAP_READ | FILE_MAP_EXECUTE;
    bool mapWrite = true;
      if (mapWrite) {
        createFileDesiredAccess |= GENERIC_WRITE;
        mappingProt |= PAGE_READWRITE;
//        mapViewProt |= 
        }
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
        warn("UnmapViewOfFile 0x%p: failed: returns 0x%d: 0x%lx\n", addr, e, GetLastError());
    }
    return e;
}


void *do_map_allocate_pd_MapViewOfFile(void *vaddr, size_t length) {
    DWORD mappingProt = PAGE_EXECUTE_READWRITE;
    DWORD mapViewAccess = FILE_MAP_READ | FILE_MAP_WRITE | FILE_MAP_EXECUTE;

    address addr_aligned = align_down((uint64_t) vaddr, vaddr_alignment_pd());
    uint64_t diff = (uint64_t) vaddr -  (uint64_t) addr_aligned;
    size_t length_aligned = length + diff;

    if (addr_aligned != (address) vaddr) {
        warn("    do_map_allocate_pd_MapViewOfFile: was not aligned. 0x%llx -> 0x%llx", vaddr, addr_aligned);
    }

    HANDLE h = CreateFileMapping(INVALID_HANDLE_VALUE, nullptr, mappingProt, 0, (DWORD) length_aligned, nullptr);
    if (h == nullptr) {
        warn("    do_map_allocate_pd_MapViewOfFile: CreateFileMapping returns = 0x%p : error = 0x%lx\n", h, GetLastError());
        return (void *) -1;
    }

    LPVOID p = MapViewOfFileEx(h, mapViewAccess, 0, 0, length, (void *) vaddr);

    if ((void*) p == vaddr || (void*) p == (void*) addr_aligned) {
        logv("do_map_allocate_pad: MapViewOfFile 0x%llx 0x%llx OK", (unsigned long long) vaddr, length);
        return vaddr;
    }

    logv("do_map_allocate_pad: MapViewOfFile 0x%llx 0x%llx bad, gets 0x%llx", (unsigned long long) vaddr, length, (unsigned long long) p);

    p = MapViewOfFileEx(h, mapViewAccess, 0, 0, length_aligned, (void *) addr_aligned);
    if ((void*) p == (void*) addr_aligned) {
        logv("    do_map_allocate_pd_MapViewOfFile: OK aligned.\n");
        return vaddr;
    }

    logv("    do_map_allocate_pd_MapViewOfFile: 0x%p fail, gets 0x%p.\n", vaddr, p);

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
                warn("VirtualQueryEx failed");
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
                            warn("    do_map_allocate_pd: existing, failed setting rw: 0x%x.\n", GetLastError());
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


void *do_map_allocate_pd(void *addr, size_t length) {

//    return do_map_allocate_pd_MapViewOfFile(addr, length);
    return do_map_allocate_pd_VirtualAlloc2(addr, length);
}


/**
 * Read a MINIDUMP_STRING, which is:
 * ULONG32 Length, WCHAR buffer (UTF16-LE)
 */
char *readstring_minidump(int fd) {
    wchar_t *wbuf = (wchar_t *) calloc(BUFLEN, 1);
    char *mbuf = (char *) calloc(BUFLEN, 1);
    if (wbuf == nullptr || mbuf == nullptr) {
        warn("Failed to allocate buf for readstring\n");
        return nullptr;
    }
    ULONG32 length;
    int e = read(fd, &length, sizeof(ULONG32));
    if (e != sizeof(ULONG32)) {
        warn("Failed to read MINIDUMP_STRING length: %d\n", e);
        return nullptr;
    }
    if (length >= BUFLEN) {
        warn("MINIDUMP_STRING length too long: %d\n", length);
        return nullptr;
    }
    e = read(fd, wbuf, length);
    if (e != length) {
        warn("Failed to read MINIDUMP_STRING chars: %d\n", e);
        return nullptr;
    }

    e = (int) wcstombs(mbuf, wbuf, length);
    if (e < (int) (length/2)) {
        warn("MINIDUMP_STRING length %d, short bad results from wcstombs: %d\n", length, e);
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


class MiniDump {
  public:
    MiniDump(const char* filename);
    void open(const char* filename);
    void close();
    ~MiniDump();

    MINIDUMP_DIRECTORY* find_stream(int stream);

    int fd;
    _MINIDUMP_HEADER hdr;
};

MiniDump::MiniDump(const char* filename) {
    open(filename);
}

/**
 * Open a MINIDUMP file. Read header.
 */
void MiniDump::open(const char *filename) {
    fd = ::open(filename, O_RDONLY);
    if (fd < 0) {
        warn("open '%s' failed: %d: %s\n", core_filename, errno, strerror(errno));
        return;
    }
//    struct minidump *dump = (struct minidump *) malloc(sizeof(struct minidump));

    // Read MiniDump header
    int e = read(fd, &hdr, sizeof(_MINIDUMP_HEADER));
    if (hdr.Signature != MINIDUMP_SIGNATURE) {
        warn("Minidump header unexpected: %lx\n", hdr.Signature);
    } else {
        if (verbose) {
            fprintf(stderr, "NumberOfStreams = %d\n", hdr.NumberOfStreams);
            fprintf(stderr, "StreamDirectoryRva = %d\n", hdr.StreamDirectoryRva);
        }
    }
}

void MiniDump::close() {
    ::close(fd);
    fd = -1;
}

MiniDump::~MiniDump() {
    close();
}


/**
 * Read minidump to locate wanted stream.
 * Seek the dump file descriptor to the stream data.
 */
MINIDUMP_DIRECTORY * MiniDump::find_stream(int stream) {
    // Stream directory
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

/**
 * Open dump, find JVM filename and load address.
 * Return the jvm_filename to confirm it was found.
 */
char *resolve_jvm_info_pd(const char *filename) {
// Read dump ModuleListStream to find jvm.dll

    MiniDump* dump = new MiniDump(filename);
    if (dump->fd < 0) { // "is valid"
        return nullptr;
    }

    MINIDUMP_DIRECTORY *md = dump->find_stream(ModuleListStream);
    if (md == nullptr) {
        warn("Minidump ModuleListStream not found\n");
        return nullptr;
    }

    // Use MINIDUMP_LOCATION_DESCRIPTOR
    ULONG32 size = md->Location.DataSize;
    ULONG32 n;
    int e = read(dump->fd, &n, sizeof(n));
    for (unsigned int j = 0; j < n; j++) {
        MINIDUMP_MODULE module;
        e = read(dump->fd, &module, sizeof(module));
        // Read name
        char *name = string_at_offset_minidump(dump->fd, module.ModuleNameRva);
        if (verbose) {
            fprintf(stderr, "MODULE name = '%s' 0x%llx\n", name, module.BaseOfImage);
        }
        // Is it the JVM?
        if (strstr(name, "\\" JVM_FILENAME) != nullptr) {
            if (verbose) {
                fprintf(stderr, "FOUND JVM name = '%s' %llx\n", name, module.BaseOfImage);
            }
            jvm_filename = name;
            jvm_address = (void *) module.BaseOfImage;
            break;
        }
        free(name);
    }
    return jvm_filename;
}


char *get_jvm_filename_pd(const char *filename) {
    if (jvm_filename == nullptr) {
        jvm_filename = resolve_jvm_info_pd(filename);
    }
    return jvm_filename;
}

void *get_jvm_load_adddress_pd(const char*corename) {
    return jvm_address;
}


void normalize(char *s) {
    for (char *p = s; *p != '\0'; p++) {
        if (*p == '/') *p = '\\';
    }
}

int copy_file_pd(const char *srcfile, const char *destfile) {
    char command[BUFLEN];
    memset(command, 0, BUFLEN);

    // Normalise paths
    char *s = (char *) malloc(strlen(srcfile));
    char *d = (char *) malloc(strlen(destfile));
    strcpy(s, srcfile);
    strcpy(d, destfile);
    normalize(s);
    normalize(d);

    strncat(command, "COPY ", BUFLEN - 1);
    strncat(command, s, BUFLEN - 1);
    strncat(command, " ", BUFLEN - 1);
    strncat(command, d, BUFLEN - 1);
    free(s);
    free(d);

    logv("copy: '%s'", command);
    int e = system(command);
    logv("copy: '%s' returns %d", command, e);
    return e;
}


int relocate_sharedlib_pd(const char *filename, const void *addr) {
    // Call editbin.exe
    char *editbin = getenv("EDITBIN");
    if (editbin  == nullptr) {
        warn("EDITBIN not set\n");
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

    int e = system(command);
    log("relocate_sharedlib_pd: '%s' returns %d", command, e);
    return e;
}



/**
 * Read the next Minidump Memory Descriptor.
 * Return nullptr for not found, should terminate.
 */
Segment* readSegment(MiniDump* dump, MINIDUMP_MEMORY_DESCRIPTOR64 *d, RVA64* currentRVA) {
    do {
        int e = read(dump->fd, d, sizeof(*d));
        if (e < 0) {
            warn("create_mappings: read: %s", strerror(errno));
            return nullptr;
        } else if (e < sizeof(d)) {
            long pos = _lseek(dump->fd, 0, SEEK_CUR);
            warn("create_mappings: read expects %d, got %d, at pos %ld.  Retry...", sizeof(d), e, pos);
            // Retry a short read.
            // e.g. test where after 80 iterations, read returns 2.
            continue;
        } else {
            break;
        }
    } while (true);

    if (max_user_vaddr_pd() > 0 && d->StartOfMemoryRange >= max_user_vaddr_pd()) {
        logv("create_mappings: terminating as address 0x%llx >= 0x%llx", d->StartOfMemoryRange, max_user_vaddr_pd());
        return nullptr; // End of user space mappings.
    }

    Segment *seg = new Segment((void *) d->StartOfMemoryRange, (size_t) d->DataSize, (off_t) *currentRVA, (size_t) d->DataSize);
    *currentRVA += d->DataSize;
    return seg;
}

int minidump_read_memory(MiniDump* dump, uint64_t addres, void* dest, size_t size) {
    return 0;
}

/*uint64_t resolve_teb(MiniDump* dump) {
    // Find Minidump ThreadListStream
    // Read _MINIDUMP_THREAD
    // Read TEB
    MINIDUMP_DIRECTORY *md = dump->find_stream(ThreadListStream);
    if (md == nullptr) {
        warn("Minidump ThreadListStream not found\n");
        return 0;
    }
    // Read MINIDUMP_THREAD_LIST 
    ULONG32 NumberOfThreads;
    int e = read(dump->fd, &NumberOfThreads, sizeof(NumberOfThreads));
    if (e < sizeof(NumberOfThreads)) {
        warn("read of NumberOfThreads failed");
        return 0;
    }
    logv("XXXX NumberOfThreads: %ld", NumberOfThreads);

    MINIDUMP_THREAD thread;
    for (unsigned int i = 0; i < NumberOfThreads; i++) {
        memset(&thread, 0, sizeof(thread));
        e = read(dump->fd, &thread, sizeof(thread));
        if (e < sizeof(thread)) {
            warn("read of MINIDUMP_THREAD %d failed: %d", i, e);
            return 0;
        }
        logv("XXX MINIDUMP_THREAD id 0x%lx teb 0x%lx", thread.ThreadId, thread.Teb);
        if (thread.Teb != 0) {
            return (uint64_t) thread.Teb;
        }
    }
    return 0;
} */

int create_mappings_pd(int fd, const char *corename, const char *jvm_copy, const char *javahome, void *addr) {
    // Read minidump memory list, create text of mappings list.
    // Plan to map data directly from core where possible.
    // If alignment simply does not work (segments too close), create larger mapping and copy bytes.

    MiniDump* dump = new MiniDump(corename);
    if (dump->fd < 0) {
        return -1;
    }

    MINIDUMP_DIRECTORY *md = dump->find_stream(Memory64ListStream);
    if (md == nullptr) {
        warn("Minidump Memory64ListStream not found\n");
        return -1;
    }

    // Maintain a list of segments to copy bytes later.
    std::list<Segment> segsToCopy;

    // Read MINIDUMP_MEMORY64_LIST 
    ULONG64 NumberOfMemoryRanges;
    RVA64 BaseRVA;
    int e = read(dump->fd, &NumberOfMemoryRanges, sizeof(NumberOfMemoryRanges));
    e = read(dump->fd, &BaseRVA, sizeof(BaseRVA));
    logv("create_mappings_pd: %lld memory ranges at base RVA=0x%llx\n", NumberOfMemoryRanges, BaseRVA);
    RVA64 currentRVA = BaseRVA;
    MINIDUMP_MEMORY_DESCRIPTOR64 d;
    ULONG64 prevAddr = 0;

    // Iterate, considering a current and next segment, so we can check for "too close" addresses.
    Segment *seg = readSegment(dump, &d, &currentRVA);
    Segment *segNext = nullptr;

    for (ULONG64 i = 0; i < NumberOfMemoryRanges; i++) {

        // Either read, or use a segNext we already read (but did not use):
        if (segNext != nullptr) {
            seg = segNext;
            segNext = nullptr;
        } else {
            seg = readSegment(dump, &d, &currentRVA);
            if (seg == nullptr) {
                break;
            }
        }

        // Repeated data in minidump can be observed at end - this may have been only seen before the read retry above...
        if (d.StartOfMemoryRange == prevAddr) {
            logv("create_mappings: skipping due to repetition, 0x%llx", prevAddr);
            continue; 
        }

        logv("create_mappings_pd: %8lld 0x%16llx 0x%16llx   dump file offset 0x%16llx \n", i, d.StartOfMemoryRange, d.DataSize, currentRVA);
//        currentRVA += d.DataSize; ( in readSegment() now)
        prevAddr = d.StartOfMemoryRange;

        if (!seg->is_relevant()) {
            logv("create_mappings: not relevant: 0x%llx", d.StartOfMemoryRange);
            continue;
        }

        // Can we coalesce (join) neighbouring regions during the following iteration,
        // if the file offsets work, to reduce number of mappings (there are likely >600).

        // Consider the NEXT region as well, in case it is too close for vaddralignment to work.
        // Will grow a bigger segment to map, that contains all these neighbouring segments (copied).
        segNext = readSegment(dump, &d, &currentRVA);
        if (segNext != nullptr) { i++; }  // Maintain outer loop counter
        Segment *biggerSeg = nullptr;

        while (segNext != nullptr && seg->end() + vaddr_alignment_pd() >= segNext->start()) {
            // Will copy these segs:
            warn("create_mappings: segs too close for alignment, seg: %p - %p next seg: %p", seg->vaddr, seg->end(), segNext->vaddr);
            char *b = seg->toString();
            fprintf(stderr, "later seg    : %s\n", b);
            free(b);
            b = segNext->toString();
            fprintf(stderr, "later segNext: %s\n", segNext->toString());
            free(b);

            // Save segs, will write "C" copy lines later.
            if (biggerSeg == nullptr) {
                segsToCopy.push_back(seg);   // Write first seg only on first time through this loop
                biggerSeg = new Segment(seg); // Start with copy of seg info.
            }
            segsToCopy.push_back(segNext);      // Write segNext on all iterations

            biggerSeg->set_end(segNext->end());  // Expand to cover both.
            b = biggerSeg->toString();
            fprintf(stderr, "BIGGER seg expanded: %s\n", b);
            free(b);

            // Next...
            seg = segNext;
            segNext = readSegment(dump, &d, &currentRVA);
            if (segNext != nullptr) { i++; }
        }

        // Write line to mappings file.
        // Use the biggerSeg if we were in the above loop.
        if (biggerSeg != nullptr) {
            char *b = biggerSeg->toString();
            fprintf(stderr, "write BIGGER seg    : %s\n", b);
            free(b);
            e = biggerSeg->write_mapping(fd, "m"); // map only, copy later
            biggerSeg = nullptr;
        } else {
            e = seg->write_mapping(fd, "M"); // map directly from core
        }
    } // End loop reading minidump memory descriptors.

    // Write regions to copy
    std::list<Segment>::iterator iter;
    for (iter = segsToCopy.begin(); iter != segsToCopy.end(); iter++) {
        iter->write_mapping(fd, "C");
    }

    writef(fd, "\n");
    return 0;
}


const int N_JVM_SYMS = 6;
const char *JVM_SYMS[N_JVM_SYMS] = {
    SYM_REVIVE_VM,
    SYM_TTY,
    SYM_JVM_VERSION,
    SYM_TC_OWNER,
    SYM_PARSE_AND_EXECUTE,
    SYM_THROWABLE_PRINT
};

void write_symbols(int fd, const char* symbols[], int count, const char *revival_dirname) {

    PLOADED_IMAGE image = ImageLoad("jvm.dll", revival_dirname);
    if (image == nullptr) {
    	error("ImageLoad error : %d", GetLastError());
    }

    HANDLE hCurrentProcess = GetCurrentProcess();
    HANDLE h2 = (HANDLE) 1;

    bool e = SymInitialize(h2, nullptr, false);
    if (e != TRUE) {
    	error("SymInitialize error : 0x%lx", GetLastError());
    }

    char moduleFilename[BUFLEN];
    snprintf(moduleFilename, BUFLEN, "%s\\jvm.dll", revival_dirname);
    SymLoadModuleEx(h2, NULL, moduleFilename, NULL, 0, 0, NULL, 0);

    TCHAR szSymbolName[MAX_SYM_NAME];
    ULONG64 buffer[(sizeof(SYMBOL_INFO) + MAX_SYM_NAME * sizeof(TCHAR) + sizeof(ULONG64) - 1) / sizeof(ULONG64)];
    PSYMBOL_INFO pSymbol = (PSYMBOL_INFO) buffer;
 	pSymbol->SizeOfStruct = sizeof(SYMBOL_INFO);
   	pSymbol->MaxNameLen = MAX_SYM_NAME;
    char buf[MAX_SYM_NAME];

    // Using SymFromName() on jvm.dll after relocation, gets us final absoulute addresses.
    for (int i = 0; i < count; i++) {
        warn("SYM: %d %s", i, symbols[i]);
    	strncpy(szSymbolName, symbols[i], MAX_SYM_NAME);
        unsigned long long address = 0;

      	if (SymFromName(h2, szSymbolName, pSymbol)) {
        	//	warn("SYM: %s = %s %p", (char *) pSymbol->Name, pSymbol->Address);
        	address = pSymbol->Address;
    	} else {
    		warn("SymFromName returned error : %d", GetLastError());
    	}
        snprintf(buf, MAX_SYM_NAME, "%s %llx 0\n", szSymbolName, address);
        write0(fd, buf);
    }
    e = SymCleanup(h2);
    if (e != TRUE) {
    	warn("SymCleanup error: %d", GetLastError());
    }
    e = ImageUnload(image);
    if (e != TRUE) {
    	warn("ImageUnload error : %d", GetLastError());
    }
}


int create_revivalbits_native_pd(const char *corename, const char *javahome, const char *revival_dirname, const char *libdir) {
    char jvm_copy[BUFLEN];

    char *jvm_path = get_jvm_filename_pd(corename);
    if (jvm_path == nullptr) {
        warn("revival: cannot locate JVM in minidump.\n") ;
        return -1;
    }
    logv("JVM = '%s'", jvm_path);
    void *addr = get_jvm_load_adddress_pd(corename);
    logv("JVM addr = %p", addr);

    // Create core.revival dir
    int e = _mkdir(revival_dirname);
    if (e < 0) {
        warn("Cannot create directory: %s: %s\n", revival_dirname, strerror(errno));
        return e;
    }

    // Use libdir if specified
    if (libdir != nullptr) {
        // find jvm.dll in libdir, use that instead of dump's jvm filename
    }

    // Copy jvm.dll into core.revival dir
    memset(jvm_copy, 0, BUFLEN);
    strncpy(jvm_copy, revival_dirname, BUFLEN - 1);
    strncat(jvm_copy, "\\" JVM_FILENAME, BUFLEN - 1);
    e = copy_file_pd(jvm_path, jvm_copy);
    if (e != 0) {
        warn("Cannot copy JVM: %s to  %s\n", jvm_copy, revival_dirname);
        return -1;
    }

    // Relocate copy of libjvm:
    e = relocate_sharedlib_pd(jvm_copy, addr);
    if (e != 0) {
        warn("Failed to relocate JVM: %d\n", e);
        //    return -1; // temp ignore to test mappings when editbin not found...
    }

    // Create symbols file
    int symbols_fd = symbols_file_create(revival_dirname);
    if (symbols_fd < 0) {
        warn("Failed to create mappings file\n");
        return -1;
    }
    logv("Write jvm symbols");
    write_symbols(symbols_fd, JVM_SYMS, N_JVM_SYMS, revival_dirname);
    logv("Write jvm symbols done");
    close(symbols_fd);

    // Create core.mappings file:
    int fd = mappings_file_create(revival_dirname, corename);
    if (fd < 0) {
        warn("Failed to create mappings file\n");
        return -1;
    }

    e = create_mappings_pd(fd, corename, jvm_copy, javahome, addr);

    close(fd);
    if (e != 0) {
        warn("Failed to create memory mappings: %d\n", e);
        return -1;
    }

    // Copy jvm.dll.pdb if present
    char jvm_debuginfo_path[BUFLEN];
    char jvm_debuginfo_copy_path[BUFLEN];
    snprintf(jvm_debuginfo_path, BUFLEN, "%s", jvm_path);
    char *p = strstr(jvm_debuginfo_path, ".dll");
    if (p != nullptr) {
        snprintf(p, BUFLEN, ".dll.pdb");
        warn("DEBUGINFO: '%s'", jvm_debuginfo_path);
        snprintf(jvm_debuginfo_copy_path, BUFLEN - 1, "%s/jvm.dll.pdb", revival_dirname);
        copy_file_pd(jvm_debuginfo_path, jvm_debuginfo_copy_path);
    }

    logv("create_revivalbits_native_pd returning %d", 0);
    return 0;
}

