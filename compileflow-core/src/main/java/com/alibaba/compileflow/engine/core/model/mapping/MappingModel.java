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
package com.alibaba.compileflow.engine.core.model.mapping;

import java.util.List;

/**
 * Boundary that owns directional input and output mappings.
 *
 * @author yusu
 */
public interface MappingModel {
    List<InputMapping> getInputMappings();

    List<OutputMapping> getOutputMappings();

    default void addInputMapping(InputMapping mapping) {
        getInputMappings().add(mapping);
    }

    default void addOutputMapping(OutputMapping mapping) {
        getOutputMappings().add(mapping);
    }
}
