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
package com.alibaba.compileflow.engine.core.runtime.execution.failure;

import com.alibaba.compileflow.engine.spi.execution.FailureHandler;
import com.alibaba.compileflow.engine.spi.execution.FailureResolution;

/**
 * Built-in failure handlers for process action execution.
 *
 * @author yusu
 */
public final class FailureHandlers {
    /**
     * Propagate the exhausted action failure to the synchronous process caller.
     */
    public static final FailureHandler PROPAGATE = context -> FailureResolution.FAIL_PROCESS;
    /**
     * Skip the failed node and continue the process.
     */
    public static final FailureHandler CONTINUE = context -> FailureResolution.CONTINUE_PROCESS;

    private FailureHandlers() {
    }
}
