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
package com.alibaba.compileflow.deploy.control.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.protocol.ProcessArtifactKeys;
import com.alibaba.compileflow.deploy.protocol.ProcessArtifactCodec;
import com.alibaba.compileflow.deploy.spi.projection.DeploymentProjectionStore;
import com.alibaba.compileflow.deploy.testkit.InMemoryDeploymentProjectionStore;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProcessArtifactProjectorTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private static final String PREFIX = "test.flow.";

    private static ProcessRef.Version ref() {
        return ProcessRef.version("default", "order.rule", "v1");
    }

    private static ProcessDefinition.Inline definition(String content) {
        return ProcessDefinition.inline(ProcessModelType.BPMN, "order.rule", content);
    }

    private static String key() {
        return ProcessArtifactKeys.versioned(PREFIX, "default", "order.rule", "v1");
    }

    private static String digest(ProcessDefinition.Inline definition) {
        return ProcessArtifactDigest.compute(definition, Map.of());
    }

    private static String artifactPayload(ProcessDefinition.Inline definition, String digest) throws Exception {
        return ProcessArtifactCodec.artifactJson(ref().namespace(), ref().code(), ref().version(), ProcessModelType.BPMN,
                definition.content(), digest);
    }

    @Test
    void writesExplicitImmutableArtifactIdentity() throws Exception {
        InMemoryDeploymentProjectionStore projectionStore = new InMemoryDeploymentProjectionStore();
        ProcessArtifactProjector projector = new ProcessArtifactProjector(projectionStore, PREFIX, TIMEOUT);
        ProcessRef.Version ref = ref();
        ProcessDefinition.Inline definition = definition("<definitions/>");
        String digest = digest(definition);

        assertThat(projector.repair(ref, definition, digest)).isEqualTo(ArtifactProjectionStatus.REPAIRED);

        ProcessArtifact artifact = ProcessArtifactCodec.parse(projectionStore.read(key(), TIMEOUT));
        assertThat(artifact.getRef()).isEqualTo(ref);
        assertThat(artifact.getDefinition().modelType()).isEqualTo(ProcessModelType.BPMN);
        assertThat(artifact.getDefinition()).isEqualTo(definition);
        assertThat(artifact.getArtifactDigest()).isEqualTo(digest);
    }

    @Test
    void samePersistedWinnerIsIdempotent() throws Exception {
        InMemoryDeploymentProjectionStore projectionStore = new InMemoryDeploymentProjectionStore();
        ProcessArtifactProjector projector = new ProcessArtifactProjector(projectionStore, PREFIX, TIMEOUT);
        ProcessDefinition.Inline definition = definition("<definitions/>");
        String digest = digest(definition);

        assertThat(projector.repair(ref(), definition, digest)).isEqualTo(ArtifactProjectionStatus.REPAIRED);
        assertThat(projector.repair(ref(), definition, digest)).isEqualTo(ArtifactProjectionStatus.PRESENT);
    }

    @Test
    void concurrentIdenticalCreateIsIdempotent() throws Exception {
        ProcessDefinition.Inline definition = definition("<definitions/>");
        String digest = digest(definition);
        String payload = artifactPayload(definition, digest);
        ConcurrentWinnerProjectionStore projectionStore = new ConcurrentWinnerProjectionStore(payload);
        ProcessArtifactProjector projector = new ProcessArtifactProjector(projectionStore, PREFIX, TIMEOUT);

        assertThat(projector.repair(ref(), definition, digest)).isEqualTo(ArtifactProjectionStatus.PRESENT);

        assertThat(projectionStore.read(key(), TIMEOUT)).isEqualTo(payload);
    }

    @Test
    void existingKeyCannotBeReboundToDifferentContentOrModelType() throws Exception {
        InMemoryDeploymentProjectionStore projectionStore = new InMemoryDeploymentProjectionStore();
        ProcessArtifactProjector projector = new ProcessArtifactProjector(projectionStore, PREFIX, TIMEOUT);
        ProcessDefinition.Inline first = definition("<definitions/>");
        projector.repair(ref(), first, digest(first));

        ProcessDefinition.Inline changed = definition("<definitions><process/></definitions>");
        assertThatThrownBy(() -> projector.repair(ref(), changed, digest(changed)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH));
        ProcessDefinition.Inline retyped =
                ProcessDefinition.inline(ProcessModelType.TBBPM, first.code(), first.content());
        assertThatThrownBy(() -> projector.repair(ref(), retyped, digest(retyped)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH));
    }

    @Test
    void channelReadFailureDoesNotDegradeIntoAnOverwrite() {
        AtomicInteger writes = new AtomicInteger();
        DeploymentProjectionStore projectionStore = new DeploymentProjectionStore() {
            @Override
            public String read(String key, Duration timeout) throws Exception {
                throw new Exception("unavailable");
            }

            @Override
            public boolean compareAndSet(String key, String expectedContent, String content, String contentType,
                    Duration timeout) {
                writes.incrementAndGet();
                return true;
            }

            @Override
            public Subscription subscribe(String key, UpdateCallback callback, Duration timeout) {
                throw new UnsupportedOperationException();
            }
        };
        ProcessArtifactProjector projector = new ProcessArtifactProjector(projectionStore, PREFIX, TIMEOUT);
        ProcessDefinition.Inline definition = definition("<definitions/>");

        assertThatThrownBy(() -> projector.repair(ref(), definition, digest(definition))).hasMessage("unavailable");
        assertThat(writes).hasValue(0);
    }

    @Test
    void corruptExistingPayloadFailsClosed() throws Exception {
        InMemoryDeploymentProjectionStore projectionStore = new InMemoryDeploymentProjectionStore();
        ProcessDefinition.Inline definition = definition("<definitions/>");
        String actualDigest = digest(definition);
        String payload = ProcessArtifactCodec
            .artifactJson(ref().namespace(), ref().code(), ref().version(), ProcessModelType.BPMN, definition.content(),
                    actualDigest)
            .replace(actualDigest, "0".repeat(64));
        assertThat(projectionStore.compareAndSet(key(), null, payload, "application/json", TIMEOUT)).isTrue();
        ProcessArtifactProjector projector = new ProcessArtifactProjector(projectionStore, PREFIX, TIMEOUT);

        assertThatThrownBy(() -> projector.repair(ref(), definition, digest(definition)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.ARTIFACT_DIGEST_MISMATCH));
    }

    private static final class ConcurrentWinnerProjectionStore implements DeploymentProjectionStore {
        private final InMemoryDeploymentProjectionStore delegate = new InMemoryDeploymentProjectionStore();
        private final String winnerPayload;
        private final AtomicBoolean winnerInjected = new AtomicBoolean();

        private ConcurrentWinnerProjectionStore(String winnerPayload) {
            this.winnerPayload = winnerPayload;
        }

        @Override
        public String read(String key, Duration timeout) throws Exception {
            return delegate.read(key, timeout);
        }

        @Override
        public boolean compareAndSet(String key, String expectedContent, String content, String contentType,
                Duration timeout) throws Exception {
            if (expectedContent == null && winnerInjected.compareAndSet(false, true)) {
                if (!delegate.compareAndSet(key, null, winnerPayload, contentType, timeout)) {
                    throw new IllegalStateException("Failed to inject concurrent winner");
                }
                return false;
            }
            return delegate.compareAndSet(key, expectedContent, content, contentType, timeout);
        }

        @Override
        public Subscription subscribe(String key, UpdateCallback callback, Duration timeout) throws Exception {
            return delegate.subscribe(key, callback, timeout);
        }
    }
}
