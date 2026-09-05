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
package com.alibaba.compileflow.engine.tbbpm.parser.var;

import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.core.model.variable.Variable;
import com.alibaba.compileflow.engine.tbbpm.parser.AbstractTbbpmElementParser;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;

/**
 * XML parser for TBBPM variable elements.
 *
 * @author wuxiang
 * @author yusu
 */
public class VarParser extends AbstractTbbpmElementParser<Variable> {
    @Override
    protected Variable doParse(XmlSource xmlSource, ParseContext parseContext) {
        Variable varNode = new Variable();
        varNode.setName(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_NAME));
        varNode.setInOutType(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_IN_OUT_TYPE));
        varNode.setDescription(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_DESCRIPTION));
        varNode.setDataType(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_DATA_TYPE));
        varNode.setDefaultValue(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_DEFAULT_VALUE));
        return varNode;
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.VAR;
    }
}
