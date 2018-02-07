/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

package java.net.http.internal.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/*
 * Transport needs some way to asynchronously notify the send operation has been
 * completed. It can have several different designs each of which has its own
 * pros and cons:
 *
 *     (1) void sendMessage(..., Callback)
 *     (2) CompletableFuture<T> sendMessage(...)
 *     (3) CompletableFuture<T> sendMessage(..., Callback)
 *     (4) boolean sendMessage(..., Callback) throws IOException
 *     ...
 *
 * If Transport's users use CFs, (1) forces these users to create CFs and pass
 * them to the callback. If any additional (dependant) action needs to be
 * attached to the returned CF, this means an extra object (CF) must be created
 * in (2). (3) and (4) solves both issues, however (4) does not abstract out
 * when exactly the operation has been performed. So the handling code needs to
 * be repeated twice. And that leads to 2 different code paths (more bugs).
 * Unless designed for this, the user should not assume any specific order of
 * completion in (3) (e.g. callback first and then the returned CF).
 *
 * The only parametrization of Transport<T> used is Transport<WebSocket>. The
 * type parameter T was introduced solely to avoid circular dependency between
 * Transport and WebSocket. After all, instances of T are used solely to
 * complete CompletableFutures. Transport doesn't care about the exact type of
 * T.
 *
 * This way the Transport is fully in charge of creating CompletableFutures.
 * On the one hand, Transport may use it to cache/reuse CompletableFutures. On
 * the other hand, the class that uses Transport, may benefit by not converting
 * from CompletableFuture<K> returned from Transport, to CompletableFuture<V>
 * needed by the said class.
 */
public interface Transport<T> {

    CompletableFuture<T> sendText(CharSequence message, boolean isLast);

    CompletableFuture<T> sendBinary(ByteBuffer message, boolean isLast);

    CompletableFuture<T> sendPing(ByteBuffer message);

    CompletableFuture<T> sendPong(ByteBuffer message);

    CompletableFuture<T> sendClose(int statusCode, String reason);

    void request(long n);

    /*
     * Why is this method needed? Since Receiver operates through callbacks
     * this method allows to abstract out what constitutes as a message being
     * received (i.e. to decide outside this type when exactly one should
     * decrement the demand).
     */
    void acknowledgeReception();

    void closeOutput() throws IOException;

    void closeInput() throws IOException;
}
