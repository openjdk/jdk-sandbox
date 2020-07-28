import java.util.*;

public class AllocationProfile {

    final String name;

    // Allocation word size spread
    public final long minimumSingleAllocationSize;
    public final long maximumSingleAllocationSize;

    // dealloc probability [0.0 .. 1.0]
    public final double randomDeallocProbability;

    public AllocationProfile(String name, long minimumSingleAllocationSize, long maximumSingleAllocationSize, double randomDeallocProbability) {
        this.minimumSingleAllocationSize = minimumSingleAllocationSize;
        this.maximumSingleAllocationSize = maximumSingleAllocationSize;
        this.randomDeallocProbability = randomDeallocProbability;
        this.name = name;
    }

    public long randomAllocationSize() {
        Random r = new Random();
        return r.nextInt((int)(maximumSingleAllocationSize - minimumSingleAllocationSize + 1)) + minimumSingleAllocationSize;
    }


    // Some standard profiles
    static final List<AllocationProfile> standardProfiles = new ArrayList<>();

    static {
        standardProfiles.add(new AllocationProfile("medium-range",1, 2048, 0.15));
        standardProfiles.add(new AllocationProfile("small-blocks",1, 512, 0.15));
        standardProfiles.add(new AllocationProfile("micro-blocks",1, 32, 0.15));
    }

    static AllocationProfile randomProfile() {
        return standardProfiles.get(RandomHelper.random().nextInt(standardProfiles.size()));
    }

    @Override
    public String toString() {
        return "MetaspaceTestAllocationProfile{" +
                "name='" + name + '\'' +
                ", minimumSingleAllocationSize=" + minimumSingleAllocationSize +
                ", maximumSingleAllocationSize=" + maximumSingleAllocationSize +
                ", randomDeallocProbability=" + randomDeallocProbability +
                '}';
    }

}
