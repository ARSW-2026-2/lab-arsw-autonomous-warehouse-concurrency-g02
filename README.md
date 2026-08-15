# ARSW — Laboratory 2
## Autonomous Warehouse: Race Conditions, Critical Sections and Thread Coordination

**Course:** Software Architectures — ARSW  
**Technology:** Java 21 · Maven · JUnit 5  
**Work mode:** teams of project 
**Development time:** one week  
**Suggested deadline:** Friday, August 14, 2026  

---

## 1. Purpose

In Laboratory 1 you explored **how concurrency can improve execution time** by comparing sequential execution, fixed thread pools and Java 21 virtual threads.

Laboratory 2 asks a different question:

> **What can go wrong when multiple threads modify shared mutable state, and how should we design a correct solution without synchronizing more than necessary?**

The laboratory connects implementation decisions with architectural reasoning. Your solution must preserve system invariants, eliminate race conditions, coordinate worker threads correctly, and explain the quality-attribute trade-offs created by your synchronization strategy.

---

## 2. Scenario

A distribution center uses autonomous robots to process parcels.

Each robot is modeled as an independent Java thread. Robots repeatedly:

1. request the next pending parcel;
2. process it;
3. register its delivery position;
4. update warehouse statistics;
5. continue until no parcels remain.

All robots share the following objects:

- `PackageQueue`
- `DeliveryRegistry`
- `WarehouseStatistics`
- `SimulationControl`

The starter project is **intentionally incorrect**. Several operations contain race conditions, inconsistent snapshots, and inefficient thread coordination.

Your task is not to remove concurrency. Your task is to make it **correct**.

---

## 3. Learning outcomes

By the end of the laboratory you should be able to:

- identify shared mutable state;
- explain a race condition using an interleaving;
- define invariants that must hold under concurrent execution;
- delimit the minimum critical region;
- use Java monitor primitives correctly;
- coordinate thread termination with `join()`;
- replace busy waiting with `wait()` / `notifyAll()`;
- distinguish thread safety from merely obtaining a correct result "most of the time";
- reason about correctness, performance and maintainability trade-offs.

---

## 4. Requirements

- JDK 21
- Maven 3.9+
- Git

Verify:

```bash
java -version
mvn -version
```

---

## 5. Build and run

Compile and run the unit tests:

```bash
mvn clean test
```

Run the starter simulation:

```bash
java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain
```

You may change the number of robots and parcels:

```bash
java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain 24 250
```

Run the race-condition probe:

```bash
java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe
```

A stronger probe:

```bash
java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 50 32 500
```

Run the pause/resume demonstration:

```bash
java -cp target/classes edu.eci.arsw.warehouse.app.PauseResumeDemo
```

> The starter is expected to produce anomalies. Do not treat a single successful execution as evidence of correctness.

---

# Part I — Diagnose before changing code

Do **not** modify the synchronization mechanisms yet.

Run the application and the probe several times.

## 1. Shared state inventory

Complete the following table in your report:

| Shared object | Mutable state | Readers | Writers | Possible invariant |
|---|---|---|---|---|
| `PackageQueue` |  |  |  |  |
| `DeliveryRegistry` |  |  |  |  |
| `WarehouseStatistics` |  |  |  |  |
| `SimulationControl` |  |  |  |  |

## 2. Evidence of incorrect behavior

Record at least **three different anomalies** observed during execution.

For each anomaly include:

- command used;
- execution number;
- relevant console output;
- class/method suspected;
- explanation.

### Evidence 1

```text
<your evidence>
```

### Evidence 2

```text
<your evidence>
```

### Evidence 3

```text
<your evidence>
```

## 3. Interleaving analysis

Choose one race condition and describe a possible interleaving.

Example format:

| Step | Thread A | Thread B | Shared state |
|---:|---|---|---|
| 1 |  |  |  |
| 2 |  |  |  |
| 3 |  |  |  |
| 4 |  |  |  |

Answer:

**Why is the final result dependent on scheduling?**

> _Write your answer here._

---

# Part II — Define the invariants

Before implementing synchronization, define what must always remain true.

At minimum evaluate these candidate invariants:

1. Every parcel is processed at most once.
2. No parcel disappears from the system.
3. Arrival positions are unique.
4. Arrival positions form a valid sequence from `1..N`.
5. The processed counter matches the number of delivery records.
6. When the simulation is reported as complete, no parcels remain pending.

For each invariant state whether it is:

- required;
- derived;
- unnecessary;
- or incomplete.

Then write your final set of invariants.

```text
I1:
I2:
I3:
...
```

---

# Part III — Protect only the critical regions

Correct the concurrency defects in:

- `PackageQueue`
- `DeliveryRegistry`
- `WarehouseStatistics`

You may use Java's monitor primitives (`synchronized`) and other Java SE synchronization utilities **only when you can justify them**.

## Restriction

Do not solve the exercise by blindly declaring every public method `synchronized`.

For each change document:

| Class | Critical region | Protected invariant | Synchronization mechanism | Why this granularity? |
|---|---|---|---|---|
|  |  |  |  |  |

Answer:

> What would happen to throughput if the protected region were unnecessarily large?

> _Write your answer here._

---

# Part IV — Correct thread completion

The starter prints a report before the worker threads have completed.

Modify the application so that:

1. all robots start concurrently;
2. the coordinating thread waits for all robot threads;
3. exactly one final report is printed;
4. the final report is consistent with the invariants.

Use Java's thread coordination mechanisms appropriately.

Document:

> Why is `Thread.sleep(...)` not a valid substitute for `join()` when waiting for a worker to finish?

> `Thread.sleep(...)` is not a valid substitute for `join()` because the goal is for the process to continue once the robots finish their tasks; however, using `Thread.sleep(60)` relies on the assumption that the robots will have advanced or completed their work within 60ms—something that isn't guaranteed. In contrast, using `join()` ensures that the program waits for the target thread to finish before proceeding.

> Furthermore, `join()` guarantees that everything a thread has done prior to completion is visible to the next thread—a guarantee not provided by the previously mentioned method.

---

# Part V — Implement PAUSE / RESUME correctly

The starter's `SimulationControl` uses active waiting:

```java
while (paused) {
    Thread.onSpinWait();
}
```

Replace this design with a **common monitor** using:

- `synchronized`
- `wait()`
- `notifyAll()`

Required behavior:

- `pause()` requests all robots to stop at a safe point;
- paused workers must not consume CPU in a busy loop;
- `resume()` wakes all waiting robots with a single coordination action;
- the simulation can continue and finish normally.

## Consistent paused snapshot

When the system is paused, report:

```text
Processed parcels
Pending parcels
Registry size
Current leader
```

Explain:

> How do you know the snapshot represents a consistent state rather than workers that are still changing shared data?

> We can guarantee the snapshot's consistency because all the methods we modified in classes such as `simulationControl` and `WarehouseMain` are `synchronized`; consequently, no thread can access them while another is using them. Furthermore, when the simulation is paused, the robots finish processing their current package and then wait, adhering to the implemented logic.

> Therefore, the requested values—Processed parcels, Pending parcels, Registry size, and Current leader—reflect the data as it stands at that moment.

---

# Part VI — Verification

After your changes run:

```bash
mvn clean test
java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 32 500
```

Expected target:

```text
Anomalous runs: 0/100
```

A correct result once is not sufficient.

Run at least three configurations:

| Robots | Parcels | Runs | Anomalies before | Anomalies after |
|---:|---:|-----:|---:|----------------:|
| 8 | 100 |  100 |  |               0 |
| 16 | 250 |  150 |  |               0 |
| 32 | 500 |  200 |  |               0 |

---

# Part VII — Architectural analysis

This laboratory is about more than Java syntax.

## 1. Decision analysis

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

# Part VIII — Mini ADR

Create:

```text
docs/ADR-001-concurrency-control.md
```

Use this structure:
# ADR
## Context
The autonomous warehouse simulation requires multiple robot threads to simultaneously access and modify shared mutable state
## Decision
We decided to implement a concurrency control strategy replacing a central approach.
1. We used Java monitor like synchronized, wait and notifyAll to handle the pause and resume logic, removing busy-waiting loops.
2. We implemented thread completion using the join method int the coordination of the threads.
## Alternatives considered
1. Global Synchronization by synchronizing all public methods in all the shared classes, but this was rejected because it would force sequential execution.
2. Single Global Lock by creating a single lock object for the entire system. We rejected this because it would create a bottlenech in the threads.


## Quality attributes affected
**Correctness / Reliability:** Positively affected. The sistem is thread-safely and preserves all business invariants.
**Performance / Throughput:** Positively affected. Eliminating busy-waiting frees CPU cycles, and using lock-free concurrent structures we can maximize the concurrent processing capabilities.
**Maintainability:** Positively affected. Therefore the complexity of the code slightly increases we encapsulated the core classes and the simulation controller.

## Evidence
**Running java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 200 32 500 and mvn clean test we can see that our system works.**

## Consequences
**Any future modifications now have to adhere strictly to the chosen concurrent structure.**
## Risks
**The current synchronization uses a single JVM if our system scales and we need multiple JVM we will need to use something else to protect the shared state like RabbitMQ or Redis.**

---

# Deliverables

Submit the repository containing:

```text
README.md
pom.xml
src/
docs/ADR-001-concurrency-control.md
docs/REPORT.md
```

`docs/REPORT.md` must include:

1. shared-state inventory;
2. observed anomalies;
3. one complete interleaving;
4. invariants;
5. critical-region justification;
6. pause/resume explanation;
7. verification results;
8. quality-attribute analysis.

---

# Constraints

- Java 21 only.
- Keep the worker model based on platform threads (`Thread`); **do not use thread pools or virtual threads in this laboratory**. Those were already studied in Laboratory 1.
- Do not remove concurrency.
- Do not replace the entire exercise with sequential execution.
- Do not solve every problem with one global lock.
- Avoid active waiting.
- Preserve the public behavior of the simulation.
- All code must compile with `mvn clean test`.
- The final race probe must demonstrate repeatable correctness.

---

# Evaluation criteria

| Criterion | Weight |
|---|---:|
| Identification and explanation of race conditions | 20% |
| Correct protection of critical regions | 25% |
| Thread completion + pause/resume coordination | 20% |
| Verification and reproducible evidence | 15% |
| Architectural reasoning and quality attributes | 15% |
| Code quality, Git history and documentation | 5% |
| **Total** | **100%** |

## Important

A solution that only "seems to work" but cannot explain its invariants and critical regions is incomplete.

A solution that uses excessive synchronization may be functionally correct but will lose points in **design** and **architectural reasoning**.

---

# Optional challenge

After completing the required solution, propose an alternative design using one of the following:

- `BlockingQueue`
- explicit `Lock` / `Condition`
- immutable messages / ownership transfer

Do not replace the required monitor exercise with the optional challenge.

Compare both designs in terms of:

- correctness;
- contention;
- readability;
- extensibility.
