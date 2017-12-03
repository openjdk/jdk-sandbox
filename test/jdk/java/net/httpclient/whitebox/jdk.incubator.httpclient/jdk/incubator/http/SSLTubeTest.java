/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http;

import jdk.incubator.http.internal.common.Demand;
import jdk.incubator.http.internal.common.FlowTube;
import jdk.incubator.http.internal.common.SSLFlowDelegate;
import jdk.incubator.http.internal.common.SSLTube;
import jdk.incubator.http.internal.common.SequentialScheduler;
import jdk.incubator.http.internal.common.Utils;
import org.testng.annotations.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Test
public class SSLTubeTest {

    private static final long COUNTER = 600;
    private static final int LONGS_PER_BUF = 800;
    private static final long TOTAL_LONGS = COUNTER * LONGS_PER_BUF;
    public static final ByteBuffer SENTINEL = ByteBuffer.allocate(0);

    static final Random rand = new Random();

    static int randomRange(int lower, int upper) {
        if (lower > upper)
            throw new IllegalArgumentException("lower > upper");
        int diff = upper - lower;
        int r = lower + rand.nextInt(diff);
        return r - (r % 8); // round down to multiple of 8 (align for longs)
    }

    private static ByteBuffer getBuffer(long startingAt) {
        ByteBuffer buf = ByteBuffer.allocate(LONGS_PER_BUF * 8);
        for (int j = 0; j < LONGS_PER_BUF; j++) {
            buf.putLong(startingAt++);
        }
        buf.flip();
        return buf;
    }

    @Test
    public void runWithSSLLoopackServer() throws IOException {
        ExecutorService sslExecutor = Executors.newCachedThreadPool();

        /* Start of wiring */
        /* Emulates an echo server */
        SSLLoopbackSubscriber server =
                new SSLLoopbackSubscriber((new SimpleSSLContext()).get(), sslExecutor);
        server.start();

        run(server, sslExecutor);
    }

    @Test
    public void runWithEchoServer() throws IOException {
        ExecutorService sslExecutor = Executors.newCachedThreadPool();

        /* Start of wiring */
        /* Emulates an echo server */
        FlowTube server = crossOverEchoServer(sslExecutor);

        run(server, sslExecutor);
    }

    private void run(FlowTube server, ExecutorService sslExecutor) throws IOException {
        FlowTube client = new SSLTube(createSSLEngine(true),
                                      sslExecutor,
                                      server);
        SubmissionPublisher<List<ByteBuffer>> p =
                new SubmissionPublisher<>(ForkJoinPool.commonPool(),
                                          Integer.MAX_VALUE);
        FlowTube.TubePublisher begin = p::subscribe;
        CompletableFuture<Void> completion = new CompletableFuture<>();
        EndSubscriber end = new EndSubscriber(TOTAL_LONGS, completion);
        client.connectFlows(begin, end);
        /* End of wiring */

        long count = 0;
        System.out.printf("Submitting %d buffer arrays\n", COUNTER);
        System.out.printf("LoopCount should be %d\n", TOTAL_LONGS);
        for (long i = 0; i < COUNTER; i++) {
            ByteBuffer b = getBuffer(count);
            count += LONGS_PER_BUF;
            p.submit(List.of(b));
        }
        System.out.println("Finished submission. Waiting for loopback");
        p.close();
        try {
            completion.join();
            System.out.println("OK");
        } finally {
            sslExecutor.shutdownNow();
        }
    }

    /**
     * This is a copy of the SSLLoopbackSubscriber used in FlowTest
     */
    static class SSLLoopbackSubscriber implements FlowTube {
        private final BlockingQueue<ByteBuffer> buffer;
        private final Socket clientSock;
        private final SSLSocket serverSock;
        private final Thread thread1, thread2, thread3;
        private volatile Flow.Subscription clientSubscription;
        private final SubmissionPublisher<List<ByteBuffer>> publisher;

        SSLLoopbackSubscriber(SSLContext ctx, ExecutorService exec) throws IOException {
            SSLServerSocketFactory fac = ctx.getServerSocketFactory();
            SSLServerSocket serv = (SSLServerSocket) fac.createServerSocket(0);
            SSLParameters params = serv.getSSLParameters();
            params.setApplicationProtocols(new String[]{"proto2"});
            serv.setSSLParameters(params);


            int serverPort = serv.getLocalPort();
            clientSock = new Socket("127.0.0.1", serverPort);
            serverSock = (SSLSocket) serv.accept();
            this.buffer = new LinkedBlockingQueue<>();
            thread1 = new Thread(this::clientWriter, "clientWriter");
            thread2 = new Thread(this::serverLoopback, "serverLoopback");
            thread3 = new Thread(this::clientReader, "clientReader");
            publisher = new SubmissionPublisher<>(exec, Flow.defaultBufferSize(),
                    this::handlePublisherException);
            SSLFlowDelegate.Monitor.add(this::monitor);
        }

        public void start() {
            thread1.start();
            thread2.start();
            thread3.start();
        }

        private void handlePublisherException(Object o, Throwable t) {
            System.out.println("Loopback Publisher exception");
            t.printStackTrace(System.out);
        }

        private final AtomicInteger readCount = new AtomicInteger();

        // reads off the SSLSocket the data from the "server"
        private void clientReader() {
            try {
                InputStream is = clientSock.getInputStream();
                final int bufsize = randomRange(512, 16 * 1024);
                System.out.println("clientReader: bufsize = " + bufsize);
                while (true) {
                    byte[] buf = new byte[bufsize];
                    int n = is.read(buf);
                    if (n == -1) {
                        System.out.println("clientReader close: read "
                                + readCount.get() + " bytes");
                        publisher.close();
                        sleep(2000);
                        Utils.close(is, clientSock);
                        return;
                    }
                    ByteBuffer bb = ByteBuffer.wrap(buf, 0, n);
                    readCount.addAndGet(n);
                    publisher.submit(List.of(bb));
                }
            } catch (Throwable e) {
                e.printStackTrace();
                Utils.close(clientSock);
            }
        }

        // writes the encrypted data from SSLFLowDelegate to the j.n.Socket
        // which is connected to the SSLSocket emulating a server.
        private void clientWriter() {
            long nbytes = 0;
            try {
                OutputStream os =
                        new BufferedOutputStream(clientSock.getOutputStream());

                while (true) {
                    ByteBuffer buf = buffer.take();
                    if (buf == SENTINEL) {
                        // finished
                        //Utils.sleep(2000);
                        System.out.println("clientWriter close: " + nbytes + " written");
                        clientSock.shutdownOutput();
                        System.out.println("clientWriter close return");
                        return;
                    }
                    int len = buf.remaining();
                    int written = writeToStream(os, buf);
                    assert len == written;
                    nbytes += len;
                    assert !buf.hasRemaining()
                            : "buffer has " + buf.remaining() + " bytes left";
                    clientSubscription.request(1);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        private int writeToStream(OutputStream os, ByteBuffer buf) throws IOException {
            byte[] b = buf.array();
            int offset = buf.arrayOffset() + buf.position();
            int n = buf.limit() - buf.position();
            os.write(b, offset, n);
            buf.position(buf.limit());
            os.flush();
            return n;
        }

        private final AtomicInteger loopCount = new AtomicInteger();

        public String monitor() {
            return "serverLoopback: loopcount = " + loopCount.toString()
                    + " clientRead: count = " + readCount.toString();
        }

        // thread2
        private void serverLoopback() {
            try {
                InputStream is = serverSock.getInputStream();
                OutputStream os = serverSock.getOutputStream();
                final int bufsize = randomRange(512, 16 * 1024);
                System.out.println("serverLoopback: bufsize = " + bufsize);
                byte[] bb = new byte[bufsize];
                while (true) {
                    int n = is.read(bb);
                    if (n == -1) {
                        sleep(2000);
                        is.close();
                        os.close();
                        serverSock.close();
                        return;
                    }
                    os.write(bb, 0, n);
                    os.flush();
                    loopCount.addAndGet(n);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }


        /**
         * This needs to be called before the chain is subscribed. It can't be
         * supplied in the constructor.
         */
        public void setReturnSubscriber(Flow.Subscriber<List<ByteBuffer>> returnSubscriber) {
            publisher.subscribe(returnSubscriber);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            clientSubscription = subscription;
            clientSubscription.request(5);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            try {
                for (ByteBuffer b : item)
                    buffer.put(b);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Utils.close(clientSock);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            throwable.printStackTrace();
            Utils.close(clientSock);
        }

        @Override
        public void onComplete() {
            try {
                buffer.put(SENTINEL);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Utils.close(clientSock);
            }
        }

        @Override
        public boolean isFinished() {
            return false;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super List<ByteBuffer>> subscriber) {
            publisher.subscribe(subscriber);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {

        }
    }

    /**
     * Creates a cross-over FlowTube than can be plugged into a client-side
     * SSLTube (in place of the SSLLoopbackSubscriber).
     * Note that the only method that can be called on the return tube
     * is connectFlows(). Calling any other method will trigger an
     * InternalError.
     * @param sslExecutor an executor
     * @return a cross-over FlowTube connected to an EchoTube.
     * @throws IOException
     */
    FlowTube crossOverEchoServer(Executor sslExecutor) throws IOException {
        LateBindingTube crossOver = new LateBindingTube();
        FlowTube server = new SSLTube(createSSLEngine(false),
                                      sslExecutor,
                                      crossOver);
        EchoTube echo = new EchoTube(6);
        server.connectFlows(FlowTube.asTubePublisher(echo), FlowTube.asTubeSubscriber(echo));

        return new CrossOverTube(crossOver);
    }

    /**
     * A cross-over FlowTube that makes it possible to reverse the direction
     * of flows. The typical usage is to connect an two opposite SSLTube,
     * one encrypting, one decrypting, to e.g. an EchoTube, with the help
     * of a LateBindingTube:
     * {@code
     * client app => SSLTube => CrossOverTube <= LateBindingTube <= SSLTube <= EchoTube
     * }
     * <p>
     * Note that the only method that can be called on the CrossOverTube is
     * connectFlows(). Calling any other method will cause an InternalError to
     * be thrown.
     * Also connectFlows() can be called only once.
     */
    private static final class CrossOverTube implements FlowTube {
        final LateBindingTube tube;
        CrossOverTube(LateBindingTube tube) {
            this.tube = tube;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super List<ByteBuffer>> subscriber) {
            throw newInternalError();
        }

        @Override
        public void connectFlows(TubePublisher writePublisher, TubeSubscriber readSubscriber) {
            tube.start(writePublisher, readSubscriber);
        }

        @Override
        public boolean isFinished() {
            return tube.isFinished();
        }

        Error newInternalError() {
            InternalError error = new InternalError();
            error.printStackTrace(System.out);
            return error;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            throw newInternalError();
        }

        @Override
        public void onError(Throwable throwable) {
            throw newInternalError();
        }

        @Override
        public void onComplete() {
            throw newInternalError();
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            throw newInternalError();
        }
    }

    /**
     * A late binding tube that makes it possible to create an
     * SSLTube before the right-hand-side tube has been created.
     * The typical usage is to make it possible to connect two
     * opposite SSLTube (one encrypting, one decrypting) through a
     * CrossOverTube:
     * {@code
     * client app => SSLTube => CrossOverTube <= LateBindingTube <= SSLTube <= EchoTube
     * }
     * <p>
     * Note that this class only supports a single call to start(): it cannot be
     * subscribed more than once from its left-hand-side (the cross over tube side).
     */
    private static class LateBindingTube implements FlowTube {

        final CompletableFuture<Flow.Publisher<List<ByteBuffer>>> futurePublisher
                = new CompletableFuture<>();
        final ConcurrentLinkedQueue<Consumer<Flow.Subscriber<? super List<ByteBuffer>>>> queue
                = new ConcurrentLinkedQueue<>();
        AtomicReference<Flow.Subscriber<? super List<ByteBuffer>>> subscriberRef = new AtomicReference<>();
        SequentialScheduler scheduler = SequentialScheduler.synchronizedScheduler(this::loop);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        private volatile boolean finished;
        private volatile boolean completed;


        public void start(Flow.Publisher<List<ByteBuffer>> publisher,
                          Flow.Subscriber<? super List<ByteBuffer>> subscriber) {
            subscriberRef.set(subscriber);
            futurePublisher.complete(publisher);
            scheduler.runOrSchedule();
        }

        @Override
        public boolean isFinished() {
            return finished;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super List<ByteBuffer>> subscriber) {
            futurePublisher.thenAccept((p) -> p.subscribe(subscriber));
            scheduler.runOrSchedule();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            queue.add((s) -> s.onSubscribe(subscription));
            scheduler.runOrSchedule();
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            queue.add((s) -> s.onNext(item));
            scheduler.runOrSchedule();
        }

        @Override
        public void onError(Throwable throwable) {
            System.out.println("LateBindingTube onError");
            throwable.printStackTrace(System.out);
            queue.add((s) -> {
                errorRef.compareAndSet(null, throwable);
                try {
                    System.out.println("LateBindingTube subscriber onError: " + throwable);
                    s.onError(errorRef.get());
                } finally {
                    finished = true;
                    System.out.println("LateBindingTube finished");
                }
            });
            scheduler.runOrSchedule();
        }

        @Override
        public void onComplete() {
            System.out.println("LateBindingTube completing");
            queue.add((s) -> {
                completed = true;
                try {
                    System.out.println("LateBindingTube complete subscriber");
                    s.onComplete();
                } finally {
                    finished = true;
                    System.out.println("LateBindingTube finished");
                }
            });
            scheduler.runOrSchedule();
        }

        private void loop() {
            if (finished) {
                scheduler.stop();
                return;
            }
            Flow.Subscriber<? super List<ByteBuffer>> subscriber = subscriberRef.get();
            if (subscriber == null) return;
            try {
                Consumer<Flow.Subscriber<? super List<ByteBuffer>>> s;
                while ((s = queue.poll()) != null) {
                    s.accept(subscriber);
                }
            } catch (Throwable t) {
                if (errorRef.compareAndSet(null, t)) {
                    onError(t);
                }
            }
        }
    }

    /**
     * An echo tube that just echoes back whatever bytes it receives.
     * This cannot be plugged to the right-hand-side of an SSLTube
     * since handshake data cannot be simply echoed back, and
     * application data most likely also need to be decrypted and
     * re-encrypted.
     */
    private static final class EchoTube implements FlowTube {

        private final static Object EOF = new Object();
        private final Executor executor = Executors.newSingleThreadExecutor();

        private final Queue<Object> queue = new ConcurrentLinkedQueue<>();
        private final int maxQueueSize;
        private final SequentialScheduler processingScheduler =
                new SequentialScheduler(createProcessingTask());

        /* Writing into this tube */
        private volatile long requested;
        private Flow.Subscription subscription;

        /* Reading from this tube */
        private final Demand demand = new Demand();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private Flow.Subscriber<? super List<ByteBuffer>> subscriber;

        private EchoTube(int maxBufferSize) {
            if (maxBufferSize < 1)
                throw new IllegalArgumentException();
            this.maxQueueSize = maxBufferSize;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super List<ByteBuffer>> subscriber) {
            this.subscriber = subscriber;
            System.out.println("EchoTube got subscriber: " + subscriber);
            this.subscriber.onSubscribe(new InternalSubscription());
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            System.out.println("EchoTube request: " + maxQueueSize);
            (this.subscription = subscription).request(requested = maxQueueSize);
        }

        private void requestMore() {
            Flow.Subscription s = subscription;
            if (s == null || cancelled.get()) return;
            long unfulfilled = queue.size() + --requested;
            if (unfulfilled <= maxQueueSize/2) {
                long req = maxQueueSize - unfulfilled;
                requested += req;
                s.request(req);
                System.out.printf("EchoTube request: %s [requested:%s, queue:%s, unfulfilled:%s]%n",
                        req, requested-req, queue.size(), unfulfilled );
            }
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            System.out.printf("EchoTube add %s [requested:%s, queue:%s]%n",
                    Utils.remaining(item), requested, queue.size());
            queue.add(item);
            processingScheduler.deferOrSchedule(executor);
        }

        @Override
        public void onError(Throwable throwable) {
            System.out.println("EchoTube add " + throwable);
            queue.add(throwable);
            processingScheduler.deferOrSchedule(executor);
        }

        @Override
        public void onComplete() {
            System.out.println("EchoTube add EOF");
            queue.add(EOF);
            processingScheduler.deferOrSchedule(executor);
        }

        @Override
        public boolean isFinished() {
            return cancelled.get();
        }

        private class InternalSubscription implements Flow.Subscription {

            @Override
            public void request(long n) {
                System.out.println("EchoTube got request: " + n);
                if (n <= 0) {
                    throw new InternalError();
                }
                if (demand.increase(n)) {
                    processingScheduler.deferOrSchedule(executor);
                }
            }

            @Override
            public void cancel() {
                cancelled.set(true);
            }
        }

        @Override
        public String toString() {
            return "EchoTube";
        }

        int transmitted = 0;
        private SequentialScheduler.RestartableTask createProcessingTask() {
            return new SequentialScheduler.CompleteRestartableTask() {

                @Override
                protected void run() {
                    try {
                        while (!cancelled.get()) {
                            Object item = queue.peek();
                            if (item == null) {
                                System.out.printf("EchoTube: queue empty, requested=%s, demand=%s, transmitted=%s%n",
                                        requested, demand.get(), transmitted);
                                requestMore();
                                return;
                            }
                            try {
                                System.out.printf("EchoTube processing item, requested=%s, demand=%s, transmitted=%s%n",
                                        requested, demand.get(), transmitted);
                                if (item instanceof List) {
                                    if (!demand.tryDecrement()) {
                                        System.out.println("EchoTube no demand");
                                        return;
                                    }
                                    @SuppressWarnings("unchecked")
                                    List<ByteBuffer> bytes = (List<ByteBuffer>) item;
                                    Object removed = queue.remove();
                                    assert removed == item;
                                    System.out.println("EchoTube processing "
                                            + Utils.remaining(bytes));
                                    transmitted++;
                                    subscriber.onNext(bytes);
                                    requestMore();
                                } else if (item instanceof Throwable) {
                                    cancelled.set(true);
                                    Object removed = queue.remove();
                                    assert removed == item;
                                    System.out.println("EchoTube processing " + item);
                                    subscriber.onError((Throwable) item);
                                } else if (item == EOF) {
                                    cancelled.set(true);
                                    Object removed = queue.remove();
                                    assert removed == item;
                                    System.out.println("EchoTube processing EOF");
                                    subscriber.onComplete();
                                } else {
                                    throw new InternalError(String.valueOf(item));
                                }
                            } finally {
                            }
                        }
                    } catch(Throwable t) {
                        t.printStackTrace();
                        throw t;
                    }
                }
            };
        }
    }

    /**
     * The final subscriber which receives the decrypted looped-back data. Just
     * needs to compare the data with what was sent. The given CF is either
     * completed exceptionally with an error or normally on success.
     */
    private static class EndSubscriber implements FlowTube.TubeSubscriber {

        private static final int REQUEST_WINDOW = 13;

        private final long nbytes;
        private final AtomicLong counter = new AtomicLong();
        private final CompletableFuture<?> completion;
        private volatile Flow.Subscription subscription;
        private long unfulfilled;

        EndSubscriber(long nbytes, CompletableFuture<?> completion) {
            this.nbytes = nbytes;
            this.completion = completion;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            unfulfilled = REQUEST_WINDOW;
            System.out.println("EndSubscriber request " + REQUEST_WINDOW);
            subscription.request(REQUEST_WINDOW);
        }

        public static String info(List<ByteBuffer> i) {
            StringBuilder sb = new StringBuilder();
            sb.append("size: ").append(Integer.toString(i.size()));
            int x = 0;
            for (ByteBuffer b : i)
                x += b.remaining();
            sb.append(" bytes: ").append(x);
            return sb.toString();
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (--unfulfilled == (REQUEST_WINDOW / 2)) {
                long req = REQUEST_WINDOW - unfulfilled;
                System.out.println("EndSubscriber request " + req);
                unfulfilled = REQUEST_WINDOW;
                subscription.request(req);
            }

            long currval = counter.get();
            if (currval % 500 == 0) {
                System.out.println("EndSubscriber: " + currval);
            }
            System.out.println("EndSubscriber onNext " + Utils.remaining(buffers));

            for (ByteBuffer buf : buffers) {
                while (buf.hasRemaining()) {
                    long n = buf.getLong();
                    if (currval > (SSLTubeTest.TOTAL_LONGS - 50)) {
                        System.out.println("End: " + currval);
                    }
                    if (n != currval++) {
                        System.out.println("ERROR at " + n + " != " + (currval - 1));
                        completion.completeExceptionally(new RuntimeException("ERROR"));
                        subscription.cancel();
                        return;
                    }
                }
            }

            counter.set(currval);
        }

        @Override
        public void onError(Throwable throwable) {
            System.out.println("EndSubscriber onError " + throwable);
            completion.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            long n = counter.get();
            if (n != nbytes) {
                System.out.printf("nbytes=%d n=%d\n", nbytes, n);
                completion.completeExceptionally(new RuntimeException("ERROR AT END"));
            } else {
                System.out.println("DONE OK");
                completion.complete(null);
            }
        }
        @Override
        public String toString() {
            return "EndSubscriber";
        }
    }

    private static SSLEngine createSSLEngine(boolean client) throws IOException {
        SSLContext context = (new SimpleSSLContext()).get();
        SSLEngine engine = context.createSSLEngine();
        SSLParameters params = context.getSupportedSSLParameters();
        params.setProtocols(new String[]{"TLSv1.2"}); // TODO: This is essential. Needs to be protocol impl
        if (client) {
            params.setApplicationProtocols(new String[]{"proto1", "proto2"}); // server will choose proto2
        } else {
            params.setApplicationProtocols(new String[]{"proto2"}); // server will choose proto2
        }
        engine.setSSLParameters(params);
        engine.setUseClientMode(client);
        return engine;
    }

    /**
     * Creates a simple usable SSLContext for SSLSocketFactory or a HttpsServer
     * using either a given keystore or a default one in the test tree.
     *
     * Using this class with a security manager requires the following
     * permissions to be granted:
     *
     * permission "java.util.PropertyPermission" "test.src.path", "read";
     * permission java.io.FilePermission "${test.src}/../../../../lib/testlibrary/jdk/testlibrary/testkeys",
     * "read"; The exact path above depends on the location of the test.
     */
    private static class SimpleSSLContext {

        private final SSLContext ssl;

        /**
         * Loads default keystore from SimpleSSLContext source directory
         */
        public SimpleSSLContext() throws IOException {
            String paths = System.getProperty("test.src.path");
            StringTokenizer st = new StringTokenizer(paths, File.pathSeparator);
            boolean securityExceptions = false;
            SSLContext sslContext = null;
            while (st.hasMoreTokens()) {
                String path = st.nextToken();
                try {
                    File f = new File(path, "../../../../lib/testlibrary/jdk/testlibrary/testkeys");
                    if (f.exists()) {
                        try (FileInputStream fis = new FileInputStream(f)) {
                            sslContext = init(fis);
                            break;
                        }
                    }
                } catch (SecurityException e) {
                    // catch and ignore because permission only required
                    // for one entry on path (at most)
                    securityExceptions = true;
                }
            }
            if (securityExceptions) {
                System.err.println("SecurityExceptions thrown on loading testkeys");
            }
            ssl = sslContext;
        }

        private SSLContext init(InputStream i) throws IOException {
            try {
                char[] passphrase = "passphrase".toCharArray();
                KeyStore ks = KeyStore.getInstance("JKS");
                ks.load(i, passphrase);

                KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                kmf.init(ks, passphrase);

                TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
                tmf.init(ks);

                SSLContext ssl = SSLContext.getInstance("TLS");
                ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
                return ssl;
            } catch (KeyManagementException | KeyStoreException |
                    UnrecoverableKeyException | CertificateException |
                    NoSuchAlgorithmException e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        public SSLContext get() {
            return ssl;
        }
    }
}
