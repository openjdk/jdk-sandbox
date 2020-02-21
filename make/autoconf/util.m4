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
# Create a function/macro that takes a series of named arguments. The call is
# similar to AC_DEFUN, but the setup of the function looks like this:
# BASIC_DEFUN_NAMED([MYFUNC], [FOO *BAR], [$@], [
# ... do something
#   AC_MSG_NOTICE([Value of BAR is ARG_BAR])
# ])
# A star (*) in front of a named argument means that it is required and it's
# presence will be verified. To pass e.g. the first value as a normal indexed
# argument, use [m4_shift($@)] as the third argument instead of [$@]. These
# arguments are referenced in the function by their name prefixed by ARG_, e.g.
# "ARG_FOO".
#
# The generated function can be called like this:
# MYFUNC(FOO: [foo-val],
#     BAR: [
#         $ECHO hello world
#     ])
# Note that the argument value must start on the same line as the argument name.
#
# Argument 1: Name of the function to define
# Argument 2: List of legal named arguments, with a * prefix for required arguments
# Argument 3: Argument array to treat as named, typically $@
# Argument 4: The main function body
AC_DEFUN([BASIC_DEFUN_NAMED],
[
  AC_DEFUN($1, [
    m4_foreach(arg, m4_split($2), [
      m4_if(m4_bregexp(arg, [^\*]), -1,
        [
          m4_set_add(legal_named_args, arg)
        ],
        [
          m4_set_add(legal_named_args, m4_substr(arg, 1))
          m4_set_add(required_named_args, m4_substr(arg, 1))
        ]
      )
    ])

    m4_foreach([arg], [$3], [
      m4_define(arg_name, m4_substr(arg, 0, m4_bregexp(arg, [: ])))
      m4_set_contains(legal_named_args, arg_name, [],[AC_MSG_ERROR([Internal error: arg_name is not a valid named argument to [$1]. Valid arguments are 'm4_set_contents(legal_named_args, [ ])'.])])
      m4_set_remove(required_named_args, arg_name)
      m4_set_remove(legal_named_args, arg_name)
      m4_pushdef([ARG_][]arg_name, m4_substr(arg, m4_incr(m4_incr(m4_bregexp(arg, [: ])))))
      m4_set_add(defined_args, arg_name)
      m4_undefine([arg_name])
    ])
    m4_set_empty(required_named_args, [], [
      AC_MSG_ERROR([Internal error: Required named arguments are missing for [$1]. Missing arguments: 'm4_set_contents(required_named_args, [ ])'])
    ])
    m4_foreach([arg], m4_indir([m4_dquote]m4_set_listc([legal_named_args])), [
      m4_pushdef([ARG_][]arg, [])
      m4_set_add(defined_args, arg)
    ])
    m4_set_delete(legal_named_args)
    m4_set_delete(required_named_args)

    # Execute function body
    $4

    m4_foreach([arg], m4_indir([m4_dquote]m4_set_listc([defined_args])), [
      m4_popdef([ARG_][]arg)
    ])

    m4_set_delete(defined_args)
  ])
])

###############################################################################
# Check if a list of space-separated words are selected only from a list of
# space-separated legal words. Typical use is to see if a user-specified
# set of words is selected from a set of legal words.
#
# Sets the specified variable to list of non-matching (offending) words, or to
# the empty string if all words are matching the legal set.
#
# $1: result variable name
# $2: list of values to check
# $3: list of legal values
AC_DEFUN([BASIC_GET_NON_MATCHING_VALUES],
[
  # grep filter function inspired by a comment to http://stackoverflow.com/a/1617326
  # Notice that the original variant fails on SLES 10 and 11
  # Some grep versions (at least bsd) behaves strangely on the base case with
  # no legal_values, so make it explicit.
  values_to_check=`$ECHO $2 | $TR ' ' '\n'`
  legal_values=`$ECHO $3 | $TR ' ' '\n'`
  if test -z "$legal_values"; then
    $1="$2"
  else
    result=`$GREP -Fvx "$legal_values" <<< "$values_to_check" | $GREP -v '^$'`
    $1=${result//$'\n'/ }
  fi
])

###############################################################################
# Check if a list of space-separated words contains any word(s) from a list of
# space-separated illegal words. Typical use is to see if a user-specified
# set of words contains any from a set of illegal words.
#
# Sets the specified variable to list of matching illegal words, or to
# the empty string if no words are matching the illegal set.
#
# $1: result variable name
# $2: list of values to check
# $3: list of illegal values
AC_DEFUN([BASIC_GET_MATCHING_VALUES],
[
  # grep filter function inspired by a comment to http://stackoverflow.com/a/1617326
  # Notice that the original variant fails on SLES 10 and 11
  # Some grep versions (at least bsd) behaves strangely on the base case with
  # no legal_values, so make it explicit.
  values_to_check=`$ECHO $2 | $TR ' ' '\n'`
  illegal_values=`$ECHO $3 | $TR ' ' '\n'`
  if test -z "$illegal_values"; then
    $1=""
  else
    result=`$GREP -Fx "$illegal_values" <<< "$values_to_check" | $GREP -v '^$'`
    $1=${result//$'\n'/ }
  fi
])

###############################################################################
# Sort a space-separated list, and remove duplicates.
#
# Sets the specified variable to the resulting list.
#
# $1: result variable name
# $2: list of values to sort
AC_DEFUN([BASIC_SORT_LIST],
[
  values_to_sort=`$ECHO $2 | $TR ' ' '\n'`
  result=`$SORT -u <<< "$values_to_sort" | $GREP -v '^$'`
  $1=${result//$'\n'/ }
])

###############################################################################
# Test if $1 is a valid argument to $3 (often is $JAVA passed as $3)
# If so, then append $1 to $2 \
# Also set JVM_ARG_OK to true/false depending on outcome.
AC_DEFUN([ADD_JVM_ARG_IF_OK],
[
  $ECHO "Check if jvm arg is ok: $1" >&AS_MESSAGE_LOG_FD
  $ECHO "Command: $3 $1 -version" >&AS_MESSAGE_LOG_FD
  OUTPUT=`$3 $1 $USER_BOOT_JDK_OPTIONS -version 2>&1`
  FOUND_WARN=`$ECHO "$OUTPUT" | $GREP -i warn`
  FOUND_VERSION=`$ECHO $OUTPUT | $GREP " version \""`
  if test "x$FOUND_VERSION" != x && test "x$FOUND_WARN" = x; then
    $2="[$]$2 $1"
    JVM_ARG_OK=true
  else
    $ECHO "Arg failed:" >&AS_MESSAGE_LOG_FD
    $ECHO "$OUTPUT" >&AS_MESSAGE_LOG_FD
    JVM_ARG_OK=false
  fi
])

# Appends a string to a path variable, only adding the : when needed.
AC_DEFUN([BASIC_APPEND_TO_PATH],
[
  if test "x$2" != x; then
    if test "x[$]$1" = x; then
      $1="$2"
    else
      $1="[$]$1:$2"
    fi
  fi
])

# Prepends a string to a path variable, only adding the : when needed.
AC_DEFUN([BASIC_PREPEND_TO_PATH],
[
  if test "x$2" != x; then
    if test "x[$]$1" = x; then
      $1="$2"
    else
      $1="$2:[$]$1"
    fi
  fi
])

################################################################################
# This will make a path absolute. Assumes it's already a unix path. Also
# resolves ~ to homedir.
AC_DEFUN([BASIC_ABSOLUTE_PATH],
[
  if test "x[$]$1" != x; then
    new_path="[$]$1"

    # Use eval to expand a potential ~. This technique does not work if there
    # are spaces in the path (which is valid at this point on Windows), so only
    # try to apply it if there is an actual ~ first in the path.
    if [ [[ "$new_path" = "~"* ]] ]; then
      eval new_path="$new_path"
      if test ! -f "$new_path" && test ! -d "$new_path"; then
        AC_MSG_ERROR([The new_path of $1, which resolves as "$new_path", is not found.])
      fi
    fi

    if test -d "$new_path"; then
      $1="`cd "$new_path"; $THEPWDCMD -L`"
    else
      dir="`$DIRNAME "$new_path"`"
      base="`$BASENAME "$new_path"`"
      $1="`cd "$dir"; $THEPWDCMD -L`/$base"
    fi
  fi
])

###############################################################################
# This will make sure the given variable points to a full and proper
# path. This means:
# 1) There will be no spaces in the path. On unix platforms,
#    spaces in the path will result in an error. On Windows,
#    the path will be rewritten using short-style to be space-free.
# 2) The path will be absolute, and it will be in unix-style (on
#     cygwin).
# $1: The name of the variable to fix
AC_DEFUN([BASIC_FIXUP_PATH],
[
  # Only process if variable expands to non-empty
  if test "x[$]$1" != x; then
    if test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.cygwin"; then
      BASIC_FIXUP_PATH_CYGWIN($1)
    elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.msys"; then
      BASIC_FIXUP_PATH_MSYS($1)
    elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.wsl"; then
      BASIC_FIXUP_PATH_WSL($1)
    else
      # We're on a unix platform. Hooray! :)
      path="[$]$1"
      has_space=`$ECHO "$path" | $GREP " "`
      if test "x$has_space" != x; then
        AC_MSG_NOTICE([The path of $1, which resolves as "$path", is invalid.])
        AC_MSG_ERROR([Spaces are not allowed in this path.])
      fi

      BASIC_ABSOLUTE_PATH(path)
      $1="$path"
    fi
  fi
])

###############################################################################
# This will make sure the given variable points to a executable
# with a full and proper path. This means:
# 1) There will be no spaces in the path. On unix platforms,
#    spaces in the path will result in an error. On Windows,
#    the path will be rewritten using short-style to be space-free.
# 2) The path will be absolute, and it will be in unix-style (on
#     cygwin).
# Any arguments given to the executable is preserved.
# If the input variable does not have a directory specification, then
# it need to be in the PATH.
# $1: The name of the variable to fix
AC_DEFUN([BASIC_FIXUP_EXECUTABLE],
[
  # Only process if variable expands to non-empty

  if test "x[$]$1" != x; then
    if test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.cygwin"; then
      BASIC_FIXUP_EXECUTABLE_CYGWIN($1)
    elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.msys"; then
      BASIC_FIXUP_EXECUTABLE_MSYS($1)
    elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.wsl"; then
      BASIC_FIXUP_EXECUTABLE_WSL($1)
    else
      # We're on a unix platform. Hooray! :)
      # First separate the path from the arguments. This will split at the first
      # space.
      complete="[$]$1"
      path="${complete%% *}"
      tmp="$complete EOL"
      arguments="${tmp#* }"

      # Cannot rely on the command "which" here since it doesn't always work.
      is_absolute_path=`$ECHO "$path" | $GREP ^/`
      if test -z "$is_absolute_path"; then
        # Path to executable is not absolute. Find it.
        IFS_save="$IFS"
        IFS=:
        for p in $PATH; do
          if test -f "$p/$path" && test -x "$p/$path"; then
            new_path="$p/$path"
            break
          fi
        done
        IFS="$IFS_save"
      else
        # This is an absolute path, we can use it without further modifications.
        new_path="$path"
      fi

      if test "x$new_path" = x; then
        AC_MSG_NOTICE([The path of $1, which resolves as "$complete", is not found.])
        has_space=`$ECHO "$complete" | $GREP " "`
        if test "x$has_space" != x; then
          AC_MSG_NOTICE([This might be caused by spaces in the path, which is not allowed.])
        fi
        AC_MSG_ERROR([Cannot locate the the path of $1])
      fi
    fi

    # Now join together the path and the arguments once again
    if test "x$arguments" != xEOL; then
      new_complete="$new_path ${arguments% *}"
    else
      new_complete="$new_path"
    fi

    if test "x$complete" != "x$new_complete"; then
      $1="$new_complete"
      AC_MSG_NOTICE([Rewriting $1 to "$new_complete"])
    fi
  fi
])

###############################################################################
AC_DEFUN([BASIC_REMOVE_SYMBOLIC_LINKS],
[
  if test "x$OPENJDK_BUILD_OS" != xwindows; then
    # Follow a chain of symbolic links. Use readlink
    # where it exists, else fall back to horribly
    # complicated shell code.
    if test "x$READLINK_TESTED" != yes; then
      # On MacOSX there is a readlink tool with a different
      # purpose than the GNU readlink tool. Check the found readlink.
      READLINK_ISGNU=`$READLINK --version 2>&1 | $GREP GNU`
      # If READLINK_ISGNU is empty, then it's a non-GNU readlink. Don't use it.
      READLINK_TESTED=yes
    fi

    if test "x$READLINK" != x && test "x$READLINK_ISGNU" != x; then
      $1=`$READLINK -f [$]$1`
    else
      # Save the current directory for restoring afterwards
      STARTDIR=$PWD
      COUNTER=0
      sym_link_dir=`$DIRNAME [$]$1`
      sym_link_file=`$BASENAME [$]$1`
      cd $sym_link_dir
      # Use -P flag to resolve symlinks in directories.
      cd `$THEPWDCMD -P`
      sym_link_dir=`$THEPWDCMD -P`
      # Resolve file symlinks
      while test $COUNTER -lt 20; do
        ISLINK=`$LS -l $sym_link_dir/$sym_link_file | $GREP '\->' | $SED -e 's/.*-> \(.*\)/\1/'`
        if test "x$ISLINK" == x; then
          # This is not a symbolic link! We are done!
          break
        fi
        # Again resolve directory symlinks since the target of the just found
        # link could be in a different directory
        cd `$DIRNAME $ISLINK`
        sym_link_dir=`$THEPWDCMD -P`
        sym_link_file=`$BASENAME $ISLINK`
        let COUNTER=COUNTER+1
      done
      cd $STARTDIR
      $1=$sym_link_dir/$sym_link_file
    fi
  fi
])

###############################################################################
# Register a --with argument but mark it as deprecated
# $1: The name of the with argument to deprecate, not including --with-
AC_DEFUN([BASIC_DEPRECATED_ARG_WITH],
[
  AC_ARG_WITH($1, [AS_HELP_STRING([--with-$1],
      [Deprecated. Option is kept for backwards compatibility and is ignored])],
      [AC_MSG_WARN([Option --with-$1 is deprecated and will be ignored.])])
])

###############################################################################
# Register a --enable argument but mark it as deprecated
# $1: The name of the with argument to deprecate, not including --enable-
# $2: The name of the argument to deprecate, in shell variable style (i.e. with _ instead of -)
# $3: Messages to user.
AC_DEFUN([BASIC_DEPRECATED_ARG_ENABLE],
[
  AC_ARG_ENABLE($1, [AS_HELP_STRING([--enable-$1],
      [Deprecated. Option is kept for backwards compatibility and is ignored])])
  if test "x$enable_$2" != x; then
    AC_MSG_WARN([Option --enable-$1 is deprecated and will be ignored.])

    if test "x$3" != x; then
      AC_MSG_WARN([$3])
    fi

  fi
])

###############################################################################
# Register an --enable-* argument as an alias for another argument.
# $1: The name of the enable argument for the new alias, not including --enable-
# $2: The full name of the argument of which to make this an alias, including
#     --enable- or --with-.
AC_DEFUN([BASIC_ALIASED_ARG_ENABLE],
[
  AC_ARG_ENABLE($1, [AS_HELP_STRING([--enable-$1], [alias for $2])], [
    # Use m4 to strip initial -- from target ($2), convert - to _, prefix enable_
    # to new alias name, and create a shell variable assignment,
    # e.g.: enable_old_style="$enable_new_alias"
    translit(patsubst($2, --), -, _)="$[enable_]translit($1, -, _)"
  ])
])

###############################################################################
# Test that variable $1 denoting a program is not empty. If empty, exit with an error.
# $1: variable to check
AC_DEFUN([BASIC_CHECK_NONEMPTY],
[
  if test "x[$]$1" = x; then
    AC_MSG_ERROR([Could not find required tool for $1])
  fi
])

###############################################################################
# Setup a tool for the given variable. If correctly specified by the user,
# use that value, otherwise search for the tool using the supplied code snippet.
# $1: variable to set
# $2: code snippet to call to look for the tool
# $3: code snippet to call if variable was used to find tool
AC_DEFUN([BASIC_SETUP_TOOL],
[
  # Publish this variable in the help.
  AC_ARG_VAR($1, [Override default value for $1])

  if [[ -z "${$1+x}" ]]; then
    # The variable is not set by user, try to locate tool using the code snippet
    $2
  else
    # The variable is set, but is it from the command line or the environment?

    # Try to remove the string !$1! from our list.
    try_remove_var=${CONFIGURE_OVERRIDDEN_VARIABLES//!$1!/}
    if test "x$try_remove_var" = "x$CONFIGURE_OVERRIDDEN_VARIABLES"; then
      # If it failed, the variable was not from the command line. Ignore it,
      # but warn the user (except for BASH, which is always set by the calling BASH).
      if test "x$1" != xBASH; then
        AC_MSG_WARN([Ignoring value of $1 from the environment. Use command line variables instead.])
      fi
      # Try to locate tool using the code snippet
      $2
    else
      # If it succeeded, then it was overridden by the user. We will use it
      # for the tool.

      # First remove it from the list of overridden variables, so we can test
      # for unknown variables in the end.
      CONFIGURE_OVERRIDDEN_VARIABLES="$try_remove_var"

      tool_override=[$]$1
      AC_MSG_NOTICE([User supplied override $1="$tool_override"])

      # Check if we try to supply an empty value
      if test "x$tool_override" = x; then
        AC_MSG_CHECKING([for $1])
        AC_MSG_RESULT([disabled])
      else
        # Split up override in command part and argument part
        tool_and_args=($tool_override)
        [ tool_command=${tool_and_args[0]} ]
        [ unset 'tool_and_args[0]' ]
        [ tool_args=${tool_and_args[@]} ]

        # Check if the provided tool contains a complete path.
        tool_basename="${tool_command##*/}"
        if test "x$tool_basename" = "x$tool_command"; then
          # A command without a complete path is provided, search $PATH.
          AC_MSG_NOTICE([Will search for user supplied tool "$tool_basename"])
          AC_PATH_PROG($1, $tool_basename)
          if test "x[$]$1" = x; then
            AC_MSG_ERROR([User supplied tool $1="$tool_basename" could not be found])
          fi
        else
          # Otherwise we believe it is a complete path. Use it as it is.
          AC_MSG_NOTICE([Will use user supplied tool "$tool_command"])
          AC_MSG_CHECKING([for $tool_command])
          if test ! -x "$tool_command"; then
            AC_MSG_RESULT([not found])
            AC_MSG_ERROR([User supplied tool $1="$tool_command" does not exist or is not executable])
          fi
           $1="$tool_command"
          AC_MSG_RESULT([found])
        fi
        if test "x$tool_args" != x; then
          # If we got arguments, re-append them to the command after the fixup.
          $1="[$]$1 $tool_args"
        fi
      fi
    fi
    $3
  fi
])

###############################################################################
# Call BASIC_SETUP_TOOL with AC_PATH_PROGS to locate the tool
# $1: variable to set
# $2: executable name (or list of names) to look for
# $3: [path]
AC_DEFUN([BASIC_PATH_PROGS],
[
  BASIC_SETUP_TOOL($1, [AC_PATH_PROGS($1, $2, , $3)])
])

###############################################################################
# Call BASIC_SETUP_TOOL with AC_CHECK_TOOLS to locate the tool
# $1: variable to set
# $2: executable name (or list of names) to look for
AC_DEFUN([BASIC_CHECK_TOOLS],
[
  BASIC_SETUP_TOOL($1, [AC_CHECK_TOOLS($1, $2)])
])

###############################################################################
# Like BASIC_PATH_PROGS but fails if no tool was found.
# $1: variable to set
# $2: executable name (or list of names) to look for
# $3: [path]
AC_DEFUN([BASIC_REQUIRE_PROGS],
[
  BASIC_PATH_PROGS($1, $2, , $3)
  BASIC_CHECK_NONEMPTY($1)
])

###############################################################################
# Like BASIC_SETUP_TOOL but fails if no tool was found.
# $1: variable to set
# $2: autoconf macro to call to look for the special tool
AC_DEFUN([BASIC_REQUIRE_SPECIAL],
[
  BASIC_SETUP_TOOL($1, [$2])
  BASIC_CHECK_NONEMPTY($1)
])

###############################################################################
# Like BASIC_REQUIRE_PROGS but also allows for bash built-ins
# $1: variable to set
# $2: executable name (or list of names) to look for
# $3: [path]
AC_DEFUN([BASIC_REQUIRE_BUILTIN_PROGS],
[
  BASIC_SETUP_TOOL($1, [AC_PATH_PROGS($1, $2, , $3)])
  if test "x[$]$1" = x; then
    AC_MSG_NOTICE([Required tool $2 not found in PATH, checking built-in])
    if help $2 > /dev/null 2>&1; then
      AC_MSG_NOTICE([Found $2 as shell built-in. Using it])
      $1="$2"
    else
      AC_MSG_ERROR([Required tool $2 also not found as built-in.])
    fi
  fi
  BASIC_CHECK_NONEMPTY($1)
])

