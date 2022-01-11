package com.alibaba.compileflow.engine.process.preruntime.converter.impl.writer.support.tbbpm;

import com.alibaba.compileflow.engine.definition.common.action.IAction;
import com.alibaba.compileflow.engine.definition.common.action.IActionHandle;
import com.alibaba.compileflow.engine.definition.tbbpm.InOutActionNode;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.writer.provider.support.TbbpmFlowElementWriterProvider;

import javax.xml.stream.XMLStreamWriter;

/**
 * @author yusu
 */
public abstract class AbstractTbbpmInOutActionNodeWriter<S extends InOutActionNode>
    extends AbstractTbbpmFlowElementWriter<S> {

    @Override
    protected void doWrite(S element, XMLStreamWriter xsw) throws Exception {
        xsw.writeStartElement(getName());
        writeNodeAttr(element, xsw);
        writeTransition(element, xsw);
        writeAction("inAction", element.getInAction(), xsw);
        writeAction("outAction", element.getOutAction(), xsw);
        enrichNodeElement(element, xsw);
        xsw.writeEndElement();
    }

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
