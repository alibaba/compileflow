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
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.rollout.ProcessRollout;
import com.alibaba.compileflow.deploy.api.rollout.RolloutConstraints;
import com.alibaba.compileflow.deploy.api.rollout.RolloutEvent;
import com.alibaba.compileflow.deploy.api.rollout.RolloutOperationKind;
import com.alibaba.compileflow.deploy.api.rollout.RolloutPhase;
import com.alibaba.compileflow.deploy.api.rollout.RolloutStrategy;
import com.alibaba.compileflow.deploy.protocol.RoutingStateCodec;
import com.alibaba.compileflow.deploy.protocol.RoutingStateKeys;
import com.alibaba.compileflow.deploy.spi.store.DeploymentPersistentDigests;
import com.alibaba.compileflow.deploy.spi.store.RolloutCreateRequest;
import com.alibaba.compileflow.deploy.spi.store.RolloutPageKey;
import com.alibaba.compileflow.deploy.spi.store.RolloutStore;
import com.alibaba.compileflow.deploy.spi.store.RolloutStoreQuery;
import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxRecord;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * JDBC rollout Store with a single transaction boundary for route changes,
 * rollout state, audit events, and routing outbox records.
 *
 * @author yusu
 */
final class JdbcRolloutStore implements RolloutStore {
    private static final String ROLLOUT_COLUMNS =
            "id, namespace, code, alias, baseline_version, target_version, base_alias_revision, alias_"
            + "revision, strategy, phase, canary_weight_bps, revision, idempotency_key, operation_kind, "
            + "targeting_policy, targeting_parameters, request_fingerprint, created_by, notes, created_at, updated_at,"
            + " completed_at";
    private final DataSource dataSource;
    private final JdbcRoutingOutboxStore outboxRepository;
    private final String keyPrefix;
    private final JdbcDeployStore.Dialect dialect;

    JdbcRolloutStore(DataSource dataSource, String keyPrefix, JdbcDeployStore.Dialect dialect) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.outboxRepository = new JdbcRoutingOutboxStore(dataSource, dialect);
        this.keyPrefix = keyPrefix;
    }

    private static long nextRevision(long current, String subject) {
        try {
            return Math.incrementExact(current);
        } catch (ArithmeticException exhausted) {
            throw DeploymentException.of(DeploymentErrorCode.ROLLOUT_CONFLICT, subject + " revision space is exhausted",
                    exhausted);
        }
    }

    private static DeploymentException jdbcError(String message, Throwable cause) {
        return DeploymentException.of(DeploymentErrorCode.STORAGE_ERROR, message, cause);
    }

    private static String containsLikePattern(String value) {
        return "%" + value.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    @Override
    public ProcessRollout create(RolloutCreateRequest createRequest) {
        Objects.requireNonNull(createRequest, "createRequest");
        NormalizedCreate request = normalize(createRequest);
        String fingerprint = fingerprint(request);

        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Optional<ProcessRollout> replay = findByIdempotencyKeyForUpdate(connection, request.namespace,
                        request.code, request.route, request.operationKind, request.idempotencyKey);
                if (replay.isPresent()) {
                    ProcessRollout existing = requireMatchingFingerprint(replay.get(), fingerprint);
                    connection.commit();
                    return existing;
                }

                requirePublishedVersion(connection, request.namespace, request.code, request.targetVersion);
                RouteSnapshot route =
                        findRouteForUpdate(connection, request.namespace, request.code, request.route).orElse(null);
                requireExpectedRouteRevision(request, route);
                rejectConcurrentCanary(connection, request.namespace, request.code, request.route);

                String baseline = stableVersion(route);
                if (request.targetVersion.equals(baseline)) {
                    throw rolloutConflict(request, "Route already points to the target version");
                }
                if (request.strategy == RolloutStrategy.CANARY) {
                    if (baseline == null) {
                        throw rolloutConflict(request, "Canary rollout requires an existing stable route");
                    }
                    if (baseline.equals(request.targetVersion)) {
                        throw rolloutConflict(request, "Canary target must differ from the stable route version");
                    }
                }

                long now = currentTimeMillis(connection);
                String rolloutId = UUID.randomUUID().toString();
                long routeRevision = nextRevision(request.expectedRouteRevision, "Alias");
                RolloutPhase phase =
                        request.strategy == RolloutStrategy.CANARY ? RolloutPhase.IN_PROGRESS : RolloutPhase.COMPLETED;
                Long completedAt = phase.isTerminal() ? Long.valueOf(now) : null;
                insertRollout(connection, rolloutId, request, baseline, routeRevision, phase, fingerprint, now,
                        completedAt);

                String stableVersion = request.strategy == RolloutStrategy.CANARY ? baseline : request.targetVersion;
                String candidateVersion = request.strategy == RolloutStrategy.CANARY ? request.targetVersion : null;
                Integer candidateWeightBps =
                        request.strategy == RolloutStrategy.CANARY ? Integer.valueOf(request.canaryWeightBps) : null;
                long committedRouteRevision = upsertRoute(connection, route, request.namespace, request.code,
                        request.route, stableVersion, candidateVersion, candidateWeightBps, request.targeting,
                        request.actor, now);
                if (committedRouteRevision != routeRevision) {
                    throw new IllegalStateException("Committed route revision does not match rollout state");
                }
                writeRouteOutbox(connection, request.namespace, request.code, request.route, stableVersion,
                        candidateVersion, candidateWeightBps, request.targeting, request.actor, now, routeRevision);

                insertEvent(connection, rolloutId, 1L,
                        phase == RolloutPhase.IN_PROGRESS ? "CANARY_STARTED" : "COMPLETED", null, phase, request.actor,
                        request.notes, now);

                ProcessRollout created = findById(connection, rolloutId)
                    .orElseThrow(() -> new IllegalStateException("Rollout disappeared after creation"));
                connection.commit();
                return created;
            } catch (Error failure) {
                rollback(connection, failure);
                throw failure;
            } catch (Exception failure) {
                rollback(connection, failure);
                Optional<ProcessRollout> replay = recoverIdempotentCreate(connection, request, fingerprint, failure);
                if (replay.isPresent()) {
                    return replay.get();
                }
                if (isUniqueConstraintViolation(failure) || isTransactionConflict(failure)) {
                    throw DeploymentException
                        .builder(DeploymentErrorCode.CONCURRENT_MODIFICATION,
                                "Route changed concurrently; read its latest revision and retry", failure)
                        .namespace(request.namespace)
                        .code(request.code)
                        .alias(request.route)
                        .build();
                }
                throw propagate("Failed to create rollout", failure);
            } finally {
                restoreAutoCommit(connection, autoCommit);
            }
        } catch (SQLException failure) {
            throw jdbcError("Failed to create rollout", failure);
        }
    }

    @Override
    public Optional<ProcessRollout> find(String rolloutId) {
        String id = requireText(rolloutId, "rolloutId");
        try (Connection connection = dataSource.getConnection()) {
            return findById(connection, id);
        } catch (SQLException failure) {
            throw jdbcError("Failed to read rollout", failure);
        }
    }

    @Override
    public List<ProcessRollout> list(RolloutStoreQuery query) {
        Objects.requireNonNull(query, "query");
        int limit = query.limit();
        RolloutPageKey cursor = query.cursor();
        QueryParts parts = queryParts(query);
        String pageSql = "SELECT " + ROLLOUT_COLUMNS + " FROM cf_rollout" + parts.where
                + (cursor == null
                ? ""
                : (parts.where.isEmpty() ? " WHERE " : " AND ") + "(created_at < ? OR (created_at = ? AND id < ?))")
                + " ORDER BY created_at DESC, id DESC LIMIT ?";
        try (Connection connection = dataSource.getConnection()) {
            List<ProcessRollout> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(pageSql)) {
                int index = bind(statement, parts.parameters);
                if (cursor != null) {
                    statement.setLong(index++, cursor.createdAt());
                    statement.setLong(index++, cursor.createdAt());
                    statement.setString(index++, cursor.rolloutId());
                }
                statement.setInt(index, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        records.add(mapRollout(rs));
                    }
                }
            }
            return List.copyOf(records);
        } catch (SQLException failure) {
            throw jdbcError("Failed to list rollouts", failure);
        }
    }

    @Override
    public List<RolloutEvent> listEvents(String rolloutId) {
        String id = requireText(rolloutId, "rolloutId");
        String sql =
                "SELECT id, rollout_id, sequence, event_type, from_phase, to_phase, actor, reason, created_"
                + "at FROM cf_rollout_event WHERE rollout_id = ? ORDER BY sequence";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            List<RolloutEvent> events = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String from = rs.getString("from_phase");
                    events.add(
                            new RolloutEvent(rs.getLong("id"), rs.getString("rollout_id"), rs.getLong("sequence"),
                                    rs.getString("event_type"), from == null ? null : RolloutPhase.valueOf(from),
                                    RolloutPhase.valueOf(rs.getString("to_phase")), rs.getString("actor"),
                                    rs.getString("reason"), Instant.ofEpochMilli(rs.getLong("created_at"))));
                }
            }
            return Collections.unmodifiableList(events);
        } catch (SQLException failure) {
            throw jdbcError("Failed to list rollout events", failure);
        }
    }

    @Override
    public ProcessRollout updateCanary(UpdateCanaryWeightCommand command) {
        Objects.requireNonNull(command, "command");
        int weightBps = requireCanaryWeightBps(command.getWeightBps());
        return mutateCanary(command.getRolloutId(), command.getExpectedRevision(), command.getActor(),
                "CANARY_WEIGHT_UPDATED", null, RolloutPhase.IN_PROGRESS, weightBps);
    }

    @Override
    public ProcessRollout promote(PromoteRolloutCommand command) {
        Objects.requireNonNull(command, "command");
        return mutateCanary(command.getRolloutId(), command.getExpectedRevision(), command.getActor(), "PROMOTED", null,
                RolloutPhase.COMPLETED, RolloutConstraints.FULL_WEIGHT_BPS);
    }

    @Override
    public ProcessRollout abort(AbortRolloutCommand command) {
        Objects.requireNonNull(command, "command");
        return mutateCanary(command.getRolloutId(), command.getExpectedRevision(), command.getActor(), "ABORTED",
                command.getReason(), RolloutPhase.ABORTED, 0);
    }

    private ProcessRollout mutateCanary(String rolloutId, long expectedRevision, String requestedActor, String eventType,
            String reason, RolloutPhase targetPhase, int weightBps) {
        String id = requireText(rolloutId, "rolloutId");
        String actor = requireText(requestedActor, "actor");
        if (expectedRevision <= 0) {
            throw DeploymentException.of(DeploymentErrorCode.INVALID_ARGUMENT,
                    "expectedRevision must be greater than zero");
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                ProcessRollout located = findById(connection, id)
                    .orElseThrow(() -> DeploymentException.of(DeploymentErrorCode.ROLLOUT_NOT_FOUND,
                            "Rollout not found: " + id));
                ProcessRef.Alias locatedAlias = located.getAlias();
                RouteSnapshot route = findRouteForUpdate(connection, locatedAlias.namespace(), locatedAlias.code(),
                        locatedAlias.alias())
                    .orElseThrow(() -> DeploymentException.of(DeploymentErrorCode.CONCURRENT_MODIFICATION,
                            "Rollout route no longer exists"));
                ProcessRollout current = findByIdForUpdate(connection, id)
                    .orElseThrow(() -> DeploymentException.of(DeploymentErrorCode.ROLLOUT_NOT_FOUND,
                            "Rollout not found: " + id));
                requireSameRolloutIdentity(located, current);
                requireMutableCanary(current, expectedRevision);
                requireOwnedRoute(current, route);

                long now = currentTimeMillis(connection);
                String stableVersion = targetPhase == RolloutPhase.COMPLETED
                        ? current.getTargetVersion().version()
                        : current.getBaselineVersion().version();
                String candidateVersion =
                        targetPhase == RolloutPhase.IN_PROGRESS ? current.getTargetVersion().version() : null;
                Integer candidateWeightBps = targetPhase == RolloutPhase.IN_PROGRESS ? Integer.valueOf(weightBps) : null;
                AliasTargeting targeting = targetPhase == RolloutPhase.IN_PROGRESS ? current.getTargeting() : null;
                ProcessRef.Alias currentAlias = current.getAlias();
                long routeRevision = upsertRoute(connection, route, currentAlias.namespace(), currentAlias.code(),
                        currentAlias.alias(), stableVersion, candidateVersion, candidateWeightBps, targeting, actor, now);
                writeRouteOutbox(connection, currentAlias.namespace(), currentAlias.code(), currentAlias.alias(),
                        stableVersion, candidateVersion, candidateWeightBps, targeting, actor, now, routeRevision);

                long nextRevision = nextRevision(current.getRolloutRevision(), "Rollout");
                Long completedAt = targetPhase.isTerminal() ? Long.valueOf(now) : null;
                transition(connection, current.getId(), current.getRolloutRevision(), current.getPhase(), targetPhase,
                        weightBps, routeRevision, now, completedAt);
                insertEvent(connection, current.getId(), nextRevision, eventType, current.getPhase(), targetPhase, actor,
                        reason, now);

                ProcessRollout updated =
                        findById(connection, id)
                    .orElseThrow(() -> new IllegalStateException("Rollout disappeared after mutation"));
                connection.commit();
                return updated;
            } catch (Error failure) {
                rollback(connection, failure);
                throw failure;
            } catch (Exception failure) {
                rollback(connection, failure);
                throw propagate("Failed to mutate rollout", failure);
            } finally {
                restoreAutoCommit(connection, autoCommit);
            }
        } catch (SQLException failure) {
            throw jdbcError("Failed to mutate rollout", failure);
        }
    }

    private void requirePublishedVersion(Connection connection, String namespace, String code, String version)
            throws SQLException {
        String sql = "SELECT 1 FROM cf_process_version WHERE namespace = ? AND code = ? AND version = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            statement.setString(2, code);
            statement.setString(3, version);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw DeploymentException
                        .builder(DeploymentErrorCode.VERSION_NOT_FOUND, "Target version not found: " + version)
                        .namespace(namespace)
                        .code(code)
                        .version(version)
                        .build();
                }
            }
        }
    }

    private void rejectConcurrentCanary(Connection connection, String namespace, String code, String route)
            throws SQLException {
        String sql = "SELECT id FROM cf_rollout WHERE namespace = ? AND code = ? AND alias = ? AND phase = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            statement.setString(2, code);
            statement.setString(3, route);
            statement.setString(4, RolloutPhase.IN_PROGRESS.name());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    throw DeploymentException
                        .builder(DeploymentErrorCode.ROLLOUT_CONFLICT,
                                "Route already has an active canary rollout: " + rs.getString(1))
                        .namespace(namespace)
                        .code(code)
                        .alias(route)
                        .build();
                }
            }
        }
    }

    private void insertRollout(Connection connection, String rolloutId, NormalizedCreate request, String baseline,
            long routeRevision, RolloutPhase phase, String fingerprint, long now, Long completedAt) throws SQLException {
        String sql =
                "INSERT INTO cf_rollout (id, namespace, code, alias, baseline_version, target_version, "
                + "base_alias_revision, alias_revision, strategy, phase, canary_weight_bps, targeting_policy, "
                + "targeting_parameters, revision, active_marker, idempotency_key, operation_kind, request_"
                + "fingerprint, created_by, notes, created_at, updated_at, completed_at) VALUES (?, ?, ?, ?, ?, ?, "
                + "?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rolloutId);
            statement.setString(2, request.namespace);
            statement.setString(3, request.code);
            statement.setString(4, request.route);
            statement.setString(5, baseline);
            statement.setString(6, request.targetVersion);
            statement.setLong(7, request.expectedRouteRevision);
            statement.setLong(8, routeRevision);
            statement.setString(9, request.strategy.name());
            statement.setString(10, phase.name());
            statement.setInt(11,
                    request.strategy == RolloutStrategy.CANARY ? request.canaryWeightBps : RolloutConstraints.FULL_WEIGHT_BPS);
            statement.setString(12, request.targeting == null ? null : request.targeting.policy());
            statement.setString(13, encodeTargetingParameters(request.targeting));
            if (phase == RolloutPhase.IN_PROGRESS) {
                statement.setInt(14, 1);
            } else {
                statement.setNull(14, Types.INTEGER);
            }
            statement.setString(15, request.idempotencyKey);
            statement.setString(16, request.operationKind.name());
            statement.setString(17, fingerprint);
            statement.setString(18, request.actor);
            statement.setString(19, request.notes);
            statement.setLong(20, now);
            statement.setLong(21, now);
            if (completedAt == null) {
                statement.setNull(22, Types.BIGINT);
            } else {
                statement.setLong(22, completedAt.longValue());
            }
            statement.executeUpdate();
        }
    }

    private void transition(Connection connection, String rolloutId, long expectedRevision, RolloutPhase expectedPhase,
            RolloutPhase targetPhase, int weightBps, long routeRevision, long now, Long completedAt) throws SQLException {
        String sql =
                "UPDATE cf_rollout SET phase = ?, canary_weight_bps = ?, revision = ?, alias_revision = ?, "
                + "active_marker = ?, updated_at = ?, completed_at = ? WHERE id = ? AND revision = ? AND " + "phase = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetPhase.name());
            statement.setInt(2, weightBps);
            statement.setLong(3, nextRevision(expectedRevision, "Rollout"));
            statement.setLong(4, routeRevision);
            if (targetPhase == RolloutPhase.IN_PROGRESS) {
                statement.setInt(5, 1);
            } else {
                statement.setNull(5, Types.INTEGER);
            }
            statement.setLong(6, now);
            if (completedAt == null) {
                statement.setNull(7, Types.BIGINT);
            } else {
                statement.setLong(7, completedAt.longValue());
            }
            statement.setString(8, rolloutId);
            statement.setLong(9, expectedRevision);
            statement.setString(10, expectedPhase.name());
            if (statement.executeUpdate() != 1) {
                throw DeploymentException.of(DeploymentErrorCode.CONCURRENT_MODIFICATION,
                        "Rollout changed concurrently: " + rolloutId);
            }
        }
    }

    private void insertEvent(Connection connection, String rolloutId, long sequence, String type, RolloutPhase from,
            RolloutPhase to, String actor, String reason, long now) throws SQLException {
        String sql =
                "INSERT INTO cf_rollout_event (rollout_id, sequence, event_type, from_phase, to_phase, "
                + "actor, reason, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rolloutId);
            statement.setLong(2, sequence);
            statement.setString(3, type);
            statement.setString(4, from == null ? null : from.name());
            statement.setString(5, to.name());
            statement.setString(6, actor);
            statement.setString(7, reason);
            statement.setLong(8, now);
            statement.executeUpdate();
        }
    }

    private Optional<RouteSnapshot> findRouteForUpdate(Connection connection, String namespace, String code,
            String route) throws SQLException {
        String sql =
                "SELECT stable_version, candidate_version, candidate_weight_bps, targeting_policy, "
                + "targeting_parameters, revision FROM cf_process_alias WHERE namespace = ? AND code = ? AND alias = ?"
                + " FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            statement.setString(2, code);
            statement.setString(3, route);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                int weightBps = rs.getInt("candidate_weight_bps");
                Integer candidateWeightBps = rs.wasNull() ? null : Integer.valueOf(weightBps);
                return Optional.of(
                        new RouteSnapshot(rs.getString("stable_version"), rs.getString("candidate_version"),
                                candidateWeightBps,
                                decodeTargeting(rs.getString("targeting_policy"), rs.getString("targeting_parameters")),
                                rs.getLong("revision")));
            }
        }
    }

    private long upsertRoute(Connection connection, RouteSnapshot existing, String namespace, String code, String route,
            String stableVersion, String candidateVersion, Integer candidateWeightBps, AliasTargeting targeting,
            String actor, long now) throws SQLException {
        if (existing == null) {
            String sql =
                    "INSERT INTO cf_process_alias (namespace, code, alias, stable_version, candidate_version, "
                    + "candidate_weight_bps, targeting_policy, targeting_parameters, revision, created_by, updated_by,"
                    + " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, namespace);
                statement.setString(2, code);
                statement.setString(3, route);
                statement.setString(4, stableVersion);
                statement.setString(5, candidateVersion);
                if (candidateWeightBps == null) {
                    statement.setNull(6, Types.INTEGER);
                } else {
                    statement.setInt(6, candidateWeightBps.intValue());
                }
                statement.setString(7, targeting == null ? null : targeting.policy());
                statement.setString(8, encodeTargetingParameters(targeting));
                statement.setString(9, actor);
                statement.setString(10, actor);
                statement.setLong(11, now);
                statement.setLong(12, now);
                statement.executeUpdate();
            }
            return 1L;
        }
        long nextRevision = nextRevision(existing.revision, "Alias");
        String sql =
                "UPDATE cf_process_alias SET stable_version = ?, candidate_version = ?, candidate_weight_"
                + "bps = ?, targeting_policy = ?, targeting_parameters = ?, revision = ?, updated_by = ?, updated_at ="
                + " ? WHERE namespace = ? AND code = ? AND alias = ? AND revision = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, stableVersion);
            statement.setString(2, candidateVersion);
            if (candidateWeightBps == null) {
                statement.setNull(3, Types.INTEGER);
            } else {
                statement.setInt(3, candidateWeightBps.intValue());
            }
            statement.setString(4, targeting == null ? null : targeting.policy());
            statement.setString(5, encodeTargetingParameters(targeting));
            statement.setLong(6, nextRevision);
            statement.setString(7, actor);
            statement.setLong(8, now);
            statement.setString(9, namespace);
            statement.setString(10, code);
            statement.setString(11, route);
            statement.setLong(12, existing.revision);
            if (statement.executeUpdate() != 1) {
                throw DeploymentException.of(DeploymentErrorCode.CONCURRENT_MODIFICATION,
                        "Route changed concurrently: " + route);
            }
        }
        return nextRevision;
    }

    private void writeRouteOutbox(Connection connection, String namespace, String code, String route,
            String stableVersion, String candidateVersion, Integer candidateWeightBps, AliasTargeting targeting,
            String actor, long now, long routeRevision) {
        String routingKey = RoutingStateKeys.aliasState(keyPrefix, namespace, code, route);
        outboxRepository.insertPending(connection, RoutingOutboxRecord.ALIAS_STATE_EVENT_TYPE, namespace, code, route,
                routingKey,
                aliasPayload(namespace, code, route, stableVersion, candidateVersion, candidateWeightBps, targeting,
                        actor, now, routeRevision));
    }

    private String aliasPayload(String namespace, String code, String route, String stableVersion,
            String candidateVersion, Integer candidateWeightBps, AliasTargeting targeting, String actor, long now,
            long revision) {
        try {
            return RoutingStateCodec.aliasStateJson(namespace, code, route, stableVersion, candidateVersion,
                    candidateWeightBps, targeting, revision, actor, now);
        } catch (Exception failure) {
            throw jdbcError("Failed to encode route outbox payload", failure);
        }
    }

    private Optional<ProcessRollout> findById(Connection connection, String rolloutId) throws SQLException {
        return queryById(connection, rolloutId, false);
    }

    private Optional<ProcessRollout> findByIdForUpdate(Connection connection, String rolloutId) throws SQLException {
        return queryById(connection, rolloutId, true);
    }

    private Optional<ProcessRollout> queryById(Connection connection, String rolloutId, boolean forUpdate)
            throws SQLException {
        String sql = "SELECT " + ROLLOUT_COLUMNS + " FROM cf_rollout WHERE id = ?" + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rolloutId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRollout(rs)) : Optional.empty();
            }
        }
    }

    private Optional<ProcessRollout> findByIdempotencyKey(Connection connection, String namespace, String code,
            String route, RolloutOperationKind operationKind, String key) throws SQLException {
        return queryByIdempotencyKey(connection, namespace, code, route, operationKind, key, false);
    }

    private Optional<ProcessRollout> findByIdempotencyKeyForUpdate(Connection connection, String namespace, String code,
            String route, RolloutOperationKind operationKind, String key) throws SQLException {
        return queryByIdempotencyKey(connection, namespace, code, route, operationKind, key, true);
    }

    private Optional<ProcessRollout> queryByIdempotencyKey(Connection connection, String namespace, String code,
            String route, RolloutOperationKind operationKind, String key, boolean forUpdate) throws SQLException {
        String sql = "SELECT " + ROLLOUT_COLUMNS + " FROM cf_rollout WHERE namespace = ? AND code = ? AND alias = ? "
                + "AND operation_kind = ? AND idempotency_key = ?" + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            statement.setString(2, code);
            statement.setString(3, route);
            statement.setString(4, operationKind.name());
            statement.setString(5, key);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRollout(rs)) : Optional.empty();
            }
        }
    }

    private ProcessRollout mapRollout(ResultSet rs) throws SQLException {
        long completed = rs.getLong("completed_at");
        Long completedAt = rs.wasNull() ? null : Long.valueOf(completed);
        ProcessRef.Alias alias =
                ProcessRef.alias(rs.getString("namespace"), rs.getString("code"), rs.getString("alias"));
        ProcessRollout.Builder builder = ProcessRollout
            .builder()
            .id(rs.getString("id"))
            .alias(alias)
            .targetVersion(ProcessRef.version(alias.namespace(), alias.code(), rs.getString("target_version")))
            .baseAliasRevision(rs.getLong("base_alias_revision"))
            .aliasRevision(rs.getLong("alias_revision"))
            .strategy(RolloutStrategy.valueOf(rs.getString("strategy")))
            .phase(RolloutPhase.valueOf(rs.getString("phase")))
            .targetWeightBps(rs.getInt("canary_weight_bps"))
            .rolloutRevision(rs.getLong("revision"))
            .idempotencyKey(rs.getString("idempotency_key"))
            .operationKind(RolloutOperationKind.valueOf(rs.getString("operation_kind")))
            .targeting(decodeTargeting(rs.getString("targeting_policy"), rs.getString("targeting_parameters")))
            .requestFingerprint(rs.getString("request_fingerprint"))
            .createdBy(rs.getString("created_by"))
            .notes(rs.getString("notes"))
            .createdAt(Instant.ofEpochMilli(rs.getLong("created_at")))
            .updatedAt(Instant.ofEpochMilli(rs.getLong("updated_at")));
        String baselineVersion = rs.getString("baseline_version");
        if (baselineVersion != null) {
            builder.baselineVersion(ProcessRef.version(alias.namespace(), alias.code(), baselineVersion));
        }
        if (completedAt != null) {
            builder.completedAt(Instant.ofEpochMilli(completedAt.longValue()));
        }
        return builder.build();
    }

    private void requireMutableCanary(ProcessRollout rollout, long expectedRevision) {
        if (rollout.getRolloutRevision() != expectedRevision) {
            throw DeploymentException.of(DeploymentErrorCode.CONCURRENT_MODIFICATION,
                    "Rollout revision mismatch: expected " + expectedRevision + " but was " + rollout.getRolloutRevision());
        }
        if (rollout.getStrategy() != RolloutStrategy.CANARY || rollout.getPhase() != RolloutPhase.IN_PROGRESS) {
            throw DeploymentException.of(DeploymentErrorCode.ROLLOUT_CONFLICT,
                    "Rollout is not an active canary: " + rollout.getId());
        }
        if (rollout.getBaselineVersion() == null) {
            throw DeploymentException.of(DeploymentErrorCode.ROLLOUT_CONFLICT,
                    "Canary rollout has no captured baseline: " + rollout.getId());
        }
    }

    private void requireSameRolloutIdentity(ProcessRollout located, ProcessRollout locked) {
        if (!located.getAlias().equals(locked.getAlias())) {
            throw DeploymentException.of(DeploymentErrorCode.CONCURRENT_MODIFICATION,
                    "Rollout identity changed concurrently: " + locked.getId());
        }
    }

    private void requireOwnedRoute(ProcessRollout rollout, RouteSnapshot route) {
        if (route.revision != rollout.getAliasRevision()
                || !Objects.equals(route.stableVersion, rollout.getBaselineVersion().version())
                || !Objects.equals(route.candidateVersion, rollout.getTargetVersion().version())
                || !Objects.equals(route.candidateWeightBps, Integer.valueOf(rollout.getTargetWeightBps()))
                || !Objects.equals(route.targeting, rollout.getTargeting())) {
            throw DeploymentException.of(DeploymentErrorCode.CONCURRENT_MODIFICATION,
                    "Route changed outside this rollout; refusing to overwrite newer intent");
        }
    }

    private String stableVersion(RouteSnapshot route) {
        if (route == null) {
            return null;
        }
        if (route.candidateVersion != null) {
            throw DeploymentException.of(DeploymentErrorCode.ROLLOUT_CONFLICT,
                    "Route already contains a candidate version; abort or promote its rollout first");
        }
        return route.stableVersion;
    }

    private void requireExpectedRouteRevision(NormalizedCreate request, RouteSnapshot route) {
        long actualRevision = route == null ? 0L : route.revision;
        if (request.expectedRouteRevision != actualRevision) {
            throw DeploymentException
                .builder(DeploymentErrorCode.CONCURRENT_MODIFICATION,
                        "Route revision mismatch: expected " + request.expectedRouteRevision + " but was " + actualRevision)
                .namespace(request.namespace)
                .code(request.code)
                .alias(request.route)
                .build();
        }
    }

    private QueryParts queryParts(RolloutStoreQuery query) {
        List<String> clauses = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        String namespace = query.namespace();
        if (namespace != null) {
            clauses.add("namespace = ?");
            parameters.add(namespace);
        }
        String code = query.code();
        if (code != null) {
            clauses.add("code = ?");
            parameters.add(code);
        }
        String keyword = query.keyword();
        if (keyword != null) {
            String pattern = containsLikePattern(keyword);
            clauses.add("(LOWER(code) LIKE ? ESCAPE '!' OR LOWER(id) LIKE ? ESCAPE '!')");
            parameters.add(pattern);
            parameters.add(pattern);
        }
        String route = query.alias();
        if (route != null) {
            clauses.add("alias = ?");
            parameters.add(route);
        }
        if (query.phase() != null) {
            clauses.add("phase = ?");
            parameters.add(query.phase().name());
        }
        return new QueryParts(clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses), parameters);
    }

    private int bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        int index = 1;
        for (Object parameter : parameters) {
            statement.setObject(index++, parameter);
        }
        return index;
    }

    private NormalizedCreate normalize(RolloutCreateRequest createRequest) {
        String idempotencyKey = createRequest.getIdempotencyKey();
        ProcessRef.Alias aliasRef =
                ProcessRef.alias(createRequest.getNamespace(), createRequest.getCode(), createRequest.getAlias());
        ProcessRef.Version versionRef =
                ProcessRef.version(aliasRef.namespace(), aliasRef.code(), createRequest.getTargetVersion());
        long expectedRouteRevision = createRequest.getExpectedAliasRevision();
        if (expectedRouteRevision < 0) {
            throw DeploymentException.of(DeploymentErrorCode.INVALID_ARGUMENT,
                    "expectedRouteRevision must be zero or greater");
        }
        String actor = createRequest.getActor();
        String notes = createRequest.getNotes();
        int canaryWeightBps = createRequest.getStrategy() == RolloutStrategy.CANARY
                ? requireCanaryWeightBps(createRequest.getCanaryWeightBps())
                : RolloutConstraints.FULL_WEIGHT_BPS;
        return new NormalizedCreate(idempotencyKey,
                Objects.requireNonNull(createRequest.getOperationKind(), "operationKind"), aliasRef.namespace(),
                aliasRef.code(), aliasRef.alias(), versionRef.version(), expectedRouteRevision,
                createRequest.getStrategy(), canaryWeightBps, createRequest.getTargeting(), actor, notes);
    }

    private int requireCanaryWeightBps(Integer weightBps) {
        if (weightBps == null) {
            throw DeploymentException.of(DeploymentErrorCode.INVALID_ARGUMENT, "canaryWeightBps is required");
        }
        try {
            return RolloutConstraints.requireActiveWeightBps(weightBps.intValue(), "canaryWeightBps");
        } catch (IllegalArgumentException invalid) {
            throw DeploymentException.of(DeploymentErrorCode.INVALID_ARGUMENT, invalid.getMessage(), invalid);
        }
    }

    private String fingerprint(NormalizedCreate request) {
        return DeploymentPersistentDigests.rolloutRequest(request.operationKind.name(), request.namespace, request.code,
                request.route, request.targetVersion, Long.toString(request.expectedRouteRevision),
                request.strategy.name(), Integer.toString(request.canaryWeightBps), request.actor,
                request.notes == null ? "" : request.notes, request.targeting == null ? "" : request.targeting.policy(),
                encodeTargetingParameters(request.targeting));
    }

    private String encodeTargetingParameters(AliasTargeting targeting) {
        Map<String, String> parameters = targeting == null ? Map.of() : new TreeMap<>(targeting.parameters());
        try {
            return JdbcDeployJson.write(parameters);
        } catch (RuntimeException failure) {
            throw jdbcError("Failed to encode Alias targeting parameters", failure);
        }
    }

    private AliasTargeting decodeTargeting(String policy, String parametersJson) throws SQLException {
        try {
            Map<String, String> parameters = JdbcDeployJson.readStringMap(parametersJson);
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

    private ProcessRollout requireMatchingFingerprint(ProcessRollout existing, String fingerprint) {
        if (!existing.getRequestFingerprint().equals(fingerprint)) {
            throw DeploymentException.of(DeploymentErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency key was already used for a different rollout request");
        }
        return existing;
    }

    private Optional<ProcessRollout> recoverIdempotentCreate(Connection connection, NormalizedCreate request,
            String fingerprint, Exception failure) {
        try {
            return findByIdempotencyKey(connection, request.namespace, request.code, request.route,
                    request.operationKind, request.idempotencyKey)
                .map(existing -> requireMatchingFingerprint(existing, fingerprint));
        } catch (SQLException recoveryFailure) {
            failure.addSuppressed(recoveryFailure);
            return Optional.empty();
        }
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

    private DeploymentException rolloutConflict(NormalizedCreate request, String message) {
        return DeploymentException
            .builder(DeploymentErrorCode.ROLLOUT_CONFLICT, message)
            .namespace(request.namespace)
            .code(request.code)
            .alias(request.route)
            .version(request.targetVersion)
            .build();
    }

    private boolean isTransactionConflict(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlFailure && dialect.isTransactionConflict(sqlFailure)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String requireText(String value, String name) {
        if (value == null) {
            throw DeploymentException.of(DeploymentErrorCode.INVALID_ARGUMENT, name + " is required");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw DeploymentException.of(DeploymentErrorCode.INVALID_ARGUMENT, name + " is required");
        }
        return normalized;
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

    private void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private void restoreAutoCommit(Connection connection, boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException ignored) {
            // The connection is about to close; the original operation result is authoritative.
        }
    }

    private RuntimeException propagate(String message, Exception failure) {
        if (isTransactionConflict(failure)) {
            return DeploymentException.of(DeploymentErrorCode.CONCURRENT_MODIFICATION,
                    "Rollout transaction conflicted; read the latest state and retry", failure);
        }
        if (failure instanceof RuntimeException runtimeFailure) {
            return runtimeFailure;
        }
        return jdbcError(message, failure);
    }

    private static final class RouteSnapshot {
        private final String stableVersion;
        private final String candidateVersion;
        private final Integer candidateWeightBps;
        private final AliasTargeting targeting;
        private final long revision;

        private RouteSnapshot(String stableVersion, String candidateVersion, Integer candidateWeightBps,
                AliasTargeting targeting, long revision) {
            this.stableVersion = stableVersion;
            this.candidateVersion = candidateVersion;
            this.candidateWeightBps = candidateWeightBps;
            this.targeting = targeting;
            this.revision = revision;
        }
    }

    private static final class QueryParts {
        private final String where;
        private final List<Object> parameters;

        private QueryParts(String where, List<Object> parameters) {
            this.where = where;
            this.parameters = parameters;
        }
    }

    private static final class NormalizedCreate {
        private final String idempotencyKey;
        private final RolloutOperationKind operationKind;
        private final String namespace;
        private final String code;
        private final String route;
        private final String targetVersion;
        private final long expectedRouteRevision;
        private final RolloutStrategy strategy;
        private final int canaryWeightBps;
        private final AliasTargeting targeting;
        private final String actor;
        private final String notes;

        private NormalizedCreate(String idempotencyKey, RolloutOperationKind operationKind, String namespace,
                String code, String route, String targetVersion, long expectedRouteRevision, RolloutStrategy strategy,
                int canaryWeightBps, AliasTargeting targeting, String actor, String notes) {
            this.idempotencyKey = idempotencyKey;
            this.operationKind = operationKind;
            this.namespace = namespace;
            this.code = code;
            this.route = route;
            this.targetVersion = targetVersion;
            this.expectedRouteRevision = expectedRouteRevision;
            this.strategy = strategy;
            this.canaryWeightBps = canaryWeightBps;
            this.targeting = targeting;
            this.actor = actor;
            this.notes = notes;
        }
    }
}
