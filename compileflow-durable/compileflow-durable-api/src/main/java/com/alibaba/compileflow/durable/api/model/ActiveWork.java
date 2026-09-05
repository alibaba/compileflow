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

/**
 * Operator-visible metadata for one unresolved Wait, Timer, or Effect occurrence owned by a Run.
 *
 * <p>The closed variants deliberately omit Wait authority tokens and Effect payloads.</p>
 *
 * @author yusu
 */
public sealed interface ActiveWork permits ActiveWait, ActiveTimer, ActiveEffect {
    /**
     * Stable logical continuation frontier that owns this occurrence.
     *
     * @return stable frontier identity
     */
    String frontierId();

    /**
     * Returns the owning process element identity.
     *
     * @return owning process element identity
     */
    String elementId();

    /**
     * Returns the monotonic occurrence sequence within the Run.
     *
     * @return monotonic occurrence sequence within the Run
     */
    long occurrenceSequence();
}
