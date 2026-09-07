/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.benchmarks;

import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Concurrent steady-state throughput of TBBPM definitions.
 *
 * <p>CompileFlow engines are thread-safe (documented in
 * {@link ProcessEngine}); this benchmark exercises the engine under
 * controlled concurrency to answer the "what happens under load?"
 * question with reproducible evidence.
 *
 * <p>The default profile runs four request threads sharing one engine instance.
 * Override the thread count with JMH's {@code -t} command-line option.
 *
 * <p>Throughput mode answers "total ops/ms under contention";
 * pair with {@code -prof gc} to measure allocation rate under
 * concurrency.
 *
 * <p>Default JMH configuration: 5 warmup iterations of 2s each,
 * 5 measurement iterations of 5s each, 1 fork.
 *
 * @author yusu
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 5)
@Fork(value = 1)
@Threads(4)
public class CompileFlowConcurrentExecuteBenchmark {
    private ProcessEngine engine;
    private ProcessRef.Version ref;

    private static void verifyResult(Map<String, Object> result) {
        if (!Integer.valueOf(42).equals(result.get("result"))) {
            throw new IllegalStateException("Benchmark flow returned an unexpected result: " + result);
        }
    }

    /**
     * Sets up the engine and loads the hello flow.
     */
    @Setup
    public void setup() {
        this.engine = ProcessEngineFactory.create();
        try {
            ProcessDefinition definition =
                    ProcessDefinition.classpath(ProcessModelType.TBBPM, "bpm.sample.hello", "flows/hello.bpm");
            this.ref = ProcessRef.version("bpm.sample.hello", "benchmark-v1");
            this.engine.runtime().load(ref, definition);
            verifyResult(engine.execute(ref, Map.of("value", 40)).orElseThrow());
        } catch (RuntimeException | Error failure) {
            try {
                engine.close();
            } catch (RuntimeException | Error closing) {
                failure.addSuppressed(closing);
            }
            throw failure;
        }
    }

    /**
     * Releases the engine.
     */
    @TearDown
    public void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    /**
     * Each thread creates its own context and executes the flow.
     * Context creation is intentionally per-call to model real
     * request handling where each invocation carries distinct input.
     *
     * @return execution result, used to defeat dead-code elimination
     */
    @Benchmark
    public Object concurrentExecute() {
        Map<String, Object> context = new HashMap<>();
        context.put("value", 40);
        return engine.execute(ref, context).orElseThrow();
    }
}
