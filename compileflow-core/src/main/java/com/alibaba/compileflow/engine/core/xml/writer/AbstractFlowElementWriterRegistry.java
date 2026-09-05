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
package com.alibaba.compileflow.engine.core.xml.writer;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.core.model.Element;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.xml.stream.XMLStreamWriter;

/**
 * Abstract provider of {@link FlowElementWriter} instances.
 *
 * @author yusu
 */
public abstract class AbstractFlowElementWriterRegistry {
    private final Map<Class<? extends Element>, FlowElementWriter<?>> writersByElementType;

    protected AbstractFlowElementWriterRegistry(Iterable<? extends FlowElementWriter<?>> writers) {
        Objects.requireNonNull(writers, "writers");
        Map<Class<? extends Element>, FlowElementWriter<?>> indexed = new LinkedHashMap<>();
        for (FlowElementWriter<?> writer : writers) {
            FlowElementWriter<?> candidate = Objects.requireNonNull(writer, "writer");
            Class<? extends Element> elementType =
                    Objects.requireNonNull(candidate.getElementClass(), "writer element class");
            FlowElementWriter<?> existing = indexed.putIfAbsent(elementType, candidate);
            if (existing != null) {
                throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                        "Duplicate writer for element type '" + elementType.getName() + "': "
                        + existing.getClass().getName() + " and " + candidate.getClass().getName());
            }
        }
        this.writersByElementType = Map.copyOf(indexed);
    }

    private static <S extends Element> void write(FlowElementWriter<S> writer, Element element,
            XMLStreamWriter xmlWriter) throws Exception {
        writer.write(writer.getElementClass().cast(element), xmlWriter);
    }

    public final void write(Element element, XMLStreamWriter xmlWriter) throws Exception {
        Objects.requireNonNull(element, "element must not be null");
        FlowElementWriter<?> writer = writersByElementType.get(element.getClass());
        if (writer == null) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_003,
                    "No writer found, name is " + element.getClass().getName());
        }
        write(writer, element, xmlWriter);
    }
}
