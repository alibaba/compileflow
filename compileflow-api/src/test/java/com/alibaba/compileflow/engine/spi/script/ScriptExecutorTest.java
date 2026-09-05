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
package com.alibaba.compileflow.engine.spi.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import org.junit.jupiter.api.Test;

class ScriptExecutorTest {
    @Test
    void requiresCanonicalLanguageNamesAtEveryExtensionBoundary() {
        assertThat(ScriptExecutor.requireCanonicalName("custom-script")).isEqualTo("custom-script");

        assertThatThrownBy(() -> ScriptExecutor.requireCanonicalName(" \t "))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("must not be blank");
        assertThatThrownBy(() -> ScriptExecutor.requireCanonicalName("Custom-Script"))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("lowercase kebab-case");
        assertThatThrownBy(() -> ScriptExecutor.requireCanonicalName("x".repeat(257)))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("must not exceed 256 characters");
    }

    @Test
    void scriptProgramIdentityRejectsSurroundingWhitespace() {
        assertThatThrownBy(() -> new ScriptProgramSpec("java", "return value;",
                java.util.List.of(new ScriptProgramSpec.Input(" value ", "java.lang.String")), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("name must not contain surrounding whitespace");
        assertThatThrownBy(() -> new ScriptProgramSpec("java", "return value;", java.util.List.of(), " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("expectedOutputType must not be blank");
    }

    @Test
    void scriptProgramRejectsNonCanonicalUnicode() {
        assertThatThrownBy(() -> new ScriptProgramSpec("java", "\ud800", java.util.List.of(), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("source must be valid Unicode");
        assertThatThrownBy(() -> new ScriptProgramSpec("java", "\u00a0", java.util.List.of(), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("source must not be blank");
        assertThatThrownBy(() -> new ScriptProgramSpec("java", "return value;",
                java.util.List.of(new ScriptProgramSpec.Input("val\u202eue", "java.lang.String")), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("name must not contain control characters or Unicode format characters");
    }
}
