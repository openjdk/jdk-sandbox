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

import java.util.ListResourceBundle;

/**
 * Snippets used in ListResourceBundleSnippets.
 */ 

final class ListResourceBundleSnippets {

    private class Dimension{
        private Dimension(int x, int y) {

        }
    }

// @start region=snippet1 :
 public class MyResources extends ListResourceBundle {
     protected Object[][] getContents() {
         return new Object[][] {
         // LOCALIZE THIS
             {"s1", "The disk \"{1}\" contains {0}."},  // MessageFormat pattern
             {"s2", "1"},                               // location of {0} in pattern
             {"s3", "My Disk"},                         // sample disk name
             {"s4", "no files"},                        // first ChoiceFormat choice
             {"s5", "one file"},                        // second ChoiceFormat choice
             {"s6", "{0,number} files"},                // third ChoiceFormat choice
             {"s7", "3 Mar 96"},                        // sample date
             {"s8", new Dimension(1,5)}                 // real object, not just string
         // END OF MATERIAL TO LOCALIZE
         };
     }
 }

 public class MyResources_fr extends ListResourceBundle {
     protected Object[][] getContents() {
         return new Object[][] {
         // LOCALIZE THIS
             {"s1", "Le disque \"{1}\" {0}."},          // MessageFormat pattern
             {"s2", "1"},                               // location of {0} in pattern
             {"s3", "Mon disque"},                      // sample disk name
             {"s4", "ne contient pas de fichiers"},     // first ChoiceFormat choice
             {"s5", "contient un fichier"},             // second ChoiceFormat choice
             {"s6", "contient {0,number} fichiers"},    // third ChoiceFormat choice
             {"s7", "3 mars 1996"},                     // sample date
             {"s8", new Dimension(1,3)}                 // real object, not just string
         // END OF MATERIAL TO LOCALIZE
         };
     }
 }
// @end snippet1

}
