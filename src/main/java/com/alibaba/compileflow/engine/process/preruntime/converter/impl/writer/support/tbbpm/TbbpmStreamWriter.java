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

import com.alibaba.compileflow.engine.definition.common.NodeContainer;
import com.alibaba.compileflow.engine.definition.tbbpm.TbbpmModel;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.writer.provider.support.TbbpmFlowElementWriterProvider;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.writer.support.AbstractFlowStreamWriter;

import javax.xml.stream.XMLStreamWriter;

/**
 * @author yusu
 */
public class TbbpmStreamWriter extends AbstractFlowStreamWriter<TbbpmModel> {

    public static TbbpmStreamWriter getInstance() {
        return TbbpmStreamWriter.Holder.INSTANCE;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doWrite(TbbpmModel flowModel, XMLStreamWriter xsw) throws Exception {
        xsw.writeStartDocument("UTF-8", "1.0");
        xsw.writeStartElement("bpm");
        //writeAttribute(xsw, "id", flowModel.getId());
        writeAttribute(xsw, "code", flowModel.getCode());
        writeAttribute(xsw, "name", flowModel.getName());
        writeAttribute(xsw, "type", flowModel.getType());
        writeAttribute(xsw, "description", flowModel.getDescription());
        writeAttribute(xsw, "bizCode", flowModel.getBizCode());
        writeAttribute(xsw, "tenantId", flowModel.getTenantId());

        writeVar(flowModel.getVars(), xsw);

        TbbpmFlowElementWriterProvider.getInstance().getWriter(NodeContainer.class).write(flowModel, xsw);

        xsw.writeEndElement();
        xsw.writeEndDocument();
    }

    private static class Holder {
        private static final TbbpmStreamWriter INSTANCE = new TbbpmStreamWriter();
    }

}
