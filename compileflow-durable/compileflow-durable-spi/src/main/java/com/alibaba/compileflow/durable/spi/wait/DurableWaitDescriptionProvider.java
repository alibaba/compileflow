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
package com.alibaba.compileflow.durable.spi.wait;

import java.util.Map;

/**
 * Deterministic application-provided description of an outward-facing Durable Wait.
 *
 * <p>The public extension point is intentionally limited to Wait description.
 * Wait results are typed partial Process-state updates validated from the exact
 * Process Definition before commit; they are not interpreted by an application
 * callback after becoming Durable facts. Action invocation is resolved
 * internally from the exact Process definition by the current deployment;
 * there is no second Action/Effect registry or application binding identity
 * in this SPI.</p>
 *
 * <p>Implementations must be thread-safe because different Turns may describe Waits concurrently.
 * A failure prevents the Wait from being committed and fails the current Turn. The application or
 * dependency-injection container owns the provider lifecycle; Durable workers never close it.</p>
 *
 * @author yusu
 */
public interface DurableWaitDescriptionProvider {
    /**
     * Returns the default deterministic provider with no application Wait attributes.
     *
     * @return default provider
     */
    static DurableWaitDescriptionProvider defaults() {
        return new DurableWaitDescriptionProvider() {
        };
    }

    /**
     * Describes one outward-facing Wait before the boundary is committed.
     *
     * <p>The mapping is part of replayable Turn execution: it must be
     * deterministic, bounded, non-blocking, side-effect free, and derived only from the supplied
     * context. It must not perform external integration or rely on mutable ambient state. The
     * returned attributes are
     * persisted atomically with the Wait and delivered only through the
     * authenticated Outbox sink. The default preserves the existing empty
     * Wait contract.</p>
     *
     * @param context exact Process semantics, Wait identity, and detached state
     * @return bounded application attributes for this Wait
     * @throws Exception when the Wait cannot be described safely
     */
    default Map<String, Object> describeWait(DurableWaitDescriptionContext context) throws Exception {
        java.util.Objects.requireNonNull(context, "context");
        return Map.of();
    }
}
