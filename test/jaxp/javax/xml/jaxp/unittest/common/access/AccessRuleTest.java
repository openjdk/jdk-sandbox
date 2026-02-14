package common.access;

import jdk.xml.internal.AccessRule;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testng.Assert;

import java.net.URI;
import java.util.stream.Stream;


/*
 * @test
 * @bug 8357394
 * @summary Verifies access rules defined by property jdk.xml.resource.access
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest /test/lib
 * @modules java.xml/jdk.xml.internal
 * @run junit/othervm common.access.AccessRuleTest
 */
public class AccessRuleTest {
    /**
     * Returns test data for testAccessRule.
     * Data: rules, URI strings, result (true if allowed, false otherwise)
     * @return test data for testAccessRule
     */
    private static Stream<Arguments> testData() {

        return Stream.of(
            Arguments.of("*", "http://all.access", true),
            Arguments.of("", "http://no.access", false),
            Arguments.of("http:*;http:/*;http://*", "http://all.http.access", true),
            Arguments.of("http://*.oracle.com", "http://subdomains.oracle.com/dtds/example.dtd", true),
            Arguments.of("https:*;https:/*;https://*", "https://all.https.access", true),
            Arguments.of("https://*.oracle.com", "https://subdomains.oracle.com/dtds/example.dtd", true),
            Arguments.of("file:*;file:/*;file://*;file:///*", "file://all.file.access", true),
            Arguments.of("http://www.oracle.com", "http://www.oracle.com/dtds/example.dtd", true),
            Arguments.of("http://www.oracle.com, http://*.oracle.com",
                "http://www.oracle.com/dtds/example.dtd; http://subdomains.oracle.com/dtds/example.dtd", true),
            Arguments.of("file:/dtds/dtd1.dtd", "file:/dtds/dtd1.dtd", true),
            Arguments.of("file:/dtds/dtd1.dtd, file:/xsds/*", "file:/dtds/dtd1.dtd; file:/xsds/example.xsd", true),
            Arguments.of("http://www.oracle.com, file:/dtds/dtd1.dtd, file:/xsds/*",
                "http://www.oracle.com/dtds/example.dtd; file:/dtds/dtd1.dtd; file:/xsds/example.xsd", true)

        );
    }
    /**
     * Verifies that the Access External Properties are supported throughout the
     * JAXP APIs.
     * @param rules the access rules separate by ";"
     * @param systemIds system IDs represented as semicolon-separated URI strings
     * @param permitted the flag indicating whether the rules permit the resource
     *                  represented by the systemId
     * @throws Exception if the test fails due to test configuration issues other
     * than the expected result
     */
    @ParameterizedTest
    @MethodSource("testData")
    public void testAccessExternalProperties(String rules, String systemIds, boolean permitted)
        throws Exception {
        String[] accessRules = rules.split(";");
        for (String rule : accessRules) {
            AccessRule accessRule = new AccessRule(rule.trim());
            String[] ids = systemIds.split(";");
            for (String systemId : ids) {
                Assert.assertEquals(accessRule.allows(URI.create(systemId.trim())), permitted);
            }
        }
    }
}
