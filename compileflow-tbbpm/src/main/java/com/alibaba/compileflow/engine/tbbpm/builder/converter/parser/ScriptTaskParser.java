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
import com.alibaba.compileflow.engine.tbbpm.definition.ScriptTaskNode;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModelConstants;

/**
 * @author wuxiang
 * @author yusu
 */
public class ScriptTaskParser extends AbstractTbbpmElementParser<ScriptTaskNode> {

    @Override
    protected ScriptTaskNode doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        ScriptTaskNode scriptTaskNode = new ScriptTaskNode();
        scriptTaskNode.setId(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_ID));
        scriptTaskNode.setName(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_NAME));
        scriptTaskNode.setDescription(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_DESCRIPTION));
        scriptTaskNode.setTag(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_TAG));
        scriptTaskNode.setG(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_G));
        return scriptTaskNode;
    }

    @Override
    protected void attachChildElement(Element childElement, ScriptTaskNode element, ParseContext parseContext) {

    }

    @Override
    public String getName() {
        return TbbpmModelConstants.SCRIPT_TASK;
    }

}
