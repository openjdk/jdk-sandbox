/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

package java.net.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.net.ssl.SSLParameters;
import jdk.internal.net.http.BufferingSubscriber;
import jdk.internal.net.http.LineSubscriberAdapter;
import jdk.internal.net.http.ResponseBodyHandlers.FileDownloadBodyHandler;
import jdk.internal.net.http.ResponseBodyHandlers.PathBodyHandler;
import jdk.internal.net.http.ResponseBodyHandlers.PushPromisesHandlerWithMap;
import jdk.internal.net.http.ResponseSubscribers;
import static jdk.internal.net.http.common.Utils.charsetFrom;

/**
 * An HTTP response.
 *
 * <p> An {@code HttpResponse} is not created directly, but rather returned as
 * a result of sending an {@link HttpRequest}. An {@code HttpResponse} is
 * made available when the response status code and headers have been received,
 * and typically after the response body has also been completely received.
 * Whether or not the {@code HttpResponse} is made available before the response
 * body has been completely received depends on the {@link BodyHandler
 * BodyHandler} provided when sending the {@code HttpRequest}.
 *
 * <p> This class provides methods for accessing the response status code,
 * headers, the response body, and the {@code HttpRequest} corresponding
 * to this response.
 **
 * @param <T> the response body type
 *
 * @since 11
 */
public abstract class HttpResponse<T> {

    /**
     * Creates an HttpResponse.
     */
    protected HttpResponse() { }

    /**
     * Returns the status code for this response.
     *
     * @return the response code
     */
    public abstract int statusCode();

    /**
     * Returns the {@link HttpRequest} corresponding to this response.
     *
     * <p> This may not be the original request provided by the caller,
     * for example, if that request was redirected.
     *
     * @see #previousResponse()
     *
     * @return the request
     */
    public abstract HttpRequest request();

    /**
     * Returns an {@code Optional} containing the previous intermediate response
     * if one was received. An intermediate response is one that is received
     * as a result of redirection or authentication. If no previous response
     * was received then an empty {@code Optional} is returned.
     *
     * @return an Optional containing the HttpResponse, if any.
     */
    public abstract Optional<HttpResponse<T>> previousResponse();

    /**
     * Returns the received response headers.
     *
     * @return the response headers
     */
    public abstract HttpHeaders headers();

    /**
     * Returns the body. Depending on the type of {@code T}, the returned body
     * may represent the body after it was read (such as {@code byte[]}, or
     * {@code String}, or {@code Path}) or it may represent an object with
     * which the body is read, such as an {@link java.io.InputStream}.
     *
     * <p> If this {@code HttpResponse} was returned from an invocation of
     * {@link #previousResponse()} then this method returns {@code null}
     *
     * @return the body
     */
    public abstract T body();

    /**
     * Returns the {@link javax.net.ssl.SSLParameters} in effect for this
     * response. Returns {@code null} if this is not a HTTPS response.
     *
     * @return the SSLParameters associated with the response
     */
    public abstract SSLParameters sslParameters();

    /**
     * Returns the {@code URI} that the response was received from. This may be
     * different from the request {@code URI} if redirection occurred.
     *
     * @return the URI of the response
     */
    public abstract URI uri();

    /**
     * Returns the HTTP protocol version that was used for this response.
     *
     * @return HTTP protocol version
     */
    public abstract HttpClient.Version version();


    private static String pathForSecurityCheck(Path path) {
        return path.toFile().getPath();
    }


    /**
     * A handler for response bodies.
     *
     * <p> The {@code BodyHandler} interface allows inspection of the response
     * code and headers, before the actual response body is received, and is
     * responsible for creating the response {@link BodySubscriber
     * BodySubscriber}. The {@code BodySubscriber} consumes the actual response
     * body bytes and converts them into a higher-level Java type.
     *
     * <p> A {@code BodyHandler} is a function that takes two parameters: the
     * response status code and the response headers; and which returns a
     * {@code BodySubscriber}. The {@code BodyHandler} is invoked when the
     * response status code and headers are available, but before the response
     * body bytes are received.
     *
     * <p> A number of convenience static factory methods are provided that
     * return pre-defined implementations that do not examine the status code
     * (meaning the body is always accepted):
     * <ul><li>{@link #asByteArray() }</li>
     * <li>{@link #asByteArrayConsumer(java.util.function.Consumer)
     * asByteArrayConsumer(Consumer)}</li>
     * <li>{@link #asString(java.nio.charset.Charset) asString(Charset)}</li>
     * <li>{@link #asFile(Path, OpenOption...) asFile(Path,OpenOption...)}</li>
     * <li>{@link #asFileDownload(java.nio.file.Path,OpenOption...)
     * asFileDownload(Path,OpenOption...)}</li>
     * <li>{@link #asInputStream() asInputStream()}</li>
     * <li>{@link #discard() }</li>
     * <li>{@link #replace(Object) }</li>
     * <li>{@link #buffering(BodyHandler, int) buffering(BodyHandler,int)}</li>
     * </ul>
     *
     * <p> These implementations return an equivalently named {@code
     * BodySubscriber}. Alternatively, a custom handler can be used to examine
     * the status code or headers, and return different body subscribers as
     * appropriate.
     *
     * <p><b>Examples:</b>
     *
     * <p> The first example uses one of the predefined handler functions that
     * always process the response body in the same way.
     * <pre>{@code   HttpRequest request = HttpRequest.newBuilder()
     *        .uri(URI.create("http://www.foo.com/"))
     *        .build();
     *  client.sendAsync(request, BodyHandler.asFile(Paths.get("/tmp/f")))
     *        .thenApply(HttpResponse::body)
     *        .thenAccept(System.out::println) }</pre>
     * Note, that even though these pre-defined handlers do not examine the
     * response code, the response code and headers are always retrievable from
     * the {@link HttpResponse}, when it is returned.
     *
     * <p> In the second example, the function returns a different subscriber
     * depending on the status code.
     * <pre>{@code   HttpRequest request = HttpRequest.newBuilder()
     *        .uri(URI.create("http://www.foo.com/"))
     *        .build();
     *  BodyHandler bodyHandler = (status, headers) -> status == 200
     *                      ? BodySubscriber.asFile(Paths.get("/tmp/f"))
     *                      : BodySubscriber.replace(Paths.get("/NULL")));
     *  client.sendAsync(request, bodyHandler))
     *        .thenApply(HttpResponse::body)
     *        .thenAccept(System.out::println) }</pre>
     *
     * @param <T> the response body type
     */
    @FunctionalInterface
    public interface BodyHandler<T> {
        /**
         * Returns a {@link BodySubscriber BodySubscriber} considering the
         * given response status code and headers. This method is invoked before
         * the actual response body bytes are read and its implementation must
         * return a {@code BodySubscriber} to consume the response body bytes.
         *
         * <p> The response body can be discarded using one of {@link
         * #discard() discard} or {@link #replace(Object) replace}.
         *
         * @param statusCode the HTTP status code received
         * @param responseHeaders the response headers received
         * @return a body subscriber
         */
        public BodySubscriber<T> apply(int statusCode, HttpHeaders responseHeaders);


        /**
         * Returns a response body handler that returns a {@link BodySubscriber
         * BodySubscriber}{@code <Void>} obtained from {@link
         * BodySubscriber#fromSubscriber(Subscriber)}, with the given
         * {@code subscriber}.
         *
         * <p> The response body is not available through this, or the {@code
         * HttpResponse} API, but instead all response body is forwarded to the
         * given {@code subscriber}, which should make it available, if
         * appropriate, through some other mechanism, e.g. an entry in a
         * database, etc.
         *
         * @apiNote This method can be used as an adapter between {@code
         * BodySubscriber} and {@code Flow.Subscriber}.
         *
         * <p> For example:
         * <pre> {@code  TextSubscriber subscriber = new TextSubscriber();
         *  HttpResponse<Void> response = client.sendAsync(request,
         *      BodyHandler.fromSubscriber(subscriber)).join();
         *  System.out.println(response.statusCode()); }</pre>
         *
         * @param subscriber the subscriber
         * @return a response body handler
         */
        public static BodyHandler<Void>
        fromSubscriber(Subscriber<? super List<ByteBuffer>> subscriber) {
            Objects.requireNonNull(subscriber);
            return (status, headers) -> BodySubscriber.fromSubscriber(subscriber,
                                                                      s -> null);
        }

        /**
         * Returns a response body handler that returns a {@link BodySubscriber
         * BodySubscriber}{@code <T>} obtained from {@link
         * BodySubscriber#fromSubscriber(Subscriber, Function)}, with the
         * given {@code subscriber} and {@code finisher} function.
         *
         * <p> The given {@code finisher} function is applied after the given
         * subscriber's {@code onComplete} has been invoked. The {@code finisher}
         * function is invoked with the given subscriber, and returns a value
         * that is set as the response's body.
         *
         * @apiNote This method can be used as an adapter between {@code
         * BodySubscriber} and {@code Flow.Subscriber}.
         *
         * <p> For example:
         * <pre> {@code  TextSubscriber subscriber = ...;  // accumulates bytes and transforms them into a String
         *  HttpResponse<String> response = client.sendAsync(request,
         *      BodyHandler.fromSubscriber(subscriber, TextSubscriber::getTextResult)).join();
         *  String text = response.body(); }</pre>
         *
         * @param <S> the type of the Subscriber
         * @param <T> the type of the response body
         * @param subscriber the subscriber
         * @param finisher a function to be applied after the subscriber has completed
         * @return a response body handler
         */
        public static <S extends Subscriber<? super List<ByteBuffer>>,T> BodyHandler<T>
        fromSubscriber(S subscriber, Function<S,T> finisher) {
            Objects.requireNonNull(subscriber);
            Objects.requireNonNull(finisher);
            return (status, headers) -> BodySubscriber.fromSubscriber(subscriber,
                                                                      finisher);
        }

        /**
         * Returns a response body handler that returns a {@link BodySubscriber
         * BodySubscriber}{@code <Void>} obtained from {@link
         * BodySubscriber#fromLineSubscriber(Subscriber, Function, Charset, String)
         * BodySubscriber.fromLineSubscriber(subscriber, s -> null, charset, null)},
         * with the given {@code subscriber}.
         * The {@link Charset charset} used to decode the response body bytes is
         * obtained from the HTTP response headers as specified by {@link #asString()},
         * and lines are delimited in the manner of {@link BufferedReader#readLine()}.
         *
         * <p> The response body is not available through this, or the {@code
         * HttpResponse} API, but instead all response body is forwarded to the
         * given {@code subscriber}, which should make it available, if
         * appropriate, through some other mechanism, e.g. an entry in a
         * database, etc.
         *
         * @apiNote This method can be used as an adapter between a {@code
         * BodySubscriber} and a text based {@code Flow.Subscriber} that parses
         * text line by line.
         *
         * <p> For example:
         * <pre> {@code  // A PrintSubscriber that implements Flow.Subscriber<String>
         *  // and print lines received by onNext() on System.out
         *  PrintSubscriber subscriber = new PrintSubscriber(System.out);
         *  client.sendAsync(request, BodyHandler.fromLineSubscriber(subscriber))
         *      .thenApply(HttpResponse::statusCode)
         *      .thenAccept((status) -> {
         *          if (status != 200) {
         *              System.err.printf("ERROR: %d status received%n", status);
         *          }
         *      }); }</pre>
         *
         * @param subscriber the subscriber
         * @return a response body handler
         */
        public static BodyHandler<Void>
        fromLineSubscriber(Subscriber<? super String> subscriber) {
            Objects.requireNonNull(subscriber);
            return (status, headers)
                    -> BodySubscriber.fromLineSubscriber(subscriber, s -> null,
                    charsetFrom(headers), null);
        }

        /**
         * Returns a response body handler that returns a {@link BodySubscriber
         * BodySubscriber}{@code <T>} obtained from {@link
         * BodySubscriber#fromLineSubscriber(Subscriber, Function, Charset, String)
         * BodySubscriber.fromLineSubscriber(subscriber, finisher, charset, lineSeparator)},
         * with the given {@code subscriber}, {@code finisher} function, and line separator.
         * The {@link Charset charset} used to decode the response body bytes is
         * obtained from the HTTP response headers as specified by {@link #asString()}.
         *
         * <p> The given {@code finisher} function is applied after the given
         * subscriber's {@code onComplete} has been invoked. The {@code finisher}
         * function is invoked with the given subscriber, and returns a value
         * that is set as the response's body.
         *
         * @apiNote This method can be used as an adapter between a {@code
         * BodySubscriber} and a text based {@code Flow.Subscriber} that parses
         * text line by line.
         *
         * <p> For example:
         * <pre> {@code  // A LineParserSubscriber that implements Flow.Subscriber<String>
         *  // and accumulates lines that match a particular pattern
         *  Pattern pattern = ...;
         *  LineParserSubscriber subscriber = new LineParserSubscriber(pattern);
         *  HttpResponse<List<String>> response = client.sendAsync(request,
         *      BodyHandler.fromLineSubscriber(subscriber, (s) -> s.getMatchingLines(), "\n")).join();
         *  if (response.statusCode() != 200) {
         *      System.err.printf("ERROR: %d status received%n", response.statusCode());
         *  } }</pre>
         *
         *
         * @param <S> the type of the Subscriber
         * @param <T> the type of the response body
         * @param subscriber the subscriber
         * @param finisher a function to be applied after the subscriber has completed
         * @param lineSeparator an optional line separator: can be {@code null},
         *                      in which case lines will be delimited in the manner of
         *                      {@link BufferedReader#readLine()}.
         * @return a response body handler
         * @throws IllegalArgumentException if the supplied {@code lineSeparator} is the empty string.
         */
        public static <S extends Subscriber<? super String>,T> BodyHandler<T>
        fromLineSubscriber(S subscriber, Function<S,T> finisher, String lineSeparator) {
            Objects.requireNonNull(subscriber);
            Objects.requireNonNull(finisher);
            // implicit null check
            if (lineSeparator != null && lineSeparator.isEmpty())
                throw new IllegalArgumentException("empty line separator");
            return (status, headers) ->
                    BodySubscriber.fromLineSubscriber(subscriber, finisher,
                            charsetFrom(headers), lineSeparator);
        }

        /**
         * Returns a response body handler which discards the response body.
         *
         * @return a response body handler
         */
        public static BodyHandler<Void> discard() {
            return (status, headers) -> BodySubscriber.discard();
        }

        /**
         * Returns a response body handler which discards the response body and
         * uses the given value as a replacement for it.
         *
         * @param <U> the response body type
         * @param value the value of U to return as the body, may be {@code null}
         * @return a response body handler
         */
        public static <U> BodyHandler<U> replace(U value) {
            return (status, headers) -> BodySubscriber.replace(value);
        }

        /**
         * Returns a {@code BodyHandler<String>} that returns a
         * {@link BodySubscriber BodySubscriber}{@code <String>} obtained from
         * {@link BodySubscriber#asString(Charset) BodySubscriber.asString(Charset)}.
         * The body is decoded using the given character set.
         *
         * @param charset the character set to convert the body with
         * @return a response body handler
         */
        public static BodyHandler<String> asString(Charset charset) {
            Objects.requireNonNull(charset);
            return (status, headers) -> BodySubscriber.asString(charset);
        }

        /**
         * Returns a {@code BodyHandler<Path>} that returns a
         * {@link BodySubscriber BodySubscriber}{@code <Path>} obtained from
         * {@link BodySubscriber#asFile(Path, OpenOption...)
         * BodySubscriber.asFile(Path,OpenOption...)}.
         *
         * <p> When the {@code HttpResponse} object is returned, the body has
         * been completely written to the file, and {@link #body()} returns a
         * reference to its {@link Path}.
         *
         * @param file the filename to store the body in
         * @param openOptions any options to use when opening/creating the file
         * @return a response body handler
         * @throws SecurityException If a security manager has been installed
         *          and it denies {@link SecurityManager#checkWrite(String)
         *          write access} to the file. The {@link
         *          SecurityManager#checkDelete(String) checkDelete} method is
         *          invoked to check delete access if the file is opened with
         *          the {@code DELETE_ON_CLOSE} option.
         */
        public static BodyHandler<Path> asFile(Path file, OpenOption... openOptions) {
            Objects.requireNonNull(file);
            List<OpenOption> opts = List.of(openOptions);
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                String fn = pathForSecurityCheck(file);
                sm.checkWrite(fn);
                if (opts.contains(StandardOpenOption.DELETE_ON_CLOSE))
                    sm.checkDelete(fn);
                if (opts.contains(StandardOpenOption.READ))
                    sm.checkRead(fn);
            }
            return new PathBodyHandler(file, openOptions);
        }

        /**
         * Returns a {@code BodyHandler<Path>} that returns a
         * {@link BodySubscriber BodySubscriber}{@code <Path>} obtained from
         * {@link BodySubscriber#asFile(Path) BodySubscriber.asFile(Path)}.
         *
         * <p> When the {@code HttpResponse} object is returned, the body has
         * been completely written to the file, and {@link #body()} returns a
         * reference to its {@link Path}.
         *
         * @param file the file to store the body in
         * @return a response body handler
         * @throws SecurityException if a security manager has been installed
         *          and it denies {@link SecurityManager#checkWrite(String)
         *          write access} to the file
         */
        public static BodyHandler<Path> asFile(Path file) {
            return BodyHandler.asFile(file, StandardOpenOption.CREATE,
                                            StandardOpenOption.WRITE);
        }

        /**
         * Returns a {@code BodyHandler<Path>} that returns a
         * {@link BodySubscriber BodySubscriber}&lt;{@link Path}&gt;
         * where the download directory is specified, but the filename is
         * obtained from the {@code Content-Disposition} response header. The
         * {@code Content-Disposition} header must specify the <i>attachment</i>
         * type and must also contain a <i>filename</i> parameter. If the
         * filename specifies multiple path components only the final component
         * is used as the filename (with the given directory name).
         *
         * <p> When the {@code HttpResponse} object is returned, the body has
         * been completely written to the file and {@link #body()} returns a
         * {@code Path} object for the file. The returned {@code Path} is the
         * combination of the supplied directory name and the file name supplied
         * by the server. If the destination directory does not exist or cannot
         * be written to, then the response will fail with an {@link IOException}.
         *
         * @param directory the directory to store the file in
         * @param openOptions open options
         * @return a response body handler
         * @throws SecurityException If a security manager has been installed
         *          and it denies {@link SecurityManager#checkWrite(String)
         *          write access} to the file. The {@link
         *          SecurityManager#checkDelete(String) checkDelete} method is
         *          invoked to check delete access if the file is opened with
         *          the {@code DELETE_ON_CLOSE} option.
         */
         //####: check if the dir exists and is writable??
        public static BodyHandler<Path> asFileDownload(Path directory,
                                                       OpenOption... openOptions) {
            Objects.requireNonNull(directory);
            List<OpenOption> opts = List.of(openOptions);
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                String fn = pathForSecurityCheck(directory);
                sm.checkWrite(fn);
                if (opts.contains(StandardOpenOption.DELETE_ON_CLOSE))
                    sm.checkDelete(fn);
                if (opts.contains(StandardOpenOption.READ))
                    sm.checkRead(fn);
            }
            return new FileDownloadBodyHandler(directory, openOptions);
        }

        /**
         * Returns a {@code BodyHandler<InputStream>} that returns a
         * {@link BodySubscriber BodySubscriber}{@code <InputStream>} obtained
         * from {@link BodySubscriber#asInputStream() BodySubscriber.asInputStream}.
         *
         * <p> When the {@code HttpResponse} object is returned, the response
         * headers will have been completely read, but the body may not have
         * been fully received yet. The {@link #body()} method returns an
         * {@link InputStream} from which the body can be read as it is received.
         *
         * @apiNote See {@link BodySubscriber#asInputStream()} for more information.
         *
         * @return a response body handler
         */
        public static BodyHandler<InputStream> asInputStream() {
            return (status, headers) -> BodySubscriber.asInputStream();
        }

        /**
         * Returns a {@code BodyHandler<Stream<String>>} that returns a
         * {@link BodySubscriber BodySubscriber}{@code <Stream<String>>} obtained from
         * {@link BodySubscriber#asLines(Charset)
         * BodySubscriber.asLines(charset)}.
         * The {@link Charset charset} used to decode the response body bytes is
         * obtained from the HTTP response headers as specified by {@link #asString()},
         * and lines are delimited in the manner of {@link BufferedReader#readLine()}.
         *
         * <p> When the {@code HttpResponse} object is returned, the body may
         * not have been completely received.
         *
         * @return a response body handler
         */
        public static BodyHandler<Stream<String>> asLines() {
            return (status, headers) ->
                    BodySubscriber.asLines(charsetFrom(headers));
        }

        /**
         * Returns a {@code BodyHandler<Void>} that returns a
         * {@link BodySubscriber BodySubscriber}{@code <Void>} obtained from
         * {@link BodySubscriber#asByteArrayConsumer(Consumer)
         * BodySubscriber.asByteArrayConsumer(Consumer)}.
         *
         * <p> When the {@code HttpResponse} object is returned, the body has
         * been completely written to the consumer.
         * @apiNote
         * The subscriber returned by this handler is not flow controlled.
         * Therefore, the supplied consumer must be able to process whatever
         * amount of data is delivered in a timely fashion.
         *
         * @param consumer a Consumer to accept the response body
         * @return a response body handler
         */
        public static BodyHandler<Void> asByteArrayConsumer(Consumer<Optional<byte[]>> consumer) {
            Objects.requireNonNull(consumer);
            return (status, headers) -> BodySubscriber.asByteArrayConsumer(consumer);
        }

        /**
         * Returns a {@code BodyHandler<byte[]>} that returns a
         * {@link BodySubscriber BodySubscriber}&lt;{@code byte[]}&gt; obtained
         * from {@link BodySubscriber#asByteArray() BodySubscriber.asByteArray()}.
         *
         * <p> When the {@code HttpResponse} object is returned, the body has
         * been completely written to the byte array.
         *
         * @return a response body handler
         */
        public static BodyHandler<byte[]> asByteArray() {
            return (status, headers) -> BodySubscriber.asByteArray();
        }

        /**
         * Returns a {@code BodyHandler<String>} that returns a
         * {@link BodySubscriber BodySubscriber}{@code <String>} obtained from
         * {@link BodySubscriber#asString(java.nio.charset.Charset)
         * BodySubscriber.asString(Charset)}. The body is
         * decoded using the character set specified in
         * the {@code Content-type} response header. If there is no such
         * header, or the character set is not supported, then
         * {@link java.nio.charset.StandardCharsets#UTF_8 UTF_8} is used.
         *
         * <p> When the {@code HttpResponse} object is returned, the body has
         * been completely written to the string.
         *
         * @return a response body handler
         */
        public static BodyHandler<String> asString() {
            return (status, headers) -> BodySubscriber.asString(charsetFrom(headers));
        }

        /**
         * Returns a {@code BodyHandler} which, when invoked, returns a {@linkplain
         * BodySubscriber#buffering(BodySubscriber,int) buffering BodySubscriber}
         * that buffers data before delivering it to the downstream subscriber.
         * These {@code BodySubscriber} instances are created by calling
         * {@link BodySubscriber#buffering(BodySubscriber,int)
         * BodySubscriber.buffering} with a subscriber obtained from the given
         * downstream handler and the {@code bufferSize} parameter.
         *
         * @param downstreamHandler the downstream handler
         * @param bufferSize the buffer size parameter passed to {@link
         *        BodySubscriber#buffering(BodySubscriber,int) BodySubscriber.buffering}
         * @return a body handler
         * @throws IllegalArgumentException if {@code bufferSize <= 0}
         */
         public static <T> BodyHandler<T> buffering(BodyHandler<T> downstreamHandler,
                                                    int bufferSize) {
             Objects.requireNonNull(downstreamHandler);
             if (bufferSize <= 0)
                 throw new IllegalArgumentException("must be greater than 0");
             return (status, headers) -> BodySubscriber
                     .buffering(downstreamHandler.apply(status, headers),
                                bufferSize);
         }
    }

    /**
     * A handler for push promises.
     *
     * <p> A <i>push promise</i> is a synthetic request sent by an HTTP/2 server
     * when retrieving an initiating client-sent request. The server has
     * determined, possibly through inspection of the initiating request, that
     * the client will likely need the promised resource, and hence pushes a
     * synthetic push request, in the form of a push promise, to the client. The
     * client can choose to accept or reject the push promise request.
     *
     * <p> A push promise request may be received up to the point where the
     * response body of the initiating client-sent request has been fully
     * received. The delivery of a push promise response, however, is not
     * coordinated with the delivery of the response to the initiating
     * client-sent request.
     *
     * @param <T> the push promise response body type
     */
    public interface PushPromiseHandler<T> {
        /**
         * Notification of an incoming push promise.
         *
         * <p> This method is invoked once for each push promise received, up
         * to the point where the response body of the initiating client-sent
         * request has been fully received.
         *
         * <p> A push promise is accepted by invoking the given {@code acceptor}
         * function. The {@code acceptor} function must be passed a non-null
         * {@code BodyHandler}, that is to be used to handle the promise's
         * response body. The acceptor function will return a {@code
         * CompletableFuture} that completes with the promise's response.
         *
         * <p> If the {@code acceptor} function is not successfully invoked,
         * then the push promise is rejected. The {@code acceptor} function will
         * throw an {@code IllegalStateException} if invoked more than once.
         *
         * @param initiatingRequest the initiating client-send request
         * @param pushPromiseRequest the synthetic push request
         * @param acceptor the acceptor function that must be successfully
         *                 invoked to accept the push promise
         */
        public void applyPushPromise(
            HttpRequest initiatingRequest,
            HttpRequest pushPromiseRequest,
            Function<HttpResponse.BodyHandler<T>,CompletableFuture<HttpResponse<T>>> acceptor
        );


        /**
         * Returns a push promise handler that accumulates push promises, and
         * their responses, into the given map.
         *
         * <p> Entries are added to the given map for each push promise accepted.
         * The entry's key is the push request, and the entry's value is a
         * {@code CompletableFuture} that completes with the response
         * corresponding to the key's push request. A push request is rejected /
         * cancelled if there is already an entry in the map whose key is
         * {@link HttpRequest#equals equal} to it. A push request is
         * rejected / cancelled if it  does not have the same origin as its
         * initiating request.
         *
         * <p> Entries are added to the given map as soon as practically
         * possible when a push promise is received and accepted. That way code,
         * using such a map like a cache, can determine if a push promise has
         * been issued by the server and avoid making, possibly, unnecessary
         * requests.
         *
         * <p> The delivery of a push promise response is not coordinated with
         * the delivery of the response to the initiating client-sent request.
         * However, when the response body for the initiating client-sent
         * request has been fully received, the map is guaranteed to be fully
         * populated, that is, no more entries will be added. The individual
         * {@code CompletableFutures} contained in the map may or may not
         * already be completed at this point.
         *
         * @param <T> the push promise response body type
         * @param pushPromiseHandler t he body handler to use for push promises
         * @param pushPromisesMap a map to accumulate push promises into
         * @return a push promise handler
         */
        public static <T> PushPromiseHandler<T>
        of(Function<HttpRequest,BodyHandler<T>> pushPromiseHandler,
           ConcurrentMap<HttpRequest,CompletableFuture<HttpResponse<T>>> pushPromisesMap) {
            return new PushPromisesHandlerWithMap<>(pushPromiseHandler, pushPromisesMap);
        }
    }

    /**
     * A {@code BodySubscriber} consumes response body bytes and converts them
     * into a higher-level Java type.
     *
     * <p> The object acts as a {@link Flow.Subscriber}&lt;{@link List}&lt;{@link
     * ByteBuffer}&gt;&gt; to the HTTP client implementation, which publishes
     * unmodifiable lists of read-only ByteBuffers containing the response body.
     * The Flow of data, as well as the order of ByteBuffers in the Flow lists,
     * is a strictly ordered representation of the response body. Both the Lists
     * and the ByteBuffers, once passed to the subscriber, are no longer used by
     * the HTTP client. The subscriber converts the incoming buffers of data to
     * some higher-level Java type {@code T}.
     *
     * <p> The {@link #getBody()} method returns a
     * {@link CompletionStage}&lt;{@code T}&gt; that provides the response body
     * object. The {@code CompletionStage} must be obtainable at any time. When
     * it completes depends on the nature of type {@code T}. In many cases,
     * when {@code T} represents the entire body after being consumed then
     * the {@code CompletionStage} completes after the body has been consumed.
     * If  {@code T} is a streaming type, such as {@link java.io.InputStream
     * InputStream}, then it completes before the body has been read, because
     * the calling code uses the {@code InputStream} to consume the data.
     *
     * @apiNote To ensure that all resources associated with the corresponding
     * HTTP exchange are properly released, an implementation of {@code
     * BodySubscriber} should ensure to {@link Flow.Subscription#request
     * request} more data until one of {@link #onComplete() onComplete} or
     * {@link #onError(Throwable) onError} are signalled, or {@link
     * Flow.Subscription#request cancel} its {@linkplain
     * #onSubscribe(Flow.Subscription) subscription} if unable or unwilling to
     * do so. Calling {@code cancel} before exhausting the response body data
     * may cause the underlying HTTP connection to be closed and prevent it
     * from being reused for subsequent operations.
     *
     * @param <T> the response body type
     */
    public interface BodySubscriber<T>
            extends Flow.Subscriber<List<ByteBuffer>> {

        /**
         * Returns a {@code CompletionStage} which when completed will return
         * the response body object. This method can be called at any time
         * relative to the other {@link Flow.Subscriber} methods and is invoked
         * using the client's {@link HttpClient#executor() executor}.
         *
         * @return a CompletionStage for the response body
         */
        public CompletionStage<T> getBody();

        /**
         * Returns a body subscriber that forwards all response body to the
         * given {@code Flow.Subscriber}. The {@linkplain #getBody() completion
         * stage} of the returned body subscriber completes after one of the
         * given subscribers {@code onComplete} or {@code onError} has been
         * invoked.
         *
         * @apiNote This method can be used as an adapter between {@code
         * BodySubscriber} and {@code Flow.Subscriber}.
         *
         * @param <S> the type of the Subscriber
         * @param subscriber the subscriber
         * @return a body subscriber
         */
        public static <S extends Subscriber<? super List<ByteBuffer>>> BodySubscriber<Void>
        fromSubscriber(S subscriber) {
            return new ResponseSubscribers.SubscriberAdapter<S,Void>(subscriber, s -> null);
        }

        /**
         * Returns a body subscriber that forwards all response body to the
         * given {@code Flow.Subscriber}. The {@linkplain #getBody() completion
         * stage} of the returned body subscriber completes after one of the
         * given subscribers {@code onComplete} or {@code onError} has been
         * invoked.
         *
         * <p> The given {@code finisher} function is applied after the given
         * subscriber's {@code onComplete} has been invoked. The {@code finisher}
         * function is invoked with the given subscriber, and returns a value
         * that is set as the response's body.
         *
         * @apiNote This method can be used as an adapter between {@code
         * BodySubscriber} and {@code Flow.Subscriber}.
         *
         * @param <S> the type of the Subscriber
         * @param <T> the type of the response body
         * @param subscriber the subscriber
         * @param finisher a function to be applied after the subscriber has
         *                 completed
         * @return a body subscriber
         */
        public static <S extends Subscriber<? super List<ByteBuffer>>,T> BodySubscriber<T>
        fromSubscriber(S subscriber,
                       Function<S,T> finisher) {
            return new ResponseSubscribers.SubscriberAdapter<S,T>(subscriber, finisher);
        }

        /**
         * Returns a body subscriber that forwards all response body to the
         * given {@code Flow.Subscriber}, line by line.
         * The {@linkplain #getBody() completion
         * stage} of the returned body subscriber completes after one of the
         * given subscribers {@code onComplete} or {@code onError} has been
         * invoked.
         * Bytes are decoded using the {@link StandardCharsets#UTF_8
         * UTF-8} charset, and lines are delimited in the manner of
         * {@link BufferedReader#readLine()}.
         *
         * @apiNote This method can be used as an adapter between {@code
         * BodySubscriber} and {@code Flow.Subscriber}.
         *
         * @implNote This is equivalent to calling <pre>{@code
         *      fromLineSubscriber(subscriber, s -> null, StandardCharsets.UTF_8, null)
         * }</pre>
         *
         * @param <S> the type of the Subscriber
         * @param subscriber the subscriber
         * @return a body subscriber
         */
        public static <S extends Subscriber<? super String>> BodySubscriber<Void>
        fromLineSubscriber(S subscriber) {
            return fromLineSubscriber(subscriber, s -> null,
                    StandardCharsets.UTF_8, null);
        }

        /**
         * Returns a body subscriber that forwards all response body to the
         * given {@code Flow.Subscriber}, line by line. The {@linkplain #getBody()
         * completion stage} of the returned body subscriber completes after
         * one of the given subscribers {@code onComplete} or {@code onError}
         * has been invoked.
         *
         * <p> The given {@code finisher} function is applied after the given
         * subscriber's {@code onComplete} has been invoked. The {@code finisher}
         * function is invoked with the given subscriber, and returns a value
         * that is set as the response's body.
         *
         * @apiNote This method can be used as an adapter between {@code
         * BodySubscriber} and {@code Flow.Subscriber}.
         *
         * @param <S> the type of the Subscriber
         * @param <T> the type of the response body
         * @param subscriber the subscriber
         * @param finisher a function to be applied after the subscriber has
         *                 completed
         * @param charset a {@link Charset} to decode the bytes
         * @param lineSeparator an optional line separator: can be {@code null},
         *                      in which case lines will be delimited in the manner of
         *                      {@link BufferedReader#readLine()}.
         * @return a body subscriber
         * @throws IllegalArgumentException if the supplied {@code lineSeparator} is the empty string.
         */
        public static <S extends Subscriber<? super String>,T> BodySubscriber<T>
        fromLineSubscriber(S subscriber,
                           Function<S,T> finisher,
                           Charset charset,
                           String lineSeparator) {
            return LineSubscriberAdapter.create(subscriber,
                    finisher, charset, lineSeparator);
        }

        /**
         * Returns a body subscriber which stores the response body as a {@code
         * String} converted using the given {@code Charset}.
         *
         * <p> The {@link HttpResponse} using this subscriber is available after
         * the entire response has been read.
         *
         * @param charset the character set to convert the String with
         * @return a body subscriber
         */
        public static BodySubscriber<String> asString(Charset charset) {
            Objects.requireNonNull(charset);
            return new ResponseSubscribers.ByteArraySubscriber<>(
                    bytes -> new String(bytes, charset)
            );
        }

        /**
         * Returns a {@code BodySubscriber} which stores the response body as a
         * byte array.
         *
         * <p> The {@link HttpResponse} using this subscriber is available after
         * the entire response has been read.
         *
         * @return a body subscriber
         */
        public static BodySubscriber<byte[]> asByteArray() {
            return new ResponseSubscribers.ByteArraySubscriber<>(
                    Function.identity() // no conversion
            );
        }

        // no security check
        private static BodySubscriber<Path> asFileImpl(Path file, OpenOption... openOptions) {
            return new ResponseSubscribers.PathSubscriber(file, openOptions);
        }

        /**
         * Returns a {@code BodySubscriber} which stores the response body in a
         * file opened with the given options and name. The file will be opened
         * with the given options using {@link FileChannel#open(Path,OpenOption...)
         * FileChannel.open} just before the body is read. Any exception thrown
         * will be returned or thrown from {@link HttpClient#send(HttpRequest,
         * BodyHandler) HttpClient::send} or {@link HttpClient#sendAsync(HttpRequest,
         * BodyHandler) HttpClient::sendAsync} as appropriate.
         *
         * <p> The {@link HttpResponse} using this subscriber is available after
         * the entire response has been read.
         *
         * @param file the file to store the body in
         * @param openOptions the list of options to open the file with
         * @return a body subscriber
         * @throws SecurityException If a security manager has been installed
         *          and it denies {@link SecurityManager#checkWrite(String)
         *          write access} to the file. The {@link
         *          SecurityManager#checkDelete(String) checkDelete} method is
         *          invoked to check delete access if the file is opened with the
         *          {@code DELETE_ON_CLOSE} option.
         */
        public static BodySubscriber<Path> asFile(Path file, OpenOption... openOptions) {
            Objects.requireNonNull(file);
            List<OpenOption> opts = List.of(openOptions);
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                String fn = pathForSecurityCheck(file);
                sm.checkWrite(fn);
                if (opts.contains(StandardOpenOption.DELETE_ON_CLOSE))
                    sm.checkDelete(fn);
                if (opts.contains(StandardOpenOption.READ))
                    sm.checkRead(fn);
            }
            return asFileImpl(file, openOptions);
        }

        /**
         * Returns a {@code BodySubscriber} which stores the response body in a
         * file opened with the given name. Has the same effect as calling
         * {@link #asFile(Path, OpenOption...) asFile} with the standard open
         * options {@code CREATE} and {@code WRITE}
         *
         * <p> The {@link HttpResponse} using this subscriber is available after
         * the entire response has been read.
         *
         * @param file the file to store the body in
         * @return a body subscriber
         * @throws SecurityException if a security manager has been installed
         *          and it denies {@link SecurityManager#checkWrite(String)
         *          write access} to the file
         */
        public static BodySubscriber<Path> asFile(Path file) {
            return asFile(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        }

        /**
         * Returns a {@code BodySubscriber} which provides the incoming body
         * data to the provided Consumer of {@code Optional<byte[]>}. Each
         * call to {@link Consumer#accept(java.lang.Object) Consumer.accept()}
         * will contain a non empty {@code Optional}, except for the final
         * invocation after all body data has been read, when the {@code
         * Optional} will be empty.
         *
         * <p> The {@link HttpResponse} using this subscriber is available after
         * the entire response has been read.
         *
         * @apiNote
         * This subscriber is not flow controlled.
         * Therefore, the supplied consumer must be able to process whatever
         * amount of data is delivered in a timely fashion.
         *
         * @param consumer a Consumer of byte arrays
         * @return a BodySubscriber
         */
        public static BodySubscriber<Void> asByteArrayConsumer(Consumer<Optional<byte[]>> consumer) {
            return new ResponseSubscribers.ConsumerSubscriber(consumer);
        }

        /**
         * Returns a {@code BodySubscriber} which streams the response body as
         * an {@link InputStream}.
         *
         * <p> The {@link HttpResponse} using this subscriber is available
         * immediately after the response headers have been read, without
         * requiring to wait for the entire body to be processed. The response
         * body can then be read directly from the {@link InputStream}.
         *
         * @apiNote To ensure that all resources associated with the
         * corresponding exchange are properly released the caller must
         * ensure to either read all bytes until EOF is reached, or call
         * {@link InputStream#close} if it is unable or unwilling to do so.
         * Calling {@code close} before exhausting the stream may cause
         * the underlying HTTP connection to be closed and prevent it
         * from being reused for subsequent operations.
         *
         * @return a body subscriber that streams the response body as an
         *         {@link InputStream}.
         */
        public static BodySubscriber<InputStream> asInputStream() {
            return new ResponseSubscribers.HttpResponseInputStream();
        }

        /**
         * Returns a {@code BodySubscriber} which streams the response body as
         * a {@link Stream Stream<String>}, where each string in the stream
         * corresponds to a line as defined by {@link BufferedReader#lines()}.
         *
         * <p> The {@link HttpResponse} using this subscriber is available
         * immediately after the response headers have been read, without
         * requiring to wait for the entire body to be processed. The response
         * body can then be read directly from the {@link Stream}.
         *
         * @apiNote To ensure that all resources associated with the
         * corresponding exchange are properly released the caller must
         * ensure to either read all lines until the stream is exhausted,
         * or call {@link Stream#close} if it is unable or unwilling to do so.
         * Calling {@code close} before exhausting the stream may cause
         * the underlying HTTP connection to be closed and prevent it
         * from being reused for subsequent operations.
         *
         * @param charset the character set to use when converting bytes to characters
         * @return a body subscriber that streams the response body as a
         *         {@link Stream Stream<String>}.
         *
         * @see BufferedReader#lines()
         */
        public static BodySubscriber<Stream<String>> asLines(Charset charset) {
            return ResponseSubscribers.createLineStream(charset);
        }

        /**
         * Returns a response subscriber which discards the response body. The
         * supplied value is the value that will be returned from
         * {@link HttpResponse#body()}.
         *
         * @param <U> The type of the response body
         * @param value the value to return from HttpResponse.body(), may be {@code null}
         * @return a {@code BodySubscriber}
         */
        public static <U> BodySubscriber<U> replace(U value) {
            return new ResponseSubscribers.NullSubscriber<>(Optional.ofNullable(value));
        }

        /**
         * Returns a response subscriber which discards the response body.
         *
         * @return a response body subscriber
         */
        public static BodySubscriber<Void> discard() {
            return new ResponseSubscribers.NullSubscriber<>(Optional.ofNullable(null));
        }

        /**
         * Returns a {@code BodySubscriber} which buffers data before delivering
         * it to the given downstream subscriber. The subscriber guarantees to
         * deliver {@code buffersize} bytes of data to each invocation of the
         * downstream's {@link #onNext(Object) onNext} method, except for
         * the final invocation, just before {@link #onComplete() onComplete}
         * is invoked. The final invocation of {@code onNext} may contain fewer
         * than {@code bufferSize} bytes.
         *
         * <p> The returned subscriber delegates its {@link #getBody()} method
         * to the downstream subscriber.
         *
         * @param downstream the downstream subscriber
         * @param bufferSize the buffer size
         * @return a buffering body subscriber
         * @throws IllegalArgumentException if {@code bufferSize <= 0}
         */
         public static <T> BodySubscriber<T> buffering(BodySubscriber<T> downstream,
                                                       int bufferSize) {
             if (bufferSize <= 0)
                 throw new IllegalArgumentException("must be greater than 0");
             return new BufferingSubscriber<T>(downstream, bufferSize);
         }

        /**
         * Returns a {@code BodySubscriber} whose response body value is that of
         * the result of applying the given function to the body object of the
         * given {@code upstream} {@code BodySubscriber}.
         *
         * <p> The mapping function is executed using the client's {@linkplain
         * HttpClient#executor() executor}, and can therefore be used to map any
         * response body type, including blocking {@link InputStream}, as shown
         * in the following example which uses a well-known JSON parser to
         * convert an {@code InputStream} into any annotated Java type.
         *
         * <p>For example:
         * <pre> {@code  public static <W> BodySubscriber<W> asJSON(Class<W> targetType) {
         *     BodySubscriber<InputStream> upstream = BodySubscriber.asInputStream();
         *
         *     BodySubscriber<W> downstream = mapping(
         *           upstream,
         *           (InputStream is) -> {
         *               try (InputStream stream = is) {
         *                   ObjectMapper objectMapper = new ObjectMapper();
         *                   return objectMapper.readValue(stream, targetType);
         *               } catch (IOException e) {
         *                   throw new UncheckedIOException(e);
         *               }
         *           });
         *    return downstream;
         * } }</pre>
         *
         * @param <T> the upstream both type
         * @param <U> the type of the body subscriber returned
         * @param upstream the body subscriber to be mapped
         * @param mapper the mapping function
         * @return a mapped body subscriber
         */
        public static <T,U> BodySubscriber<U> mapping(BodySubscriber<T> upstream,
                                                      Function<T, U> mapper)
        {
            return new ResponseSubscribers.MappingSubscriber<T, U>(upstream, mapper);
        }
    }
}
