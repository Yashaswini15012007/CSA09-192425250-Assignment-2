import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.PriorityQueue;
import java.util.Random;

public class Main {
    private static final int REPEATS = 10;
    private static final int[] SIZES = {10_000, 100_000, 1_000_000};
    private static final Comparator<Patient> ORDER = Comparator.comparingInt((Patient p) -> p.severity)
            .thenComparingLong(p -> p.arrival);
    private record Sample(long nanos, long checksum) { }
    private static final class AgingPatient {
        final String name;
        final int originalSeverity;
        final long arrivalTime;
        final long arrival;
        int effectiveSeverity;
        AgingPatient(String name, int severity, long arrivalTime, long arrival) {
            this.name = name; this.originalSeverity = severity; this.arrivalTime = arrivalTime;
            this.arrival = arrival; this.effectiveSeverity = severity;
        }
    }

    public static void main(String[] args) throws Exception {
        List<String> report = new ArrayList<>();
        report.add("\nQ6 hand-worked eight-patient example:");
        List<Patient> eight = List.of(new Patient("Asha", 5, 1), new Patient("Bala", 2, 2),
                new Patient("Chitra", 3, 3), new Patient("Dev", 1, 4), new Patient("Esha", 3, 5),
                new Patient("Farah", 2, 6), new Patient("Gopal", 5, 7), new Patient("Hari", 1, 8));
        PriorityQueue<Patient> hand = new PriorityQueue<>(ORDER);
        hand.addAll(eight);
        List<Patient> actualOrder = new ArrayList<>();
        while (!hand.isEmpty()) actualOrder.add(hand.poll());
        report.add(actualOrder.toString());
        if (!actualOrder.toString().equals("[Dev(S1), Hari(S1), Bala(S2), Farah(S2), Chitra(S3), Esha(S3), Asha(S5), Gopal(S5)]"))
            throw new IllegalStateException("Hand-worked order mismatch: " + actualOrder);
        report.add("A plain PriorityQueue iteration is a heap-array view; only poll() guarantees treatment order.");
        report.add("\nAging demonstration (one severity level per 10 time units):");
        PriorityQueue<AgingPatient> aged = new PriorityQueue<>(Comparator.comparingInt((AgingPatient p) -> p.effectiveSeverity)
                .thenComparingLong(p -> p.arrival));
        aged.add(new AgingPatient("WaitingSevere", 5, 0, 0));
        aged.add(new AgingPatient("NewUrgent", 1, 90, 1));
        promoteAged(aged, 100);
        List<String> agedOrder = new ArrayList<>();
        while (!aged.isEmpty()) {
            AgingPatient patient = aged.poll();
            agedOrder.add(patient.name + "(original=S" + patient.originalSeverity + ", effective=S" + patient.effectiveSeverity + ")");
        }
        report.add(agedOrder.toString());

        Path csv = Path.of("results.csv");
        Files.writeString(csv, "size,structure,operation,run,operations,elapsed_ns,ns_per_operation,checksum\n");
        report.add("\nPriorityQueue add/poll growth (10 measured samples after warm-up):");
        for (int size : SIZES) {
            Patient[] patients = patients(size);
            for (String operation : List.of("add", "poll")) {
                measurePriorityQueue(operation, patients);
                long[] times = new long[REPEATS];
                for (int run = 1; run <= REPEATS; run++) {
                    Sample sample = measurePriorityQueue(operation, patients);
                    times[run - 1] = sample.nanos();
                    append(csv, size, "PriorityQueue", operation, run, size, sample);
                }
                double avg = average(times);
                report.add(String.format("%7d PriorityQueue %-4s avg=%9.3f ms min=%9.3f ms max=%9.3f ms throughput=%.0f ops/s",
                        size, operation, avg / 1e6, min(times) / 1e6, max(times) / 1e6, size / (avg / 1e9)));
            }
        }
        report.add("\nAlternative full admit-and-serve cycles (10 samples; sizes bounded for sorted-list O(n) admission):");
        for (int size : new int[]{1_000, 5_000}) {
            Patient[] patients = patients(size);
            for (String structure : List.of("PriorityQueue", "SortedLinkedList", "FiveFIFOQueues")) {
                measureCycle(structure, patients);
                long[] times = new long[REPEATS];
                for (int run = 1; run <= REPEATS; run++) {
                    Sample sample = measureCycle(structure, patients);
                    times[run - 1] = sample.nanos();
                    append(csv, size, structure, "admit_serve_cycle", run, size * 2, sample);
                }
                report.add(String.format("%7d %-17s avg=%9.3f ms min=%9.3f ms max=%9.3f ms (%d operations/sample)",
                        size, structure, average(times) / 1e6, min(times) / 1e6, max(times) / 1e6, size * 2));
            }
        }
        report.add("\n5,000 patients/day = 0.058 patients/second; compare with throughput above, allowing for burst and service overhead.");
        Files.writeString(Path.of("output.txt"), String.join(System.lineSeparator(), report) + System.lineSeparator(), StandardOpenOption.APPEND);
        System.out.print(String.join(System.lineSeparator(), report));
    }

    private static void promoteAged(PriorityQueue<AgingPatient> queue, long now) {
        List<AgingPatient> patients = new ArrayList<>(queue);
        queue.clear();
        for (AgingPatient patient : patients) {
            long levels = Math.max(0, now - patient.arrivalTime) / 10;
            patient.effectiveSeverity = (int) Math.max(1, patient.originalSeverity - levels);
            queue.add(patient);
        }
    }

    private static Sample measurePriorityQueue(String operation, Patient[] patients) {
        PriorityQueue<Patient> queue = new PriorityQueue<>(ORDER);
        if (operation.equals("poll")) for (Patient patient : patients) queue.add(patient);
        long start = System.nanoTime();
        long checksum = 0;
        if (operation.equals("add")) {
            for (Patient patient : patients) queue.add(patient);
            checksum = queue.size();
        } else {
            Patient patient;
            while ((patient = queue.poll()) != null) checksum += patient.arrival;
        }
        return new Sample(System.nanoTime() - start, checksum);
    }

    private static Sample measureCycle(String structure, Patient[] patients) {
        long start = System.nanoTime();
        long checksum = 0;
        if (structure.equals("PriorityQueue")) {
            PriorityQueue<Patient> queue = new PriorityQueue<>(ORDER);
            for (Patient patient : patients) queue.add(patient);
            Patient patient;
            while ((patient = queue.poll()) != null) checksum += patient.arrival;
        } else if (structure.equals("SortedLinkedList")) {
            LinkedList<Patient> queue = new LinkedList<>();
            for (Patient patient : patients) {
                ListIterator<Patient> iterator = queue.listIterator();
                while (iterator.hasNext()) {
                    if (ORDER.compare(iterator.next(), patient) > 0) {
                        iterator.previous();
                        break;
                    }
                }
                iterator.add(patient);
            }
            while (!queue.isEmpty()) checksum += queue.removeFirst().arrival;
        } else {
            List<ArrayDeque<Patient>> buckets = new ArrayList<>(6);
            buckets.add(null);
            for (int i = 1; i <= 5; i++) buckets.add(new ArrayDeque<>());
            for (Patient patient : patients) buckets.get(patient.severity).addLast(patient);
            for (int severity = 1; severity <= 5; severity++) {
                while (!buckets.get(severity).isEmpty()) checksum += buckets.get(severity).removeFirst().arrival;
            }
        }
        return new Sample(System.nanoTime() - start, checksum);
    }

    private static Patient[] patients(int size) {
        Patient[] patients = new Patient[size];
        Random random = new Random(size);
        for (int i = 0; i < size; i++) patients[i] = new Patient("P" + i, 1 + random.nextInt(5), i);
        return patients;
    }

    private static void append(Path csv, int size, String structure, String operation, int run, int operations, Sample sample) throws IOException {
        Files.writeString(csv, String.format("%d,%s,%s,%d,%d,%d,%.2f,%d%n", size, structure, operation, run,
                operations, sample.nanos(), (double) sample.nanos() / operations, sample.checksum()), StandardOpenOption.APPEND);
    }

    private static double average(long[] values) { long sum = 0; for (long value : values) sum += value; return (double) sum / values.length; }
    private static long min(long[] values) { long result = Long.MAX_VALUE; for (long value : values) result = Math.min(result, value); return result; }
    private static long max(long[] values) { long result = Long.MIN_VALUE; for (long value : values) result = Math.max(result, value); return result; }
}