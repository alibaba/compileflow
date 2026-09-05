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

import com.alibaba.compileflow.engine.core.model.ForEachElement;
import com.alibaba.compileflow.engine.tbbpm.model.ForEachNode;
import com.alibaba.compileflow.engine.tbbpm.model.ForEachOutput;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;
import javax.xml.stream.XMLStreamWriter;

/**
 * XML writer for {@code foreach}.
 */
public final class ForEachWriter extends AbstractTbbpmNodeWriter<ForEachNode> {
    @Override
    protected String getName() {
        return TbbpmModelConstants.FOREACH;
    }

    @Override
    protected void enrichNodeAttr(ForEachNode node, XMLStreamWriter writer) throws Exception {
        writeAttribute(writer, TbbpmModelConstants.ATTRIBUTE_COLLECTION, node.getCollection());
        writeAttribute(writer, TbbpmModelConstants.ATTRIBUTE_ITEM, node.getItem());
        writeAttribute(writer, TbbpmModelConstants.ATTRIBUTE_ITEM_TYPE, node.getItemType());
        writeAttribute(writer, TbbpmModelConstants.ATTRIBUTE_INDEX, node.getIndex());
        if (node.getExecution() == ForEachElement.Execution.PARALLEL) {
            writeAttribute(writer, TbbpmModelConstants.ATTRIBUTE_EXECUTION, "parallel");
        }
    }

    @Override
    protected void writeMetadataElements(ForEachNode node, XMLStreamWriter writer) throws Exception {
        ForEachOutput output = node.getOutput();
        if (output != null) {
            writer.writeEmptyElement(TbbpmModelConstants.OUTPUT);
            writeAttribute(writer, TbbpmModelConstants.ATTRIBUTE_TARGET, output.getTarget());
            writeAttribute(writer, TbbpmModelConstants.ATTRIBUTE_SOURCE, output.getSource());
        }
    }

    @Override
    protected void enrichNodeElement(ForEachNode node, XMLStreamWriter writer) throws Exception {
        NodeContainerWriter.write(node, writer);
    }

    @Override
    public Class<ForEachNode> getElementClass() {
        return ForEachNode.class;
    }
}
