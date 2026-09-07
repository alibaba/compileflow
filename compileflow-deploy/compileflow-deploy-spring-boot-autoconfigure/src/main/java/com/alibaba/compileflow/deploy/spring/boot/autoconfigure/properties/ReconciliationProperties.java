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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties;

import com.alibaba.compileflow.spring.boot.autoconfigure.properties.DurationPropertyConstraints;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Strict binding adapter for control-plane projection reconciliation.
 *
 * @author yusu
 */
public final class ReconciliationProperties {
    /**
     * Projection reconciliation behavior.
     */
    @NotNull
    private final Mode mode;
    /**
     * Delay between projection reconciliation cycles.
     */
    @NotNull
    private final Duration interval;

    public ReconciliationProperties(@DefaultValue("REPAIR") Mode mode, @DefaultValue("1m") Duration interval) {
        this.mode = mode;
        this.interval = interval;
    }

    public Mode getMode() {
        return mode;
    }

    public Duration getInterval() {
        return interval;
    }

    @AssertTrue(message = "compileflow.deploy.reconciliation.interval must be a positive"
            + " whole-millisecond duration representable as a long")
    public boolean isIntervalValid() {
        return DurationPropertyConstraints.isPositiveWholeMilliseconds(interval);
    }

    /**
     * Mutually exclusive projection reconciliation behavior.
     */
    public enum Mode {
        /**
         * Do not schedule projection reconciliation.
         */
        DISABLED,
        /**
         * Detect and report projection drift without changing projections.
         */
        DETECT,
        /**
         * Detect and repair derived routing and artifact projections.
         */
        REPAIR
    }
}
