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
import com.alibaba.compileflow.engine.tbbpm.model.AutoTaskNode;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;

/**
 * XML parser for TBBPM auto task nodes.
 *
 * @author wuxiang
 * @author yusu
 */
public class AutoTaskParser extends AbstractTbbpmElementParser<AutoTaskNode> {
    @Override
    protected AutoTaskNode doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        AutoTaskNode autoTaskNode = new AutoTaskNode();
        parseCommonNodeAttributes(xmlSource, autoTaskNode);
        return autoTaskNode;
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.AUTO_TASK;
    }
}
