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

import com.alibaba.compileflow.deploy.api.command.AbortRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.PromoteRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.UpdateCanaryWeightCommand;
import com.alibaba.compileflow.deploy.api.rollout.ProcessRollout;
import com.alibaba.compileflow.deploy.api.rollout.RolloutEvent;
import com.alibaba.compileflow.deploy.spi.store.DeployStore;
import com.alibaba.compileflow.deploy.spi.store.ProcessAliasRecord;
import com.alibaba.compileflow.deploy.spi.store.ProcessKey;
import com.alibaba.compileflow.deploy.spi.store.ProcessVersionRecord;
import com.alibaba.compileflow.deploy.spi.store.PublishedVersionPageKey;
import com.alibaba.compileflow.deploy.spi.store.RolloutCreateRequest;
import com.alibaba.compileflow.deploy.spi.store.RolloutStoreQuery;
import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxRecord;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Shared JDBC implementation of the complete Deploy persistence authority.
 *
 * <p>All capabilities are composed from one {@link DataSource}. Rollout mutations use the same
 * provider-owned JDBC transaction for rollout state, Alias state, audit events, and routing outbox
 * append. Database providers select the small SQL dialect surface while this class owns the
 * common protocol state machine; no connection or SQL concept escapes through the Deploy SPI.
 *
 * @author yusu
 */
public abstract class JdbcDeployStore implements DeployStore {
    private final JdbcProcessVersionStore versions;
    private final JdbcProcessAliasStore aliases;
    private final JdbcRolloutStore rollouts;
    private final JdbcRoutingOutboxStore outbox;

    protected JdbcDeployStore(DataSource dataSource, String projectionKeyPrefix, Dialect dialect) {
        DataSource authority = Objects.requireNonNull(dataSource, "dataSource");
        Dialect databaseDialect = Objects.requireNonNull(dialect, "dialect");
        versions = new JdbcProcessVersionStore(authority, databaseDialect);
        aliases = new JdbcProcessAliasStore(authority);
        rollouts = new JdbcRolloutStore(authority, projectionKeyPrefix, databaseDialect);
        outbox = new JdbcRoutingOutboxStore(authority, databaseDialect);
    }

    protected static String deleteDeliveredOlderThanSql(Dialect dialect) {
        return Objects.requireNonNull(dialect, "dialect").deleteDeliveredOlderThanSql();
    }

    protected enum Dialect {
        POSTGRESQL("SELECT CURRENT_TIMESTAMP",
                """
            WITH candidates AS (
                SELECT id
                  FROM cf_routing_outbox
                 WHERE status = 'DELIVERED'
                   AND updated_at < ?
                 ORDER BY updated_at, id
                 FOR UPDATE SKIP LOCKED
                 LIMIT ?
            )
            DELETE FROM cf_routing_outbox o
             USING candidates c
             WHERE o.id = c.id
            """),
        MYSQL("SELECT CURRENT_TIMESTAMP(3)",
                """
            DELETE FROM cf_routing_outbox
             WHERE status = 'DELIVERED'
               AND updated_at < ?
             ORDER BY updated_at, id
             LIMIT ?
            """);
        private final String currentTimestampSql;
        private final String deleteDeliveredOlderThanSql;

        Dialect(String currentTimestampSql, String deleteDeliveredOlderThanSql) {
            this.currentTimestampSql = currentTimestampSql;
            this.deleteDeliveredOlderThanSql = deleteDeliveredOlderThanSql;
        }

        String currentTimestampSql() {
            return currentTimestampSql;
        }

        String deleteDeliveredOlderThanSql() {
            return deleteDeliveredOlderThanSql;
        }

        boolean isUniqueConstraintViolation(SQLException failure) {
            return switch (this) {
                case POSTGRESQL -> "23505".equals(failure.getSQLState());
                case MYSQL -> failure.getErrorCode() == 1062 || "23505".equals(failure.getSQLState());
            };
        }

        boolean isTransactionConflict(SQLException failure) {
            if ("40001".equals(failure.getSQLState())) {
                return true;
            }
            return switch (this) {
                case POSTGRESQL -> "40P01".equals(failure.getSQLState());
                case MYSQL -> failure.getErrorCode() == 1213;
            };
        }
    }

    @Override
    public long currentTimeMillis() {
        return versions.currentTimeMillis();
    }

    @Override
    public ProcessVersionRecord save(ProcessVersionRecord record) {
        return versions.save(record);
    }

    @Override
    public Optional<ProcessVersionRecord> find(String namespace, String code, String version) {
        return versions.find(namespace, code, version);
    }

    @Override
    public List<ProcessVersionRecord> list(String namespace, String code, String versionPrefix,
            PublishedVersionPageKey cursor, int limit) {
        return versions.list(namespace, code, versionPrefix, cursor, limit);
    }

    @Override
    public long countDistinctProcesses(String namespace) {
        return aliases.countDistinctProcesses(namespace);
    }

    @Override
    public Optional<ProcessAliasRecord> resolve(String namespace, String code, String alias) {
        return aliases.resolve(namespace, code, alias);
    }

    @Override
    public List<ProcessKey> listDistinctProcesses() {
        return aliases.listDistinctProcesses();
    }

    @Override
    public List<ProcessAliasRecord> listAll(String namespace, String code) {
        return aliases.listAll(namespace, code);
    }

    @Override
    public List<ProcessAliasRecord> list(String namespace, String code, String sortBy, boolean ascending, int limit,
            int offset) {
        return aliases.list(namespace, code, sortBy, ascending, limit, offset);
    }

    @Override
    public ProcessRollout create(RolloutCreateRequest request) {
        return rollouts.create(request);
    }

    @Override
    public Optional<ProcessRollout> find(String rolloutId) {
        return rollouts.find(rolloutId);
    }

    @Override
    public List<ProcessRollout> list(RolloutStoreQuery query) {
        return rollouts.list(query);
    }

    @Override
    public List<RolloutEvent> listEvents(String rolloutId) {
        return rollouts.listEvents(rolloutId);
    }

    @Override
    public ProcessRollout updateCanary(UpdateCanaryWeightCommand command) {
        return rollouts.updateCanary(command);
    }

    @Override
    public ProcessRollout promote(PromoteRolloutCommand command) {
        return rollouts.promote(command);
    }

    @Override
    public ProcessRollout abort(AbortRolloutCommand command) {
        return rollouts.abort(command);
    }

    @Override
    public List<RoutingOutboxRecord> claimPending(int limit, String claimantId, long leaseDurationMs) {
        return outbox.claimPending(limit, claimantId, leaseDurationMs);
    }

    @Override
    public long countByStatus(RoutingOutboxRecord.Status status) {
        return outbox.countByStatus(status);
    }

    @Override
    public long countExpiredClaims() {
        return outbox.countExpiredClaims();
    }

    @Override
    public int requeueFailed() {
        return outbox.requeueFailed();
    }

    @Override
    public int deleteDeliveredOlderThan(long retentionMs, int limit) {
        return outbox.deleteDeliveredOlderThan(retentionMs, limit);
    }

    @Override
    public int markDelivered(long id, String leaseToken) {
        return outbox.markDelivered(id, leaseToken);
    }

    @Override
    public long appendPending(String eventType, String namespace, String code, String alias, String routingKey,
            String payload) {
        return outbox.appendPending(eventType, namespace, code, alias, routingKey, payload);
    }

    @Override
    public boolean ensurePending(String eventType, String namespace, String code, String alias, String routingKey,
            String payload) {
        return outbox.ensurePending(eventType, namespace, code, alias, routingKey, payload);
    }

    @Override
    public int markFailed(long id, String leaseToken, String error, int maxAttempts, long retryDelayMs) {
        return outbox.markFailed(id, leaseToken, error, maxAttempts, retryDelayMs);
    }
}
