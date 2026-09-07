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
package com.alibaba.compileflow.engine.tbbpm.parser.action;

import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.core.model.action.ScriptSource;
import com.alibaba.compileflow.engine.tbbpm.parser.AbstractTbbpmElementParser;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;

/**
 * Parser for the source payload of an explicit Script action.
 *
 * @author yusu
 */
public final class ScriptSourceParser extends AbstractTbbpmElementParser<ScriptSource> {
    @Override
    protected ScriptSource doParse(XmlSource xmlSource, ParseContext parseContext) {
        ScriptSource source = new ScriptSource();
        source.setSource(xmlSource.getElementText());
        return source;
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.CODE;
    }
}
