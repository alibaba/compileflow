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

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * {@link XmlSource} backed by an {@link XMLStreamReader}.
 *
 * @author yusu
 */
public final class XmlStreamReaderSource implements XmlSource {
    private static final String COMPILE_FLOW_NAMESPACE = "http://www.compileflow.org";
    private final XMLStreamReader xmlStreamReader;

    public static XmlStreamReaderSource of(XMLStreamReader xmlStreamReader) {
        return new XmlStreamReaderSource(xmlStreamReader);
    }

    private XmlStreamReaderSource(XMLStreamReader xmlStreamReader) {
        this.xmlStreamReader = Objects.requireNonNull(xmlStreamReader, "xmlStreamReader");
    }

    @Override
    public boolean hasNext() throws XMLStreamException {
        return xmlStreamReader.hasNext() && XMLStreamReader.END_ELEMENT != xmlStreamReader.next();
    }

    @Override
    public String nextElementName() throws XMLStreamException {
        return xmlStreamReader.isStartElement() ? xmlStreamReader.getLocalName() : null;
    }

    @Override
    public String getLocalName() {
        return xmlStreamReader.getLocalName();
    }

    @Override
    public String getNamespaceURI() {
        return xmlStreamReader.getNamespaceURI();
    }

    @Override
    public String getNamespaceURI(String prefix) {
        return xmlStreamReader.getNamespaceURI(prefix);
    }

    @Override
    public String getPrefix() {
        return xmlStreamReader.getPrefix();
    }

    @Override
    public String getElementText() {
        String elementName = xmlStreamReader.getLocalName();
        try {
            return xmlStreamReader.getElementText();
        } catch (XMLStreamException e) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002, "Element must contain text only: " + elementName,
                    e);
        }
    }

    @Override
    public String getAttributeLocalName(int index) {
        return xmlStreamReader.getAttributeLocalName(index);
    }

    @Override
    public String getAttributeValue(int index) {
        return xmlStreamReader.getAttributeValue(index);
    }

    @Override
    public String getAttributeNamespace(int index) {
        return xmlStreamReader.getAttributeNamespace(index);
    }

    @Override
    public String getAttributePrefix(int index) {
        return xmlStreamReader.getAttributePrefix(index);
    }

    @Override
    public int getAttributeCount() {
        return xmlStreamReader.getAttributeCount();
    }

    public String getString(String namespaceURI, String name) {
        // StAX treats null as a namespace wildcard, not an unqualified attribute.
        return xmlStreamReader.getAttributeValue(namespaceURI == null ? XMLConstants.NULL_NS_URI : namespaceURI, name);
    }

    @Override
    public String getString(String name) {
        return getString(null, name);
    }

    @Override
    public String getCfString(String name) {
        return getString(COMPILE_FLOW_NAMESPACE, name);
    }

    @Override
    public boolean isEndElement(String name) {
        return xmlStreamReader.isEndElement() && name.equals(xmlStreamReader.getLocalName());
    }

    @Override
    public void skipCurrentElement() throws XMLStreamException {
        if (!xmlStreamReader.isStartElement()) {
            throw new XMLStreamException("Expected a start element before skipping XML subtree");
        }
        int depth = 1;
        while (depth > 0 && xmlStreamReader.hasNext()) {
            int event = xmlStreamReader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        if (depth != 0) {
            throw new XMLStreamException("Unexpected end of XML while skipping element subtree");
        }
    }

    @Override
    public String nextDirectChildElementName(String parentElementName) throws XMLStreamException {
        while (xmlStreamReader.hasNext()) {
            int event = xmlStreamReader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                return xmlStreamReader.getLocalName();
            } else if (event == XMLStreamConstants.END_ELEMENT
                    && xmlStreamReader.getLocalName().equals(parentElementName)) {
                return null;
            }
        }
        return null;
    }
}
