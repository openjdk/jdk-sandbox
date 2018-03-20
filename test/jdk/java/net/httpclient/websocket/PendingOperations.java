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

import org.testng.annotations.AfterTest;
import org.testng.annotations.DataProvider;

import java.io.IOException;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;

/* Common infrastructure for tests that check pending operations */
public class PendingOperations {

    static final Class<IllegalStateException> ISE = IllegalStateException.class;
    static final Class<IOException> IOE = IOException.class;

    DummyWebSocketServer server;
    WebSocket webSocket;

    @AfterTest
    public void cleanup() {
        server.close();
        webSocket.abort();
    }

    /* shortcut */
    static void assertHangs(CompletionStage<?> stage) {
        Support.assertHangs(stage);
    }

    /* shortcut */
    static void assertFails(Class<? extends Throwable> clazz,
                            CompletionStage<?> stage) {
        Support.assertCompletesExceptionally(clazz, stage);
    }

    @DataProvider(name = "booleans")
    public Object[][] booleans() {
        return new Object[][]{{Boolean.TRUE}, {Boolean.FALSE}};
    }
}
