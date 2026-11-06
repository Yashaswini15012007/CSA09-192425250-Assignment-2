# Q4: Pharmacy inventory map choice

## Prediction (written before implementation)

`HashMap` should provide expected $O(1)$ put/get and $O(n)$ iteration, but report order is unspecified. `TreeMap` should provide $O(log n)$ put/get and ascending-code iteration in $O(n)$; `LinkedHashMap` should provide expected $O(1)$ put/get and insertion-order iteration in $O(n)$, with linked-entry overhead. Code order is naturally produced by `TreeMap`; insertion order is naturally produced by `LinkedHashMap`. Choosing the wrong map requires sorting keys (typically $O(n log n)$) or maintaining a separate insertion sequence and looking up each entry (additional $O(n)$ references and work). A sorted array should make binary-search lookups $O(log n)$ and code-order scans $O(n)$, but insertion/update into the middle is $O(n)$.

For deployment, I will use a target below 1 ms for one lookup (and below 100 ms for a 100,000-lookup batch). I predict `HashMap` will comfortably meet the lookup target; if both reports matter, I expect a `LinkedHashMap` to preserve insertion order and a sorted code-order report to pay an explicit sort cost. Measurements will decide whether that trade is reasonable.

## Observation

All map operations were measured over 10 samples at 1,000, 100,000, and 1,000,000 medicines. At one million items, 100,000 lookups averaged 0.786 ms for `HashMap`, 1.066 ms for `LinkedHashMap`, and 9.579 ms for `TreeMap`; the sorted-array binary search averaged 3.852 ms. Each is below the 100 ms batch target (and far below 1 ms per lookup on average). One-million-entry put means were 33.773 ms, 77.331 ms, and 262.608 ms for `HashMap`, `LinkedHashMap`, and `TreeMap`; sorted-array initial build by sorting averaged 0.682 ms.

At one million items, code-order report means were 40.107 ms (`HashMap` plus key sorting), 133.742 ms (`TreeMap` iteration), 46.470 ms (`LinkedHashMap` plus sorting), and 0.283 ms (sorted array scan). Insertion-order reports averaged 23.350 ms for `HashMap` with a tracked insertion-key array, 120.632 ms for `TreeMap` with that tracking, and 14.487 ms for `LinkedHashMap` iteration. Every measured report's order check passed. The sorted array's insertion-order report is costly because it binary-searches each key; it is best for static or rarely changed inventories. All individual runs are in `results.csv`.

## Analysis

`Hashtable` is legacy synchronized collection API: each method synchronizes, null keys and values are prohibited, and modern concurrent workloads generally use `ConcurrentHashMap` instead. Synchronization does not make an entire multi-step report atomic. Hashing, tree traversal, cache behavior, allocation, and garbage collection all contribute to observed costs beyond asymptotic complexity.

## Deployment decision

Use `LinkedHashMap<Integer, Medicine>` for the stated workload because it naturally preserves the pharmacy's insertion-order report and its one-million-item 100,000-lookup batch was 1.066 ms, well inside the defined target. Generate the code-order report by sorting a temporary key list; that measured report took 46.470 ms. This is a practical balance when both reports are required. If code-order reporting becomes the dominant operation, `TreeMap` removes the explicit sort but costs more on measured inserts/lookups and its insertion-order report needs a side list. `HashMap` is the fastest lookup candidate, but it does not provide either requested order without extra work.

The sorted-array lookup is $O(log n)$ by `Arrays.binarySearch`, insertion into a sorted live array is $O(n)$ due to shifting, code-order scanning is $O(n)$, and its primitive key storage is compact. It wins static code-order scans here, but is a poor choice for frequent inventory additions/removals.