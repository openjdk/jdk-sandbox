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

import jdk.incubator.http.internal.common.SequentialScheduler.CompleteRestartableTask;

import java.util.Objects;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Acts as a subscription receiving calls to {@code request} and {@code cancel}
 * methods until the replacing subscription is set.
 *
 * <p> After the replacing subscription is set, it gets updated with the result
 * of calls happened before that and starts receiving calls to its
 * {@code request} and {@code cancel} methods.
 *
 * <p> This subscription ensures that {@code request} and {@code cancel} methods
 * of the replacing subscription are called sequentially.
 */
public final class TemporarySubscription implements Subscription {

    private final AtomicReference<Subscription> subscription = new AtomicReference<>();
    private final Demand demand = new Demand();
    private volatile boolean cancelled;
    private volatile long illegalValue = 1;

    private final SequentialScheduler scheduler = new SequentialScheduler(new UpdateTask());

    @Override
    public void request(long n) {
        if (n <= 0) {
            // Any non-positive request would do, no need to remember them
            // all or any one in particular.
            // tl;dr racy, but don't care
            illegalValue = n;
        } else {
            demand.increase(n);
        }
        scheduler.runOrSchedule();
    }

    @Override
    public void cancel() {
        cancelled = true;
        scheduler.runOrSchedule();
    }

    public void replaceWith(Subscription permanentSubscription) {
        Objects.requireNonNull(permanentSubscription);
        if (permanentSubscription == this) {
            // Otherwise it would be an unpleasant bug to chase
            throw new IllegalStateException("Self replacement");
        }
        if (!subscription.compareAndSet(null, permanentSubscription)) {
            throw new IllegalStateException("Already replaced");
        }
        scheduler.runOrSchedule();
    }

    private final class UpdateTask extends CompleteRestartableTask {

        @Override
        public void run() {
            Subscription dst = TemporarySubscription.this.subscription.get();
            if (dst == null) {
                return;
            }
            /* As long as the result is effectively the same, it does not matter
               how requests are accumulated and what goes first: request or
               cancel. See rules 3.5, 3.6, 3.7 and 3.9 from the reactive-streams
               specification. */
            long illegalValue = TemporarySubscription.this.illegalValue;
            if (illegalValue <= 0) {
                dst.request(illegalValue);
                scheduler.stop();
            } else if (cancelled) {
                dst.cancel();
                scheduler.stop();
            } else {
                long accumulatedValue = demand.decreaseAndGet(Long.MAX_VALUE);
                if (accumulatedValue > 0) {
                    dst.request(accumulatedValue);
                }
            }
        }
    }
}
