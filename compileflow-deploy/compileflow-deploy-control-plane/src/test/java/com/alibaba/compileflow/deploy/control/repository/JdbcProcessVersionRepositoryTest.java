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
import com.alibaba.compileflow.deploy.control.test.H2TestDatabase;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class JdbcProcessVersionRepositoryTest {
    private JdbcProcessVersionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcProcessVersionRepository(H2TestDatabase.createInMemoryDataSource());
    }

    @Test
    void saveIsIdempotentOnlyForTheSameImmutableContentAndModelType() {
        ProcessVersionRecord first =
                repository.save(version("flow", "v1", ProcessModelType.TBBPM, "<xml/>", "first", 10L));
        ProcessVersionRecord replay =
                repository.save(version("flow", "v1", ProcessModelType.TBBPM, "<xml/>", "retry", 20L));

        assertThat(replay.getCreatedAt()).isEqualTo(first.getCreatedAt());
        assertThat(replay.getActor()).isEqualTo("first");
        assertThat(replay.getMetadata()).containsExactlyEntriesOf(Map.of("reason", "first"));

        assertThatThrownBy(() -> repository.save(
                version("flow", "v1", ProcessModelType.TBBPM, "<xml changed=\"true\"/>", "retry", 30L)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.VERSION_CONFLICT));
        assertThatThrownBy(() -> repository.save(version("flow", "v1", ProcessModelType.BPMN, "<xml/>", "retry", 30L)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.VERSION_CONFLICT));
    }

    @Test
    void modelTypeBelongsToEachImmutableVersion() {
        ProcessVersionRecord tbbpm =
                repository.save(version("flow", "v1", ProcessModelType.TBBPM, "<xml/>", "first", 10L));
        ProcessVersionRecord bpmn =
                repository.save(version("flow", "v2", ProcessModelType.BPMN, "<xml/>", "second", 20L));

        assertThat(tbbpm.getModelType()).isEqualTo(ProcessModelType.TBBPM);
        assertThat(bpmn.getModelType()).isEqualTo(ProcessModelType.BPMN);
    }

    @Test
    void listUsesDeterministicNewestFirstOrdering() {
        repository.save(version("ordered", "v1", ProcessModelType.TBBPM, "<xml id=\"1\"/>", "test", 10L));
        repository.save(version("ordered", "v2", ProcessModelType.TBBPM, "<xml id=\"2\"/>", "test", 20L));
        repository.save(version("ordered", "v3", ProcessModelType.TBBPM, "<xml id=\"3\"/>", "test", 20L));

        assertThat(repository.list(ProcessRef.DEFAULT_NAMESPACE, "ordered", null, null, 100))
            .extracting(ProcessVersionRecord::getVersion)
            .containsExactly("v3", "v2", "v1");
    }

    @Test
    void pagesAndTreatsVersionPrefixMetacharactersLiterally() {
        repository.save(version("filtered", "release-10", ProcessModelType.TBBPM, "<xml id=\"1\"/>", "test", 10L));
        repository.save(version("filtered", "release_20", ProcessModelType.TBBPM, "<xml id=\"2\"/>", "test", 20L));
        repository.save(version("filtered", "release_30", ProcessModelType.TBBPM, "<xml id=\"3\"/>", "test", 30L));

        assertThat(repository.list(ProcessRef.DEFAULT_NAMESPACE, "filtered", "release_",
                new DeployCursorCodec.PublishedVersionKey(30L, "release_30"), 1))
            .extracting(ProcessVersionRecord::getVersion)
            .containsExactly("release_20");
    }

    @Test
    void blankNamespaceIsRejected() {
        repository.save(version("namespaced", "v1", ProcessModelType.TBBPM, "<xml/>", "test", 10L));

        assertThatThrownBy(() -> repository.find(" ", "namespaced", "v1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("namespace");
    }

    @Test
    void recordRejectsADigestThatDoesNotDescribeItsExecutableArtifact() {
        assertThatThrownBy(() -> ProcessVersionRecord
            .builder()
            .namespace(ProcessRef.DEFAULT_NAMESPACE)
            .code("flow")
            .version("v1")
            .modelType(ProcessModelType.TBBPM)
            .processDefinition(ProcessDefinition.inline("flow", "<xml/>"))
            .artifactDigest("0".repeat(64))
            .metadata(Map.of())
            .actor("test")
            .createdAt(10L)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("executable process artifact");
    }

    private ProcessVersionRecord version(String code, String version, ProcessModelType modelType, String content,
            String actor, long createdAt) {
        return ProcessVersionRecord
            .builder()
            .namespace(ProcessRef.DEFAULT_NAMESPACE)
            .code(code)
            .version(version)
            .modelType(modelType)
            .processDefinition(ProcessDefinition.inline(code, content))
            .artifactDigest(ProcessArtifactDigest.compute(modelType, ProcessDefinition.inline(code, content), Map.of()))
            .metadata(Map.of("reason", actor))
            .actor(actor)
            .createdAt(createdAt)
            .build();
    }
}
