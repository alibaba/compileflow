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
package com.alibaba.compileflow.workbench.server.process;

import com.alibaba.compileflow.engine.ProcessModelType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Timeout(30)
class ProcessOptimisticLockPersistenceTest {
    @Autowired
    private ProcessDraftRepository repository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting concurrent writers", failure);
        } catch (Exception failure) {
            throw new IllegalStateException("Concurrent writers did not reach the update barrier", failure);
        }
    }

    private static ProcessDraftEntity flow(String code) {
        Instant now = Instant.now();
        ProcessDraftEntity entity = new ProcessDraftEntity();
        entity.setCode(code);
        entity.setName("Original");
        entity.setType(ProcessModelType.BPMN);
        entity.setXml("<definitions/>");
        entity.setDescription(null);
        entity.setTagsJson("[]");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy("integration-test");
        return entity;
    }

    @Test
    void missingConcurrentWriterFailsInsteadOfWaitingForever() throws Exception {
        ExecutorService writer = Executors.newSingleThreadExecutor();
        Future<?> waiting = writer.submit(() -> await(new CyclicBarrier(2)));
        try {
            assertThatThrownBy(() -> waiting.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(TimeoutException.class);
        } finally {
            waiting.cancel(true);
            writer.shutdownNow();
            assertThat(writer.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void exactlyOneConcurrentWriterCanCommitTheSameRevision() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        transactions.setTimeout(10);
        String processCode = "concurrent.order." + UUID.randomUUID();
        try {
            transactions.executeWithoutResult(ignored -> repository.saveAndFlush(flow(processCode)));

            CyclicBarrier loaded = new CyclicBarrier(2);
            ExecutorService writers = Executors.newFixedThreadPool(2);
            try {
                Future<Boolean> first =
                        writers.submit(() -> updateAfterBarrier(transactions, loaded, processCode, "First"));
                Future<Boolean> second =
                        writers.submit(() -> updateAfterBarrier(transactions, loaded, processCode, "Second"));

                assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            } finally {
                writers.shutdownNow();
                assertThat(writers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }

            ProcessDraftEntity committed =
                    transactions.execute(ignored -> repository.findById(processCode).orElseThrow());
            assertThat(committed.getRevision()).isEqualTo(1L);
            assertThat(committed.getName()).isIn("First", "Second");
        } finally {
            transactions.executeWithoutResult(ignored -> repository
                .findById(processCode)
                .ifPresent(repository::delete));
        }
    }

    private boolean updateAfterBarrier(TransactionTemplate transactions, CyclicBarrier loaded, String code, String name) {
        try {
            transactions.executeWithoutResult(ignored -> {
                ProcessDraftEntity entity = repository.findById(code).orElseThrow();
                await(loaded);
                entity.setName(name);
                entity.setUpdatedAt(Instant.now());
                repository.saveAndFlush(entity);
            });
            return true;
        } catch (ObjectOptimisticLockingFailureException expected) {
            return false;
        }
    }
}
