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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

import java.nio.ByteBuffer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8383849
 * @summary Test to exercise post-handshake message rejection.
 * Verifies that all message types other than NewSessionTicket are rejected
 * @key randomness
 * @library /test/lib /javax/net/ssl/templates
 * @build QuicSSLEngineTemplate jdk.test.lib.RandomFactory
 * @run junit/othervm PostHandshakeProcessingTest
 */
public class PostHandshakeProcessingTest extends QuicSSLEngineTemplate {

    protected PostHandshakeProcessingTest() throws Exception {
        super();
    }

    public static IntStream bytes() {
        return IntStream.range(0, 256);
    }


    @ParameterizedTest
    @MethodSource("bytes")
    public void testClient(int message) throws Exception {
        if (message == 4) {
            log("New session ticket is an acceptable message, skipping...");
            return;
        }
        doHandshake();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(4);
            buffer.putInt(message << 24).flip();
            clientEngine.unwrap(buffer, EMPTY);
            runDelegatedTasks(clientEngine);
            // the test may throw an exception if the bad message is processed by unwrap.
            // SunJSSE processes the message in a delegated task and does not throw.
        } catch (SSLException e) {
            log("Caught expected exception: " + e);
        }

        assertTrue(clientTransport.errorReported, "Client accepted a message of type " + message);
        assertEquals(10, clientTransport.errorCode, "Error code is not unexpected_message");
        log("Got expected exception: " + clientTransport.errorCause);
    }

    @ParameterizedTest
    @MethodSource("bytes")
    public void testServer(int message) throws Exception {
        doHandshake();
        try {
            ByteBuffer buffer = ByteBuffer.allocate(4);
            buffer.putInt(message << 24).flip();
            serverEngine.unwrap(buffer, EMPTY);
            runDelegatedTasks(serverEngine);
            // the test may throw an exception if the bad message is processed by unwrap.
            // SunJSSE processes the message in a delegated task and does not throw.
        } catch (SSLException e) {
            log("Caught expected exception: " + e);
        }

        assertTrue(serverTransport.errorReported, "Server accepted a message of type " + message);
        if (serverTransport.errorCode != 10) {
            serverTransport.errorCause.printStackTrace();
        }
        assertEquals(10, serverTransport.errorCode, "Error code is not unexpected_message");
        log("Got expected exception: " + serverTransport.errorCause);
    }


    private void doHandshake() throws Exception {
        SSLEngineResult clientResult;
        SSLEngineResult serverResult;

        while (true) {
            // Only send crypto data when the recipient uses the same key space.
            // Write key space must be checked before calling wrap.
            boolean canServerSend =
                    clientTransport.currentReadSpace == serverTransport.currentWriteSpace;
            boolean canClientSend =
                    clientTransport.currentWriteSpace == serverTransport.currentReadSpace;
            log("=================");

            // client wrap
            log("---Client Wrap---");
            clientResult = clientEngine.wrap(EMPTY, cTOs);
            logEngineStatus(clientEngine, clientResult);
            runDelegatedTasks(clientEngine);

            // server wrap
            log("---Server Wrap---");
            serverResult = serverEngine.wrap(EMPTY, sTOc);
            logEngineStatus(serverEngine, serverResult);
            runDelegatedTasks(serverEngine);

            cTOs.flip();
            sTOc.flip();
            assert canServerSend || !sTOc.hasRemaining();
            assert canClientSend || !cTOs.hasRemaining();
            if (!sTOc.hasRemaining() && !cTOs.hasRemaining()) {
                log("All done");
                break;
            }

            int readKeys = clientTransport.currentReadSpace;
            do {
                // client unwrap
                log("---Client Unwrap---");
                clientResult = clientEngine.unwrap(sTOc, EMPTY);
                logEngineStatus(clientEngine, clientResult);
                runDelegatedTasks(clientEngine);
            } while (sTOc.hasRemaining() && clientResult.getStatus() == SSLEngineResult.Status.OK
                    && readKeys == clientTransport.currentReadSpace);
            assert readKeys == clientTransport.currentReadSpace || !sTOc.hasRemaining();

            readKeys = serverTransport.currentReadSpace;
            do {
                // server unwrap
                log("---Server Unwrap---");
                serverResult = serverEngine.unwrap(cTOs, EMPTY);
                logEngineStatus(serverEngine, serverResult);
                runDelegatedTasks(serverEngine);
            } while (sTOc.hasRemaining() && serverResult.getStatus() == SSLEngineResult.Status.OK
                    && readKeys == serverTransport.currentReadSpace);
            assert readKeys == serverTransport.currentReadSpace || !cTOs.hasRemaining();

            cTOs.compact();
            sTOc.compact();
        }
    }
}
