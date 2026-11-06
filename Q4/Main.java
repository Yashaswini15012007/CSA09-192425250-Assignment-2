import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Main {
    private static final int REPEATS = 10;
    private static final int LOOKUPS = 100_000;
    private static final int[] SIZES = {1_000, 100_000, 1_000_000};
    private static final String[] TYPES = {"HashMap", "TreeMap", "LinkedHashMap", "SortedArray"};
    private record Sample(long nanos, long checksum, boolean orderMatches) { }

    public static void main(String[] args) throws Exception {
        Path csv = Path.of("results.csv");
        Files.writeString(csv, "size,structure,operation,run,operations,elapsed_ns,ns_per_operation,checksum,order_matches\n");
        List<String> report = new ArrayList<>();
        report.add("Q4 pharmacy inventory benchmark; 10 measured repetitions; Java " + Runtime.version());
        for (int size : SIZES) {
            int[] insertionKeys = new int[size];
            for (int i = 0; i < size; i++) insertionKeys[i] = size - i;
            report.add("\n" + size + " medicines (insertion order is descending code):");
            for (String type : TYPES) {
                for (String operation : List.of("put", "lookup", "code_report", "insertion_report")) {
                    int count = operation.equals("lookup") ? LOOKUPS : size;
                    measure(type, operation, insertionKeys, count);
                    long[] times = new long[REPEATS];
                    boolean orderMatches = true;
                    for (int run = 1; run <= REPEATS; run++) {
                        Sample sample = measure(type, operation, insertionKeys, count);
                        times[run - 1] = sample.nanos();
                        orderMatches &= sample.orderMatches();
                        append(csv, size, type, operation, run, count, sample);
                    }
                    report.add(String.format("%-13s %-17s avg=%9.3f ms min=%9.3f ms max=%9.3f ms orderMatches=%s",
                            type, operation, average(times) / 1e6, min(times) / 1e6, max(times) / 1e6,
                            operation.contains("report") ? orderMatches : "n/a"));
                }
            }
        }
        report.add("\nLookup target: <1 ms per lookup; batch target: <100 ms per 100,000 lookups.");
        report.add("HashMap iteration order is intentionally not asserted; reports sort or follow a tracked insertion-key array.");
        Files.writeString(Path.of("output.txt"), String.join(System.lineSeparator(), report) + System.lineSeparator());
        System.out.println(String.join(System.lineSeparator(), report));
    }

    private static Sample measure(String type, String operation, int[] insertionKeys, int count) {
        if (type.equals("SortedArray")) {
            if (operation.equals("put")) {
                int[] keys = insertionKeys.clone();
                long start = System.nanoTime();
                Arrays.sort(keys);
                return new Sample(System.nanoTime() - start, keys.length, isAscending(keys));
            }
            int[] sorted = insertionKeys.clone();
            Arrays.sort(sorted);
            if (operation.equals("lookup")) {
                long start = System.nanoTime();
                long hits = 0;
                for (int i = 0; i < count; i++) if (Arrays.binarySearch(sorted, insertionKeys[i % insertionKeys.length]) >= 0) hits++;
                return new Sample(System.nanoTime() - start, hits, true);
            }
            long start = System.nanoTime();
            long checksum = 0;
            boolean matches = true;
            if (operation.equals("code_report")) {
                for (int i = 0; i < sorted.length; i++) {
                    checksum += sorted[i];
                    if (i > 0 && sorted[i] <= sorted[i - 1]) matches = false;
                }
            } else {
                for (int i = 0; i < insertionKeys.length; i++) {
                    int key = insertionKeys[i];
                    int value = sorted[Arrays.binarySearch(sorted, key)];
                    checksum += value;
                    if (value != insertionKeys[i]) matches = false;
                }
            }
            return new Sample(System.nanoTime() - start, checksum, matches);
        }

        Map<Integer, Integer> map = newEmptyMap(type);
        if (!operation.equals("put")) for (int key : insertionKeys) map.put(key, key);
        long start = System.nanoTime();
        long checksum = 0;
        boolean matches = true;
        if (operation.equals("put")) {
            for (int key : insertionKeys) map.put(key, key);
            checksum = map.size();
        } else if (operation.equals("lookup")) {
            for (int i = 0; i < count; i++) checksum += map.get(insertionKeys[i % insertionKeys.length]);
        } else if (operation.equals("code_report")) {
            List<Integer> reportKeys = new ArrayList<>(map.keySet());
            if (!type.equals("TreeMap")) reportKeys.sort(Integer::compareTo);
            for (int i = 0; i < reportKeys.size(); i++) {
                int key = reportKeys.get(i);
                checksum += map.get(key);
                if (i > 0 && reportKeys.get(i - 1) >= key) matches = false;
            }
        } else {
            Iterable<Integer> reportKeys = type.equals("LinkedHashMap") ? map.keySet() : boxed(insertionKeys);
            int index = 0;
            for (int key : reportKeys) {
                checksum += map.get(key);
                if (index >= insertionKeys.length || key != insertionKeys[index]) matches = false;
                index++;
            }
            matches &= index == insertionKeys.length;
        }
        return new Sample(System.nanoTime() - start, checksum, matches);
    }

    private static Map<Integer, Integer> newEmptyMap(String type) {
        return switch (type) {
            case "HashMap" -> new HashMap<>();
            case "TreeMap" -> new TreeMap<>();
            case "LinkedHashMap" -> new LinkedHashMap<>();
            default -> throw new IllegalArgumentException(type);
        };
    }

    private static List<Integer> boxed(int[] keys) {
        List<Integer> values = new ArrayList<>(keys.length);
        for (int key : keys) values.add(key);
        return values;
    }

    private static boolean isAscending(int[] values) {
        for (int i = 1; i < values.length; i++) if (values[i - 1] >= values[i]) return false;
        return true;
    }

    private static void append(Path csv, int size, String type, String operation, int run, int count, Sample sample) throws IOException {
        Files.writeString(csv, String.format("%d,%s,%s,%d,%d,%d,%.2f,%d,%s%n", size, type, operation, run, count,
                sample.nanos(), (double) sample.nanos() / count, sample.checksum(), sample.orderMatches()), StandardOpenOption.APPEND);
    }

    private static double average(long[] values) { long sum = 0; for (long value : values) sum += value; return (double) sum / values.length; }
    private static long min(long[] values) { long result = Long.MAX_VALUE; for (long value : values) result = Math.min(result, value); return result; }
    private static long max(long[] values) { long result = Long.MIN_VALUE; for (long value : values) result = Math.max(result, value); return result; }
}