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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLEngineResult;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8383849
 * @summary Test to verify the buffering behavior of unwrap
 * @key randomness
 * @enablePreview
 * @library /test/lib /javax/net/ssl/templates
 * @build QuicSSLEngineTemplate jdk.test.lib.RandomFactory
 * @run junit/othervm UnwrapBufferingTest
 */
public class UnwrapBufferingTest extends QuicSSLEngineTemplate {

    protected UnwrapBufferingTest() throws Exception {
        super();
    }

    @Test
    public void testBuffering() throws Exception {
        // verify that the SSLEngine buffers partial messages
        SSLEngineResult clientResult;
        SSLEngineResult serverResult;
        // use a small buffer to make sure the ClientHello message will be split.
        // Typical ClientHello messages are larger than 100 bytes.
        ByteBuffer cTOs = ByteBuffer.allocate(100);

        do {
            // client wrap
            log("---Client Wrap---");
            clientResult = clientEngine.wrap(EMPTY, cTOs);
            logEngineStatus(clientEngine, clientResult);
            runDelegatedTasks(clientEngine);

            cTOs.flip();
            assertTrue(cTOs.hasRemaining());
            // server unwrap
            log("---Server Unwrap---");
            serverResult = serverEngine.unwrap(cTOs, EMPTY);
            logEngineStatus(serverEngine, serverResult);
            runDelegatedTasks(serverEngine);
            assertFalse(cTOs.hasRemaining(), "Server did not buffer all bytes");
            cTOs.compact();
        } while (serverEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_UNWRAP);
        assertEquals(SSLEngineResult.HandshakeStatus.NEED_UNWRAP, clientEngine.getHandshakeStatus());
        assertEquals(SSLEngineResult.HandshakeStatus.NEED_WRAP, serverEngine.getHandshakeStatus());
    }

    @Test @Disabled("Not implemented yet")
    public void testBufferingOnKeyChange() throws Exception {
        // verify that the SSLEngine stops consuming after the ServerHello message,
        // even if there are more bytes available to consume.
        // Any extra bytes are supposed to be detected by the QUIC implementation
        // and rejected as a protocol violation; see RFC 9001,section 4.1.3
        // "if there is data from a previous encryption level
        // that TLS has not consumed, this MUST be treated
        // as a connection error of type PROTOCOL_VIOLATION."
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

        assertEquals(SSLEngineResult.HandshakeStatus.NEED_UNWRAP, clientEngine.getHandshakeStatus());
        assertEquals(SSLEngineResult.HandshakeStatus.NEED_WRAP, serverEngine.getHandshakeStatus());

        log("=== Server hello ===");
        // server wrap
        log("---Server Wrap---");
        serverResult = serverEngine.wrap(EMPTY, sTOc);
        logEngineStatus(serverEngine, serverResult);
        runDelegatedTasks(serverEngine);

        assertEquals(TestQuicTransport.HANDSHAKE, serverTransport.currentWriteSpace);
        int encryptedExtensionsPosition = sTOc.position();

        // now write Encrypted Extensions to the same buffer
        log("=== Encrypted extensions ===");
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

        // the client should have consumed SH only
        assertEquals(TestQuicTransport.HANDSHAKE, clientTransport.currentReadSpace);
        assertTrue(sTOc.hasRemaining(), "Client consumed EE");
        assertEquals(encryptedExtensionsPosition, sTOc.position(), "Client consumed EE");
        // the QUIC layer can and should detect the read space change
        // and report PROTOCOL_VIOLATION.
    }
}
