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

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.core.xml.parser.AbstractFlowStreamParser;
import com.alibaba.compileflow.engine.core.xml.parser.AbstractFlowElementParserRegistry;
import com.alibaba.compileflow.engine.core.model.Element;
import com.alibaba.compileflow.engine.core.model.Node;
import com.alibaba.compileflow.engine.core.model.NodeContainer;
import com.alibaba.compileflow.engine.tbbpm.model.FlowNode;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmDocument;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import com.alibaba.compileflow.engine.tbbpm.model.Transition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * XML stream parser for TBBPM flow models.
 *
 * @author wuxiang
 * @author yusu
 */
public class TbbpmXmlParser extends AbstractFlowStreamParser<TbbpmModel> {
    private static final String TBBPM_XSD = "TBBPM.xsd";

    public static TbbpmXmlParser getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    protected AbstractFlowElementParserRegistry getFlowElementParserRegistry() {
        return TbbpmElementParserRegistry.getInstance();
    }

    @Override
    protected TbbpmModel convertToFlowModel(Element top) {
        if (!(top instanceof TbbpmDocument document)) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002, "TBBPM document root is missing");
        }
        if (document.getAllNodes().isEmpty()) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002, "TBBPM document contains no flow nodes");
        }
        return buildFlowModel(document);
    }

    private TbbpmModel buildFlowModel(TbbpmDocument document) {
        TbbpmModel tbbpmModel = new TbbpmModel();
        String id = document.getId();
        String code = document.getCode();
        id = StringUtils.isEmpty(id) ? String.valueOf(Integer.toUnsignedLong(code.hashCode())) : id;
        tbbpmModel.setId(id);
        tbbpmModel.setName(document.getName());
        tbbpmModel.setDescription(document.getDescription());
        tbbpmModel.setCode(code);

        if (!document.getVariables().isEmpty()) {
            tbbpmModel.setVars(document.getVariables());
        }

        List<FlowNode> allNodes = document.getAllNodes();
        tbbpmModel.setAllNodes(allNodes);
        buildFlowTransition(tbbpmModel);
        return tbbpmModel;
    }

    private void buildFlowTransition(NodeContainer<?> nodeContainer) {
        Map<String, FlowNode> nodesById = indexFlowNodes(nodeContainer);
        for (Node element : nodeContainer.getAllNodes()) {
            FlowNode node = requireFlowNode(element);
            List<Transition> outgoingTransitions = node.getOutgoingTransitions();
            if (!outgoingTransitions.isEmpty()) {
                for (Transition outgoingTransition : outgoingTransitions) {
                    outgoingTransition.setSource(node.getId());
                    FlowNode toNode = resolveTarget(nodesById, node, outgoingTransition);
                    node.addOutgoingNode(toNode);
                    toNode.addIncomingTransition(outgoingTransition);
                    toNode.addIncomingNode(node);
                }
            }
            if (node instanceof NodeContainer<?> childContainer) {
                buildFlowTransition(childContainer);
            }
        }
    }

    private FlowNode resolveTarget(Map<String, FlowNode> nodesById, FlowNode source, Transition transition) {
        FlowNode target = nodesById.get(transition.getTarget());
        if (target == null) {
            throw new CompileFlowException.ResourceException(ErrorCode.CF_RESOURCE_002,
                    "Transition from TBBPM node '" + source.getId() + "' references unknown target node: " + transition.getTarget(),
                    null);
        }
        return target;
    }

    private Map<String, FlowNode> indexFlowNodes(NodeContainer<?> nodeContainer) {
        Map<String, FlowNode> nodesById = new LinkedHashMap<>();
        for (Node node : nodeContainer.getAllNodes()) {
            FlowNode flowNode = requireFlowNode(node);
            String nodeId = flowNode.getId();
            if (nodeId == null) {
                continue;
            }
            FlowNode existing = nodesById.putIfAbsent(nodeId, flowNode);
            if (existing != null) {
                throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                        "Duplicate TBBPM node id in container '" + nodeContainer.getId() + "': " + nodeId);
            }
        }
        return Map.copyOf(nodesById);
    }

    private FlowNode requireFlowNode(Node node) {
        if (node instanceof FlowNode flowNode) {
            return flowNode;
        }
        throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                "TBBPM node container contains a non-flow node: " + node.getClass().getName());
    }

    @Override
    protected String getXSD() {
        return TBBPM_XSD;
    }

    private static class InstanceHolder {
        private static final TbbpmXmlParser INSTANCE = new TbbpmXmlParser();
    }
}
