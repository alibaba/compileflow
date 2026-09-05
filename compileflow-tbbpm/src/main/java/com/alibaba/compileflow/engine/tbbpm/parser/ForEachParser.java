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
import com.alibaba.compileflow.engine.core.model.ForEachElement;
import com.alibaba.compileflow.engine.core.model.Element;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.tbbpm.model.ForEachNode;
import com.alibaba.compileflow.engine.tbbpm.model.ForEachOutput;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;

/**
 * XML parser for {@code foreach}.
 */
public final class ForEachParser extends AbstractTbbpmElementParser<ForEachNode> {
    @Override
    protected ForEachNode doParse(XmlSource source, ParseContext context) {
        ForEachNode node = new ForEachNode();
        parseCommonNodeAttributes(source, node);
        node.setCollection(source.getString(TbbpmModelConstants.ATTRIBUTE_COLLECTION));
        node.setItem(source.getString(TbbpmModelConstants.ATTRIBUTE_ITEM));
        node.setItemType(source.getString(TbbpmModelConstants.ATTRIBUTE_ITEM_TYPE));
        node.setIndex(source.getString(TbbpmModelConstants.ATTRIBUTE_INDEX));
        String execution = source.getString(TbbpmModelConstants.ATTRIBUTE_EXECUTION);
        if (execution != null) {
            node.setExecution(parseExecution(execution));
        }
        return node;
    }

    @Override
    protected void attachChildElement(Element child, ForEachNode node, ParseContext context) {
        if (child instanceof ForEachOutput output) {
            if (node.getOutput() != null) {
                throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                        "foreach must declare at most one output mapping");
            }
            if (!node.getOutgoingTransitions().isEmpty() || !node.getAllNodes().isEmpty()) {
                throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                        "foreach output must precede transitions and body nodes");
            }
            node.setOutput(output);
            return;
        }
        super.attachChildElement(child, node, context);
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.FOREACH;
    }

    private ForEachElement.Execution parseExecution(String execution) {
        return switch (execution) {
            case "sequential" -> ForEachElement.Execution.SEQUENTIAL;
            case "parallel" -> ForEachElement.Execution.PARALLEL;
            default -> throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "foreach execution must be sequential or parallel: " + execution);
        };
    }
}
