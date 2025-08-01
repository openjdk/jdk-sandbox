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
#include <cassert>
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


// A minimal set of operations for doing what we need to do on ELF files.
// ELFOperations is passed an open file. 
// read_basics() must be called prior to other operations.
struct ELFOperations {
    long long relocation_amount = 0;
    int file = -1;
    Elf64_Ehdr EHDR;
    char* SHDRSTR_BUFFER = nullptr;
    SharedLibMapping* NT_Mappings = 0;
    int NT_Mappings_count = 0;


    ELFOperations(int fd) {
        // Manually computed from adding fields in elf.h
        // This is to guard against the compiler adding padding without us noticing, which would break parsing.
        assert(sizeof(Elf64_Ehdr) == 64);
        assert(sizeof(Elf64_Phdr) == 56);
        assert(sizeof(Elf64_Shdr) == 64);
        assert(sizeof(Elf64_Dyn) == 16);
        assert(sizeof(Elf64_Sym) == 24);
        assert(sizeof(Elf64_Rela) == 24);
        assert(sizeof(Elf64_Rel) == 16);

        file = fd;
        assert(file != -1);
    }

    void read_basics() {
        if (SHDRSTR_BUFFER != nullptr) return;

        EHDR = read_type_at<Elf64_Ehdr>(0);
        assert_ELF_sound();
        Elf64_Shdr strndx_shdr = section_by_index(EHDR.e_shstrndx);
        SHDRSTR_BUFFER = new char[strndx_shdr.sh_size];
        read_bytes_at(strndx_shdr.sh_offset, strndx_shdr.sh_size, SHDRSTR_BUFFER);
    }

    template <typename T>
    T read_type() {
        T ret;
        if (read(file, &ret, sizeof(T)) != sizeof(T)) {
            error("read_type: %s", strerror(errno));
        }
        return ret;
    }

    template <typename T>
    T read_type_at(unsigned long at) {
        if (lseek(file, at, SEEK_SET) == -1) {
            error("read_type_at: %s", strerror(errno));
        };
        return read_type<T>();
    }

    template <typename T>
    void write_type_at(T content, unsigned long at) {
        if (lseek(file, at, SEEK_SET) == -1) {
            error("write_type_at: %s", strerror(errno));
        };

        if (write(file, &content, sizeof(T)) != sizeof(T)) {
            error("write_type_at: %s", strerror(errno));
        }
    }

#define RELOCATE(Offset, Typ, Field)            \
    {                                           \
        Typ obj = read_type_at<Typ>(Offset);    \
        obj.Field += relocation_amount;         \
        write_type_at<Typ>(obj, Offset);        \
    }

#define RELOCATE_IF(Offset, Typ, Field, Condition)     \
    {                                                  \
        Typ obj = read_type_at<Typ>(Offset);           \
        if (Condition(obj)) {                          \
            obj.Field += relocation_amount;            \
            write_type_at<Typ>(obj, Offset);           \
        }                                              \
    }                                               

    unsigned long section_header_offset(unsigned long index) {
        return EHDR.e_shoff + index * EHDR.e_shentsize;
    }

    bool section_name_is(Elf64_Shdr shdr, const char* name) {
        return (strcmp(name, SHDRSTR_BUFFER + shdr.sh_name) == 0);
    }

    Elf64_Shdr section_by_name(const char* name) {
        for (int i = 0; i < EHDR.e_shnum; i++) {
            Elf64_Shdr obj = section_by_index(i);
            if (section_name_is(obj, name)) {
                return obj;
            }
        }
        
        error("Not found.");
        return Elf64_Shdr(); // dummy
    }

    Elf64_Shdr section_by_index(unsigned long index) {
        return read_type_at<Elf64_Shdr>(EHDR.e_shoff + index * EHDR.e_shentsize);
    }

    // Returns the first phdr where predicate returns true.
    // On return, file is positioned having just read the phdr.
    Elf64_Phdr program_header_by_predicate(bool (*predptr)(Elf64_Phdr*)) {
        for (int i = 0; i < EHDR.e_phnum; i++) {
            Elf64_Phdr phdr = read_type_at<Elf64_Phdr>(EHDR.e_phoff + i * EHDR.e_phentsize);
            if (predptr(&phdr)) {
                return phdr;
            }
        }
    }

    bool should_relocate_addend(Elf64_Rela& rela) {
        switch (ELF64_R_TYPE(rela.r_info)) {
#if defined(__aarch64__)
            case R_AARCH64_RELATIVE: // just a placeholder guess...
            case R_AARCH64_ABS64:
            case R_AARCH64_JUMP_SLOT:
            case R_AARCH64_GLOB_DAT:
#else
            case R_X86_64_RELATIVE:
#endif
                return true;
            default:
                return false;
        }
    }

    void assert_ELF_sound() {
        assert(EHDR.e_ident[0] == 0x7f);
        assert(EHDR.e_ident[1] == 'E');
        assert(EHDR.e_ident[2] == 'L');
        assert(EHDR.e_ident[3] == 'F');
        assert(EHDR.e_ident[4] == ELFCLASS64);
        assert(EHDR.e_ident[5] == ELFDATA2LSB);
        assert(EHDR.e_ident[6] == EV_CURRENT);
        assert(EHDR.e_ident[7] == ELFOSABI_SYSV);
        assert(EHDR.e_version == EV_CURRENT);

    // elf.h in devkit on Linux x86_64 does not define EM_AARCH64
#if defined(__aarch64__)
        assert(EHDR.e_machine == EM_AARCH64);
#else
        assert(EHDR.e_machine == EM_X86_64);
#endif
        if (EHDR.e_phnum == PN_XNUM) {
            error("Too many program headers, handling not implemented.");
        }
        if (EHDR.e_type == ET_DYN && EHDR.e_shnum == 0) {
            error("Invalid number of section headers in shared library, zero.");
        }
        assert(EHDR.e_phentsize == sizeof(Elf64_Phdr));
    }

    void relocate_execution_header() {
        EHDR.e_entry += relocation_amount;
        write_type_at<Elf64_Ehdr>(EHDR, 0);
    }

    bool should_relocate_program_header(Elf64_Phdr& phdr) {
#ifdef __aarch64__
        phdr.p_align = 0x1000; // Need a better place to do this.
#endif
        return phdr.p_type != PT_GNU_STACK;
    }

    long long program_header_offset(int i) {
        return EHDR.e_phoff + i * EHDR.e_phentsize;
    }

    void relocate_program_headers() {
        for (int i = 0; i < EHDR.e_phnum; i++) {
            RELOCATE_IF(program_header_offset(i), Elf64_Phdr, p_vaddr, 
                should_relocate_program_header);
            RELOCATE_IF(program_header_offset(i), Elf64_Phdr, p_paddr,
                should_relocate_program_header);
        }
    }

    bool should_relocate_section_header(Elf64_Shdr& shdr) {
        if (section_name_is(shdr, ".comment")) return false;
        if (section_name_is(shdr, ".note.stapsdt")) return false;
        if (section_name_is(shdr, ".note.gnu.gold-version")) return false;
        if (section_name_is(shdr, ".gnu_debuglink")) return false;
        if (section_name_is(shdr, ".symtab")) return false;
        if (section_name_is(shdr, ".shstrtab")) return false;
        if (section_name_is(shdr, ".strtab")) return false;
        if (shdr.sh_type == SHT_NULL) return false;
        return true;
    }

    void relocate_section_headers() {
        for (int i = 0; i < EHDR.e_shnum; i++) {
            RELOCATE_IF(section_header_offset(i), Elf64_Shdr, sh_addr, 
                should_relocate_section_header);
        }
    }

    void relocate_relocation_table(const char* name) {
        Elf64_Shdr sh_reladyn = section_by_name(name);
        for (unsigned long o = sh_reladyn.sh_offset; o < (sh_reladyn.sh_offset + sh_reladyn.sh_size); o += sh_reladyn.sh_entsize) {
            Elf64_Rela rela = read_type_at<Elf64_Rela>(o);
            rela.r_offset += relocation_amount;
            if (should_relocate_addend(rela)) {
                rela.r_addend += relocation_amount;
            }
            write_type_at<Elf64_Rela>(rela, o);
        }
    }

    bool should_relocate_dynamic_tag(Elf64_Dyn& dyn) {
        switch (dyn.d_tag) {
            case DT_INIT:
            case DT_FINI:
            case DT_INIT_ARRAY:
            case DT_FINI_ARRAY:
            case DT_HASH:
            case DT_GNU_HASH:
            case DT_STRTAB:
            case DT_SYMTAB:
            case DT_PLTGOT:
            case DT_JMPREL:
            case DT_RELA:
            case DT_VERDEF:
            case DT_VERNEED:
            case DT_VERSYM:
                return true;
            default:
                return false;
        }
    }

    void relocate_dynamic_table() {
        Elf64_Shdr sh = section_by_name(".dynamic");
        for (unsigned long o = sh.sh_offset; o < sh.sh_offset + sh.sh_size; o += sh.sh_entsize) {
            Elf64_Dyn dyn = read_type_at<Elf64_Dyn>(o); 
            if (dyn.d_tag == DT_NULL) break;
            RELOCATE_IF(o, Elf64_Dyn, d_un.d_val, should_relocate_dynamic_tag)
        }
    }

    bool should_relocate_symbol(Elf64_Sym& sym) {
        if (ELF64_ST_TYPE(sym.st_info) == STT_TLS) return false;
        if (sym.st_shndx == 0) return false;
        if (sym.st_shndx == SHN_ABS) return false;
        return true;
    }


    void relocate_symbol_table(const char* name) {
        Elf64_Shdr sh = section_by_name(name);
        for (unsigned long o = sh.sh_offset; o < sh.sh_offset + sh.sh_size; o += sh.sh_entsize) {
            RELOCATE_IF(o, Elf64_Sym, st_value, should_relocate_symbol)
        }
    }

    void read_bytes_at(unsigned long at, ssize_t bytes, char* buffer) {
        if (lseek(file, at, SEEK_SET) == -1) {
            error("read_bytes_at: %s", strerror(errno));
        };
        if (read(file, buffer, bytes) != bytes) {
            error("read_bytes_at: %s", strerror(errno));
        }
    }

    void read_bytes(ssize_t bytes, char* buffer) {
        if (read(file, buffer, bytes) != bytes) {
            error("read_bytes: %s", strerror(errno));
        }
    }
    
    void relocate(unsigned long reloc_amount) {
        read_basics();
        assert(SHDRSTR_BUFFER != 0);
        assert(EHDR.e_type == ET_DYN);
        relocation_amount = reloc_amount;
        relocate_execution_header();
        relocate_program_headers();
        relocate_section_headers();
        relocate_relocation_table(".rela.dyn");
        relocate_relocation_table(".rela.plt");
        relocate_dynamic_table();
        relocate_symbol_table(".dynsym");
        relocate_symbol_table(".symtab");
    }

    // Get symbols in libjvm.
    // If dlopen relocated libjvm, should use dlsym results.
    // Write symbol list for revived process.
    void write_jvm_symbols(int fd, const char* symbols[], int N_SYMS) {
        read_basics();

        Elf64_Shdr strtab = section_by_name(".strtab");
        char* SYMTAB_BUFFER = new char[strtab.sh_size];
        read_bytes_at(strtab.sh_offset, strtab.sh_size, SYMTAB_BUFFER);

        Elf64_Shdr symtab = section_by_name(".symtab");
        for (long unsigned int i = 0; i < symtab.sh_size/symtab.sh_entsize; i++) {
            Elf64_Sym sym = read_type_at<Elf64_Sym>(symtab.sh_offset+ i*symtab.sh_entsize);

            for (int j = 0; j < N_SYMS; j++) {
                int ret = strcmp(
                    symbols[j],
                    SYMTAB_BUFFER + sym.st_name);
                if(ret == 0) {
                    char buf[2048];
                    snprintf(buf, 2048, "%s %llx %llx\n",
                            SYMTAB_BUFFER + sym.st_name,
                            (unsigned long long) sym.st_value,
                            (unsigned long long) 0);
                    write(fd, buf);
                }
            }
        }
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

    void read_NT_mappings() {
        if(NT_Mappings != 0) return;
        read_basics();
        // Read core NOTES, find NT_FILE, find libjvm.so

        lseek(file, EHDR.e_phoff, SEEK_SET);
        // Read ELF Program Headers.  Look for PT_NOTE
        Elf64_Phdr phdr;
        for (int i = 0; i < EHDR.e_phnum; i++) {
            phdr = read_type<Elf64_Phdr>();
            if (verbose) {
                fprintf(stderr, "PH %d: type 0x%x flags 0x%x vaddr 0x%lx\n", i, phdr.p_type, phdr.p_flags, phdr.p_vaddr);
            }

            if (phdr.p_type != PT_NOTE) {
                continue;
            }

            lseek(file, phdr.p_offset, SEEK_SET);
            // Read NOTES.  p_filesz
            // Look for NT_FILE note
            Elf64_Nhdr nhdr;
            uint64_t pos = lseek(file, 0, SEEK_CUR);
            uint64_t end =  pos + phdr.p_filesz;
            while (pos < end) {
                nhdr = read_type<Elf64_Nhdr>();
                if (verbose) fprintf(stderr, "NOTE type 0x%x namesz %x descsz %x\n", nhdr.n_type, nhdr.n_namesz, nhdr.n_descsz);

                // TODO where's this freed?
                char *name = (char *) malloc(nhdr.n_namesz);
                if (name == nullptr) {
                    warn("Failed malloc for namesz %d\n", nhdr.n_namesz);
                    return;
                }
                read_bytes(nhdr.n_namesz, name);

                // Align. 4 byte alignment, including when 64-bit.
                while ((lseek(file, 0, SEEK_CUR) & 0x3) != 0) {
                    read_type<char>();
                }

                if (nhdr.n_type != 0x46494c45 /* NT_FILE */) {
                    // Not NT_FILE, skip over...
                    lseek(file, (nhdr.n_descsz), SEEK_CUR);
                    pos = lseek(file, 0, SEEK_CUR);
                    continue;
                }

                // Read NT_FILE:
                NT_Mappings_count = read_type<int>();
                read_type<int>(); // pad 
                long pagesize = read_type<long>();
                if (verbose) {
                    fprintf(stderr, "NT_FILE count %d pagesize 0x%lx\n", NT_Mappings_count, pagesize);
                }
                
                NT_Mappings = new SharedLibMapping[NT_Mappings_count];
                for (int i = 0; i < NT_Mappings_count; i++) {
                    NT_Mappings[i].start = read_type<long>();
                    NT_Mappings[i].end = read_type<long>();;
                    read_type<long>(); // offset 
                }
                for (int i = 0; i < NT_Mappings_count; i++) {
                    NT_Mappings[i].path = readstring(file);
                }

                if (verbose) {
                    fprintf(stderr, "Num of mappings: %i\n", NT_Mappings_count);
                    for (int i = 0; i < NT_Mappings_count; i++) {
                        fprintf(stderr, "Mapping: %lu %lu %s\n", NT_Mappings[i].start, NT_Mappings[i].end, NT_Mappings[i].path);
                    }
                }
            }
        }
        warn("No NT_FILE NOTE found?\n");
    }

    SharedLibMapping* get_library_mapping(const char* filename) {
        read_NT_mappings();
        for (int i = 0; i < NT_Mappings_count; i++) {
            //fprintf(stderr, "Mapping: %lu %lu %s\n", mappings[i].start, mappings[i].end, mappings[i].path);
            if(strstr(NT_Mappings[i].path, filename)) {
                return &NT_Mappings[i];
            }
        }
        return nullptr;
    }

    /**
     * Return bool for whether a Program Header obviously unnecessary.
     * We have Segment::is_relevant() but can avoid getting as far as creating a Segment.
     */
    bool is_unwanted_phdr(Elf64_Phdr phdr) {
        return (phdr.p_memsz == 0 || phdr.p_filesz == 0);
    }


    bool is_inside(Elf64_Addr from, Elf64_Addr x, Elf64_Addr to) {
        return from <= x && x < to;
    }

    bool is_inside(Elf64_Phdr phdr, Elf64_Addr start, Elf64_Addr end) {
        return is_inside(start, phdr.p_vaddr, end)
        || is_inside(start, phdr.p_vaddr + phdr.p_memsz, end);
    }

    // Get list of memory mappings in core file.
    // Create list of mappings for revived process.
    //
    // Write mappings file.
    // use fd, create a Segment, call int Segment::write_mapping(int fd)

    // For each program header:
    //  If filesize or memsize is zero, skip it (maybe log)
    //  If it touches an unwantedmapping, skip it (maybe log)
    //  If not writeable and inOtherMapping* skip it (maybe log)
    //  Write an "M" entry
    void write_mappings(int mappings_fd, const char* exec_name) {
        if (verbose) log("create_mappings_pd");
        read_NT_mappings();
        int n_skipped = 0;
        lseek(file, EHDR.e_phoff, SEEK_SET);
        for (int i = 0; i < EHDR.e_phnum; i++) {
            // TODO skip some 
            
            Elf64_Phdr phdr;

            lseek(file, EHDR.e_phoff + i*sizeof(phdr), SEEK_SET);
            phdr = read_type<Elf64_Phdr>();
            if (is_unwanted_phdr(phdr)) {
                n_skipped++;
                continue;
            } 
            if (phdr.p_vaddr >= max_user_vaddr_pd()) {
                break; // Kernel mapping?  Not something we can map in.  Phdrs are in ascending address order.
            }

            // Now we want to exclude this mapping if it touches any unwanted mapping. 
            // Let's start with /bin/java #1
            // If the virtaddr is between start and end, it touches, exclude it
            bool skip = false;
            for (int i = 0; i < NT_Mappings_count; i++) {
                if(
                is_inside(phdr, NT_Mappings[i].start, NT_Mappings[i].end)
                && (
                    strstr(NT_Mappings[i].path, exec_name) || false//!(phdr.p_flags & PF_W)
                )
                ) {
                    skip = true;
                    break;
                }
            }
            if (skip) {
                if (verbose) fprintf(stderr, "\nSkipping due to bin/java at %lu\n", phdr.p_vaddr);
                n_skipped++;
                continue;
            }

            if (!(phdr.p_flags & PF_W)) {
                skip = false;
                for (int i = 0; i < NT_Mappings_count; i++) {
                    if(
                        is_inside(phdr, NT_Mappings[i].start, NT_Mappings[i].end)
                    ) {
                        skip = true;
                        break;
                    }
                }
                if (skip) {
                    if (verbose) fprintf(stderr, "\nSkipping due to nonwritable overlap at %lu\n", phdr.p_vaddr);
                    n_skipped++;
                    continue;
                }
            }

            // TODO plt/GOTs maybe
            
            if (verbose) log("Writing: %lu", phdr.p_vaddr);
            Segment s((void*)phdr.p_vaddr, phdr.p_memsz, phdr.p_offset, phdr.p_filesz);
            s.write_mapping(mappings_fd);
        }

        if (verbose) log("create_mappings_pd done.  Skipped = %i", n_skipped);
    }
};

const char *corePageFilename;
struct ELFOperations;

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
        warn("init_pd: sysconf retuns 0x%lx: %s", value, strerror(errno));
        value = 0x1000; // consider exiting
    }
    vaddr_align = value;
}

bool revival_direxists_pd(const char *dirname) {
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


bool mem_canwrite_pd(void *vaddr, size_t length) {
    return true;
}


void *do_mmap_pd(void *addr, size_t length, char *filename, int fd, off_t offset) {
    int flags = MAP_SHARED | MAP_PRIVATE | MAP_FIXED;
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
        warn("do_mmap_pd: mmap(%p, %zu, %d, %d, %d, offset %zu) failed: returns: %p: errno = %d: %s",
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
        warn("revival_checks: cannot open directory '%s'", dirname);
        return -1;
    }
    // Check LD_USE_LOAD_BIAS is set:
    char * env = getenv("LD_USE_LOAD_BIAS");
    if (env == NULL || strncmp(env, "1", 1) != 0) {
        warn("Error: LD_USE_LOAD_BIAS not set.");
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
        warn("pmap: %d", e);
    }
}

void *symbol_dynamiclookup_pd(void *h, const char *str) {
    void *s = dlsym(RTLD_NEXT, str);
    if (verbose) {
        fprintf(stderr, "symbol_dynamiclookup: %s = %p \n", str, s);
    }
    if (s == 0) {
        if (verbose) {
            warn("dlsym: %s", dlerror());
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
                warn("revival: remove existing core page file failed: %d", e);
            }
            fdTemp = open(tempName, O_WRONLY | O_CREAT | O_EXCL, 0600); 
            if (fdTemp < 0) {
                warn("cannot remove open existing core page file '%s': %d", tempName, fdTemp);
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
            warn("cannot create page file for writes to core file memory.");
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
        warn("writeTempFileBytes: lseek fails %d : %s", errno, strerror(errno));
        close(fdTemp);
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
        warn("remap: failed to create temp file. errno = %d: %s",  errno, strerror(errno));
        abort();
    }
    off_t offset = writeTempFileBytes(tempName, seg);
    if (offset == (off_t) -1 ) {
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
#else // i.e. __aarch64__
        pc = (void *) ((ucontext_t*)ucontext)->uc_mcontext.pc;
#endif
        // fprintf(stderr, "handler: pc = %p\n", pc);
        if (is_safefetch_fault(pc)) {
            void * new_pc = continuation_for_safefetch_fault(pc);
#if defined (X86_64) || defined (__x86_64__)
            ((ucontext_t*)ucontext)->uc_mcontext.gregs[REG_RIP] = (greg_t) new_pc;
#else // i.e. __arch64__
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
            warn("Access to segment that failed to revive: si_addr = %p found failed segment %p", addr, iter->vaddr);
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
    warn("handler: si_addr = %p : not handling, abort...", addr);
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
    int tries = 1; // can try unload and retry if not as requested.

    for (int i = 0; i < tries; i++) {
        void *h = dlopen(name,  RTLD_NOW | RTLD_GLOBAL);

        if (!h) {
            warn("load_sharedobject_pd: %s: %s", name, dlerror());
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
        warn("load_sharedobject_pd: %s: failed, loads at %p", name, actual);
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
            warn("load_sharedobject_pd: %s: %s", name, dlerror());
            return (void *) -1;
        }
        return h;
    }
}

int unload_sharedobject_pd(void *h) {
    return dlclose(h); // zero on success
}

void init_jvm_filename_pd(ELFOperations& core) {
}

void copy_file_pd(const char *srcfile, const char *destfile) {
    // sendfile(outfd, infd, 0, count);
    char command[BUFLEN];
    memset(command, 0, BUFLEN);
    strncat(command, "cp ", BUFLEN - 1);
    strncat(command, srcfile, BUFLEN - 1);
    strncat(command, " ", BUFLEN - 1);
    strncat(command,  destfile, BUFLEN - 1);
    int e = system(command);
    logv("copy: '%s' returns %d", command, e);
    if (e != 0) {
        warn("copy_file_pd: %s", strerror(errno));
    }
}

const int N_JVM_SYMS = 7;
const char *JVM_SYMS[N_JVM_SYMS] = {
    SYM_REVIVE_VM,
    SYM_TTY,
    SYM_JVM_VERSION,
    SYM_TC_OWNER,
    SYM_PARSE_AND_EXECUTE,
    SYM_THROWABLE_PRINT,
    SYM_THREAD_KEY
    /* safefetch syms: not required in latest JDK.
    "_ZN12StubRoutines21_safefetch32_fault_pcE",
    "_ZN12StubRoutines28_safefetch32_continuation_pcE",
    "_ZN12StubRoutines20_safefetchN_fault_pcE",
    "_ZN12StubRoutines27_safefetchN_continuation_pcE" */
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

void create_directory(const char* dirname) {
    if (mkdir(dirname, S_IRUSR | S_IWUSR | S_IXUSR) < 0) {
        error("create_directory: %s", strerror(errno));
    }
}

void init_jvm_filename_and_address(ELFOperations& core) {
    SharedLibMapping* jvm_mapping = core.get_library_mapping(JVM_FILENAME);
    jvm_filename = jvm_mapping->path;
    jvm_address = (void*) jvm_mapping->start;
    logv("JVM = '%s'", jvm_filename);
    logv("JVM addr = %p", jvm_address);
}

/**
 * Create "core.revival" directory containing what's needed to revive a corefile.
 *  * A copy of libjvm.so, relocated to load at the same address as it was in the corefile
 *  * "core.mappings" a text file with instructions on which segments to load from the core
 *  * "jvm.symbols" a text file with information about important symbols in libjvm.so
 */
int create_revivalbits_native_pd(const char *corename, const char *javahome, const char *revival_dirname, const char *libdir) {
    logv("create_revivalbits_native_pd");
    create_directory(revival_dirname);

    // find libjvm and its load address from core
    // Q will this make sense in a "transported core" scenario? / Ludvig
    ELFOperations core(open_for_read(corename));
    init_jvm_filename_and_address(core);
    
    // copy libjvm into core.revival dir
    char jvm_copy_path[BUFLEN];
    memset(jvm_copy_path, 0, BUFLEN);
    strncpy(jvm_copy_path, revival_dirname, BUFLEN - 1);
    strncat(jvm_copy_path, "/" JVM_FILENAME, BUFLEN - 1);
    copy_file_pd(jvm_filename, jvm_copy_path);

    // Create mappings file
    int mappings_fd = mappings_file_create(revival_dirname, corename);
    if (mappings_fd < 0) {
        // error already printed
        return -1;
    }
    core.write_mappings(mappings_fd, "bin/java");
    close_file_descriptor(mappings_fd, "mappings file");
    close_file_descriptor(core.file, "core file");

    // relocate copy of libjvm:
    ELFOperations jvm_copy(open_for_read_and_write(jvm_copy_path));
    logv("Relocate copy of libjvm");
    jvm_copy.relocate((unsigned long) jvm_address /* assume library currently has zero base address */);
    logv("Relocate copy of libjvm done");

    // Create symbols file
    int symbols_fd = symbols_file_create(revival_dirname);
    if (symbols_fd < 0) {
        warn("Failed to create mappings file\n");
        return -1;
    }
    logv("Write libjvm symbols");
    jvm_copy.write_jvm_symbols(symbols_fd, JVM_SYMS, N_JVM_SYMS);
    logv("Write libjvm symbols done");
    close_file_descriptor(symbols_fd, "symbols file");
    close_file_descriptor(jvm_copy.file, "jvm copy");
    
    logv("create_revivalbits_native_pd returning %d", 0);
    return 0;
}

// TODO revert to actual error handling?
