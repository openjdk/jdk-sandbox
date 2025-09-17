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

extern unsigned long long file_size(const char *filename);

/**
 * Operations on ELF files.
 * To mmap the file and update in-memory proves faster than lseek and read/write.
 *
 * But some core files may be unreasonable to mmap fully.
 * Currently we only mmmap, may need to change that above some file size, or ensure
 * what we need is mapped in.
 */
class ELFOperations {
  private:
    Elf64_Ehdr *EHDR; // Main ELF Header
    Elf64_Phdr *ph;   // First Program Header absolute address
    Elf64_Shdr *sh;   // First Section Header absolute address or nullptr

    char* SHDRSTR_BUFFER = nullptr;
    SharedLibMapping* NT_Mappings = 0;
    int NT_Mappings_count = 0;

    int fd = -1;
    long long length = -1;
    void *m = (void *) 0;

  public:
    ELFOperations(const char *filename) {
        fd = open(filename, O_RDWR);
        if (fd < 0) {
            error("cannot open '%s': %s", filename, strerror(errno));
        }
        length = file_size(filename);
        m = mmap(0, length, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
        if (m == (void *) -1) {
            error("mmap of ELF file failed: %s", strerror(errno));
        }
        EHDR = (Elf64_Ehdr*) m;

        // Set a resolved ph and sh pointer for ease.

        // NOT this:    Elf64_Phdr *phtest1 = (Elf64_Phdr*) (char *) m + (EHDR->e_phoff);
        // BUT this:    Elf64_Phdr *phtest1 = (Elf64_Phdr*) ((char *) m + (EHDR->e_phoff));
        ph = (Elf64_Phdr*) ((char *) m + (EHDR->e_phoff));

        // cores don't usually have Sections:
        if (EHDR->e_shoff > 0) {
            sh = (Elf64_Shdr*) ((char *) m + (EHDR->e_shoff));
            Elf64_Shdr* strndx_shdr = section_by_index(EHDR->e_shstrndx);
            SHDRSTR_BUFFER = (char *) m + strndx_shdr->sh_offset;
        } else {
            sh = nullptr;
        }

        // If verbose...
        logv("ELFOperations: %s EHDR = %p phoff = 0x%lx shoff = 0x%lx   ph = %p sh = %p",
             filename, EHDR, EHDR->e_phoff, EHDR->e_shoff, ph, sh);
        verify();
        // print();
    }

    void verify() {
        assert(EHDR->e_ident[0] == 0x7f);
        assert(EHDR->e_ident[1] == 'E');
        assert(EHDR->e_ident[2] == 'L');
        assert(EHDR->e_ident[3] == 'F');
        assert(EHDR->e_ident[4] == ELFCLASS64);
        assert(EHDR->e_ident[5] == ELFDATA2LSB);
        assert(EHDR->e_ident[6] == EV_CURRENT);
        assert(EHDR->e_ident[7] == ELFOSABI_SYSV);
        assert(EHDR->e_version == EV_CURRENT);

        // elf.h in devkit on Linux x86_64 does not define EM_AARCH64
#if defined(__aarch64__)
        assert(EHDR->e_machine == EM_AARCH64);
#else
        assert(EHDR->e_machine == EM_X86_64);
#endif
        if (EHDR->e_phnum == PN_XNUM) {
            error("Too many program headers, handling not implemented (%x)", EHDR->e_phnum);
        }
        if (EHDR->e_type == ET_DYN && EHDR->e_shnum == 0) {
            error("Invalid number of section headers in shared library, zero.");
        }
        assert(EHDR->e_phentsize == sizeof(Elf64_Phdr));
        assert(EHDR->e_shentsize == 0 || EHDR->e_shentsize == sizeof(Elf64_Shdr));

        // Sanity check the pointer arithmetic:
        bool debug = false;
        if (debug) {
        Elf64_Phdr* p0 = program_header(0);
        Elf64_Phdr* p1 = program_header(1);
        long diff = (long) ((uint64_t) p1 - (uint64_t) p0);
        Elf64_Phdr* p1a = next_ph(p0);
        warn("ph %p %p %p diff: %ld", p0, p1, p1a, diff);
        assert(p1 == p1a);
        assert(diff == EHDR->e_phentsize);

        Elf64_Shdr* s0 = section_header(0);
        Elf64_Shdr* s1 = section_header(1);
        diff = (long) ((uint64_t) s1 - (uint64_t) s0);
        Elf64_Shdr* s1a = next_sh(s0);
        warn("sh %p %p %p diff: %ld", s0, s1, s1a, diff);
        assert(s1 == s1a);
        assert(diff == EHDR->e_shentsize);
        }
    }

    ~ELFOperations() {
        // Free NT_mappings

        if (fd >= 0) {
            ::close(fd);
        }
        if (m != 0) {
            logv("ELFOperations: destructor munmap");
            do_munmap_pd(m, length);
        }
        logv("ELFOperations: destructor done");
    }

  public:
    void print() {
        for (int i = 0; i < EHDR->e_phnum; i++) {
            Elf64_Phdr* p = program_header(i);
            fprintf(stderr, "PH %3d %p  Type: %d offset: 0x%lx vaddr: 0x%lx\n", i, p, p->p_type, p->p_offset, p->p_vaddr);
        }
        for (int i = 0; i < EHDR->e_shnum; i++) {
            Elf64_Shdr* p = section_header(i);
            fprintf(stderr, "SH %3d %p  Type: %d addr: 0x%lx\n", i, p, p->sh_type, p->sh_addr);
        }
    }

/*    template <typename T>
    T read_type() {
        T ret;
        if (read(fd, &ret, sizeof(T)) != sizeof(T)) {
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
    } */


  private:
    // Section header actual address in mmapped file.
    Elf64_Shdr* section_header(unsigned long i) {
        return (Elf64_Shdr*) ((char*) sh + (i * EHDR->e_shentsize));
    }

    // Program header actual address in mmapped file.
    Elf64_Phdr* program_header(unsigned long i) {
        return (Elf64_Phdr*) ((char*) ph + (i * EHDR->e_phentsize));
    }

    bool section_name_is(Elf64_Shdr* shdr, const char* name) {
        return (strcmp(name, SHDRSTR_BUFFER + shdr->sh_name) == 0);
    }

    Elf64_Shdr* next_sh(Elf64_Shdr* s) {
        return (Elf64_Shdr*) ((char *) s + EHDR->e_shentsize);
    }

    Elf64_Phdr* next_ph(Elf64_Phdr* p) {
        return (Elf64_Phdr*) ((char *) p + EHDR->e_phentsize);
    }

    Elf64_Shdr* section_by_name(const char* name) {
        Elf64_Shdr* s = sh;
        for (int i = 0; i < EHDR->e_shnum; i++) {
            if (section_name_is(s, name)) {
                return s;
            }
            s = next_sh(s);
        }
        error("Section not found: %s", name);
        return nullptr; // Will not reach here
    }

    Elf64_Shdr* section_by_index(unsigned long index) {
        return (Elf64_Shdr*) ((char*) sh + (index * EHDR->e_shentsize));
    }

    // Returns the first phdr where predicate returns true.
    Elf64_Phdr* program_header_by_predicate(bool (*predptr)(Elf64_Phdr*)) {
        Elf64_Phdr* phdr = ph;
        for (int i = 0; i < EHDR->e_phnum; i++) {
            if (predptr(phdr)) {
                return phdr;
            }
            phdr = next_ph(phdr);
        }
        return nullptr;
    }

    Elf64_Phdr* program_header_by_type(Elf32_Word type) {
        Elf64_Phdr* phdr = ph;
        for (int i = 0; i < EHDR->e_phnum; i++) {
            if (phdr->p_type == type) {
                return phdr;
            }
            phdr = next_ph(phdr);
        }
        return nullptr;
    }

    bool should_relocate_addend(Elf64_Rela* rela) {
        switch (ELF64_R_TYPE(rela->r_info)) {
#if defined(__aarch64__)
            case R_AARCH64_RELATIVE:
#else
            case R_X86_64_RELATIVE:
#endif
                return true;
            default:
                return false;
        }
    }

    void relocate_execution_header(long displacement) {
        if (EHDR->e_entry != 0) {
            EHDR->e_entry += displacement;
        }
    }

    bool should_relocate_program_header(Elf64_Phdr* phdr) {
        return phdr->p_type != PT_GNU_STACK;
    }

    void relocate_program_headers(long displacement) {
        Elf64_Phdr* p = ph;
        for (int i = 0; i < EHDR->e_phnum; i++) {
            logv("relocate_program_headers %3d %p", i, p);
            if (should_relocate_program_header(p)) {
                p->p_vaddr += displacement;
                p->p_paddr += displacement;
#ifdef __aarch64__
                p->p_align = 0x1000;
#endif
            }
            p = next_ph(p);
        }
    }

    bool should_relocate_section_header(Elf64_Shdr* shdr) {
        if (section_name_is(shdr, ".comment")) return false;
        if (section_name_is(shdr, ".note.stapsdt")) return false;
        if (section_name_is(shdr, ".note.gnu.gold-version")) return false;
        if (section_name_is(shdr, ".gnu_debuglink")) return false;
        if (section_name_is(shdr, ".symtab")) return false;
        if (section_name_is(shdr, ".shstrtab")) return false;
        if (section_name_is(shdr, ".strtab")) return false;
        if (shdr->sh_type == SHT_NULL) return false;
        return true;
    }

    void relocate_section_headers(long displacement) {
        Elf64_Shdr* s = sh;
        for (int i = 0; i < EHDR->e_shnum; i++) {
            logv("relocate_section_headers %3d %p", i, s);
            if (should_relocate_section_header(s)) {
                s->sh_addr += displacement;
            }
            s = next_sh(s);
        }
    }

    void relocate_relocation_table(long displacement, const char* name) {
        Elf64_Shdr* sh_reladyn = section_by_name(name);
        for (unsigned long o = sh_reladyn->sh_offset; o < (sh_reladyn->sh_offset + sh_reladyn->sh_size); o += sh_reladyn->sh_entsize) {
            Elf64_Rela* rela = (Elf64_Rela*) ((uint64_t) m + o);
            rela->r_offset += displacement;
            if (should_relocate_addend(rela)) {
                rela->r_addend += displacement;
            }
        }
    }

    bool should_relocate_dynamic_tag(Elf64_Dyn* dyn) {
        // Dynamic entries that use the d_ptr union member should stay relative to base address?
        // Or does that not apply to us, as will have a set load address...
        // https://docs.oracle.com/cd/E19455-01/816-0559/chapter6-35405/index.html
        switch (dyn->d_tag) {
            case DT_INIT:
            case DT_FINI:
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


    uint64_t find_dynamic_value(Elf64_Shdr* s, int tag) {
        for (unsigned long o = s->sh_offset; o < s->sh_offset + s->sh_size; o += s->sh_entsize) {
            Elf64_Dyn* dyn = (Elf64_Dyn*) ((uint64_t) m + o);
            if (dyn->d_tag == DT_NULL) {
                break;
            }
            if (dyn->d_tag == tag) {
                return dyn->d_un.d_val;
            }
        }
        return 0;
    }

    /**
     * Relocate e.g. INIT_ARRAY contents.
     */
    void relocate_dyn_array(long displacement, Elf64_Dyn* dyn, int count) {
        logv("relocate_dyn_array: updating %d", count);
        // Get our mmapped address of the array:
        uint64_t *p = (uint64_t*) ((uint64_t) m + dyn->d_un.d_ptr);
        // Relocate contents:
        for (int i = 0; i < count; i++) {
            if (*p != 0) {
                uint64_t contents = *(uint64_t*) p;
                uint64_t newval = contents + displacement;
                *p = newval;
            }
            p++;
        }
        // Adjust dynamic table entry:
        dyn->d_un.d_ptr += displacement;
    }

    void relocate_dynamic_table(long displacement) {
        Elf64_Shdr* s = section_by_name(".dynamic");
        for (unsigned long o = s->sh_offset; o < s->sh_offset + s->sh_size; o += s->sh_entsize) {
            Elf64_Dyn* dyn = (Elf64_Dyn*) ((uint64_t) m + o);
            if (dyn->d_tag == DT_NULL) {
                break;
            }
            // Special-case for the .init array contents:
            if (dyn->d_tag == DT_INIT_ARRAY) {
                int count = find_dynamic_value(s, DT_INIT_ARRAYSZ) / sizeof(uint64_t);
                relocate_dyn_array(displacement, dyn, count);
            } else if (dyn->d_tag == DT_FINI_ARRAY) {
                int count = find_dynamic_value(s, DT_FINI_ARRAYSZ) / sizeof(uint64_t);
                relocate_dyn_array(displacement, dyn, count);
            } else if (should_relocate_dynamic_tag(dyn)) {
               dyn->d_un.d_ptr += displacement;
            }
        }
    }

    bool should_relocate_symbol(Elf64_Sym* sym) {
        if (ELF64_ST_TYPE(sym->st_info) == STT_TLS) return false;
        if (sym->st_shndx == 0) return false;
        if (sym->st_shndx == SHN_ABS) return false;
        return true;
    }

    void relocate_symbol_table(long displacement, const char* name) {
        Elf64_Shdr* s = section_by_name(name);
        for (unsigned long o = s->sh_offset; o < s->sh_offset + s->sh_size; o += s->sh_entsize) {
            Elf64_Sym* s = (Elf64_Sym*) ((uint64_t) m + o);
            if (should_relocate_symbol(s)) {
                s->st_value += displacement;
            }
        }
    }

    void read_bytes_at(unsigned long at, ssize_t bytes, char* buffer) {
        if (lseek(fd, at, SEEK_SET) == -1) {
            error("read_bytes_at: %s", strerror(errno));
        }
        if (read(fd, buffer, bytes) != bytes) {
            error("read_bytes_at: %s", strerror(errno));
        }
    }

    void read_bytes(ssize_t bytes, char* buffer) {
        if (read(fd, buffer, bytes) != bytes) {
            error("read_bytes: %s", strerror(errno));
        }
    }

  public:
    // Relocate file by some amount.
    void relocate(long displacement) {
        assert(EHDR->e_type == ET_DYN);
        assert(sh != 0);
        assert(SHDRSTR_BUFFER != 0);

        relocate_execution_header(displacement);
        relocate_program_headers(displacement);
        relocate_section_headers(displacement);
        relocate_relocation_table(displacement, ".rela.dyn");
        relocate_relocation_table(displacement, ".rela.plt");
        relocate_dynamic_table(displacement);
        relocate_symbol_table(displacement, ".dynsym");
        relocate_symbol_table(displacement, ".symtab");
    }

    // Write info for given symbols.
    // If dlopen relocated libjvm, should use dlsym results.
    // Write symbol list for revived process.
    void write_symbols(int fd, const char* symbols[], int count) {
        Elf64_Shdr* strtab = section_by_name(".strtab");
        char* SYMTAB_BUFFER = (char *) m + strtab->sh_offset;

        Elf64_Shdr* symtab = section_by_name(".symtab");
        for (long unsigned int i = 0; i < symtab->sh_size / symtab->sh_entsize; i++) {
            Elf64_Sym* sym = (Elf64_Sym*) ((uint64_t) m + (symtab->sh_offset + i * symtab->sh_entsize));

            for (int j = 0; j < count; j++) {
                int ret = strcmp(symbols[j], SYMTAB_BUFFER + sym->st_name);
                if (ret == 0) {
                    char buf[2048];
                    int len = snprintf(buf, 2048, "%s %llx\n",
                            SYMTAB_BUFFER + sym->st_name,
                            (unsigned long long) sym->st_value);
                    int e = write(fd, buf, len);
                    if (e != len) {
                        warn("write_symbols: write error: %s", strerror(errno));
                    }
                }
            }
        }
    }

  private:
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

    char* find_note_data(Elf64_Phdr* notes_ph, Elf64_Word type) {
        // Read NOTES.  p_filesz is limit.
        Elf64_Nhdr* nhdr = (Elf64_Nhdr*) ((uint64_t) m + notes_ph->p_offset);
        Elf64_Nhdr* end  = nhdr + notes_ph->p_filesz;

        while (nhdr < end) {
            logv("NOTE at %p type 0x%x namesz %x descsz %x", nhdr, nhdr->n_type, nhdr->n_namesz, nhdr->n_descsz);
            char *pos = (char*) nhdr;
            pos += sizeof(Elf64_Nhdr);
            if (nhdr->n_namesz > 0) {
                char *name = (char *) pos;
                logv("NOTE name='%s'", name);
                pos += nhdr->n_namesz; // n_namesz includes terminator
            }
            // Align. 4 byte alignment, including when 64-bit.
            while (((unsigned long long) pos & 0x3) != 0) {
                pos++;
            }
            // After aligning, pos points at actual NOTE data.
            if (nhdr->n_type == type) {
                return pos;
            }
            pos += nhdr->n_descsz;
            nhdr = (Elf64_Nhdr*) pos;
        }
        return nullptr;
    }

    // Read core NOTES, find NT_FILE, find libjvm.so
    // core files only
    void read_NT_mappings() {
        if (NT_Mappings != 0) return;

        // Look for Program Header PT_NOTE:
        Elf64_Phdr* notes_ph = program_header_by_type(PT_NOTE);
        if (notes_ph == nullptr) {
            error("Cannot locate NOTES");
        }
        // Look for NT_FILE note:
        char* note_nt_file = find_note_data(notes_ph, 0x46494c45 /* NT_FILE */);
        if (note_nt_file == nullptr) {
            error("Cannot locate NOTE NT_FILE");
        }
        logv("NT_FILE note data at %p", note_nt_file);
        // Read NT_FILE:
        NT_Mappings_count = *(long*)note_nt_file;
        note_nt_file += 8;
        long pagesize = *(long*) note_nt_file;
        note_nt_file += 8;
        logv("NT_FILE count %d pagesize 0x%lx", NT_Mappings_count, pagesize);

        NT_Mappings = new SharedLibMapping[NT_Mappings_count];
        for (int i = 0; i < NT_Mappings_count; i++) {
            NT_Mappings[i].start = *(long*) note_nt_file;
            note_nt_file += 8;
            NT_Mappings[i].end = *(long*) note_nt_file;
            note_nt_file += 8;
            note_nt_file += 8; // skip offset
        }
        for (int i = 0; i < NT_Mappings_count; i++) {
            NT_Mappings[i].path = note_nt_file; // readstring(file);
            note_nt_file += strlen(NT_Mappings[i].path);
            note_nt_file++; // terminator
        }
        if (verbose) {
            for (int i = 0; i < NT_Mappings_count; i++) {
                fprintf(stderr, "NT_FILE: %lu %lu %s\n", NT_Mappings[i].start, NT_Mappings[i].end, NT_Mappings[i].path);
            }
        }
    }

  public:
    /**
     * Return mapping information.  Data is valid while this ELFOperations is alive,
     * information should be copied to retain.
     */
    SharedLibMapping* get_library_mapping(const char* filename) {
        read_NT_mappings();
        for (int i = 0; i < NT_Mappings_count; i++) {
            if (strstr(NT_Mappings[i].path, filename)) {
                return &NT_Mappings[i];
            }
        }
        return nullptr;
    }

  private:
    /**
     * Return bool for whether a Program Header is obviously unnecessary.
     * We have Segment::is_relevant() but can avoid getting as far as creating a Segment.
     */
    bool is_unwanted_phdr(Elf64_Phdr* phdr) {
        return (phdr->p_memsz == 0 || phdr->p_filesz == 0);
    }


    bool is_inside(Elf64_Addr from, Elf64_Addr x, Elf64_Addr to) {
        return from <= x && x < to;
    }

    bool is_inside(Elf64_Phdr* phdr, Elf64_Addr start, Elf64_Addr end) {
        return is_inside(start, phdr->p_vaddr, end)
               || is_inside(start, phdr->p_vaddr + phdr->p_memsz, end);
    }

    // Get list of memory mappings in core file.
    // Create list of mappings for revived process.
    // Write mappings file.
    //
    // Create a Segment, call Segment::write_mapping(int fd)

    // For each program header:
    //  If filesize or memsize is zero, skip it (maybe log)
    //  If it touches an unwantedmapping, skip it (maybe log)
    //  If not writeable and inOtherMapping* skip it (maybe log)
    //  Write an "M" entry
  public:
    void write_mappings(int mappings_fd, const char* exec_name) {
        logv("write_mappings");
        if (EHDR->e_type != ET_CORE) {
            warn("write_mappings: Not writing mappings for non-core file.");
            return;
        }

        read_NT_mappings();
        int n_skipped = 0;
        Elf64_Phdr* phdr = ph;
        for (int i = 0; i < EHDR->e_phnum; i++) {
            // TODO skip some

            if (is_unwanted_phdr(phdr)) {
                n_skipped++;
                phdr = next_ph(phdr);
                continue;
            }
            if (phdr->p_vaddr >= max_user_vaddr_pd()) {
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
                logv("\nSkipping due to bin/java at %lu\n", phdr->p_vaddr);
                n_skipped++;
                phdr = next_ph(phdr);
                continue;
            }

            if (!(phdr->p_flags & PF_W)) {
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
                    if (verbose) fprintf(stderr, "\nSkipping due to nonwritable overlap at %lu\n", phdr->p_vaddr);
                    n_skipped++;
                    phdr = next_ph(phdr);
                    continue;
                }
            }

            // TODO plt/GOTs maybe

            logv("Writing: %lu", phdr->p_vaddr);
            Segment s((void*) phdr->p_vaddr, phdr->p_memsz, phdr->p_offset, phdr->p_filesz);
            s.write_mapping(mappings_fd);
            phdr = next_ph(phdr);
        }

        if (verbose) log("create_mappings_pd done.  Skipped = %i", n_skipped);
    }
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
    if (openCoreWrite) {
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
    exitForRetry();
    abort(); // Not reached
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
                error("cannot remove open existing core page file '%s': %d", tempName, fdTemp);
            }
        }
    }
    return tempName;
}

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
        error("remap: failed to create temp filename. errno = %d: %s",  errno, strerror(errno));
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

    if (addr == nullptr) {
        warn("handler: null address");
        abort();
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
            warn("Access to segment that failed to revive: si_addr = %p in failed segment %p", addr, iter->vaddr);
            exitForRetry();
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
            remap((Segment) *iter);
            return;
        }
    }
    warn("handler: si_addr = %p : not handled.", addr);
//    exitForRetry();
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
    int tries = 1; // could try unload and retry if final vaddr not as requested.

    for (int i = 0; i < tries; i++) {
        void *h = dlopen(name,  RTLD_NOW | RTLD_GLOBAL);

        if (!h) {
            warn("load_sharedobject_pd: dlopen failed: %s: %s", name, dlerror());
            return (void *) -1;
        }

        actual = base_address_for_sharedobject_live(h);
        if (verbose) {
            fprintf(stderr, "load_sharedobject_pd %d: actual = %p \n", i, actual);
        }

        if (actual == (void *) 0 || actual == vaddr) {
            return h;
        }

        // Wrong address:
        // Most likely, Address Space Layout Randomisation has given us an inhospitable layout,
        // e.g. libc where we want to have libjvm.
        // Terminate with a value that means caller should retry:
        exitForRetry();

        // Other alternative tested was: dlclose and map/block.  But not successful.
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

const int N_JVM_SYMS = 1;
const char *JVM_SYMS[N_JVM_SYMS] = {
    SYM_REVIVE_VM
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
    jvm_filename = strdup(jvm_mapping->path);
    jvm_address = (void*) jvm_mapping->start;
    logv("JVM = '%s'", jvm_filename);
    logv("JVM addr = %p", jvm_address);
}

bool try_init_jvm_filename_if_exists(const char* path, const char* suffix) {
    char search_path[BUFLEN];
    memset(search_path, 0, BUFLEN);
    strncpy(search_path, path, BUFLEN - 1);
    strncat(search_path, suffix, BUFLEN - 1);
    int fd = open(search_path, O_RDONLY);
    if (fd >= 0) {
        struct stat buffer;
        fstat(fd, &buffer);
        if (!S_ISDIR(buffer.st_mode)) {
            free(jvm_filename);
            jvm_filename = strdup(search_path);
            return true;
        }
    }
    return false;
}

void init_jvm_filename_from_libdir(const char* libdir) {
    if (try_init_jvm_filename_if_exists(libdir, "")) return;
    if (try_init_jvm_filename_if_exists(libdir, "/libjvm.so")) return;
    if (try_init_jvm_filename_if_exists(libdir, "/server/libjvm.so")) return;
    if (try_init_jvm_filename_if_exists(libdir, "/lib/server/libjvm.so")) return;
    warn("Could not find libjvm.so in %s", libdir);
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
int create_revivalbits_native_pd(const char *corename, const char *javahome, const char *revival_dirname, const char *libdir) {
    logv("create_revivalbits_native_pd");

    // Find libjvm and its load address from core
    // Q will this make sense in a "transported core" scenario? / Ludvig
    {
        ELFOperations core(corename);
        init_jvm_filename_and_address(core);

        if (libdir != nullptr) {
            init_jvm_filename_from_libdir(libdir);
        } else {
            // verify libjvm.so from coreexists
            if (!try_init_jvm_filename_if_exists(jvm_filename, "")) {
                warn("JVM library required in core not found at: %s", jvm_filename);
                warn("If using a transported core, you must use -L to specify a copy of the JDK.");
                return -1;
            }
        }

        create_directory(revival_dirname);

        // Create mappings file
        int mappings_fd = mappings_file_create(revival_dirname, corename);
        if (mappings_fd < 0) {
            // error already printed
            return -1;
        }
        core.write_mappings(mappings_fd, "bin/java");
        close_file_descriptor(mappings_fd, "mappings file");
    }

    // Copy libjvm into core.revival dir
    char jvm_copy_path[BUFLEN];
    memset(jvm_copy_path, 0, BUFLEN);
    strncpy(jvm_copy_path, revival_dirname, BUFLEN - 1);
    strncat(jvm_copy_path, "/" JVM_FILENAME, BUFLEN - 1);
    warn("Copying libjvm.so from %s", jvm_filename);
    copy_file_pd(jvm_filename, jvm_copy_path);

    // Relocate copy of libjvm:
    {
        ELFOperations jvm_copy(jvm_copy_path);
        logv("Relocate copy of libjvm to %p", jvm_address);
        jvm_copy.relocate((unsigned long) jvm_address /* assume library currently has zero base address */);
        logv("Relocate copy of libjvm done");

        // Create symbols file
        int symbols_fd = symbols_file_create(revival_dirname);
        if (symbols_fd < 0) {
            warn("Failed to create symbols file\n");
            return -1;
        }
        logv("Write libjvm symbols");
        jvm_copy.write_symbols(symbols_fd, JVM_SYMS, N_JVM_SYMS);
        logv("Write libjvm symbols done");
        close_file_descriptor(symbols_fd, "symbols file");
    }

    // Copy libjvm.debuginfo if present
    char jvm_debuginfo_path[BUFLEN];
    char jvm_debuginfo_copy_path[BUFLEN];
    snprintf(jvm_debuginfo_path, BUFLEN, "%s", jvm_filename);
    char *p = strstr(jvm_debuginfo_path, ".so");
    if (p != nullptr) {
        snprintf(p, BUFLEN, ".debuginfo");
        snprintf(jvm_debuginfo_copy_path, BUFLEN - 1, "%s/libjvm.debuginfo", revival_dirname);
        copy_file_pd(jvm_debuginfo_path, jvm_debuginfo_copy_path);
    }

    logv("create_revivalbits_native_pd returning %d", 0);
    return 0;
}

