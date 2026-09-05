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
package com.alibaba.compileflow.engine.core.routing;

import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import java.util.Map;

/**
 * Execution capability for an already-selected Alias target.
 *
 * <p>The caller selects exactly once and retains the selected immutable runtime before invoking
 * this capability. A later Alias revision does not change an already handed-off execution.
 *
 * @author yusu
 */
public interface AliasSelectionExecutor {
    /**
     * Executes a preselected Alias target with Alias attribution intact.
     *
     * @param alias     originally requested Alias
     * @param selection resolved version and observed route attribution
     * @param variables process variables
     * @param options   request-scoped execution options
     * @return process outcome
     */
    ProcessResult<Map<String, Object>> execute(ProcessRef.Alias alias, AliasSelection selection,
            Map<String, Object> variables, ProcessExecutionOptions options);
}
