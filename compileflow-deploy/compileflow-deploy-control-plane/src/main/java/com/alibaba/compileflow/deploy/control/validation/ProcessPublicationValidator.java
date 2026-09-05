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
package com.alibaba.compileflow.deploy.control.validation;

import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;

/**
 * Validates an exact process definition before it becomes an immutable published version.
 *
 * <p>Implementations must perform format parsing, schema checks, and model-structure validation.
 * Generated-code compilation may be performed as an additional check, but it cannot establish
 * readiness for runtime nodes with different class paths or component environments.
 *
 * @author yusu
 */
@FunctionalInterface
public interface ProcessPublicationValidator {
    /**
     * Validates one exact definition without creating or installing runtime state.
     *
     * @param ref        exact published version identity
     * @param modelType  process definition format
     * @param definition exact inline definition to validate
     * @return non-null report describing the completed validation
     */
    ProcessPublicationValidation validate(ProcessRef.Version ref, ProcessModelType modelType,
            ProcessDefinition.Inline definition);
}
