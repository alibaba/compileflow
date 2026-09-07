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

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MySqlRoutingOutboxStoreStateMachineTest {
    private DataSource dataSource;
    private MySqlDeployStore repository;

    @BeforeEach
    void setUp() {
        dataSource = H2TestDatabase.createInMemoryDataSource();
        repository = new MySqlDeployStore(dataSource, null);
    }

    @Test
    void pendingRecordCanBeClaimedOnlyOnceWhileLeaseIsValid() {
        long id = insertRecord();

        List<RoutingOutboxRecord> first = repository.claimPending(10, "worker-a", 30000L);
        List<RoutingOutboxRecord> second = repository.claimPending(10, "worker-b", 30000L);

        assertThat(first).singleElement().satisfies(record -> {
            assertThat(record.getId()).isEqualTo(id);
            assertThat(record.getStatus()).isEqualTo(RoutingOutboxRecord.Status.PROCESSING);
            assertThat(record.getLeaseToken()).startsWith("worker-a:");
            assertThat(record.getLeaseUntil()).isNotNull();
        });
        assertThat(second).isEmpty();
    }

    @Test
    void expiredClaimCanBeReclaimedAndOldOwnerIsFenced() throws SQLException {
        long id = insertRecord();
        RoutingOutboxRecord oldClaim = repository.claimPending(1, "worker-old", 30000L).get(0);
        expireLease(id);

        assertThat(repository.countExpiredClaims()).isOne();

        RoutingOutboxRecord newClaim = repository.claimPending(1, "worker-new", 30000L).get(0);

        assertThat(newClaim.getLeaseToken()).isNotEqualTo(oldClaim.getLeaseToken());
        assertThat(repository.markDelivered(id, oldClaim.getLeaseToken())).isZero();
        assertThat(repository.markFailed(id, oldClaim.getLeaseToken(), "late failure from expired owner", 10, 1000L)).isZero();
        assertThat(readLong(id, "attempt_count")).isZero();
        assertThat(repository.markDelivered(id, newClaim.getLeaseToken())).isOne();
        assertThat(repository.countByStatus(RoutingOutboxRecord.Status.DELIVERED)).isOne();
        assertThat(repository.countExpiredClaims()).isZero();
    }

    @Test
    void concurrentDispatchersNeverOwnTheSameRecord() throws Exception {
        for (int index = 0; index < 20; index++) {
            insertRecord(index + 1);
        }
        MySqlDeployStore secondRepository = new MySqlDeployStore(dataSource, null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<RoutingOutboxRecord>> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return repository.claimPending(10, "worker-a", 30000L);
            });
            Future<List<RoutingOutboxRecord>> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return secondRepository.claimPending(10, "worker-b", 30000L);
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<RoutingOutboxRecord> firstClaim = first.get(5, TimeUnit.SECONDS);
            List<RoutingOutboxRecord> secondClaim = second.get(5, TimeUnit.SECONDS);
            assertThat(firstClaim).hasSize(10);
            assertThat(secondClaim).hasSize(10);

            List<RoutingOutboxRecord> claimed = new ArrayList<>();
            claimed.addAll(firstClaim);
            claimed.addAll(secondClaim);
            assertThat(claimed).hasSize(20);
            assertThat(claimed).extracting(RoutingOutboxRecord::getId).doesNotHaveDuplicates();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedDeliveryPersistsBackoffAndReleasesOwnership() throws SQLException {
        long id = insertRecord();
        RoutingOutboxRecord claim = repository.claimPending(1, "worker-a", 30000L).get(0);

        assertThat(repository.markFailed(id, claim.getLeaseToken(), "projection store unavailable", 10, 60000L)).isOne();

        assertThat(repository.claimPending(1, "worker-b", 30000L)).isEmpty();
        assertThat(readString(id, "status")).isEqualTo("PENDING");
        assertThat(readLong(id, "attempt_count")).isEqualTo(1L);
        assertThat(readNullableLong(id, "next_attempt_at")).isNotNull();
        assertThat(readString(id, "lease_token")).isNull();
        assertThat(readNullableLong(id, "lease_until")).isNull();
    }

    @Test
    void exhaustedRecordDeadLettersAndCanBeRequeued() throws SQLException {
        long id = insertRecord();
        RoutingOutboxRecord claim = repository.claimPending(1, "worker-a", 30000L).get(0);
        setAttemptCount(id, 9);

        assertThat(repository.markFailed(id, claim.getLeaseToken(), "permanent failure", 10, 1000L)).isOne();
        assertThat(readString(id, "status")).isEqualTo("FAILED");
        assertThat(readNullableLong(id, "next_attempt_at")).isNull();

        assertThat(repository.requeueFailed()).isOne();
        assertThat(repository.claimPending(1, "worker-b", 30000L)).hasSize(1);
        assertThat(readLong(id, "attempt_count")).isZero();
    }

    @Test
    void ensuringTheSameDeliveryCoalescesWhileItIsActive() {
        assertThat(ensureRecord()).isTrue();
        assertThat(ensureRecord()).isFalse();

        assertThat(repository.countByStatus(RoutingOutboxRecord.Status.PENDING)).isOne();
    }

    @Test
    void ensuringACompletedDeliveryReactivatesTheExistingRecord() {
        assertThat(ensureRecord()).isTrue();
        RoutingOutboxRecord firstClaim = repository.claimPending(1, "worker-a", 30000L).get(0);
        assertThat(repository.markDelivered(firstClaim.getId(), firstClaim.getLeaseToken())).isOne();

        assertThat(ensureRecord()).isTrue();

        RoutingOutboxRecord secondClaim = repository.claimPending(1, "worker-b", 30000L).get(0);
        assertThat(secondClaim.getId()).isEqualTo(firstClaim.getId());
        assertThat(secondClaim.getAttemptCount()).isZero();
    }

    @Test
    void ensuringADeadLetterDoesNotBypassOperatorRequeue() {
        assertThat(ensureRecord()).isTrue();
        RoutingOutboxRecord claim = repository.claimPending(1, "worker-a", 30000L).get(0);
        assertThat(repository.markFailed(claim.getId(), claim.getLeaseToken(), "permanent failure", 1, 1000L)).isOne();

        assertThat(ensureRecord()).isFalse();
        assertThat(repository.countByStatus(RoutingOutboxRecord.Status.FAILED)).isOne();
        assertThat(repository.countByStatus(RoutingOutboxRecord.Status.PENDING)).isZero();
    }

    @Test
    void concurrentEnsuresProduceOneDeliveryRecord() throws Exception {
        MySqlDeployStore secondRepository = new MySqlDeployStore(dataSource, null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return ensureRecord();
            });
            Future<Boolean> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return secondRepository.ensurePending(RoutingOutboxRecord.ALIAS_STATE_EVENT_TYPE, "default",
                        "order.flow", "prod", "routing.key", "{\"revision\":1}");
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
            assertThat(repository.countByStatus(RoutingOutboxRecord.Status.PENDING)).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    private long insertRecord() {
        return insertRecord(1);
    }

    private long insertRecord(int revision) {
        return repository.appendPending(RoutingOutboxRecord.ALIAS_STATE_EVENT_TYPE, "default", "order.flow", "prod",
                "routing.key", "{\"revision\":" + revision + "}");
    }

    private boolean ensureRecord() {
        return repository.ensurePending(RoutingOutboxRecord.ALIAS_STATE_EVENT_TYPE, "default", "order.flow", "prod",
                "routing.key", "{\"revision\":1}");
    }

    private void expireLease(long id) throws SQLException {
        executeUpdate("UPDATE cf_routing_outbox SET lease_until = 0 WHERE id = ?", id, null);
    }

    private void setAttemptCount(long id, int attempts) throws SQLException {
        executeUpdate("UPDATE cf_routing_outbox SET attempt_count = ? WHERE id = ?", id, attempts);
    }

    private void executeUpdate(String sql, long id, Integer firstValue) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            if (firstValue == null) {
                statement.setLong(1, id);
            } else {
                statement.setInt(1, firstValue);
                statement.setLong(2, id);
            }
            assertThat(statement.executeUpdate()).isOne();
        }
    }

    private String readString(long id, String column) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                connection.prepareStatement("SELECT " + column + " FROM cf_routing_outbox WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private long readLong(long id, String column) throws SQLException {
        return readNullableLong(id, column);
    }

    private Long readNullableLong(long id, String column) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                connection.prepareStatement("SELECT " + column + " FROM cf_routing_outbox WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                long value = result.getLong(1);
                return result.wasNull() ? null : value;
            }
        }
    }
}
