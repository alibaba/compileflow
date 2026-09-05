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
import com.alibaba.compileflow.engine.tbbpm.model.InclusiveNode;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;

/**
 * XML parser for TBBPM inclusive gateway nodes.
 *
 * @author yusu
 */
public class InclusiveParser extends AbstractTbbpmElementParser<InclusiveNode> {
    @Override
    protected InclusiveNode doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        InclusiveNode inclusiveNode = new InclusiveNode();
        parseCommonNodeAttributes(xmlSource, inclusiveNode);
        return inclusiveNode;
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.INCLUSIVE;
    }
}
