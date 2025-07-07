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

#ifndef MACOSX
using namespace std;
#endif


#include "revival.hpp"

// Behaviour settings:
// Diagnostics
int verbose = false;

// Unmap segments before mapping them.
// Was true on aarch64 (needs re-testing).
int unmapFirst = false;

int mapWrite = false;

int _wait;           // set from env: REVIVAL_WAIT
int _abortOnClash = false;

// Set during revive_image:
char *core_filename;
int core_fd;
const char *revivaldir;

// Set during process revival prep:
char *jvm_filename = nullptr;
void *jvm_address = nullptr;

// Set during actual revival:
void *h; // handle to libjvm
void *revivalthread;
void *_tty;

std::list<Segment> writableSegments;
std::list<Segment> failedSegments;

address align_down(address ptr, uint64_t mask) {
    return ptr & ~mask;
}

address align_up(address ptr, uint64_t mask) {
    return (ptr & ~mask) + mask + 1;
}


#if defined(LINUX) || defined(__APPLE__)
pthread_key_t _pthread_key;
#endif /* LINUX */


void waitHitRet() {
    if (_wait) {
        printf("hit return");
        getchar();
    }
}

/**
 * Return true if vaddr range v1, v2 looks dangerous to map/unmap,
 * given t1, t2 describe an address range in use.
 */
int clash(address v1, address v2, address t1, address t2) {
    // Region v1, v2 surrounds region t1,t2:
    if (v1 <= t1 && v2 >= t2) {
        return true;
    }
    // Either end of region v1, v2 is inside region t1, t2:
    if ((v2 > t1 && v2 < t2)
            || (v1 > t1 && v1 < t2)) {
        return true; 
    }
    // No clash:
    return false;
}

int dangerous0(void *vaddr, unsigned long long length, address xaddr) {
    address v1 = (address) vaddr;
    address v2 = v1 + length;
    address t1 = align_down(xaddr, 0xffffff);
    address t2 = align_up(xaddr, 0xffffff);
    if (clash(v1, v2, t1, t2)) {
        return true;
    }
    return false;
}

/**
 * dangerous
 * Return true if the given vaddr, length appear dangerous to unmap/remap,
 * e.g. that range is in use the by calling stack.
 * Also possible that the address contains the current program.
 */
const char * dangerous(void *vaddr, unsigned long long length) {
    // Check against a local variable (on stack):
    int x;
    if (dangerous0(vaddr, length, (uint64_t) &x)) {
        return "conflict with local/stack";
    }
    // Check against this code:
    if (dangerous0(vaddr, length, (uint64_t) &dangerous)) {
        return "conflict with this code";
    }
#ifdef LINUX
    if (dangerous0(vaddr, length, (uint64_t) &mmap)) {
        return "conflict mmap";
    }
    if (dangerous0(vaddr, length, (uint64_t) &gettimeofday)) {
        return "conflict gettimeofday";
    }
#endif
#ifdef WINDOWS
    // consider testing a standard Windows library symbol..,.
#endif
    return nullptr;
}

/**
 * Create a memory mapping, at some virtual address, directly from a file/offset/length.
 * Return -1 on failure.
 */
int revival_mapping_mmap(void *vaddr, size_t length, off_t offset, int lines, char *sourceBinary, int fd) {
    int e = 0;
    if (verbose) {
        printf("  revival_mapping_mmap: map %d: " PTR_FORMAT " (to " PTR_FORMAT ") len=0x%zx fileoffset=0x%llx\n",
                lines, (uintptr_t) vaddr, (uintptr_t) ((uint64_t) vaddr + length), length, (long long) offset);
    }

    if (unmapFirst) {
        if (verbose) {
            printf("  revival_mapping_mmap: try UNMAP %p len=0x%zx\n", vaddr, length);
        }
        e = do_munmap_pd(vaddr, length);
        if (e) {
            printf("  revival_mapping_mmap: unmap %d failed: vaddr %p: returns: %d\n", lines, vaddr, e);
        }
    }

    // Mapping:
    void *mapped_addr = do_mmap_pd(vaddr, length, sourceBinary, fd, offset);

    if (mapped_addr != vaddr && mapped_addr != (void *) align_down((address) vaddr, vaddr_alignment_pd())) {
        if (verbose) {
            printf("  revival_mapping_mmap: line %d: mapping failed: wanted vaddr: %p returned: %p\n", lines, vaddr, mapped_addr);
        }
        e = -1;
    } else {
        if (verbose) {
            printf("  revival_mapping_mmap: line %d: mapping OK %p - %p\n", lines, vaddr, (void *) ((uint64_t) vaddr + length));
        }
        e = 0;
    }
#ifdef WINDOWS
    // Windows: alignment is more difficult as vaddr and file offset are aligned.
    // Fallback to copying.
    if (e) {
        e = revival_mapping_copy(vaddr, length, offset, true, core_filename, core_fd);
        if (verbose) {
            printf("  revival_mapping_mmap: mapping retry using copy returns: %d\n", e);
        }
    }
#endif

    return e;
}

/**
 * revival_mapping_copy
 *
 * Create a memory mapping, by allocating memory at an address, then copying bytes from some offset in a file.
 * Used when a mapping cannot be performed directly from the file, usually due to alignment problems
 * (so expect file offset to not be aligned).
 *
 * Return -1 on error.
 */
int revival_mapping_copy(void *vaddr, size_t length, off_t offset, bool allocate, char *filename, int fd) {
    int e = 0;
    if (verbose) {
        printf("  revival_mapping_copy: alloc=%d vaddr " PTR_FORMAT " - " PTR_FORMAT " len=" SIZE_FORMAT_X_0 " from file offset 0x%llx\n",
                allocate, (uintptr_t) vaddr, (uintptr_t) ((address) vaddr + length), length, (long long) offset);
    }
    // Need to create a mapping:
    if (allocate) {
        // Alignment: align address down, if address changes then extend mapping length.
        //
        // vaddr is from core.mappings, and should not really be misaligned, BUT on Windows it may not be 64k aligned.
        // As only Windows affected by that, check alignment in do_map_allocate_pd called below.

        address vaddrAligned = align_down((address) vaddr, vaddr_alignment_pd());
        size_t alignedLength = length;
        if (vaddrAligned != (address) vaddr) {
            alignedLength = length + ((unsigned long long) vaddr - vaddrAligned);
            // Update file offset? No, we will copy to the originally requested addr from original file offset.
            if (verbose) {
                printf("  revival_mapping_copy: vaddr was not aligned");
            }
        }
        // Minimum length:
        if (alignedLength < vaddr_alignment_pd()) {
            alignedLength = vaddr_alignment_pd() + 1;
        }
        if (verbose) {
            printf("  revival_mapping_copy: allocate vaddr = 0x%p vaddrAligned = 0x%p vaddr_alignment = 0x%lx alignedLength = 0x%lx\n",
                    vaddr, (void *) vaddrAligned, (unsigned long) vaddr_alignment_pd(), (unsigned long) alignedLength);
        }

        // Create allocation:
        void *newAddr = do_map_allocate_pd((void *) vaddr, length);
        if (newAddr != vaddr) {
            // Was it an alignment change?
            if (align_down((address) vaddr, vaddr_alignment_pd()) == (address) newAddr) {
                if (verbose) {
                    printf("  revival_mapping_copy: accepting aligned down address %p\n", newAddr);
                }
            } else {
                printf("  revival_mapping_copy: cannot allocate at vaddr 0x%p (0x%p), got 0x%p\n", vaddr, (void *) vaddrAligned, newAddr);
                // Already allocated.  So can we copy?  IF we can find out how much is mapped, and if write enabled.

                waitHitRet();
                return -1;
            }
        }
    }
    // Check permission
    if (!mem_canwrite_pd(vaddr, length)) {
        printf("  revival_mapping_copy: cannot write at vaddr 0x%p\n", vaddr);
        return -1;
    }

    // Copy data to allocation:
    FILE *f = fopen(filename, "rb");
    if (!f) {
        printf("cannot open: '%s': %d: %s\n", filename, errno, strerror(errno));
        return -1;
    }
    e = fseek(f, offset, SEEK_SET);
    if (e != 0) {
        printf("cannot seek '%s' to offset %llx: returns %d: %d: %s\n", filename, (long long) offset, e, errno, strerror(errno));
        fclose(f);
        return -1;
    }
    // Copy bytes from file offset to vaddr (not to a changed/aligned vaddr):
    int *p = (int*) vaddr;
    // printf("map test\n");
    *p = 123;
    // printf("map test done\n");
    int value;
    for (size_t i = 0; i < length/4; i++) {
        e = (int) fread(&value, 4, 1, f);
        if (e != 1) {
            printf("COPY fread failed: returns %d at %p pos=%zu : %d %s\n", e, p, i, errno, strerror(errno));
            break;
        }
        *p++ = value;
    }
    fclose(f);
    if (verbose) {
        printf("  revival_mapping_copy: done, copied %zd.\n", length);
    }
    // Return 0 for success, not fread result from above.
    return 0;
}


/**
 * Load a shared library, using directory name and library name, at the given address.
 * Returns the value from load_sharedobject_pd(), which is an opaque handle (not the address), or -1 for error.
 */
void *load_sharedlibrary_fromdir(const char *dirname, const char *libname, void *vaddr, char *sum) {
    char buf[BUFLEN];
    snprintf(buf, BUFLEN, "%s/%s", dirname, libname); 
    if (verbose) {
        printf("load_sharedlibrary_fromdir: %s\n", buf);
    }

    void *a = load_sharedobject_pd(buf, vaddr);

    if (verbose) {
        printf("load_sharedobject_pd: %s: returns %p\n", buf, a);
    }
    return a;
}


/**
 * Read and process the "core.mappings" file.
 * The file contains the name of the core file to open.
 * Map (revive) memory segments described by the file, into the current process.
 */
int mappings_file_read(const char *corename, const char *dirname, const char *mappings_filename) {
    int e = 0;
    char s1[BUFLEN];
    char s2[BUFLEN];
    int lines = 0;
    int m_good = 0;
    int m_bad = 0;
    memset(s1, 0, BUFLEN);

    FILE *f = fopen(mappings_filename, "r"); 
    if (!f) {
        printf("cannot open: '%s': %s\n", mappings_filename, strerror(errno));
        return -1;
    }
    // corefile details:
    e = fscanf(f, "core %s %s\n", s1 /* core filename */, s2 /* length */); 
    if (e != 2) {
        printf("mappings_file_read: unrecognised header in: %s\n", mappings_filename);
        return -1;
    }
    lines++;
    // Do not compare names: core_filename does not have to match, cores can be renamed.
    // Compare size: this should match.
    unsigned long long parsedSize = strtoull(s2, nullptr, 10);
    struct stat sb;
    // Check if Linux needs lstat here, Windows uses stat:
    if (stat(core_filename, &sb) == -1) {
        printf("cannot stat '%s': %d: %s\n", core_filename, errno, strerror(errno));
        return -1;
    }
    if (verbose || (unsigned long long) sb.st_size != parsedSize) {
        printf("%s: revivaldata recorded core size %lld, actual file size %lld\n", core_filename, parsedSize, (long long) sb.st_size);
    }
    if ((unsigned long long) sb.st_size != parsedSize) {
        return -1;
    }
    // Consider a checksum.

    // time:
    // time of crash or core file generation.  millis since epoch.
    e = fscanf(f, "time %s\n", s1);
    if (e == 1) {
        long long coretime = (long long) strtoll(s1, nullptr, 10);
        if (verbose) {
            printf("core time: %lld\n", coretime);
        }
    }
    lines++;

    // Linux needs an fd to pass to mmap.  Windows will pass a filename.
    int core_fd = -1;
#ifdef LINUX
    core_fd = open(core_filename, (mapWrite ? O_RDWR : O_RDONLY));
    if (core_fd < 0) {
        printf("%s: %s", core_filename, strerror(errno));
        return -1;
    }
#endif

    if (verbose) {
        pmap_pd(); // Show our new pmap.  Only implemented on Linux, using system() for now.
    }

    // Read and process the mappings:
    while (1) {
        lines++;
        s1[0] = '\0';
        char s3[BUFLEN];
        char s4[BUFLEN];
        char s5[BUFLEN];
        char s6[BUFLEN];
        char s7[BUFLEN];
        e = fscanf(f, "L %s %s %s\n", s1, s2, s3);
        if (e == 3) {
            void *vaddr = (void *) strtoull(s2, nullptr, 16);
            //if (verbose) {
            fprintf(stderr, "Load library '%s' required at %p...\n", s1, vaddr);
            //}
            h = load_sharedlibrary_fromdir(dirname, s1, vaddr, s3);
            if (verbose) {
                printf("load_sharedlibrary_fromdir returns: %p\n", h);
            }
            if (h == (void *) -1) {
                fprintf(stderr, "Load library '%s' failed to load at %p\n", s1, vaddr);
                return -1;
            }
            continue;
        }
        e = fscanf(f, "TLS %s %s\n", s1, s2); 
        if (e == 2) {
#ifdef WINDOWS
            void *tls_addr = (void *) strtoull(s1, nullptr, 16);
            tls_fixup_pd(tls_addr);
#else
            printf("TLS line invalid on non-Windows.\n");
#endif
            continue;
        }
        e = fscanf(f, "%s %s %s %s %s %s %s\n", s1, s2, s3, s4, s5, s6, s7);
        if (e == 7) {
            // virtual address, virtual address end, source file offset, source file mapping size, length in memory, RWX
            //  s2                s3                  s4                  s5                          s6             s7
            char *endptr;
            void *vaddr = (void *) strtoull(s2, &endptr, 16);
            size_t length = strtoul(s6, &endptr, 16);
            off_t offset = strtoul(s4, &endptr, 16);
            size_t length_file = strtoul(s5, &endptr, 16);
            const char *danger = dangerous(vaddr, length);
            if (danger != nullptr) {
                printf("skipping (%s): %p - %p len=%zx\n", danger, vaddr, (void*) ((unsigned long long) vaddr + length), length);
                Segment* thisSeg = new Segment(vaddr, length, offset, length_file);
                failedSegments.push_back(*thisSeg);
                if (_abortOnClash) {
                    abort();
                }
                continue;
            } 
            if (strstr(s7, "W") != nullptr) {
                // Write permission: add to record of writable Segments:
                Segment* thisSeg = new Segment(vaddr, length, offset, length_file);
                writableSegments.push_back(*thisSeg);
            } 
            // Most mapping lines are "M": map from core.
            if (strncmp(s1, "M", 1) == 0) { 
                int e = revival_mapping_mmap(vaddr, length, offset, lines, core_filename, core_fd);
                if (e == -1) {
                    m_bad++;
                } else {
                    m_good++;
                }
            } else if (strncmp(s1, "m", 1) == 0) {
                // Allocate only, file offset/length not used:
                void *e = do_map_allocate_pd(vaddr, length);
                if (e != vaddr) {
                    m_bad++;
                } else {
                    m_good++;
                }
            } else if (strncmp(s1, "CA", 2) == 0) { 
                // Copy with allocation:
                revival_mapping_copy(vaddr, length, offset, true, core_filename, core_fd);
            } else if (strncmp(s1, "C", 1) == 0) { 
                // Copy, no allocation needed:
                revival_mapping_copy(vaddr, length, offset, false, core_filename, core_fd);
            } else {
                // Not recognised:
                printf("mappings_file_read: unrecognised line %d: '%s'", lines, s1);
            }
            continue;
        } 
        if (strlen(s1) > 0) {
            printf("mappings_file_read: unrecognised line (2) %d: '%s'\n", lines, s1);
        }
        break;
    }
    if (verbose) {
        printf("mappings_file_read: read %d lines, mappings: %d good, %d bad.\n", lines, m_good, m_bad);
        printf("writableSegments.size = %d\n", (int) writableSegments.size());
    }
    if (core_fd >= 0) {
        close(core_fd);
    }
    fclose(f);

    if (verbose) {
        pmap_pd(); // Show our new pmap, Linux only impl.
    }

    waitHitRet();
    return 0;
}

/*
 * Lookup a symbol in core.symbols.
 * Read and process a core.symbols file in a given direcory.
 */
void *symbol0(const char *dirname, const char *sym) {
    char buf[BUFLEN];
    snprintf(buf, BUFLEN, "%s/%s", dirname, "core.symbols"); 
    int e = 0;
    void *addr = (void *) -1;
    FILE *f = fopen((char*)&buf, "r"); 
    if (!f) {
        return (void *) -1;
    }

    char s1[BUFLEN];
    char s2[BUFLEN];
    char s3[BUFLEN];
    while (1) {
        e = fscanf(f, "%s %s %s\n", s1, s2, s3);  // symbol address contents
        if (e < 3) {
            break;
        }
        if (strncmp(s1, sym, BUFLEN) == 0) {
            char *endptr;
            addr = (void*) strtoll(s2, &endptr, 16);
            break;
        } 
    }
    if (verbose) {
        printf("symbol: %s = %p (contained %s)\n", sym, addr, s3);
    }
    fclose(f);
    return addr;
}


void *symbol_deref(const char *sym) {
    void *s = symbol(sym);
    if (s != (void*) -1) {
        s = (void *) (*(intptr_t*) s);
    }
    return s;
}

/**
 * symbol 
 * Lookup a symbol, return as a void * or (void *) -1 on failure.
 *
 * Try symbol.mappings first, then a live, platform-specific lookup.
 * Using platform-specific lookups such as dlsym() are not expected
 * to work for private symbols.
 */
void *symbol(const char *sym) {

    if (!revivaldir) {
        printf("symbol: call revive_image first.\n");
        return (void *) -1;
    }
    void *s = symbol0(revivaldir, sym);
    if (s == (void *) -1) {
        // Lookup e.g. with dlsym:
        s = symbol_dynamiclookup_pd(h, sym);
    }
    if (s == (void *) -1) {
        // Lookup e.g. with dlsym:
        s = symbol_dynamiclookup_pd(h, sym);
    }
    return s;
}

void verbose_call(void *p) {
    if (verbose) {
        printf("symbol call: %p\n", p);
    }
}

/**
 * Resolve a symbol from core.symbols, and call it.
 * Use the symbol() function which will try using core.symbols first,
 * then a live lookup.
 * Return any value as a void *, and caller will ignore an unwanted return value.
 */
void *symbol_call(const char *sym) {
    void *s = symbol(sym);
    if (s == (void*) -1) {
        return (void*) -1;
    }
    verbose_call(s);
    void *(*func)() = (void*(*)()) s;
    return (func)();
}

/**
 * Resolve and call with 1 argument...
 */
void *symbol_call1(const char *sym, void *arg) {
    void *s = symbol(sym);
    if (s == (void*) -1) {
        return (void*) -1;
    }
    verbose_call(s);
    void* (*func)(void*) = (void*(*)(void*)) s;
    return (func)(arg);
}

void *symbol_call2(const char *sym, void *arg1, void *arg2) {
    void *s = symbol(sym);
    if (s == (void*) -1) {
        return (void*) -1;
    }
    verbose_call(s);
    void* (*func)(void*,void*) = (void*(*)(void*,void*)) s;
    return (func)(arg1, arg2);
}

void *symbol_call3(const char *sym, void *arg1, void *arg2, void *arg3) {
    void *s = symbol(sym);
    if (s == (void*) -1) {
        return (void*) -1;
    }
    verbose_call(s);
    void* (*func)(void*,void*,void*) = (void*(*)(void*,void*,void*)) s;
    return (func)(arg1, arg2, arg3);
}

void *symbol_call4(const char *sym, void *arg1, void *arg2, void *arg3, void *arg4) {
    void *s = symbol(sym);
    if (s == (void*) -1) {
        return (void*) -1;
    }
    verbose_call(s);
    void* (*func)(void*,void*,void*,void*) = (void*(*)(void*,void*,void*,void*)) s;
    return (func)(arg1, arg2, arg3, arg4);
}

void *symbol_call5(const char *sym, void *arg1, void *arg2, void *arg3, void *arg4, void *arg5) {
    void *s = symbol(sym);
    if (s == (void*) -1) {
        return (void*) -1;
    }
    verbose_call(s);
    void* (*func)(void*,void*,void*,void*,void*) = (void*(*)(void*,void*,void*,void*,void*)) s;
    return (func)(arg1, arg2, arg3, arg4, arg5);
}

/**
 * Resolve a symbol, and store the given value in that location.
 */
int symbol_set(const char *sym, void *value) {
    void *s = symbol(sym);
    if (s == (void*) -1) {
        return -1;
    }
    *(unsigned long long*) s = (unsigned long long) value;
    return 0;
}

int symbol_set(const char *sym, int value) {
    void *s = symbol(sym);
    if (s == (void*) -1) {
        return -1;
    }
    *(int*) s = value;
    return 0;
}


void write(int fd, const char *buf) {
    size_t len = strlen(buf);
    fprintf(stderr, "%s", buf); // temp echo to stderr
    int e = write(fd, buf, len);
    if (e < 0) {
        fprintf(stderr, "Write failed: %s\n", strerror(errno));
    } else if (e != (int) len) {
        fprintf(stderr, "Write failed: written %d buf %d.\n", e, (int) len);
    }
}

int mappings_file_create(const char *dirname, const char *corename, const char *checksum,  unsigned long long time) {
// core FILENAME
// time 123213123
// L jvm addresshex
// 
// Mappings to be written separately.
    fprintf(stderr, "XXXX mappings_file_create\n");

    char buf[BUFLEN];
    snprintf(buf, BUFLEN, "%s%s", dirname, "/core.mappings"); 
    if (verbose) {
        fprintf(stderr, "mappings_file_create: %s\n", buf);
    }
#ifdef WINDOWS
    int fd = _open(buf, _O_CREAT | _O_WRONLY | _O_TRUNC, _S_IREAD | _IWRITE);
#else
    int fd = open(buf, O_CREAT | O_WRONLY | O_TRUNC, S_IRUSR | S_IWUSR);
#endif
    if (fd < 0) {
        fprintf(stderr, "mappings_file_create: %s: %s\n", buf, strerror(errno));
        return fd;
    }
    snprintf(buf, BUFLEN, "core %s %s\n", basename((char *) corename), checksum);
    write(fd, buf);
    snprintf(buf, BUFLEN, "time %lld\n", time);
    write(fd, buf);
    snprintf(buf, BUFLEN, "L %s %llx\n", basename(jvm_filename), (unsigned long long) jvm_address);
    write(fd, buf);
    return fd;
}

int mappings_file_write(int fd, Segment seg) {
    // PRS_TODO
    return 0;
}

// REMOVE:
int revive_common() {
    // ThreadCritical: check if fixup still needed.
    //  tc_owner which needs to be us, or some operations can block.
    // Testing without this to see if the deadlock can still happen.
    /*  void *s;
        s = symbol(SYM_TC_OWNER);
        if (s == (void*) -1) {
        printf("Cannot lookup tc_owner.\n");
        } else {
        uint64_t *tc = (uint64_t*) s;
        if (verbose) {
        printf("Found  tc_owner = %p -> %p\n", tc, (void *) *tc);
        }

#ifdef LINUX
     *tc = pthread_self();
#else 
     *tc = 0;
#endif
if (verbose) {
printf("Patched tc_owner = %p -> %p\n", tc, (void *) *tc);
}
} */

// Changes in revival to reset tty mean this is not required:
// glibc: set IO_accept_foreign_vtables = 1 to aovid asserts.
// symbol_set("IO_accept_foreign_vtables", 1);

return 0;
}

/**
 * Return true if the given character pointer is a valid, set
 * environment variable (one which exists and is not null).
 */
bool env_check(char *s) {
    char *env = getenv(s);
    if (env != nullptr && strlen(env) > 0) {
        return true;
    }
    return false;
}


/**
 * Attempt to complete the revival using a helper method in the target JVM.
 */
int revive_image_cooperative() {
    // Check for the helper method:
    void *s = symbol(SYM_REVIVE_VM);
    if (s == (void*) -1) {
        fprintf(stderr, "revive: JVM helper function not found.");
        return false;
    }  

    // Call the VM helper method, save the VMThread pointer it returns.
    if (verbose) fprintf(stderr, "calling revival helper %p\n", s);
    void*(*helper)() = (void*(*)()) s;
    revivalthread = (helper)();
    if (verbose) {
        fprintf(stderr, "revive_image: JVM returned VM Thread object = %p\n", revivalthread);
    }
    if ((long long) revivalthread == 0) {
        fprintf(stderr, "revive_image: JVM helper failed\n");
        return false;
    } 
    return true;
}


/**
 * create_revivalbits
 *
 * Create and populate the revival data directory.
 *
 * Return zero on success
 */
int create_revivalbits(const char *corename, const char *javahome, const char *dirname, const char *libdir) {

    // make core.revival dir
    // find libjvm and its load address from core
    // copy libjvm into .revival dir
    // relocate copy of libjvm
    // read core file to create core.mappings file

    int e = create_revivalbits_native_pd(corename, javahome, dirname, libdir);

    return e;
}

/**
 * Create revivaldir name from corefile name.
 */
char *revival_dirname(const char *corename) {
    char *dirname = (char *) calloc(1, BUFLEN);
    if (dirname) {
        strncpy(dirname, corename, BUFLEN - 1);
        strncat(dirname, ".revival", BUFLEN - 1);
    }
    return dirname;
}

/**
 * revive_image
 * 
 * Main revival setup entry point.
 *
 * Given a core file name, create mappings data if necessary, and 
 * revive the process.
 */
int revive_image(const char *corename, const char *javahome, const char *libdir) {
    int e;
    char buf[BUFLEN];
    char *dirname;

    verbose = env_check((char *) "REVIVAL_VERBOSE");
    _wait = env_check((char *) "REVIVAL_WAIT");
    _abortOnClash = env_check((char *) "REVIVAL_ABORT");

    init_pd();

    if (corename == nullptr) {
        printf("revive_image: core file name required.\n");
        fprintf(stderr, ">>> revive_image FAIL 1\n");
        return -1;
    }
    if (revivaldir || revivalthread) {
        printf("revive_image: already called.\n");
        fprintf(stderr, ">>> revive_image FAIL 2\n");
        return -1;
    }
    // Record our copy of core file name:
    core_filename = strdup(corename);
    if (core_filename == nullptr) {
        printf("revive: alloc copy of core_filename failed.\n");
        fprintf(stderr, ">>> revive_image FAIL 3\n");
        return -1;
    }

    // Check core file exists:
    e = open(core_filename, O_RDONLY);
    if (e < 0) {
        fprintf(stderr, "revive_image: open '%s' failed: %d: %s\n", core_filename, errno, strerror(errno));
        return e;
    }
    close(e);

    dirname = revival_dirname(corename);
    if (dirname == nullptr) {
        fprintf(stderr, "revive_image: failed to allocate dirname.\n");
        return -1;
    }
    if (verbose) {
        printf("revive_image:\n");
    }
    if (verbose) {
        printf("revival directory: '%s'\n", dirname);
        printf("vaddr_alignment = %llu\n", (unsigned long long) vaddr_alignment_pd());
        printf("check if revivaldir exists? %s = %d\n",( const char *) dirname, revival_direxists_pd(dirname));
    }

    // Does revival data directory exist? If not, create data:
    if (!revival_direxists_pd(dirname)) {
        e = create_revivalbits(corename, javahome, dirname, libdir);
        if (verbose) {
            printf("revive_image: create_revivalbits return code: %d\n", e);
        }
        if (e < 0) {
            fprintf(stderr, "revive_image: create_revivalbits failed.  Return code: %d\n", e);
            return e;
        }
    }

    if (revival_checks_pd(dirname) < 0) {
        fprintf(stderr, "revive_image: revival_checks failed: %s\n", dirname);
        return -1;
    }

    // Thread specific data:
    // Previously, Linux loaded libjvm.so NOW, before calling pthread_key_create.
    // Unnecessary.  Windows was OK when NOT loading jvm.dll here.
#if defined(LINUX) || defined(__APPLE__)
    // h = load_sharedlibrary_fromdir(dirname, LIBJVM_NAME, 0, nullptr);
    e = pthread_key_create(&_pthread_key, nullptr);
    int pksize = sizeof(_pthread_key);
    if (verbose) {
        fprintf(stderr, "pthread_key_create: result %d\n", e);
        fprintf(stderr, "pthread_key size = %d\n", pksize); 
    }
#endif
#ifdef WINDOWS
    _thread_key = TlsAlloc();
    if (verbose) {
        fprintf(stderr, "TlsAlloc: thread_key = %d\n", _thread_key); 
    }
#endif

    snprintf(buf, BUFLEN, "%s%s", dirname, "/core.mappings"); 
    e = mappings_file_read(corename, dirname, buf);
    if (e < 0) {
        fprintf(stderr, "revive_image: mappings_file_read failed: %d\n", e);
        return -1;
    }
    revivaldir = dirname;

    if (false) {
        // Query JVM version: this was done as a sanity check.
        uint64_t jdkvi;
        void * s = symbol(SYM_JVM_VERSION);
        if (s == (void*) -1) {
            fprintf(stderr, "Error: revival: failed symbol lookup for " SYM_JVM_VERSION "\n");
            return -1; // fatal?
        } else {
            int(*func)() = (int(*)()) s;
            jdkvi = (*func)();  
            int jvmMajor = (int) (jdkvi >> 24);
            if (verbose) {
                printf("jvm_version = %p\n", (void*)jdkvi);
                printf("jvm major version = %d\n", jvmMajor);
            }
        }
    }

    unsigned long * _jvm_thread_key = (unsigned long *) symbol(SYM_THREAD_KEY);
    if (verbose) {
        printf("JVM's _thread_key = %p\n", _jvm_thread_key);
    }
    //int _jvm_thread_key_int = *_jvm_thread_key;

#ifdef LINUX
    // Install signal handler on Linux before revival:
    install_handler();
#endif

    e = revive_image_cooperative();
    if (!e) {
        printf("revival: failed: %d\n", e);
    }
    if (verbose) {
        fprintf(stderr, "revive_image: OK\n");
    }
    return e;
}

void *revived_vm_thread() {
    if (!revivaldir || !revivalthread) {
        printf("revived_vm_thread: call revive_imagefirst.\n");
        return nullptr;
    }
    return revivalthread;
}

void *revival_tty_existing() {
    if (!revivaldir) {
        printf("revival_tty: call revive_image first.\n");
        return nullptr;
    }
    if (!_tty) {
        if (verbose) {
            printf("Resolving tty... ");
        }
        void *tty1 = symbol(SYM_TTY);
        unsigned long long tty2 = * (unsigned long long *) tty1;
        _tty = (void*) tty2;
        if (verbose) {
            printf("%p\n", _tty);
        }
    }
    return _tty;
}


void *revived_tty() {
    if (!revivaldir) {
        printf("revival_tty: call revive_image first.\n");
        return nullptr;
    }
    if (!_tty) {
        if (verbose) printf("Resolving tty...");
        void *tty1 = symbol(SYM_TTY);
        if (tty1 == (void*) -1) {
            printf("Failed to resolve symbol '%s'\n", SYM_TTY);
        } else {
            unsigned long long tty2 = * (unsigned long long *) tty1;
            _tty = (void*) tty2;
            if (verbose) printf("tty = %p\n", _tty);
        }
    }
    return _tty;
}


int revival_dcmd(const char *command) {
    void *s = symbol(SYM_PARSE_AND_EXECUTE);
    if (s == (void*) -1) {
        printf("revival: symbol lookup failed: " SYM_PARSE_AND_EXECUTE "\n");
        return -1;
    }
    if (revived_tty() == (void*) 0L) {
        // null tty will cause a crash during DCmd output.
        printf("revival: tty failed.\n");
        return -1;
    }
    int(*dcmd_parse)(int, void*, const char*, char, void*) = (int(*)(int, void*, const char *, char, void*)) s;
    if (verbose) printf("dcmd_parse: '%s'\n", command);
    (dcmd_parse)(DCMD_SOURCE, revived_tty(), command, ' ', revived_vm_thread());

    return 0;
}

