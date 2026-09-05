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

import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;

/**
 * Provides non-executing development and diagnostic tooling.
 *
 * @author yusu
 */
public interface ProcessToolingService {
    /**
     * Runs non-executing validation and optional dry-run compilation.
     *
     * @param definition explicit process definition
     * @param options    validation stages and timeouts
     * @return validation and compilation report
     */
    ProcessPreflightReport preflight(ProcessDefinition definition, ProcessPreflightOptions options);

    /**
     * Generates the Java source that the engine would compile for an explicit definition.
     *
     * @param definition explicit process definition
     * @return generated Java source
     */
    String generateJavaCode(ProcessDefinition definition);
}
