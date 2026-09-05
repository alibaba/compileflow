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
import com.alibaba.compileflow.engine.tbbpm.model.Transition;

/**
 * XML parser for TBBPM transition elements.
 *
 * @author wuxiang
 * @author yusu
 */
public class TransitionParser extends AbstractTbbpmElementParser<Transition> {
    @Override
    protected Transition doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        Transition transitionNode = new Transition();
        transitionNode.setName(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_NAME));
        transitionNode.setCondition(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_CONDITION));
        transitionNode.setTarget(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_TO));
        transitionNode.setGeometry(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_G));
        return transitionNode;
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.TRANSITION;
    }
}
