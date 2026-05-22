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
 * @summary Test to exercise client-side EncryptedExtensions processing
 * @key randomness
 * @library /test/lib /javax/net/ssl/templates
 * @build QuicSSLEngineTemplate jdk.test.lib.RandomFactory
 * @run junit/othervm EncryptedExtensionsProcessingTest
 */
public class EncryptedExtensionsProcessingTest extends QuicSSLEngineTemplate {

    protected EncryptedExtensionsProcessingTest() throws Exception {
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

        assertTrue(clientTransport.errorReported, "Client accepted extensions without ALPN");
        assertEquals(120, clientTransport.errorCode, "Error code is not no_application_protocol");
        log("Got expected exception: " + clientTransport.errorCause);
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
        assertTrue(clientTransport.errorReported, "Client accepted extensions without transport parameters");
        assertEquals(109, clientTransport.errorCode, "Error code is not missing_extension");
        log("Got expected exception: " + clientTransport.errorCause);
    }

    private void runTest(int extensionToRemove) throws Exception {
        SSLEngineResult clientResult;
        SSLEngineResult serverResult;

        log("=== Client hello ===");
        // client wrap
        log("---Client Wrap---");
        clientResult = clientEngine.wrap(EMPTY, cTOs);
        logEngineStatus(clientEngine, clientResult);
        runDelegatedTasks(clientEngine);

        cTOs.flip();
        // server unwrap
        log("---Server Unwrap---");
        serverResult = serverEngine.unwrap(cTOs, EMPTY);
        logEngineStatus(serverEngine, serverResult);
        runDelegatedTasks(serverEngine);

        log("=== Server hello ===");
        // server wrap
        log("---Server Wrap---");
        serverResult = serverEngine.wrap(EMPTY, sTOc);
        logEngineStatus(serverEngine, serverResult);
        runDelegatedTasks(serverEngine);

        sTOc.flip();

        // client unwrap
        log("---Client Unwrap---");
        clientResult = clientEngine.unwrap(sTOc, EMPTY);
        logEngineStatus(clientEngine, clientResult);
        runDelegatedTasks(clientEngine);
        sTOc.compact();

        log("=== Encrypted extensions ===");
        // server wrap
        log("---Server Wrap---");
        serverResult = serverEngine.wrap(EMPTY, sTOc);
        logEngineStatus(serverEngine, serverResult);
        runDelegatedTasks(serverEngine);

        sTOc.flip();
        removeExtension(sTOc, extensionToRemove);

        // client unwrap
        log("---Client Unwrap---");
        clientResult = clientEngine.unwrap(sTOc, EMPTY);
        logEngineStatus(clientEngine, clientResult);
        runDelegatedTasks(clientEngine);
    }

    void removeExtension(ByteBuffer encryptedExtensions, int typeToRemove) {
        // encryptedExtensions contains one encryptedExtensions message;
        // find extensions, remove the one with the given ID,
        // adjust lengths.
        assertEquals(0, encryptedExtensions.position());
        assertEquals(8, encryptedExtensions.get(0), "Not an encrypted extensions message");
        int len = encryptedExtensions.getInt(0) & 0xffffff;
        int extensionLenPosition = 4;
        int extensionLen = encryptedExtensions.getShort(extensionLenPosition);
        int extensionsStart = extensionLenPosition + 2;
        assertEquals(len + 4, extensionsStart + extensionLen, "Unexpected data after extensions");
        while (extensionsStart < len + 4) {
            short type =  encryptedExtensions.getShort(extensionsStart);
            short extlen = encryptedExtensions.getShort(extensionsStart + 2);
            int nextExtension = extensionsStart + extlen + 4;
            log("Found extension type: " + type);
            if (type == typeToRemove) {
                // remove extension
                encryptedExtensions.put(extensionsStart, encryptedExtensions, nextExtension, len + 4 - nextExtension);
                // fix up extensions length
                encryptedExtensions.putShort(extensionLenPosition, (short)(extensionLen - extlen - 4));
                // fix up message length
                encryptedExtensions.putInt(0, 0x8000000 + len - extlen - 4);
                // fix up buffer limit.
                // This will remove any messages following encrypted extensions.
                encryptedExtensions.limit(len - extlen);
                return;
            }
            extensionsStart = nextExtension;
        }
        assertEquals(len + 4, extensionsStart, "Extension truncated");
        throw new RuntimeException("Extension not found!");
    }

    private static void logEngineStatus(
            SSLEngine engine, SSLEngineResult result) {
        log("\tResult Status    : " + result.getStatus());
        log("\tResult HS Status : " + result.getHandshakeStatus());
        log("\tEngine HS Status : " + engine.getHandshakeStatus());
        log("\tisInboundDone()  : " + engine.isInboundDone());
        log("\tisOutboundDone() : " + engine.isOutboundDone());
        log("\tMore Result      : " + result);
    }

    private static void log(String message) {
        System.err.println(message);
    }
}
