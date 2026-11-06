# Q2: Collections for a playlist

## Prediction (written before measurement)

At 10,000, 100,000, and 1,000,000 items, `ArrayList` should excel at indexed access and iteration due to contiguous storage, but front and middle insertion/removal shift $O(n)$ references. `LinkedList` should have $O(1)$ end operations but indexed access and locating a middle index costs $O(n)$; node allocation and pointer chasing will likely make iteration slower than arrays. `ArrayDeque` should make the changed Up Next workload (add at both ends, remove at front) efficient, but cannot offer indexed access or middle insertion. The manual array should resemble `ArrayList` for shifts but avoid boxing/list overhead.

## Observation

All operations had one warm-up invocation followed by 10 timed samples; collection construction was excluded from each timed region. At 1,000,000 elements, `ArrayList` indexed reads averaged 64 ns each (100 reads/sample), versus 105 microseconds for `LinkedList`; at 100,000, the corresponding means were 31 ns and 33 microseconds. The one-million-element `ArrayList` front insert/remove pair averaged 436 microseconds, while its middle pair averaged 130 microseconds. `LinkedList` middle pairs averaged 3.04 ms because finding the index is still linear. For the changed Up Next workload, `ArrayDeque` front pairs averaged 170 ns at one million elements and supported end insertion directly. The manual primitive array's one-million-item front/middle pairs were 179/83 microseconds.

An evidence-based stop point is not just an item count: for a 1,000,000-item playlist with at least 1,000 front edits per second, the measured `ArrayList` mean projects to about 0.44 seconds of CPU time per second for those edits alone. If the workflow is strictly queue-like, the measured `ArrayDeque` path is a much better fit. If indexed reads dominate, the 1,000,000-element `ArrayList` remains preferable. These figures are local microbenchmark observations, not service-level guarantees; the CSV records all samples and operation counts.

## Complexity and storage

| Structure | End add | Front insert/remove | Middle insert/remove | k-th access | Iteration | Approximate storage per element |
|---|---:|---:|---:|---:|---:|---|
| ArrayList | amortized $O(1)$ add | $O(n)$ | $O(n)$ | $O(1)$ | $O(n)$ | one reference in backing array; spare capacity possible |
| LinkedList | $O(1)$ at ends | $O(1)$ at ends | $O(n)$ locate, then $O(1)$ relink | $O(n)$ | $O(n)$ | node links, item reference, object/header overhead |
| ArrayDeque | amortized $O(1)$ | $O(1)$ at ends | unsupported directly | unsupported | $O(n)$ | one reference in circular backing array; spare capacity possible |
| Manual array | $O(1)$ if capacity exists | $O(n)$ | $O(n)$ | $O(1)$ | $O(n)$ | primitive slot in this benchmark; no per-item node |

## Analysis

An `ArrayList` remains a sensible playlist when reads, iteration, and append dominate and the playlist is not so large that occasional shifts dominate the measured workflow. A defensible stopping boundary is workload-specific: stop recommending it when repeated front/middle edits exceed the application's latency budget or dominate actual operation counts. The benchmark reports per-operation latency at each size rather than a universal item-count threshold. For Up Next, use `ArrayDeque` when only end additions and front removals are needed; it avoids list shifting. `LinkedList` is not automatically better for middle edits because locating an index is still linear.