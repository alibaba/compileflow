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
package com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support.tbbpm;

import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.definition.tbbpm.BpmNode;
import com.alibaba.compileflow.engine.definition.tbbpm.TbbpmModelConstants;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support.AbstractTbbpmElementParser;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.XMLSource;

/**
 * @author wuxiang
 * @author yusu
 */
public class BpmParser extends AbstractTbbpmElementParser<BpmNode> {

    @Override
    protected BpmNode doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        BpmNode bpmNode = new BpmNode();
        bpmNode.setId(xmlSource.getString("id"));
        bpmNode.setCode(xmlSource.getString("code"));
        bpmNode.setBizCode(xmlSource.getString("bizCode"));
        bpmNode.setTenantId(xmlSource.getString("tenantId"));
        bpmNode.setName(xmlSource.getString("name"));
        bpmNode.setType(xmlSource.getString("type"));
        bpmNode.setDescription(xmlSource.getString("description"));
        bpmNode.setVersion(xmlSource.getInt("version"));

        parseContext.setTop(bpmNode);
        return bpmNode;
    }

    @Override
    protected void attachChildElement(Element childElement, BpmNode element, ParseContext parseContext) {

    }

    @Override
    public String getName() {
        return TbbpmModelConstants.BPM;
    }

}
