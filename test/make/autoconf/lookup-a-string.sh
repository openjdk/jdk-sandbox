#!/bin/sh
# @test
# @run shell lookup-a-string.sh

. ${TESTSRC-.}/test-utils.sh

# Just testing the StringInfile function

Setup

Success bash ${CONFIGURE}

StringInFile "JRE_IMAGE_SUBDIR:=j2re-image" $BUILDDIR/spec.gmk

ResultSummary
