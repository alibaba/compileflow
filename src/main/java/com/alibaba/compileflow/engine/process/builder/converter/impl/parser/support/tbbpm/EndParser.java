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
package com.alibaba.compileflow.engine.process.builder.converter.impl.parser.support.tbbpm;

import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.definition.tbbpm.EndNode;
import com.alibaba.compileflow.engine.definition.tbbpm.TbbpmModelConstants;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.XMLSource;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.support.AbstractTbbpmElementParser;

/**
 * @author wuxiang
 * @author yusu
 */
public class EndParser extends AbstractTbbpmElementParser<EndNode> {

    @Override
    protected EndNode doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        EndNode endNode = new EndNode();
        endNode.setId(xmlSource.getString("id"));
        endNode.setName(xmlSource.getString("name"));
        endNode.setDescription(xmlSource.getString("description"));
        endNode.setG(xmlSource.getString("g"));
        endNode.setTag(xmlSource.getString("tag"));
        return endNode;
    }

    @Override
    protected void attachChildElement(Element childElement, EndNode element, ParseContext parseContext) {

    }

    @Override
    public String getName() {
        return TbbpmModelConstants.END;
    }

}
