package jdk.jfr.internal.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class PerfectHashMap<V> {
    private static final long COLLISION_SHIFT = 63;
    private static final long COLLISION_BIT = 1L << COLLISION_SHIFT;
    private static final long COLLISION_MASK = COLLISION_BIT - 1;
    private static final int MAX_REMAP_ATTEMPTS = 100000;
    private static final int  MAX_ATTEMPS_BEFORE_RESIZE = 100;

    static final long W = 64L;
    static class LinkedValue<V> {
        final V value;
        long next;

        LinkedValue(V value) {
            this.value = value;
            this.next = 0;
        }
    }

    private UniversalHashFamily hashFamily = new UniversalHashFamily();
    private PrimitiveHashMap<LinkedValue<V>> loadMap;
    private Object[] valueTable;
    private long[] routeTable;
    private long shift;
    private long shiftMask;
    private int tableLengthMask;
    private long primaryHashFunction = 0;
    private int collisions = 0;
    private int retries = 0;
    private int sizeFactor = 1;
    private boolean minimal;

    public V get(long key) {
        LinkedValue<V> v = loadMap.get(key);
        return v != null ? v.value : null;
    }

    public V put(long key, V value) {
        LinkedValue<V> existing = loadMap.put(key, new LinkedValue<V>(value));
        return existing != null ? existing.value : null;
    }

    public void forEach(BiConsumer<? super Long, ? super V> action) {
        //loadMap.forEach(PerfectHashMap<V>::callback);
    }

    public final void forEach(Consumer<? super V> action) {
        //loadMap.forEach(action);
    }

    public final long[] keys() {
        return loadMap.keys();
    }

    static class Log2 {
        private static final int MAX_SIZE_EXPONENT = 32;

        static long log2base10(long exponent) {
            return 1L << exponent;
        }

        static int log2(int value) {
            int i = 0;
            int lastMultiple = 0;
            while (i < MAX_SIZE_EXPONENT) {
                int multiple = (int)log2base10(i);
                if ((value & multiple) != 0) {
                    lastMultiple = i;
                }
                ++i;
            }
            return ((int)log2base10(lastMultiple) ^ value) != 0 ? lastMultiple + 1 : lastMultiple;
        }
            }

    static final int tableExponent(int cap) {
        return Log2.log2(cap);
    }

    PerfectHashMap() {
        this(false, 101);
    }

    PerfectHashMap(int size) {
        this(false, size);
    }

    PerfectHashMap(boolean minimal, int size) {
        this.minimal = minimal;
        this.loadMap = new PrimitiveHashMap<>(size);
        this.primaryHashFunction = hashFamily.getRandomHashFunction();
    }

    @SuppressWarnings("unchecked")
    public V getPerfect(long key) {
        int routeIdx = getIndex(key, primaryHashFunction);
        assert(routeIdx >= 0);
        assert(routeIdx < routeTable.length);
        long element = routeTable[routeIdx];
        int valueIdx = element < 0 ? getIndex(key, -element - 1) : (int)element;
        assert(valueIdx >= 0);
        assert(valueIdx < valueTable.length);
        return (V)valueTable[valueIdx];
    }

    private long getRandomHashFunction() {
        return hashFamily.getRandomHashFunction();
    }
    private int getIndex(long key, long hashFunction) {
       final int idx = UniversalHashFamily.getIndex(key, hashFunction, shift, shiftMask);
       assert(idx >= 0);
       assert(idx < routeTable.length);
       return idx;
    }
    private static boolean isColliding(long entry) {
        return entry < 0;
    }
    private boolean isNonColliding(long entry) {
        return entry > 0;
    }
    private static long setColliding(long entry) {
        return entry | COLLISION_BIT;
    }
    private static long read(long entry) {
        return entry & COLLISION_MASK;
    }

    private int nextValueTableSlot(int lastIdx) {
        assert(lastIdx < valueTable.length);
        int i = lastIdx;
        for (; i < valueTable.length; ++i) {
            if (valueTable[i] == null) {
                break;
            }
        }
        return i;
    }

    private int valueTableStore(V value, int lastIdx) {
        if (lastIdx > valueTable.length) {
            lastIdx = 0;
        }
        assert(lastIdx < valueTable.length);
        final int idx = nextValueTableSlot(lastIdx);
        assert(idx < valueTable.length);
        assert(valueTable[idx] == null);
        valueTable[idx] = value;
        return idx;
    }


    private void routeNonCollisions() {
        int lastIdx = 0;
        for (int i = 0; i < routeTable.length; ++i) {
            if (isNonColliding(routeTable[i])) {
                lastIdx = valueTableStore(loadMap.get(routeTable[i]).value, lastIdx);
                routeTable[i] = lastIdx++;
           }
        }
    }

    private void rollback(int idx, int length, long hashFunction) {
        assert(isColliding(routeTable[idx]));
        long key = read(routeTable[idx]);
        LinkedValue<V> v = loadMap.get(key); // boxing
        for (int i = 0; i < length; ++i) {
            final int valueIdx = getIndex(key, hashFunction);
            assert(valueIdx >= 0);
            assert(valueIdx < valueTable.length);
            assert(valueTable[valueIdx] != null);
            valueTable[valueIdx] = null;
            key = v.next;
            v = loadMap.get(v.next); // no boxing
        }
    }

    private boolean remap(int idx, long hashFunction) {
        assert(isColliding(routeTable[idx]));
        int completed = 0;
        long key = read(routeTable[idx]);
        LinkedValue<V> v = loadMap.get(key);
        while (key != 0) {
            final int valueIdx = getIndex(key, hashFunction);
            assert(valueIdx >= 0);
            assert(valueIdx < valueTable.length);
            if (valueTable[valueIdx] == null) {
                valueTable[valueIdx] = v.value;
                ++completed;
                key = v.next;
                v = loadMap.get(v.next);
                continue;
            }
            rollback(idx, completed, hashFunction);
            return false;
        }
        return true;
    }

    private boolean routeCollisions(int idx) {
        assert(isColliding(routeTable[idx]));
        boolean success = false;
        int attempts = 0;
        long randomHashFunction = 0;
        do {
            randomHashFunction = getRandomHashFunction();
            success = remap(idx, randomHashFunction);
            if (++attempts == MAX_REMAP_ATTEMPTS) {
                System.out.println("Failed number of attempts - restart: " + attempts);
                return false;
            }
        } while (!success);
        System.out.println("Number of remap attempts: " + attempts);
        routeTable[idx] = -1 - randomHashFunction;
        assert(-routeTable[idx] - 1 == randomHashFunction);
        return true;
    }


    private boolean routeCollisions() {
        for (int i = 0; i < routeTable.length; ++i) {
            if (isColliding(routeTable[i])) {
                if (!routeCollisions(i)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void clearLongTable(long[] table) {
        Arrays.fill(table, 0);
        for (int i = 0; i < table.length; ++i) {
            assert(table[i] == 0);
        }
    }

    private static <T extends Object> void clearReferenceTable(T[] table) {
        Arrays.fill(table, null);
        for (int i = 0; i < table.length; ++i) {
            assert(table[i] == null);
        }
    }

    private void unlinkChains() {
        for (long key : loadMap.keys()) {
            loadMap.get(key).next = 0;
        }
    }

    private void routeTableStore(long key, LinkedValue<V> value, int idx) {
        assert(idx >= 0);
        assert(idx < routeTable.length);
        long existing = read(routeTable[idx]);
        if (existing == 0) {
            routeTable[idx] = key;
            return;
        }
        ++collisions;
        routeTable[idx] = setColliding(existing);
        LinkedValue<V> existingValue = loadMap.get(existing);
        value.next = existingValue.next;
        existingValue.next = key;
    }

    private void mapKeys() {
        for (long key : loadMap.keys()) {
            routeTableStore(key, loadMap.get(key), getIndex(key, primaryHashFunction));
        }
    }

    private void validate() {
        for (long key : loadMap.keys()) {
            long element = routeTable[getIndex(key, primaryHashFunction)];
            int valueIdx = element < 0 ? getIndex(key, -element - 1) : (int)element;
            assert(valueIdx >= 0);
            assert(loadMap.get(key) == valueTable[valueIdx]);
        }
    }

    private void reset() {
        collisions = 0;
        clearLongTable(routeTable);
        clearReferenceTable(valueTable);
    }

    private int dimensionTableSize() {
        int size = loadMap.size() * sizeFactor;
        return (int)Log2.log2base10(Log2.log2(size));
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private void allocateTables() {
        int size = dimensionTableSize();
        this.tableLengthMask = size - 1;
        this.shift = W - tableExponent(size);
        this.shiftMask = Log2.log2base10(shift) - 1;
        routeTable = new long[size];
        valueTable = (V[])new Object[size];
        collisions = 0;
        retries = 0;
    }

    public void build() {
        start:
        while (true) {
            allocateTables();
            System.out.println("Table size " + routeTable.length);
            mapKeys();
            if (collisions > 0) {
                if (!routeCollisions()) {
                    unlinkChains();
                    if (++retries <= MAX_ATTEMPS_BEFORE_RESIZE) {
                      reset();
                    } else {
                      sizeFactor *= 2;
                    }
                    continue start;
                }
            }
            routeNonCollisions();
            return;
        }
    }

    public void rebuild() {
        sizeFactor = 1;
        build();
    }
    public int size() {
        return loadMap.size();
    }
}