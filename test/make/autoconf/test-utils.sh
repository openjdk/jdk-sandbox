#!/bin/sh

# BUILDDIR is set to where configure will be run
# CONFIGURE is set to the configure script to test
# TEMPOUT is a temporary file logging stdout
# TEMPERR is a temporary file logging stderr

GREP=grep

Setup() {
# If TESTSRC not set, use current directory. TESTSRC is set by jtreg
    BUILDDIR=${TESTSRC-$PWD}/`mktemp -d build.XXXXXXXXX`

# Set the configure script to test relative to BUILDDIR.
    CONFIGURE=${BUILDDIR}/../../../../configure

# Set temporary files for stdout and stderr from the configure script execution
    TEMPOUT=`mktemp`
    TEMPERR=`mktemp`

# Create build directory to run configure in
    Sys rm -rf ${BUILDDIR} 
    Sys mkdir ${BUILDDIR}
    Sys cd ${BUILDDIR}

# Clean up
    trap 'Cleanup' EXIT
}

# Run command and verify that it returned an expected result code != 0
# $1 = command

Failure() {
    Test failure "$@"
}

# Run command and verify that it returned an expected result code = 0
# $1 = command
Success() {
    Test success "$@"
}

# Verify the existence of a certain string in a file
# $1 = string to look for (using "grep" format)
# $2 = file to look in

StringInFile() {
    AdditionalTest success "$GREP" "$1" "$2"
}

# Verify that a certain string doesn't exist in a file
# $1 = string to look for (using "grep" format)
# $2 = file to look in

StringNotInFile() {
    AdditionalTest failure "$GREP" "$1" "$2"
}

# Present a summary of the results
ResultSummary() {
    HorizontalRule
    if test -n "$failed"; then
	count=`printf "%s" "$failed" | wc -c | tr -d ' '`
	echo "FAIL: $count tests failed"
	exit 1
    else
	echo "PASS: all tests gave expected results"
	exit 0
    fi
}

#
# Local functions.
# The functions below should normally not be called from the test scripts
#
Cleanup() {
    HorizontalRule
    Sys rm -rf ${BUILDDIR}
    Sys rm ${TEMPOUT} ${TEMPERR}
}

# Copied from Util.sh
failed=""

Fail() { 
    echo "FAIL: $1"
    failed="${failed}."
}

Die() { 
    printf "%s\n" "$*"
    exit 1
}

Sys() {
    printf "%s\n" "$*"
    "$@"
    rc="$?"
    test "$rc" -eq 0 || Die "Command \"$*\" failed with exitValue $rc"
}

HorizontalRule() {
    echo "-----------------------------------------------------------------"
}

# Result (pass/fail) based on result code ($?) from the test run.
# $1 = expected result (success/failure) based on result code from the executed command
# $2 = result code from test run

Report() {
    test "$#" != 2 && Die "Usage: Report testtype rc"

    if test "$1" = "success" -a "$2" = 0; then
	echo "PASS: succeeded as expected"
    elif test "$1" = "success" -a "$2" != 0; then
	Fail "test failed unexpectedly"

    elif test "$1" = "failure" -a "$2" != 0; then
	echo "PASS: failed as expected"
    elif test "$1" = "failure" -a "$2" = 0; then
	Fail "test succeeded unexpectedly"
    else

	Die "Usage: Report testtype rc"
    fi
}

# Run test and log stdout and stderr to temporary files $TEMPOUT and $TEMPERR
# $1 = expected result (success/failure) 
# $2 = command
Test() {
    HorizontalRule
    expectedResult="$1"
    shift
    printf "%s\n" "$*"
# We need to redirect to temp files to be able to look for errors in the stdout and stderr. But
# we also need to send it to console so jtreg can pick it up. 3>&1 1>&2 2>&3 swaps stdout and stderr since 
# tee only takes stdout. Then we swap it back to get correct stdout and stderr for jtreg.
# Then we need to get the $? from the command that's run in the subshell so we store it in a temp file (if
# we could use bash we could use $PIPESTATUS instead, but jtreg only uses 'sh')
    
    (s=`mktemp` ; (("$@" ; echo $?>$s) | tee $TEMPOUT) 3>&1 1>&2 2>&3 | tee $TEMPERR; exit $(cat $s; rm $s)) 3>&2 2>&1 1>&3
    Report "$expectedResult" "$?"
}

# Run test without logging stdout and stderr to temporary files
# $1 = expected result (success/failure) 
# $2 = command
AdditionalTest() {
    HorizontalRule
    expectedResult="$1"
    shift
    printf "%s\n" "$*"
    "$@"
    Report "$expectedResult" "$?"
}

