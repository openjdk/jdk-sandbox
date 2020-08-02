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

//#define LOG_PLEASE

#include "metaspaceTestsCommon.hpp"
#include "memory/metaspace/metaspaceReport.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

TEST_VM(metaspace, report_basic) {

  stringStream ss;
  //outputStream* st = tty;
  outputStream* st = &ss;

  metaspace::MetaspaceReporter::print_basic_report(st, 0);

  ASSERT_GT(ss.size(), (size_t)0);

}

// Note: full report needs CLDG lock or a safepoint. We test this as part of the
// metaspace jtreg jcmd tests, lets not test it here.
//TEST_VM(metaspace, report_full) {
//
//  outputStream* st = tty;
//
//  metaspace::MetaspaceReporter::print_report(st, 0, 0);
//
//}

