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

###############################################################################
# Terminology used in this file:
#
# Valid features      == All possible features that the JDK knows about.
# Deprecated features == Previously known features (not considered valid).
# Available features  == Features that are possible to use in this configuration.
# Default features    == Features that are on by default in this configuration.
# Enabled features    == Features requested by the user to be present.
# Disabled features   == Features excluded from being used by the user.
# Active features     == The exact set of features to be used.
#
# All valid features are considered available, unless listed as unavailable.
# All available features will be turned on as default, unless listed in a filter.
###############################################################################

# We need these as m4 defines to be able to loop over them using m4 later on.

# All valid JDK features, regardless of platform
define(jdk_features_valid, m4_normalize( \
    ifdef([custom_jdk_features_valid], custom_jdk_features_valid) \
    \
    asan cds-archive cds-classlist full-desktop java-coverage link-time-gc \
    native-coverage serviceability-agent signed static-build unlimited-crypto \
))

# Deprecated JDK features (these are ignored, but with a warning)
# Currently empty, but stays as placeholder.
define(jdk_features_deprecated, m4_normalize(
))


###############################################################################
# Parse command line options for JDK feature selection. After this function
# has run $JDK_FEATURES_ENABLED, $JDK_FEATURES_DISABLED and $JDK_FEATURES_VALID
# can be used.
#
AC_DEFUN_ONCE([JDK_FEATURES_PARSE_OPTIONS],
[
  # Setup shell variables from the m4 lists
  BASIC_SORT_LIST(JDK_FEATURES_VALID, "jdk_features_valid")
  BASIC_SORT_LIST(JDK_FEATURES_DEPRECATED, "jdk_features_deprecated")

  # First check for features using the
  # --with-jdk-features="<[-]feature>[,<[-]feature> ...]" syntax.
  AC_ARG_WITH([jdk-features], [AS_HELP_STRING([--with-jdk-features],
      [JDK features to enable (foo) or disable (-foo), separated by comma. Use
      '--help' to show possible values @<:@none@:>@])])
  if test "x$with_jdk_features" != x; then
    # Replace ","  with " ".
    user_jdk_feature_list=${with_jdk_features//,/ }
    JDK_FEATURES_ENABLED=`$ECHO $user_jdk_feature_list | \
        $AWK '{ for (i=1; i<=NF; i++) if (!match($i, /^-.*/)) printf("%s ", $i) }'`
    JDK_FEATURES_DISABLED=`$ECHO $user_jdk_feature_list | \
        $AWK '{ for (i=1; i<=NF; i++) if (match($i, /^-.*/)) printf("%s ", substr($i, 2))}'`

    # Verify that the user has provided only valid (or deprecated) features
    BASIC_GET_NON_MATCHING_VALUES(invalid_features, $JDK_FEATURES_ENABLED \
        $JDK_FEATURES_DISABLED, $JDK_FEATURES_VALID $JDK_FEATURES_DEPRECATED)
    if test "x$invalid_features" != x; then
      AC_MSG_NOTICE([Unknown JDK features specified: '$invalid_features'])
      AC_MSG_NOTICE([The available JDK features are: '$JDK_FEATURES_VALID'])
      AC_MSG_ERROR([Cannot continue])
    fi

    # Check if the user has provided deprecated features
    BASIC_GET_MATCHING_VALUES(deprecated_features, $JDK_FEATURES_ENABLED \
        $JDK_FEATURES_DISABLED, $JDK_FEATURES_DEPRECATED)
    if test "x$deprecated_features" != x; then
      AC_MSG_WARN([Deprecated JDK features specified (will be ignored): '$deprecated_features'])
      # Filter out deprecated features
      BASIC_GET_NON_MATCHING_VALUES(JDK_FEATURES_ENABLED, \
          $JDK_FEATURES_ENABLED, $deprecated_features)
      BASIC_GET_NON_MATCHING_VALUES(JDK_FEATURES_DISABLED, \
          $JDK_FEATURES_DISABLED, $deprecated_features)
    fi
  fi

  # Then check for features using the "--enable-jdk-feature-<feature>" syntax.
  # Using m4, loop over all features with the variable FEATURE.
  m4_foreach(FEATURE, m4_split(jdk_features_valid), [
    AC_ARG_ENABLE(jdk-feature-FEATURE, AS_HELP_STRING(
        [--enable-jdk-feature-FEATURE], [enable jdk feature 'FEATURE']))

    # Create an m4 variable containing a shell variable name (like
    # "enable_jdk_feature_static_build").
    define(FEATURE_SHELL, [enable_jdk_feature_]translit(FEATURE, -, _))

    if test "x$FEATURE_SHELL" = xyes; then
      JDK_FEATURES_ENABLED="$JDK_FEATURES_ENABLED FEATURE"
    elif test "x$FEATURE_SHELL" = xno; then
      JDK_FEATURES_DISABLED="$JDK_FEATURES_DISABLED FEATURE"
    elif test "x$FEATURE_SHELL" != x; then
      AC_MSG_ERROR([Invalid value for --enable-jdk-feature-FEATURE: '$FEATURE_SHELL'])
    fi

    undefine([FEATURE_SHELL])
  ])

  # Likewise, check for deprecated arguments.
  m4_foreach(FEATURE, m4_split(jdk_features_deprecated), [
    AC_ARG_ENABLE(jdk-feature-FEATURE, AS_HELP_STRING(
        [--enable-jdk-feature-FEATURE], [enable jdk feature 'FEATURE'
         (deprecated)]))

    define(FEATURE_SHELL, [enable_jdk_feature_]translit(FEATURE, -, _))

    if test "x$FEATURE_SHELL" != x; then
      AC_MSG_WARN([Deprecated JDK feature, will be ignored: --enable-jdk-feature-FEATURE])
    fi

    undefine([FEATURE_SHELL])
  ])

  # Warn if the user has both enabled and disabled a feature
  # If this happens, disable will override enable.
  BASIC_GET_MATCHING_VALUES(enabled_and_disabled, $JDK_FEATURES_ENABLED, \
      $JDK_FEATURES_DISABLED)
  if test "x$enabled_and_disabled" != x; then
    AC_MSG_WARN([Disabling of these features will override enabling: '$enabled_and_disabled'])
  fi

  # Clean up lists and announce results to user
  BASIC_SORT_LIST(JDK_FEATURES_ENABLED, $JDK_FEATURES_ENABLED)
  AC_MSG_CHECKING([for JDK features enabled by the user])
  if test "x$JDK_FEATURES_ENABLED" != x; then
    AC_MSG_RESULT(['$JDK_FEATURES_ENABLED'])
  else
    AC_MSG_RESULT([none])
  fi

  BASIC_SORT_LIST(JDK_FEATURES_DISABLED, $JDK_FEATURES_DISABLED)
  AC_MSG_CHECKING([for JDK features disabled by the user])
  if test "x$JDK_FEATURES_DISABLED" != x; then
    AC_MSG_RESULT(['$JDK_FEATURES_DISABLED'])
  else
    AC_MSG_RESULT([none])
  fi

  # Makefiles use VALID_JDK_FEATURES in check-jdk-feature to verify correctness.
  VALID_JDK_FEATURES="$JDK_FEATURES_VALID"
  AC_SUBST(VALID_JDK_FEATURES)
])

###############################################################################
# Helper function for the JDK_FEATURES_CHECK_* suite.
# The code in the code block should assign 'false' to the variable AVAILABLE
# if the feature is not available, and this function will handle everything
# else that is needed.
#
# arg 1: The name of the feature to test
# arg 2: The code block to execute
#
AC_DEFUN([JDK_FEATURES_CHECK_AVAILABILITY],
[
  # Assume that feature is available
  AVAILABLE=true

  # Execute feature test block
  $2

  AC_MSG_CHECKING([if JDK feature '$1' is available])
  if test "x$AVAILABLE" = "xtrue"; then
    AC_MSG_RESULT([yes])
  else
    AC_MSG_RESULT([no])
    JDK_FEATURES_UNAVAILABLE="$JDK_FEATURES_UNAVAILABLE $1"
  fi
])

###############################################################################
# Check if the feature 'asan' is available on this platform.
#
AC_DEFUN_ONCE([JDK_FEATURES_CHECK_ASAN],
[
  JDK_FEATURES_CHECK_AVAILABILITY(asan, [
    AC_MSG_CHECKING([if compiler supports ASan])
    if test "x$TOOLCHAIN_TYPE" = "xgcc" || \
        test "x$TOOLCHAIN_TYPE" = "xclang"; then
      AC_MSG_RESULT([yes])
    else
      AC_MSG_RESULT([no, $TOOLCHAIN_TYPE])
      AVAILABLE=false
    fi
  ])
])

###############################################################################
# Check if the feature 'cds-archive' is available on this platform.
#
AC_DEFUN_ONCE([JDK_FEATURES_CHECK_CDS_ARCHIVE],
[
  JDK_FEATURES_CHECK_AVAILABILITY(cds-archive, [
    AC_MSG_CHECKING([if we can create a default CDS archive])
    if test "x$ENABLE_CDS" = "xfalse"; then
      AC_MSG_RESULT([no, CDS is disabled])
      AVAILABLE=false
    elif test "x$COMPILE_TYPE" = "xcross"; then
      AC_MSG_RESULT([no, cross compiling])
      AVAILABLE=false
    else
      AC_MSG_RESULT([yes])
    fi
  ])
])

###############################################################################
# Check if the feature 'cds-classlist' is available on this platform.
#
AC_DEFUN_ONCE([JDK_FEATURES_CHECK_CDS_CLASSLIST],
[
  JDK_FEATURES_CHECK_AVAILABILITY(cds-classlist, [
    AC_MSG_CHECKING([if we can create a CDS class list])
    if test "x$ENABLE_CDS" = "xfalse"; then
      AC_MSG_RESULT([no, CDS is disabled])
      AVAILABLE=false
    else
      AC_MSG_RESULT([yes])
    fi
  ])
])

###############################################################################
# Check if the feature 'java-coverage' is available on this platform.
#
AC_DEFUN_ONCE([JDK_FEATURES_CHECK_JAVA_COVERAGE],
[
  JDK_FEATURES_CHECK_AVAILABILITY(java-coverage, [
    AC_MSG_CHECKING([if jcov is present])
    if test "x$JCOV_HOME" != "x"; then
      AC_MSG_RESULT([yes])
    else
      AC_MSG_RESULT([no, use --with-jcov])
      AVAILABLE=false
    fi
  ])
])

###############################################################################
# Check if the feature 'link-time-gc' is available on this platform.
#
AC_DEFUN_ONCE([JDK_FEATURES_CHECK_LINK_TIME_GC],
[
  JDK_FEATURES_CHECK_AVAILABILITY(link-time-gc, [
    AC_MSG_CHECKING([if compiler supports link time GC])
    if test "x$TOOLCHAIN_TYPE" = "xgcc"; then
      AC_MSG_RESULT([yes])
    else
      AC_MSG_RESULT([no, $TOOLCHAIN_TYPE])
      AVAILABLE=false
    fi
  ])
])

###############################################################################
# Check if the feature 'native-coverage' is available on this platform.
#
AC_DEFUN_ONCE([JDK_FEATURES_CHECK_NATIVE_COVERAGE],
[
  JDK_FEATURES_CHECK_AVAILABILITY(native-coverage, [
    AC_MSG_CHECKING([if compiler supports gcov])
    if test "x$TOOLCHAIN_TYPE" = "xgcc" || \
        test "x$TOOLCHAIN_TYPE" = "xclang"; then
      AC_MSG_RESULT([yes])
    else
      AC_MSG_RESULT([no, $TOOLCHAIN_TYPE])
      AVAILABLE=false
    fi
  ])
])

###############################################################################
# Check if the feature 'serviceability-agent' is available on this platform.
#
AC_DEFUN_ONCE([JDK_FEATURES_CHECK_SERVICEABILITY_AGENT],
[
  JDK_FEATURES_CHECK_AVAILABILITY(serviceability-agent, [
    AC_MSG_CHECKING([if Serviceability Agent (SA) is supported])
    if HOTSPOT_CHECK_JVM_VARIANT(zero); then
      AC_MSG_RESULT([no, building JVM variant zero])
      AVAILABLE=false
    elif test "x$OPENJDK_TARGET_OS" = xaix ; then
      AC_MSG_RESULT([no, $OPENJDK_TARGET_OS])
      AVAILABLE=false
    elif test "x$OPENJDK_TARGET_CPU" = xs390x ; then
      AC_MSG_RESULT([no, $OPENJDK_TARGET_CPU])
      AVAILABLE=false
    else
      AC_MSG_RESULT([yes])
    fi
  ])
])


###############################################################################
# Check if the feature 'signed' is available on this platform.
#
AC_DEFUN_ONCE([JDK_FEATURES_CHECK_SIGNED],
[
  JDK_FEATURES_CHECK_AVAILABILITY(signed, [
    AC_MSG_CHECKING([if the build can be signed])
    # FIXME
    if HOTSPOT_CHECK_JVM_VARIANT(zero); then
      AC_MSG_RESULT([no, building JVM variant zero])
      AVAILABLE=false
    elif test "x$OPENJDK_TARGET_OS" = xaix ; then
      AC_MSG_RESULT([no, $OPENJDK_TARGET_OS])
      AVAILABLE=false
    elif test "x$OPENJDK_TARGET_CPU" = xs390x ; then
      AC_MSG_RESULT([no, $OPENJDK_TARGET_CPU])
      AVAILABLE=false
    else
      AC_MSG_RESULT([yes])
    fi
  ])
])

###############################################################################
# Check if the feature 'static-build' is available on this platform.
#
AC_DEFUN_ONCE([JDK_FEATURES_CHECK_STATIC_BUILD],
[
  JDK_FEATURES_CHECK_AVAILABILITY(static-build, [
    AC_MSG_CHECKING([if we can build a static lib version])
    if test "x$OPENJDK_TARGET_OS" = "xmacosx"; then
      AC_MSG_RESULT([yes])
    else
      AC_MSG_RESULT([no, $OPENJDK_TARGET_OS])
      AVAILABLE=false
    fi
  ])
])

###############################################################################
# Setup JDK_FEATURES_UNAVAILABLE and JDK_FEATURES_FILTER to contain those
# features that are unavailable, or should be off by default, for this
# platform.
#
AC_DEFUN_ONCE([JDK_FEATURES_PREPARE],
[
  # The checks below should add unavailable features to
  # JDK_FEATURES_UNAVAILABLE.

  JDK_FEATURES_CHECK_ASAN
  JDK_FEATURES_CHECK_CDS_ARCHIVE
  JDK_FEATURES_CHECK_CDS_CLASSLIST
  JDK_FEATURES_CHECK_JAVA_COVERAGE
  JDK_FEATURES_CHECK_LINK_TIME_GC
  JDK_FEATURES_CHECK_NATIVE_COVERAGE
  JDK_FEATURES_CHECK_SERVICEABILITY_AGENT
  JDK_FEATURES_CHECK_SIGNED
  JDK_FEATURES_CHECK_STATIC_BUILD

  # Make sure to just add to JDK_FEATURES_FILTER, since it could have a value
  # already from custom extensions.

  # Never enable any of these features as default, only on request.
  JDK_FEATURES_FILTER="$JDK_FEATURES_FILTER asan \
      java-coverage native-coverage static-build"

  # Only use link-time-gc as default for linux-s390x
  if test "x$OPENJDK_TARGET_OS-$OPENJDK_TARGET_CPU" != "xlinux-s390x"; then
    JDK_FEATURES_FILTER="$JDK_FEATURES_FILTER link-time-gc"
  fi

  # FIXME: signed???
])

###############################################################################
# Calculate the actual set of active JDK features. Store the result in
# JDK_FEATURES_ACTIVE.
#
AC_DEFUN([JDK_FEATURES_CALCULATE_ACTIVE],
[
  # The default is set to all valid features except those unavailable or listed
  # in a filter.
  BASIC_GET_NON_MATCHING_VALUES(default_features, $JDK_FEATURES_VALID, \
      $JDK_FEATURES_UNAVAILABLE $JDK_FEATURES_FILTER)

  # Verify that explicitly enabled features are available
  BASIC_GET_MATCHING_VALUES(enabled_but_unavailable, $JDK_FEATURES_ENABLED, \
      $JDK_FEATURES_UNAVAILABLE)
  if test "x$enabled_but_unavailable" != x; then
    AC_MSG_NOTICE([ERROR: Unavailable JDK features explicitly enabled: '$enabled_but_unavailable'])
    AC_MSG_ERROR([Cannot continue])
  fi

  # Notify the user if their command line options has no real effect
  BASIC_GET_MATCHING_VALUES(enabled_but_default, $JDK_FEATURES_ENABLED, \
      $default_features)
  if test "x$enabled_but_default" != x; then
    AC_MSG_NOTICE([Default JDK features explicitly enabled: '$enabled_but_default'])
  fi
  BASIC_GET_MATCHING_VALUES(disabled_but_unavailable, $JDK_FEATURES_DISABLED, \
      $JDK_FEATURES_UNAVAILABLE)
  if test "x$disabled_but_unavailable" != x; then
    AC_MSG_NOTICE([Unavailable JDK features explicitly disabled: '$disabled_but_unavailable'])
  fi

  # JDK_FEATURES_ACTIVE is the set of all default features and all explicitly
  # enabled features, with the explicitly disabled features filtered out.
  BASIC_GET_NON_MATCHING_VALUES(JDK_FEATURES_ACTIVE, $default_features \
      $JDK_FEATURES_ENABLED, $JDK_FEATURES_DISABLED)
])


###############################################################################
# Set up all JDK features. Requires that JDK_FEATURES_PARSE_OPTIONS has been
# called.
#
AC_DEFUN_ONCE([JDK_FEATURES_SETUP],
[
  # Set up JDK_FEATURES_UNAVAILABLE and JDK_FEATURES_FILTER.
  JDK_FEATURES_PREPARE

  # Calculate the resulting set of enabled features. The result is stored in
  # JDK_FEATURES_ACTIVE.
  JDK_FEATURES_CALCULATE_ACTIVE

  # Keep feature list sorted and free of duplicates.
  BASIC_SORT_LIST(JDK_FEATURES, $JDK_FEATURES_ACTIVE)
  AC_MSG_CHECKING([JDK features to use])
  AC_MSG_RESULT(['$JDK_FEATURES'])

  AC_SUBST(JDK_FEATURES)
])
