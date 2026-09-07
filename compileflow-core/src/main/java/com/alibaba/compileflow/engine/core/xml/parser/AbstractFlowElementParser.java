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
package com.alibaba.compileflow.engine.core.xml.parser;

import com.alibaba.compileflow.engine.core.model.Element;

/**
 * Abstract parser for one flow element type.
 *
 * @author yusu
 */
public abstract class AbstractFlowElementParser<E extends Element> implements FlowElementParser<E> {
    @Override
    public E parse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        Element previousParent = parseContext.getParent();
        E element = doParse(xmlSource, parseContext);
        parseCommonAttributes(xmlSource, element, parseContext);
        parseContext.setParent(element);
        try {
            parseChildElements(xmlSource, element, parseContext);
        } finally {
            parseContext.setParent(previousParent);
        }
        return element;
    }

    protected abstract E doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception;

    protected abstract AbstractFlowElementParserRegistry getParserRegistry();

    protected void parseCommonAttributes(XmlSource xmlSource, E element, ParseContext parseContext) throws Exception {}

    protected void parseChildElements(XmlSource xmlSource, E element, ParseContext parseContext) throws Exception {
        String parentElementName = xmlSource.getLocalName();
        if (xmlSource.isEndElement(parentElementName)) {
            return;
        }
        for (
                String elementName = xmlSource.nextDirectChildElementName(parentElementName);
                elementName != null;
                elementName = xmlSource.nextDirectChildElementName(parentElementName)) {
            Element childElement = getParserRegistry().getParser(elementName).parse(xmlSource, parseContext);
            if (!attachPlatformChildElement(childElement, element, parseContext)) {
                attachChildElement(childElement, element, parseContext);
            }
        }
    }

    protected abstract boolean attachPlatformChildElement(Element childElement, E element, ParseContext parseContext);

    protected abstract void attachChildElement(Element childElement, E element, ParseContext parseContext);
}
