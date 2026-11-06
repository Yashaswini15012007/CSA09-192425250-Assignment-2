import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Leaderboard<T> {
    private final List<T> items;
    private final Comparator<? super T> order;

    public Leaderboard(List<? extends T> items, Comparator<? super T> order) {
        this.items = new ArrayList<>(items);
        this.order = order;
    }

    public List<T> ranking() {
        List<T> sorted = new ArrayList<>(items);
        sorted.sort(order);
        return sorted;
    }
}