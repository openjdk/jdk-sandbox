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

package jdk.incubator.http.internal;

import java.io.IOException;
import java.net.URI;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessControlContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import jdk.incubator.http.HttpHeaders;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import jdk.incubator.http.HttpResponse.BodyHandler;
import jdk.incubator.http.HttpResponse.BodySubscriber;
import jdk.incubator.http.internal.ResponseSubscribers.PathSubscriber;
import static jdk.incubator.http.internal.common.Utils.unchecked;

public final class ResponseBodyHandlers {

    private ResponseBodyHandlers() { }

    /**
     * A Path body handler.
     *
     * Note: Exists mainly too allow setting of the senders ACC post creation of
     * the handler.
     */
    public static class PathBodyHandler implements UntrustedBodyHandler<Path> {
        private final Path file;
        private final OpenOption[]openOptions;
        private volatile AccessControlContext acc;

        public PathBodyHandler(Path file, OpenOption... openOptions) {
            this.file = file;
            this.openOptions = openOptions;
        }

        @Override
        public void setAccessControlContext(AccessControlContext acc) {
            this.acc = acc;
        }

        @Override
        public BodySubscriber<Path> apply(int statusCode, HttpHeaders headers) {
            PathSubscriber bs = (PathSubscriber) asFileImpl(file, openOptions);
            bs.setAccessControlContext(acc);
            return bs;
        }
    }

    /** With push promise Map implementation */
    public static class PushPromisesHandlerWithMap<T>
        implements HttpResponse.PushPromiseHandler<T>
    {
        private final ConcurrentMap<HttpRequest,CompletableFuture<HttpResponse<T>>> pushPromisesMap;
        private final Function<HttpRequest,BodyHandler<T>> pushPromiseHandler;

        public PushPromisesHandlerWithMap(Function<HttpRequest,BodyHandler<T>> pushPromiseHandler,
                                          ConcurrentMap<HttpRequest,CompletableFuture<HttpResponse<T>>> pushPromisesMap) {
            this.pushPromiseHandler = pushPromiseHandler;
            this.pushPromisesMap = pushPromisesMap;
        }

        @Override
        public void applyPushPromise(
                HttpRequest initiatingRequest, HttpRequest pushRequest,
                Function<BodyHandler<T>,CompletableFuture<HttpResponse<T>>> acceptor)
        {
            URI initiatingURI = initiatingRequest.uri();
            URI pushRequestURI = pushRequest.uri();
            if (!initiatingURI.getHost().equalsIgnoreCase(pushRequestURI.getHost()))
                return;

            int initiatingPort = initiatingURI.getPort();
            if (initiatingPort == -1 ) {
                if ("https".equalsIgnoreCase(initiatingURI.getScheme()))
                    initiatingPort = 443;
                else
                    initiatingPort = 80;
            }
            int pushPort = pushRequestURI.getPort();
            if (pushPort == -1 ) {
                if ("https".equalsIgnoreCase(pushRequestURI.getScheme()))
                    pushPort = 443;
                else
                    pushPort = 80;
            }
            if (initiatingPort != pushPort)
                return;

            CompletableFuture<HttpResponse<T>> cf =
                    acceptor.apply(pushPromiseHandler.apply(pushRequest));
            pushPromisesMap.put(pushRequest, cf);
        }
    }

    // Similar to Path body handler, but for file download. Supports setting ACC.
    public static class FileDownloadBodyHandler implements UntrustedBodyHandler<Path> {
        private final Path directory;
        private final OpenOption[]openOptions;
        private volatile AccessControlContext acc;

        public FileDownloadBodyHandler(Path directory, OpenOption... openOptions) {
            this.directory = directory;
            this.openOptions = openOptions;
        }

        @Override
        public void setAccessControlContext(AccessControlContext acc) {
            this.acc = acc;
        }

        @Override
        public BodySubscriber<Path> apply(int statusCode, HttpHeaders headers) {
            String dispoHeader = headers.firstValue("Content-Disposition")
                    .orElseThrow(() -> unchecked(new IOException("No Content-Disposition")));
            if (!dispoHeader.startsWith("attachment;")) {
                throw unchecked(new IOException("Unknown Content-Disposition type"));
            }
            int n = dispoHeader.indexOf("filename=");
            if (n == -1) {
                throw unchecked(new IOException("Bad Content-Disposition type"));
            }
            int lastsemi = dispoHeader.lastIndexOf(';');
            String disposition;
            if (lastsemi < n) {
                disposition = dispoHeader.substring(n + 9);
            } else {
                disposition = dispoHeader.substring(n + 9, lastsemi);
            }
            Path file = Paths.get(directory.toString(), disposition);

            PathSubscriber bs = (PathSubscriber)asFileImpl(file, openOptions);
            bs.setAccessControlContext(acc);
            return bs;
        }
    }

    // no security check
    private static BodySubscriber<Path> asFileImpl(Path file, OpenOption... openOptions) {
        return new ResponseSubscribers.PathSubscriber(file, openOptions);
    }
}
