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
package com.alibaba.compileflow.engine.core.runtime.ownership;

import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessRef;
import java.util.Objects;

/**
 * Internal owner-aware lifecycle for exact local runtimes.
 *
 * <p>This contract exists between the engine core and the deployment runtime. It is not part
 * of the supported application API.
 *
 * @author yusu
 */
public interface ProcessRuntimeOwnership {
    /**
     * Requires an engine implementation that supports owner-isolated runtimes.
     *
     * @param engine engine used by the deployment data plane
     * @return owner-aware runtime lifecycle implemented by the engine
     * @throws IllegalStateException when the engine cannot provide ownership isolation
     */
    static ProcessRuntimeOwnership require(ProcessEngine engine) {
        ProcessEngine candidate = Objects.requireNonNull(engine, "engine");
        if (candidate instanceof ProcessRuntimeOwnership ownership) {
            return ownership;
        }
        throw new IllegalStateException(
                "Versioned deployment requires a CompileFlow engine with owner-aware runtime lifecycle; engine="
                + candidate.getClass().getName());
    }

    /**
     * Loads an exact runtime and retains it for one owner without publishing deployment readiness.
     * The deployment runtime publishes readiness only after the complete static dependency graph is installed.
     *
     * @param ownerId    process-local owner identity
     * @param ref        exact runtime reference
     * @param definition immutable process definition
     */
    void loadOwned(String ownerId, ProcessRef.Version ref, ProcessDefinition definition);

    /**
     * Retains an already-loaded exact runtime for one owner.
     *
     * @param ownerId process-local owner identity
     * @param ref     exact runtime reference
     * @return {@code true} when the exact runtime exists and is now retained
     */
    boolean retainOwned(String ownerId, ProcessRef.Version ref);

    /**
     * Releases one owner's claim on an exact runtime.
     *
     * @param ownerId process-local owner identity
     * @param ref     exact runtime reference
     * @return release outcome
     */
    ReleaseOutcome releaseOwned(String ownerId, ProcessRef.Version ref);

    /**
     * Result of releasing one owner's claim on an exact runtime.
     */
    enum ReleaseOutcome {
        /**
         * The owner did not hold the runtime.
         */
        NOT_OWNED,
        /**
         * The owner was released while another owner still retains the runtime.
         */
        RETAINED,
        /**
         * The last owner was released and the runtime was removed.
         */
        REMOVED
    }
}
