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
package com.alibaba.compileflow.engine.process.preruntime.converter.impl.writer.support;

import com.alibaba.compileflow.engine.definition.common.var.IVar;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import javax.xml.stream.XMLStreamWriter;
import java.util.List;

/**
 * @author yusu
 * @date 2020/06/22
 */
public abstract class AbstractFlowWriterSupport {

    protected void writeAttribute(XMLStreamWriter xsw, String name, String value) throws Exception {
        if (StringUtils.isNotBlank(name) && StringUtils.isNotBlank(value)) {
            xsw.writeAttribute(name, value);
        }
    }

    protected void writeVar(List<IVar> vars, XMLStreamWriter xsw) throws Exception {
        if (CollectionUtils.isNotEmpty(vars)) {
            for (IVar var : vars) {
                xsw.writeStartElement("var");
                writeAttribute(xsw, "name", var.getName());
                writeAttribute(xsw, "description", var.getDescription());
                writeAttribute(xsw, "dataType", var.getDataType());
                writeAttribute(xsw, "contextVarName", var.getContextVarName());
                writeAttribute(xsw, "defaultValue", var.getDefaultValue());
                writeAttribute(xsw, "inOutType", var.getInOutType());
                xsw.writeEndElement();
            }
        }
    }

}
