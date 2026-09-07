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
package com.alibaba.compileflow.engine.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.bpmn.parser.BpmnXmlParser;
import com.alibaba.compileflow.engine.bpmn.writer.BpmnXmlWriter;
import com.alibaba.compileflow.engine.bpmn.model.BpmnModel;
import com.alibaba.compileflow.engine.bpmn.model.CallActivity;
import com.alibaba.compileflow.engine.bpmn.model.EndEvent;
import com.alibaba.compileflow.engine.bpmn.model.ExclusiveGateway;
import com.alibaba.compileflow.engine.bpmn.model.GatewayDirection;
import com.alibaba.compileflow.engine.bpmn.model.InclusiveGateway;
import com.alibaba.compileflow.engine.bpmn.model.ParallelGateway;
import com.alibaba.compileflow.engine.bpmn.model.ReceiveTask;
import com.alibaba.compileflow.engine.bpmn.model.ScriptTask;
import com.alibaba.compileflow.engine.bpmn.model.ServiceTask;
import com.alibaba.compileflow.engine.bpmn.model.StandardLoopCharacteristics;
import com.alibaba.compileflow.engine.bpmn.model.StartEvent;
import com.alibaba.compileflow.engine.bpmn.model.SubProcess;
import com.alibaba.compileflow.engine.core.model.action.ActionType;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.xml.parser.SchemaValidation;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Round-trip tests for every BPMN 2.0 node type supported by CompileFlow:
 * parse XML → Model → write XML → parse again. Each test verifies the
 * model survives one full cycle without data loss.
 *
 * @author yusu
 */
class BpmnNodeRoundTripTest {
    @Test
    void processIdentityRemainsAuthoritativeAfterModelEdits() {
        BpmnModel model = parse(minimalBpmn());
        model.getProcess().setId("renamed.process");

        assertThat(model.getCode()).isEqualTo("renamed.process");
        assertThat(model.getId()).isEqualTo("renamed.process");
        assertThat(parse(write(model)).getCode()).isEqualTo(model.getCode());
    }

    @Test
    void processNameRemainsAuthoritativeAfterModelEdits() {
        BpmnModel model = parse(minimalBpmn());
        model.getProcess().setName("Renamed process");

        assertThat(model.getName()).isEqualTo("Renamed process");
        assertThat(parse(write(model)).getName()).isEqualTo(model.getName());
    }

    private static BpmnModel parse(String xml) {
        return BpmnXmlParser
            .getInstance()
            .parse(FlowSource.of("test.roundtrip", xml.getBytes(StandardCharsets.UTF_8)), SchemaValidation.DISABLED);
    }

    private static String write(BpmnModel model) {
        ByteArrayOutputStream out = (ByteArrayOutputStream) BpmnXmlWriter.getInstance().write(model);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static BpmnModel roundTrip(String xml) {
        BpmnModel first = parse(xml);
        String written = write(first);
        return parse(written);
    }

    static String minimalBpmn() {
        return String.join("\n", "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
                "             xmlns:cf=\"http://www.compileflow.org\"",
                "             targetNamespace=\"http://www.compileflow.org/bpmn/test\">",
                "  <process id=\"test.roundtrip\" name=\"Round Trip\" isExecutable=\"true\">",
                "    <startEvent id=\"start\" name=\"Start\"/>", "    <endEvent id=\"end\" name=\"End\"/>",
                "    <sequenceFlow id=\"flow1\" sourceRef=\"start\" targetRef=\"end\"/>", "  </process>",
                "</definitions>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankDefaultsSurviveRoundTrip(String value) {
        String xml = serviceTaskFlow()
            .replace("    <startEvent",
                    "<extensionElements><cf:var name=\"value\" dataType=\"java.lang.String\""
                    + " inOutType=\"param\" defaultValue=\"" + value + "\"/></extensionElements>\n    <startEvent")
            .replace("method=\"process\"/>",
                    "method=\"process\"><cf:input target=\"value\"" + " dataType=\"java.lang.String\" defaultValue=\""
                    + value + "\"/></cf:action>");
        BpmnModel parsed = parse(xml);
        assertThat(parsed.getProcess().getVariables().get(0).getDefaultValue()).isEqualTo(value);
        assertThat(parsed
            .getFlowElement("task", ServiceTask.class)
            .getAction()
            .getInputMappings()
            .get(0)
            .getDefaultValue())
            .isEqualTo(value);

        BpmnModel restored = parse(write(parsed));
        assertThat(restored.getProcess().getVariables().get(0).getDefaultValue()).isEqualTo(value);
        assertThat(restored
            .getFlowElement("task", ServiceTask.class)
            .getAction()
            .getInputMappings()
            .get(0)
            .getDefaultValue())
            .isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsBlankEffectRecoveryWithoutSchemaValidation(String recovery) {
        String xml = serviceTaskFlow()
            .replace("method=\"process\"/>",
                    "method=\"process\"><cf:effectPolicy recovery=\"" + recovery + "\"/></cf:action>");

        assertThatThrownBy(() -> parse(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Unsupported Effect recovery");
    }

    @ParameterizedTest
    @ValueSource(strings = {"exclusiveGateway", "inclusiveGateway"})
    void preservesUnresolvedDefaultFlowForValidation(String gatewayType) {
        String xml = exclusiveGatewayFlow()
            .replace("exclusiveGateway", gatewayType)
            .replace("id=\"gw\"", "id=\"gw\" default=\"missing\"");
        BpmnModel restored = roundTrip(xml);

        assertThat(restored
            .getFlowElement("gw", com.alibaba.compileflow.engine.bpmn.model.ConditionalGateway.class)
            .getDefaultFlowId())
            .isEqualTo("missing");
        assertThat(new com.alibaba.compileflow.engine.bpmn.validation.BpmnModelValidator().validate(restored))
            .anyMatch(failure -> failure.message().contains("default must reference exactly one outgoing"));
    }

    static String serviceTaskFlow() {
        return String.join("\n", "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
                "             xmlns:cf=\"http://www.compileflow.org\"",
                "             targetNamespace=\"http://www.compileflow.org/bpmn/test\">",
                "  <process id=\"test.service\" name=\"Service Task\" isExecutable=\"true\">",
                "    <startEvent id=\"start\" name=\"Start\"/>", "    <serviceTask id=\"task\" name=\"Task\">",
                "      <extensionElements>",
                "        <cf:action type=\"spring-bean\" bean=\"myService\" method=\"process\"/>",
                "      </extensionElements>", "    </serviceTask>", "    <endEvent id=\"end\" name=\"End\"/>",
                "    <sequenceFlow id=\"flow1\" sourceRef=\"start\" targetRef=\"task\"/>",
                "    <sequenceFlow id=\"flow2\" sourceRef=\"task\" targetRef=\"end\"/>", "  </process>",
                "</definitions>");
    }

    static String scriptTaskFlow() {
        return String.join("\n", "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
                "             xmlns:cf=\"http://www.compileflow.org\"",
                "             targetNamespace=\"http://www.compileflow.org/bpmn/test\">",
                "  <process id=\"test.script\" name=\"Script Task\" isExecutable=\"true\">",
                "    <startEvent id=\"start\" name=\"Start\"/>",
                "    <scriptTask id=\"script\" name=\"Script\" scriptFormat=\"qlexpress\">",
                "      <script>1 + 1</script>", "    </scriptTask>", "    <endEvent id=\"end\" name=\"End\"/>",
                "    <sequenceFlow id=\"flow1\" sourceRef=\"start\" targetRef=\"script\"/>",
                "    <sequenceFlow id=\"flow2\" sourceRef=\"script\" targetRef=\"end\"/>", "  </process>",
                "</definitions>");
    }

    static String receiveTaskFlow() {
        return """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:cf="http://www.compileflow.org"
                         targetNamespace="http://www.compileflow.org/bpmn/test">
              <process id="test.receive" name="Receive Task" isExecutable="true">
                <startEvent id="start" name="Start"/>
                <receiveTask id="receive" name="Receive"/>
                <endEvent id="end" name="End"/>
                <sequenceFlow id="flow1" sourceRef="start" targetRef="receive"/>
                <sequenceFlow id="flow2" sourceRef="receive" targetRef="end"/>
              </process>
            </definitions>
            """;
    }

    static String exclusiveGatewayFlow() {
        return String.join("\n", "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
                "             xmlns:cf=\"http://www.compileflow.org\"",
                "             targetNamespace=\"http://www.compileflow.org/bpmn/test\">",
                "  <process id=\"test.exclusive\" name=\"Exclusive\" isExecutable=\"true\">",
                "    <startEvent id=\"start\" name=\"Start\"/>",
                "    <exclusiveGateway id=\"gw\" name=\"Decision\" gatewayDirection=\"Diverging\"/>",
                "    <endEvent id=\"end1\" name=\"End1\"/>", "    <endEvent id=\"end2\" name=\"End2\"/>",
                "    <sequenceFlow id=\"flow1\" sourceRef=\"start\" targetRef=\"gw\"/>",
                "    <sequenceFlow id=\"flow2\" sourceRef=\"gw\" targetRef=\"end1\"/>",
                "    <sequenceFlow id=\"flow3\" sourceRef=\"gw\" targetRef=\"end2\"/>", "  </process>", "</definitions>");
    }

    static String parallelGatewayFlow() {
        return String.join("\n", "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
                "             xmlns:cf=\"http://www.compileflow.org\"",
                "             targetNamespace=\"http://www.compileflow.org/bpmn/test\">",
                "  <process id=\"test.parallel\" name=\"Parallel\" isExecutable=\"true\">",
                "    <startEvent id=\"start\" name=\"Start\"/>",
                "    <parallelGateway id=\"fork\" name=\"Fork\" gatewayDirection=\"Diverging\"/>",
                "    <endEvent id=\"end1\" name=\"End1\"/>", "    <endEvent id=\"end2\" name=\"End2\"/>",
                "    <sequenceFlow id=\"flow1\" sourceRef=\"start\" targetRef=\"fork\"/>",
                "    <sequenceFlow id=\"flow2\" sourceRef=\"fork\" targetRef=\"end1\"/>",
                "    <sequenceFlow id=\"flow3\" sourceRef=\"fork\" targetRef=\"end2\"/>", "  </process>",
                "</definitions>");
    }

    static String inclusiveGatewayFlow() {
        return String.join("\n", "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
                "             xmlns:cf=\"http://www.compileflow.org\"",
                "             targetNamespace=\"http://www.compileflow.org/bpmn/test\">",
                "  <process id=\"test.inclusive\" name=\"Inclusive\" isExecutable=\"true\">",
                "    <startEvent id=\"start\" name=\"Start\"/>",
                "    <inclusiveGateway id=\"fork\" name=\"Fork\" gatewayDirection=\"Diverging\"/>",
                "    <endEvent id=\"end1\" name=\"End1\"/>", "    <endEvent id=\"end2\" name=\"End2\"/>",
                "    <sequenceFlow id=\"flow1\" sourceRef=\"start\" targetRef=\"fork\"/>",
                "    <sequenceFlow id=\"flow2\" sourceRef=\"fork\" targetRef=\"end1\"/>",
                "    <sequenceFlow id=\"flow3\" sourceRef=\"fork\" targetRef=\"end2\"/>", "  </process>",
                "</definitions>");
    }

    // ---- BPMN XML templates ----
    static String callActivityFlow() {
        return """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:cf="http://www.compileflow.org"
                         targetNamespace="http://www.compileflow.org/bpmn/test">
              <process id="test.call" name="Call Activity" isExecutable="true">
                <startEvent id="start" name="Start"/>
                <callActivity id="call" name="Call" calledElement="test.subflow"
                              cf:classpath="test/subflow.bpmn"/>
                <endEvent id="end" name="End"/>
                <sequenceFlow id="flow1" sourceRef="start" targetRef="call"/>
                <sequenceFlow id="flow2" sourceRef="call" targetRef="end"/>
              </process>
            </definitions>
            """
            .strip();
    }

    static String subProcessFlow() {
        return String.join("\n", "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
                "             xmlns:cf=\"http://www.compileflow.org\"",
                "             targetNamespace=\"http://www.compileflow.org/bpmn/test\">",
                "  <process id=\"test.subprocess\" name=\"Sub Process\" isExecutable=\"true\">",
                "    <startEvent id=\"start\" name=\"Start\"/>", "    <subProcess id=\"sub\" name=\"Sub\">",
                "      <startEvent id=\"subStart\"/>", "      <endEvent id=\"subEnd\"/>",
                "      <sequenceFlow id=\"subFlow\" sourceRef=\"subStart\" targetRef=\"subEnd\"/>", "    </subProcess>",
                "    <endEvent id=\"end\" name=\"End\"/>",
                "    <sequenceFlow id=\"flow1\" sourceRef=\"start\" targetRef=\"sub\"/>",
                "    <sequenceFlow id=\"flow2\" sourceRef=\"sub\" targetRef=\"end\"/>", "  </process>", "</definitions>");
    }

    static String userTaskFlow() {
        return String.join("\n", "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
                "             targetNamespace=\"http://www.compileflow.org/bpmn/test\">",
                "  <process id=\"test.user\" isExecutable=\"true\">", "    <startEvent id=\"start\"/>",
                "    <userTask id=\"approve\" name=\"Approve\"/>", "    <endEvent id=\"end\"/>",
                "    <sequenceFlow id=\"flow1\" sourceRef=\"start\" targetRef=\"approve\"/>",
                "    <sequenceFlow id=\"flow2\" sourceRef=\"approve\" targetRef=\"end\"/>", "  </process>",
                "</definitions>");
    }

    static String transactionFlow() {
        return String.join("\n", "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
                "             targetNamespace=\"http://www.compileflow.org/bpmn/test\">",
                "  <process id=\"test.tx\" isExecutable=\"true\">", "    <startEvent id=\"start\"/>",
                "    <transaction id=\"tx\" name=\"Transaction\">", "      <startEvent id=\"txStart\"/>",
                "      <endEvent id=\"txEnd\"/>",
                "      <sequenceFlow id=\"txFlow\" sourceRef=\"txStart\" targetRef=\"txEnd\"/>", "    </transaction>",
                "    <endEvent id=\"end\"/>",
                "    <sequenceFlow id=\"flow1\" sourceRef=\"start\" " + "targetRef=\"tx\"/>",
                "    <sequenceFlow id=\"flow2\" sourceRef=\"tx\" " + "targetRef=\"end\"/>", "  </process>",
                "</definitions>");
    }

    static String eventBasedGatewayFlow() {
        return String.join("\n", "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
                "             targetNamespace=\"http://www.compileflow.org/bpmn/test\">",
                "  <process id=\"test.ebg\" isExecutable=\"true\">", "    <startEvent id=\"start\"/>",
                "    <eventBasedGateway id=\"ebg\" name=\"Event GW\"/>", "    <endEvent id=\"end\"/>",
                "    <sequenceFlow id=\"flow1\" sourceRef=\"start\" targetRef=\"ebg\"/>",
                "    <sequenceFlow id=\"flow2\" sourceRef=\"ebg\" targetRef=\"end\"/>", "  </process>", "</definitions>");
    }

    @Nested
    @DisplayName("startEvent / endEvent")
    class StartEndEvents {
        @Test
        void startEventRoundTrip() {
            BpmnModel model = roundTrip(minimalBpmn());
            StartEvent start = model.getFlowElement("start", StartEvent.class);
            assertThat(start).isNotNull();
            assertThat(start.getName()).isEqualTo("Start");
        }

        @Test
        void endEventRoundTrip() {
            BpmnModel model = roundTrip(minimalBpmn());
            EndEvent end = model.getFlowElement("end", EndEvent.class);
            assertThat(end).isNotNull();
            assertThat(end.getName()).isEqualTo("End");
        }

        @Test
        void processVariablesSurviveCanonicalRoundTrip() {
            BpmnModel parsed = parse(minimalBpmn()
                .replace("    <startEvent",
                        """
                <extensionElements>
                  <cf:var name="requestId" dataType="java.lang.String" inOutType="param"/>
                </extensionElements>
                <startEvent"""));
            String written = write(parsed);
            BpmnModel model = parse(written);

            assertThat(written).doesNotContain("contextVarName=");
            assertThat(model.getProcess().getVariables())
                .singleElement()
                .satisfies(variable -> {
                    assertThat(variable.getName()).isEqualTo("requestId");
                    assertThat(variable.getDataType()).isEqualTo("java.lang.String");
                    assertThat(variable.getInOutType()).isEqualTo("param");
                });
        }
    }

    @Nested
    @DisplayName("serviceTask")
    class ServiceTaskNodes {
        @Test
        void serviceTaskWithSpringBeanRoundTrip() {
            BpmnModel model = roundTrip(serviceTaskFlow());
            ServiceTask task = model.getFlowElement("task", ServiceTask.class);
            assertThat(task).isNotNull();
            assertThat(task.getName()).isEqualTo("Task");
            assertThat(task.getAction()).isNotNull();
            assertThat(task.getAction().getType()).isEqualTo(ActionType.SPRING_BEAN);
        }
    }

    @Nested
    @DisplayName("scriptTask")
    class ScriptTaskNodes {
        @Test
        void scriptTaskRoundTrip() {
            BpmnModel model = roundTrip(scriptTaskFlow());
            ScriptTask task = model.getFlowElement("script", ScriptTask.class);
            assertThat(task).isNotNull();
            assertThat(task.getName()).isEqualTo("Script");
        }

        @Test
        void rejectsDuplicateScripts() {
            assertThatThrownBy(() -> parse(scriptTaskFlow()
                .replace("      <script>1 + 1</script>", """
                <script>1 + 1</script>
                <script>2 + 2</script>""")))
                .isInstanceOf(RuntimeException.class)
                .rootCause()
                .hasMessageContaining("at most one script");
        }
    }

    @Nested
    @DisplayName("receiveTask")
    class ReceiveTaskNodes {
        @Test
        void receiveTaskRoundTrip() {
            BpmnModel model = roundTrip(receiveTaskFlow());
            ReceiveTask task = model.getFlowElement("receive", ReceiveTask.class);
            assertThat(task).isNotNull();
            assertThat(task.getName()).isEqualTo("Receive");
        }
    }

    @Nested
    @DisplayName("gateways")
    class GatewayNodes {
        @Test
        void exclusiveGatewayRoundTrip() {
            BpmnModel model = roundTrip(exclusiveGatewayFlow());
            ExclusiveGateway gw = model.getFlowElement("gw", ExclusiveGateway.class);
            assertThat(gw).isNotNull();
            assertThat(gw.getName()).isEqualTo("Decision");
            assertThat(gw.getGatewayDirection()).isEqualTo(GatewayDirection.DIVERGING);
        }

        @Test
        void parallelGatewayRoundTrip() {
            BpmnModel model = roundTrip(parallelGatewayFlow());
            ParallelGateway gw = model.getFlowElement("fork", ParallelGateway.class);
            assertThat(gw).isNotNull();
            assertThat(gw.getName()).isEqualTo("Fork");
            assertThat(gw.getGatewayDirection()).isEqualTo(GatewayDirection.DIVERGING);
        }

        @Test
        void inclusiveGatewayRoundTrip() {
            BpmnModel model = roundTrip(inclusiveGatewayFlow());
            InclusiveGateway gw = model.getFlowElement("fork", InclusiveGateway.class);
            assertThat(gw).isNotNull();
            assertThat(gw.getName()).isEqualTo("Fork");
            assertThat(gw.getGatewayDirection()).isEqualTo(GatewayDirection.DIVERGING);
        }

        @Test
        void rejectsDuplicateConditionExpressions() {
            String xml = exclusiveGatewayFlow()
                .replace("<sequenceFlow id=\"flow2\" sourceRef=\"gw\" targetRef=\"end1\"/>",
                        """
                    <sequenceFlow id="flow2" sourceRef="gw" targetRef="end1">
                      <conditionExpression
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:type="tFormalExpression"
                          language="java">true</conditionExpression>
                      <conditionExpression
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:type="tFormalExpression"
                          language="java">false</conditionExpression>
                    </sequenceFlow>""");

            assertThatThrownBy(() -> parse(xml))
                .isInstanceOf(RuntimeException.class)
                .rootCause()
                .hasMessageContaining("at most one conditionExpression");
        }
    }

    @Nested
    @DisplayName("subProcess / callActivity")
    class SubProcessNodes {
        @Test
        void callActivityRoundTrip() {
            BpmnModel model = roundTrip(callActivityFlow());
            CallActivity call = model.getFlowElement("call", CallActivity.class);
            assertThat(call).isNotNull();
            assertThat(call.getName()).isEqualTo("Call");
        }

        @Test
        void rejectsProcessRefExtensionElement() {
            String xml = callActivityFlow()
                .replace("<callActivity id=\"call\" name=\"Call\" calledElement=\"test.subflow\"\n"
                        + "                  cf:classpath=\"test/subflow.bpmn\"/>",
                        """
                    <callActivity id="call" name="Call" calledElement="test.subflow"
                                  cf:classpath="test/subflow.bpmn">
                      <extensionElements>
                        <cf:processRef version="child-v3"/>
                      </extensionElements>
                    </callActivity>""");

            assertThatThrownBy(() -> parse(xml)).isInstanceOf(RuntimeException.class);
        }

        @Test
        void rejectsCrossNamespaceCall() {
            String xml = callActivityFlow()
                .replace("cf:classpath=\"test/subflow.bpmn\"", "cf:namespace=\"shared\" cf:version=\"child-v3\"");

            assertThatThrownBy(() -> parse(xml))
                .isInstanceOf(CompileFlowException.class)
                .hasMessageContaining(
                        "Unsupported BPMN attribute '{http://www.compileflow.org}namespace' on callActivity");
        }

        @Test
        void callActivityLoopCharacteristicsRoundTrip() {
            BpmnModel model = roundTrip(callActivityFlow()
                .replace("<callActivity id=\"call\" name=\"Call\" calledElement=\"test.subflow\"\n"
                        + "                  cf:classpath=\"test/subflow.bpmn\"/>",
                        """
                    <callActivity id="call" name="Call" calledElement="test.subflow"
                                  cf:classpath="test/subflow.bpmn">
                      <standardLoopCharacteristics testBefore="true" loopMaximum="4">
                        <loopCondition
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:type="tFormalExpression"
                            language="java">counter &lt; 4</loopCondition>
                      </standardLoopCharacteristics>
                    </callActivity>"""));

            CallActivity call = model.getFlowElement("call", CallActivity.class);
            assertThat(call.getLoopCharacteristics()).isInstanceOf(StandardLoopCharacteristics.class);
            StandardLoopCharacteristics loop = (StandardLoopCharacteristics) call.getLoopCharacteristics();
            assertThat(loop.getTestBefore()).isTrue();
            assertThat(loop.getLoopMaximum()).isEqualTo(4L);
            assertThat(loop.getLoopCondition()).isEqualTo("counter < 4");
        }

        @Test
        void subProcessRoundTrip() {
            BpmnModel model = roundTrip(subProcessFlow());
            SubProcess sub = model.getFlowElement("sub", SubProcess.class);
            assertThat(sub).isNotNull();
            assertThat(sub.getName()).isEqualTo("Sub");
        }
    }

    @Nested
    @DisplayName("unsupported node rejection")
    class UnsupportedNodeRejection {
        @Test
        void rejectsUserTask() {
            assertThatThrownBy(() -> parse(userTaskFlow())).isInstanceOf(CompileFlowException.class);
        }

        @Test
        void rejectsTransaction() {
            assertThatThrownBy(() -> parse(transactionFlow())).isInstanceOf(CompileFlowException.class);
        }

        @Test
        void rejectsEventBasedGateway() {
            assertThatThrownBy(() -> parse(eventBasedGatewayFlow())).isInstanceOf(CompileFlowException.class);
        }
    }
}
