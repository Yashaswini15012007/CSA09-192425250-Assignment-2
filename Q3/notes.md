# Q3: `hashCode` and `equals` for attendance

## Predictions (written before running)

1. Repeatedly adding the exact same `Student` reference to a set should leave one entry: `HashSet` first sees the same reference in the same bucket, so the set should appear to work even without a proper `hashCode` implementation.
2. Adding separate scans with the same `regNo` but distinct object identities should expose the bug: with `equals` overridden and `hashCode` inherited from `Object`, the two equal objects may occupy different buckets and both remain. I predict three entries for the supplied attendance sample before the fix and two after it.
3. If `regNo` changes after insertion, a `HashSet` lookup should fail because the object is now in the bucket selected by its old hash. `TreeSet` ordering by `name` while equality is based on `regNo` is inconsistent: equal-by-regNo objects with different names can both be retained.

## Baseline and fixed outputs

The baseline was compiled and run before `hashCode` was added; its saved output in `output-before.txt` is:

```text
Unique students: 3
```

After adding `hashCode`, the saved `output.txt` begins with:

```text
Unique students: 2
```

The other predictions matched: five inserts of the same reference had size 1; two distinct equal-by-regNo scans plus another student had size 2 after the fix; mutating an inserted registration number made `contains` return false; natural `TreeSet` ordering by name retained two students that were equal by registration number; and a registration-number comparator reduced that pair to one.

## Explanation and analysis

`HashSet` uses `hashCode()` to select a bucket, then uses `equals()` among candidates in that bucket. Equal objects must have equal hash codes; otherwise equal scans can land in different buckets and are not compared. `Objects.hash(regNo)` repairs the contract for this equality definition. Fields used by `equals`/`hashCode` should not mutate while an object is a hash key. A `TreeSet` uses ordering, not hash equality, and its comparator should be consistent with equality when set semantics are meant to represent the same identity. `HashSet`/`HashMap` offer expected $O(1)$ operations; `TreeSet` offers $O(log n)$ and maintains order. In the 1,000,000-entry trial, 100,000 prebuilt-key lookups averaged 0.885 ms for `HashSet`, 7.351 ms for `TreeSet`, and 0.902 ms for `HashMap`; one-million inserts averaged 28.333, 167.581, and 19.409 ms respectively. The benchmarks are local observations; `results.csv` contains all ten samples.

## Alternative benchmark

`Experiments.java` compares `HashSet`, a `TreeSet` ordered by registration number, and `HashMap<String, Student>` on insertion and lookup workloads. The benchmark results will be summarized here.