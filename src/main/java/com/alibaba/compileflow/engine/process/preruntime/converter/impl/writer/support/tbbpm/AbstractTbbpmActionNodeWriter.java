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

import com.alibaba.compileflow.engine.definition.common.action.IAction;
import com.alibaba.compileflow.engine.definition.common.action.IActionHandle;
import com.alibaba.compileflow.engine.definition.tbbpm.ActionNode;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.writer.provider.support.TbbpmFlowElementWriterProvider;

import javax.xml.stream.XMLStreamWriter;

/**
 * @author yusu
 */
public abstract class AbstractTbbpmActionNodeWriter<S extends ActionNode>
    extends AbstractTbbpmFlowElementWriter<S> {

    @Override
    protected void doWrite(S element, XMLStreamWriter xsw) throws Exception {
        xsw.writeStartElement(getName());
        writeNodeAttr(element, xsw);
        writeTransition(element, xsw);
        writeAction(element.getAction(), xsw);
        enrichNodeElement(element, xsw);
        xsw.writeEndElement();
    }

    protected void writeAction(IAction action, XMLStreamWriter xsw) throws Exception {
        if (action == null) {
            return;
        }
        xsw.writeStartElement("action");
        writeAttribute(xsw, "type", action.getType());
        writeActionHandle(action.getActionHandle(), xsw);
        xsw.writeEndElement();
    }

    @SuppressWarnings("unchecked")
    private void writeActionHandle(IActionHandle actionHandle, XMLStreamWriter xsw) throws Exception {
        if (actionHandle == null) {
            return;
        }

        TbbpmFlowElementWriterProvider.getInstance().getWriter(actionHandle.getClass()).write(actionHandle, xsw);
    }

}
