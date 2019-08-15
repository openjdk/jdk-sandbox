package jdk.jfr.api.consumer.streaming;

import jdk.jfr.consumer.EventStream;

/**
 * @test
 * @summary Verifies that is possible to open a stream when a repository doesn't
 *          exists
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.consumer.streaming.TestUnstarted
 */
public class TestUnstarted {
    public static void main(String... args) throws Exception {
        try (EventStream es = EventStream.openRepository()) {
            es.onEvent(e -> {
                // ignore
            });
            es.startAsync();
        }
    }
}
