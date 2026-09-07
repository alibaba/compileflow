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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Steady-state execution across an exact-version Process call chain.
 *
 * <p>Depth zero is the no-call control. Higher depths add one immutable exact-version call per
 * level while keeping every individual Process minimal. This makes call-graph resolution and
 * nested invocation overhead visible without mixing in action work.
 *
 * @author yusu
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 5)
@Fork(1)
public class ProcessCallExecuteBenchmark {
    private static final String NAMESPACE = "benchmark";
    private static final String VERSION = "v1";
    @Param({"0", "1", "4"})
    public int callDepth;
    private ProcessEngine engine;
    private ProcessRef.Version root;

    /**
     * Loads the leaf first, followed by each exact parent in the chain.
     */
    @Setup
    public void setup() {
        engine = ProcessEngineFactory.create();
        try {
            for (int level = callDepth; level >= 0; level--) {
                String code = code(level);
                String source = level == callDepth ? leafFlow(code) : callerFlow(code, code(level + 1));
                engine
                    .runtime()
                    .load(ProcessRef.version(NAMESPACE, code, VERSION),
                            ProcessDefinition.inline(ProcessModelType.TBBPM, code, source));
            }
            root = ProcessRef.version(NAMESPACE, code(0), VERSION);
            engine.execute(root, Map.of()).orElseThrow();
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
     * Releases engine-owned executors and runtime caches after each fork.
     */
    @TearDown
    public void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    /**
     * Executes the prepared exact-version call chain.
     *
     * @return execution result, used to defeat dead-code elimination
     */
    @Benchmark
    public Object execute() {
        return engine.execute(root, Map.of()).orElseThrow();
    }

    private static String code(int level) {
        return "benchmark.call." + level;
    }

    private static String leafFlow(String code) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="%s" name="%s">
                <start id="start" name="Start" g="50,50,32,32">
                    <transition to="end"/>
                </start>
                <end id="end" name="End" g="200,50,32,32"/>
            </bpm>
            """
            .formatted(code, code);
    }

    private static String callerFlow(String code, String childCode) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="%s" name="%s">
                <start id="start" name="Start" g="50,50,32,32">
                    <transition to="child"/>
                </start>
                <bpmCall id="child" code="%s" version="%s" g="150,40,88,48">
                    <transition to="end"/>
                </bpmCall>
                <end id="end" name="End" g="300,50,32,32"/>
            </bpm>
            """
            .formatted(code, code, childCode, VERSION);
    }
}
