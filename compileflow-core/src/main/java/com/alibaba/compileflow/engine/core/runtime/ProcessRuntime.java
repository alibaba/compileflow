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

import com.alibaba.compileflow.engine.ProcessTrigger;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import java.util.Map;

/**
 * One executable realization of a source-format-neutral Process.
 *
 * <p>A runtime exposes Process meaning and invocation behavior only. Source authoring objects and
 * realization details such as generated Java classes are deliberately private to implementations.
 *
 * @author yusu
 */
public interface ProcessRuntime {
    /**
     * Returns the immutable Process meaning executed by this runtime.
     */
    ProcessSemanticPlan getSemanticPlan();

    /**
     * Starts a ProcessEngine invocation.
     */
    Map<String, Object> execute(Map<String, Object> variables);

    /**
     * Starts a fresh invocation at a named trigger entry.
     */
    Map<String, Object> trigger(ProcessTrigger trigger, Map<String, Object> variables);
}
