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

package openjdk.http.tutorial.exercise1;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import jdk.incubator.http.HttpResponse.BodyHandler;

import static java.lang.System.out;
import static java.util.stream.Collectors.joining;
import static jdk.incubator.http.HttpClient.Version.*;
import static jdk.incubator.http.HttpResponse.BodyHandler.*;

/**
 * The JDK HTTP Client tutorial.
 *
 * Consists of a set of exercises that must be completed. Each exercise is
 * represented as a method, whose method body must be written successfully to
 * complete the exercise. Each method-level comment describes the particular
 * exercise, what must be done, and may provide a hint to help complete it.
 *
 * The test source tree contains `Exercise1Test` that contains a set of unit
 * tests, similarly named to the to the methods in this class, that, when
 * executed, verify that the exercises have been completed successfully ( by
 * checking the value returned by each exercise ). These can be run through the
 * Maven Project's `test` target.
 *
 *
 * @author Chris Hegarty
 */
public class Retrievals {

    /**
     * Exercise 1.
     *
     * Retrieve the response status code from a request to the given
     * URI. The returned response code will be a int.
     *
     * Hint: use the discard BodyHandler since the response body is not
     * interesting.
     *
     * Hint: static imports reduce boilerplate when using BodyHandlers
     * and BodyProcessors, e.g. import static
     * jdk.incubator.http.HttpResponse.BodyHandler.discard
     */
    public static int retrieveTheStatusCode(URI uri)
        throws IOException, InterruptedException
    {
        HttpClient client = HttpClient.newBuilder().version(HTTP_1_1).build();
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        HttpResponse<?> response = client.send(request, discard(null));

        return response.statusCode();
    }

    /**
     * Exercise 1.
     *
     * Retrieve the response body from a given URI. Return the response
     * body as a String.
     *
     * Hint: use the asString BodyHandler to convert the HTTP response
     * body to a String.
     *
     * Hint: static imports reduce boilerplate when using BodyHandlers
     * and BodyProcessors, e.g. import static
     * jdk.incubator.http.HttpResponse.BodyHandler.asString
     */
    public static String retrieveResourceAsString(URI uri)
        throws IOException, InterruptedException
    {
        HttpClient client = HttpClient.newBuilder().version(HTTP_1_1).build();
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        HttpResponse<String> response = client.send(request, asString());

        return response.body();
    }

    /**
     * Exercise 2.
     *
     * Retrieve the response body from a given URI, streaming teh body
     * out to a file. Return the file's Path.
     *
     * Hint: use {@linkplain BodyHandler#asFile} to stream the HTTP
     * response body to a file.
     */
    public static Path retrieveResourceAsFile(URI uri)
        throws IOException, InterruptedException
    {
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .version(HTTP_1_1)
                .GET()
                .build();
        HttpResponse<Path> response = client.send(request,
                asFile(Paths.get("retrieveResourceAsFile.txt"), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE));

        System.out.println("CHEGAR:   sc=" + response.statusCode());
        System.out.println("CHEGAR:   by=" + Files.readAllLines(response.body()).stream().collect(joining()));

        return response.body();
    }


    /**
     * Asserts that the response code is 200 ( OK ).
     *
     * Can be used in CompletableFuture pipelines when checking the
     * response of an {@linkplain HttpClient#sendAsync} call. For
     * example:
     *    client.sendAsync(request, bodyHandler)
     *          .thenApply(Retrievals::require200StatusCode)
     *          .thenApply(...)
     *
     */
    public static <T> HttpResponse<T> require200StatusCode(HttpResponse<T> response) {
        int sc = response.statusCode();
        if (sc != 200) {
            IOException e = new IOException("Expected 200, got: " + sc);
            throw new UncheckedIOException(e);
        }
        return response;
    }

    /**
     *  Exercise 3.
     *
     * Retrieve the response body from a given URI, using the
     * asynchronous send API, sendAsync. Return a CompletableFuture that
     * completes with the response body as a String.
     *
     * Hint: The {@linkplain CompletableFuture#thenApply(Function)}
     * method can be used to map the HttpResponse to a String.
     */
    public static CompletableFuture<String> retrieveResourceAsStringUsingAsyncAPI(URI uri) {
        return HttpClient.newHttpClient()
                .sendAsync(HttpRequest.newBuilder(uri).version(HTTP_1_1).build(), asString())  // TODO: why version needed?
                .thenApply(Retrievals::require200StatusCode)
                .thenApply(HttpResponse::body);
    }


    /**
     * Wrapper around Jackson's ObjectMapper that provides an unchecked
     * {@code readValue}, what can be used to help solve the next
     * exercise, 4.
     */
    public static class UncheckedObjectMapper extends ObjectMapper {

        /** Parses the given JSON string into a Map. */
        Map<String,String> readValue(String content) {
            try {
                return this.readValue(content, new TypeReference<>(){});
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }
    }

    /**
     * Exercise 4.
     *
     * Retrieve the response body from a given URI. The response body
     * will be in the JSON format. The Jackson based UncheckedObjectMapper
     * ( above ) can be used to parse the String response body into a
     * Map.
     *
     * Hint: The asynchronous send API will allow construction of a
     * pipeline of CompletableFutures.
     *
     * Hint: The {@linkplain CompletableFuture#thenApply(Function)}
     * method can be used to map the HttpResponse to a String, and then
     * again from a Sting to a Map ( via the object mapper ).
     */
    public CompletableFuture<Map<String,String>> JSONBodyAsMap(URI uri) {
        UncheckedObjectMapper objectMapper = new UncheckedObjectMapper();
        return HttpClient.newHttpClient()
                .sendAsync(HttpRequest.newBuilder(uri).version(HTTP_1_1).build(), asString())
                .thenApply(HttpResponse::body)
                .thenApply(objectMapper::readValue);
    }



    /**
     * Post the given {@code data}, and receive the same data in
     * response. Return the response body data as a String.
     */
    public static String postData(URI uri, String data)
        throws IOException, InterruptedException
    {
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .version(HTTP_1_1)
                .POST(HttpRequest.BodyProcessor.fromString(data))
                .build();
        HttpResponse<String> response = client.send(request, asString());

        return response.body();
    }


     // JSON processor
}
