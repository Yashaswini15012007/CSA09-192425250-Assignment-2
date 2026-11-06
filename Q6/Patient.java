public final class Patient {
    public final String name;
    public final int severity;
    public final long arrival;

    public Patient(String name, int severity, long arrival) {
        this.name = name;
        this.severity = severity;
        this.arrival = arrival;
    }

    @Override
    public String toString() { return name + "(S" + severity + ")"; }
}