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
package com.alibaba.compileflow.engine.test.quality.boundary;

import com.alibaba.compileflow.engine.ProcessModelType;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.config.ProcessDefinitionConfig;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LargeProcessCodeGenerationTest {
    private static final int ACTION_COUNT = 10_000;
    private static final int STRESS_DEFINITION_MAX_BYTES = 8 * 1024 * 1024;
    private static final String CODE = "quality.large.linear10000";

    private static String linearDefinition(int actionCount) {
        StringBuilder xml = new StringBuilder(actionCount * 512);
        xml.append(
                """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="quality.large.linear10000"
                 name="Large linear process">
                <var name="counter" dataType="java.lang.Integer"
                     defaultValue="0" inOutType="return"/>
                <start id="start" g="0,0,32,32">
                    <transition to="task0"/>
                </start>
            """);
        for (int i = 0; i < actionCount; i++) {
            String target = i + 1 == actionCount ? "end" : "task" + (i + 1);
            xml
                .append("    <scriptTask id=\"task")
                .append(i)
                .append("\" g=\"0,0,80,40\">\n")
                .append("        <transition to=\"")
                .append(target)
                .append("\"/>\n")
                .append("        <action type=\"script\" language=\"java\">\n")
                .append(
                        "            <input source=\"counter\" target=\"counter\" " + "dataType=\"java.lang.Integer\"/>\n")
                .append("            <output target=\"counter\" dataType=\"java.lang.Integer\"/>\n")
                .append("            <code><![CDATA[return counter + 1;]]></code>\n")
                .append("        </action>\n")
                .append("    </scriptTask>\n");
        }
        return xml.append("    <end id=\"end\" g=\"0,0,32,32\"/>\n</bpm>\n").toString();
    }

    private static String lexicalLoopDefinition(int actionCount) {
        StringBuilder xml = new StringBuilder(actionCount * 260);
        xml.append(
                """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="quality.large.lexicalLoop"
                 name="Large lexical loop">
                <var name="items"
                     dataType="java.util.List&lt;java.lang.Integer&gt;"
                     inOutType="param"/>
                <var name="counter" dataType="java.lang.Integer"
                     defaultValue="0" inOutType="return"/>
                <start id="start" g="0,0,32,32">
                    <transition to="loop"/>
                </start>
                <foreach id="loop" g="0,0,80,40"
                             collection="items"
                             item="item"
                             itemType="java.lang.Integer">
                    <transition to="end"/>
                    <start id="loopStart"><transition to="task0"/></start>
            """);
        for (int i = 0; i < actionCount; i++) {
            xml.append("        <scriptTask id=\"task").append(i).append("\" g=\"0,0,80,40\">\n");
            if (i + 1 < actionCount) {
                xml.append("            <transition to=\"task").append(i + 1).append("\"/>\n");
            } else {
                xml.append("            <transition to=\"loopEnd\"/>\n");
            }
            xml.append(
                    """
                        <action type="script" language="java">
                                <input target="counter" dataType="java.lang.Integer"
                                     source="counter"/>
                                <input target="item" dataType="java.lang.Integer"
                                     source="item"/>
                                <output dataType="java.lang.Integer"
                                     target="counter"/>
                                <code><![CDATA[return counter + item;]]></code>

                        </action>
                    </scriptTask>
                """);
        }
        return xml
            .append(
                    """
                <end id="loopEnd"/>
                </foreach>
                <end id="end" g="0,0,32,32"/>
            </bpm>
            """)
            .toString();
    }

    private static int occurrences(String value, String expected) {
        int count = 0;
        int fromIndex = 0;
        while ((fromIndex = value.indexOf(expected, fromIndex)) >= 0) {
            count++;
            fromIndex += expected.length();
        }
        return count;
    }

    private static String generatedLocalCollisionDefinition() {
        return """
            <bpm code="quality.generated-local-collision">
                <var name="items" dataType="java.util.List&lt;java.lang.Integer&gt;" inOutType="param"/>
                <var name="total" dataType="java.lang.Integer" defaultValue="0" inOutType="return"/>
                <start id="start" g="0,0,32,32"><transition to="loop"/></start>
                <foreach id="loop" g="0,0,80,40"
                             collection="items" item="position" index="scope"
                             itemType="java.lang.Integer">
                    <transition to="end"/>
                    <start id="loopStart"><transition to="task"/></start>
                    <scriptTask id="task" g="0,0,80,40">
                        <action type="script" language="java">
                                <input target="total" dataType="java.lang.Integer"
                                     source="total"/>
                                <input target="position" dataType="java.lang.Integer"
                                     source="position"/>
                                <input target="scope" dataType="java.lang.Integer"
                                     source="scope"/>
                                <output dataType="java.lang.Integer"
                                     target="total"/>
                                <code>return total + position + scope;</code>

                        </action>
                        <transition to="loopEnd"/>
                    </scriptTask>
                    <end id="loopEnd"/>
                </foreach>
                <end id="end" g="0,0,32,32"/>
            </bpm>
            """;
    }

    @Test
    void tenThousandNodeFlowGeneratesCompilesAndExecutesDeterministically() {
        ProcessDefinition definition =
                ProcessDefinition.inline(ProcessModelType.TBBPM, CODE, linearDefinition(ACTION_COUNT));

        ProcessDefinitionConfig stressDefinitions =
                ProcessDefinitionConfig.builder().maxBytes(STRESS_DEFINITION_MAX_BYTES).build();
        try (ProcessEngine engine = ProcessEngineFactory.create(ProcessEngineTestFactory
            .builder()
            .runtimeLoadTimeout(Duration.ofSeconds(30))
            .definitions(stressDefinitions)
            .build())) {
            String firstSource = engine.tooling().generateJavaCode(definition);
            String secondSource = engine.tooling().generateJavaCode(definition);

            assertThat(secondSource).isEqualTo(firstSource);
            assertThat(firstSource)
                .contains("public final class")
                .contains("@Override")
                .contains("private int _cf$runStart()")
                .contains("private int _cf$runTask200()")
                .contains("private int _cf$runEnd()")
                .doesNotContain("executeBranch_N", "while (current", "private int _cf$run(String");
            assertThat(occurrences(firstSource, "private int _cf$run")).isEqualTo(51);
            assertThat(occurrences(firstSource, "private void _cf$executeComputeCounter(String _cf$nodeId)")).isEqualTo(
                    1);
            assertThat(occurrences(firstSource, "private Integer _cf$invokeComputeCounter()")).isEqualTo(1);
            assertThat(occurrences(firstSource, "return counter + 1;")).isEqualTo(1);
            assertThat(occurrences(firstSource, "_cf$executeComputeCounter(\"task")).isEqualTo(ACTION_COUNT);
            assertThat(firstSource.indexOf("public Map<String, Object> execute"))
                .isLessThan(firstSource.indexOf("private int _cf$runStart()"));
            assertThat(occurrences(firstSource, "ActionExecutor.open(_cf$nodeId)")).isEqualTo(1);
            assertThat(firstSource).doesNotContain("() -> inline");
            assertThat(firstSource.indexOf("} catch (Exception _cf$actionFailure)"))
                .isLessThan(firstSource.indexOf("this.counter = _cf$actionResult;"));

            ProcessResult<Map<String, Object>> result = engine.execute(definition, Map.of());

            assertThat(result.isSuccess()).as("Large generated Java must compile and execute: %s", result.getError()).isTrue();
            assertThat(result.getOutput()).containsEntry("counter", ACTION_COUNT);
        }
    }

    @Test
    void largeLoopBodyKeepsLocalVariablesInsideItsLexicalMethod() {
        String code = "quality.large.lexicalLoop";
        ProcessDefinition definition =
                ProcessDefinition.inline(ProcessModelType.TBBPM, code, lexicalLoopDefinition(201));

        try (ProcessEngine engine = ProcessEngineTestFactory.create()) {
            String source = engine.tooling().generateJavaCode(definition);
            ProcessResult<Map<String, Object>> result = engine.execute(definition, Map.of("items", List.of(1)));

            assertThat(source)
                .contains("LoopSemantics.<Integer>snapshot(this.items, \"loop\", Integer.class)")
                .contains("Integer item = _cf$iterationValues.get(_cf$position);")
                .contains("private int _cf$runLoopStart(Integer item)")
                .contains("_cf$runLoopStart(item);")
                .doesNotContain("while (current", "private int _cf$run(String");
            assertThat(occurrences(source, "LoopSemantics.<Integer>snapshot")).isEqualTo(1);
            assertThat(occurrences(source, "private int _cf$runLoopStart")).isEqualTo(1);
            assertThat(occurrences(source, "private int _cf$runTask")).isEqualTo(1);
            assertThat(occurrences(source, "private void _cf$executeComputeCounter(String _cf$nodeId, Integer item)")).isEqualTo(
                    1);
            assertThat(occurrences(source, "return counter + item;")).isEqualTo(1);
            assertThat(occurrences(source, "_cf$executeComputeCounter(\"task")).isEqualTo(201);
            assertThat(result.isSuccess()).as("Lexical loop source must compile: %s", result.getError()).isTrue();
            assertThat(result.getOutput()).containsEntry("counter", 201);
        }
    }

    @Test
    void generatedLocalsDoNotCollideWithLoopVariableNames() {
        ProcessDefinition definition = ProcessDefinition.inline(ProcessModelType.TBBPM,
                "quality.generated-local-collision", generatedLocalCollisionDefinition());

        try (ProcessEngine engine = ProcessEngineTestFactory.create()) {
            String source = engine.tooling().generateJavaCode(definition);
            ProcessResult<Map<String, Object>> result = engine.execute(definition, Map.of("items", List.of(2)));

            assertThat(source)
                .contains("for (int _cf$position = 0;")
                .contains("Integer position = _cf$iterationValues.get(_cf$position);")
                .contains("ActionExecutor.ActionScope _cf$actionScope =");
            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(result.getOutput()).containsEntry("total", 2);
        }
    }
}
