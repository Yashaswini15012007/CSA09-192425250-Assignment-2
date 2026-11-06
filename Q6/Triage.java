import java.util.Comparator;
import java.util.PriorityQueue;

public class Triage {
    public static void main(String[] args) {
        PriorityQueue<Patient> patients = new PriorityQueue<>(Comparator.comparingInt((Patient p) -> p.severity)
                .thenComparingLong(p -> p.arrival));
        patients.add(new Patient("Meena", 1, 0));
        patients.add(new Patient("Kumar", 1, 1));
        patients.add(new Patient("Lata", 2, 2));
        patients.add(new Patient("Ravi", 3, 3));
        patients.add(new Patient("John", 3, 4));
        patients.add(new Patient("Asha", 5, 5));
        StringBuilder output = new StringBuilder();
        while (!patients.isEmpty()) {
            if (!output.isEmpty()) output.append(' ');
            output.append(patients.poll());
        }
        System.out.println(output);
    }
}