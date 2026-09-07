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
package com.alibaba.compileflow.deploy.jdbc;

import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.spi.store.ProcessAliasRecord;
import com.alibaba.compileflow.deploy.spi.store.ProcessAliasStore;
import com.alibaba.compileflow.deploy.spi.store.ProcessKey;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * JDBC read Store for committed Alias routes.
 *
 * @author yusu
 */
final class JdbcProcessAliasStore implements ProcessAliasStore {
    private static final String TABLE = "cf_process_alias";
    private static final String COLUMNS =
            "namespace, code, alias, stable_version, candidate_version, candidate_weight_bps, revision,"
            + " targeting_policy, targeting_parameters, updated_by, updated_at";
    private static final String LIST_DISTINCT_PROCESSES_SQL =
            "SELECT DISTINCT namespace, code FROM " + TABLE + " ORDER BY namespace, code";
    private static final String LIST_ALL_BY_UPDATED_AT_DESC_SQL = "SELECT " + COLUMNS + " FROM " + TABLE
            + " WHERE namespace = ? AND code = ? ORDER BY updated_at DESC, alias ASC";
    private static final String LIST_BY_ALIAS_ASC_SQL = "SELECT " + COLUMNS + " FROM " + TABLE
            + " WHERE namespace = ? AND code = ? ORDER BY alias ASC LIMIT ? OFFSET ?";
    private static final String LIST_BY_ALIAS_DESC_SQL = "SELECT " + COLUMNS + " FROM " + TABLE
            + " WHERE namespace = ? AND code = ? ORDER BY alias DESC LIMIT ? OFFSET ?";
    private static final String LIST_BY_UPDATED_AT_ASC_SQL = "SELECT " + COLUMNS + " FROM " + TABLE
            + " WHERE namespace = ? AND code = ? ORDER BY updated_at ASC, alias ASC LIMIT ? OFFSET ?";
    private static final String LIST_BY_UPDATED_AT_DESC_SQL = "SELECT " + COLUMNS + " FROM " + TABLE
            + " WHERE namespace = ? AND code = ? ORDER BY updated_at DESC, alias ASC LIMIT ? OFFSET ?";
    private static final int MAX_PAGE_SIZE = 1000;
    private final DataSource dataSource;

    JdbcProcessAliasStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    private static PreparedStatement prepareListStatement(Connection connection, boolean sortByAlias, boolean ascending)
            throws SQLException {
        if (sortByAlias && ascending) {
            return connection.prepareStatement(LIST_BY_ALIAS_ASC_SQL);
        }
        if (sortByAlias) {
            return connection.prepareStatement(LIST_BY_ALIAS_DESC_SQL);
        }
        if (ascending) {
            return connection.prepareStatement(LIST_BY_UPDATED_AT_ASC_SQL);
        }
        return connection.prepareStatement(LIST_BY_UPDATED_AT_DESC_SQL);
    }

    private static DeploymentException jdbcError(String message, Throwable cause) {
        return DeploymentException.of(DeploymentErrorCode.STORAGE_ERROR, message, cause);
    }

    @Override
    public long countDistinctProcesses(String namespace) {
        String sql = "SELECT COUNT(DISTINCT code) FROM " + TABLE + " WHERE namespace = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requireNamespace(namespace));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Alias process count query returned no row");
                }
                return result.getLong(1);
            }
        } catch (SQLException failure) {
            throw jdbcError("Failed to count process identities with Aliases", failure);
        }
    }

    @Override
    public Optional<ProcessAliasRecord> resolve(String namespace, String code, String alias) {
        ProcessRef.Alias ref = ProcessRef.alias(namespace, code, alias);
        String sql = "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE namespace = ? AND code = ? AND alias = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ref.namespace());
            statement.setString(2, ref.code());
            statement.setString(3, ref.alias());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw jdbcError("Failed to resolve alias route", failure);
        }
    }

    @Override
    public List<ProcessKey> listDistinctProcesses() {
        List<ProcessKey> processes = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(LIST_DISTINCT_PROCESSES_SQL);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                processes.add(new ProcessKey(result.getString(1), result.getString(2)));
            }
            return Collections.unmodifiableList(processes);
        } catch (SQLException failure) {
            throw jdbcError("Failed to list process identities with Aliases", failure);
        }
    }

    @Override
    public List<ProcessAliasRecord> listAll(String namespace, String code) {
        ProcessKey ref = new ProcessKey(namespace, code);
        List<ProcessAliasRecord> records = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(LIST_ALL_BY_UPDATED_AT_DESC_SQL)) {
            statement.setString(1, ref.namespace());
            statement.setString(2, ref.code());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    records.add(map(result));
                }
            }
            return Collections.unmodifiableList(records);
        } catch (SQLException failure) {
            throw jdbcError("Failed to list all alias routes", failure);
        }
    }

    @Override
    public List<ProcessAliasRecord> list(String namespace, String code, String sortBy, boolean ascending, int limit,
            int offset) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        ProcessKey ref = new ProcessKey(namespace, code);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                prepareListStatement(connection, "alias".equalsIgnoreCase(sortBy), ascending)) {
            statement.setString(1, ref.namespace());
            statement.setString(2, ref.code());
            statement.setInt(3, Math.min(limit, MAX_PAGE_SIZE));
            statement.setInt(4, Math.max(0, offset));
            List<ProcessAliasRecord> records = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    records.add(map(result));
                }
            }
            return Collections.unmodifiableList(records);
        } catch (SQLException failure) {
            throw jdbcError("Failed to list alias routes", failure);
        }
    }

    private ProcessAliasRecord map(ResultSet result) throws SQLException {
        int weightBps = result.getInt("candidate_weight_bps");
        Integer candidateWeightBps = result.wasNull() ? null : Integer.valueOf(weightBps);
        return ProcessAliasRecord
            .builder()
            .namespace(result.getString("namespace"))
            .code(result.getString("code"))
            .alias(result.getString("alias"))
            .stableVersion(result.getString("stable_version"))
            .candidateVersion(result.getString("candidate_version"))
            .candidateWeightBps(candidateWeightBps)
            .targeting(decodeTargeting(result.getString("targeting_policy"), result.getString("targeting_parameters")))
            .revision(result.getLong("revision"))
            .updatedBy(result.getString("updated_by"))
            .updatedAt(result.getLong("updated_at"))
            .build();
    }

    private AliasTargeting decodeTargeting(String policy, String parametersJson) throws SQLException {
        try {
            var parameters = JdbcDeployJson.readStringMap(parametersJson);
            if (policy == null) {
                if (!parameters.isEmpty()) {
                    throw new IllegalArgumentException("Targeting parameters require a targeting policy");
                }
                return null;
            }
            return new AliasTargeting(policy, parameters);
        } catch (RuntimeException failure) {
            throw new SQLException("Stored Alias targeting configuration is invalid", failure);
        }
    }

    private String requireNamespace(String namespace) {
        return ProcessIdentifiers.requireNamespace(namespace);
    }
}
