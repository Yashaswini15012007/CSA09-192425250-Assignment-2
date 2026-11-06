# Q1: String, StringBuilder, StringBuffer

## Prediction (written before measurement)

`String` is immutable, so each `+` in a loop must create a new result and copy the accumulated prefix. For $n$ fields of similar size, cumulative copying grows approximately as $1+2+...+n=O(n^2)$. `StringBuilder` and `StringBuffer` append into expandable buffers and should take $O(n)$ total character work, with occasional resize/copy costs. I expect the gap to be small for an eight-field ticket and large for 5,000 fields. The 10,000-ticket workload repeats 50-field construction and should favor mutable builders. Live-memory deltas are approximate because the JVM controls collection and heap expansion.

## Observation

All three implementations produced matching checksums. On the short ticket, means were 0.052 ms (`String`), 0.025 ms (`StringBuilder`), and 0.022 ms (`StringBuffer`), so the small-input gap was slight. For 10,000 tickets x 50 fields, means were 18.414 ms, 7.393 ms, and 9.785 ms respectively. For 5,000 fields in one ticket, the means were 11.284 ms, 0.100 ms, and 0.086 ms. The latter is about 113x faster with `StringBuilder` than repeated `String` concatenation in this run. The shared-log test observed 44,635 of 160,000 characters with `StringBuilder` and all 160,000 with `StringBuffer`.

After-GC retained-heap deltas averaged 112 bytes for the short ticket, 424 bytes for the last retained ticket in the bulk case, and 45,832 bytes for the custom ticket across implementations. These are JVM-level approximations, not allocation totals; `results.csv` contains the per-run timing and memory readings.

## Analysis

A builder is the right default for per-ticket text because the buffer is thread-confined. A shared multi-threaded booking log needs synchronization: `StringBuffer` serializes its append operations, or a separately synchronized/concurrent logging design can be used. `StringBuilder` is not thread-safe. `String.join` and `StringJoiner` express delimiter-joining clearly and run in linear time; `String.format` is useful for presentation formatting, not repeated accumulation. Repeated concatenation has $O(n^2)$ character-copy work and intermediate strings; builder/join operations use $O(n)$ time and $O(n)$ output storage.