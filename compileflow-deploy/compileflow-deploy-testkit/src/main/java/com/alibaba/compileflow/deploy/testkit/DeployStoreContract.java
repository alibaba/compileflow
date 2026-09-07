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
package com.alibaba.compileflow.deploy.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.deploy.api.command.CreateRolloutCommand;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.rollout.ProcessRollout;
import com.alibaba.compileflow.deploy.api.rollout.RolloutPhase;
import com.alibaba.compileflow.deploy.spi.store.DeployStore;
import com.alibaba.compileflow.deploy.spi.store.ProcessAliasRecord;
import com.alibaba.compileflow.deploy.spi.store.ProcessVersionRecord;
import com.alibaba.compileflow.deploy.spi.store.PublishedVersionPageKey;
import com.alibaba.compileflow.deploy.spi.store.RolloutCreateRequest;
import com.alibaba.compileflow.deploy.spi.store.RolloutStoreQuery;
import com.alibaba.compileflow.deploy.spi.store.RoutingOutboxRecord;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Executable conformance contract for one complete Deploy persistence authority.
 *
 * <p>The contract deliberately observes only domain behavior. Provider SQL, connection handling,
 * schema layout, and transaction mechanisms remain private to each implementation.
 *
 * @author yusu
 */
public abstract class DeployStoreContract {
    private static final String NAMESPACE = "contract";
    private static final String CODE = "order-approval";
    private static final String ALIAS = "production";
    private DeployStore store;

    /**
     * Returns a newly migrated, empty provider store for the current test.
     */
    protected abstract DeployStore createEmptyStore() throws Exception;

    /**
     * Returns the store created for the current test.
     */
    protected final DeployStore store() {
        return store;
    }

    @BeforeEach
    final void initializeStore() throws Exception {
        store = createEmptyStore();
    }

    @Test
    void modelTypeBelongsToTheExactVersion() {
        store.save(version(CODE, "v1", ProcessModelType.TBBPM, "<flow id=\"v1\"/>", 10L));

        store.save(version(CODE, "v2", ProcessModelType.BPMN, "<definitions id=\"v2\"/>", 20L));
        assertThat(store.find(NAMESPACE, CODE, "v2"))
            .get()
            .extracting(ProcessVersionRecord::getModelType)
            .isEqualTo(ProcessModelType.BPMN);
        assertThatThrownBy(() -> store.save(version(CODE, "v1", ProcessModelType.BPMN, "<flow id=\"v1\"/>", 30L)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.VERSION_CONFLICT));
    }

    @Test
    void immutableVersionReplayRequiresTheSameExecutableArtifact() {
        ProcessVersionRecord original = store.save(version(CODE, "v1", ProcessModelType.TBBPM, "<flow/>", 10L));
        ProcessVersionRecord replay = store.save(version(CODE, "v1", ProcessModelType.TBBPM, "<flow/>", 20L));

        assertThat(replay.getArtifactDigest()).isEqualTo(original.getArtifactDigest());
        assertThat(replay.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThatThrownBy(() -> store.save(
                version(CODE, "v1", ProcessModelType.TBBPM, "<flow changed=\"true\"/>", 30L)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.VERSION_CONFLICT));
    }

    @Test
    void processIdentityAndVersionComparisonAreExactAndCaseSensitive() {
        store.save(version("Order", "Release-A", ProcessModelType.TBBPM, "<flow id=\"upper\"/>", 10L));
        store.save(version("order", "release-a", ProcessModelType.BPMN, "<flow id=\"lower\"/>", 20L));

        assertThat(store.find(NAMESPACE, "Order", "Release-A"))
            .get()
            .extracting(ProcessVersionRecord::getModelType)
            .isEqualTo(ProcessModelType.TBBPM);
        assertThat(store.find(NAMESPACE, "order", "release-a"))
            .get()
            .extracting(ProcessVersionRecord::getModelType)
            .isEqualTo(ProcessModelType.BPMN);
        assertThat(store.find(NAMESPACE, "ORDER", "Release-A")).isEmpty();
    }

    @Test
    void rolloutCommitsAliasAuditAndOutboxAsOneObservableFact() {
        save("v1", 10L);

        ProcessRollout rollout = store.create(allAtOnce("deploy-v1", "v1", 0L));

        assertThat(rollout.getPhase()).isEqualTo(RolloutPhase.COMPLETED);
        assertThat(store.resolve(NAMESPACE, CODE, ALIAS))
            .get()
            .satisfies(alias -> {
                assertThat(alias.getStableVersion()).isEqualTo("v1");
                assertThat(alias.getRevision()).isEqualTo(rollout.getAliasRevision());
            });
        assertThat(store.listEvents(rollout.getId()))
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getRolloutId()).isEqualTo(rollout.getId());
                assertThat(event.getSequence()).isOne();
                assertThat(event.getType()).isEqualTo("COMPLETED");
                assertThat(event.getFromPhase()).isNull();
                assertThat(event.getToPhase()).isEqualTo(RolloutPhase.COMPLETED);
                assertThat(event.getActor()).isEqualTo("contract-test");
                assertThat(event.getReason()).isNull();
            });
        assertThat(store.countByStatus(RoutingOutboxRecord.Status.PENDING)).isOne();
    }

    @Test
    void failedRolloutLeavesNoPartialAliasAuditOrOutboxState() {
        assertThatThrownBy(() -> store.create(allAtOnce("missing", "missing", 0L)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.VERSION_NOT_FOUND));

        assertThat(store.resolve(NAMESPACE, CODE, ALIAS)).isEmpty();
        assertThat(store.list(new RolloutStoreQuery(NAMESPACE, CODE, null, ALIAS, null, null, 10))).isEmpty();
        assertThat(store.countByStatus(RoutingOutboxRecord.Status.PENDING)).isZero();
    }

    @Test
    void rolloutIdempotencyAndAliasRevisionAreCompareAndSetBoundaries() {
        save("v1", 10L);
        save("v2", 20L);
        RolloutCreateRequest request = allAtOnce("stable-request", "v1", 0L);
        ProcessRollout first = store.create(request);

        assertThat(store.create(request).getId()).isEqualTo(first.getId());
        assertThatThrownBy(() -> store.create(allAtOnce("stable-request", "v2", first.getAliasRevision())))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.IDEMPOTENCY_CONFLICT));
        assertThatThrownBy(() -> store.create(allAtOnce("stale-revision", "v2", 0L)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.CONCURRENT_MODIFICATION));
        assertThat(store.resolve(NAMESPACE, CODE, ALIAS))
            .get()
            .extracting(ProcessAliasRecord::getStableVersion)
            .isEqualTo("v1");
    }

    @Test
    void onlyOneActiveCanaryMayOwnAnAlias() {
        save("v1", 10L);
        save("v2", 20L);
        save("v3", 30L);
        ProcessRollout initial = store.create(allAtOnce("deploy-v1", "v1", 0L));
        ProcessRollout canary = store.create(canary("canary-v2", "v2", initial.getAliasRevision()));

        assertThat(canary.getPhase()).isEqualTo(RolloutPhase.IN_PROGRESS);
        assertThatThrownBy(() -> store.create(canary("canary-v3", "v3", canary.getAliasRevision())))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.ROLLOUT_CONFLICT));
        assertThat(store.list(new RolloutStoreQuery(NAMESPACE, CODE, null, ALIAS, RolloutPhase.IN_PROGRESS, null, 10)))
            .singleElement()
            .extracting(ProcessRollout::getId)
            .isEqualTo(canary.getId());
    }

    @Test
    void outboxLeaseTokenFencesLateWorkersAndDeliveredFactsCanBeReactivated() {
        long id = store.appendPending(RoutingOutboxRecord.ALIAS_STATE_EVENT_TYPE, NAMESPACE, CODE, ALIAS,
                "contract.routing.key", "{\"revision\":1}");
        RoutingOutboxRecord claim = store.claimPending(1, "contract-worker", 30_000L).get(0);

        assertThat(store.markDelivered(id, "stale-token")).isZero();
        assertThat(store.markFailed(id, "stale-token", "late", 3, 0L)).isZero();
        assertThat(store.markDelivered(id, claim.getLeaseToken())).isOne();
        assertThat(store.ensurePending(RoutingOutboxRecord.ALIAS_STATE_EVENT_TYPE, NAMESPACE, CODE, ALIAS,
                "contract.routing.key", "{\"revision\":1}"))
            .isTrue();
        assertThat(store.countByStatus(RoutingOutboxRecord.Status.PENDING)).isOne();
    }

    @Test
    void keysetPaginationUsesCreatedTimeThenExactVersion() {
        save("v1", 10L);
        save("v2", 20L);
        save("v3", 20L);

        assertThat(store.list(NAMESPACE, CODE, null, null, 2))
            .extracting(ProcessVersionRecord::getVersion)
            .containsExactly("v3", "v2");
        assertThat(store.list(NAMESPACE, CODE, null, new PublishedVersionPageKey(20L, "v2"), 2))
            .extracting(ProcessVersionRecord::getVersion)
            .containsExactly("v1");
    }

    @Test
    void providerClockIsAuthoritativeAndReturnsEpochMilliseconds() {
        long before = Instant.now().minusSeconds(5).toEpochMilli();
        long providerTime = store.currentTimeMillis();
        long after = Instant.now().plusSeconds(5).toEpochMilli();

        assertThat(providerTime).isBetween(before, after);
    }

    private void save(String version, long createdAt) {
        store.save(version(CODE, version, ProcessModelType.TBBPM, "<flow id=\"" + version + "\"/>", createdAt));
    }

    private RolloutCreateRequest allAtOnce(String idempotencyKey, String version, long expectedRevision) {
        return RolloutCreateRequest.deploy(CreateRolloutCommand.allAtOnce(idempotencyKey,
                ProcessRef.alias(NAMESPACE, CODE, ALIAS), ProcessRef.version(NAMESPACE, CODE, version), expectedRevision,
                "contract-test", null));
    }

    private RolloutCreateRequest canary(String idempotencyKey, String version, long expectedRevision) {
        return RolloutCreateRequest.deploy(CreateRolloutCommand.canary(idempotencyKey,
                ProcessRef.alias(NAMESPACE, CODE, ALIAS), ProcessRef.version(NAMESPACE, CODE, version), expectedRevision,
                1_000, "contract-test", null));
    }

    private ProcessVersionRecord version(String code, String version, ProcessModelType modelType, String content,
            long createdAt) {
        ProcessDefinition.Inline definition = ProcessDefinition.inline(modelType, code, content);
        return ProcessVersionRecord
            .builder()
            .namespace(NAMESPACE)
            .code(code)
            .version(version)
            .processDefinition(definition)
            .artifactDigest(ProcessArtifactDigest.compute(definition, Map.of()))
            .actor("contract-test")
            .createdAt(createdAt)
            .build();
    }
}
