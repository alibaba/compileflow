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

import com.alibaba.compileflow.engine.core.builder.converter.writer.AbstractFlowStreamWriter;
import com.alibaba.compileflow.engine.core.definition.NodeContainer;
import com.alibaba.compileflow.engine.core.definition.var.IVar;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModel;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModelConstants;
import org.apache.commons.collections4.CollectionUtils;

import javax.xml.stream.XMLStreamWriter;
import java.util.List;

/**
 * @author yusu
 */
public class TbbpmStreamWriter extends AbstractFlowStreamWriter<TbbpmModel> {

    public static TbbpmStreamWriter getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doWrite(TbbpmModel flowModel, XMLStreamWriter xsw) throws Exception {
        xsw.writeStartDocument("UTF-8", "1.0");
        xsw.writeStartElement(TbbpmModelConstants.BPM);
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_CODE, flowModel.getCode());
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_NAME, flowModel.getName());
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_TYPE, flowModel.getType());
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_DESCRIPTION, flowModel.getDescription());

        writeVar(flowModel.getVars(), xsw);

        TbbpmFlowElementWriterProvider.getInstance().getWriter(NodeContainer.class).write(flowModel, xsw);

        xsw.writeEndElement();
        xsw.writeEndDocument();
    }

    private void writeVar(List<IVar> vars, XMLStreamWriter xsw) throws Exception {
        if (CollectionUtils.isEmpty(vars)) {
            return;
        }
        for (IVar var : vars) {
            xsw.writeStartElement(TbbpmModelConstants.VAR);
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_NAME, var.getName());
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_DESCRIPTION, var.getDescription());
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_DATA_TYPE, var.getDataType());
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_CONTEXT_VAR_NAME, var.getContextVarName());
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_DEFAULT_VALUE, var.getDefaultValue());
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_IN_OUT_TYPE, var.getInOutType());
            xsw.writeEndElement();
        }
    }

    private static class Holder {
        private static final TbbpmStreamWriter INSTANCE = new TbbpmStreamWriter();
    }

}
