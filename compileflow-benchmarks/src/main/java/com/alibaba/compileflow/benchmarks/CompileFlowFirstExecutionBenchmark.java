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
 * Cost of the first definition execution in a fresh benchmark JVM.
 *
 * <p>Each fork creates an engine before timing and performs exactly one measured
 * execution without a warmup invocation. The measurement includes resource loading,
 * XML parsing, model validation, Java code generation, JDK compilation, generated
 * class loading, and process execution. It excludes JVM process startup and engine
 * construction.
 *
 * <p>This is intentionally distinct from application startup and from the steady-state
 * cached-runtime path measured by {@link CompileFlowExecuteBenchmark}.
 *
 * @author yusu
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1, batchSize = 1)
@Fork(value = 10)
public class CompileFlowFirstExecutionBenchmark {
    private ProcessEngine engine;
    private ProcessDefinition source;

    /**
     * Creates the engine before the single measured invocation in each fork.
     */
    @Setup
    public void setup() {
        this.engine = ProcessEngineFactory.createTbbpm();
        this.source = ProcessDefinition.classpath("bpm.sample.hello", "flows/hello.bpm");
    }

    /**
     * Releases the fork-scoped engine.
     */
    @TearDown
    public void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    /**
     * Executes the first definition in this fork.
     *
     * @return execution result
     */
    @Benchmark
    public Object firstExecution() {
        Map<String, Object> context = new HashMap<>();
        context.put("value", 40);
        Map<String, Object> result = engine.execute(source, context).orElseThrow();
        if (!Integer.valueOf(42).equals(result.get("result"))) {
            throw new IllegalStateException("Benchmark flow returned an unexpected result: " + result);
        }
        return result;
    }
}
