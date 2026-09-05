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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure.properties;

import com.alibaba.compileflow.engine.config.ProcessDefinitionConfig;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Immutable Spring binding DTO for the process-definition size limit.
 *
 * @author yusu
 */
public final class EngineDefinitionProperties {
    private static final DataSize MAX_DEFINITION_SIZE = DataSize.ofBytes(ProcessDefinitionConfig.MAX_BYTES);
    /**
     * Maximum accepted size of one inline or classpath process definition.
     */
    @NotNull
    private final DataSize maxSize;

    public EngineDefinitionProperties(@DefaultValue("4MB") DataSize maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * Converts bound values into the immutable engine configuration.
     */
    public ProcessDefinitionConfig toConfig() {
        return ProcessDefinitionConfig.builder().maxBytes(Math.toIntExact(maxSize.toBytes())).build();
    }

    @AssertTrue(message = "compileflow.engine.definition.max-size must be between 1B and 100MB")
    public boolean isMaxSizeValid() {
        if (maxSize == null) {
            return false;
        }
        long bytes = maxSize.toBytes();
        return bytes > 0 && maxSize.compareTo(MAX_DEFINITION_SIZE) <= 0;
    }

    public DataSize getMaxSize() {
        return maxSize;
    }
}
