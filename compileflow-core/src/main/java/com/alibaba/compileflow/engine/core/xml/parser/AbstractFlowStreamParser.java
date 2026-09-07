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
import com.alibaba.compileflow.engine.core.model.Element;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;

/**
 * Abstract XML stream parser that builds a flow model from a {@link FlowSource}.
 *
 * @author yusu
 */
public abstract class AbstractFlowStreamParser<R> {
    private static final String MAX_ELEMENT_DEPTH_PROPERTY = "jdk.xml.maxElementDepth";
    private static final String MAX_ELEMENT_DEPTH = "128";
    private volatile Schema schema;

    private static void configureSecureXmlInputFactory(XMLInputFactory factory) {
        try {
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(MAX_ELEMENT_DEPTH_PROPERTY, MAX_ELEMENT_DEPTH);
        } catch (IllegalArgumentException failure) {
            throw new CompileFlowException(ErrorCode.CF_CONFIG_003,
                    "XMLInputFactory does not support the required secure-processing properties", failure);
        }
    }

    public R parse(FlowSource source) {
        return parse(source, SchemaValidation.STRICT);
    }

    public R parse(FlowSource source, SchemaValidation schemaValidation) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(schemaValidation, "schemaValidation must not be null");
        XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
        configureSecureXmlInputFactory(xmlInputFactory);

        try {
            if (schemaValidation != SchemaValidation.DISABLED) {
                try (InputStream schemaStream = source.openStream()) {
                    validateSchema(schemaStream);
                }
            }

            try (InputStream in = source.openStream()) {
                XMLStreamReader xmlStreamReader = xmlInputFactory.createXMLStreamReader(in);
                xmlStreamReader = xmlInputFactory.createFilteredReader(xmlStreamReader, reader -> XMLStreamConstants.START_DOCUMENT == reader.getEventType()
                        || XMLStreamConstants.END_DOCUMENT == reader.getEventType()
                        || XMLStreamConstants.START_ELEMENT == reader.getEventType()
                        || XMLStreamConstants.END_ELEMENT == reader.getEventType());

                try {
                    return parseFlowModel(XmlStreamReaderSource.of(xmlStreamReader));
                } finally {
                    xmlStreamReader.close();
                }
            }
        } catch (CompileFlowException failure) {
            throw failure;
        } catch (XMLStreamException failure) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "Flow XML is malformed: code=" + source.getCode() + ", " + failure.getMessage(), failure);
        } catch (IllegalArgumentException failure) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "Flow definition is invalid: code=" + source.getCode() + ", " + failure.getMessage(), failure);
        } catch (Exception failure) {
            throw new CompileFlowException(ErrorCode.CF_RESOURCE_002,
                    "Failed to parse flow (code=" + source.getCode() + ")", failure);
        }
    }

    protected R parseFlowModel(XmlSource xmlSource) throws Exception {
        ParseContext parseContext = new ParseContext();
        while (xmlSource.hasNext()) {
            String elementName = xmlSource.nextElementName();
            if (elementName != null) {
                getFlowElementParserRegistry().getParser(elementName).parse(xmlSource, parseContext);
            }
        }
        return convertToFlowModel(parseContext.getTop());
    }

    protected abstract AbstractFlowElementParserRegistry getFlowElementParserRegistry();

    protected abstract R convertToFlowModel(Element top);

    private void validateSchema(InputStream flowStream) throws Exception {
        Validator validator = schema().newValidator();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        validator.setProperty(MAX_ELEMENT_DEPTH_PROPERTY, MAX_ELEMENT_DEPTH);
        try {
            validator.validate(new StreamSource(flowStream));
        } catch (SAXException exception) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "Flow schema validation failed: " + exception.getMessage(), exception);
        }
    }

    private Schema schema() throws Exception {
        Schema current = schema;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = schema;
            if (current == null) {
                current = createSchema();
                schema = current;
            }
            return current;
        }
    }

    private Schema createSchema() throws Exception {
        String rootSchema = getXSD();
        ClassLoader schemaClassLoader = getClass().getClassLoader();
        if (schemaClassLoader == null) {
            throw new CompileFlowException.ResourceException(ErrorCode.CF_RESOURCE_002,
                    "No ClassLoader is available for schema resource: " + rootSchema, null);
        }
        URL rootSchemaLocation = schemaClassLoader.getResource(rootSchema);
        if (rootSchemaLocation == null) {
            throw new CompileFlowException.ResourceException(ErrorCode.CF_RESOURCE_002,
                    "Schema resource not found: " + rootSchema, null);
        }

        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try (ClasspathSchemaResourceResolver resolver =
                new ClasspathSchemaResourceResolver(rootSchema, schemaClassLoader);
                InputStream rootStream = new BufferedInputStream(rootSchemaLocation.openStream())) {
            factory.setResourceResolver(resolver);
            StreamSource source = new StreamSource(rootStream);
            source.setSystemId(rootSchemaLocation.toExternalForm());
            return factory.newSchema(source);
        }
    }

    protected abstract String getXSD();

    private static final class ClasspathSchemaResourceResolver implements LSResourceResolver, AutoCloseable {
        private final String basePath;
        private final ClassLoader classLoader;
        private final List<InputStream> openedStreams = new ArrayList<>();

        private ClasspathSchemaResourceResolver(String rootSchema, ClassLoader classLoader) {
            int slash = rootSchema == null ? -1 : rootSchema.lastIndexOf('/');
            this.basePath = slash < 0 ? "" : rootSchema.substring(0, slash + 1);
            this.classLoader = classLoader;
        }

        private static CompileFlowException.ResourceException rejectedSchemaReference(String systemId) {
            return new CompileFlowException.ResourceException(ErrorCode.CF_RESOURCE_002,
                    "Schema reference must be a relative classpath resource: " + systemId, null);
        }

        @Override
        public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId,
                String baseURI) {
            String resourceName = resolveResourceName(systemId);
            if (resourceName == null) {
                return null;
            }
            URL location = classLoader.getResource(resourceName);
            if (location == null) {
                throw new CompileFlowException.ResourceException(ErrorCode.CF_RESOURCE_002,
                        "Imported schema resource not found: " + resourceName, null);
            }
            try {
                InputStream stream = new BufferedInputStream(location.openStream());
                openedStreams.add(stream);
                return new ClasspathSchemaInput(publicId, location.toExternalForm(), stream);
            } catch (IOException failure) {
                throw new CompileFlowException.ResourceException(ErrorCode.CF_RESOURCE_002,
                        "Failed to open imported schema resource: " + resourceName, failure);
            }
        }

        private String resolveResourceName(String systemId) {
            if (systemId == null || systemId.trim().isEmpty()) {
                return null;
            }
            String normalized = systemId.trim().replace('\\', '/');
            if (normalized.startsWith("/") || normalized.indexOf(':') >= 0) {
                throw rejectedSchemaReference(systemId);
            }
            for (String segment : normalized.split("/")) {
                if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                    throw rejectedSchemaReference(systemId);
                }
            }
            return basePath + normalized;
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            for (InputStream stream : openedStreams) {
                try {
                    stream.close();
                } catch (IOException closeFailure) {
                    if (failure == null) {
                        failure = closeFailure;
                    } else {
                        failure.addSuppressed(closeFailure);
                    }
                }
            }
            openedStreams.clear();
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class ClasspathSchemaInput implements LSInput {
        private final String publicId;
        private final String systemId;
        private final InputStream byteStream;

        private ClasspathSchemaInput(String publicId, String systemId, InputStream byteStream) {
            this.publicId = publicId;
            this.systemId = systemId;
            this.byteStream = byteStream;
        }

        @Override
        public Reader getCharacterStream() {
            return null;
        }

        @Override
        public void setCharacterStream(Reader characterStream) {
            // Byte stream is the canonical source for classpath schemas.
        }

        @Override
        public InputStream getByteStream() {
            return byteStream;
        }

        @Override
        public void setByteStream(InputStream byteStream) {
            // Immutable input; the parser reads the constructor-provided stream.
        }

        @Override
        public String getStringData() {
            return null;
        }

        @Override
        public void setStringData(String stringData) {
            // Byte stream is the canonical source for classpath schemas.
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public void setSystemId(String systemId) {
            // Immutable input; the parser reads the constructor-provided system id.
        }

        @Override
        public String getPublicId() {
            return publicId;
        }

        @Override
        public void setPublicId(String publicId) {
            // Immutable input; the parser reads the constructor-provided public id.
        }

        @Override
        public String getBaseURI() {
            return null;
        }

        @Override
        public void setBaseURI(String baseURI) {
            // Classpath schemas are resolved by resource name, not base URI.
        }

        @Override
        public String getEncoding() {
            return "UTF-8";
        }

        @Override
        public void setEncoding(String encoding) {
            // Schema resources are UTF-8.
        }

        @Override
        public boolean getCertifiedText() {
            return false;
        }

        @Override
        public void setCertifiedText(boolean certifiedText) {
            // Not used by schema validation.
        }
    }
}
