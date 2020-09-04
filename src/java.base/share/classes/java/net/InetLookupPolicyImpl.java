/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package java.net;

import sun.security.action.GetPropertyAction;

import java.net.spi.InetLookupPolicy;

import static java.net.spi.InetLookupPolicy.AddressFamily.ANY;
import static java.net.spi.InetLookupPolicy.AddressFamily.IPV4;
import static java.net.spi.InetLookupPolicy.AddressFamily.IPV6;

/**
 * An address lookup policy initialized from {@code "java.net.preferIPv4Stack"},
 * {@code "java.net.preferIPv6Addresses"} system property values, and O/S configuration.
 */
class InetLookupPolicyImpl implements InetLookupPolicy {

    // Addresses family initialized from java.net system property values
    private final AddressFamily family;

    // Addresses order initialized from java.net system property values
    private final AddressesOrder order;

    // "java.net.preferIPv4Stack" system property value
    private static final String PREFER_IPV4_VALUE;

    // "java.net.preferIPv6Addresses" system property value
    private static final String PREFER_IPV6_VALUE;

    // Read the system property values
    static {
        PREFER_IPV4_VALUE =
                GetPropertyAction.privilegedGetProperty("java.net.preferIPv4Stack");
        PREFER_IPV6_VALUE =
                GetPropertyAction.privilegedGetProperty("java.net.preferIPv6Addresses");
    }

    static final InetLookupPolicy PLATFORM = new InetLookupPolicyImpl();

    // Restrict instantiation by other classes
    private InetLookupPolicyImpl() {
        family = initializeFamily();
        order = initializeOrder();
    }

    @Override
    public AddressFamily getAddressesFamily() {
        return family;
    }

    @Override
    public AddressesOrder getAddressesOrder() {
        return order;
    }

    /* Initialize the addresses family field. The following information is used
     * to make a decision about the supported address families:
     *     a) java.net.preferIPv4Stack system property value
     *     b) IPV4 availability is checked by ipv4_available call
     *     c) Type of InetAddress.impl instance
     */
    private AddressFamily initializeFamily() {
        boolean ipv4Available = isIPv4Available();
        if ("true".equals(PREFER_IPV4_VALUE) && ipv4Available) {
            return IPV4;
        }
        // Check if IPv6 is not supported
        if (InetAddress.impl instanceof Inet4AddressImpl) {
            return IPV4;
        }
        // Check if system supports IPv4, if not return IPv6
        if (!ipv4Available) {
            return IPV6;
        }
        return ANY;
    }

    // Initialize the order of addresses
    private AddressesOrder initializeOrder() {
        return switch (family) {
            case IPV4, IPV6 -> AddressesOrder.SYSTEM;
            case ANY -> getOrderForAnyAddressFamily();
        };
    }

    // Initializes the order of addresses for case when ANY address family is selected.
    private AddressesOrder getOrderForAnyAddressFamily() {
        // Logic here is identical to the initialization in InetAddress static initializer
        if (PREFER_IPV6_VALUE == null) {
            return AddressesOrder.IPV4_FIRST;
        } else if (PREFER_IPV6_VALUE.equalsIgnoreCase("true")) {
            return AddressesOrder.IPV6_FIRST;
        } else if (PREFER_IPV6_VALUE.equalsIgnoreCase("false")) {
            return AddressesOrder.IPV4_FIRST;
        } else if (PREFER_IPV6_VALUE.equalsIgnoreCase("system")) {
            return AddressesOrder.SYSTEM;
        } else {
            return AddressesOrder.IPV4_FIRST;
        }
    }


    private static native boolean isIPv4Available();
}
