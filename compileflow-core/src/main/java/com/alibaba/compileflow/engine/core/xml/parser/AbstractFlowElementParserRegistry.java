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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Abstract provider of {@link FlowElementParser} instances.
 *
 * @author yusu
 */
public abstract class AbstractFlowElementParserRegistry {
    private final Map<String, FlowElementParser<?>> parsersByName;

    protected AbstractFlowElementParserRegistry(Iterable<? extends FlowElementParser<?>> parsers) {
        Objects.requireNonNull(parsers, "parsers");
        Map<String, FlowElementParser<?>> indexed = new LinkedHashMap<>();
        for (FlowElementParser<?> parser : parsers) {
            FlowElementParser<?> candidate = Objects.requireNonNull(parser, "parser");
            String name = Objects.requireNonNull(candidate.getName(), "parser name");
            FlowElementParser<?> existing = indexed.putIfAbsent(name, candidate);
            if (existing != null) {
                throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                        "Duplicate parser name '" + name + "': " + existing.getClass().getName() + " and " + candidate
                            .getClass()
                            .getName());
            }
        }
        parsersByName = Map.copyOf(indexed);
    }

    public final FlowElementParser<?> getParser(String name) {
        FlowElementParser<?> parser = parsersByName.get(name);
        if (parser == null) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_003,
                    "No parser found, name is " + name);
        }
        return parser;
    }
}
