#
# Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

################################################################################
#
# Setup flags for C/C++ compiler
#

###############################################################################
#
# How to compile shared libraries.
#
AC_DEFUN([FLAGS_SETUP_SHARED_LIBS],
[
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    C_FLAG_REORDER=''

    # Default works for linux, might work on other platforms as well.
    SHARED_LIBRARY_FLAGS='-shared'
    SET_EXECUTABLE_ORIGIN='-Wl,-rpath,\$$ORIGIN[$]1'
    SET_SHARED_LIBRARY_ORIGIN="-Wl,-z,origin $SET_EXECUTABLE_ORIGIN"
    SET_SHARED_LIBRARY_NAME='-Wl,-soname=[$]1'
    SET_SHARED_LIBRARY_MAPFILE='-Wl,-version-script=[$]1'

  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    C_FLAG_REORDER=''

    if test "x$OPENJDK_TARGET_OS" = xmacosx; then
      # Linking is different on MacOSX
      SHARED_LIBRARY_FLAGS="-dynamiclib -compatibility_version 1.0.0 -current_version 1.0.0"
      SET_EXECUTABLE_ORIGIN='-Wl,-rpath,@loader_path$(or [$]1,/.)'
      SET_SHARED_LIBRARY_ORIGIN="$SET_EXECUTABLE_ORIGIN"
      SET_SHARED_LIBRARY_NAME='-Wl,-install_name,@rpath/[$]1'
      SET_SHARED_LIBRARY_MAPFILE='-Wl,-exported_symbols_list,[$]1'

    else
      # Default works for linux, might work on other platforms as well.
      SHARED_LIBRARY_FLAGS='-shared'
      SET_EXECUTABLE_ORIGIN='-Wl,-rpath,\$$ORIGIN[$]1'
      SET_SHARED_LIBRARY_NAME='-Wl,-soname=[$]1'
      SET_SHARED_LIBRARY_MAPFILE='-Wl,-version-script=[$]1'

      # arm specific settings
      if test "x$OPENJDK_TARGET_CPU" = "xarm"; then
        # '-Wl,-z,origin' isn't used on arm.
        SET_SHARED_LIBRARY_ORIGIN='-Wl,-rpath,\$$$$ORIGIN[$]1'
      else
        SET_SHARED_LIBRARY_ORIGIN="-Wl,-z,origin $SET_EXECUTABLE_ORIGIN"
      fi
    fi

  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    C_FLAG_REORDER='-xF'
    SHARED_LIBRARY_FLAGS="-G"
    SET_EXECUTABLE_ORIGIN='-R\$$ORIGIN[$]1'
    SET_SHARED_LIBRARY_ORIGIN="$SET_EXECUTABLE_ORIGIN"
    SET_SHARED_LIBRARY_NAME='-h [$]1'
    SET_SHARED_LIBRARY_MAPFILE='-M[$]1'

  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    C_FLAG_REORDER=''
    SHARED_LIBRARY_FLAGS="-qmkshrobj -bM:SRE -bnoentry"
    SET_EXECUTABLE_ORIGIN=""
    SET_SHARED_LIBRARY_ORIGIN=''
    SET_SHARED_LIBRARY_NAME=''
    SET_SHARED_LIBRARY_MAPFILE=''

  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    C_FLAG_REORDER=''
    SHARED_LIBRARY_FLAGS="-dll"
    SET_EXECUTABLE_ORIGIN=''
    SET_SHARED_LIBRARY_ORIGIN=''
    SET_SHARED_LIBRARY_NAME=''
    SET_SHARED_LIBRARY_MAPFILE='-def:[$]1'
  fi

  AC_SUBST(C_FLAG_REORDER)
  AC_SUBST(SET_EXECUTABLE_ORIGIN)
  AC_SUBST(SET_SHARED_LIBRARY_ORIGIN)
  AC_SUBST(SET_SHARED_LIBRARY_NAME)
  AC_SUBST(SET_SHARED_LIBRARY_MAPFILE)
  AC_SUBST(SHARED_LIBRARY_FLAGS)
])

AC_DEFUN([FLAGS_SETUP_DEBUG_SYMBOLS],
[
  # Debug symbols
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    if test "x$OPENJDK_TARGET_CPU_BITS" = "x64" && test "x$DEBUG_LEVEL" = "xfastdebug"; then
      CFLAGS_DEBUG_SYMBOLS="-g1"
    else
      CFLAGS_DEBUG_SYMBOLS="-g"
    fi
  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    CFLAGS_DEBUG_SYMBOLS="-g"
  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    CFLAGS_DEBUG_SYMBOLS="-g -xs"
    # -g0 enables debug symbols without disabling inlining.
    CXXFLAGS_DEBUG_SYMBOLS="-g0 -xs"
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    CFLAGS_DEBUG_SYMBOLS="-g"
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    CFLAGS_DEBUG_SYMBOLS="-Zi"
  fi

  if test "x$CXXFLAGS_DEBUG_SYMBOLS" = x; then
    # If we did not specify special flags for C++, use C version
    CXXFLAGS_DEBUG_SYMBOLS="$CFLAGS_DEBUG_SYMBOLS"
  fi
  AC_SUBST(CFLAGS_DEBUG_SYMBOLS)
  AC_SUBST(CXXFLAGS_DEBUG_SYMBOLS)

  # FIXME: This was never used in the old build. What to do with it?
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    # "-Og" suppported for GCC 4.8 and later
    CFLAG_OPTIMIZE_DEBUG_FLAG="-Og"
    FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [$CFLAG_OPTIMIZE_DEBUG_FLAG],
      IF_TRUE: [HAS_CFLAG_OPTIMIZE_DEBUG=true],
      IF_FALSE: [HAS_CFLAG_OPTIMIZE_DEBUG=false])
  fi

  # Debug symbols for JVM_CFLAGS
  if test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    JVM_CFLAGS_SYMBOLS="$JVM_CFLAGS_SYMBOLS -xs"
    if test "x$DEBUG_LEVEL" = xslowdebug; then
      JVM_CFLAGS_SYMBOLS="$JVM_CFLAGS_SYMBOLS -g"
    else
      # -g0 does not disable inlining, which -g does.
      JVM_CFLAGS_SYMBOLS="$JVM_CFLAGS_SYMBOLS -g0"
    fi
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    JVM_CFLAGS_SYMBOLS="$JVM_CFLAGS_SYMBOLS -Z7 -d2Zi+"
  else
    JVM_CFLAGS_SYMBOLS="$JVM_CFLAGS_SYMBOLS -g"
  fi
  AC_SUBST(JVM_CFLAGS_SYMBOLS)

  # bounds, memory and behavior checking options
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    case $DEBUG_LEVEL in
    slowdebug )
      # FIXME: By adding this to C(XX)FLAGS_DEBUG_OPTIONS/JVM_CFLAGS_SYMBOLS it
      # get's added conditionally on whether we produce debug symbols or not.
      # This is most likely not really correct.

      if test "x$STACK_PROTECTOR_CFLAG" != x; then
        JVM_CFLAGS_SYMBOLS="$JVM_CFLAGS_SYMBOLS $STACK_PROTECTOR_CFLAG --param ssp-buffer-size=1"
      fi
      ;;
    esac
  fi
])

AC_DEFUN([FLAGS_SETUP_QUALITY_CHECKS],
[
  AC_ARG_ENABLE([warnings-as-errors], [AS_HELP_STRING([--disable-warnings-as-errors],
      [do not consider native warnings to be an error @<:@enabled@:>@])])

  AC_MSG_CHECKING([if native warnings are errors])
  if test "x$enable_warnings_as_errors" = "xyes"; then
    AC_MSG_RESULT([yes (explicitly set)])
    WARNINGS_AS_ERRORS=true
  elif test "x$enable_warnings_as_errors" = "xno"; then
    AC_MSG_RESULT([no])
    WARNINGS_AS_ERRORS=false
  elif test "x$enable_warnings_as_errors" = "x"; then
    AC_MSG_RESULT([yes (default)])
    WARNINGS_AS_ERRORS=true
  else
    AC_MSG_ERROR([--enable-warnings-as-errors accepts no argument])
  fi

  AC_SUBST(WARNINGS_AS_ERRORS)

  case "${TOOLCHAIN_TYPE}" in
    microsoft)
      DISABLE_WARNING_PREFIX="-wd"
      CFLAGS_WARNINGS_ARE_ERRORS="-WX"
      ;;
    solstudio)
      DISABLE_WARNING_PREFIX="-erroff="
      CFLAGS_WARNINGS_ARE_ERRORS="-errtags -errwarn=%all"
      ;;
    gcc)
      # Prior to gcc 4.4, a -Wno-X where X is unknown for that version of gcc will cause an error
      FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [-Wno-this-is-a-warning-that-do-not-exist],
          IF_TRUE: [GCC_CAN_DISABLE_WARNINGS=true],
          IF_FALSE: [GCC_CAN_DISABLE_WARNINGS=false]
      )
      if test "x$GCC_CAN_DISABLE_WARNINGS" = "xtrue"; then
        DISABLE_WARNING_PREFIX="-Wno-"
      else
        DISABLE_WARNING_PREFIX=
      fi
      CFLAGS_WARNINGS_ARE_ERRORS="-Werror"
      # Repeate the check for the BUILD_CC and BUILD_CXX. Need to also reset
      # CFLAGS since any target specific flags will likely not work with the
      # build compiler
      CC_OLD="$CC"
      CXX_OLD="$CXX"
      CC="$BUILD_CC"
      CXX="$BUILD_CXX"
      CFLAGS_OLD="$CFLAGS"
      CFLAGS=""
      FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [-Wno-this-is-a-warning-that-do-not-exist],
          IF_TRUE: [BUILD_CC_CAN_DISABLE_WARNINGS=true],
          IF_FALSE: [BUILD_CC_CAN_DISABLE_WARNINGS=false]
      )
      if test "x$BUILD_CC_CAN_DISABLE_WARNINGS" = "xtrue"; then
        BUILD_CC_DISABLE_WARNING_PREFIX="-Wno-"
      else
        BUILD_CC_DISABLE_WARNING_PREFIX=
      fi
      CC="$CC_OLD"
      CXX="$CXX_OLD"
      CFLAGS="$CFLAGS_OLD"
      ;;
    clang)
      DISABLE_WARNING_PREFIX="-Wno-"
      CFLAGS_WARNINGS_ARE_ERRORS="-Werror"
      ;;
    xlc)
      DISABLE_WARNING_PREFIX="-qsuppress="
      CFLAGS_WARNINGS_ARE_ERRORS="-qhalt=w"
      ;;
  esac
  AC_SUBST(DISABLE_WARNING_PREFIX)
  AC_SUBST(BUILD_CC_DISABLE_WARNING_PREFIX)
  AC_SUBST(CFLAGS_WARNINGS_ARE_ERRORS)

  # bounds, memory and behavior checking options
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    case $DEBUG_LEVEL in
    release )
      # no adjustment
      ;;
    fastdebug )
      # no adjustment
      ;;
    slowdebug )
      # FIXME: By adding this to C(XX)FLAGS_DEBUG_OPTIONS/JVM_CFLAGS_SYMBOLS it
      # get's added conditionally on whether we produce debug symbols or not.
      # This is most likely not really correct.

      # Add runtime stack smashing and undefined behavior checks.
      # Not all versions of gcc support -fstack-protector
      STACK_PROTECTOR_CFLAG="-fstack-protector-all"
      FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [$STACK_PROTECTOR_CFLAG -Werror],
          IF_FALSE: [STACK_PROTECTOR_CFLAG=""])

      CFLAGS_DEBUG_OPTIONS="$STACK_PROTECTOR_CFLAG --param ssp-buffer-size=1"
      CXXFLAGS_DEBUG_OPTIONS="$STACK_PROTECTOR_CFLAG --param ssp-buffer-size=1"
      ;;
    esac
  fi
])

AC_DEFUN([FLAGS_SETUP_OPTIMIZATION],
[

  # Optimization levels
  # Most toolchains share opt flags between CC and CXX;
  # setup for C and duplicate afterwards.

  if test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    CC_HIGHEST="-fns -fsimple -fsingle -xbuiltin=%all -xdepend -xrestrict -xlibmil"

    C_O_FLAG_HIGHEST_JVM="-xO4"
    C_O_FLAG_DEBUG_JVM=""
    C_O_FLAG_SIZE=""
    C_O_FLAG_DEBUG=""
    C_O_FLAG_NONE=""
    if test "x$OPENJDK_TARGET_CPU_ARCH" = "xx86"; then
      C_O_FLAG_HIGHEST="-xO4 -Wu,-O4~yz $CC_HIGHEST"
      C_O_FLAG_HI="-xO4 -Wu,-O4~yz"
      C_O_FLAG_NORM="-xO2 -Wu,-O2~yz"
    elif test "x$OPENJDK_TARGET_CPU_ARCH" = "xsparc"; then
      C_O_FLAG_HIGHEST="-xO4 -Wc,-Qrm-s -Wc,-Qiselect-T0 \
          -xprefetch=auto,explicit -xchip=ultra $CC_HIGHEST"
      C_O_FLAG_HI="-xO4 -Wc,-Qrm-s -Wc,-Qiselect-T0"
      C_O_FLAG_NORM="-xO2 -Wc,-Qrm-s -Wc,-Qiselect-T0"
    fi
  elif test "x$TOOLCHAIN_TYPE" = xgcc; then
    C_O_FLAG_HIGHEST_JVM="-O3"
    C_O_FLAG_HIGHEST="-O3"
    C_O_FLAG_HI="-O3"
    C_O_FLAG_NORM="-O2"
    C_O_FLAG_SIZE="-Os"
    C_O_FLAG_DEBUG="-O0"
    C_O_FLAG_DEBUG_JVM="-O0"
    C_O_FLAG_NONE="-O0"
  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    if test "x$OPENJDK_TARGET_OS" = xmacosx; then
      # On MacOSX we optimize for size, something
      # we should do for all platforms?
      C_O_FLAG_HIGHEST_JVM="-Os"
      C_O_FLAG_HIGHEST="-Os"
      C_O_FLAG_HI="-Os"
      C_O_FLAG_NORM="-Os"
      C_O_FLAG_DEBUG_JVM=""
    else
      C_O_FLAG_HIGHEST_JVM="-O3"
      C_O_FLAG_HIGHEST="-O3"
      C_O_FLAG_HI="-O3"
      C_O_FLAG_NORM="-O2"
      C_O_FLAG_DEBUG_JVM="-O0"
    fi
    C_O_FLAG_SIZE="-Os"
    C_O_FLAG_DEBUG="-O0"
    C_O_FLAG_NONE="-O0"
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    C_O_FLAG_HIGHEST_JVM="-O3 -qhot=level=1 -qinline -qinlglue"
    C_O_FLAG_HIGHEST="-O3 -qhot=level=1 -qinline -qinlglue"
    C_O_FLAG_HI="-O3 -qinline -qinlglue"
    C_O_FLAG_NORM="-O2"
    C_O_FLAG_DEBUG="-qnoopt"
    # FIXME: Value below not verified.
    C_O_FLAG_DEBUG_JVM=""
    C_O_FLAG_NONE="-qnoopt"
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    C_O_FLAG_HIGHEST_JVM="-O2 -Oy-"
    C_O_FLAG_HIGHEST="-O2"
    C_O_FLAG_HI="-O1"
    C_O_FLAG_NORM="-O1"
    C_O_FLAG_DEBUG="-Od"
    C_O_FLAG_DEBUG_JVM=""
    C_O_FLAG_NONE="-Od"
    C_O_FLAG_SIZE="-Os"
  fi

  # Now copy to C++ flags
  CXX_O_FLAG_HIGHEST_JVM="$C_O_FLAG_HIGHEST_JVM"
  CXX_O_FLAG_HIGHEST="$C_O_FLAG_HIGHEST"
  CXX_O_FLAG_HI="$C_O_FLAG_HI"
  CXX_O_FLAG_NORM="$C_O_FLAG_NORM"
  CXX_O_FLAG_DEBUG="$C_O_FLAG_DEBUG"
  CXX_O_FLAG_DEBUG_JVM="$C_O_FLAG_DEBUG_JVM"
  CXX_O_FLAG_NONE="$C_O_FLAG_NONE"
  CXX_O_FLAG_SIZE="$C_O_FLAG_SIZE"

  if test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    # In solstudio, also add this to C (but not C++) flags...
    C_O_FLAG_HIGHEST="$C_O_FLAG_HIGHEST -xalias_level=basic"
  fi

  # Adjust optimization flags according to debug level.
  case $DEBUG_LEVEL in
    release )
      # no adjustment
      ;;
    fastdebug )
      # Not quite so much optimization
      C_O_FLAG_HI="$C_O_FLAG_NORM"
      CXX_O_FLAG_HI="$CXX_O_FLAG_NORM"
      ;;
    slowdebug )
      # Disable optimization
      C_O_FLAG_HIGHEST_JVM="$C_O_FLAG_DEBUG_JVM"
      C_O_FLAG_HIGHEST="$C_O_FLAG_DEBUG"
      C_O_FLAG_HI="$C_O_FLAG_DEBUG"
      C_O_FLAG_NORM="$C_O_FLAG_DEBUG"
      C_O_FLAG_SIZE="$C_O_FLAG_DEBUG"
      CXX_O_FLAG_HIGHEST_JVM="$CXX_O_FLAG_DEBUG_JVM"
      CXX_O_FLAG_HIGHEST="$CXX_O_FLAG_DEBUG"
      CXX_O_FLAG_HI="$CXX_O_FLAG_DEBUG"
      CXX_O_FLAG_NORM="$CXX_O_FLAG_DEBUG"
      CXX_O_FLAG_SIZE="$CXX_O_FLAG_DEBUG"
      ;;
  esac

  AC_SUBST(C_O_FLAG_HIGHEST_JVM)
  AC_SUBST(C_O_FLAG_HIGHEST)
  AC_SUBST(C_O_FLAG_HI)
  AC_SUBST(C_O_FLAG_NORM)
  AC_SUBST(C_O_FLAG_NONE)
  AC_SUBST(C_O_FLAG_SIZE)
  AC_SUBST(CXX_O_FLAG_HIGHEST_JVM)
  AC_SUBST(CXX_O_FLAG_HIGHEST)
  AC_SUBST(CXX_O_FLAG_HI)
  AC_SUBST(CXX_O_FLAG_NORM)
  AC_SUBST(CXX_O_FLAG_NONE)
  AC_SUBST(CXX_O_FLAG_SIZE)
])
