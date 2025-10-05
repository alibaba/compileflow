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
package com.alibaba.compileflow.engine.bpmn.builder.converter.parser;

import com.alibaba.compileflow.engine.bpmn.definition.BpmnModelConstants;
import com.alibaba.compileflow.engine.bpmn.definition.ExtensionElements;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.ParseContext;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.XMLSource;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.XMLStreamReaderSource;
import com.alibaba.compileflow.engine.core.definition.Element;
import com.alibaba.compileflow.engine.core.definition.extension.BaseExtensionElement;
import com.alibaba.compileflow.engine.core.definition.extension.ExtensionAttribute;
import com.alibaba.compileflow.engine.core.definition.extension.ExtensionElement;

/**
 * @author wuxiang
 * @author yusu
 */
public class ExtensionElementsParser extends AbstractBpmnElementParser<ExtensionElements> {

    @Override
    protected ExtensionElements doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        ExtensionElements extensionElements = new ExtensionElements();
        if (xmlSource instanceof XMLStreamReaderSource) {
            XMLStreamReaderSource reader = (XMLStreamReaderSource) xmlSource;
            String parentName = BpmnModelConstants.BPMN_ELEMENT_EXTENSION_ELEMENTS;
            for (String child = reader.nextDirectChildElementName(parentName); child != null;
                 child = reader.nextDirectChildElementName(parentName)) {
                ExtensionElement extensionElement = parseExtensionElement(xmlSource);
                BaseExtensionElement parentElement = (BaseExtensionElement) parseContext.getParent();
                parentElement.addExtensionElement(extensionElement);
                extensionElements.addExtensionElements(extensionElement);
            }
        } else {
            while (!xmlSource.endWith(BpmnModelConstants.BPMN_ELEMENT_EXTENSION_ELEMENTS)) {
                if (xmlSource.hasNext()) {
                    ExtensionElement extensionElement = parseExtensionElement(xmlSource);
                    BaseExtensionElement parentElement = (BaseExtensionElement) parseContext.getParent();
                    parentElement.addExtensionElement(extensionElement);
                    extensionElements.addExtensionElements(extensionElement);
                }
            }
        }
        return extensionElements;
    }

    @Override
    protected void parseChildElements(XMLSource xmlSource, ExtensionElements element, ParseContext parseContext) {

    }

    @Override
    protected void attachChildElement(Element childElement, ExtensionElements element, ParseContext parseContext) {

    }

    private ExtensionElement parseExtensionElement(XMLSource xmlSource) throws Exception {
        ExtensionElement extensionElement = new ExtensionElement();
        extensionElement.setName(xmlSource.getLocalName());
        extensionElement.setNamespaceURI(xmlSource.getNamespaceURI());
        extensionElement.setNamespacePrefix(xmlSource.getPrefix());

        for (int i = 0; i < xmlSource.getAttributeCount(); i++) {
            ExtensionAttribute extensionAttribute = new ExtensionAttribute();
            extensionAttribute.setName(xmlSource.getAttributeLocalName(i));
            extensionAttribute.setValue(xmlSource.getAttributeValue(i));
            extensionAttribute.setNamespace(xmlSource.getAttributeNamespace(i));
            extensionAttribute.setNamespacePrefix(xmlSource.getAttributePrefix(i));
            extensionElement.addAttribute(extensionAttribute);
        }

        if (xmlSource instanceof XMLStreamReaderSource) {
            XMLStreamReaderSource reader = (XMLStreamReaderSource) xmlSource;
            String currentName = extensionElement.getName();
            for (String child = reader.nextDirectChildElementName(currentName);
                 child != null;
                 child = reader.nextDirectChildElementName(currentName)) {
                ExtensionElement childExtensionElement = parseExtensionElement(xmlSource);
                extensionElement.addChildElement(childExtensionElement);
            }
        } else {
            while (xmlSource.hasNext()) {
                ExtensionElement childExtensionElement = parseExtensionElement(xmlSource);
                extensionElement.addChildElement(childExtensionElement);
            }
        }

        return extensionElement;
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_EXTENSION_ELEMENTS;
    }

}
