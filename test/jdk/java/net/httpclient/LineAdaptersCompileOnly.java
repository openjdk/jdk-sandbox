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

import java.util.concurrent.Flow;
import java.util.function.Function;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import static java.util.function.Function.identity;
import static java.nio.charset.StandardCharsets.*;

/*
 * @test
 * @summary Basic test for Flow adapters with generic type parameters
 * @compile LineAdaptersCompileOnly.java
 */

public class LineAdaptersCompileOnly {

    public static void main(String[] args) {
        makesSureDifferentGenericSignaturesCompile();
    }

    static void makesSureDifferentGenericSignaturesCompile() {
        BodyHandler.fromLineSubscriber(new StringSubscriber());
        BodyHandler.fromLineSubscriber(new CharSequenceSubscriber());
        BodyHandler.fromLineSubscriber(new ObjectSubscriber());

        BodySubscriber.fromLineSubscriber(new StringSubscriber());
        BodySubscriber.fromLineSubscriber(new CharSequenceSubscriber());
        BodySubscriber.fromLineSubscriber(new ObjectSubscriber());

        BodyHandler.fromLineSubscriber(new StringSubscriber(),       identity(), "\n");
        BodyHandler.fromLineSubscriber(new CharSequenceSubscriber(), identity(), "\r\n");
        BodyHandler.fromLineSubscriber(new ObjectSubscriber(),       identity(), "\n");
        BodyHandler.fromLineSubscriber(new StringSubscriber(),       identity(), null);
        BodyHandler.fromLineSubscriber(new CharSequenceSubscriber(), identity(), null);
        BodyHandler.fromLineSubscriber(new ObjectSubscriber(),       identity(), null);

        BodySubscriber.fromLineSubscriber(new StringSubscriber(),       identity(), UTF_8,    "\n");
        BodySubscriber.fromLineSubscriber(new CharSequenceSubscriber(), identity(), UTF_16,   "\r\n");
        BodySubscriber.fromLineSubscriber(new ObjectSubscriber(),       identity(), US_ASCII, "\n");
        BodySubscriber.fromLineSubscriber(new StringSubscriber(),       identity(), UTF_8,    null);
        BodySubscriber.fromLineSubscriber(new CharSequenceSubscriber(), identity(), UTF_16,   null);
        BodySubscriber.fromLineSubscriber(new ObjectSubscriber(),       identity(), US_ASCII, null);
    }

    static class StringSubscriber implements Flow.Subscriber<String> {
        @Override public void onSubscribe(Flow.Subscription subscription) { }
        @Override public void onNext(String item) { }
        @Override public void onError(Throwable throwable) { }
        @Override public void onComplete() { }
    }

    static class CharSequenceSubscriber implements Flow.Subscriber<CharSequence> {
        @Override public void onSubscribe(Flow.Subscription subscription) { }
        @Override public void onNext(CharSequence item) { }
        @Override public void onError(Throwable throwable) { }
        @Override public void onComplete() { }
    }

    static class ObjectSubscriber implements Flow.Subscriber<Object> {
        @Override public void onSubscribe(Flow.Subscription subscription) { }
        @Override public void onNext(Object item) { }
        @Override public void onError(Throwable throwable) { }
        @Override public void onComplete() { }
    }

    // ---

    static final Function<StringSubscriber,Integer> f1 = subscriber -> 1;
    static final Function<StringSubscriber,Number> f2 = subscriber -> 2;
    static final Function<StringSubscriberX,Integer> f3 = subscriber -> 3;
    static final Function<StringSubscriberX,Number> f4 = subscriber -> 4;

    static class StringSubscriberX extends StringSubscriber {
        int getIntegerX() { return 5; }
    }

    static void makesSureDifferentGenericFunctionSignaturesCompile() {
        BodyHandler<Integer> bh01 = BodyHandler.fromLineSubscriber(new StringSubscriber(), s -> 6, "\n");
        BodyHandler<Number>  bh02 = BodyHandler.fromLineSubscriber(new StringSubscriber(), s -> 7, "\n");
        BodyHandler<Integer> bh03 = BodyHandler.fromLineSubscriber(new StringSubscriber(), f1, "\n");
        BodyHandler<Number>  bh04 = BodyHandler.fromLineSubscriber(new StringSubscriber(), f1, "\n");
        BodyHandler<Number>  bh05 = BodyHandler.fromLineSubscriber(new StringSubscriber(), f2, "\n");
        BodyHandler<Integer> bh06 = BodyHandler.fromLineSubscriber(new StringSubscriberX(), f1, "\n");
        BodyHandler<Number>  bh07 = BodyHandler.fromLineSubscriber(new StringSubscriberX(), f1, "\n");
        BodyHandler<Number>  bh08 = BodyHandler.fromLineSubscriber(new StringSubscriberX(), f2, "\n");
        BodyHandler<Integer> bh09 = BodyHandler.fromLineSubscriber(new StringSubscriberX(), StringSubscriberX::getIntegerX, "\n");
        BodyHandler<Number>  bh10 = BodyHandler.fromLineSubscriber(new StringSubscriberX(), StringSubscriberX::getIntegerX, "\n");
        BodyHandler<Integer> bh11 = BodyHandler.fromLineSubscriber(new StringSubscriberX(), f3, "\n");
        BodyHandler<Number>  bh12 = BodyHandler.fromLineSubscriber(new StringSubscriberX(), f3, "\n");
        BodyHandler<Number>  bh13 = BodyHandler.fromLineSubscriber(new StringSubscriberX(), f4, "\n");

        BodySubscriber<Integer> bs01 = BodySubscriber.fromLineSubscriber(new StringSubscriber(), s -> 6, UTF_8, "\n");
        BodySubscriber<Number>  bs02 = BodySubscriber.fromLineSubscriber(new StringSubscriber(), s -> 7, UTF_8, "\n");
        BodySubscriber<Integer> bs03 = BodySubscriber.fromLineSubscriber(new StringSubscriber(), f1, UTF_8, "\n");
        BodySubscriber<Number>  bs04 = BodySubscriber.fromLineSubscriber(new StringSubscriber(), f1, UTF_8, "\n");
        BodySubscriber<Number>  bs05 = BodySubscriber.fromLineSubscriber(new StringSubscriber(), f2, UTF_8, "\n");
        BodySubscriber<Integer> bs06 = BodySubscriber.fromLineSubscriber(new StringSubscriberX(), f1, UTF_8, "\n");
        BodySubscriber<Number>  bs07 = BodySubscriber.fromLineSubscriber(new StringSubscriberX(), f1, UTF_8, "\n");
        BodySubscriber<Number>  bs08 = BodySubscriber.fromLineSubscriber(new StringSubscriberX(), f2, UTF_8, "\n");
        BodySubscriber<Integer> bs09 = BodySubscriber.fromLineSubscriber(new StringSubscriberX(), StringSubscriberX::getIntegerX, UTF_8, "\n");
        BodySubscriber<Number>  bs10 = BodySubscriber.fromLineSubscriber(new StringSubscriberX(), StringSubscriberX::getIntegerX, UTF_8, "\n");
        BodySubscriber<Integer> bs11 = BodySubscriber.fromLineSubscriber(new StringSubscriberX(), f3, UTF_8, "\n");
        BodySubscriber<Number>  bs12 = BodySubscriber.fromLineSubscriber(new StringSubscriberX(), f3, UTF_8, "\n");
        BodySubscriber<Number>  bs13 = BodySubscriber.fromLineSubscriber(new StringSubscriberX(), f4, UTF_8, "\n");
    }
}
