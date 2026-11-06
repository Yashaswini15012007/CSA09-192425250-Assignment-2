# Q6: PriorityQueue triage

## Prediction (written before measurement)

A plain FIFO `Queue` cannot select the most severe patient first. A min-heap ordered by severity and then monotonically increasing arrival number should preserve urgency and FIFO within equal severity. Insertion and removal should each grow as $O(log n)$; I predict good throughput at 10,000, 100,000, and 1,000,000 patients. Printing a `PriorityQueue` directly is not a treatment-order traversal: its backing heap only guarantees the head is minimal.

Hand-worked eight-patient arrivals:

| Arrival | Patient | Severity |
|---:|---|---:|
| 1 | Asha | 5 |
| 2 | Bala | 2 |
| 3 | Chitra | 3 |
| 4 | Dev | 1 |
| 5 | Esha | 3 |
| 6 | Farah | 2 |
| 7 | Gopal | 5 |
| 8 | Hari | 1 |

Expected service order: `Dev(S1), Hari(S1), Bala(S2), Farah(S2), Chitra(S3), Esha(S3), Asha(S5), Gopal(S5)`. Equal-severity patients remain in arrival order.

For starvation, a severity-5 patient may wait indefinitely if severity-1 work continues to arrive. An aging rule will reduce effective severity by one for every 10 time units waited, down to severity 1. Since a heap comparator must remain stable while elements are inside the heap, changing effective priorities requires a scheduled promotion pass that drains, updates, and reinserts queued patients.

## Observation

The exact supplied output was `Meena(S1) Kumar(S1) Lata(S2) Ravi(S3) John(S3) Asha(S5)`. The program's eight-patient order matched the hand calculation exactly. The aging demonstration promoted the long-waiting severity-5 patient to effective severity 1; because it arrived earlier than the new severity-1 patient, it was served first after the promotion pass.

PriorityQueue add/poll means at 10,000 items were 0.770/2.235 ms; at 100,000 they were 4.214/25.493 ms; at 1,000,000 they were 24.520/452.999 ms. At one million items this corresponds to approximately 40.8 million adds/second and 2.21 million polls/second in this local single-threaded microbenchmark. Ten samples, min/max, and operation counts are in `results.csv`.

Alternative admit-and-serve cycles averaged 0.745 ms for PriorityQueue, 26.085 ms for the sorted linked list, and 0.103 ms for five FIFO severity queues at 5,000 patients (10,000 operations). The bucket implementation is fastest for the fixed five severity values; the heap is the more general choice if priorities expand or use richer ordering.

## Alternative analysis

`PriorityQueue` offer/poll are $O(log n)$ and use $O(n)$ heap storage. A sorted `LinkedList` can serve the head in $O(1)$ but must scan/insert in $O(n)$ and allocates one node per patient. Five FIFO queues indexed by severity provide $O(1)$ admit/serve for fixed priorities and $O(n+5)$ storage, but require checking the five severity buckets and a promotion/rebucket strategy for aging. The benchmark includes sorted-list and five-queue cycles at smaller sizes to keep the $O(n^2)$ list admission experiment bounded.

## Deployment decision

5,000 patients/day is about 0.058 patients/second. Compare this with measured add/poll throughput, but do not treat a microbenchmark as a service-level guarantee; production capacity also depends on contention, I/O, bursts, monitoring, and aging-pass frequency.

The measured heap throughput exceeds the average daily arrival rate by many orders of magnitude, so the data structure can serve 5,000/day. That is not a full deployment capacity proof: arrival bursts, clinical service times, concurrency, persistence, and promotion-pass scheduling are outside this microbenchmark. A scheduled aging pass must reinsert all waiting entries because mutating priorities in place would violate heap order.