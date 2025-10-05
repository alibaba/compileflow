/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.bpmn.builder.converter.parser;

import com.alibaba.compileflow.engine.bpmn.definition.*;
import com.alibaba.compileflow.engine.bpmn.definition.Process;
import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.builder.converter.parser.AbstractFlowStreamParser;
import com.alibaba.compileflow.engine.core.builder.converter.parser.provider.AbstractFlowElementParserProvider;
import com.alibaba.compileflow.engine.core.definition.BaseFlowElement;
import com.alibaba.compileflow.engine.core.definition.Element;
import com.alibaba.compileflow.engine.core.definition.ElementContainer;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * The core parser for BPMN 2.0 XML streams.
 * <p>
 * This class orchestrates the entire parsing process. It uses an
 * {@link BpmnElementParserProvider} to delegate the parsing of individual XML
 * elements to their specific parser implementations. After the raw object graph
 * is parsed, this class is responsible for post-processing steps, such as:
 * <ul>
 *   <li>Validating the basic structure (e.g., ensuring exactly one process).</li>
 *   <li>Building the final {@link BpmnModel}.</li>
 *   <li>Connecting all the {@link SequenceFlow} transitions between nodes.</li>
 *   <li>Extracting and organizing process variables.</li>
 * </ul>
 *
 * @author wuxiang
 * @author yusu
 */
public class BpmnStreamParser extends AbstractFlowStreamParser<BpmnModel> {

    private static final String BPMN_XSD = "BPMN20.xsd";

    public static BpmnStreamParser getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    protected AbstractFlowElementParserProvider getFlowElementParserProvider() {
        return BpmnElementParserProvider.getInstance();
    }

    @Override
    protected BpmnModel convertToFlowModel(Element top) {
        if (top instanceof Definitions) {
            Definitions definitions = (Definitions) top;
            List<Process> processes = definitions.getProcesses();
            if (CollectionUtils.isEmpty(processes)) {
                throw new CompileFlowException.BusinessException(
                        ErrorCode.CF_VALIDATION_002,
                        "No process found"
                );
            }
            if (processes.size() > 1) {
                throw new CompileFlowException.BusinessException(
                        ErrorCode.CF_VALIDATION_002,
                        "Only one process supported"
                );
            }

            //only one process
            Process process = processes.get(0);
            List<BaseFlowElement> flowElements = process.getFlowElements();
            if (CollectionUtils.isEmpty(flowElements)) {
                throw new CompileFlowException.BusinessException(
                        ErrorCode.CF_VALIDATION_002,
                        "No process element found"
                );
            }

            List<Message> messages = definitions.getMessages();
            return buildBpmnModel(process, messages);
        }
        throw new CompileFlowException.BusinessException(
                ErrorCode.CF_VALIDATION_002,
                "No flow definition found"
        );
    }

    /**
     * Builds the final BpmnModel from a parsed Process definition. It sets up
     * the model's core properties and triggers the post-processing steps.
     */
    private BpmnModel buildBpmnModel(Process process, List<Message> messages) {
        String id = process.getId();
        if (StringUtils.isEmpty(id)) {
            throw new CompileFlowException.BusinessException(
                    ErrorCode.CF_VALIDATION_001,
                    "Process has no id"
            );
        }
        BpmnModel bpmnModel = new BpmnModel();
        bpmnModel.setId(id);
        bpmnModel.setName(process.getName());
        bpmnModel.setCode(id);
        bpmnModel.addProcess(process);

        attachNodeTag(bpmnModel);
        buildFlowVar(bpmnModel);
        buildFlowTransition(bpmnModel);

        if (CollectionUtils.isNotEmpty(messages)) {
            bpmnModel.setMessages(messages);
        }
        return bpmnModel;
    }

    /**
     * Sets the tag of each node to be its ID. This is a convention used by
     * the engine for certain lookup operations.
     */
    private void attachNodeTag(BpmnModel bpmnModel) {
        bpmnModel.getAllNodes().forEach(node -> node.setTag(node.getId()));
    }

    /**
     * Extracts all variable definitions (input, output, inner) from the Process
     * and attaches them to the top-level BpmnModel.
     */
    private void buildFlowVar(BpmnModel bpmnModel) {
        Process process = bpmnModel.getProcess();
        bpmnModel.setVars(process.getVars());
        bpmnModel.setParamVars(process.getParamVars());
        bpmnModel.setReturnVars(process.getReturnVars());
        bpmnModel.setInnerVars(process.getInnerVars());
    }

    /**
     * Connects all the sequence flows to their source and target nodes.
     * This is a critical step that builds the executable graph from the parsed elements.
     * It recursively handles nested sub-processes.
     */
    private void buildFlowTransition(BpmnModel bpmnModel) {
        Process process = bpmnModel.getProcess();
        buildFlowTransition(process);
    }

    private void buildFlowTransition(ElementContainer<BaseFlowElement, FlowNode> elementContainer) {
        elementContainer.getAllElements().stream().filter(flowElement -> flowElement instanceof SequenceFlow)
                .map(e -> (SequenceFlow) e).forEach(sequenceFlow -> {
                    FlowNode source = elementContainer.getNode(sequenceFlow.getSource());
                    FlowNode target = elementContainer.getNode(sequenceFlow.getTarget());
                    source.addOutgoingTransition(sequenceFlow);
                    source.addOutgoingNode(target);
                    target.addIncomingTransition(sequenceFlow);
                    target.addIncomingNode(source);
                });
        elementContainer.getAllElements().stream().filter(flowElement -> flowElement instanceof SubProcess)
                .map(e -> (SubProcess) e)
                .forEach(this::buildFlowTransition);
    }

    @Override
    protected String getXSD() {
        return BPMN_XSD;
    }

    @Override
    public String getName() {
        return "bpmn";
    }

    private static class Holder {
        private static final BpmnStreamParser INSTANCE = new BpmnStreamParser();
    }

}
