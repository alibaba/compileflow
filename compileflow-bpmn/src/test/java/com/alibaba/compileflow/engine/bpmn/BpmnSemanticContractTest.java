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
import com.alibaba.compileflow.engine.bpmn.model.ExclusiveGateway;
import com.alibaba.compileflow.engine.bpmn.model.IntermediateCatchEvent;
import com.alibaba.compileflow.engine.bpmn.model.MultiInstanceLoopCharacteristics;
import com.alibaba.compileflow.engine.bpmn.model.ReceiveTask;
import com.alibaba.compileflow.engine.bpmn.model.ScriptTask;
import com.alibaba.compileflow.engine.bpmn.model.ServiceTask;
import com.alibaba.compileflow.engine.bpmn.model.StandardLoopCharacteristics;
import com.alibaba.compileflow.engine.bpmn.model.TimerValue;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.xml.parser.SchemaValidation;
import com.alibaba.compileflow.engine.core.model.AbstractFlowElement;
import com.alibaba.compileflow.engine.core.model.action.Action;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.model.action.EffectRecovery;
import com.alibaba.compileflow.engine.core.model.action.ActionType;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BpmnSemanticContractTest {
    private static BpmnModel parseStrict(String xml) {
        return parse(xml, SchemaValidation.STRICT);
    }

    private static BpmnModel parseWithoutSchema(String xml) {
        return parse(xml, SchemaValidation.DISABLED);
    }

    private static BpmnModel parse(String xml, SchemaValidation schemaValidation) {
        return BpmnXmlParser
            .getInstance()
            .parse(FlowSource.of("semantic-contract.bpmn", xml.getBytes(StandardCharsets.UTF_8)), schemaValidation);
    }

    private static String write(BpmnModel model) {
        ByteArrayOutputStream output = (ByteArrayOutputStream) BpmnXmlWriter.getInstance().write(model);
        return output.toString(StandardCharsets.UTF_8);
    }

    private static String standardLoopFlow() {
        return flow("Definitions_contract",
                "<bpmn:serviceTask id=\"task\">"
                + "<bpmn:standardLoopCharacteristics testBefore=\"true\" loopMaximum=\"3\">"
                + "<bpmn:loopCondition xsi:type=\"bpmn:tFormalExpression\"" + " language=\"java\">"
                + "attempts &lt; 3</bpmn:loopCondition>" + "</bpmn:standardLoopCharacteristics>" + "</bpmn:serviceTask>");
    }

    private static String multiInstanceFlow(boolean sequential) {
        return flow("Definitions_multi",
                "<bpmn:serviceTask id=\"task\">" + "<bpmn:multiInstanceLoopCharacteristics isSequential=\"" + sequential
                + "\" cf:collection=\"items\" cf:item=\"item\"" + " cf:itemType=\"java.lang.String\" cf:index=\"index\""
                + (sequential ? "" : " cf:target=\"results\" cf:source=\"result\"") + "/>" + "</bpmn:serviceTask>");
    }

    private static String scriptFlow() {
        return flow("Definitions_script",
                "<bpmn:scriptTask id=\"task\" scriptFormat=\"java\">"
                + "<bpmn:script><![CDATA[\n  first();\n    second();\n]]></bpmn:script>" + "</bpmn:scriptTask>");
    }

    private static String receiveTaskFlow() {
        return String.join("\n", "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
                "                  targetNamespace=\"urn:compileflow:test\">",
                "  <bpmn:message id=\"message_order\" name=\"order\"/>",
                "  <bpmn:process id=\"receive-contract\" isExecutable=\"true\">",
                "    <bpmn:startEvent " + "id=\"start\"/>",
                "    <bpmn:receiveTask id=\"receive\" messageRef=\"message_order\"",
                "                      implementation=\"##WebService\" operationRef=\"operation_order\"/>",
                "    <bpmn:endEvent id=\"end\"/>",
                "    <bpmn:sequenceFlow id=\"to_receive\" sourceRef=\"start\" targetRef=\"receive\"/>",
                "    <bpmn:sequenceFlow id=\"to_end\" sourceRef=\"receive\" targetRef=\"end\"/>", "  </bpmn:process>",
                "</bpmn:definitions>");
    }

    private static String catchEventFlow(String definition) {
        return String.join("\n", "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
                "                  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"",
                "                  expressionLanguage=\"http://www.compileflow.org/language/java\"",
                "                  targetNamespace=\"urn:compileflow:test\">",
                "  <bpmn:message id=\"message_order\" name=\"order\"/>",
                "  <bpmn:process id=\"catch-contract\" isExecutable=\"true\">",
                "    <bpmn:startEvent " + "id=\"start\"/>", "    <bpmn:intermediateCatchEvent id=\"catch\">",
                "      " + definition, "    </bpmn:intermediateCatchEvent>", "    <bpmn:endEvent id=\"end\"/>",
                "    <bpmn:sequenceFlow id=\"to_catch\" sourceRef=\"start\" targetRef=\"catch\"/>",
                "    <bpmn:sequenceFlow id=\"to_end\" sourceRef=\"catch\" targetRef=\"end\"/>", "  </bpmn:process>",
                "</bpmn:definitions>");
    }

    private static String javaScriptActionFlow() {
        return flow("Definitions_script_java",
                "<bpmn:scriptTask id=\"task\" scriptFormat=\"java\"><bpmn:extensionElements>"
                + "<cf:output target=\"result\" dataType=\"java.lang.String\"/>"
                + "</bpmn:extensionElements><bpmn:script>return Instant.now().toString();</bpmn:script>"
                + "</bpmn:scriptTask>");
    }

    private static String customScriptActionFlow() {
        return flow("Definitions_custom_script",
                "<bpmn:scriptTask id=\"task\" scriptFormat=\"qlexpress\" cf:execution=\"effect\">"
                + "<bpmn:extensionElements><cf:effectPolicy recovery=\"manual\"/>"
                + "</bpmn:extensionElements><bpmn:script>input + 1</bpmn:script></bpmn:scriptTask>");
    }

    private static String effectActionFlow() {
        return flow("Definitions_effect",
                "<bpmn:serviceTask id=\"task\"><bpmn:extensionElements>"
                + "<cf:action type=\"spring-bean\" execution=\"effect\""
                + " bean=\"orders\" class=\"java.lang.Runnable\" method=\"run\">"
                + "<cf:effectPolicy recovery=\"reconcile\" maxAttempts=\"3\""
                + " maxReconcileAttempts=\"5\" recoveryDelay=\"PT1S\" maxRecoveryDuration=\"PT1M\">"
                + "<cf:reconcileAction type=\"spring-bean\" bean=\"orderReconciler\""
                + " class=\"java.lang.Runnable\" method=\"run\"/>" + "</cf:effectPolicy></cf:action>"
                + "</bpmn:extensionElements></bpmn:serviceTask>");
    }

    private static String invocationDurationFlow(String duration) {
        return flow("Definitions_protocol_duration",
                "<bpmn:serviceTask id=\"task\"><bpmn:extensionElements>"
                + "<cf:action type=\"java\" class=\"java.lang.Runnable\" method=\"run\">"
                + "<cf:invocationPolicy initialBackoff=\"" + duration + "\"/>"
                + "</cf:action></bpmn:extensionElements></bpmn:serviceTask>");
    }

    private static String gatewayActionFlow() {
        return String.join("\n", "<bpmn:definitions" + " xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
                "                  xmlns:cf=\"http://www.compileflow.org\"",
                "                  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"",
                "                  id=\"Definitions_gateway_action\"",
                "                  targetNamespace=\"urn:compileflow:test\">",
                "  <bpmn:process id=\"gateway-action\" isExecutable=\"true\">",
                "    <bpmn:startEvent " + "id=\"start\"/>", "    <bpmn:exclusiveGateway id=\"gateway\">",
                "      <bpmn:extensionElements>", "        <cf:action type=\"script\" language=\"java\">",
                "          <cf:code>return null;</cf:code>", "          ", "        </cf:action>",
                "      </bpmn:extensionElements>", "    </bpmn:exclusiveGateway>",
                "    <bpmn:endEvent " + "id=\"left\"/>", "    <bpmn:endEvent id=\"right\"/>",
                "    <bpmn:sequenceFlow id=\"to_gateway\"" + " sourceRef=\"start\" targetRef=\"gateway\"/>",
                "    <bpmn:sequenceFlow id=\"to_left\"" + " sourceRef=\"gateway\" targetRef=\"left\">",
                "      <bpmn:conditionExpression xsi:type=\"bpmn:tFormalExpression\""
                + " language=\"java\">flag</bpmn:conditionExpression>", "    </bpmn:sequenceFlow>",
                "    <bpmn:sequenceFlow id=\"to_right\"" + " sourceRef=\"gateway\" targetRef=\"right\"/>",
                "  </bpmn:process>", "</bpmn:definitions>");
    }

    private static String eventSubprocessFlow() {
        return flow("Definitions_event_subprocess",
                "<bpmn:subProcess id=\"task\" triggeredByEvent=\"true\">" + "<bpmn:startEvent id=\"nested_start\"/>"
                + "<bpmn:endEvent id=\"nested_end\"/>" + "<bpmn:sequenceFlow id=\"nested_flow\""
                + " sourceRef=\"nested_start\" targetRef=\"nested_end\"/>" + "</bpmn:subProcess>");
    }

    private static String nestedSubprocessFlow() {
        return flow("Definitions_nested",
                "<bpmn:subProcess id=\"task\">" + "<bpmn:startEvent id=\"nested_start\"/>"
                + "<bpmn:serviceTask id=\"nested_task\"/>" + "<bpmn:endEvent id=\"nested_end\"/>"
                + "<bpmn:sequenceFlow id=\"nested_to_task\"" + " sourceRef=\"nested_start\" targetRef=\"nested_task\"/>"
                + "<bpmn:sequenceFlow id=\"nested_to_end\"" + " sourceRef=\"nested_task\" targetRef=\"nested_end\"/>"
                + "</bpmn:subProcess>");
    }

    private static String flow(String definitionsId, String activity) {
        return String.join("\n", "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
                "                  xmlns:cf=\"http://www.compileflow.org\"",
                "                  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"",
                "                  id=\"" + definitionsId + "\"",
                "                  targetNamespace=\"urn:compileflow:test\">",
                "  <bpmn:process id=\"semantic-contract\" isExecutable=\"true\">",
                "    <bpmn:startEvent " + "id=\"start\"/>", "    " + activity, "    <bpmn:endEvent id=\"end\"/>",
                "    <bpmn:sequenceFlow id=\"to_task\" sourceRef=\"start\" targetRef=\"task\"/>",
                "    <bpmn:sequenceFlow id=\"to_end\" sourceRef=\"task\" targetRef=\"end\"/>", "  </bpmn:process>",
                "</bpmn:definitions>");
    }

    @Test
    void standardLoopRoundTripProducesSchemaValidBpmn() {
        BpmnModel model = parseStrict(standardLoopFlow());

        String xml = write(model);
        BpmnModel reparsed = parseStrict(xml);
        ServiceTask task = reparsed.getFlowElement("task", ServiceTask.class);
        StandardLoopCharacteristics loop = (StandardLoopCharacteristics) task.getLoopCharacteristics();

        assertThat(loop.getTestBefore()).isTrue();
        assertThat(loop.getLoopMaximum()).isEqualTo(3);
        assertThat(loop.getLoopCondition()).isEqualTo("attempts < 3");
        assertThat(reparsed.getDefinitionsId()).isEqualTo("Definitions_contract");
        assertThat(reparsed.getTargetNamespace()).isEqualTo("urn:compileflow:test");
    }

    @Test
    void sequentialCollectionMultiInstanceRoundTripProducesSchemaValidBpmn() {
        BpmnModel model = parseStrict(multiInstanceFlow(true));

        BpmnModel reparsed = parseStrict(write(model));
        ServiceTask task = reparsed.getFlowElement("task", ServiceTask.class);
        MultiInstanceLoopCharacteristics loop = (MultiInstanceLoopCharacteristics) task.getLoopCharacteristics();

        assertThat(loop.isSequential()).isTrue();
        assertThat(loop.getCollection()).isEqualTo("items");
        assertThat(loop.getItem()).isEqualTo("item");
        assertThat(loop.getIndex()).isEqualTo("index");
    }

    @Test
    void messageCatchEventRoundTripPreservesTheMessageReference() {
        BpmnModel reparsed = parseStrict(
                write(
                        parseStrict(
                                catchEventFlow(
                                        "<bpmn:messageEventDefinition id=\"message_definition\" messageRef=\"message_order\"/>"))));
        IntermediateCatchEvent event = reparsed.getFlowElement("catch", IntermediateCatchEvent.class);

        assertThat(event.getMessageEventDefinition().getId()).isEqualTo("message_definition");
        assertThat(event.getMessageEventDefinition().getMessageRef()).isEqualTo("message_order");
        assertThat(event.getTimerEventDefinition()).isNull();
    }

    @Test
    void timerCatchEventRoundTripPreservesLiteralAndExpressionKinds() {
        IntermediateCatchEvent literal = parseStrict(
                write(
                        parseStrict(
                                catchEventFlow(
                                        "<bpmn:timerEventDefinition><bpmn:timeDuration>PT5M</bpmn:timeDuration>" + "</bpmn:timerEventDefinition>"))))
            .getFlowElement("catch", IntermediateCatchEvent.class);
        assertThat(literal.getTimerEventDefinition().getValue())
            .extracting(TimerValue::kind, TimerValue::value, TimerValue::expression)
            .containsExactly(TimerValue.Kind.DURATION, "PT5M", false);

        IntermediateCatchEvent expression = parseStrict(
                write(
                        parseStrict(
                                catchEventFlow(
                                        "<bpmn:timerEventDefinition><bpmn:timeDate xsi:type=\"bpmn:tFormalExpression\""
                                        + " language=\"java\">wakeAt</bpmn:timeDate></bpmn:timerEventDefinition>"))))
            .getFlowElement("catch", IntermediateCatchEvent.class);
        assertThat(expression.getTimerEventDefinition().getValue())
            .extracting(TimerValue::kind, TimerValue::value, TimerValue::expression)
            .containsExactly(TimerValue.Kind.DATE, "wakeAt", true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invocationPolicy", "effectPolicy"})
    void rejectsScriptPoliciesRepeatedAcrossExtensionContainers(String policy) {
        String extensions = "<bpmn:extensionElements><cf:" + policy + "/></bpmn:extensionElements>";
        String xml = scriptFlow().replace("<bpmn:script>", extensions + extensions + "<bpmn:script>");

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("at most one cf:" + policy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing:tFormalExpression", "cf:tFormalExpression",
            "tFormalExpression", "bpmn:extra:tFormalExpression"})
    void rejectsFormalExpressionTypeOutsideTheBpmnNamespace(String type) {
        String xml = standardLoopFlow().replace("bpmn:tFormalExpression", type);

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("xsi:type");
    }

    @ParameterizedTest
    @ValueSource(strings = {"expr", ""})
    void acceptsFormalExpressionTypeResolvedThroughALocalNamespaceBinding(String prefix) {
        String binding = """
            %s="http://www.omg.org/spec/BPMN/20100524/MODEL" xsi:type="%s"
            """
            .formatted(prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix,
                    prefix.isEmpty() ? "tFormalExpression" : prefix + ":tFormalExpression");
        String xml = standardLoopFlow().replace("xsi:type=\"bpmn:tFormalExpression\"", binding);

        assertThat(parseWithoutSchema(xml)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"language=\"javascript\" xsi:type=\"bpmn:tFormalExpression\"",
            "xsi:type=\"cf:tFormalExpression\"", "language=\"java\""})
    void rejectsUnsupportedExpressionMetadataOnLiteralTimers(String attributes) {
        String xml = catchEventFlow(
                "<bpmn:timerEventDefinition><bpmn:timeDuration " + attributes
                + ">PT5M</bpmn:timeDuration></bpmn:timerEventDefinition>");

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("BPMN timeDuration");
    }

    @ParameterizedTest
    @ValueSource(strings = {"implementation=\"\"", "implementation=\"   \"",
            "operationRef=\"\"", "operationRef=\"   \""})
    void rejectsDeclaredUnsupportedReceiveTaskAttributes(String attribute) {
        String xml =
                receiveTaskFlow()
            .replace("implementation=\"##WebService\" operationRef=\"operation_order\"", attribute);

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("implementation and operationRef are not supported");
    }

    @Test
    void parallelMultiInstanceRoundTripPreservesOrderedOutputAggregation() {
        BpmnModel reparsed = parseStrict(write(parseStrict(multiInstanceFlow(false))));
        MultiInstanceLoopCharacteristics loop = (MultiInstanceLoopCharacteristics) reparsed
            .getFlowElement("task", ServiceTask.class)
            .getLoopCharacteristics();

        assertThat(loop.isSequential()).isFalse();
        assertThat(loop.getTarget()).isEqualTo("results");
        assertThat(loop.getSource()).isEqualTo("result");
    }

    @Test
    void acceptsParallelMultiInstanceWithoutOutputAggregation() {
        String xml = multiInstanceFlow(false).replace(" cf:target=\"results\" cf:source=\"result\"", "");

        MultiInstanceLoopCharacteristics loop = (MultiInstanceLoopCharacteristics) parseWithoutSchema(xml)
            .getFlowElement("task", ServiceTask.class)
            .getLoopCharacteristics();

        assertThat(loop.isSequential()).isFalse();
        assertThat(loop.getTarget()).isNull();
        assertThat(loop.getSource()).isNull();
    }

    @Test
    void rejectsSharedMultiInstanceOutputVariable() {
        String xml = multiInstanceFlow(false).replace("cf:source=\"result\"", "cf:source=\"results\"");

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("must use different names");
    }

    @Test
    void rejectsIncompleteMultiInstanceContract() {
        String xml = multiInstanceFlow(true).replace(" cf:collection=\"items\"", "").replace(" cf:item=\"item\"", "");

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("requires cf:collection");
    }

    @Test
    void rejectsBlankOptionalMultiInstanceAttributes() {
        for (String attribute : List.of("index", "itemType", "target", "source")) {
            String xml =
                    multiInstanceFlow(false)
                .replaceFirst("cf:" + attribute + "=\"[^\"]*\"", "cf:" + attribute + "=\" \"");

            assertThatThrownBy(() -> parseWithoutSchema(xml))
                .as(attribute)
                .isInstanceOf(CompileFlowException.class)
                .hasMessageContaining("must not be blank when declared");
        }
    }

    @Test
    void rejectsInvalidMultiInstanceElementClassBeforeCodeGeneration() {
        String invalidItemType = """
            cf:itemType="java/lang/String"
            """.strip();
        String xml = multiInstanceFlow(true).replace("cf:itemType=\"java.lang.String\"", invalidItemType);

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("cf:itemType must be a valid Java class name");
    }

    @Test
    void rejectsUnboundedStandardLoopWithoutACondition() {
        String xml = standardLoopFlow()
            .replace(" testBefore=\"true\" loopMaximum=\"3\"", "")
            .replace("<bpmn:loopCondition xsi:type=\"bpmn:tFormalExpression\"" + " language=\"java\">"
                    + "attempts &lt; 3</bpmn:loopCondition>", "");

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("requires loopCondition or loopMaximum");
    }

    @Test
    void rejectsNonIntegerStandardLoopMaximumWithoutSchemaValidation() {
        String xml = standardLoopFlow().replace("loopMaximum=\"3\"", "loopMaximum=\"1e2\"");

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("loopMaximum' must be an integer");
    }

    @Test
    void preservesScriptWhitespaceExactly() {
        BpmnModel model = parseStrict(scriptFlow());
        ScriptTask first = model.getFlowElement("task", ScriptTask.class);

        BpmnModel reparsed = parseStrict(write(model));
        ScriptTask second = reparsed.getFlowElement("task", ScriptTask.class);

        assertThat(first.getScript()).isEqualTo("\n  first();\n    second();\n");
        assertThat(second.getScript()).isEqualTo(first.getScript());
    }

    @Test
    void preservesDefinitionLanguagesAndCanonicalizesConditionLanguage() {
        String xml = flow("Definitions_languages",
                "<bpmn:exclusiveGateway id=\"task\" default=\"flow-default\"/>" + "<bpmn:sequenceFlow id=\"flow-true\""
                + " sourceRef=\"task\" targetRef=\"end\">" + "<bpmn:conditionExpression"
                + " xsi:type=\"bpmn:tFormalExpression\"" + " language=\"java\">value > 0"
                + "</bpmn:conditionExpression>" + "</bpmn:sequenceFlow>" + "<bpmn:sequenceFlow id=\"flow-default\""
                + " sourceRef=\"task\" targetRef=\"end\"/>")
            .replace("targetNamespace=\"urn:compileflow:test\"",
                    "targetNamespace=\"urn:compileflow:test\"" + " typeLanguage=\"http://www.w3.org/2001/XMLSchema\""
                    + " expressionLanguage=\"urn:compileflow:java\"");

        BpmnModel reparsed = parseStrict(write(parseStrict(xml)));

        assertThat(reparsed.getTypeLanguage()).isEqualTo("http://www.w3.org/2001/XMLSchema");
        assertThat(reparsed.getExpressionLanguage()).isEqualTo("urn:compileflow:java");
        assertThat(write(reparsed)).contains("language=\"java\"");
    }

    @Test
    void rejectsUnsupportedConditionExpressionLanguages() {
        String xml = flow("Definitions_condition_language",
                "<bpmn:exclusiveGateway id=\"task\" default=\"flow-default\"/>" + "<bpmn:sequenceFlow id=\"flow-true\""
                + " sourceRef=\"task\" targetRef=\"end\">" + "<bpmn:conditionExpression"
                + " xsi:type=\"bpmn:tFormalExpression\"" + " language=\"javascript\">value > 0"
                + "</bpmn:conditionExpression>" + "</bpmn:sequenceFlow>" + "<bpmn:sequenceFlow id=\"flow-default\""
                + " sourceRef=\"task\" targetRef=\"end\"/>");

        assertThatThrownBy(() -> parseStrict(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("conditionExpression language must be 'java'");
    }

    @Test
    void rejectsLanguageAttributeWithoutFormalExpressionTypeWhenSchemaIsDisabled() {
        String xml = flow("Definitions_condition_type",
                "<bpmn:exclusiveGateway id=\"task\" default=\"flow-default\"/>" + "<bpmn:sequenceFlow id=\"flow-true\""
                + " sourceRef=\"task\" targetRef=\"end\">" + "<bpmn:conditionExpression language=\"java\">"
                + "value > 0</bpmn:conditionExpression>" + "</bpmn:sequenceFlow>"
                + "<bpmn:sequenceFlow id=\"flow-default\"" + " sourceRef=\"task\" targetRef=\"end\"/>");

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("language requires xsi:type=\"tFormalExpression\"");
    }

    @Test
    void rejectsConditionWithoutAnExplicitJavaLanguageContract() {
        String xml = flow("Definitions_condition_language",
                "<bpmn:exclusiveGateway id=\"task\" default=\"flow-default\"/>" + "<bpmn:sequenceFlow id=\"flow-true\""
                + " sourceRef=\"task\" targetRef=\"end\">" + "<bpmn:conditionExpression>value > 0"
                + "</bpmn:conditionExpression>" + "</bpmn:sequenceFlow>" + "<bpmn:sequenceFlow id=\"flow-default\""
                + " sourceRef=\"task\" targetRef=\"end\"/>");

        assertThatThrownBy(() -> parseStrict(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("must declare language=\"java\"");
    }

    @Test
    void inheritsCompileFlowJavaExpressionLanguageFromDefinitions() {
        String xml = flow("Definitions_condition_language",
                "<bpmn:exclusiveGateway id=\"task\" default=\"flow-default\"/>" + "<bpmn:sequenceFlow id=\"flow-true\""
                + " sourceRef=\"task\" targetRef=\"end\">" + "<bpmn:conditionExpression>value > 0"
                + "</bpmn:conditionExpression>" + "</bpmn:sequenceFlow>" + "<bpmn:sequenceFlow id=\"flow-default\""
                + " sourceRef=\"task\" targetRef=\"end\"/>")
            .replace("targetNamespace=\"urn:compileflow:test\"",
                    "targetNamespace=\"urn:compileflow:test\"" + " expressionLanguage=\"urn:compileflow:java\"");

        ExclusiveGateway gateway = parseStrict(xml).getFlowElement("task", ExclusiveGateway.class);

        assertThat(gateway.getOutgoingTransitions().get(0).getCondition()).isEqualTo("value > 0");
    }

    @Test
    void rejectsBalancedFormalExpressionWrapper() {
        String expression = "values.stream().anyMatch(value -> { return value.equals(\"}\"); })";
        String xml = flow("Definitions_balanced_wrapper",
                "<bpmn:exclusiveGateway id=\"task\" default=\"flow-default\"/>" + "<bpmn:sequenceFlow id=\"flow-true\""
                + " sourceRef=\"task\" targetRef=\"end\">" + "<bpmn:conditionExpression"
                + " xsi:type=\"bpmn:tFormalExpression\"" + " language=\"java\">${" + expression
                + "}</bpmn:conditionExpression>" + "</bpmn:sequenceFlow>" + "<bpmn:sequenceFlow id=\"flow-default\""
                + " sourceRef=\"task\" targetRef=\"end\"/>");

        assertThatThrownBy(() -> parseStrict(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("raw expression body; wrappers are not supported");
    }

    @Test
    void rejectsMultipleTemplateFragmentsInsteadOfCorruptingJavaSource() {
        String xml = flow("Definitions_fragmented_wrapper",
                "<bpmn:exclusiveGateway id=\"task\" default=\"flow-default\"/>" + "<bpmn:sequenceFlow id=\"flow-true\""
                + " sourceRef=\"task\" targetRef=\"end\">" + "<bpmn:conditionExpression"
                + " xsi:type=\"bpmn:tFormalExpression\"" + " language=\"java\">${left} &amp;&amp; ${right}"
                + "</bpmn:conditionExpression>" + "</bpmn:sequenceFlow>" + "<bpmn:sequenceFlow id=\"flow-default\""
                + " sourceRef=\"task\" targetRef=\"end\"/>");

        assertThatThrownBy(() -> parseStrict(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("raw expression body; wrappers are not supported");
    }

    @Test
    void rejectsReceiveTaskDispatchAttributesDuringParsing() {
        assertThatThrownBy(() -> parseStrict(receiveTaskFlow()))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("implementation and operationRef are not supported");
    }

    @Test
    void supportsAllXmlSchemaBooleanLexicalForms() {
        String xml = flow("Definitions_boolean", "<bpmn:serviceTask id=\"task\"/>")
            .replace("isExecutable=\"true\"", "isExecutable=\"1\"")
            .replace("id=\"start\"/>", "id=\"start\" isInterrupting=\"1\"/>");

        BpmnModel model = parseStrict(xml);

        assertThat(model.getProcess().getExecutable()).isTrue();
    }

    @Test
    void rejectsInvalidBooleanLexicalValuesWithoutSchemaValidation() {
        String xml = flow("Definitions_boolean", "<bpmn:serviceTask id=\"task\"/>")
            .replace("isExecutable=\"true\"", "isExecutable=\"yes\"");

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("BPMN attribute 'isExecutable' must be one of: true, false, 1, 0");
    }

    @Test
    void preservesScriptTaskLanguageSourceAndMappings() {
        BpmnModel reparsed = parseStrict(write(parseStrict(javaScriptActionFlow())));
        ScriptTask task = reparsed.getFlowElement("task", ScriptTask.class);

        assertThat(task.getScriptFormat()).isEqualTo("java");
        assertThat(task.getScript()).isEqualTo("return Instant.now().toString();");
        assertThat(task.getOutputMappings().get(0).getTarget()).isEqualTo("result");
    }

    @Test
    void preservesEffectfulScriptTaskSource() {
        BpmnModel reparsed = parseStrict(write(parseStrict(customScriptActionFlow())));
        ScriptTask task = reparsed.getFlowElement("task", ScriptTask.class);

        assertThat(task.getExecution()).isEqualTo(ActionExecution.EFFECT);
        assertThat(task.getEffectPolicy().getRecovery()).isEqualTo(EffectRecovery.MANUAL);
        assertThat(task.getScriptFormat()).isEqualTo("qlexpress");
        assertThat(task.getScript()).isEqualTo("input + 1");
    }

    @Test
    void effectRecoveryPolicyRoundTripRemainsSchemaValid() {
        BpmnModel reparsed = parseStrict(write(parseStrict(effectActionFlow())));
        Action action = reparsed.getFlowElement("task", ServiceTask.class).getAction();

        assertThat(action.getExecution()).isEqualTo(ActionExecution.EFFECT);
        assertThat(action.getEffectPolicy().getRecovery()).isEqualTo(EffectRecovery.RECONCILE);
        assertThat(action.getEffectPolicy().getMaxAttempts()).isEqualTo(3);
        assertThat(action.getEffectPolicy().getMaxReconcileAttempts()).isEqualTo(5);
        assertThat(action.getEffectPolicy().getReconcileAction().getType()).isEqualTo(ActionType.SPRING_BEAN);
    }

    @Test
    void rejectsReconcileOutputWithoutSchemaValidation() {
        String xml = effectActionFlow()
            .replace(" class=\"java.lang.Runnable\" method=\"run\"/>",
                    " class=\"java.lang.Runnable\" method=\"run\">"
                    + "<cf:output target=\"result\" dataType=\"java.lang.String\"/>" + "</cf:reconcileAction>");

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Unsupported cf:output child for BPMN reconcile action");
    }

    @Test
    void rejectsReconcileInputDefaultsWithoutSchemaValidation() {
        String xml = effectActionFlow()
            .replace(" class=\"java.lang.Runnable\" method=\"run\"/>",
                    " class=\"java.lang.Runnable\" method=\"run\">"
                    + "<cf:input source=\"orderId\" target=\"requestId\" dataType=\"java.lang.String\""
                    + " defaultValue=\"fallback\"/>" + "</cf:reconcileAction>");

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Unsupported attribute on cf:input: defaultValue");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT1S", "PT60S", "PT1.001S", "PT1.000S", "P0DT1S", "P1D"})
    void extensionSchemaAcceptsTheProtocolDurationLanguage(String duration) {
        assertThat(parseStrict(invocationDurationFlow(duration))).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"P", "PT", "PT+1S", "PT-1S", "-PT1S", "P1Y", "P1M", "P1W", "pt1s",
            "PT1.0000000000S", "PT0.000000001S"})
    void extensionSchemaRejectsValuesOutsideTheProtocolDurationLanguage(String duration) {
        assertThatThrownBy(() -> parseStrict(invocationDurationFlow(duration)))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Flow schema validation failed");
    }

    @Test
    void extensionSchemaRejectsNonCanonicalIdentitiesAndInfiniteBackoff() {
        String paddedClass = flow("Definitions_padded_identity",
                "<bpmn:serviceTask id=\"task\"><bpmn:extensionElements>"
                + "<cf:action type=\"java\" class=\" java.lang.Runnable \" method=\"run\"/>"
                + "</bpmn:extensionElements></bpmn:serviceTask>");
        String infiniteBackoff = flow("Definitions_infinite_backoff",
                "<bpmn:serviceTask id=\"task\"><bpmn:extensionElements>"
                + "<cf:action type=\"java\" class=\"java.lang.Runnable\" method=\"run\">"
                + "<cf:invocationPolicy backoffMultiplier=\"INF\"/>"
                + "</cf:action></bpmn:extensionElements></bpmn:serviceTask>");
        String unicodePaddedClass = flow("Definitions_unicode_padded_identity",
                "<bpmn:serviceTask id=\"task\"><bpmn:extensionElements>"
                + "<cf:action type=\"java\" class=\"\u00a0java.lang.Runnable\" method=\"run\"/>"
                + "</bpmn:extensionElements></bpmn:serviceTask>");
        String unicodeFormatClass = flow("Definitions_unicode_format_identity",
                "<bpmn:serviceTask id=\"task\"><bpmn:extensionElements>"
                + "<cf:action type=\"java\" class=\"java.lang.\u202eRunnable\" method=\"run\"/>"
                + "</bpmn:extensionElements></bpmn:serviceTask>");

        assertThatThrownBy(() -> parseStrict(paddedClass))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Flow schema validation failed");
        assertThatThrownBy(() -> parseStrict(unicodeFormatClass))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Flow schema validation failed");
        assertThatThrownBy(() -> parseStrict(infiniteBackoff))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Flow schema validation failed");
        assertThatThrownBy(() -> parseStrict(unicodePaddedClass))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Flow schema validation failed");
    }

    @Test
    void rejectsActionsOnPureGateways() {
        assertThatThrownBy(() -> parseStrict(gatewayActionFlow()))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Unsupported cf:action extension on ExclusiveGateway");
    }

    @Test
    void rejectsDuplicateActions() {
        String duplicateActions = flow("Definitions_duplicate_action",
                "<bpmn:serviceTask id=\"task\"><bpmn:extensionElements>"
                + "<cf:action type=\"java\" class=\"java.lang.Runnable\"/>"
                + "<cf:action type=\"java\" class=\"java.lang.Runnable\"/>"
                + "</bpmn:extensionElements></bpmn:serviceTask>");

        assertThatThrownBy(() -> parseStrict(duplicateActions))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("at most one cf:action");
    }

    @Test
    void rejectsUnknownActionAttributes() {
        String unknownAttribute = flow("Definitions_unknown_action_attribute",
                "<bpmn:serviceTask id=\"task\"><bpmn:extensionElements>"
                + "<cf:action type=\"java\" class=\"java.lang.Runnable\" vendorOption=\"ignored\"/>"
                + "</bpmn:extensionElements></bpmn:serviceTask>");

        assertThatThrownBy(() -> parseStrict(unknownAttribute))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Flow schema validation failed")
            .hasMessageContaining("vendorOption");
    }

    @Test
    void rejectsUnknownExtensionElementsInsteadOfDroppingThem() {
        String xml = flow("Definitions_extension",
                "<bpmn:serviceTask id=\"task\"><bpmn:extensionElements>" + "<vendor:custom/>"
                + "</bpmn:extensionElements></bpmn:serviceTask>")
            .replace("xmlns:cf=\"http://www.compileflow.org\"",
                    "xmlns:cf=\"http://www.compileflow.org\"" + " xmlns:vendor=\"urn:vendor:test\"");

        assertThatThrownBy(() -> parseStrict(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Unsupported BPMN extension namespace");
    }

    @Test
    void rejectsUnsupportedStandardAttributesInsteadOfDroppingThem() {
        String xml =
                flow("Definitions_unsupported_attribute",
                        "<bpmn:serviceTask id=\"task\" implementation=\"##WebService\"/>");

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Unsupported BPMN attribute 'implementation' on serviceTask");
    }

    @Test
    void rejectsInvalidGatewayDirectionValues() {
        String xml = flow("Definitions_invalid_gateway_direction",
                "<bpmn:parallelGateway id=\"task\" gatewayDirection=\"Fork\"/>");

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("gatewayDirection")
            .hasMessageContaining("Unspecified, Converging, Diverging, Mixed");
    }

    @Test
    void rejectsForeignAttributesInsteadOfTreatingThemAsBpmn() {
        String serviceTask = "<bpmn:serviceTask id=\"task\" vendor:mode=\"external\"/>";
        String xml = flow("Definitions_foreign_attribute", serviceTask)
            .replace("xmlns:cf=\"http://www.compileflow.org\"",
                    "xmlns:cf=\"http://www.compileflow.org\" xmlns:vendor=\"urn:vendor:test\"");

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Unsupported BPMN attribute '{urn:vendor:test}mode'");
    }

    @Test
    void rejectsForeignElementsWithSupportedLocalNames() {
        String xml = flow("Definitions_foreign_element", "<vendor:serviceTask id=\"task\"/>")
            .replace("xmlns:cf=\"http://www.compileflow.org\"",
                    "xmlns:cf=\"http://www.compileflow.org\"" + " xmlns:vendor=\"urn:vendor:test\"");

        assertThatThrownBy(() -> parseWithoutSchema(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Unsupported namespace for BPMN element serviceTask");
    }

    @Test
    void rejectsVariablesOnNodesThatDoNotConsumeThem() {
        String xml = flow("Definitions_unsupported_node_var",
                "<bpmn:receiveTask id=\"task\"><bpmn:extensionElements>" + "<cf:var name=\"ignored\""
                + " dataType=\"java.lang.String\"" + " inOutType=\"param\"/>"
                + "</bpmn:extensionElements></bpmn:receiveTask>");

        assertThatThrownBy(() -> parseStrict(xml))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Unsupported cf:var extension on ReceiveTask");
    }

    @Test
    void acceptsLoopCharacteristicsOnReceiveTasks() {
        String xml = flow("Definitions_receive_loop",
                "<bpmn:receiveTask id=\"task\">" + "<bpmn:standardLoopCharacteristics loopMaximum=\"2\"/>" + "</bpmn:receiveTask>");

        BpmnModel model = parseWithoutSchema(xml);

        assertThat(model.getFlowElement("task", ReceiveTask.class).getLoopCharacteristics()).isNotNull();
    }

    @Test
    void rejectsEventSubprocesses() {
        assertThatThrownBy(() -> parseWithoutSchema(eventSubprocessFlow()))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Event subprocesses are not supported");
    }

    @Test
    void resolvesElementsInsideNestedSubprocesses() {
        BpmnModel model = parseStrict(nestedSubprocessFlow());

        assertThat(model.getFlowElement("nested_task", ServiceTask.class)).isNotNull();
        assertThat(model.getFlowElement("nested_end")).isNotNull();
    }

    @Test
    void acceptsStandardDocumentationAndBpmnDiagramMetadata() {
        String xml = flow("Definitions_metadata",
                "<bpmn:serviceTask id=\"task\">" + "<bpmn:documentation>Invoke the service</bpmn:documentation>"
                + "</bpmn:serviceTask>")
            .replace("xmlns:cf=\"http://www.compileflow.org\"",
                    "xmlns:cf=\"http://www.compileflow.org\""
                    + " xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\""
                    + " xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\""
                    + " xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\"")
            .replace("</bpmn:definitions>",
                    "<bpmndi:BPMNDiagram id=\"diagram\">" + "<bpmndi:BPMNPlane id=\"plane\""
                    + " bpmnElement=\"semantic-contract\">" + "<bpmndi:BPMNShape id=\"shape\" bpmnElement=\"task\">"
                    + "<dc:Bounds x=\"100\" y=\"100\"" + " width=\"100\" height=\"80\"/>" + "</bpmndi:BPMNShape>"
                    + "<bpmndi:BPMNEdge id=\"edge\" bpmnElement=\"to_task\">" + "<di:waypoint x=\"136\" y=\"118\"/>"
                    + "<di:waypoint x=\"200\" y=\"140\"/>" + "</bpmndi:BPMNEdge>" + "</bpmndi:BPMNPlane>"
                    + "</bpmndi:BPMNDiagram>" + "</bpmn:definitions>");

        BpmnModel model = parseStrict(xml);

        assertThat(model.getFlowElement("task", ServiceTask.class)).isNotNull();
    }

    @Test
    void writerRejectsUnknownModelElements() {
        com.alibaba.compileflow.engine.bpmn.model.Process process =
                new com.alibaba.compileflow.engine.bpmn.model.Process();
        process.setId("unknown");
        UnsupportedFlowElement unsupported = new UnsupportedFlowElement();
        unsupported.setId("unsupported");
        process.addElement(unsupported);
        BpmnModel model = new BpmnModel(process);

        assertThatThrownBy(() -> BpmnXmlWriter.getInstance().write(model))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Cannot serialize unsupported BPMN flow element");
    }

    @Test
    void parserRejectsUnsupportedExecutableAttributeValues() {
        assertThatThrownBy(() -> parseWithoutSchema(flow("Definitions_non_interrupting",
                "<bpmn:serviceTask id=\"task\"/>")
            .replace("id=\"start\"/>", "id=\"start\" isInterrupting=\"false\"/>")))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Non-interrupting start events");

        assertThatThrownBy(() -> parseWithoutSchema(flow("Definitions_non_immediate",
                "<bpmn:serviceTask " + "id=\"task\"/>")
            .replace("id=\"to_task\"", "id=\"to_task\" isImmediate=\"false\"")))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("isImmediate=\"false\"");
    }

    @Test
    void writerOmitsSupportedBpmnDefaultAttributes() {
        BpmnModel model = parseWithoutSchema(flow("Definitions_defaults",
                "<bpmn:subProcess id=\"task\" triggeredByEvent=\"false\"/>")
            .replace("id=\"start\"/>", "id=\"start\" isInterrupting=\"true\"/>")
            .replace("id=\"to_task\"", "id=\"to_task\" isImmediate=\"true\""));

        assertThat(write(model))
            .doesNotContain("isInterrupting=")
            .doesNotContain("triggeredByEvent=")
            .doesNotContain("isImmediate=");
    }

    private static final class UnsupportedFlowElement extends AbstractFlowElement {
    }
}
