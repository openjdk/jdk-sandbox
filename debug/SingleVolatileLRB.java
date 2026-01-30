public class SingleVolatileLRB {
    public volatile Point point = new Point(1,2);

    public static void main(String[] args) {
        for (int i=0; i<100000000; i++) {
          SingleVolatileLRB lrb = new SingleVolatileLRB();
          lrb.test(new Point(3,4));
        }
    }

    public void test(Point new_pt) {
        if (this.point != null) {
          this.point = new_pt;
        }
    }

    static class Point {
        private boolean payload[] = new boolean[1024];
        int x, y;
        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
