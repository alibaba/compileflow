package com.alibaba.compileflow.engine.tbbpm.builder.converter.writer;

import com.alibaba.compileflow.engine.core.definition.action.IAction;
import com.alibaba.compileflow.engine.core.definition.action.IActionHandle;
import com.alibaba.compileflow.engine.tbbpm.definition.FlowNode;

import javax.xml.stream.XMLStreamWriter;

/**
 * @author yusu
 */
public abstract class AbstractTbbpmInOutActionNodeWriter<S extends FlowNode>
        extends AbstractTbbpmNodeWriter<S> {

    @Override
    protected void doWrite(S element, XMLStreamWriter xsw) throws Exception {
        xsw.writeStartElement(getName());
        writeNodeAttr(element, xsw);
        writeTransition(element, xsw);
        writeAction(element, xsw);
        enrichNodeElement(element, xsw);
        xsw.writeEndElement();
    }

    protected abstract void writeAction(S element, XMLStreamWriter xsw) throws Exception;

    protected void writeAction(String actionName, IAction action, XMLStreamWriter xsw) throws Exception {
        if (action == null) {
            return;
        }
        xsw.writeStartElement(actionName);
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
