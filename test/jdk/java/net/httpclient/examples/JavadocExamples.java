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

import java.io.IOException;
import java.io.PrintStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.regex.Pattern;

/*
 * THE CONTENTS OF THIS FILE HAVE TO BE IN SYNC WITH THE EXAMPLES USED IN THE
 * JAVADOC.
 *
 * @test
 * @compile JavadocExamples.java
 */
public class JavadocExamples {
    HttpRequest request = null;
    HttpClient client = null;
    Pattern p = null;

    /**
     * @apiNote This method can be used as an adapter between a {@code
     * BodySubscriber} and a text based {@code Flow.Subscriber} that parses
     * text line by line.
     *
     * <p> For example:
     * <pre> {@code  // A PrintSubscriber that implements Flow.Subscriber<String>
     *  // and print lines received by onNext() on System.out
     *  PrintSubscriber subscriber = new PrintSubscriber(System.out);
     *  client.sendAsync(request, BodyHandlers.fromLineSubscriber(subscriber))
     *      .thenApply(HttpResponse::statusCode)
     *      .thenAccept((status) -> {
     *          if (status != 200) {
     *              System.err.printf("ERROR: %d status received%n", status);
     *          }
     *      }); }</pre>
     */
    void fromLineSubscriber1() {
         // A PrintSubscriber that implements Flow.Subscriber<String>
         // and print lines received by onNext() on System.out
         PrintSubscriber subscriber = new PrintSubscriber(System.out);
         client.sendAsync(request, BodyHandlers.fromLineSubscriber(subscriber))
                 .thenApply(HttpResponse::statusCode)
                 .thenAccept((status) -> {
                     if (status != 200) {
                         System.err.printf("ERROR: %d status received%n", status);
                     }
                 });
    }

    /**
     * @apiNote This method can be used as an adapter between a {@code
     * BodySubscriber} and a text based {@code Flow.Subscriber} that parses
     * text line by line.
     *
     * <p> For example:
     * <pre> {@code  // A LineParserSubscriber that implements Flow.Subscriber<String>
     *  // and accumulates lines that match a particular pattern
     *  Pattern pattern = ...;
     *  LineParserSubscriber subscriber = new LineParserSubscriber(pattern);
     *  HttpResponse<List<String>> response = client.send(request,
     *      BodyHandlers.fromLineSubscriber(subscriber, s -> s.getMatchingLines(), "\n"));
     *  if (response.statusCode() != 200) {
     *      System.err.printf("ERROR: %d status received%n", response.statusCode());
     *  } }</pre>
     *
     */
    void fromLineSubscriber2() throws IOException, InterruptedException {
        // A LineParserSubscriber that implements Flow.Subscriber<String>
        // and accumulates lines that match a particular pattern
        Pattern pattern = p;
        LineParserSubscriber subscriber = new LineParserSubscriber(pattern);
        HttpResponse<List<String>> response = client.send(request,
                BodyHandlers.fromLineSubscriber(subscriber, s -> s.getMatchingLines(), "\n"));
        if (response.statusCode() != 200) {
            System.err.printf("ERROR: %d status received%n", response.statusCode());
        }
    }

    static final class PrintSubscriber implements Flow.Subscriber<String> {
        final PrintStream out;
        PrintSubscriber(PrintStream out) {
            this.out = out;
        }
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }
        @Override
        public void onNext(String item) {
            out.println(item);
        }

        @Override
        public void onError(Throwable throwable) {
            throwable.printStackTrace();
        }
        @Override
        public void onComplete() {
        }
    }

    static final class LineParserSubscriber implements Flow.Subscriber<String> {
        final Pattern pattern;
        final CopyOnWriteArrayList<String> matches = new CopyOnWriteArrayList<>();
        LineParserSubscriber(Pattern pattern) {
            this.pattern = pattern;
        }
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }
        @Override
        public void onNext(String item) {
            if (pattern.matcher(item).matches()) {
                matches.add(item);
            }
        }
        @Override
        public void onError(Throwable throwable) {
            throwable.printStackTrace();
        }
        @Override
        public void onComplete() {
        }

        public List<String> getMatchingLines() {
            return Collections.unmodifiableList(matches);
        }
    }

}
