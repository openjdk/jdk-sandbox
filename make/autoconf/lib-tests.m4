#
# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

###############################################################################
#
# Check for graalunit libs, needed for running graalunit tests.
#
AC_DEFUN_ONCE([LIB_TESTS_SETUP_GRAALUNIT],
[
  AC_ARG_WITH(graalunit-lib, [AS_HELP_STRING([--with-graalunit-lib],
      [specify location of 3rd party libraries used by Graal unit tests])])

  GRAALUNIT_LIB=
  if test "x${with_graalunit_lib}" != x; then
    AC_MSG_CHECKING([for graalunit libs])
    if test "x${with_graalunit_lib}" = xno; then
      AC_MSG_RESULT([disabled, graalunit tests can not be run])
    elif test "x${with_graalunit_lib}" = xyes; then
      AC_MSG_RESULT([not specified])
      AC_MSG_ERROR([You must specify the path to 3rd party libraries used by Graal unit tests])
    else
      GRAALUNIT_LIB="${with_graalunit_lib}"
      if test ! -d "${GRAALUNIT_LIB}"; then
        AC_MSG_RESULT([no])
        AC_MSG_ERROR([Could not find graalunit 3rd party libraries as specified. (${with_graalunit_lib})])
      else
        AC_MSG_RESULT([$GRAALUNIT_LIB])
      fi
    fi
  fi

  BASIC_FIXUP_PATH([GRAALUNIT_LIB])
  AC_SUBST(GRAALUNIT_LIB)
])

###############################################################################
#
# Setup and check the Java Microbenchmark Harness
#
AC_DEFUN_ONCE([LIB_TESTS_SETUP_JMH],
[
  AC_ARG_WITH(jmh, [AS_HELP_STRING([--with-jmh],
      [Java Microbenchmark Harness for building the OpenJDK Microbenchmark Suite])])

  # JMH configuration parameters
  JMH_VERSION=1.21

  # JAR files below must be listed on a single line as a comma separated 
  # list without any spaces. The version number unfortunately needs to be 
  # written out as this file is read by both configure and make which use 
  # different variable expansion syntax.

  # JARs required for compiling microbenchmarks
  JMH_COMPILE_JAR_NAMES="jmh-generator-annprocess-1.21.jar jmh-core-1.21.jar"
  JMH_COMPILE_JARS=""

  # JARs required for running microbenchmarks
  JMH_RUNTIME_JAR_NAMES="commons-math3-3.2.jar jopt-simple-4.6.jar jmh-core-1.21.jar"
  JMH_RUNTIME_JARS=""

  if test "x$with_jmh" = xno || test "x$with_jmh" = x; then
    AC_MSG_CHECKING([for jmh])
    AC_MSG_RESULT(no)
  elif test "x$with_jmh" = xyes; then
    AC_MSG_ERROR([Must specify a directory containing JMH and required JAR files or a subdirectory named with JMH version])
  else
    # Path specified
    AC_MSG_CHECKING([for jmh])


    JMH_HOME="$with_jmh"

    BASIC_FIXUP_PATH([JMH_HOME])

    # Check that JMH directory exist
    if test ! -d [$JMH_HOME]; then
      AC_MSG_ERROR([$JMH_HOME does not exist or is not a directory])
    fi

    # Check and use version specific JMH directory
    if test -d [$JMH_HOME/$JMH_VERSION]; then
      JMH_HOME="$JMH_HOME/$JMH_VERSION"
    fi

    # Check that required files exist in the JMH directory
    for jar in $JMH_COMPILE_JAR_NAMES; do
      if test ! -f [$JMH_HOME/$jar]; then
        AC_MSG_ERROR([$JMH_HOME does not contain $jar])
      fi
      JMH_COMPILE_JARS="$JMH_COMPILE_JARS $JMH_HOME/$jar"
    done
    
    for jar in $JMH_RUNTIME_JAR_NAMES; do
      if test ! -f [$JMH_HOME/$jar]; then
        AC_MSG_ERROR([$JMH_HOME does not contain $jar])
      fi
      JMH_RUNTIME_JARS="$JMH_RUNTIME_JARS $JMH_HOME/$jar"
    done


    AC_MSG_RESULT([yes, Version: $JMH_VERSION, Location: $JMH_HOME ($JMH_COMPILE_JARS / $JMH_RUNTIME_JARS)])
  fi

  AC_SUBST(JMH_HOME)
  AC_SUBST(JMH_VERSION)
  AC_SUBST(JMH_COMPILE_JARS)
  AC_SUBST(JMH_RUNTIME_JARS)
])

