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

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;
import java.util.function.Function;
import jdk.incubator.http.HttpResponse.BodyHandler;
import jdk.incubator.http.HttpResponse.BodySubscriber;

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


        BodyHandler.fromLineSubscriber(new StringSubscriber(), Function.identity(), "\n");
        BodyHandler.fromLineSubscriber(new CharSequenceSubscriber(), Function.identity(),  "\r\n");
        BodyHandler.fromLineSubscriber(new ObjectSubscriber(), Function.identity(), "\n");
        BodyHandler.fromLineSubscriber(new StringSubscriber(), Function.identity(), null);
        BodyHandler.fromLineSubscriber(new CharSequenceSubscriber(), Function.identity(),  null);
        BodyHandler.fromLineSubscriber(new ObjectSubscriber(), Function.identity(), null);

        BodySubscriber.fromLineSubscriber(new StringSubscriber(), Function.identity(),
                StandardCharsets.UTF_8, "\n");
        BodySubscriber.fromLineSubscriber(new CharSequenceSubscriber(), Function.identity(),
                StandardCharsets.UTF_16, "\r\n");
        BodySubscriber.fromLineSubscriber(new ObjectSubscriber(), Function.identity(),
                StandardCharsets.US_ASCII, "\n");
        BodySubscriber.fromLineSubscriber(new StringSubscriber(), Function.identity(),
                StandardCharsets.UTF_8, null);
        BodySubscriber.fromLineSubscriber(new CharSequenceSubscriber(), Function.identity(),
                StandardCharsets.UTF_16, null);
        BodySubscriber.fromLineSubscriber(new ObjectSubscriber(), Function.identity(),
                StandardCharsets.US_ASCII, null);
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
}
