/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static java.net.spi.InetNameService.LookupPolicy.IPV4;
import static java.net.spi.InetNameService.LookupPolicy.IPV4_FIRST;
import static java.net.spi.InetNameService.LookupPolicy.IPV6;
import static java.net.spi.InetNameService.LookupPolicy.IPV6_FIRST;

import jtreg.SkippedException;
import jdk.test.lib.net.IPSupport;
import org.testng.annotations.Test;
import org.testng.Assert;

/*
 * @test
 * @summary Test that system properties affecting an order and a type of queried addresses
 *          are properly mapped to a lookup characteristic value.
 * @library lib providers/simple /test/lib
 * @build test.library/testlib.ResolutionRegistry simple.provider/insp.SimpleNameServiceProviderImpl
 *        jdk.test.lib.net.IPSupport LookupPolicyMappingTest
 * @run testng/othervm LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=true LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=system LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=true LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=false -Djava.net.preferIPv6Addresses=true LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=false -Djava.net.preferIPv6Addresses=false LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=false -Djava.net.preferIPv6Addresses=system LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=false -Djava.net.preferIPv6Addresses LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack=false LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack -Djava.net.preferIPv6Addresses=true LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack -Djava.net.preferIPv6Addresses=false LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack -Djava.net.preferIPv6Addresses=system LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack -Djava.net.preferIPv6Addresses LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv4Stack LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv6Addresses=true LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv6Addresses=false LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv6Addresses=system LookupPolicyMappingTest
 * @run testng/othervm -Djava.net.preferIPv6Addresses LookupPolicyMappingTest
 */

public class LookupPolicyMappingTest {

    @Test
    public void testSystemProper() throws Exception {
        IPSupport.printPlatformSupport(System.err);

        // TODO: Check if platform has interfaces with IPv4 and IPv6 addresses. IPSupport.hasIPv4/hasIPv6 cannot
        // be used here since system properties affect them.

        System.err.println("javaTest.org resolved to:" + Arrays.deepToString(InetAddress.getAllByName("javaTest.org")));

        // Acquire runtime characteristics from the test NSP
        int runtimeCharacteristics = insp.SimpleNameServiceProviderImpl.lastLookupPolicy().characteristics();

        // Calculate expected lookup policy characteristic
        String preferIPv4Stack = System.getProperty("java.net.preferIPv4Stack");
        String preferIPv6Addresses = System.getProperty("java.net.preferIPv6Addresses");
        String expectedResultsKey = calcKey(preferIPv4Stack, preferIPv6Addresses);
        int expectedCharacteristics = EXPECTED_RESULTS_MAP.get(expectedResultsKey);

        Assert.assertTrue(characteristicsMatch(runtimeCharacteristics, expectedCharacteristics), "Unexpected LookupPolicy observed");
    }

    /*
     *  Each row describes a combination of 'preferIPv4Stack', 'preferIPv6Addresses'
     *  values and the expected characteristic value
     * <preferIPv4Stack value> [true/false/""/null], <preferIPv6Addresses value> [true/false/system/""/null], "Expected characteristic value"
     */
    private static Object[][] EXPECTED_RESULTS_TABLE = {
            {"true", "true", IPV4},
            {"true", "false", IPV4},
            {"true", "system", IPV4},
            {"true", "", IPV4},
            {"true", null, IPV4},

            {"false", "true", IPV4 | IPV6 | IPV6_FIRST},
            {"false", "false", IPV4 | IPV6 | IPV4_FIRST},
            {"false", "system", IPV4 | IPV6},
            {"false", "", IPV4 | IPV6 | IPV4_FIRST},
            {"false", null, IPV4 | IPV6 | IPV4_FIRST},

            {"", "true", IPV4 | IPV6 | IPV6_FIRST},
            {"", "false", IPV4 | IPV6 | IPV4_FIRST},
            {"", "system", IPV4 | IPV6},
            {"", "", IPV4 | IPV6 | IPV4_FIRST},
            {"", null, IPV4 | IPV6 | IPV4_FIRST},

            {null, "true", IPV4 | IPV6 | IPV6_FIRST},
            {null, "false", IPV4 | IPV6 | IPV4_FIRST},
            {null, "system", IPV4 | IPV6},
            {null, "", IPV4 | IPV6 | IPV4_FIRST},
            {null, null, IPV4 | IPV6 | IPV4_FIRST},
    };

    private static final Map<String, Integer> EXPECTED_RESULTS_MAP = calculateExpectedCharacteristics();

    private static Map<String, Integer> calculateExpectedCharacteristics() {
        return Arrays.stream(EXPECTED_RESULTS_TABLE)
                .collect(Collectors.toUnmodifiableMap(
                        entry -> calcKey((String) entry[0], (String) entry[1]),
                        entry -> (Integer) entry[2])
                );
    }

    private static String calcKey(String ipv4stack, String ipv6addresses) {
        return ipv4stack + "_" + ipv6addresses;
    }

    private static boolean characteristicsMatch(int runtime, int calculated) {
        System.err.printf("Comparing characteristics:%n\t   Runtime: %s%n\tCalculated: %s%n",
                Integer.toBinaryString(runtime),
                Integer.toBinaryString(calculated));
        return (runtime & calculated) == calculated;
    }


}
