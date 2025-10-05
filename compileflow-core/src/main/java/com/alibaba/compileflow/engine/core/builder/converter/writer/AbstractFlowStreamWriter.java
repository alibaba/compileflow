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
package com.alibaba.compileflow.engine.core.builder.converter.writer;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.definition.FlowModel;
import com.sun.xml.txw2.output.IndentingXMLStreamWriter;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * @author yusu
 */
public abstract class AbstractFlowStreamWriter<T extends FlowModel> extends AbstractFlowWriterSupport
        implements FlowStreamWriter<T> {

    @Override
    public OutputStream write(T flowModel) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        XMLOutputFactory xof = XMLOutputFactory.newInstance();

        try (OutputStreamWriter osw = new OutputStreamWriter(outputStream)) {
            XMLStreamWriter xsw = new IndentingXMLStreamWriter(xof.createXMLStreamWriter(osw));
            doWrite(flowModel, xsw);
            xsw.flush();
        } catch (Exception e) {
            throw new CompileFlowException.SystemException(
                    ErrorCode.CF_RESOURCE_003,
                    "Failed to write flow model to XML stream",
                    e
            );
        }

        return outputStream;
    }

    protected abstract void doWrite(T flowModel, XMLStreamWriter xsw) throws Exception;

}
