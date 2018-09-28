#!/bin/bash -e
#
# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
# Create a bundle in the current directory, containing what's needed to
# build and run JMH microbenchmarks from the OpenJDK build.

TMPDIR=`mktemp -d -t jmh-XXXX`
trap "rm -rf \"$TMPDIR\"" EXIT

ORIG_DIR=`pwd`
cd "$TMPDIR"

JMH_VERSION=1.21
COMMONS_MATH3_VERSION=3.2
JOPT_SIMPLE_VERSION=4.6

BUNDLE_NAME=jmh-$JMH_VERSION.tar.gz

wget http://central.maven.org/maven2/org/apache/commons/commons-math3/$COMMONS_MATH3_VERSION/commons-math3-$COMMONS_MATH3_VERSION.jar
wget http://central.maven.org/maven2/net/sf/jopt-simple/jopt-simple/$JOPT_SIMPLE_VERSION/jopt-simple-$JOPT_SIMPLE_VERSION.jar
wget http://central.maven.org/maven2/org/openjdk/jmh/jmh-core/$JMH_VERSION/jmh-core-$JMH_VERSION.jar
wget http://central.maven.org/maven2/org/openjdk/jmh/jmh-generator-annprocess/$JMH_VERSION/jmh-generator-annprocess-$JMH_VERSION.jar

tar -cvzf ../$BUNDLE_NAME *
cp ../$BUNDLE_NAME "$ORIG_DIR"
