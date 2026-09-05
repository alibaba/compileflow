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
package com.alibaba.compileflow.engine.bpmn.parser;

import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.core.model.Element;

/**
 * XML parser that consumes ignored BPMN text reference nodes such as incoming/outgoing.
 *
 * @author yusu
 */
final class IgnoredTextElementParser extends AbstractBpmnElementParser<IgnoredTextElementParser.IgnoredTextElement> {
    private final String name;

    IgnoredTextElementParser(String name) {
        this.name = name;
    }

    @Override
    protected IgnoredTextElement doParse(XmlSource xmlSource, ParseContext parseContext) {
        xmlSource.getElementText();
        return new IgnoredTextElement();
    }

    @Override
    protected void attachChildElement(Element childElement, IgnoredTextElement element, ParseContext parseContext) {}

    @Override
    public String getName() {
        return name;
    }

    static final class IgnoredTextElement implements Element {
    }
}
