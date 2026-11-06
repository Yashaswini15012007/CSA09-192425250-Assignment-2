# Q5: Generic sports leaderboard

## Design and prediction (written before implementation)

The consistency principle is that one ranking definition should govern comparisons and identity. `Team.equals` will use the same fields as ranking: points, goal difference, and name. The leaderboard will take `Comparator<? super T>` so callers can supply an ordering for `T` or one of its supertypes, and `List<? extends T>` so a list of a subtype can be copied into the component. `ranking()` must create `new ArrayList<>(items)`, sort that copy, and leave the caller's list unchanged.

The comparator chain is lexicographic: points descending, goal difference descending, then name ascending. I predict exactly `[Eagles(9,6), Bisons(9,4), Panthers(9,4), Cobras(6,1)]`; Bisons precedes Panthers because their first two keys tie and `"Bisons"` sorts before `"Panthers"`. Chaining one comparator should require one $O(n log n)$ sort and $O(n)$ copy/sort workspace. A `Comparable<Team>` implementation can make the same ordering reusable as a natural order. Three stable sorts (name ascending, goal difference descending, then points descending) should also produce the same order, but make three $O(n log n)$ passes and require stable sorting.

## Correctness argument

For each team define the ranking key as the tuple $(-points,-goalDiff,name)$ ordered lexicographically with integer and string natural order. Lexicographic order over totally ordered components is a total order: comparing two distinct keys gives exactly one direction (antisymmetry of sign), and if $a<b$ and $b<c$, the first differing component establishes $a<c$ (transitivity). The comparator returns zero iff all three key fields match; `equals` uses those same fields, so ordering equality agrees with object equality. This avoids overflow-prone subtraction.

## Observation

The first output line is exactly `[Eagles(9,6), Bisons(9,4), Panthers(9,4), Cobras(6,1)]`. Bisons precedes Panthers because points and goal difference tie, then the ascending name key puts `Bisons` first. The input list remained unchanged, all tie cases were ordered as expected, and all three benchmark strategies produced a valid ranking without mutating their inputs.

The deliberately broken subtraction comparator returned `-2147483648` in both directions for `(Integer.MAX_VALUE, -1)`, violating antisymmetry. Sorting a deterministic 50,000-team stress input reproduced `Comparison method violates its general contract!`. At 100,000 teams, the average times were 48.217 ms (comparator chain), 40.879 ms (`Comparable`), and 44.969 ms (three stable sorts). At 10,000 they were 2.745, 2.428, and 4.354 ms. Although three stable passes do more sorting work asymptotically in terms of pass count, TimSort can exploit runs and measured constants/data patterns matter. Raw 10-run samples are in `results.csv`.

## Alternative analysis

`Comparable` is convenient when a type has one obvious universal order; a comparator is more reusable for teams, players, colleges, or different competition policies. Wildcards let the component consume subtypes without exposing its internal copied `List<T>`. Java's stable list sort lets least-significant-to-most-significant sorting work, but each pass costs $O(n log n)$ and sorting needs temporary space; the comparator-chain method is one pass and one copy.