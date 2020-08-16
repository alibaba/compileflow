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
package com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import org.apache.commons.lang3.StringUtils;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * @author wuxiang
 * @author yusu
 */
public class XMLStreamReaderSource implements XMLSource {

    private static final String COMPILE_FLOW_NAMESPACE = "http://compileflow.alibaba.com";

    private XMLStreamReader xmlStreamReader;

    public static XMLStreamReaderSource of(XMLStreamReader xmlStreamReader) {
        XMLStreamReaderSource xmlStreamReaderSource = new XMLStreamReaderSource();
        xmlStreamReaderSource.setXmlStreamReader(xmlStreamReader);
        return xmlStreamReaderSource;
    }

    private void setXmlStreamReader(XMLStreamReader xmlStreamReader) {
        this.xmlStreamReader = xmlStreamReader;
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
    public boolean endWith(String name) {
        return xmlStreamReader.isEndElement() && name.equals(xmlStreamReader.getLocalName());
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
    public String getPrefix() {
        return xmlStreamReader.getPrefix();
    }

    @Override
    public String getElementText() {
        try {
            return xmlStreamReader.getElementText();
        } catch (XMLStreamException e) {
            throw new CompileFlowException("Failed to get element text", e);
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
        return xmlStreamReader.getAttributeValue(namespaceURI, name);
    }

    @Override
    public String getString(String name) {
        return getString(null, name);
    }

    @Override
    public String getCfString(String name) {
        return getString(COMPILE_FLOW_NAMESPACE, name);
    }

    private int getInt(String namespaceURI, String name) {
        String vaule = getString(namespaceURI, name);
        return StringUtils.isNotEmpty(vaule) ? Integer.valueOf(vaule) : 0;
    }

    @Override
    public int getInt(String name) {
        return getInt(null, name);
    }

    @Override
    public int getCfInt(String name) {
        return getInt(COMPILE_FLOW_NAMESPACE, name);
    }

    private long getLong(String namespaceURI, String name) {
        String vaule = getString(namespaceURI, name);
        return vaule != null ? Long.valueOf(vaule) : 0;
    }

    @Override
    public long getCfLong(String name) {
        return getLong(COMPILE_FLOW_NAMESPACE, name);
    }

    @Override
    public long getLong(String name) {
        return getLong(null, name);
    }

    private boolean getBoolean(String namespaceURI, String name) {
        String vaule = getString(namespaceURI, name);
        return vaule != null ? Boolean.valueOf(vaule) : false;
    }

    @Override
    public boolean getCfBoolean(String name) {
        return getBoolean(COMPILE_FLOW_NAMESPACE, name);
    }

    @Override
    public boolean getBoolean(String name) {
        return getBoolean(null, name);
    }

}
