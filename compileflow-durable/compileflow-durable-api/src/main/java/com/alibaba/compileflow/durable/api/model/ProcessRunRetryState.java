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
package com.alibaba.compileflow.durable.api.model;

import com.alibaba.compileflow.durable.api.validation.DurableNumbers;
import java.time.Instant;
import java.util.Objects;

/**
 * State evidence for the current Run execution-retry episode.
 *
 * <p>The current scheduling eligibility is exposed separately as
 * {@link ProcessRun#availableAt()}. Claiming the Run does not erase this
 * evidence; a successfully committed Durable boundary or terminal outcome does.</p>
 *
 * @param code stable retry classification
 * @param consecutiveTurnFaults consecutive retryable turn failures
 * @param observedAt authority observation time
 *
 * @author yusu
 */
public record ProcessRunRetryState(RunRetryCode code, int consecutiveTurnFaults, Instant observedAt) {
    public ProcessRunRetryState {
        code = Objects.requireNonNull(code, "code");
        consecutiveTurnFaults = DurableNumbers.requireRange(consecutiveTurnFaults, 1, Integer.MAX_VALUE,
                "consecutiveTurnFaults");
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
    }
}
