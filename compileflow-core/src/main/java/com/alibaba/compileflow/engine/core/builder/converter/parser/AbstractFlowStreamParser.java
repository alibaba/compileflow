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

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.*;
import com.alibaba.compileflow.engine.core.builder.converter.parser.provider.AbstractFlowElementParserProvider;
import com.alibaba.compileflow.engine.core.definition.Element;
import com.alibaba.compileflow.engine.core.definition.var.IVar;
import com.alibaba.compileflow.engine.core.infrastructure.utils.ClassLoaderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author wuxiang
 * @author yusu
 */
public abstract class AbstractFlowStreamParser<R> implements FlowStreamParser<R> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFlowStreamParser.class);

    @Override
    public R parse(FlowStreamSource source) {
        return parse(source, new ParseConfig());
    }

    @Override
    public R parse(FlowStreamSource source, ParseConfig parseConfig) {
        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();

        try {
            if (parseConfig.isValidateSchema()) {
                try (InputStream schemaStream = source.getFlow()) {
                    validateSchema(schemaStream);
                } catch (Exception schemaEx) {
                    if (parseConfig.isStrictSchemaValidation()) {
                        throw schemaEx;
                    }
                    // tolerant mode: log and continue
                    LOGGER.warn("Schema validation skipped due to error (resource: {}): {}", getXSD(), schemaEx.toString());
                }
            }

            try (InputStream in = source.getFlow()) {
                if (in == null) {
                    throw new CompileFlowException.ResourceException(
                            ErrorCode.CF_RESOURCE_002,
                            "Flow stream is null for source: " + source.getCode(),
                            null
                    );
                }
                InputStreamReader inputStreamReader = new InputStreamReader(in, parseConfig.getEncoding());
                XMLStreamReader xmlStreamReader = xmlInputFactory.createXMLStreamReader(inputStreamReader);
                xmlStreamReader = xmlInputFactory.createFilteredReader(
                        xmlStreamReader, reader -> XMLStreamConstants.START_DOCUMENT == reader.getEventType()
                                || XMLStreamConstants.END_DOCUMENT == reader.getEventType()
                                || XMLStreamConstants.START_ELEMENT == reader.getEventType()
                                || XMLStreamConstants.END_ELEMENT == reader.getEventType());

                try {
                    return parseFlowModel(XMLStreamReaderSource.of(xmlStreamReader));
                } finally {
                    xmlStreamReader.close();
                }
            }
        } catch (UnsupportedEncodingException e) {
            throw new CompileFlowException.ConfigurationException(
                    ErrorCode.CF_CONFIG_001,
                    "Unsupported encoding " + parseConfig.getEncoding(),
                    e
            );
        } catch (CompileFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new CompileFlowException.SystemException(
                    ErrorCode.CF_RESOURCE_002,
                    "Failed to parse flow (code=" + source.getCode() + ", encoding=" + parseConfig.getEncoding() + ")",
                    e
            );
        }
    }

    protected R parseFlowModel(XMLSource xmlSource) throws Exception {
        ParseContext parseContext = new ParseContext();
        while (xmlSource.hasNext()) {
            String elementName = xmlSource.nextElementName();
            if (elementName != null) {
                getFlowElementParserProvider().getParser(elementName).parse(xmlSource, parseContext);
            }
        }
        return convertToFlowModel(parseContext.getTop());
    }

    protected abstract AbstractFlowElementParserProvider getFlowElementParserProvider();

    protected abstract R convertToFlowModel(Element top);

    private void validateSchema(InputStream flowStream) throws Exception {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = factory.newSchema(ClassLoaderUtils.getResource(getXSD()));

        if (schema == null) {
            throw new CompileFlowException.ResourceException(
                    ErrorCode.CF_RESOURCE_002,
                    "XSD could not be found",
                    null
            );
        }

        Validator validator = schema.newValidator();
        validator.validate(new StreamSource(flowStream));
    }

    protected abstract String getXSD();

    protected List<IVar> buildTypeVars(List<IVar> vars, String varType) {
        return vars.stream().filter(var -> varType.equals(var.getInOutType())).collect(Collectors.toList());
    }

}
