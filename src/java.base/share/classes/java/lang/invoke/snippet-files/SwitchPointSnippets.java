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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.ArrayList;
import java.util.List;

/**
 * Snippets used in SwitchPointSnippets.
 */ 

final class SwitchPointSnippets {


private void snippet1() throws NoSuchMethodException, IllegalAccessException {
// @start region=snippet1 :
 MethodHandle MH_strcat = MethodHandles.lookup()
     .findVirtual(String.class, "concat", MethodType.methodType(String.class, String.class));
 SwitchPoint spt = new SwitchPoint();
 assert(!spt.hasBeenInvalidated());
 // the following steps may be repeated to re-use the same switch point:
 MethodHandle worker1 = MH_strcat;
 MethodHandle worker2 = MethodHandles.permuteArguments(MH_strcat, MH_strcat.type(), 1, 0);
 MethodHandle worker = spt.guardWithTest(worker1, worker2);
 //assertEquals("method", (String) worker.invokeExact("met", "hod")); //@replace regex="//" replacement=""
 SwitchPoint.invalidateAll(new SwitchPoint[]{ spt });
 assert(spt.hasBeenInvalidated());
 //assertEquals("hodmet", (String) worker.invokeExact("met", "hod")); //@replace regex="//" replacement=""
// @end snippet1
}

// @start region=snippet2 :
 public class SwitchPoint {
    private static final MethodHandle
            K_true = MethodHandles.constant(boolean.class, true),
            K_false = MethodHandles.constant(boolean.class, false);
    private final MutableCallSite mcs;
    private final MethodHandle mcsInvoker;

    public SwitchPoint() {
        this.mcs = new MutableCallSite(K_true);
        this.mcsInvoker = mcs.dynamicInvoker();
    }

    public MethodHandle guardWithTest(
            MethodHandle target, MethodHandle fallback) {
        // Note:  mcsInvoker is of type ()boolean.
        // Target and fallback may take any arguments, but must have the same type.
        return MethodHandles.guardWithTest(this.mcsInvoker, target, fallback);
    }

    public static void invalidateAll(SwitchPoint[] spts) {
        List<MutableCallSite> mcss = new ArrayList<>();
        for (SwitchPoint spt : spts) mcss.add(spt.mcs);
        for (MutableCallSite mcs : mcss) mcs.setTarget(K_false);
        MutableCallSite.syncAll(mcss.toArray(new MutableCallSite[0]));
    }

    //} //@replace regex="//" replacement=""
// @end snippet2
    public boolean hasBeenInvalidated() {
        return true;
    }
}
}
