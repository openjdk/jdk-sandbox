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

import jdk.dns.client.ex.DnsResolverException;

import java.util.Vector;


/**
 * The ResourceRecords class represents the resource records in the
 * four sections of a DNS message.
 * <p>
 * The additional records section is currently ignored.
 *
 * @author Scott Seligman
 */


public class ResourceRecords {

    // Four sections:  question, answer, authority, additional.
    // The question section is treated as being made up of (shortened)
    // resource records, although this isn't technically how it's defined.
    Vector<ResourceRecord> question = new Vector<>();
    public Vector<ResourceRecord> answer = new Vector<>();
    Vector<ResourceRecord> authority = new Vector<>();
    // TODO: Try to reuse additional section to avoid additional CNAME lookups
    public Vector<ResourceRecord> additional = new Vector<>();


    public boolean hasAddressOrAlias() {
        return answer.stream().anyMatch(
                rr -> rr.rrtype == ResourceRecord.TYPE_A
                        || rr.rrtype == ResourceRecord.TYPE_AAAA
                        || rr.rrtype == ResourceRecord.TYPE_CNAME);
    }

    /*
     * True if these resource records are from a zone transfer.  In
     * that case only answer records are read (as per
     * draft-ietf-dnsext-axfr-clarify-02.txt).  Also, the rdata of
     * those answer records is not decoded (for efficiency) except
     * for SOA records.
     */
    boolean zoneXfer;

    /*
     * Returns a representation of the resource records in a DNS message.
     * Does not modify or store a reference to the msg array.
     */
    ResourceRecords(byte[] msg, int msgLen, Header hdr, boolean zoneXfer)
            throws DnsResolverException {
        if (zoneXfer) {
            answer.ensureCapacity(8192);        // an arbitrary "large" number
        }
        this.zoneXfer = zoneXfer;
        add(msg, msgLen, hdr);
    }

    /*
     * Returns the type field of the first answer record, or -1 if
     * there are no answer records.
     */
    int getFirstAnsType() {
        if (answer.size() == 0) {
            return -1;
        }
        return answer.firstElement().getType();
    }

    /*
     * Returns the type field of the last answer record, or -1 if
     * there are no answer records.
     */
    int getLastAnsType() {
        if (answer.size() == 0) {
            return -1;
        }
        return answer.lastElement().getType();
    }

    /*
     * Decodes the resource records in a DNS message and adds
     * them to this object.
     * Does not modify or store a reference to the msg array.
     */
    void add(byte[] msg, int msgLen, Header hdr) throws DnsResolverException {

        ResourceRecord rr;
        int pos = Header.HEADER_SIZE;   // current offset into msg

        try {
            for (int i = 0; i < hdr.numQuestions; i++) {
                rr = new ResourceRecord(msg, msgLen, pos, true, false);
                if (!zoneXfer) {
                    question.addElement(rr);
                }
                pos += rr.size();
            }

            for (int i = 0; i < hdr.numAnswers; i++) {
                rr = new ResourceRecord(
                        msg, msgLen, pos, false, !zoneXfer);
                answer.addElement(rr);
                pos += rr.size();
            }

            if (zoneXfer) {
                return;
            }

            for (int i = 0; i < hdr.numAuthorities; i++) {
                rr = new ResourceRecord(msg, msgLen, pos, false, true);
                authority.addElement(rr);
                pos += rr.size();
            }

            // TODO: Might be useful
            // The additional records section is currently ignored.
//            for (int i = 0; i < hdr.numAdditionals; i++) {
//                rr = new ResourceRecord(msg, msgLen, pos, false, true);
//                additional.addElement(rr);
//                pos += rr.size();
//            }

        } catch (IndexOutOfBoundsException e) {
            throw new DnsResolverException(
                    "DNS error: corrupted message");
        }
    }
}
