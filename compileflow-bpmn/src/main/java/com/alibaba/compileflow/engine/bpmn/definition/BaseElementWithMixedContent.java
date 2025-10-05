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
package com.alibaba.compileflow.engine.bpmn.definition;

import com.alibaba.compileflow.engine.core.definition.Element;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An abstract base class for BPMN elements that can contain mixed content,
 * meaning they can have both child elements and character data interspersed.
 * <p>
 * This is common for elements like expressions or documentation where the content
 * is primarily text but may also contain other markup.
 *
 * @author yusu
 */
public abstract class BaseElementWithMixedContent implements Element {

    /**
     * A list of strings representing the character data content of the element.
     */
    private List<String> content;

    private String id;

    /**
     * A map of any other attributes found on the element that are not explicitly
     * defined as fields in the subclass. This is used for extension attributes.
     */
    private Map<String, String> attributes = new HashMap<>();

    public List<String> getContent() {
        return content;
    }

    public void setContent(List<String> content) {
        this.content = content;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

}
