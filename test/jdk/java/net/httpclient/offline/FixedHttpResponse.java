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

import javax.net.ssl.SSLParameters;
import java.net.URI;
import java.util.Optional;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpHeaders;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;

/**
 * An HttpResponse consisting of the given state.
 */
public class FixedHttpResponse<T> extends HttpResponse<T> {

    private final int statusCode;
    private final HttpRequest request;
    private final HttpHeaders headers;
    private final T body;
    private final SSLParameters sslParameters;
    private final URI uri;
    private final HttpClient.Version version;

    public FixedHttpResponse(int statusCode,
                             HttpRequest request,
                             HttpHeaders headers,
                             T body,
                             SSLParameters sslParameters,
                             URI uri,
                             HttpClient.Version version) {
        this.statusCode = statusCode;
        this.request = request;
        this.headers = headers;
        this.body = body;
        this.sslParameters = sslParameters;
        this.uri = uri;
        this.version = version;
    }

    @Override
    public int statusCode() {
        return statusCode;
    }

    @Override
    public HttpRequest request() {
        return request;
    }

    @Override
    public Optional<HttpResponse<T>> previousResponse() {
        return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public T body() {
        return body;
    }

    @Override
    public SSLParameters sslParameters() {
        return sslParameters;
    }

    @Override
    public URI uri() {
        return uri;
    }

    @Override
    public HttpClient.Version version() {
        return version;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        return sb.append(super.toString()).append(" [ ")
                .append("status code: ").append(statusCode)
                .append(", request: ").append(request)
                .append(", headers: ").append(headers)
                .append(" ]")
                .toString();
    }
}
