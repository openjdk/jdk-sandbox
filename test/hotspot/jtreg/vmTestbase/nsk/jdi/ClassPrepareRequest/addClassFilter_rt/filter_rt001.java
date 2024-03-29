/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
 */

package nsk.jdi.ClassPrepareRequest.addClassFilter_rt;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;
import java.io.*;

/**
 * The test for the implementation of an object of the type
 * ClassPrepareRequest.
 *
 * The test checks that results of the method
 * <code>com.sun.jdi.ClassPrepareRequest.addClassFilter_rt()</code>
 * complies with its spec.
 *
 * The test checks up on the following assertion:
 *    Restricts the events generated by this request to be
 *    the preparation  of the given reference type and any subtypes.
 *    An event will be generated for any prepared reference type
 *    that can be safely cast to the given reference type.
 *
 * The test works as follows.
 * - The debugger resumes the debuggee and waits for the BreakpointEvent.
 * - The debuggee creates a class filter object, to load the Class to
 *   filter, and invokes the methodForCommunication to be suspended and
 *   to inform the debugger with the event.
 * - Upon getting the BreakpointEvent, the debugger
 *   - gets ReferenceType for the Class to filter,
 *   - sets up a ClassPrepareRequest and, using the ReferenceType,
 *     restricts it to sub-classes of the Class to filter,
 *     thus restricting the ClassPrepare event only to the thread1,
 *   - resumes debuggee's main thread, and
 *   - waits for the event.
 * - The debuggee creates and starts two threads, thread1 and thread2,
 *   first of them will create an object of sub-Class to be filtered,
 *   whereas second will create an object to be not filtered.
 * - Upon getting the event, the debugger performs the checks required.
 */

public class filter_rt001 extends TestDebuggerType1 {

    public static void main (String argv[]) {
        int result = run(argv,System.out);
        if (result != 0) {
            throw new RuntimeException("TEST FAILED with result " + result);
        }
    }

    public static int run (String argv[], PrintStream out) {
        debuggeeName = "nsk.jdi.ClassPrepareRequest.addClassFilter_rt.filter_rt001a";
        return new filter_rt001().runThis(argv, out);
    }

    private String testedClassName =
        "nsk.jdi.ClassPrepareRequest.addClassFilter_rt.filter_rt001aTestClass10";


    protected void testRun() {
        EventRequest  eventRequest1      = null;
        String        property1          = "ClassPrepareRequest1";
        ReferenceType testClassReference = null;

        for (int i = 0; ; i++) {

            if (!shouldRunAfterBreakpoint()) {
                vm.resume();
                break;
            }

            display(":::::: case: # " + i);

            switch (i) {

                case 0:
                testClassReference = (ReferenceType) vm.classesByName(testedClassName).get(0);

                eventRequest1 = setting21ClassPrepareRequest(testClassReference,
                                      EventRequest.SUSPEND_NONE, property1);

                display("......waiting for ClassPrepareEvent in expected thread");
                Event newEvent = eventHandler.waitForRequestedEvent(new EventRequest[]{eventRequest1}, waitTime, true);

                if ( !(newEvent instanceof ClassPrepareEvent)) {
                    setFailedStatus("ERROR: new event is not ClassPrepareEvent");
                } else {

                    String property = (String) newEvent.request().getProperty("number");
                    display("       got new ClassPrepareEvent with property 'number' == " + property);

                    if ( !property.equals(property1) ) {
                        setFailedStatus("ERROR: property is not : " + property1);
                    }
                }
                break;

                default:
                throw new Failure("** default case 2 **");
            }
        }
        return;
    }

    private ClassPrepareRequest setting21ClassPrepareRequest ( ReferenceType  testedClass,
                                                               int            suspendPolicy,
                                                               String         property       ) {
        try {
            display("......setting up ClassPrepareRequest:");
            display("       class: " + testedClass + "; property: " + property);

            ClassPrepareRequest
            cpr = eventRManager.createClassPrepareRequest();
            cpr.putProperty("number", property);
            cpr.addClassFilter(testedClass);
            cpr.setSuspendPolicy(suspendPolicy);

            display("      ClassPrepareRequest has been set up");
            return cpr;
        } catch ( Exception e ) {
            throw new Failure("** FAILURE to set up ClassPrepareRequest **");
        }
    }

}
