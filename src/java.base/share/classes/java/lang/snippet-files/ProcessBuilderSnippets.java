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

import java.io.*;
import java.util.Arrays;
import java.util.List;

/**
 * Snippets used in ProcessBuilderSnippets.
 */ 

final class ProcessBuilderSnippets {
private static void snippet1() throws IOException {
// @start region=snippet1 :
 String directory = "/home/duke/src";
 ProcessBuilder[] builders = {
              new ProcessBuilder("find", directory, "-type", "f"),
              new ProcessBuilder("xargs", "grep", "-h", "^import "),
              new ProcessBuilder("awk", "{print $2;}"),
              new ProcessBuilder("sort", "-u")};

 List<Process> processes = ProcessBuilder.startPipeline(
         Arrays.asList(builders));

 Process last = processes.get(processes.size()-1);

 try (InputStream is = last.getInputStream();
      Reader isr = new InputStreamReader(is);
      BufferedReader r = new BufferedReader(isr)) {
     long count = r.lines().count();
 }
// @end snippet1
}

}
