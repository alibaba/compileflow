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

import com.alibaba.compileflow.engine.core.xml.writer.AbstractFlowStreamWriter;
import com.alibaba.compileflow.engine.core.model.variable.Variable;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;
import java.util.List;
import javax.xml.stream.XMLStreamWriter;
import org.apache.commons.collections4.CollectionUtils;

/**
 * XML stream writer for TBBPM flow models.
 *
 * @author yusu
 */
public class TbbpmXmlWriter extends AbstractFlowStreamWriter<TbbpmModel> {
    public static TbbpmXmlWriter getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    protected void doWrite(TbbpmModel flowModel, XMLStreamWriter xsw) throws Exception {
        xsw.writeStartDocument("UTF-8", "1.0");
        xsw.writeStartElement(TbbpmModelConstants.BPM);
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_CODE, flowModel.getCode());
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_NAME, flowModel.getName());
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_DESCRIPTION, flowModel.getDescription());

        writeVar(flowModel.getVariables(), xsw);

        NodeContainerWriter.write(flowModel, xsw);

        xsw.writeEndElement();
        xsw.writeEndDocument();
    }

    private void writeVar(List<Variable> vars, XMLStreamWriter xsw) throws Exception {
        if (CollectionUtils.isEmpty(vars)) {
            return;
        }
        for (Variable var : vars) {
            xsw.writeStartElement(TbbpmModelConstants.VAR);
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_NAME, var.getName());
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_DESCRIPTION, var.getDescription());
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_DATA_TYPE, var.getDataType());
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_DEFAULT_VALUE, var.getDefaultValue());
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_IN_OUT_TYPE, var.getInOutType());
            xsw.writeEndElement();
        }
    }

    private static class InstanceHolder {
        private static final TbbpmXmlWriter INSTANCE = new TbbpmXmlWriter();
    }
}
