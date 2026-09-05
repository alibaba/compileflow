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

import com.alibaba.compileflow.engine.core.model.Element;
import javax.xml.stream.XMLStreamWriter;

/**
 * Abstract writer for one flow element type.
 *
 * @author yusu
 */
public abstract class AbstractFlowElementWriter<S extends Element> extends AbstractFlowWriter
        implements FlowElementWriter<S> {
    @Override
    public void write(S element, XMLStreamWriter xsw) throws Exception {
        doWrite(element, xsw);
    }

    protected abstract void doWrite(S element, XMLStreamWriter xsw) throws Exception;
}
