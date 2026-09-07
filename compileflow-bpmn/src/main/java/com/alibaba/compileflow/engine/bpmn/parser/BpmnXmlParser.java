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
package com.alibaba.compileflow.engine.bpmn.parser;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.bpmn.model.BpmnModel;
import com.alibaba.compileflow.engine.bpmn.model.Definitions;
import com.alibaba.compileflow.engine.bpmn.model.FlowNode;
import com.alibaba.compileflow.engine.bpmn.model.Message;
import com.alibaba.compileflow.engine.bpmn.model.Process;
import com.alibaba.compileflow.engine.bpmn.model.SequenceFlow;
import com.alibaba.compileflow.engine.bpmn.model.SubProcess;
import com.alibaba.compileflow.engine.core.xml.parser.AbstractFlowStreamParser;
import com.alibaba.compileflow.engine.core.xml.parser.AbstractFlowElementParserRegistry;
import com.alibaba.compileflow.engine.core.model.AbstractFlowElement;
import com.alibaba.compileflow.engine.core.model.Element;
import com.alibaba.compileflow.engine.core.model.ElementContainer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * XML stream parser for BPMN flow models.
 *
 * @author yusu
 */
public class BpmnXmlParser extends AbstractFlowStreamParser<BpmnModel> {
    private static final String BPMN_XSD = "BPMN20.xsd";

    public static BpmnXmlParser getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    protected AbstractFlowElementParserRegistry getFlowElementParserRegistry() {
        return BpmnElementParserRegistry.getInstance();
    }

    @Override
    protected BpmnModel convertToFlowModel(Element top) {
        if (!(top instanceof Definitions definitions)) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002, "No flow definition found");
        }
        List<Process> processes = definitions.getProcesses();
        if (processes.isEmpty()) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002, "No process found");
        }
        if (processes.size() > 1) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002, "Only one process supported");
        }
        Process process = processes.get(0);
        if (process.getFlowElements().isEmpty()) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002, "No process element found");
        }
        return buildBpmnModel(definitions, process, definitions.getMessages());
    }

    private BpmnModel buildBpmnModel(Definitions definitions, Process process, List<Message> messages) {
        String id = process.getId();
        if (StringUtils.isEmpty(id)) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002, "Process has no id");
        }
        BpmnModel bpmnModel = new BpmnModel(process);
        bpmnModel.setDefinitionsId(definitions.getId());
        bpmnModel.setTargetNamespace(definitions.getTargetNamespace());
        bpmnModel.setTypeLanguage(definitions.getTypeLanguage());
        bpmnModel.setExpressionLanguage(definitions.getExpressionLanguage());
        buildFlowTransition(process);

        if (!messages.isEmpty()) {
            bpmnModel.setMessages(messages);
        }
        return bpmnModel;
    }

    private void buildFlowTransition(ElementContainer<AbstractFlowElement, FlowNode> elementContainer) {
        Map<String, FlowNode> nodesById = indexFlowNodes(elementContainer);
        elementContainer
            .getAllElements()
            .stream()
            .filter(flowElement -> flowElement instanceof SequenceFlow)
            .map(e -> (SequenceFlow) e)
            .forEach(sequenceFlow -> {
                FlowNode source = resolveNode(nodesById, sequenceFlow, sequenceFlow.getSource(), "source");
                FlowNode target = resolveNode(nodesById, sequenceFlow, sequenceFlow.getTarget(), "target");
                source.addOutgoingTransition(sequenceFlow);
                source.addOutgoingNode(target);
                target.addIncomingTransition(sequenceFlow);
                target.addIncomingNode(source);
            });
        elementContainer
            .getAllElements()
            .stream()
            .filter(flowElement -> flowElement instanceof SubProcess)
            .map(e -> (SubProcess) e)
            .forEach(this::buildFlowTransition);
    }

    private Map<String, FlowNode> indexFlowNodes(ElementContainer<AbstractFlowElement, FlowNode> elementContainer) {
        Map<String, FlowNode> nodesById = new LinkedHashMap<>();
        for (FlowNode node : elementContainer.getAllNodes()) {
            String nodeId = node.getId();
            if (nodeId == null) {
                continue;
            }
            FlowNode existing = nodesById.putIfAbsent(nodeId, node);
            if (existing != null) {
                throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                        "Duplicate BPMN flow node id in container '" + elementContainer.getId() + "': " + nodeId);
            }
        }
        return Map.copyOf(nodesById);
    }

    private FlowNode resolveNode(Map<String, FlowNode> nodesById, SequenceFlow sequenceFlow, String nodeId,
            String endpointName) {
        FlowNode node = nodesById.get(nodeId);
        if (node != null) {
            return node;
        }
        throw new CompileFlowException.ResourceException(ErrorCode.CF_RESOURCE_002,
                "SequenceFlow '" + sequenceFlow.getId() + "' references unknown " + endpointName + " node: " + nodeId,
                null);
    }

    @Override
    protected String getXSD() {
        return BPMN_XSD;
    }

    private static class InstanceHolder {
        private static final BpmnXmlParser INSTANCE = new BpmnXmlParser();
    }
}
