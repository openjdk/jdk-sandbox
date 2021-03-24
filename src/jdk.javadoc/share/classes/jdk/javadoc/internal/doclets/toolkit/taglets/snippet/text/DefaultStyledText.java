/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.taglets.snippet.text;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static java.lang.Math.max;
import static java.lang.Math.min;

/*
 * This implementation optimizes usage for the case where style changes
 * infrequently as the text goes.
 */
public class DefaultStyledText implements StyledText {

    private final StringBuilder chars = new StringBuilder();
    private final EqualElementsRegions<Style> styles = new EqualElementsRegions<>();
    private final List<WeakReference<Scope>> scopes = new ArrayList<>();

    @Override
    public int length() {
        return chars.length();
    }

    @Override
    public Style styleAt(int index) {
        return styles.at(index);
    }

    @Override
    public char charAt(int index) {
        return chars.charAt(index);
    }

    @Override
    public void restyle(int start, int end, Function<? super Style, ? extends Style> restylingFunction) {
        for (int i = start; i < end; i++) {
            Style s = styles.at(i);
            styles.replace(i, restylingFunction.apply(s));
        }
    }

    @Override
    public void replace(int start, int end, Style s, String plaintext) {
        chars.replace(start, end, plaintext);
        for (int i = start; i < end; i++) {
            styles.delete(start); // note that the index is fixed
        }
        for (int i = start; i < start + plaintext.length(); i++) {
            styles.insert(i, s);
        }
        // the number of scopes is not expected to be big;
        // hence no optimizations are applied
        for (int i = 0; i < scopes.size(); i++) {
            Scope scope = scopes.get(i).get();
            if (scope == null) {
                continue;
            }
            update(start, end, plaintext.length(), scope);
        }
    }

    private void update(final int start, final int end, final int newLength, final Scope scope) {
        // TODO: split update into insert and delete to make the algorithm more clear
        assert start <= end;
        assert scope.start <= scope.end;
        assert newLength >= 0;
        if (end <= scope.start) {
            final int diff = newLength - (end - start);
            scope.start += diff;
            scope.end += diff;
        } else if (scope.end <= start ) {
            // no-op
        } else { // intersection
            final int intersection_len = min(end, scope.end) - max(start, scope.start);
            int len = scope.end - scope.start;
            if (start <= scope.start) {
                scope.start = start + newLength;
            } else if (end < scope.end) {
                len += newLength;
            }
            scope.end = scope.start + len - intersection_len;
        }
    }

    @Override
    public StyledText subText(int start, int end) {
        Objects.checkFromToIndex(start, end, length());
        return new SubText(start, end);
    }

    @Override
    public CharSequence asCharSequence() {
        return chars;
    }

    @Override
    public Scope select(int start, int end) {
        Objects.checkFromToIndex(start, end, length());
        Scope s = new Scope(this, start, end);
        scopes.add(new WeakReference<>(s));
        return s;
    }

    @Override
    public void consumeBy(StyledTextConsumer consumer) {
        // FIXME: clean this evil violation of encapsulation up
        CharSequence sequence = asCharSequence();
        for (int i = 1; i < styles.runs.size() - 1; i++) {
            consumer.consume(sequence, styles.runs.get(i).start, styles.runs.get(i + 1).start, styles.runs.get(i).e);
        }
    }

    @Override
    public String toString() {
        return "characters: %s, regions: %s, styles %s; [%s]".
                formatted(chars.length(), styles.nRegions(), styles.nElements(), super.toString());
    }

    private final class SubText implements StyledText {

        final int start, end;

        SubText(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public int length() {
            return end - start;
        }

        @Override
        public Style styleAt(int index) {
            Objects.checkIndex(index, length());
            return DefaultStyledText.this.styleAt(start + index);
        }

        @Override
        public char charAt(int index) {
            Objects.checkIndex(index, length());
            return DefaultStyledText.this.charAt(start + index);
        }

        @Override
        public void restyle(int start, int end, Function<? super Style, ? extends Style> restylingFunction) {
            Objects.checkFromToIndex(start, end, length());
            DefaultStyledText.this.restyle(this.start + start, this.start + end, restylingFunction);
        }

        @Override
        public void replace(int start, int end, Style s, String plaintext) {
            Objects.checkFromToIndex(start, end, length());
            DefaultStyledText.this.replace(this.start + start, this.start + end, s, plaintext);
        }

        @Override
        public StyledText subText(int start, int end) {
            Objects.checkFromToIndex(start, end, length());
            return DefaultStyledText.this.subText(this.start + start, this.start + end);
        }

        @Override
        public CharSequence asCharSequence() {
            return DefaultStyledText.this.asCharSequence().subSequence(start, end);
        }

        @Override
        public Scope select(int start, int end) {
            Objects.checkFromToIndex(start, end, length());
            return DefaultStyledText.this.select(this.start + start, this.start + end);
        }

        @Override
        public String toString() {
            return "[" + start + ", " + end + "] of " + DefaultStyledText.this;
        }
    }
}
