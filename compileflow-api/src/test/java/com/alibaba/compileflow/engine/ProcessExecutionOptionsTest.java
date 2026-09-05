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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class ProcessExecutionOptionsTest {
    @Test
    void carriesOnlyInvocationAndAliasAdmissionInputs() {
        AliasRoutingOptions aliasRouting = new AliasRoutingOptions("customer-1");
        ProcessExecutionOptions options =
                ProcessExecutionOptions.builder().invocationId("request-1").aliasRouting(aliasRouting).build();

        assertThat(options.getInvocationId()).isEqualTo("request-1");
        assertThat(options.getAliasRouting()).isSameAs(aliasRouting);
    }

    @Test
    void rejectsInvocationIdsThatAreUnsafeForLogsAndPersistence() {
        assertThatThrownBy(() -> ProcessExecutionOptions.builder().invocationId(" request-1 ").build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("surrounding whitespace");
        assertThatThrownBy(() -> ProcessExecutionOptions.builder().invocationId("request\nforged").build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invocationId");
        assertThatThrownBy(() -> ProcessExecutionOptions.builder().invocationId("-request").build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must start");
        assertThatThrownBy(() -> ProcessExecutionOptions.builder().invocationId("r".repeat(129)).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("128");
    }

    @Test
    void defaultsAreEmptyAndReusable() {
        assertThat(ProcessExecutionOptions.defaults().getInvocationId()).isNull();
        assertThat(ProcessExecutionOptions.defaults().getAliasRouting()).isSameAs(AliasRoutingOptions.defaults());
        assertThat(ProcessExecutionOptions.defaults()).isSameAs(ProcessExecutionOptions.defaults());
    }

    @Test
    void rejectsNullAliasRouting() {
        assertThatThrownBy(() -> ProcessExecutionOptions.builder().aliasRouting(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("aliasRouting");
    }
}
