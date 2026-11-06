import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static final int REPEATS = 10;
    private static final String[] METHODS = {"String", "StringBuilder", "StringBuffer"};

    private record Workload(String name, int tickets, int fieldsPerTicket) {
        long estimatedFields() {
            return (long) tickets * fieldsPerTicket;
        }
    }

    private record BuildResult(long checksum, String retained) { }

    public static void main(String[] args) throws Exception {
        List<Workload> workloads = List.of(
                new Workload("short_ticket", 1, 8),
                new Workload("bulk_10000x50", 10_000, 50),
                new Workload("custom_5000_fields", 1, 5_000));
        Path csv = Path.of("results.csv");
        Files.writeString(csv, "workload,method,run,elapsed_ns,estimated_intermediate_strings,used_memory_delta_bytes,checksum\n");
        List<String> report = new ArrayList<>();
        report.add("Q1 String construction benchmark (" + REPEATS + " measured repetitions; Java " + Runtime.version() + ")");

        for (Workload workload : workloads) {
            report.add("\n" + workload.name() + ": tickets=" + workload.tickets() + ", fields/ticket=" + workload.fieldsPerTicket());
            long expectedChecksum = -1;
            for (String method : METHODS) {
                build(workload, method);
                long[] samples = new long[REPEATS];
                long checksum = 0;
                long memoryDelta = 0;
                for (int run = 1; run <= REPEATS; run++) {
                    long start = System.nanoTime();
                    BuildResult result = build(workload, method);
                    samples[run - 1] = System.nanoTime() - start;
                    checksum = result.checksum();
                    long objects = estimatedIntermediateStrings(workload, method);
                    long memory = retainedMemoryDelta(workload, method);
                    memoryDelta += memory;
                    appendCsv(csv, workload.name(), method, run, samples[run - 1], objects, memory, checksum);
                }
                if (expectedChecksum < 0) expectedChecksum = checksum;
                if (checksum != expectedChecksum) throw new IllegalStateException("Output mismatch for " + method);
                report.add(String.format("%-12s avg=%.3f ms min=%.3f ms max=%.3f ms estimated intermediates=%d avg retained-memory delta=%d bytes checksum=%d",
                        method, average(samples) / 1e6, minimum(samples) / 1e6, maximum(samples) / 1e6,
                        estimatedIntermediateStrings(workload, method), memoryDelta / REPEATS, checksum));
            }
        }

        report.add("\nAlternative builders on the short 8-field ticket (10 repetitions each):");
        Workload shortTicket = workloads.get(0);
        for (String method : List.of("String.join", "StringJoiner", "String.format")) {
            buildAlternative(shortTicket, method);
            long[] samples = new long[REPEATS];
            long checksum = 0;
            for (int i = 0; i < REPEATS; i++) {
                long start = System.nanoTime();
                checksum = buildAlternative(shortTicket, method).checksum();
                samples[i] = System.nanoTime() - start;
                appendCsv(csv, shortTicket.name(), method, i + 1, samples[i], 1, 0, checksum);
            }
            report.add(String.format("%-12s avg=%.3f ms min=%.3f ms max=%.3f ms checksum=%d",
                    method, average(samples) / 1e6, minimum(samples) / 1e6, maximum(samples) / 1e6, checksum));
        }
        report.add("\nShared multithreaded log (8 threads x 20,000 appends):");
        report.add("StringBuilder: " + sharedAppendTest(false));
        report.add("StringBuffer:  " + sharedAppendTest(true));
        report.add("Memory deltas are approximate retained-heap deltas after GC; JVM heap sizing and object layout affect them.");
        report.add("Object estimates count one resulting String per String concatenation loop iteration; they are source-level estimates, not allocation-profiler counts.");
        Files.writeString(Path.of("output.txt"), String.join(System.lineSeparator(), report) + System.lineSeparator());
        System.out.println(String.join(System.lineSeparator(), report));
    }

    private static BuildResult build(Workload workload, String method) {
        long checksum = 0;
        String retained = "";
        for (int ticket = 0; ticket < workload.tickets(); ticket++) {
            if (method.equals("String")) {
                String text = "";
                for (int field = 0; field < workload.fieldsPerTicket(); field++) text += value(field) + "|";
                retained = text;
            } else if (method.equals("StringBuilder")) {
                StringBuilder text = new StringBuilder();
                for (int field = 0; field < workload.fieldsPerTicket(); field++) text.append(value(field)).append('|');
                retained = text.toString();
            } else {
                StringBuffer text = new StringBuffer();
                for (int field = 0; field < workload.fieldsPerTicket(); field++) text.append(value(field)).append('|');
                retained = text.toString();
            }
            checksum += retained.length();
        }
        return new BuildResult(checksum, retained);
    }

    private static BuildResult buildAlternative(Workload workload, String method) {
        String[] fields = new String[workload.fieldsPerTicket()];
        for (int i = 0; i < fields.length; i++) fields[i] = value(i);
        String text;
        switch (method) {
            case "String.join" -> text = String.join("|", fields) + "|";
            case "StringJoiner" -> {
                StringJoiner joiner = new StringJoiner("|", "", "|");
                for (String field : fields) joiner.add(field);
                text = joiner.toString();
            }
            case "String.format" -> text = String.format("%s", String.join("|", fields) + "|");
            default -> throw new IllegalArgumentException(method);
        }
        return new BuildResult((long) text.length() * workload.tickets(), text);
    }

    private static String value(int index) {
        return switch (index % 8) {
            case 0 -> "PNR" + index;
            case 1 -> "Passenger" + index;
            case 2 -> "Train" + index;
            case 3 -> "From" + index;
            case 4 -> "To" + index;
            case 5 -> "Date" + index;
            case 6 -> "Seat" + index;
            default -> "Fare" + index;
        };
    }

    private static long estimatedIntermediateStrings(Workload workload, String method) {
        return method.equals("String") ? workload.estimatedFields() : workload.tickets();
    }

    private static long retainedMemoryDelta(Workload workload, String method) {
        System.gc();
        long before = usedMemory();
        BuildResult result = build(workload, method);
        System.gc();
        long after = usedMemory();
        if (result.retained().isEmpty()) throw new IllegalStateException("Unexpected empty ticket");
        return after - before;
    }

    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static String sharedAppendTest(boolean synchronizedBuffer) throws InterruptedException {
        int threadCount = 8;
        int appendsPerThread = 20_000;
        StringBuilder builder = new StringBuilder();
        StringBuffer buffer = new StringBuffer();
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger();
        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int j = 0; j < appendsPerThread; j++) {
                        if (synchronizedBuffer) buffer.append('x'); else builder.append('x');
                    }
                } catch (Throwable error) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
            thread.start();
        }
        ready.await();
        start.countDown();
        done.await();
        int actual = synchronizedBuffer ? buffer.length() : builder.length();
        int expected = threadCount * appendsPerThread;
        return "expected=" + expected + ", actual=" + actual + ", workerErrors=" + errors.get()
                + (actual == expected && errors.get() == 0 ? " (correct this run)" : " (lost/corrupt appends observed)");
    }

    private static void appendCsv(Path csv, String workload, String method, int run, long nanos,
                                  long objects, long memory, long checksum) throws IOException {
        Files.writeString(csv, String.format("%s,%s,%d,%d,%d,%d,%d%n", workload, method, run, nanos, objects, memory, checksum),
                StandardOpenOption.APPEND);
    }

    private static double average(long[] values) {
        long sum = 0;
        for (long value : values) sum += value;
        return (double) sum / values.length;
    }

    private static long minimum(long[] values) {
        long result = Long.MAX_VALUE;
        for (long value : values) result = Math.min(result, value);
        return result;
    }

    private static long maximum(long[] values) {
        long result = Long.MIN_VALUE;
        for (long value : values) result = Math.max(result, value);
        return result;
    }
}