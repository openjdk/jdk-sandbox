/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_CONSTANTS_HPP
#define SHARE_MEMORY_METASPACE_CONSTANTS_HPP

#include "utilities/globalDefinitions.hpp"
#include "memory/metaspace/chunkLevel.hpp"

namespace metaspace {

// Constants to be used throughout metaspace


// The size for a VirtualSpaceNode (unless created differently).
static size_t VSNODE_DEFAULT_BYTE_SIZE = MAX_CHUNK_BYTE_SIZE * 4;


// When expanding the committed region of a metachunk (see Metachunk::allocate()), the preferred commit granularity, in bytes
static size_t metachunk_commit_granularity = 16 * K;


} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_BLOCKFREELIST_HPP
