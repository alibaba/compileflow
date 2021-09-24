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
package com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support.bpmn;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.util.VarUtils;
import com.alibaba.compileflow.engine.definition.bpmn.Process;
import com.alibaba.compileflow.engine.definition.bpmn.*;
import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.definition.common.ElementContainer;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.constants.ParseConstants;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.provider.support.AbstractFlowElementParserProvider;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.provider.support.BpmnElementParserProvider;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support.AbstractFlowStreamParser;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * @author wuxiang
 * @author yusu
 */
public class BpmnStreamParser extends AbstractFlowStreamParser<BpmnModel> {

    public static BpmnStreamParser getInstance() {
        return BpmnStreamParser.Holder.INSTANCE;
    }

    @Override
    protected AbstractFlowElementParserProvider getFlowElementParserProvider() {
        return BpmnElementParserProvider.getInstance();
    }

    @Override
    protected BpmnModel convertToFlowModel(Element top) {
        if (top instanceof Definitions) {
            Definitions definitions = (Definitions)top;
            List<Process> processes = definitions.getProcesses();
            if (CollectionUtils.isEmpty(processes)) {
                throw new CompileFlowException("No process founded");
            }
            if (processes.size() > 1) {
                throw new CompileFlowException("Only one process supported");
            }

            //only one process
            Process process = processes.get(0);
            List<FlowElement> flowElements = process.getFlowElements();
            if (CollectionUtils.isEmpty(flowElements)) {
                throw new CompileFlowException("No process element founded");
            }

            return buildBpmnModel(process);
        }
        throw new CompileFlowException("No flow definition founded");
    }

    private BpmnModel buildBpmnModel(Process process) {
        String id = process.getId();
        if (StringUtils.isEmpty(id)) {
            throw new CompileFlowException("Process has no id");
        }
        BpmnModel bpmnModel = new BpmnModel();
        bpmnModel.setId(id);
        bpmnModel.setName(process.getName());
        if (VarUtils.isLegalVarName(id)) {
            bpmnModel.setCode(StringUtils.uncapitalize(id));
        } else if (VarUtils.isLegalVarName(process.getName())) {
            bpmnModel.setCode(StringUtils.uncapitalize(process.getName()));
        } else {
            bpmnModel.setCode("process" + id.hashCode());
        }
        bpmnModel.addProcesses(process);

        attachNodeTag(bpmnModel);
        buildFlowVar(bpmnModel);
        buildFlowTransition(bpmnModel);
        return bpmnModel;
    }

    private void attachNodeTag(BpmnModel bpmnModel) {
        bpmnModel.getAllNodes().forEach(node -> node.setTag(node.getId()));
    }

    private void buildFlowVar(BpmnModel bpmnModel) {
        Process process = bpmnModel.getProcess();
        bpmnModel.setVars(process.getVars());
        bpmnModel.setParamVars(process.getParamVars());
        bpmnModel.setReturnVars(process.getReturnVars());
        bpmnModel.setInnerVars(process.getInnerVars());
    }

    private void buildFlowTransition(BpmnModel bpmnModel) {
        Process process = bpmnModel.getProcess();
        buildFlowTransition(process);
        process.getFlowElements().stream().filter(flowElement -> flowElement instanceof SubProcess)
            .map(e -> (SubProcess)e)
            .forEach(this::buildFlowTransition);
    }

    private void buildFlowTransition(ElementContainer<FlowElement, FlowNode> elementContainer) {
        elementContainer.getAllElements().stream().filter(flowElement -> flowElement instanceof SequenceFlow)
            .map(e -> (SequenceFlow)e).forEach(sequenceFlow -> {
            FlowNode source = elementContainer.getNode(sequenceFlow.getSourceRef());
            FlowNode target = elementContainer.getNode(sequenceFlow.getTargetRef());
            source.addOutgoingFlow(sequenceFlow);
            source.addOutgoingNode(target);
            target.addIncomingFlow(sequenceFlow);
            target.addIncomingNode(source);
        });
    }

    @Override
    protected String getXSD() {
        return ParseConstants.BPMN_XSD;
    }

    @Override
    public String getName() {
        return "bpmn";
    }

    private static class Holder {
        private static final BpmnStreamParser INSTANCE = new BpmnStreamParser();
    }

}
