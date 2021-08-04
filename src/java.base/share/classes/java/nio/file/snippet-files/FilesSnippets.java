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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/**
 * Snippets used in FilesSnippets.
 */ 

final class FilesSnippets {
     private static final OpenOption APPEND = null;
     private static final OpenOption CREATE = null;
     private static final OpenOption CREATE_NEW = null;
     private static final OpenOption READ = null;
     private static final OpenOption WRITE = null;

     FilesSnippets() throws IOException {
     }

     private static class EnumSet {

          public static Set<? extends OpenOption> of(OpenOption create) {
               return null;
          }

          public static Set<? extends OpenOption> of(OpenOption create, OpenOption append) {
               return null;
          }

          public static Set<? extends OpenOption> of(OpenOption create, OpenOption append,OpenOption write) {
               return null;
          }
     }

     private static void snippet1() throws IOException {
// @start region=snippet1 :
     Path path = null; //@replace regex="null;" replacement="..."

     // truncate and overwrite an existing file, or create the file if
     // it doesn't initially exist
     OutputStream out = Files.newOutputStream(path);

     // append to an existing file, fail if the file does not exist
     out = Files.newOutputStream(path, APPEND);

     // append to an existing file, create file if it doesn't initially exist
     out = Files.newOutputStream(path, CREATE, APPEND);

     // always create new file, failing if it already exists
     out = Files.newOutputStream(path, CREATE_NEW);
// @end snippet1
}

// @start region=snippet2 :
     Path path = null; //@replace regex="null;" replacement="..."

     // open file for reading
     ReadableByteChannel rbc = Files.newByteChannel(path, EnumSet.of(READ));

     // open file for writing to the end of an existing file, creating
     // the file if it doesn't already exist
     WritableByteChannel wbc = Files.newByteChannel(path, EnumSet.of(CREATE,APPEND));

     // create file with initial permissions, opening it for both reading and writing
     FileAttribute<Set<PosixFilePermission>> perms = null; //@replace regex="null;" replacement="..."
     SeekableByteChannel sbc =
         Files.newByteChannel(path, EnumSet.of(CREATE_NEW,READ,WRITE), perms);
// @end snippet2

}
