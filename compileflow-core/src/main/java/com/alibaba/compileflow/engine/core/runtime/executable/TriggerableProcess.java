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
package com.alibaba.compileflow.engine.core.runtime.executable;

import java.util.Map;

/**
 * Generated process implementation that exposes named trigger entries.
 *
 * <p>The runtime creates a new executable object for every call and
 * initializes it from the supplied context. Triggering therefore starts a new
 * execution at the selected entry; it does not resume a persisted Java object
 * or durable process run.
 *
 * @author yusu
 */
public interface TriggerableProcess extends ExecutableProcess {
    /**
     * Starts an execution at a trigger entry without an event selector.
     *
     * @param nodeId  unique identifier of the trigger entry
     * @param context complete variables for this invocation
     * @return declared process outputs
     * @throws Exception when generated process execution fails
     */
    Map<String, Object> trigger(String nodeId, Map<String, Object> context) throws Exception;

    /**
     * Starts an execution at a trigger entry and validates its event selector.
     *
     * @param nodeId  unique identifier of the trigger entry
     * @param event   event selector, or {@code null} for an entry without one
     * @param context complete variables for this invocation
     * @return declared process outputs
     * @throws Exception when generated process execution fails
     */
    Map<String, Object> trigger(String nodeId, String event, Map<String, Object> context) throws Exception;
}
