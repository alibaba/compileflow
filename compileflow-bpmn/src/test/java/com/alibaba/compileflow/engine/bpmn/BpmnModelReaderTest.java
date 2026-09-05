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
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.bpmn.parser.BpmnXmlParser;
import com.alibaba.compileflow.engine.bpmn.writer.BpmnXmlWriter;
import com.alibaba.compileflow.engine.bpmn.model.BpmnModel;
import com.alibaba.compileflow.engine.bpmn.model.Message;
import com.alibaba.compileflow.engine.bpmn.model.ServiceTask;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.xml.parser.SchemaValidation;
import com.alibaba.compileflow.engine.core.model.action.Action;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.model.action.InvocationPolicy;
import com.alibaba.compileflow.engine.core.model.action.EffectRecovery;
import com.alibaba.compileflow.engine.core.model.action.RetryJitter;
import com.alibaba.compileflow.engine.core.model.action.ActionType;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BpmnModelReaderTest {
    private static final String BPMN_WITH_COMPILEFLOW_EXTENSIONS =
            """
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:cf="http://www.compileflow.org"
                     targetNamespace="http://www.compileflow.org/bpmn/test">
          <process id="order" name="Order" isExecutable="true">
            <startEvent id="start" name="Start">
              <outgoing>flow_start_task</outgoing>
            </startEvent>
            <serviceTask id="task" name="Task">
              <incoming>flow_start_task</incoming>
              <extensionElements>
                <cf:action type="spring-bean" bean="orderService" class="java.lang.Runnable" method="run">
                  <cf:input target="request" dataType="String" source="request"/>
                  <cf:invocationPolicy timeout="PT20S" attemptTimeout="PT5S"
                                       maxAttempts="2" initialBackoff="PT1S"
                                       jitter="none" retryOn="transient" onFailure="propagate"/>
                </cf:action>
              </extensionElements>
              <outgoing>flow_task_end</outgoing>
            </serviceTask>
            <endEvent id="end" name="End">
              <incoming>flow_task_end</incoming>
            </endEvent>
            <sequenceFlow id="flow_start_task" sourceRef="start" targetRef="task"/>
            <sequenceFlow id="flow_task_end" sourceRef="task" targetRef="end"/>
          </process>
        </definitions>
        """;
    private static final String BPMN_WITH_DANGLING_SEQUENCE_FLOW = String.join("\n",
            "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
            "             xmlns:cf=\"http://www.compileflow.org\"",
            "             targetNamespace=\"http://www.compileflow.org/bpmn/test\">",
            "  <process id=\"broken\" isExecutable=\"true\">", "    <startEvent id=\"start\"/>",
            "    <sequenceFlow id=\"flow_start_missing\" sourceRef=\"start\" targetRef=\"missing\"/>", "  </process>",
            "</definitions>");
    private static final String BPMN_SCHEMA_VALID_WITH_CF_EXTENSION =
            """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:cf="http://www.compileflow.org"
                     targetNamespace="http://www.compileflow.org/bpmn/test">
          <process id="order" name="Order" isExecutable="true">
            <startEvent id="start" name="Start"/>
            <serviceTask id="task" name="Task">
              <extensionElements>
                <cf:action type="spring-bean" bean="orderService" class="java.lang.Runnable" method="run">
                  <cf:input target="request" dataType="String" source="request"/>
                  <cf:invocationPolicy timeout="PT20S" attemptTimeout="PT5S"
                                       maxAttempts="2" initialBackoff="PT1S"
                                       jitter="none" retryOn="transient" onFailure="propagate"/>
                </cf:action>
              </extensionElements>
            </serviceTask>
            <endEvent id="end" name="End"/>
            <sequenceFlow id="flow_start_task" sourceRef="start" targetRef="task"/>
            <sequenceFlow id="flow_task_end" sourceRef="task" targetRef="end"/>
          </process>
        </definitions>
        """;
    private static final String BPMN_WITH_EXTERNAL_ENTITY = String.join("\n", "<!DOCTYPE definitions [",
            "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\">", "]>",
            "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
            "             targetNamespace=\"http://www.compileflow.org/bpmn/test\">",
            "  <process id=\"xxe\" isExecutable=\"true\">", "    <startEvent id=\"start\" name=\"&xxe;\"/>",
            "  </process>", "</definitions>");

    private static BpmnModel parse(String xml) {
        return BpmnXmlParser.getInstance().parse(source("test.bpmn", xml), SchemaValidation.DISABLED);
    }

    private static FlowSource source(String code, String xml) {
        return FlowSource.of(code, xml.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertCompileFlowExtensions(ServiceTask task) {
        InvocationPolicy invocationPolicy = task.getAction().getInvocationPolicy();
        assertThat(invocationPolicy).isNotNull();
        assertThat(invocationPolicy.getTimeout()).isEqualTo("PT20S");
        assertThat(invocationPolicy.getAttemptTimeout()).isEqualTo("PT5S");
        assertThat(invocationPolicy.getMaxAttempts()).isEqualTo(2);
        assertThat(invocationPolicy.getInitialBackoff()).isEqualTo("PT1S");
        assertThat(invocationPolicy.getJitter()).isEqualTo(RetryJitter.NONE);
        assertThat(invocationPolicy.getRetryOn()).isEqualTo("transient");
        assertThat(invocationPolicy.getOnFailure()).isEqualTo("propagate");

        Action action = task.getAction();
        assertThat(action).isNotNull();
        assertThat(action.getType()).isEqualTo(ActionType.SPRING_BEAN);
        assertThat(action.getBean()).isEqualTo("orderService");
        assertThat(action.getClassName()).isEqualTo("java.lang.Runnable");
        assertThat(action.getMethod()).isEqualTo("run");
        assertThat(action.getInputMappings()).hasSize(1);
    }

    @Test
    void shouldRoundTripCompileFlowExtensionsAfterIncomingAndOutgoingElements() {
        BpmnModel model = parse(BPMN_WITH_COMPILEFLOW_EXTENSIONS);

        ServiceTask task = model.getFlowElement("task", ServiceTask.class);
        assertCompileFlowExtensions(task);

        ByteArrayOutputStream out = (ByteArrayOutputStream) BpmnXmlWriter.getInstance().write(model);
        String xml = new String(out.toByteArray(), StandardCharsets.UTF_8);

        assertThat(xml)
            .contains("xmlns:cf=\"http://www.compileflow.org\"")
            .contains("timeout=\"PT20S\"")
            .contains("attemptTimeout=\"PT5S\"")
            .contains("<incoming>flow_start_task</incoming>")
            .contains("<outgoing>flow_task_end</outgoing>");

        BpmnModel reparsed = parse(xml);
        assertCompileFlowExtensions(reparsed.getFlowElement("task", ServiceTask.class));

        assertThatThrownBy(() -> reparsed.getFlowElement("task", Message.class))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("expected")
            .hasMessageContaining(Message.class.getName());

        assertThatThrownBy(() -> reparsed.getProcess().getNode("flow_start_task"))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Undefined node")
            .hasMessageContaining("flow_start_task");
    }

    @Test
    void shouldRejectDanglingSequenceFlowEvenWhenSchemaValidationIsDisabled() {
        assertThatThrownBy(() -> parse(BPMN_WITH_DANGLING_SEQUENCE_FLOW))
            .isInstanceOf(CompileFlowException.ResourceException.class)
            .hasMessageContaining("unknown target node")
            .hasMessageContaining("flow_start_missing");
    }

    @Test
    void shouldRejectDuplicateNodeIdsBeforeConnectingSequenceFlows() {
        String duplicate =
                BPMN_WITH_COMPILEFLOW_EXTENSIONS.replace("<serviceTask id=\"task\"", "<serviceTask id=\"start\"");

        assertThatThrownBy(() -> parse(duplicate))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Duplicate BPMN flow node id")
            .hasMessageContaining("start");
    }

    @Test
    void shouldRejectDoctypeAndExternalEntities() {
        assertThatThrownBy(() -> parse(BPMN_WITH_EXTERNAL_ENTITY))
            .isInstanceOfSatisfying(CompileFlowException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(ErrorCode.CF_VALIDATION_002))
            .hasMessageContaining("Flow XML is malformed")
            .hasMessageNotContaining("root:");
    }

    @Test
    void shouldRejectDuplicateJobPolicies() {
        String duplicate = BPMN_WITH_COMPILEFLOW_EXTENSIONS.replace("<cf:invocationPolicy timeout=",
                "<cf:invocationPolicy maxAttempts=\"1\"/>\n          <cf:invocationPolicy timeout=");

        assertThatThrownBy(() -> parse(duplicate))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("at most one cf:invocationPolicy");
    }

    @Test
    void shouldRejectMalformedInvocationPolicyNumbers() {
        String malformed = BPMN_WITH_COMPILEFLOW_EXTENSIONS.replace("maxAttempts=\"2\"", "maxAttempts=\"two\"");

        assertThatThrownBy(() -> parse(malformed))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("maxAttempts' must be an integer");
    }

    @Test
    void shouldRoundTripCompleteEffectRecoveryPolicy() {
        String xml = BPMN_WITH_COMPILEFLOW_EXTENSIONS
            .replace("<cf:action type=\"spring-bean\" bean=",
                    "<cf:action type=\"spring-bean\" execution=\"effect\" bean=")
            .replace("        </cf:action>",
                    "          <cf:effectPolicy recovery=\"reconcile\" maxAttempts=\"3\"\n"
                    + "                           maxReconcileAttempts=\"10\" recoveryDelay=\"PT1S\"\n"
                    + "                           maxRecoveryDuration=\"PT30M\">\n"
                    + "            <cf:reconcileAction type=\"spring-bean\" bean=\"orderReconciler\"\n"
                    + "                                class=\"java.lang.Runnable\" method=\"run\"/>\n"
                    + "          </cf:effectPolicy>\n" + "        </cf:action>");

        Action action = parse(xml).getFlowElement("task", ServiceTask.class).getAction();
        assertThat(action.getExecution()).isEqualTo(ActionExecution.EFFECT);
        assertThat(action.getEffectPolicy().getRecovery()).isEqualTo(EffectRecovery.RECONCILE);
        assertThat(action.getEffectPolicy().getMaxAttempts()).isEqualTo(3);
        assertThat(action.getEffectPolicy().getMaxReconcileAttempts()).isEqualTo(10);

        ByteArrayOutputStream out = (ByteArrayOutputStream) BpmnXmlWriter.getInstance().write(parse(xml));
        Action reparsed =
                parse(out.toString(StandardCharsets.UTF_8)).getFlowElement("task", ServiceTask.class).getAction();
        assertThat(reparsed.getEffectPolicy().getRecovery()).isEqualTo(EffectRecovery.RECONCILE);
        assertThat(reparsed.getEffectPolicy().getReconcileAction().getType()).isEqualTo(ActionType.SPRING_BEAN);
    }

    @Test
    void shouldRoundTripDynamicEffectRecoverySource() {
        String xml = BPMN_WITH_COMPILEFLOW_EXTENSIONS
            .replace("<cf:action type=\"spring-bean\" bean=",
                    "<cf:action type=\"spring-bean\" execution=\"effect\" bean=")
            .replace("        </cf:action>",
                    "          <cf:effectPolicy recoveryPlanVariable=\"recoveryPlan\"/>\n" + "        </cf:action>");

        Action action = parse(xml).getFlowElement("task", ServiceTask.class).getAction();
        assertThat(action.getEffectPolicy().getRecoveryPlanVariable()).isEqualTo("recoveryPlan");
        assertThat(action.getEffectPolicy().getRecovery()).isNull();

        ByteArrayOutputStream out = (ByteArrayOutputStream) BpmnXmlWriter.getInstance().write(parse(xml));
        Action reparsed =
                parse(out.toString(StandardCharsets.UTF_8)).getFlowElement("task", ServiceTask.class).getAction();
        assertThat(reparsed.getEffectPolicy().getRecoveryPlanVariable()).isEqualTo("recoveryPlan");
    }

    @Test
    void shouldRejectInvocationPolicyDurationsBelowMillisecondPrecision() {
        String imprecise =
                BPMN_WITH_COMPILEFLOW_EXTENSIONS.replace("attemptTimeout=\"PT5S\"", "attemptTimeout=\"PT0.0001S\"");

        assertThatThrownBy(() -> parse(imprecise))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("whole-millisecond precision");
    }

    @Test
    void shouldRejectAttemptTimeoutLongerThanTheInvocationTimeout() {
        String invalid = BPMN_WITH_COMPILEFLOW_EXTENSIONS.replace("timeout=\"PT20S\" attemptTimeout=\"PT5S\"",
                "timeout=\"PT5S\" attemptTimeout=\"PT20S\"");

        assertThatThrownBy(() -> parse(invalid))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("attemptTimeoutMs must be less than or equal to timeoutMs");
    }

    @Test
    void shouldRejectUnknownRetryJitter() {
        String invalid = BPMN_WITH_COMPILEFLOW_EXTENSIONS.replace("jitter=\"none\"", "jitter=\"decorrelated\"");

        assertThatThrownBy(() -> parse(invalid))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("jitter must be one of");
    }

    @Test
    void strictValidationResolvesBundledSchemaImportsWithoutTheContextClassLoader() {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        thread.setContextClassLoader(new ClassLoader(null) {
        });
        try {
            BpmnModel model = BpmnXmlParser
                .getInstance()
                .parse(source("strict.bpmn", BPMN_SCHEMA_VALID_WITH_CF_EXTENSION), SchemaValidation.STRICT);

            assertThat(model.getCode()).isEqualTo("order");
            assertCompileFlowExtensions(model.getFlowElement("task", ServiceTask.class));
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    @Test
    void strictValidationReportsAMissingRootSchemaBeforeCreatingTheValidator() {
        assertThatThrownBy(() -> new MissingSchemaParser()
            .parse(source("missing-schema.bpmn", BPMN_WITH_COMPILEFLOW_EXTENSIONS), SchemaValidation.STRICT))
            .isInstanceOf(CompileFlowException.ResourceException.class)
            .hasMessageContaining("Schema resource not found: missing-schema.xsd");
    }

    private static final class MissingSchemaParser extends BpmnXmlParser {
        @Override
        protected String getXSD() {
            return "missing-schema.xsd";
        }
    }
}
