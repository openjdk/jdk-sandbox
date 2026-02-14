package common.access;

/**
 * @test @bug 8357394
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @modules java.xml/jdk.xml.internal
 * @run driver common.access.DOMTest 0 // RESOURCE_ACCESS has higher preference over RESOLVE
 * @run driver common.access.DOMTest 1 // RESOURCE_ACCESS has higher preference over EXTERNAL_ACCESS_*
 * @run driver common.access.DOMTest 2 // RESOLVE is set at a higher level than RESOURCE_ACCESS
 * @run driver common.access.DOMTest 3 // EXTERNAL_ACCESS_DTD is set at a higher level than RESOURCE_ACCESS
 * @run driver common.access.DOMTest 4 // none of FSP and properties are set explicitly
 * @run driver common.access.DOMTest 5 // set FSP explicitly
 * @summary verifies external access properties' preference order.
 */
public class DOMTest extends AccessTestBase {
    public static void main(String args[]) throws Exception {
        new DOMTest().run(args[1]);
        //new DOMTest().run("5");
    }

    public void run(String index) throws Exception {
        paramMap(Processor.DOM, null, index);
        super.testDOM(filename, fsp, state, config, sysProp, apiProp, cc, expectError, error);

    }
}
