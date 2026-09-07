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

import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.assembly.EngineAssembly;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
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
 * Concurrent steady-state throughput of Alias-selected execution.
 *
 * <p>The stable case measures local Alias resolution without weighted selection. The canary case
 * supplies a routing key and therefore includes deterministic SHA-256 bucket selection. Override
 * thread count with JMH {@code -t} and use {@code -prof gc} to measure allocation.
 *
 * @author yusu
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 5)
@Fork(1)
@Threads(4)
public class CompileFlowAliasExecuteBenchmark {
    private static final String CODE = "bpm.sample.hello";
    private ProcessEngine engine;
    private ProcessRef.Alias stableAlias;
    private ProcessRef.Alias canaryAlias;
    private ProcessExecutionOptions canaryOptions;

    private static Map<String, Object> variables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("value", 40);
        return variables;
    }

    private static void verifyResult(Map<String, Object> result) {
        if (!Integer.valueOf(42).equals(result.get("result"))) {
            throw new IllegalStateException("Benchmark flow returned an unexpected result: " + result);
        }
    }

    /**
     * Creates two locally ready versions and immutable stable/canary Alias localRoutingState.
     */
    @Setup
    public void setup() {
        ProcessEngineConfig config = ProcessEngineConfig.builder().discoverPlugins(false).build();
        LocalRoutingState localRoutingState = LocalRoutingState.requiringLocalInstallation();
        this.engine = EngineAssembly.create(config, EngineAssembly.assemble(config, localRoutingState));
        try {
            ProcessRef.Version versionOne = ProcessRef.version(CODE, "benchmark-v1");
            ProcessRef.Version versionTwo = ProcessRef.version(CODE, "benchmark-v2");
            ProcessDefinition definition = ProcessDefinition.classpath(ProcessModelType.TBBPM, CODE, "flows/hello.bpm");
            engine.runtime().load(versionOne, definition);
            engine.runtime().load(versionTwo, definition);

            this.stableAlias = ProcessRef.alias(CODE, "benchmark-stable");
            this.canaryAlias = ProcessRef.alias(CODE, "benchmark-canary");
            localRoutingState.applyAliasRoute(ProcessAliasRoute.stable(stableAlias, versionOne.version(), 1L));
            localRoutingState.applyAliasRoute(ProcessAliasRoute.canary(canaryAlias, versionOne.version(),
                    versionTwo.version(), 5_000, 1L));
            this.canaryOptions = ProcessExecutionOptions
                .builder()
                .aliasRouting(new AliasRoutingOptions("benchmark-user-42"))
                .build();

            verifyResult(engine.execute(stableAlias, Map.of("value", 40)).orElseThrow());
            verifyResult(engine.execute(canaryAlias, Map.of("value", 40), canaryOptions).orElseThrow());
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
     * Releases the engine and its executors after each fork.
     */
    @TearDown
    public void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    /**
     * Executes through a stable alias route.
     *
     * @return execution output used to defeat dead-code elimination
     */
    @Benchmark
    public Object stableAliasExecute() {
        return engine.execute(stableAlias, variables()).orElseThrow();
    }

    /**
     * Executes a weighted canary Alias using a deterministic routing key.
     *
     * @return execution output used to defeat dead-code elimination
     */
    @Benchmark
    public Object canaryAliasExecute() {
        return engine.execute(canaryAlias, variables(), canaryOptions).orElseThrow();
    }
}
