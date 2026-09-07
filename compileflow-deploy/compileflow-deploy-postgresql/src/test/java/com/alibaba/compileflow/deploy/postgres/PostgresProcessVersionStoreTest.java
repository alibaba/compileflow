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
package com.alibaba.compileflow.deploy.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.spi.store.PublishedVersionPageKey;
import com.alibaba.compileflow.deploy.spi.store.ProcessVersionRecord;
import java.util.Map;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class PostgresProcessVersionStoreTest {
    private PostgresDeployStore repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresDeployStore(H2TestDatabase.createInMemoryDataSource(), null);
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
    void saveRestoresBorrowedConnectionAutoCommit() throws Exception {
        DataSource database = H2TestDatabase.createInMemoryDataSource();
        Connection connection = spy(database.getConnection());
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);

        new PostgresDeployStore(dataSource, null)
            .save(version("connection-state", "v1", ProcessModelType.TBBPM, "<xml/>", "test", 10L));

        verify(connection).setAutoCommit(false);
        verify(connection).setAutoCommit(true);
    }

    @Test
    void differentVersionsCanUseDifferentModelTypes() {
        ProcessVersionRecord tbbpm =
                repository.save(version("flow", "v1", ProcessModelType.TBBPM, "<xml/>", "first", 10L));

        assertThat(tbbpm.getModelType()).isEqualTo(ProcessModelType.TBBPM);
        assertThat(repository
            .save(version("flow", "v2", ProcessModelType.BPMN, "<xml/>", "second", 20L))
            .getModelType())
            .isEqualTo(ProcessModelType.BPMN);
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
                new PublishedVersionPageKey(30L, "release_30"), 1))
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
            .processDefinition(ProcessDefinition.inline(ProcessModelType.TBBPM, "flow", "<xml/>"))
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
            .processDefinition(ProcessDefinition.inline(modelType, code, content))
            .artifactDigest(ProcessArtifactDigest.compute(ProcessDefinition.inline(modelType, code, content), Map.of()))
            .metadata(Map.of("reason", actor))
            .actor(actor)
            .createdAt(createdAt)
            .build();
    }
}
