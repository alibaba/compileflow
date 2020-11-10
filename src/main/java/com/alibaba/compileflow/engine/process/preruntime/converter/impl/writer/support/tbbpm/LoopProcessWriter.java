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
package com.alibaba.compileflow.engine.process.preruntime.converter.impl.writer.support.tbbpm;

import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.definition.common.NodeContainer;
import com.alibaba.compileflow.engine.definition.tbbpm.LoopProcessNode;
import com.alibaba.compileflow.engine.definition.tbbpm.TbbpmModelConstants;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.writer.provider.support.TbbpmFlowElementWriterProvider;

import javax.xml.stream.XMLStreamWriter;

/**
 * @author yusu
 */
public class LoopProcessWriter extends AbstractTbbpmFlowElementWriter<LoopProcessNode> {

    @Override
    protected String getName() {
        return TbbpmModelConstants.LOOP_PROCESS;
    }

    @Override
    protected void enrichNodeAttr(LoopProcessNode node, XMLStreamWriter xsw) throws Exception {
        writeAttribute(xsw, "loopType", node.getLoopType());
        writeAttribute(xsw, "whileExpression", node.getWhileExpression());
        writeAttribute(xsw, "collectionVarName", node.getCollectionVarName());
        writeAttribute(xsw, "variableName", node.getVariableName());
        writeAttribute(xsw, "indexVarName", node.getIndexVarName());
        writeAttribute(xsw, "variableClass", node.getVariableClass());
        writeAttribute(xsw, "startNodeId", node.getStartNodeId());
        writeAttribute(xsw, "endNodeId", node.getEndNodeId());
    }

    @Override
    protected void enrichNodeElement(LoopProcessNode element, XMLStreamWriter xsw) throws Exception {
        TbbpmFlowElementWriterProvider.getInstance().getWriter(NodeContainer.class).write(element, xsw);
    }

    @Override
    public Class<? extends Element> getElementClass() {
        return LoopProcessNode.class;
    }

}
