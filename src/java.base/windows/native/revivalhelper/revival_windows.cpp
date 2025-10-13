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


uint64_t valign;
uint64_t pagesize;

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
    openCoreWrite = true;

    _SYSTEM_INFO systemInfo;
    GetSystemInfo(&systemInfo);
    valign = systemInfo.dwAllocationGranularity - 1;
    pagesize = systemInfo.dwPageSize;

    if (verbose) {
        int x;
        logv("revival: init_pd: dwPageSize = %d  dwAllocationGranularity = %d  vaddr_alignment_pd() = 0x%lx  approx sp = 0x%llx",
              pagesize, systemInfo.dwAllocationGranularity, vaddr_alignment_pd(), &x);
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


// Exception handler:

LPTOP_LEVEL_EXCEPTION_FILTER previousUnhandledExceptionFilter = nullptr;

LONG WINAPI topLevelUnhandledExceptionFilter(struct _EXCEPTION_POINTERS* exceptionInfo) {

#if defined(_M_ARM64)
    address pc = (address) exceptionInfo->ContextRecord->Pc;
#elif defined(_M_AMD64)
    address pc = (address) exceptionInfo->ContextRecord->Rip;
#else
    error("revival: handler: unsupported platform");
#endif

    address addr = (address) exceptionInfo->ExceptionRecord->ExceptionInformation[1];

    warn("revival: handler: pc 0x%llx address 0x%llx", pc, addr);
    if (addr == (address) nullptr) {
        warn("handler: null address");
        abort();
    }

    // Catch access to areas we failed to map:
    std::list<Segment>::iterator iter;
    for (iter = failedSegments.begin(); iter != failedSegments.end(); iter++) {
        if (addr >= (address) iter->vaddr &&
                (unsigned long long) addr < (unsigned long long) (iter->vaddr) + (unsigned long long)(iter->length) ) {
            warn("Access to segment that failed to revive: si_addr = %p in failed segment %p", addr, iter->vaddr);
            exitForRetry();
        }
    }

    waitHitRet();
    exitForRetry();
    abort();
}

void install_handler() {
    previousUnhandledExceptionFilter = SetUnhandledExceptionFilter(topLevelUnhandledExceptionFilter);
}


void tls_fixup_pd(void *old_teb) {
    // Given we have revived memory, can read old TEB address, to find old TLS pointer.
    logv("tls_fixup: old TEB addr 0x%llx", old_teb);

    if (verbose) {
//        printTLS();
    }

    uint64_t* old_tls = (uint64_t*) ((char*) old_teb + 0x58);
    logv("tls_fixup: old _tls_array = 0x%llx contains 0x%llx", old_tls, *old_tls);

    // TEB pointer on x64 is: __readgsqword(0x30) + 0x58
    //
    uint64_t* new_teb = (uint64_t*) NtCurrentTeb();
    uint64_t* new_tls = (uint64_t*) ((char*) new_teb + 0x58);
    logv("tls_fixup: new teb = 0x%llx", new_teb);
    logv("tls_fixup: new tls = 0x%llx contains 0x%llx", new_tls, *new_tls);

    *new_tls = *old_tls;
    logv("tls_fixup: fixed new tls = 0x%llx contains 0x%llx", new_tls, *new_tls);

    if (verbose) {
//        printTLS();
     } 
}


// Utils...

void printMemBasicInfo(MEMORY_BASIC_INFORMATION meminfo) {

    uint64_t end = (uint64_t) meminfo.BaseAddress + meminfo.RegionSize;

    fprintf(stderr, "AllocBase: 0x%016llx   Base: 0x%016llx - 0x%016llx len 0x%08llx  AllocProt: 0x%08lx Prot: 0x%08lx\n",
            (uint64_t) meminfo.AllocationBase,
            (uint64_t) meminfo.BaseAddress, end, (uint64_t) meminfo.RegionSize, meminfo.AllocationProtect, meminfo.Protect);

}

void printMemBasicInfo(void* addr) {
    HANDLE hProc = GetCurrentProcess();
    MEMORY_BASIC_INFORMATION meminfo;
    size_t q = VirtualQueryEx(hProc, (PVOID) addr, &meminfo, sizeof(meminfo));
    if (q == sizeof(meminfo)) {
        printMemBasicInfo(meminfo);
    }
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
    int max_tries = 1; // Retrying, even when allocating to force a new address, is not usually succesfull.

    for (int i = 0; i < max_tries; i++) {
        HMODULE h = LoadLibraryA(name);
        if ((void*) h == vaddr) {
            return (void*) h; // success
        }
        warn("load_sharedobject_pd: %s: load failed 0x%p != requested 0x%p. error=0x%lx", name, h, vaddr, GetLastError());
        if (h != nullptr) {
            // Loaded, wrong address.
            exitForRetry(); // or just fatal
            // Alterntatively:
            int unloaded = FreeLibrary(h);
            if (unloaded == 0) {
                warn("load_sharedobject_pd: unload failed.");
                break;
            }
            // If we want to try re-trying, should map some memory at the address we just saw.
            // void* addr = do_map_allocate_pd((void*) h, 1024 * 1024);
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

void set_prot(void* addr, uint64_t length) {
    //DWORD prot = PAGE_READWRITE;
    DWORD prot = PAGE_EXECUTE_READWRITE;

    // Likely we can't set protection of some sub-range we are given, but can find the containing MEMORY_BASIC_INFORMATION
    // and set protection of that.

    DWORD lpfOldProtect;
    if (!VirtualProtect((PVOID) addr, length, prot, &lpfOldProtect)) {
        logv("    set_prot: failed setting rw (0x%lx) for: 0x%p, len 0x%lx: error 0x%x.",  prot, addr, length, GetLastError());
        if (verbose) {
            fprintf(stderr, "    ");
            printMemBasicInfo(addr);
        }

        HANDLE hProc = GetCurrentProcess();
        MEMORY_BASIC_INFORMATION meminfo;
        size_t q = VirtualQueryEx(hProc, (PVOID) addr, &meminfo, sizeof(meminfo));
        if (q != sizeof(meminfo)) {
            warn("set_prot: VirtualQueryEx failed: returned 0x%x, error 0x%x", q, GetLastError());
        } else {
            if (!VirtualProtect((PVOID) meminfo.AllocationBase, meminfo.RegionSize, prot, &lpfOldProtect)) {
                warn("        set_prot: failed setting rw (0x%lx) for: 0x%p, len 0x%lx: error 0x%x.",
                    prot, meminfo.AllocationBase, meminfo.RegionSize, GetLastError());
            } else {
                logv("        set_prot: OK setting rw (0x%lx) for: 0x%p, len 0x%lx",
                    prot, meminfo.AllocationBase, meminfo.RegionSize);

            }
        }
    }
}

bool mem_canwrite_pd(void *vaddr, size_t length) {
    MEMORY_BASIC_INFORMATION meminfo;
    HANDLE hProc = GetCurrentProcess();
    size_t q = VirtualQueryEx(hProc, vaddr, &meminfo, sizeof(meminfo));


    if (q == sizeof(meminfo)) {
        if (meminfo.Protect == PAGE_EXECUTE_READWRITE
            || meminfo.Protect == PAGE_EXECUTE_WRITECOPY
            || meminfo.Protect == PAGE_READWRITE
            || meminfo.Protect == PAGE_WRITECOPY) {
            //logv("    mem_canwrite_pd: %p protect: 0x%lx: YES", vaddr, meminfo.Protect);
            return true;
        } else {
            warn("    mem_canwrite_pd: %p protect: 0x%lx: NO", vaddr, meminfo.Protect);
            fprintf(stderr, "    ");
            printMemBasicInfo(meminfo);
            set_prot(vaddr, length);
            return false;
        }
    } else {
        logv("    mem_canwrite_pd: %p VirtualQueryEx failed: NO", vaddr);
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
void *do_mmap_pd(void *addr, size_t length, char *filename, int fd, size_t offset) {
    // TODO test FILE_MAP_COPY (COW)

    // Fail quickly if unaligned:
    uint64_t offsetAligned = align_down(offset, vaddr_alignment_pd());
    if (offsetAligned != offset) {
        logv("do_mmap_pd: address 0x%llx file offset 0x%llx not aligned, do not try mapping directly, return", addr, offset);
        return (void *) -1;
    }

    LPVOID p = nullptr;
    HANDLE h;
    HANDLE h2;
    DWORD createFileDesiredAccess = GENERIC_READ | GENERIC_EXECUTE;
    DWORD mappingProt = PAGE_EXECUTE_READ;
    DWORD mapViewAccess = FILE_MAP_READ | FILE_MAP_EXECUTE;
      if (openCoreWrite) {
        createFileDesiredAccess |= GENERIC_WRITE;
        mappingProt |= PAGE_READWRITE;
        }
    h = CreateFile(filename, createFileDesiredAccess, FILE_SHARE_READ, nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (h == nullptr) {
        logv("    do_mmap_pd: CreateFile failed: %s: 0x%lx", filename, GetLastError());
    } else {
        h2 = CreateFileMapping(h, nullptr, mappingProt, 0, 0, nullptr);
        if (h2 == nullptr) {
            logv("    do_mmap_pd: CreateFileMapping failed: %s: 0x%lx", filename, GetLastError());
            return (void *) -1;
        }
    }
    // Align virtual address:
    address addr_aligned = align_down((uint64_t) addr, vaddr_alignment_pd());
    // If vaddr changed, update offset:
    if (addr_aligned != (address) addr) {
        offset -= (size_t) ((address) addr - addr_aligned);
        // But offset must be multiple of allocation granularity
        if (offset != align_down(offset, vaddr_alignment_pd())) {
            logv("    do_mmap_pd: file offset becomes unalinged.");
        }
    }
    logv("  do_mmap_pd: will map: addr 0x%p length 0x%llx file offset 0x%llx -> offset aligned 0x%llx",
         addr, (unsigned long) length, (unsigned long) offset, (unsigned long) offsetAligned);

    HANDLE hProc = GetCurrentProcess();
    DWORD prot = PAGE_EXECUTE_READ;
    //DWORD prot = PAGE_EXECUTE_READWRITE;
    p = pMapViewOfFile3(h2, hProc, (PVOID) addr, offset, length, MEM_REPLACE_PLACEHOLDER, prot, nullptr, 0);
    if ((address) p != (address) addr) {
        logv("    do_mmap_pd: MapViewOfFile3 0x%p failed, ret=0x%p error=0x%lx", addr, p, GetLastError());
        p = (void *) -1;
        waitHitRet();
    }
    CloseHandle(h2);
    return (void *) p;
}


void *do_mmap_pd(void *addr, size_t length, size_t offset) {
    return do_mmap_pd(addr, length, core_filename, -1, offset);
}


int do_munmap_pd(void *addr, size_t length) {
    int e = UnmapViewOfFile(addr); // Returns non-zero on success.  Zero on failure.
    if (e == 0) {
        warn("UnmapViewOfFile 0x%p: failed: returns 0x%d: 0x%lx", addr, e, GetLastError());
    }
    return e;
}


void *do_map_allocate_pd_MapViewOfFile(void *vaddr, size_t length) {
    DWORD mappingProt = PAGE_EXECUTE_READWRITE;
    DWORD mapViewAccess = FILE_MAP_READ | FILE_MAP_WRITE | FILE_MAP_EXECUTE;

    HANDLE h = CreateFileMapping(INVALID_HANDLE_VALUE, nullptr, mappingProt, 0, (DWORD) length, nullptr);
    if (h == nullptr) {
        warn("    do_map_allocate_pd_MapViewOfFile: CreateFileMapping returns = 0x%p : error = 0x%lx", h, GetLastError());
        return (void *) -1;
    }

    LPVOID p = MapViewOfFileEx(h, mapViewAccess, 0, 0, length, (void *) vaddr);

    if ((void*) p == vaddr) {
        logv("do_map_allocate_pd: MapViewOfFile 0x%llx 0x%llx OK", (unsigned long long) vaddr, length);
        return vaddr;
    }

    logv("do_map_allocate_pd: MapViewOfFile 0x%llx 0x%llx bad, gets 0x%llx", (unsigned long long) vaddr, length, (unsigned long long) p);

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
    HANDLE hProc = GetCurrentProcess();
    //DWORD prot = PAGE_READWRITE;
    DWORD prot = PAGE_EXECUTE_READWRITE;

    void* p = pVirtualAlloc2(hProc, (PVOID) addr, length, MEM_RESERVE | MEM_COMMIT, prot, nullptr, 0);
    logv("    do_map_allocate_pd_VirtualAlloc2: first alloc attempt 0x%p len 0x%zx : returns = 0x%p, error = 0x%lx",
         (void *) addr, length, p, GetLastError());

        MEMORY_BASIC_INFORMATION meminfo;
        size_t q = VirtualQueryEx(hProc, addr, &meminfo, sizeof(meminfo));
        if (q != sizeof(meminfo)) {
            warn("VirtualQueryEx failed");
            return (void*) -1;
        }
        uint64_t requested_end = (uint64_t) addr + (uint64_t) length;
        uint64_t existing_end = (uint64_t) meminfo.BaseAddress + (uint64_t) meminfo.RegionSize;
        uint64_t remaining = requested_end - existing_end;
        logv("    do_map_allocate_pd_VirtualAlloc2: meminfo: base 0x%llx len 0x%llx end: 0x%llx",
             meminfo.BaseAddress, meminfo.RegionSize, existing_end);

    if ((void*) p != (void*) addr) {
        // Did not get requested address
        if (GetLastError() == 0) {
            // No error, but requested address was re-aligned.
            //  e.g. first alloc attempt 0x000000E69850B000 len 0x5000 : returns = 0x000000E698500000, error = 0x0
            // If all already mapped, just return as if alloc worked.

            if (p <= addr && requested_end <= existing_end) {
                logv("do_map_allocate: requested 0x%llx got 0x%lx contains all needed", addr, p);
                return addr;
            }

            // Expand allocation?
            logv("    do_map_allocate_pd_VirtualAlloc2: clash, retry new base 0x%llx len 0x%llx", existing_end, remaining);
            void * r = do_map_allocate_pd_VirtualAlloc2((void*) existing_end, remaining);
            // Return original requested address on success:
            if ((uint64_t) r == (uint64_t) existing_end) {
                return addr;
            } else {
                return r;
            }

        } else if (GetLastError() == ERROR_INVALID_ADDRESS) { // 0x1e7
            logv("do_map_allocate: requested 0x%llx got 0x%lx not valid, already mapped?", addr, p);
            // Already mapped, conflict?
            // Return success to proceed with copy.
            logv("    VirtualQueryEx: 1 base 0x%llx len 0x%llx allocationprotect: 0x%lx protect: 0x%lx",
                 (uint64_t) meminfo.BaseAddress, (uint64_t) meminfo.RegionSize, meminfo.AllocationProtect, meminfo.Protect);

            // Is more allocation needed?
            uint64_t wanted_end = (uint64_t) addr + (uint64_t) length;
            if (wanted_end <= existing_end) {
                logv("    do_map_allocate_pd: mapping covered by existing_end at 0x%llx", existing_end);
                return addr;
            } else {
                size_t remaining = (uint64_t) wanted_end - existing_end;
                logv("    do_map_allocate_pd: existing. remaining = 0x%llx", remaining);
                void * r = do_map_allocate_pd_VirtualAlloc2((void*) existing_end, remaining);
                // Return original requested address on success:
                if ((uint64_t) r == (uint64_t) existing_end) {
                    return addr;
                } else {
                  return r;
                }
            }
        } else {
            logv("do_map_allocate: failed");
        }
    }
    return p;
}


void *do_map_allocate_pd(void *vaddr, size_t length) {

    // mappings file is created with minidump addresses, not necessarily 64k aligned.

    address vaddr_aligned = align_down((uint64_t) vaddr, vaddr_alignment_pd());
    uint64_t diff = (uint64_t) vaddr -  (uint64_t) vaddr_aligned;
    size_t length_aligned = length + diff;
    length_aligned = align_up(length_aligned, vaddr_alignment_pd());

    if (vaddr_aligned != (address) vaddr) {
        logv("    do_map_allocate_pd: vaddr 0x%p aligns -> 0x%p  len 0x%p adjusts -> 0x%p",
            (void*) vaddr, (void*) vaddr_aligned, length, length_aligned);
    }

    // address r = (address) do_map_allocate_pd_MapViewOfFile(vaddr_aligned, length_aligned);
    address r = (address) do_map_allocate_pd_VirtualAlloc2((void*) vaddr_aligned, length_aligned);

    // Accept the aligned down address and return as if the requested vaddr was honoured.
    if (r == vaddr_aligned) {
        set_prot(vaddr, length);
        return vaddr;
    } else {
        return (void*) r;
    }
}


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

MiniDump::MiniDump(const char* filename) {
    open(filename);
    if (is_valid()) {
        read_modules();
        logv("MiniDump: NumberOfStreams = %d StreamDirectoryRva = %d", hdr.NumberOfStreams, hdr.StreamDirectoryRva);
    }
}

MiniDump *dump = nullptr;

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
        warn("data_section image: %s vaddr 0x%llx size 0x%llx", image->Sections[i].Name, image->Sections[i].VirtualAddress,
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
        warn("data_section image: %s vaddr 0x%llx size 0x%llx", image->Sections[i].Name, image->Sections[i].VirtualAddress,
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
            //jvm_data_seg = new Segment((void *) ((DWORD_PTR) image->Sections[i].VirtualAddress + (DWORD_PTR) 0x706a8), (size_t) image->Sections[i].SizeOfRawData, 0, 0); 
            continue;
        }
        if (jvm_data_seg != nullptr) {
            // Already read and set Seg, use this section as the end of that Seg.
            jvm_data_seg->set_length(image->Sections[i].VirtualAddress - jvm_data_seg->start());

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



void normalize(char *s) {
    for (char *p = s; *p != '\0'; p++) {
        if (*p == '/') *p = '\\';
    }
}

int copy_file_pd(const char *srcfile, const char *destfile) {
    char command[BUFLEN];
    memset(command, 0, BUFLEN);

    // Normalise paths
    char* s = (char *) malloc(strlen(srcfile) + 1); // or strdup
    char* d = (char *) malloc(strlen(destfile) + 1);
    if (s == nullptr || d == nullptr) {
        error("allocation failed normalizing paths to copy");
    }
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
        error("EDITBIN must be set in environment.");
    }
    // EDITBIN.EXE /DYNAMICBASE:NO /REBASE:BASE=0xaddress filename
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
    logv("relocate_sharedlib_pd: '%s' returns %d", command, e);
    return e;
}

/**
 * Prepare for caller to read memory ranges.
 *
 * Leaves dump fd positioned to read the array of MINIDUMP_MEMORY_DESCRIPTOR64
 * by calling readSegment().
 */
void MiniDump::prepare_memory_ranges() {
    MINIDUMP_DIRECTORY *md = dump->find_stream(Memory64ListStream);
    if (md == nullptr) {
        error("Minidump Memory64ListStream not found.");
    }
    int e = read(dump->get_fd(), &NumberOfMemoryRanges, sizeof(NumberOfMemoryRanges));
    e = read(dump->get_fd(), &BaseRVA, sizeof(BaseRVA));
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
                char *b = seg->toString();
                warn("XXX readSegment0 range %d new seg = %s", rangesRead, b);
                free(b);
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


uint64_t resolve_teb(MiniDump* dump) {
    // Find Minidump ThreadListStream
    // Read _MINIDUMP_THREAD
    // Read TEB
    MINIDUMP_DIRECTORY *md = dump->find_stream(ThreadListStream);
    if (md == nullptr) {
        warn("resolve_teb: Minidump ThreadListStream not found\n");
        return 0;
    }
    // Read MINIDUMP_THREAD_LIST
    ULONG32 NumberOfThreads;
    int e = read(dump->get_fd(), &NumberOfThreads, sizeof(NumberOfThreads));
    if (e < sizeof(NumberOfThreads)) {
        warn("resolve_teb: read of NumberOfThreads failed");
        return 0;
    }

    MINIDUMP_THREAD thread;
    for (unsigned int i = 0; i < NumberOfThreads; i++) {
        memset(&thread, 0, sizeof(thread));
        e = read(dump->get_fd(), &thread, sizeof(thread));
        if (e < sizeof(thread)) {
            warn("resolve_teb: read of MINIDUMP_THREAD %d failed: %d", i, e);
            return 0;
        }
        logv("resolve_teb: MINIDUMP_THREAD id 0x%lx TEB: 0x%llx", thread.ThreadId, thread.Teb);
        if (thread.Teb != 0) {
            return (uint64_t) thread.Teb;
        }
    }
    return 0;
}


int create_mappings_pd(int fd, const char *corename, const char *jvm_copy, const char *javahome, void *addr) {

    // Read minidump memory list, create text of mappings list.
    // Plan to map data directly from core where possible.
    // If alignment simply does not work (segments too close), create larger mapping and copy bytes.

    if (dump == nullptr || !dump->is_valid()) {
        error("MiniDump not initialized");
    }

    // Maintain a list of segments to copy bytes later.
    std::list<Segment> segsToCopy;

    dump->prepare_memory_ranges();

    // Read MINIDUMP_MEMORY64_LIST 
    RVA64 currentRVA = dump->getBaseRVA(); // current offset in file
    MINIDUMP_MEMORY_DESCRIPTOR64 d;
    ULONG64 prevAddr = 0;

    // Iterate, reading segments from dump, considering a current and next segment, so we can check for "too close" addresses.
    Segment *seg = dump->readSegment(&d, &currentRVA);
    Segment *segNext = nullptr;

    while (true) {
        // Use a segNext we already read (but did not use), or read a new Segment:
        if (segNext != nullptr) {
            seg = segNext;
            segNext = nullptr;
        } else {
            seg = dump->readSegment(&d, &currentRVA);
            if (seg == nullptr) {
                break;
            }
        }

/*        if (d.StartOfMemoryRange == prevAddr) {
            logv("create_mappings: skipping due to repetition, 0x%llx", prevAddr);
            continue; 
        } */

        logv("create_mappings_pd: addr 0x%llx size 0x%llx   current RVA/file offset: 0x%llx", d.StartOfMemoryRange, d.DataSize, currentRVA);
        prevAddr = d.StartOfMemoryRange;

        if (!seg->is_relevant()) {
            logv("create_mappings_pd: not relevant: 0x%llx", d.StartOfMemoryRange);
            continue;
        }

        // Consider the next region also:
        segNext = dump->readSegment(&d, &currentRVA);

        // (1) Can we coalesce (join) touching regions, if the file offsets work,
        //     to reduce number of mappings (there are likely >600).
        bool coalesce = false; // not currently using this..
        if (coalesce) {
        Segment *joinedSeg = nullptr;
        while (segNext != nullptr && seg->end() == segNext->start()
               && (seg->file_offset + seg->file_length == segNext->file_offset)) {

            logv("XXXX join 0x%llx - 0x%llx and 0x%llx - 0x%llx", seg->start(), seg->end(), segNext->start(), segNext->end());
            if (joinedSeg == nullptr) {
                joinedSeg = new Segment(seg);
            }
            joinedSeg->set_length(seg->length + segNext->length);
            if (verbose) {
                char *b = joinedSeg->toString();
                warn("JOINED seg expanded: %s", b);
                free(b);
            }

            seg = joinedSeg;
            segNext = dump->readSegment(&d, &currentRVA);
        }
        if (joinedSeg != nullptr) {
            seg = joinedSeg;  // Which may yet be relevant in (2) below.
            // segNext may still be set, but we seg is not joined to it.
        }
        }

        // (2) Is next region too close for vaddralignment to work?
        //     Grow a bigger segment to map, that will have these neighbouring segments' data copied in.
        Segment *biggerSeg = nullptr;
        while (segNext != nullptr && align_up(seg->end(), vaddr_alignment_pd()) >= segNext->start()) {
            if (verbose) {
                warn("create_mappings: segs too close for alignment, seg: %p - %p next seg: %p", seg->vaddr, seg->end(), segNext->vaddr);
                char *b = seg->toString();
                warn("later seg    : %s", b);
                free(b);
                b = segNext->toString();
                warn("later segNext: %s", segNext->toString());
                free(b);
            }
            // Save segs, will write "C" copy lines later.
            if (biggerSeg == nullptr) {
                segsToCopy.push_back(seg);    // Write first seg only on first time through this loop
                biggerSeg = new Segment(seg); // Start with copy of seg info.
            }
            segsToCopy.push_back(segNext);      // Write segNext on all iterations

            biggerSeg->set_end(segNext->end());  // Expand to cover both.
            if (verbose) {
                char *b = biggerSeg->toString();
                warn("BIGGER seg expanded: %s", b);
                free(b);
            }
            // Next...
            seg = segNext;
            segNext = dump->readSegment(&d, &currentRVA);
        }

        // Write line to mappings file.
        // Use the biggerSeg if we were in the above loop.
        int e = 0;
        if (biggerSeg != nullptr) {
            if (verbose) {
                char *b = biggerSeg->toString();
                warn("write BIGGER seg    : %s", b);
                free(b);
            }
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

    // Windows TEB: used to setup TLS on revival.
    // Could this be not necessary, with JVM cooperation?
    uint64_t tls = resolve_teb(dump);
    if (tls != 0) {
        writef(fd, "TEB %llx\n", tls);
    } else {
        warn("TEB not resolved");
    }

    writef(fd, "\n");
    return 0;
}


const int N_JVM_SYMS = 1;
const char *JVM_SYMS[N_JVM_SYMS] = {
    SYM_REVIVE_VM
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
        snprintf(buf, MAX_SYM_NAME, "%s %llx\n", szSymbolName, address);
        logv("SYM: %s", buf);
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

bool create_directory_pd(char* dirname) {
    return _mkdir(dirname) == 0;
}


int create_revivalbits_native_pd(const char* corename, const char* javahome, const char* revival_dirname, const char* libdir) {
    char jvm_copy[BUFLEN];

    dump = new MiniDump(corename);
    if (!dump->is_valid()) {
        warn ("Cannot open MiniDump: '%s'", corename);
        delete dump;
        return -1;
    }

    // Use libdir if specified
    if (libdir != nullptr) {
        init_jvm_filename_from_libdir(libdir); // Possibly set jvm_filename from libdir
    }
    if (jvm_filename == nullptr) {
        jvm_filename = strdup(dump->get_jvm_filename()); // Use MiniDump jvm filename
    }
    if (jvm_filename == nullptr) {
        error("revival: cannot locate JVM in minidump.") ;
    }
    logv("JVM = '%s'", jvm_filename);
    jvm_address = dump->get_jvm_address(); // JVM address is from dump, even if file path is not
    if (jvm_address == nullptr) {
        error("revival: cannot find address for JVM in minidump.") ;
    }
    logv("JVM addr = %p", jvm_address);

    // Copy jvm.dll into core.revival dir
    memset(jvm_copy, 0, BUFLEN);
    strncpy(jvm_copy, revival_dirname, BUFLEN - 1);
    strncat(jvm_copy, "\\" JVM_FILENAME, BUFLEN - 1);
    int e = copy_file_pd(jvm_filename, jvm_copy);
    if (e != 0) {
        warn("Failed copying JVM '%s' to '%s'", jvm_filename, jvm_copy);
        return -1;
    }

    // Relocate copy of libjvm:
    e = relocate_sharedlib_pd(jvm_copy, jvm_address);
    if (e != 0) {
        error("Failed to relocate JVM: %d", e);
    }

    // Create symbols file
    int symbols_fd = symbols_file_create(revival_dirname);
    if (symbols_fd < 0) {
        error("Failed to create mappings file");
    }
    logv("Write jvm symbols");
    write_symbols(symbols_fd, JVM_SYMS, N_JVM_SYMS, revival_dirname);
    logv("Write jvm symbols done");
    close(symbols_fd);

    // Create core.mappings file:
    // Normalize corename, so basename works. XXX
    int fd = mappings_file_create(revival_dirname, corename);
    if (fd < 0) {
        error("Failed to create mappings file");
    }

    e = create_mappings_pd(fd, corename, jvm_copy, javahome, jvm_address);

    close(fd);
    if (e != 0) {
        error("Failed to create memory mappings: %d", e);
    }

    // Copy jvm.dll.pdb if present
    char jvm_debuginfo_path[BUFLEN];
    char jvm_debuginfo_copy_path[BUFLEN];
    snprintf(jvm_debuginfo_path, BUFLEN, "%s", jvm_filename);
    char *p = strstr(jvm_debuginfo_path, ".dll");
    if (p != nullptr) {
        snprintf(p, BUFLEN, ".dll.pdb");
        snprintf(jvm_debuginfo_copy_path, BUFLEN - 1, "%s/jvm.dll.pdb", revival_dirname);
        copy_file_pd(jvm_debuginfo_path, jvm_debuginfo_copy_path);
    }

    logv("create_revivalbits_native_pd returning %d", 0);
    return 0;
}

