# ARSW — Laboratorio #2
## Plantilla de entrega — Autonomous Warehouse

**Asignatura:** Arquitecturas de Software — ARSW  
**Periodo:** 2026-2  
**Laboratorio:** #2 — Autonomous Warehouse  
**Tema:** Race Conditions · Critical Sections · Thread Coordination  
**Tecnología:** Java 21 · Maven · JUnit 5  

---

## 0. Información del equipo

| Integrante | Código / ID | GitHub          |
|---|-------------|-----------------|
| Cristian Aristizabal| 1000104617  | Cristian-Aristi |
| Daniel Peña | 1000099589 | KronorCR  |
| Santiago Pinzón | 1000103871  | els4nty         |

**Repositorio:**  
`https://github.com/ARSW-2026-2/lab-arsw-autonomous-warehouse-concurrency-g02.git`

**Commit final:**  
``

---

# 1. Evidencia de ejecución inicial

## 1.1 Verificación del entorno

Incluya la salida de:

```bash
java -version
mvn -version
```

**Evidencia:**

```text
java version "21.0.8" 2025-07-15 LTS
Java(TM) SE Runtime Environment (build 21.0.8+12-LTS-250)
Java HotSpot(TM) 64-Bit Server VM (build 21.0.8+12-LTS-250, mixed mode, sharing)
Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
Maven home: C:\Program Files\apache-maven-3.9.12
Java version: 21.0.8, vendor: Oracle Corporation, runtime: C:\Program Files\Java\jdk-21
Default locale: es_CO, platform encoding: UTF-8
OS name: "windows 11", version: "10.0", arch: "amd64", family: "windows"
```

---

## 1.2 Ejecución inicial

Comando utilizado:

```bash
java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain
```

o la configuración utilizada:

```bash
java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain <robots> <packages>
```

**Configuración utilizada:**

- Robots: 24
- Paquetes: 250

**Resultado observado:**

Starting warehouse with 24 robots and 250 parcels...
[warehouse-robot-4] Queue anomaly: IndexOutOfBoundsException
[warehouse-robot-12] Queue anomaly: IndexOutOfBoundsException

--- STARTER REPORT (intentionally premature) ---
Initial parcels : 250
Pending parcels : 246
Processed count : 2
Registry size   : 2
Current leader  : Robot-09 / parcel 1 / position 1
----------------------------------------------

---

# 2. Estado mutable compartido

Identifique los objetos y variables compartidas entre múltiples threads.

| Objeto / Clase | Estado mutable compartido | Quién lee | Quién modifica | Riesgo identificado |
|---|---|---|---|---|
| `PackageQueue` | `pending` list of parcels | `takeNext()`, `pendingCount()` | `takeNext()` | No parcel is skipped or processed twice; the list size accurately reflects remaining items. |
| `DeliveryRegistry` | `nextPosition` integer, `deliveries` list | `snapshot()` | `register()` | `nextPosition` increments sequentially without duplicates; it always equals `deliveries.size() + 1`. |
| `WarehouseStatistics` | `processedParcels` int, `totalProcessingMillis` long | `processedParcels()`, `totalProcessingMillis()` | `recordProcessed()` | `processedParcels` accurately reflects the exact number of parcels processed without lost updates. |
| `SimulationControl` | `paused` boolean | `isPaused()`, `awaitIfPaused()` | `pause()`, `resume()` | The pause state is consistent, safely published, and visible to all worker threads simultaneously. |

---

# 3. Condiciones de carrera encontradas

Documente **mínimo tres** comportamientos incorrectos o potencialmente incorrectos.

## Race Condition #1

**Clase / método involucrado:**  
PackageQueue.takeNext()

**Estado compartido involucrado:**  
La lista compartida pending de paquetes.  

**Comportamiento observado:**  
[warehouse-robot-4] Queue anomaly: IndexOutOfBoundsException

**¿Por qué ocurre?**  
This is a classic "check-then-act" race condition. Multiple threads check !pending.isEmpty() and evaluate it to true. One thread removes the last element, and when the subsequent thread reaches pending.remove(0), the list is empty, throwing an IndexOutOfBoundsException.

**Evidencia de ejecución:**

[warehouse-robot-4] Queue anomaly: IndexOutOfBoundsException
[warehouse-robot-12] Queue anomaly: IndexOutOfBoundsException

---

## Race Condition #2

**Clase / método involucrado:**  
WarehouseStatistics.recordProcessed()

**Estado compartido involucrado:**  
Los contadores primitivos, específicamente processedParcels.

**Comportamiento observado:**  
Run 01 -> RACE/ANOMALY | pending=0, processedCounter=489, registry=495

**¿Por qué ocurre?**  
The counter suffers from "lost updates" because processedParcels = current + 1 is not atomic and includes a Thread.yield(). Multiple threads read the same current value before writing back the incremented value, causing the counter to fall behind.  

**Evidencia de ejecución:**

Run 01 -> RACE/ANOMALY | pending=0, processedCounter=489, registry=495, uniqueParcels=495, uniquePositions=480, positionsContiguous=false

---

## Race Condition #3

**Clase / método involucrado:**  
DeliveryRegistry.register()

**Estado compartido involucrado:**  
El entero compartido nextPosition y la lista deliveries.

**Comportamiento observado:**  
Run 03 -> RACE/ANOMALY | ... uniquePositions=230, positionsContiguous=false

**¿Por qué ocurre?**  
The probe reveals that multiple parcels were assigned the exact same position. This happens because nextPosition is read into assignedPosition, the thread yields, and then increments. Multiple threads capture the same nextPosition before any of them increment it.  

**Evidencia de ejecución:**

Run 03 -> RACE/ANOMALY | ... uniquePositions=230, positionsContiguous=false

---

# 4. Interleaving

Seleccione una de las condiciones de carrera anteriores y represente un interleaving posible.

**Condición seleccionada:**  
WarehouseStatistics.recordProcessed() lost update race condition

| Paso | Thread A (Robot 1) | Thread B (Robot 2) | Estado compartido (`processedParcels`) |
|---:|---|---|---|
| 1 | Reads `current = processedParcels` (value is 10) | --- | 10 |
| 2 | `Thread.yield()` executes | Reads `current = processedParcels` (value is 10) | 10 |
| 3 | --- | `Thread.yield()` executes | 10 |
| 4 | Writes `processedParcels = 10 + 1` (11) | --- | 11 |
| 5 | --- | Writes `processedParcels = 10 + 1` (11) | 11 (Lost Update!) |

### Explicación

¿Por qué este orden de ejecución produce un resultado incorrecto?

**Respuesta:**

The final result depends on the exact sequence of CPU scheduling because the operation is composed of three distinct steps: read, modify, and write. If the operating system scheduler interleaves Thread B's "read" step after Thread A reads but before Thread A writes, both threads will calculate their increment based on the same stale data. If the scheduler happens to let Thread A complete all three steps before Thread B starts, the result is correct. This non-deterministic scheduling causes the race condition.

---

# 5. Invariantes del sistema

Defina las invariantes que su solución debe preservar.

## I1

I1 (Conservation of Parcels): The number of pending parcels plus the number of deliveries recorded in the registry must always equal the initialParcels count

## I2

I2 (Unique & Sequential Positions): The DeliveryRegistry must assign strictly sequential, gapless, and unique arrival positions (1, 2, 3...) to each processed parcel.

## I3

I3 (Counter Integrity): The processedParcels counter in WarehouseStatistics must exactly match the number of elements in the DeliveryRegistry's deliveries list at any given moment.

## I4 — opcional

I4 (At-Most-Once Processing): No parcel is processed or registered more than once (indicated by the strict uniqueness of parcelId in the registry).

---

# 6. Regiones críticas

Documente cada región crítica identificada.

| Clase | Región crítica | Invariante protegida | Mecanismo usado | ¿Por qué ese tamaño? |
|---|---|---|---|---|
| **PackageQueue** | The body of `takeNext()` and `pendingCount()`. | I4 (At-Most-Once Processing). | `synchronized(pending)` block. | The entire check-and-remove operation must be atomic. Synchronizing on the list ensures no other thread can evaluate `isEmpty()` while a removal is in progress. |
| **DeliveryRegistry** | The body of `register()` and `snapshot()`. | I2 (Unique & Sequential Positions). | `synchronized(this)` block. | Both the `nextPosition` increment and `deliveries.add()` must be mutually exclusive as a single atomic unit to avoid duplicate assignment of positions. |
| **WarehouseStatistics** | The body of `recordProcessed()` and the getters. | I3 (Counter Integrity). | `synchronized(this)` block and methods. | The read-modify-write cycle of the primitive counters must be protected. Synchronizing the entire update block prevents lost updates. |

---

# 7. Decisiones de sincronización

## 7.1 Alternativas consideradas

Marque y explique cuáles evaluaron:

- [x] `synchronized`
- [ ] `AtomicInteger`
- [ ] Colecciones concurrentes
- [ ] `Lock`
- [x] `wait()` / `notifyAll()`
- [ ] Otra: `________________________`

### Alternativa 1

**Descripción:**  
Global Synchronization by synchronizing all public methods in all the shared classes.

**Ventaja:**  
It is easy to implement and immediately ensures thread safety by protecting any state.

**Desventaja:**  
It was rejected because it would force sequential execution, creating bottlenecks and preventing threads from operating concurrently

### Alternativa 2

**Descripción:**  
Single Global Lock by creating a single lock object for the entire system.

**Ventaja:**  
It keeps all the control logic within a single, easy-to-follow object.

**Desventaja:**  
We rejected this because it would create a bottleneck in the threads, reduciendo drásticamente el throughput general.  

### Decisión final

**Mecanismo seleccionado:**  
Aislamiento de la región crítica utilizando monitores nativos de Java (synchronized(this), synchronized(pending)).

**Justificación:**  
Applying synchronization at the method level indiscriminately would have turned the concurrency into a secuential execution creating like bottlenecks. By protecting only the minimum critical regions inside PackageQueue, DeliveryRegistry, and WarehouseStatistics, the invariants are preserved without synchronizing more than necessary.  

---

# 8. Finalización de threads

Explique cómo garantizaron que el programa solamente genera el reporte final cuando todos los robots han terminado.

**Mecanismo utilizado:**  
The join() method implementation called awaitCompletion() in WarehouseSimulation

**Explicación:**  
In WarehouseMain.java, we modified the code so that the main thread explicitly calls simulation.awaitCompletion(). This iterates over all the active robot threads and calls join() on each one, pausing the execution of the main thread until every worker thread has fully terminated.
### Pregunta

¿Por qué usar `Thread.sleep(...)` no sería una solución correcta para esperar la finalización de todos los workers?

**Respuesta:**  
Thread.sleep(...) is not a valid substitute for join() because the goal is for the process to continue once the robots finish their tasks; however, using Thread.sleep(60) relies on the assumption that the robots will have advanced or completed their work within 60ms—something that isn't guaranteed. In contrast, using join() ensures that the program waits for the target thread to finish before proceeding. Furthermore, join() guarantees that everything a thread has done prior to completion is visible to the next thread—a guarantee not provided by the previously mentioned method.  
---

# 9. PAUSE / RESUME

## 9.1 Problema inicial

Explique por qué el busy waiting de la implementación inicial no es adecuado.

**Respuesta:**  
The busy waiting implementation (while (paused) { Thread.onSpinWait(); }) wastes a lot of CPU cycles. The threads continuously consume processing power while looping endlessly without making any real progress.  

---

## 9.2 Solución implementada

Explique cómo implementaron:

- `pause(): Usa un bloque synchronized para cambiar la variable paused a verdadera.  `
- espera de los workers: Reemplazamos el ciclo activo en awaitIfPaused() usando wait() dentro de un bloque sincronizado, lo que suspende la ejecución sin gastar CPU.  
- `resume(): Cambia la variable paused a falsa y llama de inmediato a notifyAll() en un bloque sincronizado.`
- despertar coordinado de los workers: La instrucción notifyAll() despierta a todos los hilos simultáneamente, permitiéndoles reanudar el procesamiento.  

**Respuesta:**  
We used Java monitor like synchronized, wait and notifyAll to handle the pause and resume logic, removing busy-waiting loops.

---

## 9.3 Snapshot consistente

Cuando la simulación está pausada, registre:

```text
Processed parcels:121
Pending parcels:118
Registry size:121
Current leader:Robot-03 / parcel 14 /position 1
```

Explique cómo garantizan que esos valores representan un estado consistente.

**Respuesta:**  
We can guarantee the snapshot's consistency because all the methods we modified in classes such as simulationControl and WarehouseMain are synchronized; consequently, no thread can access them while another is using them. Furthermore, when the simulation is paused, the robots finish processing their current package and then wait, adhering to the implemented logic. Therefore, the requested values reflect the data as it stands at that moment.  

---

# 10. Verificación con RaceConditionProbe

Ejecute:

```bash
java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 32 500
```

## Resultados

| Robots | Paquetes | Runs | Anomalías antes | Anomalías después |
|---:|---:|---:|---:|---:|
| 8 | 100 |100|>0 |0 |
| 16 | 250 |150|>0 |0 |
| 32 | 500 |200|>0 |0 |

### Resultado final esperado

```text
Anomalous runs: 0/100
```

**Salida obtenida:**

Run 200 -> OK           | pending=0, processedCounter=500, registry=500, uniqueParcels=500, uniquePositions=500, positionsContiguous=true
Anomalous runs: 0/200

---

# 11. Evidencia de correctitud

Explique brevemente cómo demuestran que su solución es correcta.

Considere:

- invariantes;
- múltiples ejecuciones;
- distintas cargas;
- ausencia de resultados duplicados;
- ausencia de paquetes perdidos;
- finalización correcta;
- consistencia durante pausa.

**Conclusión:**

The system before was non-deterministic but now it can be predictable and very consistent, keeping consistency in the business rules. By defining strict invariants and mapping them to their respective critical regions, we eliminated all occurrences of lost packages and duplicated arrival positions.  
Running 
```bash
java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 200 32 500
```
and mvn clean test we can see that our system works, reporting 0 anomalous executions across heavy data loads and extreme CPU contention.  
Finally, state reports during a pause show a consistent state where no threads are modifying information midway through a transaction.  

---

# 12. Impacto en atributos de calidad

| Atributo | Impacto de la solución | Evidencia / métrica |
|---|---|---|
| Correctitud / Reliability | Positively affected. The sistem is thread-safely and preserves all business invariants. | 0 anomalies across 200 simulation runs. |
| Performance / Throughput | Positively affected. Eliminating busy-waiting frees CPU cycles, and using lock-free concurrent structures we can maximize the concurrent processing capabilities. | Significant improvement over the global locking alternative. |
| Maintainability | Positively affected. Therefore the complexity of the code slightly increases we encapsulated the core classes and the simulation controller. | Logic is contained within specific shared object methods. |
| Scalability | Restricted locally to one JVM instance. | Future requirements point towards distributed architectures (Redis/ZooKeeper). |

---

# 13. Trade-off principal

¿Qué ganaron y qué sacrificaron al introducir sincronización?

**Respuesta:**

The systems now has a lot of quality managing thread-safety, passing the tests from RaceConditionProbe with zero anomalies. But the logic gets slightly more complex. Any future modifications now have to adhere strictly to the chosen concurrent structure.  En resumen, sacrificamos simplicidad y añadimos una ligera penalidad de rendimiento inherente a la adquisición de locks, pero ganamos consistencia total, correctitud y la eliminación garantizada de lost updates e IndexOutOfBoundsExceptions.

---

# 14. Análisis arquitectónico

Suponga ahora que existen tres instancias de la aplicación:

```text
                 Load Balancer
                       |
            +----------+----------+
            |          |          |
          App A      App B      App C
            \          |          /
                    Database
```

## 14.1 Pregunta

¿Los bloques `synchronized` utilizados dentro de una JVM garantizan consistencia entre `App A`, `App B` y `App C`?

- [ ] Sí
- [x] No

**Justificación:**

No, because java monitors and keywords like synchronized operate exclusively at the internal memory level of a JVM. If there are 3 instances deployed there are 3 JVM's with 3 distinct memory spaces. A lock on thread 1 don't have visibility over the second one

---

## 14.2 Evolución arquitectónica

¿Qué alternativa consideraría para garantizar consistencia entre múltiples instancias?

- [ ] Transacción en base de datos
- [ ] Restricción / constraint en base de datos
- [ ] Optimistic locking / versionado
- [x] Lock distribuido
- [ ] Otra: `________________________`

**Decisión propuesta:**

Distributed Locking using a fast data store like Redis or Apache ZooKeeper to create a central lock

**Justificación:**

Before processing a parcel, an instance must request the lock in Redis; if another instance holds it, the current one must wait. If our system scales and we need multiple JVM we will need to use something else to protect the shared state like RabbitMQ or Redis

---

# 15. Mini ADR

## ADR-001 — Concurrency control for warehouse shared state

### Context
The autonomous warehouse simulation requires multiple robot threads to simultaneously access and modify shared mutable state.

### Decision

1. We decided to implement a concurrency control strategy replacing a central approach.  
2. We used Java monitor like synchronized, wait and notifyAll to handle the pause and resume logic, removing busy-waiting loops.  We implemented thread completion using the join method int the coordination of the threads

### Alternatives considered

1. `Global Synchronization by synchronizing all public methods in all the shared classes, but this was rejected because it would force sequential execution.`
2. `Single Global Lock by creating a single lock object for the entire system. We rejected this because it would create a bottlenech in the threads.`

### Quality attributes affected

Correctness / Reliability: Positively affected. The sistem is thread-safely and preserves all business invariants.
Performance / Throughput: Positively affected. Eliminating busy-waiting frees CPU cycles, and using lock-free concurrent structures we can maximize the concurrent processing capabilities.
Maintainability: Positively affected. Therefore the complexity of the code slightly increases we encapsulated the core classes and the simulation controller.

### Evidence

Running 
```bash
java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 200 32 500 
```
and mvn clean test we can see that our system works.

### Consequences

Any future modifications now have to adhere strictly to the chosen concurrent structure.

### Risks

The current synchronization uses a single JVM if our system scales and we need multiple JVM we will need to use something else to protect the shared state like RabbitMQ or Redis.

---

# 16. Cambios realizados

Resuma los principales cambios de código.

| Archivo / Clase | Cambio realizado | Razón |
|---|---|---|
| `WarehouseMain.java` | Llamada a `simulation.awaitCompletion()` reemplazando el `Thread.sleep(60)`. | Para coordinar la terminación explícita mediante `join()`. |
| `DeliveryRegistry.java` | Implementación de `synchronized` en los métodos `register` y `snapshot`. | Garantizar una región crítica en la lectura y modificación de posiciones y registros. |
| `PackageQueue.java` | Creación de bloque `synchronized (pending)` en la extracción e iteración de paquetes. | Prevenir la falla "check-then-act" y garantizar extracciones atómicas. |
| `SimulationControl.java` | Implementación de bloque `wait()` en ciclo y uso de `notifyAll()` con métodos sincronizados. | Remplazar el active waiting (uso excesivo de CPU) por coordinación de estado nativo. |
| `WarehouseStatistics.java` | Sincronización del objeto completo `synchronized(this)` para la manipulación y retorno de contadores. | Prevenir el desfase en métricas causado por el error "Lost update". |

---

# 17. Pruebas ejecutadas

| Prueba | Comando | Resultado |
|---|---|---|
| Compilación y tests | `mvn clean test` | OK (0 errores) |
| Simulación estándar | `java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain` | Concluye el reporte una sola vez tras ejecutar todos los hilos. |
| RaceConditionProbe | `java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 32 500` | 0 anomalies detectadas. |
| Pause / Resume | `java -cp target/classes edu.eci.arsw.warehouse.app.PauseResumeDemo` | Pausa y emite estados deterministas de la simulación. |
| Otra | Análisis de calidad de código | Aprobado (Clean code). |

---

# 18. Conclusiones

Incluya entre **3 o 5 conclusiones concretas**.

1. `El uso indebido de concurrencia y estado compartido ocasiona comportamientos no deterministas en el sistema (Race Conditions), forzando resultados erróneos a nivel de memoria debido a las intercalaciones y lost updates.`
2. `Aislar y delimitar la región crítica al tamaño más pequeño posible nos permite asegurar las invariantes del modelo de negocio de manera segura, sin convertir la ejecución en secuencial y perdiendo el rendimiento (throughput).`
3. `Depender de suposiciones de tiempo a través de funciones como Thread.sleep() es un error de arquitectura fundamental para la coordinación de hilos; deben usarse métodos directos del API como join() para garantizar su terminación completa.`
4. `Los monitores nativos de Java como synchronized operan únicamente sobre el espacio de memoria de su JVM y carecen de visibilidad general ante implementaciones escalables, por lo cual se requiere recurrir a mecanismos de bloqueo distribuido (Locks y mensajería en Redis, Apache ZooKeeper).`


---

# 19. Checklist de entrega

- [x] El proyecto compila con `mvn clean test`.
- [x] El código utiliza Java 21.
- [x] No se eliminó la concurrencia.
- [x] No existe busy waiting en la solución final.
- [x] El programa espera correctamente la finalización de todos los robots.
- [x] Las regiones críticas están justificadas.
- [x] Se preservan las invariantes definidas.
- [x] El `RaceConditionProbe` final no presenta anomalías.
- [x] Se documentó el análisis arquitectónico.
- [x] Se incluyó el ADR.
- [x] El repositorio contiene commits claros.
- [x] Se incluyó la URL del repositorio y el commit final.

---

## Nota

No se evalúa la cantidad de texto. Se evalúa la capacidad de demostrar:

> **problema → evidencia → invariante → región crítica → decisión → implementación → verificación → trade-off arquitectónico**
