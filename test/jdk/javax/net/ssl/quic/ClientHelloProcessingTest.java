/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8383849
 * @summary Test to exercise server-side ClientHello processing
 * @key randomness
 * @library /test/lib /javax/net/ssl/templates
 * @build QuicSSLEngineTemplate jdk.test.lib.RandomFactory
 * @run junit/othervm ClientHelloProcessingTest
 */
public class ClientHelloProcessingTest extends QuicSSLEngineTemplate {

    protected ClientHelloProcessingTest() throws Exception {
        super();
    }

    @Test
    public void testNoALPN() throws Exception {
        // RFC 9001, 8.1: ALPN is required
        try {
            runTest(16);
            // the test may throw an exception if the bad message is processed by unwrap.
            // SunJSSE processes the message in a delegated task and does not throw.
        } catch (SSLException e) {
            log("Caught expected exception: " + e);
        }

        assertTrue(serverTransport.errorReported, "Server accepted a hello without ALPN");
        assertEquals(120, serverTransport.errorCode, "Error code is not no_application_protocol");
        log("Got expected exception: " + serverTransport.errorCause);
    }

    @Test
    public void testNoTransportParameters() throws Exception {
        // RFC 9001, 8.2: transport parameters are required
        try {
            runTest(57);
            // the test may throw an exception if the bad message is processed by unwrap.
            // SunJSSE processes the message in a delegated task and does not throw.
        } catch (SSLException e) {
            log("Caught expected exception: " + e);
        }

        assertTrue(serverTransport.errorReported, "Server accepted a hello without transport parameters");
        assertEquals(109, serverTransport.errorCode, "Error code is not missing_extension");
        log("Got expected exception: " + serverTransport.errorCause);
    }

    private void runTest(int extensionToRemove) throws Exception {
        SSLEngineResult clientResult;
        SSLEngineResult serverResult;

        // client wrap
        log("---Client Wrap---");
        clientResult = clientEngine.wrap(EMPTY, cTOs);
        logEngineStatus(clientEngine, clientResult);
        runDelegatedTasks(clientEngine);

        cTOs.flip();
        removeExtension(cTOs, extensionToRemove);
        // server unwrap
        log("---Server Unwrap---");
        serverResult = serverEngine.unwrap(cTOs, EMPTY);
        logEngineStatus(serverEngine, serverResult);
        runDelegatedTasks(serverEngine);
    }

    void removeExtension(ByteBuffer clientHello, int typeToRemove) {
        // clientHello contains one clientHello message;
        // find extensions, remove the one with the given ID,
        // adjust lengths.
        assertEquals(0, clientHello.position());
        assertEquals(1, clientHello.get(0), "Not a client hello message");
        int len = clientHello.getInt(0) & 0xffffff;
        assertEquals(clientHello.remaining() - 4, len, "Unexpected hello length");
        assertEquals(0x303, clientHello.getShort(4), "Unexpected legacy version");
        // skip over 32 bytes of random
        // RFC 9001, 8.4: legacy session id must be empty
        assertEquals(0,  clientHello.get(38), "Unexpected legacy session ID");
        int cipherSuitesLen = clientHello.getShort(39);
        assertEquals(1,  clientHello.get(41+cipherSuitesLen), "Unexpected number of compression methods");
        assertEquals(0,  clientHello.get(42 + cipherSuitesLen), "Unexpected compression method");
        int extensionLenPosition = 43 + cipherSuitesLen;
        int extensionLen = clientHello.getShort(extensionLenPosition);
        int extensionsStart = extensionLenPosition + 2;
        assertEquals(len + 4, extensionsStart + extensionLen, "Unexpected data after extensions");
        while (extensionsStart < len + 4) {
            short type =  clientHello.getShort(extensionsStart);
            short extlen = clientHello.getShort(extensionsStart + 2);
            int nextExtension = extensionsStart + extlen + 4;
            log("Found extension type: " + type);
            if (type == typeToRemove) {
                // remove extension
                clientHello.put(extensionsStart, clientHello, nextExtension, len + 4 - nextExtension);
                // fix up extensions length
                clientHello.putShort(extensionLenPosition, (short)(extensionLen - extlen - 4));
                // fix up message length
                clientHello.putInt(0, 0x1000000 + len - extlen - 4);
                // fix up buffer limit
                clientHello.limit(len - extlen);
                return;
            }
            extensionsStart = nextExtension;
        }
        assertEquals(len + 4, extensionsStart, "Extension truncated");
        throw new RuntimeException("Extension not found!");
    }

}
