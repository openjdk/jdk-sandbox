/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import sun.security.ssl.SSLCipher.SSLWriteCipher;

import javax.crypto.SecretKey;
import javax.net.ssl.QuicTLSCallbacks;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * {@code OutputRecord} implementation for {@code QuicTLSEngineImpl}.
 */
final class QuicEngineOutputRecord extends OutputRecord implements SSLRecord {

    private final HandshakeFragment fragmenter = new HandshakeFragment();
    private final QuicTLSCallbacks quicTLSCallbacks;

    private volatile boolean isCloseWaiting;

    QuicEngineOutputRecord(HandshakeHash handshakeHash, QuicTLSCallbacks quicTLSCallbacks) {
        super(handshakeHash, SSLWriteCipher.nullTlsWriteCipher());
        this.quicTLSCallbacks = quicTLSCallbacks;

        this.packetSize = SSLRecord.maxRecordSize;
        this.protocolVersion = ProtocolVersion.NONE;
    }

    @Override
    public void close() throws IOException {
        recordLock.lock();
        try {
            if (!isClosed) {
                if (!fragmenter.isEmpty()) {
                    isCloseWaiting = true;
                } else {
                    super.close();
                }
            }
        } finally {
            recordLock.unlock();
        }
    }

    boolean isClosed() {
        return isClosed || isCloseWaiting;
    }

    @Override
    void encodeAlert(byte level, byte description) {
        // do nothing; the alert is delivered directly by the TransportContext
    }

    @Override
    void encodeHandshake(byte[] source,
            int offset, int length) throws IOException {
        recordLock.lock();
        try {
            if (isClosed()) {
                if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                    SSLLogger.warning("outbound has closed, ignore outbound " +
                                    "handshake message",
                            ByteBuffer.wrap(source, offset, length));
                }
                return;
            }

            firstMessage = false;

            byte handshakeType = source[offset];
            if (handshakeHash.isHashable(handshakeType)) {
                handshakeHash.deliver(source, offset, length);
            }

            fragmenter.queueUpFragment(source, offset, length);
        } finally {
            recordLock.unlock();
        }
    }

    @Override
    void encodeChangeCipherSpec() {
        throw new UnsupportedOperationException();
    }

    @Override
    Ciphertext encode(ByteBuffer[] srcs, int srcsOffset, int srcsLength, ByteBuffer[] dsts, int dstsOffset, int dstsLength) throws IOException {
        recordLock.lock();
        try {
            ByteBuffer dst = dsts[0];
            if (isClosed()) {
                if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                    SSLLogger.warning("outbound has closed, ignore outbound " +
                            "cached messages");
                }

                return null;
            }
            return fragmenter.acquireCiphertext(dst);
        } finally {
            recordLock.unlock();
        }
    }

    @Override
    void changeT13WriteCiphers(SSLTrafficKeyDerivation kdg, HandshakeContext hc,
             SecretKey writeSecret, boolean useChangeCipherSpec) {
        recordLock.lock();
        try {
            fragmenter.changePacketSpace(writeSecret);
        } finally {
            recordLock.unlock();
        }
    }

    @Override
    void changeWriteCiphers(SSLWriteCipher writeCipher, boolean useChangeCipherSpec) {
        throw new UnsupportedOperationException("Should not call this");
    }

    @Override
    void changeWriteCiphers(SSLWriteCipher writeCipher, byte keyUpdateRequest) {
        throw new UnsupportedOperationException("Should not call this");
    }

    @Override
    boolean isEmpty() {
        recordLock.lock();
        try {
            return fragmenter.isEmpty();
        } finally {
            recordLock.unlock();
        }
    }

    // buffered record fragment
    private static class HandshakeMemo {
        int acquireOffset;
        SecretKey nextKey;
        byte[] fragment;
    }

    final class HandshakeFragment {
        private final LinkedList<HandshakeMemo> handshakeMemos =
                new LinkedList<>();

        private int currentPacketSpace;

        void queueUpFragment(byte[] source,
                int offset, int length) throws IOException {
            HandshakeMemo memo = new HandshakeMemo();

            memo.fragment = new byte[length];
            assert Record.getInt24(ByteBuffer.wrap(source, offset + 1, 3))
                    == length - 4 : "Invalid handshake message length";
            System.arraycopy(source, offset, memo.fragment, 0, length);

            handshakeMemos.add(memo);
        }

        void changePacketSpace(SecretKey baseSecret) {
            HandshakeMemo lastMemo = handshakeMemos.peekLast();
            if (lastMemo != null) {
                lastMemo.nextKey = baseSecret;
            } else {
                currentPacketSpace++;
                switch(currentPacketSpace) {
                    case 1->
                            quicTLSCallbacks.deriveHandshakeWriteSecrets(baseSecret);
                    case 2->
                            quicTLSCallbacks.deriveApplicationWriteSecrets(baseSecret);
                    default ->
                            throw new AssertionError("Unexpected key epoch");
                }
            }
        }

        Ciphertext acquireCiphertext(ByteBuffer dstBuf) {
            HandshakeMemo hsMemo = handshakeMemos.pollFirst();
            if (hsMemo == null) {
                return null;
            }
            int fragLen = Math.min(dstBuf.remaining(), hsMemo.fragment.length - hsMemo.acquireOffset);
            dstBuf.put(hsMemo.fragment, hsMemo.acquireOffset, fragLen);
            hsMemo.acquireOffset += fragLen;
            if (hsMemo.acquireOffset < hsMemo.fragment.length) {
                handshakeMemos.addFirst(hsMemo);
            } else if (hsMemo.nextKey != null) {
                currentPacketSpace++;
                switch(currentPacketSpace) {
                    case 1->
                            quicTLSCallbacks.deriveHandshakeWriteSecrets(hsMemo.nextKey);
                    case 2->
                            quicTLSCallbacks.deriveApplicationWriteSecrets(hsMemo.nextKey);
                    default ->
                            throw new AssertionError("Unexpected key epoch");
                }
            }
            return new Ciphertext(ContentType.HANDSHAKE.id,
                    hsMemo.fragment[0], -1L);
        }

        boolean isEmpty() {
            return handshakeMemos.isEmpty();
        }
    }
}
