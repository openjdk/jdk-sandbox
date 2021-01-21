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

import java.nio.file.Path;
import java.nio.file.Paths;

public class TestSnippetTag extends JavadocTester {

    final ToolBox tb;

    public static void main(String... args) throws Exception {
        JavadocTester tester = new TestSnippetTag();
        tester.runTests(m -> new Object[]{Paths.get(m.getName())});
    }

    TestSnippetTag() {
        tb = new ToolBox();
    }

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

        checkOutput(Output.OUT,
                    true,
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

        // FIXME: In this and all similar tests check that there are no other errors, let alone errors related to {@snippet}
        //        To achieve that, we might need to change JavadocTester (i.e. add "consume output", "check that the output is empty", etc.)

        checkOutput(Output.OUT,
                    true,
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

        checkOutput(Output.OUT,
                    true,
                    """
                            A.java:3: error - @snippet specifies multiple external contents, which is ambiguous""",
                    """
                            A.java:3: error - @snippet specifies external and inline contents, which is ambiguous""");
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

        checkOutput(Output.OUT,
                    true,
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

        checkOutput(Output.OUT,
                    true,
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

        checkOutput(Output.OUT,
                    true,
                    """
                            A.java:3: error - @snippet specifies multiple external contents, which is ambiguous""");
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

        checkOutput(Output.OUT,
                    true,
                    """
                            A.java:3: error - @snippet specifies multiple external contents, which is ambiguous""");
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

        checkOutput(Output.OUT,
                    true,
                    """
                            A.java:3: error - @snippet specifies multiple external contents, which is ambiguous""",
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

        checkOutput(Output.OUT, true,
                    """
                            A.java:3: error - @snippet specifies multiple external contents, which is ambiguous""",
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