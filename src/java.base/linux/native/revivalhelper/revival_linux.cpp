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

#include <dirent.h>
#include <dlfcn.h>
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <libgen.h>
#include <signal.h>
#include <cassert>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include <sys/mman.h>
#include <sys/sendfile.h>
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
#include "elffile.hpp"


long vaddr_align; // set by init_pd

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
    // Manually computed from adding fields in elf.h
    // This is to guard against the compiler adding padding without us noticing, which would break parsing.
    assert(sizeof(Elf64_Ehdr) == 64);
    assert(sizeof(Elf64_Phdr) == 56);
    assert(sizeof(Elf64_Shdr) == 64);
    assert(sizeof(Elf64_Dyn) == 16);
    assert(sizeof(Elf64_Sym) == 24);
    assert(sizeof(Elf64_Rela) == 24);
    assert(sizeof(Elf64_Rel) == 16);

    // pagesize, expect 0x1000
    long value = sysconf(_SC_PAGESIZE);
    if (value < 0) {
        warn("init_pd: sysconf retuns 0x%lx: %s", value, strerror(errno));
        value = 0x1000; // consider exiting
    }
    vaddr_align = value;
    logv("revival: init_pd: vaddr_alignment = 0x%llx\n", (unsigned long long) vaddr_alignment_pd());
}


bool dir_exists_pd(const char *dirname) {
    int fd = open(dirname, O_DIRECTORY);
    if (fd < 0) {
        if (errno != ENOENT) {
            warn("checking revivaldirectory '%s': %d: %s", dirname, errno, strerror(errno));
        }
    } else {
        close(fd);
        return true;
    }
    return false;
}

bool dir_isempty_pd(const char *dirname) {
    int count = 0;
    DIR* dir = opendir(dirname);
    if (dir != nullptr) {
        struct dirent* ent;
        while (true) {
            ent = readdir(dir);
            if (ent == nullptr) {
                break;
            }
            count++;
        }
        closedir(dir);
        return count <= 2;
    }
    return false;
}

bool file_exists_pd(const char *filename) {
    int fd = open(filename, O_RDONLY);
    if (fd < 0) {
        if (errno != ENOENT) {
            warn("checking file'%s': %d: %s", filename, errno, strerror(errno));
        }
    } else {
        close(fd);
        return true;
    }
    return false;
}

bool file_exists_indir_pd(const char* dirname, const char* filename) {
    char path[BUFLEN];
    snprintf(path, BUFLEN - 1, "%s%s%s", dirname, FILE_SEPARATOR, filename);
    return file_exists_pd(path);
}


char* readstring_at_offset_pd(const char* filename, uint64_t offset) {
    int fd = open(filename, O_RDONLY);
    if (fd < 0) {
        warn("cannot open %s", filename);
        return (char*) -1;
    }
    off_t pos = lseek(fd, offset, SEEK_SET);
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
    ELFFile elf(filename, nullptr);
    return elf.readstring_at_address(addr);
}


bool mem_canwrite_pd(void *vaddr, size_t length) {
    return true;
}


void *do_mmap_pd(void *addr, size_t length, char *filename, int fd, size_t offset) {
    int flags = MAP_SHARED | MAP_PRIVATE | MAP_FIXED;
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
            logv("do_mmap_pd: 1 mmap(%p, %zu, %d, %d, %d, offset %zu) EINVAL\n", addr, length, prot, flags, fd, offset);

            long align_mask = offset_alignment_pd() - 1;
            size_t offset_aligned = align_down((uint64_t) offset, align_mask);
            size_t shift = (offset - offset_aligned);
            size_t length_aligned = length + shift;
            void *addr_aligned = (void*) (((unsigned long long) addr) - shift);
            logv(" offset_alignment = %p offset = %zu offset aligned = %zu shift = %zu new length = %zu new addr = %p\n",
                 (void*) align_mask, offset, offset_aligned, shift, length_aligned, addr_aligned);
            e = mmap(addr_aligned, length_aligned, prot, flags, fd, offset_aligned);

            if (e == (void*) -1L) {
                if (errno == EINVAL) {
                    // But the above made the address badly aligned...  Will need to allocate and copy data.
                    logv("do_mmap_pd: 2 mmap(%p, %zu, %d, %d, %d, offset %zu) EINVAL\n",
                         addr_aligned, length_aligned, prot, flags, fd, offset_aligned);
                    int e2 = revival_mapping_copy(addr, length, offset, true, filename, fd);
                    if (e2 == -1L) {
                        warn("do_mmap_pd: called revival_mapping_copy and failed: %d\n", e2);
                        e = (void*) -1L;
                    } else {
                        e = addr; // meaning ok, mapped at that address
                    }
                }
            }
        }
    }
    if (e == (void*) -1L) {
        warn("do_mmap_pd: mmap(%p, %zu, %d, %d, %d, offset %zu) failed: returns: %p: errno = %d: %s",
                addr, length, prot, flags, fd, offset, e, errno, strerror(errno));
    }
    return e;
}

void *do_mmap_pd(void *addr, size_t length, size_t offset) {
    return do_mmap_pd(addr, length, NULL, core_fd, offset);
}

int do_munmap_pd(void *addr, size_t length) {
    int e = munmap(addr, length);
    if (e) {
        warn("munmap_pd: %p failed: returns: %d: errno = %d: %s",  addr, e, errno, strerror(errno));
    }
    return e;
}

/**
 * Create a memory mapping at a given address, length.
 */
void *do_map_allocate_pd(void *vaddr, size_t length) {
    int prot = PROT_READ | PROT_WRITE;
    int flags = MAP_ANONYMOUS | MAP_PRIVATE | MAP_FIXED | MAP_NORESERVE;
    int fd = -1;
    size_t offset = 0;

    void *h = mmap(vaddr, length, prot, flags, fd, offset);
    logv("do_map_allocate: mmap(%p, %zu, %d, %d, %d, %zu) returns: %p\n",
          vaddr, length, prot, flags, fd, offset, h);
    if (h == (void *) -1) {
        warn("do_map_allocate: mmap(%p, %zu, %d, %d, %d, %zu) failed: returns: %p: errno = %d: %s\n",
             vaddr, length, prot, flags, fd, offset, h, errno, strerror(errno));
    }
    return h;
}


int revival_checks_pd(const char *dirname) {
    // Check LD_USE_LOAD_BIAS is set:
    char * env = getenv("LD_USE_LOAD_BIAS");
    if (env == NULL || strncmp(env, "1", 1) != 0) {
        error("Error: LD_USE_LOAD_BIAS not set.");
    }
    return 0;
}

void *symbol_dynamiclookup_pd(void *h, const char *str) {
    void *s = dlsym(RTLD_NEXT, str);
    logv("symbol_dynamiclookup: %s = %p \n", str, s);
    if (s == 0) {
        if (verbose) {
            warn("dlsym: %s", dlerror());
        }
        return (void *) -1;
    }
    return s;
}


/*
 * Create a file name for the core page file, in the revivaldir.
 * Delete any existing file, otherwise it grows without limit.
 */
const char *createTempFilename() {
    char *tempName  = (char*) calloc(1, BUFLEN); // never free'd
    if (tempName == NULL) {
        error("createTempFilename: calloc failed");
    }
    char *p = strncat(tempName, revivaldir, BUFLEN - 1);
    p = strncat(p, "/revivaltemp", BUFLEN - 1);
    logv("core page file: '%s'\n", tempName);
    int fdTemp = open(tempName, O_WRONLY | O_CREAT | O_EXCL, 0600);
    if (fdTemp < 0) {
        if (errno == EEXIST) {
            logv("revival: remove existing core page file '%s'\n", tempName);
            int e = unlink(tempName);
            if (e < 0) {
                warn("revival: remove existing core page file failed: %d", e);
            }
            fdTemp = open(tempName, O_WRONLY | O_CREAT | O_EXCL, 0600); 
            if (fdTemp < 0) {
                error("cannot remove open existing core page file '%s': %d", tempName, fdTemp);
            }
        }
    }
    return tempName;
}


const char *corePageFilename;

/*
 * Return the name of the temp file to use for writing.
 */
const char *getCorePageFilename() {
    if (corePageFilename == NULL) {
        // Create filename on demand.
        corePageFilename = createTempFilename();
        if (corePageFilename == NULL) {
            error("cannot create page file for writes to core file memory.");
        }
    }
    return corePageFilename;
}

/*
 * Open a named file and append data for a Segment from the core.
 * Return the offset at which we wrote, or negative on error.
 */
size_t writeTempFileBytes(const char *tempName, Segment seg) {
    int fdTemp = open(tempName, O_WRONLY | O_APPEND);
    if (fdTemp < 0) {
        return fdTemp;
    }
    off_t pos = lseek(fdTemp, 0, SEEK_END);
    if (pos < 0) {
        warn("writeTempFileBytes: lseek fails %d : %s", errno, strerror(errno));
        close(fdTemp);
    }
    // Write bytes
    size_t s = write(fdTemp, seg.vaddr, seg.length);
    if (s != seg.length) {
        warn("writeTempFileBytes: written %d of %d.\n", (int) s, (int) seg.length);
    }
    close (fdTemp);
    return (size_t) pos;
}

/*
 * remap a segment
 * Copy bytes from core file to temp file,
 * map writable.
 */
void remap(Segment seg) {
    const char *tempName = getCorePageFilename();
    if (tempName == NULL) {
        error("remap: failed to create temp filename. errno = %d: %s",  errno, strerror(errno));
    }
    size_t offset = writeTempFileBytes(tempName, seg);
    if (offset == (size_t) -1 ) {
        warn("remap: failed to write bytes to temp file '%s'. errno = %d: %s", tempName, errno, strerror(errno));
        abort();
    }
    int fd = open(tempName, O_RDWR);
    if (fd<0) {
        warn("remap: failed to open temp file. errno = %d: %s",  errno, strerror(errno));
        abort();
    }
    int e1 = do_munmap_pd(seg.vaddr, seg.length);
    if (e1) {
        warn("remap: failed to munmap 0x%p failed: returns: %d: errno = %d: %s",  seg.vaddr, e1, errno, strerror(errno));
        abort();
    }
    int flags = MAP_PRIVATE | MAP_FIXED; // previously also MAP_SHARED
    int prot = PROT_READ | PROT_EXEC | PROT_WRITE; // should use mappings file info
    void * e = mmap(seg.vaddr, seg.length, prot, flags, fd, offset);
    if ((long long) e < 0) {
        warn("remap: mmap 0x%p failed: returns: 0x%p: errno = %d: %s",  seg.vaddr, e, errno, strerror(errno));
        abort();
    }
    close(fd);
}


/*
 * Signal handler.
 * Catch errors and do mapping of writeable areas on demand.
 *
 * Could be used to map all memory lazily, for faster startup.
 */
void handler(int sig, siginfo_t *info, void *ucontext) {
    void * addr  = (void *) info->si_addr;
    logv("revival: handler: sig = %d for address %p\n", sig, addr);

    if (addr == nullptr) {
        warn("handler: null address");
        abort();
    }

    // Catch access to areas we failed to map:
    std::list<Segment>::iterator iter;
    for (iter = failedSegments.begin(); iter != failedSegments.end(); iter++) {
        if (addr >= iter->vaddr &&
                (unsigned long long) addr < (unsigned long long) (iter->vaddr) + (unsigned long long)(iter->length) ) {
            warn("Access to segment that failed to revive: si_addr = %p in failed segment %p", addr, iter->vaddr);
            exitForRetry();
        }
    }

    // Handle writing to the core:
    // Check again if PRIVATE mapping makes this unnecessary. XXXX
    //
    // If this is a fault in an address covered by an area we mapped from the core,
    // which should be writable, then create a new mapping that can be written
    // without changing the core.
    for (iter = writableSegments.begin(); iter != writableSegments.end(); iter++) {
        if (addr >= iter->vaddr &&
                (unsigned long long) addr < (unsigned long long) (iter->vaddr) + (unsigned long long)(iter->length) ) {
            logv("handler: si_addr = %p found writable segment %p\n", addr, iter->vaddr);
            remap((Segment) *iter);
            return;
        }
    }
    warn("handler: si_addr = %p : not handled.", addr);
//    exitForRetry();
    abort();
}

/*
 * Install the signal hander.
 *
 */
void install_handler() {
    struct sigaction sa, old_sa;
    sigfillset(&sa.sa_mask);
    sa.sa_sigaction = handler;
    sa.sa_flags = SA_SIGINFO|SA_RESTART;
    int e = sigaction(SIGSEGV, &sa, &old_sa);
    if (e) {
        warn("sigaction SIGSEGV: %d\n", e);
    }
    e = sigaction(SIGBUS, &sa, &old_sa);
    if (e) {
        warn("sigaction SIGBUS: %d\n", e);
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
        warn("base_address_for_sharedobject_live: dlinfo error %d: %s", e, dlerror());
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
    int max_tries = 1; // Retrying, even when allocating to force a new address, is not usually succesfull.

    for (int i = 0; i < max_tries; i++) {
        void *h = dlopen(name,  RTLD_NOW | RTLD_GLOBAL);

        if (!h) {
            warn("load_sharedobject_pd: dlopen failed: %s: %s", name, dlerror());
            return (void *) -1;
        }

        actual = base_address_for_sharedobject_live(h);
        logv("load_sharedobject_pd %d: actual = %p \n", i, actual);

        if (actual == (void *) 0 || actual == vaddr) {
            return h;
        }

        // Wrong address:
        // Most likely, Address Space Layout Randomisation has given us an inhospitable layout,
        // e.g. libc where we want to have libjvm.
        // Terminate with a value that means caller should retry:
        exitForRetry();

        // dlclose and map/block.  Not successful.
        unload_sharedobject_pd(h);
        /* void *block = */ do_map_allocate_pd(actual, vaddr_alignment_pd());
    }

    if (actual != (void *) 0 && actual != vaddr) {
        warn("load_sharedobject_pd: %s: failed, loads at %p", name, actual);
        unload_sharedobject_pd(h);
        return (void *) -1;
    }

    return h;
}

/**
 * Experimental loading shared object by mmap, then fixing up.  Not fully implemented.
 * Fixing up the shared object and using dlopen is easier.
 */
void *load_sharedobject_mmap_pd(const char *filename, void *vaddr) {
    int loaded = 0;
    int fd = open(filename, O_RDONLY);
    if (fd < 0) {
        warn("load_sharedobject_mmap_pd: cannot open %s", filename);
        return (void *) -1;
    }
    // Read ELF header, find Program Headers.
    Elf64_Ehdr hdr;
    size_t e = read(fd, &hdr, sizeof(hdr));
    if (e < sizeof(hdr)) {
        warn("load_sharedobject_mmap_pd: failed to read ELF header %s: %ld", filename, e);
        return (void *) -1;
    }
    lseek(fd, hdr.e_phoff, SEEK_SET);
    // Read ELF Program Headers.  Look for PT_LOAD.
    Elf64_Phdr phdr;
    for (int i = 0; i < hdr.e_phnum; i++) {
        e = read(fd, &phdr, sizeof(phdr));
        if (e < sizeof(phdr)) {
            warn("load_sharedobject_mmap_pd: failed to read ELF Program Header %s: %ld", filename, e);
            return (void *) -1;
        }
        warn("load_sharedobject_mmap_pd: PH %d: type 0x%x flags 0x%x vaddr 0x%lx\n", i, phdr.p_type, phdr.p_flags, phdr.p_vaddr);
        if (phdr.p_type == PT_LOAD) {
            if (phdr.p_flags == (PF_X | PF_R) || phdr.p_flags == (PF_R | PF_W)) {
                // Expect a non-prelinked/relocated library, with zero base address.
                // Map PH at the given vaddr plus PH vaddr.
                uint64_t va = (uint64_t) vaddr + (uint64_t) phdr.p_vaddr;
                warn("load_sharedobject_mmap_pd: LOAD offset %lx vaddr %p \n", phdr.p_offset, (void *) va);
                void * a = do_mmap_pd((void *) va, (size_t) phdr.p_filesz, (char *) filename, fd, (size_t) phdr.p_offset);
                warn("load_sharedobject_mmap_pd: %s: %p\n", filename, a);
                if ((uint64_t) a > 0) {
                    warn("load_sharedobject_mmap_pd OK\n");
                    loaded++;
                }
            }
        }
    }
    // Not done yet. We need to do the job of the runtime linker.
    // Calls via pltgot need fixing for the new base address.
    // That includes a table of relocations into ourself, and that requires resolving from all other libraries.
    // A prelink-like edit of the binary is easier, and lets the runtime linker do this.
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
            warn("load_sharedobject_pd: %s: %s", name, dlerror());
            return (void *) -1;
        }
        return h;
    }
}

int unload_sharedobject_pd(void *h) {
    return dlclose(h); // zero on success
}

void copy_file_pd(const char *srcfile, const char *destfile) {
    int fd_src = open(srcfile, O_RDONLY, S_IRUSR);
    if (fd_src < 0) {
        error("Cannot open source file %s: %s", srcfile, strerror(errno));
    }
    int fd_dest = open(destfile, O_CREAT | O_WRONLY | O_TRUNC, S_IRUSR | S_IWUSR);
    if (fd_dest < 0) {
        error("Cannot open destination file %s: %s", destfile, strerror(errno));
    }
    ssize_t count = (ssize_t) file_size(srcfile);
    ssize_t e = sendfile(fd_dest, fd_src, 0, count);
    if (e != count) {
        warn("copy_file_pd: requested copy %ld bytes: got %s", count, strerror(errno));
    }
    close(fd_src);
    close(fd_dest);
}


const int N_JVM_SYMS = 2;
const char *JVM_SYMS[N_JVM_SYMS] = {
    SYM_REVIVE_VM, SYM_VM_RELEASE
};

int open_for_read(const char* filename) {
    int fd = open(filename, O_RDONLY);
    if (fd < 0) {
        warn("Cannot open %s: %s\n", filename, strerror(errno));
        return -1;
    }
    return fd;
}

int open_for_read_and_write(const char* filename) {
    int fd = open(filename, O_RDWR);
    if (fd < 0) {
        warn("Cannot open %s: %s\n", filename, strerror(errno));
        return -1;
    }
    return fd;
}

void close_file_descriptor(int fd, const char* name) {
    if (close(fd) < 0) {
        error("close_file_descriptor: %s", strerror(errno));
    }
}

bool create_directory_pd(char* dirname) {
    return mkdir(dirname, S_IRUSR | S_IWUSR | S_IXUSR) == 0;
}


/**
 * Create a "core.revival" directory containing what's needed to revive a corefile:
 *
 *  - A copy of libjvm.so, which this method then relocates to load at the same address as it was in the corefile
 *  - "core.mappings" a text file with instructions on which segments to load from the core
 *  - "jvm.symbols" a text file with information about important symbols in libjvm.so
 *
 * Also take a copy of libjvm.debuginfo if present.
 */
int create_revivalbits_native_pd(const char* corename, const char* javahome, const char* revival_dirname, const char* libdir) {

    {
        ELFFile core(corename, libdir);
		if (!core.is_core()) {
			error("Not a core file: %s", corename);
		}
        // Find JVM and its load address from core
		// Optionally find all library mappings.
        Segment* jvm_mapping = core.get_library_mapping(JVM_FILENAME);
        if (jvm_mapping == nullptr) {
            error("revival: cannot locate JVM from core.") ;
        }
        jvm_filename = strdup(jvm_mapping->name);
        jvm_address = (void*) jvm_mapping->vaddr;
        logv("JVM = '%s'", jvm_filename);
        logv("JVM addr = %p", jvm_address);

        // Create mappings file
        int mappings_fd = mappings_file_create(revival_dirname, corename);
        if (mappings_fd < 0) {
            // error already printed
            return -1;
        }
        core.write_mem_mappings(mappings_fd, "bin/java");
        close_file_descriptor(mappings_fd, "mappings file");
    }

    // Copy libjvm into core.revival dir
    char jvm_copy_path[BUFLEN];
    memset(jvm_copy_path, 0, BUFLEN);
    strncpy(jvm_copy_path, revival_dirname, BUFLEN - 1);
    strncat(jvm_copy_path, "/" JVM_FILENAME, BUFLEN - 1);
    logv("Copying libjvm.so from %s", jvm_filename);
    copy_file_pd(jvm_filename, jvm_copy_path);

    // Relocate copy of libjvm:
    {
        ELFFile jvm_copy(jvm_copy_path, nullptr);
        logv("Relocate copy of libjvm to %p", jvm_address);
        jvm_copy.relocate((unsigned long) jvm_address /* assume library currently has zero base address */);
        logv("Relocate copy of libjvm done");

        // Create symbols file
        int symbols_fd = symbols_file_create(revival_dirname);
        if (symbols_fd < 0) {
            warn("Failed to create symbols file\n");
            return -1;
        }
        logv("Write symbols");
        jvm_copy.write_symbols(symbols_fd, JVM_SYMS, N_JVM_SYMS);
        logv("Write symbols done");
        close_file_descriptor(symbols_fd, "symbols file");
    }

    // Copy libjvm.debuginfo if present
    char jvm_debuginfo_path[BUFLEN];
    char jvm_debuginfo_copy_path[BUFLEN];
    snprintf(jvm_debuginfo_path, BUFLEN, "%s", jvm_filename);
    char *p = strstr(jvm_debuginfo_path, ".so");
    if (p != nullptr) {
        snprintf(p, BUFLEN, ".debuginfo"); // Append to jvm_debuginfo_path
        if (file_exists_pd(jvm_debuginfo_path)) {
            snprintf(jvm_debuginfo_copy_path, BUFLEN - 1, "%s/libjvm.debuginfo", revival_dirname);
            copy_file_pd(jvm_debuginfo_path, jvm_debuginfo_copy_path);
        }
    }

    logv("create_revivalbits_native_pd returning %d", 0);
    return 0;
}

