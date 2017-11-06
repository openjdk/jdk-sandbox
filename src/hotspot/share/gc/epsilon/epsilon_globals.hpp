/*
 * Copyright (c) 2017, Red Hat, Inc. and/or its affiliates.
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
 *
 */

#ifndef SHARE_VM_GC_EPSILON_GLOBALS_HPP
#define SHARE_VM_GC_EPSILON_GLOBALS_HPP

#include "runtime/globals.hpp"
//
// Defines all globals flags used by the Epsilon GC.
//

#define EPSILON_FLAGS(develop, \
                 develop_pd, \
                 product, \
                 product_pd, \
                 diagnostic, \
                 diagnostic_pd, \
                 experimental, \
                 notproduct, \
                 manageable, \
                 product_rw, \
                 range, \
                 constraint, \
                 writeable) \
                                                                            \
  experimental(size_t, EpsilonMaxTLABSize, 4 * M,                           \
          "Max TLAB size to use with Epsilon GC. Larger value improves "    \
          "performance at the expense of per-thread memory waste. This "    \
          "asks TLAB machinery to cap TLAB sizes at this value")            \
          range(1, max_intx)                                                \
                                                                            \
  experimental(size_t, EpsilonMinHeapExpand, 128 * M,                       \
          "Min expansion step for heap. Larger value improves performance " \
          "at the potential expense of memory waste.")                      \
          range(1, max_intx)

EPSILON_FLAGS(DECLARE_DEVELOPER_FLAG, \
         DECLARE_PD_DEVELOPER_FLAG, \
         DECLARE_PRODUCT_FLAG, \
         DECLARE_PD_PRODUCT_FLAG, \
         DECLARE_DIAGNOSTIC_FLAG, \
         DECLARE_PD_DIAGNOSTIC_FLAG, \
         DECLARE_EXPERIMENTAL_FLAG, \
         DECLARE_NOTPRODUCT_FLAG, \
         DECLARE_MANAGEABLE_FLAG, \
         DECLARE_PRODUCT_RW_FLAG, \
         IGNORE_RANGE, \
         IGNORE_CONSTRAINT, \
         IGNORE_WRITEABLE)

#endif // SHARE_VM_GC_EPSILON_GLOBALS_HPP
