package jdk.xml.internal;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RuleReader {
    public static AccessRule parse(String raw) {
//        if (raw == null || raw.trim().isEmpty()) {
//            return new AccessRule(null, null);
//        }

        Set<AccessRule> rules = new LinkedHashSet<>();
        boolean hasEmpty = false;
        List<String> tokens = List.of(raw.split("\\s*,\\s*"));

        for (String token : tokens) {
            if (token.isEmpty()) {
                hasEmpty = true;
                continue;
            }

            AccessRule rule;
            try {
                rule = parseToken(token);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid resource access rule: " + token, e);
            }

            if (!rules.add(rule)) {
                // Optionally: log a warning about redundant rule
            }
        }

        // Conflict check: cannot combine "" (block all) with any other rules
        if (hasEmpty && !rules.isEmpty()) {
            throw new IllegalArgumentException("Conflicting rules: cannot combine empty string (block all) with access rules");
        }
return null;
        //return new AccessRule(rules, null);
    }

    private static AccessRule parseToken(String token) {
        if (!token.startsWith("@")) {
            //return new HostOrDomainRule(token);
        }
/*
        String value = token.substring(1).toLowerCase(Locale.ROOT);
        if (ALLOWED_KEYWORDS.contains(value)) {
            return new KeywordRule(value);
        }

        if (ALLOWED_SCHEMES.contains(value)) {
            return new SchemeRule(value);
        }
*/
        throw new IllegalArgumentException("Unknown keyword or scheme: @" + "");
    }
}
