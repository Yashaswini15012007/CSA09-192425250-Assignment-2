import java.util.Objects;

public class Student implements Comparable<Student> {
    private int regNo;
    private final String name;

    public Student(int regNo, String name) {
        this.regNo = regNo;
        this.name = name;
    }

    public int getRegNo() { return regNo; }
    public void setRegNo(int regNo) { this.regNo = regNo; }
    public String getName() { return name; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Student student)) return false;
        return regNo == student.regNo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(regNo);
    }

    @Override
    public int compareTo(Student other) {
        return name.compareTo(other.name);
    }

    @Override
    public String toString() { return name + "(" + regNo + ")"; }
}