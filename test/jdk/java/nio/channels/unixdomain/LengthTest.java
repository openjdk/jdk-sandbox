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
 * @summary Test UnixDomainSocketAddress constructor
 * @library /test/lib
 * @run testng/othervm LengthTest
 */

import org.testng.SkipException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.lang.System.out;
import static java.nio.channels.UnixDomainSocketAddress.MAXNAMELENGTH;
import static jdk.test.lib.Asserts.assertTrue;

import java.nio.channels.UnixDomainSocketAddress;
import java.nio.file.Path;

public class LengthTest {

    @BeforeTest
    public void setup() {
        out.println("MAXNAMELENGTH: " + MAXNAMELENGTH);

        if (MAXNAMELENGTH == -1) {
            throw new SkipException("Unix domain channels not supported");
        }
    }

    @DataProvider(name = "strings")
    public Object[][] strings() {
        return new Object[][]{
                {""},
                {new String(new char[100]).replaceAll("\0", "x")},
                {new String(new char[MAXNAMELENGTH]).replaceAll("\0", "x")},
                {new String(new char[MAXNAMELENGTH - 1]).replaceAll("\0", "x")},
        };
    }

    @Test(dataProvider = "strings")
    public void expectPass(String s) {
        var addr = new UnixDomainSocketAddress(s);
        assertTrue(addr.getPathName().equals(s), "getPathName.equals(s)");
        var p = Path.of(s);
        addr = new UnixDomainSocketAddress(p);
        assertTrue(addr.getPath().equals(p), "getPath.equals(p)");
    }

    @Test
    public void expectFail() {
        String s = new String(new char[MAXNAMELENGTH + 1]).replaceAll("\0", "x");
        try {
            new UnixDomainSocketAddress(s);
            throw new RuntimeException("Expected IAE");
        } catch (IllegalArgumentException iae) {
            out.println("\tCaught expected exception: " + iae);
        }
        try {
            var p = Path.of(s);
            new UnixDomainSocketAddress(p);
            throw new RuntimeException("Expected IAE");
        } catch (IllegalArgumentException iae) {
            out.println("\tCaught expected exception: " + iae);
        }
    }

    @Test
    public void expectNPE() {
        try {
            String s = null;
            new UnixDomainSocketAddress(s);
            throw new RuntimeException("Expected NPE");
        } catch (NullPointerException npe) {
            out.println("\tCaught expected exception: " + npe);
        }
        try {
            Path p = null;
            new UnixDomainSocketAddress(p);
            throw new RuntimeException("Expected NPE");
        } catch (NullPointerException npe) {
            out.println("\tCaught expected exception: " + npe);
        }
    }
}
