/*
 * Copyright (c) 2017, Red Hat, Inc. and/or its affiliates.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

package sun.jvm.hotspot.gc.epsilon;

import java.io.*;
import java.util.*;

import sun.jvm.hotspot.gc.shared.CollectedHeap;
import sun.jvm.hotspot.gc.shared.CollectedHeapName;
import sun.jvm.hotspot.code.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.memory.*;

public class EpsilonHeap extends CollectedHeap {

  private static Field spaceField;
  private VirtualSpace space;

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static void initialize(TypeDataBase db) {
    Type type = db.lookupType("EpsilonHeap");
    spaceField = type.getField("_virtual_space");
  }

  public EpsilonHeap(Address addr) {
    super(addr);
    space = new VirtualSpace(addr.addOffsetTo(spaceField.getOffset()));
  }

  @Override
  public CollectedHeapName kind() {
    return CollectedHeapName.EPSILON;
  }

  public VirtualSpace space() {
    return space;
  }

  @Override
  public void printOn(PrintStream tty) {
     MemRegion mr = reservedRegion();
     tty.println("Epsilon heap");
     tty.println(" reserved: [" + mr.start() + ", " + mr.end() + "]");
     tty.println(" committed: [" + space.low() + ", " + space.high() + "]");
  }

}
