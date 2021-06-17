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

package jdk.javadoc.internal.doclets.toolkit.taglets.snippet.action;

import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.text.AnnotatedText;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Replace implements Action {

    private final Pattern pattern;
    private final String replacement;
    private final AnnotatedText<?> text;

    public Replace(String r, Pattern p, AnnotatedText<?> t) {
        this.replacement = r;
        this.pattern = p;
        this.text = t;
    }

    @Override
    public void perform() {
        // We cannot seem to iterate backwards (i.e. match from end to start).
        String s = text.asCharSequence().toString(); // fixate the sequence
        Matcher matcher = pattern.matcher(s);
        List<Replacement> replacements = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        int off = 0; // offset because of the replacements (can be negative)
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            matcher.appendReplacement(b, replacement);
            // everything from start to replacement.length() is the actual replacement

            // Until JDK-8261619 is resolved, this requires some amount of waste
            // and careful index manipulation

            String ss = b.substring(start + off);
            off = b.length() - end;

            replacements.add(new Replacement(start, end, ss));
        }
        // there's no need to call: matcher.appendTail(b);
        for (int i = replacements.size() - 1; i >= 0; i--) {
            Replacement r = replacements.get(i);
            // TODO we can use a different style, e.g. that of at (r.start - 1)
            text.subText(r.start, r.end).replace(Set.of(), r.value);
        }
    }
}
