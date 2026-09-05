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

import com.alibaba.compileflow.engine.core.model.NodeContainer;
import com.alibaba.compileflow.engine.tbbpm.model.FlowNode;
import javax.xml.stream.XMLStreamWriter;

/**
 * XML writer for TBBPM node container elements.
 *
 * @author yusu
 */
final class NodeContainerWriter {
    private NodeContainerWriter() {
    }

    static void write(NodeContainer<? extends FlowNode> element, XMLStreamWriter xsw) throws Exception {
        for (FlowNode node : element.getAllNodes()) {
            TbbpmElementWriterRegistry.getInstance().write(node, xsw);
        }
    }
}
