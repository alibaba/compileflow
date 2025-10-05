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
package com.alibaba.compileflow.engine.tbbpm.builder.converter.parser;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.builder.converter.parser.AbstractFlowStreamParser;
import com.alibaba.compileflow.engine.core.builder.converter.parser.provider.AbstractFlowElementParserProvider;
import com.alibaba.compileflow.engine.core.definition.Element;
import com.alibaba.compileflow.engine.core.definition.NodeContainer;
import com.alibaba.compileflow.engine.core.definition.var.IVar;
import com.alibaba.compileflow.engine.tbbpm.definition.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author wuxiang
 * @author yusu
 */
public class TbbpmStreamParser extends AbstractFlowStreamParser<TbbpmModel> {

    private static final String TBBPM_XSD = "TBBPM.xsd";

    public static TbbpmStreamParser getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    protected AbstractFlowElementParserProvider getFlowElementParserProvider() {
        return TbbpmElementParserProvider.getInstance();
    }

    @Override
    protected TbbpmModel convertToFlowModel(Element top) {
        if (top instanceof BpmNode) {
            BpmNode bpmNode = (BpmNode) top;

            List<FlowNode> allNodes = bpmNode.getAllNodes();
            if (CollectionUtils.isEmpty(allNodes)) {
                throw new CompileFlowException.BusinessException(ErrorCode.CF_VALIDATION_002, "No bpm node found");
            }

            return buildFlowModel(bpmNode);
        }
        throw new CompileFlowException.BusinessException(ErrorCode.CF_VALIDATION_002, "No bpm node found");
    }

    private TbbpmModel buildFlowModel(BpmNode bpmNode) {
        TbbpmModel tbbpmModel = new TbbpmModel();
        String id = bpmNode.getId();
        String code = bpmNode.getCode();
        id = StringUtils.isEmpty(id) ? String.valueOf(Integer.toUnsignedLong(code.hashCode())) : id;
        tbbpmModel.setId(id);
        tbbpmModel.setName(bpmNode.getName());
        tbbpmModel.setDescription(bpmNode.getDescription());
        tbbpmModel.setType(bpmNode.getType());
        tbbpmModel.setCode(code);

        buildFlowVar(bpmNode, tbbpmModel);

        List<FlowNode> allNodes = bpmNode.getAllNodes();
        tbbpmModel.setAllNodes(allNodes);
        List<FlowNode> runtimeNodes = buildRuntimeNodes(allNodes);
        tbbpmModel.setRuntimeNodes(runtimeNodes);

        buildFlowTransition(tbbpmModel);
        return tbbpmModel;
    }

    private void buildFlowVar(BpmNode bpmNode, TbbpmModel tbbpmModel) {
        List<IVar> vars = bpmNode.getVars();
        if (CollectionUtils.isNotEmpty(vars)) {
            tbbpmModel.setVars(vars);
            tbbpmModel.setParamVars(buildTypeVars(vars, "param"));
            tbbpmModel.setReturnVars(buildTypeVars(vars, "return"));
            tbbpmModel.setInnerVars(buildTypeVars(vars, "inner"));
        }
    }

    private void buildFlowTransition(NodeContainer<FlowNode> nodeContainer) {
        List<FlowNode> allNodes = nodeContainer.getAllNodes();
        for (FlowNode node : allNodes) {
            List<Transition> outgoingTransitions = node.getOutgoingTransitions();
            if (CollectionUtils.isNotEmpty(outgoingTransitions)) {
                for (Transition outgoingTransition : outgoingTransitions) {
                    FlowNode toNode = nodeContainer.getNode(outgoingTransition.getTarget());
                    node.addOutgoingNode(toNode);
                    toNode.addIncomingTransition(outgoingTransition);
                    toNode.addIncomingNode(node);
                }
            }
            if (node instanceof NodeContainer) {
                buildFlowTransition((NodeContainer) node);
            }
        }
    }

    private List<FlowNode> buildRuntimeNodes(List<FlowNode> allNodes) {
        return allNodes.stream().filter(node -> !(node instanceof NoteNode)).collect(Collectors.toList());
    }

    @Override
    protected String getXSD() {
        return TBBPM_XSD;
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.COMPILE_FLOW;
    }

    private static class Holder {
        private static final TbbpmStreamParser INSTANCE = new TbbpmStreamParser();
    }

}
