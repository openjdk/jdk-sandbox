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

#include <elf.h>
#include <errno.h>

#include "revival.hpp"


/**
 * An ELF file.
 *
 * Provide operations such as file inspection, but also a destructive operation 
 * to relocate the file to a new base virtual address.
 *
 * To mmap the file and update in-memory proves faster than lseek and read/write.
 * But some core files may be unreasonable to mmap fully.
 *
 * Currently we only mmmap, may need to change that above some file size, or ensure
 * what we need is mapped in.
 */
class ELFFile {
  public:
    ELFFile(const char* filename, const char* libdir);
    ~ELFFile();

    uint64_t file_offset_for_vaddr(uint64_t addr);
    char* readstring_at_address(uint64_t addr);

    void print(); // Show PHs and Sections

    // Relocate actual file by some amount.
    void relocate(long displacement);

    // Write symbol list for revived process.
    void write_symbols(int fd, const char* symbols[], int count);

    // Return mapping information.  Data is valid while this ELFFile is alive,
    // information should be copied to retain.
    SharedLibMapping* get_library_mapping(const char* filename);

    // Write the list of memory mappings in the core, to be used in the revived process.
    void write_mappings(int mappings_fd, const char* exec_name);

  private:
    const char* filename;
    const char* libdir;
    Elf64_Ehdr* hdr;  // Main ELF Header
    Elf64_Phdr* ph;   // First Program Header absolute address
    Elf64_Shdr* sh;   // First Section Header absolute address or nullptr
    char *shdr_strings;

    int fd;
    long long length;
    void *m; // Address of mapped ELF file

    SharedLibMapping* mappings;
    int mappings_count;

    void verify();

    char* find_note_data(Elf64_Phdr* notes_ph, Elf64_Word type);
    void read_file_mappings();

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


    // Section header actual address in mmapped file.
    Elf64_Shdr* section_header(unsigned long i) {
        return (Elf64_Shdr*) ((char*) sh + (i * hdr->e_shentsize));
    }

    // Program header actual address in mmapped file.
    Elf64_Phdr* program_header(unsigned long i) {
        return (Elf64_Phdr*) ((char*) ph + (i * hdr->e_phentsize));
    }

    bool section_name_is(Elf64_Shdr* shdr, const char* name) {
        return (strcmp(name, shdr_strings + shdr->sh_name) == 0);
    }

    Elf64_Shdr* next_sh(Elf64_Shdr* s) {
        return (Elf64_Shdr*) ((char *) s + hdr->e_shentsize);
    }

    Elf64_Phdr* next_ph(Elf64_Phdr* p) {
        return (Elf64_Phdr*) ((char *) p + hdr->e_phentsize);
    }

    Elf64_Shdr* section_by_name(const char* name) {
        Elf64_Shdr* s = sh;
        for (int i = 0; i < hdr->e_shnum; i++) {
            if (section_name_is(s, name)) {
                return s;
            }
            s = next_sh(s);
        }
        error("Section not found: %s", name);
        return nullptr; // Will not reach here
    }

    Elf64_Shdr* section_by_index(unsigned long index) {
        return (Elf64_Shdr*) ((char*) sh + (index * hdr->e_shentsize));
    }

    // Returns the first phdr where predicate returns true.
    Elf64_Phdr* program_header_by_predicate(bool (*predptr)(Elf64_Phdr*)) {
        Elf64_Phdr* phdr = ph;
        for (int i = 0; i < hdr->e_phnum; i++) {
            if (predptr(phdr)) {
                return phdr;
            }
            phdr = next_ph(phdr);
        }
        return nullptr;
    }

    Elf64_Phdr* program_header_by_type(Elf32_Word type) {
        Elf64_Phdr* phdr = ph;
        for (int i = 0; i < hdr->e_phnum; i++) {
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
        if (hdr->e_entry != 0) {
            hdr->e_entry += displacement;
        }
    }

    bool should_relocate_program_header(Elf64_Phdr* phdr) {
        return phdr->p_type != PT_GNU_STACK;
    }

    void relocate_program_headers(long displacement) {
        Elf64_Phdr* p = ph;
        for (int i = 0; i < hdr->e_phnum; i++) {
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
        for (int i = 0; i < hdr->e_shnum; i++) {
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
};

