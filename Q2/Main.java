import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class Main {
    private static final int REPEATS = 10;
    private static final int[] SIZES = {10_000, 100_000, 1_000_000};
    private static final String[] TYPES = {"ArrayList", "LinkedList", "ArrayDeque", "ManualArray"};
    private record RunResult(long elapsedNanos, long checksum) { }

    public static void main(String[] args) throws Exception {
        Path csv = Path.of("results.csv");
        Files.writeString(csv, "size,collection,operation,run,operations,elapsed_ns,ns_per_operation,checksum\n");
        List<String> output = new ArrayList<>();
        output.add("Q2 collection benchmark; 10 measured repetitions per applicable operation; Java " + Runtime.version());
        for (int size : SIZES) {
            output.add("\nSize " + size + ":");
            for (String type : TYPES) {
                for (String operation : List.of("add_end", "front_insert_remove", "middle_insert_remove", "get_kth", "full_iteration")) {
                    if (!supports(type, operation)) continue;
                    int count = operation.equals("add_end") ? 1_000 : operation.contains("insert_remove") ? Math.max(5, Math.min(100, 5_000_000 / size)) : operation.equals("get_kth") ? Math.max(100, 100_000_000 / size) : 1;
                    long[] samples = new long[REPEATS];
                    long checksum = 0;
                    run(type, operation, size, count);
                    for (int run = 1; run <= REPEATS; run++) {
                        RunResult result = run(type, operation, size, count);
                        checksum = result.checksum();
                        samples[run - 1] = result.elapsedNanos();
                        append(csv, size, type, operation, run, count, samples[run - 1], checksum);
                    }
                    output.add(String.format("%-11s %-23s avg=%9.3f ms min=%9.3f ms max=%9.3f ms (%d operations/sample, %.1f ns/op)",
                            type, operation, average(samples) / 1e6, min(samples) / 1e6, max(samples) / 1e6,
                            count, average(samples) / count));
                }
            }
        }
        output.add("\nArrayDeque supports efficient operations at both ends and iteration, but has no indexed get or middle insertion.");
        Files.writeString(Path.of("output.txt"), String.join(System.lineSeparator(), output) + System.lineSeparator());
        System.out.println(String.join(System.lineSeparator(), output));
    }

    private static boolean supports(String type, String operation) {
        if (type.equals("ArrayDeque") && (operation.equals("middle_insert_remove") || operation.equals("get_kth"))) return false;
        return true;
    }

    private static RunResult run(String type, String operation, int size, int count) {
        if (type.equals("ManualArray")) {
            int[] values = new int[size + count + 2];
            for (int i = 0; i < size; i++) values[i] = i;
            long start = System.nanoTime();
            long checksum = runArray(values, operation, size, count);
            return new RunResult(System.nanoTime() - start, checksum);
        }
        if (type.equals("ArrayDeque")) {
            ArrayDeque<Integer> deque = new ArrayDeque<>(size + count + 2);
            for (int i = 0; i < size; i++) deque.addLast(i);
            long start = System.nanoTime();
            long checksum = runDeque(deque, operation, count);
            return new RunResult(System.nanoTime() - start, checksum);
        }
        List<Integer> list = type.equals("ArrayList") ? new ArrayList<>(size + count + 2) : new LinkedList<>();
        for (int i = 0; i < size; i++) list.add(i);
        long start = System.nanoTime();
        long checksum = runList(list, operation, count);
        return new RunResult(System.nanoTime() - start, checksum);
    }

    private static long runList(List<Integer> list, String operation, int count) {
        long checksum = 0;
        switch (operation) {
            case "add_end" -> { for (int i = 0; i < count; i++) list.add(i); return list.size(); }
            case "front_insert_remove" -> {
                for (int i = 0; i < count; i++) { list.add(0, -i); checksum += list.remove(0); }
            }
            case "middle_insert_remove" -> {
                for (int i = 0; i < count; i++) { int middle = list.size() / 2; list.add(middle, -i); checksum += list.remove(middle); }
            }
            case "get_kth" -> { for (int i = 0; i < count; i++) checksum += list.get((i * 997) % list.size()); }
            case "full_iteration" -> { for (int value : list) checksum += value; }
            default -> throw new IllegalArgumentException(operation);
        }
        return checksum;
    }

    private static long runDeque(Deque<Integer> deque, String operation, int count) {
        long checksum = 0;
        switch (operation) {
            case "add_end" -> { for (int i = 0; i < count; i++) deque.addLast(i); return deque.size(); }
            case "front_insert_remove" -> {
                for (int i = 0; i < count; i++) { deque.addFirst(-i); checksum += deque.removeFirst(); }
            }
            case "full_iteration" -> { for (int value : deque) checksum += value; }
            default -> throw new IllegalArgumentException(operation);
        }
        return checksum;
    }

    private static long runArray(int[] values, String operation, int size, int count) {
        long checksum = 0;
        switch (operation) {
            case "add_end" -> { for (int i = 0; i < count; i++) values[size + i] = i; return size + count; }
            case "front_insert_remove" -> {
                for (int i = 0; i < count; i++) { System.arraycopy(values, 0, values, 1, size); values[0] = -i; checksum += values[0]; System.arraycopy(values, 1, values, 0, size); }
            }
            case "middle_insert_remove" -> {
                for (int i = 0; i < count; i++) { int middle = size / 2; System.arraycopy(values, middle, values, middle + 1, size - middle); values[middle] = -i; checksum += values[middle]; System.arraycopy(values, middle + 1, values, middle, size - middle); }
            }
            case "get_kth" -> { for (int i = 0; i < count; i++) checksum += values[(i * 997) % size]; }
            case "full_iteration" -> { for (int i = 0; i < size; i++) checksum += values[i]; }
            default -> throw new IllegalArgumentException(operation);
        }
        return checksum;
    }

    private static void append(Path csv, int size, String type, String operation, int run, int count, long nanos, long checksum) throws IOException {
        Files.writeString(csv, String.format("%d,%s,%s,%d,%d,%d,%.2f,%d%n", size, type, operation, run, count, nanos,
                (double) nanos / count, checksum), StandardOpenOption.APPEND);
    }

    private static double average(long[] values) { long sum = 0; for (long value : values) sum += value; return (double) sum / values.length; }
    private static long min(long[] values) { long result = Long.MAX_VALUE; for (long value : values) result = Math.min(result, value); return result; }
    private static long max(long[] values) { long result = Long.MIN_VALUE; for (long value : values) result = Math.max(result, value); return result; }
}