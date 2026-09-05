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
package com.alibaba.compileflow.engine.bpmn.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.bpmn.parser.BpmnXmlParser;
import com.alibaba.compileflow.engine.bpmn.model.BpmnModel;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.xml.parser.SchemaValidation;
import com.alibaba.compileflow.engine.core.validation.ValidationFailure;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class BpmnModelValidatorTest {
    private final BpmnModelValidator validator = new BpmnModelValidator();

    private static String gatewayProcess(String defaultFlowId, String candidateFlow, String fallbackFlow) {
        return """
            <bpmn:startEvent id="start"/>
            <bpmn:exclusiveGateway id="decision" default="%s"/>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="enter"
                               sourceRef="start"
                               targetRef="decision"/>
            %s
            %s
            """
            .formatted(defaultFlowId, candidateFlow, fallbackFlow);
    }

    private static String conditionedFlow(String id, String expression) {
        return """
            <bpmn:sequenceFlow id="%s"
                               sourceRef="decision"
                               targetRef="end">
              <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression"
                                        language="java"><![CDATA[%s]]>
              </bpmn:conditionExpression>
            </bpmn:sequenceFlow>
            """
            .formatted(id, expression);
    }

    private static String plainFlow(String id) {
        return """
            <bpmn:sequenceFlow id="%s"
                               sourceRef="decision"
                               targetRef="end"/>
            """
            .formatted(id);
    }

    private static BpmnModel parse(String subprocessBody) {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions
                xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                xmlns:cf="http://www.compileflow.org"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                targetNamespace="urn:compileflow:validator">
              <bpmn:process id="validator_flow" isExecutable="true">
                <bpmn:startEvent id="start"/>
                <bpmn:subProcess id="subprocess">
            %s
                </bpmn:subProcess>
                <bpmn:endEvent id="end"/>
                <bpmn:sequenceFlow id="root_f1"
                                   sourceRef="start"
                                   targetRef="subprocess"/>
                <bpmn:sequenceFlow id="root_f2"
                                   sourceRef="subprocess"
                                   targetRef="end"/>
              </bpmn:process>
            </bpmn:definitions>
            """
            .formatted(subprocessBody);
        return BpmnXmlParser
            .getInstance()
            .parse(FlowSource.of("validator.bpmn", xml.getBytes(StandardCharsets.UTF_8)), SchemaValidation.DISABLED);
    }

    private static BpmnModel parseProcess(String processBody) {
        return parseProcess(processBody, "");
    }

    private static BpmnModel parseProcess(String processBody, String definitionsBody) {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions
                xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                xmlns:cf="http://www.compileflow.org"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                targetNamespace="urn:compileflow:validator">
            %s
              <bpmn:process id="validator_flow" isExecutable="true">
            %s
              </bpmn:process>
            </bpmn:definitions>
            """
            .formatted(definitionsBody, processBody);
        return BpmnXmlParser
            .getInstance()
            .parse(FlowSource.of("validator.bpmn", xml.getBytes(StandardCharsets.UTF_8)), SchemaValidation.DISABLED);
    }

    @Test
    void acceptsConnectedEmbeddedSubprocess() {
        List<ValidationFailure> messages = validator.validate(
                parse(
                        """
            <bpmn:startEvent id="nested_start"/>
            <bpmn:scriptTask id="nested_task" scriptFormat="qlexpress">
              <bpmn:script>1 + 1</bpmn:script>
            </bpmn:scriptTask>
            <bpmn:endEvent id="nested_end"/>
            <bpmn:sequenceFlow id="nested_f1"
                               sourceRef="nested_start"
                               targetRef="nested_task"/>
            <bpmn:sequenceFlow id="nested_f2"
                               sourceRef="nested_task"
                               targetRef="nested_end"/>
            """));

        assertThat(messages).isEmpty();
    }

    @Test
    void rejectsServiceTaskWithoutAnAction() {
        List<ValidationFailure> messages = validator.validate(
                parse(
                        """
            <bpmn:startEvent id="nested_start"/>
            <bpmn:serviceTask id="nested_task"/>
            <bpmn:endEvent id="nested_end"/>
            <bpmn:sequenceFlow id="nested_f1"
                               sourceRef="nested_start"
                               targetRef="nested_task"/>
            <bpmn:sequenceFlow id="nested_f2"
                               sourceRef="nested_task"
                               targetRef="nested_end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("serviceTask must declare one cf:action"));
    }

    @Test
    void acceptsManagedReplayWithoutDurableExecutionDeclaration() {
        List<ValidationFailure> messages = validator.validate(
                parseProcess(
                        """
            <bpmn:startEvent id="start"/>
            <bpmn:scriptTask id="task" scriptFormat="qlexpress">
              <bpmn:script>1 + 1</bpmn:script>
            </bpmn:scriptTask>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="flow1"
                               sourceRef="start"
                               targetRef="task"/>
            <bpmn:sequenceFlow id="flow2"
                               sourceRef="task"
                               targetRef="end"/>
            """));

        assertThat(messages).isEmpty();
    }

    @Test
    void rejectsEffectPolicyWithoutEffectExecution() {
        List<ValidationFailure> messages = validator.validate(
                parseProcess(
                        """
            <bpmn:startEvent id="start"/>
            <bpmn:serviceTask id="task">
              <bpmn:extensionElements>
                <cf:action type="spring-bean" bean="orders" class="java.lang.Runnable" method="run">
                  <cf:effectPolicy recovery="manual"/>
                </cf:action>
              </bpmn:extensionElements>
            </bpmn:serviceTask>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="flow1" sourceRef="start" targetRef="task"/>
            <bpmn:sequenceFlow id="flow2" sourceRef="task" targetRef="end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("effectPolicy requires execution=\"effect\""));
    }

    @Test
    void rejectsStandardLoopMaximumOutsideSemanticPlanRange() {
        assertThatThrownBy(() -> parseProcess(
                """
            <bpmn:startEvent id="start"/>
            <bpmn:scriptTask id="task" scriptFormat="qlexpress">
              <bpmn:standardLoopCharacteristics loopMaximum="2147483648"/>
              <bpmn:script>1 + 1</bpmn:script>
            </bpmn:scriptTask>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="flow1" sourceRef="start" targetRef="task"/>
            <bpmn:sequenceFlow id="flow2" sourceRef="task" targetRef="end"/>
            """))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("loopMaximum must not exceed 2147483647");
    }

    @Test
    void rejectsNegativeTimerDuration() {
        List<ValidationFailure> messages = validator.validate(
                parseProcess(
                        """
            <bpmn:startEvent id="start"/>
            <bpmn:intermediateCatchEvent id="timer">
              <bpmn:timerEventDefinition>
                <bpmn:timeDuration>-PT1S</bpmn:timeDuration>
              </bpmn:timerEventDefinition>
            </bpmn:intermediateCatchEvent>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="flow1" sourceRef="start" targetRef="timer"/>
            <bpmn:sequenceFlow id="flow2" sourceRef="timer" targetRef="end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("timeDuration must not be negative"));
    }

    @Test
    void rejectsTimerDurationThatCannotBeStoredAsMilliseconds() {
        List<ValidationFailure> messages = validator.validate(
                parseProcess(
                        """
            <bpmn:startEvent id="start"/>
            <bpmn:intermediateCatchEvent id="timer">
              <bpmn:timerEventDefinition>
                <bpmn:timeDuration>P106751991168D</bpmn:timeDuration>
              </bpmn:timerEventDefinition>
            </bpmn:intermediateCatchEvent>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="flow1" sourceRef="start" targetRef="timer"/>
            <bpmn:sequenceFlow id="flow2" sourceRef="timer" targetRef="end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("representable as milliseconds"));
    }

    @Test
    void acceptsInvocationPolicyOnNativeScript() {
        assertThat(validator.validate(
                parseProcess(
                        """
            <bpmn:startEvent id="start"/>
            <bpmn:scriptTask id="task" scriptFormat="qlexpress">
              <bpmn:extensionElements>
                <cf:invocationPolicy attemptTimeout="PT1S" maxAttempts="2"/>
              </bpmn:extensionElements>
              <bpmn:script>1 + 1</bpmn:script>
            </bpmn:scriptTask>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="flow1"
                               sourceRef="start"
                               targetRef="task"/>
            <bpmn:sequenceFlow id="flow2"
                               sourceRef="task"
                               targetRef="end"/>
            """)))
            .isEmpty();
    }

    @Test
    void rejectsScriptActionOnServiceTask() {
        List<ValidationFailure> messages = validator.validate(
                parseProcess(
                        """
            <bpmn:startEvent id="start"/>
            <bpmn:serviceTask id="task">
              <bpmn:extensionElements>
                <cf:action type="script" language="qlexpress">
                  <cf:code>1 + 1</cf:code>
                </cf:action>
              </bpmn:extensionElements>
            </bpmn:serviceTask>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="flow1" sourceRef="start" targetRef="task"/>
            <bpmn:sequenceFlow id="flow2" sourceRef="task" targetRef="end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("serviceTask action type must be java or spring-bean"));
    }

    @Test
    void rejectsContextMappingOnProcessVariables() {
        assertThatThrownBy(() -> parseProcess(
                """
            <bpmn:extensionElements>
              <cf:var name="input"
                      dataType="java.lang.String"
                      source="request.value"/>
            </bpmn:extensionElements>
            <bpmn:startEvent id="start"/>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="flow"
                               sourceRef="start"
                               targetRef="end"/>
            """))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Unsupported attribute on cf:var: source");
    }

    @Test
    void rejectsCallActivityWithoutACalledElement() {
        List<ValidationFailure> messages = validator.validate(
                parse(
                        """
            <bpmn:startEvent id="nested_start"/>
            <bpmn:callActivity id="nested_call"/>
            <bpmn:endEvent id="nested_end"/>
            <bpmn:sequenceFlow id="nested_f1"
                               sourceRef="nested_start"
                               targetRef="nested_call"/>
            <bpmn:sequenceFlow id="nested_f2"
                               sourceRef="nested_call"
                               targetRef="nested_end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("callActivity nested_call has an invalid process reference: code"));
    }

    @Test
    void rejectsCallActivityWithAnUnresolvableProcessCode() {
        List<ValidationFailure> messages = validator.validate(
                parse(
                        """
            <bpmn:startEvent id="nested_start"/>
            <bpmn:callActivity id="nested_call"
                               calledElement="invalid:code"/>
            <bpmn:endEvent id="nested_end"/>
            <bpmn:sequenceFlow id="nested_f1"
                               sourceRef="nested_start"
                               targetRef="nested_call"/>
            <bpmn:sequenceFlow id="nested_f2"
                               sourceRef="nested_call"
                               targetRef="nested_end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("has an invalid process reference"));
    }

    @Test
    void rejectsConflictingCallActivityTargets() {
        List<ValidationFailure> messages = validator.validate(
                parse(
                        """
            <bpmn:startEvent id="nested_start"/>
            <bpmn:callActivity id="nested_call"
                               calledElement="child.flow"
                               cf:classpath="child/flow.bpmn"
                               cf:version="v1"/>
            <bpmn:endEvent id="nested_end"/>
            <bpmn:sequenceFlow id="nested_f1"
                               sourceRef="nested_start"
                               targetRef="nested_call"/>
            <bpmn:sequenceFlow id="nested_f2"
                               sourceRef="nested_call"
                               targetRef="nested_end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("exactly one of classpath or version is required"));
    }

    @Test
    void rejectsCallActivityOutputsMappedToTheSameParentVariable() {
        List<ValidationFailure> messages = validator.validate(
                parseProcess(
                        """
            <bpmn:extensionElements>
              <cf:var name="result"
                      dataType="java.lang.Integer"
                      inOutType="return"/>
            </bpmn:extensionElements>
            <bpmn:startEvent id="start"/>
            <bpmn:callActivity id="call"
                               calledElement="child.flow"
                               cf:classpath="child/flow.bpmn">
              <bpmn:extensionElements>
                <cf:output
                        source="firstResult"
                        target="result"/>
                <cf:output
                        source="secondResult"
                        target="result"/>
              </bpmn:extensionElements>
            </bpmn:callActivity>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="toCall"
                               sourceRef="start"
                               targetRef="call"/>
            <bpmn:sequenceFlow id="toEnd"
                               sourceRef="call"
                               targetRef="end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("duplicate output target: result"));
    }

    @Test
    void rejectsDataTypeOnCalledProcessInput() {
        List<ValidationFailure> messages = validator.validate(
                parseProcess(
                        """
            <bpmn:extensionElements>
              <cf:var name="request"
                      dataType="java.lang.String"
                      inOutType="param"/>
            </bpmn:extensionElements>
            <bpmn:startEvent id="start"/>
            <bpmn:callActivity id="call"
                               calledElement="child.flow"
                               cf:classpath="child/flow.bpmn">
              <bpmn:extensionElements>
                <cf:input source="request"
                          target="request"
                          dataType="java.lang.String"/>
              </bpmn:extensionElements>
            </bpmn:callActivity>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="toCall"
                               sourceRef="start"
                               targetRef="call"/>
            <bpmn:sequenceFlow id="toEnd"
                               sourceRef="call"
                               targetRef="end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("called-process input must not declare dataType"));
    }

    @Test
    void rejectsReceiveTaskWithUnknownMessageReference() {
        List<ValidationFailure> messages = validator.validate(
                parse(
                        """
            <bpmn:startEvent id="nested_start"/>
            <bpmn:receiveTask id="nested_receive"
                              messageRef="missing_message"/>
            <bpmn:endEvent id="nested_end"/>
            <bpmn:sequenceFlow id="nested_f1"
                               sourceRef="nested_start"
                               targetRef="nested_receive"/>
            <bpmn:sequenceFlow id="nested_f2"
                               sourceRef="nested_receive"
                               targetRef="nested_end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("messageRef must resolve to exactly one message"));
    }

    @Test
    void rejectsInvalidExecutableActionContract() {
        List<ValidationFailure> messages = validator.validate(
                parse(
                        """
            <bpmn:startEvent id="nested_start"/>
            <bpmn:serviceTask id="nested_task">
              <bpmn:extensionElements>
                <cf:action type="java" class="invalid/class"
                                   method="not-valid"/>
              </bpmn:extensionElements>
            </bpmn:serviceTask>
            <bpmn:endEvent id="nested_end"/>
            <bpmn:sequenceFlow id="nested_f1"
                               sourceRef="nested_start"
                               targetRef="nested_task"/>
            <bpmn:sequenceFlow id="nested_f2"
                               sourceRef="nested_task"
                               targetRef="nested_end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("has invalid class"))
            .anyMatch(message -> message.contains("has invalid Java method"));
    }

    @Test
    void rejectsUnknownMultiInstanceCollectionBeforeCodeGeneration() {
        List<ValidationFailure> messages = validator.validate(
                parseProcess(
                        """
            <bpmn:startEvent id="start"/>
            <bpmn:subProcess id="loop">
              <bpmn:multiInstanceLoopCharacteristics
                  isSequential="true"
                  cf:collection="missing"
                  cf:item="item"/>
              <bpmn:startEvent id="inner_start"/>
              <bpmn:endEvent id="inner_end"/>
              <bpmn:sequenceFlow id="inner_flow"
                                 sourceRef="inner_start"
                                 targetRef="inner_end"/>
            </bpmn:subProcess>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="to_loop"
                               sourceRef="start"
                               targetRef="loop"/>
            <bpmn:sequenceFlow id="to_end"
                               sourceRef="loop"
                               targetRef="end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("cf:collection must reference a declared process variable"));
    }

    @Test
    void rejectsMultiInstanceVariablesThatShadowProcessState() {
        List<ValidationFailure> messages = validator.validate(
                parseProcess(
                        """
            <bpmn:extensionElements>
              <cf:var name="items"
                      dataType="java.util.List&lt;java.lang.String&gt;"
                      inOutType="param"/>
            </bpmn:extensionElements>
            <bpmn:startEvent id="start"/>
            <bpmn:subProcess id="loop">
              <bpmn:multiInstanceLoopCharacteristics
                  isSequential="true"
                  cf:collection="items"
                  cf:item="items"/>
              <bpmn:startEvent id="inner_start"/>
              <bpmn:endEvent id="inner_end"/>
              <bpmn:sequenceFlow id="inner_flow"
                                 sourceRef="inner_start"
                                 targetRef="inner_end"/>
            </bpmn:subProcess>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="to_loop"
                               sourceRef="start"
                               targetRef="loop"/>
            <bpmn:sequenceFlow id="to_end"
                               sourceRef="loop"
                               targetRef="end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("cf:item must not shadow a process"));
    }

    @Test
    void acceptsNestedCollectionFromAnEnclosingMultiInstanceVariable() {
        List<ValidationFailure> messages = validator.validate(
                parseProcess(
                        """
            <bpmn:extensionElements>
              <cf:var name="groups"
                      dataType="java.util.List&lt;java.util.List&lt;java.lang.String&gt;&gt;"
                      inOutType="param"/>
            </bpmn:extensionElements>
            <bpmn:startEvent id="start"/>
            <bpmn:subProcess id="outer_loop">
              <bpmn:multiInstanceLoopCharacteristics
                  isSequential="true"
                  cf:collection="groups"
                  cf:item="group"
                  cf:itemType="java.util.List"/>
              <bpmn:startEvent id="outer_start"/>
              <bpmn:subProcess id="inner_loop">
                <bpmn:multiInstanceLoopCharacteristics
                    isSequential="true"
                    cf:collection="group"
                    cf:item="item"
                    cf:itemType="java.lang.String"/>
                <bpmn:startEvent id="inner_start"/>
                <bpmn:endEvent id="inner_end"/>
                <bpmn:sequenceFlow id="inner_flow"
                                   sourceRef="inner_start"
                                   targetRef="inner_end"/>
              </bpmn:subProcess>
              <bpmn:endEvent id="outer_end"/>
              <bpmn:sequenceFlow id="outer_to_inner"
                                 sourceRef="outer_start"
                                 targetRef="inner_loop"/>
              <bpmn:sequenceFlow id="outer_to_end"
                                 sourceRef="inner_loop"
                                 targetRef="outer_end"/>
            </bpmn:subProcess>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="to_outer"
                               sourceRef="start"
                               targetRef="outer_loop"/>
            <bpmn:sequenceFlow id="to_end"
                               sourceRef="outer_loop"
                               targetRef="end"/>
            """));

        assertThat(messages).isEmpty();
    }

    @Test
    void acceptsTriggerEntryInsideEmbeddedSubprocess() {
        List<ValidationFailure> messages = validator.validate(
                parse(
                        """
            <bpmn:startEvent id="nested_start"/>
            <bpmn:intermediateCatchEvent id="nested_wait">
              <bpmn:timerEventDefinition>
                <bpmn:timeDuration>PT5M</bpmn:timeDuration>
              </bpmn:timerEventDefinition>
            </bpmn:intermediateCatchEvent>
            <bpmn:endEvent id="nested_end"/>
            <bpmn:sequenceFlow id="nested_f1"
                               sourceRef="nested_start"
                               targetRef="nested_wait"/>
            <bpmn:sequenceFlow id="nested_f2"
                               sourceRef="nested_wait"
                               targetRef="nested_end"/>
            """));

        assertThat(messages).isEmpty();
    }

    @Test
    void acceptsConcurrentSplitInsideEmbeddedSubprocess() {
        List<ValidationFailure> messages = validator.validate(
                parse(
                        """
            <bpmn:startEvent id="nested_start"/>
            <bpmn:parallelGateway id="fork"/>
            <bpmn:scriptTask id="left" scriptFormat="qlexpress">
              <bpmn:script>1 + 1</bpmn:script>
            </bpmn:scriptTask>
            <bpmn:scriptTask id="right" scriptFormat="qlexpress">
              <bpmn:script>2 + 2</bpmn:script>
            </bpmn:scriptTask>
            <bpmn:parallelGateway id="join"/>
            <bpmn:endEvent id="nested_end"/>
            <bpmn:sequenceFlow id="to_fork"
                               sourceRef="nested_start"
                               targetRef="fork"/>
            <bpmn:sequenceFlow id="to_left"
                               sourceRef="fork"
                               targetRef="left"/>
            <bpmn:sequenceFlow id="to_right"
                               sourceRef="fork"
                               targetRef="right"/>
            <bpmn:sequenceFlow id="left_to_join"
                               sourceRef="left"
                               targetRef="join"/>
            <bpmn:sequenceFlow id="right_to_join"
                               sourceRef="right"
                               targetRef="join"/>
            <bpmn:sequenceFlow id="to_end"
                               sourceRef="join"
                               targetRef="nested_end"/>
            """));

        assertThat(messages).isEmpty();
    }

    @Test
    void rejectsNodeIdsDuplicatedAcrossContainerBoundaries() {
        List<ValidationFailure> messages = validator.validate(
                parse(
                        """
            <bpmn:startEvent id="nested_start"/>
            <bpmn:serviceTask id="start"/>
            <bpmn:endEvent id="nested_end"/>
            <bpmn:sequenceFlow id="nested_f1"
                               sourceRef="nested_start"
                               targetRef="start"/>
            <bpmn:sequenceFlow id="nested_f2"
                               sourceRef="start"
                               targetRef="nested_end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("duplicate node id across the complete model"));
    }

    @Test
    void rejectsSubprocessWithoutAnEndEvent() {
        List<ValidationFailure> messages = validator.validate(
                parse(
                        """
            <bpmn:startEvent id="nested_start"/>
            <bpmn:serviceTask id="nested_task"/>
            <bpmn:sequenceFlow id="nested_f1"
                               sourceRef="nested_start"
                               targetRef="nested_task"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("must contain exactly one end event"));
    }

    @Test
    void rejectsUnreachableNodesInsideSubprocess() {
        List<ValidationFailure> messages = validator.validate(
                parse(
                        """
            <bpmn:startEvent id="nested_start"/>
            <bpmn:serviceTask id="nested_task"/>
            <bpmn:serviceTask id="orphan"/>
            <bpmn:endEvent id="nested_end"/>
            <bpmn:sequenceFlow id="nested_f1"
                               sourceRef="nested_start"
                               targetRef="nested_task"/>
            <bpmn:sequenceFlow id="nested_f2"
                               sourceRef="nested_task"
                               targetRef="nested_end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("nodeIds=[orphan]"));
    }

    @Test
    void rejectsDuplicateSequenceFlowIdsAcrossContainers() {
        List<ValidationFailure> messages = validator.validate(
                parse(
                        """
            <bpmn:startEvent id="nested_start"/>
            <bpmn:scriptTask id="nested_task" scriptFormat="qlexpress">
              <bpmn:script>1 + 1</bpmn:script>
            </bpmn:scriptTask>
            <bpmn:endEvent id="nested_end"/>
            <bpmn:sequenceFlow id="root_f1"
                               sourceRef="nested_start"
                               targetRef="nested_task"/>
            <bpmn:sequenceFlow id="nested_f2"
                               sourceRef="nested_task"
                               targetRef="nested_end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("duplicateIds=[root_f1]"));
    }

    @Test
    void rejectsNonInterruptingStartEvents() {
        assertThatThrownBy(() -> parseProcess(
                """
            <bpmn:startEvent id="start" isInterrupting="false"/>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="flow"
                               sourceRef="start"
                               targetRef="end"/>
            """))
            .hasMessageContaining("Non-interrupting start events");
    }

    @Test
    void rejectsNonImmediateExecutableSequenceFlows() {
        assertThatThrownBy(() -> parseProcess(
                """
            <bpmn:startEvent id="start"/>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="flow"
                               sourceRef="start"
                               targetRef="end"
                               isImmediate="false"/>
            """))
            .hasMessageContaining("isImmediate=\"false\"");
    }

    @Test
    void rejectsUnsupportedReceiveTaskRoutingContracts() {
        assertThatThrownBy(() -> parseProcess("""
            <bpmn:startEvent id="start"/>
            <bpmn:receiveTask id="receive"
                              messageRef="message"
                              implementation="custom"
                              operationRef="operation"/>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="flow_1"
                               sourceRef="start"
                               targetRef="receive"/>
            <bpmn:sequenceFlow id="flow_2"
                               sourceRef="receive"
                               targetRef="end"/>
            """,
                """
            <bpmn:message id="message" name="Order received"/>
            """))
            .hasMessageContaining("implementation and operationRef are not supported");
    }

    @Test
    void rejectsReceiveTaskWithoutMessageReference() {
        List<ValidationFailure> messages = validator.validate(
                parseProcess(
                        """
            <bpmn:startEvent id="start"/>
            <bpmn:receiveTask id="receive"/>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="flow_1"
                               sourceRef="start"
                               targetRef="receive"/>
            <bpmn:sequenceFlow id="flow_2"
                               sourceRef="receive"
                               targetRef="end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("receiveTask must declare messageRef"));
    }

    @Test
    void acceptsSupportedIntermediateCatchEvents() {
        List<ValidationFailure> message = validator.validate(
                parseProcess("""
            <bpmn:startEvent id="start"/>
            <bpmn:intermediateCatchEvent id="catch">
              <bpmn:messageEventDefinition messageRef="message"/>
            </bpmn:intermediateCatchEvent>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="flow_1" sourceRef="start" targetRef="catch"/>
            <bpmn:sequenceFlow id="flow_2" sourceRef="catch" targetRef="end"/>
            """,
                        """
            <bpmn:message id="message" name="Order received"/>
            """));
        List<ValidationFailure> timer = validator.validate(
                parseProcess(
                        """
            <bpmn:startEvent id="start"/>
            <bpmn:intermediateCatchEvent id="catch">
              <bpmn:timerEventDefinition>
                <bpmn:timeDuration>PT5M</bpmn:timeDuration>
              </bpmn:timerEventDefinition>
            </bpmn:intermediateCatchEvent>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="flow_1" sourceRef="start" targetRef="catch"/>
            <bpmn:sequenceFlow id="flow_2" sourceRef="catch" targetRef="end"/>
            """));

        assertThat(message).isEmpty();
        assertThat(timer).isEmpty();
    }

    @Test
    void acceptsSourceLegalTimerCatchEventValues() {
        List<ValidationFailure> cycle = validator.validate(
                parseProcess(
                        """
            <bpmn:startEvent id="start"/>
            <bpmn:intermediateCatchEvent id="catch">
              <bpmn:timerEventDefinition>
                <bpmn:timeCycle>R/PT5M</bpmn:timeCycle>
              </bpmn:timerEventDefinition>
            </bpmn:intermediateCatchEvent>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="flow_1" sourceRef="start" targetRef="catch"/>
            <bpmn:sequenceFlow id="flow_2" sourceRef="catch" targetRef="end"/>
            """));
        List<ValidationFailure> literalDate = validator.validate(
                parseProcess(
                        """
            <bpmn:startEvent id="start"/>
            <bpmn:intermediateCatchEvent id="catch">
              <bpmn:timerEventDefinition>
                <bpmn:timeDate>2026-08-19T00:00:00Z</bpmn:timeDate>
              </bpmn:timerEventDefinition>
            </bpmn:intermediateCatchEvent>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="flow_1" sourceRef="start" targetRef="catch"/>
            <bpmn:sequenceFlow id="flow_2" sourceRef="catch" targetRef="end"/>
            """));

        assertThat(cycle).isEmpty();
        assertThat(literalDate).isEmpty();
    }

    @Test
    void acceptsLoopCharacteristicsOnReceiveTask() {
        List<ValidationFailure> messages = validator.validate(
                parseProcess("""
            <bpmn:startEvent id="start"/>
            <bpmn:receiveTask id="receive" messageRef="message">
              <bpmn:standardLoopCharacteristics loopMaximum="2"/>
            </bpmn:receiveTask>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="flow_1" sourceRef="start" targetRef="receive"/>
            <bpmn:sequenceFlow id="flow_2" sourceRef="receive" targetRef="end"/>
            """,
                        """
            <bpmn:message id="message" name="Order received"/>
            """));

        assertThat(messages).isEmpty();
    }

    @Test
    void rejectsRemovedReceiveTaskEntryAndExitActions() {
        assertThatThrownBy(() -> parseProcess("""
            <bpmn:startEvent id="start"/>
            <bpmn:receiveTask id="receive" messageRef="message">
              <bpmn:extensionElements>
                <cf:inAction type="java">
                  <cf:actionHandle method="run"/>
                </cf:inAction>
                <cf:outAction type="java">
                  <cf:actionHandle class="example.ValidAction"
                                   method="run"/>
                </cf:outAction>
              </bpmn:extensionElements>
            </bpmn:receiveTask>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="flow_1"
                               sourceRef="start"
                               targetRef="receive"/>
            <bpmn:sequenceFlow id="flow_2"
                               sourceRef="receive"
                               targetRef="end"/>
            """,
                """
            <bpmn:message id="message" name="Order received"/>
            """))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Unsupported cf:inAction extension");
    }

    @Test
    void rejectsUnknownOrConditionalDefaultSequenceFlows() {
        List<ValidationFailure> unknownDefault = validator.validate(
                parseProcess(
                        gatewayProcess("missing", conditionedFlow("candidate", "true"),
                                conditionedFlow("fallback", "false"))));
        List<ValidationFailure> conditionalDefault = validator.validate(
                parseProcess(
                        gatewayProcess("fallback", conditionedFlow("candidate", "true"),
                                conditionedFlow("fallback", "false"))));

        assertThat(unknownDefault)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("default must reference exactly one outgoing"));
        assertThat(conditionalDefault)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("default sequenceFlow must not declare a condition"));
    }

    @Test
    void rejectsUnguardedNonDefaultGatewayFlow() {
        List<ValidationFailure> messages =
                validator.validate(
                        parseProcess(gatewayProcess("fallback", plainFlow("candidate"), plainFlow("fallback"))));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("non-default outgoing sequenceFlow must declare a Java condition"));
    }

    @Test
    void acceptsExplicitUnguardedDefaultAfterGuardedFlows() {
        List<ValidationFailure> messages = validator.validate(
                parseProcess(
                        gatewayProcess("fallback", conditionedFlow("candidate", "amount > 100"), plainFlow("fallback"))));

        assertThat(messages).isEmpty();
    }

    @Test
    void validatesDeclaredGatewayDirectionAgainstTopology() {
        List<ValidationFailure> matching = validator.validate(
                parseProcess(
                        """
            <bpmn:startEvent id="start"/>
            <bpmn:parallelGateway id="fork" gatewayDirection="Diverging"/>
            <bpmn:endEvent id="left"/>
            <bpmn:endEvent id="right"/>
            <bpmn:sequenceFlow id="enter" sourceRef="start" targetRef="fork"/>
            <bpmn:sequenceFlow id="to_left" sourceRef="fork" targetRef="left"/>
            <bpmn:sequenceFlow id="to_right" sourceRef="fork" targetRef="right"/>
            """));
        List<ValidationFailure> mismatched = validator.validate(
                parseProcess(
                        """
            <bpmn:startEvent id="start"/>
            <bpmn:parallelGateway id="fork" gatewayDirection="Converging"/>
            <bpmn:endEvent id="left"/>
            <bpmn:endEvent id="right"/>
            <bpmn:sequenceFlow id="enter" sourceRef="start" targetRef="fork"/>
            <bpmn:sequenceFlow id="to_left" sourceRef="fork" targetRef="left"/>
            <bpmn:sequenceFlow id="to_right" sourceRef="fork" targetRef="right"/>
            """));

        assertThat(matching)
            .extracting(ValidationFailure::message)
            .noneMatch(message -> message.contains("gatewayDirection"));
        assertThat(mismatched)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains(
                    "gatewayDirection must match control-flow topology, id=fork, declared=Converging, actual=Diverging"));
    }

    @Test
    void rejectsConditionOnParallelGatewayFlow() {
        List<ValidationFailure> messages = validator.validate(
                parseProcess(
                        """
            <bpmn:startEvent id="start"/>
            <bpmn:parallelGateway id="parallel"/>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="enter"
                               sourceRef="start"
                               targetRef="parallel"/>
            <bpmn:sequenceFlow id="conditional"
                               sourceRef="parallel"
                               targetRef="end">
              <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression"
                                        language="java">true
              </bpmn:conditionExpression>
            </bpmn:sequenceFlow>
            <bpmn:sequenceFlow id="unconditional"
                               sourceRef="parallel"
                               targetRef="end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains(
                    "Parallel gateway should not have conditional" + " outgoing transitions"));
    }

    @Test
    void rejectsImplicitBranchingAndConditionsOnActivities() {
        List<ValidationFailure> messages = validator.validate(
                parseProcess(
                        """
            <bpmn:startEvent id="start"/>
            <bpmn:scriptTask id="task" scriptFormat="qlexpress">
              <bpmn:script>1 + 1</bpmn:script>
            </bpmn:scriptTask>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="to_task"
                               sourceRef="start"
                               targetRef="task"/>
            <bpmn:sequenceFlow id="first"
                               sourceRef="task"
                               targetRef="end">
              <bpmn:conditionExpression language="java"
                  xsi:type="bpmn:tFormalExpression">true</bpmn:conditionExpression>
            </bpmn:sequenceFlow>
            <bpmn:sequenceFlow id="second"
                               sourceRef="task"
                               targetRef="end"/>
            """));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("Non-gateway node must not branch"))
            .anyMatch(message -> message.contains("Conditional outgoing transitions are supported only"));
    }

    @Test
    void rejectsDirectMutationInGatewayConditions() {
        List<ValidationFailure> messages = validator.validate(
                parseProcess(
                        gatewayProcess("fallback", conditionedFlow("candidate", "approved = true"),
                                plainFlow("fallback"))));

        assertThat(messages)
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("must be side-effect free"));
    }
}
