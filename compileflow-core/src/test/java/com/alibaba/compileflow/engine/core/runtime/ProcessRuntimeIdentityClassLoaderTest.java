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

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.runtimeIdentity;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProcessRuntimeIdentityClassLoaderTest {
    @Test
    void classLoaderComparisonUsesReferenceIdentity() {
        ProcessRuntimeRequest source = RuntimeTestFixtures.inline("test", "<flow/>");
        ClassLoader parent = getClass().getClassLoader();
        ClassLoader first = new EqualClassLoader(parent);
        ClassLoader second = new EqualClassLoader(parent);

        ProcessRuntimeIdentity firstIdentity = runtimeIdentity(source, first);

        assertThat(runtimeIdentity(source, first)).isEqualTo(firstIdentity);
        assertThat(runtimeIdentity(source, second)).isNotEqualTo(firstIdentity);
    }

    @Test
    void originDoesNotParticipateInCompilationIdentity() {
        ClassLoader classLoader = getClass().getClassLoader();
        ProcessRuntimeIdentity.PipelineIdentity pipelineIdentity = ProcessRuntimeIdentity.newPipelineIdentity();
        byte[] bytes = "<flow/>".getBytes(StandardCharsets.UTF_8);
        ProcessRuntimeIdentity first = runtimeIdentity(ProcessDefinitionSnapshot.of(ProcessModelType.TBBPM, "default",
                        "test", null, bytes, "file first.bpm"), pipelineIdentity, classLoader);
        ProcessRuntimeIdentity repeated = runtimeIdentity(ProcessDefinitionSnapshot.of(ProcessModelType.TBBPM, "default",
                        "test", null, bytes, "classpath flows/second.bpm"), pipelineIdentity, classLoader);

        assertThat(repeated).isEqualTo(first);
    }

    @Test
    void compilationPipelineComparisonUsesReferenceIdentity() {
        ClassLoader classLoader = getClass().getClassLoader();
        ProcessDefinitionSnapshot definition = ProcessDefinitionSnapshot.of(ProcessModelType.TBBPM, "default", "test",
                null, "<flow/>".getBytes(StandardCharsets.UTF_8), "test");
        ProcessRuntimeIdentity.PipelineIdentity firstScope = ProcessRuntimeIdentity.newPipelineIdentity();
        ProcessRuntimeIdentity.PipelineIdentity secondScope = ProcessRuntimeIdentity.newPipelineIdentity();
        ProcessRuntimeIdentity first = runtimeIdentity(definition, firstScope, classLoader);

        assertThat(runtimeIdentity(definition, firstScope, classLoader)).isEqualTo(first);
        assertThat(runtimeIdentity(definition, secondScope, classLoader)).isNotEqualTo(first);
    }

    private static final class EqualClassLoader extends ClassLoader {
        private EqualClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualClassLoader;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
