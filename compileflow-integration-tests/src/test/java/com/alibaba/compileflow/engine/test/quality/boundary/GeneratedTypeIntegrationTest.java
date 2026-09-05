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

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeneratedTypeIntegrationTest {
    private static final ProcessDefinition.Inline PARAMETER_DEFAULTS = ProcessDefinition.inline("test.parameter-"
            + "defaults",
            """
            <bpm code="test.parameter-defaults" name="Parameter Defaults">
                <var name="optional" dataType="java.lang.String" inOutType="param"/>
                <var name="limit" dataType="java.lang.Integer" defaultValue="7" inOutType="param"/>
                <var name="result" dataType="java.lang.String" inOutType="return"/>
                <start id="start" g="0,0,32,32"><transition to="format"/></start>
                <scriptTask id="format" g="60,0,100,40">
                    <action type="script" language="java">
                            <input target="optional" dataType="java.lang.String"
                                 source="optional"/>
                            <input target="limit" dataType="java.lang.Integer"
                                 source="limit"/>
                            <output dataType="java.lang.String"
                                 target="result"/>
                            <code>return String.valueOf(optional) + ":" + limit;</code>

                    </action>
                    <transition to="end"/>
                </scriptTask>
                <end id="end" g="200,0,32,32"/>
            </bpm>
            """);
    private static final ProcessDefinition.Inline SOURCE = ProcessDefinition.inline("test.generated-types",
            """
        <?xml version="1.0" encoding="UTF-8" ?>
        <bpm code="test.generated-types" name="Generated Types">
            <var name="count" dataType="int" inOutType="param"/>
            <var name="items"
                 dataType="java.util.List&lt;java.util.Map&lt;java.lang.String, java.lang.Integer&gt;&gt;"
                 inOutType="param"/>
            <var name="result" dataType="int" inOutType="return"/>
            <start id="start" name="Start" g="50,50,32,32">
                <transition to="calculate"/>
            </start>
            <scriptTask id="calculate" name="Calculate" g="140,42,120,48">
                <action type="script" language="java">
                        <input target="count" dataType="int"
                             source="count"/>
                        <input target="items"
                             dataType="java.util.List&lt;java.util.Map&lt;java.lang.String, java.lang.Integer&gt;&gt;"
                             source="items"/>
                        <output dataType="int"
                             target="result"/>
                        <code><![CDATA[return count + items.get(0).get("delta");]]></code>

                </action>
                <transition to="end"/>
            </scriptTask>
            <end id="end" name="End" g="320,50,32,32"/>
        </bpm>
        """);
    private static final ProcessDefinition.Inline ACTION_OUTPUT_CONVERSION = ProcessDefinition.inline("test.action-"
            + "output-conversion",
            """
            <bpm code="test.action-output-conversion"
                 name="Action Output Conversion">
                <var name="a" dataType="java.lang.Integer"
                     inOutType="param"/>
                <var name="b" dataType="java.lang.Integer"
                     inOutType="param"/>
                <var name="javaResult" dataType="java.lang.Long"
                     inOutType="return"/>
                <var name="javaCodeResult" dataType="java.lang.Long"
                     inOutType="return"/>
                <var name="scriptResult" dataType="java.lang.Long"
                     inOutType="return"/>
                <start id="start" g="0,0,32,32">
                    <transition to="java"/>
                </start>
                <autoTask id="java" g="60,0,100,40">
                    <action type="java"
                                class="com.alibaba.compileflow.engine.test.support.mocks.MockJavaService"
                                method="add">
                            <input target="a" dataType="java.lang.Integer"
                                 source="a"/>
                            <input target="b" dataType="java.lang.Integer"
                                 source="b"/>
                            <output dataType="java.lang.Integer"
                                 target="javaResult"/>

                    </action>
                    <transition to="javaCode"/>
                </autoTask>
                <scriptTask id="javaCode" g="180,0,100,40">
                    <action type="script" language="java">
                            <input target="a"
                                 dataType="java.lang.Integer"
                                 source="a"/>
                            <input target="b"
                                 dataType="java.lang.Integer"
                                 source="b"/>
                            <output
                                 dataType="java.lang.Integer"
                                 target="javaCodeResult"/>
                            <code>return a + b;</code>

                    </action>
                    <transition to="script"/>
                </scriptTask>
                <scriptTask id="script" g="420,0,100,40">
                    <action type="script" language="qlexpress">
                            <input target="a"
                                 dataType="java.lang.Integer"
                                 source="a"/>
                            <input target="b"
                                 dataType="java.lang.Integer"
                                 source="b"/>
                            <output
                                 dataType="java.lang.Integer"
                                 target="scriptResult"/>
                            <code>a + b</code>

                    </action>
                    <transition to="end"/>
                </scriptTask>
                <end id="end" g="540,0,32,32"/>
            </bpm>
            """);
    private static final ProcessDefinition.Inline DEFAULT_VALUE_TYPES = ProcessDefinition.inline("test.default-value-"
            + "types",
            """
            <bpm code="test.default-value-types"
                 name="Default Value Types">
                <var name="decimal"
                     dataType="java.math.BigDecimal"
                     defaultValue="1.50" inOutType="return"/>
                <var name="integer"
                     dataType="java.math.BigInteger"
                     defaultValue="42" inOutType="return"/>
                <var name="localDate"
                     dataType="java.time.LocalDate"
                     defaultValue="2026-07-14"
                     inOutType="return"/>
                <var name="localTime"
                     dataType="java.time.LocalTime"
                     defaultValue="12:34:56"
                     inOutType="return"/>
                <var name="localDateTime"
                     dataType="java.time.LocalDateTime"
                     defaultValue="2026-07-14T12:34:56.123456789"
                     inOutType="return"/>
                <var name="instant"
                     dataType="java.time.Instant"
                     defaultValue="2026-07-14T12:34:56.123456789Z"
                     inOutType="return"/>
                <var name="sqlDate"
                     dataType="java.sql.Date"
                     defaultValue="2026-07-14"
                     inOutType="return"/>
                <var name="sqlTime"
                     dataType="java.sql.Time"
                     defaultValue="12:34:56"
                     inOutType="return"/>
                <var name="localTimestamp"
                     dataType="java.sql.Timestamp"
                     defaultValue="2026-07-14T12:34:56.123456789"
                     inOutType="return"/>
                <var name="instantTimestamp"
                     dataType="java.sql.Timestamp"
                     defaultValue="2026-07-14T12:34:56.123456789Z"
                     inOutType="return"/>
                <start id="start" g="0,0,32,32">
                    <transition to="end"/>
                </start>
                <end id="end" g="80,0,32,32"/>
            </bpm>
            """);
    private static final ProcessDefinition.Inline UTIL_DATE_DEFAULT = ProcessDefinition.inline("test.util-date-default",
            """
            <bpm code="test.util-date-default"
                 name="Util Date Default">
                <var name="legacyDate"
                     dataType="java.util.Date"
                     defaultValue="2026-07-14T12:34:56.123Z"
                     inOutType="return"/>
                <start id="start" g="0,0,32,32">
                    <transition to="end"/>
                </start>
                <end id="end" g="80,0,32,32"/>
            </bpm>
            """);
    private static final ProcessDefinition.Inline ACTION_PARAMETER_DEFAULTS = ProcessDefinition.inline("test.action-"
            + "parameter-defaults",
            """
            <bpm code="test.action-parameter-defaults"
                 name="Action Parameter Defaults">
                <var name="javaCodeResult"
                     dataType="java.lang.String"
                     inOutType="return"/>
                <var name="scriptResult"
                     dataType="java.lang.String"
                     inOutType="return"/>
                <start id="start" g="0,0,32,32">
                    <transition to="javaCode"/>
                </start>
                <scriptTask id="javaCode" g="60,0,100,40">
                    <action type="script" language="java">
                            <input target="date"
                                 dataType="java.time.LocalDate"
                                 defaultValue="2026-07-14"/>
                            <output
                                 dataType="java.lang.String"
                                 target="javaCodeResult"/>
                            <code>return date.toString();</code>

                    </action>
                    <transition to="script"/>
                </scriptTask>
                <scriptTask id="script" g="300,0,100,40">
                    <action type="script" language="qlexpress">
                            <input target="amount"
                                 dataType="java.math.BigDecimal"
                                 defaultValue="1.50"/>
                            <output
                                 dataType="java.lang.String"
                                 target="scriptResult"/>
                            <code>amount</code>

                    </action>
                    <transition to="end"/>
                </scriptTask>
                <end id="end" g="420,0,32,32"/>
            </bpm>
            """);

    private static String generatedTypeBody(String sourceCode) {
        int typeStart = sourceCode.indexOf("public final class ");
        assertThat(typeStart).isNotNegative();
        return sourceCode.substring(typeStart);
    }

    @Test
    void guardsOnlyParametersWhoseDefaultsMustBePreserved() {
        ProcessEngineConfig config = ProcessEngineTestFactory.tbbpmBuilder().discoverPlugins(false).build();

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            String sourceCode = engine.tooling().generateJavaCode(PARAMETER_DEFAULTS);
            ProcessResult<Map<String, Object>> result = engine.execute(PARAMETER_DEFAULTS, Map.of());

            assertThat(sourceCode)
                .contains("this.optional = DataTypes.transfer(context.get(\"optional\"), String.class);")
                .doesNotContain("context.containsKey(\"optional\")")
                .contains("""
                        this.limit = DataTypes.transfer(context.getOrDefault("limit", this.limit), Integer.class)
                        """
                    .trim())
                .doesNotContain("context.containsKey(");
            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(result.getOutput()).containsEntry("result", "null:7");
        }
    }

    @Test
    void compilesLargeScriptSourcesWithoutOversizedConstants() {
        String script = "/*" + "x".repeat(70_000) + "*/ return 1;";
        ProcessDefinition definition = ProcessDefinition.inline("test.large-script-literal",
                """
                <bpm code="test.large-script-literal" name="Large Script Literal">
                    <var name="result" dataType="java.lang.Integer" inOutType="return"/>
                    <start id="start" g="0,0,32,32"><transition to="script"/></start>
                    <scriptTask id="script" g="60,0,100,40">
                        <action type="script" language="java">
                                <output dataType="java.lang.Integer"
                                     target="result"/>
                                <code><![CDATA[%s]]></code>

                        </action>
                        <transition to="end"/>
                    </scriptTask>
                    <end id="end" g="200,0,32,32"/>
                </bpm>
                """
                    .formatted(script));

        try (ProcessEngine engine = ProcessEngineTestFactory.createTbbpm()) {
            String sourceCode = engine.tooling().generateJavaCode(definition);
            ProcessResult<Map<String, Object>> result = engine.execute(definition, Map.of());

            assertThat(sourceCode).contains("new StringBuilder(").contains(".append(").contains(".toString()");
            assertThat(sourceCode.lines().mapToInt(String::length).max().orElseThrow()).isLessThanOrEqualTo(120);
            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(result.getOutput()).containsEntry("result", 1);
        }
    }

    @Test
    void normalizesPrimitiveVariablesAndExecutesNestedGenericVariables() {
        ProcessEngineConfig config = ProcessEngineTestFactory.tbbpmBuilder().discoverPlugins(false).build();

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            String sourceCode = engine.tooling().generateJavaCode(SOURCE);

            assertThat(sourceCode)
                .contains("import java.util.List;")
                .contains("import java.util.Map;")
                .contains("import javax.annotation.processing.Generated;")
                .doesNotContain("import null.")
                .contains("// Generated CompileFlow process:")
                .contains("// Semantic digest:")
                .contains("@Generated(value = \"compileflow.process-java/v2\"")
                .contains("private Integer count;")
                .contains("private Integer result;")
                .contains("private List<Map<String, Integer>> items;")
                .doesNotContain(" = null;")
                .doesNotContain("allVariables")
                .doesNotContain("private static final int _CF_BREAK")
                .doesNotContain("private static final int _CF_CONTINUE")
                .doesNotContain("private static final int _CF_PAUSED")
                .doesNotContain("Specialized Action shared by semantically identical activities");
            assertThat(sourceCode)
                .contains("private static final ScriptProgramSpec _cf$script")
                .containsOnlyOnce("new ScriptProgramSpec(")
                .contains("ScriptProgramSpec.Input(\"count\", \"java.lang.Integer\")")
                .contains("\"items\",")
                .contains("\"java.util.List<java.util.Map<java.lang.String, java.lang.Integer>>\"");
            assertThat(engine.tooling().generateJavaCode(SOURCE)).isEqualTo(sourceCode);
            int longestLine = sourceCode.lines().mapToInt(String::length).max().orElseThrow();
            assertThat(longestLine).isLessThanOrEqualTo(120);

            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("count", 40);
            variables.put("items", List.of(Map.of("delta", 2)));
            Map<String, Object> originalVariables = new LinkedHashMap<>(variables);

            ProcessResult<Map<String, Object>> result = engine.execute(SOURCE, variables);

            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(result.getOutput()).containsEntry("result", 42);
            assertThat(result.getOutput()).isNotSameAs(variables);
            assertThat(variables).containsExactlyEntriesOf(originalVariables);
        }
    }

    @Test
    void convertsEveryActionOutputAtTheProcessVariableBoundary() {
        ProcessEngineConfig config = ProcessEngineTestFactory.tbbpmBuilder().discoverPlugins(false).build();

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            String sourceCode = engine.tooling().generateJavaCode(ACTION_OUTPUT_CONVERSION);
            ProcessResult<Map<String, Object>> result = engine.execute(ACTION_OUTPUT_CONVERSION, Map.of("a", 3, "b", 4));

            assertThat(sourceCode).contains("DataTypes.transfer(");
            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(result.getOutput())
                .containsEntry("javaResult", 7L)
                .containsEntry("javaCodeResult", 7L)
                .containsEntry("scriptResult", 7L);
        }
    }

    @Test
    void importsAndExecutesCanonicalDefaultValueExpressions() {
        ProcessEngineConfig config = ProcessEngineTestFactory.tbbpmBuilder().discoverPlugins(false).build();

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            String sourceCode = engine.tooling().generateJavaCode(DEFAULT_VALUE_TYPES);
            ProcessResult<Map<String, Object>> result = engine.execute(DEFAULT_VALUE_TYPES, Map.of());

            assertThat(sourceCode)
                .contains("import java.math.BigDecimal;")
                .contains("import java.sql.Timestamp;")
                .contains("import java.time.Instant;")
                .contains("new BigDecimal(\"1.50\")")
                .contains("Timestamp.from(Instant.parse(");
            assertThat(generatedTypeBody(sourceCode))
                .doesNotContain("java.lang.")
                .doesNotContain("java.math.")
                .doesNotContain("java.sql.")
                .doesNotContain("java.time.");
            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(result.getOutput())
                .containsEntry("decimal", new BigDecimal("1.50"))
                .containsEntry("integer", new BigInteger("42"))
                .containsEntry("localDate", LocalDate.parse("2026-07-14"))
                .containsEntry("localTime", LocalTime.parse("12:34:56"))
                .containsEntry("localDateTime", LocalDateTime.parse("2026-07-14T12:34:56.123456789"))
                .containsEntry("instant", Instant.parse("2026-07-14T12:34:56.123456789Z"))
                .containsEntry("localTimestamp", Timestamp.valueOf("2026-07-14 12:34:56.123456789"))
                .containsEntry("instantTimestamp", Timestamp.from(Instant.parse("2026-07-14T12:34:56.123456789Z")));
            assertThat(result.getOutput().get("sqlDate")).hasToString("2026-07-14");
            assertThat(result.getOutput().get("sqlTime")).hasToString("12:34:56");
        }
    }

    @Test
    void importsAndExecutesUtilDateDefaultWithoutHelperTypePaths() {
        ProcessEngineConfig config = ProcessEngineTestFactory.tbbpmBuilder().discoverPlugins(false).build();

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            String sourceCode = engine.tooling().generateJavaCode(UTIL_DATE_DEFAULT);
            ProcessResult<Map<String, Object>> result = engine.execute(UTIL_DATE_DEFAULT, Map.of());

            assertThat(sourceCode).contains("import java.util.Date;").contains("new Date(1784032496123L)");
            assertThat(generatedTypeBody(sourceCode)).doesNotContain("java.util.").doesNotContain("java.time.");
            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(result.getOutput().get("legacyDate")).isInstanceOf(java.util.Date.class);
            assertThat(((java.util.Date) result.getOutput().get("legacyDate")).getTime()).isEqualTo(1784032496123L);
        }
    }

    @Test
    void importsDefaultTypesAcrossEveryActionExecutionPath() {
        ProcessEngineConfig config = ProcessEngineTestFactory.tbbpmBuilder().discoverPlugins(false).build();

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            String sourceCode = engine.tooling().generateJavaCode(ACTION_PARAMETER_DEFAULTS);
            ProcessResult<Map<String, Object>> result = engine.execute(ACTION_PARAMETER_DEFAULTS, Map.of());

            assertThat(sourceCode).contains("import java.math.BigDecimal;").contains("import java.time.LocalDate;");
            assertThat(sourceCode)
                .contains("Collections.singletonMap(\"date\", LocalDate.parse(\"2026-07-14\"))")
                .contains("Collections.singletonMap(\"amount\", new BigDecimal(\"1.50\"))");
            assertThat(result.isSuccess()).as(String.valueOf(result.getError())).isTrue();
            assertThat(result.getOutput()).containsEntry("javaCodeResult", "2026-07-14").containsEntry("scriptResult",
                    "1.50");
        }
    }
}
