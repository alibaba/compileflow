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
import com.alibaba.compileflow.engine.tbbpm.model.BpmCallNode;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;

/**
 * XML parser for referenced TBBPM calls.
 *
 * @author yusu
 */
public class BpmCallParser extends AbstractTbbpmElementParser<BpmCallNode> {
    @Override
    protected BpmCallNode doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        BpmCallNode call = new BpmCallNode();
        parseCommonNodeAttributes(xmlSource, call);
        call.setCode(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_CODE));
        call.setClasspath(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_CLASSPATH));
        call.setVersion(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_VERSION));
        return call;
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.BPM_CALL;
    }
}
