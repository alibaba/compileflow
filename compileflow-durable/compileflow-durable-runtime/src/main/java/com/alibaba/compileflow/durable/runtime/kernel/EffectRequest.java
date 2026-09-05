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
package com.alibaba.compileflow.durable.runtime.kernel;

import com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan;
import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import java.util.Map;
import java.util.Objects;

/**
 * One compiled request to schedule an exact governed Effect contract.
 *
 * @author yusu
 */
public record EffectRequest(String elementId, EffectRecoveryPlan recoveryPlan, Map<String, Object> input) {
    public EffectRequest {
        elementId = DurableIdentifiers.requireIdentity(elementId, "elementId", 128);
        recoveryPlan = Objects.requireNonNull(recoveryPlan, "recoveryPlan");
        input = DurableValueSnapshots.immutableMap(input);
    }

    @Override
    public String toString() {
        return "EffectRequest{elementId=" + elementId + ", recoveryPlan=" + recoveryPlan + ", input=<redacted>}";
    }
}
