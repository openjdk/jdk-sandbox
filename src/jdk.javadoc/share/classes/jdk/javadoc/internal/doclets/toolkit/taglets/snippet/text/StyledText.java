/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Function;

/*
 * Mutable text composed of individually styleable characters.
 *
 * Operations that take both start and end indices work as follows. Start is
 * inclusive and end is exclusive; which means the affected index i satisfies this:
 * start <= i && i < end.
 *
 * //FIXME WHY newlines are also included and there's no such thing as a line or a paragraph?
 */
public interface StyledText {

    int length();

    Style styleAt(int index);

    char charAt(int index);

    void restyle(int start, int end, Function<? super Style, ? extends Style> restylingFunction);

    /*
     * A multi-purpose operation that can be used to replace, insert or delete
     * text. The effect on a text is as if [start, end) were deleted and
     * then plaintext inserted at start.
     */
    void replace(int start, int end, Style s, String plaintext);

    // FIXME: a low-level operation that allows to avoids creating a string from a char[]
    default void replace(int start, int end, Style s, char[] str, int offset, int len) {
        replace(start, end, s, new String(str, offset, len));
    }

    // "Subtext" has bad connotation.
    StyledText subText(int start, int end);

    StyledText getBookmark(String name);

    void setBookmark(String name, int start, int end);

    CharSequence asCharSequence();

    /*
     * Selects a text portion to keep track of it; it will be consistently updated.
     * Once not needed, the scope is disposed automatically.
     */
    Scope select(int start, int end);

    // Aims to efficiently provide text (in bulk) for consumption; there's NO
    // guarantee as to a CharSequence is the longest possible for this style;
    // consumers should concatenate as required.
    /*
     * The text always calls consumer at least once; even if the text is empty.
     */
    default void consumeBy(StyledTextConsumer consumer) {
        CharSequence seq = asCharSequence();
        for (int i = 0; i < length(); i++) {
            consumer.consume(seq, i, i + 1, styleAt(i));
        }
    }
}
