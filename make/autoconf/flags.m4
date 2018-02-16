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

    # Pass these on to the open part of configure as if they were set using
    # --with-extra-c[xx]flags.
    EXTRA_CFLAGS="$EXTRA_CFLAGS $ARM_ARCH_TYPE_FLAGS $ARM_FLOAT_TYPE_FLAGS"
    EXTRA_CXXFLAGS="$EXTRA_CXXFLAGS $ARM_ARCH_TYPE_FLAGS $ARM_FLOAT_TYPE_FLAGS"
    # Get rid of annoying "note: the mangling of 'va_list' has changed in GCC 4.4"
    # FIXME: This should not really be set using extra_cflags.
    if test "x$OPENJDK_TARGET_CPU" = xarm; then
        EXTRA_CFLAGS="$EXTRA_CFLAGS -Wno-psabi"
        EXTRA_CXXFLAGS="$EXTRA_CXXFLAGS -Wno-psabi"
    fi
    # Also add JDK_ARCH_ABI_PROP_NAME define, but only to CFLAGS.
    EXTRA_CFLAGS="$EXTRA_CFLAGS -DJDK_ARCH_ABI_PROP_NAME='\"\$(JDK_ARCH_ABI_PROP_NAME)\"'"
    # And pass the architecture flags to the linker as well
    EXTRA_LDFLAGS="$EXTRA_LDFLAGS $ARM_ARCH_TYPE_FLAGS $ARM_FLOAT_TYPE_FLAGS"
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
  if test "x$CFLAGS" != "x${ADDED_CFLAGS}"; then
    AC_MSG_WARN([Ignoring CFLAGS($CFLAGS) found in environment. Use --with-extra-cflags])
  fi

  if test "x$CXXFLAGS" != "x${ADDED_CXXFLAGS}"; then
    AC_MSG_WARN([Ignoring CXXFLAGS($CXXFLAGS) found in environment. Use --with-extra-cxxflags])
  fi

  if test "x$LDFLAGS" != "x${ADDED_LDFLAGS}"; then
    AC_MSG_WARN([Ignoring LDFLAGS($LDFLAGS) found in environment. Use --with-extra-ldflags])
  fi

  AC_ARG_WITH(extra-cflags, [AS_HELP_STRING([--with-extra-cflags],
      [extra flags to be used when compiling jdk c-files])])

  AC_ARG_WITH(extra-cxxflags, [AS_HELP_STRING([--with-extra-cxxflags],
      [extra flags to be used when compiling jdk c++-files])])

  AC_ARG_WITH(extra-ldflags, [AS_HELP_STRING([--with-extra-ldflags],
      [extra flags to be used when linking jdk])])

  EXTRA_CFLAGS="$with_extra_cflags"
  EXTRA_CXXFLAGS="$with_extra_cxxflags"
  EXTRA_LDFLAGS="$with_extra_ldflags"

  AC_SUBST(EXTRA_CFLAGS)
  AC_SUBST(EXTRA_CXXFLAGS)
  AC_SUBST(EXTRA_LDFLAGS)

  # The global CFLAGS and LDLAGS variables are used by configure tests and
  # should include the extra parameters
  CFLAGS="$EXTRA_CFLAGS"
  CXXFLAGS="$EXTRA_CXXFLAGS"
  LDFLAGS="$EXTRA_LDFLAGS"
  CPPFLAGS=""
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
      fi
    elif test "x$TOOLCHAIN_TYPE" = xgcc; then
      $1SYSROOT_CFLAGS="--sysroot=[$]$1SYSROOT"
      $1SYSROOT_LDFLAGS="--sysroot=[$]$1SYSROOT"
    elif test "x$TOOLCHAIN_TYPE" = xclang; then
      $1SYSROOT_CFLAGS="-isysroot [$]$1SYSROOT"
      $1SYSROOT_LDFLAGS="-isysroot [$]$1SYSROOT"
    fi
    # The global CFLAGS and LDFLAGS variables need these for configure to function
    $1CFLAGS="[$]$1CFLAGS [$]$1SYSROOT_CFLAGS"
    $1CPPFLAGS="[$]$1CPPFLAGS [$]$1SYSROOT_CFLAGS"
    $1CXXFLAGS="[$]$1CXXFLAGS [$]$1SYSROOT_CFLAGS"
    $1LDFLAGS="[$]$1LDFLAGS [$]$1SYSROOT_LDFLAGS"
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
])

AC_DEFUN([FLAGS_SETUP_COMPILER_FLAGS_FOR_LIBS],
[
  FLAGS_SETUP_SHARED_LIBS

  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    if test "x$OPENJDK_TARGET_OS" = xmacosx; then
      if test "x$STATIC_BUILD" = xfalse; then
        JVM_CFLAGS="$JVM_CFLAGS $PICFLAG"
      fi
    fi
  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    if test "x$OPENJDK_TARGET_OS" = xmacosx; then
      if test "x$STATIC_BUILD" = xfalse; then
        JVM_CFLAGS="$JVM_CFLAGS -fPIC"
      fi
    fi
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    JVM_CFLAGS="$JVM_CFLAGS $PICFLAG"
  fi


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

  FLAGS_OS=OPENJDK_TARGET_OS
  FLAGS_OS_TYPE=OPENJDK_TARGET_OS_TYPE
  FLAGS_CPU=OPENJDK_TARGET_CPU
  FLAGS_CPU_ARCH=OPENJDK_TARGET_CPU_ARCH
  FLAGS_CPU_BITS=OPENJDK_TARGET_CPU_BITS
  FLAGS_CPU_ENDIAN=OPENJDK_TARGET_CPU_ENDIAN
  FLAGS_CPU_LEGACY=OPENJDK_TARGET_CPU_LEGACY
  FLAGS_ADD_LP64=OPENJDK_TARGET_ADD_LP64

  # On some platforms (mac) the linker warns about non existing -L dirs.
  # For any of the variants server, client or minimal, the dir matches the
  # variant name. The "main" variant should be used for linking. For the
  # rest, the dir is just server.
  if HOTSPOT_CHECK_JVM_VARIANT(server) || HOTSPOT_CHECK_JVM_VARIANT(client) \
      || HOTSPOT_CHECK_JVM_VARIANT(minimal); then
    JVM_VARIANT_PATH=$JVM_VARIANT_MAIN
  else
    JVM_VARIANT_PATH=server
  fi

  FLAGS_SETUP_COMPILER_FLAGS_FOR_JDK_HELPER([TARGET])

  FLAGS_OS=OPENJDK_BUILD_OS
  FLAGS_OS_TYPE=OPENJDK_BUILD_OS_TYPE
  FLAGS_CPU=OPENJDK_BUILD_CPU
  FLAGS_CPU_ARCH=OPENJDK_BUILD_CPU_ARCH
  FLAGS_CPU_BITS=OPENJDK_BUILD_CPU_BITS
  FLAGS_CPU_ENDIAN=OPENJDK_BUILD_CPU_ENDIAN
  FLAGS_CPU_LEGACY=OPENJDK_BUILD_CPU_LEGACY
  FLAGS_ADD_LP64=OPENJDK_BUILD_ADD_LP64=

  # When building a buildjdk, it's always only the server variant
  OPENJDK_BUILD_JVM_VARIANT_PATH=server

  FLAGS_SETUP_COMPILER_FLAGS_FOR_JDK_HELPER([BUILD], [OPENJDK_BUILD_])

  # Tests are only ever compiled for TARGET
  # Flags for compiling test libraries
  CFLAGS_TESTLIB="$COMMON_CCXXFLAGS_JDK $CFLAGS_JDK $PICFLAG ${_SPECIAL_EXTRA_1} ${_SPECIAL_EXTRA_2}"
  CXXFLAGS_TESTLIB="$COMMON_CCXXFLAGS_JDK $CXXFLAGS_JDK $PICFLAG ${_SPECIAL_EXTRA_1} ${_SPECIAL_EXTRA_2}"

  # Flags for compiling test executables
  CFLAGS_TESTEXE="$COMMON_CCXXFLAGS_JDK $CFLAGS_JDK"
  CXXFLAGS_TESTEXE="$COMMON_CCXXFLAGS_JDK $CXXFLAGS_JDK"

  AC_SUBST(CFLAGS_TESTLIB)
  AC_SUBST(CFLAGS_TESTEXE)
  AC_SUBST(CXXFLAGS_TESTLIB)
  AC_SUBST(CXXFLAGS_TESTEXE)

  LDFLAGS_TESTLIB="$LDFLAGS_JDKLIB"
  LDFLAGS_TESTEXE="$LDFLAGS_JDKEXE $JAVA_BASE_LDFLAGS"

  AC_SUBST(LDFLAGS_TESTLIB)
  AC_SUBST(LDFLAGS_TESTEXE)

])

################################################################################
# $1 - Either BUILD or TARGET to pick the correct OS/CPU variables to check
#      conditionals against.
# $2 - Optional prefix for each variable defined.
AC_DEFUN([FLAGS_SETUP_COMPILER_FLAGS_FOR_JDK_HELPER],
[
  # Special extras...
  if test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    if test "x$FLAGS_CPU_ARCH" = "xsparc"; then
      $2_SPECIAL_EXTRA_1="-xregs=no%appl" # add on both EXTRA
    fi
    $2_SPECIAL_EXTRA_2="-errtags=yes -errfmt" # add on both EXTRA
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    $2_SPECIAL_1="-qchars=signed -qfullpath -qsaveopt"  # add on both CFLAGS
    $2CFLAGS_JDK="${$2CFLAGS_JDK} ${$2_SPECIAL_1}"
    $2CXXFLAGS_JDK="${$2CXXFLAGS_JDK} ${$2_SPECIAL_1}"
  elif test "x$TOOLCHAIN_TYPE" = xgcc; then
    $2CXXSTD_CXXFLAG="-std=gnu++98" # only for CXX and JVM
    FLAGS_CXX_COMPILER_CHECK_ARGUMENTS(ARGUMENT: [[$]$2CXXSTD_CXXFLAG -Werror],
    						 IF_FALSE: [$2CXXSTD_CXXFLAG=""])
    $2CXXFLAGS_JDK="${$2CXXFLAGS_JDK} ${$2CXXSTD_CXXFLAG}"
    $2JVM_CFLAGS="${$2JVM_CFLAGS} ${$2CXXSTD_CXXFLAG}"
    AC_SUBST($2CXXSTD_CXXFLAG)
  fi
  if test "x$OPENJDK_TARGET_OS" = xsolaris; then
    $2_SPECIAL_2="-D__solaris__"  # add on both CFLAGS
    $2CFLAGS_JDK="${$2CFLAGS_JDK} ${$2_SPECIAL_2}"
    $2CXXFLAGS_JDK="${$2CXXFLAGS_JDK} ${$2_SPECIAL_2}"
  fi

  $2CFLAGS_JDK="${$2CFLAGS_JDK} ${$2EXTRA_CFLAGS}"
  $2CXXFLAGS_JDK="${$2CXXFLAGS_JDK} ${$2EXTRA_CXXFLAGS}"
  $2LDFLAGS_JDK="${$2LDFLAGS_JDK} ${$2EXTRA_LDFLAGS}"


  # PER TOOLCHAIN:
    # LINKER_BASIC
    # LINKER: shared-lib-special, exe-special
    # LINKER: arch/cpu-special
  # COMPILER-ARCH/CPU-special (-m, -f...)
  # COMPILER-warnings
  # COMPILER: shared-lib-special, exe-special

  # LINKER LDFLAGS

  # BASIC_LDFLAGS (per toolchain)
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    # If this is a --hash-style=gnu system, use --hash-style=both, why?
    # We have previously set HAS_GNU_HASH if this is the case
    if test -n "$HAS_GNU_HASH"; then
      BASIC_LDFLAGS="-Wl,--hash-style=both"
      LIBJSIG_HASHSTYLE_LDFLAGS="-Wl,--hash-style=both"
      AC_SUBST(LIBJSIG_HASHSTYLE_LDFLAGS)
    fi

    # And since we now know that the linker is gnu, then add -z defs, to forbid
    # undefined symbols in object files.
    BASIC_LDFLAGS="$BASIC_LDFLAGS -Wl,-z,defs"

    BASIC_LDFLAGS_JDK_ONLY=
    BASIC_LDFLAGS_JVM_ONLY="-Wl,-z,noexecstack -Wl,-O1"

    BASIC_LDFLAGS_JDK_LIB_ONLY="-Wl,-z,noexecstack"
    LIBJSIG_NOEXECSTACK_LDFLAGS="-Wl,-z,noexecstack"


    if test "x$HAS_LINKER_RELRO" = "xtrue"; then
      BASIC_LDFLAGS_JVM_ONLY="$BASIC_LDFLAGS_JVM_ONLY $LINKER_RELRO_FLAG"
    fi

  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    BASIC_LDFLAGS_JVM_ONLY="-mno-omit-leaf-frame-pointer -mstack-alignment=16 -stdlib=libstdc++ -fPIC"
  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    BASIC_LDFLAGS="-Wl,-z,defs"
    BASIC_LDFLAGS_ONLYCXX="-norunpath"
    BASIC_LDFLAGS_ONLYCXX_JDK_ONLY="-xnolib"

    BASIC_LDFLAGS_JDK_ONLY="-ztext"
    BASIC_LDFLAGS_JVM_ONLY="-library=%none -mt -z noversion"
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    BASIC_LDFLAGS="-b64 -brtl -bnolibpath -bexpall -bernotok -btextpsize:64K -bdatapsize:64K -bstackpsize:64K"
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    BASIC_LDFLAGS="-nologo -opt:ref"
    BASIC_LDFLAGS_JDK_ONLY="-incremental:no"
    BASIC_LDFLAGS_JVM_ONLY="-opt:icf,8 -subsystem:windows -base:0x8000000"
  fi
  $2LDFLAGS_JDK="${$2LDFLAGS_JDK} ${BASIC_LDFLAGS}"
  $2JVM_LDFLAGS="${$2JVM_LDFLAGS} ${BASIC_LDFLAGS} $BASIC_LDFLAGS_ONLYCXX"
  $2LDFLAGS_CXX_JDK="[$]$2LDFLAGS_CXX_JDK $BASIC_LDFLAGS_ONLYCXX"

  # CPU_LDFLAGS (per toolchain)
  # This can differ between TARGET and BUILD.
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
      if test "x$FLAGS_CPU" = xx86; then
        $2_CPU_LDFLAGS_JVM_ONLY="-march=i586"
      fi
  elif test "x$TOOLCHAIN_TYPE" = xclang; then
  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    if test "x$FLAGS_CPU_ARCH" = "xsparc"; then
      $2_CPU_LDFLAGS_JVM_ONLY="-xarch=sparc"
    fi
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    if test "x$FLAGS_CPU" = "xx86"; then
      $2_CPU_LDFLAGS="-safeseh"
      # NOTE: Old build added -machine. Probably not needed.
      $2_CPU_LDFLAGS_JVM_ONLY="-machine:I386"
    else
      $2_CPU_LDFLAGS_JVM_ONLY="-machine:AMD64"
    fi
    fi
  fi
  $2LDFLAGS_JDK="${$2LDFLAGS_JDK} ${$2_CPU_LDFLAGS}"
  $2JVM_LDFLAGS="${$2JVM_LDFLAGS} ${$2_CPU_LDFLAGS} ${$2_CPU_LDFLAGS_JVM_ONLY}"

  # OS_LDFLAGS (per toolchain)
  if test "x$TOOLCHAIN_TYPE" = xclang || test "x$TOOLCHAIN_TYPE" = xgcc; then
    if test "x$FLAGS_OS" = xmacosx; then
      # Assume clang or gcc.
      # FIXME: We should really generalize SET_SHARED_LIBRARY_ORIGIN instead.
      OS_LDFLAGS_JVM_ONLY="[$]$2JVM_LDFLAGS -Wl,-rpath,@loader_path/. -Wl,-rpath,@loader_path/.."

      OS_LDFLAGS_JDK_ONLY="[$]$2LDFLAGS_JDK -mmacosx-version-min=\$(MACOSX_VERSION_MIN)"
    fi
  fi
  $2LDFLAGS_JDK="${$2LDFLAGS_JDK} ${$OS_LDFLAGS_JDK_ONLY}"
  $2JVM_LDFLAGS="${$2JVM_LDFLAGS} ${$OS_LDFLAGS_JVM_ONLY}"

  # DEBUGLEVEL_LDFLAGS (per toolchain)
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    if test "x$FLAGS_OS" = xlinux; then
       if test x$DEBUG_LEVEL = xrelease; then
          DEBUGLEVEL_LDFLAGS_JDK_ONLY="${$2LDFLAGS_JDK} -Wl,-O1"
       else
          # mark relocations read only on (fast/slow) debug builds
          if test "x$HAS_LINKER_RELRO" = "xtrue"; then
            DEBUGLEVEL_LDFLAGS_JDK_ONLY="$LINKER_RELRO_FLAG"
          fi
       fi
       if test x$DEBUG_LEVEL = xslowdebug; then
          if test "x$HAS_LINKER_NOW" = "xtrue"; then
            # do relocations at load
            DEBUGLEVEL_LDFLAGS="$LINKER_NOW_FLAG"
          fi
       fi
    fi
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    # We need '-qminimaltoc' or '-qpic=large -bbigtoc' if the TOC overflows.
    # Hotspot now overflows its 64K TOC (currently only for debug),
    # so we build with '-qpic=large -bbigtoc'.
    if test "x$DEBUG_LEVEL" != xrelease; then
      DEBUGLEVEL_LDFLAGS_JVM_ONLY="[$]$2JVM_LDFLAGS -bbigtoc"
    fi
  fi

  # EXECUTABLE_LDFLAGS (per toolchain)
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    EXECUTABLE_LDFLAGS="[$]$2LDFLAGS_JDKEXE -Wl,--allow-shlib-undefined"
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    if test "x$FLAGS_CPU" = "x86_64"; then
      LDFLAGS_STACK_SIZE=1048576
    else
      LDFLAGS_STACK_SIZE=327680
    fi
    $2_CPU_EXECUTABLE_LDFLAGS="${$2LDFLAGS_JDKEXE} -stack:$LDFLAGS_STACK_SIZE"
  fi
  $2LDFLAGS_JDKEXE="${$2LDFLAGS_JDK}"
  $2LDFLAGS_JDKEXE="${$2LDFLAGS_JDKEXE} ${$2EXTRA_LDFLAGS_JDK}"

  # LIBRARY_LDFLAGS (per toolchain)
  # Customize LDFLAGS for libs
  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    LDFLAGS_JDK_LIBPATH="-libpath:${OUTPUTDIR}/support/modules_libs/java.base"
  else
    LDFLAGS_JDK_LIBPATH="-L\$(SUPPORT_OUTPUTDIR)/modules_libs/java.base \
        -L\$(SUPPORT_OUTPUTDIR)/modules_libs/java.base/${$2JVM_VARIANT_PATH}"
  fi

  LIBRARY_LDFLAGS_JDK_ONLY="${SHARED_LIBRARY_FLAGS} ${LDFLAGS_NO_EXEC_STACK} $LDFLAGS_JDK_LIBPATH ${$2EXTRA_LDFLAGS_JDK} $BASIC_LDFLAGS_JDK_LIB_ONLY"
  $2LDFLAGS_JDKLIB="${LIBRARY_LDFLAGS_JDK_ONLY} "

  # PER OS?
    # LIBS: default libs

  ###############################################################################
  #
  # Now setup the CFLAGS and LDFLAGS for the JDK build.
  # Later we will also have CFLAGS and LDFLAGS for the hotspot subrepo build.
  #

  # Setup compiler/platform specific flags into
  #    $2CFLAGS_JDK    - C Compiler flags
  #    $2CXXFLAGS_JDK  - C++ Compiler flags
  #    $2COMMON_CCXXFLAGS_JDK - common to C and C++
  if test "x$TOOLCHAIN_TYPE" = xgcc; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D_GNU_SOURCE"
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D_REENTRANT"
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -fcheck-new"
    if test "x$FLAGS_CPU" = xx86; then
      # Force compatibility with i586 on 32 bit intel platforms.
      $2COMMON_CCXXFLAGS="${$2COMMON_CCXXFLAGS} -march=i586"
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -march=i586"
    fi
    $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS [$]$2COMMON_CCXXFLAGS_JDK -Wall -Wextra -Wno-unused -Wno-unused-parameter -Wformat=2 \
        -pipe -D_GNU_SOURCE -D_REENTRANT -D_LARGEFILE64_SOURCE"
    case $FLAGS_CPU_ARCH in
      arm )
        # on arm we don't prevent gcc to omit frame pointer but do prevent strict aliasing
        $2CFLAGS_JDK="${$2CFLAGS_JDK} -fno-strict-aliasing"
        $2COMMON_CCXXFLAGS_JDK="${$2COMMON_CCXXFLAGS_JDK} -fsigned-char"
        ;;
      ppc )
        # on ppc we don't prevent gcc to omit frame pointer but do prevent strict aliasing
        $2CFLAGS_JDK="${$2CFLAGS_JDK} -fno-strict-aliasing"
        ;;
      s390 )
        $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -fno-omit-frame-pointer -mbackchain -march=z10"
        $2CFLAGS_JDK="${$2CFLAGS_JDK} -fno-strict-aliasing"
        ;;
      * )
        $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -fno-omit-frame-pointer"
        $2CFLAGS_JDK="${$2CFLAGS_JDK} -fno-strict-aliasing"
        ;;
    esac
    TOOLCHAIN_CHECK_COMPILER_VERSION(VERSION: 6, PREFIX: $2, IF_AT_LEAST: FLAGS_SETUP_GCC6_COMPILER_FLAGS($2))
  elif test "x$TOOLCHAIN_TYPE" = xclang; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D_GNU_SOURCE"

    # Restrict the debug information created by Clang to avoid
    # too big object files and speed the build up a little bit
    # (see http://llvm.org/bugs/show_bug.cgi?id=7554)
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -flimit-debug-info"
    if test "x$FLAGS_OS" = xlinux; then
      if test "x$FLAGS_CPU" = xx86; then
        # Force compatibility with i586 on 32 bit intel platforms.
        $2COMMON_CCXXFLAGS="${$2COMMON_CCXXFLAGS} -march=i586"
        $2JVM_CFLAGS="[$]$2JVM_CFLAGS -march=i586"
      fi
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -Wno-sometimes-uninitialized"
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS [$]$2COMMON_CCXXFLAGS_JDK -Wall -Wextra -Wno-unused -Wno-unused-parameter -Wformat=2 \
          -pipe -D_GNU_SOURCE -D_REENTRANT -D_LARGEFILE64_SOURCE"
      case $FLAGS_CPU_ARCH in
        ppc )
          # on ppc we don't prevent gcc to omit frame pointer but do prevent strict aliasing
          $2CFLAGS_JDK="${$2CFLAGS_JDK} -fno-strict-aliasing"
          ;;
        * )
          $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -fno-omit-frame-pointer"
          $2CFLAGS_JDK="${$2CFLAGS_JDK} -fno-strict-aliasing"
          ;;
      esac
    fi
  elif test "x$TOOLCHAIN_TYPE" = xsolstudio; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -DSPARC_WORKS"
    $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS [$]$2COMMON_CCXXFLAGS_JDK -DTRACING -DMACRO_MEMSYS_OPS -DBREAKPTS"
    if test "x$FLAGS_CPU_ARCH" = xx86; then
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -DcpuIntel -Di586 -D$FLAGS_CPU_LEGACY_LIB"
    fi

    $2CFLAGS_JDK="[$]$2CFLAGS_JDK -xc99=%none -xCC -errshort=tags -Xa -v -mt -W0,-noglobal"
    $2CXXFLAGS_JDK="[$]$2CXXFLAGS_JDK -errtags=yes +w -mt -features=no%except -DCC_NOEX -norunpath -xnolib"
  elif test "x$TOOLCHAIN_TYPE" = xxlc; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D_REENTRANT"
    $2CFLAGS_JDK="[$]$2CFLAGS_JDK -D_GNU_SOURCE -D_REENTRANT -D_LARGEFILE64_SOURCE -DSTDC"
    $2CXXFLAGS_JDK="[$]$2CXXFLAGS_JDK -D_GNU_SOURCE -D_REENTRANT -D_LARGEFILE64_SOURCE -DSTDC"
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS [$]$2COMMON_CCXXFLAGS_JDK \
        -MD -Zc:wchar_t- -W3 -wd4800 \
        -DWIN32_LEAN_AND_MEAN \
        -D_CRT_SECURE_NO_DEPRECATE -D_CRT_NONSTDC_NO_DEPRECATE \
        -DWIN32 -DIAL"
    if test "x$FLAGS_CPU" = xx86_64; then
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -D_AMD64_ -Damd64"
    else
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -D_X86_ -Dx86"
    fi
    # If building with Visual Studio 2010, we can still use _STATIC_CPPLIB to
    # avoid bundling msvcpNNN.dll. Doesn't work with newer versions of visual
    # studio.
    if test "x$TOOLCHAIN_VERSION" = "x2010"; then
      STATIC_CPPLIB_FLAGS="-D_STATIC_CPPLIB -D_DISABLE_DEPRECATE_STATIC_CPPLIB"
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK $STATIC_CPPLIB_FLAGS"
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS $STATIC_CPPLIB_FLAGS"
    fi
  fi

  ###############################################################################

  # Adjust flags according to debug level.
  case $DEBUG_LEVEL in
    fastdebug | slowdebug )
      $2CFLAGS_JDK="[$]$2CFLAGS_JDK $CFLAGS_DEBUG_SYMBOLS $CFLAGS_DEBUG_OPTIONS"
      $2CXXFLAGS_JDK="[$]$2CXXFLAGS_JDK $CXXFLAGS_DEBUG_SYMBOLS $CXXFLAGS_DEBUG_OPTIONS"
      ;;
    release )
      ;;
    * )
      AC_MSG_ERROR([Unrecognized \$DEBUG_LEVEL: $DEBUG_LEVEL])
      ;;
  esac

  # Set some common defines. These works for all compilers, but assume
  # -D is universally accepted.

  # Setup endianness
  if test "x$FLAGS_CPU_ENDIAN" = xlittle; then
    # The macro _LITTLE_ENDIAN needs to be defined the same to avoid the
    #   Sun C compiler warning message: warning: macro redefined: _LITTLE_ENDIAN
    #   (The Solaris X86 system defines this in file /usr/include/sys/isa_defs.h).
    #   Note: -Dmacro         is the same as    #define macro 1
    #         -Dmacro=        is the same as    #define macro
    if test "x$FLAGS_OS" = xsolaris; then
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -D_LITTLE_ENDIAN="
    else
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -D_LITTLE_ENDIAN"
    fi
  else
    # Same goes for _BIG_ENDIAN. Do we really need to set *ENDIAN on Solaris if they
    # are defined in the system?
    if test "x$FLAGS_OS" = xsolaris; then
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -D_BIG_ENDIAN="
    else
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -D_BIG_ENDIAN"
    fi
  fi

  # Always enable optional macros for VM.
  $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D__STDC_FORMAT_MACROS"
  $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D__STDC_LIMIT_MACROS"
  $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D__STDC_CONSTANT_MACROS"

  # Setup target OS define. Use OS target name but in upper case.
  FLAGS_OS_UPPERCASE=`$ECHO $FLAGS_OS | $TR 'abcdefghijklmnopqrstuvwxyz' 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'`
  $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -D$FLAGS_OS_UPPERCASE"

  # Setup target CPU
  $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK \
      $FLAGS_ADD_LP64 \
      -DARCH='\"$FLAGS_CPU_LEGACY\"' -D$FLAGS_CPU_LEGACY"

  # Setup debug/release defines
  if test "x$DEBUG_LEVEL" = xrelease; then
    $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -DNDEBUG"
    if test "x$FLAGS_OS" = xsolaris; then
      $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -DTRIMMED"
    fi
  else
    $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -DDEBUG"
  fi

  # Optional POSIX functionality needed by the VM

  if test "x$HAS_CLOCK_GETTIME" = "xtrue"; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -DSUPPORTS_CLOCK_MONOTONIC"
    if test "x$CLOCK_GETTIME_IN_LIBRT" = "xtrue"; then
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -DNEEDS_LIBRT"
    fi
  fi


  # Set some additional per-OS defines.
  if test "x$FLAGS_OS" = xlinux; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -DLINUX"
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -pipe $PICFLAG -fno-rtti -fno-exceptions \
        -fvisibility=hidden -fno-strict-aliasing -fno-omit-frame-pointer"
  elif test "x$FLAGS_OS" = xsolaris; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -DSOLARIS"
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -template=no%extdef -features=no%split_init \
        -D_Crun_inline_placement -library=stlport4 $PICFLAG -mt -features=no%except"
  elif test "x$FLAGS_OS" = xmacosx; then
    $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -D_ALLBSD_SOURCE -D_DARWIN_UNLIMITED_SELECT"
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D_ALLBSD_SOURCE"
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D_DARWIN_C_SOURCE -D_XOPEN_SOURCE"
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -fno-rtti -fno-exceptions -fvisibility=hidden \
        -mno-omit-leaf-frame-pointer -mstack-alignment=16 -pipe -fno-strict-aliasing \
        -fno-omit-frame-pointer"
  elif test "x$FLAGS_OS" = xaix; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -DAIX"
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -qtune=balanced \
        -qalias=noansi -qstrict -qtls=default -qlanglvl=c99vla \
        -qlanglvl=noredefmac -qnortti -qnoeh -qignerrno"
    # We need '-qminimaltoc' or '-qpic=large -bbigtoc' if the TOC overflows.
    # Hotspot now overflows its 64K TOC (currently only for debug),
    # so for debug we build with '-qpic=large -bbigtoc'.
    if test "x$DEBUG_LEVEL" = xslowdebug || test "x$DEBUG_LEVEL" = xfastdebug; then
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -qpic=large"
    fi
  elif test "x$FLAGS_OS" = xbsd; then
    $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK -D_ALLBSD_SOURCE"
  elif test "x$FLAGS_OS" = xwindows; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D_WINDOWS -DWIN32 -D_JNI_IMPLEMENTATION_"
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -nologo -W3 -MD -MP"
  fi

  # Set some additional per-CPU defines.
  if test "x$FLAGS_OS-$FLAGS_CPU" = xwindows-x86; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -arch:IA32"
  elif test "x$FLAGS_OS-$FLAGS_CPU" = xsolaris-sparcv9; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -xarch=sparc"
  elif test "x$FLAGS_CPU" = xppc64; then
    if test "x$FLAGS_OS" = xlinux; then
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -minsert-sched-nops=regroup_exact -mno-multiple -mno-string"
      # fixes `relocation truncated to fit' error for gcc 4.1.
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -mminimal-toc"
      # Use ppc64 instructions, but schedule for power5
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -mcpu=powerpc64 -mtune=power5"
    elif test "x$FLAGS_OS" = xaix; then
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -qarch=ppc64"
    fi
  elif test "x$FLAGS_CPU" = xppc64le; then
    if test "x$FLAGS_OS" = xlinux; then
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -minsert-sched-nops=regroup_exact -mno-multiple -mno-string"
      # Little endian machine uses ELFv2 ABI.
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -DABI_ELFv2"
      # Use Power8, this is the first CPU to support PPC64 LE with ELFv2 ABI.
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -mcpu=power8 -mtune=power8"
    fi
  elif test "x$FLAGS_CPU" = xs390x; then
    if test "x$FLAGS_OS" = xlinux; then
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -mbackchain -march=z10"
    fi
  fi

  if test "x$FLAGS_CPU_ENDIAN" = xlittle; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -DVM_LITTLE_ENDIAN"
  fi

  if test "x$FLAGS_CPU_BITS" = x64; then
    if test "x$FLAGS_OS" != xsolaris && test "x$FLAGS_OS" != xaix; then
      # Solaris does not have _LP64=1 in the old build.
      # xlc on AIX defines _LP64=1 by default and issues a warning if we redefine it.
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -D_LP64=1"
    fi
  fi

  # Set $2JVM_CFLAGS warning handling
  if test "x$FLAGS_OS" = xlinux; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -Wpointer-arith -Wsign-compare -Wunused-function \
        -Wunused-value -Woverloaded-virtual"

    if test "x$TOOLCHAIN_TYPE" = xgcc; then
      TOOLCHAIN_CHECK_COMPILER_VERSION(VERSION: [4.8], PREFIX: $2,
          IF_AT_LEAST: [
            # These flags either do not work or give spurious warnings prior to gcc 4.8.
            $2JVM_CFLAGS="[$]$2JVM_CFLAGS -Wno-format-zero-length -Wtype-limits -Wuninitialized"
          ]
      )
    fi
    if ! HOTSPOT_CHECK_JVM_VARIANT(zero); then
      # Non-zero builds have stricter warnings
      $2JVM_CFLAGS="[$]$2JVM_CFLAGS -Wreturn-type -Wundef -Wformat=2"
    else
      if test "x$TOOLCHAIN_TYPE" = xclang; then
        # Some versions of llvm do not like -Wundef
        $2JVM_CFLAGS="[$]$2JVM_CFLAGS -Wno-undef"
      fi
    fi
  elif test "x$FLAGS_OS" = xmacosx; then
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS -Wno-deprecated -Wpointer-arith \
        -Wsign-compare -Wundef -Wunused-function -Wformat=2"
  fi

  # Additional macosx handling
  if test "x$FLAGS_OS" = xmacosx; then
    # Let the flags variables get resolved in make for easier override on make
    # command line. AvailabilityMacros.h versions have no dots, ex: 1070.
    $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK \
        -DMAC_OS_X_VERSION_MIN_REQUIRED=\$(subst .,,\$(MACOSX_VERSION_MIN)) \
        -mmacosx-version-min=\$(MACOSX_VERSION_MIN)"
    $2JVM_CFLAGS="[$]$2JVM_CFLAGS \
        -DMAC_OS_X_VERSION_MIN_REQUIRED=\$(subst .,,\$(MACOSX_VERSION_MIN)) \
        -mmacosx-version-min=\$(MACOSX_VERSION_MIN)"
    $2ARFLAGS="$2$ARFLAGS -mmacosx-version-min=\$(MACOSX_VERSION_MIN)"

    if test -n "$MACOSX_VERSION_MAX"; then
        $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK \
            -DMAC_OS_X_VERSION_MAX_ALLOWED=\$(subst .,,\$(MACOSX_VERSION_MAX))"
        $2JVM_CFLAGS="[$]$2JVM_CFLAGS \
            -DMAC_OS_X_VERSION_MAX_ALLOWED=\$(subst .,,\$(MACOSX_VERSION_MAX))"
    fi
  fi

  # Setup some hard coded includes
  $2COMMON_CCXXFLAGS_JDK="[$]$2COMMON_CCXXFLAGS_JDK \
      -I\$(SUPPORT_OUTPUTDIR)/modules_include/java.base \
      -I\$(SUPPORT_OUTPUTDIR)/modules_include/java.base/\$(OPENJDK_TARGET_OS_INCLUDE_SUBDIR) \
      -I${TOPDIR}/src/java.base/share/native/libjava \
      -I${TOPDIR}/src/java.base/$FLAGS_OS_TYPE/native/libjava \
      -I${TOPDIR}/src/hotspot/share/include \
      -I${TOPDIR}/src/hotspot/os/${HOTSPOT_$1_OS_TYPE}/include"

  # The shared libraries are compiled using the picflag.

  $2CFLAGS_JDKLIB="[$]$2COMMON_CCXXFLAGS_JDK \
      [$]$2CFLAGS_JDK [$]$2EXTRA_CFLAGS_JDK $PICFLAG ${$2_SPECIAL_EXTRA_1} ${$2_SPECIAL_EXTRA_2}"
  $2CXXFLAGS_JDKLIB="[$]$2COMMON_CCXXFLAGS_JDK \
      [$]$2CXXFLAGS_JDK [$]$2EXTRA_CXXFLAGS_JDK $PICFLAG ${$2_SPECIAL_EXTRA_1} ${$2_SPECIAL_EXTRA_2}"

  # Executable flags
  $2CFLAGS_JDKEXE="[$]$2COMMON_CCXXFLAGS_JDK [$]$2CFLAGS_JDK [$]$2EXTRA_CFLAGS_JDK"
  $2CXXFLAGS_JDKEXE="[$]$2COMMON_CCXXFLAGS_JDK [$]$2CXXFLAGS_JDK [$]$2EXTRA_CXXFLAGS_JDK"

  AC_SUBST($2CFLAGS_JDKLIB)
  AC_SUBST($2CFLAGS_JDKEXE)
  AC_SUBST($2CXXFLAGS_JDKLIB)
  AC_SUBST($2CXXFLAGS_JDKEXE)


  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    $2JDKLIB_LIBS=""
  else
    $2JDKLIB_LIBS="-ljava -ljvm"
    if test "x$TOOLCHAIN_TYPE" = xsolstudio; then
      $2JDKLIB_LIBS="[$]$2JDKLIB_LIBS -lc"
    fi
  fi

  # Set $2JVM_LIBS (per os)
  if test "x$FLAGS_OS" = xlinux; then
    $2JVM_LIBS="[$]$2JVM_LIBS -lm -ldl -lpthread"
  elif test "x$FLAGS_OS" = xsolaris; then
    # FIXME: This hard-coded path is not really proper.
    if test "x$FLAGS_CPU" = xx86_64; then
      $2SOLARIS_LIBM_LIBS="/usr/lib/amd64/libm.so.1"
    elif test "x$FLAGS_CPU" = xsparcv9; then
      $2SOLARIS_LIBM_LIBS="/usr/lib/sparcv9/libm.so.1"
    fi
    $2JVM_LIBS="[$]$2JVM_LIBS -lsocket -lsched -ldl $SOLARIS_LIBM_LIBS -lCrun \
        -lthread -ldoor -lc -ldemangle -lnsl -lrt"
  elif test "x$FLAGS_OS" = xmacosx; then
    $2JVM_LIBS="[$]$2JVM_LIBS -lm"
  elif test "x$FLAGS_OS" = xaix; then
    $2JVM_LIBS="[$]$2JVM_LIBS -Wl,-lC_r -lm -ldl -lpthread"
  elif test "x$FLAGS_OS" = xbsd; then
    $2JVM_LIBS="[$]$2JVM_LIBS -lm"
  elif test "x$FLAGS_OS" = xwindows; then
    $2JVM_LIBS="[$]$2JVM_LIBS kernel32.lib user32.lib gdi32.lib winspool.lib \
        comdlg32.lib advapi32.lib shell32.lib ole32.lib oleaut32.lib uuid.lib \
        wsock32.lib winmm.lib version.lib psapi.lib"
    fi

  # Set $2JVM_ASFLAGS
  if test "x$FLAGS_OS" = xlinux; then
    if test "x$FLAGS_CPU" = xx86; then
      $2JVM_ASFLAGS="[$]$2JVM_ASFLAGS -march=i586"
    fi
  elif test "x$FLAGS_OS" = xmacosx; then
    $2JVM_ASFLAGS="[$]$2JVM_ASFLAGS -x assembler-with-cpp -mno-omit-leaf-frame-pointer -mstack-alignment=16"
  fi

  AC_SUBST($2LDFLAGS_JDKLIB)
  AC_SUBST($2LDFLAGS_JDKEXE)
  AC_SUBST($2JDKLIB_LIBS)
  AC_SUBST($2JDKEXE_LIBS)
  AC_SUBST($2LDFLAGS_CXX_JDK)
  AC_SUBST($2LDFLAGS_HASH_STYLE)
  AC_SUBST($2LDFLAGS_NO_EXEC_STACK)

  AC_SUBST($2JVM_CFLAGS)
  AC_SUBST($2JVM_LDFLAGS)
  AC_SUBST($2JVM_ASFLAGS)
  AC_SUBST($2JVM_LIBS)

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
# $1 - Optional prefix for each variable defined.
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
  $1CFLAGS_JDK="[$]$1CFLAGS_JDK ${NO_DELETE_NULL_POINTER_CHECKS_CFLAG} ${NO_LIFETIME_DSE_CFLAG}"
  $1JVM_CFLAGS="[$]$1JVM_CFLAGS ${NO_DELETE_NULL_POINTER_CHECKS_CFLAG} ${NO_LIFETIME_DSE_CFLAG}"
])
