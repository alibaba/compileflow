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
package com.alibaba.compileflow.engine.tbbpm.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.xml.parser.SchemaValidation;
import com.alibaba.compileflow.engine.core.model.action.Action;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.model.action.EffectRecovery;
import com.alibaba.compileflow.engine.core.model.action.ActionType;
import com.alibaba.compileflow.engine.tbbpm.writer.TbbpmXmlWriter;
import com.alibaba.compileflow.engine.tbbpm.model.AutoTaskNode;
import com.alibaba.compileflow.engine.tbbpm.model.BreakNode;
import com.alibaba.compileflow.engine.tbbpm.model.ContinueNode;
import com.alibaba.compileflow.engine.tbbpm.model.ExclusiveNode;
import com.alibaba.compileflow.engine.tbbpm.model.EndNode;
import com.alibaba.compileflow.engine.tbbpm.model.FlowNode;
import com.alibaba.compileflow.engine.tbbpm.model.InclusiveNode;
import com.alibaba.compileflow.engine.tbbpm.model.ForEachNode;
import com.alibaba.compileflow.engine.tbbpm.model.WhileNode;
import com.alibaba.compileflow.engine.tbbpm.model.NoteNode;
import com.alibaba.compileflow.engine.tbbpm.model.ParallelNode;
import com.alibaba.compileflow.engine.tbbpm.model.ScriptTaskNode;
import com.alibaba.compileflow.engine.tbbpm.model.StartNode;
import com.alibaba.compileflow.engine.tbbpm.model.SubBpmNode;
import com.alibaba.compileflow.engine.tbbpm.model.BpmCallNode;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import com.alibaba.compileflow.engine.tbbpm.model.WaitEventTaskNode;
import com.alibaba.compileflow.engine.tbbpm.model.WaitTaskNode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Round-trip tests for every TBBPM node type: parse XML → Model → write XML →
 * parse again. Each test verifies that the model survives one full cycle
 * without data loss, proving the parser/writer pair is contractually aligned.
 *
 * @author yusu
 */
class TbbpmNodeRoundTripTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "code=\"\"", "code=\"   \""})
    void rejectsMissingOrBlankProcessCodeAsInvalidInput(String codeAttribute) {
        String xml = "<bpm " + codeAttribute + "><start id=\"start\"><transition to=\"end\"/>"
                + "</start><end id=\"end\"/></bpm>";

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOfSatisfying(CompileFlowException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(com.alibaba.compileflow.engine.ErrorCode.CF_VALIDATION_002))
            .hasMessageContaining("process code must not be blank");
    }

    private static TbbpmModel parse(String xml) {
        return TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("test.roundtrip", xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static TbbpmModel parseWithoutSchema(String xml) {
        return TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("test.roundtrip", xml.getBytes(StandardCharsets.UTF_8)), SchemaValidation.DISABLED);
    }

    private static String write(TbbpmModel model) {
        ByteArrayOutputStream out = (ByteArrayOutputStream) TbbpmXmlWriter.getInstance().write(model);
        return out.toString(StandardCharsets.UTF_8);
    }

    /**
     * Core round-trip: parse → write → parse, return the second model.
     */
    private static TbbpmModel roundTrip(String xml) {
        TbbpmModel first = parse(xml);
        String written = write(first);
        return parse(written);
    }

    static String minimalFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="test.roundtrip" name="Round Trip">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="end"/>
                </start>
                <end id="end" name="End" g="100,0,32,32"/>
            </bpm>
            """;
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankDefaultsSurviveRoundTrip(String value) {
        String xml = autoTaskJavaFlow()
            .replace("<start id=",
                    "<var name=\"value\" dataType=\"java.lang.String\" inOutType=\"param\" defaultValue=\"" + value + "\"/>\n<start id=")
            .replace("method=\"run\"/>",
                    "method=\"run\"><input target=\"value\" dataType=\"java.lang.String\"" + " defaultValue=\"" + value + "\"/></action>");
        TbbpmModel parsed = parse(xml);
        assertThat(parsed.getVariables().get(0).getDefaultValue()).isEqualTo(value);
        assertThat(((AutoTaskNode) parsed.getNode("task")).getAction().getInputMappings().get(0).getDefaultValue())
            .isEqualTo(value);

        TbbpmModel restored = parse(write(parsed));
        assertThat(restored.getVariables().get(0).getDefaultValue()).isEqualTo(value);
        assertThat(((AutoTaskNode) restored.getNode("task"))
            .getAction()
            .getInputMappings()
            .get(0)
            .getDefaultValue())
            .isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsBlankEffectRecoveryWithoutSchemaValidation(String recovery) {
        String xml = autoTaskJavaFlow()
            .replace("method=\"run\"/>", "method=\"run\"><effectPolicy recovery=\"" + recovery + "\"/></action>");

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Unsupported Effect recovery");
    }

    @Test
    void rejectsDataTypeOnForEachOutputWithoutSchemaValidation() {
        String xml = forEachFlow()
            .replace("<transition to=\"end\"/>",
                    "<output source=\"value\" target=\"results\" dataType=\"java.lang.String\"/><transition to=\"end\"/>");

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("foreach output must not declare dataType");
    }

    static String autoTaskJavaFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="test.auto" name="Auto Task">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="task"/>
                </start>
                <autoTask id="task" name="Task" g="80,0,100,40">
                    <action type="java" execution="replayable" class="com.example.MyHandler" method="run"/>
                    <transition to="end"/>
                </autoTask>
                <end id="end" name="End" g="220,0,32,32"/>
            </bpm>
            """;
    }

    static String autoTaskSpringBeanFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="test.spring" name="Spring Task">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="task"/>
                </start>
                <autoTask id="task" name="Task" g="80,0,100,40">
                    <action type="spring-bean" bean="myService" method="process"/>
                    <transition to="end"/>
                </autoTask>
                <end id="end" name="End" g="220,0,32,32"/>
            </bpm>
            """;
    }

    static String scriptActionFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="test.script.action" name="Script Action">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="task"/>
                </start>
                <scriptTask id="task" name="Task" g="80,0,100,40">
                    <action type="script" language="java">
                            <code>return "a]]&gt;b";</code>

                    </action>
                    <transition to="end"/>
                </scriptTask>
                <end id="end" name="End" g="220,0,32,32"/>
            </bpm>
            """;
    }

    static String scriptCodeChildFlow() {
        return """
            <bpm code="test.script.code">
                <scriptTask id="task" g="0,0,100,40">
                    <action type="script" language="qlexpress"><code>1 + 1</code>
                    </action>
                </scriptTask>
            </bpm>
            """;
    }

    static String scriptCodeModeFlow() {
        return """
            <bpm code="test.script.mode">
                <scriptTask id="task" g="0,0,100,40">
                    <action type="script" language="java">
                            <code mode="block">class Task {}</code>

                    </action>
                </scriptTask>
            </bpm>
            """;
    }

    static String scriptTaskFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="test.script" name="Script Task">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="script"/>
                </start>
                <scriptTask id="script" name="Script" g="80,0,100,40">
                    <action type="script" language="qlexpress"><code>'ok'</code>
                    </action>
                    <transition to="end"/>
                </scriptTask>
                <end id="end" name="End" g="220,0,32,32"/>
            </bpm>
            """;
    }

    static String exclusiveFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="test.decision" name="Decision">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="decision"/>
                </start>
                <exclusive id="decision" name="Decision" g="80,0,100,40">
                    <transition to="end1" condition="route == &quot;ready&quot;"/>
                    <transition to="end2"/>
                </exclusive>
                <end id="end1" name="End1" g="220,0,32,32"/>
                <end id="end2" name="End2" g="220,60,32,32"/>
            </bpm>
            """;
    }

    static String parallelFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="test.parallel" name="Parallel">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="fork"/>
                </start>
                <parallel id="fork" name="Fork" g="80,0,100,40">
                    <transition to="end1"/>
                    <transition to="end2"/>
                </parallel>
                <end id="end1" name="End1" g="220,0,32,32"/>
                <end id="end2" name="End2" g="220,60,32,32"/>
            </bpm>
            """;
    }

    // ---- Minimal flow template ----
    static String inclusiveFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="test.inclusive" name="Inclusive">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="fork"/>
                </start>
                <inclusive id="fork" name="Fork" g="80,0,100,40">
                    <transition to="end1"/>
                    <transition to="end2"/>
                </inclusive>
                <end id="end1" name="End1" g="220,0,32,32"/>
                <end id="end2" name="End2" g="220,60,32,32"/>
            </bpm>
            """;
    }

    static String bpmCallFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="test.sub" name="BpmCall">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="sub"/>
                </start>
                <bpmCall id="sub" name="Sub" g="80,0,100,40"
                        code="test.subflow" classpath="test/subflow.bpm">
                    <transition to="end"/>
                </bpmCall>
                <end id="end" name="End" g="220,0,32,32"/>
            </bpm>
            """;
    }

    static String subBpmFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="test.embedded" name="Embedded BPM">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="validation"/>
                </start>
                <subBpm id="validation" name="Validation" g="80,0,240,160">
                    <start id="validationStart" name="Start" g="100,40,32,32">
                        <transition to="validate"/>
                    </start>
                    <scriptTask id="validate" name="Validate" g="160,40,100,40">
                        <action type="script" language="java"><code>result = "valid";</code>
                        </action>
                        <transition to="validationEnd"/>
                    </scriptTask>
                    <end id="validationEnd" name="End" g="280,40,32,32"/>
                    <transition to="end"/>
                </subBpm>
                <end id="end" name="End" g="360,0,32,32"/>
            </bpm>
            """;
    }

    static String waitTaskFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="test.wait" name="Wait Task">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="wait"/>
                </start>
                <waitTask id="wait" name="Wait" timeout="PT24H" g="80,0,100,40">
                    <transition to="end"/>
                </waitTask>
                <end id="end" name="End" g="220,0,32,32"/>
            </bpm>
            """;
    }

    static String waitEventTaskFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="test.waitevent" name="Wait Event Task">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="waitEvent"/>
                </start>
                <waitEventTask id="waitEvent" name="WaitEvent"
                               event="payment.completed" timeout="PT30M" g="80,0,100,40">
                    <transition to="end"/>
                </waitEventTask>
                <end id="end" name="End" g="220,0,32,32"/>
            </bpm>
            """;
    }

    static String forEachFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="test.foreach" name="ForEach">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="loop"/>
                </start>
                <foreach id="loop" name="Loop" g="80,0,100,40"
                             collection="items" item="item" itemType="java.lang.Object">
                    <transition to="end"/>
                    <start id="loopStart"><transition to="body"/></start>
                    <autoTask id="body" name="Body" g="100,20,100,40"><transition to="loopEnd"/></autoTask>
                    <end id="loopEnd"/>
                </foreach>
                <end id="end" name="End" g="220,0,32,32"/>
            </bpm>
            """;
    }

    static String whileFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="test.while" name="While">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="loop"/>
                </start>
                <while id="loop" name="Loop" g="80,0,100,40"
                           condition="true" maxIterations="100">
                    <transition to="end"/>
                    <start id="loopStart"><transition to="body"/></start>
                    <autoTask id="body" name="Body" g="120,0,100,40">
                        <transition to="cont"/>
                    </autoTask>
                    <continue id="cont" name="Continue" g="200,0,32,32"/>
                    <end id="loopEnd"/>
                </while>
                <end id="end" name="End" g="260,0,32,32"/>
            </bpm>
            """;
    }

    static String whileWithBreakFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="test.break" name="WhileBreak">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="loop"/>
                </start>
                <while id="loop" name="Loop" g="80,0,100,40"
                           condition="true" maxIterations="100">
                    <transition to="end"/>
                    <start id="loopStart"><transition to="body"/></start>
                    <autoTask id="body" name="Body" g="120,0,100,40">
                        <transition to="brk"/>
                    </autoTask>
                    <break id="brk" name="Break" g="200,0,32,32"/>
                    <end id="loopEnd"/>
                </while>
                <end id="end" name="End" g="260,0,32,32"/>
            </bpm>
            """;
    }

    static String noteFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="test.note" name="Note">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="end"/>
                </start>
                <end id="end" name="End" g="100,0,32,32"/>
                <note id="note1" name="Important note" g="50,60,100,20"/>
            </bpm>
            """;
    }

    @Test
    void writesUtf8IndependentlyOfTheJvmDefaultCharset() {
        String expectedName = "caf\u00e9";
        TbbpmModel model = parse(minimalFlow().replace("Round Trip", expectedName));

        String written = write(model);

        assertThat(written.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")).isTrue();
        assertThat(parse(written).getName()).isEqualTo(expectedName);
    }

    @Nested
    @DisplayName("start / end")
    class StartEndNodes {
        @Test
        void startNodeRoundTrip() {
            TbbpmModel model = roundTrip(minimalFlow());
            StartNode start = (StartNode) model.getNode("start");
            assertThat(start).isNotNull();
            assertThat(start.getName()).isEqualTo("Start");
            assertThat(start.getOutgoingTransitions().get(0).getSource()).isEqualTo("start");
        }

        @Test
        void endNodeRoundTrip() {
            TbbpmModel model = roundTrip(minimalFlow());
            EndNode end = (EndNode) model.getNode("end");
            assertThat(end).isNotNull();
            assertThat(end.getName()).isEqualTo("End");
        }

        @Test
        void rejectsUnknownTransitionTargetsWithAControlledResourceFailure() {
            String xml = minimalFlow().replace("<transition to=\"end\"/>", "<transition to=\"missing\"/>");

            assertThatThrownBy(() -> parse(xml))
                .isInstanceOf(CompileFlowException.ResourceException.class)
                .hasMessageContaining("references unknown target node: missing");
        }

        @Test
        void rejectsDuplicateNodeIdsBeforeConnectingTransitions() {
            String xml = minimalFlow().replace("<end id=\"end\"", "<end id=\"start\"");
            FlowSource source = FlowSource.of("test.roundtrip", xml.getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> TbbpmXmlParser.getInstance().parse(source, SchemaValidation.DISABLED))
                .isInstanceOf(CompileFlowException.class)
                .hasMessageContaining("Duplicate TBBPM node id")
                .hasMessageContaining("start");
        }

        @Test
        void rejectsUnknownAttributesWhenSchemaValidationIsDisabled() {
            String xml = minimalFlow().replace("<start id=\"start\"", "<start id=\"start\" priorty=\"10\"");
            FlowSource source = FlowSource.of("test.roundtrip", xml.getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> TbbpmXmlParser.getInstance().parse(source, SchemaValidation.DISABLED))
                .isInstanceOf(CompileFlowException.class)
                .hasMessageContaining("Unsupported TBBPM attribute 'priorty' on start");
        }

        @Test
        void rejectsNamespacedAttributesWhenSchemaValidationIsDisabled() {
            String xml = minimalFlow().replace("<bpm ", "<bpm xmlns:vendor=\"urn:vendor\" vendor:mode=\"fast\" ");
            FlowSource source = FlowSource.of("test.roundtrip", xml.getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> TbbpmXmlParser.getInstance().parse(source, SchemaValidation.DISABLED))
                .isInstanceOf(CompileFlowException.class)
                .hasMessageContaining("Unsupported TBBPM attribute '{urn:vendor}mode' on bpm");
        }

        @Test
        void rejectsRemovedRootVariableMappingsWithoutSchemaValidation() {
            String xml = minimalFlow()
                .replace("<start id=\"start\"",
                        "<output source=\"input\" target=\"request\"/>\n" + "<start id=\"start\"");

            assertThatThrownBy(() -> parseWithoutSchema(xml))
                .isInstanceOf(CompileFlowException.class)
                .hasMessageContaining("OutputMapping");
        }
    }

    @Nested
    @DisplayName("autoTask")
    class AutoTaskNodes {
        @Test
        void autoTaskWithJavaActionRoundTrip() {
            TbbpmModel model = roundTrip(autoTaskJavaFlow());
            AutoTaskNode task = (AutoTaskNode) model.getNode("task");
            assertThat(task).isNotNull();
            assertThat(task.getName()).isEqualTo("Task");
            assertThat(task.getAction()).isNotNull();
            assertThat(task.getAction().getType()).isEqualTo(ActionType.JAVA);
            assertThat(task.getAction().getExecution()).isEqualTo(ActionExecution.REPLAYABLE);
        }

        @Test
        void autoTaskWithSpringBeanActionRoundTrip() {
            TbbpmModel model = roundTrip(autoTaskSpringBeanFlow());
            AutoTaskNode task = (AutoTaskNode) model.getNode("task");
            assertThat(task).isNotNull();
            assertThat(task.getAction().getType()).isEqualTo(ActionType.SPRING_BEAN);
        }

        @Test
        void effectExecutionAndRecoveryPolicyRoundTripWithoutSecondTargetNamespace() {
            TbbpmModel model = roundTrip(
                    """
                <bpm code="test.effect.action">
                    <start id="start" g="0,0,32,32"><transition to="task"/></start>
                    <autoTask id="task" g="80,0,100,40">
                        <action type="spring-bean" execution="effect" bean="inventory" method="reserve">
                                <input target="order" dataType="java.lang.String"
                                     source="order"/>

                            <effectPolicy recovery="reconcile"
                                          maxAttempts="3"
                                          maxReconcileAttempts="10"
                                          recoveryDelay="PT1S"
                                          maxRecoveryDuration="PT1H">
                                <reconcileAction type="spring-bean" bean="inventory" method="query"/>
                            </effectPolicy>
                        </action>
                        <transition to="end"/>
                    </autoTask>
                    <end id="end" g="220,0,32,32"/>
                </bpm>
                """);

            Action action = ((AutoTaskNode) model.getNode("task")).getAction();
            assertThat(action.getExecution()).isEqualTo(ActionExecution.EFFECT);
            assertThat(action.getEffectPolicy().getRecovery()).isEqualTo(EffectRecovery.RECONCILE);
            assertThat(action.getEffectPolicy().getMaxAttempts()).isEqualTo(3);
            assertThat(action.getEffectPolicy().getReconcileAction().getBean()).isEqualTo("inventory");
        }

        @Test
        void omittedMethodsRemainOmittedInTheSourceModel() {
            TbbpmModel javaModel = roundTrip(autoTaskJavaFlow().replace(" method=\"run\"", ""));
            assertThat(((AutoTaskNode) javaModel.getNode("task")).getAction().getMethod()).isNull();

            TbbpmModel springModel = roundTrip(autoTaskSpringBeanFlow().replace(" method=\"process\"", ""));
            assertThat(((AutoTaskNode) springModel.getNode("task")).getAction().getMethod()).isNull();
        }

        @Test
        void rejectsRemovedActionHandleWithoutSchemaValidation() {
            String xml = autoTaskJavaFlow().replace("method=\"run\"/>", "method=\"run\"><actionHandle/></action>");

            assertThatThrownBy(() -> parseWithoutSchema(xml)).isInstanceOf(RuntimeException.class);
        }

        @Test
        void rejectsOutputOnReconcileActionWithoutSchemaValidation() {
            String xml = autoTaskJavaFlow()
                .replace("method=\"run\"/>",
                        "method=\"run\"><effectPolicy recovery=\"reconcile\" maxAttempts=\"1\" "
                        + "maxReconcileAttempts=\"1\" recoveryDelay=\"PT1S\"><reconcileAction type=\"java\" "
                        + "class=\"java.lang.System\" method=\"currentTimeMillis\"><output target=\"result\" "
                        + "dataType=\"java.lang.Long\"/></reconcileAction></effectPolicy></action>");

            assertThatThrownBy(() -> parseWithoutSchema(xml))
                .hasMessageContaining("Unsupported TBBPM child element OutputMapping under ReconcileAction");
        }

        @Test
        void rejectsDefaultValueOnReconcileInputWithoutSchemaValidation() {
            String xml = autoTaskJavaFlow()
                .replace("method=\"run\"/>",
                        "method=\"run\"><effectPolicy recovery=\"reconcile\" maxAttempts=\"1\" "
                        + "maxReconcileAttempts=\"1\" recoveryDelay=\"PT1S\"><reconcileAction type=\"java\" "
                        + "class=\"java.lang.System\" method=\"currentTimeMillis\"><input source=\"request\" "
                        + "target=\"request\" dataType=\"java.lang.String\" defaultValue=\"fallback\"/>"
                        + "</reconcileAction></effectPolicy></action>");

            assertThatThrownBy(() -> parseWithoutSchema(xml)).hasMessageContaining(
                    "reconcile input must not declare defaultValue");
        }
    }

    @Nested
    @DisplayName("scriptTask")
    class ScriptTaskNodes {
        @Test
        void scriptTaskRoundTrip() {
            TbbpmModel model = roundTrip(scriptTaskFlow());
            ScriptTaskNode task = (ScriptTaskNode) model.getNode("script");
            assertThat(task).isNotNull();
            assertThat(task.getName()).isEqualTo("Script");
        }

        @Test
        void scriptActionRoundTrip() {
            TbbpmModel model = roundTrip(scriptActionFlow());
            Action action = ((ScriptTaskNode) model.getNode("task")).getAction();

            assertThat(action.getLanguage()).isEqualTo("java");
            assertThat(action.getSource()).isEqualTo("return \"a]]>b\";");
        }

        @Test
        void acceptsCodeChildForScriptAction() {
            TbbpmModel model = roundTrip(scriptCodeChildFlow());
            Action action = ((ScriptTaskNode) model.getNode("task")).getAction();
            assertThat(action.getLanguage()).isEqualTo("qlexpress");
            assertThat(action.getSource()).isEqualTo("1 + 1");
        }

        @Test
        void rejectsModeForScriptSource() {
            assertThatThrownBy(() -> parse(scriptCodeModeFlow())).isInstanceOf(RuntimeException.class);
        }

        @Test
        void rejectsDuplicateScriptSourceChildrenWithoutSchemaValidation() {
            String xml = scriptActionFlow()
                .replace("<code>return \"a]]&gt;b\";</code>", "<code>return 1;</code><code>return 2;</code>");

            assertThatThrownBy(() -> parseWithoutSchema(xml)).isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("exclusive gateway")
    class ExclusiveNodes {
        @Test
        void exclusiveNodeRoundTrip() {
            TbbpmModel model = roundTrip(exclusiveFlow());
            ExclusiveNode decision = (ExclusiveNode) model.getNode("decision");
            assertThat(decision).isNotNull();
            assertThat(decision.getName()).isEqualTo("Decision");
            assertThat(decision.getOutgoingTransitions().size()).isEqualTo(2);
            assertThat(decision.getOutgoingTransitions().get(0).getCondition()).isEqualTo("route == \"ready\"");
        }
    }

    @Nested
    @DisplayName("parallel / inclusive gateways")
    class GatewayNodes {
        @Test
        void parallelNodeRoundTrip() {
            TbbpmModel model = roundTrip(parallelFlow());
            ParallelNode parallel = (ParallelNode) model.getNode("fork");
            assertThat(parallel).isNotNull();
            assertThat(parallel.getName()).isEqualTo("Fork");
        }

        @Test
        void inclusiveNodeRoundTrip() {
            TbbpmModel model = roundTrip(inclusiveFlow());
            InclusiveNode inclusive = (InclusiveNode) model.getNode("fork");
            assertThat(inclusive).isNotNull();
            assertThat(inclusive.getName()).isEqualTo("Fork");
        }
    }

    @Nested
    @DisplayName("bpmCall")
    class BpmCallNodes {
        @Test
        void bpmCallNodeRoundTrip() {
            TbbpmModel model = roundTrip(bpmCallFlow());
            BpmCallNode call = (BpmCallNode) model.getNode("sub");
            assertThat(call).isNotNull();
            assertThat(call.getName()).isEqualTo("Sub");
            assertThat(call.getCode()).isEqualTo("test.subflow");
            assertThat(call.getClasspath()).isEqualTo("test/subflow.bpm");
        }

        @Test
        void rejectsDataTypeOnCalledProcessInput() {
            String xml = bpmCallFlow()
                .replace("<transition to=\"end\"/>",
                        "<input target=\"value\" source=\"value\" dataType=\"java.lang.String\"/>\n"
                        + "                    <transition to=\"end\"/>");

            assertThatThrownBy(() -> parse(xml)).isInstanceOf(RuntimeException.class);
        }

        @Test
        void rejectsCrossNamespaceCall() {
            String crossNamespaceCall = """
                code="test.subflow" bpmCallNamespace="shared"
                """.strip();
            String xml = bpmCallFlow().replace("code=\"test.subflow\"", crossNamespaceCall);

            assertThatThrownBy(() -> parseWithoutSchema(xml)).isInstanceOf(RuntimeException.class);
        }

        @Test
        void rejectsRemovedImplicitWaitAttribute() {
            String callWithImplicitWait = """
                code="test.subflow" waitForTrigger="true"
                """.strip();
            String xml = bpmCallFlow().replace("code=\"test.subflow\"", callWithImplicitWait);

            assertThatThrownBy(() -> parse(xml)).isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("subBpm")
    class SubBpmNodes {
        @Test
        void embeddedSubBpmRoundTrip() {
            TbbpmModel model = roundTrip(subBpmFlow());
            SubBpmNode subBpm = (SubBpmNode) model.getNode("validation");

            assertThat(subBpm.getStartNode().getId()).isEqualTo("validationStart");
            assertThat(subBpm.getEndNode().getId()).isEqualTo("validationEnd");
            assertThat(subBpm.getAllNodes())
                .extracting(FlowNode::getId)
                .containsExactly("validationStart", "validate", "validationEnd");
        }

        @Test
        void rejectsLegacyCallAttributes() {
            String xml = subBpmFlow().replace("id=\"validation\"", "id=\"validation\" code=\"child\"");

            assertThatThrownBy(() -> parseWithoutSchema(xml)).isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("waitTask / waitEventTask")
    class WaitNodes {
        @Test
        void waitTaskRoundTrip() {
            TbbpmModel model = roundTrip(waitTaskFlow());
            WaitTaskNode wait = (WaitTaskNode) model.getNode("wait");
            assertThat(wait).isNotNull();
            assertThat(wait.getName()).isEqualTo("Wait");
            assertThat(wait.getTimeout()).isEqualTo("PT24H");
        }

        @Test
        void waitEventTaskRoundTrip() {
            TbbpmModel model = roundTrip(waitEventTaskFlow());
            WaitEventTaskNode waitEvent = (WaitEventTaskNode) model.getNode("waitEvent");
            assertThat(waitEvent).isNotNull();
            assertThat(waitEvent.getName()).isEqualTo("WaitEvent");
            assertThat(waitEvent.getEvent()).isEqualTo("payment.completed");
            assertThat(waitEvent.getTimeout()).isEqualTo("PT30M");
        }

        @Test
        void rejectsUnexpectedWaitChildrenWithoutSchemaValidation() {
            String xml = waitTaskFlow()
                .replace("<transition to=\"end\"/>",
                        """
                <action type="java" class="com.example.Handler"/>
                <transition to="end"/>""");

            assertThatThrownBy(() -> parseWithoutSchema(xml)).isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("while / foreach / continue / break")
    class LoopNodes {
        @Test
        void forEachRoundTrip() {
            TbbpmModel first = parse(forEachFlow());
            String written = write(first);
            assertThat(written).doesNotContain("execution=\"sequential\"");

            TbbpmModel model = parse(written);
            ForEachNode loop = (ForEachNode) model.getNode("loop");
            assertThat(loop).isNotNull();
            assertThat(loop.getCollection()).isEqualTo("items");
        }

        @Test
        void parallelForEachContractRoundTrip() {
            String xml = forEachFlow()
                .replace("collection=\"items\"", "execution=\"parallel\" collection=\"items\"")
                .replace("<transition to=\"end\"/>",
                        "<output target=\"results\" source=\"slot\"/><transition to=\"end\"/>");

            TbbpmModel model = roundTrip(xml);
            ForEachNode loop = (ForEachNode) model.getNode("loop");

            assertThat(loop.getExecution().name()).isEqualTo("PARALLEL");
            assertThat(loop.getOutput().getTarget()).isEqualTo("results");
            assertThat(loop.getOutput().getSource()).isEqualTo("slot");
        }

        @Test
        void rejectsNonCanonicalExecutionValueWithoutSchemaValidation() {
            String nonCanonicalAttributes = """
                execution="PARALLEL" collection="items"
                """.strip();
            String xml = forEachFlow().replace("collection=\"items\"", nonCanonicalAttributes);

            assertThatThrownBy(() -> parseWithoutSchema(xml)).isInstanceOf(CompileFlowException.class);
        }

        @Test
        void rejectsOutputAfterExecutableChildrenWithoutSchemaValidation() {
            String xml = forEachFlow()
                .replace("<transition to=\"end\"/>",
                        "<transition to=\"end\"/><output target=\"results\" source=\"slot\"/>");

            assertThatThrownBy(() -> parseWithoutSchema(xml))
                .isInstanceOf(CompileFlowException.class)
                .hasMessageContaining("output must precede transitions and body nodes");
        }

        @Test
        void whileRoundTrip() {
            TbbpmModel model = roundTrip(whileFlow());
            WhileNode loop = (WhileNode) model.getNode("loop");
            assertThat(loop).isNotNull();
            assertThat(loop.getCondition()).isEqualTo("true");
        }

        @Test
        void rejectsMaxIterationsOutsideTheRuntimeIntegerRange() {
            String xml = whileFlow().replace("maxIterations=\"100\"", "maxIterations=\"2147483648\"");

            assertThatThrownBy(() -> parse(xml)).isInstanceOf(CompileFlowException.class);
        }

        @Test
        void continueNodeRoundTrip() {
            TbbpmModel model = roundTrip(whileFlow());
            WhileNode loop = (WhileNode) model.getNode("loop");
            ContinueNode cont = (ContinueNode) loop.getNode("cont");
            assertThat(cont).isNotNull();
        }

        @Test
        void breakNodeRoundTrip() {
            TbbpmModel model = roundTrip(whileWithBreakFlow());
            WhileNode loop = (WhileNode) model.getNode("loop");
            BreakNode brk = (BreakNode) loop.getNode("brk");
            assertThat(brk).isNotNull();
        }
    }

    @Nested
    @DisplayName("note (annotation)")
    class NoteNodes {
        @Test
        void noteNodeRoundTrip() {
            TbbpmModel model = roundTrip(noteFlow());
            NoteNode note = (NoteNode) model.getNode("note1");
            assertThat(note).isNotNull();
            assertThat(note.getName()).isEqualTo("Important note");
        }
    }
}
