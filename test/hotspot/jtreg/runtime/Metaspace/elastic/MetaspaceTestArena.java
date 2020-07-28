

import sun.hotspot.WhiteBox;

import java.util.concurrent.atomic.AtomicLong;

public class MetaspaceTestArena {

    long arena;

    final long allocationCeiling;

    // Number and word size of allocations
    long allocatedWords = 0;
    long numAllocated = 0;
    long deallocatedWords = 0;
    long numDeallocated = 0;
    long numAllocationFailures = 0;

    private synchronized boolean reachedCeiling() {
        return (allocatedWords - deallocatedWords) > allocationCeiling;
    }

    private synchronized void accountAllocation(long words) {
        numAllocated ++;
        allocatedWords += words;
    }

    private synchronized void accountDeallocation(long words) {
        numDeallocated ++;
        deallocatedWords += words;
    }

    MetaspaceTestArena(long arena0, long allocationCeiling) {
        this.allocationCeiling = allocationCeiling;
        this.arena = arena0;
    }

    public Allocation allocate(long words) {
        if (reachedCeiling()) {
            numAllocationFailures ++;
            return null;
        }
        WhiteBox wb = WhiteBox.getWhiteBox();
        long p = wb.allocateFromMetaspaceTestArena(arena, words);
        if (p == 0) {
            numAllocationFailures ++;
            return null;
        } else {
            accountAllocation(words);
        }
        return new Allocation(p, words);
    }

    public void deallocate(Allocation a) {
        WhiteBox wb = WhiteBox.getWhiteBox();
        wb.deallocateToMetaspaceTestArena(arena, a.p, a.word_size);
        accountDeallocation(a.word_size);
    }

    //// Convenience functions ////

    public Allocation allocate_expect_success(long words) {
        Allocation a = allocate(words);
        if (a.isNull()) {
            throw new RuntimeException("Allocation failed (" + words + ")");
        }
        return a;
    }

    public void allocate_expect_failure(long words) {
        Allocation a = allocate(words);
        if (!a.isNull()) {
            throw new RuntimeException("Allocation failed (" + words + ")");
        }
    }

    boolean isLive() {
        return arena != 0;
    }


    @Override
    public String toString() {
        return "arena=" + arena +
                ", ceiling=" + allocationCeiling +
                ", allocatedWords=" + allocatedWords +
                ", numAllocated=" + numAllocated +
                ", deallocatedWords=" + deallocatedWords +
                ", numDeallocated=" + numDeallocated +
                ", numAllocationFailures=" + numAllocationFailures +
                '}';
    }

}
