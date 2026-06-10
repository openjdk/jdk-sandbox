/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import sun.security.ssl.SSLCipher.SSLReadCipher;

import javax.net.ssl.QuicTLSCallbacks;
import javax.net.ssl.SSLProtocolException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * {@code InputRecord} implementation for a QUIC {@code SSLEngine}.
 */
final class QuicEngineInputRecord extends InputRecord implements SSLRecord {

    // Cache for incomplete handshake messages.
    private ByteBuffer handshakeBuffer = null;
    final QuicTLSCallbacks quicTLSCallbacks;
    // 0 - initial, 1 - handshake, 2 - one-rtt
    int epoch;

    QuicEngineInputRecord(HandshakeHash handshakeHash, QuicTLSCallbacks quicTLSCallbacks) {
        super(handshakeHash, SSLReadCipher.nullTlsReadCipher());
        this.quicTLSCallbacks = quicTLSCallbacks;
    }

    @Override
    int estimateFragmentSize(int packetSize) {
        // QUIC engine never produces application data
        return 0;
    }

    @Override
    int bytesInCompletePacket(
        ByteBuffer[] srcs, int srcsOffset, int srcsLength) {
        // QUIC does not use record layer
        return 0;
    }

    @Override
    Plaintext[] decode(ByteBuffer[] srcs, int srcsOffset,
            int srcsLength) throws IOException {
        if (srcs == null || srcs.length == 0 || srcsLength == 0) {
            return new Plaintext[0];
        } else if (srcsLength == 1) {
            return decode(srcs[srcsOffset]);
        } else {
            ByteBuffer packet = extract(srcs,
                    srcsOffset, srcsLength, SSLRecord.headerSize);

            return decode(packet);
        }
    }

    private Plaintext[] decode(ByteBuffer packet)
            throws IOException {

        if (isClosed) {
            return null;
        }

        if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.RECORD_PACKET)) {
            SSLLogger.fine("Raw read", packet);
        }

        return decodeInputRecord(packet);
    }

    @Override
    void changeReadCiphers(SSLReadCipher readCipher) {
        super.changeReadCiphers(readCipher);
        epoch++;
        switch(epoch) {
            case 1->
                    quicTLSCallbacks.deriveHandshakeReadSecrets(readCipher.baseSecret);
            case 2->
                    quicTLSCallbacks.deriveApplicationReadSecrets(readCipher.baseSecret);
            default ->
                throw new AssertionError("Unexpected key epoch");
        }
    }

    private Plaintext[] decodeInputRecord(ByteBuffer fragment)
            throws IOException {
        //
        // parse handshake messages
        //
        ByteBuffer handshakeFrag = fragment;
        if ((handshakeBuffer != null) &&
                (handshakeBuffer.remaining() != 0)) {
            ByteBuffer bb = ByteBuffer.wrap(new byte[
                    handshakeBuffer.remaining() + fragment.remaining()]);
            bb.put(handshakeBuffer);
            bb.put(fragment);
            handshakeFrag = bb.rewind();
            handshakeBuffer = null;
        }

        ArrayList<Plaintext> plaintexts = new ArrayList<>(5);
        while (handshakeFrag.hasRemaining()) {
            int remaining = handshakeFrag.remaining();
            if (remaining < handshakeHeaderSize) {
                handshakeBuffer = ByteBuffer.wrap(new byte[remaining]);
                handshakeBuffer.put(handshakeFrag);
                handshakeBuffer.rewind();
                break;
            }

            handshakeFrag.mark();

            // Fail fast for unknown handshake message.
            byte handshakeType = handshakeFrag.get();
            if (!SSLHandshake.isKnown(handshakeType)) {
                // SSLTransport.decode will convert this to UNEXPECTED_MESSAGE
                throw new SSLProtocolException(
                        "Unknown handshake type size, Handshake.msg_type = " +
                                (handshakeType & 0xFF));
            }

            int handshakeBodyLen = Record.getInt24(handshakeFrag);
            if (handshakeBodyLen > SSLConfiguration.maxHandshakeMessageSize) {
                // SSLTransport.decode will convert this to UNEXPECTED_MESSAGE
                throw new SSLProtocolException(
                        "The size of the handshake message ("
                                + handshakeBodyLen
                                + ") exceeds the maximum allowed size ("
                                + SSLConfiguration.maxHandshakeMessageSize
                                + ")");
            }

            handshakeFrag.reset();
            int handshakeMessageLen =
                    handshakeHeaderSize + handshakeBodyLen;
            if (remaining < handshakeMessageLen) {
                handshakeBuffer = ByteBuffer.wrap(new byte[remaining]);
                handshakeBuffer.put(handshakeFrag);
                handshakeBuffer.rewind();
                break;
            } else if (remaining == handshakeMessageLen) {
                if (handshakeHash.isHashable(handshakeType)) {
                    handshakeHash.receive(handshakeFrag);
                }

                plaintexts.add(
                        new Plaintext(ContentType.HANDSHAKE.id,
                                (byte)3, (byte)3, -1, -1L, handshakeFrag)
                );
                break;
            } else {
                int fragPos = handshakeFrag.position();
                int fragLim = handshakeFrag.limit();
                int nextPos = fragPos + handshakeMessageLen;
                handshakeFrag.limit(nextPos);

                if (handshakeHash.isHashable(handshakeType)) {
                    handshakeHash.receive(handshakeFrag);
                }

                plaintexts.add(
                        new Plaintext(ContentType.HANDSHAKE.id, (byte)3, (byte)3,
                                -1, -1L, handshakeFrag.slice())
                );

                handshakeFrag.position(nextPos);
                handshakeFrag.limit(fragLim);
            }
        }
        // TODO ensure that cipher spec changes only happen
        //  after all handshake bytes are consumed (JDK-8366453)
        return plaintexts.toArray(new Plaintext[0]);
    }

}
