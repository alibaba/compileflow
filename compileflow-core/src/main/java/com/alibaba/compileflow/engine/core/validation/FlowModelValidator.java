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
package com.alibaba.compileflow.engine.core.validation;

import com.alibaba.compileflow.engine.core.model.FlowModel;
import java.util.List;

/**
 * Internal structural validation rule set applied before process code generation.
 *
 * @author yusu
 */
public interface FlowModelValidator {
    /**
     * Validates one parsed process model.
     *
     * @param flowModel parsed process model
     * @return validation failures, or an empty list when valid
     */
    List<ValidationFailure> validate(FlowModel<?> flowModel);
}
