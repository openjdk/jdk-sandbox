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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This publisher signals {@code onNext} synchronously and
 * {@code onComplete}/{@code onError} asynchronously to its only subscriber.
 *
 * <p> This publisher supports a single subscriber over this publisher's
 * lifetime. {@code signalComplete} and {@code signalError} may be called before
 * the subscriber has subscribed.
 *
 * <p> The subscriber's requests are signalled to the subscription supplied to
 * the {@code feedback} method.
 *
 * <p> {@code subscribe} and {@code feedback} methods can be called in any
 * order.
 *
 * <p> {@code signalNext} may be called recursively, the implementation will
 * bound the depth of the recursion.
 *
 * <p> It is always an error to call {@code signalNext} without a sufficient
 * demand.
 *
 * <p> If subscriber throws an exception from any of its methods, the
 * subscription will be cancelled.
 */
public final class SynchronousPublisher<T> implements Publisher<T> {
    /*
     * PENDING, ACTIVE and CANCELLED are states. TERMINATE and DELIVERING are
     * state modifiers, they cannot appear in the state on their own.
     *
     * PENDING, ACTIVE and CANCELLED are mutually exclusive states. Any two of
     * those bits cannot be set at the same time in state.
     *
     * PENDING -----------------> ACTIVE <------> DELIVERING
     *    |                         |
     *    +------> TERMINATE <------+
     *    |            |            |
     *    |            v            |
     *    +------> CANCELLED <------+
     *
     * The following states are allowed:
     *
     *     PENDING
     *     PENDING | TERMINATE,
     *     ACTIVE,
     *     ACTIVE | DELIVERING,
     *     ACTIVE | TERMINATE,
     *     ACTIVE | DELIVERING | TERMINATE
     *     CANCELLED
     */
    /**
     * A state modifier meaning {@code onSubscribe} has not been called yet.
     *
     * <p> After {@code onSubscribe} has been called the machine can transition
     * into {@code ACTIVE}, {@code PENDING | TERMINATE} or {@code CANCELLED}.
     */
    private static final int PENDING = 1;
    /**
     * A state modifier meaning {@code onSubscribe} has been called, no error
     * and no completion has been signalled and {@code onNext} may be called.
     */
    private static final int ACTIVE = 2;
    /**
     * A state modifier meaning no calls to subscriber may be made.
     *
     * <p> Once this modifier is set, it will not be unset. It's a final state.
     */
    private static final int CANCELLED = 4;
    /**
     * A state modifier meaning {@code onNext} is being called and no other
     * signal may be made.
     *
     * <p> This bit can be set at any time. signalNext uses it to ensure the
     * method is called sequentially.
     */
    private static final int DELIVERING = 8;
    /**
     * A state modifier meaning the next call must be either {@code onComplete}
     * or {@code onError}.
     *
     * <p> The concrete method depends on the value of {@code terminationType}).
     * {@code TERMINATE} bit cannot appear on its own, it can be set only with
     * {@code PENDING} or {@code ACTIVE}.
     */
    private static final int TERMINATE = 16;
    /**
     * Current demand. If fulfilled, no {@code onNext} signals may be made.
     */
    private final Demand demand = new Demand();
    /**
     * The current state of the subscription. Contains disjunctions of the above
     * state modifiers.
     */
    private final AtomicInteger state = new AtomicInteger(PENDING);
    /**
     * A convenient way to represent 3 values: not set, completion and error.
     */
    private final AtomicReference<Optional<Throwable>> terminationType
            = new AtomicReference<>();
    /**
     * {@code signalNext} uses this lock to ensure the method is called in a
     * thread-safe manner.
     */
    private final ReentrantLock nextLock = new ReentrantLock();
    private T next;

    private final Object lock = new Object();
    /**
     * This map stores the subscribers attempted to subscribe to this publisher.
     * It is needed so this publisher does not call {@code onSubscribe} on a
     * subscriber more than once (Rule 2.12).
     *
     * <p> It will most likely have a single entry for the only subscriber.
     * Because this publisher is one-off, subscribing to it more than once is an
     * error.
     */
    private final Map<Subscriber<?>, Object> knownSubscribers
            = new WeakHashMap<>(1, 1);
    /**
     * The active subscriber. This reference will be reset to {@code null} once
     * the subscription becomes cancelled (Rule 3.13).
     */
    private volatile Subscriber<? super T> subscriber;
    /**
     * A temporary subscription that receives all calls to
     * {@code request}/{@code cancel} until two things happen: (1) the feedback
     * becomes set and (2) {@code onSubscribe} method is called on the
     * subscriber.
     *
     * <p> The first condition is obvious. The second one is about not
     * propagating requests to {@code feedback} until {@code onSubscribe} call
     * has been finished. The reason is that Rule 1.3 requires the subscriber
     * methods to be called in a thread-safe manner. This, in particular,
     * implies that if called from multiple threads, the calls must not be
     * concurrent. If, for instance, {@code subscription.request(long)) (and
     * this is a usual state of affairs) is called from within
     * {@code onSubscribe} call, the publisher will have to resort to some sort
     * of queueing (locks, queues, etc.) of possibly arriving {@code onNext}
     * signals while in {@code onSubscribe}. This publisher doesn't queue
     * signals, instead it "queues" requests. Because requests are just numbers
     * and requests are additive, the effective queue is a single number of
     * total requests made so far.
     */
    private final TemporarySubscription temporarySubscription
            = new TemporarySubscription();
    private volatile Subscription feedback;
    /**
     * Keeping track of whether a subscription may be made. (The {@code
     * subscriber} field may later become {@code null}, but this flag is
     * permanent. Once {@code true} forever {@code true}.
     */
    private boolean subscribed;

    @Override
    public void subscribe(Subscriber<? super T> sub) {
        Objects.requireNonNull(sub);
        boolean success = false;
        boolean duplicate = false;
        synchronized (lock) {
            if (!subscribed) {
                subscribed = true;
                subscriber = sub;
                assert !knownSubscribers.containsKey(subscriber);
                knownSubscribers.put(subscriber, null);
                success = true;
            } else if (sub.equals(subscriber)) {
                duplicate = true;
            } else if (!knownSubscribers.containsKey(sub)) {
                knownSubscribers.put(sub, null);
            } else {
                return;
            }
        }
        if (success) {
            signalSubscribe();
        } else if (duplicate) {
            signalError(new IllegalStateException("Duplicate subscribe"));
        } else {
            // This is a best-effort attempt for an isolated publisher to call
            // a foreign subscriber's methods in a sequential order. However it
            // cannot be guaranteed unless all publishers share information on
            // all subscribers in the system. This publisher does its job right.
            sub.onSubscribe(new NopSubscription());
            sub.onError(new IllegalStateException("Already subscribed"));
        }
    }

    /**
     * Accepts a subscription that is signalled with the subscriber's requests.
     *
     * @throws NullPointerException
     *         if {@code subscription} is {@code null}
     * @throws IllegalStateException
     *         if there is a feedback subscription already
     */
    public void feedback(Subscription subscription) {
        Objects.requireNonNull(subscription);
        synchronized (lock) {
            if (feedback != null) {
                throw new IllegalStateException(
                        "Already has a feedback subscription");
            }
            feedback = subscription;
            if ((state.get() & PENDING) == 0) {
                temporarySubscription.replaceWith(new PermanentSubscription());
            }
        }
    }

    /**
     * Tries to deliver the specified item to the subscriber.
     *
     * <p> The item may not be delivered even if there is a demand. This can
     * happen as a result of subscriber cancelling the subscription by
     * signalling {@code cancel} or this publisher cancelling the subscription
     * by signaling {@code onError} or {@code onComplete}.
     *
     * <p> Given no exception is thrown, a call to this method decremented the
     * demand.
     *
     * @param item
     *         the item to deliver to the subscriber
     *
     * @return {@code true} iff the subscriber has received {@code item}
     * @throws NullPointerException
     *         if {@code item} is {@code null}
     * @throws IllegalStateException
     *         if there is no demand
     * @throws IllegalStateException
     *         the method is called concurrently
     */
    public boolean signalNext(T item) {
        Objects.requireNonNull(item);
        if (!nextLock.tryLock()) {
            throw new IllegalStateException("Concurrent signalling");
        }
        boolean recursion = false;
        try {
            next = item;
            while (true) {
                int s = state.get();
                if ((s & DELIVERING) == DELIVERING) {
                    recursion = true;
                    break;
                } else if (state.compareAndSet(s, s | DELIVERING)) {
                    break;
                }
            }
            if (!demand.tryDecrement()) {
                // Hopefully this will help to find bugs in this publisher's
                // clients. Because signalNext should never be issues without
                // having a sufficient demand. Even if the thing is cancelled!
//                next = null;
                throw new IllegalStateException("No demand");
            }
            if (recursion) {
                return true;
            }
            while (next != null) {
                int s = state.get();
                if ((s & (ACTIVE | TERMINATE)) == (ACTIVE | TERMINATE)) {
                    if (state.compareAndSet(
                            s, CANCELLED | (s & ~(ACTIVE | TERMINATE)))) {
                        // terminationType must be read only after the
                        // termination condition has been observed
                        // (those have been stored in the opposite order)
                        Optional<Throwable> t = terminationType.get();
                        dispatchTerminationAndUnsubscribe(t);
                        return false;
                    }
                } else if ((s & ACTIVE) == ACTIVE) {
                    try {
                        T t = next;
                        next = null;
                        subscriber.onNext(t);
                    } catch (Throwable t) {
                        cancelNow();
                        throw t;
                    }
                } else if ((s & CANCELLED) == CANCELLED) {
                    return false;
                } else if ((s & PENDING) == PENDING) {
                    // Actually someone called signalNext even before
                    // onSubscribe has been called, but from this publisher's
                    // API point of view it's still "No demand"
                    throw new IllegalStateException("No demand");
                } else {
                    throw new InternalError(String.valueOf(s));
                }
            }
            return true;
        } finally {
            while (!recursion) { // If the call was not recursive unset the bit
                int s = state.get();
                if ((s & DELIVERING) != DELIVERING) {
                    throw new InternalError(String.valueOf(s));
                } else if (state.compareAndSet(s, s & ~DELIVERING)) {
                    break;
                }
            }
            nextLock.unlock();
        }
    }

    /**
     * Cancels the subscription by signalling {@code onError} to the subscriber.
     *
     * <p> Will not signal {@code onError} if the subscription has been
     * cancelled already.
     *
     * <p> This method may be called at any time.
     *
     * @param error
     *         the error to signal
     *
     * @throws NullPointerException
     *         if {@code error} is {@code null}
     */
    public void signalError(Throwable error) {
        terminateNow(Optional.of(error));
    }

    /**
     * Cancels the subscription by signalling {@code onComplete} to the
     * subscriber.
     *
     * <p> Will not signal {@code onComplete} if the subscription has been
     * cancelled already.
     *
     * <p> This method may be called at any time.
     */
    public void signalComplete() {
        terminateNow(Optional.empty());
    }

    /**
     * Must be called first and at most once.
     */
    private void signalSubscribe() {
        assert subscribed;
        try {
            subscriber.onSubscribe(temporarySubscription);
        } catch (Throwable t) {
            cancelNow();
            throw t;
        }
        while (true) {
            int s = state.get();
            if ((s & (PENDING | TERMINATE)) == (PENDING | TERMINATE)) {
                if (state.compareAndSet(
                        s, CANCELLED | (s & ~(PENDING | TERMINATE)))) {
                    Optional<Throwable> t = terminationType.get();
                    dispatchTerminationAndUnsubscribe(t);
                    return;
                }
            } else if ((s & PENDING) == PENDING) {
                if (state.compareAndSet(s, ACTIVE | (s & ~PENDING))) {
                    synchronized (lock) {
                        if (feedback != null) {
                            temporarySubscription
                                    .replaceWith(new PermanentSubscription());
                        }
                    }
                    return;
                }
            } else { // It should not be in any other state
                throw new InternalError(String.valueOf(s));
            }
        }
    }

    private void unsubscribe() {
        subscriber = null;
    }

    private final static class NopSubscription implements Subscription {

        @Override
        public void request(long n) { }
        @Override
        public void cancel() { }
    }

    private final class PermanentSubscription implements Subscription {

        @Override
        public void request(long n) {
            if (n <= 0) {
                signalError(new IllegalArgumentException(
                        "non-positive subscription request"));
            } else {
                demand.increase(n);
                feedback.request(n);
            }
        }

        @Override
        public void cancel() {
            if (cancelNow()) {
                unsubscribe();
                // feedback.cancel() is called at most once
                // (let's not assume idempotency)
                feedback.cancel();
            }
        }
    }

    /**
     * Cancels the subscription unless it has been cancelled already.
     *
     * @return {@code true} iff the subscription has been cancelled as a result
     *         of this call
     */
    private boolean cancelNow() {
        while (true) {
            int s = state.get();
            if ((s & CANCELLED) == CANCELLED) {
                return false;
            } else if ((s & (ACTIVE | PENDING)) != 0) {
                // ACTIVE or PENDING
                if (state.compareAndSet(
                        s, CANCELLED | (s & ~(ACTIVE | PENDING)))) {
                    unsubscribe();
                    return true;
                }
            } else {
                throw new InternalError(String.valueOf(s));
            }
        }
    }

    /**
     * Terminates this subscription unless is has been cancelled already.
     *
     * @param t the type of termination
     */
    private void terminateNow(Optional<Throwable> t) {
        // Termination condition must be set only after the termination
        // type has been set (those will be read in the opposite order)
        if (!terminationType.compareAndSet(null, t)) {
            return;
        }
        while (true) {
            int s = state.get();
            if ((s & CANCELLED) == CANCELLED) {
                return;
            } else if ((s & (PENDING | DELIVERING)) != 0) {
                // PENDING or DELIVERING (which implies ACTIVE)
                if (state.compareAndSet(s, s | TERMINATE)) {
                    return;
                }
            } else if ((s & ACTIVE) == ACTIVE) {
                if (state.compareAndSet(s, CANCELLED | (s & ~ACTIVE))) {
                    dispatchTerminationAndUnsubscribe(t);
                    return;
                }
            } else {
                throw new InternalError(String.valueOf(s));
            }
        }
    }

    private void dispatchTerminationAndUnsubscribe(Optional<Throwable> t) {
        try {
            t.ifPresentOrElse(subscriber::onError, subscriber::onComplete);
        } finally {
            unsubscribe();
        }
    }
}
