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
package com.alibaba.compileflow.durable.runtime.program;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.durable.runtime.machine.DurableMachinePlan;
import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializer;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierStepResult;
import com.alibaba.compileflow.durable.runtime.kernel.MachineTurnResult;
import com.alibaba.compileflow.durable.runtime.kernel.TurnBudget;
import com.alibaba.compileflow.durable.runtime.action.DurableActionInvoker;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionProvider;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Differential corpus shared by the default Process runtime and the Durable TBBPM target.
 *
 * @author yusu
 */
public class TbbpmProcessDurableDifferentialTest {
    private static final int MAX_TURNS = 128;

    @Test
    void sequenceProducesTheSameProcessOutput() throws Exception {
        assertEquivalent(sequenceFlow(), Map.of("input", 40), Map.of("result", 42));
    }

    @Test
    void embeddedScopeProducesTheSameProcessOutput() throws Exception {
        assertEquivalent(embeddedScopeFlow(), Map.of("input", 40), Map.of("result", 41));
    }

    @Test
    void forEachUsesTheSameSnapshotBindingsAndIterationOrder() throws Exception {
        assertEquivalent(forEachFlow(), Map.of("items", List.of("A", "B", "C")), Map.of("result", "A0B1C2"));
    }

    @Test
    void sequentialForEachAggregatesOrderedResults() throws Exception {
        assertEquivalent(aggregatingForEachFlow(), Map.of("items", List.of("A", "B", "C")),
                Map.of("results", List.of("A", "B", "C")));
        assertEquivalent(aggregatingForEachFlow(), Map.of("items", List.of()), Map.of("results", List.of()));
        assertEquivalent(breakingAggregatingForEachFlow(), Map.of("items", List.of("A", "B", "C")),
                Map.of("results", List.of("A", "B")));
        assertEquivalent(continuingAggregatingForEachFlow(), Map.of("items", List.of("A", "B", "C")),
                Map.of("results", List.of("A", "unassigned", "C")));
    }

    @Test
    void nestedSequentialLoopsUseTheSameLexicalScopes() throws Exception {
        assertEquivalent(nestedForEachFlow(), Map.of("items", List.of("A", "B")), Map.of("result", "A0B1A0B1"));
    }

    @Test
    void processDefaultsApplyOnlyWhenTheCallerOmittedTheVariable() throws Exception {
        assertEquivalent(defaultFlow(), Map.of(), Map.of("result", "definition-default"));
        Map<String, Object> explicitNullInput = new LinkedHashMap<>();
        explicitNullInput.put("value", null);
        Map<String, Object> explicitNullOutput = new LinkedHashMap<>();
        explicitNullOutput.put("result", null);
        assertEquivalent(defaultFlow(), explicitNullInput, explicitNullOutput);
    }

    @Test
    void parallelMergesTheSameDisjointBranchWrites() throws Exception {
        assertEquivalent(parallelFlow(), Map.of("seed", "order"), Map.of("left", "order-left", "right", "order-right"));
    }

    @Test
    void inclusiveUsesTheSameSelectedSetAndDefaultLaw() throws Exception {
        String definition = inclusiveFlow();
        assertEquivalent(definition, Map.of("seed", "order", "leftEnabled", true, "rightEnabled", true),
                Map.of("left", "order-left", "right", "order-right", "fallback", ""));
        assertEquivalent(definition, Map.of("seed", "order", "leftEnabled", false, "rightEnabled", false),
                Map.of("left", "", "right", "", "fallback", "order-fallback"));
    }

    @Test
    void nestedParallelScopesProduceTheSameDeterministicMerge() throws Exception {
        assertEquivalent(nestedParallelFlow(), Map.of("seed", "order"),
                Map.of("innerLeft", "order-inner-left", "innerRight", "order-inner-right", "outerRight",
                        "order-outer-right"));
    }

    private void assertEquivalent(String definition, Map<String, Object> input, Map<String, Object> expected)
            throws Exception {
        Map<String, Object> processOutput = executeProcess(definition, input);
        Map<String, Object> durableOutput = executeDurable(definition, input);

        assertThat(processOutput).containsExactlyInAnyOrderEntriesOf(expected);
        assertThat(durableOutput).containsExactlyInAnyOrderEntriesOf(expected);
        assertThat(durableOutput).isEqualTo(processOutput);
    }

    private Map<String, Object> executeProcess(String definition, Map<String, Object> input) throws Exception {
        String code = parse(definition).getCode();
        try (ProcessEngine engine = ProcessEngineFactory.createTbbpm()) {
            ProcessDefinition.Inline source = ProcessDefinition.inline(code, definition);
            engine.tooling().generateJavaCode(source);
            ProcessResult<Map<String, Object>> result = engine.execute(source, input);
            assertThat(result.isSuccess())
                .as("Process execution failed: %s %s",
                        result.getError() == null ? "<none>" : result.getError().getCode(),
                        result.getError() == null ? "<none>" : result.getError().getMessage())
                .isTrue();
            return result.getOutput();
        }
    }

    private Map<String, Object> executeDurable(String definition, Map<String, Object> input) throws Exception {
        TbbpmModel model = parse(definition);
        CompiledMachineProgram compiled = DurableCompilerTestSupport.compile(model, getClass().getClassLoader());
        DurableExecutionContext context = new DurableExecutionContext(compiled.machinePlan(),
                new DurableActionInvoker(ProcessComponentResolver.disabled(), ScriptExecutorRegistry.from(List.of()),
                        getClass().getClassLoader()), DurableWaitDescriptionProvider.defaults(),
                new DurableValueSerializer(compiled.machinePlan()));

        DurableValueSerializer serializer = new DurableValueSerializer(compiled.machinePlan());
        Map<String, Object> compiledOutput = executeDurableProgram(compiled.program(), input, context, serializer);

        DurableMachinePlan machine = DurableCompilerTestSupport.lower(model);
        DurableProgram interpreted =
                new DurableInterpretedProgramCompiler().compile(machine, getClass().getClassLoader());
        Map<String, Object> interpretedOutput = executeDurableProgram(interpreted, input, context, serializer);
        assertThat(interpretedOutput).isEqualTo(compiledOutput);
        return compiledOutput;
    }

    private Map<String, Object> executeDurableProgram(DurableProgram program, Map<String, Object> input,
            DurableExecutionContext context, DurableValueSerializer serializer) throws Exception {
        ContinuationSnapshot continuation =
                serializer.decode(serializer.encode(ContinuationSnapshot.start(new LinkedHashMap<>(input))));
        for (int turn = 0; turn < MAX_TURNS; turn++) {
            MachineTurnResult result = program.advance(continuation, List.of(), TurnBudget.defaults(), context);
            if (result.outcome() instanceof FrontierStepResult.Completed completed) {
                return completed.output();
            }
            assertThat(result.outcome()).isNotInstanceOf(FrontierStepResult.Failed.class);
            continuation = result.continuation();
        }
        throw new AssertionError("Durable execution did not complete within " + MAX_TURNS + " Turns");
    }

    private TbbpmModel parse(String definition) {
        return TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("native-durable-differential", definition.getBytes(StandardCharsets.UTF_8)));
    }

    private static String sequenceFlow() {
        return """
            <bpm code="differential.sequence">
              <var name="input" dataType="java.lang.Integer" inOutType="param"/>
              <var name="result" dataType="java.lang.Integer" defaultValue="0" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="first"/></start>
              %s
              %s
              <end id="end" g="360,0,32,32"/>
            </bpm>
            """
            .formatted(incrementTask("first", "input", "second"), incrementTask("second", "result", "end"));
    }

    private static String embeddedScopeFlow() {
        return """
            <bpm code="differential.embedded-scope">
              <var name="input" dataType="java.lang.Integer" inOutType="param"/>
              <var name="result" dataType="java.lang.Integer" defaultValue="0" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="scope"/></start>
              <subBpm id="scope" g="50,0,220,120">
                <start id="scopeStart" g="70,20,32,32"><transition to="increment"/></start>
                %s
                <end id="scopeEnd" g="230,20,32,32"/>
                <transition to="end"/>
              </subBpm>
              <end id="end" g="320,0,32,32"/>
            </bpm>
            """
            .formatted(incrementTask("increment", "input", "scopeEnd"));
    }

    private static String forEachFlow() {
        return """
            <bpm code="differential.foreach">
              <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <var name="result" dataType="java.lang.String" defaultValue="" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="loop"/></start>
              <foreach id="loop" collection="items"
                   item="item" itemType="java.lang.String" index="itemIndex" g="40,0,220,120">
                <transition to="end"/>
                <start id="loopStart"><transition to="append"/></start>
                <autoTask id="append" g="70,20,100,40">
                  <action type="java" execution="replayable" class="%s" method="append">
                      <input target="path" dataType="java.lang.String" source="result"/>
                      <input target="item" dataType="java.lang.String" source="item"/>
                      <input target="index" dataType="java.lang.Integer" source="itemIndex"/>
                      <output dataType="java.lang.String" target="result"/>

                  </action>
                  <transition to="loopEnd"/>
                </autoTask>
                <end id="loopEnd"/>
              </foreach>
              <end id="end" g="300,0,32,32"/>
            </bpm>
            """
            .formatted(TbbpmDifferentialActions.class.getName());
    }

    private static String aggregatingForEachFlow() {
        return """
            <bpm code="differential.foreach.output">
              <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <var name="slot" dataType="java.lang.String" inOutType="inner"/>
              <var name="results" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="return"/>
              <start id="start"><transition to="loop"/></start>
              <foreach id="loop" collection="items"
                   item="item" itemType="java.lang.String">
                <output target="results" source="slot"/>
                <transition to="end"/>
                <start id="loopStart"><transition to="work"/></start>
                <autoTask id="work">
                  <action type="java" execution="replayable" class="%s" method="identity">
                      <input target="item" dataType="java.lang.String" source="item"/>
                      <output dataType="java.lang.String" target="slot"/>

                  </action>
                  <transition to="loopEnd"/>
                </autoTask>
                <end id="loopEnd"/>
              </foreach>
              <end id="end"/>
            </bpm>
            """
            .formatted(TbbpmDifferentialActions.class.getName());
    }

    private static String breakingAggregatingForEachFlow() {
        return aggregatingForEachFlow()
            .replace("differential.foreach.output", "differential.foreach.output.break")
            .replace("item=\"item\" itemType=\"java.lang.String\"",
                    "item=\"item\" itemType=\"java.lang.String\" index=\"index\"")
            .replace("<transition to=\"loopEnd\"/>", "<transition to=\"stop\"/>")
            .replace("<end id=\"loopEnd\"/>",
                    "<break id=\"stop\" condition=\"index == 1\"><transition to=\"loopEnd\"/></break>" + "<end id=\"loopEnd\"/>");
    }

    private static String continuingAggregatingForEachFlow() {
        return """
            <bpm code="differential.foreach.output.continue">
              <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <var name="slot" dataType="java.lang.String" defaultValue="unassigned" inOutType="inner"/>
              <var name="results" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="return"/>
              <start id="start"><transition to="loop"/></start>
              <foreach id="loop" collection="items" item="item"
                   itemType="java.lang.String" index="index">
                <output target="results" source="slot"/>
                <transition to="end"/>
                <start id="loopStart"><transition to="skip"/></start>
                <continue id="skip" condition="index == 1"><transition to="work"/></continue>
                <autoTask id="work">
                  <action type="java" execution="replayable" class="%s" method="identity">
                      <input target="item" dataType="java.lang.String" source="item"/>
                      <output dataType="java.lang.String" target="slot"/>

                  </action>
                  <transition to="loopEnd"/>
                </autoTask>
                <end id="loopEnd"/>
              </foreach>
              <end id="end"/>
            </bpm>
            """
            .formatted(TbbpmDifferentialActions.class.getName());
    }

    private static String defaultFlow() {
        return """
            <bpm code="differential.defaults">
              <var name="value" dataType="java.lang.String" defaultValue="definition-default" inOutType="param"/>
              <var name="result" dataType="java.lang.String" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="copy"/></start>
              <autoTask id="copy" g="80,0,100,40">
                <action type="java" execution="replayable" class="%s" method="identity">
                    <input target="value" dataType="java.lang.String" source="value"/>
                    <output dataType="java.lang.String" target="result"/>

                </action>
                <transition to="end"/>
              </autoTask>
              <end id="end" g="220,0,32,32"/>
            </bpm>
            """
            .formatted(TbbpmDifferentialActions.class.getName());
    }

    private static String nestedForEachFlow() {
        return """
            <bpm code="differential.foreach.nested">
              <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <var name="result" dataType="java.lang.String" defaultValue="" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="outer"/></start>
              <foreach id="outer" collection="items"
                   item="outerItem" itemType="java.lang.String" index="outerIndex" g="40,0,300,180">
                <transition to="end"/>
                <start id="outerStart"><transition to="inner"/></start>
                <foreach id="inner" collection="items"
                     item="innerItem" itemType="java.lang.String" index="innerIndex" g="70,20,220,120">
                  <start id="innerStart"><transition to="append"/></start>
                  <autoTask id="append" g="100,40,100,40">
                    <action type="java" execution="replayable" class="%s" method="append">
                        <input target="path" dataType="java.lang.String" source="result"/>
                        <input target="item" dataType="java.lang.String" source="innerItem"/>
                        <input target="index" dataType="java.lang.Integer" source="innerIndex"/>
                        <output dataType="java.lang.String" target="result"/>

                    </action>
                    <transition to="innerEnd"/>
                  </autoTask>
                  <end id="innerEnd"/>
                  <transition to="outerEnd"/>
                </foreach>
                <end id="outerEnd"/>
              </foreach>
              <end id="end" g="380,0,32,32"/>
            </bpm>
            """
            .formatted(TbbpmDifferentialActions.class.getName());
    }

    private static String parallelFlow() {
        return """
            <bpm code="differential.parallel">
              %s
              <start id="start" g="0,0,32,32"><transition to="fork"/></start>
              <parallel id="fork" g="50,0,40,40">
                <transition to="leftTask"/><transition to="rightTask"/>
              </parallel>
              %s
              %s
              <parallel id="join" g="300,0,40,40"><transition to="end"/></parallel>
              <end id="end" g="380,0,32,32"/>
            </bpm>
            """
            .formatted(labelVariables("left", "right"), labelTask("leftTask", "left", "left", "join"),
                    labelTask("rightTask", "right", "right", "join"));
    }

    private static String inclusiveFlow() {
        return """
            <bpm code="differential.inclusive">
              %s
              <var name="leftEnabled" dataType="java.lang.Boolean" inOutType="param"/>
              <var name="rightEnabled" dataType="java.lang.Boolean" inOutType="param"/>
              <start id="start" g="0,0,32,32"><transition to="fork"/></start>
              <inclusive id="fork" g="50,0,40,40">
                <transition to="leftTask" condition="leftEnabled.booleanValue()"/>
                <transition to="rightTask" condition="rightEnabled.booleanValue()"/>
                <transition to="fallbackTask"/>
              </inclusive>
              %s
              %s
              %s
              <inclusive id="join" g="300,0,40,40"><transition to="end"/></inclusive>
              <end id="end" g="380,0,32,32"/>
            </bpm>
            """
            .formatted(labelVariables("left", "right", "fallback"), labelTask("leftTask", "left", "left", "join"),
                    labelTask("rightTask", "right", "right", "join"),
                    labelTask("fallbackTask", "fallback", "fallback", "join"));
    }

    private static String nestedParallelFlow() {
        return """
            <bpm code="differential.parallel.nested">
              %s
              <start id="start" g="0,0,32,32"><transition to="outerFork"/></start>
              <parallel id="outerFork" g="50,0,40,40">
                <transition to="innerFork"/><transition to="outerRightTask"/>
              </parallel>
              <parallel id="innerFork" g="120,0,40,40">
                <transition to="innerLeftTask"/><transition to="innerRightTask"/>
              </parallel>
              %s
              %s
              <parallel id="innerJoin" g="300,0,40,40"><transition to="outerJoin"/></parallel>
              %s
              <parallel id="outerJoin" g="400,0,40,40"><transition to="end"/></parallel>
              <end id="end" g="480,0,32,32"/>
            </bpm>
            """
            .formatted(labelVariables("innerLeft", "innerRight", "outerRight"),
                    labelTask("innerLeftTask", "innerLeft", "inner-left", "innerJoin"),
                    labelTask("innerRightTask", "innerRight", "inner-right", "innerJoin"),
                    labelTask("outerRightTask", "outerRight", "outer-right", "outerJoin"));
    }

    private static String incrementTask(String id, String source, String target) {
        return """
            <autoTask id="%s" g="80,0,100,40">
              <action type="java" execution="replayable" class="%s" method="increment">
                  <input target="value" dataType="java.lang.Integer" source="%s"/>
                  <output dataType="java.lang.Integer" target="result"/>

              </action>
              <transition to="%s"/>
            </autoTask>
            """
            .formatted(id, TbbpmDifferentialActions.class.getName(), source, target);
    }

    private static String labelVariables(String... outputs) {
        StringBuilder variables =
                new StringBuilder()
            .append("<var name=\"seed\" dataType=\"java.lang.String\" " + "inOutType=\"param\"/>\n");
        for (String output : outputs) {
            variables
                .append("<var name=\"")
                .append(output)
                .append("\" dataType=\"java.lang.String\" defaultValue=\"\" inOutType=\"return\"/>\n");
        }
        return variables.toString();
    }

    private static String labelTask(String id, String output, String label, String target) {
        return """
            <autoTask id="%s" g="160,0,100,40">
              <action type="java" execution="replayable" class="%s" method="label">
                  <input target="value" dataType="java.lang.String" source="seed"/>
                  <input target="label" dataType="java.lang.String" defaultValue="%s"/>
                  <output dataType="java.lang.String" target="%s"/>

              </action>
              <transition to="%s"/>
            </autoTask>
            """
            .formatted(id, TbbpmDifferentialActions.class.getName(), label, output, target);
    }
}
