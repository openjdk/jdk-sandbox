import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Test
public class RestAdapterPerfTest {

    private static Random random = new Random(System.currentTimeMillis());
    private static AtomicInteger count = new AtomicInteger(1);

    public static void main(String[] args) throws Exception {
        RestAdapterTest test = new RestAdapterTest();
        List<Runnable> tasks = new ArrayList<>();

        tasks.add(test::testAllMBeanServers);
        tasks.add(test::testAllMBeanInfo);
        tasks.add(test::testAllMBeans);
        tasks.add(test::testMBeanFiltering);
        tasks.add(test::testMBeanGetAttributes);
        tasks.add(test::testMBeanSetAttributes);
        tasks.add(test::testMbeanNoArgOperations);
        tasks.add(test::testAllMBeansBulkRequest);
        tasks.add(test::testThreadMXBeanBulkRequest);
        tasks.add(test::testThreadMXBeanThreadInfo);

        ThreadPoolExecutor es = (ThreadPoolExecutor) Executors.newFixedThreadPool(20);
        es.setThreadFactory((Runnable R) -> new Thread(R, "perf-" + count.getAndIncrement()));
        long current = System.currentTimeMillis();
        test.setupServers();
        for (int i = 0; i < 200; i++) {
            Runnable task = tasks.get(random.nextInt(tasks.size()));
            es.execute(task);
        }

        System.out.println("Submitted 200 tasks");
        es.shutdown();
        es.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        float v = (float) (System.currentTimeMillis() - current) / (float) 1000;
        System.out.println("Total time = " + v + "s");
        test.tearDownServers();
    }
}
