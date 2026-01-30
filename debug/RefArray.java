public class RefArray {
    private final int size = 1000000;
    private final Object[] src;

    public RefArray() {
        src = new Object[size];
        for (int i = 0; i < size; i++) {
            src[i] = new Object();
        }
    }

    public void test() {
        for (Object t : this.src) {
            dummy(t);
        }
    }

    public void dummy(Object o){
    }

    public static void main(String[] args) {
        RefArray ra = new RefArray();
        for (int i=0; i<100000; i++) ra.test();
    }
}
