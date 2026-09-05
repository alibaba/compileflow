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
import com.alibaba.compileflow.deploy.api.protocol.artifact.ProcessArtifactKeys;
import com.alibaba.compileflow.deploy.api.protocol.artifact.ProcessArtifactParser;
import com.alibaba.compileflow.deploy.api.protocol.artifact.ProcessArtifactPayloads;
import com.alibaba.compileflow.deploy.api.sync.DeploymentSyncChannel;
import com.alibaba.compileflow.deploy.api.sync.inmemory.InMemoryDeploymentSyncChannel;
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
        return ProcessDefinition.inline("order.rule", content);
    }

    private static String key() {
        return ProcessArtifactKeys.versioned(PREFIX, "default", "order.rule", "v1");
    }

    private static String digest(ProcessModelType modelType, ProcessDefinition.Inline definition) {
        return ProcessArtifactDigest.compute(modelType, definition, Map.of());
    }

    private static String artifactPayload(ProcessDefinition.Inline definition, String digest) throws Exception {
        return ProcessArtifactPayloads.artifactJson(ref().namespace(), ref().code(), ref().version(),
                ProcessModelType.BPMN, definition.content(), digest);
    }

    @Test
    void writesExplicitImmutableArtifactIdentity() throws Exception {
        InMemoryDeploymentSyncChannel channel = new InMemoryDeploymentSyncChannel();
        ProcessArtifactProjector projector = new ProcessArtifactProjector(channel, PREFIX, TIMEOUT);
        ProcessRef.Version ref = ref();
        ProcessDefinition.Inline definition = definition("<definitions/>");
        String digest = digest(ProcessModelType.BPMN, definition);

        assertThat(projector.repair(ref, ProcessModelType.BPMN, definition, digest))
            .isEqualTo(ArtifactProjectionStatus.REPAIRED);

        ProcessArtifact artifact = ProcessArtifactParser.parse(channel.read(key(), TIMEOUT));
        assertThat(artifact.getRef()).isEqualTo(ref);
        assertThat(artifact.getModelType()).isEqualTo(ProcessModelType.BPMN);
        assertThat(artifact.getDefinition()).isEqualTo(definition);
        assertThat(artifact.getArtifactDigest()).isEqualTo(digest);
    }

    @Test
    void samePersistedWinnerIsIdempotent() throws Exception {
        InMemoryDeploymentSyncChannel channel = new InMemoryDeploymentSyncChannel();
        ProcessArtifactProjector projector = new ProcessArtifactProjector(channel, PREFIX, TIMEOUT);
        ProcessDefinition.Inline definition = definition("<definitions/>");
        String digest = digest(ProcessModelType.BPMN, definition);

        assertThat(projector.repair(ref(), ProcessModelType.BPMN, definition, digest))
            .isEqualTo(ArtifactProjectionStatus.REPAIRED);
        assertThat(projector.repair(ref(), ProcessModelType.BPMN, definition, digest))
            .isEqualTo(ArtifactProjectionStatus.PRESENT);
    }

    @Test
    void concurrentIdenticalCreateIsIdempotent() throws Exception {
        ProcessDefinition.Inline definition = definition("<definitions/>");
        String digest = digest(ProcessModelType.BPMN, definition);
        String payload = artifactPayload(definition, digest);
        ConcurrentWinnerChannel channel = new ConcurrentWinnerChannel(payload);
        ProcessArtifactProjector projector = new ProcessArtifactProjector(channel, PREFIX, TIMEOUT);

        assertThat(projector.repair(ref(), ProcessModelType.BPMN, definition, digest))
            .isEqualTo(ArtifactProjectionStatus.PRESENT);

        assertThat(channel.read(key(), TIMEOUT)).isEqualTo(payload);
    }

    @Test
    void existingKeyCannotBeReboundToDifferentContentOrModelType() throws Exception {
        InMemoryDeploymentSyncChannel channel = new InMemoryDeploymentSyncChannel();
        ProcessArtifactProjector projector = new ProcessArtifactProjector(channel, PREFIX, TIMEOUT);
        ProcessDefinition.Inline first = definition("<definitions/>");
        projector.repair(ref(), ProcessModelType.BPMN, first, digest(ProcessModelType.BPMN, first));

        ProcessDefinition.Inline changed = definition("<definitions><process/></definitions>");
        assertThatThrownBy(() -> projector.repair(ref(), ProcessModelType.BPMN, changed,
                digest(ProcessModelType.BPMN, changed)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH));
        assertThatThrownBy(() -> projector.repair(ref(), ProcessModelType.TBBPM, first,
                digest(ProcessModelType.TBBPM, first)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH));
    }

    @Test
    void channelReadFailureDoesNotDegradeIntoAnOverwrite() {
        AtomicInteger writes = new AtomicInteger();
        DeploymentSyncChannel channel = new DeploymentSyncChannel() {
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
        ProcessArtifactProjector projector = new ProcessArtifactProjector(channel, PREFIX, TIMEOUT);
        ProcessDefinition.Inline definition = definition("<definitions/>");

        assertThatThrownBy(() -> projector.repair(ref(), ProcessModelType.BPMN, definition,
                digest(ProcessModelType.BPMN, definition)))
            .hasMessage("unavailable");
        assertThat(writes).hasValue(0);
    }

    @Test
    void corruptExistingPayloadFailsClosed() throws Exception {
        InMemoryDeploymentSyncChannel channel = new InMemoryDeploymentSyncChannel();
        ProcessDefinition.Inline definition = definition("<definitions/>");
        String actualDigest = digest(ProcessModelType.BPMN, definition);
        String payload = ProcessArtifactPayloads
            .artifactJson(ref().namespace(), ref().code(), ref().version(), ProcessModelType.BPMN, definition.content(),
                    actualDigest)
            .replace(actualDigest, "0".repeat(64));
        assertThat(channel.compareAndSet(key(), null, payload, "application/json", TIMEOUT)).isTrue();
        ProcessArtifactProjector projector = new ProcessArtifactProjector(channel, PREFIX, TIMEOUT);

        assertThatThrownBy(() -> projector.repair(ref(), ProcessModelType.BPMN, definition,
                digest(ProcessModelType.BPMN, definition)))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.ARTIFACT_DIGEST_MISMATCH));
    }

    private static final class ConcurrentWinnerChannel implements DeploymentSyncChannel {
        private final InMemoryDeploymentSyncChannel delegate = new InMemoryDeploymentSyncChannel();
        private final String winnerPayload;
        private final AtomicBoolean winnerInjected = new AtomicBoolean();

        private ConcurrentWinnerChannel(String winnerPayload) {
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
