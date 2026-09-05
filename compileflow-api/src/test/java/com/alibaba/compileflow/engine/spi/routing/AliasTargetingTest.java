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
package com.alibaba.compileflow.engine.spi.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AliasTargetingTest {
    @Test
    void validatesCopiesAndRedactsConfiguration() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("tenant-group", "private-value");

        AliasTargeting targeting = new AliasTargeting("enterprise-cohort", source);
        source.clear();

        assertThat(targeting.parameters()).containsEntry("tenant-group", "private-value");
        assertThat(targeting.toString()).contains("tenant-group").doesNotContain("private-value");
        assertThatThrownBy(() -> targeting.parameters().put("other", "value"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new AliasTargeting("EnterpriseCohort"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("lowercase kebab-case");
    }

    @Test
    void requiresTargetingOnlyOnCandidateRoutes() {
        var alias = com.alibaba.compileflow.engine.ProcessRef.alias("default", "order", "prod");

        assertThatThrownBy(() -> new ProcessAliasRoute(alias,
                com.alibaba.compileflow.engine.ProcessRef.version("default", "order", "v1"), null, 0,
                new AliasTargeting("enterprise-cohort"), 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("without a candidateVersion");
    }
}
