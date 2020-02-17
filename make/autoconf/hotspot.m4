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

# We need these as m4 defines to be able to loop over them using m4 later on.

# All valid JVM features, regardless of platform
define(valid_jvm_features, m4_normalize( \
    ifdef([custom_valid_jvm_features], custom_valid_jvm_features) \
    \
    aot cds compiler1 compiler2 dtrace epsilongc g1gc graal jfr jni-check \
    jvmci jvmti link-time-opt management minimal nmt parallelgc serialgc \
    services shenandoahgc static-build vm-structs zero zgc \
))

# Deprecated JVM features (these are ignored, but with a warning)
define(deprecated_jvm_features, m4_normalize(
    cmsgc trace \
))

# All valid JVM variants
VALID_JVM_VARIANTS="server client minimal core zero custom"

###############################################################################
# Check if the specified JVM variant should be built. To be used in shell if
# constructs, like this:
# if HOTSPOT_CHECK_JVM_VARIANT(server); then
#
# Only valid to use after HOTSPOT_SETUP_JVM_VARIANTS has setup variants.

# Definition kept in one line to allow inlining in if statements.
# Additional [] needed to keep m4 from mangling shell constructs.
AC_DEFUN([HOTSPOT_CHECK_JVM_VARIANT],
[ [ [[ " $JVM_VARIANTS " =~ " $1 " ]] ] ])

###############################################################################
# Check which variants of the JVM that we want to build. Available variants are:
#   server: normal interpreter, and a tiered C1/C2 compiler
#   client: normal interpreter, and C1 (no C2 compiler)
#   minimal: reduced form of client with optional features stripped out
#   core: normal interpreter only, no compiler
#   zero: C++ based interpreter only, no compiler
#   custom: baseline JVM with no default features
#
AC_DEFUN_ONCE([HOTSPOT_SETUP_JVM_VARIANTS],
[
  AC_ARG_WITH([jvm-variants], [AS_HELP_STRING([--with-jvm-variants],
      [JVM variants (separated by commas) to build (server,client,minimal,core,zero,custom) @<:@server@:>@])])

  if test "x$with_jvm_variants" = x; then
    with_jvm_variants="server"
  fi
  JVM_VARIANTS_OPT="$with_jvm_variants"

  # Has the user listed more than one variant?
  # Additional [] needed to keep m4 from mangling shell constructs.
  if [ [[ "$JVM_VARIANTS_OPT" =~ "," ]] ]; then
    BUILDING_MULTIPLE_JVM_VARIANTS=true
  else
    BUILDING_MULTIPLE_JVM_VARIANTS=false
  fi
  # Replace the commas with AND for use in the build directory name.
  JVM_VARIANTS_WITH_AND=`$ECHO "$JVM_VARIANTS_OPT" | $SED -e 's/,/AND/g'`

  AC_MSG_CHECKING([which variants of the JVM to build])
  # JVM_VARIANTS is a space-separated list.
  # Also use minimal, not minimal1 (which is kept for backwards compatibility).
  JVM_VARIANTS=`$ECHO $JVM_VARIANTS_OPT | $SED -e 's/,/ /g' -e 's/minimal1/minimal/'`
  AC_MSG_RESULT([$JVM_VARIANTS])

  # Check that the selected variants are valid
  BASIC_GET_NON_MATCHING_VALUES(INVALID_VARIANTS, $JVM_VARIANTS, $VALID_JVM_VARIANTS)
  if test "x$INVALID_VARIANTS" != x; then
    AC_MSG_NOTICE([Unknown variant(s) specified: "$INVALID_VARIANTS"])
    AC_MSG_NOTICE([The available JVM variants are: "$VALID_JVM_VARIANTS"])
    AC_MSG_ERROR([Cannot continue])
  fi

  # All "special" variants share the same output directory ("server")
  VALID_MULTIPLE_JVM_VARIANTS="server client minimal"
  BASIC_GET_NON_MATCHING_VALUES(INVALID_MULTIPLE_VARIANTS, $JVM_VARIANTS, $VALID_MULTIPLE_JVM_VARIANTS)
  if  test "x$INVALID_MULTIPLE_VARIANTS" != x && test "x$BUILDING_MULTIPLE_JVM_VARIANTS" = xtrue; then
    AC_MSG_ERROR([You cannot build multiple variants with anything else than $VALID_MULTIPLE_JVM_VARIANTS.])
  fi

  # The "main" variant is the one used by other libs to link against during the
  # build.
  if test "x$BUILDING_MULTIPLE_JVM_VARIANTS" = "xtrue"; then
    MAIN_VARIANT_PRIO_ORDER="server client minimal"
    for variant in $MAIN_VARIANT_PRIO_ORDER; do
      if HOTSPOT_CHECK_JVM_VARIANT($variant); then
        JVM_VARIANT_MAIN="$variant"
        break
      fi
    done
  else
    JVM_VARIANT_MAIN="$JVM_VARIANTS"
  fi

  AC_SUBST(JVM_VARIANTS)
  AC_SUBST(VALID_JVM_VARIANTS)
  AC_SUBST(JVM_VARIANT_MAIN)
])

# arg 1: feature name
# arg 2: code block to execute. Should set AVAILABLE=false in case of failure
AC_DEFUN([HOTSPOT_CHECK_FEATURE_AVAILABILITY],
[
  # Assume that feature is available
  AVAILABLE=true

  # Execute feature test block
  $2

  AC_MSG_CHECKING([if JVM feature '$1' is available])
  if test "x$AVAILABLE" = "xtrue"; then
    AC_MSG_RESULT([yes])
  else
    AC_MSG_RESULT([no])
    UNAVAILABLE_FEATURES="$UNAVAILABLE_FEATURES $1"
  fi
])

AC_DEFUN([HOTSPOT_SETUP_FEATURES_FOR_PLATFORM],
[
  # Check if features are unavailable for this platform.

  # The code block in HOTSPOT_CHECK_FEATURE_AVAILABILITY should set
  # AVAILABLE=false if the feature is not available on this platform. If so, the
  # feature will be added to UNAVAILABLE_FEATURES.

  HOTSPOT_CHECK_FEATURE_AVAILABILITY(aot,
  [
    AC_MSG_CHECKING([if platform is supported by AOT])
    # AOT requires JVMCI, and is therefore only available where JVMCI is available.
    if test "x$OPENJDK_TARGET_CPU" = "xx86_64" || test "x$OPENJDK_TARGET_CPU" = "xaarch64"; then
      AC_MSG_RESULT([yes])
    else
      AC_MSG_RESULT([no, $OPENJDK_TARGET_CPU])
      AVAILABLE=false
    fi

    AC_MSG_CHECKING([if AOT source code is present])
    if test -e "${TOPDIR}/src/jdk.internal.vm.compiler" && test -e "${TOPDIR}/src/jdk.aot"; then
      AC_MSG_RESULT([yes])
    else
      AC_MSG_RESULT([no, src/jdk.internal.vm.compiler or src/jdk.aot is missing])
      AVAILABLE=false
    fi
  ])

  HOTSPOT_CHECK_FEATURE_AVAILABILITY(cds,
  [
    AC_MSG_CHECKING([if platform is supported by CDS])
    if test "x$OPENJDK_TARGET_OS" != xaix; then
      AC_MSG_RESULT([yes])
    else
      AC_MSG_RESULT([no, $OPENJDK_TARGET_OS])
      AVAILABLE=false
    fi
  ])

  HOTSPOT_CHECK_FEATURE_AVAILABILITY(dtrace,
  [
    AC_MSG_CHECKING([for dtrace tool])
    if test "x$DTRACE" != "x" && test -x "$DTRACE"; then
      AC_MSG_RESULT([$DTRACE])
    else
      AC_MSG_RESULT([no])
      AVAILABLE=false
    fi

    AC_CHECK_HEADERS([sys/sdt.h], [DTRACE_HEADERS_OK=yes],[DTRACE_HEADERS_OK=no])
    if test "x$DTRACE_HEADERS_OK" != "xyes"; then
      HELP_MSG_MISSING_DEPENDENCY([dtrace])
      AC_MSG_NOTICE([Cannot enable dtrace with missing dependencies. See above.])
      AVAILABLE=false
    fi
  ])

  HOTSPOT_CHECK_FEATURE_AVAILABILITY(graal,
  [
    AC_MSG_CHECKING([if platform is supported by Graal])
    # Graal requires JVMCI, and is therefore only available where JVMCI is available.
    if test "x$OPENJDK_TARGET_CPU" = "xx86_64" || test "x$OPENJDK_TARGET_CPU" = "xaarch64" ; then
      AC_MSG_RESULT([yes])
    else
      AC_MSG_RESULT([no, $OPENJDK_TARGET_CPU])
      AVAILABLE=false
    fi
  ])

  HOTSPOT_CHECK_FEATURE_AVAILABILITY(jfr,
  [
    AC_MSG_CHECKING([if platform is supported by JFR])
    if test "x$OPENJDK_TARGET_OS" = xaix || \
        test "x$OPENJDK_TARGET_OS-$OPENJDK_TARGET_CPU" = "xlinux-sparcv9"; then
      AC_MSG_RESULT([no, $OPENJDK_TARGET_OS-$OPENJDK_TARGET_CPU])
      AVAILABLE=false
    else
      AC_MSG_RESULT([yes])
    fi
  ])

  HOTSPOT_CHECK_FEATURE_AVAILABILITY(jvmci,
  [
    AC_MSG_CHECKING([if platform is supported by JVMCI])
    if test "x$OPENJDK_TARGET_CPU" = "xx86_64" || test "x$OPENJDK_TARGET_CPU" = "xaarch64" ; then
      AC_MSG_RESULT([yes])
    else
      AC_MSG_RESULT([no, $OPENJDK_TARGET_CPU])
      AVAILABLE=false
    fi
  ])

  HOTSPOT_CHECK_FEATURE_AVAILABILITY(shenandoahgc,
  [
    AC_MSG_CHECKING([if platform is supported by Shenandoah])
    if test "x$OPENJDK_TARGET_CPU_ARCH" = "xx86" || test "x$OPENJDK_TARGET_CPU" = "xaarch64" ; then
      AC_MSG_RESULT([yes])
    else
      AC_MSG_RESULT([no, $OPENJDK_TARGET_CPU])
      AVAILABLE=false
    fi
  ])

  HOTSPOT_CHECK_FEATURE_AVAILABILITY(static-build,
  [
    AC_MSG_CHECKING([if static-build is enabled in configure])
    if test "x$STATIC_BUILD" = "xtrue"; then
      AC_MSG_RESULT([yes])
    else
      AC_MSG_RESULT([no, use --enable-static-build to enable static build.])
      AVAILABLE=false
    fi
  ])

  HOTSPOT_CHECK_FEATURE_AVAILABILITY(zgc,
  [
    AC_MSG_CHECKING([if platform is supported by ZGC])
    if test "x$OPENJDK_TARGET_CPU" = "xx86_64"; then
      if test "x$OPENJDK_TARGET_OS" = "xlinux" || test "x$OPENJDK_TARGET_OS" = "xwindows" || \
          test "x$OPENJDK_TARGET_OS" = "xmacosx"; then
        AC_MSG_RESULT([yes])
      else
        AC_MSG_RESULT([no, $OPENJDK_TARGET_OS-$OPENJDK_TARGET_CPU])
        AVAILABLE=false
      fi
    elif test "x$OPENJDK_TARGET_OS-$OPENJDK_TARGET_CPU" = "xlinux-aarch64"; then
        AC_MSG_RESULT([yes])
    else
        AC_MSG_RESULT([no, $OPENJDK_TARGET_OS-$OPENJDK_TARGET_CPU])
        AVAILABLE=false
    fi

    if test "x$OPENJDK_TARGET_OS" = "xwindows"; then
      AC_MSG_CHECKING([if Windows APIs required for ZGC is present])
      AC_COMPILE_IFELSE(
        [AC_LANG_PROGRAM([[#include <windows.h>]],
          [[struct MEM_EXTENDED_PARAMETER x;]])
        ],
        [
          AC_MSG_RESULT([yes])
        ],
        [
          AC_MSG_RESULT([no, missing required APIs])
          AVAILABLE=false
        ]
      )
    fi
  ])
])

AC_DEFUN([HOTSPOT_SETUP_FEATURES_FOR_VARIANT],
[
  # Check if features are unavailable for this JVM variant.
  # This means that is not possible to build this feature for this variant.
  VARIANT=$1

  if test "x$VARIANT" = "xcore"; then
    UNAVAILABLE_FEATURES_VARIANT="cds"
  fi

  if test "x$VARIANT" = "xminimal"; then
    UNAVAILABLE_FEATURES_VARIANT="cds"
  else
    UNAVAILABLE_FEATURES_VARIANT="minimal"
  fi

  if test "x$VARIANT" = "xzero"; then
    UNAVAILABLE_FEATURES_VARIANT="aot cds compiler1 compiler2 epsilongc g1gc \
        graal jvmci shenandoahgc zgc"
  else
    UNAVAILABLE_FEATURES_VARIANT="zero"
  fi
])


AC_DEFUN([HOTSPOT_SETUP_FEATURES_FILTER],
[
  # Check if a feature should be off by default for this JVM variant.
  VARIANT=$1

  # Allow for custom extensions to have a default filter for all variants.
  DEFAULT_FILTER="$CUSTOM_DEFAULT_FILTER"

  # Is this variant client?
  if test "x$VARIANT" = "xclient"; then
    DEFAULT_FILTER="$DEFAULT_FILTER aot compiler2 graal jvmci"
  fi

  # Is this variant core?
  if test "x$VARIANT" = "xcore"; then
    DEFAULT_FILTER="$DEFAULT_FILTER aot compiler1 compiler2 graal jvmci"
  fi

  # Is this variant minimal?
  if test "x$VARIANT" = "xminimal"; then
    DEFAULT_FILTER="$DEFAULT_FILTER aot cds compiler2 dtrace epsilongc g1gc \
        graal jfr jni-check jvmci jvmti management nmt parallelgc services \
        shenandoahgc vm-structs zgc"
    if test "x$OPENJDK_TARGET_CPU" != xarm ; then
      # No other platforms than arm-32 should have link-time-opt as default.
      DEFAULT_FILTER="$DEFAULT_FILTER link-time-opt"
    fi
  else
    # No other variants should have link-time-opt as default.
    DEFAULT_FILTER="$DEFAULT_FILTER link-time-opt"
  fi

  # Is this variant zero?
  if test "x$VARIANT" = "xzero"; then
    DEFAULT_FILTER="$DEFAULT_FILTER jfr"
  fi

  # Platform-specific filters
  if test "x$OPENJDK_TARGET_OS" = xaix; then
    DEFAULT_FILTER="$DEFAULT_FILTER jfr"
  fi

  if test "x$OPENJDK_TARGET_OS-$OPENJDK_TARGET_CPU" = "xlinux-sparcv9"; then
    DEFAULT_FILTER="$DEFAULT_FILTER jfr"
  fi
])

###############################################################################
# Check if the specified JVM feature is enabled. To be used in shell if
# constructs, like this:
# if HOTSPOT_CHECK_JVM_FEATURE(jvmti); then
#
# Only valid to use in HOTSPOT_VERIFY_FEATURES.

# Definition kept in one line to allow inlining in if statements.
# Additional [] needed to keep m4 from mangling shell constructs.
AC_DEFUN([HOTSPOT_CHECK_JVM_FEATURE],
[ [ [[ " $RESULTING_FEATURES " =~ " $1 " ]] ] ])

###############################################################################
# Check if dtrace should be enabled and has all prerequisites present.
#
AC_DEFUN([HOTSPOT_VERIFY_FEATURES],
[
  # Verify that dependencies are met for inter-feature relations.
  if HOTSPOT_CHECK_JVM_FEATURE(aot) && ! HOTSPOT_CHECK_JVM_FEATURE(graal); then
    AC_MSG_ERROR([Specified JVM feature 'aot' requires feature 'graal' for variant '$1'])
  fi

  if HOTSPOT_CHECK_JVM_FEATURE(graal) && ! HOTSPOT_CHECK_JVM_FEATURE(jvmci); then
    AC_MSG_ERROR([Specified JVM feature 'graal' requires feature 'jvmci' for variant '$1'])
  fi

  if HOTSPOT_CHECK_JVM_FEATURE(jvmci) && ! (HOTSPOT_CHECK_JVM_FEATURE(compiler1) || HOTSPOT_CHECK_JVM_FEATURE(compiler2)); then
    AC_MSG_ERROR([Specified JVM feature 'jvmci' requires feature 'compiler2' or 'compiler1' for variant '$1'])
  fi

  if HOTSPOT_CHECK_JVM_FEATURE(jvmti) && ! HOTSPOT_CHECK_JVM_FEATURE(services); then
    AC_MSG_ERROR([Specified JVM feature 'jvmti' requires feature 'services' for variant '$1'])
  fi

  if HOTSPOT_CHECK_JVM_FEATURE(management) && ! HOTSPOT_CHECK_JVM_FEATURE(nmt); then
    AC_MSG_ERROR([Specified JVM feature 'management' requires feature 'nmt' for variant '$1'])
  fi

  # If at least one variant is missing cds, generating classlists is not possible.
  if ! HOTSPOT_CHECK_JVM_FEATURE(cds); then
    CDS_IS_ENABLED="false"
  fi

  # Verify that we have at least one gc selected (i.e., feature named "*gc").
  # Additional [] needed to keep m4 from mangling shell constructs.
  [ GC_FEATURES=`$ECHO $RESULTING_FEATURES | $GREP -w '[^ ]*gc'` ]
  if test "x$GC_FEATURES" = x; then
      AC_MSG_NOTICE([At least one gc needed for variant '$1'.])
      AC_MSG_NOTICE([Specified features: '$RESULTING_FEATURES'])
      AC_MSG_ERROR([Cannot continue])
  fi

  # Validate features for configure script errors (not user errors)
  BASIC_GET_NON_MATCHING_VALUES(INVALID_FEATURES, $RESULTING_FEATURES, $VALID_JVM_FEATURES)
  if test "x$INVALID_FEATURES" != x; then
    AC_MSG_ERROR([Internal configure script error. Invalid JVM feature(s): $INVALID_FEATURES])
  fi

  # Keep feature list sorted and free of duplicates
  BASIC_SORT_LIST(RESULTING_FEATURES, $RESULTING_FEATURES)
])

AC_DEFUN_ONCE([HOTSPOT_PARSE_JVM_FEATURES],
[
  # Setup shell variables from the m4 lists
  BASIC_SORT_LIST(VALID_JVM_FEATURES, "valid_jvm_features")
  BASIC_SORT_LIST(DEPRECATED_JVM_FEATURES, "deprecated_jvm_features")

  # The user can in some cases supply additional jvm features. For the custom
  # variant, this defines the entire variant.

  # For historical reasons, some jvm features have their own, shorter names.
  # Keep those as aliases for the --enable-jvm-feature-* style arguments.
  BASIC_ALIASED_ARG_ENABLE(aot, --enable-jvm-feature-aot)
  BASIC_ALIASED_ARG_ENABLE(cds, --enable-jvm-feature-cds)
  BASIC_ALIASED_ARG_ENABLE(dtrace, --enable-jvm-feature-dtrace)

  # First check for features using the
  # --with-jvm-features="<[-]feature>[,<[-]feature> ...]" syntax.
  AC_ARG_WITH([jvm-features], [AS_HELP_STRING([--with-jvm-features],
      [JVM features to enable (foo) or disable (-foo), separated by comma. Use '--help' to show possible values @<:@none@:>@])])
  if test "x$with_jvm_features" != x; then
    AC_MSG_CHECKING([user specified JVM feature list])
    USER_JVM_FEATURE_LIST=`$ECHO $with_jvm_features | $SED -e 's/,/ /g'`
    AC_MSG_RESULT([$user_jvm_feature_list])
    # These features will be added to all variant defaults
    JVM_FEATURES=`$ECHO $USER_JVM_FEATURE_LIST | $AWK '{ for (i=1; i<=NF; i++) if (!match($i, /^-.*/)) printf("%s ", $i) }'`
    # These features will be removed from all variant defaults
    DISABLED_JVM_FEATURES=`$ECHO $USER_JVM_FEATURE_LIST | $AWK '{ for (i=1; i<=NF; i++) if (match($i, /^-.*/)) printf("%s ", substr($i, 2))}'`

    # Verify that the user has provided valid features
    BASIC_GET_NON_MATCHING_VALUES(INVALID_FEATURES, $JVM_FEATURES $DISABLED_JVM_FEATURES, $VALID_JVM_FEATURES $DEPRECATED_JVM_FEATURES)
    if test "x$INVALID_FEATURES" != x; then
      AC_MSG_NOTICE([Unknown JVM features specified: "$INVALID_FEATURES"])
      AC_MSG_NOTICE([The available JVM features are: "$VALID_JVM_FEATURES"])
      AC_MSG_ERROR([Cannot continue])
    fi

    # Check if the user has provided deprecated features
    BASIC_GET_MATCHING_VALUES(DEPRECATED_FEATURES, $JVM_FEATURES $DISABLED_JVM_FEATURES, $DEPRECATED_JVM_FEATURES)
    if test "x$DEPRECATED_FEATURES" != x; then
      AC_MSG_WARN([Deprecated JVM features specified (will be ignored): "$DEPRECATED_FEATURES"])
      # Filter out deprecated features
      BASIC_GET_NON_MATCHING_VALUES(JVM_FEATURES, $JVM_FEATURES, $DEPRECATED_FEATURES)
      BASIC_GET_NON_MATCHING_VALUES(DISABLED_JVM_FEATURES, $DISABLED_JVM_FEATURES, $DEPRECATED_FEATURES)
    fi
  fi

  # Then check for features using the "--enable-jvm-feature-<feature>" syntax.
  # Using m4, loop over all features with the variable FEATURE.
  m4_foreach(FEATURE, m4_split(valid_jvm_features), [
    AC_ARG_ENABLE(jvm-feature-FEATURE, AS_HELP_STRING([--enable-jvm-feature-FEATURE],
        [enable jvm feature 'FEATURE']))

    # Create an m4 variable containing a shell variable name like
    # "enable_jvm_feature_static_build".
    define(FEATURE_SHELL, [enable_jvm_feature_]translit(FEATURE, -, _))

    if test "x$FEATURE_SHELL" = xyes; then
      JVM_FEATURES="$JVM_FEATURES FEATURE"
    elif test "x$FEATURE_SHELL" = xno; then
      DISABLED_JVM_FEATURES="$DISABLED_JVM_FEATURES FEATURE"
    elif test "x$FEATURE_SHELL" != x; then
      AC_MSG_ERROR([Invalid value for --enable-jvm-feature-FEATURE: $FEATURE_SHELL])
    fi

    undefine([FEATURE_SHELL])
  ])

  # Likewise, check for deprecated arguments.
  m4_foreach(FEATURE, m4_split(deprecated_jvm_features), [
    AC_ARG_ENABLE(jvm-feature-FEATURE, AS_HELP_STRING([--enable-jvm-feature-FEATURE],
        [enable jvm feature 'FEATURE' (deprecated)]))

    define(FEATURE_SHELL, [enable_jvm_feature_]translit(FEATURE, -, _))

    if test "x$FEATURE_SHELL" != x; then
      AC_MSG_WARN([Deprecated JVM feature, will be ignored: --enable-jvm-feature-FEATURE])
    fi

    undefine([FEATURE_SHELL])
  ])

  # Used for verification of Makefiles by check-jvm-feature
  AC_SUBST(VALID_JVM_FEATURES)
])

###############################################################################
# Set up all JVM features for each JVM variant.
#
AC_DEFUN_ONCE([HOTSPOT_SETUP_JVM_FEATURES],
[
  # It is possible to generate classlists only if all JVM variants has cds enabled.
  CDS_IS_ENABLED="true"

  # Figure out if any features is unavailable for this platform.
  # The result is stored in UNAVAILABLE_FEATURES.
  HOTSPOT_SETUP_FEATURES_FOR_PLATFORM

  for variant in $JVM_VARIANTS; do
    # Figure out if any features is unavailable for this variant.
    # The result is stored in UNAVAILABLE_FEATURES_VARIANT.
    HOTSPOT_SETUP_FEATURES_FOR_VARIANT($variant)

    # Setup the string used to filter out features from being turned on by default.
    # The result is stored in DEFAULT_FILTER.
    HOTSPOT_SETUP_FEATURES_FILTER($variant)

    if test "x$variant" != xcustom; then
      BASIC_GET_NON_MATCHING_VALUES(DEFAULT_FOR_VARIANT, $VALID_JVM_FEATURES, $UNAVAILABLE_FEATURES $UNAVAILABLE_FEATURES_VARIANT $DEFAULT_FILTER)
    else
      # For the 'custom' variant, the default is to start with an empty set
      DEFAULT_FOR_VARIANT=""
    fi

    # Verify explicitly enabled features
    BASIC_GET_MATCHING_VALUES(ENABLED_BUT_UNAVAILABLE, $JVM_FEATURES, $UNAVAILABLE_FEATURES $UNAVAILABLE_FEATURES_VARIANT)
    if test "x$ENABLED_BUT_UNAVAILABLE" != x; then
      AC_MSG_NOTICE([ERROR: Unavailable JVM features explicitly enabled for '$variant': '$ENABLED_BUT_UNAVAILABLE'])
      AC_MSG_ERROR([Cannot continue])
    fi

    BASIC_GET_MATCHING_VALUES(ENABLED_BUT_DEFAULT, $JVM_FEATURES, $DEFAULT_FOR_VARIANT)
    if test "x$ENABLED_BUT_DEFAULT" != x; then
      AC_MSG_NOTICE([Default JVM features explicitly enabled for '$variant': '$ENABLED_BUT_DEFAULT'])
    fi

    # Verify explicitly disabled features
    BASIC_GET_MATCHING_VALUES(DISABLED_BUT_UNAVAILABLE, $DISABLED_JVM_FEATURES, $UNAVAILABLE_FEATURES $UNAVAILABLE_FEATURES_VARIANT)
    if test "x$DISABLED_BUT_UNAVAILABLE" != x; then
      AC_MSG_NOTICE([Unavailable JVM features explicitly disabled for '$variant': '$DISABLED_BUT_UNAVAILABLE'])
    fi

    # RESULTING_FEATURES is the set of all default features and all explicitly
    # enabled features, with the explicitly disabled features filtered out.
    BASIC_GET_NON_MATCHING_VALUES(RESULTING_FEATURES, $DEFAULT_FOR_VARIANT $JVM_FEATURES, $DISABLED_JVM_FEATURES)

    # Verify consistency for RESULTING_FEATURES
    HOTSPOT_VERIFY_FEATURES($variant)

    AC_MSG_CHECKING([JVM features to use for variant '$variant'])
    AC_MSG_RESULT([$RESULTING_FEATURES])

    # Save this as e.g. JVM_FEATURES_server, using indirect variable referencing.
    features_var_name=JVM_FEATURES_$variant
    eval $features_var_name=\"$RESULTING_FEATURES\"
  done

  # Unfortunately AC_SUBST does not work with non-literally named variables,
  # so list all variants here.
  AC_SUBST(JVM_FEATURES_server)
  AC_SUBST(JVM_FEATURES_client)
  AC_SUBST(JVM_FEATURES_minimal)
  AC_SUBST(JVM_FEATURES_core)
  AC_SUBST(JVM_FEATURES_zero)
  AC_SUBST(JVM_FEATURES_custom)
])

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
