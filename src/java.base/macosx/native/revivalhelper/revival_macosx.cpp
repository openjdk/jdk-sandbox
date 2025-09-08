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

#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <dlfcn.h>
#include <pthread.h>
#include <signal.h>
#include <time.h>
#include <unistd.h>
#include <libgen.h>
#include <sys/mman.h>
#include <sys/time.h>
#include <sys/stat.h>

// mach thread status for arm_thread_state64_get_pc
// should ifdef arm
#include <mach/machine/_structs.h>
#include <mach/arm/_structs.h>
#include <mach/arm/thread_status.h>

#include <list>


#include "revival.hpp"

const char *corePageFilename;


uint64_t vaddr_alignment_pd() {
    return 0xfff;
}

uint64_t offset_alignment_pd() {
    // pagesize, e.g. 0x1000
    long value = sysconf(_SC_PAGESIZE);
    if (value == -1) {
        value = 0x1000;
    }
    return value;
}

uint64_t length_alignment_pd() {
    return 0xfff;
}

unsigned long long max_user_vaddr_pd() {
    return 0x0; // update when known
}

void init_pd() {

}


bool revival_direxists_pd(const char *dirname) {
    int fd = open(dirname, O_DIRECTORY);
    if (fd < 0) {
        if (errno != ENOENT) {
            printf("checking revivaldirectory '%s': %d: %s\n", dirname, errno, strerror(errno));
        }
    } else {
        close(fd);
        return true;
    }
    return false;
}

int flags = MAP_SHARED | MAP_PRIVATE | MAP_FIXED;

bool mem_canwrite_pd(void *vaddr, size_t length) {
    return true;
}

void *do_mmap_pd(void *addr, size_t length, char *filename, int fd, off_t offset) {
    fprintf(stderr, ">>> do_mmap_pd(%p, %zu, %s, %d, %lld)\n", addr, length, filename, fd, offset);
    int prot = PROT_READ | PROT_EXEC;
    if (openCoreWrite) {
        prot |= PROT_WRITE;
    }
    // Try with literal values.  Should work for a regular Linux core file.
    void *e = mmap(addr, length, prot, flags, fd, offset);
    if (e == (void*) -1L) {
        if (errno == EINVAL) {
            // EINVAL is likely on a Linux gcore (gdb) due to unaligned file offsets.
            // mmap requires offset to be a multiple of pagesize, retry with aligned offset.
            if (verbose) printf("do_mmap_pd: 1 mmap(%p, %zu, %d, %d, %d, offset %lld) EINVAL\n", addr, length, prot, flags, fd, offset);

            long align_mask = offset_alignment_pd() - 1;
            off_t offset_aligned = align_down((uint64_t) offset, align_mask);
            size_t shift = (offset - offset_aligned);
            size_t length_aligned = length + shift;
            void *addr_aligned = (void*) (((unsigned long long) addr) - shift);
            if (verbose) printf(" offset_alignment = %p offset = %lld offset aligned = %lld shift = %zu new length = %zu new addr = %p\n",
                    (void*) align_mask, offset, offset_aligned, shift, length_aligned, addr_aligned);
            e = mmap(addr_aligned, length_aligned, prot, flags, fd, offset_aligned);

            if (e == (void*) -1L) {
                if (errno == EINVAL) {
                    // But the above made the address badly aligned...  Will need to allocate and copy data.
                    if (verbose) printf("do_mmap_pd: 2 mmap(%p, %zu, %d, %d, %d, offset %lld) EINVAL\n", addr_aligned, length_aligned, prot, flags, fd, offset_aligned);
                    int e2 = revival_mapping_copy(addr, length, offset, true, filename, fd);
                    if (e2 == -1L) {
                        printf("do_mmap_pd called revival_mapping_copy and failed: %d\n", e2);
                        e = (void*) -1L;
                    } else {
                        e = addr; // meaning ok, mapped at that address
                    }
                }
            }
        }
    }
    if (e == (void*) -1L) {
        printf("do_mmap_pd: mmap(%p, %zu, %d, %d, %d, offset %lld) failed: returns: %p: errno = %d: %s\n",
                addr, length, prot, flags, fd, offset, e, errno, strerror(errno));
    }
    return e;
}

void *do_mmap_pd(void *addr, size_t length, off_t offset) {
    return do_mmap_pd(addr, length, NULL, core_fd, offset);
}

int do_munmap_pd(void *addr, size_t length) {
    int e = munmap(addr, length);
    if (e) {
        printf("munmap_pd: %p failed: returns: %d: errno = %d: %s\n",  addr, e, errno, strerror(errno));
    }
    return e;
}

/**
 * Create a memory mapping at a given address, length.
 */
void *do_map_allocate_pd(void *vaddr, size_t length) {
    fprintf(stderr, ">>> do_map_allocate_pd(%p, %zu)\n", vaddr, length);
    int prot = PROT_READ | PROT_WRITE;
    int flags = MAP_ANONYMOUS | MAP_PRIVATE | MAP_FIXED;
    int fd = -1;
    size_t offset = 0;
    void *h = mmap(vaddr, length, prot, flags, fd, offset);
    if (h == (void *) -1) {
        printf("do_map_allocate_pd: mmap(%p, %zu, %d, %d, %d, %zu) failed: returns: %p: errno = %d: %s\n",
                vaddr, length, prot, flags, fd, offset, h, errno, strerror(errno));
        return (void *) -1;
    }
    return h;
}


int revival_checks_pd(const char *dirname) {
    fprintf(stderr, ">>> revival_checks_pd(%s)\n", dirname);
    // Check dirname is valid:
    int e = open(dirname, O_DIRECTORY);
    if (e < 0) {
        printf("revival_checks_pd: cannot open directory '%s'.\n", dirname);
        fprintf(stderr, ">>> revival_checks_pd FAIL 1\n");
        return -1;
    }
    fprintf(stderr, ">>> revival_checks_pd PASSS\n");
    return 0;
}

void pmap_pd() {
    char buf[BUFLEN];
    pid_t pid = getpid();
    snprintf(buf, BUFLEN, "pmap %d", pid);
    int e = system(buf);
    if (e != 0) {
        printf("pmap: %d\n", e);
    }
}

void * symbol_dynamiclookup_pd(void *h, const char *str) {
    fprintf(stderr, ">>> symbol_dynamiclookup_pd(%p, %s)\n", h, str);
    void * s = dlsym(RTLD_NEXT, str);
    if (verbose) {
        printf("symbol_dynamiclookup_pd: %s = %p \n", str, s);
    }
    if (s == 0) {
        if (verbose) {
            printf("dlsym: %s\n", dlerror());
        }
        return (void *) -1;
    }
    return s;
}


void *safefetch32_fault_pc;
void *safefetch32_continuation_pc;
void *safefetchN_fault_pc;
void *safefetchN_continuation_pc;

/*
 * is_safefetch_fault
 *
 * An implementation (copy) of StubRoutines::is_safefetch_fault(pc))
 * We could just call continuation_for_safefetch_fault and check it returns a non-null address.
 */
bool is_safefetch_fault(void *pc) {
    return pc != NULL &&
        (pc == safefetch32_fault_pc ||
         pc == safefetchN_fault_pc);
}

/*
 * continuation_for_safefetch_fault
 *
 * Implementation of StubRoutines::continuation_for_safefetch_fault(pc)
 */
void *continuation_for_safefetch_fault(void *pc) {
    if (pc == safefetch32_fault_pc) return safefetch32_continuation_pc;
    if (pc == safefetchN_fault_pc) return safefetchN_continuation_pc;
    abort();
}


/*
 * Create a file name for the core page file, in the revivaldir.
 * Delete any existing file, otherwise it grows without limit.
 */
const char *createTempFilename() {
    fprintf(stderr, ">>> createTempFilename()\n");
    char *tempName  = (char*) calloc(1, BUFLEN); // never free'd
    if (tempName == NULL) {
        return NULL;
    }
    char *p = strncat(tempName, revivaldir, BUFLEN - 1);
    p = strncat(p, "/revivaltemp", BUFLEN - 1);
    if (verbose) {
        printf("core page file: '%s'\n", tempName);
    }
    int fdTemp = open(tempName, O_WRONLY | O_CREAT | O_EXCL, 0600);
    if (fdTemp < 0) {
        if (errno == EEXIST) {
            if (verbose) {
                printf("revival: remove existing core page file '%s'\n", tempName);
            }
            int e = unlink(tempName);
            if (e < 0) {
                printf("revival: remove existing core page file failed: %d.\n", e);
            }
            fdTemp = open(tempName, O_WRONLY | O_CREAT | O_EXCL, 0600);
            if (fdTemp < 0) {
                printf("cannot remove open existing core page file '%s': %d\n", tempName, fdTemp);
                return NULL;
            }
        }
    }
    return tempName;
}

/*
 * Return the name of the temp file to use for writing.
 * Create if necessary.
 */
const char *getCorePageFilename() {
    fprintf(stderr, ">>> getCorePageFilename()\n");
    if (corePageFilename == NULL) {
        corePageFilename = createTempFilename();
        if (corePageFilename == NULL) {
            printf("cannot create page file for writes to core file memory.\n");
            abort();
        }
    }
    return corePageFilename;
}

/*
 * Open a named file and append data for a Segment from the core.
 * Return the offset at which we wrote, or negative on error.
 */
off_t writeTempFileBytes(const char *tempName, Segment seg) {
    int fdTemp = open(tempName, O_WRONLY | O_APPEND);
    if (fdTemp < 0) {
        return fdTemp;
    }
    off_t pos = lseek(fdTemp, 0, SEEK_END);
    if (pos < 0) {
        close(fdTemp);
        printf("writeTempFileBytes: lseek fails %d : %s\n", errno, strerror(errno));
    }
    // Write bytes
    size_t s = write(fdTemp, seg.vaddr, seg.length);
    if (s != seg.length) {
        printf("writeTempFileBytes: written %d of %d.\n", (int) s, (int) seg.length);
    }
    close (fdTemp);
    return pos;
}

/*
 * remap a segment
 * Copy bytes from core file to temp file,
 * map writable.
 */
void remap(Segment seg) {
    fprintf(stderr, ">>> remap(%p)\n", &seg);
    const char *tempName = getCorePageFilename();
    if (tempName == NULL) {
        printf("remap: failed to create temp file. errno = %d: %s\n",  errno, strerror(errno));
        abort();
    }
    off_t offset = writeTempFileBytes(tempName, seg);
    if (offset == (off_t) -1 ) {
        printf("remap: failed to write bytes to temp file '%s'. errno = %d: %s\n", tempName, errno, strerror(errno));
        abort();
    }
    int fd = open(tempName, O_RDWR);
    if (fd<0) {
        printf("remap: failed to open temp file. errno = %d: %s\n",  errno, strerror(errno));
        abort();
    }
    int e1 = do_munmap_pd(seg.vaddr, seg.length);
    if (e1) {
        printf("remap: failed to munmap 0x%p failed: returns: %d: errno = %d: %s\n",  seg.vaddr, e1, errno, strerror(errno));
        abort();
    }
    // int flags = MAP_SHARED | MAP_PRIVATE | MAP_FIXED;
    int flags = MAP_PRIVATE | MAP_FIXED;
    int prot = PROT_READ | PROT_EXEC | PROT_WRITE; // could use mappings file info
    void * e = mmap(seg.vaddr, seg.length, prot, flags, fd, offset);
    if ((long long) e < 0) {
        printf("remap: mmap 0x%p failed: returns: 0x%p: errno = %d: %s\n",  seg.vaddr, e, errno, strerror(errno));
        abort();
    }
    close(fd);
}


/*
 * Signal handler.
 * Used for safefetch, and for mapping writeable areas on demand.
 *
 * Could be used to map all memory lazily, for faster startup.
 */
void handler(int sig, siginfo_t *info, void *ucontext) {
    void * addr  = (void *) info->si_addr;
    void * pc = nullptr;
    if (verbose) {
        printf("handler: sig = %d for address %p\n", sig, addr);
    }
    if (info != NULL && ucontext != NULL) {
        // Check if this is a safefetch which we should handle.
#if defined (X86_64) || defined (__x86_64__)
        pc = (void *) ((ucontext_t*)ucontext)->uc_mcontext->__es.__faultvaddr;
#else // i.e. AARCH64
        pc = (void *) arm_thread_state64_get_pc(
                ((ucontext_t*)ucontext)->uc_mcontext->__ss
                );
#endif
        // printf("handler: pc = %p\n", pc);
        if (is_safefetch_fault(pc)) {
            void * new_pc = continuation_for_safefetch_fault(pc);
#if defined (X86_64) || defined (__x86_64__)
            // XXX Where to set PC?  Can't be __faultvaddr? (check hotspot impl?)
            // But safefetch has changed: JDK-8283326
            ((ucontext_t*)ucontext)->uc_mcontext->__es.__faultvaddr = (uint64_t) new_pc;
#else // i.e. AARCH64
            arm_thread_state64_set_pc_fptr(
                    ((ucontext_t*)ucontext)->uc_mcontext->__ss, new_pc
                    );
#endif
            return;
        }
    }
    // Catch access to areas we failed to map (dangerous):
    std::list<Segment>::iterator iter;
    for (iter = failedSegments.begin(); iter != failedSegments.end(); iter++) {
        if (addr >= iter->vaddr &&
                (unsigned long long) addr < (unsigned long long) (iter->vaddr) + (unsigned long long)(iter->length) ) {
            printf("Access to segment that failed to revive: si_addr = %p found failed segment %p\n", addr, iter->vaddr);
            abort();
        }
    }

    // Handle writing to the core:
    // If this is a fault in an address covered by an area we mapped from the core,
    // which should be writable, then create a new mapping that can be written
    // without changing the core.
    for (iter = writableSegments.begin(); iter != writableSegments.end(); iter++) {
        if (addr >= iter->vaddr &&
                (unsigned long long) addr < (unsigned long long) (iter->vaddr) + (unsigned long long)(iter->length) ) {
            if (verbose) {
                printf("handler: si_addr = %p found writable segment %p\n", addr, iter->vaddr);
            }
            remap((Segment)*iter);
            return;
        }
    }
    printf("handler: si_addr = %p : not handling, abort...\n", addr);
    abort();
}

/*
 * Install the signal hander for safefetch.
 *
 * Handling SEGV is enough with a serial GC for safefetch to work,
 * using G1, we see SIGBUS also.
 */
void install_handler() {
    fprintf(stderr, ">>> install_handler()\n");
    safefetch32_fault_pc = symbol_deref("_ZN12StubRoutines21_safefetch32_fault_pcE");
    safefetch32_continuation_pc = symbol_deref("_ZN12StubRoutines28_safefetch32_continuation_pcE");
    safefetchN_fault_pc = symbol_deref("_ZN12StubRoutines20_safefetchN_fault_pcE");
    safefetchN_continuation_pc = symbol_deref("_ZN12StubRoutines27_safefetchN_continuation_pcE");

    struct sigaction sa, old_sa;
    sigfillset(&sa.sa_mask);
    sa.sa_sigaction = handler;
    sa.sa_flags = SA_SIGINFO|SA_RESTART;
    int e = sigaction(SIGSEGV, &sa, &old_sa);
    if (e) {
        printf("sigaction SIGSEGV: %d\n", e);
    }
    e = sigaction(SIGBUS, &sa, &old_sa);
    if (e) {
        printf("sigaction SIGBUS: %d\n", e);
    }
}



void *load_sharedobject_pd(const char *name, void *vaddr) {
    fprintf(stderr, ">>> load_sharedobject_pd(%s, %p)\n", name, vaddr);
    void *h = dlopen(name,  RTLD_NOW | RTLD_GLOBAL);
    if (!h) {
        printf("%s\n", dlerror());
        return (void *) -1;
    }
    return h;
}

int unload_sharedobject_pd(void *h) {
    // return dlclose(h);
    return 0;
}


/**
 *
 */
int create_revivalbits_native_pd(const char *corename, const char *javahome, const char *dirname, const char *libdir) {

    return mkdir(dirname, S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH);

    // Copy libjvm/native libraries: find path in core.

    // Relocate libjvm/native librarires.

    // Get list of memory mappings in core file.
    // Create list of mappings for revived process.
    // Avoid regions:

    // Get symbols in libjvm.
    // If dlopen relocated libjvm, should use dlsym results.
    // Write symbol list for revived process.

    return -1;
}

