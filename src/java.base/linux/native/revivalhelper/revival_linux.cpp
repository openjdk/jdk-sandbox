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

#include <dlfcn.h>
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <libgen.h>
#include <pthread.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>

#include <list>
#include <array>

// For dlinfo:
#define _GNU_SOURCE 1
#include <link.h>
#include <dlfcn.h>


#include "revival.hpp"

struct SharedLibMapping {
    Elf64_Addr start;
    Elf64_Addr end;
    char* path;
};

const char *corePageFilename;


long vaddr_align;

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
    return 0xffff800000000000;
}

void init_pd() {
    // pagesize, expect 0x1000
    long value = sysconf(_SC_PAGESIZE);
    if (value < 0) {
        fprintf(stderr, "init_pd: sysconf retuns 0x%lx: %s\n", value, strerror(errno));
        value = 0x1000; // consider exiting
    }
    vaddr_align = value;
}

bool revival_direxists_pd(const char *dirname) {
    int fd = open(dirname, O_DIRECTORY);
    if (fd < 0) {
        if (errno != ENOENT) {
            fprintf(stderr, "checking revivaldirectory '%s': %d: %s\n", dirname, errno, strerror(errno));
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
    int prot = PROT_READ | PROT_EXEC;
    if (mapWrite) {
        prot |= PROT_WRITE;
    }
    // Try with literal values.  Should work for a regular Linux core file.
    void *e = mmap(addr, length, prot, flags, fd, offset);
    if (e == (void*) -1L) {
        if (errno == EINVAL) {
            // EINVAL is likely on a Linux gcore (gdb) due to unaligned file offsets.
            // mmap requires offset to be a multiple of pagesize, retry with aligned offset.
            if (verbose) fprintf(stderr, "do_mmap_pd: 1 mmap(%p, %zu, %d, %d, %d, offset %zu) EINVAL\n", addr, length, prot, flags, fd, offset);

            long align_mask = offset_alignment_pd() - 1;
            off_t offset_aligned = align_down((uint64_t) offset, align_mask);
            size_t shift = (offset - offset_aligned);
            size_t length_aligned = length + shift;
            void *addr_aligned = (void*) (((unsigned long long) addr) - shift);
            if (verbose) fprintf(stderr, " offset_alignment = %p offset = %zu offset aligned = %zu shift = %zu new length = %zu new addr = %p\n",
                    (void*) align_mask, offset, offset_aligned, shift, length_aligned, addr_aligned);
            e = mmap(addr_aligned, length_aligned, prot, flags, fd, offset_aligned);

            if (e == (void*) -1L) {
                if (errno == EINVAL) {
                    // But the above made the address badly aligned...  Will need to allocate and copy data.
                    if (verbose) {
                        fprintf(stderr, "do_mmap_pd: 2 mmap(%p, %zu, %d, %d, %d, offset %zu) EINVAL\n",
                                addr_aligned, length_aligned, prot, flags, fd, offset_aligned);
                    }
                    int e2 = revival_mapping_copy(addr, length, offset, true, filename, fd);
                    if (e2 == -1L) {
                        fprintf(stderr, "do_mmap_pd: called revival_mapping_copy and failed: %d\n", e2);
                        e = (void*) -1L;
                    } else {
                        e = addr; // meaning ok, mapped at that address
                    }
                }
            }
        }
    }
    if (e == (void*) -1L) {
        fprintf(stderr, "do_mmap_pd: mmap(%p, %zu, %d, %d, %d, offset %zu) failed: returns: %p: errno = %d: %s\n",
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
        fprintf(stderr, "munmap_pd: %p failed: returns: %d: errno = %d: %s\n",  addr, e, errno, strerror(errno));
    }
    return e;
}

/**
 * Create a memory mapping at a given address, length.
 */
void *do_map_allocate_pd(void *vaddr, size_t length) {
    int prot = PROT_READ | PROT_WRITE;
    // int flags = MAP_ANONYMOUS | MAP_PRIVATE | MAP_FIXED;
    int flags = MAP_ANONYMOUS | MAP_PRIVATE | MAP_FIXED | MAP_NORESERVE;
    int fd = -1;
    size_t offset = 0;

    void *h = mmap(vaddr, length, prot, flags, fd, offset);
    if (verbose) {
        fprintf(stderr, "do_map_allocate: mmap(%p, %zu, %d, %d, %d, %zu) returns: %p\n",
                vaddr, length, prot, flags, fd, offset, h);
    }
    if (h == (void *) -1) {
        fprintf(stderr, "do_map_allocate: mmap(%p, %zu, %d, %d, %d, %zu) failed: returns: %p: errno = %d: %s\n",
                vaddr, length, prot, flags, fd, offset, h, errno, strerror(errno));
    }
    return h;
}


int revival_checks_pd(const char *dirname) {
    // Check dirname is valid:
    int e = open(dirname, O_DIRECTORY);
    if (e < 0) {
        fprintf(stderr, "revival_checks: cannot open directory '%s'.\n", dirname);
        return -1;
    }
    // Check LD_USE_LOAD_BIAS is set:
    char * env = getenv("LD_USE_LOAD_BIAS");
    if (env == NULL || strncmp(env, "1", 1) != 0) {
        fprintf(stderr, "Error: LD_USE_LOAD_BIAS not set.\n");
        return -1;
    }
    return 0;
}

void pmap_pd() {
    char buf[BUFLEN];
    pid_t pid = getpid();
    snprintf(buf, BUFLEN, "pmap %d", pid);
    int e = system(buf);
    if (e != 0) {
        fprintf(stderr, "pmap: %d\n", e);
    }
}

void *symbol_dynamiclookup_pd(void *h, const char *str) {
    void *s = dlsym(RTLD_NEXT, str);
    if (verbose) {
        fprintf(stderr, "symbol_dynamiclookup: %s = %p \n", str, s);
    }
    if (s == 0) {
        if (verbose) {
            fprintf(stderr, "dlsym: %s\n", dlerror());
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
    char *tempName  = (char*) calloc(1, BUFLEN); // never free'd
    if (tempName == NULL) {
        return NULL;
    }
    char *p = strncat(tempName, revivaldir, BUFLEN - 1);
    p = strncat(p, "/revivaltemp", BUFLEN - 1);
    if (verbose) {
        fprintf(stderr, "core page file: '%s'\n", tempName);
    }
    int fdTemp = open(tempName, O_WRONLY | O_CREAT | O_EXCL, 0600);
    if (fdTemp < 0) {
        if (errno == EEXIST) {
            if (verbose) {
                fprintf(stderr, "revival: remove existing core page file '%s'\n", tempName);
            }
            int e = unlink(tempName);
            if (e < 0) {
                fprintf(stderr, "revival: remove existing core page file failed: %d.\n", e);
            }
            fdTemp = open(tempName, O_WRONLY | O_CREAT | O_EXCL, 0600); 
            if (fdTemp < 0) {
                fprintf(stderr, "cannot remove open existing core page file '%s': %d\n", tempName, fdTemp);
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
    if (corePageFilename == NULL) {
        corePageFilename = createTempFilename();
        if (corePageFilename == NULL) {
            fprintf(stderr, "cannot create page file for writes to core file memory.\n");
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
        fprintf(stderr, "writeTempFileBytes: lseek fails %d : %s\n", errno, strerror(errno));
    }
    // Write bytes
    size_t s = write(fdTemp, seg.vaddr, seg.length); 
    if (s != seg.length) {
        fprintf(stderr, "writeTempFileBytes: written %d of %d.\n", (int) s, (int) seg.length);
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
    const char *tempName = getCorePageFilename();
    if (tempName == NULL) {
        fprintf(stderr, "remap: failed to create temp file. errno = %d: %s\n",  errno, strerror(errno));
        abort();
    }
    off_t offset = writeTempFileBytes(tempName, seg);
    if (offset == (off_t) -1 ) {
        fprintf(stderr, "remap: failed to write bytes to temp file '%s'. errno = %d: %s\n", tempName, errno, strerror(errno));
        abort();
    }
    int fd = open(tempName, O_RDWR);
    if (fd<0) {
        fprintf(stderr, "remap: failed to open temp file. errno = %d: %s\n",  errno, strerror(errno));
        abort();
    }
    int e1 = do_munmap_pd(seg.vaddr, seg.length);
    if (e1) {
        fprintf(stderr, "remap: failed to munmap 0x%p failed: returns: %d: errno = %d: %s\n",  seg.vaddr, e1, errno, strerror(errno));
        abort();
    }
    // int flags = MAP_SHARED | MAP_PRIVATE | MAP_FIXED;
    int flags = MAP_PRIVATE | MAP_FIXED;
    int prot = PROT_READ | PROT_EXEC | PROT_WRITE; // could use mappings file info
    void * e = mmap(seg.vaddr, seg.length, prot, flags, fd, offset);
    if ((long long) e < 0) {
        fprintf(stderr, "remap: mmap 0x%p failed: returns: 0x%p: errno = %d: %s\n",  seg.vaddr, e, errno, strerror(errno));
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
    void * pc;
    if (verbose) {
        fprintf(stderr, "handler: sig = %d for address %p\n", sig, addr);
    }
    if (info != NULL && ucontext != NULL) {
        // Check if this is a safefetch which we should handle:
#if defined (X86_64) || defined (__x86_64__)
        pc = (void *) ((ucontext_t*)ucontext)->uc_mcontext.gregs[REG_RIP];
#else // i.e. AARCH64
        pc = (void *) ((ucontext_t*)ucontext)->uc_mcontext.pc;
#endif
        // fprintf(stderr, "handler: pc = %p\n", pc);
        if (is_safefetch_fault(pc)) {
            void * new_pc = continuation_for_safefetch_fault(pc);
#if defined (X86_64) || defined (__x86_64__)
            ((ucontext_t*)ucontext)->uc_mcontext.gregs[REG_RIP] = (greg_t) new_pc;
#else // i.e. AARCH64
            ((ucontext_t*)ucontext)->uc_mcontext.pc = (greg_t) new_pc;
#endif
            return;
        }
    }
    // Catch access to areas we failed to map (dangerous):
    std::list<Segment>::iterator iter;
    for (iter = failedSegments.begin(); iter != failedSegments.end(); iter++) {
        if (addr >= iter->vaddr && 
                (unsigned long long) addr < (unsigned long long) (iter->vaddr) + (unsigned long long)(iter->length) ) {
            fprintf(stderr, "Access to segment that failed to revive: si_addr = %p found failed segment %p\n", addr, iter->vaddr);
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
                fprintf(stderr, "handler: si_addr = %p found writable segment %p\n", addr, iter->vaddr);
            }
            remap((Segment)*iter);
            return;
        }
    }
    fprintf(stderr, "handler: si_addr = %p : not handling, abort...\n", addr);
    abort(); 
}

/*
 * Install the signal hander for safefetch.
 *
 * Handling SEGV is enough with a serial GC for safefetch to work,
 * using G1, we see SIGBUS also.
 */
void install_handler() {
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
        fprintf(stderr, "sigaction SIGSEGV: %d\n", e);
    }
    e = sigaction(SIGBUS, &sa, &old_sa);
    if (e) {
        fprintf(stderr, "sigaction SIGBUS: %d\n", e);
    }
}


/**
 * Return the actual load address for a shared object, given its opaque handle
 * (the value returned from dlopen).
 *
 * Actually, this returns the difference from the preferred address.
 * For a file with no preferred address, that IS the loaded address.
 */
void *base_address_for_sharedobject_live(void *h) {
    struct link_map lm;
    struct link_map *lp = &lm;
    struct link_map **lpp = &lp;

    int e = dlinfo(h, RTLD_DI_LINKMAP, (struct link_map **) lpp);
    if (e == -1) {
        fprintf(stderr, "base_address_for_sharedobject_live: dlinfo error %d: %s\n", e, dlerror());
        return (void *) -1;
    }
    return (void *) (*lpp)->l_addr;
}

/**
 * Use dlopen to load a sharedobject.
 * Verify the base address of the loaded library is as requested, if possible.
 *
 * Return the opaque handle from dlopen, which is not the load address.
 * Return -1 for error.
 */
void *load_sharedobject_verify_pd(const char *name, void *vaddr) {

    void *actual = nullptr;
    int tries = 1; // can try unload and retry if not as requested.

    for (int i = 0; i < tries; i++) {
        void *h = dlopen(name,  RTLD_NOW | RTLD_GLOBAL);

        if (!h) {
            fprintf(stderr, "load_sharedobject_pd: %s: %s\n", name, dlerror());
            return (void *) -1;
        }

        actual = base_address_for_sharedobject_live(h);
        if (verbose) {
            fprintf(stderr, "%d  load_sharedobject_pd: actual = %p \n", i, actual);
        }

        if (actual == (void *) 0 || actual == vaddr) {
            break;
        }

        // Wrong address, dlclose and map/block.
        unload_sharedobject_pd(h);
        /* void *block = */ do_map_allocate_pd(actual, vaddr_alignment_pd());
        i++;
    }

    if (actual != (void *) 0 && actual != vaddr) {
        fprintf(stderr, "load_sharedobject_pd: %s: failed, loads at %p\n", name, actual);
        unload_sharedobject_pd(h);
        return (void *) -1;
    }

    return h;
}

/**
 * Experimental loading shared object by mmap, then fixing up.  Not fully implemented.
 */
void *load_sharedobject_mmap_pd(const char *filename, void *vaddr) {
    int loaded = 0;
    int fd = open(filename, O_RDONLY);
    if (fd < 0) {
        fprintf(stderr, "load_sharedobject_mmap_pd: cannot open %s\n", filename);
        return (void *) -1;
    }
    // Read ELF header, find Program Headers.
    Elf64_Ehdr hdr;
    size_t e = read(fd, &hdr, sizeof(hdr));
    if (e < sizeof(hdr)) {
        fprintf(stderr, "load_sharedobject_mmap_pd: failed to read ELF header %s: %ld\n", filename, e);
        return (void *) -1;
    }
    lseek(fd, hdr.e_phoff, SEEK_SET);
    // Read ELF Program Headers.  Look for PT_LOAD.
    Elf64_Phdr phdr;
    for (int i = 0; i < hdr.e_phnum; i++) {
        e = read(fd, &phdr, sizeof(phdr));
        if (e < sizeof(phdr)) {
            fprintf(stderr, "load_sharedobject_mmap_pd: failed to read ELF Program Header %s: %ld\n", filename, e);
            return (void *) -1;
        }
        fprintf(stderr, "load_sharedobject_mmap_pd: PH %d: type 0x%x flags 0x%x vaddr 0x%lx\n", i, phdr.p_type, phdr.p_flags, phdr.p_vaddr);
        if (phdr.p_type == PT_LOAD) {
            if (phdr.p_flags == (PF_X | PF_R) || phdr.p_flags == (PF_R | PF_W)) {
                // Expect a non-prelinked/relocated library, with zero base address.
                // Map PH at the given vaddr plus PH vaddr.
                uint64_t va = (uint64_t) vaddr + (uint64_t) phdr.p_vaddr;
                fprintf(stderr, "load_sharedobject_mmap_pd: LOAD offset %lx vaddr %p \n", phdr.p_offset, (void *) va);
                void * a = do_mmap_pd((void *) va, phdr.p_filesz, (char *) filename, fd, phdr.p_offset);
                fprintf(stderr, "load_sharedobject_mmap_pd: %s: %p\n", filename, a);
                if ((uint64_t) a > 0) {
                    fprintf(stderr, "load_sharedobject_mmap_pd OK\n");
                    loaded++;
                }
            }
        }
    }
    // Not done yet. We need to do the job of the runtime linker.
    // Calls via pltgot need fixing for the new base address.
    // That includes a table of relocations into ourself, and that require resolving from all other libraries.
    // A prelink-like edit of the binary might be easier, and lets the runtime linker do this.
    // That is still quite a bit of work, but relocations are by a fixed amount, no lookups in other librarires required.
    if (loaded > 0) {
        return vaddr;
    } else {
        return (void *) -1;
    }
}


void *load_sharedobject_pd(const char *name, void *vaddr) {

    bool verify = true;
    bool mmap = false;

    if (verify) {
        return load_sharedobject_verify_pd(name, vaddr);
    } else if (mmap) {
        return load_sharedobject_mmap_pd(name, vaddr);
    } else {
        void *h = dlopen(name,  RTLD_NOW | RTLD_GLOBAL);
        if (!h) {
            fprintf(stderr, "load_sharedobject_pd: %s: %s\n", name, dlerror());
            return (void *) -1;
        }
        return h;
    }
}

int unload_sharedobject_pd(void *h) {
    return dlclose(h); // zero on success
}

char *readstring(int fd) {
    char *buf = (char *) malloc(BUFLEN);
    if (buf == nullptr) {
        return nullptr;
    }
    int c = 0;
    do {
        int e = read(fd, &buf[c], 1);
        if (e != 1) {
            free(buf);
            return nullptr;
        }
    } while (buf[c++] != 0);
    return buf;
}

SharedLibMapping* read_NT_mappings(
    int fd,
    int &count_out
) {
    lseek(fd, 0, SEEK_SET);
// Read core NOTES, find NT_FILE, find libjvm.so

    // Read ELF header, find NOTES
    Elf64_Ehdr hdr;
    size_t e = read(fd, &hdr, sizeof(hdr));
    if (e < sizeof(hdr)) {
        fprintf(stderr, "Failed to read ELF header: %ld\n", e);
        return nullptr;
    }
    // TODO: Sanity check ELF header.

    lseek(fd, hdr.e_phoff, SEEK_SET);
    // Read ELF Program Headers.  Look for PT_NOTE
    Elf64_Phdr phdr;
    for (int i = 0; i < hdr.e_phnum; i++) {
        e = read(fd, &phdr, sizeof(phdr));
        if (e < sizeof(phdr)) {
            fprintf(stderr, "Failed to read ELF Program Header: %ld\n", e);
            return nullptr;
        }
        if (verbose) {
            fprintf(stderr, "PH %d: type 0x%x flags 0x%x vaddr 0x%lx\n", i, phdr.p_type, phdr.p_flags, phdr.p_vaddr);
        }
        if (phdr.p_type == PT_NOTE) {
            lseek(fd, phdr.p_offset, SEEK_SET);
            // Read NOTES.  p_filesz
            // Look for NT_FILE note
            Elf64_Nhdr nhdr;
            uint64_t pos = lseek(fd, 0, SEEK_CUR);
            uint64_t end =  pos + phdr.p_filesz;
            while (pos < end) {
                e = read(fd, &nhdr, sizeof(nhdr));
                if (e < sizeof(nhdr)) {
                    fprintf(stderr, "Failed to read NOTE header: %ld\n", e);
                    return nullptr;
                }
                fprintf(stderr, "NOTE type 0x%x namesz %x descsz %x\n", nhdr.n_type, nhdr.n_namesz, nhdr.n_descsz);
                // TODO where's this freed?
                char *name = (char *) malloc(nhdr.n_namesz);
                if (name == nullptr) {
                    fprintf(stderr, "Failed malloc for namesz %d\n", nhdr.n_namesz);
                    return nullptr;
                }
                e = read(fd, name, nhdr.n_namesz);

                // Align. 4 byte alignment, including when 64-bit.
                while ((lseek(fd, 0, SEEK_CUR) & 0x3) != 0) {
                    char c;
                    e = read(fd, &c, 1);
                }

                if (nhdr.n_type == 0x46494c45 /* NT_FILE */) {
                    // Read NT_FILE:
                    int count;
                    e = read(fd, &count, sizeof (int));
                    int pad;
                    e = read(fd, &pad, sizeof (int));
                    long pagesize;
                    e = read(fd, &pagesize, sizeof (long));
                    if (verbose) {
                        fprintf(stderr, "NT_FILE count %d pagesize 0x%lx\n", count, pagesize);
                    }
                    
                    SharedLibMapping* ret = new SharedLibMapping[count];
                    count_out = count;

                    uint64_t start;
                    uint64_t end;
                    uint64_t offset;
                    for (int i = 0; i < count; i++) {
                        e = read(fd, &start, sizeof (long));
                        e = read(fd, &end, sizeof (long));
                        e = read(fd, &offset, sizeof (long)); // multiply offset by pagesize

                        ret[i].start = start;
                        ret[i].end = end;
                    }

                    for (int i = 0; i < count; i++) {
                        ret[i].path = readstring(fd);
                    }
                    return ret;
                } else {
                    // Not NT_FILE, skip over...
                    lseek(fd, (nhdr.n_descsz), SEEK_CUR);
                }
                pos = lseek(fd, 0, SEEK_CUR);
            }
            break; // only need one NOTE
        }
    }
    fprintf(stderr, "No NT_FILE NOTE found?\n");
    return nullptr;
}


void init_jvm_filename_pd(int core_fd) {
    //char* jvm_filename = nullptr;
    //if (filename != nullptr) {
    //    jvm_filename = resolve_jvm_info_pd(filename);
   // }
    //return jvm_filename;

    int count_out = 0;
    SharedLibMapping* mappings = read_NT_mappings(core_fd, count_out);
    //fprintf(stderr, "Num of mappings: %i\n", count_out);
    for (int i = 0; i < count_out; i++) {
        //fprintf(stderr, "Mapping: %lu %lu %s\n", mappings[i].start, mappings[i].end, mappings[i].path);
        if(strstr(mappings[i].path, "libjvm.so")) {
            jvm_filename = mappings[i].path;// TODO yes memleak
            jvm_address = (void*) mappings[i].start;
            return;
        }
    }
}

void *get_jvm_load_adddress_pd(const char*corename) {
    return jvm_address;
}

int copy_file_pd(const char *srcfile, const char *destfile) {

    // sendfile(outfd, infd, 0, count);
    char command[BUFLEN];
    memset(command, 0, BUFLEN);
    strncat(command, "cp ", BUFLEN - 1);
    strncat(command, srcfile, BUFLEN - 1);
    strncat(command, " ", BUFLEN - 1);
    strncat(command,  destfile, BUFLEN - 1);
    return system(command);
}

bool is_unwanted_phdr(Elf64_Phdr phdr) {
    if (
        phdr.p_memsz == 0 ||
        phdr.p_filesz == 0
    ) return true;


   return false;
}


bool is_inside(Elf64_Addr from, Elf64_Addr x, Elf64_Addr to) {
    return from <= x && x < to;
}

bool is_inside(Elf64_Phdr phdr, Elf64_Addr start, Elf64_Addr end) {
    return is_inside(start, phdr.p_vaddr, end)
      || is_inside(start, phdr.p_vaddr + phdr.p_memsz, end);
}

// PRS_TODO
int create_mappings_pd(int mappings_fd, int core_fd, const char *jvm_copy, const char *javahome, void *addr) {
    char command[BUFLEN];
    memset(command, 0, BUFLEN);

    // Get list of memory mappings in core file.
    // Create list of mappings for revived process.
    //
    // Write mappings file.
    // use fd, create a Segment, call int Segment::write_mapping(int fd)

    // Get symbols in libjvm.
    // If dlopen relocated libjvm, should use dlsym results.
    // Write symbol list for revived process.


    // For each program header:
    //  If filesize or memsize is zero, skip it (maybe log)
    //  If it touches an unwantedmapping, skipt it (maybe log)
    //  If not writeable and inOtherMapping* skip it (maybe log)
    //  Write an "M" entry

    Elf64_Ehdr hdr;
    size_t e = read(core_fd, &hdr, sizeof(hdr));
    if (e < sizeof(hdr)) {
        fprintf(stderr, "Failed to read ELF header: %ld\n", e);
        close(core_fd);
        return -1;
    }

    int count_out = 0;
    SharedLibMapping* mappings = read_NT_mappings(core_fd, count_out);
    fprintf(stderr, "Num of mappings: %i\n", count_out);
    for (int i = 0; i < count_out; i++) {
        fprintf(stderr, "Mapping: %lu %lu %s\n", mappings[i].start, mappings[i].end, mappings[i].path);
    }

    int n_skipped = 0;
    lseek(core_fd, hdr.e_phoff, SEEK_SET);
    for (int i = 0; i < hdr.e_phnum; i++) {
        // TODO skip some 
        
        Elf64_Phdr phdr;

        lseek(core_fd, hdr.e_phoff + i*sizeof(phdr), SEEK_SET);
        e = read(core_fd, &phdr, sizeof(phdr));
        if (e < sizeof(phdr)) {
            fprintf(stderr, "Failed to read ELF program header: %ld\n", e);
            close(core_fd);
            return -1;
        }

        if (
            phdr.p_memsz == 0 ||
            phdr.p_filesz == 0
        ) {
            n_skipped++;
            continue;
        } 

        // Now we want to exclude this mapping if it touches any unwanted mapping. 
        // Let's start with /bin/java #1
        // If the virtaddr is between start and end, it touches, exclude it
 
        bool skip = false;
        for (int i = 0; i < count_out; i++) {
            if(
              is_inside(phdr, mappings[i].start, mappings[i].end)
              && (
                strstr(mappings[i].path, "bin/java") || false//!(phdr.p_flags & PF_W)
              )
            ) {
                skip = true;
                break;
            }
        }
        if (skip) {
            fprintf(stderr, "\nSkipping at %lu\n", phdr.p_vaddr);
            n_skipped++;
            continue;
        }

        // TODO plt/GOTs maybe
        // TODO "non-writeable mappings that are part of other mappings"
        

        fprintf(stderr, "Writing: %lu\n", phdr.p_vaddr);
        Segment s((void*)phdr.p_vaddr, phdr.p_memsz, phdr.p_offset, phdr.p_filesz);
        s.write_mapping(mappings_fd);
    }
    fprintf(stderr, "Skipped number: %i\n", n_skipped);

    //int jvm_fd = open(jvm_filename, O_RDONLY);
    //if (jvm_fd < 0) {
    //    fprintf(stderr, "Cannot open %s: %s\n", jvm_filename, strerror(errno));
    //    return -1;
   // }

    //size_t e = read(jvm_fd, &hdr, sizeof(hdr));
    //if (e < sizeof(hdr)) {
    //    fprintf(stderr, "Failed to read ELF header %s: %ld\n", jvm_filename, e);
    //    close(core_fd);
     //   return -1;
   // }
    // Then do symbols
    // TODO symbols
    //for(int i = 0; i < hdr.e_shnum; i++) {
    //    kkkkkkkkk
   // }


    return 0;
}

/**
 *
 */
int create_revivalbits_native_pd(const char *corename, const char *javahome, const char *revival_dirname, const char *libdir) {
    int core_fd = open(corename, O_RDONLY);
    if (core_fd < 0) {
        fprintf(stderr, "Cannot open %s: %s\n", corename, strerror(errno));
        return -1;
    }

    // find libjvm and its load address from core
    init_jvm_filename_pd(core_fd);
    if (jvm_filename == nullptr) {
        fprintf(stderr, "revival: cannot locate JVM in core %s.\n", corename) ;
        return -1;
    }
    fprintf(stderr, "JVM = '%s'\n", jvm_filename);
    jvm_address = get_jvm_load_adddress_pd(corename);
    fprintf(stderr, "JVM addr = %p\n", jvm_address);

    // make core.revival dir
    int e = mkdir(revival_dirname, S_IRUSR | S_IWUSR | S_IXUSR);
    if (e < 0) {
        fprintf(stderr, "Cannot create directory: %s: %s\n", revival_dirname, strerror(errno));
        return e;
    }

    // copy libjvm into core.revival dir
    char jvm_copy[BUFLEN];
    memset(jvm_copy, 0, BUFLEN);
    strncpy(jvm_copy, revival_dirname, BUFLEN - 1);
    strncat(jvm_copy, "/libjvm.so", BUFLEN - 1);
    fprintf(stderr, "copying JVM to: %s\n", jvm_copy);
    e = copy_file_pd(jvm_filename, jvm_copy);
    if (e != 0) {
        fprintf(stderr, "Cannot copy JVM: %s to  %s\n", jvm_filename, revival_dirname);
        return -1;
    }

    // relocate copy of libjvm:
    e = relocate_sharedlib_pd(jvm_copy, jvm_address, javahome);
    if (e != 0) {
        fprintf(stderr, "failed to relocate JVM\n");
        return -1;
    }

    // Create core.mappings file:
    int mappings_fd = mappings_file_create(revival_dirname, corename);
    if (mappings_fd < 0) {
        fprintf(stderr, "failed to create mappings file\n");
        return -1;
    }

    // read core file to create core.mappings file
    e = create_mappings_pd(mappings_fd, core_fd, jvm_copy, javahome, jvm_address);

    //// Create core.symbols file:
    //int mappings_fd = mappings_file_create(revival_dirname, corename);
    //if (mappings_fd < 0) {
    //    fprintf(stderr, "failed to create mappings file\n");
    //    return -1;
   // }


    fsync(mappings_fd);
    if (close(mappings_fd) < 0) {
        fprintf(stderr, "failed to close mappings\n");
        return -1;
    }

    if (verbose) {
        printf("create_revivalbits_native_pd returning %d.\n", e);
    }
    return e;
}
