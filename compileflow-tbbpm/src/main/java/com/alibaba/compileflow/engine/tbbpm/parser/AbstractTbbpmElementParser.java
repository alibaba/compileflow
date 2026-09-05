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
import com.alibaba.compileflow.engine.core.xml.parser.AbstractFlowElementParser;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.core.xml.parser.AbstractFlowElementParserRegistry;
import com.alibaba.compileflow.engine.core.model.Element;
import com.alibaba.compileflow.engine.core.model.action.Action;
import com.alibaba.compileflow.engine.core.model.action.HasAction;
import com.alibaba.compileflow.engine.core.model.mapping.InputMapping;
import com.alibaba.compileflow.engine.core.model.mapping.MappingModel;
import com.alibaba.compileflow.engine.core.model.mapping.OutputMapping;
import com.alibaba.compileflow.engine.core.model.variable.VariableContainer;
import com.alibaba.compileflow.engine.core.model.variable.Variable;
import com.alibaba.compileflow.engine.tbbpm.model.FlowNode;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmNodeContainer;
import com.alibaba.compileflow.engine.tbbpm.model.Transition;

/**
 * Abstract base class for TBBPM element XML parsers.
 *
 * @author yusu
 */
public abstract class AbstractTbbpmElementParser<E extends Element> extends AbstractFlowElementParser<E> {
    @Override
    public E parse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        TbbpmAttributeContract.validate(xmlSource);
        return super.parse(xmlSource, parseContext);
    }

    @Override
    public AbstractFlowElementParserRegistry getParserRegistry() {
        return TbbpmElementParserRegistry.getInstance();
    }

    @Override
    protected boolean attachPlatformChildElement(Element childElement, E element, ParseContext parseContext) {
        if (element instanceof FlowNode flowNode && childElement instanceof Transition transition) {
            flowNode.addOutgoingTransition(transition);
            return true;
        }
        if (element instanceof HasAction actionOwner && childElement instanceof Action action) {
            if (actionOwner.getAction() != null) {
                throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                        "An action node must declare at most one action element");
            }
            actionOwner.setAction(action);
            return true;
        }
        if (element instanceof VariableContainer variableOwner && childElement instanceof Variable variable) {
            variableOwner.addVariable(variable);
            return true;
        }
        if (element instanceof MappingModel mappingOwner && childElement instanceof InputMapping input) {
            mappingOwner.addInputMapping(input);
            return true;
        }
        if (element instanceof MappingModel mappingOwner && childElement instanceof OutputMapping output) {
            mappingOwner.addOutputMapping(output);
            return true;
        }
        if (element instanceof TbbpmNodeContainer container && childElement instanceof FlowNode flowNode) {
            container.addNode(flowNode);
            return true;
        }
        return false;
    }

    /**
     * Rejects recognized elements placed under an unsupported TBBPM parent.
     */
    @Override
    protected void attachChildElement(Element childElement, E element, ParseContext parseContext) {
        throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                "Unsupported TBBPM child element " + childElement.getClass().getSimpleName() + " under "
                + element.getClass().getSimpleName(), null);
    }

    protected <N extends FlowNode> void parseCommonNodeAttributes(XmlSource xmlSource, N node) {
        node.setId(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_ID));
        node.setName(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_NAME));
        node.setDescription(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_DESCRIPTION));
        node.setGeometry(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_G));
    }
}
