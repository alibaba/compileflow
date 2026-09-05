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
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AliasRoutingOptionsTest {
    @Test
    void preservesOpaqueRoutingKey() {
        AliasRoutingOptions options = new AliasRoutingOptions(" customer-1 ");

        assertThat(options.routingKey()).isEqualTo(" customer-1 ");
    }

    @Test
    void rejectsInvalidRoutingKeys() {
        assertThatThrownBy(() -> new AliasRoutingOptions("route\ud800"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("valid Unicode");
        assertThatThrownBy(() -> new AliasRoutingOptions("x".repeat(513)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("512");
    }

    @Test
    void sharesEmptyDefaults() {
        assertThat(AliasRoutingOptions.defaults().isEmpty()).isTrue();
        assertThat(AliasRoutingOptions.defaults()).isSameAs(AliasRoutingOptions.defaults());
        assertThat(new AliasRoutingOptions("").isEmpty()).isTrue();
        assertThat(new AliasRoutingOptions("secret").toString()).doesNotContain("secret");
    }

    @Test
    void validatesCopiesAndRedactsTargetingAttributes() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("region", "private-value");

        AliasRoutingOptions options = new AliasRoutingOptions("cohort", source);
        source.clear();

        assertThat(options.attributes()).containsEntry("region", "private-value");
        assertThat(options.toString()).contains("region").doesNotContain("private-value").doesNotContain("cohort");
        assertThatThrownBy(() -> options.attributes().put("tier", "gold")).isInstanceOf(
                UnsupportedOperationException.class);
        assertThatThrownBy(() -> new AliasRoutingOptions(null, Map.of("__cf_secret", "value")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reserved");
    }
}
