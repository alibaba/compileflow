/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.builder.converter.parser;

import com.alibaba.compileflow.engine.core.builder.converter.parser.model.ParseContext;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.XMLSource;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.XMLStreamReaderSource;
import com.alibaba.compileflow.engine.core.builder.converter.parser.provider.AbstractFlowElementParserProvider;
import com.alibaba.compileflow.engine.core.builder.converter.parser.provider.ParserProviderSupport;
import com.alibaba.compileflow.engine.core.definition.Element;

/**
 * @author wuxiang
 * @author yusu
 */
public abstract class AbstractFlowElementParser<E extends Element> implements FlowElementParser<E>,
        ParserProviderSupport<AbstractFlowElementParserProvider> {

    @Override
    public E parse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        E element = doParse(xmlSource, parseContext);
        parseContext.setParent(element);
        parseChildElements(xmlSource, element, parseContext);
        return element;
    }

    protected abstract E doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception;

    protected void parseChildElements(XMLSource xmlSource, E element, ParseContext parseContext) throws Exception {
        String parentElementName = xmlSource.getCurrentElementName();
        if (xmlSource.isEndElement(parentElementName)) {
            return;
        }

        // Prefer direct-child iteration when available for better readability and robustness
        if (xmlSource instanceof XMLStreamReaderSource) {
            XMLStreamReaderSource reader = (XMLStreamReaderSource) xmlSource;
            for (String elementName = reader.nextDirectChildElementName(parentElementName);
                 elementName != null;
                 elementName = reader.nextDirectChildElementName(parentElementName)) {
                Element childElement = getParserProvider().getParser(elementName)
                        .parse(xmlSource, parseContext);
                if (!attachPlatformChildElement(childElement, element, parseContext)) {
                    attachChildElement(childElement, element, parseContext);
                }
            }
            return;
        }

        // Fallback to the original advancing loop
        while (xmlSource.hasNext()) {
            String elementName = xmlSource.nextElementName();
            if (elementName != null) {
                Element childElement = getParserProvider().getParser(elementName)
                        .parse(xmlSource, parseContext);
                if (!attachPlatformChildElement(childElement, element, parseContext)) {
                    attachChildElement(childElement, element, parseContext);
                }
            }
        }
    }

    protected abstract boolean attachPlatformChildElement(Element childElement, E element, ParseContext parseContext);

    protected abstract void attachChildElement(Element childElement, E element, ParseContext parseContext);

}
