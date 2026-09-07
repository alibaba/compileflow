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
package com.alibaba.compileflow.durable.runtime.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.runtime.kernel.BranchActivation;
import com.alibaba.compileflow.durable.runtime.kernel.ConcurrentBranchFrame;
import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.ForEachFrame;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierId;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.ResumePoint;
import com.alibaba.compileflow.durable.runtime.kernel.WhileFrame;
import com.alibaba.compileflow.durable.runtime.program.DurableCompilerTestSupport;
import com.alibaba.compileflow.durable.runtime.machine.DurableMachinePlan;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallTarget;
import com.alibaba.compileflow.engine.core.type.DataTypeException;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DurableValueSerializerTest {
    private static final ResumePoint POSITION = ResumePoint.afterElement("approval");

    @Test
    void javaSourceArraysPreserveTheirConcreteElementTypes() {
        for (var sample :
                Map
            .<String, Object>of("byte[]", new byte[] {1, 2, 3}, "int", 42, "java.lang.String[][]",
                    new String[][] {{"a", "b"}}, "java.util.List<byte[]>", List.of(new byte[] {4, 5}),
                    "java.util.List<java.lang.String>[]", new List<?>[] {List.of("a")})
            .entrySet()) {
            DurableValueSerializer serializer = new DurableValueSerializer(
                    machine(
                            "<var name=\"value\" dataType=\""
                            + sample.getKey().replace("<", "&lt;").replace(">", "&gt;") + "\" inOutType=\"param\"/>"));
            byte[] encoded = serializer.encode(ContinuationSnapshot.start(Map.of("value", sample.getValue())));
            ContinuationSnapshot recovered = serializer.decode(encoded);
            assertThat(recovered.variables().get("value"))
                .isInstanceOf(sample.getValue() instanceof List<?> ? List.class : sample.getValue().getClass());
            assertThat(serializer.encode(recovered)).isEqualTo(encoded);
        }
    }

    @Test
    void rejectsVoidAndNestedRawObjectVariableContracts() {
        assertThatThrownBy(() -> new DurableValueSerializer(
                machine("<var name=\"value\" dataType=\"void\" inOutType=\"param\"/>")))
            .isInstanceOf(DataTypeException.UnsupportedTypeException.class)
            .hasMessageContaining("void");
        for (String declaration :
                List.of("java.lang.Object", "java.lang.Object[]", "java.util.List<java.lang.Object>", "java.util.List",
                        "java.util.List[]")) {
            assertThatThrownBy(() -> new DurableValueSerializer(
                    machine(
                            "<var name=\"value\" dataType=\"" + declaration.replace("<", "&lt;").replace(">", "&gt;")
                            + "\" inOutType=\"param\"/>")))
                .as(declaration)
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void roundTripsDeclaredVariablesAndDefinitionTypedScopeState() {
        DurableValueSerializer serializer = new DurableValueSerializer(nestedLoopWaitMachine());
        BigDecimal order = new BigDecimal("12.30");
        List<String> lines = List.of("sku-1", "sku-2");
        ContinuationSnapshot source = new ContinuationSnapshot(POSITION,
                Map.of("order", order, "lines", lines, "status", "PENDING"),
                List.of(new ForEachFrame("items", 1, lines), new WhileFrame("poll", 3)));

        byte[] encoded = serializer.encode(source);
        ContinuationSnapshot decoded = serializer.decode(encoded);

        assertThat(decoded).isEqualTo(source);
        String json = new String(encoded, StandardCharsets.UTF_8);
        assertThat(json)
            .contains("\"resumeKind\":\"AFTER_ELEMENT\"", "\"resumeElement\":\"approval\"", "\"variables\"",
                    "\"scopes\"");
        assertThat(json).doesNotContain("maxIterations", "@class");
    }

    @Test
    void startHasNoResumeDescriptorOrScopeFrames() {
        DurableValueSerializer serializer =
                new DurableValueSerializer(
                        machine("<var name=\"id\" dataType=\"java.lang.String\" inOutType=\"param\"/>"));

        ContinuationSnapshot decoded =
                serializer.decode(serializer.encode(ContinuationSnapshot.start(Map.of("id", "42"))));

        assertThat(decoded).isEqualTo(ContinuationSnapshot.start(Map.of("id", "42")));
    }

    @Test
    void startPreservesOmittedFieldsAndExplicitNullAsDifferentInputs() {
        DurableValueSerializer serializer = new DurableValueSerializer(
                machine(
                        """
            <var name="provided" dataType="java.lang.String" inOutType="param"/>
            <var name="result" dataType="java.lang.String" inOutType="param"/>
            """));
        Map<String, Object> explicitNull = new LinkedHashMap<>();
        explicitNull.put("result", null);

        ContinuationSnapshot omitted =
                serializer.decode(serializer.encode(ContinuationSnapshot.start(Map.of("provided", "value"))));
        ContinuationSnapshot overridden = serializer.decode(serializer.encode(ContinuationSnapshot.start(explicitNull)));

        assertThat(omitted.variables()).containsExactlyEntriesOf(Map.of("provided", "value"));
        assertThat(overridden.variables()).containsKey("result").containsEntry("result", null);
    }

    @Test
    void processCallDefaultsUseTheExactChildParameterType() {
        DurableValueSerializer serializer = new DurableValueSerializer(
                machine("<var name=\"count\" dataType=\"java.lang.Integer\" inOutType=\"param\"/>"));
        ProcessCallPlan blankDefault = new ProcessCallPlan("child", new ProcessCallTarget.Classpath("child.bpm"),
                List.of(new ProcessCallPlan.Input(null, "count", " ")), List.of());
        ProcessCallPlan numericDefault = new ProcessCallPlan("child", new ProcessCallTarget.Classpath("child.bpm"),
                List.of(new ProcessCallPlan.Input(null, "count", "42")), List.of());
        ProcessCallPlan source = new ProcessCallPlan("child", new ProcessCallTarget.Classpath("child.bpm"),
                List.of(new ProcessCallPlan.Input("value", "count", null)), List.of());

        assertThat(serializer.normalizeProcessCallInput(blankDefault, Map.of())).containsEntry("count", null);
        assertThat(serializer.normalizeProcessCallInput(numericDefault, Map.of())).containsEntry("count", 42);
        assertThat(serializer.normalizeProcessCallInput(source, Map.of("count", "7"))).containsEntry("count", 7);
    }

    @Test
    void startRejectsInternalAndReturnVariablesOnEncodeAndRecovery() {
        DurableValueSerializer serializer = new DurableValueSerializer(
                machine("<var name=\"result\" dataType=\"java.lang.String\" inOutType=\"return\"/>"));

        assertThatThrownBy(() -> serializer.encode(ContinuationSnapshot.start(Map.of("result", "forged"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a Start input");
        assertThatThrownBy(() -> serializer.decode(
                bytes(
                        "{\"frontiers\":[{\"id\":\"root\",\"resumeKind\":\"START\","
                        + "\"resumeElement\":null,\"variables\":{\"result\":\"forged\"},"
                        + "\"scopes\":[],\"branches\":[],\"multiInstance\":null}]}")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a Start input");
    }

    @Test
    void multiFrontierContinuationRoundTripsBranchLineageAndSplitBaselines() {
        DurableValueSerializer serializer = new DurableValueSerializer(
                waitMachine("""
            <var name="approved" dataType="java.lang.Boolean" inOutType="param"/>
            """));
        Map<String, Object> baseline = Map.of("approved", false);
        BranchActivation leftActivation = new BranchActivation(0, "left");
        BranchActivation rightActivation = new BranchActivation(1, "right");
        Set<BranchActivation> selected = Set.of(leftActivation, rightActivation);
        ConcurrentBranchFrame left =
                new ConcurrentBranchFrame(FrontierId.ROOT, "fork", "join", leftActivation, baseline, selected);
        ConcurrentBranchFrame right =
                new ConcurrentBranchFrame(FrontierId.ROOT, "fork", "join", rightActivation, baseline, selected);
        ContinuationSnapshot source = new ContinuationSnapshot(List.of(new FrontierSnapshot(right.frontierId(), POSITION,
                        baseline, List.of(), List.of(right)),
                new FrontierSnapshot(left.frontierId(), POSITION, baseline, List.of(), List.of(left))));

        byte[] encoded = serializer.encode(source);
        ContinuationSnapshot decoded = serializer.decode(encoded);

        assertThat(decoded).isEqualTo(source);
        assertThat(decoded.frontiers())
            .extracting(FrontierSnapshot::frontierId)
            .containsExactly(right.frontierId(), left.frontierId());
        assertThat(new String(encoded, StandardCharsets.UTF_8))
            .contains("\"frontiers\"", "\"parentId\":\"root\"", "\"baseline\":{\"approved\":false}");
    }

    @Test
    void waitPayloadRoundTripsOnlyTypedDeclaredPartialState() {
        DurableValueSerializer serializer = new DurableValueSerializer(
                machine(
                        """
            <var name="approved" dataType="java.lang.Boolean" inOutType="param"/>
            <var name="note" dataType="java.lang.String" inOutType="param"/>
            """));

        byte[] encoded = serializer.encodeWaitPayload(Map.of("approved", true));

        assertThat(serializer.decodeWaitPayload(encoded)).containsExactlyEntriesOf(Map.of("approved", true));
        assertThat(new String(encoded, StandardCharsets.UTF_8)).isEqualTo("{\"approved\":true}");
    }

    @Test
    void waitPayloadRejectsUndeclaredOrIllTypedValuesBeforeCommit() {
        DurableValueSerializer serializer = new DurableValueSerializer(
                machine("<var name=\"approved\" dataType=\"java.lang.Boolean\" inOutType=\"param\"/>"));

        assertThatThrownBy(() -> serializer.encodeWaitPayload(Map.of("unknown", true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("undeclared");
        assertThatThrownBy(() -> serializer.encodeWaitPayload(Map.of("approved", "yes")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("declared type");
        assertThatThrownBy(() -> serializer.decodeWaitPayload(bytes("{\"unknown\":true}")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Undeclared");
    }

    @Test
    void effectOutputRejectsIllTypedProcessStateBeforeCommitAndOnRecovery() {
        DurableValueSerializer serializer = new DurableValueSerializer(effectMachine());

        assertThatThrownBy(() -> serializer.encodeEffectOutput("charge", Map.of("approved", "yes")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("declared type");
        assertThatThrownBy(() -> serializer.decodeEffectOutput("charge", bytes("{\"approved\":\"yes\"}")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("declared type");
        assertThatThrownBy(() -> serializer.encodeProcessResult(Map.of("approved", "yes")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("declared type");
    }

    @Test
    void rejectsUntypedAndUnorderedVariableContractsAtCompilation() {
        assertThatThrownBy(() -> new DurableValueSerializer(
                machine("<var name=\"value\" dataType=\"java.lang.Object\" inOutType=\"param\"/>")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Object");
        assertThatThrownBy(() -> new DurableValueSerializer(
                machine(
                        "<var name=\"values\" dataType=\"java.util.Set&lt;java.lang.String&gt;\" " + "inOutType=\"param\"/>")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ordered");
        assertThatThrownBy(() -> new DurableValueSerializer(
                machine("<var name=\"values\" dataType=\"java.util.List\" inOutType=\"param\"/>")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("concrete element type");
    }

    @Test
    void rejectsAmbiguousOrHostileJson() {
        DurableValueSerializer serializer = new DurableValueSerializer(
                machine("<var name=\"value\" dataType=\"java.lang.Double\" inOutType=\"param\"/>"));

        assertThatThrownBy(() -> serializer.decode(
                bytes("{\"resumeAfterElement\":null,\"variables\":{\"value\":1,\"value\":2}," + "\"scopes\":[]}")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> serializer.decode(
                bytes("{\"resumeAfterElement\":null,\"variables\":{\"value\":1},\"scopes\":[]} true")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> serializer.decode(
                bytes("{\"resumeAfterElement\":null,\"variables\":{\"value\":NaN},\"scopes\":[]}")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> serializer.decode(
                bytes(
                        "{\"resumeAfterElement\":null,\"variables\":{\"value\":1,\"@class\":" + "\"java.lang.Runtime\"},\"scopes\":[]}")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> serializer.decode(
                bytes("{\"resumeAfterElement\":null,\"variables\":{\"value\":1},\"scopes\":[]," + "\"extra\":true}")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRuntimeTypeMismatchAndSnapshotLimitOverflow() {
        DurableValueSerializer serializer = new DurableValueSerializer(
                machine("<var name=\"value\" dataType=\"java.lang.Integer\" inOutType=\"param\"/>"));
        assertThatThrownBy(() -> serializer.encode(ContinuationSnapshot.start(Map.of("value", "not-an-integer"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("declared type");

        DurableValueSerializer tiny = new DurableValueSerializer(machine(
                        "<var name=\"value\" dataType=\"java.lang.String\" inOutType=\"param\"/>"),
                new DurableValueSerializer.Limits(128, 16, 100, 20, 128, 32, 32), getClass().getClassLoader());
        assertThatThrownBy(() -> tiny.encode(ContinuationSnapshot.start(Map.of("value", "x".repeat(200)))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesScopeAgainstTheExactDefinitionRatherThanPersistedPolicyCopies() {
        DurableValueSerializer serializer = new DurableValueSerializer(whileWaitMachine(2));

        assertThatThrownBy(() -> serializer.encode(
                new ContinuationSnapshot(POSITION, Map.of(), List.of(new WhileFrame("poll", 2)))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("bound");
    }

    private static DurableMachinePlan machine(String variables) {
        return machine(variables,
                """
            <start id="start" g="0,0,32,32"><transition to="end"/></start>
            <end id="end" g="80,0,32,32"/>
            """);
    }

    private static DurableMachinePlan waitMachine(String variables) {
        return machine(variables,
                """
            <start id="start" g="0,0,32,32"><transition to="approval"/></start>
            <waitEventTask id="approval" event="approved" g="60,0,100,40"><transition to="end"/></waitEventTask>
            <end id="end" g="200,0,32,32"/>
            """);
    }

    private static DurableMachinePlan whileWaitMachine(int maxIterations) {
        return machine("",
                """
            <start id="start" g="0,0,32,32"><transition to="poll"/></start>
            <while id="poll" condition="true" maxIterations="%d" g="60,0,180,100">
              <transition to="end"/>
              <start id="pollStart"><transition to="approval"/></start>
              <waitEventTask id="approval" event="approved" g="90,20,100,40"><transition to="pollEnd"/></waitEventTask>
              <end id="pollEnd"/>
            </while>
            <end id="end" g="280,0,32,32"/>
            """
                    .formatted(maxIterations));
    }

    private static DurableMachinePlan nestedLoopWaitMachine() {
        return machine("""
            <var name="order" dataType="java.math.BigDecimal" inOutType="param"/>
            <var name="lines" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
            <var name="status" dataType="java.lang.String" inOutType="param"/>
            """,
                """
            <start id="start" g="0,0,32,32"><transition to="items"/></start>
            <foreach id="items" collection="lines"
                 item="line" itemType="java.lang.String" g="60,0,300,160">
              <transition to="end"/>
              <start id="itemsStart"><transition to="poll"/></start>
              <while id="poll" condition="true" maxIterations="10" g="90,20,200,100">
                <start id="pollStart"><transition to="approval"/></start>
                <waitEventTask id="approval" event="approved" g="120,40,100,40"><transition to="pollEnd"/></waitEventTask>
                <end id="pollEnd"/>
                <transition to="itemsEnd"/>
              </while>
              <end id="itemsEnd"/>
            </foreach>
            <end id="end" g="400,0,32,32"/>
            """);
    }

    private static DurableMachinePlan effectMachine() {
        return machine("<var name=\"approved\" dataType=\"java.lang.Boolean\" inOutType=\"return\"/>",
                """
            <start id="start" g="0,0,32,32"><transition to="charge"/></start>
            <autoTask id="charge" g="60,0,100,40">
              <action type="java" execution="effect" class="%s" method="execute">
                  <output dataType="java.lang.Boolean" target="approved"/>

              </action>
              <transition to="end"/>
            </autoTask>
            <end id="end" g="200,0,32,32"/>
            """
                    .formatted(Actions.class.getName()));
    }

    private static DurableMachinePlan machine(String variables, String nodes) {
        String xml = "<bpm code=\"serializer.test\">" + variables + nodes + "</bpm>";
        var model =
                TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("serializer.test", xml.getBytes(StandardCharsets.UTF_8)));
        return DurableCompilerTestSupport.lower(model);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public static final class Actions {
        public Boolean execute() {
            return Boolean.TRUE;
        }
    }
}
