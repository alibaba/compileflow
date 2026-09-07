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
package com.alibaba.compileflow.deploy.mysql;

import com.alibaba.compileflow.engine.ProcessModelType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.deploy.spi.store.ProcessAliasRecord;
import com.alibaba.compileflow.deploy.spi.store.ProcessKey;
import com.alibaba.compileflow.deploy.spi.store.ProcessVersionRecord;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessRef;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class MySqlProcessAliasStoreTest {
    private static final String CODE = "route.list";
    private DataSource dataSource;
    private MySqlDeployStore repository;

    private static List<String> aliases(List<ProcessAliasRecord> records) {
        return records.stream().map(ProcessAliasRecord::getAlias).toList();
    }

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = H2TestDatabase.createInMemoryDataSource();
        repository = new MySqlDeployStore(dataSource, null);
        ProcessDefinition.Inline definition =
                ProcessDefinition.inline(ProcessModelType.TBBPM, CODE, "<bpm code=\"route.list\"/>");
        new MySqlDeployStore(dataSource, null)
            .save(ProcessVersionRecord
                .builder()
                .namespace(ProcessRef.DEFAULT_NAMESPACE)
                .code(CODE)
                .version("v1")
                .processDefinition(definition)
                .artifactDigest(ProcessArtifactDigest.compute(definition, Map.of()))
                .actor("test")
                .createdAt(1L)
                .build());
        insert("staging", 200L);
        insert("production", 300L);
        insert("development", 100L);
    }

    @Test
    void listsByAliasInBothDirections() {
        assertThat(aliases(repository.list(ProcessRef.DEFAULT_NAMESPACE, CODE, "alias", true, 10, 0)))
            .containsExactly("development", "production", "staging");
        assertThat(aliases(repository.list(ProcessRef.DEFAULT_NAMESPACE, CODE, "alias", false, 10, 0)))
            .containsExactly("staging", "production", "development");
    }

    @Test
    void listsByUpdatedTimeWithStablePagination() {
        assertThat(aliases(repository.list(ProcessRef.DEFAULT_NAMESPACE, CODE, "updatedAt", false, 2, 0)))
            .containsExactly("production", "staging");
        assertThat(aliases(repository.list(ProcessRef.DEFAULT_NAMESPACE, CODE, "updatedAt", true, 2, 1)))
            .containsExactly("staging", "production");
    }

    @Test
    void countsEachProcessOnceAcrossAliases() throws SQLException {
        assertThat(repository.countDistinctProcesses(ProcessRef.DEFAULT_NAMESPACE)).isEqualTo(1);
        assertThat(repository.listDistinctProcesses()).containsExactly(
                new ProcessKey(ProcessRef.DEFAULT_NAMESPACE, CODE));

        String otherCode = "route.other";
        new MySqlDeployStore(dataSource, null)
            .save(ProcessVersionRecord
                .builder()
                .namespace(ProcessRef.DEFAULT_NAMESPACE)
                .code(otherCode)
                .version("v1")
                .processDefinition(ProcessDefinition.inline(ProcessModelType.TBBPM, otherCode,
                        "<bpm code=\"route.other\"/>"))
                .artifactDigest(ProcessArtifactDigest.compute(ProcessDefinition.inline(ProcessModelType.TBBPM,
                                "route.other", "<bpm code=\"route.other\"/>"), Map.of()))
                .actor("test")
                .createdAt(1L)
                .build());
        insert(otherCode, "production", 400L);

        assertThat(repository.countDistinctProcesses(ProcessRef.DEFAULT_NAMESPACE)).isEqualTo(2);
        assertThat(repository.countDistinctProcesses("other")).isZero();
        assertThat(repository.listDistinctProcesses())
            .containsExactly(new ProcessKey(ProcessRef.DEFAULT_NAMESPACE, CODE),
                    new ProcessKey(ProcessRef.DEFAULT_NAMESPACE, otherCode));
    }

    @Test
    void unboundedListDoesNotSilentlyTruncateAtPageLimit() throws SQLException {
        insertAliases(1_000);

        assertThat(repository.listAll(ProcessRef.DEFAULT_NAMESPACE, CODE)).hasSize(1_003);
    }

    @Test
    void normalizesInvalidPageBounds() {
        assertThat(repository.list(ProcessRef.DEFAULT_NAMESPACE, CODE, "updatedAt", false, 0, 0)).isEmpty();
        assertThat(aliases(repository.list(ProcessRef.DEFAULT_NAMESPACE, CODE, "updatedAt", false, 1, -10)))
            .containsExactly("production");
    }

    @Test
    void rejectsMissingExplicitNamespace() {
        assertThatThrownBy(() -> repository.list(null, CODE, "alias", true, 10, 0))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("namespace");
    }

    private void insert(String alias, long updatedAt) throws SQLException {
        insert(CODE, alias, updatedAt);
    }

    private void insert(String code, String alias, long updatedAt) throws SQLException {
        String sql =
                "INSERT INTO cf_process_alias (namespace, code, alias, stable_version, "
                + "candidate_version, candidate_weight_bps, revision, created_by, updated_by, "
                + "created_at, updated_at) VALUES (?, ?, ?, ?, NULL, NULL, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ProcessRef.DEFAULT_NAMESPACE);
            statement.setString(2, code);
            statement.setString(3, alias);
            statement.setString(4, "v1");
            statement.setLong(5, 1L);
            statement.setString(6, "test");
            statement.setString(7, "test");
            statement.setLong(8, updatedAt);
            statement.setLong(9, updatedAt);
            statement.executeUpdate();
        }
    }

    private void insertAliases(int count) throws SQLException {
        String sql =
                "INSERT INTO cf_process_alias (namespace, code, alias, stable_version, "
                + "candidate_version, candidate_weight_bps, revision, created_by, updated_by, "
                + "created_at, updated_at) VALUES (?, ?, ?, ?, NULL, NULL, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < count; index++) {
                long timestamp = index + 1L;
                statement.setString(1, ProcessRef.DEFAULT_NAMESPACE);
                statement.setString(2, CODE);
                statement.setString(3, "bulk-" + index);
                statement.setString(4, "v1");
                statement.setLong(5, 1L);
                statement.setString(6, "test");
                statement.setString(7, "test");
                statement.setLong(8, timestamp);
                statement.setLong(9, timestamp);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
