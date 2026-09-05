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
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;
import com.alibaba.compileflow.engine.tbbpm.model.WhileNode;

/**
 * XML parser for {@code while}.
 */
public final class WhileParser extends AbstractTbbpmElementParser<WhileNode> {
    @Override
    protected WhileNode doParse(XmlSource source, ParseContext context) {
        WhileNode node = new WhileNode();
        parseCommonNodeAttributes(source, node);
        node.setCondition(source.getString(TbbpmModelConstants.ATTRIBUTE_CONDITION));
        node.setIndex(source.getString(TbbpmModelConstants.ATTRIBUTE_INDEX));
        String maxIterations = source.getString(TbbpmModelConstants.ATTRIBUTE_MAX_ITERATIONS);
        if (maxIterations != null) {
            try {
                node.setMaxIterations(Integer.valueOf(maxIterations));
            } catch (NumberFormatException invalid) {
                throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                        "while maxIterations must be a 32-bit integer: " + maxIterations, invalid);
            }
        }
        return node;
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.WHILE;
    }
}
