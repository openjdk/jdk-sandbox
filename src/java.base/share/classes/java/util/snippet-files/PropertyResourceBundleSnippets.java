/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.text.MessageFormat;

/**
 * Snippets used in PropertyResourceBundleSnippets.
 */ 

final class PropertyResourceBundleSnippets {
// @start region=snippet1 :
 // MessageFormat pattern
 //s1=Die Platte \"{1}\" enth&auml;lt {0}. //@replace regex="//" replacement=""

 // location of {0} in pattern
 //s2=1 //@replace regex="//" replacement=""

 // sample disk name
 //s3=Meine Platte //@replace regex="//" replacement=""

 // first ChoiceFormat choice
 //s4=keine Dateien //@replace regex="//" replacement=""

 // second ChoiceFormat choice
 //s5=eine Datei //@replace regex="//" replacement=""

 // third ChoiceFormat choice
 //s6={0,number} Dateien //@replace regex="//" replacement=""

 // sample date
 //s7=3. M\u00e4rz 1996 //@replace regex="//" replacement=""
// @end snippet1

}
