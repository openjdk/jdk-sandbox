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


ELFFile::ELFFile(const char *filename) {
    this->filename = filename;
    fd = open(filename, O_RDWR);
    if (fd < 0) {
        error("cannot open '%s': %s", filename, strerror(errno));
    }
    length = file_size(filename);
    m = mmap(0, length, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (m == (void *) -1) {
        error("mmap of ELF file failed: %s", strerror(errno));
    }
    hdr = (Elf64_Ehdr*) m;

    // Set resolved ph and sh pointers for ease.
    // Careful with ptr arithmetic, do NOT use:
    // = (Elf64_Phdr*) (char *) m + (hdr->e_phoff);
    // But use:
    ph = (Elf64_Phdr*) ((char *) m + (hdr->e_phoff));

    if (hdr->e_shoff > 0) {
        sh = (Elf64_Shdr*) ((char *) m + (hdr->e_shoff));
        Elf64_Shdr* strndx_shdr = section_by_index(hdr->e_shstrndx);
        shdr_strings = (char *) m + strndx_shdr->sh_offset;
    } else {
        // cores don't usually have Sections
        sh = nullptr;
    }

    logv("ELFFile: %s hdr = %p phoff = 0x%lx shoff = 0x%lx   ph = %p sh = %p",
    filename, hdr, hdr->e_phoff, hdr->e_shoff, ph, sh);
   verify();
}

void ELFFile::verify() {
        assert(hdr->e_ident[0] == 0x7f);
        assert(hdr->e_ident[1] == 'E');
        assert(hdr->e_ident[2] == 'L');
        assert(hdr->e_ident[3] == 'F');
        assert(hdr->e_ident[4] == ELFCLASS64);
        assert(hdr->e_ident[5] == ELFDATA2LSB);
        assert(hdr->e_ident[6] == EV_CURRENT);
        assert(hdr->e_ident[7] == ELFOSABI_SYSV);
        assert(hdr->e_version == EV_CURRENT);

        // elf.h in devkit on Linux x86_64 does not define EM_AARCH64
#if defined(__aarch64__)
        assert(hdr->e_machine == EM_AARCH64);
#else
        assert(hdr->e_machine == EM_X86_64);
#endif
        if (hdr->e_phnum == PN_XNUM) {
            error("Too many program headers, handling not implemented (%x)", hdr->e_phnum);
        }
        if (hdr->e_type == ET_DYN && hdr->e_shnum == 0) {
            error("Invalid number of section headers in shared library, zero.");
        }
        assert(hdr->e_phentsize == sizeof(Elf64_Phdr));
        assert(hdr->e_shentsize == 0 || hdr->e_shentsize == sizeof(Elf64_Shdr));

        // Sanity check the pointer arithmetic:
        bool debug = false;
        if (debug) {
        Elf64_Phdr* p0 = program_header(0);
        Elf64_Phdr* p1 = program_header(1);
        long diff = (long) ((uint64_t) p1 - (uint64_t) p0);
        Elf64_Phdr* p1a = next_ph(p0);
        warn("ph %p %p %p diff: %ld", p0, p1, p1a, diff);
        assert(p1 == p1a);
        assert(diff == hdr->e_phentsize);

        Elf64_Shdr* s0 = section_header(0);
        Elf64_Shdr* s1 = section_header(1);
        diff = (long) ((uint64_t) s1 - (uint64_t) s0);
        Elf64_Shdr* s1a = next_sh(s0);
        warn("sh %p %p %p diff: %ld", s0, s1, s1a, diff);
        assert(s1 == s1a);
        assert(diff == hdr->e_shentsize);
        }
}

ELFFile::~ELFFile() {
    // Free NT_mappings
    if (fd >= 0) {
        ::close(fd);
    }
    if (m != 0) {
        do_munmap_pd(m, length);
    }
}

void ELFFile::print() {
    for (int i = 0; i < hdr->e_phnum; i++) {
        Elf64_Phdr* p = program_header(i);
        fprintf(stderr, "PH %3d %p  Type: %d offset: 0x%lx vaddr: 0x%lx\n", i, p, p->p_type, p->p_offset, p->p_vaddr);
    }
    for (int i = 0; i < hdr->e_shnum; i++) {
        Elf64_Shdr* p = section_header(i);
        fprintf(stderr, "SH %3d %p  Type: %d addr: 0x%lx\n", i, p, p->sh_type, p->sh_addr);
    }
}

char* ELFFile::read_string_at_address(uint64_t addr) {
    // Locate PT_LOAD for this address.
    Elf64_Phdr* phdr = ph;
    for (int i = 0; i < hdr->e_phnum; i++) {
        if (phdr->p_type == PT_LOAD) {
            if (phdr->p_vaddr >= addr) {
                uint64_t offset = phdr->p_offset + (addr - phdr->p_vaddr);
                return readstring_at_pd(filename, offset);
            }
        }
        phdr = next_ph(phdr);
    }
    return nullptr;
}

void ELFFile::relocate(long displacement) {
    assert(hdr->e_type == ET_DYN);
    assert(sh != 0);
    assert(shdr_strings != 0);

    relocate_execution_header(displacement);
    relocate_program_headers(displacement);
    relocate_section_headers(displacement);
    relocate_relocation_table(displacement, ".rela.dyn");
    relocate_relocation_table(displacement, ".rela.plt");
    relocate_dynamic_table(displacement);
    relocate_symbol_table(displacement, ".dynsym");
    relocate_symbol_table(displacement, ".symtab");
}

void ELFFile::write_symbols(int fd, const char* symbols[], int count) {
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


SharedLibMapping* ELFFile::get_library_mapping(const char* filename) {
    read_NT_mappings();
    for (int i = 0; i < NT_Mappings_count; i++) {
        if (strstr(NT_Mappings[i].path, filename)) {
            return &NT_Mappings[i];
        }
    }
    return nullptr;
}


void ELFFile::write_mappings(int mappings_fd, const char* exec_name) {
   /* For each program header:
    *   Skip if:
    *     filesize or memsize is zero
    *     it touches an unwanted mapping
    *     not writeable and inOtherMapping*
    *   Create a Segment, call Segment::write_mapping(int fd) to write an "M" entry.
    */
        logv("write_mappings");
        if (hdr->e_type != ET_CORE) {
            warn("write_mappings: Not writing mappings for non-core file.");
            return;
        }

        read_NT_mappings();
        int n_skipped = 0;
        Elf64_Phdr* phdr = ph;
        for (int i = 0; i < hdr->e_phnum; i++) {
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
                if (is_inside(phdr, NT_Mappings[i].start, NT_Mappings[i].end)
                    && strstr(NT_Mappings[i].path, exec_name) /*|| false //!(phdr.p_flags & PF_W)) */
                   ) {
                    skip = true;
                    break;
                }
            }
            if (skip) {
                logv("Skipping due to bin/java at %lu", phdr->p_vaddr);
                n_skipped++;
                phdr = next_ph(phdr);
                continue;
            }

            if (!(phdr->p_flags & PF_W)) {
                skip = false;
                for (int i = 0; i < NT_Mappings_count; i++) {
                    if (is_inside(phdr, NT_Mappings[i].start, NT_Mappings[i].end)) {
                        skip = true;
                        break;
                    }
                }
                if (skip) {
                    logv("Skipping due to nonwritable overlap at %lu", phdr->p_vaddr);
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

