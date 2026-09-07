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

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessModelType;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "compileflow.workbench.server.execution-log.purge-batch-size=2")
@ActiveProfiles("test")
@Transactional
class ExecutionLogAggregationPersistenceTest {
    @Test
    void unresolvedVersionFailurePersistsWithoutInventingAFormat() {
        logService.persist(
                new ExecutionLogRecord("missing", "failed", 1L, "CF_EXEC_001", "Unavailable", 1_000L,
                        "unresolved-invocation", null, 0, "trace", "default", null, null, "v1", null, "version", null,
                        null));
        logRepository.flush();
        assertThat(logRepository.findAll())
            .filteredOn(row -> "unresolved-invocation".equals(row.getInvocationId()))
            .singleElement()
            .satisfies(row -> {
                assertThat(row.getModelType()).isNull();
                assertThat(row.getSourceDigest()).isNull();
                assertThat(row.getRequestedVersion()).isEqualTo("v1");
            });
    }

    @Test
    void topNGroupsUseAllDimensionsAndExplicitNullOrdering() {
        for (String namespace : List.of("z", "a")) {
            for (String alias : new String[] {"z", "a", null}) {
                logService.persist(
                        new ExecutionLogRecord("tied", "failed", 1L, "E", "same", 1_000L, namespace + "-" + alias, null,
                                0, "trace", namespace, ProcessModelType.TBBPM, "a".repeat(64), null, "v1", "alias",
                                alias, alias == null ? null : 1L));
            }
        }
        for (int limit = 1; limit <= 6; limit++) {
            List<String> expected =
                    java.util.Arrays.asList("a:null", "a:a", "a:z", "z:null", "z:a", "z:z").subList(0, limit);
            assertThat(logService.aggregateErrors(0L, 2_000L, limit))
                .extracting(row -> row.namespace() + ":" + row.routeAlias())
                .containsExactlyElementsOf(expected);
            assertThat(logService.aggregateVersions("tied", 0L, 2_000L, limit))
                .extracting(row -> row.namespace() + ":" + row.routeAlias())
                .containsExactlyElementsOf(expected);
        }
    }

    @Autowired
    private ExecutionLogService logService;
    @Autowired
    private ExecutionLogRepository logRepository;

    @BeforeEach
    void isolateLogsWithinTheTestTransaction() {
        logRepository.deleteAllInBatch();
    }

    private static ExecutionLogRecord record(String processCode, String status, long durationMs, String errorCode,
            String errorMessage, long loggedAt) {
        return new ExecutionLogRecord(processCode, status, durationMs, errorCode, errorMessage, loggedAt,
                "inv-" + loggedAt, null, 0, "trace-" + loggedAt, "default", ProcessModelType.TBBPM, "a".repeat(64), null,
                "v1", "alias", "production", 1L);
    }

    private static ExecutionLogRecord previewRecord(long loggedAt) {
        return new ExecutionLogRecord("preview.flow", "success", 999L, null, null, loggedAt, "preview-invocation", null,
                0, "preview-trace", "default", ProcessModelType.TBBPM, "b".repeat(64), null, null, "definition", null,
                null);
    }

    @Test
    void aggregatesTheSharedExecutionLogInsideOneBoundedWindow() {
        logService.persist(record("payment.approve", "success", 10L, null, null, 1_000L));
        logService.persist(record("payment.approve", "failed", 20L, "CF_EXEC_001", "Rejected", 2_000L));
        logService.persist(record("payment.approve", "failed", 30L, "CF_EXEC_001", "Rejected", 3_000L));
        logService.persist(record("payment.approve", "failed", 50L, "CF_EXEC_004", "Timed out", 5_000L));
        logService.persist(record("invoice.approve", "success", 40L, null, null, 4_000L));
        logService.persist(previewRecord(4_500L));
        logService.persist(record("outside.window", "success", 50L, null, null, 60_000L));

        ExecutionLogService.ExecutionAggregate aggregate = logService.aggregate(0L, 60_000L);
        List<ExecutionLogService.ProcessAggregate> flows = logService.aggregateProcesses(0L, 60_000L, 10);
        List<ExecutionLogService.ErrorAggregate> errors = logService.aggregateErrors(0L, 60_000L, 10);
        List<ExecutionLogService.VersionAggregate> versions =
                logService.aggregateVersions("payment.approve", 0L, 60_000L, 10);
        List<ExecutionLogService.TrendAggregate> trends = logService.aggregateTrends(0L, 60_000L, 30_000L);

        assertThat(aggregate).isEqualTo(new ExecutionLogService.ExecutionAggregate(5L, 2L, 3L, 150L));
        assertThat(aggregate.averageDurationMs()).isEqualTo(30L);
        assertThat(flows).first().satisfies(flow -> {
            assertThat(flow.processCode()).isEqualTo("payment.approve");
            assertThat(flow.executionCount()).isEqualTo(4L);
            assertThat(flow.successCount()).isEqualTo(1L);
            assertThat(flow.totalDurationMs()).isEqualTo(110L);
        });
        assertThat(errors).hasSize(2);
        assertThat(errors)
            .filteredOn(error -> "CF_EXEC_001".equals(error.errorCode()))
            .singleElement()
            .satisfies(error -> {
                assertThat(error.processCode()).isEqualTo("payment.approve");
                assertThat(error.errorMessage()).isEqualTo("Rejected");
                assertThat(error.occurrenceCount()).isEqualTo(2L);
                assertThat(error.lastOccurred()).isEqualTo(3_000L);
            });
        assertThat(errors)
            .extracting(ExecutionLogService.ErrorAggregate::errorCode)
            .containsExactlyInAnyOrder("CF_EXEC_001", "CF_EXEC_004");
        assertThat(versions)
            .singleElement()
            .satisfies(version -> {
                assertThat(version.executionCount()).isEqualTo(4L);
                assertThat(version.successCount()).isEqualTo(1L);
                assertThat(version.failedCount()).isEqualTo(3L);
                assertThat(version.routeAlias()).isEqualTo("production");
            });
        assertThat(trends)
            .singleElement()
            .satisfies(bucket -> {
                assertThat(bucket.bucketIndex()).isZero();
                assertThat(bucket.executionCount()).isEqualTo(5L);
                assertThat(bucket.successCount()).isEqualTo(2L);
                assertThat(bucket.failedCount()).isEqualTo(3L);
            });
    }

    @Test
    void trendBucketsUseFloorAtNonAlignedBoundaries() {
        logService.persist(record("bucket.flow", "success", 1L, null, null, 1_001L));
        logService.persist(record("bucket.flow", "failed", 1L, "E", "failed", 1_999L));
        logService.persist(record("bucket.flow", "success", 1L, null, null, 2_001L));
        logService.persist(record("bucket.flow", "success", 1L, null, null, 4_001L));

        assertThat(logService.aggregateTrends(1_001L, 4_001L, 1_000L))
            .containsExactly(new ExecutionLogService.TrendAggregate(0L, 2L, 1L, 1L),
                    new ExecutionLogService.TrendAggregate(1L, 1L, 1L, 0L));
    }

    @Test
    void searchTreatsLikeMetacharactersLiterallyAndUsesStableOrdering() {
        long loggedAt = 10_000L;
        logService.persist(record("payment.approve", "failed", 10L, "CF_EXEC_001", "rate%limit", loggedAt));
        logService.persist(record("payment.approve", "failed", 10L, "CFXEXECX001", "rateXlimit", loggedAt));

        List<ExecutionLogEntity> underscoreMatches =
                logService.search(ExecutionLogService.LogQuery.matching("CF_EXEC_001"), 1, 20).getContent();
        List<ExecutionLogEntity> percentMatches =
                logService.search(ExecutionLogService.LogQuery.matching("rate%limit"), 1, 20).getContent();
        List<String> orderedIds = logService
            .search(ExecutionLogService.LogQuery.empty(), 1, 20)
            .getContent()
            .stream()
            .filter(row -> row.getLoggedAt() == loggedAt)
            .map(ExecutionLogEntity::getId)
            .toList();

        assertThat(underscoreMatches).extracting(ExecutionLogEntity::getErrorCode).containsExactly("CF_EXEC_001");
        assertThat(percentMatches).extracting(ExecutionLogEntity::getErrorMessage).containsExactly("rate%limit");
        assertThat(orderedIds).isSortedAccordingTo(Comparator.naturalOrder());
    }

    @Test
    void canaryEvidenceIncludesOnlyAliasRoutedExecutions() {
        logService.persist(record("payment.approve", "success", 10L, null, null, 1_000L));
        logService.persist(
                new ExecutionLogRecord("payment.approve", "success", 20L, null, null, 2_000L, "inv-2", null, 0,
                        "trace-2", "default", ProcessModelType.TBBPM, "c".repeat(64), "v1", "v1", "version",
                        "production", 1L));

        assertThat(logService.findForRouteSince("default", "payment.approve", "production", 0L))
            .singleElement()
            .extracting(ExecutionLogEntity::getRoutingSource)
            .isEqualTo("alias");
    }

    @Test
    void retentionDeletionIsBoundedAndPreservesRowsAtTheCutoff() {
        logService.persist(record("oldest.flow", "success", 10L, null, null, 1_000L));
        logService.persist(record("older.flow", "success", 10L, null, null, 2_000L));
        logService.persist(record("old.flow", "success", 10L, null, null, 3_000L));
        logService.persist(record("cutoff.flow", "success", 10L, null, null, 4_000L));

        assertThat(logService.purgeOlderThan(4_000L)).isEqualTo(new ExecutionLogService.PurgeResult(2, true));
        assertThat(logRepository.count()).isEqualTo(2L);
        assertThat(logService.purgeOlderThan(4_000L)).isEqualTo(new ExecutionLogService.PurgeResult(1, false));
        assertThat(logRepository.count()).isOne();
        assertThat(logService.purgeOlderThan(4_000L)).isEqualTo(new ExecutionLogService.PurgeResult(0, false));
        assertThat(logRepository.findAll()).extracting(ExecutionLogEntity::getProcessCode).containsExactly(
                "cutoff.flow");
    }
}
