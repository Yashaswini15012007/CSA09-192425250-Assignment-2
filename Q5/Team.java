import java.util.Comparator;
import java.util.Objects;

public final class Team implements Comparable<Team> {
    private static final Comparator<Team> BY_RANK = Comparator
            .comparingInt((Team team) -> team.points).reversed()
            .thenComparing(Comparator.comparingInt((Team team) -> team.goalDiff).reversed())
            .thenComparing(team -> team.name);

    public final String name;
    public final int points;
    public final int goalDiff;

    public Team(String name, int points, int goalDiff) {
        this.name = name;
        this.points = points;
        this.goalDiff = goalDiff;
    }

    @Override
    public int compareTo(Team other) { return BY_RANK.compare(this, other); }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Team team)) return false;
        return points == team.points && goalDiff == team.goalDiff && name.equals(team.name);
    }

    @Override
    public int hashCode() { return Objects.hash(name, points, goalDiff); }

    @Override
    public String toString() { return name + "(" + points + "," + goalDiff + ")"; }
}