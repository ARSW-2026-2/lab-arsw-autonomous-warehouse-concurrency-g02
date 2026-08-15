# Laboratory 2 Report

## 1. Shared-state inventory

| Shared object | Mutable state | Readers | Writers | Possible invariant |
| :--- | :--- | :--- | :--- | :--- |
| **PackageQueue** | `pending` list of parcels | `takeNext()`, `pendingCount()` | `takeNext()` | No parcel is skipped or processed twice; the list size accurately reflects remaining items. |
| **DeliveryRegistry** | `nextPosition` integer, `deliveries` list | `snapshot()` | `register()` | `nextPosition` increments sequentially without duplicates; it always equals `deliveries.size() + 1`. |
| **WarehouseStatistics** | `processedParcels` int, `totalProcessingMillis` long | `processedParcels()`, `totalProcessingMillis()` | `recordProcessed()` | `processedParcels` accurately reflects the exact number of parcels processed without lost updates. |
| **SimulationControl** | `paused` boolean | `isPaused()`, `awaitIfPaused()` | `pause()`, `resume()` | The pause state is consistent, safely published, and visible to all worker threads simultaneously. |


## 2. Observed anomalies

**Evidence 1**
*   **Command used:** `java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain 24 250`
*   **Execution number:** 1
*   **Relevant console output:** `[warehouse-robot-4] Queue anomaly: IndexOutOfBoundsException`
*   **Class/method suspected:** `PackageQueue.takeNext()`
*   **Explanation:** This is a classic "check-then-act" race condition. Multiple threads check `!pending.isEmpty()` and evaluate it to true. One thread removes the last element, and when the subsequent thread reaches `pending.remove(0)`, the list is empty, throwing an `IndexOutOfBoundsException`.

**Evidence 2**
*   **Command used:** `java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 50 32 500`
*   **Execution number:** 1 (Run 01)
*   **Relevant console output:** `Run 01 -> RACE/ANOMALY | pending=0, processedCounter=489, registry=495, uniqueParcels=495, uniquePositions=480, positionsContiguous=false`
*   **Class/method suspected:** `WarehouseStatistics.recordProcessed()`
*   **Explanation:** The `processedCounter` (489) does not match the registry size (495). The counter suffers from "lost updates" because `processedParcels = current + 1` is not atomic and includes a `Thread.yield()`. Multiple threads read the same `current` value before writing back the incremented value, causing the counter to fall behind.

**Evidence 3**
*   **Command used:** `java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe`
*   **Execution number:** 3 (Run 03)
*   **Relevant console output:** `Run 03 -> RACE/ANOMALY | ... uniquePositions=230, positionsContiguous=false`
*   **Class/method suspected:** `DeliveryRegistry.register()`
*   **Explanation:** The probe reveals that out of 250 parcels, there are only 230 unique arrival positions, meaning multiple parcels were assigned the exact same position. This happens because `nextPosition` is read into `assignedPosition`, the thread yields, and then increments. Multiple threads capture the same `nextPosition` before any of them increment it.


## 3. Interleaving analysis

**Target:** `WarehouseStatistics.recordProcessed()` lost update race condition.

| Step | Thread A (Robot 1) | Thread B (Robot 2) | Shared state (`processedParcels`) |
| :--- | :--- | :--- | :--- |
| 1 | Reads `current = processedParcels` (value is 10) | --- | 10 |
| 2 | `Thread.yield()` executes | Reads `current = processedParcels` (value is 10) | 10 |
| 3 | --- | `Thread.yield()` executes | 10 |
| 4 | Writes `processedParcels = 10 + 1` (11) | --- | 11 |
| 5 | --- | Writes `processedParcels = 10 + 1` (11) | 11 (Lost Update!) |

**Answer:**
**Why is the final result dependent on scheduling?**
The final result depends on the exact sequence of CPU scheduling because the operation is composed of three distinct steps: read, modify, and write. If the operating system scheduler interleaves Thread B's "read" step *after* Thread A reads but *before* Thread A writes, both threads will calculate their increment based on the same stale data. If the scheduler happens to let Thread A complete all three steps before Thread B starts, the result is correct. This non-deterministic scheduling causes the race condition.

## 4. System invariants

#### Candidate Invariants Evaluation
*   **Every parcel is processed at most once:** Required.
*   **No parcel disappears from the system:** Required.
*   **Arrival positions are unique:** Required.
*   **Arrival positions form a valid sequence from 1..N:** Required.
*   **The processed counter matches the number of delivery records:** Derived (it is a consequence of ensuring no lost updates occur in either the registry or the statistics counters, but it's a vital assertion to verify system health).
*   **When the simulation is reported as complete, no parcels remain pending:** Required.

#### Final Set of Invariants
*   **I1 (Conservation of Parcels):** The number of `pending` parcels plus the number of `deliveries` recorded in the registry must always equal the `initialParcels` count.
*   **I2 (Unique & Sequential Positions):** The `DeliveryRegistry` must assign strictly sequential, gapless, and unique arrival positions (1, 2, 3...) to each processed parcel.
*   **I3 (Counter Integrity):** The `processedParcels` counter in `WarehouseStatistics` must exactly match the number of elements in the `DeliveryRegistry`'s `deliveries` list at any given moment.
*   **I4 (At-Most-Once Processing):** No parcel is processed or registered more than once (indicated by the strict uniqueness of `parcelId` in the registry).


## 5. Critical regions and synchronization decisions

| Class | Critical region | Protected invariant | Synchronization mechanism | Why this granularity? |
| :--- | :--- | :--- | :--- | :--- |
| **PackageQueue** | The body of `takeNext()` and `pendingCount()`. | I4 (At-Most-Once Processing). | `synchronized(pending)` block. | The entire check-and-remove operation must be atomic. Synchronizing on the list ensures no other thread can evaluate `isEmpty()` while a removal is in progress. |
| **DeliveryRegistry** | The body of `register()` and `snapshot()`. | I2 (Unique & Sequential Positions). | `synchronized(this)` block. | Both the `nextPosition` increment and `deliveries.add()` must be mutually exclusive as a single atomic unit to avoid duplicate assignment of positions. |
| **WarehouseStatistics** | The body of `recordProcessed()` and the getters. | I3 (Counter Integrity). | `synchronized(this)` block and methods. | The read-modify-write cycle of the primitive counters must be protected. Synchronizing the entire update block prevents lost updates. |

**Answer:**
**What would happen to throughput if the protected region were unnecessarily large?**
If the protected region were too large (for example, synchronizing the robot's entire `process()` method inside the `WarehouseRobot` class or using one global lock for the whole simulation), the threads would execute sequentially rather than concurrently. Throughput would plummet because workers would be blocked waiting for the lock even when performing independent and time-consuming tasks. This would completely defeat the purpose of multithreading, increasing the overall execution time.

## 6. Thread completion and pause/resume coordination

### Thread Completion:
In this part, in the WarehouseMain.java, we can see a problem because after to start the simulation, we have a problem that the code use `Thread.sleep(...)` and this have problems because the programmer put any time, and nobody knows if the simulation progressed or finished during this time, which is causing us problems, so we switched to using an implementation that was already used in WarehouseSimulation, that the robot use the `join()`, this implementation called `awaitCompletion()`.

Document:

> Why is `Thread.sleep(...)` not a valid substitute for `join()` when waiting for a worker to finish?

> `Thread.sleep(...)` is not a valid substitute for `join()` because the goal is for the process to continue once the robots finish their tasks; however, using `Thread.sleep(60)` relies on the assumption that the robots will have advanced or completed their work within 60ms—something that isn't guaranteed. In contrast, using `join()` ensures that the program waits for the target thread to finish before proceeding.

> Furthermore, `join()` guarantees that everything a thread has done prior to completion is visible to the next thread—a guarantee not provided by the previously mentioned method.

### Pause/Resume:
In this part we modify the class `SimulationControl` to add the implementation that the Lab say, in the pause of the robots, then the pause, we put the `notifyAll()`, what guarantees us that all the threads will know.
 
> How do you know the snapshot represents a consistent state rather than workers that are still changing shared data?

> We can guarantee the snapshot's consistency because all the methods we modified in classes such as `simulationControl` and `WarehouseMain` are `synchronized`; consequently, no thread can access them while another is using them. Furthermore, when the simulation is paused, the robots finish processing their current package and then wait, adhering to the implemented logic.

> Therefore, the requested values—Processed parcels, Pending parcels, Registry size, and Current leader—reflect the data as it stands at that moment.

## 7. Verification results

| Robots | Parcels | Runs | Anomalies before | Anomalies after |
|---:|---:|-----:|---:|----------------:|
| 8 | 100 |  100 |  |               0 |
| 16 | 250 |  150 |  |               0 |
| 32 | 500 |  200 |  |               0 |

---
**Disclaimer: We couldn't get the before results because when we got here we'd already done the most of the laboratory**
## 8. Quality-attribute analysis

For your main synchronization decision answer:

- What problem were you solving?

**The core problem was the existence of multiple threads accessing and modifying a shared mutable state without any concurrency control. This generated race conditions, resulting in lost parcels, duplicated processing, etc. Besides the systems used active waiting for pause control wasting a lot of CPU cycles.**
- What invariant had to be preserved?

**- No parcel should be lost or processed more than once**

**- The total parcels must exactly match the size of the delivery registry**

**- State reports during a pause must show a consistent state where no threads are modifying information midway through a transaction.**
- What alternatives did you consider?

**- Global synchronization like adding the synchronized keyword to absolutely all methods of the shared classes.**

**- Using concurrent structures and specific monitors replacing unsafe standard collections with concurrency-optimized versions and barely using synchronized blocks strictly for thread coordination.**
- Why did you choose the final mechanism?

**The second alternative was chosen because applying synchronization at the method level indiscriminately would have turned the concurrency into a secuential execution creating like bottlenecks.**
- What are its consequences?

**The systems now has a lot of quality managing thread-safety, passing the tests from RaceConditionProbe with zero anomalies. But the logic gets slightly more complex**

## 2. Quality attributes

Discuss the impact of your solution on at least:

- **Correctness / reliability**

**The impact is very positive. The system before was non-deterministic but now it can be predictable and very consistent, keeping consistency in the business rules.**
- **Performance / throughput**

**Performance improved significantly compared to a potential global lock solution with synchronized blocks. By reducing the size of the critical regions, threads can process parcels concurrently, besides, eliminating the paused loop and replacing it with wait, we improved the throughput of the machine.**

- **Maintainability**

**Although the complex of the code slightly increased we could encapsule the synchronization logic, most of the code remained clean and focused on the workflow logic.

## 3. Architectural boundary question

Assume tomorrow the warehouse is deployed as **three independent JVM instances** behind a load balancer.

Answer:

> Would your `synchronized` blocks still protect the business invariant across all three instances? Why or why not?
- **No, because java monitors and keywords like synchronized operate exclusively at the internal memory level of a JVM. If there are 3 instances deployed there are 3 JVM's with 3 distinct memory spaces. A lock on thread 1 don't have visibility over the second one.**

> What type of architectural mechanism would then be required?

**Distributed Locking using a fast data store like Redis or Apache ZooKeeper to create a central lock. Before processing a parcel, an instance must request the lock in Redis; if another instance holds it, the current one must wait.
Do not implement a distributed solution. Analyze it.

---

