/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Basic functional test of Optional
 * @author Mike Duigou
 * @run testng Basic
 */

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

import static org.testng.Assert.*;
import org.testng.annotations.Test;


public class Basic {

    /**
     * Asserts that the runnable throws an exception of the given type.
     * Fails if no exception is thrown, or if an exception of the wrong
     * type is thrown. This is used instead of @Test(expectedExceptions)
     * because that can be applied only at the granularity of a test.
     */
    void assertThrows(Runnable r, Class<? extends Exception> clazz) {
        try {
            r.run();
            fail();
        } catch (Exception ex) {
            assertTrue(clazz.isInstance(ex));
        }
    }

    /**
     * Checks a block of assertions over an empty Optional.
     */
    void checkEmpty(Optional<String> empty) {
        assertTrue(empty.equals(Optional.empty()));
        assertTrue(Optional.empty().equals(empty));
        assertFalse(empty.equals(Optional.of("")));
        assertFalse(Optional.of("").equals(empty));
        assertFalse(empty.isPresent());
        assertEquals(empty.hashCode(), 0);
        assertEquals(empty.orElse("x"), "x");
        assertEquals(empty.orElseGet(() -> "y"), "y");

        assertThrows(() -> empty.get(), NoSuchElementException.class);
        assertThrows(() -> empty.orElseThrow(), NoSuchElementException.class);
        assertThrows(() -> empty.orElseThrow(ObscureException::new), ObscureException.class);

        var b = new AtomicBoolean();
        empty.ifPresent(s -> b.set(true));
        assertFalse(b.get());

        var b1 = new AtomicBoolean(false);
        var b2 = new AtomicBoolean(false);
        empty.ifPresentOrElse(s -> b1.set(true), () -> b2.set(true));
        assertFalse(b1.get());
        assertTrue(b2.get());
    }

    /**
     * Checks a block of assertions over an Optional that is expected to
     * have a particular value present.
     */
    void checkPresent(Optional<String> opt, String expected) {
        assertFalse(opt.equals(Optional.empty()));
        assertFalse(Optional.empty().equals(opt));
        assertTrue(opt.equals(Optional.of(expected)));
        assertTrue(Optional.of(expected).equals(opt));
        assertFalse(opt.equals(Optional.of("unexpected")));
        assertFalse(Optional.of("unexpected").equals(opt));
        assertTrue(opt.isPresent());
        assertEquals(opt.hashCode(), expected.hashCode());
        assertEquals(opt.orElse("unexpected"), expected);
        assertEquals(opt.orElseGet(() -> "unexpected"), expected);

        assertEquals(opt.get(), expected);
        assertEquals(opt.orElseThrow(), expected);
        assertEquals(opt.orElseThrow(ObscureException::new), expected);

        var b = new AtomicBoolean(false);
        opt.ifPresent(s -> b.set(true));
        assertTrue(b.get());

        var b1 = new AtomicBoolean(false);
        var b2 = new AtomicBoolean(false);
        opt.ifPresentOrElse(s -> b1.set(true), () -> b2.set(true));
        assertTrue(b1.get());
        assertFalse(b2.get());
    }

    @Test
    public void testEmpty() {
        checkEmpty(Optional.empty());
    }

    @Test
    public void testOfNull() {
        assertThrows(() -> Optional.of(null), NullPointerException.class);
    }

    @Test
    public void testOfPresent() {
        checkPresent(Optional.of("xyzzy"), "xyzzy");
    }

    @Test
    public void testOfNullableNull() {
        checkEmpty(Optional.ofNullable(null));
    }

    @Test
    public void testOfNullablePresent() {
        checkPresent(Optional.of("xyzzy"), "xyzzy");
    }

    @Test
    public void testFilterEmpty() {
        checkEmpty(Optional.<String>empty().filter(s -> { fail(); return true; }));
    }

    @Test
    public void testFilterFalse() {
        checkEmpty(Optional.of("xyzzy").filter(s -> s.equals("plugh")));
    }

    @Test
    public void testFilterTrue() {
        checkPresent(Optional.of("xyzzy").filter(s -> s.equals("xyzzy")), "xyzzy");
    }

    @Test
    public void testMapEmpty() {
        checkEmpty(Optional.empty().map(s -> { fail(); return ""; }));
    }

    @Test
    public void testMapPresent() {
        checkPresent(Optional.of("xyzzy").map(s -> s.replace("xyzzy", "plugh")), "plugh");
    }

    @Test
    public void testFlatMapEmpty() {
        checkEmpty(Optional.empty().flatMap(s -> { fail(); return Optional.of(""); }));
    }

    @Test
    public void testFlatMapPresentReturnEmpty() {
        checkEmpty(Optional.of("xyzzy")
                           .flatMap(s -> { assertEquals(s, "xyzzy"); return Optional.empty(); }));
    }

    @Test
    public void testFlatMapPresentReturnPresent() {
        checkPresent(Optional.of("xyzzy")
                             .flatMap(s -> { assertEquals(s, "xyzzy"); return Optional.of("plugh"); }),
                     "plugh");
    }

    @Test
    public void testOrEmptyEmpty() {
        checkEmpty(Optional.<String>empty().or(() -> Optional.empty()));
    }

    @Test
    public void testOrEmptyPresent() {
        checkPresent(Optional.<String>empty().or(() -> Optional.of("plugh")), "plugh");
    }

    @Test
    public void testOrPresentDontCare() {
        checkPresent(Optional.of("xyzzy").or(() -> { fail(); return Optional.of("plugh"); }), "xyzzy");
    }

    @Test
    public void testStreamEmpty() {
        assertEquals(Optional.empty().stream().collect(toList()), List.of());
    }

    @Test
    public void testStreamPresent() {
        assertEquals(Optional.of("xyzzy").stream().collect(toList()), List.of("xyzzy"));
    }

    // ===== old tests =====

    @Test(groups = "unit")
    public void testOldEmpty() {
        Optional<Boolean> empty = Optional.empty();
        Optional<String> presentEmptyString = Optional.of("");
        Optional<Boolean> present = Optional.of(Boolean.TRUE);

        // empty
        assertTrue(empty.equals(empty));
        assertTrue(empty.equals(Optional.empty()));
        assertTrue(!empty.equals(present));
        assertTrue(0 == empty.hashCode());
        assertTrue(!empty.toString().isEmpty());
        assertTrue(!empty.toString().equals(presentEmptyString.toString()));
        assertTrue(!empty.isPresent());

        empty.ifPresent(v -> fail());

        AtomicBoolean emptyCheck = new AtomicBoolean();
        empty.ifPresentOrElse(v -> fail(), () -> emptyCheck.set(true));
        assertTrue(emptyCheck.get());

        try {
            empty.ifPresentOrElse(v -> fail(), () -> { throw new ObscureException(); });
            fail();
        } catch (ObscureException expected) {
        } catch (AssertionError e) {
            throw e;
        } catch (Throwable t) {
            fail();
        }

        assertSame(null, empty.orElse(null));
        RuntimeException orElse = new RuntimeException() { };
        assertSame(Boolean.FALSE, empty.orElse(Boolean.FALSE));
        assertSame(null, empty.orElseGet(() -> null));
        assertSame(Boolean.FALSE, empty.orElseGet(() -> Boolean.FALSE));
    }

    @Test(groups = "unit")
    public void testIfPresentAndOrElseAndNull() {
        Optional<Boolean> empty = Optional.empty();
        Optional<Boolean> present = Optional.of(Boolean.TRUE);

        // No NPE
        present.ifPresentOrElse(v -> {}, null);
        empty.ifPresent(null);
        empty.ifPresentOrElse(null, () -> {});

        // NPE
        try {
            present.ifPresent(null);
            fail();
        } catch (NullPointerException ex) {}
        try {
            present.ifPresentOrElse(null, () -> {});
            fail();
        } catch (NullPointerException ex) {}
        try {
            empty.ifPresentOrElse(v -> {}, null);
            fail();
        } catch (NullPointerException ex) {}
    }

    @Test(expectedExceptions=NoSuchElementException.class)
    public void testEmptyGet() {
        Optional<Boolean> empty = Optional.empty();

        Boolean got = empty.get();
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void testEmptyOrElseGetNull() {
        Optional<Boolean> empty = Optional.empty();

        Boolean got = empty.orElseGet(null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void testEmptyOrElseThrowNull() throws Throwable {
        Optional<Boolean> empty = Optional.empty();

        Boolean got = empty.orElseThrow(null);
    }

    @Test(expectedExceptions=ObscureException.class)
    public void testEmptyOrElseThrow() throws Exception {
        Optional<Boolean> empty = Optional.empty();

        Boolean got = empty.orElseThrow(ObscureException::new);
    }

    @Test(expectedExceptions=NoSuchElementException.class)
    public void testEmptyOrElseThrowNoArg() throws Exception {
        Optional<Boolean> empty = Optional.empty();

        Boolean got = empty.orElseThrow();
    }

    @Test(groups = "unit")
    public void testPresent() {
        Optional<Boolean> empty = Optional.empty();
        Optional<String> presentEmptyString = Optional.of("");
        Optional<Boolean> present = Optional.of(Boolean.TRUE);

        // present
        assertTrue(present.equals(present));
        assertTrue(present.equals(Optional.of(Boolean.TRUE)));
        assertTrue(!present.equals(empty));
        assertTrue(Boolean.TRUE.hashCode() == present.hashCode());
        assertTrue(!present.toString().isEmpty());
        assertTrue(!present.toString().equals(presentEmptyString.toString()));
        assertTrue(-1 != present.toString().indexOf(Boolean.TRUE.toString()));
        assertSame(Boolean.TRUE, present.get());
        assertSame(Boolean.TRUE, present.orElseThrow());

        AtomicBoolean presentCheck = new AtomicBoolean();
        present.ifPresent(v -> presentCheck.set(true));
        assertTrue(presentCheck.get());
        presentCheck.set(false);
        present.ifPresentOrElse(v -> presentCheck.set(true), () -> fail());
        assertTrue(presentCheck.get());

        try {
            present.ifPresent(v -> { throw new ObscureException(); });
            fail();
        } catch (ObscureException expected) {
        } catch (AssertionError e) {
            throw e;
        } catch (Throwable t) {
            fail();
        }
        try {
            present.ifPresentOrElse(v -> { throw new ObscureException(); }, () -> fail());
            fail();
        } catch (ObscureException expected) {
        } catch (AssertionError e) {
            throw e;
        } catch (Throwable t) {
            fail();
        }

        assertSame(Boolean.TRUE, present.orElse(null));
        assertSame(Boolean.TRUE, present.orElse(Boolean.FALSE));
        assertSame(Boolean.TRUE, present.orElseGet(null));
        assertSame(Boolean.TRUE, present.orElseGet(() -> null));
        assertSame(Boolean.TRUE, present.orElseGet(() -> Boolean.FALSE));
        assertSame(Boolean.TRUE, present.<RuntimeException>orElseThrow(null));
        assertSame(Boolean.TRUE, present.<RuntimeException>orElseThrow(ObscureException::new));
    }

    @Test(groups = "unit")
    public void testOfNullable() {
        Optional<String> instance = Optional.ofNullable(null);
        assertFalse(instance.isPresent());

        instance = Optional.ofNullable("Duke");
        assertTrue(instance.isPresent());
        assertEquals(instance.get(), "Duke");
        assertEquals(instance.orElseThrow(), "Duke");
    }

    @Test(groups = "unit")
    public void testFilter() {
        // Null mapper function
        Optional<String> empty = Optional.empty();
        Optional<String> duke = Optional.of("Duke");

        try {
            Optional<String> result = empty.filter(null);
            fail("Should throw NPE on null mapping function");
        } catch (NullPointerException npe) {
            // expected
        }

        Optional<String> result = empty.filter(String::isEmpty);
        assertFalse(result.isPresent());

        result = duke.filter(String::isEmpty);
        assertFalse(result.isPresent());
        result = duke.filter(s -> s.startsWith("D"));
        assertTrue(result.isPresent());
        assertEquals(result.get(), "Duke");
        assertEquals(result.orElseThrow(), "Duke");

        Optional<String> emptyString = Optional.of("");
        result = emptyString.filter(String::isEmpty);
        assertTrue(result.isPresent());
        assertEquals(result.get(), "");
        assertEquals(result.orElseThrow(), "");
    }

    @Test(groups = "unit")
    public void testMap() {
        Optional<String> empty = Optional.empty();
        Optional<String> duke = Optional.of("Duke");

        // Null mapper function
        try {
            Optional<Boolean> b = empty.map(null);
            fail("Should throw NPE on null mapping function");
        } catch (NullPointerException npe) {
            // expected
        }

        // Map an empty value
        Optional<Boolean> b = empty.map(String::isEmpty);
        assertFalse(b.isPresent());

        // Map into null
        b = empty.map(n -> null);
        assertFalse(b.isPresent());
        b = duke.map(s -> null);
        assertFalse(b.isPresent());

        // Map to value
        Optional<Integer> l = duke.map(String::length);
        assertEquals(l.get().intValue(), 4);
    }

    @Test(groups = "unit")
    public void testFlatMap() {
        Optional<String> empty = Optional.empty();
        Optional<String> duke = Optional.of("Duke");

        // Null mapper function
        try {
            Optional<Boolean> b = empty.flatMap(null);
            fail("Should throw NPE on null mapping function");
        } catch (NullPointerException npe) {
            // expected
        }

        // Map into null
        try {
            Optional<Boolean> b = duke.flatMap(s -> null);
            fail("Should throw NPE when mapper return null");
        } catch (NullPointerException npe) {
            // expected
        }

        // Empty won't invoke mapper function
        try {
            Optional<Boolean> b = empty.flatMap(s -> null);
            assertFalse(b.isPresent());
        } catch (NullPointerException npe) {
            fail("Mapper function should not be invoked");
        }

        // Map an empty value
        Optional<Integer> l = empty.flatMap(s -> Optional.of(s.length()));
        assertFalse(l.isPresent());

        // Map to value
        Optional<Integer> fixture = Optional.of(Integer.MAX_VALUE);
        l = duke.flatMap(s -> Optional.of(s.length()));
        assertTrue(l.isPresent());
        assertEquals(l.get().intValue(), 4);
        assertEquals(l.orElseThrow().intValue(), 4);

        // Verify same instance
        l = duke.flatMap(s -> fixture);
        assertSame(l, fixture);
    }

    @Test(groups = "unit")
    public void testOr() {
        Optional<String> empty = Optional.empty();
        Optional<String> duke = Optional.of("Duke");

        // Null supplier
        try {
            Optional<String> b = empty.or(null);
            fail("Should throw NPE on null supplier");
        } catch (NullPointerException npe) {
            // expected
        }

        // Supply null
        try {
            Optional<String> b = empty.or(() -> null);
            fail("Should throw NPE when supplier returns null");
        } catch (NullPointerException npe) {
            // expected
        }

        // Non-empty won't invoke supplier
        try {
            Optional<String> b = duke.or(() -> null);
            assertTrue(b.isPresent());
        } catch (NullPointerException npe) {
            fail("Supplier should not be invoked");
        }

        // Supply for empty
        Optional<String> suppliedDuke = empty.or(() -> duke);
        assertTrue(suppliedDuke.isPresent());
        assertSame(suppliedDuke, duke);

        // Supply for non-empty
        Optional<String> actualDuke = duke.or(() -> Optional.of("Other Duke"));
        assertTrue(actualDuke.isPresent());
        assertSame(actualDuke, duke);
    }

    @Test(groups = "unit")
    public void testStream() {
        {
            Stream<String> s = Optional.<String>empty().stream();
            assertFalse(s.isParallel());

            Object[] es = s.toArray();
            assertEquals(es.length, 0);
        }

        {
            Stream<String> s = Optional.of("Duke").stream();
            assertFalse(s.isParallel());

            String[] es = s.toArray(String[]::new);
            assertEquals(es.length, 1);
            assertEquals(es[0], "Duke");
        }
    }

    private static class ObscureException extends RuntimeException {

    }
}
