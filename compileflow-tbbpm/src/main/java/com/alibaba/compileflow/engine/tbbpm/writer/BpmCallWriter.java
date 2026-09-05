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

import com.alibaba.compileflow.engine.tbbpm.model.BpmCallNode;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;
import javax.xml.stream.XMLStreamWriter;

/**
 * XML writer for referenced TBBPM calls.
 *
 * @author yusu
 */
public class BpmCallWriter extends AbstractTbbpmNodeWriter<BpmCallNode> {
    @Override
    protected String getName() {
        return TbbpmModelConstants.BPM_CALL;
    }

    @Override
    protected void enrichNodeAttr(BpmCallNode node, XMLStreamWriter xsw) throws Exception {
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_CODE, node.getCode());
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_CLASSPATH, node.getClasspath());
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_VERSION, node.getVersion());
    }

    @Override
    protected void enrichNodeElement(BpmCallNode node, XMLStreamWriter xsw) throws Exception {
        writeMappings(node.getInputMappings(), node.getOutputMappings(), xsw, false);
    }

    @Override
    public Class<BpmCallNode> getElementClass() {
        return BpmCallNode.class;
    }
}
