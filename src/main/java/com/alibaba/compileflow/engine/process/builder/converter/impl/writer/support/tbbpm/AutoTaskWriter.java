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
package com.alibaba.compileflow.engine.process.builder.converter.impl.writer.support.tbbpm;

import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.definition.tbbpm.AutoTaskNode;
import com.alibaba.compileflow.engine.definition.tbbpm.TbbpmModelConstants;

import javax.xml.stream.XMLStreamWriter;

/**
 * @author yusu
 */
public class AutoTaskWriter extends AbstractTbbpmActionNodeWriter<AutoTaskNode> {

    @Override
    protected String getName() {
        return TbbpmModelConstants.AUTO_TASK;
    }

    @Override
    protected void enrichNodeAttr(AutoTaskNode node, XMLStreamWriter xsw) {

    }

    @Override
    protected void enrichNodeElement(AutoTaskNode element, XMLStreamWriter xsw) throws Exception {

    }

    @Override
    public Class<? extends Element> getElementClass() {
        return AutoTaskNode.class;
    }

}
