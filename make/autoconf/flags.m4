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

m4_include([flags-cflags.m4])
m4_include([flags-ldflags.m4])
m4_include([flags-other.m4])

# Sort the flags in a given variable, and remove superfluous whitespace.
#
# $1 = name of variable to sort
AC_DEFUN([FLAGS_SORT_FLAGS], [
  $1=`$ECHO ${$1} | $TR ' ' '\n' | $SORT | $TR '\n' ' '`
])

################################################################################
#
# Setup ABI profile (for arm)
#
AC_DEFUN([FLAGS_SETUP_ABI_PROFILE],
[
  AC_ARG_WITH(abi-profile, [AS_HELP_STRING([--with-abi-profile],
      [specify ABI profile for ARM builds (arm-vfp-sflt,arm-vfp-hflt,arm-sflt, armv5-vfp-sflt,armv6-vfp-hflt,arm64,aarch64) @<:@toolchain dependent@:>@ ])])

  if test "x$with_abi_profile" != x; then
    if test "x$OPENJDK_TARGET_CPU" != xarm && \
        test "x$OPENJDK_TARGET_CPU" != xaarch64; then
      AC_MSG_ERROR([--with-abi-profile only available on arm/aarch64])
    fi

    OPENJDK_TARGET_ABI_PROFILE=$with_abi_profile
    AC_MSG_CHECKING([for ABI profle])
    AC_MSG_RESULT([$OPENJDK_TARGET_ABI_PROFILE])

    if test "x$OPENJDK_TARGET_ABI_PROFILE" = xarm-vfp-sflt; then
      ARM_FLOAT_TYPE=vfp-sflt
      ARM_ARCH_TYPE_FLAGS='-march=armv7-a -mthumb'
    elif test "x$OPENJDK_TARGET_ABI_PROFILE" = xarm-vfp-hflt; then
      ARM_FLOAT_TYPE=vfp-hflt
      ARM_ARCH_TYPE_FLAGS='-march=armv7-a -mthumb'
    elif test "x$OPENJDK_TARGET_ABI_PROFILE" = xarm-sflt; then
      ARM_FLOAT_TYPE=sflt
      ARM_ARCH_TYPE_FLAGS='-march=armv5t -marm'
    elif test "x$OPENJDK_TARGET_ABI_PROFILE" = xarmv5-vfp-sflt; then
      ARM_FLOAT_TYPE=vfp-sflt
      ARM_ARCH_TYPE_FLAGS='-march=armv5t -marm'
    elif test "x$OPENJDK_TARGET_ABI_PROFILE" = xarmv6-vfp-hflt; then
      ARM_FLOAT_TYPE=vfp-hflt
      ARM_ARCH_TYPE_FLAGS='-march=armv6 -marm'
    elif test "x$OPENJDK_TARGET_ABI_PROFILE" = xarm64; then
      # No special flags, just need to trigger setting JDK_ARCH_ABI_PROP_NAME
      ARM_FLOAT_TYPE=
      ARM_ARCH_TYPE_FLAGS=
    elif test "x$OPENJDK_TARGET_ABI_PROFILE" = xaarch64; then
      # No special flags, just need to trigger setting JDK_ARCH_ABI_PROP_NAME
      ARM_FLOAT_TYPE=
      ARM_ARCH_TYPE_FLAGS=
    else
      AC_MSG_ERROR([Invalid ABI profile: "$OPENJDK_TARGET_ABI_PROFILE"])
    fi

    if test "x$ARM_FLOAT_TYPE" = xvfp-sflt; then
      ARM_FLOAT_TYPE_FLAGS='-mfloat-abi=softfp -mfpu=vfp -DFLOAT_ARCH=-vfp-sflt'
    elif test "x$ARM_FLOAT_TYPE" = xvfp-hflt; then
      ARM_FLOAT_TYPE_FLAGS='-mfloat-abi=hard -mfpu=vfp -DFLOAT_ARCH=-vfp-hflt'
    elif test "x$ARM_FLOAT_TYPE" = xsflt; then
      ARM_FLOAT_TYPE_FLAGS='-msoft-float -mfpu=vfp'
    fi
    AC_MSG_CHECKING([for $ARM_FLOAT_TYPE floating point flags])
    AC_MSG_RESULT([$ARM_FLOAT_TYPE_FLAGS])

    AC_MSG_CHECKING([for arch type flags])
    AC_MSG_RESULT([$ARM_ARCH_TYPE_FLAGS])

    # Now set JDK_ARCH_ABI_PROP_NAME. This is equivalent to the last part of the
    # autoconf target triplet.
    [ JDK_ARCH_ABI_PROP_NAME=`$ECHO $OPENJDK_TARGET_AUTOCONF_NAME | $SED -e 's/.*-\([^-]*\)$/\1/'` ]
    # Sanity check that it is a known ABI.
    if test "x$JDK_ARCH_ABI_PROP_NAME" != xgnu && \
        test "x$JDK_ARCH_ABI_PROP_NAME" != xgnueabi  && \
        test "x$JDK_ARCH_ABI_PROP_NAME" != xgnueabihf; then
          AC_MSG_WARN([Unknown autoconf target triplet ABI: "$JDK_ARCH_ABI_PROP_NAME"])
    fi
    AC_MSG_CHECKING([for ABI property name])
    AC_MSG_RESULT([$JDK_ARCH_ABI_PROP_NAME])
    AC_SUBST(JDK_ARCH_ABI_PROP_NAME)
  fi

  # When building with an abi profile, the name of that profile is appended on the
  # bundle platform, which is used in bundle names.
  if test "x$OPENJDK_TARGET_ABI_PROFILE" != x; then
    OPENJDK_TARGET_BUNDLE_PLATFORM="$OPENJDK_TARGET_OS_BUNDLE-$OPENJDK_TARGET_ABI_PROFILE"
  fi
])

# Reset the global CFLAGS/LDFLAGS variables and initialize them with the
# corresponding configure arguments instead
AC_DEFUN_ONCE([FLAGS_SETUP_USER_SUPPLIED_FLAGS],
[
  if test "x$CFLAGS" != "x"; then
    AC_MSG_WARN([Ignoring CFLAGS($CFLAGS) found in environment. Use --with-extra-cflags])
  fi

  if test "x$CXXFLAGS" != "x"; then
    AC_MSG_WARN([Ignoring CXXFLAGS($CXXFLAGS) found in environment. Use --with-extra-cxxflags])
  fi

  if test "x$LDFLAGS" != "x"; then
    AC_MSG_WARN([Ignoring LDFLAGS($LDFLAGS) found in environment. Use --with-extra-ldflags])
  fi

  AC_ARG_WITH(extra-cflags, [AS_HELP_STRING([--with-extra-cflags],
      [extra flags to be used when compiling jdk c-files])])

  AC_ARG_WITH(extra-cxxflags, [AS_HELP_STRING([--with-extra-cxxflags],
      [extra flags to be used when compiling jdk c++-files])])

  AC_ARG_WITH(extra-ldflags, [AS_HELP_STRING([--with-extra-ldflags],
      [extra flags to be used when linking jdk])])

  USER_CFLAGS="$with_extra_cflags"
  USER_CXXFLAGS="$with_extra_cxxflags"
  USER_LDFLAGS="$with_extra_ldflags"
])

# Setup the sysroot flags and add them to global CFLAGS and LDFLAGS so
# that configure can use them while detecting compilers.
# TOOLCHAIN_TYPE is available here.
# Param 1 - Optional prefix to all variables. (e.g BUILD_)
AC_DEFUN([FLAGS_SETUP_SYSROOT_FLAGS],
[
  if test "x[$]$1SYSROOT" != "x"; then
    if test "x$TOOLCHAIN_TYPE" = xsolstudio; then
      if test "x$OPENJDK_TARGET_OS" = xsolaris; then
        # Solaris Studio does not have a concept of sysroot. Instead we must
        # make sure the default include and lib dirs are appended to each
        # compile and link command line. Must also add -I-xbuiltin to enable
        # inlining of system functions and intrinsics.
        $1SYSROOT_CFLAGS="-I-xbuiltin -I[$]$1SYSROOT/usr/include"
        $1SYSROOT_LDFLAGS="-L[$]$1SYSROOT/usr/lib$OPENJDK_TARGET_CPU_ISADIR \
            -L[$]$1SYSROOT/lib$OPENJDK_TARGET_CPU_ISADIR"
        # If the devkit contains the ld linker, make sure we use it.
        AC_PATH_PROG(SOLARIS_LD, ld, , $DEVKIT_TOOLCHAIN_PATH:$DEVKIT_EXTRA_PATH)
        # Make sure this ld is runnable.
        if test -f "$SOLARIS_LD"; then
          if "$SOLARIS_LD" -V > /dev/null 2> /dev/null; then
            $1SYSROOT_LDFLAGS="[$]$1SYSROOT_LDFLAGS -Yl,$(dirname $SOLARIS_LD)"
          else
            AC_MSG_WARN([Could not run $SOLARIS_LD found in devkit, reverting to system ld])
          fi
        fi
      fi
    elif test "x$TOOLCHAIN_TYPE" = xgcc; then
      $1SYSROOT_CFLAGS="--sysroot=[$]$1SYSROOT"
      $1SYSROOT_LDFLAGS="--sysroot=[$]$1SYSROOT"
    elif test "x$TOOLCHAIN_TYPE" = xclang; then
      $1SYSROOT_CFLAGS="-isysroot [$]$1SYSROOT"
      $1SYSROOT_LDFLAGS="-isysroot [$]$1SYSROOT"
    fi
  fi

  if test "x$OPENJDK_TARGET_OS" = xmacosx; then
    # We also need -iframework<path>/System/Library/Frameworks
    $1SYSROOT_CFLAGS="[$]$1SYSROOT_CFLAGS -iframework [$]$1SYSROOT/System/Library/Frameworks"
    $1SYSROOT_LDFLAGS="[$]$1SYSROOT_LDFLAGS -iframework [$]$1SYSROOT/System/Library/Frameworks"
    # These always need to be set, or we can't find the frameworks embedded in JavaVM.framework
    # set this here so it doesn't have to be peppered throughout the forest
    $1SYSROOT_CFLAGS="[$]$1SYSROOT_CFLAGS -F [$]$1SYSROOT/System/Library/Frameworks/JavaVM.framework/Frameworks"
    $1SYSROOT_LDFLAGS="[$]$1SYSROOT_LDFLAGS -F [$]$1SYSROOT/System/Library/Frameworks/JavaVM.framework/Frameworks"
  fi

  AC_SUBST($1SYSROOT_CFLAGS)
  AC_SUBST($1SYSROOT_LDFLAGS)
])

AC_DEFUN_ONCE([FLAGS_SETUP_GLOBAL_FLAGS],
[
  if test "x$TOOLCHAIN_TYPE" = xxlc; then
    MACHINE_FLAG="-q${OPENJDK_TARGET_CPU_BITS}"
  elif test "x$TOOLCHAIN_TYPE" != xmicrosoft; then
    MACHINE_FLAG="-m${OPENJDK_TARGET_CPU_BITS}"
  fi

  # FIXME: global flags are not used yet...
  # The "global" flags will *always* be set. Without them, it is not possible to
  # get a working compilation.
  GLOBAL_CFLAGS="$MACHINE_FLAG $SYSROOT_CFLAGS $USER_CFLAGS"
  GLOBAL_CXXFLAGS="$MACHINE_FLAG $SYSROOT_CFLAGS $USER_CXXFLAGS"
  GLOBAL_LDFLAGS="$MACHINE_FLAG $SYSROOT_LDFLAGS $USER_LDFLAGS"
  # FIXME: Don't really know how to do with this, but this was the old behavior
  GLOBAL_CPPFLAGS="$SYSROOT_CFLAGS"
  AC_SUBST(GLOBAL_CFLAGS)
  AC_SUBST(GLOBAL_CXXFLAGS)
  AC_SUBST(GLOBAL_LDFLAGS)
  AC_SUBST(GLOBAL_CPPFLAGS)

  # FIXME: For compatilibity, export this as EXTRA_CFLAGS for now.
  EXTRA_CFLAGS="$MACHINE_FLAG $USER_CFLAGS"
  EXTRA_CXXFLAGS="$MACHINE_FLAG $USER_CXXFLAGS"
  EXTRA_LDFLAGS="$MACHINE_FLAG $USER_LDFLAGS"

  AC_SUBST(EXTRA_CFLAGS)
  AC_SUBST(EXTRA_CXXFLAGS)
  AC_SUBST(EXTRA_LDFLAGS)

  # For autoconf testing to work, the global flags must also be stored in the
  # "unnamed" CFLAGS etc.
  CFLAGS="$GLOBAL_CFLAGS"
  CXXFLAGS="$GLOBAL_CXXFLAGS"
  LDFLAGS="$GLOBAL_LDFLAGS"
  CPPFLAGS="$GLOBAL_CPPFLAGS"
])

AC_DEFUN_ONCE([FLAGS_SETUP_INIT_FLAGS],
[
  FLAGS_SETUP_TOOLCHAIN_CONTROL

  FLAGS_SETUP_ARFLAGS
  FLAGS_SETUP_STRIPFLAGS

  FLAGS_SETUP_RCFLAGS

  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    # silence copyright notice and other headers.
    COMMON_CCXXFLAGS="$COMMON_CCXXFLAGS -nologo"
  fi

  if test "x$BUILD_SYSROOT" != x; then
    FLAGS_SETUP_SYSROOT_FLAGS([BUILD_])
  else
    BUILD_SYSROOT_CFLAGS="$SYSROOT_CFLAGS"
    BUILD_SYSROOT_LDFLAGS="$SYSROOT_LDFLAGS"
  fi
  AC_SUBST(BUILD_SYSROOT_CFLAGS)
  AC_SUBST(BUILD_SYSROOT_LDFLAGS)

])

AC_DEFUN([FLAGS_SETUP_COMPILER_FLAGS_FOR_LIBS],
[
  FLAGS_SETUP_SHARED_LIBS


  # The (cross) compiler is now configured, we can now test capabilities
  # of the target platform.
])

# Documentation on common flags used for solstudio in HIGHEST.
#
# WARNING: Use of OPTIMIZATION_LEVEL=HIGHEST in your Makefile needs to be
#          done with care, there are some assumptions below that need to
#          be understood about the use of pointers, and IEEE behavior.
#
# -fns: Use non-standard floating point mode (not IEEE 754)
# -fsimple: Do some simplification of floating point arithmetic (not IEEE 754)
# -fsingle: Use single precision floating point with 'float'
# -xalias_level=basic: Assume memory references via basic pointer types do not alias
#   (Source with excessing pointer casting and data access with mixed
#    pointer types are not recommended)
# -xbuiltin=%all: Use intrinsic or inline versions for math/std functions
#   (If you expect perfect errno behavior, do not use this)
# -xdepend: Loop data dependency optimizations (need -xO3 or higher)
# -xrestrict: Pointer parameters to functions do not overlap
#   (Similar to -xalias_level=basic usage, but less obvious sometimes.
#    If you pass in multiple pointers to the same data, do not use this)
# -xlibmil: Inline some library routines
#   (If you expect perfect errno behavior, do not use this)
# -xlibmopt: Use optimized math routines (CURRENTLY DISABLED)
#   (If you expect perfect errno behavior, do not use this)
#  Can cause undefined external on Solaris 8 X86 on __sincos, removing for now

AC_DEFUN_ONCE([FLAGS_SETUP_COMPILER_FLAGS_FOR_OPTIMIZATION],
[
  FLAGS_SETUP_DEBUG_SYMBOLS
  FLAGS_SETUP_QUALITY_CHECKS
  FLAGS_SETUP_OPTIMIZATION

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

  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    if test "x$DEBUG_LEVEL" != xrelease; then
      if test "x$OPENJDK_TARGET_CPU" = xx86_64; then
        JVM_CFLAGS="$JVM_CFLAGS -homeparams"
      fi
    fi
  fi
])


AC_DEFUN([FLAGS_SETUP_COMPILER_FLAGS_FOR_JDK],
[
  # Additional macosx handling
  if test "x$FLAGS_OS" = xmacosx; then
    # MACOSX_VERSION_MIN specifies the lowest version of Macosx that the built
    # binaries should be compatible with, even if compiled on a newer version
    # of the OS. It currently has a hard coded value. Setting this also limits
    # exposure to API changes in header files. Bumping this is likely to
    # require code changes to build.
    MACOSX_VERSION_MIN=10.7.0
    AC_SUBST(MACOSX_VERSION_MIN)

    # Setting --with-macosx-version-max=<version> makes it an error to build or
    # link to macosx APIs that are newer than the given OS version. The expected
    # format for <version> is either nn.n.n or nn.nn.nn. See /usr/include/AvailabilityMacros.h.
    AC_ARG_WITH([macosx-version-max], [AS_HELP_STRING([--with-macosx-version-max],
        [error on use of newer functionality. @<:@macosx@:>@])],
        [
          if echo "$with_macosx_version_max" | $GREP -q "^[[0-9]][[0-9]]\.[[0-9]]\.[[0-9]]\$"; then
              MACOSX_VERSION_MAX=$with_macosx_version_max
          elif echo "$with_macosx_version_max" | $GREP -q "^[[0-9]][[0-9]]\.[[0-9]][[0-9]]\.[[0-9]][[0-9]]\$"; then
              MACOSX_VERSION_MAX=$with_macosx_version_max
          elif test "x$with_macosx_version_max" = "xno"; then
              # Use build system default
              MACOSX_VERSION_MAX=
          else
              AC_MSG_ERROR([osx version format must be nn.n.n or nn.nn.nn])
          fi
        ],
        [MACOSX_VERSION_MAX=]
    )
    AC_SUBST(MACOSX_VERSION_MAX)
  fi

  FLAGS_SETUP_ABI_PROFILE

  # Optional POSIX functionality needed by the JVM
  #
  # Check if clock_gettime is available and in which library. This indicates
  # availability of CLOCK_MONOTONIC for hotspot. But we don't need to link, so
  # don't let it update LIBS.
  save_LIBS="$LIBS"
  AC_SEARCH_LIBS(clock_gettime, rt, [HAS_CLOCK_GETTIME=true], [])
  if test "x$LIBS" = "x-lrt "; then
    CLOCK_GETTIME_IN_LIBRT=true
  fi
  LIBS="$save_LIBS"

  FLAGS_SETUP_LINKER_FLAGS_FOR_JDK

  ### CFLAGS

  FLAGS_SETUP_COMPILER_FLAGS_FOR_JDK_HELPER

  FLAGS_OS=$OPENJDK_TARGET_OS
  FLAGS_OS_TYPE=$OPENJDK_TARGET_OS_TYPE
  FLAGS_CPU=$OPENJDK_TARGET_CPU
  FLAGS_CPU_ARCH=$OPENJDK_TARGET_CPU_ARCH
  FLAGS_CPU_BITS=$OPENJDK_TARGET_CPU_BITS
  FLAGS_CPU_ENDIAN=$OPENJDK_TARGET_CPU_ENDIAN
  FLAGS_CPU_LEGACY=$OPENJDK_TARGET_CPU_LEGACY
  FLAGS_ADD_LP64=$OPENJDK_TARGET_ADD_LP64

  FLAGS_SETUP_COMPILER_FLAGS_FOR_JDK_CPU_DEP([TARGET])

  FLAGS_OS=$OPENJDK_BUILD_OS
  FLAGS_OS_TYPE=$OPENJDK_BUILD_OS_TYPE
  FLAGS_CPU=$OPENJDK_BUILD_CPU
  FLAGS_CPU_ARCH=$OPENJDK_BUILD_CPU_ARCH
  FLAGS_CPU_BITS=$OPENJDK_BUILD_CPU_BITS
  FLAGS_CPU_ENDIAN=$OPENJDK_BUILD_CPU_ENDIAN
  FLAGS_CPU_LEGACY=$OPENJDK_BUILD_CPU_LEGACY
  FLAGS_ADD_LP64=$OPENJDK_BUILD_ADD_LP64

  FLAGS_SETUP_COMPILER_FLAGS_FOR_JDK_CPU_DEP([BUILD], [OPENJDK_BUILD_])

  # Tests are only ever compiled for TARGET
  CFLAGS_TESTLIB="$CFLAGS_JDKLIB"
  CXXFLAGS_TESTLIB="$CXXFLAGS_JDKLIB"
  CFLAGS_TESTEXE="$CFLAGS_JDKEXE"
  CXXFLAGS_TESTEXE="$CXXFLAGS_JDKEXE"

  AC_SUBST(CFLAGS_TESTLIB)
  AC_SUBST(CFLAGS_TESTEXE)
  AC_SUBST(CXXFLAGS_TESTLIB)
  AC_SUBST(CXXFLAGS_TESTEXE)

   ############## ARFLAGS
  # Additional macosx handling
  if test "x$OPENJDK_TARGET_OS" = xmacosx; then
    ARFLAGS="$ARFLAGS -mmacosx-version-min=\$(MACOSX_VERSION_MIN)"
  fi

   ############## ASFLAGS

  # Set JVM_ASFLAGS
  if test "x$OPENJDK_TARGET_OS" = xmacosx; then
    JVM_ASFLAGS="-x assembler-with-cpp -mno-omit-leaf-frame-pointer -mstack-alignment=16"
  fi
  JVM_ASFLAGS="$JVM_ASFLAGS $MACHINE_FLAG"

  AC_SUBST(JVM_ASFLAGS)
])

################################################################################
# platform independent
AC_DEFUN([FLAGS_SETUP_COMPILER_FLAGS_FOR_JDK_HELPER],
[
  #### OS DEFINES, these should be independent on toolchain
  if test "x$OPENJDK_TARGET_OS" = xlinux; then
    CFLAGS_OS_DEF_JVM="-DLINUX"
    CFLAGS_OS_DEF_JDK="-D_GNU_SOURCE -D_REENTRANT -D_LARGEFILE64_SOURCE"
  elif test "x$OPENJDK_TARGET_OS" = xsolaris; then
    CFLAGS_OS_DEF_JVM="-DSOLARIS"
    CFLAGS_OS_DEF_JDK="-D__solaris__"
  elif test "x$OPENJDK_TARGET_OS" = xmacosx; then
    CFLAGS_OS_DEF_JVM="-D_ALLBSD_SOURCE -D_DARWIN_C_SOURCE -D_XOPEN_SOURCE"
    CFLAGS_OS_DEF_JDK="-D_ALLBSD_SOURCE -D_DARWIN_UNLIMITED_SELECT"
  elif test "x$OPENJDK_TARGET_OS" = xaix; then
    CFLAGS_OS_DEF_JVM="-DAIX"
  elif test "x$OPENJDK_TARGET_OS" = xbsd; then
    CFLAGS_OS_DEF_JDK="-D_ALLBSD_SOURCE"
  elif test "x$OPENJDK_TARGET_OS" = xwindows; then
    CFLAGS_OS_DEF_JVM="-D_WINDOWS -DWIN32 -D_JNI_IMPLEMENTATION_"
  fi

  # Setup target OS define. Use OS target name but in upper case.
  OPENJDK_TARGET_OS_UPPERCASE=`$ECHO $OPENJDK_TARGET_OS | $TR 'abcdefghijklmnopqrstuvwxyz' 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'`
  CFLAGS_OS_DEF_JDK="$CFLAGS_OS_DEF_JDK -D$OPENJDK_TARGET_OS_UPPERCASE"

  #### GLOBAL DEFINES
  # Set some common defines. These works for all compilers, but assume
  # -D is universally accepted.

  # Always enable optional macros for VM.
  ALWAYS_CFLAGS_JVM="-D__STDC_FORMAT_MACROS -D__STDC_LIMIT_MACROS -D__STDC_CONSTANT_MACROS"

  # Setup some hard coded includes
  ALWAYS_CFLAGS_JDK=" \
      -I\$(SUPPORT_OUTPUTDIR)/modules_include/java.base \
      -I\$(SUPPORT_OUTPUTDIR)/modules_include/java.base/\$(OPENJDK_TARGET_OS_INCLUDE_SUBDIR) \
      -I${TOPDIR}/src/java.base/share/native/libjava \
      -I${TOPDIR}/src/java.base/$OPENJDK_TARGET_OS_TYPE/native/libjava \
      -I${TOPDIR}/src/hotspot/share/include \
      -I${TOPDIR}/src/hotspot/os/${HOTSPOT_TARGET_OS_TYPE}/include"

  ###############################################################################

  # Adjust flags according to debug level.
  # Setup debug/release defines
  if test "x$DEBUG_LEVEL" = xrelease; then
    DEBUG_CFLAGS_JDK="-DNDEBUG"
    if test "x$OPENJDK_TARGET_OS" = xsolaris; then
      DEBUG_CFLAGS_JDK="$DEBUG_CFLAGS_JDK -DTRIMMED"
    fi
  else
    DEBUG_CFLAGS_JDK="-DDEBUG"

    if test "x$TOOLCHAIN_TYPE" = xxlc; then
      # We need '-qminimaltoc' or '-qpic=large -bbigtoc' if the TOC overflows.
      # Hotspot now overflows its 64K TOC (currently only for debug),
      # so for debug we build with '-qpic=large -bbigtoc'.
      DEBUG_CFLAGS_JVM="-qpic=large"
    fi
  fi

  if test "x$DEBUG_LEVEL" != xrelease; then
    DEBUG_SYMBOLS_CFLAGS_JDK="$CFLAGS_DEBUG_SYMBOLS $CFLAGS_DEBUG_OPTIONS"
  fi

  #### TOOLCHAIN DEFINES

  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    ALWAYS_DEFINES_JVM="-D_GNU_SOURCE -D_REENTRANT"
  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    ALWAYS_DEFINES_JVM="-D_GNU_SOURCE"
  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    ALWAYS_DEFINES_JVM="-DSPARC_WORKS -D_Crun_inline_placement"
    ALWAYS_DEFINES_JDK="-DTRACING -DMACRO_MEMSYS_OPS -DBREAKPTS"
    ALWAYS_DEFINES_JDK_CXXONLY="-DCC_NOEX"
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    ALWAYS_DEFINES_JVM="-D_REENTRANT"
    ALWAYS_DEFINES_JDK="-D_GNU_SOURCE -D_REENTRANT -D_LARGEFILE64_SOURCE -DSTDC"
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    ALWAYS_DEFINES_JDK="-DWIN32_LEAN_AND_MEAN -D_CRT_SECURE_NO_DEPRECATE \
        -D_CRT_NONSTDC_NO_DEPRECATE -DWIN32 -DIAL"
  fi


  ###############################################################################
  #
  #
  # CFLAGS BASIC
  if test "x$TOOLCHAIN_TYPE" = xgcc || test "x$TOOLCHAIN_TYPE" = xclang; then
    # COMMON to gcc and clang
    TOOLCHAIN_CFLAGS_JVM="-pipe -fno-rtti -fno-exceptions \
        -fvisibility=hidden -fno-strict-aliasing -fno-omit-frame-pointer"
  fi

  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    TOOLCHAIN_CFLAGS_JVM="$TOOLCHAIN_CFLAGS_JVM -fcheck-new"
    TOOLCHAIN_CFLAGS_JDK="-pipe"
    TOOLCHAIN_CFLAGS_JDK_CONLY="-fno-strict-aliasing" # technically NOT for CXX (but since this gives *worse* performance, use no-strict-aliasing everywhere!)

    CXXSTD_CXXFLAG="-std=gnu++98"
    FLAGS_CXX_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [$CXXSTD_CXXFLAG -Werror],
    						 IF_FALSE: [CXXSTD_CXXFLAG=""])
    TOOLCHAIN_CFLAGS_JDK_CXXONLY="$CXXSTD_CXXFLAG"
    TOOLCHAIN_CFLAGS_JVM="$TOOLCHAIN_CFLAGS_JVM $CXXSTD_CXXFLAG"
    ADLC_CXXFLAG="$CXXSTD_CXXFLAG"


  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    # Restrict the debug information created by Clang to avoid
    # too big object files and speed the build up a little bit
    # (see http://llvm.org/bugs/show_bug.cgi?id=7554)
    TOOLCHAIN_CFLAGS_JVM="$TOOLCHAIN_CFLAGS_JVM -flimit-debug-info"

    if test "x$OPENJDK_TARGET_OS" = xlinux; then
      TOOLCHAIN_CFLAGS_JDK="-pipe"
      TOOLCHAIN_CFLAGS_JDK_CONLY="-fno-strict-aliasing" # technically NOT for CXX
    fi
  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    TOOLCHAIN_CFLAGS_JDK="mt"
    TOOLCHAIN_CFLAGS_JDK_CONLY="-xc99=%none -xCC -Xa -v -W0,-noglobal" # C only
    TOOLCHAIN_CFLAGS_JDK_CXXONLY="-features=no%except -norunpath -xnolib" # CXX only
    TOOLCHAIN_CFLAGS_JVM="-template=no%extdef -features=no%split_init \
        -library=stlport4 -mt -features=no%except"
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    TOOLCHAIN_CFLAGS_JDK="-qchars=signed -qfullpath -qsaveopt"  # add on both CFLAGS
    TOOLCHAIN_CFLAGS_JVM="-qtune=balanced \
        -qalias=noansi -qstrict -qtls=default -qlanglvl=c99vla \
        -qlanglvl=noredefmac -qnortti -qnoeh -qignerrno"
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    TOOLCHAIN_CFLAGS_JVM="-nologo -MD -MP"
    TOOLCHAIN_CFLAGS_JDK="-MD -Zc:wchar_t-"
  fi

  # CFLAGS WARNINGS STUFF
  # Set JVM_CFLAGS warning handling
  if test "x$TOOLCHAIN_TYPE" = xgcc || test "x$TOOLCHAIN_TYPE" = xclang; then
    # COMMON to gcc and clang
    WARNING_CFLAGS_JVM="-Wpointer-arith -Wsign-compare -Wunused-function"
    if ! HOTSPOT_CHECK_JVM_VARIANT(zero); then
      # Non-zero builds have stricter warnings
      WARNING_CFLAGS_JVM="$WARNING_CFLAGS_JVM -Wundef -Wformat=2"
    fi

  fi
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    WARNING_CFLAGS_JDK="-Wall -Wextra -Wno-unused -Wno-unused-parameter -Wformat=2"
    WARNING_CFLAGS_JVM="$WARNING_CFLAGS_JVM -Wunused-value -Woverloaded-virtual"

    if ! HOTSPOT_CHECK_JVM_VARIANT(zero); then
      # Non-zero builds have stricter warnings
      WARNING_CFLAGS_JVM="$WARNING_CFLAGS_JVM -Wreturn-type"
    fi
  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    WARNING_CFLAGS_JVM="$WARNING_CFLAGS_JVM -Wno-deprecated"
    if test "x$OPENJDK_TARGET_OS" = xlinux; then
      WARNING_CFLAGS_JVM="$WARNING_CFLAGS_JVM -Wno-sometimes-uninitialized"
      WARNING_CFLAGS_JDK="-Wall -Wextra -Wno-unused -Wno-unused-parameter -Wformat=2"
    fi
  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    WARNING_CFLAGS_JDK_CONLY="-errshort=tags"
    WARNING_CFLAGS_JDK_CXXONLY="+w"
    WARNING_CFLAGS_JDK="-errtags=yes -errfmt"
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    WARNING_CFLAGS="-W3"
    WARNING_CFLAGS_JDK="-wd4800"
  fi

  # Set some additional per-OS defines.

  # Additional macosx handling
  if test "x$OPENJDK_TARGET_OS" = xmacosx; then
    # Let the flags variables get resolved in make for easier override on make
    # command line. AvailabilityMacros.h versions have no dots, ex: 1070.
    OS_CFLAGS="-DMAC_OS_X_VERSION_MIN_REQUIRED=\$(subst .,,\$(MACOSX_VERSION_MIN)) \
        -mmacosx-version-min=\$(MACOSX_VERSION_MIN)"

    if test -n "$MACOSX_VERSION_MAX"; then
        OS_CFLAGS="$OS_CFLAGS \
            -DMAC_OS_X_VERSION_MAX_ALLOWED=\$(subst .,,\$(MACOSX_VERSION_MAX))"
    fi
  fi

  # Where does this really belong??
  if test "x$TOOLCHAIN_TYPE" = xgcc || test "x$TOOLCHAIN_TYPE" = xclang; then
    PICFLAG="-fPIC"
  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    PICFLAG="-KPIC"
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
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    PICFLAG=""
  fi

  JVM_PICFLAG="$PICFLAG"
  JDK_PICFLAG="$PICFLAG"

  if test "x$OPENJDK_TARGET_OS" = xmacosx; then
    # Linking is different on MacOSX
    JDK_PICFLAG=''
    if test "x$STATIC_BUILD" = xtrue; then
      JVM_PICFLAG=""
    fi
  fi

  if test "x$OPENJDK_TARGET_OS" = xmacosx; then
    OS_CFLAGS_JVM="$OS_CFLAGS_JVM -mno-omit-leaf-frame-pointer -mstack-alignment=16"
  fi
  # Optional POSIX functionality needed by the VM

  if test "x$HAS_CLOCK_GETTIME" = "xtrue"; then
    OS_CFLAGS_JVM="$OS_CFLAGS_JVM -DSUPPORTS_CLOCK_MONOTONIC"
    if test "x$CLOCK_GETTIME_IN_LIBRT" = "xtrue"; then
      OS_CFLAGS_JVM="$OS_CFLAGS_JVM -DNEEDS_LIBRT"
    fi
  fi

  # EXPORT
  AC_SUBST(ADLC_CXXFLAG)
])

################################################################################
# $1 - Either BUILD or TARGET to pick the correct OS/CPU variables to check
#      conditionals against.
# $2 - Optional prefix for each variable defined.
AC_DEFUN([FLAGS_SETUP_COMPILER_FLAGS_FOR_JDK_CPU_DEP],
[
  #### CPU DEFINES, these should be independent on toolchain

  # Setup target CPU
  # Setup endianness
  # The macros _LITTLE/BIG_ENDIAN needs to be defined with = to avoid
  # sunstudio warning message: warning: macro redefined: _LITTLE_ENDIAN
  if test "x$FLAGS_CPU_ENDIAN" = xlittle; then
    $1_DEFINES_CPU_JVM="-DVM_LITTLE_ENDIAN"
    $1_DEFINES_CPU_JDK="-D_LITTLE_ENDIAN="
  else
    $1_DEFINES_CPU_JDK="-D_BIG_ENDIAN="
  fi

  # setup CPU bit size
  $1_DEFINES_CPU_JDK="${$1_DEFINES_CPU_JDK} -DARCH='\"$FLAGS_CPU_LEGACY\"' \
      -D$FLAGS_CPU_LEGACY"

  if test "x$FLAGS_CPU_BITS" = x64; then
    # -D_LP64=1 is only set on linux and mac. Setting on windows causes diff in
    # unpack200.exe.
    if test "x$FLAGS_OS" = xlinux || test "x$FLAGS_OS" = xmacosx; then
      $1_DEFINES_CPU_JDK="${$1_DEFINES_CPU_JDK} -D_LP64=1"
    fi
    if test "x$FLAGS_OS" != xsolaris && test "x$FLAGS_OS" != xaix; then
      # Solaris does not have _LP64=1 in the old build.
      # xlc on AIX defines _LP64=1 by default and issues a warning if we redefine it.
      $1_DEFINES_CPU_JVM="${$1_DEFINES_CPU_JVM} -D_LP64=1"
    fi
  fi

  # toolchain dependend, per-cpu
  if test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    if test "x$FLAGS_CPU_ARCH" = xx86; then
      $1_DEFINES_CPU_JDK="${$1_DEFINES_CPU_JDK} -DcpuIntel -Di586 -D$FLAGS_CPU_LEGACY_LIB"
    fi
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    if test "x$FLAGS_CPU" = xx86_64; then
      $1_DEFINES_CPU_JDK="${$1_DEFINES_CPU_JDK} -D_AMD64_ -Damd64"
    else
      $1_DEFINES_CPU_JDK="${$1_DEFINES_CPU_JDK} -D_X86_ -Dx86"
    fi
  fi

  # CFLAGS PER CPU
  if test "x$TOOLCHAIN_TYPE" = xgcc || test "x$TOOLCHAIN_TYPE" = xclang; then
    # COMMON to gcc and clang
    if test "x$FLAGS_CPU" = xx86; then
      # Force compatibility with i586 on 32 bit intel platforms.
      $1_CFLAGS_CPU="-march=i586"
    fi
  fi

  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    if test "x$FLAGS_CPU" = xarm; then
      # -Wno-psabi to get rid of annoying "note: the mangling of 'va_list' has changed in GCC 4.4"
      $1_CFLAGS_CPU="-fsigned-char -Wno-psabi $ARM_ARCH_TYPE_FLAGS $ARM_FLOAT_TYPE_FLAGS -DJDK_ARCH_ABI_PROP_NAME='\"\$(JDK_ARCH_ABI_PROP_NAME)\"'"
      $1_CFLAGS_CPU_JVM="-DARM"
    elif test "x$FLAGS_CPU" = xaarch64; then
      if test "x$HOTSPOT_TARGET_CPU_PORT" = xarm64; then
        $1_CFLAGS_CPU_JVM="-fsigned-char -DARM"
      fi
    elif test "x$FLAGS_CPU_ARCH" = xppc; then
      $1_CFLAGS_CPU_JVM="-minsert-sched-nops=regroup_exact -mno-multiple -mno-string"
      if test "x$FLAGS_CPU" = xppc64; then
        # -mminimal-toc fixes `relocation truncated to fit' error for gcc 4.1.
        # Use ppc64 instructions, but schedule for power5
        $1_CFLAGS_CPU_JVM="${$1_CFLAGS_CPU_JVM} -mminimal-toc -mcpu=powerpc64 -mtune=power5"
      elif test "x$FLAGS_CPU" = xppc64le; then
        # Little endian machine uses ELFv2 ABI.
        # Use Power8, this is the first CPU to support PPC64 LE with ELFv2 ABI.
        $1_CFLAGS_CPU_JVM="${$1_CFLAGS_CPU_JVM} -DABI_ELFv2 -mcpu=power8 -mtune=power8"
      fi
    elif test "x$FLAGS_CPU" = xs390x; then
      $1_CFLAGS_CPU="-mbackchain -march=z10"
    fi

    if test "x$FLAGS_CPU_ARCH" != xarm &&  test "x$FLAGS_CPU_ARCH" != xppc; then
      # for all archs except arm and ppc, prevent gcc to omit frame pointer
      $1_CFLAGS_CPU_JDK="${$1_CFLAGS_CPU_JDK} -fno-omit-frame-pointer"
    fi

  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    if test "x$FLAGS_OS" = xlinux; then
      # ppc test not really needed for clang
      if test "x$FLAGS_CPU_ARCH" != xarm &&  test "x$FLAGS_CPU_ARCH" != xppc; then
        # for all archs except arm and ppc, prevent gcc to omit frame pointer
        $1_CFLAGS_CPU_JDK="${$1_CFLAGS_CPU_JDK} -fno-omit-frame-pointer"
      fi
    fi

  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    if test "x$FLAGS_CPU" = xx86_64; then
      # NOTE: -xregs=no%frameptr is supposed to be default on x64
      $1_CFLAGS_CPU_JDK="-xregs=no%frameptr"
    elif test "x$FLAGS_CPU" = xsparcv9; then
      $1_CFLAGS_CPU_JVM="-xarch=sparc"
      $1_CFLAGS_CPU_JDK="-xregs=no%appl"
    fi

  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    if test "x$FLAGS_CPU" = xppc64; then
      $1_CFLAGS_CPU_JVM="-qarch=ppc64"
    fi

  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    if test "x$FLAGS_CPU" = xx86; then
      $1_CFLAGS_CPU_JVM="-arch:IA32"
    fi
  fi

  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    TOOLCHAIN_CHECK_COMPILER_VERSION(VERSION: 6, PREFIX: $2, IF_AT_LEAST: FLAGS_SETUP_GCC6_COMPILER_FLAGS($1))
    $1_TOOLCHAIN_CFLAGS="${$1_GCC6_CFLAGS}"

    TOOLCHAIN_CHECK_COMPILER_VERSION(VERSION: [4.8], PREFIX: $2,
        IF_AT_LEAST: [
          # These flags either do not work or give spurious warnings prior to gcc 4.8.
          $1_WARNING_CFLAGS_JVM="-Wno-format-zero-length -Wtype-limits -Wuninitialized"
        ]
    )
  fi

  # EXPORT to API
  CFLAGS_JVM_COMMON="$ALWAYS_CFLAGS_JVM $ALWAYS_DEFINES_JVM $TOOLCHAIN_CFLAGS_JVM \
      $OS_CFLAGS $OS_CFLAGS_JVM $CFLAGS_OS_DEF_JVM $DEBUG_CFLAGS_JVM \
      $WARNING_CFLAGS $WARNING_CFLAGS_JVM $JVM_PICFLAG"

  CFLAGS_JDK_COMMON="$ALWAYS_CFLAGS_JDK $ALWAYS_DEFINES_JDK $TOOLCHAIN_CFLAGS_JDK \
      $OS_CFLAGS $CFLAGS_OS_DEF_JDK $DEBUG_CFLAGS_JDK $DEBUG_SYMBOLS_CFLAGS_JDK \
      $WARNING_CFLAGS $WARNING_CFLAGS_JDK $EXTRA_CXXFLAGS"

  CFLAGS_JDK_COMMON_CONLY="$TOOLCHAIN_CFLAGS_JDK_CONLY $WARNING_CFLAGS_JDK_CONLY $EXTRA_CFLAGS"
  CFLAGS_JDK_COMMON_CXXONLY="$ALWAYS_DEFINES_JDK_CXXONLY $TOOLCHAIN_CFLAGS_JDK_CXXONLY \
      $WARNING_CFLAGS_JDK_CXXONLY $EXTRA_CXXFLAGS"

  $1_CFLAGS_JVM="${$1_DEFINES_CPU_JVM} ${$1_CFLAGS_CPU} ${$1_CFLAGS_CPU_JVM} ${$1_TOOLCHAIN_CFLAGS} ${$1_WARNING_CFLAGS_JVM}"
  $1_CFLAGS_JDK="${$1_DEFINES_CPU_JDK} ${$1_CFLAGS_CPU} ${$1_CFLAGS_CPU_JDK} ${$1_TOOLCHAIN_CFLAGS}"

  $2JVM_CFLAGS="$CFLAGS_JVM_COMMON ${$1_CFLAGS_JVM}"

  $2CFLAGS_JDKEXE="$CFLAGS_JDK_COMMON $CFLAGS_JDK_COMMON_CONLY ${$1_CFLAGS_JDK}"
  $2CXXFLAGS_JDKEXE="$CFLAGS_JDK_COMMON $CFLAGS_JDK_COMMON_CXXONLY ${$1_CFLAGS_JDK}"
  $2CFLAGS_JDKLIB="${$2CFLAGS_JDKEXE} $JDK_PICFLAG"
  $2CXXFLAGS_JDKLIB="${$2CXXFLAGS_JDKEXE} $JDK_PICFLAG"

  AC_SUBST($2JVM_CFLAGS)
  AC_SUBST($2CFLAGS_JDKLIB)
  AC_SUBST($2CFLAGS_JDKEXE)
  AC_SUBST($2CXXFLAGS_JDKLIB)
  AC_SUBST($2CXXFLAGS_JDKEXE)
])

# FLAGS_C_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [ARGUMENT], IF_TRUE: [RUN-IF-TRUE],
#                                  IF_FALSE: [RUN-IF-FALSE])
# ------------------------------------------------------------
# Check that the C compiler supports an argument
BASIC_DEFUN_NAMED([FLAGS_C_COMPILER_CHECK_ARGUMENTS],
    [*ARGUMENT IF_TRUE IF_FALSE], [$@],
[
  AC_MSG_CHECKING([if the C compiler supports "ARG_ARGUMENT"])
  supports=yes

  saved_cflags="$CFLAGS"
  CFLAGS="$CFLAGS ARG_ARGUMENT"
  AC_LANG_PUSH([C])
  AC_COMPILE_IFELSE([AC_LANG_SOURCE([[int i;]])], [],
      [supports=no])
  AC_LANG_POP([C])
  CFLAGS="$saved_cflags"

  AC_MSG_RESULT([$supports])
  if test "x$supports" = "xyes" ; then
    :
    ARG_IF_TRUE
  else
    :
    ARG_IF_FALSE
  fi
])

# FLAGS_CXX_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [ARGUMENT], IF_TRUE: [RUN-IF-TRUE],
#                                    IF_FALSE: [RUN-IF-FALSE])
# ------------------------------------------------------------
# Check that the C++ compiler supports an argument
BASIC_DEFUN_NAMED([FLAGS_CXX_COMPILER_CHECK_ARGUMENTS],
    [*ARGUMENT IF_TRUE IF_FALSE], [$@],
[
  AC_MSG_CHECKING([if the C++ compiler supports "ARG_ARGUMENT"])
  supports=yes

  saved_cxxflags="$CXXFLAGS"
  CXXFLAGS="$CXXFLAG ARG_ARGUMENT"
  AC_LANG_PUSH([C++])
  AC_COMPILE_IFELSE([AC_LANG_SOURCE([[int i;]])], [],
      [supports=no])
  AC_LANG_POP([C++])
  CXXFLAGS="$saved_cxxflags"

  AC_MSG_RESULT([$supports])
  if test "x$supports" = "xyes" ; then
    :
    ARG_IF_TRUE
  else
    :
    ARG_IF_FALSE
  fi
])

# FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [ARGUMENT], IF_TRUE: [RUN-IF-TRUE],
#                                IF_FALSE: [RUN-IF-FALSE])
# ------------------------------------------------------------
# Check that the C and C++ compilers support an argument
BASIC_DEFUN_NAMED([FLAGS_COMPILER_CHECK_ARGUMENTS],
    [*ARGUMENT IF_TRUE IF_FALSE], [$@],
[
  FLAGS_C_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [ARG_ARGUMENT],
  					     IF_TRUE: [C_COMP_SUPPORTS="yes"],
					     IF_FALSE: [C_COMP_SUPPORTS="no"])
  FLAGS_CXX_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [ARG_ARGUMENT],
  					       IF_TRUE: [CXX_COMP_SUPPORTS="yes"],
					       IF_FALSE: [CXX_COMP_SUPPORTS="no"])

  AC_MSG_CHECKING([if both compilers support "ARG_ARGUMENT"])
  supports=no
  if test "x$C_COMP_SUPPORTS" = "xyes" -a "x$CXX_COMP_SUPPORTS" = "xyes"; then supports=yes; fi

  AC_MSG_RESULT([$supports])
  if test "x$supports" = "xyes" ; then
    :
    ARG_IF_TRUE
  else
    :
    ARG_IF_FALSE
  fi
])

# FLAGS_LINKER_CHECK_ARGUMENTS(ARGUMENT: [ARGUMENT], IF_TRUE: [RUN-IF-TRUE],
#                                   IF_FALSE: [RUN-IF-FALSE])
# ------------------------------------------------------------
# Check that the linker support an argument
BASIC_DEFUN_NAMED([FLAGS_LINKER_CHECK_ARGUMENTS],
    [*ARGUMENT IF_TRUE IF_FALSE], [$@],
[
  AC_MSG_CHECKING([if linker supports "ARG_ARGUMENT"])
  supports=yes

  saved_ldflags="$LDFLAGS"
  LDFLAGS="$LDFLAGS ARG_ARGUMENT"
  AC_LANG_PUSH([C])
  AC_LINK_IFELSE([AC_LANG_PROGRAM([[]],[[]])],
      [], [supports=no])
  AC_LANG_POP([C])
  LDFLAGS="$saved_ldflags"

  AC_MSG_RESULT([$supports])
  if test "x$supports" = "xyes" ; then
    :
    ARG_IF_TRUE
  else
    :
    ARG_IF_FALSE
  fi
])

AC_DEFUN_ONCE([FLAGS_SETUP_COMPILER_FLAGS_MISC],
[
  # Check that the compiler supports -mX (or -qX on AIX) flags
  # Set COMPILER_SUPPORTS_TARGET_BITS_FLAG to 'true' if it does
  FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [${COMPILER_TARGET_BITS_FLAG}${OPENJDK_TARGET_CPU_BITS}],
      IF_TRUE: [COMPILER_SUPPORTS_TARGET_BITS_FLAG=true],
      IF_FALSE: [COMPILER_SUPPORTS_TARGET_BITS_FLAG=false])
  AC_SUBST(COMPILER_SUPPORTS_TARGET_BITS_FLAG)

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
])

# FLAGS_SETUP_GCC6_COMPILER_FLAGS([PREFIX])
# Arguments:
# $1 - Prefix for each variable defined.
AC_DEFUN([FLAGS_SETUP_GCC6_COMPILER_FLAGS],
[
  # These flags are required for GCC 6 builds as undefined behaviour in OpenJDK code
  # runs afoul of the more aggressive versions of these optimisations.
  # Notably, value range propagation now assumes that the this pointer of C++
  # member functions is non-null.
  NO_DELETE_NULL_POINTER_CHECKS_CFLAG="-fno-delete-null-pointer-checks"
  dnl Argument check is disabled until FLAGS_COMPILER_CHECK_ARGUMENTS handles cross-compilation
  dnl FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [$NO_DELETE_NULL_POINTER_CHECKS_CFLAG -Werror],
  dnl					     IF_FALSE: [NO_DELETE_NULL_POINTER_CHECKS_CFLAG=""])
  NO_LIFETIME_DSE_CFLAG="-fno-lifetime-dse"
  dnl Argument check is disabled until FLAGS_COMPILER_CHECK_ARGUMENTS handles cross-compilation
  dnl FLAGS_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [$NO_LIFETIME_DSE_CFLAG -Werror],
  dnl					     IF_FALSE: [NO_LIFETIME_DSE_CFLAG=""])
  AC_MSG_NOTICE([GCC >= 6 detected; adding ${NO_DELETE_NULL_POINTER_CHECKS_CFLAG} and ${NO_LIFETIME_DSE_CFLAG}])
  $1_GCC6_CFLAGS="${NO_DELETE_NULL_POINTER_CHECKS_CFLAG} ${NO_LIFETIME_DSE_CFLAG}"
])
