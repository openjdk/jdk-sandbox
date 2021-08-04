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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.*;
import java.util.List;

/**
 * Snippets used in AclFileAttributeViewSnippets.
 */ 

final class AclFileAttributeViewSnippets {
     private Path file;


     private void snippet1() throws IOException {
          // @start region=snippet1 :
          // lookup "joe"
          UserPrincipal joe = file.getFileSystem().getUserPrincipalLookupService()
                  .lookupPrincipalByName("joe");

          // get view
          AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);

          // create ACE to give "joe" read access
          AclEntry entry = AclEntry.newBuilder()
                  .setType(AclEntryType.ALLOW)
                  .setPrincipal(joe)
                  .setPermissions(AclEntryPermission.READ_DATA, AclEntryPermission.READ_ATTRIBUTES)
                  .build();

          // read ACL, insert ACE, re-write ACL
          List<AclEntry> acl = view.getAcl();


          acl.add(0, entry);   // insert before any DENY entries
          view.setAcl(acl);
     // @end snippet1
     }

}
