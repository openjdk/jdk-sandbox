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
 * @run testng/othervm
 *      -Djdk.internal.httpclient.websocket.debug=true
 *       PendingBinaryPingClose
 */

import org.testng.annotations.Test;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.net.http.HttpClient.newHttpClient;

public class PendingBinaryPingClose extends PendingOperations {

    @Test(dataProvider = "booleans")
    public void pendingBinaryPingClose(boolean last) throws Exception {
        server = Support.notReadingServer();
        server.open();
        webSocket = newHttpClient().newWebSocketBuilder()
                .buildAsync(server.getURI(), new WebSocket.Listener() { })
                .join();
        ByteBuffer data = ByteBuffer.allocate(65536);
        CompletableFuture<WebSocket> cfBinary;
        for (int i = 0; ; i++) {  // fill up the send buffer
            long start = System.currentTimeMillis();
            System.out.printf("begin cycle #%s at %s%n", i, start);
            cfBinary = webSocket.sendBinary(data, last);
            try {
                cfBinary.get(5, TimeUnit.SECONDS);
                data.clear();
            } catch (TimeoutException e) {
                break;
            } finally {
                long stop = System.currentTimeMillis();
                System.out.printf("end cycle #%s at %s (%s ms)%n", i, stop, stop - start);
            }
        }
        assertFails(ISE, webSocket.sendText("", true));
        assertFails(ISE, webSocket.sendText("", false));
        assertFails(ISE, webSocket.sendBinary(ByteBuffer.allocate(0), true));
        assertFails(ISE, webSocket.sendBinary(ByteBuffer.allocate(0), false));
        CompletableFuture<WebSocket> cfPing = webSocket.sendPing(ByteBuffer.allocate(125));
        assertHangs(cfPing);
        assertFails(ISE, webSocket.sendPing(ByteBuffer.allocate(125)));
        assertFails(ISE, webSocket.sendPong(ByteBuffer.allocate(125)));
        CompletableFuture<WebSocket> cfClose = webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
        assertHangs(cfClose);
        webSocket.abort();
        assertFails(IOE, cfBinary);
        assertFails(IOE, cfPing);
        assertFails(IOE, cfClose);
    }
}
