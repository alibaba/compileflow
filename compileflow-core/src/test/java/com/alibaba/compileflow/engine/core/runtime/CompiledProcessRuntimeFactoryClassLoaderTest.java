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
package com.alibaba.compileflow.engine.core.runtime;

import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.inline;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.resolved;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.core.java.compiler.JavaCompiler;
import com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompiler;
import com.alibaba.compileflow.engine.core.source.FlowModelReader;
import com.alibaba.compileflow.engine.core.validation.FlowModelValidator;
import com.alibaba.compileflow.engine.core.model.FlowModel;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompiledProcessRuntimeFactoryClassLoaderTest {
    @Test
    void installsAndRestoresTheCompilationContextClassLoader() {
        ClassLoader compilationLoader = new ClassLoader(getClass().getClassLoader()) {
        };
        Thread currentThread = Thread.currentThread();
        ClassLoader original = currentThread.getContextClassLoader();
        RuntimeException expected = new RuntimeException("stop after checking loader");
        FlowModelReader<FlowModel<?>> reader = new FlowModelReader<>() {
            @Override
            public FlowModel<?> read(ProcessDefinitionSnapshot definition) {
                assertThat(currentThread.getContextClassLoader()).isSameAs(compilationLoader);
                throw expected;
            }
        };
        JavaCompiler compiler = (sources, output, option) -> {};
        FlowModelValidator validator = model -> List.of();
        ProcessSemanticCompiler<FlowModel<?>> semanticCompiler =
                new ProcessSemanticCompiler<>(reader, validator, model -> {
            throw new AssertionError("semantic frontend must not run");
        });
        CompiledProcessRuntimeFactory factory = new CompiledProcessRuntimeFactory(semanticCompiler,
                ScriptExecutorRegistry.from(List.of()), compiler, JavaDiagnosticsConfig.defaults());

        assertThatThrownBy(() -> factory.createRuntime(resolved(inline("test", "<flow/>")), compilationLoader))
            .isSameAs(expected);
        assertThat(currentThread.getContextClassLoader()).isSameAs(original);
    }
}
