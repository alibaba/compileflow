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
import java.util.Locale;
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
 * Parse, graph-analysis, and Java-source generation cost for structured gateways.
 *
 * <p>The wide shape stresses branch and region validation. The deep shape stresses
 * nested post-dominator regions and continuation ownership. Java compilation and
 * execution are intentionally outside this benchmark and covered by integration tests.
 *
 * @author yusu
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class StructuredGatewayCodeGenerationBenchmark {
    @Param({"WIDE", "DEEP"})
    private Shape shape;
    @Param({"32", "128", "256"})
    private int size;
    private ProcessEngine engine;
    private ProcessDefinition definition;

    /**
     * Creates and validates the selected deterministic model.
     */
    @Setup
    public void setup() {
        engine = ProcessEngineFactory.create();
        try {
            String code = "benchmark.gateway." + shape.name().toLowerCase(Locale.ROOT) + size;
            definition = ProcessDefinition.inline(ProcessModelType.TBBPM, code, model(code, shape, size));
            if (engine.tooling().generateJavaCode(definition).isBlank()) {
                throw new IllegalStateException("Gateway benchmark produced empty Java source");
            }
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
     * Releases engine-owned resources after the trial.
     */
    @TearDown
    public void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    /**
     * Generates Java source from the complete definition.
     *
     * @return generated source, retained by JMH to prevent dead-code elimination
     */
    @Benchmark
    public String generateJavaSource() {
        return engine.tooling().generateJavaCode(definition);
    }

    private static String model(String code, Shape shape, int size) {
        return switch (shape) {
            case WIDE -> wideModel(code, size);
            case DEEP -> deepModel(code, size);
        };
    }

    private static String wideModel(String code, int branchCount) {
        StringBuilder xml = documentStart(code);
        xml
            .append("  <start id=\"start\" g=\"0,0,32,32\"><transition to=\"split\"/></start>\n")
            .append("  <exclusive id=\"split\" g=\"0,0,80,48\">\n");
        for (int i = 0; i < branchCount; i++) {
            xml.append("    <transition to=\"branch").append(i).append('"');
            if (i < branchCount - 1) {
                xml.append(" condition=\"route == ").append(i).append("\"");
            }
            xml.append("/>\n");
        }
        xml.append("  </exclusive>\n");
        for (int i = 0; i < branchCount; i++) {
            task(xml, "branch" + i, "merge");
        }
        task(xml, "merge", "end");
        return documentEnd(xml);
    }

    private static String deepModel(String code, int depth) {
        StringBuilder xml = documentStart(code);
        xml.append("  <start id=\"start\" g=\"0,0,32,32\"><transition to=\"split0\"/></start>\n");
        for (int i = 0; i < depth; i++) {
            xml
                .append("  <exclusive id=\"split")
                .append(i)
                .append("\" g=\"0,0,80,48\">\n")
                .append("    <transition to=\"leaf")
                .append(i)
                .append("\" condition=\"false\"/>\n")
                .append("    <transition to=\"")
                .append(i + 1 < depth ? "split" + (i + 1) : "terminal")
                .append("\"/>\n")
                .append("  </exclusive>\n");
            task(xml, "leaf" + i, "merge" + i);
        }
        task(xml, "terminal", "merge" + (depth - 1));
        for (int i = depth - 1; i >= 0; i--) {
            task(xml, "merge" + i, i == 0 ? "end" : "merge" + (i - 1));
        }
        return documentEnd(xml);
    }

    private static StringBuilder documentStart(String code) {
        return new StringBuilder()
            .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            .append("<bpm code=\"")
            .append(code)
            .append("\">\n")
            .append("  <var name=\"route\" dataType=\"java.lang.Integer\" inOutType=\"param\"/>\n");
    }

    private static String documentEnd(StringBuilder xml) {
        return xml.append("  <end id=\"end\" g=\"0,0,32,32\"/>\n</bpm>").toString();
    }

    private static void task(StringBuilder xml, String id, String target) {
        xml
            .append("  <scriptTask id=\"")
            .append(id)
            .append("\" g=\"0,0,80,48\">\n")
            .append("    <action type=\"script\" language=\"qlexpress\">")
            .append("<input source=\"route\" target=\"route\" dataType=\"java.lang.Integer\"/>")
            .append("<code>route</code></action>\n")
            .append("    <transition to=\"")
            .append(target)
            .append("\"/>\n")
            .append("  </scriptTask>\n");
    }

    public enum Shape {
        WIDE,
        DEEP
    }
}
