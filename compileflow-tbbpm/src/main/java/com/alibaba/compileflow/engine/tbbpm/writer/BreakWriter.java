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

import com.alibaba.compileflow.engine.tbbpm.model.BreakNode;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;
import javax.xml.stream.XMLStreamWriter;

/**
 * XML writer for TBBPM break nodes.
 *
 * @author yusu
 */
public class BreakWriter extends AbstractTbbpmNodeWriter<BreakNode> {
    @Override
    protected String getName() {
        return TbbpmModelConstants.BREAK;
    }

    @Override
    protected void enrichNodeAttr(BreakNode node, XMLStreamWriter xsw) throws Exception {
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_CONDITION, node.getCondition());
    }

    @Override
    public Class<BreakNode> getElementClass() {
        return BreakNode.class;
    }
}
