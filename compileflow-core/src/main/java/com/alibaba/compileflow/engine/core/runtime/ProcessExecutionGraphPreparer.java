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
package com.alibaba.compileflow.engine.core.runtime;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessRef;
import java.util.Objects;

/**
 * Engine capability for preparing a complete exact Process call graph.
 *
 * <p>Deployment uses this capability before publishing local-ready state. Keeping preparation in
 * the engine makes deployment and process execution share the same graph contract validation.</p>
 *
 * @author yusu
 */
public interface ProcessExecutionGraphPreparer {
    /**
     * Requires an engine that can prepare exact execution graphs.
     *
     * @param engine candidate engine
     * @return graph preparation capability
     */
    static ProcessExecutionGraphPreparer require(ProcessEngine engine) {
        ProcessEngine candidate = Objects.requireNonNull(engine, "engine");
        if (candidate instanceof ProcessExecutionGraphPreparer preparer) {
            return preparer;
        }
        throw new IllegalStateException(
                "Deployment requires an engine with exact Process graph preparation; engine=" + candidate
                    .getClass()
                    .getName());
    }

    /**
     * Prepares and validates the complete graph rooted at an installed exact Version.
     *
     * @param root exact immutable root Version
     * @return prepared graph retained by the engine cache
     */
    ProcessCallGraph prepareExact(ProcessRef.Version root);
}
