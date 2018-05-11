/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.*;
import sun.security.ssl.SSLExtension.ExtensionConsumer;

import sun.security.ssl.SSLExtension.SSLExtensionSpec;
import sun.security.ssl.SSLHandshake.HandshakeMessage;

/**
 * Pack of the "psk_key_exchange_modes" extensions.
 */
final class PskKeyExchangeModesExtension {
    static final HandshakeProducer chNetworkProducer =
            new PskKeyExchangeModesProducer();
    static final ExtensionConsumer chOnLoadConsumer =
            new PskKeyExchangeModesConsumer();

    enum PskKeyExchangeMode {
        PSK_KE(0),
        PSK_DHE_KE(1);

        private final int v;

        PskKeyExchangeMode(int v) {
            this.v = v;
        }

        static PskKeyExchangeMode ofInt(int v) {
            for(PskKeyExchangeMode mode : values()) {
                if (mode.v == v) {
                    return mode;
                }
            }

            return null;
        }
    }

    static final class PskKeyExchangeModesSpec implements SSLExtensionSpec {


        final List<PskKeyExchangeMode> modes;

        PskKeyExchangeModesSpec(List<PskKeyExchangeMode> modes) {
            this.modes = modes;
        }

        PskKeyExchangeModesSpec(ByteBuffer m) throws IOException {

            modes = new ArrayList<>();
            int modesEncodedLength = Record.getInt8(m);
            int modesReadLength = 0;
            while (modesReadLength < modesEncodedLength) {
                int mode = Record.getInt8(m);
                // TODO: handle incorrect values
                modes.add(PskKeyExchangeMode.ofInt(mode));
                modesReadLength += 1;
            }
        }

        byte[] getEncoded() throws IOException {

            int encodedLength = modes.size() + 1;
            byte[] buffer = new byte[encodedLength];
            ByteBuffer m = ByteBuffer.wrap(buffer);
            Record.putInt8(m, modes.size());
            for(PskKeyExchangeMode curMode : modes) {
                Record.putInt8(m, curMode.v);
            }

            return buffer;
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
            "\"PskKeyExchangeModes\": '{'\n" +
            "  \"ke_modes\"      : \"{0}\",\n" +
            "'}'",
            Locale.ENGLISH);

            Object[] messageFields = {
            Utilities.indent(modesString()),
            };

            return messageFormat.format(messageFields);
        }

        String modesString() {
            StringBuilder result = new StringBuilder();
            for(PskKeyExchangeMode curMode : modes) {
                result.append(curMode.toString() + "\n");
            }

            return result.toString();
        }
    }


    private static final
            class PskKeyExchangeModesConsumer implements ExtensionConsumer {
        // Prevent instantiation of this class.
        private PskKeyExchangeModesConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
            HandshakeMessage message, ByteBuffer buffer) throws IOException {

            ServerHandshakeContext shc =
                    (ServerHandshakeContext) message.handshakeContext;

            PskKeyExchangeModesSpec modes = new PskKeyExchangeModesSpec(buffer);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                "Received PskKeyExchangeModes extension: ", modes);
            }

            shc.pskKeyExchangeModes = modes.modes;
        }

    }

    private static final
            class PskKeyExchangeModesProducer implements HandshakeProducer {

        static final List<PskKeyExchangeMode> MODES =
            List.of(PskKeyExchangeMode.PSK_DHE_KE);
        static final PskKeyExchangeModesSpec MODES_MSG =
            new PskKeyExchangeModesSpec(MODES);

        // Prevent instantiation of this class.
        private PskKeyExchangeModesProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {

            return MODES_MSG.getEncoded();
        }

    }
}

