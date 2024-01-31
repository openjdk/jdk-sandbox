/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
/*
 * @test
 * @summary  Foo
 * @requires (os.family == "linux")
 * @run main/othervm MultiThreadTest
 */

import sun.nio.ch.iouring.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

public class MultiThreadTest {

    static BlockingRing ring;

    static void initIOUring() throws IOException {
        IOUring iouring = new IOUring(16, 16, 16 * 1024);
        ring = BlockingRing.getMultiThreadedRing(iouring);
    }
    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        initIOUring();

        // Platform threads
        ThreadFactory tf = (r) -> new Thread(r);
        /*
        System.out.println("Trying Platform threads");
        multitest(tf, MultiThreadTest::iouringtest, "/tmp/test/iouring");
        multitest(tf, MultiThreadTest::channeltest, "/tmp/test/channels");
        //multitest(tf, MultiThreadTest::iouringtest, "/tmp/test/iouring");
        //multitest(tf, MultiThreadTest::channeltest, "/tmp/test/channels");

         */
        tf = Thread.ofVirtual().factory();
        System.out.println("Trying Virtual threads");
        Files.createDirectories(Path.of("/tmp/test"));
        multitest(tf, MultiThreadTest::iouringtest, "/tmp/test/iouring");
        multitest(tf, MultiThreadTest::channeltest, "/tmp/test/channels");
        //multitest(tf, MultiThreadTest::iouringtest, "/tmp/test/iouring");
        //multitest(tf, MultiThreadTest::channeltest, "/tmp/test/channels");
    }

    static int NTHREADS = 100;
    static int FILE_SIZE = 1 * 1024;

    static record TestRun(CompletableFuture<Void> cf, int size, String nameBase) {}
    static List<CompletableFuture<Void>> cfs = new LinkedList<>();
    static void multitest(ThreadFactory fac, Consumer<TestRun> test, String nameBase) throws ExecutionException, InterruptedException {
        long l1 = System.currentTimeMillis();
        System.out.printf("NTHREADS: %d, FILE_SIZE: %d\n", NTHREADS, FILE_SIZE);
        for (int i=0; i<NTHREADS; i++) {
            CompletableFuture<Void> cf = new CompletableFuture<>();
            TestRun r = new TestRun(cf, FILE_SIZE, nameBase + "." + i);
            Thread t = fac.newThread(()-> {test.accept(r);});
            cfs.add(cf);
            t.start();
        }
        System.out.printf("%d threads started\n", NTHREADS);
        CompletableFuture.allOf(cfs.toArray(new CompletableFuture[0])).get();
        long l2 = System.currentTimeMillis();
        System.out.println("Finished " + nameBase + " " + (l2 - l1) + " millisec");
    }

    static void channeltest(TestRun test) {
        try {
            SingleThreadTest.channeltest(test.nameBase(), test.size(), false, new OpenOption[]{StandardOpenOption.SYNC});
            test.cf().complete(null);
        } catch (Throwable e) {
            test.cf().completeExceptionally(e);
        }
    }

    static void iouringtest(TestRun test) {
        try {
            SingleThreadTest.iouringtest(test.nameBase(), test.size(), false, ring, IOURingFile.O_SYNC);
            test.cf().complete(null);
        } catch (Throwable e) {
            test.cf().completeExceptionally(e);
        }
    }
}
