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

import com.alibaba.compileflow.engine.tbbpm.definition.FlowNode;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModelConstants;
import com.alibaba.compileflow.engine.tbbpm.definition.Transition;
import org.apache.commons.collections4.CollectionUtils;

import javax.xml.stream.XMLStreamWriter;
import java.util.List;

/**
 * @author yusu
 */
public abstract class AbstractTbbpmNodeWriter<S extends FlowNode> extends AbstractTbbpmFlowElementWriter<S> {

    @Override
    protected void doWrite(S element, XMLStreamWriter xsw) throws Exception {
        xsw.writeStartElement(getName());
        writeNodeAttr(element, xsw);
        writeTransition(element, xsw);
        enrichNodeElement(element, xsw);
        xsw.writeEndElement();
    }

    protected void writeNodeAttr(S node, XMLStreamWriter xsw) throws Exception {
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_ID, node.getId());
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_NAME, node.getName());
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_DESCRIPTION, node.getDescription());
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_TAG, node.getTag());
        enrichNodeAttr(node, xsw);
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_G, node.getG());
    }

    protected abstract String getName();

    protected abstract void enrichNodeAttr(S node, XMLStreamWriter xsw) throws Exception;

    protected void writeTransition(S node, XMLStreamWriter xsw) throws Exception {
        List<Transition> transitions = node.getOutgoingTransitions();
        if (CollectionUtils.isEmpty(transitions)) {
            return;
        }
        for (Transition transition : transitions) {
            xsw.writeStartElement(TbbpmModelConstants.TRANSITION);
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_TO, transition.getTarget());
            if (transition.getPriority() > 0) {
                writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_PRIORITY, String.valueOf(transition.getPriority()));
            }
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_NAME, transition.getName());
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_EXPRESSION, transition.getExpression());
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_G, transition.getG());
            xsw.writeEndElement();
        }
    }

    protected abstract void enrichNodeElement(S element, XMLStreamWriter xsw) throws Exception;

}
