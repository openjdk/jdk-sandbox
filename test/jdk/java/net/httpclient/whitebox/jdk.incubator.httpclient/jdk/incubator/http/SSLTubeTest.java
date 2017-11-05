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
import jdk.incubator.http.internal.common.SSLTube;
import jdk.incubator.http.internal.common.SequentialScheduler;
import org.testng.annotations.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Queue;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Test
public class SSLTubeTest {

    private static final long COUNTER = 600;
    private static final int LONGS_PER_BUF = 800;
    private static final long TOTAL_LONGS = COUNTER * LONGS_PER_BUF;

    private static ByteBuffer getBuffer(long startingAt) {
        ByteBuffer buf = ByteBuffer.allocate(LONGS_PER_BUF * 8);
        for (int j = 0; j < LONGS_PER_BUF; j++) {
            buf.putLong(startingAt++);
        }
        buf.flip();
        return buf;
    }

    @Test(timeOut = 30000)
    public void run() throws IOException {
        /* Start of wiring */
        ExecutorService sslExecutor = Executors.newCachedThreadPool();
        /* Emulates an echo server */
        FlowTube server = new SSLTube(createSSLEngine(false),
                                      sslExecutor,
                                      new EchoTube(16));
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

    private static final class EchoTube implements FlowTube {

        private final static Object EOF = new Object();
        private final Executor executor = Executors.newSingleThreadExecutor();

        private final Queue<Object> queue = new ConcurrentLinkedQueue<>();
        private final int maxQueueSize;
        private final SequentialScheduler processingScheduler =
                new SequentialScheduler(createProcessingTask());

        /* Writing into this tube */
        private long unfulfilled;
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
            this.subscriber.onSubscribe(new InternalSubscription());
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            unfulfilled = maxQueueSize;
            (this.subscription = subscription).request(maxQueueSize);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            if (--unfulfilled == (maxQueueSize / 2)) {
                subscription.request(maxQueueSize - unfulfilled);
                unfulfilled = maxQueueSize;
            }
            queue.add(item);
            processingScheduler.deferOrSchedule(executor);
        }

        @Override
        public void onError(Throwable throwable) {
            queue.add(throwable);
            processingScheduler.deferOrSchedule(executor);
        }

        @Override
        public void onComplete() {
            queue.add(EOF);
            processingScheduler.deferOrSchedule(executor);
        }

        @Override
        public boolean isFinished() {
            return false;
        }

        private class InternalSubscription implements Flow.Subscription {

            @Override
            public void request(long n) {
                if (n <= 0) {
                    throw new InternalError();
                }
                demand.increase(n);
                processingScheduler.runOrSchedule();
            }

            @Override
            public void cancel() {
                cancelled.set(true);
            }
        }

        private SequentialScheduler.RestartableTask createProcessingTask() {
            return new SequentialScheduler.CompleteRestartableTask() {

                @Override
                protected void run() {
                    while (!cancelled.get()) {
                        Object item = queue.peek();
                        if (item == null)
                            return;
                        try {
                            if (item instanceof List) {
                                if (!demand.tryDecrement())
                                    return;
                                @SuppressWarnings("unchecked")
                                List<ByteBuffer> bytes = (List<ByteBuffer>) item;
                                subscriber.onNext(bytes);
                            } else if (item instanceof Throwable) {
                                cancelled.set(true);
                                subscriber.onError((Throwable) item);
                            } else if (item == EOF) {
                                cancelled.set(true);
                                subscriber.onComplete();
                            } else {
                                throw new InternalError(String.valueOf(item));
                            }
                        } finally {
                            Object removed = queue.remove();
                            assert removed == item;
                        }
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
                subscription.request(REQUEST_WINDOW - unfulfilled);
                unfulfilled = REQUEST_WINDOW;
            }

            long currval = counter.get();
            if (currval % 500 == 0) {
                System.out.println("End: " + currval);
            }

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
