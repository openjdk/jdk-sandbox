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

import jdk.test.lib.RandomFactory;
import org.junit.jupiter.api.BeforeAll;

import javax.crypto.SecretKey;
import javax.net.ssl.QuicTLSAdapter;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * A QUIC SSLEngine usage example which simplifies the presentation
 * by removing the I/O and multi-threading concerns.
 *
 * The test creates two SSLEngines, simulating a client and server.
 * The "transport" layer consists two byte buffers:  think of them
 * as directly connected pipes.
 *
 * Note, this is a *very* simple example: real code uses QUIC CRYPTO
 * frames to transport data.
 */
/*
 * @test
 * @bug 8383849
 * @summary Basic QUIC SSLEngine test
 * @key randomness
 * @library /test/lib
 * @build SSLContextTemplate jdk.test.lib.RandomFactory
 * @run main/othervm QuicSSLEngineTemplate
 */
public class QuicSSLEngineTemplate extends SSLContextTemplate {

    private final byte[] clientParams;
    private final byte[] serverParams;
    public static final ByteBuffer EMPTY = ByteBuffer.allocate(0);
    /*
     * Enables the JSSE system debugging system property:
     *
     *     -Djavax.net.debug=all
     *
     * This gives a lot of low-level information about operations underway,
     * including specific handshake messages, and might be best examined
     * after gaining some familiarity with this application.
     */
    private static final boolean debug = false;

    static Random RNG;

    protected final SSLEngine clientEngine;     // client Engine

    protected final SSLEngine serverEngine;     // server Engine

    protected final TestQuicTransport clientTransport;     // client Engine

    protected final TestQuicTransport serverTransport;     // server Engine

    // For data transport, this example uses local ByteBuffers.  This
    // isn't really useful, but the purpose of this example is to show
    // SSLEngine concepts, not how to do network transport.
    protected final ByteBuffer cTOs;      // "reliable" transport client->server
    protected final ByteBuffer sTOc;      // "reliable" transport server->client

    @BeforeAll
    static void setUp() {
        RNG = RandomFactory.getRandom();
    }

    protected QuicSSLEngineTemplate() throws Exception {
        clientParams = new byte[RNG.nextInt(100)];
        serverParams = new byte[RNG.nextInt(100)];
        RNG.nextBytes(clientParams);
        RNG.nextBytes(serverParams);

        serverTransport = createServerQuicTransport();
        clientTransport = createClientQuicTransport();
        serverEngine = configureServerEngine(
                createServerSSLContext().createSSLEngine(serverTransport));

        clientEngine = configureClientEngine(
                createClientSSLContext().createSSLEngine(clientTransport));

        // We use a hardcoded buffer size here.
        // In practice the write buffer can be arbitrarily small.
        // The read buffer should be big enough to store the largest message.
        cTOs = ByteBuffer.allocate(10000);
        sTOc = ByteBuffer.allocate(10000);
    }

    protected static class TestQuicTransport implements QuicTLSAdapter {

        protected static final int INITIAL = 0;
        protected static final int HANDSHAKE = 1;
        protected static final int APP = 2;
        protected int currentReadSpace = INITIAL;
        protected int currentWriteSpace = INITIAL;
        protected boolean errorReported;
        protected final byte[] localParameters;
        protected final byte[] expectedRemoteParameters;
        protected byte errorCode;
        protected Throwable errorCause;

        protected TestQuicTransport(byte[] localParameters, byte[] expectedRemoteParameters) {
            this.localParameters = localParameters;
            this.expectedRemoteParameters = expectedRemoteParameters;
        }

        @Override
        public void deriveHandshakeReadSecrets(SecretKey secretKey) {
            log("Derived handshake read secrets");
            currentReadSpace = HANDSHAKE;
        }

        @Override
        public void deriveHandshakeWriteSecrets(SecretKey secretKey) {
            log("Derived handshake write secrets");
            currentWriteSpace = HANDSHAKE;
        }

        @Override
        public void deriveApplicationReadSecrets(SecretKey secretKey) {
            log("Derived app read secrets");
            currentReadSpace = APP;
        }

        @Override
        public void deriveApplicationWriteSecrets(SecretKey secretKey) {
            log("Derived app write secrets");
            currentWriteSpace = APP;
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return new String[] {"TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384",
                    "TLS_CHACHA20_POLY1305_SHA256"};
        }

        @Override
        public ByteBuffer localQuicTransportParameters() {
            return ByteBuffer.wrap(localParameters);
        }

        @Override
        public void remoteQuicTransportParameters(ByteBuffer parameters) throws IOException {
            assert parameters.mismatch(ByteBuffer.wrap(expectedRemoteParameters)) == -1 :
                    "Received unexpected transport parameters";
        }

        @Override
        public void cryptoError(byte code, Exception closeReason) {
            errorReported = true;
            errorCode = code;
            errorCause = closeReason;
            log("Crypto error called with error code: " + (code&0xff)
                    + ", exception: " + closeReason);
        }
    }
    //
    // Protected methods could be used to customize the test case.
    //
    protected TestQuicTransport createClientQuicTransport() {
        return new TestQuicTransport(clientParams, serverParams);
    }

    protected TestQuicTransport createServerQuicTransport() {
        return new TestQuicTransport(serverParams, clientParams);
    }

    /*
     * Configure the client side engine.
     */
    protected SSLEngine configureClientEngine(SSLEngine clientEngine) {
        clientEngine.setUseClientMode(true);

        // Get/set parameters if needed
        SSLParameters paramsClient = clientEngine.getSSLParameters();
        paramsClient.setApplicationProtocols(new String[] {"test"});
        clientEngine.setSSLParameters(paramsClient);

        return clientEngine;
    }

    /*
     * Configure the server side engine.
     */
    protected SSLEngine configureServerEngine(SSLEngine serverEngine) {
        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(true);

        // Get/set parameters if needed
        //
        SSLParameters paramsServer = serverEngine.getSSLParameters();
        paramsServer.setApplicationProtocols(new String[] {"test"});
        serverEngine.setSSLParameters(paramsServer);

        return serverEngine;
    }

    public static void main(String[] args) throws Exception {
        if (debug) {
            System.setProperty("javax.net.debug", "all");
        }

        new QuicSSLEngineTemplate().runTest();
    }

    //
    // Private methods used to build the common part of the test.
    //

    private void runTest() throws Exception {
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
        assert clientTransport.currentReadSpace == TestQuicTransport.APP
                && serverTransport.currentWriteSpace == TestQuicTransport.APP
                && clientTransport.currentWriteSpace == TestQuicTransport.APP
                && serverTransport.currentReadSpace == TestQuicTransport.APP :
            "Handshake did not complete";
        assert !clientTransport.errorReported && !serverTransport.errorReported :
                "Handshake failed";

    }

    protected static void logEngineStatus(SSLEngine engine) {
        log("\tCurrent HS State: " + engine.getHandshakeStatus());
        if (engine.isInboundDone()) {
            log("\tisInboundDone() : " + engine.isInboundDone());
        }
        if (engine.isOutboundDone()) {
            log("\tisOutboundDone(): " + engine.isOutboundDone());
        }
    }

    protected static void logEngineStatus(
            SSLEngine engine, SSLEngineResult result) {
        log("\tResult           : " + result);
        if (result.getHandshakeStatus() != engine.getHandshakeStatus()) {
            log("\tEngine HS Status : " + engine.getHandshakeStatus());
        }
        if (engine.isInboundDone()) {
            log("\tisInboundDone() : " + engine.isInboundDone());
        }
        if (engine.isOutboundDone()) {
            log("\tisOutboundDone(): " + engine.isOutboundDone());
        }
    }

    protected static void log(String message) {
        System.err.println(message);
    }

    // If the result indicates that we have outstanding tasks to do,
    // go ahead and run them in this thread.
    protected static void runDelegatedTasks(SSLEngine engine) throws Exception {
        if (engine.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
            Runnable runnable;
            while ((runnable = engine.getDelegatedTask()) != null) {
                log("    running delegated task...");
                runnable.run();
            }
            HandshakeStatus hsStatus = engine.getHandshakeStatus();
            if (hsStatus == HandshakeStatus.NEED_TASK) {
                throw new Exception(
                        "handshake shouldn't need additional tasks");
            }
            logEngineStatus(engine);
        }
    }
}
