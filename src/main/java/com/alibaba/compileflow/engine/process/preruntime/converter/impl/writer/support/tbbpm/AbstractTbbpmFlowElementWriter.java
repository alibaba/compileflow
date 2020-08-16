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

import com.alibaba.compileflow.engine.definition.tbbpm.FlowNode;
import com.alibaba.compileflow.engine.definition.tbbpm.Transition;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.writer.support.AbstractFlowElementWriter;
import org.apache.commons.collections4.CollectionUtils;

import javax.xml.stream.XMLStreamWriter;
import java.util.List;

/**
 * @author yusu
 */
public abstract class AbstractTbbpmFlowElementWriter<S extends FlowNode> extends AbstractFlowElementWriter<S> {

    @Override
    protected void doWrite(S element, XMLStreamWriter xsw) throws Exception {
        xsw.writeStartElement(getName());
        writeNodeAttr(element, xsw);
        writeTransition(element, xsw);
        enrichNodeElement(element, xsw);
        xsw.writeEndElement();
    }

    protected void writeNodeAttr(S node, XMLStreamWriter xsw) throws Exception {
        writeAttribute(xsw, "id", node.getId());
        writeAttribute(xsw, "name", node.getName());
        writeAttribute(xsw, "description", node.getDescription());
        writeAttribute(xsw, "tag", node.getTag());
        enrichNodeAttr(node, xsw);
        writeAttribute(xsw, "g", node.getG());
    }

    protected abstract String getName();

    protected abstract void enrichNodeAttr(S node, XMLStreamWriter xsw) throws Exception;

    protected void writeTransition(S node, XMLStreamWriter xsw) throws Exception {
        List<Transition> transitions = node.getTransitions();
        if (CollectionUtils.isEmpty(transitions)) {
            return;
        }
        for (Transition transition : transitions) {
            xsw.writeStartElement("transition");
            writeAttribute(xsw, "to", transition.getTo());
            if (transition.getPriority() > 0) {
                writeAttribute(xsw, "priority", String.valueOf(transition.getPriority()));
            }
            writeAttribute(xsw, "name", transition.getName());
            writeAttribute(xsw, "expression", transition.getExpression());
            writeAttribute(xsw, "g", transition.getG());
            xsw.writeEndElement();
        }
    }

    protected abstract void enrichNodeElement(S element, XMLStreamWriter xsw) throws Exception;

}
