/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http.internal.common;

import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED;

public class SSLTube implements FlowTube {

    static final boolean DEBUG = Utils.DEBUG; // revisit: temporary developer's flag.
    final System.Logger debug =
            Utils.getDebugLogger(this::dbgString, DEBUG);

    private final FlowTube tube;
    private final SSLSubscriberWrapper readSubscriber;
    private final SSLSubscriptionWrapper writeSubscription;
    private final SSLFlowDelegate sslDelegate;
    private final SSLEngine engine;
    private volatile boolean finished;

    public SSLTube(SSLEngine engine, Executor executor, FlowTube tube) {
        Objects.requireNonNull(engine);
        Objects.requireNonNull(executor);
        this.tube = Objects.requireNonNull(tube);
        writeSubscription = new SSLSubscriptionWrapper();
        readSubscriber = new SSLSubscriberWrapper();
        this.engine = engine;
        sslDelegate = new SSLFlowDelegate(engine,
                                          executor,
                                          readSubscriber,
                                          tube); // FIXME
        tube.subscribe(sslDelegate.upstreamReader());
        sslDelegate.upstreamWriter().onSubscribe(writeSubscription);
    }

    public CompletableFuture<String> getALPN() {
        return sslDelegate.alpn();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super List<ByteBuffer>> s) {
        readSubscriber.dropSubscription();
        readSubscriber.setDelegate(s);
        s.onSubscribe(readSubscription);
    }

    /**
     * Tells whether, or not, this FlowTube has finished receiving data.
     *
     * @return true when one of this FlowTube Subscriber's OnError or onComplete
     * methods have been invoked
     */
    @Override
    public boolean isFinished() {
        return finished;
    }

    private volatile Flow.Subscription readSubscription;

    // The DelegateWrapper wraps a subscribed {@code Flow.Subscriber} and
    // tracks the subscriber's state. In particular it makes sure that
    // onComplete/onError are not called before onSubscribed.
    final static class DelegateWrapper implements FlowTube.TubeSubscriber {
        private final FlowTube.TubeSubscriber delegate;
        volatile boolean subscribedCalled;
        volatile boolean subscribedDone;
        volatile boolean completed;
        volatile Throwable error;
        DelegateWrapper(Flow.Subscriber<? super List<ByteBuffer>> delegate) {
            this.delegate = FlowTube.asTubeSubscriber(delegate);
        }

        @Override
        public void dropSubscription() {
            if (subscribedCalled) {
                delegate.dropSubscription();
            }
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            assert subscribedCalled;
            delegate.onNext(item);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            onSubscribe(delegate::onSubscribe, subscription);
        }

        @Override
        public void onConnection(Flow.Subscription subscription) {
            onSubscribe(delegate::onConnection, subscription);
        }

        private void onSubscribe(Consumer<Flow.Subscription> method,
                                 Flow.Subscription subscription) {
            subscribedCalled = true;
            method.accept(subscription);
            Throwable x;
            boolean finished;
            synchronized (this) {
                subscribedDone = true;
                x = error;
                finished = completed;
            }
            if (x != null) {
                delegate.onError(x);
            } else if (finished) {
                delegate.onComplete();
            }
        }

        @Override
        public void onError(Throwable t) {
            if (completed) return;
            boolean subscribed;
            synchronized (this) {
                if (completed) return;
                error = t;
                completed = true;
                subscribed = subscribedDone;
            }
            if (subscribed) {
                delegate.onError(t);
            }
        }

        @Override
        public void onComplete() {
            if (completed) return;
            boolean subscribed;
            synchronized (this) {
                if (completed) return;
                completed = true;
                subscribed = subscribedDone;
            }
            if (subscribed) {
                delegate.onComplete();
            }
        }

        @Override
        public String toString() {
            return "DelegateWrapper:" + delegate.toString();
        }

    }

    // Used to read data from the SSLTube.
    final class SSLSubscriberWrapper implements FlowTube.TubeSubscriber {
        private volatile DelegateWrapper delegate;
        private volatile DelegateWrapper subscribed;
        private volatile boolean onCompleteReceived;
        private final AtomicReference<Throwable> errorRef
                = new AtomicReference<>();

        void setDelegate(Flow.Subscriber<? super List<ByteBuffer>> delegate) {
            debug.log(Level.DEBUG, "SSLSubscriberWrapper (reader) got delegate: %s",
                      delegate);
            assert delegate != null;
            DelegateWrapper delegateWrapper = new DelegateWrapper(delegate);
            Flow.Subscription subscription;
            synchronized (this) {
                this.delegate = delegateWrapper;
                subscription = readSubscription;
            }
            if (subscription == null) {
                debug.log(Level.DEBUG, "SSLSubscriberWrapper (reader) no subscription yet");
                return;
            }

            onNewSubscription(delegateWrapper,
                              delegateWrapper::onSubscribe,
                              subscription);
        }

        @Override
        public void dropSubscription() {
            DelegateWrapper subscriberImpl = delegate;
            if (subscriberImpl != null) {
                subscriberImpl.dropSubscription();
            }
        }

        @Override
        public void onConnection(Flow.Subscription subscription) {
            debug.log(Level.DEBUG,
                      "SSLSubscriberWrapper (reader) onConnection(%s)",
                      subscription);
            assert subscription != null;
            DelegateWrapper subscriberImpl;
            synchronized (this) {
                subscriberImpl = delegate;
                readSubscription = subscription;
            }
            if (subscriberImpl == null) {
                debug.log(Level.DEBUG,
                      "SSLSubscriberWrapper (reader) onConnection: no delegate yet");
                return;
            }
            onNewSubscription(subscriberImpl,
                              subscriberImpl::onConnection,
                              subscription);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            debug.log(Level.DEBUG,
                      "SSLSubscriberWrapper (reader) onSubscribe(%s)",
                      subscription);
            readSubscription = subscription;
            assert subscription != null;
            DelegateWrapper subscriberImpl;
            synchronized (this) {
                subscriberImpl = delegate;
                readSubscription = subscription;
            }
            if (subscriberImpl == null) {
                debug.log(Level.DEBUG,
                      "SSLSubscriberWrapper (reader) onSubscribe: no delegate yet");
                return;
            }
            onNewSubscription(subscriberImpl,
                              subscriberImpl::onSubscribe,
                              subscription);
        }

        private void onNewSubscription(DelegateWrapper subscriberImpl,
                                       Consumer<Flow.Subscription> method,
                                       Flow.Subscription subscription) {
            assert subscriberImpl != null;
            assert method != null;
            assert subscription != null;

            Throwable failed;
            boolean completed;
            // reset any demand that may have been made by the previous
            // subscriber
            sslDelegate.resetReaderDemand();
            // send the subscription to the subscriber.
            method.accept(subscription);
            // reschedule after calling onSubscribe (this should not be
            // strictly needed as the first call to subscription.request()
            // coming after resetting the demand should trigger it).
            // However, it should not do any harm.
            sslDelegate.resumeReader();

            // The following twisted logic is just here that we don't invoke
            // onError before onSubscribe. It also prevents race conditions
            // if onError is invoked concurrently with setDelegate.
            synchronized (this) {
                failed = this.errorRef.get();
                completed = finished;
                if (delegate == subscriberImpl) {
                    subscribed = subscriberImpl;
                }
            }
            if (failed != null) {
                subscriberImpl.onError(failed);
            } else if (completed) {
                subscriberImpl.onComplete();
            }
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            delegate.onNext(item);
        }

        public void onErrorImpl(Throwable throwable) {
            // The following twisted logic is just here that we don't invoke
            // onError before onSubscribe. It also prevents race conditions
            // if onError is invoked concurrently with setDelegate.
            // See setDelegate.

            errorRef.compareAndSet(null, throwable);
            Throwable failed = errorRef.get();
            finished = true;
            debug.log(Level.DEBUG, "%s: onErrorImpl: %s", this, throwable);
            DelegateWrapper subscriberImpl;
            synchronized (this) {
                subscriberImpl = subscribed;
            }
            if (subscriberImpl != null) {
                subscriberImpl.onError(failed);
            } else {
                debug.log(Level.DEBUG, "%s: delegate null, stored %s", this, failed);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            assert !finished && !onCompleteReceived;
            onErrorImpl(throwable);
        }

        private boolean handshaking() {
            HandshakeStatus hs = engine.getHandshakeStatus();
            return !(hs == NOT_HANDSHAKING || hs == FINISHED);
        }

        @Override
        public void onComplete() {
            assert !finished && !onCompleteReceived;
            onCompleteReceived = true;
            DelegateWrapper subscriberImpl;
            synchronized(this) {
                subscriberImpl = subscribed;
            }

            if (handshaking()) {
                onErrorImpl(new SSLHandshakeException(
                        "Remote host terminated the handshake"));
            } else if (subscriberImpl != null) {
                finished = true;
                subscriberImpl.onComplete();
            }
        }
    }

    @Override
    public void connectFlows(TubePublisher writePub,
                             TubeSubscriber readSub) {
        debug.log(Level.DEBUG, "connecting flows");
        readSubscriber.setDelegate(readSub);
        writePub.subscribe(this);
    }

    /** Outstanding write demand from the SSL Flow Delegate. */
    private final Demand writeDemand = new Demand();

    final class SSLSubscriptionWrapper implements Flow.Subscription {

        volatile Flow.Subscription delegate;

        void setSubscription(Flow.Subscription sub) {
            long demand = writeDemand.get(); // FIXME: isn't it a racy way of passing the demand?
            delegate = sub;
            debug.log(Level.DEBUG, "setSubscription: demand=%d", demand);
            if (demand > 0)
                sub.request(demand);
        }

        @Override
        public void request(long n) {
            writeDemand.increase(n);
            debug.log(Level.DEBUG, "request: n=%d", n);
            Flow.Subscription sub = delegate;
            if (sub != null && n > 0) {
                sub.request(n);
            }
        }

        @Override
        public void cancel() {
            // TODO:  no-op or error?
        }
    }

    /* Subscriber - writing side */
    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        Objects.requireNonNull(subscription);
        Flow.Subscription x = writeSubscription.delegate;
        if (x != null)
            x.cancel();

        writeSubscription.setSubscription(subscription);
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        Objects.requireNonNull(item);
        boolean decremented = writeDemand.tryDecrement();
        assert decremented : "Unexpected writeDemand: ";
        debug.log(Level.DEBUG,
                "sending %d  buffers to SSL flow delegate", item.size());
        sslDelegate.upstreamWriter().onNext(item);
    }

    @Override
    public void onError(Throwable throwable) {
        Objects.requireNonNull(throwable);
        sslDelegate.upstreamWriter().onError(throwable);
    }

    @Override
    public void onComplete() {
        sslDelegate.upstreamWriter().onComplete();
    }

    @Override
    public String toString() {
        return dbgString();
    }

    final String dbgString() {
        return "SSLTube(" + tube + ")";
    }

}
