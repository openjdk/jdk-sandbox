
module java.management.rest {

    requires transitive java.management;
    requires jdk.httpserver;

    exports javax.management.remote.rest;
}
