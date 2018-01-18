/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @build DummyWebSocketServer
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=trace Exceptionally
 */

import jdk.incubator.http.WebSocket;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static jdk.incubator.http.HttpClient.newHttpClient;
import static jdk.incubator.http.WebSocket.NORMAL_CLOSURE;
import static org.testng.Assert.assertThrows;

public class Exceptionally {

    static final Class<NullPointerException> NPE = NullPointerException.class;

    @Test
    public void testNull() throws IOException {
        try (DummyWebSocketServer server = new DummyWebSocketServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            assertThrows(NPE, () -> ws.sendPing(null));
            assertThrows(NPE, () -> ws.sendPong(null));
            assertThrows(NPE, () -> ws.sendClose(NORMAL_CLOSURE, null));

            // ... add more NPE scenarios
        }
    }

    private static String stringWithNBytes(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++)
            sb.append("A");
        return sb.toString();
    }

    @Test
    public void testIllegalArgument() throws IOException {
        try (DummyWebSocketServer server = new DummyWebSocketServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            assertIAE(ws.sendPing(ByteBuffer.allocate(126)));
            assertIAE(ws.sendPing(ByteBuffer.allocate(127)));
            assertIAE(ws.sendPing(ByteBuffer.allocate(128)));
            assertIAE(ws.sendPing(ByteBuffer.allocate(150)));

            assertIAE(ws.sendPong(ByteBuffer.allocate(126)));
            assertIAE(ws.sendPong(ByteBuffer.allocate(127)));
            assertIAE(ws.sendPong(ByteBuffer.allocate(128)));
            assertIAE(ws.sendPong(ByteBuffer.allocate(150)));

            assertIAE(ws.sendClose(NORMAL_CLOSURE, stringWithNBytes(124)));
            assertIAE(ws.sendClose(NORMAL_CLOSURE, stringWithNBytes(150)));
            assertIAE(ws.sendClose(NORMAL_CLOSURE - 1, "a reason"));

            // ... add more CF complete exceptionally scenarios
        }
    }

    @Test
    public void testIllegalState() throws IOException {
        try (DummyWebSocketServer server = new DummyWebSocketServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            ws.sendClose(NORMAL_CLOSURE, "normal close").join();

            assertISE(ws.sendPing(ByteBuffer.allocate(125)));
            assertISE(ws.sendPing(ByteBuffer.allocate(124)));
            assertISE(ws.sendPing(ByteBuffer.allocate(  1)));
            assertISE(ws.sendPing(ByteBuffer.allocate(  0)));

            assertISE(ws.sendPong(ByteBuffer.allocate(125)));
            assertISE(ws.sendPong(ByteBuffer.allocate(124)));
            assertISE(ws.sendPong(ByteBuffer.allocate(  1)));
            assertISE(ws.sendPong(ByteBuffer.allocate(  0)));

            // ... add more CF complete exceptionally scenarios
        }
    }

    private void assertIAE(CompletableFuture<?> stage) {
        assertExceptionally(IllegalArgumentException.class, stage);
    }

    private void assertISE(CompletableFuture<?> stage) {
        assertExceptionally(IllegalStateException.class, stage);
    }

    private void assertExceptionally(Class<? extends Throwable> clazz,
                                     CompletableFuture<?> stage) {
        stage.handle((result, error) -> {
            if (error instanceof CompletionException) {
                Throwable cause = error.getCause();
                if (cause == null) {
                    throw new AssertionError("Unexpected null cause: " + error);
                }
                assertException(clazz, cause);
            } else {
                assertException(clazz, error);
            }
            return null;
        }).join();
    }

    private void assertException(Class<? extends Throwable> clazz, Throwable t) {
        if (t == null) {
            throw new AssertionError("Expected " + clazz + ", caught nothing");
        }
        if (!clazz.isInstance(t)) {
            throw new AssertionError("Expected " + clazz + ", caught " + t);
        }
    }

    // ... more API assertions ???
}
