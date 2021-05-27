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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
    // next-line tag behaves as if it were specified on the next line

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
    private final MarkupParser markupParser = new MarkupParser();

    // Incomplete actions waiting for their complementary @end
    private final Regions regions = new Regions();
    // List of tags; consumed from top to bottom
    private final Queue<Tag> tags = new LinkedList<>();

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
            // The bellow Pattern.compile should never throw PatternSyntaxException
            Pattern pattern = Pattern.compile("^(.*)(" + Pattern.quote(eolMarker) + "(\\s*@\\s*\\w+.+?))$");
            this.markedUpLine = pattern.matcher(""); // reusable matcher
        }

        tags.clear();
        regions.clear();

        Queue<Action> actions = new LinkedList<>();

        StyledText text = new DefaultStyledText();
        Iterator<String> iterator = source.lines().iterator();
        boolean trailingNewline = source.endsWith("\r") || source.endsWith("\n");
        int lineStart = 0;
        List<Tag> previousLineTags = new ArrayList<>();
        List<Tag> thisLineTags = new ArrayList<>();
        List<Tag> tempList = new ArrayList<>();
        while (iterator.hasNext()) {
            String rawLine = iterator.next();
            boolean addLineTerminator = iterator.hasNext() || trailingNewline;
            String line;
            markedUpLine.reset(rawLine);
            if (markedUpLine.matches()) {
                String maybeMarkup = markedUpLine.group(3);
                line = markedUpLine.group(1).stripTrailing() + (addLineTerminator ? "\n" : ""); // remove any whitespace
                List<Tag> parsedTags = markupParser.parse(maybeMarkup);

                for (Tag t : parsedTags) {
                    t.markupPosition = markedUpLine.start(3);
                }

                thisLineTags.addAll(parsedTags);
                for (var tagIterator = thisLineTags.iterator(); tagIterator.hasNext(); ) {
                    Tag i = tagIterator.next();
                    if (i.appliesToNextLine) {
                        tagIterator.remove();
                        i.appliesToNextLine = false; // clear the flag
                        tempList.add(i);
                    } else {
                        i.markupCommentPosition = markedUpLine.start(2); // e.g. @end that relates to the same line
                    }
                }
                if (parsedTags.isEmpty()) { // not a valid markup comment
                    line = rawLine + (addLineTerminator ? "\n" : "");
                }
            } else {
                line = rawLine + (addLineTerminator ? "\n" : "");
            }

            thisLineTags.addAll(0, previousLineTags); // prepend!
            previousLineTags.clear();
            for (Tag t : thisLineTags) {
                t.start = lineStart;
                t.end = lineStart + line.length(); // this includes line terminator, if any
                processTag(t);
            }
            previousLineTags.addAll(tempList);
            tempList.clear();

            thisLineTags.clear();

            append(text, line);
            lineStart += line.length();
        }

        if (!previousLineTags.isEmpty()) {
            throw new ParseException("Tags refer to non-existent lines");
        }

        // also report on unpaired with corresponding `end` or unknown tags
        if (!regions.isEmpty()) {
            throw new ParseException("Unpaired region(s)");
        }

        for (var t : tags) {

            // Translate a list of attributes into a more convenient form
            Attributes attributes = new Attributes(t.attributes());

            final var substring = attributes.get("substring", Attribute.Valued.class);
            final var regex = attributes.get("regex", Attribute.Valued.class);

            if (!t.name().equals("start") && substring.isPresent() && regex.isPresent()) {
                throw new ParseException("'substring' and 'regex' cannot be used simultaneously");
            }

            switch (t.name()) {
                case "link" -> {
                    var target = attributes.get("target", Attribute.Valued.class)
                            .orElseThrow(() -> new ParseException("target is absent"));
                    var type = attributes.get("type", Attribute.Valued.class);
                    String typeValue = type.isPresent() ? type.get().value() : "link";
                    if (!typeValue.equals("link") && !typeValue.equals("linkplain")) {
                        throw new ParseException("Unknown link type: '%s'".formatted(typeValue));
                    }
                    Restyle a = new Restyle(s -> s.and(Style.link(target.value())),
                                            createRegexPattern(substring, regex, t.markupPosition),
                                            text.select(t.start(), t.end()));
                    actions.add(a);
                }
                case "replace" -> {
                    var replacement = attributes.get("replacement", Attribute.Valued.class)
                            .orElseThrow(() -> new ParseException("replacement is absent"));
                    Replace a = new Replace(replacement.value(),
                                            createRegexPattern(substring, regex, t.markupPosition),
                                            text.select(t.start(), t.end()));
                    actions.add(a);
                }
                case "highlight" -> {
                    var type = attributes.get("type", Attribute.Valued.class);

                    String typeValue = type.isPresent() ? type.get().value() : "bold";

                    Restyle a = new Restyle(s -> s.and(Style.name(typeValue)),
                                            createRegexPattern(substring, regex, t.markupPosition),
                                            text.select(t.start(), t.end()));
                    actions.add(a);
                }
                case "start" -> {
                    var region = attributes.get("region", Attribute.Valued.class)
                            .orElseThrow(() -> new ParseException("Unnamed start"));
                    String regionValue = region.value();
                    if (regionValue.isBlank()) {
                        throw new ParseException("Blank region name");
                    }
                    if (t.attributes().size() != 1) {
                        throw new ParseException("Unexpected attributes");
                    }
                    actions.add(new Start(region.value(), text.select(t.start(), t.end()), t.markupCommentPosition));
                }
            }
        }

        return new Result(text, actions);
    }

    private Pattern createRegexPattern(Optional<Attribute.Valued> substring,
                                       Optional<Attribute.Valued> regex,
                                       int offset) throws ParseException {
        Pattern pattern;
        if (substring.isPresent()) {
            pattern = Pattern.compile(Pattern.quote(substring.get().value())); // cannot throw an exception
        } else if (regex.isEmpty()) {
            pattern = Pattern.compile("(?s).+"); // cannot throw an exception
        } else {
            // Unlike string literals in Java source, attribute values in markup
            // do not use escapes. So indices of characters in the regex pattern
            // directly map to their corresponding positions in snippet source.
            try {
                pattern = Pattern.compile(regex.get().value());
            } catch (PatternSyntaxException e) {
                int index = e.getIndex();
                final int pos = index == -1 ? -1 : offset + regex.get().valueStartPosition() + index;
                throw new ParseException(e.getDescription(), pos);
            }
        }
        return pattern;
    }

    private void processTag(Tag i) throws ParseException {

        Attributes attributes = new Attributes(i.attributes()); // FIXME: we create them twice
        Optional<Attribute> region = attributes.get("region", Attribute.class);

        if (!i.name().equals("end")) {
            tags.add(i);
            if (region.isPresent()) {
                if (region.get() instanceof Attribute.Valued v) {
                    String name = v.value();
                    if (!regions.addNamed(name, i)) {
                        throw new ParseException("Duplicated region: " + name);
                    }
                } else {
                    // TODO: change to exhaustive switch after "Pattern Matching for switch" is implemented
                    assert region.get() instanceof Attribute.Valueless;
                    regions.addAnonymous(i);
                }
            }
        } else {
            if (region.isEmpty() || region.get() instanceof Attribute.Valueless) {
                Optional<Tag> tag = regions.removeLast();
                if (tag.isEmpty()) {
                    throw new ParseException("No started regions to end");
                }
                completeTag(tag.get(), i);
            } else {
                assert region.get() instanceof Attribute.Valued;
                String name = ((Attribute.Valued) region.get()).value();
                Optional<Tag> tag = regions.removeNamed(name);
                if (tag.isEmpty()) {
                    throw new ParseException("Ending a non-started region %s".formatted(name));
                }
                completeTag(tag.get(), i);
            }
        }
    }

    static final class Tag {

        String name;
        int markupCommentPosition; // the position of markup, not the tag; this position is, for example, used by @end
        int markupPosition; // the position from which the markup tags are read
        int start;
        int end;
        List<Attribute> attributes;
        boolean appliesToNextLine;

        String name() {
            return name;
        }

        List<Attribute> attributes() {
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
            return "Tag{" +
                    "name='" + name + '\'' +
                    ", start=" + start +
                    ", end=" + end +
                    ", attributes=" + attributes +
                    '}';
        }
    }

    private void completeTag(Tag start, Tag end) {
        assert !start.name().equals("end") : start;
        assert end.name().equals("end") : end;
        start.markupCommentPosition = end.markupCommentPosition; // smuggle the position of the corresponding end
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

    /*
     * Encapsulates the data structure used to manage regions.
     *
     * boolean-returning commands return true if succeed and false if fail.
     */
    public static final class Regions {

        /*
         * LinkedHashMap does not fit here because of both the need for unique
         * keys for anonymous regions and inability to easily access the most
         * recently put entry.
         *
         * Since we expect only a few regions, a list will do.
         */
        private final ArrayList<Map.Entry<Optional<String>, Tag>> tags = new ArrayList<>();

        void addAnonymous(Tag i) {
            tags.add(Map.entry(Optional.empty(), i));
        }

        boolean addNamed(String name, Tag i) {
            boolean matches = tags.stream()
                    .anyMatch(entry -> entry.getKey().isPresent() && entry.getKey().get().equals(name));
            if (matches) {
                return false; // won't add a duplicate
            }
            tags.add(Map.entry(Optional.of(name), i));
            return true;
        }

        Optional<Tag> removeNamed(String name) {
            for (var iterator = tags.iterator(); iterator.hasNext(); ) {
                var entry = iterator.next();
                if (entry.getKey().isPresent() && entry.getKey().get().equals(name)) {
                    iterator.remove();
                    return Optional.of(entry.getValue());
                }
            }
            return Optional.empty();
        }

        Optional<Tag> removeLast() {
            if (tags.isEmpty()) {
                return Optional.empty();
            }
            Map.Entry<Optional<String>, Tag> e = tags.remove(tags.size() - 1);
            return Optional.of(e.getValue());
        }

        void clear() {
            tags.clear();
        }

        boolean isEmpty() {
            return tags.isEmpty();
        }
    }
}
