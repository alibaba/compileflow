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
package com.alibaba.compileflow.engine;

/**
 * Names an entry from which a trigger operation starts a new process execution.
 *
 * <p>This value does not identify a persisted process instance and does not carry durable
 * workflow or external message state.
 *
 * @param nodeId unique identifier of the trigger entry
 * @param event  optional event selector at that entry
 * @author yusu
 */
public record ProcessTrigger(String nodeId, String event) {
    /**
     * Validates the trigger without normalizing its identity.
     *
     * @param nodeId unique identifier of the trigger entry
     * @param event  optional event selector at that entry
     */
    public ProcessTrigger {
        nodeId = ProcessIdentifiers.requireNodeId(nodeId);
        event = ProcessIdentifiers.optionalEvent(event);
    }

    /**
     * Creates a trigger without an event selector.
     *
     * @param nodeId unique identifier of the trigger entry
     * @return trigger value
     */
    public static ProcessTrigger at(String nodeId) {
        return new ProcessTrigger(nodeId, null);
    }

    /**
     * Creates a trigger with an event selector.
     *
     * @param nodeId unique identifier of the trigger entry
     * @param event  event selector
     * @return trigger value
     */
    public static ProcessTrigger on(String nodeId, String event) {
        return new ProcessTrigger(nodeId, event);
    }
}
