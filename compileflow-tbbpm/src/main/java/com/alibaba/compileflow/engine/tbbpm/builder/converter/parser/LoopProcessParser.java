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

import com.alibaba.compileflow.engine.core.builder.converter.parser.model.ParseContext;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.XMLSource;
import com.alibaba.compileflow.engine.core.definition.Element;
import com.alibaba.compileflow.engine.tbbpm.definition.LoopProcessNode;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModelConstants;

/**
 * @author wuxiang
 * @author yusu
 */
public class LoopProcessParser extends AbstractTbbpmElementParser<LoopProcessNode> {

    @Override
    protected LoopProcessNode doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        LoopProcessNode loopProcessNode = new LoopProcessNode();
        loopProcessNode.setId(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_ID));
        loopProcessNode.setName(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_NAME));
        loopProcessNode.setDescription(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_DESCRIPTION));
        loopProcessNode.setTag(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_TAG));
        loopProcessNode.setG(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_G));
        loopProcessNode.setLoopType(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_LOOP_TYPE));
        loopProcessNode.setVariableClass(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_VARIABLE_CLASS));
        loopProcessNode.setVariableName(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_VARIABLE_NAME));
        loopProcessNode.setIndexVarName(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_INDEX_VAR_NAME));
        loopProcessNode.setCollectionVarName(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_COLLECTION_VAR_NAME));
        loopProcessNode.setWhileExpression(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_WHILE_EXPRESSION));
        loopProcessNode.setStartNodeId(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_START_NODE_ID));
        loopProcessNode.setEndNodeId(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_END_NODE_ID));
        return loopProcessNode;
    }

    @Override
    protected void attachChildElement(Element childElement, LoopProcessNode element, ParseContext parseContext) {

    }

    @Override
    public String getName() {
        return TbbpmModelConstants.LOOP_PROCESS;
    }

}
