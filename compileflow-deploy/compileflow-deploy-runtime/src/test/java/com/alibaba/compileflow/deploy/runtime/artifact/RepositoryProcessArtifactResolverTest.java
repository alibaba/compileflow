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
package com.alibaba.compileflow.deploy.runtime.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RepositoryProcessArtifactResolverTest {
    @Test
    void resolvesFromRepositoryUsingThePublishedDigest() {
        ProcessArtifactSource source = mock(ProcessArtifactSource.class);
        ProcessDefinition.Inline definition = ProcessDefinition.inline("demo.flow", "<definitions/>");
        ProcessRef.Version ref = ProcessRef.version("default", "demo.flow", "v1");
        String digest = ProcessArtifactDigest.compute(ProcessModelType.TBBPM, definition, java.util.Map.of());
        ProcessArtifact stored = new ProcessArtifact(ref, ProcessModelType.TBBPM, definition, digest);
        when(source.find(ref)).thenReturn(Optional.of(stored));

        RepositoryProcessArtifactResolver resolver = new RepositoryProcessArtifactResolver(source);
        ProcessArtifact artifact = resolver.resolve(ProcessRef.version("default", "demo.flow", "v1"));
        assertThat(artifact.getRef().code()).isEqualTo("demo.flow");
        assertThat(artifact.getRef().version()).isEqualTo("v1");
        assertThat(artifact.getModelType()).isEqualTo(ProcessModelType.TBBPM);
        assertThat(artifact.getArtifactDigest()).isEqualTo(digest);
    }

    @Test
    void rejectsARepositoryRecordWithTheWrongIdentity() {
        ProcessArtifactSource source = mock(ProcessArtifactSource.class);
        ProcessDefinition.Inline definition = ProcessDefinition.inline("demo.flow", "<definitions/>");
        ProcessRef.Version requested = ProcessRef.version("default", "demo.flow", "v1");
        ProcessRef.Version storedRef = ProcessRef.version("default", "demo.flow", "v2");
        ProcessArtifact stored = new ProcessArtifact(storedRef, ProcessModelType.TBBPM, definition,
                ProcessArtifactDigest.compute(ProcessModelType.TBBPM, definition, java.util.Map.of()));
        when(source.find(requested)).thenReturn(Optional.of(stored));

        RepositoryProcessArtifactResolver resolver = new RepositoryProcessArtifactResolver(source);

        assertThatThrownBy(() -> resolver.resolve(ProcessRef.version("default", "demo.flow", "v1")))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH));
    }

    @Test
    void shouldWrapRepositoryFailureWithDeploymentContext() {
        ProcessArtifactSource source = mock(ProcessArtifactSource.class);
        when(source.find(ProcessRef.version("default", "demo.flow", "v1"))).thenThrow(
                new IllegalStateException("db down"));

        RepositoryProcessArtifactResolver resolver = new RepositoryProcessArtifactResolver(source);
        assertThatThrownBy(() -> resolver.resolve(ProcessRef.version("default", "demo.flow", "v1")))
            .isInstanceOfSatisfying(DeploymentException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(DeploymentErrorCode.REPOSITORY_ERROR);
                assertThat(ex.getNamespace()).isEqualTo("default");
                assertThat(ex.getCode()).isEqualTo("demo.flow");
                assertThat(ex.getVersion()).isEqualTo("v1");
            });
    }
}
