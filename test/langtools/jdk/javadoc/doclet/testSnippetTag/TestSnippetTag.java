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
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.* toolbox.ToolBox builder.ClassBuilder
 * @run main TestSnippetTag
 */

import builder.ClassBuilder;
import builder.ClassBuilder.MethodBuilder;
import javadoc.tester.JavadocTester;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

// FIXME
//   0. Add tests for snippets in all types of elements: e.g., fields
//      and constructors (i.e. not only methods.)
//   1. Add tests for snippets in doc-files/*.html
//     a. Make sure that both inline and external snippets work as expected
//        and that inline snippets allow "*/" sequence
//   2. Add tests for bad tag syntax
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
}