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

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Immutable Spring binding DTO for synchronous process-call limits.
 *
 * @author yusu
 */
public final class EngineCallProperties {
    /**
     * Maximum root-inclusive synchronous process-call depth.
     */
    @Min(1)
    @Max(256)
    private final int maxDepth;

    public EngineCallProperties(@DefaultValue("32") int maxDepth) {
        this.maxDepth = maxDepth;
    }

    /**
     * Returns the configured process-call depth limit.
     *
     * @return root-inclusive maximum call depth
     */
    public int getMaxDepth() {
        return maxDepth;
    }

    @Override
    public String toString() {
        return "EngineCallProperties{maxDepth=" + maxDepth + '}';
    }
}
