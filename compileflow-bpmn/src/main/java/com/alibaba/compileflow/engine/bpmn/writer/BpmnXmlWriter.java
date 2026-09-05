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
package com.alibaba.compileflow.engine.bpmn.writer;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.bpmn.validation.BpmnLoopContract;
import com.alibaba.compileflow.engine.bpmn.model.Activity;
import com.alibaba.compileflow.engine.bpmn.model.BpmnModel;
import com.alibaba.compileflow.engine.bpmn.model.BpmnModelConstants;
import com.alibaba.compileflow.engine.bpmn.model.CallActivity;
import com.alibaba.compileflow.engine.bpmn.model.EndEvent;
import com.alibaba.compileflow.engine.bpmn.model.ExclusiveGateway;
import com.alibaba.compileflow.engine.bpmn.model.FlowNode;
import com.alibaba.compileflow.engine.bpmn.model.Gateway;
import com.alibaba.compileflow.engine.bpmn.model.GatewayDirection;
import com.alibaba.compileflow.engine.bpmn.model.InclusiveGateway;
import com.alibaba.compileflow.engine.bpmn.model.IntermediateCatchEvent;
import com.alibaba.compileflow.engine.bpmn.model.LoopCharacteristics;
import com.alibaba.compileflow.engine.bpmn.model.Message;
import com.alibaba.compileflow.engine.bpmn.model.MessageEventDefinition;
import com.alibaba.compileflow.engine.bpmn.model.MultiInstanceLoopCharacteristics;
import com.alibaba.compileflow.engine.bpmn.model.ParallelGateway;
import com.alibaba.compileflow.engine.bpmn.model.Process;
import com.alibaba.compileflow.engine.bpmn.model.ReceiveTask;
import com.alibaba.compileflow.engine.bpmn.model.ScriptTask;
import com.alibaba.compileflow.engine.bpmn.model.SequenceFlow;
import com.alibaba.compileflow.engine.bpmn.model.ServiceTask;
import com.alibaba.compileflow.engine.bpmn.model.StandardLoopCharacteristics;
import com.alibaba.compileflow.engine.bpmn.model.StartEvent;
import com.alibaba.compileflow.engine.bpmn.model.SubProcess;
import com.alibaba.compileflow.engine.bpmn.model.TimerEventDefinition;
import com.alibaba.compileflow.engine.bpmn.model.TimerValue;
import com.alibaba.compileflow.engine.core.xml.writer.AbstractFlowStreamWriter;
import com.alibaba.compileflow.engine.core.model.AbstractFlowElement;
import com.alibaba.compileflow.engine.core.model.mapping.InputMapping;
import com.alibaba.compileflow.engine.core.model.mapping.MappingModel;
import com.alibaba.compileflow.engine.core.model.mapping.OutputMapping;
import com.alibaba.compileflow.engine.core.model.action.Action;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.model.action.ActionType;
import com.alibaba.compileflow.engine.core.model.action.InvocationPolicy;
import com.alibaba.compileflow.engine.core.model.action.EffectPolicy;
import com.alibaba.compileflow.engine.core.model.action.HasAction;
import com.alibaba.compileflow.engine.core.model.action.EffectiveEffectPolicy;
import com.alibaba.compileflow.engine.core.model.action.ReconcileAction;
import com.alibaba.compileflow.engine.core.model.action.ReconcileInput;
import com.alibaba.compileflow.engine.core.model.extension.AbstractExtensionElement;
import com.alibaba.compileflow.engine.core.model.variable.VariableContainer;
import com.alibaba.compileflow.engine.core.model.variable.Variable;
import java.util.List;
import javax.xml.stream.XMLStreamWriter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * XML stream writer for BPMN flow models.
 *
 * @author yusu
 */
public class BpmnXmlWriter extends AbstractFlowStreamWriter<BpmnModel> {
    private static final String DEFAULT_TARGET_NAMESPACE = "urn:compileflow:bpmn";

    public static BpmnXmlWriter getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    protected void doWrite(BpmnModel flowModel, XMLStreamWriter xsw) throws Exception {
        xsw.writeStartDocument("UTF-8", "1.0");
        writeDefinitions(xsw, flowModel);
        xsw.writeEndDocument();
    }

    private void writeDefinitions(XMLStreamWriter xsw, BpmnModel model) throws Exception {
        xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_DEFINITIONS);

        xsw.writeNamespace("", BpmnModelConstants.BPMN20_NS);
        xsw.writeNamespace("bpmndi", BpmnModelConstants.BPMNDI_NS);
        xsw.writeNamespace("dc", BpmnModelConstants.DC_NS);
        xsw.writeNamespace("di", BpmnModelConstants.DI_NS);
        xsw.writeNamespace("xsi", BpmnModelConstants.XSI_NS);
        xsw.writeNamespace(BpmnModelConstants.CF_PREFIX, BpmnModelConstants.CF_NS);

        Process process = model.getProcess();
        String definitionsId = StringUtils.defaultIfBlank(model.getDefinitionsId(), "Definitions_" + process.getId());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_ID, definitionsId);
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_TARGET_NAMESPACE,
                StringUtils.defaultIfBlank(model.getTargetNamespace(), DEFAULT_TARGET_NAMESPACE));
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_TYPE_LANGUAGE, model.getTypeLanguage());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_EXPRESSION_LANGUAGE, model.getExpressionLanguage());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_EXPORTER, "CompileFlow");

        List<Message> messages = model.getMessages();
        if (CollectionUtils.isNotEmpty(messages)) {
            for (Message message : messages) {
                writeMessage(xsw, message);
            }
        }

        writeProcess(xsw, process);

        xsw.writeEndElement();
    }

    private void writeProcess(XMLStreamWriter xsw, Process process) throws Exception {
        xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_PROCESS);

        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_ID, process.getId());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_NAME, process.getName());
        if (process.getExecutable() != null) {
            writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_IS_EXECUTABLE, process.getExecutable().toString());
        }

        writeExtensionElementsIfPresent(xsw, process);

        List<AbstractFlowElement> flowElements = process.getFlowElements();
        if (CollectionUtils.isNotEmpty(flowElements)) {
            for (AbstractFlowElement element : flowElements) {
                writeFlowElement(xsw, element);
            }
        }

        xsw.writeEndElement();
    }

    private void writeFlowElement(XMLStreamWriter xsw, AbstractFlowElement element) throws Exception {
        if (element instanceof StartEvent) {
            writeStartEvent(xsw, (StartEvent) element);
        } else if (element instanceof EndEvent) {
            writeEndEvent(xsw, (EndEvent) element);
        } else if (element instanceof ServiceTask) {
            writeServiceTask(xsw, (ServiceTask) element);
        } else if (element instanceof ScriptTask) {
            writeScriptTask(xsw, (ScriptTask) element);
        } else if (element instanceof ReceiveTask) {
            writeReceiveTask(xsw, (ReceiveTask) element);
        } else if (element instanceof IntermediateCatchEvent) {
            writeIntermediateCatchEvent(xsw, (IntermediateCatchEvent) element);
        } else if (element instanceof SubProcess) {
            writeSubProcess(xsw, (SubProcess) element);
        } else if (element instanceof CallActivity) {
            writeCallActivity(xsw, (CallActivity) element);
        } else if (element instanceof ExclusiveGateway) {
            writeExclusiveGateway(xsw, (ExclusiveGateway) element);
        } else if (element instanceof ParallelGateway) {
            writeParallelGateway(xsw, (ParallelGateway) element);
        } else if (element instanceof InclusiveGateway) {
            writeInclusiveGateway(xsw, (InclusiveGateway) element);
        } else if (element instanceof SequenceFlow) {
            writeSequenceFlow(xsw, (SequenceFlow) element);
        } else {
            String elementType = element == null ? "null" : element.getClass().getName();
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "Cannot serialize unsupported BPMN flow element: " + elementType, null);
        }
    }

    private void writeStartEvent(XMLStreamWriter xsw, StartEvent startEvent) throws Exception {
        xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_START_EVENT);
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_ID, startEvent.getId());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_NAME, startEvent.getName());

        writeOutgoingFlows(xsw, startEvent);
        // startEvent
        xsw.writeEndElement();
    }

    private void writeEndEvent(XMLStreamWriter xsw, EndEvent endEvent) throws Exception {
        xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_END_EVENT);
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_ID, endEvent.getId());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_NAME, endEvent.getName());

        writeIncomingFlows(xsw, endEvent);
        // endEvent
        xsw.writeEndElement();
    }

    private void writeServiceTask(XMLStreamWriter xsw, ServiceTask serviceTask) throws Exception {
        xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_SERVICE_TASK);
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_ID, serviceTask.getId());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_NAME, serviceTask.getName());

        writeExtensionElementsIfPresent(xsw, serviceTask);
        writeIncomingFlows(xsw, serviceTask);
        writeOutgoingFlows(xsw, serviceTask);
        writeLoopCharacteristics(xsw, serviceTask);
        // serviceTask
        xsw.writeEndElement();
    }

    private void writeScriptTask(XMLStreamWriter xsw, ScriptTask scriptTask) throws Exception {
        xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_SCRIPT_TASK);
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_ID, scriptTask.getId());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_NAME, scriptTask.getName());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_SCRIPT_FORMAT, scriptTask.getScriptFormat());
        if (scriptTask.getExecution() != ActionExecution.REPLAYABLE) {
            writeCfAttribute(xsw, BpmnModelConstants.CF_ATTRIBUTE_EXECUTION, scriptTask.getExecution().getValue());
        }
        writeExtensionElementsIfPresent(xsw, scriptTask);
        writeIncomingFlows(xsw, scriptTask);
        writeOutgoingFlows(xsw, scriptTask);
        writeLoopCharacteristics(xsw, scriptTask);

        if (scriptTask.getScript() != null) {
            xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_SCRIPT);
            xsw.writeCharacters(scriptTask.getScript());
            // script
            xsw.writeEndElement();
        }
        // scriptTask
        xsw.writeEndElement();
    }

    private void writeReceiveTask(XMLStreamWriter xsw, ReceiveTask receiveTask) throws Exception {
        xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_RECEIVE_TASK);
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_ID, receiveTask.getId());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_NAME, receiveTask.getName());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_MESSAGE_REF, receiveTask.getMessageRef());

        writeExtensionElementsIfPresent(xsw, receiveTask);
        writeIncomingFlows(xsw, receiveTask);
        writeOutgoingFlows(xsw, receiveTask);
        // receiveTask
        xsw.writeEndElement();
    }

    private void writeIntermediateCatchEvent(XMLStreamWriter xsw, IntermediateCatchEvent event) throws Exception {
        xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_INTERMEDIATE_CATCH_EVENT);
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_ID, event.getId());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_NAME, event.getName());
        writeIncomingFlows(xsw, event);
        writeOutgoingFlows(xsw, event);
        if (event.getMessageEventDefinition() != null) {
            writeMessageEventDefinition(xsw, event.getMessageEventDefinition());
        } else if (event.getTimerEventDefinition() != null) {
            writeTimerEventDefinition(xsw, event.getTimerEventDefinition());
        } else {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "Cannot serialize intermediateCatchEvent without a supported event definition", null);
        }
        // intermediateCatchEvent
        xsw.writeEndElement();
    }

    private void writeMessageEventDefinition(XMLStreamWriter xsw, MessageEventDefinition definition) throws Exception {
        xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_MESSAGE_EVENT_DEFINITION);
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_ID, definition.getId());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_MESSAGE_REF, definition.getMessageRef());
        xsw.writeEndElement();
    }

    private void writeTimerEventDefinition(XMLStreamWriter xsw, TimerEventDefinition definition) throws Exception {
        if (definition.getValue() == null) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "Cannot serialize timerEventDefinition without a timer value", null);
        }
        xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_TIMER_EVENT_DEFINITION);
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_ID, definition.getId());
        TimerValue timer = definition.getValue();
        String elementName = switch (timer.kind()) {
            case DURATION -> BpmnModelConstants.BPMN_ELEMENT_TIME_DURATION;
            case DATE -> BpmnModelConstants.BPMN_ELEMENT_TIME_DATE;
            case CYCLE -> BpmnModelConstants.BPMN_ELEMENT_TIME_CYCLE;
        };
        xsw.writeStartElement(elementName);
        if (timer.expression()) {
            writeFormalExpressionType(xsw);
            writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_LANGUAGE, "java");
            xsw.writeCharacters(timer.value());
        } else {
            xsw.writeCharacters(timer.value());
        }
        xsw.writeEndElement();
        // timerEventDefinition
        xsw.writeEndElement();
    }

    private void writeSubProcess(XMLStreamWriter xsw, SubProcess subProcess) throws Exception {
        xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_SUB_PROCESS);
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_ID, subProcess.getId());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_NAME, subProcess.getName());

        writeExtensionElementsIfPresent(xsw, subProcess);
        writeIncomingFlows(xsw, subProcess);
        writeOutgoingFlows(xsw, subProcess);
        writeLoopCharacteristics(xsw, subProcess);
        // SubProcess can contain nested elements; write them recursively.
        List<AbstractFlowElement> childElements = subProcess.getAllElements();
        if (CollectionUtils.isNotEmpty(childElements)) {
            for (AbstractFlowElement childElement : childElements) {
                writeFlowElement(xsw, childElement);
            }
        }
        // subProcess
        xsw.writeEndElement();
    }

    private void writeCallActivity(XMLStreamWriter xsw, CallActivity callActivity) throws Exception {
        xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_CALL_ACTIVITY);
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_ID, callActivity.getId());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_NAME, callActivity.getName());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_CALLED_ELEMENT, callActivity.getCalledElement());
        writeCfAttribute(xsw, BpmnModelConstants.CF_ATTRIBUTE_CLASSPATH, callActivity.getClasspath());
        writeCfAttribute(xsw, BpmnModelConstants.CF_ATTRIBUTE_VERSION, callActivity.getVersion());

        writeExtensionElementsIfPresent(xsw, callActivity);
        writeIncomingFlows(xsw, callActivity);
        writeOutgoingFlows(xsw, callActivity);
        writeLoopCharacteristics(xsw, callActivity);
        // callActivity
        xsw.writeEndElement();
    }

    private void writeCfAttribute(XMLStreamWriter writer, String name, String value) throws Exception {
        if (StringUtils.isNotBlank(value)) {
            writer.writeAttribute(BpmnModelConstants.CF_PREFIX, BpmnModelConstants.CF_NS, name, value);
        }
    }

    private void writeExclusiveGateway(XMLStreamWriter xsw, ExclusiveGateway gateway) throws Exception {
        xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_EXCLUSIVE_GATEWAY);
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_ID, gateway.getId());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_NAME, gateway.getName());
        writeGatewayDirection(xsw, gateway);
        SequenceFlow defaultTransition = gateway.getDefaultTransition();
        if (defaultTransition != null) {
            writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_DEFAULT, defaultTransition.getId());
        }

        writeExtensionElementsIfPresent(xsw, gateway);
        writeIncomingFlows(xsw, gateway);
        writeOutgoingFlows(xsw, gateway);
        // exclusiveGateway
        xsw.writeEndElement();
    }

    private void writeParallelGateway(XMLStreamWriter xsw, ParallelGateway gateway) throws Exception {
        xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_PARALLEL_GATEWAY);
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_ID, gateway.getId());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_NAME, gateway.getName());
        writeGatewayDirection(xsw, gateway);

        writeExtensionElementsIfPresent(xsw, gateway);
        writeIncomingFlows(xsw, gateway);
        writeOutgoingFlows(xsw, gateway);
        // parallelGateway
        xsw.writeEndElement();
    }

    private void writeInclusiveGateway(XMLStreamWriter xsw, InclusiveGateway gateway) throws Exception {
        xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_INCLUSIVE_GATEWAY);
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_ID, gateway.getId());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_NAME, gateway.getName());
        writeGatewayDirection(xsw, gateway);
        SequenceFlow defaultTransition = gateway.getDefaultTransition();
        if (defaultTransition != null) {
            writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_DEFAULT, defaultTransition.getId());
        }

        writeExtensionElementsIfPresent(xsw, gateway);
        writeIncomingFlows(xsw, gateway);
        writeOutgoingFlows(xsw, gateway);
        // inclusiveGateway
        xsw.writeEndElement();
    }

    private void writeGatewayDirection(XMLStreamWriter writer, Gateway gateway) throws Exception {
        if (gateway.getGatewayDirection() != GatewayDirection.UNSPECIFIED) {
            writeAttribute(writer, BpmnModelConstants.BPMN_ATTRIBUTE_GATEWAY_DIRECTION,
                    gateway.getGatewayDirection().getXmlValue());
        }
    }

    private void writeSequenceFlow(XMLStreamWriter xsw, SequenceFlow sequenceFlow) throws Exception {
        xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_SEQUENCE_FLOW);
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_ID, sequenceFlow.getId());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_NAME, sequenceFlow.getName());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_SOURCE_REF, sequenceFlow.getSource());
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_TARGET_REF, sequenceFlow.getTarget());
        if (sequenceFlow.getCondition() != null) {
            xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_CONDITION_EXPRESSION);
            writeFormalExpressionType(xsw);
            writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_LANGUAGE, "java");
            xsw.writeCharacters(sequenceFlow.getCondition());
            // conditionExpression
            xsw.writeEndElement();
        }
        // sequenceFlow
        xsw.writeEndElement();
    }

    private void writeMessage(XMLStreamWriter writer, Message message) throws Exception {
        writer.writeCharacters("  ");
        writer.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_MESSAGE);
        writeAttribute(writer, BpmnModelConstants.BPMN_ATTRIBUTE_ID, message.getId());
        writeAttribute(writer, BpmnModelConstants.BPMN_ATTRIBUTE_NAME, message.getName());
        // message
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private void writeIncomingFlows(XMLStreamWriter writer, FlowNode node) throws Exception {
        List<SequenceFlow> incomingTransitions = node.getIncomingTransitions();
        if (incomingTransitions != null && !incomingTransitions.isEmpty()) {
            for (SequenceFlow transition : incomingTransitions) {
                writer.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_INCOMING);
                writer.writeCharacters(transition.getId());
                // incoming
                writer.writeEndElement();
            }
        }
    }

    private void writeOutgoingFlows(XMLStreamWriter writer, FlowNode node) throws Exception {
        List<SequenceFlow> outgoingTransitions = node.getOutgoingTransitions();
        if (outgoingTransitions != null && !outgoingTransitions.isEmpty()) {
            for (SequenceFlow transition : outgoingTransitions) {
                writer.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_OUTGOING);
                writer.writeCharacters(transition.getId());
                // outgoing
                writer.writeEndElement();
            }
        }
    }

    private void writeExtensionElementsIfPresent(XMLStreamWriter xsw, AbstractExtensionElement element) throws Exception {
        boolean hasExtensions = false;

        if (element instanceof VariableContainer
                && CollectionUtils.isNotEmpty(((VariableContainer) element).getVariables())) {
            hasExtensions = true;
        }
        if (element instanceof MappingModel mappings
                && (!mappings.getInputMappings().isEmpty() || !mappings.getOutputMappings().isEmpty())) {
            hasExtensions = true;
        }
        if (element instanceof HasAction && ((HasAction) element).getAction() != null) {
            hasExtensions = true;
        }
        if (element instanceof ScriptTask scriptTask
                && (scriptTask.getInvocationPolicy() != null || scriptTask.getEffectPolicy() != null)) {
            hasExtensions = true;
        }

        if (!hasExtensions) {
            return;
        }

        xsw.writeStartElement(BpmnModelConstants.BPMN_ELEMENT_EXTENSION_ELEMENTS);

        if (element instanceof VariableContainer) {
            writeExtensionVars(xsw, ((VariableContainer) element).getVariables());
        }
        if (element instanceof MappingModel mappings) {
            writeMappings(xsw, mappings.getInputMappings(), mappings.getOutputMappings(),
                    !(element instanceof CallActivity));
        }

        if (element instanceof HasAction) {
            writeExtensionAction(xsw, ((HasAction) element).getAction());
        }
        if (element instanceof ScriptTask scriptTask) {
            writeInvocationPolicyElement(xsw, scriptTask.getInvocationPolicy());
            writeEffectPolicy(xsw, scriptTask.getEffectPolicy());
        }
        // extensionElements
        xsw.writeEndElement();
    }

    private void writeExtensionVars(XMLStreamWriter xsw, List<Variable> vars) throws Exception {
        if (CollectionUtils.isEmpty(vars)) {
            return;
        }
        for (Variable var : vars) {
            xsw.writeStartElement(BpmnModelConstants.CF_NS, BpmnModelConstants.BPMN_EXT_ATTRIBUTE_VAR);
            writeAttribute(xsw, "name", var.getName());
            writeAttribute(xsw, "description", var.getDescription());
            writeAttribute(xsw, "dataType", var.getDataType());
            writeAttribute(xsw, "defaultValue", var.getDefaultValue());
            writeAttribute(xsw, "inOutType", var.getInOutType());
            xsw.writeEndElement();
        }
    }

    /**
     * Write cf:invocationPolicy as a child element inside extensionElements.
     */
    private void writeInvocationPolicyElement(XMLStreamWriter xsw, InvocationPolicy invocationPolicy) throws Exception {
        if (invocationPolicy == null) {
            return;
        }
        xsw.writeStartElement(BpmnModelConstants.CF_NS, BpmnModelConstants.BPMN_EXT_ELEMENT_INVOCATION_POLICY);
        writeAttribute(xsw, BpmnModelConstants.BPMN_EXT_ATTRIBUTE_INVOCATION_POLICY_TIMEOUT,
                invocationPolicy.getTimeout());
        writeAttribute(xsw, BpmnModelConstants.BPMN_EXT_ATTRIBUTE_INVOCATION_POLICY_ATTEMPT_TIMEOUT,
                invocationPolicy.getAttemptTimeout());
        if (invocationPolicy.getMaxAttempts() != null) {
            writeAttribute(xsw, BpmnModelConstants.BPMN_EXT_ATTRIBUTE_INVOCATION_POLICY_MAX_ATTEMPTS,
                    String.valueOf(invocationPolicy.getMaxAttempts()));
        }
        writeAttribute(xsw, BpmnModelConstants.BPMN_EXT_ATTRIBUTE_INVOCATION_POLICY_INITIAL_BACKOFF,
                invocationPolicy.getInitialBackoff());
        if (invocationPolicy.getBackoffMultiplier() != null) {
            writeAttribute(xsw, BpmnModelConstants.BPMN_EXT_ATTRIBUTE_INVOCATION_POLICY_BACKOFF_MULTIPLIER,
                    String.valueOf(invocationPolicy.getBackoffMultiplier()));
        }
        writeAttribute(xsw, BpmnModelConstants.BPMN_EXT_ATTRIBUTE_INVOCATION_POLICY_MAX_BACKOFF,
                invocationPolicy.getMaxBackoff());
        if (invocationPolicy.getJitter() != null) {
            writeAttribute(xsw, BpmnModelConstants.BPMN_EXT_ATTRIBUTE_INVOCATION_POLICY_JITTER,
                    invocationPolicy.getJitter().getValue());
        }
        writeAttribute(xsw, BpmnModelConstants.BPMN_EXT_ATTRIBUTE_INVOCATION_POLICY_RETRY_ON,
                invocationPolicy.getRetryOn());
        writeAttribute(xsw, BpmnModelConstants.BPMN_EXT_ATTRIBUTE_INVOCATION_POLICY_ON_FAILURE,
                invocationPolicy.getOnFailure());
        // cf:invocationPolicy
        xsw.writeEndElement();
    }

    private void writeExtensionAction(XMLStreamWriter xsw, Action action) throws Exception {
        writeExtensionAction(xsw, action, BpmnModelConstants.BPMN_EXT_ATTRIBUTE_ACTION);
    }

    private void writeExtensionAction(XMLStreamWriter xsw, Action action, String elementName) throws Exception {
        if (action == null) {
            return;
        }
        if (action.getType() == null) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002, "Cannot serialize a BPMN action without a type",
                    null);
        }
        xsw.writeStartElement(BpmnModelConstants.CF_NS, elementName);
        writeAttribute(xsw, "type", action.getType().getValue());
        if (BpmnModelConstants.BPMN_EXT_ATTRIBUTE_ACTION.equals(elementName) && action.getExecution() != null) {
            writeAttribute(xsw, "execution", action.getExecution().getValue());
        }

        switch (action.getType()) {
            case SPRING_BEAN -> {
                writeAttribute(xsw, "bean", action.getBean());
                writeAttribute(xsw, "class", action.getClassName());
                writeAttribute(xsw, "method", action.getMethod());
            }
            case JAVA -> {
                writeAttribute(xsw, "class", action.getClassName());
                writeAttribute(xsw, "method", action.getMethod());
            }
            case SCRIPT -> writeAttribute(xsw, "language", action.getLanguage());
        }

        writeMappings(xsw, action.getInputMappings(), action.getOutputMappings(), true);
        if (action.getType() == ActionType.SCRIPT) {
            writeCodeElement(xsw, action.getSource());
        }
        if (BpmnModelConstants.BPMN_EXT_ATTRIBUTE_ACTION.equals(elementName)) {
            writeInvocationPolicyElement(xsw, action.getInvocationPolicy());
            writeEffectPolicy(xsw, action.getEffectPolicy());
        }
        // cf:action
        xsw.writeEndElement();
    }

    private void writeReconcileAction(XMLStreamWriter xsw, ReconcileAction action) throws Exception {
        if (action == null) {
            return;
        }
        if (action.getType() == null) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "Cannot serialize a BPMN reconcile action without a type", null);
        }
        xsw.writeStartElement(BpmnModelConstants.CF_NS, BpmnModelConstants.BPMN_EXT_ELEMENT_RECONCILE_ACTION);
        writeAttribute(xsw, "type", action.getType().getValue());
        switch (action.getType()) {
            case SPRING_BEAN -> {
                writeAttribute(xsw, "bean", action.getBean());
                writeAttribute(xsw, "class", action.getClassName());
                writeAttribute(xsw, "method", action.getMethod());
            }
            case JAVA -> {
                writeAttribute(xsw, "class", action.getClassName());
                writeAttribute(xsw, "method", action.getMethod());
            }
            case SCRIPT -> writeAttribute(xsw, "language", action.getLanguage());
        }
        for (ReconcileInput input : action.getInputs()) {
            xsw.writeStartElement(BpmnModelConstants.CF_NS, BpmnModelConstants.BPMN_EXT_ELEMENT_INPUT);
            writeAttribute(xsw, "source", input.getSource());
            writeAttribute(xsw, "target", input.getTarget());
            writeAttribute(xsw, "dataType", input.getDataType());
            xsw.writeEndElement();
        }
        if (action.getType() == ActionType.SCRIPT) {
            writeCodeElement(xsw, action.getSource());
        }
        xsw.writeEndElement();
    }

    private void writeEffectPolicy(XMLStreamWriter xsw, EffectPolicy policy) throws Exception {
        if (policy == null) {
            return;
        }
        EffectiveEffectPolicy.from(policy);
        xsw.writeStartElement(BpmnModelConstants.CF_NS, BpmnModelConstants.BPMN_EXT_ELEMENT_EFFECT_POLICY);
        writeAttribute(xsw, "recoveryPlanVariable", policy.getRecoveryPlanVariable());
        if (policy.getRecovery() != null) {
            writeAttribute(xsw, "recovery", policy.getRecovery().getValue());
        }
        if (policy.getMaxAttempts() != null) {
            writeAttribute(xsw, "maxAttempts", policy.getMaxAttempts().toString());
        }
        if (policy.getMaxReconcileAttempts() != null) {
            writeAttribute(xsw, "maxReconcileAttempts", policy.getMaxReconcileAttempts().toString());
        }
        writeAttribute(xsw, "recoveryDelay", policy.getRecoveryDelay());
        writeAttribute(xsw, "maxRecoveryDuration", policy.getMaxRecoveryDuration());
        writeReconcileAction(xsw, policy.getReconcileAction());
        // cf:effectPolicy
        xsw.writeEndElement();
    }

    private void writeCodeElement(XMLStreamWriter xsw, String code) throws Exception {
        xsw.writeStartElement(BpmnModelConstants.CF_NS, BpmnModelConstants.BPMN_EXT_ATTRIBUTE_CODE);
        if (code != null) {
            xsw.writeCharacters(code);
        }
        xsw.writeEndElement();
    }

    private void writeMappings(XMLStreamWriter xsw, List<InputMapping> inputs, List<OutputMapping> outputs,
            boolean actionBoundary) throws Exception {
        for (InputMapping input : inputs) {
            xsw.writeStartElement(BpmnModelConstants.CF_NS, BpmnModelConstants.BPMN_EXT_ELEMENT_INPUT);
            writeAttribute(xsw, "source", input.getSource());
            writeAttribute(xsw, "target", input.getTarget());
            if (actionBoundary) {
                writeAttribute(xsw, "dataType", input.getDataType());
            }
            writeAttribute(xsw, "defaultValue", input.getDefaultValue());
            xsw.writeEndElement();
        }
        for (OutputMapping output : outputs) {
            xsw.writeStartElement(BpmnModelConstants.CF_NS, BpmnModelConstants.BPMN_EXT_ELEMENT_OUTPUT);
            writeAttribute(xsw, "source", output.getSource());
            writeAttribute(xsw, "target", output.getTarget());
            if (actionBoundary) {
                writeAttribute(xsw, "dataType", output.getDataType());
            }
            xsw.writeEndElement();
        }
    }

    private void writeLoopCharacteristics(XMLStreamWriter xsw, Activity activity) throws Exception {
        LoopCharacteristics loopChar = activity.getLoopCharacteristics();
        if (loopChar == null) {
            return;
        }

        if (loopChar instanceof MultiInstanceLoopCharacteristics) {
            writeMultiInstanceLoopCharacteristics(xsw, (MultiInstanceLoopCharacteristics) loopChar);
        } else if (loopChar instanceof StandardLoopCharacteristics) {
            writeStandardLoopCharacteristics(xsw, (StandardLoopCharacteristics) loopChar);
        } else {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "Cannot serialize unsupported BPMN loop characteristics: " + loopChar.getClass().getName(), null);
        }
    }

    private void writeMultiInstanceLoopCharacteristics(XMLStreamWriter xsw, MultiInstanceLoopCharacteristics loopChar)
            throws Exception {
        if (loopChar == null) {
            return;
        }
        BpmnLoopContract.validate(loopChar);

        xsw.writeStartElement(BpmnModelConstants.BPMN20_NS, "multiInstanceLoopCharacteristics");

        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_ID, loopChar.getId());
        writeAttribute(xsw, "isSequential", String.valueOf(loopChar.isSequential()));
        writeAttributeWithNamespace(xsw, BpmnModelConstants.CF_NS, "collection", loopChar.getCollection());
        writeAttributeWithNamespace(xsw, BpmnModelConstants.CF_NS, "item", loopChar.getItem());
        writeAttributeWithNamespace(xsw, BpmnModelConstants.CF_NS, "index", loopChar.getIndex());
        writeAttributeWithNamespace(xsw, BpmnModelConstants.CF_NS, "itemType", loopChar.getItemType());
        writeAttributeWithNamespace(xsw, BpmnModelConstants.CF_NS, "target", loopChar.getTarget());
        writeAttributeWithNamespace(xsw, BpmnModelConstants.CF_NS, "source", loopChar.getSource());
        // multiInstanceLoopCharacteristics
        xsw.writeEndElement();
    }

    private void writeStandardLoopCharacteristics(XMLStreamWriter xsw, StandardLoopCharacteristics loopChar)
            throws Exception {
        BpmnLoopContract.validate(loopChar);
        xsw.writeStartElement(BpmnModelConstants.BPMN20_NS, "standardLoopCharacteristics");
        writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_ID, loopChar.getId());
        if (loopChar.getTestBefore() != null) {
            writeAttribute(xsw, "testBefore", loopChar.getTestBefore().toString());
        }
        if (loopChar.getLoopMaximum() != null) {
            writeAttribute(xsw, "loopMaximum", loopChar.getLoopMaximum().toString());
        }
        if (StringUtils.isNotBlank(loopChar.getLoopCondition())) {
            xsw.writeStartElement(BpmnModelConstants.BPMN20_NS, "loopCondition");
            writeFormalExpressionType(xsw);
            writeAttribute(xsw, BpmnModelConstants.BPMN_ATTRIBUTE_LANGUAGE, "java");
            xsw.writeCharacters(loopChar.getLoopCondition());
            xsw.writeEndElement();
        }
        xsw.writeEndElement();
    }

    private void writeFormalExpressionType(XMLStreamWriter xsw) throws Exception {
        xsw.writeAttribute("xsi", BpmnModelConstants.XSI_NS, BpmnModelConstants.XSI_ATTRIBUTE_TYPE, "tFormalExpression");
    }

    private void writeAttributeWithNamespace(XMLStreamWriter xsw, String namespace, String localName, Object value)
            throws Exception {
        if (value != null) {
            xsw.writeAttribute(namespace, localName, value.toString());
        }
    }

    private static class InstanceHolder {
        private static final BpmnXmlWriter INSTANCE = new BpmnXmlWriter();
    }
}
