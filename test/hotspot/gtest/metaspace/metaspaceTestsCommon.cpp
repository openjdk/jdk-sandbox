/*
 * Copyright (c) 2019, SAP SE. All rights reserved.
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


#include "precompiled.hpp"

#include "metaspaceTestsCommon.hpp"

#include "utilities/globalDefinitions.hpp"

#ifdef _WIN32
#include <psapi.h>
#endif

void calc_random_range(size_t outer_range_len, range_t* out, size_t alignment) {

  assert(is_aligned(outer_range_len, alignment), "bad input range");
  assert(outer_range_len > 0, "no zero range");

  size_t l1 = os::random() % outer_range_len;
  size_t l2 = os::random() % outer_range_len;
  if (l1 > l2) {
    size_t l = l1;
    l1 = l2;
    l2 = l;
  }
  l1 = align_down(l1, alignment);
  l2 = align_up(l2, alignment);

  // disallow zero range
  if (l2 == l1) {
    if (l1 >= alignment) {
      l1 -= alignment;
    } else {
      assert(l2 <= outer_range_len - alignment, "Sanity");
      l2 += alignment;
    }
  }

  assert(l2 - l1 > 0 && l2 - l1 <= outer_range_len, "Sanity " SIZE_FORMAT "-" SIZE_FORMAT ".", l1, l2);
  assert(is_aligned(l1, alignment), "Sanity");
  assert(is_aligned(l2, alignment), "Sanity");

  out->from = l1; out->to = l2;

}

void calc_random_address_range(const address_range_t* outer_range, address_range_t* out, size_t alignment) {
  range_t r;
  calc_random_range(outer_range->word_size, &r, alignment);

  out->p = outer_range->p + r.from;
  out->word_size = r.from - r.to;
}

size_t get_workingset_size() {
#if defined(_WIN32)
  PROCESS_MEMORY_COUNTERS info;
  GetProcessMemoryInfo(GetCurrentProcess(), &info, sizeof(info));
  return (size_t)info.WorkingSetSize;
#elif defined(LINUX)
  long result = 0L;
  FILE* f = fopen("/proc/self/statm", "r");
  if (f == NULL) {
    return 0;
  }
  // Second number in statm, in num of pages
  if (fscanf(f, "%*s%ld", &result) != 1 ) {
    fclose(f);
    return 0;
  }
  fclose(f);
  return (size_t)result * (size_t)os::vm_page_size();
#else
  return 0L;
#endif
}



void zap_range(MetaWord* p, size_t word_size) {
  for (MetaWord* pzap = p; pzap < p + word_size; pzap += os::vm_page_size()) {
    *pzap = 0;
  }
}



// Writes a uniqe pattern to p
void mark_address(MetaWord* p, uintx pattern) {
  MetaWord x = (MetaWord)((uintx) p ^ pattern);
  *p = x;
}

// checks pattern at address
bool check_marked_address(const MetaWord* p, uintx pattern) {
  MetaWord x = (MetaWord)((uintx) p ^ pattern);
  EXPECT_EQ(*p, x);
  return *p == x;
}

// "fill_range_with_pattern" fills a range of heap words with pointers to itself.
//
// The idea is to fill a memory range with a pattern which is both marked clearly to the caller
// and cannot be moved without becoming invalid.
//
// The filled range can be checked with check_range_for_pattern. One also can only check
// a sub range of the original range.
void fill_range_with_pattern(MetaWord* p, uintx pattern, size_t word_size) {
  assert(word_size > 0 && p != NULL, "sanity");
  for (MetaWord* p2 = p; p2 < p + word_size; p2 ++) {
    mark_address(p2, pattern);
  }
}

bool check_range_for_pattern(const MetaWord* p, uintx pattern, size_t word_size) {
  assert(word_size > 0 && p != NULL, "sanity");
  const MetaWord* p2 = p;
  while (p2 < p + word_size && check_marked_address(p2, pattern)) {
    p2 ++;
  }
  return p2 < p + word_size;
}


// Similar to fill_range_with_pattern, but only marks start and end. This is optimized for cases
// where fill_range_with_pattern just is too slow.
// Use check_marked_range to check the range. In contrast to check_range_for_pattern, only the original
// range can be checked.
void mark_range(MetaWord* p, uintx pattern, size_t word_size) {
  assert(word_size > 0 && p != NULL, "sanity");
  mark_address(p, pattern);
  mark_address(p + word_size - 1, pattern);
}

bool check_marked_range(const MetaWord* p, uintx pattern, size_t word_size) {
  assert(word_size > 0 && p != NULL, "sanity");
  return check_marked_address(p, pattern) && check_marked_address(p + word_size - 1, pattern);
}

