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

class ProcessDefinitionTest {
    @Test
    void inlineStringRepresentationDoesNotRevealContent() {
        String secretContent = "<process password=\"secret-value\"/>";

        assertThat(ProcessDefinition.inline("order.flow", secretContent).toString())
            .doesNotContain(secretContent)
            .doesNotContain("secret-value");
    }

    @Test
    void classpathResourceNamesAreNormalizedAndCannotTraverseParents() {
        ProcessDefinition.Classpath definition = ProcessDefinition.classpath("order.flow", "/flows/./order.bpm");

        assertThat(definition.resourcePath()).isEqualTo("flows/order.bpm");
        assertThatThrownBy(() -> ProcessDefinition.classpath("order.flow", "flows/../secret.bpm"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("parent traversal");
    }

    @Test
    void classpathExplicitlyCarriesItsResourceIdentity() {
        ProcessDefinition.Classpath definition = ProcessDefinition.classpath("order.flow", "order/flow.bpm");

        assertThat(definition.code()).isEqualTo("order.flow");
        assertThat(definition.resourcePath()).isEqualTo("order/flow.bpm");
    }

    @Test
    void invalidDefinitionsFailAtTheApiBoundary() {
        assertThatThrownBy(() -> ProcessDefinition.inline("order/flow", "<flow/>"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ASCII");
        assertThatThrownBy(() -> ProcessDefinition.inline("c".repeat(129), "<flow/>"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("128");
        assertThatThrownBy(() -> ProcessDefinition.inline("order.flow", " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("content");
        assertThatThrownBy(() -> ProcessDefinition.inline("order.flow", "\u00a0"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("content");
        assertThatThrownBy(() -> ProcessDefinition.inline("order.flow", "<flow>\uD800</flow>"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unicode");
    }
}
