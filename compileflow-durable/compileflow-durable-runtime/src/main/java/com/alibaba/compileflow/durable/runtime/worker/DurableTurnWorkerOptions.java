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
package com.alibaba.compileflow.durable.runtime.worker;

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import com.alibaba.compileflow.durable.api.validation.DurableNumbers;
import com.alibaba.compileflow.durable.runtime.kernel.MultiInstanceState;
import com.alibaba.compileflow.durable.runtime.kernel.TurnBudget;
import java.time.Duration;

/**
 * Operational Turn scheduling only; none of these values affect Process results.
 *
 * @author yusu
 */
public record DurableTurnWorkerOptions(String workerId, Duration turnFaultBackoff, int turnMaxSteps,
        int maxActiveIterations) {
    public DurableTurnWorkerOptions {
        workerId = DurableIdentifiers.requireIdentity(workerId, "workerId", 128);
        turnFaultBackoff = DurableNumbers.requirePositiveDurationMillis(turnFaultBackoff, Duration.ofHours(1),
                "turnFaultBackoff");
        if (turnMaxSteps <= 0 || turnMaxSteps > TurnBudget.ABSOLUTE_MAX_STEPS) {
            throw new IllegalArgumentException("turnMaxSteps must be in [1, " + TurnBudget.ABSOLUTE_MAX_STEPS + ']');
        }
        if (maxActiveIterations <= 0 || maxActiveIterations > MultiInstanceState.ABSOLUTE_MAX_ACTIVE) {
            throw new IllegalArgumentException(
                    "maxActiveIterations must be in [1, " + MultiInstanceState.ABSOLUTE_MAX_ACTIVE + ']');
        }
    }
}
