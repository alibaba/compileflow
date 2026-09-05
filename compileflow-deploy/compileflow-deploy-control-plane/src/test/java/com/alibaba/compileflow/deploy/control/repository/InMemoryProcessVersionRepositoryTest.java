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
package com.alibaba.compileflow.deploy.control.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class InMemoryProcessVersionRepositoryTest {
    @Test
    void replayReturnsThePersistedWinnerWithoutRewritingAuditData() {
        InMemoryProcessVersionRepository repository = new InMemoryProcessVersionRepository();
        ProcessVersionRecord first = repository.save(version(ProcessModelType.TBBPM, "<xml/>", "first", 1L));
        ProcessVersionRecord replay = repository.save(version(ProcessModelType.TBBPM, "<xml/>", "retry", 2L));

        assertThat(replay).isSameAs(first);
        assertThat(replay.getActor()).isEqualTo("first");
        assertThat(replay.getCreatedAt()).isEqualTo(1L);
        assertThat(replay.getMetadata()).containsExactlyEntriesOf(Map.of("reason", "first"));
    }

    @Test
    void anExistingVersionCannotBeReboundToDifferentContent() {
        InMemoryProcessVersionRepository repository = new InMemoryProcessVersionRepository();
        repository.save(version(ProcessModelType.TBBPM, "<xml/>", "first", 1L));

        assertThatThrownBy(() -> repository.save(
                version(ProcessModelType.TBBPM, "<xml changed=\"true\"/>", "retry", 2L)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.VERSION_CONFLICT));
    }

    @Test
    void anExistingVersionCannotBeReinterpretedAsAnotherModelType() {
        InMemoryProcessVersionRepository repository = new InMemoryProcessVersionRepository();
        repository.save(version(ProcessModelType.TBBPM, "<xml/>", "first", 1L));

        assertThatThrownBy(() -> repository.save(version(ProcessModelType.BPMN, "<xml/>", "retry", 2L)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.VERSION_CONFLICT));
    }

    @Test
    void listUsesTheSameLiteralPrefixOrderingAndPaginationAsJdbc() {
        InMemoryProcessVersionRepository repository = new InMemoryProcessVersionRepository();
        repository.save(version("release-10", ProcessModelType.TBBPM, "<xml id=\"1\"/>", "first", 10L));
        repository.save(version("release_20", ProcessModelType.TBBPM, "<xml id=\"2\"/>", "first", 20L));
        repository.save(version("release_30", ProcessModelType.TBBPM, "<xml id=\"3\"/>", "first", 30L));

        assertThat(repository.list(ProcessRef.DEFAULT_NAMESPACE, "flow", "release_",
                new DeployCursorCodec.PublishedVersionKey(30L, "release_30"), 1))
            .extracting(ProcessVersionRecord::getVersion)
            .containsExactly("release_20");
    }

    private ProcessVersionRecord version(ProcessModelType modelType, String content, String actor, long createdAt) {
        return version("v1", modelType, content, actor, createdAt);
    }

    private ProcessVersionRecord version(String version, ProcessModelType modelType, String content, String actor,
            long createdAt) {
        return ProcessVersionRecord
            .builder()
            .namespace(ProcessRef.DEFAULT_NAMESPACE)
            .code("flow")
            .version(version)
            .modelType(modelType)
            .processDefinition(ProcessDefinition.inline("flow", content))
            .artifactDigest(ProcessArtifactDigest.compute(modelType, ProcessDefinition.inline("flow", content), Map.of()))
            .metadata(Map.of("reason", actor))
            .actor(actor)
            .createdAt(createdAt)
            .build();
    }
}
