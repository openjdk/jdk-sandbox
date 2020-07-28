

public class Allocation {

    public long p;
    public long word_size;

    public Allocation(long p, long word_size) {
        this.p = p;
        this.word_size = word_size;
    }

    public boolean isNull() {
        return p == 0;
    }

    @Override
    public String toString() {
        return "Allocation{" +
                "p=" + p +
                ", word_size=" + word_size +
                '}';
    }
}
