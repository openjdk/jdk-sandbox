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

import java.lang.invoke.*;

/**
 * Snippets used in CallSiteSnippets.
 */ 

final class CallSiteSnippets {
// @start region=snippet1 :
  static void test () throws Throwable {
    // THE FOLLOWING LINE IS PSEUDOCODE FOR A JVM INSTRUCTION
    //InvokeDynamic[#bootstrapDynamic].baz("baz arg", 2, 3.14);
  }

  private static void printArgs (Object...args){
    System.out.println(java.util.Arrays.deepToString(args));
  }

  private static MethodHandle printArgs; //@replace regex="static" replacement="static final"

  static {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    Class thisClass = lookup.lookupClass();  // (who am I?)
    try { //@replace replacement=""
      printArgs = lookup.findStatic(thisClass,
              "printArgs", MethodType.methodType(void.class, Object[].class));
    } catch (Exception e) {e.printStackTrace();} //@replace replacement=""
  }

  private static CallSite bootstrapDynamic (MethodHandles.Lookup caller, String name, MethodType type){
    // ignore caller and name, but match the type:
    return new ConstantCallSite(printArgs.asType(type));}
// @end snippet1


}
