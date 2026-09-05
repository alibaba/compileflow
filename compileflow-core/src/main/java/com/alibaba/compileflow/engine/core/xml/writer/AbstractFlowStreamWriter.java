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
package com.alibaba.compileflow.engine.core.xml.writer;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.core.model.FlowModel;
import com.sun.xml.txw2.output.IndentingXMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

/**
 * Abstract writer that serializes a flow model to an XML stream.
 *
 * @author yusu
 */
public abstract class AbstractFlowStreamWriter<T extends FlowModel<?>> extends AbstractFlowWriter {
    public OutputStream write(T flowModel) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        XMLOutputFactory xof = XMLOutputFactory.newInstance();

        try (OutputStreamWriter osw = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            XMLStreamWriter xsw = new IndentingXMLStreamWriter(xof.createXMLStreamWriter(osw));
            doWrite(flowModel, xsw);
            xsw.flush();
        } catch (CompileFlowException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new CompileFlowException(ErrorCode.CF_RESOURCE_003, "Failed to write flow model to XML stream",
                    failure);
        }

        return outputStream;
    }

    protected abstract void doWrite(T flowModel, XMLStreamWriter xsw) throws Exception;
}
