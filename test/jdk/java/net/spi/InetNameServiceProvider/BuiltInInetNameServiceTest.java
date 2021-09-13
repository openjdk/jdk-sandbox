import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetNameService;

import static org.testng.Assert.*;

/*
 * @test
 * @summary Check that the built-in inet name service is used by default.
 * @modules java.base/java.net:open
 * @run testng/othervm BuiltInInetNameServiceTest
 */

public class BuiltInInetNameServiceTest {

    private Field builtInNameServiceField, nameServiceField;

    @BeforeTest
    public void beforeTest() throws NoSuchFieldException {
        Class<InetAddress> inetAddressClass = InetAddress.class;
        // Needs to happen for InetAddress.nameService to be initialized
        try {
            InetAddress.getByName("test");
        } catch (UnknownHostException e) {
            // Do nothing, only want to assign nameService
        }
        builtInNameServiceField = inetAddressClass.getDeclaredField("BUILTIN_INET_NAME_SERVICE");
        builtInNameServiceField.setAccessible(true);
        nameServiceField = inetAddressClass.getDeclaredField("nameService");
        nameServiceField.setAccessible(true);
    }

    @Test
    public void testDefaultNSContext() throws IllegalAccessException {
        // Test that the name service used by default is the BUILTIN_INET_NAME_SERVICE
        Object defaultNameServiceObject = builtInNameServiceField.get(InetNameService.class);
        Object usedNameServiceObject = nameServiceField.get(InetNameService.class);

        assertTrue(defaultNameServiceObject == usedNameServiceObject);

        String defaultClassName = defaultNameServiceObject.getClass().getCanonicalName();
        String currentClassName = usedNameServiceObject.getClass().getCanonicalName();

        assertNotNull(defaultClassName, "defaultClassName not set");
        assertNotNull(currentClassName, "currentClassName name not set");

        assertEquals(currentClassName, defaultClassName,
                "BUILTIN_INET_NAME_SERVICE name service was not used.");
        System.err.println("Name service used by default is the built-in name service");
    }
}