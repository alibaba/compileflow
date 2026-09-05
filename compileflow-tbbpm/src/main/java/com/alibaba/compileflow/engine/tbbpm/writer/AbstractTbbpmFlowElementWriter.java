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

import com.alibaba.compileflow.engine.core.xml.writer.AbstractFlowElementWriter;
import com.alibaba.compileflow.engine.core.model.Element;
import com.alibaba.compileflow.engine.core.model.mapping.InputMapping;
import com.alibaba.compileflow.engine.core.model.mapping.OutputMapping;
import com.alibaba.compileflow.engine.core.model.variable.Variable;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;
import java.util.List;
import javax.xml.stream.XMLStreamWriter;
import org.apache.commons.collections4.CollectionUtils;

/**
 * Abstract base class for TBBPM flow element XML writers.
 *
 * @author yusu
 */
public abstract class AbstractTbbpmFlowElementWriter<S extends Element> extends AbstractFlowElementWriter<S> {
    protected void writeVar(List<Variable> vars, XMLStreamWriter xsw) throws Exception {
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

    protected void writeMappings(List<InputMapping> inputs, List<OutputMapping> outputs, XMLStreamWriter xsw,
            boolean actionBoundary) throws Exception {
        for (InputMapping input : inputs) {
            xsw.writeStartElement(TbbpmModelConstants.INPUT);
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_SOURCE, input.getSource());
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_TARGET, input.getTarget());
            if (actionBoundary) {
                writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_DATA_TYPE, input.getDataType());
            }
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_DEFAULT_VALUE, input.getDefaultValue());
            xsw.writeEndElement();
        }
        for (OutputMapping output : outputs) {
            xsw.writeStartElement(TbbpmModelConstants.OUTPUT);
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_SOURCE, output.getSource());
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_TARGET, output.getTarget());
            if (actionBoundary) {
                writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_DATA_TYPE, output.getDataType());
            }
            xsw.writeEndElement();
        }
    }
}
