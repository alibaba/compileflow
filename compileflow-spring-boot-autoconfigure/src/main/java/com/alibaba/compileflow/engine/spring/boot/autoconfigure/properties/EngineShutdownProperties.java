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

import com.alibaba.compileflow.spring.boot.autoconfigure.properties.DurationPropertyConstraints;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Total lifecycle budget for shutting down one engine.
 *
 * @author yusu
 */
public final class EngineShutdownProperties {
    /**
     * Total budget shared by operation draining and executor shutdown.
     */
    @NotNull
    private final Duration timeout;

    public EngineShutdownProperties(@DefaultValue("15s") Duration timeout) {
        this.timeout = timeout;
    }

    public Duration getTimeout() {
        return timeout;
    }

    @AssertTrue(message = "shutdown timeout must be a positive whole-millisecond duration representable as a long")
    public boolean isValid() {
        return DurationPropertyConstraints.isPositiveWholeMilliseconds(timeout);
    }
}
