package jdk.xml.internal;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Represents a parsed rule for matching external resource access permissions based on URI patterns.
 * <p>
 * This class encapsulates a resource access rule consisting of a scheme, optional host and port,
 * and path pattern. It is used to determine if a specific {@link java.net.URI} is permitted based on
 * rules specified with the {@code jdk.xml.resource.access} property.
 * </p>
 * <p>
 * Supported rule format:
 * <pre>
 *   [scheme]://host[:port][/path-pattern]
 *   [scheme]:/[path-pattern]     (for local schemes such as file, jar, jrt)
 * </pre>
 * <ul>
 *   <li><b>scheme</b>: The URI scheme (e.g., http, https, ftp, file, jar, jrt).</li>
 *   <li><b>host</b>: Domain name, IPv4, or IPv6 address. For local schemes ("file", "jar", "jrt"), host is omitted.</li>
 *   <li><b>port</b>: (optional) Port number to match. If omitted, matches the default port for the scheme.</li>
 *   <li><b>path-pattern</b>: (optional) Resource path. Supports wildcards (e.g., {@code /*}, {@code /dtds/*}).</li>
 * </ul>
 * <p>
 * Wildcards are allowed in host (e.g., <code>*.foo.com</code>) and path (e.g., <code>/*</code> or <code>/foo/*</code>).
 * </p>
 * <p>
 * Example patterns:
 * <ul>
 *   <li>{@code http://*.foo.com:8080/*} — allows HTTP resources on any subdomain of foo.com at port 8080, any path</li>
 *   <li>{@code file:/dtds/*} — allows all files under /dtds</li>
 *   <li>{@code file:/foo/bar.dtd} — allows only the local file /foo/bar.dtd</li>
 *   <li>{@code *} — allows unrestricted access</li>
 * </ul>
 * </p>
 * <p>
 * The {@link #allows(java.net.URI)} method determines whether a given URI is permitted according to this rule.
 * </p>
 *
 * @author (your name)
 */
public class AccessRule {
    private final List<URIPatternRule> rules = new ArrayList<>();
    private final boolean allowAll;
    private final boolean denyAll;
    private final String rawInput;

    public AccessRule(String input) {
//        if (input == null || input.isBlank())
//            throw new IllegalArgumentException("jdk.xml.resource.access must not be null or empty");
        this.rawInput = input;
        String trimmedInput = input == null ? "" : input.trim();
        if (trimmedInput.equals("*")) {
            allowAll = true;
            denyAll = false;
            return;
        } else if (trimmedInput.isEmpty()) {
            allowAll = false;
            denyAll = true;
            return;
        }
        allowAll = false;
        denyAll = false;
        String[] tokens = input.split(",");
        for (String rawToken : tokens) {
            String token = rawToken.trim();
            if (token.isEmpty()) continue;
            rules.add(URIPatternRule.parse(token));
        }
    }

    public boolean allows(URI uri) {
        if (denyAll) return false;
        if (allowAll) return true;
        for (URIPatternRule rule : rules) {
            if (rule.matches(uri)) return true;
        }
        return false;
    }

    @Override
    public String toString() { return rawInput; }

    /**
     * Represents a parsed URI-based pattern rule.
     */
    public static class URIPatternRule {
        private final String scheme;
        private final HostPattern hostPattern;
        private final Integer port;  // null if not set
        private final PathPattern pathPattern; // always present

        private URIPatternRule(String scheme, HostPattern hostPattern, Integer port, PathPattern pathPattern) {
            this.scheme = scheme;
            this.hostPattern = hostPattern;
            this.port = port;
            this.pathPattern = pathPattern;
        }

        /**
         * Parses the specified pattern string.
         * Example patterns: file:*, file:/foo, http://foo.com, https://*.foo.com:8080
         * @param pattern the pattern string
         * @return an instance of URIPatternRule from the pattern string
         */
        public static URIPatternRule parse(String pattern) {
            // Syntax: [scheme]:/{0,3}[host[:port]][/path-pattern]
            int schemeSep = pattern.indexOf(':');
            if (schemeSep <= 0)
                throw new IllegalArgumentException("Missing or invalid scheme in resource access pattern: " + pattern);

            String scheme = pattern.substring(0, schemeSep).toLowerCase(Locale.ROOT);
            if (!isSupportedScheme(scheme))
                throw new IllegalArgumentException("Unsupported scheme in resource access pattern: " + pattern);

            String rest = pattern.substring(schemeSep + 1);
            // Remove up to 3 leading slashes
            String afterSlashes = rest.replaceFirst("^/{0,3}", "");

            HostPattern hostPattern = null;
            Integer port = null;
            PathPattern pathPattern = null;
            // Handle file/jar/jrt schemes as path patterns
            boolean isLocalScheme = scheme.equals("file") || scheme.equals("jar") || scheme.equals("jrt");
            if (isLocalScheme) {
                // afterSlashes is the path pattern, can be empty or "*"
                if (afterSlashes.isEmpty() || afterSlashes.equals("*")) {
                    pathPattern = PathPattern.of("*"); // Accept everything if not specified
                } else {
                    if (!afterSlashes.startsWith("/")) afterSlashes = "/" + afterSlashes;
                    pathPattern = PathPattern.of(afterSlashes);
                }
            } else {
                // For network: split host[:port] and optional /path
                String hostPart;
                String pathPart = null;
                int slashIndex = afterSlashes.indexOf('/');
                if (slashIndex >= 0) {
                    hostPart = afterSlashes.substring(0, slashIndex);
                    pathPart = afterSlashes.substring(slashIndex); // includes "/"
                } else {
                    hostPart = afterSlashes;
                }
                // Port
                int portSep = hostPart.lastIndexOf(':');
                if (portSep > 0 && portSep < hostPart.length() - 1
                    && isPortNumber(hostPart.substring(portSep + 1))) {
                    hostPattern = HostPattern.of(hostPart.substring(0, portSep));
                    port = Integer.parseInt(hostPart.substring(portSep + 1));
                } else if (!hostPart.isEmpty()) {
                    hostPattern = HostPattern.of(hostPart);
                }
                // pathPattern is set only if specified, null otherwise
                if (pathPart != null && !pathPart.isEmpty() && !pathPart.equals("/*")) {
                    pathPattern = PathPattern.of(pathPart);
                } else if (pathPart != null && (pathPart.isEmpty() || pathPart.equals("/*"))) {
                    pathPattern = PathPattern.of("*");
                }
            }
            return new URIPatternRule(scheme, hostPattern, port, pathPattern);
        }

        public boolean matches(URI uri) {
            if (uri == null) return false;
            String testScheme = uri.getScheme();
            if (testScheme == null || !testScheme.equalsIgnoreCase(scheme)) return false;

            // Local: path-pattern match only
            if (hostPattern == null) {
                if (pathPattern == null) return true; // match all local of that scheme
                String uriPath = uri.getPath();
                return pathPattern.matches(uriPath);
            }

            // Network: host and port required
            String testHost = uri.getHost();
            if (!hostPattern.matches(testHost)) return false;
            if (port != null && port != (uri.getPort() == -1 ? getDefaultPort(scheme) : uri.getPort())) return false;
            // If a pathPattern is present, also match path; else, path is ignored
            if (pathPattern != null) {
                String uriPath = uri.getPath();
                return pathPattern.matches(uriPath);
            }
            return true;
        }

        private static boolean isSupportedScheme(String scheme) {
            return switch (scheme) {
                case "http", "https", "ftp", "file", "jar", "jrt" -> true;
                default -> false;
            };
        }

        /**
         * Check if string is an integer between 0 and 65535 (valid TCP/UDP port range).
         */
        private static boolean isPortNumber(String str) {
            try {
                int port = Integer.parseInt(str);
                return port >= 0 && port <= 65535;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // standard ports for known schemes
        private static int getDefaultPort(String scheme) {
            return switch (scheme) {
                case "http" -> 80;
                case "https" -> 443;
                case "ftp" -> 21;
                default -> -1;
            };
        }
    }


    // Host pattern matching for exact, IPv4 and IPv6 hosts.
    public static class HostPattern {
        private final String pattern;
        private final boolean isAny;
        private final boolean isSubdomainPattern;

        private HostPattern(String pattern, boolean isAny, boolean isSubdomainPattern) {
            this.pattern = pattern;
            this.isAny = isAny;
            this.isSubdomainPattern = isSubdomainPattern;
        }

        public static HostPattern of(String hostPattern) {
            String trimmed = hostPattern.trim();
            if (trimmed.equals("*")) {
                return new HostPattern("*", true, false);
            }
            if (trimmed.startsWith("*.")) {
                // *.example.com
                return new HostPattern(trimmed.substring(2).toLowerCase(Locale.ROOT), false, true);
            }
            // Check for valid IPv4 address
            if (trimmed.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
                return new HostPattern(trimmed, false, false);
            }
            // Check for valid IPv6 address in bracket form
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                // Optionally, more IPv6 validation can be added here
                return new HostPattern(trimmed.toLowerCase(Locale.ROOT), false, false);
            }
            // Otherwise, treat as literal domain
            return new HostPattern(trimmed.toLowerCase(Locale.ROOT), false, false);
        }

        public boolean matches(String testHost) {
            if (isAny) return true;
            if (testHost == null) return false;
            testHost = testHost.toLowerCase(Locale.ROOT);
            // Subdomain wildcard
            if (isSubdomainPattern) {
                return testHost.endsWith("." + pattern);
            }
            // Exact match (domain, IPv4, or IPv6)
            return testHost.equals(pattern);
        }
    }

    public static class PathPattern {
        private final String pattern; // E.g. /dtds/* or /*
        private final boolean isAny;
        private final boolean isDirectory; // endsWith /*

        private PathPattern(String pattern, boolean isAny, boolean isDirectory) {
            this.pattern = pattern;
            this.isAny = isAny;
            this.isDirectory = isDirectory;
        }

        public static PathPattern of(String pattern) {
            pattern = (pattern == null || pattern.isEmpty()) ? "/" : pattern;
            // supports *, /foo/*, /foo/bar
            if (pattern.equals("*") || pattern.equals("/*")) {
                return new PathPattern(pattern, true, false);
            }
            if (pattern.endsWith("/*")) {
                return new PathPattern(pattern.substring(0, pattern.length() - 2), false, true);
            }
            return new PathPattern(pattern, false, false);
        }

        public boolean matches(String testPath) {
            if (isAny) return true;
            if (testPath == null) return false;
            if (isDirectory) {
                // Path starts with this directory
                return testPath.startsWith(pattern + "/") || testPath.equals(pattern);
            }
            return testPath.equals(pattern);
        }
    }

    /**
     * Host pattern matching for exact, wildcard, or IPv4 wildcard hosts.
     */
    public static class HostPattern1 {
        private final String pattern;
        private final boolean isAny;
        private final boolean isSubdomainPattern;
        private final boolean isIPv4Wildcard;
        private final Pattern ipv4Regex;

        private HostPattern1(String pattern, boolean isAny, boolean isSubdomainPattern, boolean isIPv4Wildcard, Pattern ipv4Regex) {
            this.pattern = pattern;
            this.isAny = isAny;
            this.isSubdomainPattern = isSubdomainPattern;
            this.isIPv4Wildcard = isIPv4Wildcard;
            this.ipv4Regex = ipv4Regex;
        }

        public static HostPattern1 of(String hostPattern) {
            String trimmed = hostPattern.trim();
            if (trimmed.equals("*")) {
                return new HostPattern1("*", true, false, false, null);
            }
            if (trimmed.startsWith("*.")) {
                // *.example.com
                return new HostPattern1(trimmed.substring(2).toLowerCase(Locale.ROOT), false, true, false, null);
            }
            if (trimmed.matches("\\d+\\.\\d+\\.\\d+\\.\\*")) {
                // e.g. 192.168.1.* (support 0 or more * at the end)
                String regex = trimmed.replace(".", "\\.").replace("*", "\\d{1,3}");
                Pattern ipv4Regex = Pattern.compile("^" + regex + "$");
                return new HostPattern1(trimmed, false, false, true, ipv4Regex);
            }
            // literal domain or ip
            return new HostPattern1(trimmed.toLowerCase(Locale.ROOT), false, false, false, null);
        }

        public boolean matches(String testHost) {
            if (isAny) return true;
            if (testHost == null) return false;
            testHost = testHost.toLowerCase(Locale.ROOT);
            if (isSubdomainPattern) {
                return testHost.endsWith("." + pattern);
            }
            if (isIPv4Wildcard && ipv4Regex != null) {
                return ipv4Regex.matcher(testHost).matches();
            }
            return testHost.equals(pattern);
        }
    }
}
