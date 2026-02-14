package common.access;

import common.util.TestBase;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;

/**
 * Prototype review:
 *     Tests are added on top of those created for the built-in catalog
 *     See line 63 - 120 for cases
 */
public class AccessTestBase extends TestBase {
    /*
     * DataProvider for verifying preferences.
     *
     * Fields:
     *     file, FSP, state of setting, config file, system property, api property,
     *     Custom Catalog, error expected, error code or expected result
     */
    //@DataProvider(name = "configWCatalogForParsers")
    public Object[][] getConfigs(Processor processor) {
        // file with an external DTD that's not in JdkCatalog
        String fileDTDNotInC = "properties1.xml";
        // file with an external DTD that's in the Custom Catalog
        String fileDTDInCC = "test.xml";
        // file with an external DTD that's in JdkCatalog
        String javaDTD = "properties.xml";
        // file with an external DTD thats in the Custom Catalog
        String w3cDTD = "xhtml11.xml";

        // error code when CATALOG=strict; The cause for DOM
        String errCode = "JAXP09040001";
        // error code when EXTERNAL_ACCESS_DTD denies access
        String errCodeEAD = "accessExternalDTD";

        // error (not from catalog) is expect when CATALOG=continue
        boolean isErrExpected = true;
        String expected1 = UNKNOWN_HOST;

        // expected when reference is resolved by Catalog
        String expected3 = "", expected4 = "";
        switch (processor) {
            case SAX:
                errCode = "JAXP00090001";
                break;
            case STAX:
                errCode = "JAXP00090001";
                //errCode = "JAXP00090001";
                // StAX is non-validating parser
                isErrExpected = false;
                expected1 = ".*[\\w\\s]*(value1)[\\w\\s]*.*";
                expected3 = "Minimal XHTML 1.1 DocumentThis is a minimal XHTML 1.1 document.";
                expected4 = ".*(123)[\\w\\s]*.*";
                break;
            default:
                break;
        }

        return new Object[][]{
            /**
             * Case 1: Later introduced property has higher preference
             *          Note: External reference not in the built-in catalog
             * Expect: error as the parser continues and tries to access an invalid site
             *         java.net.UnknownHostException: invalid.site.com
            /**
            /**
             * Case 1-1: RESOURCE_ACCESS has higher preference over RESOLVE:
             *           both are set via the API, RESOURCE_ACCESS is set to `allow`
            */
            {fileDTDNotInC, null, PropertyState.CONFIG_FILE_SYSTEM_API, Properties.CONFIG_FILE_CATALOG_STRICT, null, new Properties[]{Properties.CATALOG2, Properties.ACCESS0}, null, isErrExpected, expected1},
            /**
             * Case 1-2: RESOURCE_ACCESS has higher preference over EXTERNAL_ACCESS_*:
             *           both are set via the API, RESOURCE_ACCESS is set to `allow`
            */
            {fileDTDNotInC, null, PropertyState.CONFIG_FILE_SYSTEM_API, Properties.CONFIG_FILE_CATALOG_STRICT, null, new Properties[]{Properties.AED2, Properties.ACCESS0}, null, isErrExpected, expected1},

            /**
             * Case 2: Existing properties has higher preference when they are set at a higher level
             *
            */
            /**
             * Case 2-1: RESOLVE is set at a higher level than RESOURCE_ACCESS
             *           RESOLVE via the API set to `strict`, RESOURCE_ACCESS system property
             * [Fatal Error] properties1.xml:2:75: JAXP00090001: The CatalogResolver is enabled with the catalog "JdkCatalog.xml", but a CatalogException is returned.
             * org.xml.sax.SAXException: javax.xml.catalog.CatalogException: JAXP09040001: No match found for publicId 'null' and systemId 'http://invalid.site.com/dtd/properties1.dtd'.
             * javax.xml.catalog.CatalogException: JAXP09040001: No match found for publicId 'null' and systemId 'http://invalid.site.com/dtd/properties1.dtd'.
            */
            {fileDTDNotInC, null, PropertyState.CONFIG_FILE_SYSTEM_API, Properties.CONFIG_FILE_CATALOG_STRICT, new Properties[]{Properties.ACCESS0}, new Properties[]{Properties.CATALOG2}, null, true, errCode},
            /**
             * Case 2-2: EXTERNAL_ACCESS_DTD is set at a higher level than RESOURCE_ACCESS
             *           EXTERNAL_ACCESS_DTD via the API set to `dent`, RESOURCE_ACCESS system property
             * Expect: [Fatal Error] properties1.xml:7:11: External DTD: Failed to read external DTD 'properties1.dtd', because 'http' access is not allowed due to restriction set by the accessExternalDTD property.
            */
            {fileDTDNotInC, null, PropertyState.CONFIG_FILE_SYSTEM_API, Properties.CONFIG_FILE_CATALOG_STRICT, new Properties[]{Properties.ACCESS0}, new Properties[]{Properties.AED2}, null, true, errCodeEAD},

            /**
             * Case 3: compatibility test:
             *         FSP does not affect RESOURCE_ACCESS (until the JEP turns restriction on by default)
             *
            */
            /**
            /**
             * Case 3-1: none of FSP and properties are set explicitly
             *           all access properties on 'allow' by default
             *
             * Expect: error as the parser continues and tries to access an invalid site
             *         java.net.UnknownHostException: invalid.site.com
            */
            {fileDTDNotInC, null, null, null, null, null, null, isErrExpected, expected1},
            /**
             * Case 3-2: set FSP explicitly:
             *           EXTERNAL_ACCESS_DTD is enabled by FSP
             *
             * Expect: [Fatal Error] properties1.xml:7:11: External DTD: Failed to read external DTD 'properties1.dtd', because 'http' access is not allowed due to restriction set by the accessExternalDTD property.
             *
            */
            {fileDTDNotInC, Properties.FSP, null, null, null, null, null, true, errCodeEAD},

        };
    }

    /*
     * DataProvider for testing configuring properties for validation or transform.
     *
     * Fields:
     *     xml file, xsd or xsl file, FSP, state of setting, config file, system property,
     *     api property, Custom Catalog, error expected, error code or expected result
     */
    //@DataProvider(name = "validationOrTransform")
    public Object[][] getConfig(String m) {
        // Schema Import
        String xmlFile = "XSDImport_company.xsd";
        String xsdOrXsl = null;
        String expected = "";
        String errCode = "JAXP00090001";

        switch (m) {
            case "SchemaTest2":
                // Schema Include
                xmlFile = "XSDInclude_company.xsd";
                break;
            case "Validation":
                // Schema Location
                xmlFile = "val_test.xml";
                break;
            case "Stylesheet":
                errCode = "JAXP09040001";
                xmlFile = "XSLDTD.xsl";
                break;
            case "Transform":
                xmlFile = "XSLPI.xml";
                errCode = "JAXP09040001";
                xsdOrXsl = "<?xml version='1.0'?>"
                + "<!DOCTYPE top SYSTEM 'test.dtd'"
                + "["
                + "<!ENTITY % pe \"x\">"
                + "<!ENTITY   x1 \"AAAAA\">"
                + "<!ENTITY   x2 \"bbb\">"
                +"]>"
                + "<?xml-stylesheet href=\""
                + TEST_SOURCE_DIR
                + "/XSLPI_target.xsl\" type=\"text/xml\"?>"
                + "<xsl:stylesheet "
                + "    xmlns:xsl='http://www.w3.org/1999/XSL/Transform' "
                + "    version='1.0'>"
                + "</xsl:stylesheet> ";
                break;
            default:
                break;
        }

        return new Object[][]{
            // Case 1: external reference not in the JDKCatalog
            /**
             * Case 1-1: default setting; no Config file; Catalog: continue
             * Expect: pass without error
             */
            {xmlFile, xsdOrXsl, null, null, null, null, null, null, false, expected},

            /**
             * Case 1-2: set CATALOG to strict in a Config file
             * Expect: Exception since the external reference is not in the Catalog
             * Sample Error Msg:
             * org.xml.sax.SAXParseException; systemId: file:path/XSDImport_company.xsd;
             * lineNumber: 10; columnNumber: 11;
             * JAXP00090001: The CatalogResolver is enabled with the catalog "JdkCatalog.xml",
             * but a CatalogException is returned.
             */
            {xmlFile, xsdOrXsl, null, PropertyState.CONFIG_FILE, Properties.CONFIG_FILE_CATALOG_STRICT, null, null, null, true, errCode},

            /**
             * Case 1-3: set CATALOG back to continue through the System Property
             * Expect: pass without error
             */
            {xmlFile, xsdOrXsl, null, PropertyState.CONFIG_FILE_SYSTEM, Properties.CONFIG_FILE_CATALOG_STRICT, new Properties[]{Properties.CATALOG0}, null, null, false, expected},

            /**
             * Case 1-4: override the settings in Case 3 with the API property, and set Catalog to strict
             * Expect: Exception since the external reference is not in the Catalog
             */
            {xmlFile, xsdOrXsl, null, PropertyState.CONFIG_FILE_SYSTEM_API, Properties.CONFIG_FILE_CATALOG_STRICT, new Properties[]{Properties.CATALOG0}, new Properties[]{Properties.CATALOG2}, null, true, errCode},

            /**
             * Case 1-5: use Custom Catalog to resolve external references
             * Expect: pass without error
             */
            {xmlFile, xsdOrXsl, null, PropertyState.CONFIG_FILE_SYSTEM_API, Properties.CONFIG_FILE_CATALOG_STRICT, new Properties[]{Properties.CATALOG0}, new Properties[]{Properties.CATALOG2}, CustomCatalog.STRICT, false, expected},

        };
    }

//    @Test(dataProvider = "configWCatalogForParsers", priority=0)
    public void testDOM(String filename, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        DocumentBuilderFactory dbf = getDBF(fsp, state, config, sysProp, apiProp, cc);
        process(filename, dbf, expectError, error);
    }

//    @Test(dataProvider = "configWCatalogForParsers")
    public void testSAX(String filename, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        SAXParser parser = getSAXParser(fsp, state, config, sysProp, apiProp, cc);
        process(filename, parser, expectError, error);
    }

//    @Test(dataProvider = "configWCatalogForParsers")
    public void testStAX(String filename, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        XMLInputFactory xif = getXMLInputFactory(state, config, sysProp, apiProp, cc);
        process(filename, xif, expectError, error);
    }

//    @Test(dataProvider = "validationOrTransform")
    public void testSchema1(String filename, String xsd, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        SchemaFactory sf = getSchemaFactory(fsp, state, config, sysProp, apiProp, cc);
        process(filename, sf, expectError, error);
    }

//    @Test(dataProvider = "validationOrTransform")
    public void testSchema2(String filename, String xsd, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {
        testSchema1(filename, xsd, fsp, state, config, sysProp, apiProp, cc, expectError, error);
    }

//    @Test(dataProvider = "validationOrTransform")
    public void testValidation(String filename, String xsd, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        SchemaFactory sf = getSchemaFactory(fsp, state, config, sysProp, apiProp, cc);
        validate(filename, sf, expectError, error);
    }

//    @Test(dataProvider = "validationOrTransform")
    public void testStylesheet(String filename, String xsl, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        TransformerFactory tf = getTransformerFactory(fsp, state, config, sysProp, apiProp, cc);
        process(filename, tf, expectError, error);
    }

//    @Test(dataProvider = "validationOrTransform")
    public void testTransform(String filename, String xsl, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        TransformerFactory tf = getTransformerFactory(fsp, state, config, sysProp, apiProp, cc);
        transform(filename, xsl, tf, expectError, error);
    }

    // parameters in the same order as the test method
    String filename; String xsd; String xsl; Properties fsp; PropertyState state;
    Properties config; Properties[] sysProp; Properties[] apiProp; CustomCatalog cc;
    boolean expectError; String error;

    // Maps the DataProvider array to individual parameters
    public void paramMap(Processor processor, String method, String index) {
        int i = 0;
        Object[][] params;
        if (processor == Processor.VALIDATOR ||
                processor == Processor.TRANSFORMER) {
            params = getConfig(method);
            i = 1;
        } else {
            params = getConfigs(processor);
        }
        Object[] param = params[Integer.parseInt(index)];
        filename = (String)param[0];
// JEP error message
//        filename="message.xml";
        if (processor == Processor.VALIDATOR) {
            xsd = (String)param[i];
        } else if (processor == Processor.TRANSFORMER) {
            xsl = (String)param[i];
        }
        fsp = (Properties)param[i + 1];
        state = (PropertyState)param[i + 2];
        config = (Properties)param[i + 3];
        sysProp = (Properties[])param[i + 4];
        apiProp = (Properties[])param[i + 5];
        cc = (CustomCatalog)param[i + 6];
        expectError = (boolean)param[i + 7];
        error = (String)param[i + 8];
    }
}
