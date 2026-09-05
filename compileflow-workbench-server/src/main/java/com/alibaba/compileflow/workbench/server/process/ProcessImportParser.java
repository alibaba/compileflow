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
import java.io.StringReader;
import java.util.Objects;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Parses imported process definition files for the Workbench.
 *
 * @author yusu
 */
final class ProcessImportParser {
    private static final String BPMN_MODEL_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";

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
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
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

    record ImportedProcess(String code, String name, ProcessModelType type) {}

    record ImportedDocument(ImportedProcess process, String xml) {}
}
