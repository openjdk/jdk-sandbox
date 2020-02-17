#
# Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

# Handling of JVM variants and JVM features reside in separate files.
m4_include([hotspot-variants.m4])
m4_include([hotspot-features.m4])

################################################################################
# Check if gtest should be built
#
AC_DEFUN_ONCE([HOTSPOT_ENABLE_DISABLE_GTEST],
[
  AC_ARG_ENABLE([hotspot-gtest], [AS_HELP_STRING([--disable-hotspot-gtest],
      [Disables building of the Hotspot unit tests @<:@enabled@:>@])])

  if test -e "${TOPDIR}/test/hotspot/gtest"; then
    GTEST_DIR_EXISTS="true"
  else
    GTEST_DIR_EXISTS="false"
  fi

  AC_MSG_CHECKING([if Hotspot gtest unit tests should be built])
  if test "x$enable_hotspot_gtest" = "xyes"; then
    if test "x$GTEST_DIR_EXISTS" = "xtrue"; then
      AC_MSG_RESULT([yes, forced])
      BUILD_GTEST="true"
    else
      AC_MSG_ERROR([Cannot build gtest without the test source])
    fi
  elif test "x$enable_hotspot_gtest" = "xno"; then
    AC_MSG_RESULT([no, forced])
    BUILD_GTEST="false"
  elif test "x$enable_hotspot_gtest" = "x"; then
    if test "x$GTEST_DIR_EXISTS" = "xtrue"; then
      AC_MSG_RESULT([yes])
      BUILD_GTEST="true"
    else
      AC_MSG_RESULT([no])
      BUILD_GTEST="false"
    fi
  else
    AC_MSG_ERROR([--enable-gtest must be either yes or no])
  fi

  AC_SUBST(BUILD_GTEST)
])

###############################################################################
# Misc hotspot setup that does not fit elsewhere.
#
AC_DEFUN_ONCE([HOTSPOT_SETUP_MISC],
[
  if HOTSPOT_CHECK_JVM_VARIANT(zero); then
    # zero behaves as a platform and rewrites these values. This is a bit weird.
    # We are guaranteed that we do not build any other variants when building zero.
    HOTSPOT_TARGET_CPU=zero
    HOTSPOT_TARGET_CPU_ARCH=zero
  fi

  # Override hotspot cpu definitions for ARM platforms
  if test "x$OPENJDK_TARGET_CPU" = xarm; then
    HOTSPOT_TARGET_CPU=arm_32
    HOTSPOT_TARGET_CPU_DEFINE="ARM32"
  fi

  # --with-cpu-port is no longer supported
  BASIC_DEPRECATED_ARG_WITH(with-cpu-port)
])
