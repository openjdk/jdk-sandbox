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
#include <string>
#include <cstdio>
#include <cstring>
#include <cassert>
#include <iostream>
#include <fstream>
#include <elf.h>

#include "revival.hpp"

// A minimal set of operations for doing what we need to do on ELF files.
// ELFOperations is passed an open file. 
// read_basics() must be called prior to other operations.
struct ELFOperations {
    long long relocation_amount = 0;
    int ELF_FILE = -1;
    Elf64_Ehdr EHDR;
    char* SHDRSTR_BUFFER = 0;

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

        ELF_FILE = fd;
        assert(ELF_FILE != -1);
    }

    void read_basics() {
        EHDR = read_type_at<Elf64_Ehdr>(0);
        assert_ELF_sound();
        Elf64_Shdr strndx_shdr = section_by_index(EHDR.e_shstrndx);
        SHDRSTR_BUFFER = new char[strndx_shdr.sh_size];
        read_bytes_at(strndx_shdr.sh_offset, strndx_shdr.sh_size, SHDRSTR_BUFFER);
    }

    template <typename T>
    T read_type_at(unsigned long at) {
        T ret;
        if (lseek(ELF_FILE, at, SEEK_SET) == -1) {
            error(strerror(errno));
        };
        if (read(ELF_FILE, &ret, sizeof(T)) != sizeof(T)) {
            error(strerror(errno));
        }
        return ret;
    }

    template <typename T>
    void write_type_at(T content, unsigned long at) {
        if (lseek(ELF_FILE, at, SEEK_SET) == -1) {
            error(strerror(errno));
        };

        if (write(ELF_FILE, &content, sizeof(T)) != sizeof(T)) {
            error(strerror(errno));
        }
    }

    #define RELOCATE(Offset, Typ, Field)        \
    {                                           \
        Typ obj = read_type_at<Typ>(Offset);    \
        obj.Field += relocation_amount;         \
        write_type_at<Typ>(obj, Offset);        \
    }

    #define RELOCATE_IF(Offset, Typ, Field, Condition) \
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

    bool should_relocate_addend(Elf64_Rela& rela) {
        switch (ELF64_R_TYPE(rela.r_info)) {
    #if defined(AARCH64)
            case R_AARCH64_RELATIVE: // just a placeholder guess...
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
        assert(EHDR.e_type == ET_DYN);
        assert(EHDR.e_version == EV_CURRENT);

    // elf.h in devkit on Linux x86_64 does not define EM_AARCH64
    #if defined(AARCH64)
        assert(EHDR.e_machine == EM_AARCH64);
    #else
        assert(EHDR.e_machine == EM_X86_64);
    #endif
        if (EHDR.e_phnum == PN_XNUM) {
            error("Too many program headers, handling not implemented.");
        }
        if (EHDR.e_shnum == 0) {
            error("Invalid number of section headers, zero.");
        }
        assert(EHDR.e_phentsize == sizeof(Elf64_Phdr));
    }

    void relocate_execution_header() {
        EHDR.e_entry += relocation_amount;
        write_type_at<Elf64_Ehdr>(EHDR, 0);
    }

    bool should_relocate_program_header(Elf64_Phdr& phdr) {
        return phdr.p_type != PT_GNU_STACK;
    }

    void relocate_program_headers() {
        for (int i = 0; i < EHDR.e_phnum; i++) {
            RELOCATE_IF(EHDR.e_phoff + i * EHDR.e_phentsize, Elf64_Phdr, p_vaddr, 
                should_relocate_program_header);
            RELOCATE_IF(EHDR.e_phoff + i * EHDR.e_phentsize, Elf64_Phdr, p_paddr,
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
        if (lseek(ELF_FILE, at, SEEK_SET) == -1) {
            error(strerror(errno));
        };
        if (read(ELF_FILE, buffer, bytes) != bytes) {
            error(strerror(errno));
        }
    }

    void relocate(unsigned long reloc_amount) {
        assert(SHDRSTR_BUFFER != 0);
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

    void write_jvm_symbols(int fd) {
        assert(SHDRSTR_BUFFER != 0);
        Elf64_Shdr sh = section_by_name(".strtab");
        char* SYMTAB_BUFFER = new char[sh.sh_size];
        read_bytes_at(sh.sh_offset, sh.sh_size, SYMTAB_BUFFER);

        sh = section_by_name(".symtab");
        for (long unsigned int i = 0; i < sh.sh_size/sh.sh_entsize; i++) {
            Elf64_Sym sym = read_type_at<Elf64_Sym>(sh.sh_offset+ i*sh.sh_entsize);

            // Symbols. Possibly move to common header.
            const int N_SYMS = 7;
            const char* symbols[N_SYMS] = {
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
};

int relocate_sharedlib_pd(const char* filename, const void *addr) {
    if (verbose) log("relocate_sharedlib_pd");

    int ELF_FILE = open(filename, O_RDWR);
    if (ELF_FILE == -1) {
        error(strerror(errno));
    }
    ELFOperations l(ELF_FILE);
    l.read_basics();
    l.relocate((unsigned long) addr /* assume library currently has zero base address */);
    if (close(ELF_FILE) == -1) {
        error(strerror(errno));
    }

    if (verbose) log("relocate_sharedlib_pd_done");
    return 0;
}

int generate_symbols_pd(const char* filename, int symbols_fd) {
    if (verbose) log("generate_symbols_pd");

    int ELF_FILE = open(filename, O_RDONLY);
    if (ELF_FILE == -1) {
        error(strerror(errno));
    }
    ELFOperations l(ELF_FILE);
    l.read_basics();
    l.write_jvm_symbols(symbols_fd);
    if (close(ELF_FILE) == -1) {
        error(strerror(errno));
    }

    if (verbose) log("generate_symbols_pd done");
    return 0;
}
