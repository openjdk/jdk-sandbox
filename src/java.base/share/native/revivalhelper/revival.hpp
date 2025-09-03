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
//#include <inttypes.h>
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

// The few essential known symbols are defined in SYM_... macros.
// This one is "C" and common to all platforms:
#define SYM_REVIVE_VM "process_revival"


//
// Platform specifics (break into seprate headers when makes sense...)
//
#ifdef LINUX

#include <libgen.h>
#include <pthread.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/time.h>

#define JVM_FILENAME "libjvm.so"

#define SYM_JVM_VERSION "_ZN19Abstract_VM_Version11jvm_versionEv"
#define SYM_THROWABLE_PRINT "_ZN19java_lang_Throwable5printE3oopP12outputStream"
#define SYM_TC_OWNER "_ZL8tc_owner"
#define SYM_PARSE_AND_EXECUTE "_ZN4DCmd17parse_and_executeE10DCmdSourceP12outputStreamPKccP10JavaThread"
#define SYM_TTY "tty"
#define SYM_THREAD_KEY "_ZL11_thread_key"

void install_handler();

#endif /* LINUX */


#ifdef WIN32

#include <io.h>
#include <windows.h>

static DWORD _thread_key;
void tls_fixup_pd(void *tlsPtr);

#define JVM_FILENAME "jvm.dll"

#define SYM_JVM_VERSION "?jvm_version@Abstract_VM_Version@@SAIXZ"
#define SYM_THROWABLE_PRINT "?print@java_lang_Throwable@@SAXPEAVoopDesc@@PEAVoutputStream@@@Z"
#define SYM_TC_OWNER "?lock_owner@@3KA"
#define SYM_PARSE_AND_EXECUTE "?parse_and_execute@DCmd@@SAXW4DCmdSource@@PEAVoutputStream@@PEBDDPEAVJavaThread@@@Z"
#define SYM_TTY "?tty@@3PEAVoutputStream@@EA"
#define SYM_THREAD_KEY "?_thread_key@@3KA"

#define _exit _Exit

#endif /* WINDOWS */


#ifdef MACOSX

#include <libgen.h>
#include <unistd.h>
#include <sys/time.h>

#define JVM_FILENAME "libjvm.dylib"

#define SYM_JVM_VERSION "?jvm_version@Abstract_VM_Version@@SAIXZ"
#define SYM_THROWABLE_PRINT "_ZN19java_lang_Throwable5printE3oopP12outputStream"
#define SYM_TC_OWNER "_ZL8tc_owner"
#define SYM_PARSE_AND_EXECUTE "parse_and_execute@DCmd@@SAXW4DCmdSource@@PEAVoutputStream@@PEBDDPEAVThread@@@Z"
#define SYM_TTY "tty"
#define SYM_THREAD_KEY "_ZL11_thread_key"

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
  uint64_t jvm_version;
  void* vm_thread;
  void* parse_and_execute;
  void* tty;
  void* info;
};

/**
 * Main revival setup entry point.
 * Given a core file name, create mappings data if necessary, and
 * revive into the current process.
 *
 * Return 0 for success, -1 for failure.
 */
int revive_image(const char *corefile, const char *javahome, const char *libdir);

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
extern int verbose;         // set from env: REVIVAL_VERBOSE
extern int _abortOnClash;   // set from env: REVIVAL_ABORT

// Optionally map core files with write permission:
// On Linux, map core files read only, signal handler remaps to handle writes.
// If true, core file is actually changed by writes.
extern int mapWrite;

// Revival state:
extern char *core_filename;
extern int core_fd;
extern const char *revivaldir;
extern void *revivalthread;
extern void *h; // handle to libjvm

extern void exitForRetry(); // Exit with value signalling an address space clash that may be temporary.

struct SharedLibMapping {
    uint64_t start;
    uint64_t end;
    char *path;
};


class Segment {
    public:
        Segment(void *v, size_t len, off_t offset, size_t file_len) : 
            vaddr(v), length(len), file_offset(offset), file_length(file_len) {}

        Segment(Segment *s) : 
            vaddr(s->vaddr), length(s->length), file_offset(s->file_offset), file_length(s->file_length) {}

        void   *vaddr;
        size_t length;
        off_t  file_offset;
        size_t file_length;

        uint64_t start() { return (uint64_t) vaddr; }
        uint64_t end() { return (uint64_t) vaddr + length; }
        void set_end(uint64_t addr) { length = addr - (uint64_t) vaddr; }

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
void *do_mmap_pd(void *addr, size_t length, off_t offset);
void *do_mmap_pd(void *addr, size_t length, char *filename, int fd, off_t offset);
void *do_mmap_pd(void *addr, size_t length, off_t offset);

int do_munmap_pd(void *addr, size_t length);

void *do_map_allocate_pd(void *addr, size_t length);

/**
 * Return the VMThread created.
 */
void *revived_vm_thread();

SharedLibMapping* read_NT_mappings2(int core_fd, int& count_out);

/**
 * Return a boolean true if the given revival directory exists.
 */
bool revival_direxists_pd(const char *dirname);

int revival_mapping_allocate(void *vaddr, size_t length);

int revival_mapping_copy(void *vaddr, size_t length, off_t offset, bool allocate, char *filename, int fd);

int relocate_sharedlib_pd(const char* filename, const void *addr);


int create_revivalbits_native_pd(const char *corename, const char *javahome, const char *dirname, const char *libdir);

/**
 * Create core.mappings file and write the header lines.
 * Return the fd so other code can write the memory mapping lines.
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
 * Write string fully to fd, log if error.
 */
void write0(int fd, const char *buf);

void writef(int fd, const char *format, ...);

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

// Log to stderr.  Adds timestamp and newline to given message.
void log(const char *format, ...) ATTRIBUTE_PRINTF(1, 2);
// With verbose check.
void logv(const char *format, ...) ATTRIBUTE_PRINTF(1, 2);

// Write to stderr.  Adds newline.
void warn(const char *format, ...) ATTRIBUTE_PRINTF(1, 2);

// Write to stderr and exit.  Adds newline.
void error(const char *format, ...) ATTRIBUTE_PRINTF(1, 2);


#endif /* REVIVAL_H */

