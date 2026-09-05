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
package com.alibaba.compileflow.engine.tbbpm.parser;

import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;
import com.alibaba.compileflow.engine.tbbpm.model.WaitEventTaskNode;

/**
 * XML parser for TBBPM wait event task nodes.
 *
 * @author wuxiang
 */
public class WaitEventTaskParser extends AbstractTbbpmElementParser<WaitEventTaskNode> {
    @Override
    public String getName() {
        return TbbpmModelConstants.WAIT_EVENT_TASK;
    }

    @Override
    protected WaitEventTaskNode doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        WaitEventTaskNode waitEventNode = new WaitEventTaskNode();
        parseCommonNodeAttributes(xmlSource, waitEventNode);
        waitEventNode.setEvent(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_EVENT));
        waitEventNode.setTimeout(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_TIMEOUT));
        return waitEventNode;
    }
}
