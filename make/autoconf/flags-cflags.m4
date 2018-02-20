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
    PICFLAG="-fPIC"
    C_FLAG_REORDER=''
    CXX_FLAG_REORDER=''

    if test "x$OPENJDK_TARGET_OS" = xmacosx; then
      # Linking is different on MacOSX
      if test "x$STATIC_BUILD" = xtrue; then
        SHARED_LIBRARY_FLAGS ='-undefined dynamic_lookup'
      else
        SHARED_LIBRARY_FLAGS="-dynamiclib -compatibility_version 1.0.0 -current_version 1.0.0 -fPIC"
      fi
      SET_EXECUTABLE_ORIGIN='-Wl,-rpath,@loader_path$(or [$]1,/.)'
      SET_SHARED_LIBRARY_ORIGIN="$SET_EXECUTABLE_ORIGIN"
      SET_SHARED_LIBRARY_NAME='-Wl,-install_name,@rpath/[$]1'
      SET_SHARED_LIBRARY_MAPFILE='-Wl,-exported_symbols_list,[$]1'
    else
      # Default works for linux, might work on other platforms as well.
      SHARED_LIBRARY_FLAGS='-shared'
      SET_EXECUTABLE_ORIGIN='-Wl,-rpath,\$$ORIGIN[$]1'
      SET_SHARED_LIBRARY_ORIGIN="-Wl,-z,origin $SET_EXECUTABLE_ORIGIN"
      SET_SHARED_LIBRARY_NAME='-Wl,-soname=[$]1'
      SET_SHARED_LIBRARY_MAPFILE='-Wl,-version-script=[$]1'
    fi
  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    C_FLAG_REORDER=''
    CXX_FLAG_REORDER=''

    if test "x$OPENJDK_TARGET_OS" = xmacosx; then
      # Linking is different on MacOSX
      PICFLAG=''
      SHARED_LIBRARY_FLAGS="-dynamiclib -compatibility_version 1.0.0 -current_version 1.0.0"
      SET_EXECUTABLE_ORIGIN='-Wl,-rpath,@loader_path$(or [$]1,/.)'
      SET_SHARED_LIBRARY_ORIGIN="$SET_EXECUTABLE_ORIGIN"
      SET_SHARED_LIBRARY_NAME='-Wl,-install_name,@rpath/[$]1'
      SET_SHARED_LIBRARY_MAPFILE='-Wl,-exported_symbols_list,[$]1'

    else
      # Default works for linux, might work on other platforms as well.
      PICFLAG='-fPIC'
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
    if test "x$OPENJDK_TARGET_CPU" = xsparcv9; then
      PICFLAG="-xcode=pic32"
    else
      PICFLAG="-KPIC"
    fi
    C_FLAG_REORDER='-xF'
    CXX_FLAG_REORDER='-xF'
    SHARED_LIBRARY_FLAGS="-G"
    SET_EXECUTABLE_ORIGIN='-R\$$ORIGIN[$]1'
    SET_SHARED_LIBRARY_ORIGIN="$SET_EXECUTABLE_ORIGIN"
    SET_SHARED_LIBRARY_NAME='-h [$]1'
    SET_SHARED_LIBRARY_MAPFILE='-M[$]1'
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    # '-qpic' defaults to 'qpic=small'. This means that the compiler generates only
    # one instruction for accessing the TOC. If the TOC grows larger than 64K, the linker
    # will have to patch this single instruction with a call to some out-of-order code which
    # does the load from the TOC. This is of course slow. But in that case we also would have
    # to use '-bbigtoc' for linking anyway so we could also change the PICFLAG to 'qpic=large'.
    # With 'qpic=large' the compiler will by default generate a two-instruction sequence which
    # can be patched directly by the linker and does not require a jump to out-of-order code.
    # Another alternative instead of using 'qpic=large -bbigtoc' may be to use '-qminimaltoc'
    # instead. This creates a distinct TOC for every compilation unit (and thus requires two
    # loads for accessing a global variable). But there are rumors that this may be seen as a
    # 'performance feature' because of improved code locality of the symbols used in a
    # compilation unit.
    PICFLAG="-qpic"
    C_FLAG_REORDER=''
    CXX_FLAG_REORDER=''
    SHARED_LIBRARY_FLAGS="-qmkshrobj -bM:SRE -bnoentry"
    SET_EXECUTABLE_ORIGIN=""
    SET_SHARED_LIBRARY_ORIGIN=''
    SET_SHARED_LIBRARY_NAME=''
    SET_SHARED_LIBRARY_MAPFILE=''
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    PICFLAG=""
    C_FLAG_REORDER=''
    CXX_FLAG_REORDER=''
    SHARED_LIBRARY_FLAGS="-dll"
    SET_EXECUTABLE_ORIGIN=''
    SET_SHARED_LIBRARY_ORIGIN=''
    SET_SHARED_LIBRARY_NAME=''
    SET_SHARED_LIBRARY_MAPFILE='-def:[$]1'
  fi

  AC_SUBST(C_FLAG_REORDER)
  AC_SUBST(CXX_FLAG_REORDER)
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

])

AC_DEFUN([FLAGS_SETUP_QUALITY_CHECKS],
[
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
      FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [$STACK_PROTECTOR_CFLAG -Werror], IF_FALSE: [STACK_PROTECTOR_CFLAG=""])

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
      C_O_FLAG_HIGHEST="-xO4 -Wc,-Qrm-s -Wc,-Qiselect-T0 -xprefetch=auto,explicit -xchip=ultra $CC_HIGHEST"
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
