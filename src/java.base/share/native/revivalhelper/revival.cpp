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
int unmapFirst = false; // was used in testing, likely remove

int openCoreWrite = false;

int _wait;           // set from env: REVIVAL_WAIT
int _abortOnClash = false;

// Set during revive_image:
char *core_filename;
unsigned long long core_timestamp;
int core_fd;
const char *revivaldir;


// Set during actual revival:
void *h; // handle to libjvm
struct revival_data* rdata;

std::list<Segment> writableSegments;
std::list<Segment> failedSegments;


// Revival prep state:
char *jvm_filename = nullptr;
void *jvm_address = nullptr;
std::list<Segment> avoidSegments;

void exitForRetry() {
    _exit(7);
}

address align_down(address ptr, uint64_t mask) {
    return ptr & ~mask;
}

address align_up(address ptr, uint64_t mask) {
    return (ptr & ~mask) + mask + 1;
}


void write0(int fd, const char *buf) {
    size_t len = strlen(buf);
    int e = (int) write(fd, buf, (unsigned int) len);
    if (e < 0) {
        fprintf(stderr, "revival write: Write failed: %s\n", strerror(errno));
    } else if (e != (int) len) {
        fprintf(stderr, "revival write: Write failed: written %d buf %d.\n", e, (int) len);
    }
}

void writef(int fd, const char *format, ...) {
    char buffer[BUFLEN];
    memset(buffer, 0, BUFLEN);
    va_list args;
    va_start(args, format);
    vsnprintf(buffer, BUFLEN - 1, format, args);
    va_end(args);
    write0(fd, buffer);
}

void log0(const char *msg) {
    // Add timestamp and newline to message, write on stderr.
    char buffer[BUFLEN];
#ifndef WINDOWS
    struct timeval t;
    gettimeofday(&t, nullptr);
    snprintf(buffer, BUFLEN - 1, "%ld.%ld: %s\n", t.tv_sec, (long) t.tv_usec, msg);
#else
    // TODO timestamp on Windows
    snprintf(buffer, BUFLEN - 1, "%s\n", msg);

#endif
    write0(2 /* stderr */, buffer);
}

void log(const char *format, ...) {
    char buffer[BUFLEN];
    memset(buffer, 0, BUFLEN);
    va_list args;
    va_start(args, format);
    vsnprintf(buffer, BUFLEN - 1, format, args);
    va_end(args);
    log0(buffer);
}

void logv(const char* format, ...) {
    if (verbose) {
        char buffer[BUFLEN];
        memset(buffer, 0, BUFLEN);
        va_list args;
        va_start(args, format);
        vsnprintf(buffer, BUFLEN - 1, format, args);
        va_end(args);
        log0(buffer);
    }
}

void warn(const char *format, ...) {
    char buffer[BUFLEN];
    memset(buffer, 0, BUFLEN);
    va_list args;
    va_start(args, format);
    vsnprintf(buffer, BUFLEN - 1, format, args);
    va_end(args);
    write0(2 /* stderr */, buffer);
    write0(2, "\n");
}

void error(const char *format, ...) {
    char buffer[BUFLEN];
    memset(buffer, 0, BUFLEN);
    va_list args;
    va_start(args, format);
    vsnprintf(buffer, BUFLEN - 1, format, args);
    va_end(args);
    write0(2 /* stderr */, buffer);
    write0(2, "\n");
    exit(1);
}


void waitHitRet() {
    if (_wait) {
        warn("hit return");
        getchar();
    }
}

/**
 * Return the file size in bytes, or zero on error.
 */
unsigned long long file_size(const char *filename) {
    struct stat sb;
    if (stat(filename, &sb) == -1) {
       warn("cannot stat '%s': %d: %s", filename, errno, strerror(errno));
       return 0;
   }
   return (long long) sb.st_size;
}

/**
 * Return the file modification time in seconds, or 0 on error.
 */
unsigned long long file_time(const char *filename) {
    struct stat sb;
    if (stat(filename, &sb) == -1) {
       warn("cannot stat '%s': %d: %s", filename, errno, strerror(errno));
       return 0;
   }
   return (long long) sb.st_mtime;
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
int revival_mapping_mmap(void *vaddr, size_t length, off_t offset, int lines, char *filename, int fd) {
    int e = 0;
    logv("  revival_mapping_mmap: map %d: " PTR_FORMAT " (to " PTR_FORMAT ") len=0x%zx fileoffset=0x%llx\n",
         lines, (uintptr_t) vaddr, (uintptr_t) ((uint64_t) vaddr + length), length, (long long) offset);

    if (unmapFirst) {
        logv("  revival_mapping_mmap: try UNMAP %p len=0x%zx\n", vaddr, length);
        e = do_munmap_pd(vaddr, length);
        if (e) {
            warn("  revival_mapping_mmap: unmap %d failed: vaddr %p: returns: %d\n", lines, vaddr, e);
        }
    }

    // Mapping:
    void *mapped_addr = do_mmap_pd(vaddr, length, filename, fd, offset);

    // Accept the wanted address, or if it was aligned-down:
    if (mapped_addr != vaddr && mapped_addr != (void *) align_down((address) vaddr, vaddr_alignment_pd())) {
        logv("  revival_mapping_mmap: line %d: mapping failed: wanted vaddr: %p returned: %p\n", lines, vaddr, mapped_addr);
        e = -1;
    } else {
        logv("  revival_mapping_mmap: line %d: mapping OK %p - %p\n", lines, vaddr, (void *) ((uint64_t) vaddr + length));
        e = 0;
    }
#ifdef WINDOWS
    // Windows: alignment is more difficult as vaddr and file offset must be aligned.  Fallback to alloc and copy:
    if (e) {
        logv("  revival_mapping_mmap: map failed, will retry using alloc + copy");
        e = revival_mapping_copy(vaddr, length, offset, true /* allocate */, filename, core_fd);
        logv("  revival_mapping_mmap: retry using revival_mapping_copy returns: %d\n", e);
    }
#endif
    return e;
}


int revival_mapping_allocate(void *vaddr, size_t length) {
    void *e = do_map_allocate_pd(vaddr, length);
    if (e != vaddr) {
        return -1;
    }
    return 0;
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
//    logv("  revival_mapping_copy: alloc=%d vaddr " PTR_FORMAT " - " PTR_FORMAT " len=" SIZE_FORMAT_X_0 " from file offset 0x%llx\n",
//         allocate, (uintptr_t) vaddr, (uintptr_t) ((address) vaddr + length), length, (long long) offset);

    if (allocate) {
        // Need to create a mapping: (not normally true)
        e = revival_mapping_allocate(vaddr, length);
        if (e < 0) {
            warn("  revival_mapping_copy: allocation required, but failed.");
            return -1;
        }
    }
    // Check permission
    if (!mem_canwrite_pd(vaddr, length)) {
        warn("  revival_mapping_copy: cannot write at vaddr 0x%p", vaddr);
        return -1;
    }

    // Copy data to allocation:
    FILE *f = fopen(filename, "rb");
    if (!f) {
        warn("revival_mapping_copy: cannot open: '%s': %d: %s", filename, errno, strerror(errno));
        return -1;
    }
    e = fseek(f, offset, SEEK_SET);
    if (e != 0) {
        warn("revival_mapping_copy: cannot seek '%s' to offset %llx: returns %d: %d: %s", filename, (long long) offset, e, errno, strerror(errno));
        fclose(f);
        return -1;
    }
    // Copy bytes from file offset to vaddr (not to a changed/aligned vaddr):
    int *p = (int*) vaddr;
    // printf("map test\n");
    *p = 123;
    //warn("map test done\n");

    // Read.  --> todo: use ptr to destination, read to there directly...
    int value;
    for (size_t i = 0; i < length/4; i++) {
        e = (int) fread(&value, 4, 1, f);
        if (e != 1) {
            warn("COPY fread failed: returns %d at %p pos=%zu : %d %s\n", e, p, i, errno, strerror(errno));
            break;
        }
        *p++ = value;
    }
    fclose(f);
    return 0;
}


/**
 * Load a shared library, using directory name and library name, at the given address.
 * Returns the value from load_sharedobject_pd(), which is an opaque handle (not the address), or -1 for error.
 */
void *load_sharedlibrary_fromdir(const char *dirname, const char *libname, void *vaddr, char *sum) {
    char buf[BUFLEN];
    snprintf(buf, BUFLEN, "%s/%s", dirname, libname); 
    logv("load_sharedlibrary_fromdir: %s\n", buf);
    void *a = load_sharedobject_pd(buf, vaddr);
    logv("load_sharedobject_pd: %s: returns %p\n", buf, a);
    waitHitRet();
    return a;
}


/**
 * Read and process the "core.mappings" file.
 * The file contains the name of the core file to open.
 * Map (revive) memory segments described by the file, into the current process.
 *
 * The core.mappings little language:

M 	map directly from core                      revival_mapping_mmap(vaddr, length, offset, lines, core_filename, core_fd);
m 	map allocation, not backed by core          revival_mapping_allocate(void *vaddr, size_t length);
C 	copy data (into an earlier "m" allocation)  revival_mapping_copy(vaddr, length, offset, false, core_filename, core_fd);

 *
 */
int mappings_file_read(const char *corename, const char *dirname, const char *mappings_filename) {
    int e = 0;
    char s1[BUFLEN];
    char s2[BUFLEN];
    int lines = 0;

    int M_good = 0;
    int m_good = 0;
    int C_good = 0;
    int M_bad = 0;
    int m_bad = 0;
    int C_bad = 0;
    memset(s1, 0, BUFLEN);

    FILE *f = fopen(mappings_filename, "r"); 
    if (!f) {
        warn("cannot open: '%s': %s", mappings_filename, strerror(errno));
        return -1;
    }
    // corefile details:
    e = fscanf(f, "core %s %s\n", s1 /* core filename */, s2 /* length */); 
    if (e != 2) {
        warn("mappings_file_read: unrecognised header in: %s", mappings_filename);
        return -1;
    }
    lines++;
    // Do not compare names: core_filename does not have to match, cores can be renamed.
    // Compare size: this should match.
    unsigned long long parsedSize = strtoull(s2, nullptr, 10);
    unsigned long long coresize = file_size(corename);
    if (verbose || (unsigned long long) coresize != parsedSize) {
        printf("%s: revival data recorded core size %lld, actual file size %lld\n", core_filename, parsedSize, coresize);
    }
    if (coresize != parsedSize) {
        return -1;
    }
    // Consider a checksum.

    // time:
    // time of crash or core file generation.  millis since epoch.
    core_timestamp = 0;
    e = fscanf(f, "time %s\n", s1);
    if (e == 1) {
        core_timestamp = (long long) strtoll(s1, nullptr, 10);
        warn("core time: %lld\n", core_timestamp);
    } else {
        warn("time record not found in file");
    }
    lines++;

    // Linux needs an fd to pass to mmap.  Windows will pass a filename.
    int core_fd = -1;
#ifdef LINUX
    core_fd = open(core_filename, (openCoreWrite ? O_RDWR : O_RDONLY));
    if (core_fd < 0) {
        warn("%s: %s", core_filename, strerror(errno));
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
            printf("Load library '%s' required at %p...\n", s1, vaddr);
            h = load_sharedlibrary_fromdir(dirname, s1, vaddr, s3);
            logv("load_sharedlibrary_fromdir returns: %p", h);
            if (h == (void *) -1) {
                warn("Load library '%s' failed to load at %p", s1, vaddr);
                return -1;
            }
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
                warn("skipping (%s): %p - %p len=%zx\n", danger, vaddr, (void*) ((unsigned long long) vaddr + length), length);
                Segment* thisSeg = new Segment(vaddr, length, offset, length_file);
                failedSegments.push_back(*thisSeg);
                if (_abortOnClash) {
                    abort();
                } else {
                    exitForRetry();
                }
                continue;
            } 
            if (strstr(s7, "W") != nullptr) {
                // Write permission: add to record of writable Segments:
                Segment* thisSeg = new Segment(vaddr, length, offset, length_file);
                writableSegments.push_back(*thisSeg);
            } 
            if (strncmp(s1, "M", 1) == 0) { 
                // Map memory from core:
                int e = revival_mapping_mmap(vaddr, length, offset, lines, core_filename, core_fd);
                // Windows: will try revival_mapping_copy (allocate) on failure.
                if (e < 0) {
                    M_bad++;
                } else {
                    M_good++;
                }
            } else if (strncmp(s1, "m", 1) == 0) {
                // Allocate only, file offset/length not used:
                int e = revival_mapping_allocate(vaddr, length);
                if (e < 0) {
                    m_bad++;
                } else {
                    m_good++;
                }
            } else if (strncmp(s1, "C", 1) == 0) { 
                // Copy, no allocation needed:
                int e = revival_mapping_copy(vaddr, length, offset, false, core_filename, core_fd);
                if (e < 0) {
                    warn("mappings_file_read: copy failed for seg at 0x%llx", (unsigned long long) vaddr);
                    C_bad++;
                } else {
                    C_good++;
                }
            } else {
                // Not recognised:
                printf("mappings_file_read: unrecognised mapping line %d: '%s'", lines, s1);
            }
            continue;
        } 
        if (strlen(s1) > 0) {
            printf("mappings_file_read: unrecognised line (2) %d: '%s'\n", lines, s1);
        }
        break;
    }
    if (verbose) {
        printf("mappings_file_read: read %d lines, Mappings: %d good, %d bad. map allocs: %d good, %d bad.  Copies: %d good, %d bad\n",
               lines, M_good, M_bad, m_good, m_bad, C_good, C_bad);
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
 * Lookup a symbol in jvm.symbols.
 * Read and process a jvm.symbols file in a given direcory.
 */
void *symbol_resolve_from_symbol_file(const char *dirname, const char *sym) {
    char buf[BUFLEN];
    snprintf(buf, BUFLEN, "%s/%s", dirname, SYMBOLS_FILENAME);
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
        memset(s1, 0, BUFLEN);
        memset(s2, 0, BUFLEN);
        memset(s3, 0, BUFLEN);
        e = fscanf(f, "%s %s %s\n", s1, s2, s3);  // symbol address [contents]
        if (e < 2) {
            break;
        }
        if (strncmp(s1, sym, BUFLEN) == 0) {
            char *endptr;
            addr = (void*) strtoll(s2, &endptr, 16);
            break;
        } 
    }
    if (verbose) {
        if (e == 2) {
            printf("symbol: %s = %p\n", sym, addr);
        } else {
            printf("symbol: %s = %p (contained %s)\n", sym, addr, s3);
        }
    }
    fclose(f);

    if (addr == 0) {
        return (void *) -1;
    } else {
        return addr;
    }
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
 *
 * Using platform-specific lookups such as dlsym() are not expected
 * to work for private symbols.
 */
void *symbol(const char *sym) {
    if (!revivaldir) {
        printf("symbol: call revive_image first.\n");
        return (void *) -1;
    }
    void *s = symbol_resolve_from_symbol_file(revivaldir, sym);
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
    logv("symbol call: %p\n", p);
}

/**
 * Resolve a symbol from jvm.symbols, and call it.
 * Use the symbol() function which will try using jvm.symbols first,
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
 * Functions to make a function call, or resolve and call:
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

// Only using call5 at the moment:
void *call5(void *s, void *arg1, void *arg2, void *arg3, void *arg4, void *arg5) {
    verbose_call(s);
    void* (*func)(void*,void*,void*,void*,void*) = (void*(*)(void*,void*,void*,void*,void*)) s;
    return (func)(arg1, arg2, arg3, arg4, arg5);
}

void *symbol_call5(const char *sym, void *arg1, void *arg2, void *arg3, void *arg4, void *arg5) {
    void *s = symbol(sym);
    if (s == (void*) -1) {
        return (void*) -1;
    }
    return call5(s, arg1, arg2, arg3, arg4, arg5);
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


#ifdef WINDOWS
char *basename(char *s) {
    for (char *p = s + strlen(s); p != s; p--) {
		if (*p == '\\') {
			p++;
            return p;
        }
	}
    return s;
}
#endif


int mappings_file_create(const char *dirname, const char *corename) {
// Create file and write 3 header lines:
// core FILENAME size
// time 123213123
// L jvm addresshex 0   (0 is placeholder for possible checksum)
// 
// Memory mappings to be written separately.

    char buf[BUFLEN];
    snprintf(buf, BUFLEN, "%s%s", dirname, "/" MAPPINGS_FILENAME);
    logv("mappings_file_create: %s", buf);
#ifdef WINDOWS
    int fd = _open(buf, _O_CREAT | _O_WRONLY | _O_TRUNC, _S_IREAD | _S_IWRITE);
#else
    int fd = open(buf, O_CREAT | O_WRONLY | O_TRUNC, S_IRUSR | S_IWUSR);
#endif
    if (fd < 0) {
        warn("mappings_file_create: %s: %s\n", buf, strerror(errno));
        return fd;
    }

    unsigned long long coresize = file_size(corename);
    snprintf(buf, BUFLEN, "core %s %lld\n", basename((char *) corename), coresize);
    write0(fd, buf);
    snprintf(buf, BUFLEN, "time %llu\n", file_time(corename));
    write0(fd, buf);
    const char *checksum = "0";
    snprintf(buf, BUFLEN, "L %s %llx %s\n", basename(jvm_filename), (unsigned long long) jvm_address, checksum);
    write0(fd, buf);
    return fd;
}

int symbols_file_create(const char *dirname) {
    char buf[BUFLEN];
    snprintf(buf, BUFLEN, "%s%s", dirname, "/" SYMBOLS_FILENAME);
    logv("symbols_file_create: %s", buf);
#ifdef WINDOWS
    int fd = _open(buf, _O_CREAT | _O_WRONLY | _O_TRUNC, _S_IREAD | _S_IWRITE);
#else
    int fd = open(buf, O_CREAT | O_WRONLY | O_TRUNC, S_IRUSR | S_IWUSR);
#endif
    if (fd < 0) {
        warn("symbols_file_create: %s: %s\n", buf, strerror(errno));
        return fd;
    }
    return fd;
}


/**
 * Segment
 */

bool Segment::contains(Segment* seg) {
  return seg->start() >= this->start() && seg->end() <= this->end();
}
bool Segment::contains(uint64_t addr) {
  return addr >= this->start() && addr <= this->end();
}

/**
 * Is this Segment not trivially ignorable, e.g. zero-length.
 */
bool Segment::is_relevant() {
  return length > 0 && file_length > 0;
}

/**
 * Write this Segment, formatted as a core.mappings line, to the given fd.
 */
int Segment::write_mapping(int fd) {
    return write_mapping(fd, "M");
}

int Segment::write_mapping(int fd, const char *type) {
    // type vaddr endaddress fileoffset filesize memsize perms
    // e.g.
    // M 2d05a12e000 2d05a12f000 19615fd4 1000 1000 RW-
    char buf[BUFLEN];
    snprintf(buf, BUFLEN, "%s %llx %llx %llx %llx %llx %s\n",
             type,
             (unsigned long long) vaddr,
             (unsigned long long) end(), // vaddr + length,
             (unsigned long long) file_offset,
             (unsigned long long) file_length,
             (unsigned long long) length,
             "RWX" // temp
            );
    write0(fd, buf); // includes warning on error
    return 0;
}

char *Segment::toString() {
    char* buf = (char *) malloc(BUFLEN);
    snprintf(buf, BUFLEN, "Segment: %llx - %llx off: %llx len:%llx",
             (unsigned long long) vaddr,
             (unsigned long long) end(), // vaddr + length,
             (unsigned long long) file_offset,
             (unsigned long long) file_length
            );
    return buf;
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
 * Complete the revival using a helper method in the target JVM.
 *
 * Return 0 for success, -1 for error.
 */
int revive_image_cooperative() {
    void *s = symbol(SYM_REVIVE_VM);
    if (s == (void*) -1) {
        warn("revive_image: JVM helper function not found.");
        return -1;
    }  

    logv("revive_image: calling revival helper %p", s);
    void*(*helper)() = (void*(*)()) s;
    waitHitRet();
    rdata = (struct revival_data *) (helper)();
    logv("revive_image: helper returns %p", rdata);
    if (rdata == nullptr) {
        warn("revive_image: JVM helper failed\n");
        return -1;
    } 
    if (rdata->version != 1) {
        error("revival data wrong version: %llx", (unsigned long long) rdata->version);
    }
    logv("revive_image: revival_data 0x%llx 0x%llx", (unsigned long long) rdata->magic, (unsigned long long) rdata->version);
    logv("revive_image: revival_data %s / %s / %s / %s", rdata->runtime_name, rdata->runtime_version, rdata->runtime_vendor_version,
         rdata->jdk_debug_level);
    logv("revive_image: VM Thread object = %p", rdata->vm_thread);
    warn("revive_image: initial_time_count ns = %lld", (unsigned long long) rdata->initial_time_count);
    warn("revive_image: initial_time_date  s  = %lld", (unsigned long long) rdata->initial_time_date);

#ifdef LINUX
        uint64_t lifetime_s = core_timestamp - rdata->initial_time_date;
        // Set clock_getting in revival support library (preloaded)
        void (*func)(unsigned long long) = (void(*)(unsigned long long)) dlsym(RTLD_NEXT, "set_revival_time_s");
        if (func != nullptr) {
            func(lifetime_s + (rdata->initial_time_count / 1000000000));
        } else {
            warn("set_revival_time: symbol lookup failed.");
        }
#endif

    return 0;
}


/**
 * create_revivalbits
 *
 * Create and populate the revival data directory.
 *
 * Only called when the directory (core.revival) does not exist.
 *
 * Return zero on success.
 */
int create_revivalbits(const char *corename, const char *javahome, const char *dirname, const char *libdir) {
    // Currenly per-platform implementations.  Could hoist some common work here.
    //
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
        strncat(dirname, REVIVAL_SUFFIX, BUFLEN - 1);
    }
    return dirname;
}

int revive_image(const char *corename, const char *javahome, const char *libdir) {
    int e;
    char buf[BUFLEN];
    char *dirname;

    verbose = env_check((char *) "REVIVAL_VERBOSE");
    _wait = env_check((char *) "REVIVAL_WAIT");
    _abortOnClash = env_check((char *) "REVIVAL_ABORT");

    init_pd();

    if (corename == nullptr) {
        warn("revive_image: core file name required.");
        return -1;
    }
    if (rdata != nullptr && rdata->vm_thread) {
        warn("revive_image: already called.");
        return -1;
    }
    // Record our copy of core file name:
    core_filename = strdup(corename);
    if (core_filename == nullptr) {
        warn("revive: alloc copy of core_filename failed.");
        return -1;
    }

    // Check core file exists:
    e = open(core_filename, O_RDONLY);
    if (e < 0) {
        warn("revive_image: open '%s' failed: %d: %s", core_filename, errno, strerror(errno));
        return e;
    }
    close(e);

    dirname = revival_dirname(corename);
    if (dirname == nullptr) {
        warn("revive_image: failed to allocate dirname.");
        return -1;
    }
    if (verbose) {
        printf("revive_image:\n");
        printf("revival directory: '%s'\n", dirname);
        printf("vaddr_alignment = %llu\n", (unsigned long long) vaddr_alignment_pd());
    }

    // Does revival data directory exist? If not, create data:
    if (!revival_direxists_pd(dirname)) {
        e = create_revivalbits(corename, javahome, dirname, libdir);
        logv("revive_image: create_revivalbits return code: %d", e);
        if (e < 0) {
            warn("revive_image: create_revivalbits failed.  Return code: %d", e);
            return e;
        }
    }

    if (revival_checks_pd(dirname) < 0) {
        warn("revive_image: revival_checks failed: %s", dirname);
        return -1;
    }

    snprintf(buf, BUFLEN, "%s%s", dirname, "/" MAPPINGS_FILENAME);
    e = mappings_file_read(corename, dirname, buf);
    if (e < 0) {
        warn("revive_image: mappings_file_read failed: %d", e);
        return -1;
    }
    revivaldir = dirname;

#ifdef LINUX
    // Install signal handler on Linux before revival:
    install_handler();
#endif

    e = revive_image_cooperative();
    if (e < 0) {
        warn("revival: revive_image failed: %d", e);
    } else {
        logv("revive_image: OK");
    }
    return e;
}

void *revived_vm_thread() {
    if (!revivaldir || !rdata || !rdata->vm_thread) {
        error("revived_vm_thread: call revive_image first.");
    }
    return rdata->vm_thread;
}

void *revived_tty() {
    if (!revivaldir || !rdata) {
        error("revival_tty: call revive_image first.");
    }
    return rdata->tty;
}


int revival_dcmd(const char *command) {
    if (!revivaldir || !rdata) {
        error("revival_dcmd: call revive_image first.");
    }
    void *s = rdata->parse_and_execute;
    if (s == nullptr) {
        error("revival_dcmd: no parse_and_execute in revival data.");
    }
    if (revived_tty() == nullptr) {
        // null tty will cause a crash during DCmd output.
        error("revival_dcmd: tty not set.");
    }

    logv("revival_dcmd: '%s'\n", command);
    // We can call parse_and_execute like this:
    //   int(*dcmd_parse)(int, void*, const char*, char, void*) = (int(*)(int, void*, const char *, char, void*)) s;
    //   (dcmd_parse)(DCMD_SOURCE, revived_tty(), command, ' ', revived_vm_thread());
    // Or with:
    call5(s, (void*) DCMD_SOURCE, revived_tty(), (void*) command, (void*) ' ', revived_vm_thread());
    return 0;
}

