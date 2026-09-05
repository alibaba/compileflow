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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.core.model.variable.Variable;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessVariableContainerTest {
    @Test
    void derivesImmutableCategoryViewsFromCurrentVariables() {
        Variables variables = new Variables();
        Variable input = variable("input", ProcessVariableContainer.VARIABLE_TYPE_PARAM);
        Variable result = variable("result", ProcessVariableContainer.VARIABLE_TYPE_RETURN);
        variables.addVariable(input);

        assertThat(variables.getParameterVariables()).containsExactly(input);
        assertThat(variables.getReturnVariables()).isEmpty();

        variables.addVariable(result);

        assertThat(variables.getReturnVariables()).containsExactly(result);
        assertThatThrownBy(() -> variables.getParameterVariables().clear()).isInstanceOf(
                UnsupportedOperationException.class);
        assertThat(variables.getVariables()).containsExactly(input, result);
    }

    private static Variable variable(String name, String type) {
        Variable variable = new Variable();
        variable.setName(name);
        variable.setInOutType(type);
        return variable;
    }

    private static final class Variables implements ProcessVariableContainer {
        private final List<Variable> vars = new ArrayList<>();

        @Override
        public List<Variable> getVariables() {
            return vars;
        }
    }
}
