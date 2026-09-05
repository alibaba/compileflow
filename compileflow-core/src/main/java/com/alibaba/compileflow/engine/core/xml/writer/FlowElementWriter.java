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
 * Writes one flow element type to XML.
 *
 * @author yusu
 */
public interface FlowElementWriter<S extends Element> {
    void write(S element, XMLStreamWriter xsw) throws Exception;

    Class<S> getElementClass();
}
