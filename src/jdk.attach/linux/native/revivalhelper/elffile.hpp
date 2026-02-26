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
 * Provide operations such as file inspection to read memory segments, and also
 * a destructive operation to relocate the file to a new base virtual address.
 *
 * To mmap the file and update in-memory proves faster than lseek and read/write,
 * particularly for relocation.
 */
class ELFFile {
  public:
    ELFFile(const char* filename, const char* libdir);
    ~ELFFile();

    bool is_valid();
    bool is_core();

    // Static check if named file is ELF
    static bool is_elf(const char* filename);

    uint64_t file_offset_for_vaddr(uint64_t addr);
    char* readstring_at_address(uint64_t addr);

    // Return shared library information.
    Segment* get_library_mapping(const char* filename);
    std::list<Segment> get_library_mappings();

    // Relocate actual file by some amount.
    void relocate(long displacement);

    // Write the list of memory mappings in the core.
    void write_mem_mappings(int mappings_fd, const char* exec_name);

    // Write symbol list for revived process.
    void write_symbols(int symbols_fd, const char* symbols[], int count);

    void print(); // Diagnostic, show PHs and Sections

  private:
    const char* filename;
    const char* libdir;
    int fd;
    long long length;
    void* m;            // Address of mapped ELF file
    Elf64_Ehdr* hdr;    // Main ELF Header
    Elf64_Phdr* ph;     // First Program Header absolute address
    Elf64_Shdr* sh;     // First Section Header absolute address or nullptr
    char* shdr_strings;

    std::list<Segment> libs;

    bool verify();

    char* find_note_data(Elf64_Phdr* notes_ph, Elf64_Word type);
    void read_sharedlibs();

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
    Elf64_Shdr* section_header(unsigned long i);

    // Program header actual address in mmapped file.
    Elf64_Phdr* program_header(unsigned long i);

    bool section_name_is(Elf64_Shdr* shdr, const char* name);

    Elf64_Shdr* next_sh(Elf64_Shdr* s);

    Elf64_Phdr* next_ph(Elf64_Phdr* p);

    Elf64_Shdr* section_by_name(const char* name);

    Elf64_Shdr* section_by_index(unsigned long index);

    // Returns the first phdr where predicate returns true.
    Elf64_Phdr* program_header_by_predicate(bool (*predptr)(Elf64_Phdr*));

    Elf64_Phdr* program_header_by_type(Elf32_Word type);

    // Relocation methods
    void relocate_execution_header(long displacement);

    void relocate_program_headers(long displacement);

    bool should_relocate_section_header(Elf64_Shdr* shdr);

    void relocate_section_headers(long displacement);

    void relocate_relocation_table(long displacement, const char* name);

    uint64_t find_dynamic_value(Elf64_Shdr* s, int tag);

    // Relocate e.g. INIT_ARRAY contents.
    void relocate_dyn_array(long displacement, Elf64_Dyn* dyn, int count);

    void relocate_dynamic_table(long displacement);

    bool should_relocate_symbol(Elf64_Sym* sym);

    void relocate_symbol_table(long displacement, const char* name);

    void read_bytes_at(unsigned long at, ssize_t bytes, char* buffer);

    void read_bytes(ssize_t bytes, char* buffer);
};
