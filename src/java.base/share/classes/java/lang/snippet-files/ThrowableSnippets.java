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

/**
 * Snippets used in ThrowableSnippets.
 */ 

final class ThrowableSnippets {
// @start region=snippet5 :
 public class Junk {
     public static void main(String args[]) {
         try {
             a();
         } catch(HighLevelException e) {
             e.printStackTrace();
         }
     }
     static void a() throws HighLevelException {
         try {
             b();
         } catch(MidLevelException e) {
             //throw new HighLevelException(e); //@replace regex="//" replacement=""
         }
     }
     static void b() throws MidLevelException {
         c();
     }
     static void c() throws MidLevelException {
         try {
             d();
         } catch(LowLevelException e) {
             //throw new MidLevelException(e); //@replace regex="//" replacement=""
         }
     }
     static void d() throws LowLevelException {
        e();
     }
     static void e() throws LowLevelException {
         //throw new LowLevelException(); //@replace regex="//" replacement=""
     }
 }

 class HighLevelException extends Exception {
     HighLevelException(Throwable cause) { super(cause); }
 }

 class MidLevelException extends Exception {
     MidLevelException(Throwable cause)  { super(cause); }
 }

 class LowLevelException extends Exception {
 }
// @end snippet5

}
