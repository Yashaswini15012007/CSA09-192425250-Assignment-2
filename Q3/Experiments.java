import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class Experiments {
    private static final int REPEATS = 10;
    private static final int[] SIZES = {1_000, 100_000, 1_000_000};
    private static final String[] COLLECTIONS = {"HashSet", "TreeSet", "HashMap"};
    private record Sample(long nanos, long checksum) { }

    public static void main(String[] args) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("\nQ3 behavior tests (predictions were recorded in notes.md before running):");
        Set<Student> sameReference = new HashSet<>();
        Student one = new Student(10, "Same");
        for (int i = 0; i < 5; i++) sameReference.add(one);
        lines.add("Test 1 same-reference scans: predicted size=1, actual size=" + sameReference.size());

        Set<Student> duplicates = new HashSet<>();
        duplicates.add(new Student(20, "First scan"));
        duplicates.add(new Student(20, "Second scan"));
        duplicates.add(new Student(21, "Another student"));
        lines.add("Test 2 separate duplicate scans after fix: predicted size=2, actual size=" + duplicates.size());

        Student mutated = new Student(30, "Mutable key");
        Set<Student> mutableSet = new HashSet<>();
        mutableSet.add(mutated);
        mutated.setRegNo(31);
        lines.add("Test 3 after regNo mutation: contains(same object)=" + mutableSet.contains(mutated));
        TreeSet<Student> inconsistent = new TreeSet<>();
        Student sameIdA = new Student(40, "Alpha");
        Student sameIdB = new Student(40, "Zulu");
        inconsistent.add(sameIdA);
        inconsistent.add(sameIdB);
        lines.add("TreeSet natural order by name; same regNo objects equals=" + sameIdA.equals(sameIdB)
                + ", set size=" + inconsistent.size());
        TreeSet<Student> consistent = new TreeSet<>(Comparator.comparingInt(Student::getRegNo));
        consistent.add(sameIdA);
        consistent.add(sameIdB);
        lines.add("TreeSet comparator by regNo: set size=" + consistent.size());
        Map<String, Student> byRegistration = new HashMap<>();
        byRegistration.put("40", sameIdA);
        lines.add("HashMap alternative lookup: " + byRegistration.get("40"));

        Path csv = Path.of("results.csv");
        Files.writeString(csv, "size,collection,operation,run,operations,elapsed_ns,ns_per_operation,checksum\n");
        lines.add("\n10-run average timings (setup excluded, operation counts are recorded in CSV):");
        for (int size : SIZES) {
            int lookupCount = 100_000;
            for (String collection : COLLECTIONS) {
                for (String operation : List.of("insert", "lookup")) {
                    int count = operation.equals("insert") ? size : lookupCount;
                    measure(collection, operation, size, count);
                    long[] times = new long[REPEATS];
                    for (int run = 1; run <= REPEATS; run++) {
                        Sample sample = measure(collection, operation, size, count);
                        times[run - 1] = sample.nanos();
                        append(csv, size, collection, operation, run, count, sample);
                    }
                    lines.add(String.format("%7d %-8s %-6s avg=%9.3f ms min=%9.3f ms max=%9.3f ms (%d ops/sample)",
                            size, collection, operation, average(times) / 1e6, min(times) / 1e6, max(times) / 1e6, count));
                }
            }
        }
        String report = String.join(System.lineSeparator(), lines) + System.lineSeparator();
        Files.writeString(Path.of("output.txt"), report, StandardOpenOption.APPEND);
        System.out.print(report);
    }

    private static Sample measure(String collection, String operation, int size, int count) {
        Student[] students = new Student[size];
        String[] keys = new String[size];
        for (int i = 0; i < size; i++) {
            students[i] = new Student(i, "Student" + i);
            keys[i] = Integer.toString(i);
        }
        if (collection.equals("HashSet")) {
            Set<Student> values = new HashSet<>(size * 4 / 3 + 1);
            if (operation.equals("lookup")) for (Student student : students) values.add(student);
            long start = System.nanoTime();
            long checksum = 0;
            if (operation.equals("insert")) for (Student student : students) values.add(student);
            else for (int i = 0; i < count; i++) if (values.contains(students[i % size])) checksum++;
            if (operation.equals("insert")) checksum = values.size();
            return new Sample(System.nanoTime() - start, checksum);
        } else if (collection.equals("TreeSet")) {
            TreeSet<Student> values = new TreeSet<>(Comparator.comparingInt(Student::getRegNo));
            if (operation.equals("lookup")) for (Student student : students) values.add(student);
            long start = System.nanoTime();
            long checksum = 0;
            if (operation.equals("insert")) for (Student student : students) values.add(student);
            else for (int i = 0; i < count; i++) if (values.contains(students[i % size])) checksum++;
            if (operation.equals("insert")) checksum = values.size();
            return new Sample(System.nanoTime() - start, checksum);
        } else {
            Map<String, Student> values = new HashMap<>(size * 4 / 3 + 1);
            if (operation.equals("lookup")) for (int i = 0; i < size; i++) values.put(keys[i], students[i]);
            long start = System.nanoTime();
            long checksum = 0;
            if (operation.equals("insert")) for (int i = 0; i < size; i++) values.put(keys[i], students[i]);
            else for (int i = 0; i < count; i++) if (values.get(keys[i % size]) != null) checksum++;
            if (operation.equals("insert")) checksum = values.size();
            return new Sample(System.nanoTime() - start, checksum);
        }
    }

    private static void append(Path csv, int size, String collection, String operation, int run, int count, Sample sample) throws IOException {
        Files.writeString(csv, String.format("%d,%s,%s,%d,%d,%d,%.2f,%d%n", size, collection, operation, run, count,
                sample.nanos(), (double) sample.nanos() / count, sample.checksum()), StandardOpenOption.APPEND);
    }

    private static double average(long[] values) { long sum = 0; for (long value : values) sum += value; return (double) sum / values.length; }
    private static long min(long[] values) { long result = Long.MAX_VALUE; for (long value : values) result = Math.min(result, value); return result; }
    private static long max(long[] values) { long result = Long.MIN_VALUE; for (long value : values) result = Math.max(result, value); return result; }
}