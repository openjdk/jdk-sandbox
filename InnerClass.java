/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8032869
 * @summary javap should render inner classes in a friendly way
 * @modules jdk.jdeps/com.sun.tools.javap
 */

import java.io.*;
import java.util.function.Function;
import javax.lang.model.element.ElementKind;

public class InnerClass {
/*    public static void main(String... args) throws Exception {
        new InnerClass().run();
    }

    void run() throws Exception {
        String testClasses = System.getProperty("test.classes");
        String out = javap("-v", "-classpath", testClasses, A.class.getName());

        String nl = System.getProperty("line.separator");
        out = out.replaceAll(nl, "\n");

        if (out.contains("\n\n\n"))
            error("double blank line found");

        System.out.println(out);

        expect(out,"InnerClasses:");
        expect(out, "// A=class InnerClass$A of class InnerClass");
        expect(out, "// B=class InnerClass$A$B of class InnerClass$A");
        expect(out, "// C=class InnerClass$A$C of class InnerClass$A");
        expect(out, "// D=class InnerClass$A$D of class InnerClass$A");
        expect(out, "// E=class InnerClass$A$E of class InnerClass$A");
        expect(out, "// F=class InnerClass$A$F of class InnerClass$A");
        expect(out, "// G=class InnerClass$A$G of class InnerClass$A");
        expect(out, "// H=class InnerClass$A$H of class InnerClass$A");
        expect(out, "// I=class InnerClass$A$I of class InnerClass$A");
        expect(out, "// K=class InnerClass$A$K of class InnerClass$A");
        expect(out, "// L=class InnerClass$A$L of class InnerClass$A");

        if (errors > 0)
            throw new Exception(errors + " errors found");
    }

    String javap(String... args) throws Exception {
        StringWriter sw = new StringWriter();
        int rc;
        try (PrintWriter out = new PrintWriter(sw)) {
            rc = com.sun.tools.javap.Main.run(args, out);
        }
        System.out.println(sw.toString());
        if (rc < 0)
            throw new Exception("javap exited, rc=" + rc);
        return sw.toString();
    }

    void expect(String text, String expect) {
        if (!text.contains(expect))
            error("expected text not found");
    }

    void error(String msg) {
        System.out.println("Error: " + msg);
        errors++;
    }

    int errors;
*/
    /* Simple test classes to run through javap. */
    public static class A {

        class B { }
        abstract class C { }
        final class D { }
        static final class E { }
        static class F { }
        static final class G { }
        interface H { }
        @interface I { }
        private class J { }
        protected class K { }
        public class L { }
    }
}
