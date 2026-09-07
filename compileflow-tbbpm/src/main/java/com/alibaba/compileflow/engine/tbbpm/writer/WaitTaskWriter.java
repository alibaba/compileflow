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
import com.alibaba.compileflow.engine.tbbpm.model.WaitTaskNode;
import javax.xml.stream.XMLStreamWriter;

/**
 * XML writer for TBBPM wait task nodes.
 *
 * @author yusu
 */
public class WaitTaskWriter extends AbstractTbbpmNodeWriter<WaitTaskNode> {
    @Override
    protected String getName() {
        return TbbpmModelConstants.WAIT_TASK;
    }

    @Override
    protected void enrichNodeAttr(WaitTaskNode node, XMLStreamWriter xsw) throws Exception {
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_TIMEOUT, node.getTimeout());
    }

    @Override
    public Class<WaitTaskNode> getElementClass() {
        return WaitTaskNode.class;
    }
}
