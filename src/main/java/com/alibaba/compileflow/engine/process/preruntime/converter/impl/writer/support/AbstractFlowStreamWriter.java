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

import com.alibaba.compileflow.engine.definition.common.FlowModel;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.writer.FlowStreamWriter;
import com.sun.xml.txw2.output.IndentingXMLStreamWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(AbstractFlowStreamWriter.class);

    @Override
    public OutputStream write(T flowModel) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            XMLOutputFactory xof = XMLOutputFactory.newInstance();
            OutputStreamWriter osw = new OutputStreamWriter(outputStream);
            XMLStreamWriter xsw = new IndentingXMLStreamWriter(xof.createXMLStreamWriter(osw));

            doWrite(flowModel, xsw);

            xsw.flush();

            outputStream.close();

            xsw.close();

            return outputStream;
        } catch (Exception e) {
            logger.error("Failed to write outputStream", e);
            return null;
        }
    }

    protected abstract void doWrite(T flowModel, XMLStreamWriter xsw) throws Exception;

}
