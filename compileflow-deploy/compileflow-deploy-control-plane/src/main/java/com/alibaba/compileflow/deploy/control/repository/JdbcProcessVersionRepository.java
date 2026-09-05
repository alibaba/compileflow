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

import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.artifact.ProcessCallBinding;
import com.alibaba.compileflow.deploy.api.protocol.json.DeploymentProtocolJson;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostgreSQL authority, accessed through JDBC, for immutable published process
 * versions.
 *
 * @author yusu
 */
public final class JdbcProcessVersionRepository implements ProcessVersionRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcProcessVersionRepository.class);
    private static final String TABLE = "cf_process_version";
    private static final String COLUMNS =
            "namespace, code, version, model_type, content, digest, call_bindings, metadata, actor, created_at";
    private static final String SELECT_BASE = "SELECT " + COLUMNS + " FROM " + TABLE + " ";
    private final DataSource dataSource;

    public JdbcProcessVersionRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    private static boolean sameContent(ProcessVersionRecord existing, ProcessVersionRecord candidate) {
        return existing.getModelType() == candidate.getModelType()
                && existing.getProcessDefinition().equals(candidate.getProcessDefinition())
                && existing.getArtifactDigest().equals(candidate.getArtifactDigest())
                && existing.getCallBindings().equals(candidate.getCallBindings());
    }

    private static DeploymentException versionConflict(ProcessVersionRecord candidate) {
        return DeploymentException
            .builder(DeploymentErrorCode.VERSION_CONFLICT,
                    "Process version already exists with different content or model type")
            .namespace(candidate.getNamespace())
            .code(candidate.getCode())
            .version(candidate.getVersion())
            .build();
    }

    private static boolean isConstraintViolation(SQLException failure) {
        String sqlState = failure.getSQLState();
        return sqlState != null && sqlState.startsWith("23");
    }

    private static String versionFilter(String pattern) {
        return pattern == null ? "" : " AND version LIKE ? ESCAPE '!'";
    }

    private static String versionPrefixPattern(String versionPrefix) {
        if (versionPrefix == null) {
            return null;
        }
        return versionPrefix.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static DeploymentException jdbcError(String message, Throwable cause) {
        return DeploymentException.of(DeploymentErrorCode.REPOSITORY_ERROR, message, cause);
    }

    @Override
    public long currentTimeMillis() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT CURRENT_TIMESTAMP");
                ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("Database clock query returned no row");
            }
            return result.getTimestamp(1).getTime();
        } catch (SQLException failure) {
            throw jdbcError("Failed to read the database clock", failure);
        }
    }

    @Override
    public ProcessVersionRecord save(ProcessVersionRecord record) {
        ProcessVersionRecord candidate = Objects.requireNonNull(record, "record");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ProcessVersionRecord stored = insertVersion(connection, candidate);
                connection.commit();
                LOGGER.info("Inserted process version: ns={} code={} version={}", candidate.getNamespace(),
                        candidate.getCode(), candidate.getVersion());
                return stored;
            } catch (SQLException failure) {
                rollback(connection, failure);
                throw failure;
            } catch (DeploymentException failure) {
                rollback(connection, failure);
                throw failure;
            } catch (RuntimeException | Error failure) {
                rollback(connection, failure);
                throw failure;
            }
        } catch (DeploymentException failure) {
            throw failure;
        } catch (SQLException failure) {
            throw jdbcError("Failed to save process version", failure);
        }
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    @Override
    public Optional<ProcessVersionRecord> find(String namespace, String code, String version) {
        ProcessRef.Version ref = ProcessRef.version(namespace, code, version);
        try (Connection connection = dataSource.getConnection()) {
            return fetch(connection, ref.namespace(), ref.code(), ref.version());
        } catch (SQLException failure) {
            throw jdbcError("Failed to find process version", failure);
        }
    }

    @Override
    public List<ProcessVersionRecord> list(String namespace, String code, String versionPrefix,
            DeployCursorCodec.PublishedVersionKey cursor, int limit) {
        if (limit <= 0 || limit > 101) {
            throw new IllegalArgumentException("limit must be between 1 and 101");
        }
        ProcessKey ref = new ProcessKey(namespace, code);
        String pattern = versionPrefixPattern(versionPrefix);
        String sql = SELECT_BASE + "WHERE namespace = ? AND code = ?" + versionFilter(pattern)
                + (cursor == null ? "" : " AND (created_at < ? OR (created_at = ? AND version < ?))")
                + " ORDER BY created_at DESC, version DESC LIMIT ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ref.namespace());
            statement.setString(2, ref.code());
            int nextIndex = 3;
            if (pattern != null) {
                statement.setString(nextIndex++, pattern);
            }
            if (cursor != null) {
                statement.setLong(nextIndex++, cursor.createdAt());
                statement.setLong(nextIndex++, cursor.createdAt());
                statement.setString(nextIndex++, cursor.version());
            }
            statement.setInt(nextIndex, limit);
            List<ProcessVersionRecord> records = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    records.add(map(result));
                }
            }
            return Collections.unmodifiableList(records);
        } catch (SQLException failure) {
            throw jdbcError("Failed to list process versions", failure);
        }
    }

    private ProcessVersionRecord insertVersion(Connection connection, ProcessVersionRecord record) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Savepoint savepoint = connection.setSavepoint();
        try {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, record.getNamespace());
                statement.setString(2, record.getCode());
                statement.setString(3, record.getVersion());
                statement.setString(4, record.getModelType().name());
                statement.setString(5, record.getProcessDefinition().content());
                statement.setString(6, record.getArtifactDigest());
                statement.setString(7, encodeCallBindings(record.getCallBindings()));
                statement.setString(8, encodeMetadata(record.getMetadata()));
                statement.setString(9, record.getActor());
                statement.setLong(10, record.getCreatedAt());
                statement.executeUpdate();
            }
            return record;
        } catch (SQLException failure) {
            if (!isConstraintViolation(failure)) {
                throw failure;
            }
            connection.rollback(savepoint);
            ProcessVersionRecord stored =
                    fetch(connection, record.getNamespace(), record.getCode(), record.getVersion()).orElse(null);
            if (stored == null) {
                throw failure;
            }
            if (sameContent(stored, record)) {
                return stored;
            }
            throw versionConflict(record);
        } finally {
            try {
                connection.releaseSavepoint(savepoint);
            } catch (SQLException ignored) {
                // Some drivers release savepoints implicitly after rollback.
            }
        }
    }

    private Optional<ProcessVersionRecord> fetch(Connection connection, String namespace, String code, String version)
            throws SQLException {
        String sql = SELECT_BASE + "WHERE namespace = ? AND code = ? AND version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            statement.setString(2, code);
            statement.setString(3, version);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    private ProcessVersionRecord map(ResultSet result) throws SQLException {
        String namespace = result.getString("namespace");
        String code = result.getString("code");
        String version = result.getString("version");
        return ProcessVersionRecord
            .builder()
            .namespace(namespace)
            .code(code)
            .version(version)
            .modelType(ProcessModelType.valueOf(result.getString("model_type")))
            .processDefinition(ProcessDefinition.inline(code, result.getString("content")))
            .artifactDigest(result.getString("digest"))
            .callBindings(decodeCallBindings(result.getString("call_bindings")))
            .metadata(decodeMetadata(result.getString("metadata")))
            .actor(result.getString("actor"))
            .createdAt(result.getLong("created_at"))
            .build();
    }

    private String encodeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return DeploymentProtocolJson.write(metadata);
        } catch (Exception failure) {
            throw jdbcError("Failed to encode version metadata", failure);
        }
    }

    private Map<String, String> decodeMetadata(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyMap();
        }
        try {
            return DeploymentProtocolJson.readStringMap(json);
        } catch (Exception failure) {
            throw jdbcError("Failed to decode version metadata", failure);
        }
    }

    private String encodeCallBindings(List<ProcessCallBinding> bindings) {
        List<Map<String, String>> payload = bindings
            .stream()
            .map(binding -> Map.of("callSiteId", binding.callSiteId(), "code", binding.code(), "namespace",
                    binding.target().namespace(), "version", binding.target().version()))
            .toList();
        try {
            return DeploymentProtocolJson.write(payload);
        } catch (Exception failure) {
            throw jdbcError("Failed to encode Process call bindings", failure);
        }
    }

    private List<ProcessCallBinding> decodeCallBindings(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            List<Map<String, String>> values = DeploymentProtocolJson.readStringMapList(json);
            return values
                .stream()
                .map(value -> new ProcessCallBinding(value.get("callSiteId"),
                        ProcessRef.version(value.get("namespace"), value.get("code"), value.get("version"))))
                .toList();
        } catch (Exception failure) {
            throw jdbcError("Failed to decode Process call bindings", failure);
        }
    }
}
