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

package jdk.javadoc.internal.doclets.toolkit.taglets.snippet.parser;

import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.action.Action;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.action.Replace;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.action.Restyle;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.action.Start;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.text.DefaultStyledText;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.text.Style;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.text.StyledText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/*
 * Semantics of a EOL comment; plus
 * 1. This parser treats input as plain text. This may result in markup being
 * produced from unexpected places; for example, when parsing Java text blocks:
 *
 *     String text =
 *         """
 *             // @start x
 *         """;
 *
 * false positives are possible, but false negatives are not.
 * To remediate that, perhaps a no-op trailing // @start x @end x might be added.
 *
 * 2. To allow some preexisting constructs, unknown actions in a leading position are skipped;
 * for example, "// @formatter:on" marker in IntelliJ IDEA is ignored.
 *
 * 3. This match's value can be confused for a trailing markup.
 *
 *     String x; // comment // another comment // @formatter:on // @highlight match="// @"
 *
 * Do we need escapes?
 *
 * 4. Rules for EOL are very different among formats: compare Java's // with properties' #/!
 *
 * 5. A convenience `end` ends all the things started so far.
 */
// FIXME: How to treat Form Feed? (i.e. is it vertical or horizontal whitespace?)
// FIXME: what to do with lines not covered by any markup? (i.e. in between markup)
// FIXME: all parsing errors must be localized.
public final class Parser {

    //                  v next line
    //         : action :
    //         ^
    // this line
    //
    // next-line instruction behaves as if it were specified on the next line

    // Regions
    //  * a line
    //  * a set of lines
    //  * a range of lines (a set of adjacent lines that unlocks multi-line regex)

    // label "name"
    //     Puts a label on the line. That label can be used to refer to that
    //     line in the body (a.k.a. payload) of the marked up snippet.

    // Examples of constructs sharing the same line:
    //
    //   * highlight/replace and show
    //   * highlight bold and (partially) highlight italic
    //
    // What if they start on the same line but end on different lines?
    // Syntax should be compact

    private String eolMarker;
    private Matcher markedUpLine;
    private final InstructionParser markupParser = new InstructionParser();

    // Incomplete actions waiting for their complementary @end
    private final Map<String, Instruction> unpaired = new HashMap<>();
    // List of instructions; consumed from top to bottom
    private final Queue<Instruction> instructions = new LinkedList<>();

    public Result parse(String source) throws ParseException {
        return parse("//", source);
    }

    public Result parse(String eolMarker, String source) throws ParseException {
        Objects.requireNonNull(eolMarker);
        Objects.requireNonNull(source);
        if (!Objects.equals(eolMarker, this.eolMarker)) {
            if (eolMarker.length() < 1) {
                throw new IllegalArgumentException();
            }
            for (int i = 0; i < eolMarker.length(); i++) {
                switch (eolMarker.charAt(i)) {
                    case '\f', '\n', '\r' -> throw new IllegalArgumentException();
                }
            }
            this.eolMarker = eolMarker;
            // capture the rightmost "//"
            this.markedUpLine = Pattern.compile("^(.*)(\\Q%s\\E(\\s*@\\s*\\w+.+?))$".formatted(eolMarker))
                    .matcher(""); // TODO: quote properly with Pattern.quote
        }

        instructions.clear();
        unpaired.clear();

        Queue<Action> actions = new LinkedList<>();

        StyledText text = new DefaultStyledText();
        Iterator<String> iterator = source.lines().iterator();
        boolean trailingNewline = source.endsWith("\r") || source.endsWith("\n");
        int lineStart = 0;
        List<Instruction> previousLineInstructions = new ArrayList<>();
        List<Instruction> thisLineInstructions = new ArrayList<>();
        List<Instruction> tempList = new ArrayList<>();
        while (iterator.hasNext()) {
            String rawLine = iterator.next();
            boolean addLineTerminator = iterator.hasNext() || trailingNewline;
            String line;
            markedUpLine.reset(rawLine);
            if (markedUpLine.matches()) {
                String maybeMarkup = markedUpLine.group(3);
                line = markedUpLine.group(1).stripTrailing() + (addLineTerminator ? "\n" : ""); // remove any whitespace
                List<Instruction> parsedInstructions = markupParser.parse(maybeMarkup);
                thisLineInstructions.addAll(parsedInstructions);
                for (var instructionIterator = thisLineInstructions.iterator(); instructionIterator.hasNext(); ) {
                    Instruction i = instructionIterator.next();
                    if (i.appliesToNextLine) {
                        instructionIterator.remove();
                        i.appliesToNextLine = false; // clear the flag
                        tempList.add(i);
                    } else {
                        i.position = markedUpLine.start(2); // e.g. @end that relates to the same line
                    }
                }
                if (parsedInstructions.isEmpty()) { // not a valid markup comment
                    line = rawLine + (addLineTerminator ? "\n" : "");
                }
            } else {
                line = rawLine + (addLineTerminator ? "\n" : "");
            }

            thisLineInstructions.addAll(0, previousLineInstructions); // prepend!
            previousLineInstructions.clear();
            for (Instruction i : thisLineInstructions) {
                i.start = lineStart;
                i.end = lineStart + line.length(); // this includes line terminator, if any
                processInstruction(i);
            }
            previousLineInstructions.addAll(tempList);
            tempList.clear();

            thisLineInstructions.clear();

            append(text, line);
            lineStart += line.length();
        }

        if (!previousLineInstructions.isEmpty()) {
            throw new ParseException("Instructions refer to non-existent lines");
        }

        // also report on unpaired with corresponding `end` or unknown instructions
        if (!unpaired.isEmpty()) {
            throw new ParseException("Unpaired regions: " +
                                             unpaired.values()
                                                     .stream()
                                                     .map(i -> i.regionIdentifier)
                                                     .collect(Collectors.joining(", ")));
        }

        for (var i : instructions) {

            assert nonNullValues(i.attributes()) : i.attributes();

            if (!i.name().equals("start")
                    && i.attributes().containsKey("substring")
                    && i.attributes.containsKey("regex")) {
                throw new ParseException("'substring' and 'regex' cannot be used simultaneously");
            }

            String substring = i.attributes().get("substring");
            String regex;
            if (substring != null) {
                regex = Pattern.quote(substring);
            } else if ((regex = i.attributes().get("regex")) == null) {
                regex = "(?s).+";
            }

            switch (i.name()) {
                case "link" -> {
                    String target = i.attributes().get("target");
                    if (target == null) {
                        throw new ParseException("target is absent");
                    }
                    String type = i.attributes().getOrDefault("type", "link");
                    if (!type.equals("link") && !type.equals("linkplain")) {
                        throw new ParseException("Unknown link type: '%s'".formatted(type));
                    }
                    Restyle a = new Restyle(s -> s.and(Style.link(target)),
                                            Pattern.compile(regex),
                                            text.select(i.start(), i.end()));
                    actions.add(a);
                }
                case "replace" -> {
                    String replacement = i.attributes().get("replacement");
                    if (replacement == null) {
                        throw new ParseException("replacement is absent");
                    }
                    Replace a = new Replace(replacement,
                                            Pattern.compile(regex),
                                            text.select(i.start(), i.end()));
                    actions.add(a);
                }
                case "highlight" -> {
                    String type = i.attributes().getOrDefault("type", "bold");
                    Restyle a = new Restyle(s -> s.and(Style.name(type)),
                                            Pattern.compile(regex),
                                            text.select(i.start(), i.end()));
                    actions.add(a);
                }
                case "start" -> {
                    if (i.regionIdentifier.isEmpty()) {
                        throw new ParseException("Unnamed start");
                    }
                    if (!i.attributes().isEmpty()) {
                        throw new ParseException("Unexpected attributes: " + String.join(", ", i.attributes.keySet()));
                    }
                    actions.add(new Start(i.regionIdentifier(), text.select(i.start(), i.end()), i.position));
                }
            }
        }

        return new Result(text, actions);
    }

    // A map that passes this test has the following property:
    //     a.containsKey(x) == (a.get(x) == null)
    private static boolean nonNullValues(Map<String, String> attributes) {
        for (String v : attributes.values()) {
            if (v == null) {
                return false;
            }
        }
        return true;
    }

    private void processInstruction(Instruction i) throws ParseException {
        if (!i.name().equals("end")) {
            instructions.add(i);
            if (!i.regionIdentifier().isEmpty() && unpaired.putIfAbsent(i.regionIdentifier(), i) != null) {
                throw new ParseException("Duplicated region " + i.regionIdentifier());
            }
        } else {
            if (i.regionIdentifier().isEmpty()) {
                if (unpaired.isEmpty()) {
                    throw new ParseException("No started regions to end");
                }
                unpaired.forEach((ignored, j) -> completeInstruction(j, i));
                unpaired.clear();
            } else {
                Instruction j = unpaired.remove(i.regionIdentifier());
                if (j == null) {
                    throw new ParseException("Ending a non-started region %s".formatted(i.regionIdentifier()));
                }
                completeInstruction(j, i);
            }
        }
    }

    static final class Instruction {

        String name;
        int position; // the position of markup, not the instruction; this position is, for example, used by @end
        int start;
        int end;
        String regionIdentifier;
        Map<String, String> attributes;
        boolean appliesToNextLine;

        String name() {
            return name;
        }

        String regionIdentifier() {
            return regionIdentifier;
        }

        Map<String, String> attributes() {
            return attributes;
        }

        int start() {
            return start;
        }

        int end() {
            return end;
        }

        @Override
        public String toString() {
            return "Instruction{" +
                    "name='" + name + '\'' +
                    ", start=" + start +
                    ", end=" + end +
                    ", regionIdentifier='" + regionIdentifier + '\'' +
                    ", attributes=" + attributes +
                    '}';
        }
    }

    private void completeInstruction(Instruction start, Instruction end) {
        assert !start.name().equals("end") : start;
        assert end.name().equals("end") : end;
        start.position = end.position; // smuggle the position of the corresponding end
        start.end = end.end();
    }

    private void append(StyledText text, CharSequence s) {
        text.replace(text.length(), text.length(), Style.none(), s.toString());
    }

    public static final class Result {
        private final StyledText text;
        private final Queue<Action> actions;

        public Result(StyledText text, Queue<Action> actions) {
            this.text = text;
            this.actions = actions;
        }

        public StyledText text() {
            return text;
        }

        public Queue<Action> actions() {
            return actions;
        }
    }
}
