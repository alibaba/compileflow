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

import com.alibaba.compileflow.engine.core.model.Element;
import com.alibaba.compileflow.engine.core.model.mapping.OutputMapping;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.tbbpm.model.ForEachNode;
import com.alibaba.compileflow.engine.tbbpm.model.ForEachOutput;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;
import com.alibaba.compileflow.engine.tbbpm.parser.AbstractTbbpmElementParser;

/**
 * Parser for an explicit boundary output mapping.
 *
 * @author yusu
 */
public final class OutputParser extends AbstractTbbpmElementParser<Element> {
    @Override
    protected Element doParse(XmlSource source, ParseContext context) {
        if (context.getParent() instanceof ForEachNode) {
            ForEachOutput output = new ForEachOutput();
            output.setTarget(source.getString(TbbpmModelConstants.ATTRIBUTE_TARGET));
            output.setSource(source.getString(TbbpmModelConstants.ATTRIBUTE_SOURCE));
            return output;
        }
        OutputMapping output = new OutputMapping();
        output.setSource(source.getString(TbbpmModelConstants.ATTRIBUTE_SOURCE));
        output.setDataType(source.getString(TbbpmModelConstants.ATTRIBUTE_DATA_TYPE));
        output.setTarget(source.getString(TbbpmModelConstants.ATTRIBUTE_TARGET));
        return output;
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.OUTPUT;
    }
}
