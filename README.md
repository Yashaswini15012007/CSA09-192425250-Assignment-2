# Programming in Java, Assignment 2 (CO2)

**Student:** Yashaswini  
**Register No:** 192425250  
**Java:** 17+ (measured in this workspace with Java 27)

`java -version` in the measurement environment:

```text
java version "27" 2026-09-15
Java(TM) SE Runtime Environment (build 27+35-2325)
Java HotSpot(TM) 64-Bit Server VM (build 27+35-2325, mixed mode, sharing)
```

Each question folder contains source, `output.txt`, `results.csv` (where applicable), and prediction-first `notes.md`. CSV files contain individual timed samples, not only summaries. Memory readings are approximate JVM heap deltas after GC. The original assignment starter files were not present in the initially empty workspace, so complete equivalent examples were created; no supplied starter code was available to preserve.

## Compile and run

Run these commands from this project root in PowerShell. Use another terminal with the matching working directory if needed.

| Question | Compile and run |
|---|---|
| Q1 | `Set-Location Q1; javac Main.java; java Main` |
| Q2 | `Set-Location Q2; javac Main.java; java Main` |
| Q3 | `Set-Location Q3; javac Student.java Attendance.java Experiments.java; java Attendance; java Experiments` |
| Q4 | `Set-Location Q4; javac Main.java; java Main` |
| Q5 | `Set-Location Q5; javac Leaderboard.java Team.java Main.java; java Main` |
| Q6 | `Set-Location Q6; javac Patient.java Triage.java Main.java; java Triage; java Main` |
| Q7 | `Set-Location Q7; javac Main.java; java Main` |

Q3's initial no-`hashCode` result is preserved in `Q3/output-before.txt`; the corrected run is in `Q3/output.txt`. Q6's `Main` appends its experiments after the exact `Triage` line. The benchmark programs regenerate their corresponding `results.csv` and `output.txt` when run.

## Result notes

- **Q1:** For 5,000 fields, `StringBuilder` was 72.98x faster in the fresh Q7 paired run; estimated intermediate strings fell from 5,000 to one. The shared `StringBuilder` test lost appends, while `StringBuffer` retained all.
- **Q2:** `ArrayList` is best for indexed reads; `ArrayDeque` is the tested fit for two-ended Up Next operations. `LinkedList` middle/index operations were slow at large sizes.
- **Q3:** Adding `hashCode` consistent with `equals` changed the supplied attendance result from three entries to two. Mutating a hashed identity field broke lookup as predicted.
- **Q4:** `LinkedHashMap` was selected for the workload because it naturally preserves insertion order while meeting the lookup target; code-order reports sort a temporary key list.
- **Q5:** Comparator-chain ranking returned the exact requested order without modifying the input; the deliberately broken subtraction comparator violated its contract and triggered TimSort's exception.
- **Q6:** PriorityQueue treatment order preserved arrival order among equal severities; the measured million-item throughput comfortably exceeded 5,000 patients/day on this local benchmark.
- **Q7:** Replacing repeated immutable-prefix construction reduced mean time by 98.63% and estimated intermediate result strings by 99.98%, with exact output equality.

See [`report.md`](report.md) for predictions, detailed measurements, complexity comparisons, explanations, and the redesign validation.