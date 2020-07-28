
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class RandomAllocatorThread extends Thread {

    public final CyclicBarrier gate;
    public final RandomAllocator allocator;
    public final int id;

    public RandomAllocatorThread(CyclicBarrier gate, RandomAllocator allocator, int id) {
        this.gate = gate;
        this.allocator = allocator;
        this.id = id;
    }

    @Override
    public void run() {

       // System.out.println("* [" + id + "] " + allocator);

        try {
            if (gate != null) {
                gate.await();
            }
        } catch (InterruptedException | BrokenBarrierException e) {
            // At this point, interrupt would be an error.
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        while (!Thread.interrupted()) {
            for (int i = 0; i < 1000; i++) {
                allocator.tick();
            }
        }

        // System.out.println("+ [" + id + "] " + allocator);

    }



}
