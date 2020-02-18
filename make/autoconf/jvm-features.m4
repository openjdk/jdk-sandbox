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
define(jvm_features_valid, m4_normalize( \
    ifdef([custom_jvm_features_valid], custom_jvm_features_valid) \
    \
    aot cds compiler1 compiler2 dtrace epsilongc g1gc graal jfr jni-check \
    jvmci jvmti link-time-opt management minimal nmt parallelgc serialgc \
    services shenandoahgc static-build vm-structs zero zgc \
))

# Deprecated JVM features (these are ignored, but with a warning)
define(jvm_features_deprecated, m4_normalize(
    cmsgc trace \
))


# Parse command line options for JVM features selection. After this function
# has run $JVM_FEATURES, $DISABLED_JVM_FEATURES and $JVM_FEATURES_VALID can be
# used.
AC_DEFUN_ONCE([JVM_FEATURES_PARSE_OPTIONS],
[
  # Setup shell variables from the m4 lists
  BASIC_SORT_LIST(JVM_FEATURES_VALID, "jvm_features_valid")
  BASIC_SORT_LIST(JVM_FEATURES_DEPRECATED, "jvm_features_deprecated")

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
    BASIC_GET_NON_MATCHING_VALUES(INVALID_FEATURES, $JVM_FEATURES $DISABLED_JVM_FEATURES, $JVM_FEATURES_VALID $JVM_FEATURES_DEPRECATED)
    if test "x$INVALID_FEATURES" != x; then
      AC_MSG_NOTICE([Unknown JVM features specified: "$INVALID_FEATURES"])
      AC_MSG_NOTICE([The available JVM features are: "$JVM_FEATURES_VALID"])
      AC_MSG_ERROR([Cannot continue])
    fi

    # Check if the user has provided deprecated features
    BASIC_GET_MATCHING_VALUES(DEPRECATED_FEATURES, $JVM_FEATURES $DISABLED_JVM_FEATURES, $JVM_FEATURES_DEPRECATED)
    if test "x$DEPRECATED_FEATURES" != x; then
      AC_MSG_WARN([Deprecated JVM features specified (will be ignored): "$DEPRECATED_FEATURES"])
      # Filter out deprecated features
      BASIC_GET_NON_MATCHING_VALUES(JVM_FEATURES, $JVM_FEATURES, $DEPRECATED_FEATURES)
      BASIC_GET_NON_MATCHING_VALUES(DISABLED_JVM_FEATURES, $DISABLED_JVM_FEATURES, $DEPRECATED_FEATURES)
    fi
  fi

  # Then check for features using the "--enable-jvm-feature-<feature>" syntax.
  # Using m4, loop over all features with the variable FEATURE.
  m4_foreach(FEATURE, m4_split(jvm_features_valid), [
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
  m4_foreach(FEATURE, m4_split(jvm_features_deprecated), [
    AC_ARG_ENABLE(jvm-feature-FEATURE, AS_HELP_STRING([--enable-jvm-feature-FEATURE],
        [enable jvm feature 'FEATURE' (deprecated)]))

    define(FEATURE_SHELL, [enable_jvm_feature_]translit(FEATURE, -, _))

    if test "x$FEATURE_SHELL" != x; then
      AC_MSG_WARN([Deprecated JVM feature, will be ignored: --enable-jvm-feature-FEATURE])
    fi

    undefine([FEATURE_SHELL])
  ])

  # Used for verification of Makefiles by check-jvm-feature
  # FIXME!!!!
  VALID_JVM_FEATURES="$JVM_FEATURES_VALID"
  AC_SUBST(VALID_JVM_FEATURES)
])

# Helper function for the JVM_FEATURES_CHECK_* suite.
# The code in the code block should assign 'false' to the variable AVAILABLE
# if the feature is not available, and this function will handle everything
# else that is needed.
#
# arg 1: The name of the feature to test
# arg 2: The code block to execute
AC_DEFUN([JVM_FEATURES_CHECK_AVAILABILITY],
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
    JVM_FEATURES_PLATFORM_UNAVAILABLE="$JVM_FEATURES_PLATFORM_UNAVAILABLE $1"
  fi
])

# Check if the feature 'aot' is available on this platform.
AC_DEFUN_ONCE([JVM_FEATURES_CHECK_AOT],
[
  JVM_FEATURES_CHECK_AVAILABILITY(aot, [
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
])

# Check if the feature 'cds' is available on this platform.
AC_DEFUN_ONCE([JVM_FEATURES_CHECK_CDS],
[
  JVM_FEATURES_CHECK_AVAILABILITY(cds, [
    AC_MSG_CHECKING([if platform is supported by CDS])
    if test "x$OPENJDK_TARGET_OS" != xaix; then
      AC_MSG_RESULT([yes])
    else
      AC_MSG_RESULT([no, $OPENJDK_TARGET_OS])
      AVAILABLE=false
    fi
  ])
])

# Check if the feature 'dtrace' is available on this platform.
AC_DEFUN_ONCE([JVM_FEATURES_CHECK_DTRACE],
[
  JVM_FEATURES_CHECK_AVAILABILITY(dtrace, [
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
])

# Check if the feature 'graal' is available on this platform.
AC_DEFUN_ONCE([JVM_FEATURES_CHECK_GRAAL],
[
  JVM_FEATURES_CHECK_AVAILABILITY(graal, [
    AC_MSG_CHECKING([if platform is supported by Graal])
    # Graal requires JVMCI, and is therefore only available where JVMCI is available.
    if test "x$OPENJDK_TARGET_CPU" = "xx86_64" || test "x$OPENJDK_TARGET_CPU" = "xaarch64" ; then
      AC_MSG_RESULT([yes])
    else
      AC_MSG_RESULT([no, $OPENJDK_TARGET_CPU])
      AVAILABLE=false
    fi
  ])
])

# Check if the feature 'jfr' is available on this platform.
AC_DEFUN_ONCE([JVM_FEATURES_CHECK_JFR],
[
  JVM_FEATURES_CHECK_AVAILABILITY(jfr, [
    AC_MSG_CHECKING([if platform is supported by JFR])
    if test "x$OPENJDK_TARGET_OS" = xaix || \
        test "x$OPENJDK_TARGET_OS-$OPENJDK_TARGET_CPU" = "xlinux-sparcv9"; then
      AC_MSG_RESULT([no, $OPENJDK_TARGET_OS-$OPENJDK_TARGET_CPU])
      AVAILABLE=false
    else
      AC_MSG_RESULT([yes])
    fi
  ])
])

# Check if the feature 'jvmci' is available on this platform.
AC_DEFUN_ONCE([JVM_FEATURES_CHECK_JVMCI],
[
  JVM_FEATURES_CHECK_AVAILABILITY(jvmci, [
    AC_MSG_CHECKING([if platform is supported by JVMCI])
    if test "x$OPENJDK_TARGET_CPU" = "xx86_64" || test "x$OPENJDK_TARGET_CPU" = "xaarch64" ; then
      AC_MSG_RESULT([yes])
    else
      AC_MSG_RESULT([no, $OPENJDK_TARGET_CPU])
      AVAILABLE=false
    fi
  ])
])

# Check if the feature 'shenandoahgc' is available on this platform.
AC_DEFUN_ONCE([JVM_FEATURES_CHECK_SHENANDOAHGC],
[
  JVM_FEATURES_CHECK_AVAILABILITY(shenandoahgc, [
    AC_MSG_CHECKING([if platform is supported by Shenandoah])
    if test "x$OPENJDK_TARGET_CPU_ARCH" = "xx86" || test "x$OPENJDK_TARGET_CPU" = "xaarch64" ; then
      AC_MSG_RESULT([yes])
    else
      AC_MSG_RESULT([no, $OPENJDK_TARGET_CPU])
      AVAILABLE=false
    fi
  ])
])

# Check if the feature 'static-build' is available on this platform.
AC_DEFUN_ONCE([JVM_FEATURES_CHECK_STATIC_BUILD],
[
  JVM_FEATURES_CHECK_AVAILABILITY(static-build, [
    AC_MSG_CHECKING([if static-build is enabled in configure])
    if test "x$STATIC_BUILD" = "xtrue"; then
      AC_MSG_RESULT([yes])
    else
      AC_MSG_RESULT([no, use --enable-static-build to enable static build.])
      AVAILABLE=false
    fi
  ])
])

# Check if the feature 'zgc' is available on this platform.
AC_DEFUN_ONCE([JVM_FEATURES_CHECK_ZGC],
[
  JVM_FEATURES_CHECK_AVAILABILITY(zgc, [
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

# Figure out if any features is unavailable for this platform.
# The result is stored in JVM_FEATURES_PLATFORM_UNAVAILABLE.
AC_DEFUN_ONCE([JVM_FEATURES_PREPARE_PLATFORM],
[
  # Check if features are unavailable for this platform.
  # The checks below should add unavailable features to
  # JVM_FEATURES_PLATFORM_UNAVAILABLE.

  JVM_FEATURES_CHECK_AOT
  JVM_FEATURES_CHECK_CDS
  JVM_FEATURES_CHECK_DTRACE
  JVM_FEATURES_CHECK_GRAAL
  JVM_FEATURES_CHECK_JFR
  JVM_FEATURES_CHECK_JVMCI
  JVM_FEATURES_CHECK_SHENANDOAHGC
  JVM_FEATURES_CHECK_STATIC_BUILD
  JVM_FEATURES_CHECK_ZGC

  # Filter out features by default for all variants on certain platforms.
  # Make sure to just add to JVM_FEATURES_PLATFORM_FILTER, since it could
  # have a value already from custom extensions.
  if test "x$OPENJDK_TARGET_OS" = xaix; then
    JVM_FEATURES_PLATFORM_FILTER="$JVM_FEATURES_PLATFORM_FILTER jfr"
  fi

  if test "x$OPENJDK_TARGET_OS-$OPENJDK_TARGET_CPU" = "xlinux-sparcv9"; then
    JVM_FEATURES_PLATFORM_FILTER="$JVM_FEATURES_PLATFORM_FILTER jfr"
  fi
])

AC_DEFUN([JVM_FEATURES_PREPARE_VARIANT],
[
  variant=$1

  # Check which features are unavailable for this JVM variant.
  # This means that is not possible to build these features for this variant.
  if test "x$variant" = "xminimal"; then
    JVM_FEATURES_VARIANT_UNAVAILABLE="cds zero"
  elif test "x$variant" = "xcore"; then
    JVM_FEATURES_VARIANT_UNAVAILABLE="cds minimal zero"
  elif test "x$variant" = "xzero"; then
    JVM_FEATURES_VARIANT_UNAVAILABLE="aot cds compiler1 compiler2 \
        epsilongc g1gc graal jvmci minimal shenandoahgc zgc"
  else
    JVM_FEATURES_VARIANT_UNAVAILABLE="minimal zero"
  fi

  # Check which features should be off by default for this JVM variant.
  if test "x$variant" = "xclient"; then
    JVM_FEATURES_VARIANT_FILTER="aot compiler2 graal jvmci link-time-opt"
  elif test "x$variant" = "xminimal"; then
    JVM_FEATURES_VARIANT_FILTER="aot cds compiler2 dtrace epsilongc g1gc \
        graal jfr jni-check jvmci jvmti management nmt parallelgc services \
        shenandoahgc vm-structs zgc"
    if test "x$OPENJDK_TARGET_CPU" != xarm ; then
      # Only arm-32 should have link-time-opt enabled as default.
      JVM_FEATURES_VARIANT_FILTER="$JVM_FEATURES_VARIANT_FILTER \
          link-time-opt"
    fi
  elif test "x$variant" = "xcore"; then
    JVM_FEATURES_VARIANT_FILTER="aot compiler1 compiler2 graal jvmci \
        link-time-opt"
  elif test "x$variant" = "xzero"; then
    JVM_FEATURES_VARIANT_FILTER="jfr link-time-opt"
  else
    JVM_FEATURES_VARIANT_FILTER="link-time-opt"
  fi
])

AC_DEFUN([JVM_FEATURES_CALCULATE_ACTIVE],
[
  variant=$1

  # As default, start with all valid features, and then remove unavailable
  # features, and those in the platform/variant filters.
  if test "x$variant" != xcustom; then
    BASIC_GET_NON_MATCHING_VALUES(DEFAULT_FOR_VARIANT, $JVM_FEATURES_VALID, $JVM_FEATURES_PLATFORM_UNAVAILABLE $JVM_FEATURES_VARIANT_UNAVAILABLE $JVM_FEATURES_PLATFORM_FILTER $JVM_FEATURES_VARIANT_FILTER)
  else
    # Except for the 'custom' variant, where the default is to start with an
    # empty set.
    DEFAULT_FOR_VARIANT=""
  fi

  # Verify explicitly enabled features
  BASIC_GET_MATCHING_VALUES(ENABLED_BUT_UNAVAILABLE, $JVM_FEATURES, $JVM_FEATURES_PLATFORM_UNAVAILABLE $JVM_FEATURES_VARIANT_UNAVAILABLE)
  if test "x$ENABLED_BUT_UNAVAILABLE" != x; then
    AC_MSG_NOTICE([ERROR: Unavailable JVM features explicitly enabled for '$variant': '$ENABLED_BUT_UNAVAILABLE'])
    AC_MSG_ERROR([Cannot continue])
  fi
  BASIC_GET_MATCHING_VALUES(ENABLED_BUT_DEFAULT, $JVM_FEATURES, $DEFAULT_FOR_VARIANT)
  if test "x$ENABLED_BUT_DEFAULT" != x; then
    AC_MSG_NOTICE([Default JVM features explicitly enabled for '$variant': '$ENABLED_BUT_DEFAULT'])
  fi

  # Verify explicitly disabled features
  BASIC_GET_MATCHING_VALUES(DISABLED_BUT_UNAVAILABLE, $DISABLED_JVM_FEATURES, $JVM_FEATURES_PLATFORM_UNAVAILABLE $JVM_FEATURES_VARIANT_UNAVAILABLE)
  if test "x$DISABLED_BUT_UNAVAILABLE" != x; then
    AC_MSG_NOTICE([Unavailable JVM features explicitly disabled for '$variant': '$DISABLED_BUT_UNAVAILABLE'])
  fi

  # RESULTING_FEATURES is the set of all default features and all explicitly
  # enabled features, with the explicitly disabled features filtered out.
  BASIC_GET_NON_MATCHING_VALUES(RESULTING_FEATURES, $DEFAULT_FOR_VARIANT $JVM_FEATURES, $DISABLED_JVM_FEATURES)
])

###############################################################################
# Helper function for JVM_FEATURES_VERIFY. Check if the specified JVM
# feature is enabled. To be used in shell if constructs, like this:
# if JVM_FEATURES_IS_ACTIVE(jvmti); then
#
# Definition kept in one line to allow inlining in if statements.
# Additional [] needed to keep m4 from mangling shell constructs.
AC_DEFUN([JVM_FEATURES_IS_ACTIVE],
[ [ [[ " $RESULTING_FEATURES " =~ ' '$1' ' ]] ] ])

###############################################################################
# Verify that the resulting set of features is consistent and allowed.
#
AC_DEFUN([JVM_FEATURES_VERIFY],
[
  variant=$1

  # Verify that dependencies are met for inter-feature relations.
  if JVM_FEATURES_IS_ACTIVE(aot) && ! JVM_FEATURES_IS_ACTIVE(graal); then
    AC_MSG_ERROR([Specified JVM feature 'aot' requires feature 'graal' for variant '$variant'])
  fi

  if JVM_FEATURES_IS_ACTIVE(graal) && ! JVM_FEATURES_IS_ACTIVE(jvmci); then
    AC_MSG_ERROR([Specified JVM feature 'graal' requires feature 'jvmci' for variant '$variant'])
  fi

  if JVM_FEATURES_IS_ACTIVE(jvmci) && ! (JVM_FEATURES_IS_ACTIVE(compiler1) || JVM_FEATURES_IS_ACTIVE(compiler2)); then
    AC_MSG_ERROR([Specified JVM feature 'jvmci' requires feature 'compiler2' or 'compiler1' for variant '$variant'])
  fi

  if JVM_FEATURES_IS_ACTIVE(jvmti) && ! JVM_FEATURES_IS_ACTIVE(services); then
    AC_MSG_ERROR([Specified JVM feature 'jvmti' requires feature 'services' for variant '$variant'])
  fi

  if JVM_FEATURES_IS_ACTIVE(management) && ! JVM_FEATURES_IS_ACTIVE(nmt); then
    AC_MSG_ERROR([Specified JVM feature 'management' requires feature 'nmt' for variant '$variant'])
  fi

  # If at least one variant is missing cds, generating classlists is not possible.
  if ! JVM_FEATURES_IS_ACTIVE(cds); then
    CDS_IS_ENABLED="false"
  fi

  # Verify that we have at least one gc selected (i.e., feature named "*gc").
  if ! JVM_FEATURES_IS_ACTIVE(.*gc); then
      AC_MSG_NOTICE([At least one gc needed for variant '$variant'.])
      AC_MSG_NOTICE([Specified features: '$RESULTING_FEATURES'])
      AC_MSG_ERROR([Cannot continue])
  fi

  # Validate features for configure script errors (not user errors)
  BASIC_GET_NON_MATCHING_VALUES(INVALID_FEATURES, $RESULTING_FEATURES, $JVM_FEATURES_VALID)
  if test "x$INVALID_FEATURES" != x; then
    AC_MSG_ERROR([Internal configure script error. Invalid JVM feature(s): $INVALID_FEATURES])
  fi
])

###############################################################################
# Set up all JVM features for each enabled JVM variant.
#
AC_DEFUN_ONCE([JVM_FEATURES_SETUP],
[
  JVM_FEATURES_PREPARE_PLATFORM

  # For classlist generation, we must know if all variants support CDS. Assume
  # so, and disable in JVM_FEATURES_VERIFY if a non-CDS variant is found.
  CDS_IS_ENABLED="true"

  for variant in $JVM_VARIANTS; do
      # Figure out if any features are unavailable, or should be filtered out
      # by default, for this variant.
      # Store the result in JVM_FEATURES_VARIANT_UNAVAILABLE and
      # JVM_FEATURES_VARIANT_FILTER.
      JVM_FEATURES_PREPARE_VARIANT($variant)

      # Calculate the resulting set of enabled features for this variant.
      # The result is stored in RESULTING_FEATURES.
      JVM_FEATURES_CALCULATE_ACTIVE($variant)

      # Verify consistency for RESULTING_FEATURES
      JVM_FEATURES_VERIFY($variant)

      # Keep feature list sorted and free of duplicates
      BASIC_SORT_LIST(RESULTING_FEATURES, $RESULTING_FEATURES)
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
