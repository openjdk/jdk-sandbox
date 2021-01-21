/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8201533
 * @library /tools/lib ../../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.* toolbox.ToolBox toolbox.ModuleBuilder builder.ClassBuilder
 * @run main TestSnippetTag
 */

import builder.ClassBuilder;
import builder.ClassBuilder.MethodBuilder;
import javadoc.tester.JavadocTester;
import toolbox.ModuleBuilder;
import toolbox.ToolBox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Map.entry;

// FIXME
//   0. Add tests for snippets in all types of elements: e.g., fields
//      and constructors (i.e. not only methods.)
//   1. Add tests for snippets in doc-files/*.html
//     a. Make sure that both inline and external snippets work as expected
//        and that inline snippets allow "*/" sequence
//   2. Add tests for bad tag syntax
//   3. Add tests for good tag syntax (e.g. attributes separated by newlines)
//   4. Add tests for nested structure under "snippet-files/"
public class TestSnippetTag extends JavadocTester {

    private final ToolBox tb = new ToolBox();

    public static void main(String... args) throws Exception {
        new TestSnippetTag().runTests(m -> new Object[]{Paths.get(m.getName())});
    }

    private TestSnippetTag() { }

    @Test
    public void testInline(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class")
                // Basic
                .addMembers(
                        MethodBuilder
                                .parse("public void case10() { }")
                                .setComments("""
                                                     {@snippet :
                                                         Hello, Snippet!
                                                     }
                                                     """))
                // Leading whitespace before `:`
                .addMembers(
                        MethodBuilder
                                .parse("public void case20() { }")
                                .setComments("""
                                                     {@snippet       :
                                                         Hello, Snippet!
                                                     }
                                                     """))
                // Trailing whitespace after `:`
                .addMembers(
                        MethodBuilder
                                .parse("public void case30() { }")
                                .setComments("""
                                                     {@snippet :      \s
                                                         Hello, Snippet!
                                                     }
                                                     """))
                // Attributes do not interfere with body
                .addMembers(
                        MethodBuilder
                                .parse("public void case31() { }")
                                .setComments("""
                                                     {@snippet  attr1="val1"    :
                                                         Hello, Snippet!
                                                     }
                                                     """))
                // Multi-line
                .addMembers(
                        MethodBuilder
                                .parse("public void case40() { }")
                                .setComments("""
                                                     {@snippet :
                                                         Hello
                                                         ,
                                                          Snippet!
                                                     }
                                                     """))
                // Leading empty line
                .addMembers(
                        MethodBuilder
                                .parse("public void case50() { }")
                                .setComments("""
                                                     {@snippet :

                                                         Hello
                                                         ,
                                                          Snippet!
                                                     }
                                                     """))
                // Trailing empty line
                .addMembers(
                        MethodBuilder
                                .parse("public void case60() { }")
                                .setComments("""
                                                     {@snippet :
                                                         Hello
                                                         ,
                                                          Snippet!

                                                     }
                                                     """))
                // Controlling indent with `}`
                .addMembers(
                        MethodBuilder
                                .parse("public void case70() { }")
                                .setComments("""
                                                     {@snippet :
                                                         Hello
                                                         ,
                                                          Snippet!
                                                         }
                                                     """))
                // No trailing newline before `}`
                .addMembers(
                        MethodBuilder
                                .parse("public void case80() { }")
                                .setComments("""
                                                     {@snippet :
                                                         Hello
                                                         ,
                                                          Snippet!}
                                                     """))
// FIXME: stripIndent used in implementation removes trailing whitespace too
//
//                // Trailing space is preserved
//                .addMembers(
//                        MethodBuilder
//                                .parse("public void case90() { }")
//                                .setComments("""
//                                                     {@snippet :
//                                                         Hello
//                                                         ,    \s
//                                                          Snippet!
//                                                     }
//                                                     """))
                // Escape sequences of Text Blocks and string literals are not interpreted:
                .addMembers(
                        MethodBuilder
                                .parse("public void case100() { }")
                                .setComments("""
                                                     {@snippet :
                                                         \\b\\t\\n\\f\\r\\"\\'\\\
                                                         Hello\\
                                                         ,\\s
                                                          Snippet!
                                                     }
                                                     """))
                // HTML is not interpreted
                .addMembers(
                        MethodBuilder
                                .parse("public void case110() { }")
                                .setComments("""
                                                     {@snippet :
                                                         </pre>
                                                             <!-- comment -->
                                                         <b>&trade;</b> &#8230; " '
                                                     }
                                                     """))
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.OK);

        checkOrder("pkg/A.html",
                   """
                           <span class="element-name">case10</span>()</div>
                           <div class="block">
                           <pre class="snippet">
                               Hello, Snippet!
                           </pre>
                           </div>""");

        checkOrder("pkg/A.html",
                   """
                           <span class="element-name">case20</span>()</div>
                           <div class="block">
                           <pre class="snippet">
                               Hello, Snippet!
                           </pre>
                           </div>""");

        checkOrder("pkg/A.html",
                   """
                           <span class="element-name">case30</span>()</div>
                           <div class="block">
                           <pre class="snippet">
                               Hello, Snippet!
                           </pre>
                           </div>""");

        checkOrder("pkg/A.html",
                   """
                           <span class="element-name">case31</span>()</div>
                           <div class="block">
                           <pre class="snippet">
                               Hello, Snippet!
                           </pre>
                           </div>""");

        checkOrder("pkg/A.html",
                   """
                           <span class="element-name">case40</span>()</div>
                           <div class="block">
                           <pre class="snippet">
                               Hello
                               ,
                                Snippet!
                           </pre>
                           </div>""");

        checkOrder("pkg/A.html",
                   """
                           <span class="element-name">case50</span>()</div>
                           <div class="block">
                           <pre class="snippet">

                               Hello
                               ,
                                Snippet!
                           </pre>
                           </div>""");

        checkOrder("pkg/A.html",
                   """
                           <span class="element-name">case60</span>()</div>
                           <div class="block">
                           <pre class="snippet">
                               Hello
                               ,
                                Snippet!

                           </pre>
                           </div>""");

        checkOrder("pkg/A.html",
                   """
                           <span class="element-name">case70</span>()</div>
                           <div class="block">
                           <pre class="snippet">
                           Hello
                           ,
                            Snippet!
                           </pre>
                           </div>""");

        checkOrder("pkg/A.html",
                   """
                           <span class="element-name">case80</span>()</div>
                           <div class="block">
                           <pre class="snippet">
                           Hello
                           ,
                            Snippet!</pre>
                           </div>""");

//        checkOrder("pkg/A.html",
//                   """
//                           <span class="element-name">case90</span>()</div>
//                           <div class="block">
//                           <pre class="snippet">
//                               Hello
//                               ,
//                                Snippet!
//                           </pre>
//                           </div>""");

        checkOrder("pkg/A.html",
                   """
                           <span class="element-name">case100</span>()</div>
                           <div class="block">
                           <pre class="snippet">
                               \\b\\t\\n\\f\\r\\"\\'\\    Hello\\
                               ,\\s
                                Snippet!
                           </pre>
                           </div>""");

        checkOrder("pkg/A.html",
                   """
                           <span class="element-name">case110</span>()</div>
                           <div class="block">
                           <pre class="snippet">
                               &lt;/pre&gt;
                                   &lt;!-- comment --&gt;
                               &lt;b&gt;&amp;trade;&lt;/b&gt; &amp;#8230; " '
                           </pre>
                           </div>""");
    }

    @Test
    public void testExternalFile(Path base) throws Exception {

        // Maps an input to a function that yields an expected output
        final Map<String, Function<String, String>> testCases = Map.of(
                """
                        Hello, Snippet!
                        """, Function.identity(),
                """
                            Hello, Snippet!
                        """, Function.identity(),
                """
                            Hello
                            ,
                             Snippet!
                        """, Function.identity(),
                """
                                            
                            Hello
                            ,
                             Snippet!
                        """, Function.identity(),
                """
                            Hello
                            ,
                             Snippet!

                        """, Function.identity(),
                """
                            Hello
                            ,        \s
                             Snippet!
                        """, Function.identity(),
                """
                        Hello
                        ,
                         Snippet!""", Function.identity(),
                """
                            \\b\\t\\n\\f\\r\\"\\'\\\
                            Hello\\
                            ,\\s
                             Snippet!
                        """, Function.identity(),
                """
                            </pre>
                                <!-- comment -->
                            <b>&trade;</b> &#8230; " '
                        """, s ->
                        """
                                    &lt;/pre&gt;
                                        &lt;!-- comment --&gt;
                                    &lt;b&gt;&amp;trade;&lt;/b&gt; &amp;#8230; " '
                                """,
                """
                            &lt;/pre&gt;
                                &lt;!-- comment --&gt;
                            &lt;b&gt;&amp;trade;&lt;/b&gt; &amp;#8230; " '
                        """, s -> s.replaceAll("&", "&amp;")
        );

        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        ClassBuilder classBuilder = new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class");

        // Indices are mapped to corresponding inputs not to depend on iteration order of `testCase`
        Map<Integer, String> inputs = new LinkedHashMap<>();

        // I would use a single-threaded counter if we had one.
        // Using an object rather than a primitive variable (e.g. `int id`) allows to utilize forEach
        AtomicInteger counter = new AtomicInteger();

        testCases.keySet().forEach(input -> {
            int id = counter.incrementAndGet();
            classBuilder
                    .addMembers(
                            MethodBuilder
                                    .parse("public void case%s() { }".formatted(id))
                                    .setComments("""
                                                         {@snippet file="%s.txt"}
                                                         """.formatted(id)));
            addSnippetFile(srcDir, "pkg", "%s.txt".formatted(id), input);
            inputs.put(id, input);
        });

        classBuilder.write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.OK);

        inputs.forEach((index, input) -> {
            String expectedOutput = testCases.get(input).apply(input);
            checkOrder("pkg/A.html",
                       """
                               <span class="element-name">case%s</span>()</div>
                               <div class="block">
                               <pre class="snippet">
                               %s</pre>
                               </div>""".formatted(index, expectedOutput));
        });
    }

    // FIXME
    //   Explore the toolbox.ToolBox.writeFile and toolbox.ToolBox.writeJavaFiles methods:
    //   see if any of them could be used instead of this one
    private void addSnippetFile(Path srcDir, String packageName, String fileName, String content) throws UncheckedIOException {
        String[] components = packageName.split("\\.");
        Path snippetFiles = Path.of(components[0], Arrays.copyOfRange(components, 1, components.length)).resolve("snippet-files");
        try {
            Path p = Files.createDirectories(srcDir.resolve(snippetFiles));
            Files.writeString(p.resolve(fileName), content, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void testExternalFileNotFound(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        var fileName = "text.txt";

        new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class")
                .addMembers(
                        MethodBuilder
                                .parse("public void test() { }")
                                .setComments("""
                                                     {@snippet file="%s"}
                                                     """.formatted(fileName)))
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.ERROR);

        checkOutput(Output.OUT, true,
                    """
                            A.java:4: error - File not found: %s""".formatted(fileName));
    }

    @Test // FIXME perhaps this could be unified with testExternalFile
    public void testExternalFileModuleSourcePath(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        var fileName = "snippet.txt";

        String MODULE_NAME = "mdl1";
        String PACKAGE_NAME = "pkg1.pkg2";

        Path moduleDir = new ModuleBuilder(tb, MODULE_NAME)
                .exports(PACKAGE_NAME)
                .write(srcDir);

        new ClassBuilder(tb, String.join(".", PACKAGE_NAME, "A"))
                .setModifiers("public", "class")
                .addMembers(
                        MethodBuilder
                                .parse("public void test() { }")
                                .setComments(""" 
                                                     {@snippet file="%s"}
                                                     """.formatted(fileName)))
                .write(moduleDir);

        addSnippetFile(moduleDir, PACKAGE_NAME, fileName, "content");

        javadoc("-d", outDir.toString(),
                "--module-source-path", srcDir.toString(),
                "--module", MODULE_NAME);

        checkExit(Exit.OK);
    }

    @Test // FIXME perhaps this could be unified with testExternalFileNotFound
    public void testExternalFileNotFoundModuleSourcePath(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        var fileName = "text.txt";

        var MODULE_NAME = "mdl1";
        var PACKAGE_NAME = "pkg1.pkg2";

        Path moduleDir = new ModuleBuilder(tb, MODULE_NAME)
                .exports(PACKAGE_NAME)
                .write(srcDir);

        new ClassBuilder(tb, String.join(".", PACKAGE_NAME, "A"))
                .setModifiers("public", "class")
                .addMembers(
                        MethodBuilder
                                .parse("public void test() { }")
                                .setComments(""" 
                                                     {@snippet file="%s"}
                                                     """.formatted(fileName)))
                .write(moduleDir);

        javadoc("-d", outDir.toString(),
                "--module-source-path", srcDir.toString(),
                "--module", MODULE_NAME);

        checkExit(Exit.ERROR);

        checkOutput(Output.OUT, true,
                    """
                            A.java:4: error - File not found: %s""".formatted(fileName));
    }

    @Test
    public void testConflict10(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                                     {@snippet}
                                     """)
                .setModifiers("public", "class")
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.ERROR);

        checkOutput(Output.OUT, true,
                    """
                            A.java:3: error - @snippet does not specify contents""");
    }

    @Test
    public void testConflict20(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                                     {@snippet file="" class="" :
                                         Hello, Snippet!
                                     }
                                     """)
                .setModifiers("public", "class")
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.ERROR);

        // FIXME
        //   In this and all similar tests check that there are no other errors, let alone errors related to {@snippet}
        //   To achieve that, we might need to change JavadocTester (i.e. add "consume output", "check that the output is empty", etc.)

        checkOutput(Output.OUT, true,
                    """
                            A.java:3: error - @snippet specifies multiple external contents, which is ambiguous""");
    }

    @Test
    public void testConflict30(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                                     {@snippet class="" file="" :
                                         Hello, Snippet!
                                     }
                                     """)
                .setModifiers("public", "class")
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.ERROR);


        checkOutputEither(Output.OUT,
                          """
                                  A.java:3: error - @snippet specifies multiple external contents, which is ambiguous""",
                          """
                                  A.java:3: error - @snippet specifies external and inline contents, which is ambiguous""");
    }

    // FIXME: perhaps this method could be added to JavadocTester
    private void checkOutputEither(Output out, String first, String... other) {
        checking("checkOutputEither");
        String output = getOutput(out);

        Stream<String> strings = Stream.concat(Stream.of(first), Stream.of(other));
        Optional<String> any = strings.filter(output::contains).findAny();

        if (any.isPresent()) {
            passed(": following text is found:\n" + any.get());
        } else {
            failed(": nothing found");
        }
    }

    @Test
    public void testConflict40(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                                     {@snippet class="" :
                                         Hello, Snippet!
                                     }
                                     """)
                .setModifiers("public", "class")
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.ERROR);

        checkOutput(Output.OUT, true,
                    """
                            A.java:3: error - @snippet specifies external and inline contents, which is ambiguous""");
    }

    @Test
    public void testConflict50(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                                     {@snippet file="" :
                                         Hello, Snippet!
                                     }
                                     """)
                .setModifiers("public", "class")
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.ERROR);

        checkOutput(Output.OUT, true,
                    """
                            A.java:3: error - @snippet specifies external and inline contents, which is ambiguous""");
    }

    @Test
    public void testConflict60(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                                     {@snippet file="" file=""}
                                     """)
                .setModifiers("public", "class")
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.ERROR);

        checkOutput(Output.OUT, true,
                    """
                            A.java:3: error - repeated attribute: "file\"""");
    }

    @Test
    public void testConflict70(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                                     {@snippet class="" class="" }
                                     """)
                .setModifiers("public", "class")
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.ERROR);

        checkOutput(Output.OUT, true,
                    """
                            A.java:3: error - repeated attribute: "class\"""");
    }

    @Test
    public void testConflict80(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                                     {@snippet class="" class="" :
                                         Hello, Snippet!
                                     }
                                     """)
                .setModifiers("public", "class")
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.ERROR);

        checkOutputEither(Output.OUT,
                          """
                                  A.java:3: error - repeated attribute: "class\"""",
                          """
                                  A.java:3: error - @snippet specifies external and inline contents, which is ambiguous""");
    }

    @Test
    public void testConflict90(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                                     {@snippet file="" file="" :
                                         Hello, Snippet!
                                     }
                                     """)
                .setModifiers("public", "class")
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.ERROR);

        checkOutputEither(Output.OUT,
                          """
                                  A.java:3: error - repeated attribute: "file\"""",
                          """
                                  A.java:3: error - @snippet specifies external and inline contents, which is ambiguous""");
    }

    // Those are excerpts from the diagnostic messages for two different tags that sit on the same line:
    //
    //     A.java:3: error - @snippet does not specify contents
    //     A.java:3: error - @snippet does not specify contents
    //
    // FIXME: fix and uncomment this test if and when that problem with diagnostic output has been resolved
    //
    //@Test
    public void testErrorPositionResolution(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                                     {@snippet} {@snippet}
                                     """)
                .setModifiers("public", "class")
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.ERROR);

        checkOutput(Output.OUT, true,
                    """
                            A.java:3: error - @snippet does not specify contents""",
                    """
                            A.java:3: error - @snippet does not specify contents""");
    }

    @Test
    public void testRegion(Path base) throws Exception {

        // Maps an input to an expected output
        final Map<Snippet, String> testCases = Map.ofEntries(
                entry(newSnippetBuilder()
                              .body("""
                                            // snippet-region-start : here
                                            Hello
                                            ,
                                             Snippet!
                                            // snippet-region-stop : here
                                            """)
                              .region("here")
                              .build(),
                      """
                              Hello
                              ,
                               Snippet!
                              """
                )
                ,
                entry(newSnippetBuilder()
                              .body("""
                                                // snippet-region-start : here
                                                Hello
                                                ,
                                                 Snippet!
                                            // snippet-region-stop : here
                                                """)
                              .region("here")
                              .build(),
                      """
                                  Hello
                                  ,
                                   Snippet!
                              """)
                ,
                entry(newSnippetBuilder()
                              .body("""
                                                // snippet-region-start : here
                                                Hello
                                                ,
                                                 Snippet!// snippet-region-stop : here
                                            """)
                              .region("here")
                              .build(),
                      """
                              Hello
                              ,
                               Snippet!\
                              """
                )
                ,
                entry(newSnippetBuilder()
                              .body("""
                                            // snippet-region-start : there
                                            // snippet-region-stop : there

                                                // snippet-region-start : here
                                                Hello
                                                ,
                                                 Snippet!
                                                // snippet-region-stop : here
                                                   """)
                              .region("here")
                              .build(),
                      """
                              Hello
                              ,
                               Snippet!
                              """
                )
                ,
                entry(newSnippetBuilder()
                              .body("""
                                            // snippet-region-start : here
                                                Hello
                                            // snippet-region-stop : here

                                                 , Snippet!
                                            // snippet-region-stop : here
                                                """)
                              .region("here")
                              .build()
                        ,
                      """
                                  Hello
                              """
                )
                ,
                entry(newSnippetBuilder()
                              .body("""
                                            // snippet-region-start : here
                                                This is the only line you should see.
                                            // snippet-region-stop : here
                                            // snippet-region-start : hereafter
                                                You should NOT see this.
                                            // snippet-region-stop : hereafter
                                                """)
                              .region("here")
                              .build(),
                      """
                                  This is the only line you should see.
                              """
                )
                ,
                entry(newSnippetBuilder()
                              .body("""
                                            // snippet-region-start : here
                                                You should NOT see this.
                                            // snippet-region-stop : here
                                            // snippet-region-start : hereafter
                                                This is the only line you should see.
                                            // snippet-region-stop : hereafter
                                                """)
                              .region("hereafter")
                              .build(),
                      """
                                  This is the only line you should see.
                              """
                )
                ,
                entry(newSnippetBuilder()
                              .body("""
                                            // snippet-region-start : beforehand
                                                You should NOT see this.
                                            // snippet-region-stop : beforehand
                                            // snippet-region-start : before
                                                This is the only line you should see.
                                            // snippet-region-stop : before
                                                """)
                              .region("before")
                              .build(),
                      """
                                  This is the only line you should see.
                              """
                )
                ,
                entry(newSnippetBuilder()
                              .body("""
                                            // snippet-region-start : beforehand
                                                This is the only line you should see.
                                            // snippet-region-stop : beforehand
                                            // snippet-region-start : before
                                                You should NOT see this.
                                            // snippet-region-stop : before
                                                """)
                              .region("beforehand")
                              .build(),
                      """
                                  This is the only line you should see.
                              """
                )
        );

        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        ClassBuilder classBuilder = new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class");

        // Indices are mapped to corresponding inputs not to depend on iteration order of `testCase`
        Map<Integer, Snippet> inputs = new LinkedHashMap<>();

        // I would use a single-threaded counter if we had one.
        // Using an object rather than a primitive variable (e.g. `int id`) allows to utilize forEach
        AtomicInteger counter = new AtomicInteger();

        testCases.keySet().forEach(input -> {
            int id = counter.incrementAndGet();
            classBuilder
                    .addMembers(
                            MethodBuilder
                                    .parse("public void case%s() { }".formatted(id))
                                    .setComments("""
                                                         {@snippet region="%s" :
                                                         %s}
                                                         """.formatted(input.region(), input.body())));
            inputs.put(id, input);
        });

        classBuilder.write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.OK);

        inputs.forEach((index, input) -> {
            String expectedOutput = testCases.get(input);
            checkOrder("pkg/A.html",
                       """
                               <span class="element-name">case%s</span>()</div>
                               <div class="block">
                               <pre class="snippet">
                               %s</pre>
                               </div>""".formatted(index, expectedOutput));
        });
    }

    @Test
    public void testComment(Path base) throws Exception {

        // Maps an input to an expected output
        final Map<Snippet, String> testCases = Map.ofEntries(
                entry(newSnippetBuilder()
                              .body("""
                                            // snippet-comment : Hello
                                            ,
                                             Snippet!""")
                              .build(),
                      """
                              Hello
                              ,
                               Snippet!"""
                )
                ,
                entry(newSnippetBuilder()
                              .body("""
                                            // snippet-comment :Hello
                                            ,
                                             Snippet!""")
                              .build(),
                      """
                              Hello
                              ,
                               Snippet!"""
                )
                ,
                entry(newSnippetBuilder()
                              .body("""
                                            // snippet-comment :  Hello
                                            ,
                                             Snippet!""")
                              .build(),
                      """
                               Hello
                              ,
                               Snippet!"""
                )
                ,
                entry(newSnippetBuilder()
                              .body("""
                                            // snippet-comment : // snippet-comment : my comment""")
                              .build(),
                      """
                              // snippet-comment : my comment"""
                )
        );

        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        ClassBuilder classBuilder = new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "class");

        // Indices are mapped to corresponding inputs not to depend on iteration order of `testCase`
        Map<Integer, Snippet> inputs = new LinkedHashMap<>();

        // I would use a single-threaded counter if we had one.
        // Using an object rather than a primitive variable (e.g. `int id`) allows to utilize forEach
        AtomicInteger counter = new AtomicInteger();

        testCases.keySet().forEach(input -> {
            int id = counter.incrementAndGet();
            classBuilder
                    .addMembers(
                            MethodBuilder
                                    .parse("public void case%s() { }".formatted(id))
                                    .setComments("""
                                                         {@snippet :
                                                         %s}
                                                         """.formatted(input.body())));
            inputs.put(id, input);
        });

        classBuilder.write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.OK);

        inputs.forEach((index, input) -> {
            String expectedOutput = testCases.get(input);
            checkOrder("pkg/A.html",
                       """
                               <span class="element-name">case%s</span>()</div>
                               <div class="block">
                               <pre class="snippet">
                               %s</pre>
                               </div>""".formatted(index, expectedOutput));
        });
    }

    private static Snippet.Builder newSnippetBuilder() {
        return new Snippet.Builder();
    }

    private static class Snippet { // TODO: use a language record when it becomes available

        private final String regionName;
        private final String body;

        private Snippet(String regionName, String body) {
            this.regionName = regionName;
            this.body = body;
        }

        public String region() {
            return regionName;
        }

        public String body() {
            return body;
        }

        static class Builder {

            private String regionName;
            private String body;

            Builder region(String name) {
                this.regionName = name;
                return this;
            }

            Builder body(String content) {
                this.body = content;
                return this;
            }

            Snippet build() {
                return new Snippet(regionName, body);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Snippet snippet = (Snippet) o;
            return Objects.equals(regionName, snippet.regionName) &&
                    Objects.equals(body, snippet.body);
        }

        @Override
        public int hashCode() {
            return Objects.hash(regionName, body);
        }

        @Override
        public String toString() {
            return "Snippet{" +
                    "region='" + regionName + '\'' +
                    ", body='" + body + '\'' +
                    '}';
        }
    }
}
