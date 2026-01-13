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

#ifndef REVIVAL_H
#define REVIVAL_H


#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <time.h>

#include <cinttypes>
#include <list>
#include <new>
#include <set>

// More types
typedef uint64_t address;

#define PTR_FORMAT               "0x%016"     PRIxPTR
#define UINTX_FORMAT_X_0         "0x%016"     PRIxPTR
#define SIZE_FORMAT_X_0          "0x%016"     PRIxPTR

#define BUFLEN 2048

// Source param for executing DiagnosticCommand (sync with diagnosticFramework.hpp)
#define DCMD_SOURCE 8

// Filenames
#define MAPPINGS_FILENAME "core.mappings"
#define SYMBOLS_FILENAME "jvm.symbols"
#define REVIVAL_SUFFIX ".revival"

// Essential symbols to resolve are defined in SYM_... macros.
// This one is "C" and common to all platforms:
#define SYM_REVIVE_VM "process_revival"


void install_handler();

//
// Platform specifics
//

// Linux
#ifdef LINUX

#include <dlfcn.h>
#include <libgen.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/time.h>

#define JVM_FILENAME "libjvm.so"
#define FILE_SEPARATOR  "/"

#define SYM_VM_RELEASE "_ZN19Abstract_VM_Version13_s_vm_releaseE"

#endif /* LINUX */


// Windows
#ifdef WIN32

#include <io.h>
#include <windows.h>

void tls_fixup_pd(void* tlsPtr);
void normalize_path_pd(char *s);

#define JVM_FILENAME "jvm.dll"
#define FILE_SEPARATOR  "\\"

// #define SYM_VM_RELEASE "?_s_vm_release@Abstract_VM_Version@@1PEBDEB"
#define SYM_VM_RELEASE "_s_vm_release_global"

#define _exit _Exit

#endif /* WINDOWS */


// MacOSX
#ifdef MACOSX

#include <libgen.h>
#include <unistd.h>
#include <sys/time.h>

#define JVM_FILENAME "libjvm.dylib"
#define FILE_SEPARATOR  "/"

#define SYM_VM_RELEASE "todo"

#define _exit _Exit

#endif /* MACOSX */


//
// The revival interface:
// revivalhelper tool uses only these two functions.
//

// One structure to keep in sync with the JVM:

struct revival_data {
  uint64_t magic;
  uint64_t version;
  uint64_t status;

  const char *runtime_name;
  const char *runtime_version;
  const char *runtime_vendor_version;
  const char *jdk_debug_level;

  uint64_t initial_time_count; // Linux: clock_gettime MONOTONIC (since system boot)
  uint64_t initial_time_date;  // Linux: time_t since epoch
  double error_time;

  void* vm_thread;
  void* tty;
  void* parse_and_execute;
  void* throwable_print;
  void* info1;
  void* info2;
  void* info3;
};

/**
 * Main revival setup entry point.
 * Given a core file name, create mappings data if necessary, and
 * revive into the current process.
 *
 * Accept optional library search directory, and directory for stored revival data directory, which may both be null.
 *
 * Return 0 for success, -1 for failure.
 */
int revive_image(const char* corefile, const char* javahome, const char* libdir, const char* revival_data_path);

/**
 * Invoke the given jcmd operation, e.g. "Thread.print" or a string containing command and parameters
 * (space separacter).
 *
 * Calls into the revived JVM:
 * void DCmd::parse_and_execute(DCmdSource source, outputStream* out, const char* cmdline, char delim, TRAPS)
 */
int revival_dcmd(const char *command);


//
// Revival internals:
//

// Behaviour:
extern int verbose;          // set from env: REVIVAL_VERBOSE
extern int skipVersionCheck; // set from env: REVIVAL_SKIPVERSIONCHECK
extern int _abortOnClash;    // set from env: REVIVAL_ABORT

// Optionally map core files with write permission:
// On Linux, map core files read only, signal handler remaps to handle writes.
// If true, core file is actually changed by writes.
extern int openCoreWrite;

// Revival state:
extern char *core_filename;
extern int core_fd;
extern const char *revivaldir;
extern void *revivalthread;
extern void *h; // handle to libjvm

// Exit code signalling an address space clash that may be temporary, caller should retry.
#define EXIT_CODE_SUGGEST_RETRY 7

extern void exitForRetry(); // exit process using above exit code to signal a retry

struct SharedLibMapping {
    uint64_t start;
    uint64_t end;
    char *path;
};


class Segment {
    public:
        Segment(void *v, size_t len, size_t offset, size_t file_len) :
            vaddr(v), length(len), file_offset(offset), file_length(file_len) {}

        Segment(Segment* s) :
            vaddr(s->vaddr), length(s->length), file_offset(s->file_offset), file_length(s->file_length) {}

        void   *vaddr;
        size_t length;
        size_t file_offset;
        size_t file_length;

        uint64_t start() { return (uint64_t) vaddr; }
        uint64_t end() { return (uint64_t) vaddr + length; }
        void set_end(uint64_t addr) { length = addr - (uint64_t) vaddr; }
        void set_length(uint64_t len) { length = len; file_length = len; }
        void move_start(long dist);

        bool contains(Segment *seg);
        bool contains(uint64_t addr);

        bool is_relevant();
        int write_mapping(int fd);
        int write_mapping(int fd, const char* type);

        char *toString();
};

extern std::list<Segment> writableSegments;
extern std::list<Segment> failedSegments;



// Revival prep state:
extern char *jvm_filename;
extern void *jvm_address;
extern std::list<Segment> avoidSegments;

char* readstring(int fd);
char* readstring_at_offset_pd(const char* filename, uint64_t offset);
char* readstring_from_core_at_vaddr_pd(const char* filename, uint64_t addr);

bool create_directory_pd(char* dirname);

bool try_init_jvm_filename_if_exists(const char* path, const char* suffix);
void init_jvm_filename_from_libdir(const char* libdir);


// Symbol lookup
void *symbol(const char *symbol);
void *symbol_deref(const char *symbol);

/**
 * Platform-specific symbol lookup.
 * Return symbol information as a pointer.
 * Implementations can print platform-specfic error info and then
 * return (void*) -1 on failure.
 */
void *symbol_dynamiclookup_pd(void *h, const char*str);


void *symbol_call(const char *sym);
void *symbol_call1(const char *sym, void *arg);
void *symbol_call2(const char *sym, void *arg1, void *arg2);
void *symbol_call3(const char *sym, void *arg1, void *arg2, void *arg3);
void *symbol_call4(const char *sym, void *arg1, void *arg2, void *arg3, void *arg4);
void *symbol_call5(const char *sym, void *arg1, void *arg2, void *arg3, void *arg4, void *arg5);

address align_down(address ptr, uint64_t mask);
address align_up(address ptr, uint64_t mask);

uint64_t vaddr_alignment_pd(); // return a mask, e.g. 0xfff
uint64_t offset_alignment_pd();
uint64_t length_alignment_pd();

/*
 * Platform-specific setup, e.g. find system alignment.
 */
void init_pd();

// Create a memory mapping from the core/dump file.
// Return address of allocation, or -1 for failure.
void *do_mmap_pd(void *addr, size_t length, char *filename, int fd, size_t offset);
void *do_mmap_pd(void *addr, size_t length, size_t offset);

int do_munmap_pd(void *addr, size_t length);

void *do_map_allocate_pd(void *addr, size_t length);

/**
 * Return the VMThread created.
 */
void *revived_vm_thread();


/**
 * Utilities to return a boolean for file or directory existence.
 */
bool dir_exists_pd(const char* dirname);
bool dir_isempty_pd(const char* dirname);
bool file_exists_pd(const char* filename);
bool file_exists_indir_pd(const char* dirname, const char* filename);

char* find_filename_in_libdir(const char* libdir, const char* filename);

unsigned long long file_size(const char* filename);


int revival_mapping_allocate(void *vaddr, size_t length);

int revival_mapping_copy(void *vaddr, size_t length, size_t offset, bool allocate, char *filename, int fd);

int relocate_sharedlib_pd(const char* filename, const void *addr);


int create_revivalbits_native_pd(const char* corename, const char* javahome, const char* dirname, const char *libdir);

/**
 * Create the named "core.mappings" file and write the header lines.
 * Return the fd so other code can write the memory mapping lines,
 * or negative on error.
 */
int mappings_file_create(const char *filename, const char *corename);

/**
 * Create jvm.symbols file
 * Return the fd so other code can write the symbols lines.
 */
int symbols_file_create(const char *filename);

int generate_symbols_pd(const char *name, int fd);

/**
 *  Load a shared library.  Return an opaque handle (not the load address), or -1 for error.
 */
void *load_sharedobject_pd(const char *name, void *vaddr);

/**
 * Unload a shared library identified by handle.  Return zero on success. 
 */
int unload_sharedobject_pd(void *h);


bool mem_canwrite_pd(void *vaddr, size_t length);

int revival_checks_pd(const char *dirname);
int dangerous0(void *vaddr, unsigned long long length, address xaddr);
const char *dangerous( void *vaddr, unsigned long long length);

/**
 * If we know an upper limit on process virtual address, return it, or return 0 if not known.
 */
unsigned long long max_user_vaddr_pd();

/**
 * Diagnostic utils:
 */

// Simple pause for debugging when REVIVAL_WAIT is set in env.
void waitHitRet();

// Possibly implement a process memory map display for debugging.
void pmap_pd();


//
// Diagnostics:
//

// Avoid "error: format string is not a string literal [-Werror,-Wformat-nonliteral]"
// on Mac, but __attribute__ not a feature on MSVC/Windows.
#if !defined(__GNUC__) && !defined(__clang__)
#define __attribute__(x)
#endif
#define ATTRIBUTE_PRINTF(fmt,vargs)  __attribute__((format(printf, fmt, vargs)))

// Write string fully to fd, log if error.
void write0(int fd, const char *buf);

void writef(int fd, const char *format, ...) ATTRIBUTE_PRINTF(2, 3);

// Log to stderr.  Adds timestamp and newline to given message.
void log(const char *format, ...) ATTRIBUTE_PRINTF(1, 2);

// With verbose check.
void logv(const char *format, ...) ATTRIBUTE_PRINTF(1, 2);

// Write to stderr.  Adds newline.
void warn(const char *format, ...) ATTRIBUTE_PRINTF(1, 2);

// Write to stderr and exit.  Adds newline.
void error(const char *format, ...) ATTRIBUTE_PRINTF(1, 2);


#endif /* REVIVAL_H */

