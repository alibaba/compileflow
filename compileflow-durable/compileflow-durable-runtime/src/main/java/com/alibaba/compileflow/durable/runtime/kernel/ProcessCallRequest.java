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

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import java.util.Map;
import java.util.Objects;

/**
 * One Process-owned request to activate a nested Process invocation.
 *
 * @author yusu
 */
public record ProcessCallRequest(String elementId, Map<String, Object> input) {
    public ProcessCallRequest {
        elementId = requireText(elementId, "elementId");
        input = DurableValueSnapshots.immutableMap(Objects.requireNonNull(input, "input"));
    }

    private static String requireText(String value, String name) {
        return DurableIdentifiers.requireIdentity(value, name, 128);
    }

    @Override
    public String toString() {
        return "ProcessCallRequest{elementId=" + elementId + ", input=<redacted>}";
    }
}
