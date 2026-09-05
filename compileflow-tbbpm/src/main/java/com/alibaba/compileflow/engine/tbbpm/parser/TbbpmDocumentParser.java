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
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmDocument;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;

/**
 * Parser for the TBBPM document root.
 *
 * @author wuxiang
 * @author yusu
 */
public class TbbpmDocumentParser extends AbstractTbbpmElementParser<TbbpmDocument> {
    @Override
    protected TbbpmDocument doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        TbbpmDocument document = new TbbpmDocument();
        document.setCode(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_CODE));
        document.setName(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_NAME));
        document.setDescription(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_DESCRIPTION));

        parseContext.setTop(document);
        return document;
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.BPM;
    }
}
