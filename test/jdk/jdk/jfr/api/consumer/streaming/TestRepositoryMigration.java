package jdk.jfr.api.consumer.streaming;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;

import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.jcmd.JcmdHelper;

/**
 * @test
 * @summary Verifies that is possible to stream from a repository that is being
 *          moved.
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.api.consumer.streaming.TestRepositoryMigration
 */
public class TestRepositoryMigration {
    static class MigrationEvent extends Event {
        int id;
    }

    public static void main(String... args) throws Exception {
        Path newRepository = Paths.get("new-repository");
        CountDownLatch events = new CountDownLatch(2);
        try (EventStream es = EventStream.openRepository()) {
            es.setStartTime(Instant.EPOCH);
            es.onEvent(e -> {
                System.out.println(e);
                if (e.getInt("id") == 1) {
                    events.countDown();
                }
                if (e.getInt("id") == 2) {
                    events.countDown();
                }
            });
            es.startAsync();
            try (Recording r = new Recording()) {
                r.setFlushInterval(Duration.ofSeconds(1));
                r.start();
                // Chunk in default repository
                MigrationEvent e1 = new MigrationEvent();
                e1.id = 1;
                e1.commit();
               JcmdHelper.jcmd("JFR.configure", "repositorypath=" + newRepository.toAbsolutePath());
                // Chunk in new repository
                MigrationEvent e2 = new MigrationEvent();
                e2.id = 2;
                e2.commit();
                r.stop();
                events.await();
                // Verify that it happened in new repository
                if (!Files.exists(newRepository)) {
                    throw new AssertionError("Could not find repository " + newRepository);
                }
                System.out.println("Listing contents in new repository:");
                boolean empty= true;
                for (Path p: Files.newDirectoryStream(newRepository)) {
                    System.out.println(p.toAbsolutePath());
                    empty = false;
                }
                System.out.println();
                if (empty) {
                    throw new AssertionError("Could not find contents in new repository location " + newRepository);
                }
            }
        }
    }

}
