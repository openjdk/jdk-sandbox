package jdk.jfr.internal.util;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.ConcurrentModificationException;
import java.util.Random;

public class PrimitiveHashMap<V> {

    static final long W = 64L;
    static final long A = 4633630788178346939L;
    final Random rand = new Random();

    private static int getIndex(long key, long hashFunction, long shift, long mask) {
        return (int)(((A * key) + (hashFunction & mask)) >>> shift);
    }
    private long getRandomHashFunction() {
        return rand.nextLong();
    }
    /**
     * The maximum capacity, used if a higher value is implicitly specified
     * by either of the constructors with arguments.
     */
    static final int MAX_SIZE_EXPONENT = 30;
    static final int MAXIMUM_CAPACITY = 1 << MAX_SIZE_EXPONENT;

    static final int DEFAULT_SIZE_EXPONENT = 4;
    static final int DEFAULT_INITIAL_CAPACITY = 1 << DEFAULT_SIZE_EXPONENT; // aka 16
    /**
     * The load factor used when none specified in constructor.
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    static class Log2 {

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
        /**
     * Returns a power of two size for the given target capacity.
     */
    static final int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }
    static final int tableExponent(int cap) {
        return Log2.log2(cap);
    }

    static class Node<V> {
        final long key;
        V value;

        Node(long key, V value) {
            this.key = key;
            this.value = value;
        }

        public final long getKey()     { return key; }
        public final V getValue()      { return value; }
        public final String toString() { return key + "=" + value; }
    }

    private Node<V>[] table;
    private int size;
    private int threshold;
    private long shift;
    private long shiftMask;
    private int tableLengthMask;
    int modCount;
    private final float loadFactor;
    long h1 = 0;
    long h2 = 0;

    public PrimitiveHashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        }
        if (initialCapacity > MAXIMUM_CAPACITY) {
            initialCapacity = MAXIMUM_CAPACITY;
        }
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new IllegalArgumentException("Illegal load factor: " + loadFactor);
        }
        this.loadFactor = loadFactor;
        this.threshold = tableSizeFor(initialCapacity);
        h1 = getRandomHashFunction();
        h2 = getRandomHashFunction();
        resize();
    }

    public PrimitiveHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    public PrimitiveHashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    public final void forEach(BiConsumer<? super Long, ? super V> action) {
        Node<V>[] tab;
        if (action == null)
            throw new NullPointerException();
        if (size > 0 && (tab = table) != null) {
            int mc = modCount;
            for (int i = 0; i < table.length; ++i) {
                if (table[i] == null) continue;
                action.accept(table[i].getKey(), table[i].getValue());
            }
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
    }

    public final void forEach(Consumer<? super V> action) {
        Node<V>[] tab;
        if (action == null)
            throw new NullPointerException();
        if (size > 0 && (tab = table) != null) {
            int mc = modCount;
            for (int i = 0; i < table.length; ++i) {
                if (table[i] == null) continue;
                action.accept(table[i].getValue());
            }
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
    }

    public final long[] keys() {
        long[] keys = new long[size];
        int j = 0;
        for (int i = 0; i < table.length; ++i) {
            if (table[i] == null) continue;
            keys[j++] = table[i].getKey();
        }
        assert(j == size);
        assert(keys.length == size);
        return keys;
    }

    public Collection<V> values () {
        final PrimitiveHashMap<V> thisMap = this;
        return new AbstractCollection<V>() {
            private PrimitiveHashMap<V> map = thisMap;
            public Iterator<V> iterator() {
                return new Iterator<V>() {
                    private int i = 0;
                    private long [] k = keys();
                    public boolean hasNext() {
                        return i < k.length;
                    }
                    public V next() {
                        assert(i < k.length);
                        return map.get(k[i++]);
                    }
                };
            }
            public int size() {
                return map.size();
            }
            public boolean isEmpty() {
                return size() != 0;
            }
            public void clear() {
                throw new UnsupportedOperationException();
            }
            public boolean contains(Object v) {
                for (V value : map.values()) {
                    if (v == value) {
                        return true;
                    }
                }
                return false;
            }
        };
    }
    private int doubleHash(long key, int i) {
        int h1_idx = getIndex(key, h1, shift, shiftMask);
        assert(h1_idx < table.length);
        int h2_idx = 0;
        if (i != 0) {
            h2_idx = getIndex(key, h2, shift, shiftMask);
            h2_idx |= 1;
            assert((h2_idx & 1) == 1);
        }
        assert(h2_idx < table.length);
        final int idx = (h1_idx + (i * h2_idx)) & tableLengthMask;
        assert(idx >= 0);
        assert(idx < table.length);
        return idx;
    }

     /**
     * Initializes or doubles table size.  If null, allocates in
     * accord with initial capacity target held in field threshold.
     * Otherwise, because we are using power-of-two expansion, the
     * elements from each bin must either stay at same index, or move
     * with a power of two offset in the new table.
     *
     * @return the table
     */
    final Node<V>[] resize() {
        Node<V>[] oldTab = table;
        int oldCap = (oldTab == null) ? 0 : oldTab.length;
        int oldThr = threshold;
        int newCap, newThr = 0;
        if (oldCap > 0) {
            if (oldCap >= MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return oldTab;
            }
            else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&
                     oldCap >= DEFAULT_INITIAL_CAPACITY)
                newThr = oldThr << 1; // double threshold
        }
        else if (oldThr > 0) // initial capacity was placed in threshold
            newCap = oldThr;
        else {               // zero initial threshold signifies using defaults
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
        }
        if (newThr == 0) {
            float ft = (float)newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                      (int)ft : Integer.MAX_VALUE);
        }
        threshold = newThr;
        @SuppressWarnings({"rawtypes","unchecked"})
        Node<V>[] newTab = (Node<V>[])new Node[newCap];
        table = newTab;
        tableLengthMask = newCap - 1;
        this.shift = W - tableExponent(newCap);
        this.shiftMask = Log2.log2base10(shift) - 1;
        if (oldTab != null) {
            for (int j = 0; j < oldCap; ++j) {
                Node<V> e;
                if ((e = oldTab[j]) != null) {
                    oldTab[j] = null;
                    reinsert(e);
                }
            }
        }
        return newTab;
    }

    // used by table resize
    private void reinsert(Node<V> e) {
        assert(size < table.length);
        for (int i = 0; i < table.length; ++i) {
            int idx = doubleHash(e.getKey(), i);
            assert(idx >= 0);
            assert(idx < table.length);
            if (table[idx] == null) {
                table[idx] = e;
                return;
            }
            assert(table[idx].key != e.getKey());
        }
    }

    public V put(long key, V value) {
        Node<V> existing = insert(key, value);
        return existing != null ? existing.value : null;
    }

    private Node<V> insert(long key, V value) {
        return insert(new Node<V>(key, value), key);
    }

    private Node<V> insert(Node<V> e, final long key) {
        assert(size < table.length);
        assert(e.getKey() == key);
        Node<V> existing = null;
        for (int i = 0; i < table.length; ++i) {
            int idx = doubleHash(key, i);
            assert(idx >= 0);
            assert(idx < table.length);
            if (table[idx] == null) {
                table[idx] = e;
                ++size;
                break;
            } else {
               if (table[idx].key == key) {
                   existing = table[idx];
                   table[idx] = e;
                   break;
               }
            }
        }
        if (size > threshold) {
            resize();
        }
        return existing;
    }

    private Node<V> find(long key) {
        Node<V> result = null;
        for (int i = 0; i < table.length; ++i) {
            int idx = doubleHash(key, i);
            assert(idx >= 0);
            assert(idx < table.length);
            result = table[idx];
            if (result == null || result.key == key) {
                break;
            }
        }
        return result;
    }


    public V get(long key) {
        Node<V> existing = find(key);
        return existing != null ? existing.value : null;
    }

    public boolean containsKey(long key) {
        return find(key) != null;
    }
    public int size() {
        return this.size;
    }
}