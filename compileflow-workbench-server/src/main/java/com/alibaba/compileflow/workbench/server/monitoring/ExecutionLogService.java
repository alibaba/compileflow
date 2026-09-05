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
package com.alibaba.compileflow.workbench.server.monitoring;

import com.alibaba.compileflow.engine.ProcessText;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists and queries durable process execution logs.
 *
 * @author yusu
 */
@Service
public class ExecutionLogService {
    private static final int MAX_SEARCH_PAGE_SIZE = 500;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 4096;
    private static final char LIKE_ESCAPE = '!';
    private static final Sort LOG_ORDER = Sort.by(Sort.Order.desc("loggedAt"), Sort.Order.asc("id"));
    private final ExecutionLogRepository repository;
    private final int maxQueryRows;
    private final int purgeBatchSize;

    public ExecutionLogService(ExecutionLogRepository repository, CompileFlowWorkbenchServerProperties properties) {
        this.repository = repository;
        this.maxQueryRows = properties.getExecutionLog().getMaxQueryRows();
        this.purgeBatchSize = properties.getExecutionLog().getPurgeBatchSize();
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }

    static String buildLogId(ExecutionLogRecord entry) {
        return "log-" + entry.loggedAt() + "-" + UUID.randomUUID();
    }

    private static Specification<ExecutionLogEntity> toSpecification(LogQuery query) {
        Objects.requireNonNull(query, "query");
        return (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            addEquals(predicates, builder, root.get("processCode"), query.processCode());
            if (hasText(query.status()) && !"all".equals(query.status())) {
                addEquals(predicates, builder, root.get("status"), query.status());
            }
            addEquals(predicates, builder, root.get("invocationId"), query.invocationId());
            addEquals(predicates, builder, root.get("parentInvocationId"), query.parentInvocationId());
            addEquals(predicates, builder, root.get("traceId"), query.traceId());
            if (query.callDepth() != null) {
                predicates.add(builder.equal(root.get("callDepth"), query.callDepth()));
            }
            addEquals(predicates, builder, root.get("namespace"), query.namespace());
            addEquals(predicates, builder, root.get("requestedVersion"), query.requestedVersion());
            addEquals(predicates, builder, root.get("effectiveVersion"), query.effectiveVersion());
            addEquals(predicates, builder, root.get("routingSource"), query.routingSource());
            addEquals(predicates, builder, root.get("routeAlias"), query.routeAlias());
            if (query.routeRevision() != null) {
                predicates.add(builder.equal(root.get("routeRevision"), query.routeRevision()));
            }
            if (query.startMs() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("loggedAt"), query.startMs()));
            }
            if (query.endMs() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("loggedAt"), query.endMs()));
            }
            if (hasText(query.keyword())) {
                String pattern = "%" + escapeLike(query.keyword().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(builder.or(like(builder, root.get("processCode"), pattern),
                        like(builder, root.get("status"), pattern), like(builder, root.get("errorCode"), pattern),
                        like(builder, root.get("errorMessage"), pattern),
                        like(builder, root.get("invocationId"), pattern),
                        like(builder, root.get("parentInvocationId"), pattern),
                        like(builder, root.get("traceId"), pattern), like(builder, root.get("namespace"), pattern),
                        like(builder, root.get("requestedVersion"), pattern),
                        like(builder, root.get("effectiveVersion"), pattern),
                        like(builder, root.get("routingSource"), pattern),
                        like(builder, root.get("routeAlias"), pattern)));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate like(jakarta.persistence.criteria.CriteriaBuilder builder, Expression<String> expression,
            String pattern) {
        return builder.like(builder.lower(expression), pattern, LIKE_ESCAPE);
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static String truncate(String value, int maxLength) {
        return value == null ? null : ProcessText.truncateCodePoints(value, maxLength);
    }

    private static void addEquals(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder builder,
            Expression<String> expression, String value) {
        if (hasText(value)) {
            predicates.add(builder.equal(expression, value.trim()));
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Transactional
    public void persist(ExecutionLogRecord entry) {
        ExecutionLogEntity entity = new ExecutionLogEntity();
        entity.setId(buildLogId(entry));
        entity.setProcessCode(entry.processCode());
        entity.setStatus(entry.status());
        entity.setDurationMs(entry.durationMs());
        entity.setErrorCode(entry.errorCode());
        entity.setErrorMessage(truncate(entry.errorMessage(), MAX_ERROR_MESSAGE_LENGTH));
        entity.setInvocationId(entry.invocationId());
        entity.setParentInvocationId(entry.parentInvocationId());
        entity.setCallDepth(entry.callDepth());
        entity.setTraceId(entry.traceId());
        entity.setNamespace(entry.namespace());
        entity.setModelType(entry.modelType());
        entity.setSourceDigest(entry.sourceDigest());
        entity.setRequestedVersion(entry.requestedVersion());
        entity.setEffectiveVersion(entry.effectiveVersion());
        entity.setRoutingSource(entry.routingSource());
        entity.setRouteAlias(entry.routeAlias());
        entity.setRouteRevision(entry.routeRevision());
        entity.setLoggedAt(entry.loggedAt());
        repository.save(entity);
    }

    @Transactional
    public PurgeResult purgeOlderThan(long cutoffMs) {
        List<String> ids = repository.findIdsOlderThan(cutoffMs, PageRequest.of(0, purgeBatchSize));
        int deletedCount = ids.isEmpty() ? 0 : repository.deleteIds(ids);
        return new PurgeResult(deletedCount, repository.existsByLoggedAtLessThan(cutoffMs));
    }

    public List<ExecutionLogEntity> findForRouteSince(String namespace, String processCode, String routeAlias,
            long cutoffMs) {
        List<ExecutionLogEntity> rows = repository.findAliasExecutionsSince(namespace, processCode, routeAlias, cutoffMs,
                PageRequest.of(0, maxQueryRows + 1));
        return requireCompleteResult(rows);
    }

    public Optional<ExecutionLogEntity> findById(String id) {
        return repository.findById(id);
    }

    public Page<ExecutionLogEntity> search(LogQuery query, int page, int pageSize) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be greater than or equal to 1");
        }
        if (pageSize < 1 || pageSize > MAX_SEARCH_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and 500");
        }
        return repository.findAll(toSpecification(query), PageRequest.of(page - 1, pageSize, LOG_ORDER));
    }

    public List<ExecutionLogEntity> export(LogQuery query) {
        List<ExecutionLogEntity> rows = repository.findAllLimited(toSpecification(query), LOG_ORDER, maxQueryRows + 1);
        return requireCompleteResult(rows);
    }

    public ExecutionAggregate aggregate(long startMs, long endMs) {
        ExecutionLogRepository.ExecutionAggregateProjection projection = repository.aggregateBetween(startMs, endMs);
        return new ExecutionAggregate(value(projection.getTotalExecutions()), value(projection.getSuccessExecutions()),
                value(projection.getFailedExecutions()), value(projection.getTotalDurationMs()));
    }

    public List<ProcessAggregate> aggregateProcesses(long startMs, long endMs, int limit) {
        return repository
            .aggregateProcessesBetween(startMs, endMs, PageRequest.of(0, limit))
            .stream()
            .map(row -> new ProcessAggregate(row.getProcessCode(), value(row.getExecutionCount()),
                    value(row.getTotalDurationMs()), value(row.getSuccessCount())))
            .toList();
    }

    public List<ErrorAggregate> aggregateErrors(long startMs, long endMs, int limit) {
        return repository
            .aggregateErrorsBetween(startMs, endMs, PageRequest.of(0, limit))
            .stream()
            .map(row -> new ErrorAggregate(row.getProcessCode(), row.getErrorCode(), row.getErrorMessage(),
                    row.getNamespace(), row.getRequestedVersion(), row.getEffectiveVersion(), row.getRoutingSource(),
                    row.getRouteAlias(), row.getRouteRevision(), value(row.getOccurrenceCount()),
                    value(row.getLastOccurred())))
            .toList();
    }

    public List<VersionAggregate> aggregateVersions(String processCode, long startMs, long endMs, int limit) {
        return repository
            .aggregateVersionsBetween(processCode, startMs, endMs, PageRequest.of(0, limit))
            .stream()
            .map(row -> new VersionAggregate(row.getProcessCode(), row.getNamespace(), row.getEffectiveVersion(),
                    row.getRoutingSource(), row.getRouteAlias(), value(row.getExecutionCount()),
                    value(row.getSuccessCount()), value(row.getFailedCount()), value(row.getTotalDurationMs())))
            .toList();
    }

    public List<TrendAggregate> aggregateTrends(long startMs, long endMs, long bucketMs) {
        return repository
            .aggregateTrendsBetween(startMs, endMs, bucketMs)
            .stream()
            .map(row -> new TrendAggregate(value(row.getBucketIndex()), value(row.getExecutionCount()),
                    value(row.getSuccessCount()), value(row.getFailedCount())))
            .toList();
    }

    private List<ExecutionLogEntity> requireCompleteResult(List<ExecutionLogEntity> rows) {
        if (rows.size() > maxQueryRows) {
            throw new ExecutionLogQueryLimitExceededException(maxQueryRows);
        }
        return List.copyOf(rows);
    }

    public record LogQuery(String processCode, String status, String keyword, Long startMs, Long endMs,
            String invocationId, String parentInvocationId, String traceId, Integer callDepth, String namespace,
            String requestedVersion, String effectiveVersion, String routingSource, String routeAlias,
            Long routeRevision) {
        public LogQuery {
            if (startMs != null && endMs != null && startMs > endMs) {
                throw new IllegalArgumentException("startTime must be before or equal to endTime");
            }
            if (callDepth != null && callDepth < 0) {
                throw new IllegalArgumentException("callDepth must not be negative");
            }
            if (routeRevision != null && routeRevision <= 0) {
                throw new IllegalArgumentException("routeRevision must be greater than 0");
            }
        }

        public static LogQuery empty() {
            return matching(null);
        }

        public static LogQuery matching(String keyword) {
            return new LogQuery(null, null, keyword, null, null, null, null, null, null, null, null, null, null, null,
                    null);
        }
    }

    public record PurgeResult(int deletedCount, boolean hasMore) {}

    public record ExecutionAggregate(long totalExecutions, long successExecutions, long failedExecutions,
            long totalDurationMs) {
        public long averageDurationMs() {
            return totalExecutions == 0L ? 0L : totalDurationMs / totalExecutions;
        }
    }

    public record ProcessAggregate(String processCode, long executionCount, long totalDurationMs, long successCount) {}

    public record ErrorAggregate(String processCode, String errorCode, String errorMessage, String namespace,
            String requestedVersion, String effectiveVersion, String routingSource, String routeAlias,
            Long routeRevision, long occurrenceCount, long lastOccurred) {}

    public record VersionAggregate(String processCode, String namespace, String effectiveVersion, String routingSource,
            String routeAlias, long executionCount, long successCount, long failedCount, long totalDurationMs) {}

    public record TrendAggregate(long bucketIndex, long executionCount, long successCount, long failedCount) {}
}
