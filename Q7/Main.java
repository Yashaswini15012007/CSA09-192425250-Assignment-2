import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final int REPEATS = 10;
    private static final int FIELD_COUNT = 5_000;
    private record Sample(long nanos, long checksum, long memoryDelta) { }

    public static void main(String[] args) throws Exception {
        String[] fields = fields();
        Path csv = Path.of("results.csv");
        Files.writeString(csv, "method,run,field_operations,elapsed_ns,estimated_intermediate_strings,retained_memory_delta_bytes,checksum\n");
        List<String> report = new ArrayList<>();
        report.add("Q7 paired ticket-construction validation; identical 5,000 fields; 10 measured repetitions; Java " + Runtime.version());
        String baselineOutput = concatenate(fields);
        String redesignedOutput = build(fields);
        if (!baselineOutput.equals(redesignedOutput)) throw new IllegalStateException("Output mismatch");
        report.add("Exact output equality: true; output length=" + baselineOutput.length());
        long baselineAverage = 0;
        long builderAverage = 0;
        for (String method : List.of("String", "StringBuilder")) {
            measure(method, fields);
            long[] times = new long[REPEATS];
            long memoryTotal = 0;
            long checksum = 0;
            for (int run = 1; run <= REPEATS; run++) {
                Sample sample = measure(method, fields);
                times[run - 1] = sample.nanos();
                memoryTotal += sample.memoryDelta();
                checksum = sample.checksum();
                long objects = method.equals("String") ? FIELD_COUNT : 1;
                append(csv, method, run, sample, objects);
            }
            double avg = average(times);
            if (method.equals("String")) baselineAverage = (long) avg; else builderAverage = (long) avg;
            report.add(String.format("%-12s avg=%9.3f ms min=%9.3f ms max=%9.3f ms estimated intermediate strings=%d avg retained-memory delta=%d bytes checksum=%d",
                    method, avg / 1e6, min(times) / 1e6, max(times) / 1e6,
                    method.equals("String") ? FIELD_COUNT : 1, memoryTotal / REPEATS, checksum));
        }
        report.add(String.format("Mean time reduction: %.2f%%; speedup: %.2fx; estimated intermediate-string reduction: %.2f%%",
                (1.0 - (double) builderAverage / baselineAverage) * 100.0,
                (double) baselineAverage / builderAverage,
                (1.0 - 1.0 / FIELD_COUNT) * 100.0));
        report.add("Retained memory is measured after GC with the returned output kept alive; it excludes transient allocation totals and is approximate.");
        Files.writeString(Path.of("output.txt"), String.join(System.lineSeparator(), report) + System.lineSeparator());
        System.out.println(String.join(System.lineSeparator(), report));
    }

    private static Sample measure(String method, String[] fields) {
        long start = System.nanoTime();
        String result = method.equals("String") ? concatenate(fields) : build(fields);
        long elapsed = System.nanoTime() - start;
        long checksum = result.length();
        long memory = retainedMemoryDelta(method, fields);
        return new Sample(elapsed, checksum, memory);
    }

    private static String concatenate(String[] fields) {
        String ticket = "";
        for (String field : fields) ticket += field + "|";
        return ticket;
    }

    private static String build(String[] fields) {
        StringBuilder ticket = new StringBuilder(fields.length * 16);
        for (String field : fields) ticket.append(field).append('|');
        return ticket.toString();
    }

    private static long retainedMemoryDelta(String method, String[] fields) {
        System.gc();
        long before = usedMemory();
        String result = method.equals("String") ? concatenate(fields) : build(fields);
        System.gc();
        long after = usedMemory();
        return result.isEmpty() ? 0 : after - before;
    }

    private static String[] fields() {
        String[] values = new String[FIELD_COUNT];
        for (int i = 0; i < FIELD_COUNT; i++) {
            values[i] = switch (i % 8) {
                case 0 -> "PNR" + i;
                case 1 -> "Passenger" + i;
                case 2 -> "Train" + i;
                case 3 -> "From" + i;
                case 4 -> "To" + i;
                case 5 -> "Date" + i;
                case 6 -> "Seat" + i;
                default -> "Fare" + i;
            };
        }
        return values;
    }

    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void append(Path csv, String method, int run, Sample sample, long objects) throws IOException {
        Files.writeString(csv, String.format("%s,%d,%d,%d,%d,%d,%d%n", method, run, FIELD_COUNT,
                sample.nanos(), objects, sample.memoryDelta(), sample.checksum()), StandardOpenOption.APPEND);
    }

    private static double average(long[] values) { long sum = 0; for (long value : values) sum += value; return (double) sum / values.length; }
    private static long min(long[] values) { long result = Long.MAX_VALUE; for (long value : values) result = Math.min(result, value); return result; }
    private static long max(long[] values) { long result = Long.MIN_VALUE; for (long value : values) result = Math.max(result, value); return result; }
}