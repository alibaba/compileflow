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
package com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model;

/**
 * @author wuxiang
 * @author yusu
 */
public interface XMLSource {

    boolean hasNext() throws Exception;

    String nextElementName() throws Exception;

    boolean endWith(String name);

    String getLocalName();

    String getNamespaceURI();

    String getPrefix();

    String getElementText();

    String getAttributeLocalName(int index);

    String getAttributeValue(int index);

    String getAttributeNamespace(int index);

    String getAttributePrefix(int index);

    int getAttributeCount();

    String getString(String name);

    String getCfString(String name);

    int getInt(String name);

    int getCfInt(String name);

    long getLong(String name);

    long getCfLong(String name);

    boolean getBoolean(String name);

    boolean getCfBoolean(String name);

    /**
     * 获取当前元素名（用于作用域判断）
     */
    String getCurrentElementName();

    /**
     * 判断当前是否为指定元素的结束标签
     */
    boolean isEndElement(String name);
}
