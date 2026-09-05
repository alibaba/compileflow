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
package com.alibaba.compileflow.engine.core.model.extension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Extension element with nested attributes and children.
 *
 * @author yusu
 */
public class ExtensionElement extends AbstractExtensionElement {
    private final Map<String, List<ExtensionElement>> childElements = new LinkedHashMap<>();
    private String name;
    private String namespacePrefix;
    private String namespaceURI;
    private String textContent;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNamespacePrefix() {
        return namespacePrefix;
    }

    public void setNamespacePrefix(String namespacePrefix) {
        this.namespacePrefix = namespacePrefix;
    }

    public String getNamespaceURI() {
        return namespaceURI;
    }

    public void setNamespaceURI(String namespaceURI) {
        this.namespaceURI = namespaceURI;
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public List<ExtensionElement> getChildElements() {
        return childElements.values().stream().flatMap(List::stream).toList();
    }

    public List<ExtensionElement> getChildElements(String childElementName) {
        return childElements.getOrDefault(childElementName, List.of());
    }

    public void addChildElement(ExtensionElement childElement) {
        if (childElement != null && StringUtils.isNotEmpty(childElement.getName())) {
            List<ExtensionElement> extensionElements =
                    childElements.computeIfAbsent(childElement.getName(), (name) -> new ArrayList<>());
            extensionElements.add(childElement);
        }
    }
}
