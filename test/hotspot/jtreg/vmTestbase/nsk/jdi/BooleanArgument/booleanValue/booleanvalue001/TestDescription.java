/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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


/*
 * @test
 *
 * @summary converted from VM Testbase nsk/jdi/BooleanArgument/booleanValue/booleanvalue001.
 * VM Testbase keywords: [quick, jpda, jdi]
 * VM Testbase readme:
 * DESCRIPTION:
 *     The test for the implementation of an object of the type
 *     Connector.BooleanArgument.
 *     The test checks up that a result of the method
 *     com.sun.jdi.connect.Connector.BooleanArgument.booleanValue()
 *     complies with its specification.
 *     The test works as follows:
 *     - Virtual Machine Manager is invoked.
 *     - First Connector.BooleanArgument object is searched among
 *     Connectors.
 *     If no the argument is found out the test exits
 *     with the return value = 95 and a warning message.
 *     - The following checks are applied:
 *     setValue(true); setValue("true");
 *     IF    !argument.booleanValue();
 *     THEN  an error detected
 *     ELSE  the check passed
 *     setValue(true); setValue("false");
 *     IF    argument.booleanValue();
 *     THEN  an error detected
 *     ELSE  the check passed
 *     setValue(false); setValue("true");
 *     IF    !argument.booleanValue();
 *     THEN  an error detected
 *     ELSE  the check passed
 *     setValue(false); setValue("false");
 *     IF    argument.booleanValue();
 *     THEN  an error detected
 *     ELSE  the check passed
 *     setValue("true"); setValue(true);
 *     IF    !argument.booleanValue()
 *     THEN  an error detected
 *     ELSE  the check passed
 *     setValue("true"); setValue(false);
 *     IF    argument.booleanValue()
 *     THEN  an error detected
 *     ELSE  the check passed
 *     setValue("false"); setValue(true);
 *     IF    !argument.booleanValue()
 *     THEN  an error detected
 *     ELSE  the check passed
 *     setValue("false"); setValue(false);
 *     IF    argument.booleanValue()
 *     THEN  an error detected
 *     ELSE  the check passed
 *     In case of error the test produces the return value 97 and
 *     a corresponding error message(s).
 *     Otherwise, the test is passed and produces
 *     the return value 95 and no message.
 * COMMENTS:
 *
 * @library /vmTestbase
 *          /test/lib
 * @build nsk.jdi.BooleanArgument.booleanValue.booleanvalue001
 * @run driver
 *      nsk.jdi.BooleanArgument.booleanValue.booleanvalue001
 *      -verbose
 *      -arch=${os.family}-${os.simpleArch}
 *      -waittime=5
 *      -debugee.vmkind=java
 *      -transport.address=dynamic
 *      -debugee.vmkeys="${test.vm.opts} ${test.java.opts}"
 */

