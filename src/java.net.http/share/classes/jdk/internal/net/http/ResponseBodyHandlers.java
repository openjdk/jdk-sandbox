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

package jdk.internal.net.http;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessControlContext;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.internal.net.http.ResponseSubscribers.PathSubscriber;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

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
        private final OpenOption[] openOptions;
        private volatile AccessControlContext acc;

        public FileDownloadBodyHandler(Path directory, OpenOption... openOptions) {
            this.directory = directory;
            this.openOptions = openOptions;
        }

        @Override
        public void setAccessControlContext(AccessControlContext acc) {
            this.acc = acc;
        }

        /** The "attachment" disposition-type and separator. */
        static final String DISPOSITION_TYPE = "attachment;";

        /** The "filename" parameter. */
        static final Pattern FILENAME = Pattern.compile("filename\\s*=", CASE_INSENSITIVE);

        static final List<String> PROHIBITED = List.of(".", "..", "", "~" , "|");

        static final UncheckedIOException unchecked(int code,
                                                    HttpHeaders headers,
                                                    String msg) {
            String s = String.format("%s in response [%d, %s]", msg, code, headers);
            return new UncheckedIOException(new IOException(s));
        }

        @Override
        public BodySubscriber<Path> apply(int statusCode, HttpHeaders headers) {
            String dispoHeader = headers.firstValue("Content-Disposition")
                    .orElseThrow(() -> unchecked(statusCode, headers,
                            "No Content-Disposition header"));

            if (!dispoHeader.regionMatches(true, // ignoreCase
                                           0, DISPOSITION_TYPE,
                                           0, DISPOSITION_TYPE.length())) {
                throw unchecked(statusCode, headers, "Unknown Content-Disposition type");
            }

            Matcher matcher = FILENAME.matcher(dispoHeader);
            if (!matcher.find()) {
                throw unchecked(statusCode, headers,
                          "Bad Content-Disposition filename parameter");
            }
            int n = matcher.end();

            int semi = dispoHeader.substring(n).indexOf(";");
            String filenameParam;
            if (semi < 0) {
                filenameParam = dispoHeader.substring(n);
            } else {
                filenameParam = dispoHeader.substring(n, n + semi);
            }

            // strip all but the last path segment
            int x = filenameParam.lastIndexOf("/");
            if (x != -1) {
                filenameParam = filenameParam.substring(x+1);
            }
            x = filenameParam.lastIndexOf("\\");
            if (x != -1) {
                filenameParam = filenameParam.substring(x+1);
            }

            filenameParam = filenameParam.trim();

            if (filenameParam.startsWith("\"")) {  // quoted-string
                if (!filenameParam.endsWith("\"") || filenameParam.length() == 1) {
                    throw unchecked(statusCode, headers,
                            "Badly quoted Content-Disposition filename parameter");
                }
                filenameParam = filenameParam.substring(1, filenameParam.length() -1 );
            } else {  // token,
                if (filenameParam.contains(" ")) {  // space disallowed
                    throw unchecked(statusCode, headers,
                            "unquoted space in Content-Disposition filename parameter");
                }
            }

            if (PROHIBITED.contains(filenameParam)) {
                throw unchecked(statusCode, headers,
                        "Prohibited Content-Disposition filename parameter:"
                                + filenameParam);
            }

            Path file = Paths.get(directory.toString(), filenameParam);

            if (!file.startsWith(directory)) {
                throw unchecked(statusCode, headers,
                        "Resulting file, " + file.toString() + ", outside of given directory");
            }

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
