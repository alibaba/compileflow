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
package com.alibaba.compileflow.engine.core.runtime.context;

import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.core.runtime.ProcessCallGraph;
import java.util.Map;

/**
 * Invokes one exact target from a resolved Process call graph.
 *
 * @author yusu
 */
public interface ProcessCallInvoker {
    ProcessResult<Map<String, Object>> invoke(ProcessCallGraph callGraph, ProcessCallGraph.ProcessNode target,
            Map<String, Object> variables);
}
