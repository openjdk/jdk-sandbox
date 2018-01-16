/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8087112 8159814
 * @library /lib/testlibrary server
 * @build jdk.testlibrary.SimpleSSLContext
 * @modules java.base/sun.net.www.http
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.common
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.frame
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.hpack
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=errors,requests,responses ServerPush
 */

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import jdk.incubator.http.*;
import jdk.incubator.http.HttpResponse.BodyHandler;
import jdk.incubator.http.HttpResponse.BodySubscriber;
import jdk.incubator.http.HttpResponse.PushPromiseHandler;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.*;


public class ServerPush {

    static final int LOOPS = 13;
    static final int FILE_SIZE = 512 * 1024 + 343;

    static Path tempFile;

    Http2TestServer server;
    URI uri;

    @BeforeTest
    public void setup() throws Exception {
        server = new Http2TestServer(false, 0);
        server.addHandler(new PushHandler(FILE_SIZE, LOOPS), "/");
        tempFile = TestUtil.getAFile(FILE_SIZE);
        System.out.println("Using temp file:" + tempFile);

        System.err.println("Server listening on port " + server.getAddress().getPort());
        server.start();
        int port = server.getAddress().getPort();
        uri = new URI("http://127.0.0.1:" + port + "/foo/a/b/c");
    }

    @AfterTest
    public void teardown() {
        server.stop();
    }

    static final UnaryOperator<HttpResponse<?>>
            assert200ResponseCode = (response) -> {
                assertEquals(response.statusCode(), 200);
                return response;
    };

    interface Peeker<T> extends UnaryOperator<T> {
        void peek(T t);

        default T apply(T t)
        {
            peek(t);
            return t;
        }
    }

    @Test
    public void testTypeString() throws Exception {
        // use multi-level path
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();

        String tempFileAsString = new String(Files.readAllBytes(tempFile), UTF_8);

        // Attempt 2
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();

        ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<String>>> results = new ConcurrentHashMap<>();


        // Example 2 - of(...) building your own Map, everything as a String
        PushPromiseHandler<String> pph = (initial, pushRequest, acceptor) -> {
            BodyHandler<String> s = BodyHandler.asString(UTF_8);
            CompletableFuture<HttpResponse<String>> cf = acceptor.apply(s);
            results.put(pushRequest, cf);
        };

        CompletableFuture<HttpResponse<String>> cf =
                client.sendAsync(request, BodyHandler.asString(), pph);
        cf.join();
        results.put(request, cf);

        System.err.println(results.size());
        Set<HttpRequest> requests = results.keySet();

        System.err.println("results.size: " + results.size());
        for (HttpRequest r : requests) {
            String result = results.get(r).get().body();
            if (!result.equals(tempFileAsString)) {
                System.err.println("Got [" + result + ", expected [" + tempFileAsString + "]");
            }
        }
        if (requests.size() != LOOPS + 1)
            throw new RuntimeException("Some results missing, expected:" + LOOPS + 1 + ", got:" + results.size());
    }

    // --- Path ---

    static final Path dir = Paths.get(".", "serverPush");
    static BodyHandler<Path> requestToPath(HttpRequest req) {
        URI u = req.uri();
        Path path = Paths.get(dir.toString(), u.getPath());
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException ee) {
            throw new UncheckedIOException(ee);
        }
        return BodyHandler.asFile(path);
    };

    @Test
    public void testTypePath() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();

        ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<Path>>> map
                = new ConcurrentHashMap<>();

        // Example 4 - of(...) building your own Map, everything as a Path
        PushPromiseHandler<Path> pushPromiseHandler = (initial, pushRequest, acceptor) -> {
            BodyHandler<Path> pp = requestToPath(pushRequest);
            CompletableFuture<HttpResponse<Path>> cf = acceptor.apply(pp);
            map.put(pushRequest, cf);
        };

        CompletableFuture<HttpResponse<Path>> cf =
                client.sendAsync(request, requestToPath(request), pushPromiseHandler);
        cf.join();
        map.put(request, cf);

        System.err.println("map.size: " + map.size());
        for (HttpRequest r : map.keySet()) {
            Path path = map.get(r).get().body();
            String fileAsString = new String(Files.readAllBytes(path), UTF_8);
            String tempFileAsString = new String(Files.readAllBytes(tempFile), UTF_8);
            assertEquals(fileAsString, tempFileAsString);
        }
        assertEquals(map.size(),  LOOPS + 1);
    }

    // ---  Consumer<byte[]> ---

    static class ByteArrayConsumer implements Consumer<Optional<byte[]>> {
        volatile List<byte[]> listByteArrays = new ArrayList<>();
        volatile byte[] accumulatedBytes;

        public byte[] getAccumulatedBytes() { return accumulatedBytes; }

        @Override
        public void accept(Optional<byte[]> optionalBytes) {
            assert accumulatedBytes == null;
            if (!optionalBytes.isPresent()) {
                int size = listByteArrays.stream().mapToInt(ba -> ba.length).sum();
                ByteBuffer bb = ByteBuffer.allocate(size);
                listByteArrays.stream().forEach(ba -> bb.put(ba));
                accumulatedBytes = bb.array();
            } else {
                listByteArrays.add(optionalBytes.get());
            }
        }
    }

    @Test
    public void testTypeByteArrayConsumer() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();

        ConcurrentMap<HttpRequest, CompletableFuture<HttpResponse<Void>>> resultMap
                = new ConcurrentHashMap<>();
        Map<HttpRequest,ByteArrayConsumer> byteArrayConsumerMap
                = new ConcurrentHashMap<>();

        ByteArrayConsumer bac = new ByteArrayConsumer();
        byteArrayConsumerMap.put(request, bac);

        // Example 5 - withXXX and everything as a consumer of optional byte[]
        PushPromiseHandler<Void> pushPromiseHandler =
                PushPromiseHandler.withPushPromises(pushRequest -> {
                                                       ByteArrayConsumer bc = new ByteArrayConsumer();
                                                       byteArrayConsumerMap.put(pushRequest, bc);
                                                       return BodyHandler.asByteArrayConsumer(bc);
                                                    },
                                                    resultMap);

        CompletableFuture<HttpResponse<Void>> cf =
                client.sendAsync(request, BodyHandler.asByteArrayConsumer(bac), pushPromiseHandler);
        cf.join();
        resultMap.put(request, cf);

        System.err.println("map.size: " + resultMap.size());
        for (HttpRequest r : resultMap.keySet()) {
            resultMap.get(r).join();
            byte[] ba = byteArrayConsumerMap.get(r).getAccumulatedBytes();
            String result = new String(ba, UTF_8);
            System.out.println("HEGO result=" + result);
            System.out.println("HEGO result.length=" + result.length());
            System.err.printf("%s -> %s\n", r.uri().toString(), result);
            String tempFileAsString = new String(Files.readAllBytes(tempFile), UTF_8);
            System.out.println("HEGO tempFileAsString=" + tempFileAsString);
            System.out.println("HEGO tempFileAsString.length=" + tempFileAsString.length());
            assertEquals(result, tempFileAsString);
        }

        assertEquals(resultMap.size(), LOOPS + 1);
    }
}
