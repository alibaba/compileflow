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
package com.alibaba.compileflow.deploy.runtime.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.protocol.artifact.ProcessArtifactKeys;
import com.alibaba.compileflow.deploy.api.protocol.artifact.ProcessArtifactPayloads;
import com.alibaba.compileflow.deploy.api.sync.inmemory.InMemoryDeploymentSyncChannel;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChannelProcessArtifactResolverTest {
    private static final Duration CHANNEL_TIMEOUT = Duration.ofSeconds(1);

    @Test
    void shouldResolveContentAndDigestFromChannelPayload() throws Exception {
        InMemoryDeploymentSyncChannel ch = new InMemoryDeploymentSyncChannel();
        String prefix = "compileflow.process.";
        ProcessRef.Version key = ProcessRef.version("default", "demo.flow", "v1");
        String contentKey = ProcessArtifactKeys.versioned(prefix, key.namespace(), key.code(), key.version());

        String content = "<definitions/>";
        String digest = ProcessArtifactDigest.compute(ProcessModelType.BPMN,
                ProcessDefinition.inline("demo.flow", content), Map.of());
        String payload =
                ProcessArtifactPayloads.artifactJson("default", "demo.flow", "v1", ProcessModelType.BPMN, content,
                        digest);
        assertThat(ch.compareAndSet(contentKey, null, payload, "json", CHANNEL_TIMEOUT)).isTrue();

        ChannelProcessArtifactResolver resolver = new ChannelProcessArtifactResolver(ch, prefix, CHANNEL_TIMEOUT);
        ProcessArtifact artifact = resolver.resolve(key);
        assertThat(artifact).isNotNull();
        assertThat(artifact.getRef().code()).isEqualTo("demo.flow");
        assertThat(artifact.getRef().version()).isEqualTo("v1");
        assertThat(artifact.getModelType()).isEqualTo(ProcessModelType.BPMN);
        assertThat(artifact.getDefinition().content()).isEqualTo(content);
        assertThat(artifact.getArtifactDigest()).isEqualTo(digest);
    }
}
