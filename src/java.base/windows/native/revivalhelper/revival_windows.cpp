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


uint64_t vaddr_align; // set by init_pd

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
    return vaddr_align;
}

uint64_t offset_alignment_pd() {
    return vaddr_align;
}

uint64_t length_alignment_pd() {
    return vaddr_align;
}

unsigned long long max_user_vaddr_pd() {
    return 0x7FFFFFFFFFFF;
}

void init_pd() {
    int x;

    openCoreWrite = true;

    _SYSTEM_INFO systemInfo;
    GetSystemInfo(&systemInfo);
    vaddr_align = systemInfo.dwAllocationGranularity - 1;

    // Report pagesize in verbose log for reference, but not used.
    uint64_t pagesize = systemInfo.dwPageSize;

    logv("revival: init_pd: dwAllocationGranularity = %d  vaddr_alignment_pd() = 0x%lx  approx sp = 0x%llx dwPageSize = %d",
          systemInfo.dwAllocationGranularity, vaddr_alignment_pd(), &x, pagesize);

    if (vaddr_align != 0xffff) {
        // expected: dwAllocationGranularity = 65536
        warn("Note: dwAllocationGranularity not 64k, vaddr_align = %lld", vaddr_align);
    }

    // Function lookups
    install_kernelbase_1803_symbol_or_exit(pVirtualAlloc2, "VirtualAlloc2");
    install_kernelbase_1803_symbol_or_exit(pMapViewOfFile3, "MapViewOfFile3");
}


void normalize_path_pd(char *s) {
    for (char *p = s; *p != '\0'; p++) {
        if (*p == '/') *p = '\\';
    }
}

bool dir_exists_pd(const char *dirname) {
    DWORD attr = GetFileAttributes(dirname);
    return attr != INVALID_FILE_ATTRIBUTES && (attr & FILE_ATTRIBUTE_DIRECTORY);
}

bool dir_isempty_pd(const char *dirname) {
    return PathIsDirectoryEmptyA(dirname);
}

bool file_exists_pd(const char *filename) {
    DWORD attr = GetFileAttributes(filename);
    return attr != INVALID_FILE_ATTRIBUTES;
}

bool file_exists_indir_pd(const char* dirname, const char* filename) {
    char path[BUFLEN];
    snprintf(path, BUFLEN - 1, "%s%s%s", dirname, FILE_SEPARATOR, filename);
    return file_exists_pd(path);
}

int revival_checks_pd(const char *dirname) {
    return 0;
}


// Exception handler:

LPTOP_LEVEL_EXCEPTION_FILTER previousUnhandledExceptionFilter = nullptr;

LONG WINAPI topLevelUnhandledExceptionFilter(struct _EXCEPTION_POINTERS* exceptionInfo) {

//#if defined(_M_ARM64)
//    address pc = (address) exceptionInfo->ContextRecord->Pc;
//#elif defined(_M_AMD64)
#if defined(_M_AMD64)
    address pc = (address) exceptionInfo->ContextRecord->Rip;
#else
    error("revival: handler: unsupported platform");
#endif

    address addr = (address) exceptionInfo->ExceptionRecord->ExceptionInformation[1];
    warn("revival: handler: pc 0x%llx address 0x%llx", pc, addr);

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
    abort(); // not reached
}

void install_handler() {
    previousUnhandledExceptionFilter = SetUnhandledExceptionFilter(topLevelUnhandledExceptionFilter);
}


void tls_fixup_pd(void *old_teb) {
    logv("tls_fixup: given old TEB addr 0x%llx", old_teb);

    // Given we have revived memory, read old TEB address, to find old TLS pointer.
    uint64_t* old_tls = (uint64_t*) ((char*) old_teb + 0x58);
    logv("tls_fixup: old _tls_array = 0x%llx contains 0x%llx", old_tls, *old_tls);

    // TEB pointer on x64 is: __readgsqword(0x30) + 0x58
    uint64_t* new_teb = (uint64_t*) NtCurrentTeb();
    uint64_t* new_tls = (uint64_t*) ((char*) new_teb + 0x58);
    logv("tls_fixup: new teb = 0x%llx", new_teb);
    logv("tls_fixup: new tls = 0x%llx contains 0x%llx", new_tls, *new_tls);

    *new_tls = *old_tls;
    logv("tls_fixup: fixed new tls = 0x%llx contains 0x%llx", new_tls, *new_tls);
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
    address r = (address) do_map_allocate_pd_VirtualAlloc2((void*) vaddr_aligned, length_aligned);

    // Accept the aligned down address and return as if the requested vaddr was honoured.
    if (r == vaddr_aligned) {
        set_prot(vaddr, length);
        return vaddr;
    } else {
        return (void*) r;
    }
}


char* readstring_at_offset_pd(const char* filename, uint64_t offset) {
   int fd = open(filename, O_RDONLY | O_BINARY);
    if (fd < 0) {
        warn("cannot open %s", filename);
        return (char*) -1;
    }
    off_t pos = lseek(fd, (long) offset, SEEK_SET);
    if (pos < 0) {
        warn("readstring_at_pd: %s: lseek(%ld) fails %d : %s", filename, offset, errno, strerror(errno));
        close(fd);
        return (char*) -1;
    }
    char* s = readstring(fd);
    close(fd);
    return s;
}

char* readstring_from_core_at_vaddr_pd(const char* filename, uint64_t addr) {
    MiniDump dump(filename, nullptr);
    return dump.readstring_at_address(addr);
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
    normalize_path_pd(s);
    normalize_path_pd(d);

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


char *editbin = nullptr;

char* check_editbin() {
    char *editbin_env = getenv("EDITBIN");
    if (editbin_env != nullptr) {
        if (!file_exists_pd(editbin_env)) {
            error("EDITBIN from environment does not exist: '%s'", editbin_env);
        }
        logv("Using EDITBIN: '%s'", editbin_env);
        return editbin_env;
    } else {
        return nullptr;
    }
}


int relocate_sharedlib_pd(const char *filename, const void *addr) {

    if (editbin == nullptr) {
        if (!PEFile::relocate(filename, (long long) addr)) {
            return -1;
        }
        if (!PEFile::remove_dynamicbase(filename)) {
            return -1;
        }
        return 0;
    } else {
        // Call: EDITBIN.EXE /DYNAMICBASE:NO /REBASE:BASE=0xaddress filename
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
}


uint64_t resolve_teb(MiniDump* dump) {
    // Find MiniDump ThreadListStream
    // Read _MINIDUMP_THREAD
    // Read TEB
    MINIDUMP_DIRECTORY *md = dump->find_stream(ThreadListStream);
    if (md == nullptr) {
        warn("resolve_teb: MiniDump ThreadListStream not found\n");
        return 0;
    }
    // Read MINIDUMP_THREAD_LIST
    ULONG32 NumberOfThreads;
    int e = read(dump->get_fd(), &NumberOfThreads, sizeof(NumberOfThreads));
    if (e < sizeof(NumberOfThreads)) {
        warn("resolve_teb: read of NumberOfThreads failed");
        free(md);
        return 0;
    }

    MINIDUMP_THREAD thread;
    for (unsigned int i = 0; i < NumberOfThreads; i++) {
        memset(&thread, 0, sizeof(thread));
        e = read(dump->get_fd(), &thread, sizeof(thread));
        if (e < sizeof(thread)) {
            warn("resolve_teb: read of MINIDUMP_THREAD %d failed: %d", i, e);
            free(md);
            return 0;
        }
        logv("resolve_teb: MINIDUMP_THREAD id 0x%lx TEB: 0x%llx", thread.ThreadId, thread.Teb);
        if (thread.Teb != 0) {
            free(md);
            return (uint64_t) thread.Teb;
        }
    }
    free(md);
    return 0;
}


int write_mem_mappings(MiniDump* dump, int fd, const char *corename, const char *jvm_copy,
                       Segment* jvm_data_seg, Segment* jvm_rdata_seg, Segment* jvm_iat_seg) {

    // Read minidump memory list, create text of mappings list.
    // Plan to map data directly from core where possible.
    // If alignment simply does not work (segments too close), create larger mapping and copy bytes.

    // Maintain a list of segments to copy bytes later.
    std::list<Segment> segsToCopy;

    dump->prepare_memory_ranges(); // Get ready to read Segments: locate Memory64ListStream to read all MINIDUMP_MEMORY64_LIST
    RVA64 currentRVA = dump->getBaseRVA(); // current offset in file
    MINIDUMP_MEMORY_DESCRIPTOR64 d;
    ULONG64 prevAddr = 0;

    // Iterate, reading segments from dump.  Consider a current and next segment, so we can check for "too close" addresses.
    Segment* seg = nullptr;
    Segment* segNext = nullptr;

    while (true) {
        if (seg == nullptr || segNext == nullptr) {
            // First iteration, or no segNext waiting:
            seg = dump->readSegment(&d, &currentRVA, true);
        } else {
            // Use a segNext we already read (but did not use):
            seg = segNext;
            segNext = nullptr;
        }

        if (seg == nullptr) {
            break;
        }

        logv("create_mappings_pd: addr 0x%llx size 0x%llx   current RVA/file offset: 0x%llx", d.StartOfMemoryRange, d.DataSize, currentRVA);
        prevAddr = d.StartOfMemoryRange;

        if (!seg->is_relevant()) {
            logv("create_mappings_pd: not relevant: 0x%llx", d.StartOfMemoryRange);
            continue;
        }

        // Consider the next region also:
        segNext = dump->readSegment(&d, &currentRVA, true);

        // Can we coalesce (join) touching regions, if the file offsets work,
        // to reduce number of mappings (there are likely >600).
/*      bool coalesce = false;
        if (coalesce) {
        Segment *joinedSeg = nullptr;
        while (segNext != nullptr && seg->end() == segNext->start()
               && (seg->file_offset + seg->file_length == segNext->file_offset)) {

            logv("create_mappings_pd: join 0x%llx - 0x%llx and 0x%llx - 0x%llx", seg->start(), seg->end(), segNext->start(), segNext->end());
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
            segNext = dump->readSegment(&d, &currentRVA, true);
        }
        if (joinedSeg != nullptr) {
            seg = joinedSeg;  // Which may yet be relevant in (2) below.
            // segNext may still be set, but we seg is not joined to it.
        }
        } */

        // Is next region too close for vaddralignment to work?
        // Grow a bigger segment to map, that will have these neighbouring segments' data copied in.
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
            segNext = dump->readSegment(&d, &currentRVA, true);
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


const int N_JVM_SYMS = 2;
const char *JVM_SYMS[N_JVM_SYMS] = {
    SYM_REVIVE_VM,
    SYM_VM_RELEASE
};

void write_symbols(int fd, const char* symbols[], int count, const char *revival_dirname) {
    // Using SymFromName() on jvm.dll after relocation will give final absoulute addresses.
    PLOADED_IMAGE image = ImageLoad(JVM_FILENAME, revival_dirname);
    if (image == nullptr) {
        error("write_symbols: ImageLoad error '%s': %d", GetLastError());
    }

    HANDLE hCurrentProcess = GetCurrentProcess();
    HANDLE h2 = (HANDLE) 1;
    bool e = SymInitialize(h2, nullptr, false);
    if (e != TRUE) {
        error("write_symbols: SymInitialize error : 0x%lx", GetLastError());
    }

    char moduleFilename[BUFLEN];
    snprintf(moduleFilename, BUFLEN, "%s\\" JVM_FILENAME, revival_dirname);
    SymLoadModuleEx(h2, NULL, moduleFilename, NULL, 0, 0, NULL, 0);

    TCHAR szSymbolName[MAX_SYM_NAME];
    ULONG64 buffer[(sizeof(SYMBOL_INFO) + MAX_SYM_NAME * sizeof(TCHAR) + sizeof(ULONG64) - 1) / sizeof(ULONG64)];
    PSYMBOL_INFO pSymbol = (PSYMBOL_INFO) buffer;
 	pSymbol->SizeOfStruct = sizeof(SYMBOL_INFO);
   	pSymbol->MaxNameLen = MAX_SYM_NAME;
    char buf[MAX_SYM_NAME];

    for (int i = 0; i < count; i++) {
    	strncpy(szSymbolName, symbols[i], MAX_SYM_NAME);
        if (!SymFromName(h2, szSymbolName, pSymbol)) {
            warn("write_symbols: %d: SymFromName '%s' failed, error: %d", i, szSymbolName, GetLastError());
        } else {
            snprintf(buf, BUFLEN, "%s %llx\n", szSymbolName, pSymbol->Address);
            logv("write_symbols: %d: %s", i, buf); // buf includes return char so causes extra newline
            write0(fd, buf);
        }
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

    // Check early for editbin.exe:
    editbin = check_editbin();

    // Using libdir to resolve JVM from alternative path in MiniDump:
    waitHitRet();
    MiniDump dump(corename, libdir);
    if (!dump.is_valid()) {
        warn ("Cannot open MiniDump: '%s'", corename);
        return -1;
    }

    // Find JVM and its load address from core
    // Optionally find all library mappings.
    Segment* jvm_mapping = dump.get_library_mapping(JVM_FILENAME);
    if (jvm_mapping == nullptr) {
        error("revival: cannot locate JVM from core.") ;
    }
    jvm_filename = strdup(jvm_mapping->name);
    jvm_address = (void*) jvm_mapping->vaddr;
    logv("JVM = '%s'", jvm_filename);
    logv("JVM addr = %p", jvm_address);
    if (!file_exists_pd(jvm_filename)) {
        error("No file for JVM '%s'", jvm_filename);
    }

    // jvm_data_segs
    Segment* jvm_data_seg = new Segment();
    Segment* jvm_rdata_seg = new Segment();
    Segment* jvm_iat_seg = new Segment();
    if (!PEFile::find_data_segs(jvm_filename, jvm_address, &jvm_data_seg, &jvm_rdata_seg, &jvm_iat_seg)) {
        error("Failed to find JVM data segments.");
    }
    logv("JVM .rdata SEG: 0x%llx - 0x%llx", jvm_rdata_seg->start(), jvm_rdata_seg->end());
    logv("JVM .data  SEG: 0x%llx - 0x%llx", jvm_data_seg->start(),  jvm_data_seg->end());
    logv("JVM iat    SEG: 0x%llx - 0x%llx", jvm_iat_seg->start(),   jvm_iat_seg->end());
    dump.set_jvm_data(jvm_data_seg, jvm_rdata_seg, jvm_iat_seg);

    // Copy jvm.dll into core.revival dir
    memset(jvm_copy, 0, BUFLEN);
    strncpy(jvm_copy, revival_dirname, BUFLEN - 1);
    strncat(jvm_copy, "\\" JVM_FILENAME, BUFLEN - 1);
    int e = copy_file_pd(jvm_filename, jvm_copy);
    if (e != 0) {
        warn("Failed copying JVM '%s' to '%s'", jvm_filename, jvm_copy);
        return -1;
    }

    // Copy jvm.dll.pdb and .map files if present.
    // (move this before relocate, RebaseImage64 may update the .pdb).
    char jvm_debuginfo_path[BUFLEN];
    char jvm_debuginfo_copy_path[BUFLEN];
    snprintf(jvm_debuginfo_path, BUFLEN, "%s", jvm_filename);
    char *p = strstr(jvm_debuginfo_path, ".dll");
    if (p != nullptr) {
        snprintf(p, BUFLEN, ".dll.pdb");
        if (file_exists_pd(jvm_debuginfo_path)) {
            snprintf(jvm_debuginfo_copy_path, BUFLEN - 1, "%s/" JVM_FILENAME ".pdb", revival_dirname);
            copy_file_pd(jvm_debuginfo_path, jvm_debuginfo_copy_path);
        }
        snprintf(p, BUFLEN, ".dll.map");
        if (file_exists_pd(jvm_debuginfo_path)) {
            snprintf(jvm_debuginfo_copy_path, BUFLEN - 1, "%s/" JVM_FILENAME ".map", revival_dirname);
            copy_file_pd(jvm_debuginfo_path, jvm_debuginfo_copy_path);
        }
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
    logv("Write symbols");
    write_symbols(symbols_fd, JVM_SYMS, N_JVM_SYMS, revival_dirname);
    logv("Write symbols done");
    close(symbols_fd);

    // Create (open) the core.mappings file:
    // Normalize corename, so basename works. XXX
    int fd = mappings_file_create(revival_dirname, corename);
    if (fd < 0) {
        error("Failed to create mappings file.");
    }
    // Write memory mappings into the file:
    e = write_mem_mappings(&dump, fd, corename, jvm_copy, jvm_data_seg, jvm_rdata_seg, jvm_iat_seg);

    close(fd);
    if (e != 0) {
        error("Failed to create memory mappings: %d", e);
    }

    logv("create_revivalbits_native_pd returning %d", 0);
    return 0;
}
