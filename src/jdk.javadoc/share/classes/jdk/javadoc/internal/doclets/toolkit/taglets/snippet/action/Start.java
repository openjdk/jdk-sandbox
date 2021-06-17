/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.taglets.snippet.action;

import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.text.AnnotatedText;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Populates bookmarks in AnnotatedText
public class Start implements Action {

    private final String name;
    private final AnnotatedText<?> text;
    private final int pos;

    public Start(String name, AnnotatedText<?> text, int pos) {
        this.name = name;
        this.text = text;
        this.pos = pos;
    }

    @Override
    public void perform() {
        stripLeadingIncidentalWhitespace(text);
        int len = text.length();
        String str = text.asCharSequence().toString();
        if (str.endsWith("\r\n")) {
            len -= 2;
        } else if (str.endsWith("\n") || str.endsWith("\r")) {
            len -= 1;
        }
        text.subText(0, len).bookmark(name);
    }

    // Unfortunately, the region cannot be simply replaced with result of
    // String.stripIndent as styles corresponding to the characters will be lost
    private void stripLeadingIncidentalWhitespace(AnnotatedText<?> text) {
        List<Replacement> replacements = findIncidentalSpace(text.asCharSequence());
        for (int i = replacements.size() - 1; i >= 0; i--) {
            Replacement r = replacements.get(i);
            // TODO we can use a different style, e.g. that of at (r.start - 1)
            text.subText(r.start, r.end).replace(Set.of(), r.value);
        }
    }

    // I do not want to re-implement error-prone stripIndent
    private List<Replacement> findIncidentalSpace(CharSequence s) {
        List<Replacement> replacements = new ArrayList<>();
        String str = s.toString();
        if (str.endsWith("\r\n")) {
            str = str.substring(0, str.length() - 2);
        } else if (str.endsWith("\n") || str.endsWith("\r")) {
            str = str.substring(0, str.length() - 1);
        }
        str = str + " ".repeat(Math.max(0, pos));
        String before = str;
        String after = str.stripIndent();
        int diff = leadingWhitespaceLength(before) - leadingWhitespaceLength(after);
        if (diff == 0) {
            return List.of();
        }
        int i = 0;
        while (i < s.length()) {
            final int start = i;
            while (i < s.length()) {
                char c = s.charAt(i++); // note unconditional increment
                if (c == '\n')  // assert newlines are only '\n'
                    break;
            }
            if (i - start >= diff) // remove leading whitespace only if it is there
                replacements.add(new Replacement(start, start + diff, ""));
        }
        return replacements;
    }

    private static int leadingWhitespaceLength(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }
}
