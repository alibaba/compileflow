# CompileFlow Benchmarks

JMH benchmarks for the CompileFlow engine. This module provides a reproducible harness for the performance discussion in
the project documentation. The repository does not publish context-free performance ratios.

The suite covers steady-state throughput, first-definition execution, BPMN parity, concurrent execution, Durable
cold-cache preparation, warm Store-free Machine advancement, and real-PostgreSQL Durable boundary latency. It compares
CompileFlow with hand-written Java or another CompileFlow path; this module has no third-party process-engine dependency.

## Quick start

```bash
# 1. Build the benchmark module and its reactor dependencies.
./mvnw verify -Pbenchmarks -pl compileflow-benchmarks -am

# 2. Run a single benchmark class:
java -jar compileflow-benchmarks/target/compileflow-benchmarks.jar \
    -rf json \
    -rff compileflow-benchmarks/target/jmh-result.json \
    CompileFlowExecuteBenchmark
```

## Benchmark suite

| Benchmark class                         | Mode           | What it measures                                                                                 | Baseline                                                      |
|-----------------------------------------|----------------|--------------------------------------------------------------------------------------------------|---------------------------------------------------------------|
| `BaselineJavaBenchmark`                 | Throughput     | Hand-written Java equivalent of `value + 2`                                                      | absolute floor                                                |
| `CompileFlowExecuteBenchmark`           | Throughput     | TBBPM exact-version execution after `admin.load`                                                 | `BaselineJavaBenchmark`                                       |
| `BpmnExecuteBenchmark`                  | Throughput     | BPMN exact-version execution after `admin.load`                                                  | `CompileFlowExecuteBenchmark` (same engine, different format) |
| `CompileFlowFirstExecutionBenchmark`    | SingleShotTime | First definition execution in a fresh fork; excludes JVM startup and engine construction         | independent fork samples                                      |
| `CompileFlowConcurrentExecuteBenchmark` | Throughput     | Concurrent exact-version execution under 4 threads                                               | `CompileFlowExecuteBenchmark` (single-thread)                 |
| `CompileFlowAliasExecuteBenchmark`      | Throughput     | Concurrent stable Alias and weighted-canary Alias execution under 4 threads                      | `CompileFlowConcurrentExecuteBenchmark`                       |
| `ProcessCallExecuteBenchmark`           | Throughput     | Exact-version execution through minimal Process call chains at depths 0, 1, and 4                 | depth 0 under the same benchmark configuration                |
| `OperationGateBenchmark`                | Throughput     | Steady-state lifecycle admission using exact cache-line-striped counters, non-atomic `LongAdder`, shared atomics, or a fair read lock | `AtomicBoolean` state read                              |
| `StructuredGatewayCodeGenerationBenchmark` | Average time | Parse, graph analysis, and Java-source generation for wide and deeply nested structured gateways | compare matching shape and size across commits                |
| `DurableProcessRuntimeLoadBenchmark`    | SingleShotTime | Parse, validate, lower, generate, javac, load, and construct one uncached Durable process runtime from an exact definition at 50/200/1000/5000 nodes | compare matching size across commits                          |
| `DurableMachineAdvanceBenchmark`        | Average time   | Advance one already compiled Store-free Machine from a fresh continuation through completion for sequence-64, loop-64, Parallel-8, and Inclusive-8 | compare the same named scenario across commits                |
| `DurablePostgresBoundaryBenchmark`      | Sample time    | Current seven-table Durable admission, Run claim/completion, Wait, Timer, Complete, Effect dispatch/completion/UNKNOWN/reconcile, and Outbox transactions on real PostgreSQL | independent boundary distributions                            |

Default JMH configuration for throughput benchmarks: 5 warmup iterations × 2s, 5 measurement iterations × 5s, 1 fork.
The first-execution benchmark performs one un-warmed measurement in each of 10 independent forks. Override with
`-wi`, `-i`, `-f`, `-r`, and `-t` flags on the command line. The scheduled CI measurement uses 3 forks for each
single-thread steady-state throughput benchmark, keeps the scenario-specific defaults for first execution, and runs a
shorter 1/4/16/32/64-thread sweep for exact-version and Alias contention.

### Durable PostgreSQL boundaries

The Durable benchmark requires a disposable PostgreSQL database. It cleans the database's `public` schema and therefore
requires an explicit destructive-test opt-in:

```bash
export COMPILEFLOW_DURABLE_BENCHMARK_JDBC_URL=jdbc:postgresql://127.0.0.1:5432/compileflow_benchmark
export COMPILEFLOW_DURABLE_BENCHMARK_USERNAME=compileflow_benchmark
export COMPILEFLOW_DURABLE_BENCHMARK_PASSWORD='replace-me'
export COMPILEFLOW_DURABLE_BENCHMARK_ALLOW_CLEAN=true

java -jar compileflow-benchmarks/target/compileflow-benchmarks.jar \
  -rf json \
  -rff compileflow-benchmarks/target/jmh-durable-postgres.json \
  -t 1 -wi 2 -i 3 -w 1s -r 2s -f 1 \
  -p snapshotBytes=16384 \
  DurablePostgresBoundaryBenchmark
```

Use a dedicated database identity and database. Never point this benchmark at a shared or production database. The
measured operations use real PostgreSQL, Flyway V1→latest, database-time fencing, transactional Run/Effect/Outbox
authority, and the current Durable envelope representation. Durable Kernel does not provide payload encryption or a
KEK; deployments that require encryption must place that responsibility in their configured storage/security boundary.
Start and Wait-completion identities remain unique; claim/Wait/Effect setup and post-claim settlement are outside the
corresponding measured boundary. `snapshotBytes` defaults to a 16 KiB, 64 KiB, 256 KiB, and 1 MiB matrix. Pin one value
when comparing transaction types, then use the matrix to inspect payload-size sensitivity. The scheduled workflow also
runs Start, Run claim, and Effect reconciliation at 1/4/16/32 threads; this is a contention trend, not a capacity claim.

## Methodology

- The flow under test is a minimal 3-node flow (`start → scriptTask → end`)
  that computes `value + 2`. TBBPM uses `flows/hello.bpm`; BPMN uses
  `flows/hello.bpmn` (semantically identical, QL scriptFormat).
- The baseline calls a hand-written method that performs the same arithmetic.
- Hot-path benchmarks load one immutable version during setup and execute that exact reference. Each operation creates a
  fresh request-variable map, matching the baseline and avoiding shared mutable input across threads.
- The Process call benchmark loads the leaf before its exact-version parents and compares a no-call control with one-
  and four-call chains. Every Process is otherwise a minimal start-to-end flow, so the result isolates call-graph
  resolution and nested invocation costs rather than action work.
- Setup verifies that each benchmark flow succeeds and returns `42`; a broken flow fails the benchmark instead of
  producing a deceptively fast error path.
- The first-execution benchmark creates the engine before timing and executes one definition without a warmup
  invocation. The measured operation includes resource loading, parse, validation, code generation, Java compilation,
  generated class loading, and execution.
- The Durable preparation benchmark constructs the compiler and exact immutable definition before timing. Each fork
  measures one cold-cache parse-to-program pipeline and deliberately excludes Store I/O, Run admission, and database
  boundaries. Publish the `nodes` parameter with every result.
- The Durable Machine benchmark prepares and compiles one immutable scenario during trial setup. Its measured operation
  creates a fresh semantic continuation and advances the already compiled Program to a terminal result. It includes
  replayable Action invocation and all coordinator Turns required by structured gateways, but excludes preparation,
  javac, class loading, Store I/O, Run admission and PostgreSQL. Publish the `scenario` parameter with every result and
  compare only identical scenarios.
- Steady-state forks warm up the JIT before measurement; the first-execution benchmark has no warmup invocation.
- Results are emitted as JSON under `compileflow-benchmarks/target/`.
- Durable sample-time JSON includes percentile distributions for boundary transactions. Publish the snapshot-size
  parameter, thread count, database version, and topology with the raw result.
- Run `CompileFlowAliasExecuteBenchmark` with `-t 1`, `-t 4`, `-t 16`, `-t 32`, and `-t 64` to inspect contention
  scaling. Add `-prof gc` when investigating the canary routing-key digest or other allocation costs.

## Scope and limitations

- "First execution" is not JVM or application startup. JVM launch and engine construction are outside the timed
  operation.
- The concurrent benchmark is a closed-loop, saturated four-thread throughput test. It is not an open-loop production
  latency or tail-latency test.
- JMH allocation profilers are useful for diagnosis, but allocation rate is not retained heap. Publish retained-memory
  claims only from a separately described lifecycle test with an explicit reachability and GC protocol.

## Comparison boundary

This module measures CompileFlow against itself and against a plain-Java baseline. It does **not** include Activiti,
Camunda, or Flowable benchmarks. Cross-product comparisons are outside this repository because:

1. **Dependency isolation** — third-party engines bring heavy classpath footprints and database schemas that would
   pollute the CompileFlow reactor.
2. **Fairness** — a CompileFlow-tuned benchmark module cannot be the neutral arbiter of a cross-engine comparison.
3. **Scope** — throughput numbers alone do not capture differences in supported semantics, persistence, deployment, or
   operational behavior.

## Reporting results

When you publish benchmark results, include:

1. The exact JMH command line
2. The JDK distribution and version (`java -version`)
3. The OS, CPU model, and RAM
4. The CompileFlow commit hash
5. The raw JSON output (`-rf json -rff ...`)

Do not quote single-number comparisons (`100x faster`) without the above context. The comparison documents explicitly
avoid such numbers; new results should follow the same discipline.

The weekly and manually dispatched GitHub Actions workflow archives the raw JSON and runner environment for inspection.
Its engine and Durable contention sweeps use one fork, two 1-second warmups, and three 2-second measurements per thread
count; the Durable payload sweep uses the same settings across 16 KiB to 1 MiB snapshots. These are trend signals rather
than publication-quality benchmarks. Standard GitHub-hosted runners are fresh virtual machines, not dedicated stable
benchmark hardware, so the workflow does not fail builds from an absolute throughput threshold. Compare results only
when the runner class, JDK, JVM flags, PostgreSQL image, parameters, and JMH settings match; use score uncertainty and
repeated runs rather than a single point estimate. A release-blocking regression gate requires controlled, dedicated
hardware and a separately reviewed baseline. The PostgreSQL service in that workflow is a single ephemeral container
and provides a reproducible regression trend, not HA, failover, storage, or production-capacity evidence.

## Layout

```text
compileflow-benchmarks/
├── README.md
├── pom.xml
└── src/main/
    ├── resources/flows/
    │   ├── hello.bpm                 ← TBBPM benchmark flow
    │   └── hello.bpmn              ← BPMN 2.0 benchmark flow
    └── java/com/alibaba/compileflow/
        ├── benchmarks/
        │   ├── BaselineJavaBenchmark.java
        │   ├── CompileFlowExecuteBenchmark.java
        │   ├── BpmnExecuteBenchmark.java
        │   ├── CompileFlowFirstExecutionBenchmark.java
        │   ├── CompileFlowConcurrentExecuteBenchmark.java
        │   ├── DurableProcessRuntimeLoadBenchmark.java
        │   ├── DurableMachineAdvanceBenchmark.java
        │   ├── DurablePostgresBoundaryBenchmark.java
        │   ├── CompileFlowAliasExecuteBenchmark.java
        │   ├── ProcessCallExecuteBenchmark.java
        │   ├── OperationGateBenchmark.java
        │   └── StructuredGatewayCodeGenerationBenchmark.java
```

All benchmark sources stay in the benchmark-owned package. Alias setup uses the same public routing-snapshot helper as
other non-router callers instead of borrowing a production implementation package.

Benchmarks are not part of the default reactor. The `-Pbenchmarks` profile in the root `pom.xml` adds this module.
Pull-request CI does not run the suite; the weekly performance workflow validates expected benchmarks, forks,
iterations, and finite metrics before archiving results and environment metadata.
