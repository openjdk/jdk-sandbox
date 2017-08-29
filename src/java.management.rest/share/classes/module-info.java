
module java.management.rest {

    requires transitive java.management;
    requires jdk.httpserver;

    exports javax.management.remote.rest;
    exports com.oracle.jmx.remote.rest.json;
    exports com.oracle.jmx.remote.rest.json.parser;
}
