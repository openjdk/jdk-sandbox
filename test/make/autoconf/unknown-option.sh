#!/bin/sh
# @test
# @run shell unknown-option.sh

. ${TESTSRC-.}/test-utils.sh

Setup

Failure bash ${CONFIGURE} -xxx

ResultSummary
