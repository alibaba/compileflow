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
package com.alibaba.compileflow.engine.tbbpm.builder.converter.writer;

import com.alibaba.compileflow.engine.core.definition.Element;
import com.alibaba.compileflow.engine.tbbpm.definition.SubBpmNode;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModelConstants;

import javax.xml.stream.XMLStreamWriter;

/**
 * @author yusu
 */
public class SubBpmWriter extends AbstractTbbpmNodeWriter<SubBpmNode> {

    @Override
    protected String getName() {
        return TbbpmModelConstants.SUB_BPM;
    }

    @Override
    protected void enrichNodeAttr(SubBpmNode node, XMLStreamWriter xsw) throws Exception {
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_SUB_BPM_CODE, node.getSubBpmCode());
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_TYPE, node.getType());
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_WAIT_FOR_COMPLETION, String.valueOf(node.isWaitForCompletion()));
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_WAIT_FOR_TRIGGER, String.valueOf(node.isWaitForTrigger()));

    }

    @Override
    protected void enrichNodeElement(SubBpmNode node, XMLStreamWriter xsw) throws Exception {
        if (node.getVars() != null) {
            writeVar(node.getVars(), xsw);
        }
    }

    @Override
    public Class<? extends Element> getElementClass() {
        return SubBpmNode.class;
    }

}
