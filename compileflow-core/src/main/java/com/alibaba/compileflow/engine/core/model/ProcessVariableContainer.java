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
package com.alibaba.compileflow.engine.core.model;

import com.alibaba.compileflow.engine.core.model.variable.VariableContainer;
import com.alibaba.compileflow.engine.core.model.variable.Variable;
import java.util.List;

/**
 * Convenience accessors over typed process variables.
 *
 * @author yusu
 */
public interface ProcessVariableContainer extends VariableContainer {
    String VARIABLE_TYPE_PARAM = "param";
    String VARIABLE_TYPE_INNER = "inner";
    String VARIABLE_TYPE_RETURN = "return";

    @Override
    default void addVariable(Variable variable) {
        getVariables().add(variable);
    }

    default List<Variable> getParameterVariables() {
        return getVariables()
            .stream()
            .filter(variable -> VARIABLE_TYPE_PARAM.equals(variable.getInOutType()))
            .toList();
    }

    default List<Variable> getReturnVariables() {
        return getVariables()
            .stream()
            .filter(variable -> VARIABLE_TYPE_RETURN.equals(variable.getInOutType()))
            .toList();
    }

    default List<Variable> getInternalVariables() {
        return getVariables()
            .stream()
            .filter(variable -> VARIABLE_TYPE_INNER.equals(variable.getInOutType()))
            .toList();
    }

    default Variable getReturnVariable() {
        return getVariables()
            .stream()
            .filter(variable -> VARIABLE_TYPE_RETURN.equals(variable.getInOutType()))
            .findFirst()
            .orElse(null);
    }
}
