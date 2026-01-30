import java.lang.ref.WeakReference;

public class WeakRef {
    public static void main(String[] args) {
        WeakReference<Point> tgt = new WeakReference<>(new Point(1, 2));
        for (int i=0; i<100000; i++) test(tgt);
        System.out.println(test(tgt));
    }

    public static Point test(WeakReference<Point> tgt) {
        return tgt.get();
    }

    public static void gc() {
        System.gc();
    }

    static class Point {
        int x, y;
        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
