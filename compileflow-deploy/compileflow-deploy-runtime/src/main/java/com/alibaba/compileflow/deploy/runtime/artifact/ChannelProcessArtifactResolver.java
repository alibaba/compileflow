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

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessText;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.protocol.artifact.ProcessArtifactKeys;
import com.alibaba.compileflow.deploy.api.protocol.artifact.ProcessArtifactParser;
import com.alibaba.compileflow.deploy.api.sync.DeploymentSyncChannel;
import java.time.Duration;
import java.util.Objects;
import com.alibaba.compileflow.deploy.api.protocol.ProtocolKeyCodec;
import org.apache.commons.lang3.StringUtils;

/**
 * Resolves immutable artifacts from a distributed deployment sync channel.
 *
 * @author yusu
 */
public final class ChannelProcessArtifactResolver implements ProcessArtifactResolver {
    private final DeploymentSyncChannel channel;
    private final String keyPrefix;
    private final Duration operationTimeout;

    public ChannelProcessArtifactResolver(DeploymentSyncChannel channel, String keyPrefix, Duration operationTimeout) {
        this.channel = Objects.requireNonNull(channel, "channel");
        String configuredPrefix = ProcessText.requireNonBlank(keyPrefix, "keyPrefix");
        this.keyPrefix = ProtocolKeyCodec.keyPrefix(configuredPrefix, ProcessArtifactKeys.DEFAULT_PREFIX);
        this.operationTimeout = Objects.requireNonNull(operationTimeout, "operationTimeout");
        if (operationTimeout.isZero() || operationTimeout.isNegative()) {
            throw new IllegalArgumentException("operationTimeout must be positive");
        }
    }

    @Override
    public ProcessArtifact resolve(ProcessRef.Version key) throws Exception {
        Objects.requireNonNull(key, "key");
        String contentKey = ProcessArtifactKeys.versioned(keyPrefix, key.namespace(), key.code(), key.version());
        String payload = channel.read(contentKey, operationTimeout);
        if (StringUtils.isBlank(payload)) {
            throw DeploymentException
                .builder(DeploymentErrorCode.VERSION_NOT_FOUND, "Process artifact missing in channel: key=" + contentKey)
                .namespace(key.namespace())
                .code(key.code())
                .version(key.version())
                .build();
        }
        ProcessArtifact parsed = ProcessArtifactParser.parse(payload);
        ProcessRef.Version ref = parsed.getRef();

        if (!key.equals(ref)) {
            throw DeploymentException
                .builder(DeploymentErrorCode.ARTIFACT_IDENTITY_MISMATCH,
                        "Process artifact identity mismatch: want=" + key + " got=" + ref.namespace() + "/" + ref.code()
                        + "/" + ref.version())
                .namespace(key.namespace())
                .code(key.code())
                .version(key.version())
                .build();
        }

        return parsed;
    }
}
