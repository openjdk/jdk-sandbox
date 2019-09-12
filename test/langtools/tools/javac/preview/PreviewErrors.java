/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8226585
 * @summary Verify behavior w.r.t. preview feature API errors and warnings
 * @library /tools/lib
 * @modules
 *      java.base/jdk.internal
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @compile --enable-preview -source ${jdk.version} PreviewErrors.java
 * @run main/othervm --enable-preview PreviewErrors
 */

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import jdk.internal.PreviewFeature;

public class PreviewErrors extends TestRunner {

    protected ToolBox tb;

    PreviewErrors() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String... args) throws Exception {
        PreviewErrors t = new PreviewErrors();
        t.runTests();
    }

    /**
     * Run all methods annotated with @Test, and throw an exception if any
     * errors are reported..
     *
     * @throws Exception if any errors occurred
     */
    protected void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    Path[] findJavaFiles(Path... paths) throws IOException {
        return tb.findJavaFiles(paths);
    }

    @Test
    public void essentialApi(Path base) throws Exception {
        Path src = base.resolve("src");
        Path srcJavaBase = src.resolve("java.base");
        Path classes = base.resolve("classes");
        Path classesJavaBase = classes.resolve("java.base");

        Files.createDirectories(classesJavaBase);

        Path srcTest = src.resolve("test");
        Path classesTest = classes.resolve("test");

        Files.createDirectories(classesTest);

        for (EssentialAPI essential : EssentialAPI.values()) {
            tb.writeJavaFiles(srcJavaBase,
                              """
                              package java.lang;
                              public class Extra {
                                  @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.${preview}
                                                               ${essential})
                                  public static void test() { }
                              }
                              """.replace("${preview}", PreviewFeature.Feature.values()[0].name())
                                 .replace("${essential}", essential.code()));

            new JavacTask(tb)
                    .outdir(classesJavaBase)
                    .options("--patch-module", "java.base=" + srcJavaBase.toString())
                    .files(findJavaFiles(srcJavaBase))
                    .run()
                    .writeAll();

            for (Preview preview : Preview.values()) {
                for (Lint lint : Lint.values()) {
                    for (Suppress suppress : Suppress.values()) {
                        tb.writeJavaFiles(srcTest,
                                          """
                                          package test;
                                          public class Test {
                                              ${suppress}
                                              public void test() {
                                                  Extra.test();
                                              }
                                          }
                                          """.replace("${suppress}", suppress.code()));

                        List<String> options = new ArrayList<>();

                        options.add("-XDrawDiagnostics");
                        options.add("--patch-module");
                        options.add("java.base=" + classesJavaBase.toString());
                        options.add("-source");
                        options.add(String.valueOf(Runtime.version().feature()));

                        if (preview.opt() != null) {
                            options.add(preview.opt());
                        }

                        if (lint.opt() != null) {
                            options.add(lint.opt());
                        }
                        List<String> output;
                        List<String> expected;
                        Task.Expect expect;

                        if (essential == EssentialAPI.YES) {
                            if (preview == Preview.YES) {
                                if (lint == Lint.ENABLE_PREVIEW) {
                                    expected = List.of("Test.java:5:14: compiler.warn.is.preview: test()",
                                                       "1 warning");
                                } else {
                                    expected = List.of("- compiler.note.preview.filename: Test.java",
                                                       "- compiler.note.preview.recompile");
                                }
                                expect = Task.Expect.SUCCESS;
                            } else {
                                expected = List.of("Test.java:5:14: compiler.err.is.preview: test()",
                                                   "1 error");
                                expect = Task.Expect.FAIL;
                            }
                        } else {
                            if (suppress == Suppress.YES) {
                                expected = List.of("");
                            } else if ((preview == Preview.YES && (lint == Lint.NONE || lint == Lint.DISABLE_PREVIEW)) ||
                                       (preview == Preview.NO && lint == Lint.DISABLE_PREVIEW)) {
                                expected = List.of("- compiler.note.preview.filename: Test.java",
                                                   "- compiler.note.preview.recompile");
                            } else {
                                expected = List.of("Test.java:5:14: compiler.warn.is.preview: test()",
                                                   "1 warning");
                            }
                            expect = Task.Expect.SUCCESS;
                        }

                        output = new JavacTask(tb)
                                .outdir(classesTest)
                                .options(options)
                                .files(findJavaFiles(srcTest))
                                .run(expect)
                                .writeAll()
                                .getOutputLines(Task.OutputKind.DIRECT);

                        if (!expected.equals(output)) {
                            throw new IllegalStateException("Unexpected output for " + essential + ", " + preview + ", " + lint + ", " + suppress + ": " + output);
                        }
                    }
                }
            }
        }
    }

    public enum EssentialAPI {
        YES(", essentialAPI=true"),
        NO(", essentialAPI=false");

        private final String code;

        private EssentialAPI(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public enum Preview {
        YES("--enable-preview"),
        NO(null);

        private final String opt;

        private Preview(String opt) {
            this.opt = opt;
        }

        public String opt() {
            return opt;
        }
    }

    public enum Lint {
        NONE(null),
        ENABLE_PREVIEW("-Xlint:preview"),
        DISABLE_PREVIEW("-Xlint:-preview");

        private final String opt;

        private Lint(String opt) {
            this.opt = opt;
        }

        public String opt() {
            return opt;
        }
    }

    public enum Suppress {
        YES("@SuppressWarnings(\"preview\")"),
        NO("");

        private final String code;

        private Suppress(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
