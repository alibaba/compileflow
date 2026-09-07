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
package com.alibaba.compileflow.engine.core.semantic;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import com.alibaba.compileflow.engine.core.model.FlowModel;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessSemanticCompilerRegistryTest {
    private ProcessSemanticCompilerProvider provider(ProcessModelType type, RuntimeException reached) {
        return new ProcessSemanticCompilerProvider() {
            @Override
            public ProcessModelType getModelType() {
                return type;
            }

            @Override
            public ProcessSemanticCompiler<?> createSemanticCompiler() {
                return new ProcessSemanticCompiler<FlowModel<?>>(source -> {
                    throw reached;
                }, model -> List.of(), model -> {
                    throw new AssertionError("Unreachable");
                });
            }
        };
    }

    @Test
    void dispatchesOnlyFromTheTypedSnapshot() {
        RuntimeException tbbpm = new IllegalStateException("TBBPM reader");
        RuntimeException bpmn = new IllegalStateException("BPMN reader");
        var registry = new ProcessSemanticCompilerRegistry(List.of(provider(ProcessModelType.TBBPM, tbbpm),
                provider(ProcessModelType.BPMN, bpmn)));
        for (var type : ProcessModelType.values()) {
            var snapshot = ProcessDefinitionSnapshot.of(type, "default", "same", null, new byte[] {1}, "test");
            assertThatThrownBy(() -> registry.compile(snapshot)).isSameAs(type == ProcessModelType.TBBPM ? tbbpm : bpmn);
        }
    }

    @Test
    void duplicateFrontendIsAConfigurationError() {
        var first = provider(ProcessModelType.TBBPM, new IllegalStateException());
        var second = provider(ProcessModelType.TBBPM, new IllegalStateException());
        assertThatThrownBy(() -> new ProcessSemanticCompilerRegistry(List.of(first, second)))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("Multiple semantic compiler providers");
    }

    @Test
    void missingFrontendFailsWithoutGuessingAnotherFormat() {
        var registry = new ProcessSemanticCompilerRegistry(List.of());
        var snapshot =
                ProcessDefinitionSnapshot.of(ProcessModelType.BPMN, "default", "same", null, new byte[] {1}, "test");
        assertThatThrownBy(() -> registry.compile(snapshot))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("BPMN");
    }
}
