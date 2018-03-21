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

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A WebSocket client.
 *
 * <p> To create a {@code WebSocket} use the {@link HttpClient#newWebSocketBuilder}
 * method. To close a {@code WebSocket} use one of the {@code sendClose} or
 * {@code abort} methods.
 *
 * <p> WebSocket messages are sent through a {@code WebSocket} and received
 * through the {@code WebSocket.Listener}. Messages can be sent until
 * the output is closed, and received until the input is closed.
 * A {@code WebSocket} whose output and input are both closed may be considered
 * itself closed. To check these states use {@link #isOutputClosed()} and
 * {@link #isInputClosed()}.
 *
 * <p> Methods that send messages return {@code CompletableFuture} which
 * completes normally if the message is sent or completes exceptionally if an
 * error occurs.
 *
 * A <i>receive method</i> is any of the {@code onText}, {@code onBinary},
 * {@code onPing}, {@code onPong} and {@code onClose} methods of
 * {@code Listener}. A {@code WebSocket} maintains an internal counter.
 * This counter indicates how many invocations of the associated listener's
 * receive methods have been requested, but not yet made. While this counter is
 * zero the {@code WebSocket} does not invoke any of the receive methods. The
 * counter is incremented by {@code n} when {@code request(n)} is called. The
 * counter is decremented by one when the {@code WebSocket} invokes a receive
 * method. {@code onError} is not a receive method. The {@code WebSocket} may
 * invoke {@code onError} at any given time. If the {@code WebSocket} invokes
 * {@code onError} or {@code onClose}, then no further listener methods will be
 * invoked, no matter the value of the counter. For a newly built
 * {@code WebSocket} the value of the counter is zero.
 *
 * <p> When sending or receiving a message in parts, a whole message is
 * transferred as a sequence of one or more invocations where the last
 * invocation is identified via an additional method argument.
 *
 * <p> Unless otherwise stated, {@code null} arguments will cause methods
 * of {@code WebSocket} to throw {@code NullPointerException}, similarly,
 * {@code WebSocket} will not pass {@code null} arguments to methods of
 * {@code Listener}.
 *
 * <p> The state of a {@code WebSocket} is not changed by the invocations that
 * throw or return a {@code CompletableFuture} that completes with one of the
 * {@code NullPointerException}, {@code IllegalArgumentException},
 * {@code IllegalStateException} exceptions.
 *
 * <p> A {@code WebSocket} invokes methods of its listener in a thread-safe
 * manner.
 *
 * <p> {@code WebSocket} handles Ping and Close messages automatically (as per
 * RFC 6455) by replying with Pong and Close messages respectively. If the
 * listener receives Ping or Close messages, no mandatory actions from the
 * listener are required.
 *
 * @apiNote The relationship between a WebSocket and an instance of Listener
 * associated with it is analogous to that of Subscription and the related
 * Subscriber of type {@link java.util.concurrent.Flow}.
 *
 * @since 11
 */
public interface WebSocket {

    /**
     * The WebSocket Close message status code (<code>{@value}</code>),
     * indicating normal closure, meaning that the purpose for which the
     * connection was established has been fulfilled.
     *
     * @see #sendClose(int, String)
     * @see Listener#onClose(WebSocket, int, String)
     */
    int NORMAL_CLOSURE = 1000;

    /**
     * A builder for creating {@code WebSocket} instances.
     *
     * <p> To obtain a {@code WebSocket} configure a builder as required by
     * calling intermediate methods (the ones that return the builder itself),
     * then call {@code buildAsync()}. If an intermediate method is not called,
     * an appropriate default value (or behavior) will be assumed.
     *
     * <p> Unless otherwise stated, {@code null} arguments will cause methods of
     * {@code Builder} to throw {@code NullPointerException}.
     *
     * @since 11
     */
    interface Builder {

        /**
         * Adds the given name-value pair to the list of additional HTTP headers
         * sent during the opening handshake.
         *
         * <p> Headers defined in
         * <a href="https://tools.ietf.org/html/rfc6455#section-11.3">WebSocket
         * Protocol</a> are illegal. If this method is not invoked, no
         * additional HTTP headers will be sent.
         *
         * @param name
         *         the header name
         * @param value
         *         the header value
         *
         * @return this builder
         */
        Builder header(String name, String value);

        /**
         * Sets a timeout for establishing a WebSocket connection.
         *
         * <p> If the connection is not established within the specified
         * duration then building of the {@code WebSocket} will fail with
         * {@link HttpTimeoutException}. If this method is not invoked then the
         * infinite timeout is assumed.
         *
         * @param timeout
         *         the timeout, non-{@linkplain Duration#isNegative() negative},
         *         non-{@linkplain Duration#ZERO ZERO}
         *
         * @return this builder
         */
        Builder connectTimeout(Duration timeout);

        /**
         * Sets a request for the given subprotocols.
         *
         * <p> After the {@code WebSocket} has been built, the actual
         * subprotocol can be queried via
         * {@link WebSocket#getSubprotocol WebSocket.getSubprotocol()}.
         *
         * <p> Subprotocols are specified in the order of preference. The most
         * preferred subprotocol is specified first. If there are any additional
         * subprotocols they are enumerated from the most preferred to the least
         * preferred.
         *
         * <p> Subprotocols not conforming to the syntax of subprotocol
         * identifiers are illegal. If this method is not invoked then no
         * subprotocols will be requested.
         *
         * @param mostPreferred
         *         the most preferred subprotocol
         * @param lesserPreferred
         *         the lesser preferred subprotocols
         *
         * @return this builder
         */
        Builder subprotocols(String mostPreferred, String... lesserPreferred);

        /**
         * Builds a {@link WebSocket} connected to the given {@code URI} and
         * associated with the given {@code Listener}.
         *
         * <p> Returns a {@code CompletableFuture} which will either complete
         * normally with the resulting {@code WebSocket} or complete
         * exceptionally with one of the following errors:
         * <ul>
         * <li> {@link IOException} -
         *          if an I/O error occurs
         * <li> {@link WebSocketHandshakeException} -
         *          if the opening handshake fails
         * <li> {@link HttpTimeoutException} -
         *          if the opening handshake does not complete within
         *          the timeout
         * <li> {@link InterruptedException} -
         *          if the operation is interrupted
         * <li> {@link SecurityException} -
         *          if a security manager has been installed and it denies
         *          {@link java.net.URLPermission access} to {@code uri}.
         *          <a href="HttpRequest.html#securitychecks">Security checks</a>
         *          contains more information relating to the security context
         *          in which the the listener is invoked.
         * <li> {@link IllegalArgumentException} -
         *          if any of the arguments of this builder's methods are
         *          illegal
         * </ul>
         *
         * @param uri
         *         the WebSocket URI
         * @param listener
         *         the listener
         *
         * @return a {@code CompletableFuture} with the {@code WebSocket}
         */
        CompletableFuture<WebSocket> buildAsync(URI uri, Listener listener);
    }

    /**
     * The receiving interface of {@code WebSocket}.
     *
     * <p> A {@code WebSocket} invokes methods on the associated listener when
     * it receives messages or encounters events. A {@code WebSocket} invokes
     * methods on the listener in a thread-safe manner.
     *
     * <p> Messages received by the {@code Listener} conform to the WebSocket
     * Protocol, otherwise {@code onError} with a {@link IOException} is invoked.
     * Any {@code IOException} raised by {@code WebSocket} will result in an
     * invocation of {@code onError} with that exception. Unless otherwise
     * stated if a listener's method throws an exception or a
     * {@code CompletionStage} returned from a method completes exceptionally,
     * the {@code WebSocket} will invoke {@code onError} with this exception.
     *
     * <p> If a listener's method returns {@code null} rather than a
     * {@code CompletionStage}, {@code WebSocket} will behave as if the listener
     * returned a {@code CompletionStage} that is already completed normally.
     *
     * @apiNote Methods of {@code Listener} have a {@code WebSocket} parameter
     * which holds an invoking {@code WebSocket} at runtime. A careful attention
     * is required if a listener is associated with more than a single
     * {@code WebSocket}. In this case invocations related to different
     * instances of {@code WebSocket} may not be ordered and may even happen
     * concurrently.
     *
     * @since 11
     */
    interface Listener {

        /**
         * A {@code WebSocket} has been connected.
         *
         * <p> This is the first invocation and it is made at most once. This
         * method is typically used to make an initial request for messages.
         *
         * @implSpec The default implementation of this method behaves as if:
         *
         * <pre>{@code
         *     webSocket.request(1);
         * }</pre>
         *
         * @param webSocket
         *         the WebSocket that has been connected
         */
        default void onOpen(WebSocket webSocket) { webSocket.request(1); }

        /**
         * A Text message has been received.
         *
         * <p> Return a {@code CompletionStage} which will be used by the
         * {@code WebSocket} as an indication it may reclaim the
         * {@code CharSequence}. Do not access the {@code CharSequence} after
         * this {@ode CompletionStage} has completed.
         *
         * @implSpec The default implementation of this method behaves as if:
         *
         * <pre>{@code
         *     webSocket.request(1);
         *     return null;
         * }</pre>
         *
         * @implNote This method is always invoked with character sequences
         * which are complete UTF-16 sequences.
         *
         * @param webSocket
         *         the WebSocket on which the message has been received
         * @param message
         *         the message
         * @param last
         *         whether this is the last part of the message
         *
         * @return a {@code CompletionStage} which completes when the
         * {@code CharSequence} may be reclaimed; or {@code null} if it may be
         * reclaimed immediately
         */
        default CompletionStage<?> onText(WebSocket webSocket,
                                          CharSequence message,
                                          boolean last) {
            webSocket.request(1);
            return null;
        }

        /**
         * A Binary message has been received.
         *
         * <p> This message consists of bytes from the buffer's position to
         * its limit.
         *
         * <p> Return a {@code CompletionStage} which will be used by the
         * {@code WebSocket} as an indication it may reclaim the
         * {@code ByteBuffer}. Do not access the {@code ByteBuffer} after
         * this {@ode CompletionStage} has completed.
         *
         * @implSpec The default implementation of this method behaves as if:
         *
         * <pre>{@code
         *     webSocket.request(1);
         *     return null;
         * }</pre>
         *
         * @param webSocket
         *         the WebSocket on which the message has been received
         * @param message
         *         the message
         * @param last
         *         whether this is the last part of the message
         *
         * @return a {@code CompletionStage} which completes when the
         * {@code ByteBuffer} may be reclaimed; or {@code null} if it may be
         * reclaimed immediately
         */
        default CompletionStage<?> onBinary(WebSocket webSocket,
                                            ByteBuffer message,
                                            boolean last) {
            webSocket.request(1);
            return null;
        }

        /**
         * A Ping message has been received.
         *
         * <p> As guaranteed by the WebSocket Protocol, the message consists of
         * not more than {@code 125} bytes. These bytes are located from the
         * buffer's position to its limit.
         *
         * <p> Return a {@code CompletionStage} which will be used by the
         * {@code WebSocket} as a signal it may reclaim the
         * {@code ByteBuffer}. Do not access the {@code ByteBuffer} after
         * this {@ode CompletionStage} has completed.
         *
         * @implSpec The default implementation of this method behaves as if:
         *
         * <pre>{@code
         *     webSocket.request(1);
         *     return null;
         * }</pre>
         *
         * @param webSocket
         *         the WebSocket on which the message has been received
         * @param message
         *         the message
         *
         * @return a {@code CompletionStage} which completes when the
         * {@code ByteBuffer} may be reclaimed; or {@code null} if it may be
         * reclaimed immediately
         */
        default CompletionStage<?> onPing(WebSocket webSocket,
                                          ByteBuffer message) {
            webSocket.request(1);
            return null;
        }

        /**
         * A Pong message has been received.
         *
         * <p> As guaranteed by the WebSocket Protocol, the message consists of
         * not more than {@code 125} bytes. These bytes are located from the
         * buffer's position to its limit.
         *
         * <p> Return a {@code CompletionStage} which will be used by the
         * {@code WebSocket} as a signal it may reclaim the
         * {@code ByteBuffer}. Do not access the {@code ByteBuffer} after
         * this {@ode CompletionStage} has completed.
         *
         * @implSpec The default implementation of this method behaves as if:
         *
         * <pre>{@code
         *     webSocket.request(1);
         *     return null;
         * }</pre>
         *
         * @param webSocket
         *         the WebSocket on which the message has been received
         * @param message
         *         the message
         *
         * @return a {@code CompletionStage} which completes when the
         * {@code ByteBuffer} may be reclaimed; or {@code null} if it may be
         * reclaimed immediately
         */
        default CompletionStage<?> onPong(WebSocket webSocket,
                                          ByteBuffer message) {
            webSocket.request(1);
            return null;
        }

        /**
         * Receives a Close message indicating the {@code WebSocket}'s input has
         * been closed.
         *
         * <p> This is the last invocation from the {@code WebSocket}. By the
         * time this invocation begins the {@code WebSocket}'s input will have
         * been closed. Be prepared to receive this invocation at any time after
         * {@code onOpen} regardless of whether or not any messages have been
         * requested from the {@code WebSocket}.
         *
         * <p> A Close message consists of a status code and a reason for
         * closing. The status code is an integer from the range
         * {@code 1000 <= code <= 65535}. The {@code reason} is a string which
         * has an UTF-8 representation not longer than {@code 123} bytes.
         *
         * <p> If the {@code WebSocket}'s output is not already closed, the
         * {@code CompletionStage} returned by this method will be used as an
         * indication that the {@code WebSocket}'s output may be closed. The
         * {@code WebSocket} will close its output at the earliest of completion
         * of the returned {@code CompletionStage} or invoking either of the
         * {@code sendClose} or {@code abort} methods.
         *
         * @apiNote Returning a {@code CompletionStage} that never completes,
         * effectively disables the reciprocating closure of the output.
         *
         * <p> To specify a custom closure code and/or reason code the sendClose
         * may be invoked from inside onClose call:
         * <pre>{@code
         *  public CompletionStage<?> onClose(WebSocket webSocket,
         *                             int statusCode,
         *                             String reason) {
         *      webSocket.sendClose(CUSTOM_STATUS_CODE, CUSTOM_REASON);
         *      return new CompletableFuture<Void>();
         *  }
         * }</pre>
         *
         * @implSpec The default implementation of this method returns
         * {@code null}, indicating that the output should be closed
         * immediately.
         *
         * @param webSocket
         *         the WebSocket on which the message has been received
         * @param statusCode
         *         the status code
         * @param reason
         *         the reason
         *
         * @return a {@code CompletionStage} which completes when the
         * {@code WebSocket} may be closed; or {@code null} if it may be
         * closed immediately
         */
        default CompletionStage<?> onClose(WebSocket webSocket,
                                           int statusCode,
                                           String reason) {
            return null;
        }

        /**
         * An error has occurred.
         *
         * <p> This is the last invocation from the {@code WebSocket}. By the
         * time this invocation begins both {@code WebSocket}'s input and output
         * will have been closed. Be prepared to receive this invocation at any
         * time after {@code onOpen} regardless of whether or not any messages
         * have been requested from the {@code WebSocket}.
         *
         * <p> If an exception is thrown from this method, resulting behavior is
         * undefined.
         *
         * @param webSocket
         *         the WebSocket on which the error has occurred
         * @param error
         *         the error
         */
        default void onError(WebSocket webSocket, Throwable error) { }
    }

    /**
     * Sends a Text message with characters from the given {@code CharSequence}.
     *
     * <p> The character sequence must not be modified until the
     * {@code CompletableFuture} returned from this method has completed.
     *
     * <p> A {@code CompletableFuture} returned from this method can
     * complete exceptionally with:
     * <ul>
     * <li> {@link IllegalStateException} -
     *          if the previous Text or Binary message has not been sent yet
     *          or if a previous Binary message has been sent with
     *              {@code last} equals {@code false}
     * <li> {@link IOException} -
     *          if an I/O error occurs, or if the output is closed
     * </ul>
     *
     * @implNote If a partial or malformed UTF-16 sequence is passed to this
     * method, a {@code CompletableFuture} returned will complete exceptionally
     * with {@code IOException}.
     *
     * @param message
     *         the message
     * @param last
     *         {@code true} if this is the last part of the message,
     *         {@code false} otherwise
     *
     * @return a {@code CompletableFuture} that completes, with this
     * {@code WebSocket}, when the message has been sent
     */
    CompletableFuture<WebSocket> sendText(CharSequence message, boolean last);

    /**
     * Sends a Binary message with bytes from the given {@code ByteBuffer}.
     *
     * <p> The message consists of bytes from the buffer's position to its
     * limit. Upon normal completion of a {@code CompletableFuture} returned
     * from this method the buffer will have no remaining bytes. The buffer must
     * not be accessed until after that.
     *
     * <p> The {@code CompletableFuture} returned from this method can
     * complete exceptionally with:
     * <ul>
     * <li> {@link IllegalStateException} -
     *          if the previous Binary or Text message has not been sent yet
     *          or if a previous Text message has been sent with
     *              {@code last} equals {@code false}
     * <li> {@link IOException} -
     *          if an I/O error occurs, or if the output is closed
     * </ul>
     *
     * @param message
     *         the message
     * @param last
     *         {@code true} if this is the last part of the message,
     *         {@code false} otherwise
     *
     * @return a {@code CompletableFuture} that completes, with this
     * {@code WebSocket}, when the message has been sent
     */
    CompletableFuture<WebSocket> sendBinary(ByteBuffer message, boolean last);

    /**
     * Sends a Ping message with bytes from the given {@code ByteBuffer}.
     *
     * <p> The message consists of not more than {@code 125} bytes from the
     * buffer's position to its limit. Upon normal completion of a
     * {@code CompletableFuture} returned from this method the buffer will
     * have no remaining bytes. The buffer must not be accessed until after that.
     *
     * <p> The {@code CompletableFuture} returned from this method can
     * complete exceptionally with:
     * <ul>
     * <li> {@link IllegalStateException} -
     *          if the previous Ping or Pong message has not been sent yet
     * <li> {@link IllegalArgumentException} -
     *          if the message is too long
     * <li> {@link IOException} -
     *          if an I/O error occurs, or if the output is closed
     * </ul>
     *
     * @param message
     *         the message
     *
     * @return a {@code CompletableFuture} that completes, with this
     * {@code WebSocket}, when the Ping message has been sent
     */
    CompletableFuture<WebSocket> sendPing(ByteBuffer message);

    /**
     * Sends a Pong message with bytes from the given {@code ByteBuffer}.
     *
     * <p> The message consists of not more than {@code 125} bytes from the
     * buffer's position to its limit. Upon normal completion of a
     * {@code CompletableFuture} returned from this method the buffer will have
     * no remaining bytes. The buffer must not be accessed until after that.
     *
     * <p> The {@code CompletableFuture} returned from this method can
     * complete exceptionally with:
     * <ul>
     * <li> {@link IllegalStateException} -
     *          if the previous Ping or Pong message has not been sent yet
     * <li> {@link IllegalArgumentException} -
     *          if the message is too long
     * <li> {@link IOException} -
     *          if an I/O error occurs, or if the output is closed
     * </ul>
     *
     * @param message
     *         the message
     *
     * @return a {@code CompletableFuture} that completes, with this
     * {@code WebSocket}, when the Pong message has been sent
     */
    CompletableFuture<WebSocket> sendPong(ByteBuffer message);

    /**
     * Initiates an orderly closure of this {@code WebSocket}'s output by
     * sending a Close message with the given status code and the reason.
     *
     * <p> The {@code statusCode} is an integer from the range
     * {@code 1000 <= code <= 4999}. Status codes {@code 1002}, {@code 1003},
     * {@code 1006}, {@code 1007}, {@code 1009}, {@code 1010}, {@code 1012},
     * {@code 1013} and {@code 1015} are illegal. Behaviour in respect to other
     * status codes is implementation-specific. The {@code reason} is a string
     * that has an UTF-8 representation not longer than {@code 123} bytes.
     *
     * <p> A {@code CompletableFuture} returned from this method can
     * complete exceptionally with:
     * <ul>
     * <li> {@link IllegalArgumentException} -
     *          if {@code statusCode} is illegal
     * <li> {@link IOException} -
     *          if an I/O error occurs, or if the output is closed
     * </ul>
     *
     * <p> Unless the {@code CompletableFuture} returned from this method
     * completes with {@code IllegalArgumentException}, or the method throws
     * {@code NullPointerException}, the output will be closed.
     *
     * <p> If not already closed, the input remains open until it is
     * {@linkplain Listener#onClose(WebSocket, int, String) closed} by the server,
     * or {@code abort} is invoked, or an
     * {@linkplain Listener#onError(WebSocket, Throwable) error} occurs.
     *
     * @apiNote Use the provided integer constant {@link #NORMAL_CLOSURE} as a
     * status code and an empty string as a reason in a typical case
     * <pre>{@code
     *     CompletableFuture<WebSocket> webSocket = ...
     *     webSocket.thenCompose(ws -> ws.sendText("Hello, ", false))
     *              .thenCompose(ws -> ws.sendText("world!", true))
     *              .thenCompose(ws -> ws.sendClose(WebSocket.NORMAL_CLOSURE, ""))
     *              .join();
     * }</pre>
     *
     * @param statusCode
     *         the status code
     * @param reason
     *         the reason
     *
     * @return a {@code CompletableFuture} that completes, with this
     * {@code WebSocket}, when the Close message has been sent
     */
    CompletableFuture<WebSocket> sendClose(int statusCode, String reason);

    /**
     * Increments the counter of invocations requested from this
     * {@code WebSocket} to the associated listener by the given number.
     *
     * <p> This WebSocket will invoke {@code onText}, {@code onBinary},
     * {@code onPing}, {@code onPong} or {@code onClose} methods on the
     * associated listener up to {@code n} more times.
     *
     * @apiNote The parameter of this method is the number of invocations being
     * requested from this {@code WebSocket} to the associated {@code Listener},
     * not the number of messages. Sometimes a message may be delivered in a
     * single invocation, but not always. For example, Ping, Pong and Close
     * messages are delivered in a single invocation of {@code onPing},
     * {@code onPong} and {@code onClose} respectively. However, whether or not
     * Text and Binary messages are delivered in a single invocation of
     * {@code onText} and {@code onBinary} depends on the boolean argument
     * ({@code last}) of these methods. If {@code last} is {@code false}, then
     * there is more to a message than has been delivered to the invocation.
     *
     * <p> Here is an example of a listener that requests invocations, one at a
     * time, until a complete message has been accumulated, then processes the
     * result:
     * <pre>WebSocket.Listener listener = new WebSocket.Listener() {
     *
     *    StringBuilder text = new StringBuilder();
     *
     *    &#64;Override
     *    public CompletionStage&lt;?&gt; onText(WebSocket webSocket,
     *                                           CharSequence message,
     *                                           boolean last) {
     *        text.append(message);
     *        if (last) {
     *            processCompleteTextMessage(text);
     *            text = new StringBuilder();
     *        }
     *        webSocket.request(1);
     *        return null;
     *    }
     *    ...
     * }</pre>
     *
     * @param n
     *         the number of invocations
     *
     * @throws IllegalArgumentException
     *         if {@code n <= 0}
     */
    void request(long n);

    /**
     * Returns the subprotocol for this {@code WebSocket}.
     *
     * @return the subprotocol for this {@code WebSocket}, or an empty
     * {@code String} if there's no subprotocol
     */
    String getSubprotocol();

    /**
     * Tells whether this {@code WebSocket}'s output is closed.
     *
     * <p> If this method returns {@code true}, subsequent invocations will also
     * return {@code true}.
     *
     * @return {@code true} if closed, {@code false} otherwise
     */
    boolean isOutputClosed();

    /**
     * Tells whether this {@code WebSocket}'s input is closed.
     *
     * <p> If this method returns {@code true}, subsequent invocations will also
     * return {@code true}.
     *
     * @return {@code true} if closed, {@code false} otherwise
     */
    boolean isInputClosed();

    /**
     * Closes this {@code WebSocket}'s input and output abruptly.
     *
     * <p> When this method returns both the input and the output will have been
     * closed. Any pending send operations will fail with {@code IOException}.
     * Subsequent invocations of {@code abort()} will have no effect.
     */
    void abort();
}
