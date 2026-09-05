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

import com.alibaba.compileflow.durable.runtime.action.DurableActionInvoker;
import com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializer;
import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.MachineTurnResult;
import com.alibaba.compileflow.durable.runtime.kernel.TurnBudget;
import com.alibaba.compileflow.durable.runtime.machine.DurableProcessCompiler;
import com.alibaba.compileflow.durable.runtime.program.DurableExecutionContext;
import com.alibaba.compileflow.durable.runtime.program.DurableJavaProgramCompiler;
import com.alibaba.compileflow.durable.runtime.program.DurableProgram;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionProvider;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import com.alibaba.compileflow.engine.tbbpm.semantic.TbbpmSemanticFrontend;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.openjdk.jmh.annotations.Warmup;

/**
 * Warm, Store-free execution cost of the compiled Durable Machine.
 *
 * <p>Program compilation, class loading, Run admission and PostgreSQL are outside the measured
 * operation. Every invocation starts one new in-memory semantic continuation and advances it
 * through completion, including any coordinator Turns required by a structured gateway.</p>
 *
 * @author yusu
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(3)
public class DurableMachineAdvanceBenchmark {
    private static final int SEQUENTIAL_STEPS = 64;
    private static final int LOOP_ITERATIONS = 64;
    private static final int GATEWAY_BRANCHES = 8;
    private static final int MAX_TURNS = GATEWAY_BRANCHES * 4 + 8;
    private static final TurnBudget TURN_BUDGET = TurnBudget.defaults();
    @Param({"SEQUENCE_64", "LOOP_64", "PARALLEL_8", "INCLUSIVE_8"})
    private String scenario;
    private DurableProgram program;
    private DurableExecutionContext context;
    private Map<String, Object> input;

    /**
     * Compiles one immutable scenario before JMH enters a measured operation.
     */
    @Setup
    public void setup() {
        ScenarioFixture fixture = switch (scenario) {
            case "SEQUENCE_64" -> fixture(sequenceModel(SEQUENTIAL_STEPS), Map.of());
            case "LOOP_64" -> fixture(loopModel(), Map.of("items", repeatedItems(LOOP_ITERATIONS)));
            case "PARALLEL_8" -> fixture(parallelModel(GATEWAY_BRANCHES), Map.of());
            case "INCLUSIVE_8" -> fixture(inclusiveModel(GATEWAY_BRANCHES), inclusiveInput(GATEWAY_BRANCHES));
            default -> throw new IllegalArgumentException("Unknown Durable Machine scenario: " + scenario);
        };
        program = fixture.program();
        context = fixture.context();
        input = fixture.input();
    }

    /**
     * Advances one fresh semantic continuation to a terminal result.
     *
     * @return the terminal Machine Turn, retained by JMH
     */
    @Benchmark
    public MachineTurnResult advanceToCompletion() throws Exception {
        ContinuationSnapshot continuation = ContinuationSnapshot.start(input);
        for (int turn = 0; turn < MAX_TURNS; turn++) {
            MachineTurnResult result = program.advance(continuation, List.of(), TURN_BUDGET, context);
            if (result.continuation() == null) {
                return result;
            }
            continuation = result.continuation();
        }
        throw new IllegalStateException("Durable Machine did not complete within " + MAX_TURNS + " Turns");
    }

    private ScenarioFixture fixture(String xml, Map<String, Object> scenarioInput) {
        TbbpmModel model = TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("benchmark.durable.machine", xml.getBytes(StandardCharsets.UTF_8)));
        var machinePlan = new DurableProcessCompiler().lower(new TbbpmSemanticFrontend().compile(model));
        DurableProgram compiled = new DurableJavaProgramCompiler(JavaDiagnosticsConfig.defaults())
            .compile(machinePlan, getClass().getClassLoader());
        DurableExecutionContext executionContext = new DurableExecutionContext(machinePlan,
                new DurableActionInvoker(ProcessComponentResolver.disabled(), ScriptExecutorRegistry.from(List.of()),
                        getClass().getClassLoader()), DurableWaitDescriptionProvider.defaults(),
                new DurableValueSerializer(machinePlan));
        return new ScenarioFixture(compiled, executionContext, Map.copyOf(scenarioInput));
    }

    private static List<String> repeatedItems(int size) {
        return Collections.nCopies(size, "item");
    }

    private static Map<String, Object> inclusiveInput(int branches) {
        Map<String, Object> input = new LinkedHashMap<>();
        for (int branch = 0; branch < branches; branch++) {
            input.put("enabled" + branch, true);
        }
        return input;
    }

    private static String sequenceModel(int steps) {
        StringBuilder xml = documentStart("benchmark.durable.machine.sequence");
        xml.append("  <start id=\"start\" g=\"0,0,32,32\"><transition to=\"step0\"/></start>\n");
        for (int step = 0; step < steps; step++) {
            appendReplayableTask(xml, "step" + step, step + 1 == steps ? "end" : "step" + (step + 1));
        }
        return documentEnd(xml);
    }

    private static String loopModel() {
        StringBuilder xml = documentStart("benchmark.durable.machine.loop");
        xml
            .append(
                    """
                  <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
                """)
            .append("  <start id=\"start\" g=\"0,0,32,32\"><transition to=\"loop\"/></start>\n")
            .append("  <foreach id=\"loop\" collection=\"items\"")
            .append(" item=\"item\" itemType=\"java.lang.String\"")
            .append(" index=\"itemIndex\"")
            .append(" g=\"40,0,180,120\">\n")
            .append("    <transition to=\"end\"/>\n")
            .append("    <start id=\"loopStart\"><transition to=\"body\"/></start>\n");
        appendReplayableTask(xml, "body", "loopEnd", "    ");
        xml.append("    <end id=\"loopEnd\"/>\n");
        xml.append("  </foreach>\n");
        return documentEnd(xml);
    }

    private static String parallelModel(int branches) {
        StringBuilder xml = documentStart("benchmark.durable.machine.parallel");
        xml
            .append("  <start id=\"start\" g=\"0,0,32,32\"><transition to=\"fork\"/></start>\n")
            .append("  <parallel id=\"fork\" g=\"50,0,40,40\">\n");
        for (int branch = 0; branch < branches; branch++) {
            xml.append("    <transition to=\"branch").append(branch).append("\"/>\n");
        }
        xml.append("  </parallel>\n");
        for (int branch = 0; branch < branches; branch++) {
            appendReplayableTask(xml, "branch" + branch, "join");
        }
        xml.append("  <parallel id=\"join\" g=\"260,0,40,40\"><transition to=\"end\"/></parallel>\n");
        return documentEnd(xml);
    }

    private static String inclusiveModel(int branches) {
        StringBuilder xml = documentStart("benchmark.durable.machine.inclusive");
        for (int branch = 0; branch < branches; branch++) {
            xml
                .append("  <var name=\"enabled")
                .append(branch)
                .append("\" dataType=\"java.lang.Boolean\" inOutType=\"param\"/>\n");
        }
        xml
            .append("  <start id=\"start\" g=\"0,0,32,32\"><transition to=\"fork\"/></start>\n")
            .append("  <inclusive id=\"fork\" g=\"50,0,40,40\">\n");
        for (int branch = 0; branch < branches; branch++) {
            xml
                .append("    <transition to=\"branch")
                .append(branch)
                .append("\" condition=\"enabled")
                .append(branch)
                .append(".booleanValue()\"/>\n");
        }
        xml.append("    <transition to=\"fallback\"/>\n").append("  </inclusive>\n");
        for (int branch = 0; branch < branches; branch++) {
            appendReplayableTask(xml, "branch" + branch, "join");
        }
        appendReplayableTask(xml, "fallback", "join");
        xml.append("  <inclusive id=\"join\" g=\"260,0,40,40\"><transition to=\"end\"/></inclusive>\n");
        return documentEnd(xml);
    }

    private static StringBuilder documentStart(String code) {
        return new StringBuilder()
            .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            .append("<bpm code=\"")
            .append(code)
            .append("\">\n");
    }

    private static String documentEnd(StringBuilder xml) {
        return xml.append("  <end id=\"end\" g=\"340,0,32,32\"/>\n</bpm>\n").toString();
    }

    private static void appendReplayableTask(StringBuilder xml, String id, String target) {
        appendReplayableTask(xml, id, target, "  ");
    }

    private static void appendReplayableTask(StringBuilder xml, String id, String target, String indent) {
        xml
            .append(indent)
            .append("<autoTask id=\"")
            .append(id)
            .append("\" g=\"120,0,100,40\">\n")
            .append(indent)
            .append(
                    "  <action type=\"java\" execution=\"replayable\" " + "class=\"java.lang.String\" method=\"isEmpty\"/>\n");
        if (target != null) {
            xml.append(indent).append("  <transition to=\"").append(target).append("\"/>\n");
        }
        xml.append(indent).append("</autoTask>\n");
    }

    private record ScenarioFixture(DurableProgram program, DurableExecutionContext context, Map<String, Object> input) {}
}
