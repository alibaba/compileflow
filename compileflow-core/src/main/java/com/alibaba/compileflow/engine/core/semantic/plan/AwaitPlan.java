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
package com.alibaba.compileflow.engine.core.semantic.plan;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.core.semantic.ProtocolDuration;
import java.time.Duration;

/**
 * An externally completed boundary in Process semantics.
 *
 * @author yusu
 */
public record AwaitPlan(String event, Duration timeout) implements OperationPlan {
    public AwaitPlan {
        event = ProcessIdentifiers.optionalEvent(event);
        if (timeout != null) {
            ProtocolDuration.requireNonNegativeMillis(timeout, "timeout");
        }
    }
}
