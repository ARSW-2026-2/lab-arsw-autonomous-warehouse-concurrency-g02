# ADR-001: Concurrency control for warehouse shared state

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