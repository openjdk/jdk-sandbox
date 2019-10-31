/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.dns.client.internal;

import jdk.dns.client.AddressFamily;
import jdk.dns.client.ex.DnsInvalidAttributeIdentifierException;

public class ResourceClassType {
    int rrclass;
    int rrtype;

    ResourceClassType(int rrtype) {
        this.rrclass = ResourceRecord.CLASS_INTERNET;
        this.rrtype = rrtype;
    }

    public int getRecordClass() {
        return rrclass;
    }

    public int getRecordType() {
        return rrtype;
    }

    public static ResourceClassType fromAddressFamily(AddressFamily addressFamily) {
        ResourceClassType[] cts;
        try {
            cts = ResourceClassType.attrIdsToClassesAndTypes(typeFromAddressFamily(addressFamily));
        } catch (DnsInvalidAttributeIdentifierException e) {
            return new ResourceClassType(ResourceRecord.QTYPE_STAR);
        }
        return ResourceClassType.getClassAndTypeToQuery(cts);
    }

    private static String[] typeFromAddressFamily(AddressFamily addressFamily) {
        switch (addressFamily) {
            case IPv4:
                return new String[]{"A", "CNAME"};
            case IPv6:
                return new String[]{"AAAA", "CNAME"};
            default:
                return new String[]{"A", "AAAA", "CNAME"};
        }
    }


    public static ResourceClassType[] attrIdsToClassesAndTypes(String[] attrIds)
            throws DnsInvalidAttributeIdentifierException {
        if (attrIds == null) {
            return null;
        }
        ResourceClassType[] cts = new ResourceClassType[attrIds.length];

        for (int i = 0; i < attrIds.length; i++) {
            cts[i] = fromAttrId(attrIds[i]);
        }
        return cts;
    }

    private static ResourceClassType fromAttrId(String attrId)
            throws DnsInvalidAttributeIdentifierException {

        if (attrId.isEmpty()) {
            throw new DnsInvalidAttributeIdentifierException(
                    "Attribute ID cannot be empty");
        }
        int rrclass;
        int rrtype;
        int space = attrId.indexOf(' ');

        // class
        if (space < 0) {
            rrclass = ResourceRecord.CLASS_INTERNET;
        } else {
            String className = attrId.substring(0, space);
            rrclass = ResourceRecord.getRrclass(className);
            if (rrclass < 0) {
                throw new DnsInvalidAttributeIdentifierException(
                        "Unknown resource record class '" + className + '\'');
            }
        }

        // type
        String typeName = attrId.substring(space + 1);
        rrtype = ResourceRecord.getType(typeName);
        if (rrtype < 0) {
            throw new DnsInvalidAttributeIdentifierException(
                    "Unknown resource record type '" + typeName + '\'');
        }

        return new ResourceClassType(rrtype);
    }

    /*
     * Returns the most restrictive resource record class and type
     * that may be used to query for records matching cts.
     * See classAndTypeMatch() for matching rules.
     */
    public static ResourceClassType getClassAndTypeToQuery(ResourceClassType[] cts) {
        int rrclass;
        int rrtype;

        if (cts == null) {
            // Query all records.
            throw new RuntimeException("Internal DNS resolver error");
        } else if (cts.length == 0) {
            // No records are requested, but we need to ask for something.
            rrtype = ResourceRecord.QTYPE_STAR;
        } else {
            rrclass = ResourceRecord.CLASS_INTERNET;
            rrtype = cts[0].rrtype;
            for (int i = 1; i < cts.length; i++) {
                if (rrclass != cts[i].rrclass) {
                    throw new RuntimeException("Internal error: Only CLASS_INTERNET is supported");
                }
                if (rrtype != cts[i].rrtype) {
                    rrtype = ResourceRecord.QTYPE_STAR;
                }
            }
        }
        return new ResourceClassType(rrtype);
    }
}
