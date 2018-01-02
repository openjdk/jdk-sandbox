
/* @test
 * @summary JSON parser test for special characters in json string
 * @modules java.management.rest/com.oracle.jmx.remote.rest.json
 *          java.management.rest/com.oracle.jmx.remote.rest.json.parser
 * @build JsonParserSpecialCharTest
 * @run testng/othervm JsonParserSpecialCharTest
 */

import com.oracle.jmx.remote.rest.json.JSONElement;
import com.oracle.jmx.remote.rest.json.parser.JSONParser;
import com.oracle.jmx.remote.rest.json.parser.ParseException;
import com.oracle.jmx.remote.rest.json.parser.TokenMgrError;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
public class JsonParserSpecialCharTest {

    @DataProvider(name = "getOkStrings")
    Object[][] getOkStrings() {
        Object[][] output = new Object[5][1];
        output[0][0] = "\"ac" + "\nde\\bf\"";
        output[1][0] = "\"abcd\\\"cdef\"";
        output[2][0] = "\"abcd\\\\cdef\"";
        output[3][0] = "\"abc\\rdcde\\f\"";
        output[4][0] = "\"\"";
        return output;
    }

    @DataProvider(name = "getKoStrings")
    Object[][] getKoStrings() {
        Object[][] output = new Object[3][1];
        output[0][0] = "\"a\\ef\"";
        output[1][0] = "\"abg\"cde\"";
        output[2][0] = "\"a\\\bgcde\"";
        return output;
    }

    @Test(dataProvider = "getOkStrings")
    public void testOk(String input) throws ParseException {
        JSONParser parser = new JSONParser(input);
        System.out.println("Input: " + input + ", Output: " +parser.parse().toJsonString());
    }

    @Test(dataProvider = "getKoStrings")
    public void testKo(String input) {
        try{
            JSONParser parser = new JSONParser(input);
            JSONElement parse = parser.parse();
            System.out.println("Input: " + input + ", Output: " + parse.toJsonString());
            throw new RuntimeException("FAILED");
        } catch (ParseException ex) {
        } catch (TokenMgrError error) {}
    }
}
