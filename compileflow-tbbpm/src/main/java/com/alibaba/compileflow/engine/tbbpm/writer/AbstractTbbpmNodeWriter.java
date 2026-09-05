/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.tbbpm.writer;

import com.alibaba.compileflow.engine.tbbpm.model.FlowNode;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;
import com.alibaba.compileflow.engine.tbbpm.model.Transition;
import java.util.List;
import javax.xml.stream.XMLStreamWriter;
import org.apache.commons.collections4.CollectionUtils;

/**
 * Abstract base class for TBBPM node XML writers.
 *
 * @param <S> the flow node type
 * @author yusu
 */
public abstract class AbstractTbbpmNodeWriter<S extends FlowNode> extends AbstractTbbpmFlowElementWriter<S> {
    @Override
    protected void doWrite(S element, XMLStreamWriter xsw) throws Exception {
        xsw.writeStartElement(getName());
        writeNodeAttr(element, xsw);
        writeMetadataElements(element, xsw);
        enrichNodeElement(element, xsw);
        writeTransition(element, xsw);
        xsw.writeEndElement();
    }

    protected void writeNodeAttr(S node, XMLStreamWriter xsw) throws Exception {
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_ID, node.getId());
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_NAME, node.getName());
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_DESCRIPTION, node.getDescription());
        enrichNodeAttr(node, xsw);
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_G, node.getGeometry());
    }

    protected abstract String getName();

    protected void enrichNodeAttr(S node, XMLStreamWriter xsw) throws Exception {}

    protected void writeMetadataElements(S node, XMLStreamWriter xsw) throws Exception {}

    protected void writeTransition(S node, XMLStreamWriter xsw) throws Exception {
        List<Transition> transitions = node.getOutgoingTransitions();
        if (CollectionUtils.isEmpty(transitions)) {
            return;
        }
        for (Transition transition : transitions) {
            xsw.writeStartElement(TbbpmModelConstants.TRANSITION);
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_TO, transition.getTarget());
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_NAME, transition.getName());
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_CONDITION, transition.getCondition());
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_G, transition.getGeometry());
            xsw.writeEndElement();
        }
    }

    protected void enrichNodeElement(S element, XMLStreamWriter xsw) throws Exception {}
}
