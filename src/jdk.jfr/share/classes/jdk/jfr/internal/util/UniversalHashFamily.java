package jdk.jfr.internal.util;

import java.util.Random;

public class UniversalHashFamily {
    final Random rand = new Random();

    private static long getA(long hashFunction) {
        return hashFunction | 1;
    }

    private static long getB(long hashFunction, long mask) {
        return hashFunction & mask;
    }

    private static long getHash(long key, long hashFunction, long mask) {
        return (getA(hashFunction) * key) + (hashFunction & mask);
    }

    public static int getIndex(long key, long hashFunction, long shift, long mask) {
        return (int)(getHash(key, hashFunction, mask) >>> shift);
    }

    public long getRandomHashFunction() {
        return rand.nextLong() & Long.MAX_VALUE;
    }
}