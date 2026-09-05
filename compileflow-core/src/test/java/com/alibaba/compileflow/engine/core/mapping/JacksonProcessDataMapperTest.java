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
package com.alibaba.compileflow.engine.core.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JacksonProcessDataMapperTest {
    private final JacksonProcessDataMapper mapper = JacksonProcessDataMapper.createDefault();

    @Test
    void mapsObjectsThroughTheCanonicalVariablesMap() {
        Map<String, Object> variables = mapper.toVariables(new Input("created", 3));
        Output output = mapper.fromVariables(variables, Output.class);

        assertThat(variables).containsEntry("state", "created").containsEntry("count", 3);
        assertThat(output.state).isEqualTo("created");
        assertThat(output.count).isEqualTo(3);
    }

    @Test
    void snapshotsMapsAndRejectsNonStringKeys() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("state", "created");

        Map<String, Object> variables = mapper.toVariables(source);
        source.put("state", "changed");

        assertThat(variables).containsEntry("state", "created");
        assertThatThrownBy(() -> mapper.toVariables(Map.of(1, "invalid")))
            .isInstanceOf(CompileFlowException.ValidationException.class)
            .hasMessageContaining("keys must be strings");
    }

    private record Input(String state, int count) {}

    private static final class Output {
        public String state;
        public int count;
    }
}
