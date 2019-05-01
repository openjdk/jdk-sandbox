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

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Class to present one Ldap message.
 */
public class LdapMessage {
    private final byte[] messages;
    private int messageID;
    private Operation operation;

    public enum Operation {
        BIND_REQUEST(0x60, "BindRequest"), // [APPLICATION 0]
        BIND_RESPONSE(0x61, "BindResponse"), // [APPLICATION 1]
        UNBIND_REQUEST(0x42, "UnbindRequest"), // [APPLICATION 2]
        SEARCH_REQUEST(0x63, "SearchRequest"), // [APPLICATION 3]
        SEARCH_RESULT_ENTRY(0x64, "SearchResultEntry"), // [APPLICATION 4]
        SEARCH_RESULT_DONE(0x65, "SearchResultDone"), // [APPLICATION 5]
        MODIFY_REQUEST(0x66, "ModifyRequest"), // [APPLICATION 6]
        MODIFY_RESPONSE(0x67, "ModifyResponse"), // [APPLICATION 7]
        ADD_REQUEST(0x68, "AddRequest"), // [APPLICATION 8]
        ADD_RESPONSE(0x69, "AddResponse"), // [APPLICATION 9]
        DELETE_REQUEST(0x4A, "DeleteRequest"), // [APPLICATION 10]
        DELETE_RESPONSE(0x6B, "DeleteResponse"), // [APPLICATION 11]
        MODIFY_DN_REQUEST(0x6C, "ModifyDNRequest"), // [APPLICATION 12]
        MODIFY_DN_RESPONSE(0x6D, "ModifyDNResponse"), // [APPLICATION 13]
        COMPARE_REQUEST(0x6E, "CompareRequest"), // [APPLICATION 14]
        COMPARE_RESPONSE(0x6F, "CompareResponse"), // [APPLICATION 15]
        ABANDON_REQUEST(0x50, "AbandonRequest"), // [APPLICATION 16]
        SEARCH_RESULT_REFERENCE(0x73,
                "SearchResultReference"), // [APPLICATION 19]
        EXTENDED_REQUEST(0x77, "ExtendedRequest"), // [APPLICATION 23]
        EXTENDED_RESPONSE(0x78, "ExtendedResponse"), // [APPLICATION 24]
        INTERMEDIATE_RESPONSE(0x79, "IntermediateResponse"); // [APPLICATION 25]

        private final int id;
        private final String name;

        Operation(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        @Override
        public String toString() {
            return name;
        }

        public static Operation fromId(int id) {
            Optional<Operation> optional = Stream.of(Operation.values())
                    .filter(o -> o.id == id).findFirst();
            if (optional.isPresent()) {
                return optional.get();
            } else {
                throw new RuntimeException(
                        "Unknown id " + id + " for enum Operation.");
            }
        }
    }

    public LdapMessage(byte[] messages) {
        this.messages = messages;
        parse();
    }

    public LdapMessage(String hexString) {
        this(parseHexBinary(hexString));
    }

    // Extracts the message ID and operation ID from an LDAP protocol encoding
    private void parse() {
        if (messages == null || messages.length < 2) {
            throw new RuntimeException(
                    "Invalid ldap messages: " + Arrays.toString(messages));
        }

        if (messages[0] != 0x30) {
            throw new RuntimeException("Bad LDAP encoding in messages, "
                    + "expected ASN.1 SEQUENCE tag (0x30), encountered "
                    + messages[0]);
        }

        int index = 2;
        if ((messages[1] & 0x80) == 0x80) {
            index += (messages[1] & 0x0F);
        }

        if (messages[index] != 0x02) {
            throw new RuntimeException("Bad LDAP encoding in messages, "
                    + "expected ASN.1 INTEGER tag (0x02), encountered "
                    + messages[index]);
        }
        int length = messages[index + 1];
        index += 2;
        messageID = new BigInteger(1,
                Arrays.copyOfRange(messages, index, index + length)).intValue();
        index += length;
        int operationID = messages[index];
        operation = Operation.fromId(operationID);
    }

    /**
     * Return original ldap message in byte array.
     *
     * @return original ldap message
     */
    public byte[] getMessages() {
        return Arrays.copyOf(messages, messages.length);
    }

    /**
     * Return ldap message id.
     *
     * @return ldap message id.
     */
    public int getMessageID() {
        return messageID;
    }

    /**
     * Return ldap message's operation.
     *
     * @return ldap message's operation.
     */
    public Operation getOperation() {
        return operation;
    }

    private static byte[] parseHexBinary(String s) {

        final int len = s.length();

        // "111" is not a valid hex encoding.
        if (len % 2 != 0) {
            throw new IllegalArgumentException(
                    "hexBinary needs to be even-length: " + s);
        }

        byte[] out = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            int h = hexToBin(s.charAt(i));
            int l = hexToBin(s.charAt(i + 1));
            if (h == -1 || l == -1) {
                throw new IllegalArgumentException(
                        "contains illegal character for hexBinary: " + s);
            }

            out[i / 2] = (byte) (h * 16 + l);
        }

        return out;
    }

    private static int hexToBin(char ch) {
        if ('0' <= ch && ch <= '9') {
            return ch - '0';
        }
        if ('A' <= ch && ch <= 'F') {
            return ch - 'A' + 10;
        }
        if ('a' <= ch && ch <= 'f') {
            return ch - 'a' + 10;
        }
        return -1;
    }
}
