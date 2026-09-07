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
package com.alibaba.compileflow.deploy.testkit;

import com.alibaba.compileflow.engine.ProcessModelType;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessRef;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Executable exact-identity contract for artifact-source providers.
 *
 * @author yusu
 */
public abstract class ProcessArtifactSourceContract {
    private static final ProcessRef.Version REF = ProcessRef.version("contract", "order", "v1");
    private static final ProcessDefinition.Inline DEFINITION =
            ProcessDefinition.inline(ProcessModelType.TBBPM, "order", "<flow/>");
    private static final ProcessArtifact ARTIFACT =
            new ProcessArtifact(REF, DEFINITION, ProcessArtifactDigest.compute(DEFINITION, Map.of()));
    private ProcessArtifactSource source;

    /**
     * Creates a source containing exactly the supplied artifact.
     */
    protected abstract ProcessArtifactSource createSource(ProcessArtifact artifact) throws Exception;

    @BeforeEach
    final void initializeSource() throws Exception {
        source = createSource(ARTIFACT);
    }

    @Test
    void returnsTheExactStoredArtifact() {
        assertThat(source.find(REF)).contains(ARTIFACT);
    }

    @Test
    void doesNotFallbackAcrossVersionOrProcessIdentity() {
        assertThat(source.find(ProcessRef.version("contract", "order", "v2"))).isEmpty();
        assertThat(source.find(ProcessRef.version("contract", "other", "v1"))).isEmpty();
        assertThat(source.find(ProcessRef.version("other", "order", "v1"))).isEmpty();
    }
}
