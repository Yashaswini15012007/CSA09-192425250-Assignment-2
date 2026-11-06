import java.util.HashSet;
import java.util.Set;

public class Attendance {
    public static void main(String[] args) {
        Set<Student> attendance = new HashSet<>();
        attendance.add(new Student(192425250, "Yashaswini"));
        attendance.add(new Student(192425250, "Yashaswini duplicate scan"));
        attendance.add(new Student(192425251, "Student B"));
        System.out.println("Unique students: " + attendance.size());
    }
}