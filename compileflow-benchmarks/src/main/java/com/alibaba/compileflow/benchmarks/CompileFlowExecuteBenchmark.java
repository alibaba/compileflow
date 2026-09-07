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
import org.openjdk.jmh.annotations.Warmup;

/**
 * Steady-state throughput of exact-version execution after the runtime is loaded.
 *
 * <p>The flow is a minimal 3-node TBBPM flow equivalent to
 * {@code examples/spring-boot-basic/src/main/resources/flows/hello.bpm}: it
 * computes {@code value + 2} and returns it. Compare against
 * {@link BaselineJavaBenchmark#baselineAddTwo} which calls the same arithmetic
 * as a plain Java method.
 *
 * <p>Default JMH configuration: 5 warmup iterations of 2s each, 5 measurement
 * iterations of 5s each, 1 fork. Override on the command line with
 * {@code -wi}, {@code -i}, {@code -f}, {@code -r}.
 *
 * @author yusu
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 5)
@Fork(value = 1)
public class CompileFlowExecuteBenchmark {
    private ProcessEngine engine;
    private ProcessRef.Version ref;

    private static void verifyResult(Map<String, Object> result) {
        if (!Integer.valueOf(42).equals(result.get("result"))) {
            throw new IllegalStateException("Benchmark flow returned an unexpected result: " + result);
        }
    }

    /**
     * Sets up the engine and loads the hello flow under an exact version.
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
     * Releases the engine to avoid leaking thread pools across forks.
     */
    @TearDown
    public void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    /**
     * Hot-path execute. The engine returns the cached runtime; this method
     * measures the cost of routing to the cached runtime, dispatching the
     * process instance, and returning the map result.
     *
     * @return execution result, used to defeat dead-code elimination
     */
    @Benchmark
    public Object execute() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("value", 40);
        return engine.execute(ref, variables).orElseThrow();
    }
}
