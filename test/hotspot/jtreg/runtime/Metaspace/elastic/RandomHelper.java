
import java.util.Random;

public class RandomHelper {

    static Random rand;

    static {
        long seed = Long.parseLong(System.getProperty("metaspace.random.seed", "0"));
        if (seed == 0) {
            seed = System.currentTimeMillis();
            System.out.println("Random seed: " + seed);
        } else {
            System.out.println("Random seed: " + seed + " (passed in)");
        }
        rand = new Random(seed);
    }

    static Random random() { return rand; }

    static boolean fiftyfifty() { return random().nextInt(10) >= 5; }

}
