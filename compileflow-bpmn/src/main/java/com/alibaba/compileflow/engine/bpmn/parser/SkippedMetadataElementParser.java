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

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.core.xml.parser.FlowElementParser;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import java.util.Objects;

/**
 * Consumes an explicitly allowlisted metadata subtree that has no execution semantics.
 *
 * @author yusu
 */
final class SkippedMetadataElementParser implements FlowElementParser<IgnoredTextElementParser.IgnoredTextElement> {
    private final String name;
    private final String namespace;

    SkippedMetadataElementParser(String name, String namespace) {
        this.name = Objects.requireNonNull(name, "name");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
    }

    @Override
    public IgnoredTextElementParser.IgnoredTextElement parse(XmlSource xmlSource, ParseContext parseContext)
            throws Exception {
        if (!name.equals(xmlSource.getLocalName()) || !namespace.equals(xmlSource.getNamespaceURI())) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "Unsupported namespace for BPMN metadata " + name + ": " + xmlSource.getNamespaceURI(), null);
        }
        xmlSource.skipCurrentElement();
        return new IgnoredTextElementParser.IgnoredTextElement();
    }

    @Override
    public String getName() {
        return name;
    }
}
