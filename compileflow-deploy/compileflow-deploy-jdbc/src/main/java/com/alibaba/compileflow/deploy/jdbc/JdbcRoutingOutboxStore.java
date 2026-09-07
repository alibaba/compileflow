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
import com.alibaba.compileflow.deploy.spi.store.DeploymentPersistentDigests;
import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxRecord;
import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxStore;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessRef;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC persistence for routing-outbox claim, delivery, and retention.
 *
 * @author yusu
 */
final class JdbcRoutingOutboxStore implements RoutingOutboxStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcRoutingOutboxStore.class);
    private static final String OUTBOX_TABLE = "cf_routing_outbox";
    private final DataSource dataSource;
    private final JdbcDeployStore.Dialect dialect;

    JdbcRoutingOutboxStore(DataSource dataSource, JdbcDeployStore.Dialect dialect) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
    }

    private static RoutingOutboxRecord withLease(RoutingOutboxRecord record, String leaseToken, long leaseUntil,
            long updatedAt) {
        return new RoutingOutboxRecord(record.getId(), record.getEventType(), record.getNamespace(), record.getCode(),
                record.getAlias(), record.getRoutingKey(), record.getPayload(), RoutingOutboxRecord.Status.PROCESSING,
                record.getAttemptCount(), null, record.getLastError(), leaseToken, leaseUntil, record.getCreatedAt(),
                updatedAt);
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private long currentTimeMillis(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(dialect.currentTimestampSql());
                ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("Database clock query returned no row");
            }
            return result.getTimestamp(1).getTime();
        }
    }

    private static long addSaturated(long value, long increment) {
        return increment > Long.MAX_VALUE - value ? Long.MAX_VALUE : value + increment;
    }

    private static long subtractFloorZero(long value, long decrement) {
        return decrement >= value ? 0L : value - decrement;
    }

    private boolean reactivateDelivered(Connection connection, String deliveryKey) throws SQLException {
        long now = currentTimeMillis(connection);
        final String sql = "UPDATE " + OUTBOX_TABLE
                + " SET status = 'PENDING', attempt_count = 0, next_attempt_at = NULL, last_error = NULL, lease_token = NULL, lease_until = NULL, updated_at = ? WHERE delivery_key = ? AND status = 'DELIVERED'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, now);
            statement.setString(2, deliveryKey);
            return statement.executeUpdate() == 1;
        }
    }

    private static String deliveryKey(String eventType, String namespace, String code, String alias, String routingKey,
            String payload) {
        return DeploymentPersistentDigests.routingOutbox(eventType, namespace, code, alias, routingKey, payload);
    }

    private boolean isUniqueConstraintViolation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlFailure && dialect.isUniqueConstraintViolation(sqlFailure)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static DeploymentException outboxError(String message, Throwable cause) {
        return DeploymentException.of(DeploymentErrorCode.STORAGE_ERROR, message, cause);
    }

    long insertPending(Connection connection, String eventType, String namespace, String code, String alias,
            String routingKey, String payload) {
        Objects.requireNonNull(connection, "connection");
        if (!RoutingOutboxRecord.ALIAS_STATE_EVENT_TYPE.equals(eventType)) {
            throw new IllegalArgumentException("Unsupported routing outbox event type: " + eventType);
        }
        ProcessRef.Alias ref = ProcessRef.alias(namespace, code, alias);
        String validatedRoutingKey = ProcessIdentifiers.requireExactIdentity(routingKey, "routingKey", 512);
        String validatedPayload = Objects.requireNonNull(payload, "payload");
        if (validatedPayload.isBlank()) {
            throw new IllegalArgumentException("payload must not be blank");
        }

        long now;
        try {
            now = currentTimeMillis(connection);
        } catch (SQLException failure) {
            throw outboxError("Failed to read database clock for routing outbox insert", failure);
        }
        final String sql = "INSERT INTO " + OUTBOX_TABLE
                + " (delivery_key, event_type, namespace, code, alias, routing_key, payload, status, attempt_count, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1,
                    deliveryKey(eventType, ref.namespace(), ref.code(), ref.alias(), validatedRoutingKey,
                            validatedPayload));
            ps.setString(2, eventType);
            ps.setString(3, ref.namespace());
            ps.setString(4, ref.code());
            ps.setString(5, ref.alias());
            ps.setString(6, validatedRoutingKey);
            ps.setString(7, validatedPayload);
            ps.setLong(8, now);
            ps.setLong(9, now);
            ps.executeUpdate();
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
            }
            throw new IllegalStateException("No generated key returned for outbox insert: eventType=" + eventType);
        } catch (SQLException failure) {
            throw outboxError("Failed to insert routing outbox record", failure);
        }
    }

    @Override
    public long countByStatus(RoutingOutboxRecord.Status status) {
        Objects.requireNonNull(status, "status");
        final String sql = "SELECT COUNT(*) FROM " + OUTBOX_TABLE + " WHERE status = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException failure) {
            throw outboxError("Failed to count outbox records by status: " + status, failure);
        }
    }

    @Override
    public long countExpiredClaims() {
        final String sql = "SELECT COUNT(*) FROM " + OUTBOX_TABLE + " WHERE status = 'PROCESSING' AND lease_until < ?";
        try (Connection connection = dataSource.getConnection()) {
            long now = currentTimeMillis(connection);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, now);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getLong(1) : 0L;
                }
            }
        } catch (SQLException failure) {
            throw outboxError("Failed to count expired routing outbox claims", failure);
        }
    }

    @Override
    public int requeueFailed() {
        final String sql = "UPDATE " + OUTBOX_TABLE
                + " SET status = 'PENDING', attempt_count = 0, next_attempt_at = NULL, last_error = NULL, lease_token = NULL, lease_until = NULL, updated_at = ? WHERE status = 'FAILED'";
        try (Connection connection = dataSource.getConnection()) {
            long now = currentTimeMillis(connection);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, now);
                return ps.executeUpdate();
            }
        } catch (SQLException failure) {
            throw outboxError("Failed to re-queue dead-letter outbox records", failure);
        }
    }

    @Override
    public int deleteDeliveredOlderThan(long retentionMs, int limit) {
        if (retentionMs < 0) {
            throw new IllegalArgumentException("retentionMs must not be negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        try (Connection connection = dataSource.getConnection()) {
            long cutoffMs = subtractFloorZero(currentTimeMillis(connection), retentionMs);
            try (PreparedStatement ps = connection.prepareStatement(dialect.deleteDeliveredOlderThanSql())) {
                ps.setLong(1, cutoffMs);
                ps.setInt(2, limit);
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    LOGGER.info("Cleaned up {} DELIVERED outbox records older than {}ms", deleted, retentionMs);
                }
                return deleted;
            }
        } catch (SQLException failure) {
            throw outboxError("Failed to delete delivered outbox records with retention " + retentionMs + "ms", failure);
        }
    }

    @Override
    public List<RoutingOutboxRecord> claimPending(int limit, String claimantId, long leaseDurationMs) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (claimantId == null || claimantId.isBlank()) {
            throw new IllegalArgumentException("claimantId must not be blank");
        }
        if (leaseDurationMs <= 0) {
            throw new IllegalArgumentException("leaseDurationMs must be positive");
        }
        final String selectSql = "SELECT id, event_type, namespace, code, alias, routing_key, payload, status, "
                + "attempt_count, next_attempt_at, last_error, lease_token, lease_until, created_at, updated_at FROM "
                + OUTBOX_TABLE + " WHERE (status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= ?)) "
                + "OR (status = 'PROCESSING' AND lease_until < ?) ORDER BY id ASC LIMIT ? FOR UPDATE SKIP LOCKED";
        final String claimSql = "UPDATE " + OUTBOX_TABLE
                + " SET status = 'PROCESSING', next_attempt_at = NULL, lease_token = ?, lease_until = ?, updated_at = ? "
                + "WHERE id = ? AND ((status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= ?)) "
                + "OR (status = 'PROCESSING' AND lease_until < ?))";
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                long now = currentTimeMillis(connection);
                long leaseUntil = addSaturated(now, leaseDurationMs);
                List<RoutingOutboxRecord> candidates = selectCandidates(connection, selectSql, now, limit);
                List<RoutingOutboxRecord> claimed = new ArrayList<>();
                for (RoutingOutboxRecord candidate : candidates) {
                    String leaseToken = claimantId + ":" + UUID.randomUUID();
                    try (PreparedStatement statement = connection.prepareStatement(claimSql)) {
                        statement.setString(1, leaseToken);
                        statement.setLong(2, leaseUntil);
                        statement.setLong(3, now);
                        statement.setLong(4, candidate.getId());
                        statement.setLong(5, now);
                        statement.setLong(6, now);
                        if (statement.executeUpdate() == 1) {
                            claimed.add(withLease(candidate, leaseToken, leaseUntil, now));
                        }
                    }
                }
                connection.commit();
                return claimed;
            } catch (Error failure) {
                rollback(connection, failure);
                throw failure;
            } catch (Exception failure) {
                rollback(connection, failure);
                if (failure instanceof SQLException sqlFailure) {
                    throw sqlFailure;
                }
                if (failure instanceof RuntimeException runtimeFailure) {
                    throw runtimeFailure;
                }
                throw new IllegalStateException("Unexpected checked exception", failure);
            } finally {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException cleanupFailure) {
                    LOGGER.debug("Failed to restore outbox JDBC auto-commit", cleanupFailure);
                }
            }
        } catch (SQLException failure) {
            throw outboxError("Failed to claim pending routing outbox records", failure);
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
    public int markDelivered(long id, String leaseToken) {
        Objects.requireNonNull(leaseToken, "leaseToken");
        final String sql = "UPDATE " + OUTBOX_TABLE
                + " SET status = 'DELIVERED', last_error = NULL, lease_token = NULL, lease_until = NULL, updated_at = ? WHERE id = ? AND status = 'PROCESSING' AND lease_token = ? AND lease_until >= ?";
        try (Connection connection = dataSource.getConnection()) {
            long now = currentTimeMillis(connection);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, now);
                ps.setLong(2, id);
                ps.setString(3, leaseToken);
                ps.setLong(4, now);
                return ps.executeUpdate();
            }
        } catch (SQLException failure) {
            throw outboxError("Failed to mark routing outbox record as delivered: id=" + id, failure);
        }
    }

    @Override
    public long appendPending(String eventType, String namespace, String code, String alias, String routingKey,
            String payload) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(routingKey, "routingKey");
        Objects.requireNonNull(payload, "payload");

        try (Connection connection = dataSource.getConnection()) {
            return insertPending(connection, eventType, namespace, code, alias, routingKey, payload);
        } catch (SQLException failure) {
            throw outboxError("Failed to acquire connection for appendPending", failure);
        }
    }

    @Override
    public boolean ensurePending(String eventType, String namespace, String code, String alias, String routingKey,
            String payload) {
        if (!RoutingOutboxRecord.ALIAS_STATE_EVENT_TYPE.equals(eventType)) {
            throw new IllegalArgumentException("Unsupported routing outbox event type: " + eventType);
        }
        ProcessRef.Alias ref = ProcessRef.alias(namespace, code, alias);
        String validatedRoutingKey = ProcessIdentifiers.requireExactIdentity(routingKey, "routingKey", 512);
        String validatedPayload = Objects.requireNonNull(payload, "payload");
        if (validatedPayload.isBlank()) {
            throw new IllegalArgumentException("payload must not be blank");
        }
        String deliveryKey =
                deliveryKey(eventType, ref.namespace(), ref.code(), ref.alias(), validatedRoutingKey, validatedPayload);

        try (Connection connection = dataSource.getConnection()) {
            try {
                insertPending(connection, eventType, ref.namespace(), ref.code(), ref.alias(), validatedRoutingKey,
                        validatedPayload);
                return true;
            } catch (DeploymentException failure) {
                if (!isUniqueConstraintViolation(failure)) {
                    throw failure;
                }
            }
            return reactivateDelivered(connection, deliveryKey);
        } catch (SQLException failure) {
            throw outboxError("Failed to ensure pending standalone outbox record", failure);
        }
    }

    @Override
    public int markFailed(long id, String leaseToken, String error, int maxAttempts, long retryDelayMs) {
        Objects.requireNonNull(leaseToken, "leaseToken");
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (retryDelayMs < 0) {
            throw new IllegalArgumentException("retryDelayMs must not be negative");
        }
        // MySQL evaluates assignments left to right, so both decisions must precede the increment.
        final String sql = "UPDATE " + OUTBOX_TABLE + " SET last_error = ?, "
                + "next_attempt_at = CASE WHEN attempt_count + 1 >= ? THEN NULL ELSE ? END, "
                + "status = CASE WHEN attempt_count + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END, "
                + "attempt_count = attempt_count + 1, " + "lease_token = NULL, lease_until = NULL, updated_at = ? "
                + "WHERE id = ? AND status = 'PROCESSING' AND lease_token = ? AND lease_until >= ?";
        try (Connection connection = dataSource.getConnection()) {
            long now = currentTimeMillis(connection);
            long nextAttemptAt = addSaturated(now, retryDelayMs);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, error);
                ps.setInt(2, maxAttempts);
                ps.setLong(3, nextAttemptAt);
                ps.setInt(4, maxAttempts);
                ps.setLong(5, now);
                ps.setLong(6, id);
                ps.setString(7, leaseToken);
                ps.setLong(8, now);
                return ps.executeUpdate();
            }
        } catch (SQLException failure) {
            throw outboxError("Failed to mark routing outbox record as failed: id=" + id, failure);
        }
    }

    private RoutingOutboxRecord mapRow(ResultSet rs) throws SQLException {
        Long nextAttemptAt = rs.getLong("next_attempt_at");
        if (rs.wasNull()) {
            nextAttemptAt = null;
        }
        return new RoutingOutboxRecord(rs.getLong("id"), rs.getString("event_type"), rs.getString("namespace"),
                rs.getString("code"), rs.getString("alias"), rs.getString("routing_key"), rs.getString("payload"),
                RoutingOutboxRecord.Status.valueOf(rs.getString("status")), rs.getInt("attempt_count"), nextAttemptAt,
                rs.getString("last_error"), rs.getString("lease_token"), nullableLong(rs, "lease_until"),
                rs.getLong("created_at"), rs.getLong("updated_at"));
    }

    private List<RoutingOutboxRecord> selectCandidates(Connection connection, String sql, long now, int limit)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, now);
            statement.setLong(2, now);
            statement.setInt(3, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<RoutingOutboxRecord> candidates = new ArrayList<>();
                while (result.next()) {
                    candidates.add(mapRow(result));
                }
                return candidates;
            }
        }
    }
}
