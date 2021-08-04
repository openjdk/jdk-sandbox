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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.ArrayList;
import java.util.List;

/**
 * Snippets used in BootstrapCallInfoSnippets.
 */ 

final class BootstrapCallInfoSnippets {
  private class BootstrapCallInfo<Object>{

    private Object invocationName() { return null; }
    private Object invocationType() { return null; }
    private Object get(int i) { return null; }
    private int size() { return 0; }

    public <E> List<E> asList() { return null;}
  }

// @start region=snippet1 :
static Object genericBSM(Lookup lookup, BootstrapCallInfo<Object> bsci)
    throws Throwable {

  ArrayList<Object> args = new ArrayList<>();
  args.add(lookup);
  args.add(bsci.invocationName());
  args.add(bsci.invocationType());

  MethodHandle bsm = (MethodHandle) bsci.get(0);
  List<Object> restOfArgs = bsci.asList().subList(1, bsci.size());

  // the next line eagerly resolves all remaining static arguments:
  args.addAll(restOfArgs);
  return bsm.invokeWithArguments(args);
}
// @end snippet1


}
