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

import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;
import com.alibaba.compileflow.engine.tbbpm.model.WhileNode;
import javax.xml.stream.XMLStreamWriter;

/**
 * XML writer for {@code while}.
 */
public final class WhileWriter extends AbstractTbbpmNodeWriter<WhileNode> {
    @Override
    protected String getName() {
        return TbbpmModelConstants.WHILE;
    }

    @Override
    protected void enrichNodeAttr(WhileNode node, XMLStreamWriter writer) throws Exception {
        writeAttribute(writer, TbbpmModelConstants.ATTRIBUTE_CONDITION, node.getCondition());
        writeAttribute(writer, TbbpmModelConstants.ATTRIBUTE_INDEX, node.getIndex());
        if (node.getMaxIterations() != null) {
            writeAttribute(writer, TbbpmModelConstants.ATTRIBUTE_MAX_ITERATIONS, String.valueOf(node.getMaxIterations()));
        }
    }

    @Override
    protected void enrichNodeElement(WhileNode node, XMLStreamWriter writer) throws Exception {
        NodeContainerWriter.write(node, writer);
    }

    @Override
    public Class<WhileNode> getElementClass() {
        return WhileNode.class;
    }
}
