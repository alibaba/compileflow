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

import com.alibaba.compileflow.durable.api.validation.DurableNumbers;
import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Declarative request to commit a wait boundary.
 *
 * @author yusu
 */
public record WaitRequest(String boundaryId, String event, Duration deadlineAfter, Map<String, Object> attributes) {
    public WaitRequest(String boundaryId, String event, Map<String, Object> attributes) {
        this(boundaryId, event, null, attributes);
    }

    public WaitRequest {
        boundaryId = requireIdentity(boundaryId, "boundaryId");
        event = ProcessIdentifiers.optionalEvent(event);
        if (deadlineAfter != null) {
            deadlineAfter = DurableNumbers.requireDurationMillis(deadlineAfter, Duration.ofDays(36500), "deadlineAfter");
        }
        attributes = DurableValueSnapshots.immutableMap(Objects.requireNonNull(attributes, "attributes"));
    }

    private static String requireIdentity(String value, String name) {
        return DurableIdentifiers.requireIdentity(value, name, 128);
    }

    @Override
    public String toString() {
        return "WaitRequest{boundaryId=" + boundaryId + ", event=" + (event == null ? "none" : "<present>")
                + ", deadlineAfter=" + deadlineAfter + ", attributes=<redacted>}";
    }
}
