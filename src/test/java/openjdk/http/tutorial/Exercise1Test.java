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

package openjdk.http.tutorial;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import openjdk.http.tutorial.exercise1.Retrievals;
import openjdk.http.tutorial.exercise1.Retrievals.UncheckedObjectMapper;
import static java.lang.System.out;
import static java.nio.file.StandardOpenOption.*;
import static java.util.stream.Collectors.joining;
import static jdk.incubator.http.HttpClient.Version.*;
import static jdk.incubator.http.HttpResponse.BodyHandler.asFile;
import static jdk.incubator.http.HttpResponse.BodyHandler.asString;
import static jdk.incubator.http.HttpResponse.BodyHandler.discard;
import static org.junit.Assert.assertEquals;

/**
 * @author Chris Hegarty
 */
public class Exercise1Test {

    @Test
    public void retrieveTheStatusCode()
        throws IOException, InterruptedException
    {
        URI uri = URI.create("http://httpbin.org/get");

        int expectedStatusCode = statusCode(uri);
        int actualStatusCode = Retrievals.retrieveTheStatusCode(uri);

        assertEquals("Unexpected status code",
                     expectedStatusCode,
                     actualStatusCode);
    }

    @Test
    public void retrieveResourceAsString()
        throws IOException, InterruptedException
    {
        URI uri = URI.create("http://httpbin.org/get");

        String expectedResponseBody = bodyAsString(uri);
        String actualResponseBody = Retrievals.retrieveResourceAsString(uri);

        assertEquals("Unexpected response body",
                     expectedResponseBody,
                     actualResponseBody);
    }

    @Test
    public void retrieveResourceAsFile()
        throws IOException, InterruptedException
    {
        URI uri = URI.create("http://httpbin.org/get");

        Path expectedResponseBodyFile = bodyAsFile(uri);
        Path actualResponseBodyFile = Retrievals.retrieveResourceAsFile(uri);
        byte[] b1 = Files.readAllBytes(expectedResponseBodyFile);
        byte[] b2 = Files.readAllBytes(actualResponseBodyFile);

        System.out.println("CHEGAR  b1 = " + new String(b1));
        System.out.println("CHEGAR  b2 = " + new String(b2));

        Assert.assertArrayEquals("Unexpected response body", b1, b2);
    }

    @Test
    public void retrieveResourceAsStringUsingAsyncAPI()
        throws IOException, InterruptedException
    {
        URI uri = URI.create("http://httpbin.org/get");

        String expectedResponseBody = bodyAsString(uri);
        String actualResponseBody =
                Retrievals.retrieveResourceAsStringUsingAsyncAPI(uri).join();

        assertEquals("Unexpected response body",
                     expectedResponseBody,
                     actualResponseBody);
    }

//    @Test
//    public void JSONBodyAsMap() {
//        throws IOException, InterruptedException
//        URI uri = URI.create("http://httpbin.org/get");
//
//        UncheckedObjectMapper objectMapper = new UncheckedObjectMapper();
//        String expectedResponseBody = objectMapper(bodyAsString(uri));
//        String actualResponseBody =
//                Retrievals.retrieveResourceAsStringUsingAsyncAPI(uri).join();
//
//        assertEquals("Unexpected response body",
//                expectedResponseBody,
//                actualResponseBody);
//    }





    //@Test
    public void postData()
        throws IOException, InterruptedException
    {
        URI uri = URI.create("http://httpbin.org/post");
        String message = "Hello there!";

        String actualResponseBody = Retrievals.postData(uri, message);
        String expectedResponseBody = message;

        assertEquals("Unexpected response body",
                     expectedResponseBody,
                     actualResponseBody);
    }

    /** Wrapper around Jackson's ObjectMapper that provides unchecked readValue. */
    static class UncheckedObjectMapper extends ObjectMapper{

        Map<String,String> readValue(String content) {
            try {
                return this.readValue(content, new TypeReference<>(){});
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }
    }

    private UncheckedObjectMapper objectMapper = new UncheckedObjectMapper();


    /**
     * The Echo JSON service on echo.jsontest.com returns a customized
     * JSON object that you can define through a REST-style URL. For
     * example, calling http://echo.jsontest.com/key/value/one/two
     * will return the following JSON:
     *
     *  {
     *     “one”: “two”,
     *     “key”: “value”
     *  }
     */
    @Test
    public void bodyAsJSON() {
        String[] pairs = new String[] {
           "Name", "chegar",
           "Country", "Ireland",
           "Citizenship", "Irish"
        };
        String path = Arrays.stream(pairs).collect(joining("/"));
        URI uri = URI.create("http://echo.jsontest.com/" + path);

        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        client.sendAsync(request, asString())
                .thenCompose(response -> {   // maps HttpResponse to String
                    assertEquals(response.statusCode(), 200);
                    return CompletableFuture.completedFuture(response.body()); })
                .thenAccept(body -> {        // consumes the response body
                    out.println("received: " + body);
                    Map<String, String> map = objectMapper.readValue(body);

                    assertEquals(map.get("Name"), "chegar");
                    assertEquals(map.get("Country"), "Ireland");
                    assertEquals(map.get("Citizenship"), "Irish"); })
              .join();
    }


    // ---- some trivial infrastructure to help output messages

    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            out.println("\nStarting test: " + description.getMethodName());
        }
        @Override
        protected void finished(Description description) {
            out.println("Finished test: " + description.getMethodName());
        }
        @Override
        protected void failed(Throwable e, Description description) {
            e.printStackTrace();
        }
    };



    // ---- demonstration code below




































    interface Peeker<T> extends Function<T,T>
    {
        void peek(T t);

        default T apply(T t)
        {
            peek(t);
            return t;
        }
    }

    static void assertStatusCode200(HttpResponse<?> response) {
        assertEquals(200, response.statusCode());
    }

    private static int statusCode(URI uri) {
        return HttpClient.newBuilder().version(HTTP_1_1).build()
                .sendAsync(HttpRequest.newBuilder(uri).build(), discard(null))
                .thenApply((Peeker<HttpResponse<?>>)Exercise1Test::assertStatusCode200)
                .thenApply(HttpResponse::statusCode)
                .join();
    }

    private static String bodyAsString(URI uri) {
        return HttpClient.newBuilder().version(HTTP_1_1).build()
                .sendAsync(HttpRequest.newBuilder(uri).build(), asString())
                .thenApply((Peeker<HttpResponse<String>>)Exercise1Test::assertStatusCode200)
                .thenApply(HttpResponse::body)
                .join();
    }

//    private static String bodyAsString(URI uri)
//            throws IOException, InterruptedException
//    {
//        HttpClient client = HttpClient.newBuilder().build();
//        HttpRequest request = HttpRequest.newBuilder(uri)
//                .version(HttpClient.Version.HTTP_1_1)
//                .GET()
//                .build();
//        HttpResponse<String> response = client.send(request, asString());
//
//        Assert.assertEquals(200, response.statusCode());
//
//        return response.body();
//    }

    private static Path bodyAsFile(URI uri)
        throws IOException, InterruptedException
    {
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .version(HttpClient.Version.HTTP_1_1)
                .GET()
                .build();
        Path p = Paths.get("Exercise1Test_bodyAsFile.txt");
        HttpResponse<Path> response = client.send(request,
                asFile(p, CREATE, TRUNCATE_EXISTING, WRITE));

        Assert.assertEquals(200, response.statusCode());

        return response.body();
    }

    private static String postDataGetResponseBody(URI uri, String data)
        throws IOException, InterruptedException
    {
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .version(HttpClient.Version.HTTP_1_1)
                .POST(HttpRequest.BodyProcessor.fromString(data))
                .build();
        HttpResponse<String> response = client.send(request, asString());

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String,Object> map = objectMapper.readValue(response.body(), new TypeReference<>(){});

        System.out.println("CHEGAR map: " + map);

        //JSONObject json = new JSONObject(myResponse);

        Assert.assertEquals(200, response.statusCode());

        return response.body();
    }
}
