public class BarrierCodeSizeSuite {

    static final int ROOTS    = 512;
    static final int ARRAYLEN = 512;

    static final Node[] roots = new Node[ROOTS];
    static final Object[] arr = new Object[ARRAYLEN];

    static volatile Object sinkObj;
    static volatile int    sinkInt;

    static class Node {
        Node a, b, c, d;
        Object p1, p2, p3;
        volatile Object v1, v2;
        int id;
        Node(int id) { this.id = id; }
    }

    public static void main(String[] args) {
        // init some non-null state so write barriers always see references
        for (int i = 0; i < ROOTS; i++) {
            Node n = new Node(i);
            n.a = new Node(i + 10_000);
            n.b = new Node(i + 20_000);
            n.c = new Node(i + 30_000);
            n.d = new Node(i + 40_000);
            n.p1 = new Object();
            n.p2 = new Object();
            n.p3 = new Object();
            roots[i] = n;
        }
        for (int i = 0; i < ARRAYLEN; i++) {
            arr[i] = roots[i % ROOTS];
        }

        System.out.println("BarrierCodeSizeSuite running.");
        System.out.println("Example VM flags:");
        System.out.println("  -XX:-TieredCompilation");
        System.out.println("  -XX:CompileCommand=dontinline,BarrierCodeSizeSuite::hot");
        System.out.println("  -XX:CompileCommand=dontinline,BarrierCodeSizeSuite::bomb0");
        System.out.println("  -XX:CompileCommand=dontinline,BarrierCodeSizeSuite::bomb1");
        System.out.println("  -XX:CompileCommand=dontinline,BarrierCodeSizeSuite::bomb2");
        System.out.println("  -XX:CompileCommand=dontinline,BarrierCodeSizeSuite::bomb3");
        System.out.println("  -XX:CompileCommand=dontinline,BarrierCodeSizeSuite::bomb4");
        System.out.println("  -XX:CompileCommand=dontinline,BarrierCodeSizeSuite::bomb5");
        System.out.println("  -XX:CompileCommand=dontinline,BarrierCodeSizeSuite::bomb6");
        System.out.println("  -XX:CompileCommand=dontinline,BarrierCodeSizeSuite::bomb7");
        System.out.println("  -XX:CompileCommand=dontinline,BarrierCodeSizeSuite::bomb8");
        System.out.println("  -XX:CompileCommand=dontinline,BarrierCodeSizeSuite::bomb9");
        System.out.println("  -XX:+UnlockDiagnosticVMOptions -XX:+PrintAssembly");

        int x = 0;
        while (true) {
            x ^= hot(x);
            if ((x & 0xFFFFF) == 0) {
                System.out.println("x=" + x + " sink=" + sinkObj);
            }
        }
    }

    // Hot dispatcher – all the real work happens in bombX methods
    static int hot(int seed) {
        int acc = seed;
        acc ^= bomb0(seed + 101);
        acc ^= bomb1(seed + 203);
        acc ^= bomb2(seed + 307);
        acc ^= bomb3(seed + 409);
        acc ^= bomb4(seed + 503);
        acc ^= bomb5(seed + 601);
        acc ^= bomb6(seed + 701);
        acc ^= bomb7(seed + 809);
        acc ^= bomb8(seed + 907);
        acc ^= bomb9(seed + 1009);
        sinkInt ^= acc;
        return acc;
    }

    // All bombX methods are structurally similar but use different base constants so
    // the compiler can’t trivially merge everything. Each one is barrier-dense.

    static int bomb0(int seed) {
        int acc = seed;
        int base = seed;

        for (int b = 0; b < 16; b++) {
            int s = base + (b * 32);

            int i0 = (s + 0) & (ROOTS - 1);
            int i1 = (s + 1) & (ROOTS - 1);
            int i2 = (s + 2) & (ROOTS - 1);
            int i3 = (s + 3) & (ROOTS - 1);

            Node r0 = roots[i0];
            Node r1 = roots[i1];
            Node r2 = roots[i2];
            Node r3 = roots[i3];

            if (r0 != null) {
                Object old = r0.p1; sinkObj = old;
                r0.p1 = r1;
                r0.p2 = r2;
                r0.p3 = null;
                r0.a  = roots[(s + 4) & (ROOTS - 1)];
                r0.b  = r3;
                r0.c  = r2;
                r0.d  = roots[(s + 5) & (ROOTS - 1)];

                arr[(s + 0) & (ARRAYLEN - 1)] = r0;
                arr[(s + 1) & (ARRAYLEN - 1)] = r1;
                arr[(s + 2) & (ARRAYLEN - 1)] = r2;
                arr[(s + 3) & (ARRAYLEN - 1)] = null;

                r0.v1 = r1;
                r0.v2 = arr[(s + 4) & (ARRAYLEN - 1)];
            }
            if (r1 != null) {
                Object old = r1.p2; sinkObj = old;
                r1.p1 = r2;
                r1.p2 = roots[(s + 6) & (ROOTS - 1)];
                r1.p3 = null;
                r1.a  = r0;
                r1.b  = r3;
                r1.c  = roots[(s + 7) & (ROOTS - 1)];
                r1.d  = r2;

                arr[(s + 4) & (ARRAYLEN - 1)] = r1;
                arr[(s + 5) & (ARRAYLEN - 1)] = r2;
                arr[(s + 6) & (ARRAYLEN - 1)] = r3;
                arr[(s + 7) & (ARRAYLEN - 1)] = null;

                r1.v1 = arr[(s + 6) & (ARRAYLEN - 1)];
                r1.v2 = r0;
            }
            if (r2 != null) {
                Object old = r2.p3; sinkObj = old;
                r2.p1 = r0;
                r2.p2 = roots[(s + 8) & (ROOTS - 1)];
                r2.p3 = r1;
                r2.a  = r3;
                r2.b  = null;
                r2.c  = roots[(s + 9) & (ROOTS - 1)];
                r2.d  = r1;

                arr[(s + 8)  & (ARRAYLEN - 1)] = r2;
                arr[(s + 9)  & (ARRAYLEN - 1)] = r1;
                arr[(s + 10) & (ARRAYLEN - 1)] = null;
                arr[(s + 11) & (ARRAYLEN - 1)] = r0;

                r2.v1 = r3;
                r2.v2 = arr[(s + 10) & (ARRAYLEN - 1)];
            }
            if (r3 != null) {
                Object old = r3.p1; sinkObj = old;
                r3.p1 = r2;
                r3.p2 = r1;
                r3.p3 = roots[(s + 10) & (ROOTS - 1)];
                r3.a  = null;
                r3.b  = r0;
                r3.c  = roots[(s + 11) & (ROOTS - 1)];
                r3.d  = r2;

                arr[(s + 12) & (ARRAYLEN - 1)] = r3;
                arr[(s + 13) & (ARRAYLEN - 1)] = r2;
                arr[(s + 14) & (ARRAYLEN - 1)] = r1;
                arr[(s + 15) & (ARRAYLEN - 1)] = null;

                r3.v1 = arr[(s + 12) & (ARRAYLEN - 1)];
                r3.v2 = r0;
            }

            acc ^= (r0 != null ? r0.id : 0)
                 ^ (r1 != null ? r1.id : 0)
                 ^ (r2 != null ? r2.id : 0)
                 ^ (r3 != null ? r3.id : 0);
        }

        sinkInt ^= acc;
        return acc;
    }

    static int bomb1(int seed) {
        int acc = seed;
        int base = seed + 10_000;

        for (int b = 0; b < 16; b++) {
            int s = base + (b * 32);

            int i0 = (s + 0) & (ROOTS - 1);
            int i1 = (s + 1) & (ROOTS - 1);
            int i2 = (s + 2) & (ROOTS - 1);
            int i3 = (s + 3) & (ROOTS - 1);

            Node r0 = roots[i0];
            Node r1 = roots[i1];
            Node r2 = roots[i2];
            Node r3 = roots[i3];

            if (r0 != null) {
                Object old = r0.p2; sinkObj = old;
                r0.p1 = roots[(s + 4) & (ROOTS - 1)];
                r0.p2 = r1;
                r0.p3 = r2;
                r0.a  = r3;
                r0.b  = null;
                r0.c  = roots[(s + 5) & (ROOTS - 1)];
                r0.d  = r2;

                arr[(s + 0) & (ARRAYLEN - 1)] = r1;
                arr[(s + 1) & (ARRAYLEN - 1)] = r0;
                arr[(s + 2) & (ARRAYLEN - 1)] = r2;
                arr[(s + 3) & (ARRAYLEN - 1)] = null;

                r0.v1 = r3;
                r0.v2 = arr[(s + 4) & (ARRAYLEN - 1)];
            }
            if (r1 != null) {
                Object old = r1.p3; sinkObj = old;
                r1.p1 = r2;
                r1.p2 = r3;
                r1.p3 = null;
                r1.a  = roots[(s + 6) & (ROOTS - 1)];
                r1.b  = r0;
                r1.c  = r2;
                r1.d  = r3;

                arr[(s + 4) & (ARRAYLEN - 1)] = r1;
                arr[(s + 5) & (ARRAYLEN - 1)] = r3;
                arr[(s + 6) & (ARRAYLEN - 1)] = r0;
                arr[(s + 7) & (ARRAYLEN - 1)] = null;

                r1.v1 = arr[(s + 6) & (ARRAYLEN - 1)];
                r1.v2 = r2;
            }
            if (r2 != null) {
                Object old = r2.p1; sinkObj = old;
                r2.p1 = roots[(s + 7) & (ROOTS - 1)];
                r2.p2 = r0;
                r2.p3 = r1;
                r2.a  = r3;
                r2.b  = roots[(s + 8) & (ROOTS - 1)];
                r2.c  = null;
                r2.d  = r1;

                arr[(s + 8)  & (ARRAYLEN - 1)] = r2;
                arr[(s + 9)  & (ARRAYLEN - 1)] = r0;
                arr[(s + 10) & (ARRAYLEN - 1)] = r1;
                arr[(s + 11) & (ARRAYLEN - 1)] = null;

                r2.v1 = arr[(s + 10) & (ARRAYLEN - 1)];
                r2.v2 = r3;
            }
            if (r3 != null) {
                Object old = r3.p2; sinkObj = old;
                r3.p1 = r2;
                r3.p2 = roots[(s + 9) & (ROOTS - 1)];
                r3.p3 = null;
                r3.a  = r1;
                r3.b  = r0;
                r3.c  = roots[(s + 10) & (ROOTS - 1)];
                r3.d  = r2;

                arr[(s + 12) & (ARRAYLEN - 1)] = r3;
                arr[(s + 13) & (ARRAYLEN - 1)] = r0;
                arr[(s + 14) & (ARRAYLEN - 1)] = r2;
                arr[(s + 15) & (ARRAYLEN - 1)] = null;

                r3.v1 = arr[(s + 12) & (ARRAYLEN - 1)];
                r3.v2 = r2;
            }

            acc ^= (r0 != null ? r0.id : 0)
                 ^ (r1 != null ? r1.id : 0)
                 ^ (r2 != null ? r2.id : 0)
                 ^ (r3 != null ? r3.id : 0);
        }

        sinkInt ^= acc;
        return acc;
    }

    static int bomb2(int seed) {
        int acc = seed;
        int base = seed + 20_000;

        for (int b = 0; b < 16; b++) {
            int s = base + (b * 32);

            int i0 = (s + 0) & (ROOTS - 1);
            int i1 = (s + 1) & (ROOTS - 1);
            int i2 = (s + 2) & (ROOTS - 1);
            int i3 = (s + 3) & (ROOTS - 1);

            Node r0 = roots[i0];
            Node r1 = roots[i1];
            Node r2 = roots[i2];
            Node r3 = roots[i3];

            if (r0 != null) {
                Object old = r0.p3; sinkObj = old;
                r0.p1 = r1;
                r0.p2 = roots[(s + 4) & (ROOTS - 1)];
                r0.p3 = r2;
                r0.a  = r3;
                r0.b  = r2;
                r0.c  = null;
                r0.d  = roots[(s + 5) & (ROOTS - 1)];

                arr[(s + 0) & (ARRAYLEN - 1)] = r0;
                arr[(s + 1) & (ARRAYLEN - 1)] = r2;
                arr[(s + 2) & (ARRAYLEN - 1)] = r1;
                arr[(s + 3) & (ARRAYLEN - 1)] = null;

                r0.v1 = r1;
                r0.v2 = arr[(s + 2) & (ARRAYLEN - 1)];
            }
            if (r1 != null) {
                Object old = r1.p1; sinkObj = old;
                r1.p1 = r0;
                r1.p2 = r2;
                r1.p3 = roots[(s + 6) & (ROOTS - 1)];
                r1.a  = null;
                r1.b  = r3;
                r1.c  = roots[(s + 7) & (ROOTS - 1)];
                r1.d  = r2;

                arr[(s + 4) & (ARRAYLEN - 1)] = r1;
                arr[(s + 5) & (ARRAYLEN - 1)] = r3;
                arr[(s + 6) & (ARRAYLEN - 1)] = r0;
                arr[(s + 7) & (ARRAYLEN - 1)] = null;

                r1.v1 = arr[(s + 6) & (ARRAYLEN - 1)];
                r1.v2 = r2;
            }
            if (r2 != null) {
                Object old = r2.p2; sinkObj = old;
                r2.p1 = roots[(s + 8) & (ROOTS - 1)];
                r2.p2 = r0;
                r2.p3 = r1;
                r2.a  = r3;
                r2.b  = roots[(s + 9) & (ROOTS - 1)];
                r2.c  = null;
                r2.d  = r1;

                arr[(s + 8)  & (ARRAYLEN - 1)] = r2;
                arr[(s + 9)  & (ARRAYLEN - 1)] = r0;
                arr[(s + 10) & (ARRAYLEN - 1)] = r1;
                arr[(s + 11) & (ARRAYLEN - 1)] = null;

                r2.v1 = arr[(s + 10) & (ARRAYLEN - 1)];
                r2.v2 = r3;
            }
            if (r3 != null) {
                Object old = r3.p3; sinkObj = old;
                r3.p1 = r2;
                r3.p2 = r1;
                r3.p3 = roots[(s + 10) & (ROOTS - 1)];
                r3.a  = r0;
                r3.b  = null;
                r3.c  = roots[(s + 11) & (ROOTS - 1)];
                r3.d  = r2;

                arr[(s + 12) & (ARRAYLEN - 1)] = r3;
                arr[(s + 13) & (ARRAYLEN - 1)] = r2;
                arr[(s + 14) & (ARRAYLEN - 1)] = r1;
                arr[(s + 15) & (ARRAYLEN - 1)] = null;

                r3.v1 = arr[(s + 12) & (ARRAYLEN - 1)];
                r3.v2 = r0;
            }

            acc ^= (r0 != null ? r0.id : 0)
                 ^ (r1 != null ? r1.id : 0)
                 ^ (r2 != null ? r2.id : 0)
                 ^ (r3 != null ? r3.id : 0);
        }

        sinkInt ^= acc;
        return acc;
    }

    static int bomb3(int seed) {
        int acc = seed;
        int base = seed + 30_000;

        for (int b = 0; b < 16; b++) {
            int s = base + (b * 32);

            int i0 = (s + 0) & (ROOTS - 1);
            int i1 = (s + 1) & (ROOTS - 1);
            int i2 = (s + 2) & (ROOTS - 1);
            int i3 = (s + 3) & (ROOTS - 1);

            Node r0 = roots[i0];
            Node r1 = roots[i1];
            Node r2 = roots[i2];
            Node r3 = roots[i3];

            if (r0 != null) {
                Object old = r0.p1; sinkObj = old;
                r0.p1 = r1;
                r0.p2 = r2;
                r0.p3 = roots[(s + 4) & (ROOTS - 1)];
                r0.a  = r3;
                r0.b  = r2;
                r0.c  = null;
                r0.d  = roots[(s + 5) & (ROOTS - 1)];

                arr[(s + 0) & (ARRAYLEN - 1)] = r0;
                arr[(s + 1) & (ARRAYLEN - 1)] = r1;
                arr[(s + 2) & (ARRAYLEN - 1)] = r2;
                arr[(s + 3) & (ARRAYLEN - 1)] = null;

                r0.v1 = r1;
                r0.v2 = arr[(s + 2) & (ARRAYLEN - 1)];
            }
            if (r1 != null) {
                Object old = r1.p1; sinkObj = old;
                r1.p1 = r0;
                r1.p2 = r2;
                r1.p3 = roots[(s + 6) & (ROOTS - 1)];
                r1.a  = null;
                r1.b  = r3;
                r1.c  = roots[(s + 7) & (ROOTS - 1)];
                r1.d  = r2;

                arr[(s + 4) & (ARRAYLEN - 1)] = r1;
                arr[(s + 5) & (ARRAYLEN - 1)] = r2;
                arr[(s + 6) & (ARRAYLEN - 1)] = r0;
                arr[(s + 7) & (ARRAYLEN - 1)] = null;

                r1.v1 = arr[(s + 6) & (ARRAYLEN - 1)];
                r1.v2 = r2;
            }
            if (r2 != null) {
                Object old = r2.p2; sinkObj = old;
                r2.p1 = roots[(s + 8) & (ROOTS - 1)];
                r2.p2 = r0;
                r2.p3 = r1;
                r2.a  = r3;
                r2.b  = roots[(s + 9) & (ROOTS - 1)];
                r2.c  = null;
                r2.d  = r1;

                arr[(s + 8)  & (ARRAYLEN - 1)] = r2;
                arr[(s + 9)  & (ARRAYLEN - 1)] = r0;
                arr[(s + 10) & (ARRAYLEN - 1)] = r1;
                arr[(s + 11) & (ARRAYLEN - 1)] = null;

                r2.v1 = arr[(s + 10) & (ARRAYLEN - 1)];
                r2.v2 = r3;
            }
            if (r3 != null) {
                Object old = r3.p3; sinkObj = old;
                r3.p1 = r2;
                r3.p2 = r1;
                r3.p3 = roots[(s + 10) & (ROOTS - 1)];
                r3.a  = r0;
                r3.b  = null;
                r3.c  = roots[(s + 11) & (ROOTS - 1)];
                r3.d  = r2;

                arr[(s + 12) & (ARRAYLEN - 1)] = r3;
                arr[(s + 13) & (ARRAYLEN - 1)] = r2;
                arr[(s + 14) & (ARRAYLEN - 1)] = r1;
                arr[(s + 15) & (ARRAYLEN - 1)] = null;

                r3.v1 = arr[(s + 12) & (ARRAYLEN - 1)];
                r3.v2 = r0;
            }

            acc ^= (r0 != null ? r0.id : 0)
                 ^ (r1 != null ? r1.id : 0)
                 ^ (r2 != null ? r2.id : 0)
                 ^ (r3 != null ? r3.id : 0);
        }

        sinkInt ^= acc;
        return acc;
    }

    static int bomb4(int seed) {
        int acc = seed;
        int base = seed + 40_000;

        for (int b = 0; b < 16; b++) {
            int s = base + (b * 32);

            int i0 = (s + 0) & (ROOTS - 1);
            int i1 = (s + 1) & (ROOTS - 1);
            int i2 = (s + 2) & (ROOTS - 1);
            int i3 = (s + 3) & (ROOTS - 1);

            Node r0 = roots[i0];
            Node r1 = roots[i1];
            Node r2 = roots[i2];
            Node r3 = roots[i3];

            if (r0 != null) {
                Object old = r0.p2; sinkObj = old;
                r0.p1 = roots[(s + 4) & (ROOTS - 1)];
                r0.p2 = r1;
                r0.p3 = r2;
                r0.a  = r3;
                r0.b  = null;
                r0.c  = roots[(s + 5) & (ROOTS - 1)];
                r0.d  = r2;

                arr[(s + 0) & (ARRAYLEN - 1)] = r1;
                arr[(s + 1) & (ARRAYLEN - 1)] = r0;
                arr[(s + 2) & (ARRAYLEN - 1)] = r2;
                arr[(s + 3) & (ARRAYLEN - 1)] = null;

                r0.v1 = r3;
                r0.v2 = arr[(s + 4) & (ARRAYLEN - 1)];
            }
            if (r1 != null) {
                Object old = r1.p3; sinkObj = old;
                r1.p1 = r2;
                r1.p2 = r3;
                r1.p3 = null;
                r1.a  = roots[(s + 6) & (ROOTS - 1)];
                r1.b  = r0;
                r1.c  = r2;
                r1.d  = r3;

                arr[(s + 4) & (ARRAYLEN - 1)] = r1;
                arr[(s + 5) & (ARRAYLEN - 1)] = r3;
                arr[(s + 6) & (ARRAYLEN - 1)] = r0;
                arr[(s + 7) & (ARRAYLEN - 1)] = null;

                r1.v1 = arr[(s + 6) & (ARRAYLEN - 1)];
                r1.v2 = r2;
            }
            if (r2 != null) {
                Object old = r2.p1; sinkObj = old;
                r2.p1 = roots[(s + 7) & (ROOTS - 1)];
                r2.p2 = r0;
                r2.p3 = r1;
                r2.a  = r3;
                r2.b  = roots[(s + 8) & (ROOTS - 1)];
                r2.c  = null;
                r2.d  = r1;

                arr[(s + 8)  & (ARRAYLEN - 1)] = r2;
                arr[(s + 9)  & (ARRAYLEN - 1)] = r0;
                arr[(s + 10) & (ARRAYLEN - 1)] = r1;
                arr[(s + 11) & (ARRAYLEN - 1)] = null;

                r2.v1 = arr[(s + 10) & (ARRAYLEN - 1)];
                r2.v2 = r3;
            }
            if (r3 != null) {
                Object old = r3.p2; sinkObj = old;
                r3.p1 = r2;
                r3.p2 = roots[(s + 9) & (ROOTS - 1)];
                r3.p3 = null;
                r3.a  = r1;
                r3.b  = r0;
                r3.c  = roots[(s + 10) & (ROOTS - 1)];
                r3.d  = r2;

                arr[(s + 12) & (ARRAYLEN - 1)] = r3;
                arr[(s + 13) & (ARRAYLEN - 1)] = r0;
                arr[(s + 14) & (ARRAYLEN - 1)] = r2;
                arr[(s + 15) & (ARRAYLEN - 1)] = null;

                r3.v1 = arr[(s + 12) & (ARRAYLEN - 1)];
                r3.v2 = r2;
            }

            acc ^= (r0 != null ? r0.id : 0)
                 ^ (r1 != null ? r1.id : 0)
                 ^ (r2 != null ? r2.id : 0)
                 ^ (r3 != null ? r3.id : 0);
        }

        sinkInt ^= acc;
        return acc;
    }

    static int bomb5(int seed) {
        int acc = seed;
        int base = seed + 50_000;

        for (int b = 0; b < 16; b++) {
            int s = base + (b * 32);

            int i0 = (s + 0) & (ROOTS - 1);
            int i1 = (s + 1) & (ROOTS - 1);
            int i2 = (s + 2) & (ROOTS - 1);
            int i3 = (s + 3) & (ROOTS - 1);

            Node r0 = roots[i0];
            Node r1 = roots[i1];
            Node r2 = roots[i2];
            Node r3 = roots[i3];

            if (r0 != null) {
                Object old = r0.p3; sinkObj = old;
                r0.p1 = r1;
                r0.p2 = roots[(s + 4) & (ROOTS - 1)];
                r0.p3 = r2;
                r0.a  = r3;
                r0.b  = r2;
                r0.c  = null;
                r0.d  = roots[(s + 5) & (ROOTS - 1)];

                arr[(s + 0) & (ARRAYLEN - 1)] = r0;
                arr[(s + 1) & (ARRAYLEN - 1)] = r2;
                arr[(s + 2) & (ARRAYLEN - 1)] = r1;
                arr[(s + 3) & (ARRAYLEN - 1)] = null;

                r0.v1 = r1;
                r0.v2 = arr[(s + 2) & (ARRAYLEN - 1)];
            }
            if (r1 != null) {
                Object old = r1.p1; sinkObj = old;
                r1.p1 = r0;
                r1.p2 = r2;
                r1.p3 = roots[(s + 6) & (ROOTS - 1)];
                r1.a  = null;
                r1.b  = r3;
                r1.c  = roots[(s + 7) & (ROOTS - 1)];
                r1.d  = r2;

                arr[(s + 4) & (ARRAYLEN - 1)] = r1;
                arr[(s + 5) & (ARRAYLEN - 1)] = r2;
                arr[(s + 6) & (ARRAYLEN - 1)] = r0;
                arr[(s + 7) & (ARRAYLEN - 1)] = null;

                r1.v1 = arr[(s + 6) & (ARRAYLEN - 1)];
                r1.v2 = r2;
            }
            if (r2 != null) {
                Object old = r2.p2; sinkObj = old;
                r2.p1 = roots[(s + 8) & (ROOTS - 1)];
                r2.p2 = r0;
                r2.p3 = r1;
                r2.a  = r3;
                r2.b  = roots[(s + 9) & (ROOTS - 1)];
                r2.c  = null;
                r2.d  = r1;

                arr[(s + 8)  & (ARRAYLEN - 1)] = r2;
                arr[(s + 9)  & (ARRAYLEN - 1)] = r0;
                arr[(s + 10) & (ARRAYLEN - 1)] = r1;
                arr[(s + 11) & (ARRAYLEN - 1)] = null;

                r2.v1 = arr[(s + 10) & (ARRAYLEN - 1)];
                r2.v2 = r3;
            }
            if (r3 != null) {
                Object old = r3.p3; sinkObj = old;
                r3.p1 = r2;
                r3.p2 = r1;
                r3.p3 = roots[(s + 10) & (ROOTS - 1)];
                r3.a  = r0;
                r3.b  = null;
                r3.c  = roots[(s + 11) & (ROOTS - 1)];
                r3.d  = r2;

                arr[(s + 12) & (ARRAYLEN - 1)] = r3;
                arr[(s + 13) & (ARRAYLEN - 1)] = r2;
                arr[(s + 14) & (ARRAYLEN - 1)] = r1;
                arr[(s + 15) & (ARRAYLEN - 1)] = null;

                r3.v1 = arr[(s + 12) & (ARRAYLEN - 1)];
                r3.v2 = r0;
            }

            acc ^= (r0 != null ? r0.id : 0)
                 ^ (r1 != null ? r1.id : 0)
                 ^ (r2 != null ? r2.id : 0)
                 ^ (r3 != null ? r3.id : 0);
        }

        sinkInt ^= acc;
        return acc;
    }

    static int bomb6(int seed) {
        int acc = seed;
        int base = seed + 60_000;

        for (int b = 0; b < 16; b++) {
            int s = base + (b * 32);

            int i0 = (s + 0) & (ROOTS - 1);
            int i1 = (s + 1) & (ROOTS - 1);
            int i2 = (s + 2) & (ROOTS - 1);
            int i3 = (s + 3) & (ROOTS - 1);

            Node r0 = roots[i0];
            Node r1 = roots[i1];
            Node r2 = roots[i2];
            Node r3 = roots[i3];

            if (r0 != null) {
                Object old = r0.p1; sinkObj = old;
                r0.p1 = r1;
                r0.p2 = r2;
                r0.p3 = roots[(s + 4) & (ROOTS - 1)];
                r0.a  = r3;
                r0.b  = r2;
                r0.c  = null;
                r0.d  = roots[(s + 5) & (ROOTS - 1)];

                arr[(s + 0) & (ARRAYLEN - 1)] = r0;
                arr[(s + 1) & (ARRAYLEN - 1)] = r1;
                arr[(s + 2) & (ARRAYLEN - 1)] = r2;
                arr[(s + 3) & (ARRAYLEN - 1)] = null;

                r0.v1 = r1;
                r0.v2 = arr[(s + 2) & (ARRAYLEN - 1)];
            }
            if (r1 != null) {
                Object old = r1.p1; sinkObj = old;
                r1.p1 = r0;
                r1.p2 = r2;
                r1.p3 = roots[(s + 6) & (ROOTS - 1)];
                r1.a  = null;
                r1.b  = r3;
                r1.c  = roots[(s + 7) & (ROOTS - 1)];
                r1.d  = r2;

                arr[(s + 4) & (ARRAYLEN - 1)] = r1;
                arr[(s + 5) & (ARRAYLEN - 1)] = r2;
                arr[(s + 6) & (ARRAYLEN - 1)] = r0;
                arr[(s + 7) & (ARRAYLEN - 1)] = null;

                r1.v1 = arr[(s + 6) & (ARRAYLEN - 1)];
                r1.v2 = r2;
            }
            if (r2 != null) {
                Object old = r2.p2; sinkObj = old;
                r2.p1 = roots[(s + 8) & (ROOTS - 1)];
                r2.p2 = r0;
                r2.p3 = r1;
                r2.a  = r3;
                r2.b  = roots[(s + 9) & (ROOTS - 1)];
                r2.c  = null;
                r2.d  = r1;

                arr[(s + 8)  & (ARRAYLEN - 1)] = r2;
                arr[(s + 9)  & (ARRAYLEN - 1)] = r0;
                arr[(s + 10) & (ARRAYLEN - 1)] = r1;
                arr[(s + 11) & (ARRAYLEN - 1)] = null;

                r2.v1 = arr[(s + 10) & (ARRAYLEN - 1)];
                r2.v2 = r3;
            }
            if (r3 != null) {
                Object old = r3.p3; sinkObj = old;
                r3.p1 = r2;
                r3.p2 = r1;
                r3.p3 = roots[(s + 10) & (ROOTS - 1)];
                r3.a  = r0;
                r3.b  = null;
                r3.c  = roots[(s + 11) & (ROOTS - 1)];
                r3.d  = r2;

                arr[(s + 12) & (ARRAYLEN - 1)] = r3;
                arr[(s + 13) & (ARRAYLEN - 1)] = r2;
                arr[(s + 14) & (ARRAYLEN - 1)] = r1;
                arr[(s + 15) & (ARRAYLEN - 1)] = null;

                r3.v1 = arr[(s + 12) & (ARRAYLEN - 1)];
                r3.v2 = r0;
            }

            acc ^= (r0 != null ? r0.id : 0)
                 ^ (r1 != null ? r1.id : 0)
                 ^ (r2 != null ? r2.id : 0)
                 ^ (r3 != null ? r3.id : 0);
        }

        sinkInt ^= acc;
        return acc;
    }

    static int bomb7(int seed) {
        int acc = seed;
        int base = seed + 70_000;

        for (int b = 0; b < 16; b++) {
            int s = base + (b * 32);

            int i0 = (s + 0) & (ROOTS - 1);
            int i1 = (s + 1) & (ROOTS - 1);
            int i2 = (s + 2) & (ROOTS - 1);
            int i3 = (s + 3) & (ROOTS - 1);

            Node r0 = roots[i0];
            Node r1 = roots[i1];
            Node r2 = roots[i2];
            Node r3 = roots[i3];

            if (r0 != null) {
                Object old = r0.p2; sinkObj = old;
                r0.p1 = roots[(s + 4) & (ROOTS - 1)];
                r0.p2 = r1;
                r0.p3 = r2;
                r0.a  = r3;
                r0.b  = null;
                r0.c  = roots[(s + 5) & (ROOTS - 1)];
                r0.d  = r2;

                arr[(s + 0) & (ARRAYLEN - 1)] = r1;
                arr[(s + 1) & (ARRAYLEN - 1)] = r0;
                arr[(s + 2) & (ARRAYLEN - 1)] = r2;
                arr[(s + 3) & (ARRAYLEN - 1)] = null;

                r0.v1 = r3;
                r0.v2 = arr[(s + 4) & (ARRAYLEN - 1)];
            }
            if (r1 != null) {
                Object old = r1.p3; sinkObj = old;
                r1.p1 = r2;
                r1.p2 = r3;
                r1.p3 = null;
                r1.a  = roots[(s + 6) & (ROOTS - 1)];
                r1.b  = r0;
                r1.c  = r2;
                r1.d  = r3;

                arr[(s + 4) & (ARRAYLEN - 1)] = r1;
                arr[(s + 5) & (ARRAYLEN - 1)] = r3;
                arr[(s + 6) & (ARRAYLEN - 1)] = r0;
                arr[(s + 7) & (ARRAYLEN - 1)] = null;

                r1.v1 = arr[(s + 6) & (ARRAYLEN - 1)];
                r1.v2 = r2;
            }
            if (r2 != null) {
                Object old = r2.p1; sinkObj = old;
                r2.p1 = roots[(s + 7) & (ROOTS - 1)];
                r2.p2 = r0;
                r2.p3 = r1;
                r2.a  = r3;
                r2.b  = roots[(s + 8) & (ROOTS - 1)];
                r2.c  = null;
                r2.d  = r1;

                arr[(s + 8)  & (ARRAYLEN - 1)] = r2;
                arr[(s + 9)  & (ARRAYLEN - 1)] = r0;
                arr[(s + 10) & (ARRAYLEN - 1)] = r1;
                arr[(s + 11) & (ARRAYLEN - 1)] = null;

                r2.v1 = arr[(s + 10) & (ARRAYLEN - 1)];
                r2.v2 = r3;
            }
            if (r3 != null) {
                Object old = r3.p2; sinkObj = old;
                r3.p1 = r2;
                r3.p2 = roots[(s + 9) & (ROOTS - 1)];
                r3.p3 = null;
                r3.a  = r1;
                r3.b  = r0;
                r3.c  = roots[(s + 10) & (ROOTS - 1)];
                r3.d  = r2;

                arr[(s + 12) & (ARRAYLEN - 1)] = r3;
                arr[(s + 13) & (ARRAYLEN - 1)] = r0;
                arr[(s + 14) & (ARRAYLEN - 1)] = r2;
                arr[(s + 15) & (ARRAYLEN - 1)] = null;

                r3.v1 = arr[(s + 12) & (ARRAYLEN - 1)];
                r3.v2 = r2;
            }

            acc ^= (r0 != null ? r0.id : 0)
                 ^ (r1 != null ? r1.id : 0)
                 ^ (r2 != null ? r2.id : 0)
                 ^ (r3 != null ? r3.id : 0);
        }

        sinkInt ^= acc;
        return acc;
    }

    static int bomb8(int seed) {
        int acc = seed;
        int base = seed + 80_000;

        for (int b = 0; b < 16; b++) {
            int s = base + (b * 32);

            int i0 = (s + 0) & (ROOTS - 1);
            int i1 = (s + 1) & (ROOTS - 1);
            int i2 = (s + 2) & (ROOTS - 1);
            int i3 = (s + 3) & (ROOTS - 1);

            Node r0 = roots[i0];
            Node r1 = roots[i1];
            Node r2 = roots[i2];
            Node r3 = roots[i3];

            if (r0 != null) {
                Object old = r0.p3; sinkObj = old;
                r0.p1 = r1;
                r0.p2 = roots[(s + 4) & (ROOTS - 1)];
                r0.p3 = r2;
                r0.a  = r3;
                r0.b  = r2;
                r0.c  = null;
                r0.d  = roots[(s + 5) & (ROOTS - 1)];

                arr[(s + 0) & (ARRAYLEN - 1)] = r0;
                arr[(s + 1) & (ARRAYLEN - 1)] = r2;
                arr[(s + 2) & (ARRAYLEN - 1)] = r1;
                arr[(s + 3) & (ARRAYLEN - 1)] = null;

                r0.v1 = r1;
                r0.v2 = arr[(s + 2) & (ARRAYLEN - 1)];
            }
            if (r1 != null) {
                Object old = r1.p1; sinkObj = old;
                r1.p1 = r0;
                r1.p2 = r2;
                r1.p3 = roots[(s + 6) & (ROOTS - 1)];
                r1.a  = null;
                r1.b  = r3;
                r1.c  = roots[(s + 7) & (ROOTS - 1)];
                r1.d  = r2;

                arr[(s + 4) & (ARRAYLEN - 1)] = r1;
                arr[(s + 5) & (ARRAYLEN - 1)] = r2;
                arr[(s + 6) & (ARRAYLEN - 1)] = r0;
                arr[(s + 7) & (ARRAYLEN - 1)] = null;

                r1.v1 = arr[(s + 6) & (ARRAYLEN - 1)];
                r1.v2 = r2;
            }
            if (r2 != null) {
                Object old = r2.p2; sinkObj = old;
                r2.p1 = roots[(s + 8) & (ROOTS - 1)];
                r2.p2 = r0;
                r2.p3 = r1;
                r2.a  = r3;
                r2.b  = roots[(s + 9) & (ROOTS - 1)];
                r2.c  = null;
                r2.d  = r1;

                arr[(s + 8)  & (ARRAYLEN - 1)] = r2;
                arr[(s + 9)  & (ARRAYLEN - 1)] = r0;
                arr[(s + 10) & (ARRAYLEN - 1)] = r1;
                arr[(s + 11) & (ARRAYLEN - 1)] = null;

                r2.v1 = arr[(s + 10) & (ARRAYLEN - 1)];
                r2.v2 = r3;
            }
            if (r3 != null) {
                Object old = r3.p3; sinkObj = old;
                r3.p1 = r2;
                r3.p2 = r1;
                r3.p3 = roots[(s + 10) & (ROOTS - 1)];
                r3.a  = r0;
                r3.b  = null;
                r3.c  = roots[(s + 11) & (ROOTS - 1)];
                r3.d  = r2;

                arr[(s + 12) & (ARRAYLEN - 1)] = r3;
                arr[(s + 13) & (ARRAYLEN - 1)] = r2;
                arr[(s + 14) & (ARRAYLEN - 1)] = r1;
                arr[(s + 15) & (ARRAYLEN - 1)] = null;

                r3.v1 = arr[(s + 12) & (ARRAYLEN - 1)];
                r3.v2 = r0;
            }

            acc ^= (r0 != null ? r0.id : 0)
                 ^ (r1 != null ? r1.id : 0)
                 ^ (r2 != null ? r2.id : 0)
                 ^ (r3 != null ? r3.id : 0);
        }

        sinkInt ^= acc;
        return acc;
    }

    static int bomb9(int seed) {
        int acc = seed;
        int base = seed + 90_000;

        for (int b = 0; b < 16; b++) {
            int s = base + (b * 32);

            int i0 = (s + 0) & (ROOTS - 1);
            int i1 = (s + 1) & (ROOTS - 1);
            int i2 = (s + 2) & (ROOTS - 1);
            int i3 = (s + 3) & (ROOTS - 1);

            Node r0 = roots[i0];
            Node r1 = roots[i1];
            Node r2 = roots[i2];
            Node r3 = roots[i3];

            if (r0 != null) {
                Object old = r0.p1; sinkObj = old;
                r0.p1 = r1;
                r0.p2 = r2;
                r0.p3 = roots[(s + 4) & (ROOTS - 1)];
                r0.a  = r3;
                r0.b  = r2;
                r0.c  = null;
                r0.d  = roots[(s + 5) & (ROOTS - 1)];

                arr[(s + 0) & (ARRAYLEN - 1)] = r0;
                arr[(s + 1) & (ARRAYLEN - 1)] = r1;
                arr[(s + 2) & (ARRAYLEN - 1)] = r2;
                arr[(s + 3) & (ARRAYLEN - 1)] = null;

                r0.v1 = r1;
                r0.v2 = arr[(s + 2) & (ARRAYLEN - 1)];
            }
            if (r1 != null) {
                Object old = r1.p1; sinkObj = old;
                r1.p1 = r0;
                r1.p2 = r2;
                r1.p3 = roots[(s + 6) & (ROOTS - 1)];
                r1.a  = null;
                r1.b  = r3;
                r1.c  = roots[(s + 7) & (ROOTS - 1)];
                r1.d  = r2;

                arr[(s + 4) & (ARRAYLEN - 1)] = r1;
                arr[(s + 5) & (ARRAYLEN - 1)] = r2;
                arr[(s + 6) & (ARRAYLEN - 1)] = r0;
                arr[(s + 7) & (ARRAYLEN - 1)] = null;

                r1.v1 = arr[(s + 6) & (ARRAYLEN - 1)];
                r1.v2 = r2;
            }
            if (r2 != null) {
                Object old = r2.p2; sinkObj = old;
                r2.p1 = roots[(s + 8) & (ROOTS - 1)];
                r2.p2 = r0;
                r2.p3 = r1;
                r2.a  = r3;
                r2.b  = roots[(s + 9) & (ROOTS - 1)];
                r2.c  = null;
                r2.d  = r1;

                arr[(s + 8)  & (ARRAYLEN - 1)] = r2;
                arr[(s + 9)  & (ARRAYLEN - 1)] = r0;
                arr[(s + 10) & (ARRAYLEN - 1)] = r1;
                arr[(s + 11) & (ARRAYLEN - 1)] = null;

                r2.v1 = arr[(s + 10) & (ARRAYLEN - 1)];
                r2.v2 = r3;
            }
            if (r3 != null) {
                Object old = r3.p3; sinkObj = old;
                r3.p1 = r2;
                r3.p2 = r1;
                r3.p3 = roots[(s + 10) & (ROOTS - 1)];
                r3.a  = r0;
                r3.b  = null;
                r3.c  = roots[(s + 11) & (ROOTS - 1)];
                r3.d  = r2;

                arr[(s + 12) & (ARRAYLEN - 1)] = r3;
                arr[(s + 13) & (ARRAYLEN - 1)] = r2;
                arr[(s + 14) & (ARRAYLEN - 1)] = r1;
                arr[(s + 15) & (ARRAYLEN - 1)] = null;

                r3.v1 = arr[(s + 12) & (ARRAYLEN - 1)];
                r3.v2 = r0;
            }

            acc ^= (r0 != null ? r0.id : 0)
                 ^ (r1 != null ? r1.id : 0)
                 ^ (r2 != null ? r2.id : 0)
                 ^ (r3 != null ? r3.id : 0);
        }

        sinkInt ^= acc;
        return acc;
    }
}

