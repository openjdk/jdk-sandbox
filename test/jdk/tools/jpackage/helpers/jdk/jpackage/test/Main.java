/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import static jdk.jpackage.test.TestBuilder.CMDLINE_ARG_PREFIX;


public final class Main {
    public static void main(String args[]) throws Throwable {
        boolean listTests = false;
        List<TestInstance> tests = new ArrayList<>();
        try (TestBuilder testBuilder = new TestBuilder(tests::add)) {
            for (var arg : args) {
                if (TKit.VERBOSE_TEST_SETUP) {
                    TKit.log(String.format("Parsing [%s]...", arg));
                }

                if ((CMDLINE_ARG_PREFIX + "list").equals(arg)) {
                    listTests = true;
                    continue;
                }

                boolean success = false;
                try {
                    testBuilder.processCmdLineArg(arg);
                    success = true;
                } catch (Throwable throwable) {
                    TKit.unbox(throwable);
                } finally {
                    if (!success) {
                        TKit.log(
                                String.format("Error processing parameter=[%s]",
                                        arg));
                    }
                }
            }
        }

        // Order tests by their full names to have stable test sequence.
        tests = tests.stream().sorted((a, b) -> a.fullName().compareTo(
                b.fullName())).collect(Collectors.toList());

        if (listTests) {
            // Just list the tests
            tests.stream().forEach(test -> System.out.println(test.fullName()));
            return;
        }

        TKit.runTests(tests);

        final long failedCount = tests.stream().filter(Functional.identity(
                TestInstance::passed).negate()).count();
        final long passedCount = tests.size() - failedCount;

        TKit.log(String.format("[==========] %d tests ran", tests.size()));
        TKit.log(String.format("[  PASSED  ] %d %s", passedCount,
                passedCount == 1 ? "test" : "tests"));
        if (failedCount != 0) {
            TKit.log(String.format("[  FAILED  ] %d %s, listed below",
                    failedCount,
                    failedCount == 1 ? "test" : "tests"));
            tests.stream().filter(
                    Functional.identity(TestInstance::passed).negate()).forEach(
                    test -> TKit.log(String.format("[  FAILED  ] %s",
                            test.fullName())));

            final String errorMessage = String.format("%d FAILED %s",
                    failedCount, failedCount == 1 ? "TEST" : "TESTS");
            TKit.log(errorMessage);
            throw new RuntimeException(errorMessage);
        }
    }
}
