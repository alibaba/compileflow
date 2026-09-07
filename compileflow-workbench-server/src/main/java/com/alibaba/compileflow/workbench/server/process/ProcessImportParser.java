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
package com.alibaba.compileflow.workbench.server.process;

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessText;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parses imported process definitions and rewrites editable draft identities without model conversion.
 *
 * @author yusu
 */
final class ProcessImportParser {
    private static final String BPMN_MODEL_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String MAX_ELEMENT_DEPTH_PROPERTY = "jdk.xml.maxElementDepth";
    private static final String MAX_ELEMENT_DEPTH = "128";

    private static ImportedProcess parseDocument(XMLStreamReader reader) throws XMLStreamException {
        ImportedProcess imported = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                imported = parseRoot(reader);
                break;
            }
        }
        if (imported == null) {
            throw new IllegalArgumentException("Imported XML has no document element");
        }
        while (reader.hasNext()) {
            reader.next();
        }
        return imported;
    }

    private static ImportedProcess parseRoot(XMLStreamReader reader) throws XMLStreamException {
        String root = reader.getLocalName();
        if ("bpm".equals(root) && (reader.getNamespaceURI() == null || reader.getNamespaceURI().isEmpty())) {
            return tbbpm(reader);
        }
        if ("definitions".equals(root) && BPMN_MODEL_NAMESPACE.equals(reader.getNamespaceURI())) {
            return bpmn(reader);
        }
        throw new IllegalArgumentException(
                "Imported XML must be a TBBPM <bpm> document or a BPMN 2.0 <definitions> document");
    }

    private static ImportedProcess tbbpm(XMLStreamReader reader) throws XMLStreamException {
        String code = attribute(reader, "code");
        String name = optionalAttribute(reader, "name");
        consumeElement(reader);
        return new ImportedProcess(code, name, ProcessModelType.TBBPM);
    }

    private static void consumeElement(XMLStreamReader reader) throws XMLStreamException {
        int depth = 1;
        while (depth > 0) {
            if (!reader.hasNext()) {
                throw new XMLStreamException("Unexpected end of XML element");
            }
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    private static ImportedProcess bpmn(XMLStreamReader reader) throws XMLStreamException {
        ImportedProcess imported = null;
        int depth = 1;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                if (depth == 2 && "process".equals(reader.getLocalName())
                        && BPMN_MODEL_NAMESPACE.equals(reader.getNamespaceURI())) {
                    if (imported != null) {
                        throw new IllegalArgumentException("Imported BPMN must contain exactly one process");
                    }
                    imported = new ImportedProcess(attribute(reader, "id"), optionalAttribute(reader, "name"),
                            ProcessModelType.BPMN);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
                if (depth == 0) {
                    break;
                }
            }
        }
        if (imported == null) {
            throw new IllegalArgumentException("Imported BPMN must contain exactly one process");
        }
        return imported;
    }

    private static String attribute(XMLStreamReader reader, String name) {
        String value = optionalAttribute(reader, name);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Imported " + reader.getLocalName() + " element requires a non-blank " + name + " attribute");
        }
        return value;
    }

    private static String optionalAttribute(XMLStreamReader reader, String name) {
        String value = reader.getAttributeValue(null, name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static XMLInputFactory secureInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(MAX_ELEMENT_DEPTH_PROPERTY, MAX_ELEMENT_DEPTH);
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("External XML resources are not allowed");
        });
        return factory;
    }

    ImportedDocument parse(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        String xml = ProcessText.decodeUtf8(bytes, "Imported XML");
        if (!xml.isEmpty() && xml.charAt(0) == '\uFEFF') {
            xml = xml.substring(1);
        }
        return new ImportedDocument(parse(xml), xml);
    }

    ImportedProcess parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("Imported XML must not be empty");
        }
        XMLInputFactory factory = secureInputFactory();
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
            try {
                return parseDocument(reader);
            } finally {
                reader.close();
            }
        } catch (XMLStreamException failure) {
            throw new IllegalArgumentException("Imported file is not well-formed XML", failure);
        }
    }

    String copyWithIdentity(String xml, ProcessModelType type, String code, String name) {
        if (xml == null || xml.isBlank()) {
            return xml;
        }
        String documentXml = xml.charAt(0) == '\uFEFF' ? xml.substring(1) : xml;
        ImportedProcess imported;
        try {
            imported = parse(documentXml);
        } catch (IllegalArgumentException invalidDraft) {
            return xml;
        }
        if (imported.type() != type) {
            return xml;
        }
        Document document;
        try {
            document = secureDocumentBuilder().parse(new InputSource(new StringReader(documentXml)));
        } catch (SAXException | IOException invalidDraft) {
            return xml;
        }
        Element root = document.getDocumentElement();
        Element process = type == ProcessModelType.TBBPM ? root : directBpmnProcess(root);
        process.setAttribute(type == ProcessModelType.TBBPM ? "code" : "id", code);
        process.setAttribute("name", name);
        if (type == ProcessModelType.BPMN) {
            String namespace = root.getAttribute("targetNamespace");
            renameProcessReferences(document, BPMN_MODEL_NAMESPACE, "participant", "processRef", namespace,
                    imported.code(), code);
            renameProcessReferences(document, "http://www.omg.org/spec/BPMN/20100524/DI", "BPMNPlane", "bpmnElement",
                    namespace, imported.code(), code);
        }
        try {
            TransformerFactory factory = TransformerFactory.newDefaultInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.VERSION, document.getXmlVersion());
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter output = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toString();
        } catch (TransformerException failure) {
            throw new IllegalStateException("Failed to rewrite process draft identity", failure);
        }
    }

    private static DocumentBuilder secureDocumentBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setAttribute(MAX_ELEMENT_DEPTH_PROPERTY, MAX_ELEMENT_DEPTH);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("External XML resources are not allowed");
            });
            builder.setErrorHandler(new DefaultHandler() {
                @Override
                public void fatalError(SAXParseException failure) throws SAXException {
                    throw failure;
                }
            });
            return builder;
        } catch (ParserConfigurationException failure) {
            throw new IllegalStateException("Cannot configure the secure draft XML parser", failure);
        }
    }

    private static Element directBpmnProcess(Element root) {
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && "process".equals(element.getLocalName())
                    && BPMN_MODEL_NAMESPACE.equals(element.getNamespaceURI())) {
                return element;
            }
        }
        throw new IllegalStateException("Validated BPMN document has no direct process element");
    }

    private static void renameProcessReferences(Document document, String elementNamespace, String elementName,
            String attributeName, String processNamespace, String oldCode, String newCode) {
        NodeList elements = document.getElementsByTagNameNS(elementNamespace, elementName);
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            String reference = element.getAttribute(attributeName);
            if (oldCode.equals(reference)) {
                element.setAttribute(attributeName, newCode);
            } else {
                int colon = reference.indexOf(':');
                if (colon > 0 && oldCode.equals(reference.substring(colon + 1)) && !processNamespace.isEmpty()
                        && processNamespace.equals(element.lookupNamespaceURI(reference.substring(0, colon)))) {
                    element.setAttribute(attributeName, reference.substring(0, colon + 1) + newCode);
                }
            }
        }
    }

    record ImportedProcess(String code, String name, ProcessModelType type) {}

    record ImportedDocument(ImportedProcess process, String xml) {}
}
