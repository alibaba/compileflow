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

import com.alibaba.compileflow.engine.bpmn.model.BpmnModelConstants;
import com.alibaba.compileflow.engine.bpmn.model.ExtensionElements;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.core.xml.parser.XmlStreamReaderSource;
import com.alibaba.compileflow.engine.core.model.Element;
import com.alibaba.compileflow.engine.core.model.extension.AbstractExtensionElement;
import com.alibaba.compileflow.engine.core.model.extension.ExtensionAttribute;
import com.alibaba.compileflow.engine.core.model.extension.ExtensionElement;

/**
 * XML parser for BPMN extensionElements containers.
 *
 * @author yusu
 */
public class ExtensionElementsParser extends AbstractBpmnElementParser<ExtensionElements> {
    @Override
    protected ExtensionElements doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        ExtensionElements extensionElements = new ExtensionElements();
        if (xmlSource instanceof XmlStreamReaderSource) {
            XmlStreamReaderSource reader = (XmlStreamReaderSource) xmlSource;
            String parentName = BpmnModelConstants.BPMN_ELEMENT_EXTENSION_ELEMENTS;
            for (
                    String child = reader.nextDirectChildElementName(parentName);
                    child != null;
                    child = reader.nextDirectChildElementName(parentName)) {
                ExtensionElement extensionElement = parseExtensionElement(xmlSource);
                attachToParentIfSupported(parseContext, extensionElement);
                extensionElements.addExtensionElements(extensionElement);
            }
        } else {
            while (!xmlSource.endWith(BpmnModelConstants.BPMN_ELEMENT_EXTENSION_ELEMENTS)) {
                if (xmlSource.hasNext()) {
                    ExtensionElement extensionElement = parseExtensionElement(xmlSource);
                    attachToParentIfSupported(parseContext, extensionElement);
                    extensionElements.addExtensionElements(extensionElement);
                }
            }
        }
        return extensionElements;
    }

    @Override
    protected void parseChildElements(XmlSource xmlSource, ExtensionElements element, ParseContext parseContext) {}

    @Override
    protected void attachChildElement(Element childElement, ExtensionElements element, ParseContext parseContext) {}

    private ExtensionElement parseExtensionElement(XmlSource xmlSource) throws Exception {
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

        if (isTextExtension(extensionElement)) {
            extensionElement.setTextContent(xmlSource.getElementText());
            return extensionElement;
        }

        if (xmlSource instanceof XmlStreamReaderSource) {
            XmlStreamReaderSource reader = (XmlStreamReaderSource) xmlSource;
            String currentName = extensionElement.getName();
            for (
                    String child = reader.nextDirectChildElementName(currentName);
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

    private boolean isTextExtension(ExtensionElement element) {
        if (!BpmnModelConstants.CF_NS.equals(element.getNamespaceURI())) {
            return false;
        }
        return BpmnModelConstants.BPMN_EXT_ATTRIBUTE_CODE.equals(element.getName())
                || "import".equals(element.getName());
    }

    private void attachToParentIfSupported(ParseContext parseContext, ExtensionElement extensionElement) {
        Element parent = parseContext.getParent();
        if (parent instanceof AbstractExtensionElement) {
            ((AbstractExtensionElement) parent).addExtensionElement(extensionElement);
        }
    }

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_EXTENSION_ELEMENTS;
    }
}
