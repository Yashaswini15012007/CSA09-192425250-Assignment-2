import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class Main {
    private static final int REPEATS = 10;
    private static final int[] SIZES = {1_000, 10_000, 100_000};
    private record Sample(long nanos, boolean correct, boolean inputUnchanged) { }

    public static void main(String[] args) throws Exception {
        Comparator<Team> byRank = Comparator.comparingInt((Team t) -> t.points).reversed()
                .thenComparing(Comparator.comparingInt((Team t) -> t.goalDiff).reversed())
                .thenComparing(t -> t.name);
        List<Team> sample = new ArrayList<>(List.of(
                new Team("Panthers", 9, 4), new Team("Cobras", 6, 1),
                new Team("Eagles", 9, 6), new Team("Bisons", 9, 4)));
        List<Team> original = new ArrayList<>(sample);
        List<Team> ranking = new Leaderboard<>(sample, byRank).ranking();
        String exact = ranking.toString();
        if (!exact.equals("[Eagles(9,6), Bisons(9,4), Panthers(9,4), Cobras(6,1)]")) throw new IllegalStateException(exact);
        List<String> report = new ArrayList<>();
        report.add(exact);
        report.add("Input unchanged after ranking: " + sample.equals(original));

        List<Team> tied = List.of(new Team("Zulu", 10, 5), new Team("Alpha", 10, 5),
                new Team("Beta", 10, 5), new Team("Bottom", 10, 4), new Team("Other", 9, 5));
        report.add("Tie levels: " + new Leaderboard<>(tied, byRank).ranking());

        Comparator<Team> broken = (left, right) -> right.points - left.points;
        Team high = new Team("High", Integer.MAX_VALUE, 0);
        Team low = new Team("Low", -1, 0);
        report.add("Broken subtraction comparator signs for (MAX,-1): compare(a,b)=" + broken.compare(high, low)
                + ", compare(b,a)=" + broken.compare(low, high) + " (both negative; antisymmetry broken)");
        List<Team> randomTeams = new ArrayList<>();
        Random random = new Random(192425250L);
        for (int i = 0; i < 50_000; i++) randomTeams.add(new Team("T" + i, random.nextInt(), i % 31));
        try {
            randomTeams.sort(broken);
            report.add("TimSort contract exception: not reproduced on the deterministic stress input");
        } catch (IllegalArgumentException error) {
            report.add("TimSort contract exception reproduced: " + error.getMessage());
        }

        Path csv = Path.of("results.csv");
        Files.writeString(csv, "size,strategy,run,items,elapsed_ns,correct_order,input_unchanged\n");
        report.add("\n10-run average sorting benchmarks (setup excluded):");
        for (int size : SIZES) {
            List<Team> teams = generatedTeams(size);
            for (String strategy : List.of("ComparatorChain", "Comparable", "ThreeStableSorts")) {
                benchmark(strategy, teams, byRank);
                long[] samples = new long[REPEATS];
                boolean correct = true;
                boolean unchanged = true;
                for (int run = 1; run <= REPEATS; run++) {
                    Sample result = benchmark(strategy, teams, byRank);
                    samples[run - 1] = result.nanos();
                    correct &= result.correct();
                    unchanged &= result.inputUnchanged();
                    Files.writeString(csv, String.format("%d,%s,%d,%d,%d,%s,%s%n", size, strategy, run,
                            size, result.nanos(), result.correct(), result.inputUnchanged()), StandardOpenOption.APPEND);
                }
                report.add(String.format("%7d %-17s avg=%9.3f ms min=%9.3f ms max=%9.3f ms correct=%s inputUnchanged=%s",
                        size, strategy, average(samples) / 1e6, min(samples) / 1e6, max(samples) / 1e6, correct, unchanged));
            }
        }
        Files.writeString(Path.of("output.txt"), String.join(System.lineSeparator(), report) + System.lineSeparator());
        System.out.println(String.join(System.lineSeparator(), report));
    }

    private static Sample benchmark(String strategy, List<Team> teams, Comparator<Team> byRank) {
        List<Team> inputSnapshot = new ArrayList<>(teams);
        long start = System.nanoTime();
        List<Team> result;
        if (strategy.equals("ComparatorChain")) result = new Leaderboard<>(teams, byRank).ranking();
        else if (strategy.equals("Comparable")) result = new Leaderboard<>(teams, Comparator.naturalOrder()).ranking();
        else {
            result = new ArrayList<>(teams);
            result.sort(Comparator.comparing((Team team) -> team.name));
            result.sort(Comparator.comparingInt((Team team) -> team.goalDiff).reversed());
            result.sort(Comparator.comparingInt((Team team) -> team.points).reversed());
        }
        long elapsed = System.nanoTime() - start;
        boolean correct = true;
        for (int i = 1; i < result.size(); i++) if (byRank.compare(result.get(i - 1), result.get(i)) > 0) correct = false;
        boolean unchanged = teams.size() == inputSnapshot.size();
        for (int i = 0; unchanged && i < teams.size(); i++) unchanged = teams.get(i) == inputSnapshot.get(i);
        return new Sample(elapsed, correct, unchanged);
    }

    private static List<Team> generatedTeams(int size) {
        Random random = new Random(size);
        List<Team> teams = new ArrayList<>(size);
        for (int i = 0; i < size; i++) teams.add(new Team("Club" + i, random.nextInt(101), random.nextInt(41) - 20));
        return teams;
    }

    private static double average(long[] values) { long sum = 0; for (long value : values) sum += value; return (double) sum / values.length; }
    private static long min(long[] values) { long result = Long.MAX_VALUE; for (long value : values) result = Math.min(result, value); return result; }
    private static long max(long[] values) { long result = Long.MIN_VALUE; for (long value : values) result = Math.max(result, value); return result; }
}