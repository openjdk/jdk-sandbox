import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;

import static org.testng.Assert.*;

/*
 * @test
 * @summary Check that the built-in inet name service is used by default.
 * @modules java.base/java.net:open
 * @run testng/othervm BuiltInResolverTest
 */

public class BuiltInResolverTest {

    private Field builtInResolverField, resolverField;

    @BeforeTest
    public void beforeTest() throws NoSuchFieldException {
        Class<InetAddress> inetAddressClass = InetAddress.class;
        // Needs to happen for InetAddress.nameService to be initialized
        try {
            InetAddress.getByName("test");
        } catch (UnknownHostException e) {
            // Do nothing, only want to assign nameService
        }
        builtInResolverField = inetAddressClass.getDeclaredField("BUILTIN_RESOLVER");
        builtInResolverField.setAccessible(true);
        resolverField = inetAddressClass.getDeclaredField("resolver");
        resolverField.setAccessible(true);
    }

    @Test
    public void testDefaultNSContext() throws IllegalAccessException {
        // Test that the name service used by default is the BUILTIN_INET_NAME_SERVICE
        Object defaultResolverObject = builtInResolverField.get(InetAddressResolver.class);
        Object usedResolverObject = resolverField.get(InetAddressResolver.class);

        assertTrue(defaultResolverObject == usedResolverObject);

        String defaultClassName = defaultResolverObject.getClass().getCanonicalName();
        String currentClassName = usedResolverObject.getClass().getCanonicalName();

        assertNotNull(defaultClassName, "defaultClassName not set");
        assertNotNull(currentClassName, "currentClassName name not set");

        assertEquals(currentClassName, defaultClassName,
                "BUILTIN_RESOLVER resolver was not used.");
        System.err.println("Resolver used by default is the built-in resolver");
    }
}