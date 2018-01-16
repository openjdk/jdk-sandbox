/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http;

import java.security.AccessControlContext;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import jdk.incubator.http.HttpResponse.PushPromiseHandler;
import jdk.incubator.http.HttpResponse.UntrustedBodyHandler;
import jdk.incubator.http.internal.common.MinimalFuture;
import jdk.incubator.http.internal.common.Log;

/**
 * One PushGroup object is associated with the parent Stream of the pushed
 * Streams. This keeps track of all common state associated with the pushes.
 */
class PushGroup<T> {
    private final HttpRequest initiatingRequest;

    final CompletableFuture<Void> noMorePushesCF;

    volatile Throwable error; // any exception that occurred during pushes

    // user's subscriber object
    final PushPromiseHandler<T> pushPromiseHandler;

    private final AccessControlContext acc;

    int numberOfPushes;
    int remainingPushes;
    boolean noMorePushes = false;

    PushGroup(PushPromiseHandler<T> pushPromiseHandler,
              HttpRequestImpl initiatingRequest,
              AccessControlContext acc) {
        this(pushPromiseHandler, initiatingRequest, new MinimalFuture<>(), acc);
    }

    // Check mainBodyHandler before calling nested constructor.
    private PushGroup(HttpResponse.PushPromiseHandler<T> pushPromiseHandler,
                      HttpRequestImpl initiatingRequest,
                      CompletableFuture<HttpResponse<T>> mainResponse,
                      AccessControlContext acc) {
        this.noMorePushesCF = new MinimalFuture<>();
        this.pushPromiseHandler = pushPromiseHandler;
        this.initiatingRequest = initiatingRequest;
        // Restricts the file publisher with the senders ACC, if any
        if (pushPromiseHandler instanceof UntrustedBodyHandler)
            ((UntrustedBodyHandler)this.pushPromiseHandler).setAccessControlContext(acc);
        this.acc = acc;
    }

    static class Acceptor<T> {
        final HttpRequest initiator, push;
        volatile HttpResponse.BodyHandler<T> bodyHandler = null;
        volatile CompletableFuture<HttpResponse<T>> cf;

        Acceptor(HttpRequest initiator, HttpRequest push) {
            this.initiator = initiator;
            this.push = push;
        }

        CompletableFuture<HttpResponse<T>> accept(HttpResponse.BodyHandler<T> bodyHandler) {
            Objects.requireNonNull(bodyHandler);
            cf = new MinimalFuture<>();
            if (this.bodyHandler != null)
                throw new IllegalStateException();
            this.bodyHandler = bodyHandler;
            return cf;
        }

        HttpResponse.BodyHandler<T> bodyHandler() {
            return bodyHandler;
        }

        CompletableFuture<HttpResponse<T>> cf() {
            return cf;
        }

        boolean accepted() {
            return cf != null;
        }
    }

    Acceptor<T> acceptPushRequest(HttpRequest pushRequest) {
        Acceptor<T> acceptor = new Acceptor<>(initiatingRequest, pushRequest);

        pushPromiseHandler.applyPushPromise(initiatingRequest, pushRequest, acceptor::accept);

        if (acceptor.accepted()) {
            numberOfPushes++;
            remainingPushes++;
        }
        return acceptor;

    }

    // This is called when the main body response completes because it means
    // no more PUSH_PROMISEs are possible

    synchronized void noMorePushes(boolean noMore) {
        noMorePushes = noMore;
        checkIfCompleted();
        noMorePushesCF.complete(null);
    }

    CompletableFuture<Void> pushesCF() {
        return noMorePushesCF;
    }

    synchronized boolean noMorePushes() {
        return noMorePushes;
    }

    synchronized void pushCompleted() {
        remainingPushes--;
        checkIfCompleted();
    }

    synchronized void checkIfCompleted() {
        if (Log.trace()) {
            Log.logTrace("PushGroup remainingPushes={0} error={1} noMorePushes={2}",
                         remainingPushes,
                         (error==null)?error:error.getClass().getSimpleName(),
                         noMorePushes);
        }
        if (remainingPushes == 0 && error == null && noMorePushes) {
            if (Log.trace()) {
                Log.logTrace("push completed");
            }
        }
    }

    synchronized void pushError(Throwable t) {
        if (t == null) {
            return;
        }
        this.error = t;
    }
}
